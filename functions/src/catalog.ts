import { Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { normalizeSearch } from "./textUtil";
import { bool, callable, db, docId, int, loadActor, obj, oneOf, optStr, phone, str, writeAudit } from "./util";

// الفئات الأصلية تبقى مقبولة حتى لو لم تُنشأ كوثائق في categories
export const LEGACY_CATEGORIES = ["grocery", "pharmacy", "other"];

// ---------- المتاجر (المدير) ----------
export const adminUpsertStore = callable(async (req) => {
  const d = obj(req.data);
  const storeId = d.storeId ? docId(d.storeId, "storeId") : null;
  const data = {
    name: str(d.name, "name", 2, 80),
    category: docId(d.category, "category"),
    phone: phone(d.phone),
    address: optStr(d.address, "address", 200),
    status: oneOf(d.status, "status", ["active", "suspended"] as const),
    isOpen: bool(d.isOpen, "isOpen"),
  };
  // الصورة اختيارية: غيابها = إبقاء الحالية، null = إزالتها
  const imagePath = d.imagePath === undefined ? undefined : optStr(d.imagePath, "imagePath", 300);
  const result = await db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["admin"], tx, { admin: "stores" });
    const ref = storeId ? db.doc(`stores/${storeId}`) : db.collection("stores").doc();
    const catSnap = LEGACY_CATEGORIES.includes(data.category) ? null : await tx.get(db.doc(`categories/${data.category}`));
    const snap = await tx.get(ref);
    if (storeId && !snap.exists) throw new HttpsError("not-found", "store_not_found");
    if (catSnap && !catSnap.exists) throw new HttpsError("invalid-argument", "invalid_category");
    if (imagePath && !imagePath.startsWith(`stores/${ref.id}/`)) throw new HttpsError("invalid-argument", "invalid_imagePath");
    const now = Timestamp.now();
    const renamed = snap.exists && snap.get("name") !== data.name;
    const full = imagePath === undefined ? data : { ...data, imagePath };
    if (snap.exists) tx.update(ref, { ...full, updatedAt: now });
    else tx.set(ref, { ...full, imagePath: imagePath ?? null, createdAt: now, updatedAt: now });
    writeAudit(tx, actor, snap.exists ? "update_store" : "create_store", "store", ref.id, { ...full });
    return { storeId: ref.id, renamed };
  });
  // تحديث اسم المتجر المنسوخ في منتجاته (حتى 450 منتجًا)
  if (result.renamed) {
    const prods = await db.collection("products").where("storeId", "==", result.storeId).limit(450).get();
    const batch = db.batch();
    prods.docs.forEach((p) => batch.update(p.ref, { storeName: data.name }));
    if (!prods.empty) await batch.commit();
  }
  return { storeId: result.storeId };
});

// ---------- المنتجات (التاجر لمتجره، أو المدير) ----------
export const upsertProduct = callable(async (req) => {
  const d = obj(req.data);
  const productId = d.productId ? docId(d.productId, "productId") : null;
  const data = {
    name: str(d.name, "name", 2, 100),
    description: optStr(d.description, "description", 500),
    category: optStr(d.category, "category", 60),
    price: int(d.price, "price", 0, 10_000_000),
    // null = كمية غير مُتتبَّعة
    stockQty: d.stockQty === null || d.stockQty === undefined ? null : int(d.stockQty, "stockQty", 0, 1_000_000),
    isAvailable: bool(d.isAvailable, "isAvailable"),
    imagePath: optStr(d.imagePath, "imagePath", 300),
  };

  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["merchant", "admin"], tx, { merchant: "products", admin: "stores" });
    const storeId = actor.role === "merchant" ? actor.storeId : docId(d.storeId, "storeId");
    if (!storeId) throw new HttpsError("failed-precondition", "no_store");
    const storeSnap = await tx.get(db.doc(`stores/${storeId}`));
    if (!storeSnap.exists || (actor.role === "merchant" && storeSnap.get("status") !== "active"))
      throw new HttpsError("failed-precondition", "store_unavailable");
    if (data.imagePath && !data.imagePath.startsWith(`products/${storeId}/`)) throw new HttpsError("invalid-argument", "invalid_imagePath");

    const ref = productId ? db.doc(`products/${productId}`) : db.collection("products").doc();
    const snap = await tx.get(ref);
    if (productId && (!snap.exists || snap.get("storeId") !== storeId)) throw new HttpsError("not-found", "product_not_found");
    const now = Timestamp.now();
    // حقول البحث: الاسم المطبَّع واسم المتجر (يُنسخ ليظهر في نتائج البحث العام)
    const searchFields = { nameLower: normalizeSearch(data.name), storeName: storeSnap.get("name") as string };
    if (snap.exists) tx.update(ref, { ...data, ...searchFields, updatedAt: now });
    else tx.set(ref, { ...data, ...searchFields, storeId, createdAt: now, updatedAt: now });
    writeAudit(tx, actor, snap.exists ? "update_product" : "create_product", "product", ref.id, {
      storeId, oldPrice: snap.exists ? snap.get("price") : null, newPrice: data.price,
    });
    return { productId: ref.id };
  });
});

export const setStoreOpen = callable(async (req) => {
  const d = obj(req.data);
  const isOpen = bool(d.isOpen, "isOpen");
  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["merchant", "admin"], tx, { merchant: "open_close", admin: "stores" });
    const storeId = actor.role === "merchant" ? actor.storeId : docId(d.storeId, "storeId");
    if (!storeId) throw new HttpsError("failed-precondition", "no_store");
    const ref = db.doc(`stores/${storeId}`);
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "store_not_found");
    if (actor.role === "merchant" && snap.get("status") !== "active") throw new HttpsError("permission-denied", "store_suspended");
    tx.update(ref, { isOpen, updatedAt: Timestamp.now() });
    writeAudit(tx, actor, "set_store_open", "store", storeId, { isOpen });
    return { ok: true };
  });
});
