import { Role } from "./config";

// كتالوج الصلاحيات لكل دور. غياب الحقل `permissions` في ملف المستخدم = كل صلاحيات دوره (سلوك قديم محفوظ).
export const PERMISSION_CATALOG: Record<string, readonly string[]> = {
  admin: ["orders", "deliveries", "stores", "categories", "users", "permissions", "grant_admin", "finance", "expenses", "settings", "reports", "audit"],
  merchant: ["orders", "products", "open_close", "reports", "balance"],
  courier: ["deliveries", "reports"],
};

export function allPermissions(role: string): string[] {
  return [...(PERMISSION_CATALOG[role] ?? [])];
}

/** الصلاحيات الفعلية: null/غير موجود = الكل؛ وإلا تُقاطَع مع كتالوج الدور (تُهمل أي قيمة غريبة). */
export function effectivePermissions(role: string, stored: unknown): string[] {
  const all = allPermissions(role);
  if (!Array.isArray(stored)) return all;
  return all.filter((p) => stored.includes(p));
}

export function hasPermission(role: string, stored: unknown, perm: string): boolean {
  return effectivePermissions(role, stored).includes(perm);
}

/** الصلاحية المطلوبة لتغيير حالة طلب حسب دور المنفِّذ والحالة الهدف (الزبون بلا صلاحيات). */
export function permissionForStatusChange(role: Role | string, to: string): string | null {
  if (role === "merchant") return "orders";
  if (role === "courier") return "deliveries";
  if (role === "admin") return ["out_for_delivery", "delivered", "delivery_failed"].includes(to) ? "deliveries" : "orders";
  return null;
}

/** يتحقق من قائمة صلاحيات مرسلة لدور معيّن: كلها ضمن الكتالوج، بلا تكرار. */
export function validatePermissionList(role: string, list: unknown): string[] | null {
  if (!Array.isArray(list)) return null;
  const all = allPermissions(role);
  const out: string[] = [];
  for (const p of list) {
    if (typeof p !== "string" || !all.includes(p)) return null;
    if (!out.includes(p)) out.push(p);
  }
  return out;
}

/** هل كل عناصر `list` موجودة في `allowed`؟ (يمنع منح صلاحية لا يملكها المانح) */
export function isSubset(list: readonly string[], allowed: readonly string[]): boolean {
  return list.every((p) => allowed.includes(p));
}
