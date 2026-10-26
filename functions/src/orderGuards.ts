import { ActorRole, Status } from "./config";
import { canTransition } from "./stateMachine";

// قواعد الحماية الخالصة لتغيير حالة الطلب (بلا Firestore) كي يمكن اختبارها مباشرة.
// تُستعمل داخل معاملة changeOrderStatus في orders.ts.
export interface GuardOrder {
  status: Status;
  revision?: number;
  approvedSnapshot?: { revision?: number } | null;
  courierUid?: string | null;
  preparingAtMs?: number | null; // لحظة بدء التجهيز (مللي ثانية)
}

export interface GuardContext { nowMs: number; cancelGraceMs: number }

export type GuardResult =
  | { kind: "apply" }
  | { kind: "already_applied" }
  | { kind: "reject"; code: string; httpCode: "failed-precondition" | "permission-denied" };

export interface SimpleLine { productId: string; qty: number; unitPrice: number }

/** هل مراجعة التاجر تطابق ما طلبه الزبون تمامًا؟ (نفس المنتجات والكميات والأسعار) */
export function isUnchangedReview(requested: SimpleLine[] | undefined | null, reviewed: SimpleLine[]): boolean {
  if (!Array.isArray(requested) || requested.length === 0 || requested.length !== reviewed.length) return false;
  return reviewed.every((l) =>
    requested.some((r) => r.productId === l.productId && r.qty === l.qty && r.unitPrice === l.unitPrice));
}

/** قبول مباشر: نفس المنتجات ونفس الكميات تمامًا، وكل سعر يساوي ما رآه الزبون أو أقل منه (لا يضر الزبون أبدًا) */
export function isNoWorseReview(requested: SimpleLine[] | undefined | null, reviewed: SimpleLine[]): boolean {
  if (!Array.isArray(requested) || requested.length === 0 || requested.length !== reviewed.length) return false;
  return reviewed.every((l) =>
    requested.some((r) => r.productId === l.productId && r.qty === l.qty && l.unitPrice <= r.unitPrice));
}

const NEEDS_APPROVAL: Status[] = ["preparing", "ready_for_pickup", "delivered"];

export function evaluateStatusChange(
  role: ActorRole, uid: string, o: GuardOrder, to: Status, ctx?: GuardContext
): GuardResult {
  // الضغط المزدوج أو إعادة المحاولة: لا أثر ثانٍ
  if (o.status === to) {
    // مندوب ثانٍ يضغط «استلمت» بعد أن استلمه غيره: خطأ صريح لا نجاح كاذب
    if (to === "out_for_delivery" && o.courierUid !== uid)
      return { kind: "reject", code: "already_taken", httpCode: "failed-precondition" };
    return { kind: "already_applied" };
  }
  if (!canTransition(role, o.status, to))
    return { kind: "reject", code: `transition_not_allowed:${o.status}->${to}`, httpCode: "failed-precondition" };
  // إلغاء الزبون أثناء التجهيز: فقط خلال المهلة بعد بدء التجهيز
  if (role === "customer" && o.status === "preparing" && to === "cancelled") {
    const start = o.preparingAtMs;
    const ok = !!ctx && ctx.cancelGraceMs > 0 && typeof start === "number" && ctx.nowMs - start <= ctx.cancelGraceMs;
    if (!ok) return { kind: "reject", code: "cancel_window_closed", httpCode: "failed-precondition" };
  }
  if (role === "courier" && o.status === "out_for_delivery" && o.courierUid !== uid)
    return { kind: "reject", code: "not_your_delivery", httpCode: "permission-denied" };
  if (NEEDS_APPROVAL.includes(to)) {
    const a = o.approvedSnapshot;
    if (!a || a.revision !== o.revision)
      return { kind: "reject", code: "total_not_approved", httpCode: "failed-precondition" };
  }
  return { kind: "apply" };
}

/** عمولة المنصة من المندوب: مبلغ ثابت تحدده الإدارة، لا تتجاوز رسم التوصيل المحصَّل ولا تقل عن صفر */
export function courierCommissionFor(deliveryRevenue: number, setting: unknown): number {
  const c = typeof setting === "number" && isFinite(setting) ? Math.floor(setting) : 0;
  return Math.max(0, Math.min(c, Math.max(0, Math.floor(deliveryRevenue))));
}
