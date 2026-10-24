import { DocumentReference, DocumentSnapshot, FieldValue, Query, Timestamp, Transaction } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import {
  callable, db, docId, int, loadActor, obj, optStr, readSettings, str, submissionKey, writeAudit,
} from "./util";
import { LOCAL_OFFSET_MS, STATUSES } from "./config";
import { AggregateField } from "firebase-admin/firestore";

export type Ledger = "cash" | "store_payable" | "delivery_revenue";

export interface EntryInput {
  orderId?: string | null;
  storeId?: string | null;
  ledger: Ledger;
  type: string;
  amount: number; // موقّع: الموجب زيادة والسالب نقصان في هذا الدفتر
  balanceField?: "payable" | "settled" | null;
  balanceDelta?: number;
  reversesEntryId?: string | null;
  replacesEntryId?: string | null;
  settlementId?: string | null;
  note?: string | null;
  isTest: boolean;
}

// القيد يُنشأ بمعرّف ثابت عبر create: إعادة المحاولة لا تنتج قيدًا مكررًا.
export function addEntry(tx: Transaction, id: string, e: EntryInput, actor: { uid: string; role: string }) {
  tx.create(db.doc(`financial_entries/${id}`), {
    orderId: e.orderId ?? null,
    storeId: e.storeId ?? null,
    ledger: e.ledger,
    type: e.type,
    amount: e.amount,
    balanceField: e.balanceField ?? null,
    balanceDelta: e.balanceDelta ?? 0,
    reversesEntryId: e.reversesEntryId ?? null,
    replacesEntryId: e.replacesEntryId ?? null,
    settlementId: e.settlementId ?? null,
    note: e.note ?? null,
    isTest: e.isTest,
    createdAt: Timestamp.now(),
    createdBy: actor.uid,
    createdByRole: actor.role,
  });
}

export function bumpBalance(tx: Transaction, storeId: string, field: "payable" | "settled", delta: number) {
  tx.set(
    db.doc(`store_balances/${storeId}`),
    { storeId, [field]: FieldValue.increment(delta), updatedAt: Timestamp.now() },
    { merge: true }
  );
}

function settlementState(payable: number, settled: number): "unsettled" | "partially_settled" | "settled" {
  if (settled <= 0) return "unsettled";
  if (settled >= payable) return "settled";
  return "partially_settled";
}

// ---------- تسجيل تسوية مع متجر ----------
export const adminRecordSettlement = callable(async (req) => {
  const d = obj(req.data);
  const storeId = docId(d.storeId, "storeId");
  const amount = int(d.amount, "amount", 1, 1_000_000_000);
  const key = submissionKey(d.settlementKey, "settlementKey");
  const note = optStr(d.note, "note", 300);
  const settlementId = `${storeId}_${key}`;

  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["admin"], tx);
    const setRef = db.doc(`store_settlements/${settlementId}`);
    const balRef = db.doc(`store_balances/${storeId}`);
    const storeRef = db.doc(`stores/${storeId}`);
    const [setSnap, balSnap, storeSnap] = await tx.getAll(setRef, balRef, storeRef);
    if (setSnap.exists) return { settlementId, duplicate: true };
    if (!storeSnap.exists) throw new HttpsError("not-found", "store_not_found");

    const payable = (balSnap.get("payable") as number | undefined) ?? 0;
    const settled = (balSnap.get("settled") as number | undefined) ?? 0;
    if (amount > payable - settled) throw new HttpsError("failed-precondition", "exceeds_outstanding");

    // توزيع المبلغ على أقدم الطلبات غير المسوّاة
    const q = db
      .collection("orders")
      .where("storeId", "==", storeId)
      .where("settlementStatus", "in", ["unsettled", "partially_settled"])
      .orderBy("deliveredAt", "asc")
      .limit(200);
    const orders = await tx.get(q);

    let left = amount;
    const allocations: { orderId: string; amount: number }[] = [];
    const updates: { ref: DocumentReference; settledAmount: number; status: string }[] = [];
    for (const doc of orders.docs) {
      if (left <= 0) break;
      const o = doc.data();
      const due = (o.payableAmount as number) - ((o.settledAmount as number) ?? 0);
      if (due <= 0) continue;
      const take = Math.min(due, left);
      left -= take;
      allocations.push({ orderId: doc.id, amount: take });
      const newSettled = ((o.settledAmount as number) ?? 0) + take;
      updates.push({ ref: doc.ref, settledAmount: newSettled, status: settlementState(o.payableAmount, newSettled) });
    }

    const now = Timestamp.now();
    tx.create(setRef, { storeId, amount, note, allocations, unallocated: left, createdAt: now, createdBy: actor.uid });
    for (const u of updates) tx.update(u.ref, { settledAmount: u.settledAmount, settlementStatus: u.status, updatedAt: now });
    addEntry(tx, `${settlementId}__store_payable`, {
      storeId, ledger: "store_payable", type: "settlement", amount: -amount,
      balanceField: "settled", balanceDelta: amount, settlementId, note, isTest: false,
    }, actor);
    addEntry(tx, `${settlementId}__cash`, {
      storeId, ledger: "cash", type: "settlement", amount: -amount, settlementId, note, isTest: false,
    }, actor);
    bumpBalance(tx, storeId, "settled", amount);
    writeAudit(tx, actor, "record_settlement", "store", storeId, { settlementId, amount });
    return { settlementId, duplicate: false };
  });
});

