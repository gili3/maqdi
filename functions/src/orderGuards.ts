import { ActorRole, Status } from "./config";
import { canTransition } from "./stateMachine";

// قواعد الحماية الخالصة لتغيير حالة الطلب (بلا Firestore) كي يمكن اختبارها مباشرة.
// تُستعمل داخل معاملة changeOrderStatus في orders.ts.
export interface GuardOrder {
  status: Status;
  revision?: number;
  approvedSnapshot?: { revision?: number } | null;
  courierUid?: string | null;
}

export type GuardResult =
  | { kind: "apply" }
  | { kind: "already_applied" }
  | { kind: "reject"; code: string; httpCode: "failed-precondition" | "permission-denied" };

const NEEDS_APPROVAL: Status[] = ["preparing", "ready_for_pickup", "delivered"];

export function evaluateStatusChange(
  role: ActorRole, uid: string, o: GuardOrder, to: Status
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
  if (role === "courier" && o.status === "out_for_delivery" && o.courierUid !== uid)
    return { kind: "reject", code: "not_your_delivery", httpCode: "permission-denied" };
  if (NEEDS_APPROVAL.includes(to)) {
    const a = o.approvedSnapshot;
    if (!a || a.revision !== o.revision)
      return { kind: "reject", code: "total_not_approved", httpCode: "failed-precondition" };
  }
  return { kind: "apply" };
}
