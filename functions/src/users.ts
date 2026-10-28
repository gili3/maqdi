import { Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { allPermissions, effectivePermissions, isSubset, validatePermissionList } from "./permissions";
import { phoneFromToken } from "./authLogic";
import { callable, db, email, int, bool, obj, oneOf, optStr, str, docId, loadActor, writeAudit } from "./util";

// تُستدعى بعد أول تسجيل دخول لإنشاء الملف الشخصي (هاتف برمز SMS، أو Google).
// الدور يُحدَّد من الخادم فقط: كل حساب جديد زبون؛ يرفعه المدير لاحقًا من شاشة المستخدمين.
export const ensureProfile = callable(async (req) => {
  if (!req.auth) throw new HttpsError("unauthenticated", "login_required");
  const token = req.auth.token as Record<string, unknown>;
  const fb = token.firebase as { sign_in_provider?: string } | undefined;
  let mail = "";
  let phone: string | null = null;
  if (fb?.sign_in_provider === "phone") {
    // الرقم هنا موثَّق برمز SMS من Firebase. الدول المقبولة يحددها Firebase (SMS region policy)
    phone = phoneFromToken(token.phone_number);
    if (!phone) throw new HttpsError("permission-denied", "phone_invalid");
  } else {
    // غير الهاتف: لا يُقبل إلا بريد موثَّق (Google). لا حسابات بريد/كلمة مرور بلا توثيق.
    if (typeof token.email !== "string" || token.email_verified !== true) throw new HttpsError("permission-denied", "email_not_verified");
    mail = token.email.toLowerCase();
  }
  const d = obj(req.data);
  const name = optStr(d.name, "name", 80) ?? (typeof token.name === "string" ? token.name.slice(0, 80) : "");

  return db.runTransaction(async (tx) => {
    const uref = db.doc(`users/${req.auth!.uid}`);
    const u = await tx.get(uref);
    if (u.exists) return { role: u.get("role"), status: u.get("status"), storeId: u.get("storeId") ?? null };
    const now = Timestamp.now();
    tx.create(uref, { email: mail, name, phone, role: "customer", storeId: null, permissions: null, status: "active", isTestAccount: false, savedAddress: null, fcmTokens: [], createdAt: now, updatedAt: now });
    return { role: "customer", status: "active", storeId: null };
  });
});

// ---------- إدارة المستخدمين (المدير) ----------
// تعيين دور تاجر أو موصّل لمستخدم موجود (سجّل دخوله مرة على الأقل). لا دعوات بالبريد.
// صلاحية الأدمن لا تُمنح من هنا: انظر adminGrantAdmin.
// permissions: غير مُرسَل = يُحفظ للحساب بنفس الدور ويُعاد للافتراضي عند تغيير الدور،
//              null = كل صلاحيات الدور، قائمة = صلاحيات محدّدة.
export const adminSetUserRole = callable(async (req) => {
  const d = obj(req.data);
  const uid = str(d.uid, "uid", 5, 128);
  const role = oneOf(d.role, "role", ["merchant", "courier"] as const);
  const storeId = role === "merchant" ? docId(d.storeId, "storeId") : null;
  const hasPerms = d.permissions !== undefined;
  let perms: string[] | null = null;
  if (hasPerms && d.permissions !== null) {
    perms = validatePermissionList(role, d.permissions);
    if (perms === null) throw new HttpsError("invalid-argument", "invalid_permissions");
  }

  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["admin"], tx, { admin: "users" });
    if (hasPerms && !actor.permissions.includes("permissions")) throw new HttpsError("permission-denied", "permission_denied");
    if (storeId) {
      const s = await tx.get(db.doc(`stores/${storeId}`));
      if (!s.exists) throw new HttpsError("not-found", "store_not_found");
    }
    const ref = db.doc(`users/${uid}`);
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "user_not_found");
    const oldRole = String(snap.get("role"));
    // لا يُخفَّض أدمن هنا: يُسحب منه الأدمن من شاشة المستخدمين عمدًا
    if (oldRole === "admin") throw new HttpsError("failed-precondition", "user_is_admin");
    const patch: Record<string, unknown> = { role, storeId, updatedAt: Timestamp.now() };
    if (hasPerms) patch.permissions = perms;
    else if (oldRole !== role) patch.permissions = null;
    tx.update(ref, patch);
    writeAudit(tx, actor, "set_user_role", "user", uid, { oldRole, role, storeId, permissions: hasPerms ? perms : "unchanged" });
    return { ok: true };
  });
});

