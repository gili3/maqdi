const test = require("node:test");
const assert = require("node:assert");
const { evaluateStatusChange } = require("../lib/orderGuards");

const approved = (o = {}) => ({ status: "confirmed", revision: 1, approvedSnapshot: { revision: 1 }, courierUid: null, ...o });
const rejected = (r, code) => { assert.equal(r.kind, "reject"); assert.equal(r.code, code); };

test("منع استلام الطلب مرتين: المندوب الثاني يتلقى already_taken", () => {
  const o = approved({ status: "out_for_delivery", courierUid: "c1" });
  rejected(evaluateStatusChange("courier", "c2", o, "out_for_delivery"), "already_taken");
});

test("الاستلام الأول يمر، وإعادة ضغط المندوب نفسه لا أثر ثانٍ له", () => {
  assert.equal(evaluateStatusChange("courier", "c1", approved({ status: "ready_for_pickup" }), "out_for_delivery").kind, "apply");
  const taken = approved({ status: "out_for_delivery", courierUid: "c1" });
  assert.equal(evaluateStatusChange("courier", "c1", taken, "out_for_delivery").kind, "already_applied");
});

test("منع التجهيز قبل موافقة الزبون (لا موافقة أو موافقة على نسخة قديمة)", () => {
  rejected(evaluateStatusChange("merchant", "m1", approved({ status: "awaiting_customer" }), "preparing"), "transition_not_allowed:awaiting_customer->preparing");
  rejected(evaluateStatusChange("merchant", "m1", approved({ approvedSnapshot: null }), "preparing"), "total_not_approved");
  rejected(evaluateStatusChange("merchant", "m1", approved({ revision: 2 }), "preparing"), "total_not_approved");
  assert.equal(evaluateStatusChange("merchant", "m1", approved(), "preparing").kind, "apply");
});

test("منع تحرك المندوب قبل جاهزية الطلب", () => {
  for (const status of ["pending_merchant", "awaiting_customer", "confirmed", "preparing"])
    rejected(evaluateStatusChange("courier", "c1", approved({ status }), "out_for_delivery"), `transition_not_allowed:${status}->out_for_delivery`);
});

test("منع إغلاق الطلب مرتين وإغلاقه من غير صاحب التوصيل", () => {
  const out = approved({ status: "out_for_delivery", courierUid: "c1" });
  rejected(evaluateStatusChange("courier", "c2", out, "delivered"), "not_your_delivery");
  assert.equal(evaluateStatusChange("courier", "c1", out, "delivered").kind, "apply");
  const done = approved({ status: "delivered", courierUid: "c1" });
  assert.equal(evaluateStatusChange("courier", "c1", done, "delivered").kind, "already_applied"); // لا قيود مالية ثانية
  rejected(evaluateStatusChange("admin", "a1", done, "delivery_failed"), "transition_not_allowed:delivered->delivery_failed");
  rejected(evaluateStatusChange("admin", "a1", done, "cancelled"), "transition_not_allowed:delivered->cancelled");
});

test("لا تسليم دون موافقة معتمدة على النسخة الحالية", () => {
  rejected(evaluateStatusChange("courier", "c1", approved({ status: "out_for_delivery", courierUid: "c1", approvedSnapshot: null }), "delivered"), "total_not_approved");
});

const { isUnchangedReview, evaluateStatusChange: evalChange } = require("../lib/orderGuards");

test("القبول بلا تغيير: نفس المنتجات والكميات والأسعار فقط", () => {
  const req = [{ productId: "a", qty: 2, unitPrice: 500 }, { productId: "b", qty: 1, unitPrice: 900 }];
  assert.equal(isUnchangedReview(req, [{ productId: "b", qty: 1, unitPrice: 900 }, { productId: "a", qty: 2, unitPrice: 500 }]), true);
  assert.equal(isUnchangedReview(req, [{ productId: "a", qty: 2, unitPrice: 600 }, { productId: "b", qty: 1, unitPrice: 900 }]), false); // سعر
  assert.equal(isUnchangedReview(req, [{ productId: "a", qty: 1, unitPrice: 500 }, { productId: "b", qty: 1, unitPrice: 900 }]), false); // كمية
  assert.equal(isUnchangedReview(req, [{ productId: "a", qty: 2, unitPrice: 500 }]), false); // حذف منتج
  assert.equal(isUnchangedReview(req, [...req, { productId: "c", qty: 1, unitPrice: 1 }]), false); // إضافة منتج
  assert.equal(isUnchangedReview([], []), false);
  assert.equal(isUnchangedReview(undefined, req), false);
});

test("لا التفاف على الموافقة: changeOrderStatus يرفض التجهيز المباشر بلا لقطة موافقة", () => {
  const r = evalChange("merchant", "m1", { status: "pending_merchant", revision: 0, approvedSnapshot: null }, "preparing");
  assert.equal(r.kind, "reject");
  assert.equal(r.code, "total_not_approved");
});

const { isNoWorseReview } = require("../lib/orderGuards");

test("القبول المرن: السعر الأقل أو المساوي يُقبل، والأعلى أو تغيير الكمية لا", () => {
  const req = [{ productId: "a", qty: 2, unitPrice: 500 }, { productId: "b", qty: 1, unitPrice: 900 }];
  assert.equal(isNoWorseReview(req, [{ productId: "a", qty: 2, unitPrice: 450 }, { productId: "b", qty: 1, unitPrice: 900 }]), true);
  assert.equal(isNoWorseReview(req, req), true);
  assert.equal(isNoWorseReview(req, [{ productId: "a", qty: 2, unitPrice: 501 }, { productId: "b", qty: 1, unitPrice: 900 }]), false);
  assert.equal(isNoWorseReview(req, [{ productId: "a", qty: 1, unitPrice: 400 }, { productId: "b", qty: 1, unitPrice: 900 }]), false);
  assert.equal(isNoWorseReview(req, [{ productId: "a", qty: 2, unitPrice: 400 }]), false);
});

test("إلغاء الزبون أثناء التجهيز: داخل المهلة فقط", () => {
  const o = { status: "preparing", revision: 1, approvedSnapshot: { revision: 1 }, preparingAtMs: 1_000_000 };
  const ctx = (now, grace = 180_000) => ({ nowMs: now, cancelGraceMs: grace });
  assert.equal(evalChange("customer", "c1", o, "cancelled", ctx(1_100_000)).kind, "apply");
  assert.equal(evalChange("customer", "c1", o, "cancelled", ctx(1_000_000 + 180_000)).kind, "apply");
  const late = evalChange("customer", "c1", o, "cancelled", ctx(1_000_000 + 180_001));
  assert.equal(late.kind, "reject"); assert.equal(late.code, "cancel_window_closed");
  assert.equal(evalChange("customer", "c1", o, "cancelled", ctx(1_000_001, 0)).kind, "reject"); // المهلة مغلقة
  assert.equal(evalChange("customer", "c1", o, "cancelled").kind, "reject"); // بلا سياق
  assert.equal(evalChange("customer", "c1", { ...o, status: "ready_for_pickup" }, "cancelled", ctx(1_000_001)).kind, "reject");
  assert.equal(evalChange("merchant", "m1", o, "cancelled", ctx(9_999_999)).kind, "apply"); // التاجر غير مقيّد
});
