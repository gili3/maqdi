export type Target = "customer" | "merchants" | "couriers" | "admins";
export interface Plan { to: Target; title: string; body: string }

/** من يُخطَر عند وصول الطلب إلى حالة جديدة (دالة نقية قابلة للاختبار) */
export function planFor(after: Record<string, unknown>): Plan[] {
  const p = (to: Target, title: string, body: string): Plan => ({ to, title, body });
  switch (after.status) {
    case "pending_merchant":
      return [p("merchants", "طلب جديد", "وصلك طلب جديد، راجعه الآن.")];
    case "awaiting_customer":
      return [p("customer", "راجع إجمالي طلبك", "راجع التاجر طلبك. وافق على الإجمالي ليبدأ التجهيز.")];
    case "confirmed":
      return [p("merchants", "تمت موافقة الزبون", "وافق الزبون على الإجمالي. يمكنك بدء التجهيز.")];
    case "preparing":
      return [p("customer", "طلبك قيد التجهيز", "بدأ المتجر تجهيز طلبك.")];
    case "ready_for_pickup":
      return [
        p("couriers", "طلب جاهز للاستلام", "يوجد طلب جاهز في المتجر."),
        p("customer", "طلبك جاهز", "طلبك جاهز وبانتظار المندوب."),
      ];
    case "out_for_delivery":
      return [p("customer", "طلبك في الطريق", "بدأ التوصيل.")];
    case "delivered":
      return [
        p("customer", "تم التسليم", "شكرًا لاستخدامك مقضي."),
        p("merchants", "تم تسليم الطلب", "سُلّم الطلب للزبون وسُجّل مستحقك."),
      ];
    case "rejected":
      return [p("customer", "تم رفض الطلب", "اعتذر المتجر عن تنفيذ طلبك.")];
    case "cancelled":
      return after.cancelledByRole === "customer"
        ? [p("merchants", "أُلغي الطلب", "ألغى الزبون الطلب.")]
        : [
            p("customer", "أُلغي طلبك", "أُلغي طلبك. افتح الطلب لمعرفة السبب."),
            p("merchants", "أُلغي الطلب", "أُلغي الطلب من الإدارة أو المتجر."),
          ];
    case "expired":
      return [
        p("customer", "انتهت مهلة الطلب", "انتهت المهلة، يمكنك إرسال طلب جديد."),
        p("merchants", "انتهت مهلة طلب", "انتهت مهلة الطلب دون إتمامه."),
      ];
    case "delivery_failed":
      return [
        p("customer", "تعذّر التسليم", "تعذّر تسليم طلبك. سنتواصل معك."),
        p("admins", "تعذّر تسليم طلب", "أعد المندوب الطلب. راجع السبب."),
      ];
    default:
      return [];
  }
}
