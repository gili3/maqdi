import { DocumentSnapshot, Timestamp, Transaction } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { addEntry, bumpBalance, settlementState } from "./finance";
import { refundCap, RefundKind, storeChargeFor, withinRefundWindow } from "./paymentLogic";
import {
  bool, callable, db, docId, int, loadActor, obj, oneOf, optStr, readSettings, str, submissionKey, writeAudit,
} from "./util";

type Doc = Record<string, any>;

export const autoRefundRef = (orderId: string) => db.doc(`refund_requests/auto_${orderId}`);

/**
 * ينشئ طلب استرداد تلقائيًا (إلغاء بعد الدفع، أو دفع زائد عند التسليم). لا يكرّر: المعرّف ثابت لكل طلب.
 * existing: نتيجة tx.get للمرجع autoRefundRef(orderId) وقد قُرئت قبل أي كتابة في المعاملة.
 * يرجع true إن أنشأ طلبًا جديدًا.
 */
export function writeAutoRefund(
  tx: Transaction, existing: DocumentSnapshot | null, orderId: string, o: Doc,
  kind: Exclude<RefundKind, "customer">, amount: number, reason: string, refundAccount: string | null,
): boolean {
  if (amount <= 0 || (existing && existing.exists)) return false;
  const now = Timestamp.now();
  tx.create(autoRefundRef(orderId), {
    orderId, customerId: o.customerId, customerName: o.customerName ?? "", storeId: o.storeId, storeName: o.storeName ?? "",
    kind, source: "auto", amount, approvedAmount: null, reason, refundAccount,
    paymentMethod: o.paymentMethod ?? "bank_transfer",
    status: "pending", reference: null, adminNote: null, chargeStore: false, chargedToStore: 0,
    isTest: !!o.isTest, createdAt: now, resolvedAt: null, resolvedBy: null,
  });
  tx.update(db.doc(`orders/${orderId}`), { refundOpenId: `auto_${orderId}`, updatedAt: now });
  return true;
}

// ---------- طلب استرداد من الزبون (بعد التسليم) ----------
export const createRefundRequest = callable(async (req) => {
  const d = obj(req.data);
  const orderId = docId(d.orderId, "orderId");
  const amount = int(d.amount, "amount", 1, 1_000_000_000);
  const reason = str(d.reason, "reason", 3, 300);
  const refundAccount = str(d.refundAccount, "refundAccount", 3, 200);
  const key = submissionKey(d.refundKey, "refundKey");
  const refundId = `${orderId}_${key}`;

  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["customer"], tx);
    const orderRef = db.doc(`orders/${orderId}`);
    const reqRef = db.doc(`refund_requests/${refundId}`);
    const [oSnap, rSnap] = await tx.getAll(orderRef, reqRef);
    const settings = await readSettings(tx);
    if (rSnap.exists) return { refundId, duplicate: true }; // إعادة إرسال بعد انقطاع الاتصال
    if (!oSnap.exists) throw new HttpsError("not-found", "order_not_found");
    const o = oSnap.data() as Doc;
    if (o.customerId !== actor.uid) throw new HttpsError("permission-denied", "order_access_denied");
    if (o.status !== "delivered") throw new HttpsError("failed-precondition", "refund_not_allowed");
    if (o.refundOpenId) throw new HttpsError("failed-precondition", "refund_already_open");
    const deliveredAt = o.deliveredAt as Timestamp | undefined;
    if (!withinRefundWindow(deliveredAt ? deliveredAt.toMillis() : null, Date.now(), Number(settings.refundWindowHours)))
      throw new HttpsError("failed-precondition", "refund_window_closed");
    const approvedTotal = Number(o.approvedSnapshot?.total ?? o.total ?? 0);
    if (amount > refundCap(approvedTotal, Number(o.refundedAmount ?? 0))) throw new HttpsError("failed-precondition", "refund_amount_exceeds");

    const now = Timestamp.now();
    tx.create(reqRef, {
      orderId, customerId: actor.uid, customerName: actor.name, storeId: o.storeId, storeName: o.storeName ?? "",
      kind: "customer", source: "customer", amount, approvedAmount: null, reason, refundAccount,
      paymentMethod: o.paymentMethod ?? "cash_on_delivery",
      status: "pending", reference: null, adminNote: null, chargeStore: false, chargedToStore: 0,
      isTest: !!o.isTest, createdAt: now, resolvedAt: null, resolvedBy: null,
    });
    tx.update(orderRef, { refundOpenId: refundId, updatedAt: now });
    writeAudit(tx, actor, "create_refund_request", "refund_request", refundId, { orderId, amount });
    return { refundId, duplicate: false };
  });
});

