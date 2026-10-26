const test = require("node:test");
const assert = require("node:assert");
const { canTransition, TRANSITIONS } = require("../lib/stateMachine");
const { STATUSES } = require("../lib/config");

test("التاجر لا يبدأ التجهيز بعد تعديل المراجعة إلا بموافقة الزبون", () => {
  assert.equal(canTransition("merchant", "awaiting_customer", "preparing"), false);
  assert.equal(canTransition("courier", "pending_merchant", "preparing"), false);
  assert.equal(canTransition("customer", "pending_merchant", "preparing"), false);
  assert.equal(canTransition("merchant", "confirmed", "preparing"), true);
  assert.equal(canTransition("merchant", "pending_merchant", "preparing"), true); // القبول بلا تغيير
});

test("الموافقة على الإجمالي للزبون فقط", () => {
  assert.equal(canTransition("customer", "awaiting_customer", "confirmed"), true);
  for (const r of ["merchant", "admin", "courier", "system"])
    assert.equal(canTransition(r, "awaiting_customer", "confirmed"), false);
});

test("تغيير السعر بعد الموافقة يعيد الطلب إلى awaiting_customer", () => {
  assert.equal(canTransition("merchant", "confirmed", "awaiting_customer"), true);
  assert.equal(canTransition("merchant", "preparing", "awaiting_customer"), true);
});

test("لا توصيل قبل ready_for_pickup ولا تسليم قبل out_for_delivery", () => {
  for (const from of STATUSES) {
    if (from !== "ready_for_pickup") assert.equal(canTransition("courier", from, "out_for_delivery"), false, from);
    if (from !== "out_for_delivery") assert.equal(canTransition("courier", from, "delivered"), false, from);
  }
});

test("لا ينتقل الطلب إلى ready_for_pickup إلا من preparing", () => {
  for (const from of STATUSES) {
    assert.equal(canTransition("merchant", from, "ready_for_pickup"), from === "preparing", from);
  }
});

test("الحالات النهائية لا تخرج منها أي انتقالات", () => {
  for (const t of ["delivered", "rejected", "cancelled", "expired", "delivery_failed"])
    assert.equal(TRANSITIONS.some((r) => r.from === t), false, t);
});

test("الزبون لا يلغي بعد الجاهزية (والتجهيز مقيّد بمهلة في الحارس)", () => {
  assert.equal(canTransition("customer", "preparing", "cancelled"), true);
  assert.equal(canTransition("customer", "ready_for_pickup", "cancelled"), false);
  assert.equal(canTransition("customer", "confirmed", "cancelled"), true);
});

test("الانتهاء التلقائي للنظام فقط", () => {
  assert.equal(canTransition("system", "pending_merchant", "expired"), true);
  assert.equal(canTransition("admin", "pending_merchant", "expired"), false);
});
