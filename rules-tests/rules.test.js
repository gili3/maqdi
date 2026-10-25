// اختبارات قواعد Firestore (تعمل على محاكي Firebase داخل GitHub Actions)
const test = require("node:test");
const fs = require("node:fs");
const path = require("node:path");
const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const { doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc, collection, query, where } = require("firebase/firestore");

let env;

const USERS = {
  admin1: { role: "admin", status: "active", name: "مدير", fcmTokens: [] },
  m1: { role: "merchant", status: "active", storeId: "s1", name: "تاجر 1", fcmTokens: [] },
  m2: { role: "merchant", status: "active", storeId: "s2", name: "تاجر 2", fcmTokens: [] },
  c1: { role: "customer", status: "active", name: "زبون 1", phone: "0911", fcmTokens: [] },
  c2: { role: "customer", status: "active", name: "زبون 2", fcmTokens: [] },
  k1: { role: "courier", status: "active", name: "موصّل", fcmTokens: [] },
  sus: { role: "customer", status: "suspended", name: "موقوف", fcmTokens: [] },
};

test.before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-maqdi",
    firestore: { rules: fs.readFileSync(path.join(__dirname, "..", "firestore.rules"), "utf8") },
  });
});

test.after(async () => { await env.cleanup(); });

test.beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    for (const [uid, u] of Object.entries(USERS)) await setDoc(doc(db, "users", uid), u);
    await setDoc(doc(db, "stores/s1"), { name: "بقالة 1", status: "active", isOpen: true });
    await setDoc(doc(db, "products/p1"), { storeId: "s1", name: "سكر", price: 1000, isAvailable: true });
    await setDoc(doc(db, "orders/o1"), { customerId: "c1", storeId: "s1", status: "pending_merchant" });
    await setDoc(doc(db, "orders/o2"), { customerId: "c2", storeId: "s2", status: "ready_for_pickup" });
    await setDoc(doc(db, "financial_entries/e1"), { storeId: "s1", ledger: "store_payable", amount: 100 });
    await setDoc(doc(db, "financial_entries/e2"), { storeId: "s2", ledger: "store_payable", amount: 100 });
    await setDoc(doc(db, "financial_entries/e3"), { storeId: "s1", ledger: "cash", amount: 100 });
    await setDoc(doc(db, "audit_logs/a1"), { action: "x" });
    await setDoc(doc(db, "users/c1/notifications/n1"), { title: "t", body: "b", read: false });
    await setDoc(doc(db, "store_balances/s1"), { payable: 100, settled: 0 });
    await setDoc(doc(db, "store_balances/s2"), { payable: 100, settled: 0 });
  });
});

const as = (uid) => env.authenticatedContext(uid).firestore();

// 1) التاجر لا يرى طلبات متجر آخر
test("التاجر يرى طلبات متجره فقط", async () => {
  await assertSucceeds(getDoc(doc(as("m1"), "orders/o1")));
  await assertFails(getDoc(doc(as("m1"), "orders/o2")));
  await assertSucceeds(getDocs(query(collection(as("m1"), "orders"), where("storeId", "==", "s1"))));
  await assertFails(getDocs(query(collection(as("m1"), "orders"), where("storeId", "==", "s2"))));
});

// الزبون يصل إلى طلباته فقط
test("الزبون يرى طلباته فقط", async () => {
  await assertSucceeds(getDoc(doc(as("c1"), "orders/o1")));
  await assertFails(getDoc(doc(as("c1"), "orders/o2")));
});

// 4) الموصّل لا يرى الطلب قبل ready_for_pickup
test("الموصّل لا يرى الطلب قبل أن يصبح جاهزًا", async () => {
  await assertFails(getDoc(doc(as("k1"), "orders/o1")));
  await assertSucceeds(getDoc(doc(as("k1"), "orders/o2")));
});

// 7 و 11) الصلاحيات في قاعدة البيانات: لا كتابة مباشرة على الطلبات والمالية والأسعار
test("لا أحد يكتب الطلبات أو الأسعار أو القيود مباشرة", async () => {
  for (const uid of ["c1", "m1", "k1", "admin1"]) {
    await assertFails(setDoc(doc(as(uid), "orders/new1"), { customerId: uid, storeId: "s1", status: "confirmed" }));
    await assertFails(updateDoc(doc(as(uid), "orders/o1"), { status: "delivered" }));
    await assertFails(updateDoc(doc(as(uid), "products/p1"), { price: 1 }));
    await assertFails(setDoc(doc(as(uid), "financial_entries/x"), { amount: 1 }));
    await assertFails(setDoc(doc(as(uid), "settings/app"), { deliveryFee: 0 }));
    await assertFails(deleteDoc(doc(as(uid), "audit_logs/a1")));
  }
});

