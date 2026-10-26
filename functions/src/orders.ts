import { Timestamp, Transaction } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { OPEN_STATUSES, Status } from "./config";
import { addEntry, bumpBalance } from "./finance";
import { canTransition } from "./stateMachine";
import { permissionForStatusChange } from "./permissions";
import { courierCommissionFor, evaluateStatusChange, GuardOrder, isNoWorseReview } from "./orderGuards";
import {
  Actor, arr, callable, db, docId, int, loadActor, obj, oneOf, optStr, optPhone, readSettings, str,
  submissionKey, writeAudit,
} from "./util";

type Order = Record<string, any>;
interface Line { productId: string; name: string; unitPrice: number; qty: number; lineTotal: number }

const minutes = (m: number) => Timestamp.fromMillis(Date.now() + m * 60_000);

function assertAccess(actor: Actor, o: Order) {
  const ok =
    actor.role === "admin" ||
    (actor.role === "customer" && o.customerId === actor.uid) ||
    (actor.role === "merchant" && !!actor.storeId && o.storeId === actor.storeId) ||
    (actor.role === "courier" &&
      (["ready_for_pickup", "out_for_delivery"].includes(o.status) || o.courierUid === actor.uid));
  if (!ok) throw new HttpsError("permission-denied", "order_access_denied");
}

function history(tx: Transaction, orderId: string, actor: { uid: string; role: string },
  from: Status | null, to: Status, extra: Record<string, unknown> = {}) {
  tx.set(db.collection(`orders/${orderId}/history`).doc(), {
    from, to, actorUid: actor.uid, actorRole: actor.role, at: Timestamp.now(), ...extra,
  });
}

// ---------- 1) إنشاء الطلب (الزبون) ----------
export const createOrder = callable(async (req) => {
  const d = obj(req.data);
  const submissionId = submissionKey(d.submissionId, "submissionId");
  const storeId = docId(d.storeId, "storeId");
  const merged = new Map<string, number>();
  for (const raw of arr(d.items, "items", 1, 50)) {
    const it = obj(raw);
    const pid = docId(it.productId, "productId");
    merged.set(pid, Math.min(99, (merged.get(pid) ?? 0) + int(it.qty, "qty", 1, 99)));
  }
  optPhone(d.phone); // يُتحقق من صحته فقط ولا يُحفظ في الطلب: رقم الزبون خاص
  const address = str(d.address, "address", 3, 300);
  const locationNote = optStr(d.locationNote, "locationNote", 300);

  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["customer"], tx);
    const userSnap = await tx.get(db.doc(`users/${actor.uid}`));
    const idemRef = db.doc(`idempotency/${actor.uid}_${submissionId}`);
    const pids = [...merged.keys()];
    const prodRefs = pids.map((p) => db.doc(`products/${p}`));
    const [idem, storeSnap, ...prodSnaps] = await tx.getAll(idemRef, db.doc(`stores/${storeId}`), ...prodRefs);
    const settings = await readSettings(tx);
    const openOrders = await tx.get(
      db.collection("orders").where("customerId", "==", actor.uid).where("status", "in", OPEN_STATUSES).limit(50)
    );

    // إعادة إرسال نفس الطلب بعد انقطاع الاتصال ترجع الطلب الأول
    if (idem.exists) return { orderId: idem.get("orderId") as string, duplicate: true };

    if (!settings.ordersEnabled) throw new HttpsError("failed-precondition", "orders_disabled");
    // سقف الطلبات المفتوحة لكل زبون (حماية من الإساءة)
    if (openOrders.size >= ((settings.maxOpenOrders as number) ?? 3)) throw new HttpsError("failed-precondition", "too_many_open_orders");
    if (!storeSnap.exists || storeSnap.get("status") !== "active") throw new HttpsError("failed-precondition", "store_unavailable");
    if (storeSnap.get("isOpen") !== true) throw new HttpsError("failed-precondition", "store_closed");

    const lines: Line[] = [];
    prodSnaps.forEach((p, i) => {
      const qty = merged.get(pids[i])!;
      if (!p.exists || p.get("storeId") !== storeId || p.get("isAvailable") !== true)
        throw new HttpsError("failed-precondition", `product_unavailable:${pids[i]}`);
      const stock = p.get("stockQty");
      if (typeof stock === "number" && stock < qty) throw new HttpsError("failed-precondition", `insufficient_stock:${pids[i]}`);
      const unitPrice = p.get("price") as number;
      lines.push({ productId: pids[i], name: p.get("name"), unitPrice, qty, lineTotal: unitPrice * qty });
    });
    const itemsTotal = lines.reduce((s, l) => s + l.lineTotal, 0);
    const deliveryFee = settings.deliveryFee as number;
    const now = Timestamp.now();
    const orderRef = db.collection("orders").doc(); // معرّف عشوائي غير قابل للتخمين

    tx.set(orderRef, {
      customerId: actor.uid,
      customerName: actor.name,
      customerPhone: null,
      storeId,
      storeName: storeSnap.get("name"),
      status: "pending_merchant",
      requestedItems: lines,
      proposal: null,
      approvedSnapshot: null,
      revision: 0,
      itemsTotal,
      deliveryFee,
      discount: 0,
      total: itemsTotal + deliveryFee,
      totalsAreEstimate: true,
      paymentMethod: "cash_on_delivery",
      address,
      locationNote,
      submissionId,
      isTest: userSnap.get("isTestAccount") === true,
      settlementStatus: "unsettled",
      settledAmount: 0,
      payableAmount: 0,
      createdAt: now,
      updatedAt: now,
      expiresAt: minutes(settings.merchantReviewMinutes as number),
    });
    tx.set(idemRef, { orderId: orderRef.id, uid: actor.uid, createdAt: now });
    history(tx, orderRef.id, actor, null, "pending_merchant");
    return { orderId: orderRef.id, duplicate: false };
  });
});

