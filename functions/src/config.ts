export const REGION = "europe-west3";

export const STATUSES = [
  "pending_merchant",
  "awaiting_customer",
  "confirmed",
  "preparing",
  "ready_for_pickup",
  "out_for_delivery",
  "delivered",
  "rejected",
  "cancelled",
  "expired",
  "delivery_failed",
] as const;
export type Status = (typeof STATUSES)[number];

export type Role = "customer" | "merchant" | "admin" | "courier";
export type ActorRole = Role | "system";

export const TERMINAL: Status[] = [
  "delivered",
  "rejected",
  "cancelled",
  "expired",
  "delivery_failed",
];

export const OPEN_STATUSES: Status[] = STATUSES.filter((s) => !TERMINAL.includes(s));

export const DEFAULT_SETTINGS = {
  deliveryFee: 4000,
  merchantReviewMinutes: 15,
  customerApprovalMinutes: 30,
  ordersEnabled: true,
  maxOpenOrders: 3, // أقصى عدد طلبات مفتوحة لكل زبون
  courierCommission: 0, // عمولة المنصة من المندوب (جنيه) على كل توصيل؛ تحددها الإدارة من الإعدادات
  courierPickupMinutes: 10, // بعد «جاهز للاستلام» وبلا مندوب: تنبيه الإدارة بعد هذه المدة (0 = بلا تنبيه)
  customerCancelGraceMinutes: 3, // مهلة إلغاء الزبون بعد أن يقبل التاجر ويبدأ التجهيز (0 = لا إلغاء)
};

// السودان UTC+2 (بدون توقيت صيفي)
export const LOCAL_OFFSET_MS = 2 * 3600 * 1000;
