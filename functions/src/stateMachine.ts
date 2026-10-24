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

  { from: "preparing", to: "ready_for_pickup", roles: ["merchant", "admin"] },
  { from: "preparing", to: "awaiting_customer", roles: ["merchant", "admin"] },
  { from: "preparing", to: "cancelled", roles: ["merchant", "admin"] },

  // التوصيل
  { from: "ready_for_pickup", to: "out_for_delivery", roles: ["courier", "admin"] },
  { from: "ready_for_pickup", to: "cancelled", roles: ["admin"] },

  { from: "out_for_delivery", to: "delivered", roles: ["courier", "admin"] },
  { from: "out_for_delivery", to: "delivery_failed", roles: ["courier", "admin"] },
];

export function canTransition(role: ActorRole, from: Status, to: Status): boolean {
  return TRANSITIONS.some((r) => r.from === from && r.to === to && r.roles.includes(role));
}