// ---------- 2) اقتراح التاجر (أول مراجعة أو تعديل بعد الموافقة) ----------
export const proposeOrder = callable(async (req) => {
  const d = obj(req.data);
  const orderId = docId(d.orderId, "orderId");
  const note = optStr(d.note, "note", 300);
  const rawItems = arr(d.items, "items", 1, 50).map((r) => {
    const it = obj(r);
    return {
      productId: docId(it.productId, "productId"),
      qty: int(it.qty, "qty", 1, 999),
      unitPrice: it.unitPrice === undefined || it.unitPrice === null ? null : int(it.unitPrice, "unitPrice", 0, 10_000_000),
    };
  });
  if (new Set(rawItems.map((i) => i.productId)).size !== rawItems.length) throw new HttpsError("invalid-argument", "duplicate_items");

  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["merchant", "admin"], tx, { merchant: "orders", admin: "orders" });
    const ref = db.doc(`orders/${orderId}`);
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "order_not_found");
    const o = snap.data() as Order;
    assertAccess(actor, o);
    if (!canTransition(actor.role, o.status, "awaiting_customer"))
      throw new HttpsError("failed-precondition", `transition_not_allowed:${o.status}->awaiting_customer`);
    const settings = await readSettings(tx);
    const prodSnaps = await tx.getAll(...rawItems.map((i) => db.doc(`products/${i.productId}`)));

    const lines: Line[] = rawItems.map((it, i) => {
      const p = prodSnaps[i];
      if (!p.exists || p.get("storeId") !== o.storeId) throw new HttpsError("failed-precondition", `product_not_in_store:${it.productId}`);
      const unitPrice = it.unitPrice ?? (p.get("price") as number);
      return { productId: it.productId, name: p.get("name"), unitPrice, qty: it.qty, lineTotal: unitPrice * it.qty };
    });
    const itemsTotal = lines.reduce((s, l) => s + l.lineTotal, 0);
    const deliveryFee = o.deliveryFee as number;
    const discount = (o.discount as number) ?? 0;
    const total = itemsTotal + deliveryFee - discount;
    const revision = ((o.revision as number) ?? 0) + 1;
    const now = Timestamp.now();
    const changedAfterApproval = o.status !== "pending_merchant";

    // قبول مباشر: نفس المنتجات والكميات وبأسعار مساوية أو أقل مما رآه الزبون، فلا حاجة لموافقة ثانية
    if (o.status === "pending_merchant" && isNoWorseReview(o.requestedItems, lines)) {
      const proposal = { items: lines, itemsTotal, deliveryFee, discount, total, note, revision, proposedAt: now, proposedBy: actor.uid };
      tx.update(ref, {
        status: "preparing",
        proposal,
        approvedSnapshot: { ...proposal, approvedAt: now, approvedBy: o.customerId, autoApproved: true },
        approvedAt: now,
        preparingAt: now,
        preparingBy: actor.uid,
        itemsTotal,
        total,
        totalsAreEstimate: false,
        revision,
        updatedAt: now,
        expiresAt: null,
      });
      history(tx, orderId, actor, o.status, "preparing", { revision, autoApproved: true });
      writeAudit(tx, actor, "accept_order", "order", orderId, { revision, itemsTotal, total, autoApproved: true });
      return { ok: true, revision, autoAccepted: true };
    }

    tx.update(ref, {
      status: "awaiting_customer",
      proposal: { items: lines, itemsTotal, deliveryFee, discount, total, note, revision, proposedAt: now, proposedBy: actor.uid },
      approvedSnapshot: null,
      approvedAt: null,
      preparingAt: null,
      itemsTotal,
      total,
      totalsAreEstimate: false,
      revision,
      updatedAt: now,
      expiresAt: minutes(settings.customerApprovalMinutes as number),
    });
    history(tx, orderId, actor, o.status, "awaiting_customer", {
      revision, changedAfterApproval, previousApproval: o.approvedSnapshot ?? null,
    });
    writeAudit(tx, actor, "propose_order", "order", orderId, { revision, itemsTotal, total, changedAfterApproval });
    return { ok: true, revision };
  });
});

