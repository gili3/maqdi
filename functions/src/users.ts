import { Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { validatePermissionList } from "./permissions";
import { callable, db, email, int, bool, obj, oneOf, optStr, str, docId, loadActor, writeAudit } from "./util";

// تُستدعى بعد أول تسجيل دخول بـ Google لإنشاء الملف الشخصي.
// الدور يُحدَّد من الخادم فقط: دعوة من المدير أو زبون افتراضيًا.
export const ensureProfile = callable(async (req) => {
  if (!req.auth) throw new HttpsError("unauthenticated", "login_required");
  const token = req.auth.token as Record<string, unknown>;
  if (token.email_verified !== true || typeof token.email !== "string") throw new HttpsError("permission-denied", "email_not_verified");
  const mail = token.email.toLowerCase();
  const d = obj(req.data);
  const name = optStr(d.name, "name", 80) ?? (typeof token.name === "string" ? token.name.slice(0, 80) : "");

  return db.runTransaction(async (tx) => {
    const uref = db.doc(`users/${req.auth!.uid}`);
    const inviteRef = db.doc(`invites/${mail}`);
    const [u, inv] = await tx.getAll(uref, inviteRef);
    if (u.exists) return { role: u.get("role"), status: u.get("status"), storeId: u.get("storeId") ?? null };

    let role = "customer";
    let storeId: string | null = null;
    if (inv.exists && inv.get("used") !== true) {
      role = inv.get("role");
      storeId = inv.get("storeId") ?? null;
    }
    const now = Timestamp.now();
    tx.create(uref, { email: mail, name, phone: null, role, storeId, status: "active", isTestAccount: false, savedAddress: null, fcmTokens: [], createdAt: now, updatedAt: now });
    if (inv.exists && role !== "customer") tx.update(inviteRef, { used: true, usedBy: req.auth!.uid, usedAt: now });
    return { role, status: "active", storeId };
  });
});

// ---------- إدارة المستخدمين (المدير) ----------
export const adminInviteUser = callable(async (req) => {
  const d = obj(req.data);
  const mail = email(d.email);
  const role = oneOf(d.role, "role", ["merchant", "courier", "admin"] as const);
  const storeId = role === "merchant" ? docId(d.storeId, "storeId") : null;

  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["admin"], tx, { admin: "users" });
    if (storeId) {
      const s = await tx.get(db.doc(`stores/${storeId}`));
      if (!s.exists) throw new HttpsError("not-found", "store_not_found");
    }
    const existing = await tx.get(db.collection("users").where("email", "==", mail).limit(1));
    const now = Timestamp.now();
    if (!existing.empty) {
      // تغيير الدور يعيد الصلاحيات للافتراضي (كل صلاحيات الدور الجديد)
      const sameRole = existing.docs[0].get("role") === role;
      tx.update(existing.docs[0].ref, { role, storeId, ...(sameRole ? {} : { permissions: null }), updatedAt: now });
    } else {
      tx.set(db.doc(`invites/${mail}`), { role, storeId, used: false, createdBy: actor.uid, createdAt: now });
    }
    writeAudit(tx, actor, "invite_user", "user", mail, { role, storeId, existingUser: !existing.empty });
    return { ok: true, appliedToExistingUser: !existing.empty };
  });
});

export const adminSetUserStatus = callable(async (req) => {
  const d = obj(req.data);
  const uid = str(d.uid, "uid", 5, 128);
  const status = d.status === undefined ? null : oneOf(d.status, "status", ["active", "suspended"] as const);
  const isTest = d.isTestAccount === undefined ? null : bool(d.isTestAccount, "isTestAccount");
  if (status === null && isTest === null) throw new HttpsError("invalid-argument", "nothing_to_change");

  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["admin"], tx, { admin: "users" });
    if (uid === actor.uid && status === "suspended") throw new HttpsError("failed-precondition", "cannot_suspend_self");
    const ref = db.doc(`users/${uid}`);
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "user_not_found");
    const patch: Record<string, unknown> = { updatedAt: Timestamp.now() };
    if (status !== null) patch.status = status;
    if (isTest !== null) patch.isTestAccount = isTest;
    tx.update(ref, patch);
    writeAudit(tx, actor, "set_user_status", "user", uid, { status, isTest });
    return { ok: true };
  });
});

export const adminUpdateSettings = callable(async (req) => {
  const d = obj(req.data);
  const patch: Record<string, unknown> = {
    deliveryFee: int(d.deliveryFee, "deliveryFee", 0, 1_000_000),
    merchantReviewMinutes: int(d.merchantReviewMinutes, "merchantReviewMinutes", 1, 1440),
    customerApprovalMinutes: int(d.customerApprovalMinutes, "customerApprovalMinutes", 1, 1440),
    ordersEnabled: bool(d.ordersEnabled, "ordersEnabled"),
    updatedAt: Timestamp.now(),
  };
  if (d.maxOpenOrders !== undefined) patch.maxOpenOrders = int(d.maxOpenOrders, "maxOpenOrders", 1, 20);
  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["admin"], tx, { admin: "settings" });
    const ref = db.doc("settings/app");
    const old = await tx.get(ref);
    tx.set(ref, patch, { merge: true });
    writeAudit(tx, actor, "update_settings", "settings", "app", { old: old.data() ?? null, new: patch });
    return { ok: true };
  });
});

// ---------- تخصيص صلاحيات مستخدم (المدير الذي يملك صلاحية "permissions") ----------
// permissions = null يعيد الصلاحيات الافتراضية (كل صلاحيات الدور).
export const adminSetUserPermissions = callable(async (req) => {
  const d = obj(req.data);
  const uid = str(d.uid, "uid", 5, 128);
  const raw = d.permissions;
  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["admin"], tx, { admin: "permissions" });
    const ref = db.doc(`users/${uid}`);
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "user_not_found");
    const role = String(snap.get("role"));
    if (role === "customer") throw new HttpsError("failed-precondition", "role_has_no_permissions");
    let list: string[] | null = null;
    if (raw !== null) {
      list = validatePermissionList(role, raw);
      if (list === null) throw new HttpsError("invalid-argument", "invalid_permissions");
    }
    // منع المدير من سحب صلاحية إدارة الصلاحيات من نفسه (يقفل نفسه خارج اللوحة)
    if (uid === actor.uid && list !== null && !list.includes("permissions")) throw new HttpsError("failed-precondition", "cannot_remove_own_permissions");
    tx.update(ref, { permissions: list, updatedAt: Timestamp.now() });
    writeAudit(tx, actor, "set_user_permissions", "user", uid, { role, permissions: list });
    return { ok: true };
  });
});
