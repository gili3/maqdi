import { getMessaging } from "firebase-admin/messaging";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { REGION } from "./config";
import { db } from "./util";

type Msg = { title: string; body: string };
const MESSAGES: Record<string, { to: "customer" | "merchant" | "couriers"; msg: Msg }> = {
  pending_merchant: { to: "merchant", msg: { title: "طلب جديد", body: "وصلك طلب جديد، راجعه الآن." } },
  awaiting_customer: { to: "customer", msg: { title: "راجع إجمالي طلبك", body: "راجع التاجر طلبك. وافق على الإجمالي ليبدأ التجهيز." } },
  confirmed: { to: "merchant", msg: { title: "تمت الموافقة", body: "وافق الزبون على الإجمالي. يمكنك بدء التجهيز." } },
  ready_for_pickup: { to: "couriers", msg: { title: "طلب جاهز للاستلام", body: "يوجد طلب جاهز في المتجر." } },
  out_for_delivery: { to: "customer", msg: { title: "طلبك في الطريق", body: "بدأ التوصيل." } },
  delivered: { to: "customer", msg: { title: "تم التسليم", body: "شكرًا لاستخدامك مقضي." } },
  rejected: { to: "customer", msg: { title: "تم رفض الطلب", body: "اعتذر المتجر عن تنفيذ طلبك." } },
  expired: { to: "customer", msg: { title: "انتهت مهلة الطلب", body: "انتهت المهلة، يمكنك إرسال طلب جديد." } },
  delivery_failed: { to: "customer", msg: { title: "تعذّر التسليم", body: "تعذّر تسليم طلبك. سنتواصل معك." } },
};

async function tokensFor(uids: string[]): Promise<string[]> { 
  const out: string[] = [];
  for (const uid of uids) {
    const s = await db.doc(`users/${uid}`).get();
    if (s.get("status") === "active") out.push(...((s.get("fcmTokens") as string[] | undefined) ?? []));
  }
  return out;
}

export const onOrderChanged = onDocumentWritten({ document: "orders/{orderId}", region: REGION }, async (event) => {
  const after = event.data?.after.data();
  const before = event.data?.before.data();
  if (!after || before?.status === after.status) return;
  const m = MESSAGES[after.status as string];
  if (!m) return;
  try {
    let uids: string[] = [];
    if (m.to === "customer") uids = [after.customerId];
    else if (m.to === "merchant") {
      const q = await db.collection("users").where("role", "==", "merchant").where("storeId", "==", after.storeId).get();
      uids = q.docs.map((d) => d.id);
    } else {
      const q = await db.collection("users").where("role", "in", ["courier", "admin"]).get();
      uids = q.docs.map((d) => d.id);
    }
    const tokens = (await tokensFor(uids)).slice(0, 500);
    if (tokens.length === 0) return;
    await getMessaging().sendEachForMulticast({
      tokens,
      notification: m.msg,
      data: { orderId: event.params.orderId, status: String(after.status) },
      android: { priority: "high" },
    });
  } catch (e) {
    console.error("notification_failed", e instanceof Error ? e.message : "unknown");
  }
});