// ---------- 3) رد الزبون على الإجمالي ----------
export const respondToProposal = callable(async (req) => {
  const d = obj(req.data);
  const orderId = docId(d.orderId, "orderId");
  const revision = int(d.revision, "revision", 0, 1_000_000);
  const decision = oneOf(d.decision, "decision", ["approve", "decline"] as const);

  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["customer"], tx);
    const ref = db.doc(`orders/${orderId}`);
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "order_not_found");
    const o = snap.data() as Order;
    assertAccess(actor, o);
    const now = Timestamp.now();

    if (decision === "approve") {
      if (o.status === "confirmed" && o.approvedSnapshot?.revision === revision) return { ok: true, alreadyApplied: true };
      if (o.status !== "awaiting_customer") throw new HttpsError("failed-precondition", "not_awaiting_customer");
      if (o.revision !== revision || !o.proposal) throw new HttpsError("failed-precondition", "stale_proposal");
      if (o.expiresAt && (o.expiresAt as Timestamp).toMillis() <= now.toMillis()) throw new HttpsError("failed-precondition", "proposal_expired");
      tx.update(ref, {
        status: "confirmed",
        approvedSnapshot: { ...o.proposal, approvedAt: now, approvedBy: actor.uid },
        approvedAt: now,
        updatedAt: now,
        expiresAt: null,
      });
      history(tx, orderId, actor, "awaiting_customer", "confirmed", { revision, total: o.proposal.total });
      writeAudit(tx, actor, "approve_total", "order", orderId, { revision, total: o.proposal.total });
      return { ok: true };
    }

    if (o.status === "cancelled") return { ok: true, alreadyApplied: true };
    if (!canTransition("customer", o.status, "cancelled")) throw new HttpsError("failed-precondition", "cannot_decline_now");
    tx.update(ref, {
      status: "cancelled", cancelReason: "declined_by_customer", cancelledAt: now, cancelledBy: actor.uid,
      cancelledByRole: "customer", updatedAt: now, expiresAt: null,
    });
    history(tx, orderId, actor, o.status, "cancelled", { reason: "declined_by_customer" });
    writeAudit(tx, actor, "decline_total", "order", orderId, { revision });
    return { ok: true };
  });
});

// ---------- 4) بقية الانتقالات ----------
const SIMPLE_TARGETS = [
  "rejected", "cancelled", "preparing", "ready_for_pickup", "out_for_delivery", "delivered", "delivery_failed",
] as const;
const NEEDS_REASON: Status[] = ["rejected", "cancelled", "delivery_failed"];

