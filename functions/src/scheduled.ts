import { Timestamp } from "firebase-admin/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { REGION } from "./config";
import { getMessaging } from "firebase-admin/messaging";
import { db, readSettings, writeAudit } from "./util";
import { autoRefundRef, writeAutoRefund } from "./refunds";

// كل 5 دقائق: تنتقل الطلبات المنتهية المهلة إلى expired
export const expireOrders = onSchedule({ schedule: "every 5 minutes", region: REGION, timeZone: "Africa/Khartoum" }, async () => {
  const now = Timestamp.now();
  const due = await db
    .collection("orders")
    .where("status", "in", ["pending_merchant", "awaiting_customer", "confirmed"])
    .where("expiresAt", "<=", now)
    .limit(200)
    .get();

  for (const doc of due.docs) {
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(doc.ref);
      const autoSnap = await tx.get(autoRefundRef(doc.id));
      const o = snap.data();
      if (!o || !["pending_merchant", "awaiting_customer", "confirmed"].includes(o.status)) return;
      if (!o.expiresAt || (o.expiresAt as Timestamp).toMillis() > Date.now()) return;
      // «تمت الموافقة» لا ينتهي إلا إن كان بانتظار تحويل الزبون (طلب بالتحويل لم يدفع في المهلة)
      if (o.status === "confirmed" && !(o.paymentMethod === "bank_transfer" && o.paymentStatus === "awaiting_payment")) return;
      tx.update(doc.ref, { status: "expired", expiredAt: Timestamp.now(), updatedAt: Timestamp.now(), expiresAt: null });
      // انتهت مهلة تعديل بعد أن دفع الزبون: يُرد له المبلغ
      if (o.paymentStatus === "verified") {
        writeAutoRefund(tx, autoSnap, doc.id, o, "cancelled", Number(o.paidAmount ?? 0), "انتهت مهلة الطلب بعد الدفع", null);
      }
      tx.set(db.collection(`orders/${doc.id}/history`).doc(), { from: o.status, to: "expired", actorUid: "system", actorRole: "system", at: Timestamp.now() });
      writeAudit(tx, { uid: "system", role: "system" }, "order_expired", "order", doc.id, { from: o.status });
    });
  }
});

// كل 5 دقائق: طلب «جاهز للاستلام» لم يستلمه مندوب خلال المدة المحددة في الإعدادات ينبّه الإدارة مرة واحدة
export const alertUnclaimedOrders = onSchedule({ schedule: "every 5 minutes", region: REGION, timeZone: "Africa/Khartoum" }, async () => {
  const settings = await readSettings();
  const minutes = Number(settings.courierPickupMinutes ?? 0);
  if (!minutes || minutes <= 0) return;
  const cutoff = Timestamp.fromMillis(Date.now() - minutes * 60_000);
  const due = await db
    .collection("orders")
    .where("status", "==", "ready_for_pickup")
    .where("readyAt", "<=", cutoff)
    .limit(50)
    .get();
  const fresh = due.docs.filter((d) => !d.get("unclaimedAlertedAt"));
  if (fresh.length === 0) return;

  const admins = await db.collection("users").where("role", "==", "admin").limit(50).get();
  const active = admins.docs.filter((a) => a.get("status") === "active");
  if (active.length === 0) return;
  const tokens = [...new Set(active.flatMap((a) => (a.get("fcmTokens") as string[] | undefined) ?? []))].slice(0, 500);

  for (const doc of fresh) {
    const marked = await db.runTransaction(async (tx) => {
      const snap = await tx.get(doc.ref);
      const o = snap.data();
      if (!o || o.status !== "ready_for_pickup" || o.unclaimedAlertedAt) return false;
      tx.update(doc.ref, { unclaimedAlertedAt: Timestamp.now() });
      return true;
    });
    if (!marked) continue;
    const title = "طلب بلا مندوب";
    const body = "لم يستلمه أحد بعد.";
    const batch = db.batch();
    for (const a of active) {
      batch.set(db.collection(`users/${a.id}/notifications`).doc(), {
        title, body, orderId: doc.id, status: "ready_for_pickup", read: false, createdAt: Timestamp.now(),
      });
    }
    await batch.commit();
    if (tokens.length > 0) {
      try {
        await getMessaging().sendEachForMulticast({
          tokens, notification: { title, body }, data: { orderId: doc.id, status: "ready_for_pickup" },
          android: { priority: "high", notification: { channelId: "maqdi_orders" } },
        });
      } catch (e) {
        console.error("unclaimed_alert_failed", e instanceof Error ? e.message : "unknown");
      }
    }
  }
});
