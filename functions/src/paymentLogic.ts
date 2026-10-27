// منطق الدفع بالتحويل والاسترداد (دوال خالصة بلا Firestore كي يمكن اختبارها مباشرة)

export const PAYMENT_METHODS = ["cash_on_delivery", "bank_transfer"] as const;
export type PaymentMethod = (typeof PAYMENT_METHODS)[number];

// not_required: نقدًا عند الاستلام | awaiting_payment: بانتظار التحويل | proof_submitted: أُرسل الإثبات وينتظر الإدارة | verified: أكّدت الإدارة الاستلام
export type PaymentStatus = "not_required" | "awaiting_payment" | "proof_submitted" | "verified";

export type RefundKind = "customer" | "cancelled" | "overpay";

/** التحويل لا يُجهَّز بعده الطلب إلا بعد تأكيد الإدارة استلام المبلغ */
export function paymentBlocksPreparing(method: unknown, status: unknown): boolean {
  return method === "bank_transfer" && status !== "verified";
}

/** المبلغ الذي يحصّله المندوب نقدًا: الإجمالي المعتمد ناقص ما أُكّد استلامه بالتحويل */
export function cashDueAtDelivery(total: number, paidAmount: number): number {
  return Math.max(0, Math.floor(total) - Math.max(0, Math.floor(paidAmount)));
}

/** ما دفعه الزبون زيادة عن الإجمالي (يُرد تلقائيًا) */
export function overpaidAmount(total: number, paidAmount: number): number {
  return Math.max(0, Math.floor(paidAmount) - Math.floor(total));
}

/** أقصى مبلغ يمكن طلب استرداده بعد التسليم */
export function refundCap(approvedTotal: number, alreadyRefunded: number): number {
  return Math.max(0, Math.floor(approvedTotal) - Math.max(0, Math.floor(alreadyRefunded)));
}

/** هل ما زال الزبون داخل مهلة طلب الاسترداد بعد التسليم؟ */
export function withinRefundWindow(deliveredAtMs: number | null | undefined, nowMs: number, windowHours: number): boolean {
  if (typeof deliveredAtMs !== "number" || !(windowHours > 0)) return false;
  return nowMs - deliveredAtMs <= windowHours * 3_600_000;
}

/** مسار صورة الإثبات يجب أن يتبع الطلب نفسه: payments/{orderId}/{file} */
export function proofPathValid(orderId: string, path: string): boolean {
  const prefix = `payments/${orderId}/`;
  if (!path.startsWith(prefix)) return false;
  const file = path.slice(prefix.length);
  return /^[A-Za-z0-9._-]{1,100}$/.test(file) && !file.includes("..");
}

/** ما يُخصم من مستحق المتجر عند الاسترداد: لا يتجاوز المبلغ ولا المستحق الحالي */
export function storeChargeFor(chargeStore: boolean, amount: number, payableAmount: number): number {
  if (!chargeStore) return 0;
  return Math.max(0, Math.min(Math.floor(amount), Math.floor(payableAmount)));
}