export const changeOrderStatus = callable(async (req) => {
  const d = obj(req.data);
  const orderId = docId(d.orderId, "orderId");
  const to = oneOf(d.to, "to", SIMPLE_TARGETS);
  const reason = NEEDS_REASON.includes(to) ? str(d.reason, "reason", 3, 300) : null;

  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, undefined, tx);
    const needed = permissionForStatusChange(actor.role, to);
    if (needed && !actor.permissions.includes(needed)) throw new HttpsError("permission-denied", "permission_denied");
    const ref = db.doc(`orders/${orderId}`);
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "order_not_found");
    const o = snap.data() as Order;
    assertAccess(actor, o);

    const settings = await readSettings(tx);
    const preparingAt = o.preparingAt as Timestamp | null | undefined;
    const verdict = evaluateStatusChange(
      actor.role, actor.uid,
      { ...(o as GuardOrder), preparingAtMs: preparingAt ? preparingAt.toMillis() : null },
      to,
      { nowMs: Date.now(), cancelGraceMs: ((settings.customerCancelGraceMinutes as number) ?? 3) * 60_000 },
    );
    if (verdict.kind === "already_applied") return { ok: true, status: to, alreadyApplied: true };
    if (verdict.kind === "reject") throw new HttpsError(verdict.httpCode, verdict.code);

    const now = Timestamp.now();
    const patch: Record<string, unknown> = { status: to, updatedAt: now, expiresAt: null };

    switch (to) {
      case "rejected":
        Object.assign(patch, { rejectReason: reason, rejectedAt: now, rejectedBy: actor.uid });
        break;
      case "cancelled":
        Object.assign(patch, { cancelReason: reason, cancelledAt: now, cancelledBy: actor.uid, cancelledByRole: actor.role });
        break;
      case "delivery_failed":
        Object.assign(patch, { failReason: reason, failedAt: now, failedBy: actor.uid });
        break;
      case "preparing":
      case "ready_for_pickup": {
        const a = o.approvedSnapshot;
        if (!a || a.revision !== o.revision) throw new HttpsError("failed-precondition", "total_not_approved");
        Object.assign(patch, to === "preparing" ? { preparingAt: now, preparingBy: actor.uid } : { readyAt: now, readyBy: actor.uid });
        break;
      }
      case "out_for_delivery": {
        // رقم المندوب يظهر للزبون ليتصل به (رقم الزبون لا يظهر لأحد)
        const cu = await tx.get(db.doc(`users/${actor.uid}`));
        const courierPhone = (cu.get("phone") as string | null | undefined) ?? null;
        Object.assign(patch, { pickedUpAt: now, courierUid: actor.uid, courierName: actor.name, courierPhone });
        break;
      }
      case "delivered": {
        const a = o.approvedSnapshot;
        if (!a || a.revision !== o.revision) throw new HttpsError("failed-precondition", "total_not_approved");
        const itemsTotal = a.itemsTotal as number;
        const deliveryRevenue = (a.deliveryFee as number) - ((a.discount as number) ?? 0);
        const collected = a.total as number;
        // التسليم والقيود المالية في معاملة واحدة؛ المعرّفات الثابتة تمنع التكرار
        addEntry(tx, `${orderId}__cash`, { orderId, storeId: o.storeId, ledger: "cash", type: "order_delivered", amount: collected, isTest: !!o.isTest }, actor);
        addEntry(tx, `${orderId}__store_payable`, {
          orderId, storeId: o.storeId, ledger: "store_payable", type: "order_delivered", amount: itemsTotal,
          balanceField: "payable", balanceDelta: itemsTotal, isTest: !!o.isTest,
        }, actor);
        addEntry(tx, `${orderId}__delivery_revenue`, { orderId, storeId: o.storeId, ledger: "delivery_revenue", type: "order_delivered", amount: deliveryRevenue, isTest: !!o.isTest }, actor);
        // عمولة المنصة من المندوب (سجل مستقل لا يغيّر بقية القيود؛ بقيمة تحددها الإدارة)
        const commission = courierCommissionFor(deliveryRevenue, settings.courierCommission);
        if (commission > 0) {
          addEntry(tx, `${orderId}__courier_commission`, {
            orderId, storeId: o.storeId, ledger: "courier_commission", type: "order_delivered", amount: commission,
            note: `courier:${o.courierUid ?? ""}`, isTest: !!o.isTest,
          }, actor);
        }
        bumpBalance(tx, o.storeId, "payable", itemsTotal);
        Object.assign(patch, {
          deliveredAt: now, deliveredBy: actor.uid, collectedAmount: collected,
          payableAmount: itemsTotal, settledAmount: 0, settlementStatus: "unsettled",
        });
        break;
      }
    }

    tx.update(ref, patch);
    history(tx, orderId, actor, o.status, to, reason ? { reason } : {});
    writeAudit(tx, actor, `order_${to}`, "order", orderId, { from: o.status, reason });
    return { ok: true, status: to };
  });
});
