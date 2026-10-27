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
      if (after.paymentMethod === "bank_transfer" && after.paymentStatus === "awaiting_payment")
        return [
          p("customer", "حوّل المبلغ لبدء التجهيز", "حوّل الإجمالي وأرفق صورة الإيصال."),
          p("merchants", "تمت الموافقة", "بانتظار تحويل الزبون قبل التجهيز."),
        ];
      return [p("merchants", "تمت موافقة الزبون", "يمكنك بدء التجهيز.")];
    case "preparing":
      return [
        p("customer", "طلبك قيد التجهيز", "بدأ المتجر تجهيزه."),
        p("couriers", "طلب قادم", "سيكون جاهزًا قريبًا."),
      ];
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

const money = (n: unknown) => `${Number(n ?? 0).toLocaleString("en-US")} ج.س`;

/** إشعارات تغيّر حالة الدفع بالتحويل (تُستدعى حين يتغير paymentStatus) */
export function paymentPlanFor(before: Record<string, unknown> | undefined, after: Record<string, unknown>): Plan[] {
  const p = (to: Target, title: string, body: string): Plan => ({ to, title, body });
  if (after.paymentMethod !== "bank_transfer" || before?.paymentStatus === after.paymentStatus) return [];
  switch (after.paymentStatus) {
    case "proof_submitted":
      return [p("admins", "إثبات تحويل جديد", "راجع الإيصال وأكّد استلام المبلغ.")];
    case "verified": {
      const plans = [p("customer", "تم تأكيد دفعك", "شكرًا، سيبدأ المتجر تجهيز طلبك.")];
      if (after.status === "confirmed" || after.status === "preparing") plans.push(p("merchants", "تم تأكيد الدفع", "يمكنك بدء التجهيز."));
      return plans;
    }
    case "awaiting_payment":
      return before?.paymentStatus === "proof_submitted"
        ? [p("customer", "لم يُقبل إثبات التحويل", `${after.paymentRejectReason ?? "أعد رفع إيصال واضح."}`)]
        : [];
    default:
      return [];
  }
}

/** إشعارات طلبات الاسترداد: عند الإنشاء وعند القرار */
export function refundPlanFor(before: Record<string, unknown> | undefined, after: Record<string, unknown>): Plan[] {
  const p = (to: Target, title: string, body: string): Plan => ({ to, title, body });
  if (!before) {
    return after.source === "auto"
      ? [p("admins", "استرداد مستحق لزبون", `${money(after.amount)} — ${after.reason ?? ""}`),
         p("customer", "سيُعاد إليك مبلغك", "سجّلنا طلب استرداد لطلبك وسنحوّله إليك.")]
      : [p("admins", "طلب استرداد جديد", `${money(after.amount)} — ${after.reason ?? ""}`)];
  }
  if (before.status === after.status) return [];
  if (after.status === "refunded") return [p("customer", "تم رد مبلغك", `حوّلنا ${money(after.approvedAmount)} إلى حسابك.`)];
  if (after.status === "rejected") return [p("customer", "رُفض طلب الاسترداد", `${after.adminNote ?? ""}`)];
  return [];
}
