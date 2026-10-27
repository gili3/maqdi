import { Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { addEntry } from "./finance";
import { proofPathValid } from "./paymentLogic";
import { autoRefundRef, writeAutoRefund } from "./refunds";
import { TERMINAL } from "./config";
import { callable, db, docId, int, loadActor, obj, oneOf, optStr, readSettings, str, writeAudit } from "./util";

type Doc = Record<string, any>;

// ---------- الزبون: يرفع صورة إيصال التحويل بعد موافقته على الإجمالي ----------
// صورة الإثبات تُرفع إلى Storage من التطبيق (payments/{orderId}/...)، وهنا يُسجَّل مسارها وبياناتها.
// بيانات الإثبات في مجموعة مستقلة (payment_proofs) كي لا يراها التاجر ولا المندوب.
export const submitTransferProof = callable(async (req) => {
  const d = obj(req.data);
  const orderId = docId(d.orderId, "orderId");
  const proofPath = str(d.proofPath, "proofPath", 10, 300);
  if (!proofPathValid(orderId, proofPath)) throw new HttpsError("invalid-argument", "invalid_proofPath");
  const payerAccount = str(d.payerAccount, "payerAccount", 3, 120);
  const transferRef = optStr(d.transferRef, "transferRef", 60);

  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["customer"], tx);
    const ref = db.doc(`orders/${orderId}`);
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "order_not_found");
    const o = snap.data() as Doc;
    if (o.customerId !== actor.uid) throw new HttpsError("permission-denied", "order_access_denied");
    if (o.paymentMethod !== "bank_transfer") throw new HttpsError("failed-precondition", "not_transfer_order");
    if (o.paymentStatus === "verified") throw new HttpsError("failed-precondition", "already_paid");
    // الدفع بعد موافقة الزبون على الإجمالي فقط
    if (o.status !== "confirmed") throw new HttpsError("failed-precondition", "payment_not_open");
    const approved = o.approvedSnapshot as Doc | null;
    if (!approved || approved.revision !== o.revision) throw new HttpsError("failed-precondition", "total_not_approved");

    const now = Timestamp.now();
    const amount = approved.total as number;
    tx.set(db.doc(`payment_proofs/${orderId}`), {
      orderId, customerId: actor.uid, storeId: o.storeId, path: proofPath, transferRef, payerAccount, amount,
      status: "submitted", rejectReason: null, submittedAt: now, reviewedAt: null, reviewedBy: null, receivedAmount: null,
    });
    // أثناء مراجعة الإدارة لا تنتهي مهلة الدفع
    tx.update(ref, { paymentStatus: "proof_submitted", paymentRejectReason: null, expiresAt: null, updatedAt: now });
    writeAudit(tx, actor, "submit_transfer_proof", "order", orderId, { amount });
    return { ok: true };
  });
});

// ---------- الإدارة: تأكيد استلام التحويل أو رفض الإثبات ----------
export const adminReviewPayment = callable(async (req) => {
  const d = obj(req.data);
  const orderId = docId(d.orderId, "orderId");
  const decision = oneOf(d.decision, "decision", ["verify", "reject"] as const);
  const reason = decision === "reject" ? str(d.reason, "reason", 3, 300) : null;
  const received = d.receivedAmount === undefined || d.receivedAmount === null ? null : int(d.receivedAmount, "receivedAmount", 1, 1_000_000_000);

  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["admin"], tx, { admin: "finance" });
    const ref = db.doc(`orders/${orderId}`);
    const proofRef = db.doc(`payment_proofs/${orderId}`);
    const [snap, proofSnap, autoSnap] = await tx.getAll(ref, proofRef, autoRefundRef(orderId));
    const settings = await readSettings(tx);
    if (!snap.exists) throw new HttpsError("not-found", "order_not_found");
    const o = snap.data() as Doc;
    if (o.paymentMethod !== "bank_transfer") throw new HttpsError("failed-precondition", "not_transfer_order");
    if (decision === "verify" && o.paymentStatus === "verified") return { ok: true, alreadyApplied: true };
    if (o.paymentStatus !== "proof_submitted" || !proofSnap.exists) throw new HttpsError("failed-precondition", "no_proof_to_review");
    const proof = proofSnap.data() as Doc;
    const now = Timestamp.now();

    if (decision === "reject") {
      // يعود الطلب لانتظار الدفع بمهلة جديدة (إن كان ما زال في «تمت الموافقة»)
      const stillOpen = o.status === "confirmed";
      tx.update(ref, {
        paymentStatus: "awaiting_payment", paymentRejectReason: reason, updatedAt: now,
        expiresAt: stillOpen ? Timestamp.fromMillis(Date.now() + Number(settings.paymentWindowMinutes) * 60_000) : null,
      });
      tx.update(proofRef, { status: "rejected", rejectReason: reason, reviewedAt: now, reviewedBy: actor.uid });
      writeAudit(tx, actor, "reject_transfer_proof", "order", orderId, { reason });
      return { ok: true };
    }

    const amount = received ?? (proof.amount as number);
    addEntry(tx, `${orderId}__bank_in`, {
      orderId, storeId: o.storeId, ledger: "bank", type: "transfer_received", amount, note: (proof.transferRef as string | null) ?? null, isTest: !!o.isTest,
    }, actor);
    tx.update(ref, { paymentStatus: "verified", paidAmount: amount, paidAt: now, paymentVerifiedBy: actor.uid, paymentRejectReason: null, expiresAt: null, updatedAt: now });
    tx.update(proofRef, { status: "verified", receivedAmount: amount, reviewedAt: now, reviewedBy: actor.uid });
    // وصل المبلغ بعد أن انتهى الطلب (أُلغي أو انتهت مهلته): يجب رده للزبون
    if (TERMINAL.includes(o.status)) {
      writeAutoRefund(tx, autoSnap, orderId, o, "cancelled", amount, "دفع وصل بعد انتهاء الطلب", (proof.payerAccount as string | null) ?? null);
    }
    writeAudit(tx, actor, "verify_transfer", "order", orderId, { amount });
    return { ok: true };
  });
});
