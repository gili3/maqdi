const test = require("node:test");
const assert = require("node:assert");
const { buildReport, localDay } = require("../lib/reportLogic");
const { normalizeSearch } = require("../lib/textUtil");
const { planFor } = require("../lib/notificationPlan");

const OFFSET = 2 * 3600 * 1000;
const t = (iso) => new Date(iso).getTime();
const order = (storeId, iso, items, fee = 4000, discount = 0) => ({
  storeId, storeName: storeId.toUpperCase(), deliveredAtMs: t(iso),
  itemsTotal: items.reduce((s, i) => s + i.lineTotal, 0), deliveryFee: fee, discount,
  collected: items.reduce((s, i) => s + i.lineTotal, 0) + fee - discount, items,
});
const line = (id, qty, unit) => ({ productId: id, name: "منتج " + id, qty, lineTotal: qty * unit });

test("اليوم يُحسب بتوقيت السودان (UTC+2)", () => {
  assert.equal(localDay(t("2026-10-24T22:30:00Z"), OFFSET), "2026-10-25");
  assert.equal(localDay(t("2026-10-24T21:59:00Z"), OFFSET), "2026-10-24");
});

test("إجماليات التقرير: مستحق المتجر منفصل عن إيراد التوصيل", () => {
  const r = buildReport([
    order("s1", "2026-10-24T10:00:00Z", [line("a", 2, 5000)]),   // 10,000 + 4,000
    order("s2", "2026-10-24T11:00:00Z", [line("b", 1, 20000)]),  // 20,000 + 4,000
  ], OFFSET);
  assert.equal(r.totals.orders, 2);
  assert.equal(r.totals.itemsTotal, 30000);
  assert.equal(r.totals.deliveryRevenue, 8000);
  assert.equal(r.totals.collected, 38000);
  assert.equal(r.totals.itemsTotal + r.totals.deliveryRevenue, r.totals.collected);
});

test("الخصم يُخصم من إيراد التوصيل فقط", () => {
  const r = buildReport([order("s1", "2026-10-24T10:00:00Z", [line("a", 1, 10000)], 4000, 1000)], OFFSET);
  assert.equal(r.totals.itemsTotal, 10000);
  assert.equal(r.totals.deliveryRevenue, 3000);
  assert.equal(r.totals.collected, 13000);
});

test("التجميع حسب اليوم والمتجر وأكثر المنتجات مبيعًا", () => {
  const r = buildReport([
    order("s1", "2026-10-24T10:00:00Z", [line("a", 3, 1000), line("b", 1, 500)]),
    order("s1", "2026-10-25T10:00:00Z", [line("a", 2, 1000)]),
    order("s2", "2026-10-25T12:00:00Z", [line("c", 9, 100)]),
  ], OFFSET);
  assert.deepEqual(r.byDay.map((d) => [d.day, d.orders]), [["2026-10-24", 2 - 1], ["2026-10-25", 2]]);
  assert.equal(r.byStore[0].storeId, "s1");
  assert.equal(r.topProducts[0].productId, "c");
  assert.equal(r.topProducts[0].qty, 9);
  assert.equal(r.topProducts.find((p) => p.productId === "a").qty, 5);
});

test("تقرير فارغ لا ينهار", () => {
  const r = buildReport([], OFFSET);
  assert.equal(r.totals.orders, 0);
  assert.deepEqual(r.byDay, []);
});

test("تطبيع نص البحث العربي", () => {
  assert.equal(normalizeSearch("  أَلبَان  "), "البان");
  assert.equal(normalizeSearch("إبريق"), "ابريق");
  assert.equal(normalizeSearch("مدرسة"), "مدرسه");
  assert.equal(normalizeSearch("على"), "علي");
  assert.equal(normalizeSearch("Milk  POWDER"), "milk powder");
});

test("خطة الإشعارات: من يُخطَر في كل حالة", () => {
  const to = (o) => planFor(o).map((p) => p.to).sort();
  assert.deepEqual(to({ status: "pending_merchant" }), ["merchants"]);
  assert.deepEqual(to({ status: "awaiting_customer" }), ["customer"]);
  assert.deepEqual(to({ status: "confirmed" }), ["merchants"]);
  assert.deepEqual(to({ status: "ready_for_pickup" }), ["couriers", "customer"]);
  assert.deepEqual(to({ status: "delivered" }), ["customer", "merchants"]);
  assert.deepEqual(to({ status: "cancelled", cancelledByRole: "customer" }), ["merchants"]);
  assert.deepEqual(to({ status: "cancelled", cancelledByRole: "admin" }), ["customer", "merchants"]);
  assert.deepEqual(to({ status: "delivery_failed" }), ["admins", "customer"]);
  assert.deepEqual(to({ status: "unknown" }), []);
});

test("طلب جديد للتاجر تنبيه عاجل فقط", () => {
  const { planFor } = require("../lib/notificationPlan");
  const urgentOf = (status) => planFor({ status }).some((p) => p.urgent === true);
  assert.equal(urgentOf("pending_merchant"), true);
  for (const s of ["awaiting_customer", "confirmed", "preparing", "ready_for_pickup", "out_for_delivery", "delivered", "cancelled"])
    assert.equal(urgentOf(s), false, s);
});
