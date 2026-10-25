import { Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { LEGACY_CATEGORIES } from "./catalog";
import { bool, callable, db, docId, int, loadActor, obj, str, writeAudit } from "./util";

// فئات المتاجر القابلة للتخصيص من الإدارة. categoryId اختياري عند الإنشاء (لإنشاء الفئات الأصلية بنفس معرّفاتها).
export const adminUpsertCategory = callable(async (req) => {
  const d = obj(req.data);
  const categoryId = d.categoryId ? docId(d.categoryId, "categoryId") : null;
  const data = {
    name: str(d.name, "name", 2, 40),
    order: int(d.order, "order", 0, 1000),
    isActive: bool(d.isActive, "isActive"),
  };
  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["admin"], tx, { admin: "categories" });
    const ref = categoryId ? db.doc(`categories/${categoryId}`) : db.collection("categories").doc();
    const snap = await tx.get(ref);
    const now = Timestamp.now();
    if (snap.exists) tx.update(ref, { ...data, updatedAt: now });
    else tx.set(ref, { ...data, createdAt: now, updatedAt: now });
    writeAudit(tx, actor, snap.exists ? "update_category" : "create_category", "category", ref.id, data);
    return { categoryId: ref.id };
  });
});

export const adminDeleteCategory = callable(async (req) => {
  const categoryId = docId(obj(req.data).categoryId, "categoryId");
  return db.runTransaction(async (tx) => {
    const actor = await loadActor(req, ["admin"], tx, { admin: "categories" });
    const used = await tx.get(db.collection("stores").where("category", "==", categoryId).limit(1));
    if (!used.empty) throw new HttpsError("failed-precondition", "category_in_use");
    const ref = db.doc(`categories/${categoryId}`);
    // الفئات الأصلية تُخفى بدل الحذف حتى لا تنكسر متاجر قديمة
    if (LEGACY_CATEGORIES.includes(categoryId)) tx.set(ref, { isActive: false, updatedAt: Timestamp.now() }, { merge: true });
    else tx.delete(ref);
    writeAudit(tx, actor, "delete_category", "category", categoryId, {});
    return { ok: true };
  });
});