// ---------- قرار الإدارة: تم الاسترداد أو رفض الطلب ----------
export const adminResolveRefund = callable(async (req) => {
  const d = obj(req.data);
  const refundId = docId(d.refundId, "refundId");
  const decision = oneOf(d.decision, "decision", ["refund", "reject"] as const);
  const adminNote = optStr(d.adminNote, "adminNote", 300);
  const chargeStore = d.chargeStore === undefined || d.chargeStore === null ? false : bool(d.chargeStore, "chargeStore");
  const reference = decision === "refund" ? str(d.reference, "reference", 3, 100) : null;
  const approvedAmount = d.approvedAmount === undefined || d.approvedAmount === null ? null : int(d.approvedAmount, "approvedAmount", 1, 1_000_000_000);
  if (decision === "reject" && !adminNote) throw new HttpsError("invalid-argument", "invalid_adminNote");

  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["admin"], tx, { admin: "finance" });
    const rRef = db.doc(`refund_requests/${refundId}`);
    const rSnap = await tx.get(rRef);
    if (!rSnap.exists) throw new HttpsError("not-found", "refund_not_found");
    const r = rSnap.data() as Doc;
    const oRef = db.doc(`orders/${r.orderId}`);
    const oSnap = await tx.get(oRef);

    if (r.status !== "pending") {
      if ((decision === "refund" && r.status === "refunded") || (decision === "reject" && r.status === "rejected"))
        return { ok: true, alreadyApplied: true };
      throw new HttpsError("failed-precondition", "refund_already_resolved");
    }
    if (!oSnap.exists) throw new HttpsError("not-found", "order_not_found");
    const o = oSnap.data() as Doc;
    const now = Timestamp.now();

    if (decision === "reject") {
      tx.update(rRef, { status: "rejected", adminNote, resolvedAt: now, resolvedBy: actor.uid });
      tx.update(oRef, { refundOpenId: null, lastRefundStatus: "rejected", updatedAt: now });
      writeAudit(tx, actor, "reject_refund", "refund_request", refundId, { orderId: r.orderId, adminNote });
      return { ok: true };
    }

    const amount = approvedAmount ?? (r.amount as number);
    if (amount > (r.amount as number)) throw new HttpsError("invalid-argument", "invalid_approvedAmount");
    const orderPatch: Doc = { refundOpenId: null, lastRefundStatus: "refunded", updatedAt: now };
    let charged = 0;

    // خروج المبلغ من الحساب البنكي للمنصة
    addEntry(tx, `${refundId}__bank_out`, {
      orderId: r.orderId, storeId: r.storeId, ledger: "bank", type: "refund_paid", amount: -amount, note: reference, isTest: !!r.isTest,
    }, actor);

    if (r.kind === "customer") {
      // استرداد بعد التسليم: إما على المتجر (يُخصم من مستحقه) أو تتحمله المنصة (يُخصم من صافي إيرادها)
      charged = storeChargeFor(chargeStore, amount, Number(o.payableAmount ?? 0));
      if (charged > 0) {
        addEntry(tx, `${refundId}__store_charge`, {
          orderId: r.orderId, storeId: r.storeId, ledger: "store_payable", type: "refund_charged", amount: -charged,
          balanceField: "payable", balanceDelta: -charged, note: reference, isTest: !!r.isTest,
        }, actor);
        bumpBalance(tx, r.storeId as string, "payable", -charged);
        const newPayable = Number(o.payableAmount ?? 0) - charged;
        orderPatch.payableAmount = newPayable;
        orderPatch.settlementStatus = settlementState(newPayable, Number(o.settledAmount ?? 0));
      }
      const platformCost = amount - charged;
      if (platformCost > 0) {
        addEntry(tx, `${refundId}__platform_cost`, {
          orderId: r.orderId, storeId: r.storeId, ledger: "refund_cost", type: "refund_cost", amount: platformCost, note: reference, isTest: !!r.isTest,
        }, actor);
      }
      orderPatch.refundedAmount = Number(o.refundedAmount ?? 0) + amount;
    }
    tx.update(oRef, orderPatch);
    tx.update(rRef, {
      status: "refunded", approvedAmount: amount, reference, adminNote, chargeStore: charged > 0, chargedToStore: charged,
      resolvedAt: now, resolvedBy: actor.uid,
    });
    writeAudit(tx, actor, "refund_paid", "refund_request", refundId, { orderId: r.orderId, amount, charged, reference });
    return { ok: true };
  });
});