// ---------- منح/سحب صلاحية الأدمن ----------
// تتطلب صلاحية «grant_admin». لا يُمنح المستلم أكثر مما يملكه المانح نفسه (منع تصعيد الصلاحيات).
export const adminGrantAdmin = callable(async (req) => {
  const d = obj(req.data);
  const uid = str(d.uid, "uid", 5, 128);
  const list = validatePermissionList("admin", d.permissions);
  if (list === null || list.length === 0) throw new HttpsError("invalid-argument", "invalid_permissions");

  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["admin"], tx, { admin: "grant_admin" });
    if (uid === actor.uid) throw new HttpsError("failed-precondition", "cannot_grant_self");
    if (!isSubset(list, actor.permissions)) throw new HttpsError("permission-denied", "cannot_grant_beyond_own");
    const ref = db.doc(`users/${uid}`);
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "user_not_found");
    const oldRole = String(snap.get("role"));
    if (oldRole === "admin") throw new HttpsError("failed-precondition", "already_admin");
    // تُخزَّن القائمة صريحة دائمًا (لا null) حتى لا يرث الأدمن الجديد صلاحيات تُضاف مستقبلًا
    tx.update(ref, { role: "admin", storeId: null, permissions: list, updatedAt: Timestamp.now() });
    writeAudit(tx, actor, "grant_admin", "user", uid, { oldRole, permissions: list });
    return { ok: true };
  });
});

export const adminRevokeAdmin = callable(async (req) => {
  const d = obj(req.data);
  const uid = str(d.uid, "uid", 5, 128);
  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["admin"], tx, { admin: "grant_admin" });
    if (uid === actor.uid) throw new HttpsError("failed-precondition", "cannot_revoke_self");
    const ref = db.doc(`users/${uid}`);
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "user_not_found");
    if (snap.get("role") !== "admin") throw new HttpsError("failed-precondition", "not_admin");
    // لا يسحب أدمنٌ صلاحية أدمن يملك أكثر منه
    const targetPerms = effectivePermissions("admin", snap.get("permissions"));
    if (!isSubset(targetPerms, actor.permissions)) throw new HttpsError("permission-denied", "cannot_revoke_higher");
    tx.update(ref, { role: "customer", storeId: null, permissions: null, updatedAt: Timestamp.now() });
    writeAudit(tx, actor, "revoke_admin", "user", uid, { permissions: targetPerms });
    return { ok: true };
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
  if (d.courierCommission !== undefined) patch.courierCommission = int(d.courierCommission, "courierCommission", 0, 1_000_000);
  if (d.courierPickupMinutes !== undefined) patch.courierPickupMinutes = int(d.courierPickupMinutes, "courierPickupMinutes", 0, 240);
  if (d.customerCancelGraceMinutes !== undefined) patch.customerCancelGraceMinutes = int(d.customerCancelGraceMinutes, "customerCancelGraceMinutes", 0, 30);
  if (d.transferEnabled !== undefined) patch.transferEnabled = bool(d.transferEnabled, "transferEnabled");
  if (d.transferBankName !== undefined) patch.transferBankName = d.transferBankName === "" ? "" : str(d.transferBankName, "transferBankName", 1, 80);
  if (d.transferAccountNumber !== undefined) patch.transferAccountNumber = d.transferAccountNumber === "" ? "" : str(d.transferAccountNumber, "transferAccountNumber", 3, 60);
  if (d.transferAccountName !== undefined) patch.transferAccountName = d.transferAccountName === "" ? "" : str(d.transferAccountName, "transferAccountName", 2, 80);
  if (d.transferNote !== undefined) patch.transferNote = d.transferNote === "" ? "" : str(d.transferNote, "transferNote", 1, 300);
  if (d.paymentWindowMinutes !== undefined) patch.paymentWindowMinutes = int(d.paymentWindowMinutes, "paymentWindowMinutes", 5, 1440);
  if (d.refundWindowHours !== undefined) patch.refundWindowHours = int(d.refundWindowHours, "refundWindowHours", 1, 720);
  // لا يُفعَّل التحويل بلا بيانات حساب كاملة (الحالية أو الجديدة)
  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["admin"], tx, { admin: "settings" });
    const ref = db.doc("settings/app");
    const old = await tx.get(ref);
    const merged = { ...(old.data() ?? {}), ...patch } as Record<string, unknown>;
    if (merged.transferEnabled === true && (!merged.transferBankName || !merged.transferAccountNumber || !merged.transferAccountName))
      throw new HttpsError("failed-precondition", "transfer_details_required");
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
    // لا يمنح المدير أدمنًا (ولا نفسه) أكثر مما يملكه هو
    if (role === "admin") {
      const resulting = list ?? allPermissions("admin");
      if (!isSubset(resulting, actor.permissions)) throw new HttpsError("permission-denied", "cannot_grant_beyond_own");
    }
    // منع المدير من سحب صلاحية إدارة الصلاحيات من نفسه (يقفل نفسه خارج اللوحة)
    if (uid === actor.uid && list !== null && !list.includes("permissions")) throw new HttpsError("failed-precondition", "cannot_remove_own_permissions");
    tx.update(ref, { permissions: list, updatedAt: Timestamp.now() });
    writeAudit(tx, actor, "set_user_permissions", "user", uid, { role, permissions: list });
    return { ok: true };
  });
});
