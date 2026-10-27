import { ActorRole, Status } from "./config";

interface Rule {
  from: Status;
  to: Status;
  roles: ActorRole[];
}

// الجدول الوحيد المسموح به لتغيير الحالات. أي انتقال غير مذكور هنا مرفوض.
export const TRANSITIONS: Rule[] = [
  // مراجعة التاجر
  { from: "pending_merchant", to: "awaiting_customer", roles: ["merchant", "admin"] },
  // قبول الطلب كما هو (نفس المنتجات والكميات والأسعار التي رآها الزبون): يدخل التجهيز مباشرة
  // ولا يمرّ إلا عبر proposeOrder؛ حارس changeOrderStatus يرفضه لغياب لقطة الموافقة
  { from: "pending_merchant", to: "preparing", roles: ["merchant", "admin"] },
  // طلب بالتحويل قبله التاجر كما هو: يصبح «تمت الموافقة» مباشرة بانتظار الدفع (لا يمرّ إلا عبر proposeOrder)
  { from: "pending_merchant", to: "confirmed", roles: ["merchant", "admin"] },
  { from: "pending_merchant", to: "rejected", roles: ["merchant", "admin"] },
  { from: "pending_merchant", to: "cancelled", roles: ["customer", "admin"] },
  { from: "pending_merchant", to: "expired", roles: ["system"] },

  // موافقة الزبون
  { from: "awaiting_customer", to: "confirmed", roles: ["customer"] },
  { from: "awaiting_customer", to: "cancelled", roles: ["customer", "admin"] },
  { from: "awaiting_customer", to: "expired", roles: ["system"] },

  // بعد الموافقة
  { from: "confirmed", to: "preparing", roles: ["merchant", "admin"] },
  { from: "confirmed", to: "awaiting_customer", roles: ["merchant", "admin"] },
  { from: "confirmed", to: "cancelled", roles: ["customer", "merchant", "admin"] },
  // انتهت مهلة دفع الزبون بالتحويل
  { from: "confirmed", to: "expired", roles: ["system"] },

  { from: "preparing", to: "ready_for_pickup", roles: ["merchant", "admin"] },
  { from: "preparing", to: "awaiting_customer", roles: ["merchant", "admin"] },
  // الزبون يلغي في التجهيز فقط خلال مهلة قصيرة (يفرضها الحارس في orderGuards)
  { from: "preparing", to: "cancelled", roles: ["customer", "merchant", "admin"] },

  // التوصيل
  { from: "ready_for_pickup", to: "out_for_delivery", roles: ["courier", "admin"] },
  { from: "ready_for_pickup", to: "cancelled", roles: ["admin"] },

  { from: "out_for_delivery", to: "delivered", roles: ["courier", "admin"] },
  { from: "out_for_delivery", to: "delivery_failed", roles: ["courier", "admin"] },
];

export function canTransition(role: ActorRole, from: Status, to: Status): boolean {
  return TRANSITIONS.some((r) => r.from === from && r.to === to && r.roles.includes(role));
}
