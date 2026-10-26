export type Target = "customer" | "merchants" | "couriers" | "admins";
export interface Plan { to: Target; title: string; body: string }

/** من يُخطَر عند وصول الطلب إلى حالة جديدة (دالة نقية قابلة للاختبار) */
export function planFor(after: Record<string, unknown>): Plan[] {
  const p = (to: Target, title: string, body: string): Plan => ({ to, title, body });
  switch (after.status) {
    case "pending_merchant":
      return [p("merchants", "طلب جديد", "وصلك طلب جديد.")];
    case "awaiting_customer":
      return [p("customer", "راجع إجمالي طلبك", "وافق على الإجمالي ليبدأ التجهيز.")];
    case "confirmed":
      return [p("merchants", "تمت موافقة الزبون", "يمكنك بدء التجهيز.")];
    case "preparing":
      return [p("customer", "طلبك قيد التجهيز", "بدأ المتجر تجهيزه.")];
    case "ready_for_pickup":
      return [
        p("couriers", "طلب جاهز للاستلام", "طلب جاهز في المتجر."),
        p("customer", "طلبك جاهز", "بانتظار المندوب."),
      ];
    case "out_for_delivery":
      return [p("customer", "طلبك في الطريق", "بدأ التوصيل.")];
    case "delivered":
      return [
        p("customer", "تم التسليم", "شكرًا لاستخدامك مقضي."),
        p("merchants", "تم تسليم الطلب", "سُجّل مستحقك."),
      ];
    case "rejected":
      return [p("customer", "تم رفض الطلب", "اعتذر المتجر عن تنفيذه.")];
    case "cancelled":
      return after.cancelledByRole === "customer"
        ? [p("merchants", "أُلغي الطلب", "ألغاه الزبون.")]
        : [
            p("customer", "أُلغي طلبك", "افتحه لمعرفة السبب."),
            p("merchants", "أُلغي الطلب", "ألغته الإدارة أو المتجر."),
          ];
    case "expired":
      return [
        p("customer", "انتهت مهلة الطلب", "يمكنك إرسال طلب جديد."),
        p("merchants", "انتهت مهلة طلب", "لم يكتمل الطلب."),
      ];
    case "delivery_failed":
      return [
        p("customer", "تعذّر التسليم", "سنتواصل معك."),
        p("admins", "تعذّر تسليم طلب", "راجع السبب."),
      ];
    default:
      return [];
  }
}
