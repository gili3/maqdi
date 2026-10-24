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

export const DEFAULT_SETTINGS = {
  deliveryFee: 4000,
  merchantReviewMinutes: 15,
  customerApprovalMinutes: 30,
  ordersEnabled: true,
};

// السودان UTC+2 (بدون توقيت صيفي)
export const LOCAL_OFFSET_MS = 2 * 3600 * 1000;
