import { Query, Timestamp } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { REGION } from "./config";
import { planFor, Target } from "./notificationPlan";
import { db } from "./util";

interface Recipient { uid: string; tokens: string[] }

async function recipients(to: Target, order: Record<string, any>): Promise<Recipient[]> {
  const fromDoc = (id: string, d: Record<string, any> | undefined): Recipient | null =>
    d && d.status === "active" ? { uid: id, tokens: (d.fcmTokens as string[] | undefined) ?? [] } : null;

  if (to === "customer") {
    const s = await db.doc(`users/${order.customerId}`).get();
    const r = fromDoc(s.id, s.data());
    return r ? [r] : [];
  }
  let q: Query = db.collection("users");
  if (to === "merchants") q = q.where("role", "==", "merchant").where("storeId", "==", order.storeId);
  else if (to === "admins") q = q.where("role", "==", "admin");
  else q = q.where("role", "in", ["courier", "admin"]); // المدير يوصّل أيضًا
  const snap = await q.limit(100).get();
  return snap.docs.map((d) => fromDoc(d.id, d.data())).filter((r): r is Recipient => r !== null);
}

export const onOrderChanged = onDocumentWritten({ document: "orders/{orderId}", region: REGION }, async (event) => {
  const after = event.data?.after.data();
  const before = event.data?.before.data();
  if (!after || before?.status === after.status) return;
  const plans = planFor(after);
  if (plans.length === 0) return;
  const orderId = event.params.orderId;

  for (const plan of plans) {
    try {
      const rs = (await recipients(plan.to, after)).slice(0, 100);
      if (rs.length === 0) continue;

      // 1) مركز الإشعارات داخل التطبيق
      const batch = db.batch();
      for (const r of rs) {
        batch.set(db.collection(`users/${r.uid}/notifications`).doc(), {
          title: plan.title, body: plan.body, orderId, status: after.status, read: false, createdAt: Timestamp.now(),
        });
      }
      await batch.commit();

      // 2) إشعار الدفع FCM
      const tokens = [...new Set(rs.flatMap((r) => r.tokens))].slice(0, 500);
      if (tokens.length > 0) {
        await getMessaging().sendEachForMulticast({
          tokens,
          notification: { title: plan.title, body: plan.body },
          data: { orderId, status: String(after.status) },
          android: { priority: "high" },
        });
      }
    } catch (e) {
      console.error("notification_failed", plan.to, e instanceof Error ? e.message : "unknown");
    }
  }
});