// 8) مستحقات المتجر: التاجر يرى قيود مستحقاته فقط، لا النقد ولا متجرًا آخر
test("التاجر يرى مستحقات متجره فقط", async () => {
  await assertSucceeds(getDoc(doc(as("m1"), "financial_entries/e1")));
  await assertFails(getDoc(doc(as("m1"), "financial_entries/e2")));
  await assertFails(getDoc(doc(as("m1"), "financial_entries/e3")));
  await assertSucceeds(getDoc(doc(as("m1"), "store_balances/s1")));
  await assertFails(getDoc(doc(as("m1"), "store_balances/s2")));
});

test("الزبون لا يرى المالية ولا سجل التدقيق", async () => {
  await assertFails(getDoc(doc(as("c1"), "financial_entries/e1")));
  await assertFails(getDoc(doc(as("c1"), "audit_logs/a1")));
  await assertFails(getDoc(doc(as("c1"), "store_balances/s1")));
});

test("المدير يقرأ المالية وسجل التدقيق", async () => {
  await assertSucceeds(getDoc(doc(as("admin1"), "financial_entries/e3")));
  await assertSucceeds(getDoc(doc(as("admin1"), "audit_logs/a1")));
});

// الملف الشخصي: يعدّل بياناته فقط ولا يرفع صلاحيته
test("المستخدم لا يغيّر دوره أو حالته", async () => {
  await assertSucceeds(updateDoc(doc(as("c1"), "users/c1"), { name: "اسم جديد" }));
  await assertFails(updateDoc(doc(as("c1"), "users/c1"), { role: "admin" }));
  await assertFails(updateDoc(doc(as("c1"), "users/c1"), { status: "active", storeId: "s1" }));
  await assertFails(updateDoc(doc(as("c1"), "users/c2"), { name: "x" }));
  await assertFails(setDoc(doc(as("c1"), "users/newuser"), { role: "admin", status: "active" }));
});

// 10) الحساب الموقوف لا ينفذ عمليات
test("الحساب الموقوف لا يقرأ ولا يعدّل", async () => {
  await assertFails(getDoc(doc(as("sus"), "stores/s1")));
  await assertFails(updateDoc(doc(as("sus"), "users/sus"), { name: "x" }));
});

test("غير المسجّل لا يقرأ شيئًا", async () => {
  const anon = env.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(anon, "stores/s1")));
  await assertFails(getDoc(doc(anon, "orders/o1")));
});

test("الزبون يقرأ المتاجر والمنتجات", async () => {
  await assertSucceeds(getDoc(doc(as("c1"), "stores/s1")));
  await assertSucceeds(getDoc(doc(as("c1"), "products/p1")));
});

test("الإشعارات: يقرأ إشعاراته ويعلّمها مقروءة فقط", async () => {
  await assertSucceeds(getDoc(doc(as("c1"), "users/c1/notifications/n1")));
  await assertFails(getDoc(doc(as("c2"), "users/c1/notifications/n1")));
  await assertSucceeds(updateDoc(doc(as("c1"), "users/c1/notifications/n1"), { read: true }));
  await assertFails(updateDoc(doc(as("c1"), "users/c1/notifications/n1"), { title: "معدّل" }));
  await assertFails(setDoc(doc(as("c1"), "users/c1/notifications/n2"), { title: "x", body: "y", read: false }));
  await assertFails(deleteDoc(doc(as("c1"), "users/c1/notifications/n1")));
});

test("الموصّل يقرأ سجل توصيلاته فقط", async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "orders/o3"), { customerId: "c2", storeId: "s2", status: "delivered", courierUid: "k1" });
    await setDoc(doc(ctx.firestore(), "orders/o4"), { customerId: "c2", storeId: "s2", status: "delivered", courierUid: "other" });
  });
  await assertSucceeds(getDoc(doc(as("k1"), "orders/o3")));
  await assertFails(getDoc(doc(as("k1"), "orders/o4")));
});

test("الموصّل لا يرى توصيلات غيره الجارية، ويرى الجاهزة وتوصيلاته هو", async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "orders/o5"), { customerId: "c2", storeId: "s2", status: "out_for_delivery", courierUid: "other" });
    await setDoc(doc(ctx.firestore(), "orders/o6"), { customerId: "c2", storeId: "s2", status: "out_for_delivery", courierUid: "k1" });
  });
  await assertFails(getDoc(doc(as("k1"), "orders/o5")));
  await assertSucceeds(getDoc(doc(as("k1"), "orders/o6")));
  await assertSucceeds(getDoc(doc(as("k1"), "orders/o2")));
  const orders = collection(as("k1"), "orders");
  await assertSucceeds(getDocs(query(orders, where("status", "==", "ready_for_pickup"))));
  await assertSucceeds(getDocs(query(orders, where("courierUid", "==", "k1"), where("status", "==", "out_for_delivery"))));
  await assertFails(getDocs(query(orders, where("status", "==", "out_for_delivery"))));
});