// ---------- قيد تصحيح مرتبط بقيد أصلي (لا تعديل ولا حذف) ----------
export const adminCorrectEntry = callable(async (req) => {
  const d = obj(req.data);
  const entryId = docId(d.entryId, "entryId");
  const reason = str(d.reason, "reason", 3, 300);
  const replacement = d.replacementAmount === undefined || d.replacementAmount === null
    ? null
    : int(d.replacementAmount, "replacementAmount", -1_000_000_000, 1_000_000_000);

  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["admin"], tx);
    const origRef = db.doc(`financial_entries/${entryId}`);
    const revRef = db.doc(`financial_entries/${entryId}__rev`);
    const [origSnap, revSnap] = await tx.getAll(origRef, revRef);
    if (!origSnap.exists) throw new HttpsError("not-found", "entry_not_found");
    if (revSnap.exists) throw new HttpsError("failed-precondition", "already_reversed");
    const o = origSnap.data() as Record<string, any>;
    if (o.reversesEntryId) throw new HttpsError("failed-precondition", "cannot_reverse_a_correction");

    // قراءات الطلبات المرتبطة قبل أي كتابة
    let orderRef: DocumentReference | null = null;
    let orderSnap: DocumentSnapshot | null = null;
    if (o.orderId && o.balanceField === "payable") {
      orderRef = db.doc(`orders/${o.orderId}`);
      orderSnap = await tx.get(orderRef);
    }
    let settlementOrders: { ref: DocumentReference; snap: DocumentSnapshot; amount: number }[] = [];
    if (o.type === "settlement" && o.balanceField === "settled" && o.settlementId) {
      const setSnap = await tx.get(db.doc(`store_settlements/${o.settlementId}`));
      const allocs = ((setSnap.get("allocations") as { orderId: string; amount: number }[] | undefined) ?? []);
      const refs = allocs.map((a) => db.doc(`orders/${a.orderId}`));
      const snaps = refs.length ? await tx.getAll(...refs) : [];
      settlementOrders = snaps.map((s, i) => ({ ref: refs[i], snap: s, amount: allocs[i].amount }));
    }

    const now = Timestamp.now();
    addEntry(tx, `${entryId}__rev`, {
      orderId: o.orderId, storeId: o.storeId, ledger: o.ledger, type: "correction",
      amount: -o.amount, balanceField: o.balanceField, balanceDelta: -(o.balanceDelta ?? 0),
      reversesEntryId: entryId, settlementId: o.settlementId ?? null, note: reason, isTest: !!o.isTest,
    }, actor);

    if (o.balanceField && o.storeId) bumpBalance(tx, o.storeId, o.balanceField, -(o.balanceDelta ?? 0));

    if (orderRef && orderSnap?.exists) {
      const od = orderSnap.data() as Record<string, any>;
      const newPayable = (od.payableAmount ?? 0) - (o.balanceDelta ?? 0);
      tx.update(orderRef, {
        payableAmount: newPayable,
        settlementStatus: settlementState(newPayable, od.settledAmount ?? 0),
        updatedAt: now,
      });
    }
    for (const s of settlementOrders) {
      if (!s.snap.exists) continue;
      const od = s.snap.data() as Record<string, any>;
      const newSettled = Math.max(0, (od.settledAmount ?? 0) - s.amount);
      tx.update(s.ref, { settledAmount: newSettled, settlementStatus: settlementState(od.payableAmount ?? 0, newSettled), updatedAt: now });
    }

    if (replacement !== null) {
      addEntry(tx, `${entryId}__repl`, {
        orderId: o.orderId, storeId: o.storeId, ledger: o.ledger, type: "correction_replacement",
        amount: replacement, replacesEntryId: entryId, note: reason, isTest: !!o.isTest,
      }, actor);
    }
    writeAudit(tx, actor, "correct_entry", "financial_entry", entryId, { reason, replacement });
    return { ok: true };
  });
});

