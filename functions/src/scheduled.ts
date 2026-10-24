import { Timestamp } from "firebase-admin/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { REGION } from "./config";
import { db, writeAudit } from "./util";

// كل 5 دقائق: تنتقل الطلبات المنتهية المهلة إلى expired
export const expireOrders = onSchedule({ schedule: "every 5 minutes", region: REGION, timeZone: "Africa/Khartoum" }, async () => {
  const now = Timestamp.now();
  const due = await db
    .collection("orders")
    .where("status", "in", ["pending_merchant", "awaiting_customer"])
    .where("expiresAt", "<=", now)
    .limit(200)
    .get();

  for (const doc of due.docs) {
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(doc.ref);
      const o = snap.data();
      if (!o || !["pending_merchant", "awaiting_customer"].includes(o.status)) return;
      if (!o.expiresAt || (o.expiresAt as Timestamp).toMillis() > Date.now()) return;
      tx.update(doc.ref, { status: "expired", expiredAt: Timestamp.now(), updatedAt: Timestamp.now(), expiresAt: null });
      tx.set(db.collection(`orders/${doc.id}/history`).doc(), { from: o.status, to: "expired", actorUid: "system", actorRole: "system", at: Timestamp.now() });
      writeAudit(tx, { uid: "system", role: "system" }, "order_expired", "order", doc.id, { from: o.status });
    });
  }
});
