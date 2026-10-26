import { getApps, initializeApp } from "firebase-admin/app";
import { FieldValue, Transaction, getFirestore } from "firebase-admin/firestore";
import * as logger from "firebase-functions/logger";
import { CallableRequest, HttpsError, onCall } from "firebase-functions/v2/https";
import { DEFAULT_SETTINGS, REGION, Role } from "./config";
import { effectivePermissions } from "./permissions";

if (getApps().length === 0) initializeApp();
export const db = getFirestore();

// غلاف موحد لكل الدوال المستدعاة من التطبيقات
export function callable<R>(handler: (req: CallableRequest<unknown>) => Promise<R>) {
  return onCall({ region: REGION, maxInstances: 10 }, handler);
}

export interface Actor {
  uid: string;
  role: Role;
  name: string;
  storeId: string | null;
  permissions: string[];
}

// يتحقق من الهوية والدور وأن الحساب غير موقوف. يقرأ من الخادم دائمًا.
export async function loadActor(
  req: CallableRequest<unknown>,
  roles?: Role[],
  tx?: Transaction,
  need?: Partial<Record<Role, string>>
): Promise<Actor> {
  if (!req.auth) throw new HttpsError("unauthenticated", "login_required");
  const ref = db.doc(`users/${req.auth.uid}`);
  const snap = tx ? await tx.get(ref) : await ref.get();
  if (!snap.exists) throw new HttpsError("permission-denied", "profile_missing");
  const u = snap.data() as Record<string, unknown>;
  if (u.status !== "active") throw new HttpsError("permission-denied", "account_suspended");
  const role = u.role as Role;
  if (roles && !roles.includes(role)) throw new HttpsError("permission-denied", "role_not_allowed");
  const permissions = effectivePermissions(role, u.permissions);
  const required = need?.[role];
  if (required && !permissions.includes(required)) throw new HttpsError("permission-denied", "permission_denied");
  return {
    uid: req.auth.uid,
    role,
    name: String(u.name ?? ""),
    storeId: (u.storeId as string | undefined) ?? null,
    permissions,
  };
}

export async function readSettings(tx?: Transaction) {
  const ref = db.doc("settings/app");
  const snap = tx ? await tx.get(ref) : await ref.get();
  return { ...DEFAULT_SETTINGS, ...((snap.data() as object | undefined) ?? {}) };
}

// ---------- التحقق من المدخلات ----------
const bad = (field: string) => new HttpsError("invalid-argument", `invalid_${field}`);

export function obj(v: unknown): Record<string, unknown> {
  return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : {};
}

export function str(v: unknown, field: string, min = 1, max = 500): string {
  if (typeof v !== "string") throw bad(field);
  const s = v.trim();
  if (s.length < min || s.length > max) throw bad(field);
  return s;
}

export function optStr(v: unknown, field: string, max = 500): string | null {
  if (v === undefined || v === null || v === "") return null;
  return str(v, field, 1, max);
}

export function int(v: unknown, field: string, min: number, max: number): number {
  if (typeof v !== "number" || !Number.isInteger(v) || v < min || v > max) throw bad(field);
  return v;
}

export function bool(v: unknown, field: string): boolean {
  if (typeof v !== "boolean") throw bad(field);
  return v;
}

export function oneOf<T extends string>(v: unknown, field: string, list: readonly T[]): T {
  if (typeof v !== "string" || !list.includes(v as T)) throw bad(field);
  return v as T;
}

export function arr(v: unknown, field: string, min: number, max: number): unknown[] {
  if (!Array.isArray(v) || v.length < min || v.length > max) throw bad(field);
  return v;
}

export function docId(v: unknown, field: string): string {
  const s = str(v, field, 1, 128);
  if (!/^[A-Za-z0-9_-]+$/.test(s)) throw bad(field);
  return s;
}

// معرّف الإرسال الذي يولّده التطبيق لمنع تكرار الطلب
export function submissionKey(v: unknown, field: string): string {
  const s = str(v, field, 16, 64);
  if (!/^[A-Za-z0-9_-]+$/.test(s)) throw bad(field);
  return s;
}

export function phone(v: unknown): string {
  const s = str(v, "phone", 6, 20);
  if (!/^[0-9+\- ]+$/.test(s)) throw bad("phone");
  return s;
}

/** رقم اختياري: الفارغ يعني بلا رقم، وغير الفارغ يجب أن يكون صحيحًا */
export function optPhone(v: unknown): string {
  if (v === undefined || v === null || (typeof v === "string" && v.trim() === "")) return "";
  return phone(v);
}

export function email(v: unknown): string {
  const s = str(v, "email", 5, 200).toLowerCase();
  if (!/^[^\s@/]+@[^\s@/]+\.[^\s@/]+$/.test(s)) throw bad("email");
  return s;
}

// ---------- سجل التدقيق (بدون أسرار) ----------
export function writeAudit(
  tx: Transaction,
  actor: { uid: string; role: string },
  action: string,
  targetType: string,
  targetId: string,
  details: Record<string, unknown> = {}
) {
  tx.set(db.collection("audit_logs").doc(), {
    actorUid: actor.uid,
    actorRole: actor.role,
    action,
    targetType,
    targetId,
    details,
    at: FieldValue.serverTimestamp(),
  });
}

/**
 * ينفّذ جزءًا غير حرج من استجابة. إن فشل (مثل فهرس لم يجهز بعد) يُسجَّل الخطأ بنصه الكامل
 * (يحوي رابط إنشاء الفهرس) ويرجع البديل، فلا تفشل الشاشة كلها بسبب رقم واحد.
 */
export async function safely<T>(label: string, fn: () => Promise<T>, fallback: T): Promise<{ value: T; ok: boolean }> {
  try {
    return { value: await fn(), ok: true };
  } catch (e) {
    logger.error(`partial_failure:${label}`, e);
    return { value: fallback, ok: false };
  }
}