// ---------- المصروفات ----------
export const adminAddExpense = callable(async (req) => {
  const d = obj(req.data);
  const amount = int(d.amount, "amount", 1, 1_000_000_000);
  const category = str(d.category, "category", 2, 60);
  const note = optStr(d.note, "note", 300);
  const key = submissionKey(d.expenseKey, "expenseKey");
  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["admin"], tx);
    const ref = db.doc(`expenses/${key}`);
    const snap = await tx.get(ref);
    if (snap.exists) return { expenseId: key, duplicate: true };
    tx.create(ref, { amount, category, note, createdAt: Timestamp.now(), createdBy: actor.uid });
    writeAudit(tx, actor, "add_expense", "expense", key, { amount, category });
    return { expenseId: key, duplicate: false };
  });
});

// ---------- تصدير CSV ----------
function csvCell(v: unknown, isText: boolean): string {
  let s = v === null || v === undefined ? "" : String(v);
  // منع حقن الصيغ في Excel للحقول النصية
  if (isText && /^[=+\-@\t\r]/.test(s)) s = "'" + s;
  return `"${s.replace(/"/g, '""')}"`;
}

export const adminExportFinancialCsv = callable(async (req) => {
  const d = obj(req.data);
  const from = new Date(str(d.from, "from", 8, 40));
  const to = new Date(str(d.to, "to", 8, 40));
  if (isNaN(from.getTime()) || isNaN(to.getTime()) || to <= from) throw new HttpsError("invalid-argument", "invalid_range");
  const storeId = d.storeId ? docId(d.storeId, "storeId") : null;
  await loadActor(req, ["admin"]);

  let q: Query = db.collection("financial_entries");
  if (storeId) q = q.where("storeId", "==", storeId);
  q = q.where("createdAt", ">=", Timestamp.fromDate(from)).where("createdAt", "<", Timestamp.fromDate(to)).orderBy("createdAt", "asc").limit(5000);
  const snap = await q.get();

  const head = ["id", "createdAt", "orderId", "storeId", "ledger", "type", "amount", "reversesEntryId", "isTest", "note", "createdBy"];
  const lines = [head.map((h) => csvCell(h, false)).join(",")];
  for (const doc of snap.docs) {
    const e = doc.data();
    lines.push([
      csvCell(doc.id, true), csvCell((e.createdAt as Timestamp).toDate().toISOString(), false),
      csvCell(e.orderId, true), csvCell(e.storeId, true), csvCell(e.ledger, true), csvCell(e.type, true),
      csvCell(e.amount, false), csvCell(e.reversesEntryId, true), csvCell(e.isTest, false),
      csvCell(e.note, true), csvCell(e.createdBy, true),
    ].join(","));
  }
  return { csv: lines.join("\n"), rows: snap.size, truncated: snap.size === 5000 };
});

// ---------- لوحة الإحصائيات ----------
export const adminDashboard = callable(async (req) => {
  await loadActor(req, ["admin"]);
  const now = Date.now();
  const startLocal = Math.floor((now + LOCAL_OFFSET_MS) / 86_400_000) * 86_400_000 - LOCAL_OFFSET_MS;
  const startTs = Timestamp.fromMillis(startLocal);

  const counts: Record<string, number> = {};
  await Promise.all(
    STATUSES.map(async (s) => {
      const r = await db.collection("orders").where("status", "==", s).count().get();
      counts[s] = r.data().count;
    })
  );

  const sumFor = async (ledger: Ledger) => {
    const r = await db
      .collection("financial_entries")
      .where("ledger", "==", ledger)
      .where("createdAt", ">=", startTs)
      .aggregate({ total: AggregateField.sum("amount") })
      .get();
    return r.data().total ?? 0;
  };
  const [deliveryRevenueToday, cashToday] = await Promise.all([sumFor("delivery_revenue"), sumFor("cash")]);
  const settings = await readSettings();
  return { ordersByStatus: counts, deliveryRevenueToday, cashToday, settings };
});
