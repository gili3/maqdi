const test = require("node:test");
const assert = require("node:assert");
const {
  paymentBlocksPreparing, cashDueAtDelivery, overpaidAmount, refundCap, withinRefundWindow, proofPathValid, storeChargeFor,
} = require("../lib/paymentLogic");
const { evaluateStatusChange } = require("../lib/orderGuards");
const { canTransition } = require("../lib/stateMachine");
const { paymentPlanFor, refundPlanFor, planFor } = require("../lib/notificationPlan");

const approved = (o = {}) => ({ status: "confirmed", revision: 1, approvedSnapshot: { revision: 1 }, courierUid: null, ...o });

test("طلب بالتحويل لا يُجهَّز قبل تأكيد الدفع، ويُجهَّز بعده", () => {
  for (const paymentStatus of ["awaiting_payment", "proof_submitted"]) {
    const r = evaluateStatusChange("merchant", "m1", approved({ paymentMethod: "bank_transfer", paymentStatus }), "preparing");
    assert.equal(r.kind, "reject");
    assert.equal(r.code, "payment_not_verified");
  }
  assert.equal(evaluateStatusChange("merchant", "m1", approved({ paymentMethod: "bank_transfer", paymentStatus: "verified" }), "preparing").kind, "apply");
});

test("الدفع نقدًا لا يتأثر (حتى بلا حقول دفع في الطلبات القديمة)", () => {
  assert.equal(evaluateStatusChange("merchant", "m1", approved(), "preparing").kind, "apply");
  assert.equal(evaluateStatusChange("merchant", "m1", approved({ paymentMethod: "cash_on_delivery", paymentStatus: "not_required" }), "preparing").kind, "apply");
  assert.equal(paymentBlocksPreparing(undefined, undefined), false);
});

test("المدير أيضًا لا يتجاوز شرط الدفع", () => {
  const r = evaluateStatusChange("admin", "a1", approved({ paymentMethod: "bank_transfer", paymentStatus: "awaiting_payment" }), "preparing");
  assert.equal(r.code, "payment_not_verified");
});

test("المحصَّل نقدًا عند التسليم = الإجمالي ناقص المدفوع بالتحويل", () => {
  assert.equal(cashDueAtDelivery(50000, 0), 50000);
  assert.equal(cashDueAtDelivery(50000, 50000), 0);
  assert.equal(cashDueAtDelivery(50000, 30000), 20000);
  assert.equal(cashDueAtDelivery(50000, 70000), 0);
});

test("الدفع الزائد يُحسب للرد", () => {
  assert.equal(overpaidAmount(50000, 70000), 20000);
  assert.equal(overpaidAmount(50000, 50000), 0);
  assert.equal(overpaidAmount(50000, 0), 0);
});

test("سقف الاسترداد وحدوده", () => {
  assert.equal(refundCap(50000, 0), 50000);
  assert.equal(refundCap(50000, 20000), 30000);
  assert.equal(refundCap(50000, 80000), 0);
});

test("مهلة الاسترداد بعد التسليم", () => {
  const now = 1_000_000_000_000;
  assert.equal(withinRefundWindow(now - 3_600_000, now, 48), true);
  assert.equal(withinRefundWindow(now - 49 * 3_600_000, now, 48), false);
  assert.equal(withinRefundWindow(null, now, 48), false);
  assert.equal(withinRefundWindow(now, now, 0), false);
});

test("مسار صورة الإثبات يجب أن يتبع الطلب نفسه", () => {
  assert.equal(proofPathValid("ord1", "payments/ord1/abc.jpg"), true);
  assert.equal(proofPathValid("ord1", "payments/ord2/abc.jpg"), false);
  assert.equal(proofPathValid("ord1", "products/s/p/main.jpg"), false);
  assert.equal(proofPathValid("ord1", "payments/ord1/../x.jpg"), false);
  assert.equal(proofPathValid("ord1", "payments/ord1/a/b.jpg"), false);
});

test("الخصم من مستحق المتجر لا يتجاوز المبلغ ولا المستحق", () => {
  assert.equal(storeChargeFor(false, 10000, 50000), 0);
  assert.equal(storeChargeFor(true, 10000, 50000), 10000);
  assert.equal(storeChargeFor(true, 10000, 4000), 4000);
  assert.equal(storeChargeFor(true, 10000, -5), 0);
});

test("انتقالات جديدة: قبول مباشر إلى «تمت الموافقة»، وانتهاء مهلة الدفع للنظام فقط", () => {
  assert.equal(canTransition("merchant", "pending_merchant", "confirmed"), true);
  assert.equal(canTransition("customer", "pending_merchant", "confirmed"), false);
  assert.equal(canTransition("courier", "pending_merchant", "confirmed"), false);
  assert.equal(canTransition("system", "confirmed", "expired"), true);
  assert.equal(canTransition("merchant", "confirmed", "expired"), false);
});

test("إشعارات الدفع: الإثبات للإدارة، والتأكيد للزبون والتاجر، والرفض للزبون", () => {
  const base = { paymentMethod: "bank_transfer", status: "confirmed" };
  assert.deepEqual(paymentPlanFor({ ...base, paymentStatus: "awaiting_payment" }, { ...base, paymentStatus: "proof_submitted" }).map((p) => p.to), ["admins"]);
  assert.deepEqual(paymentPlanFor({ ...base, paymentStatus: "proof_submitted" }, { ...base, paymentStatus: "verified" }).map((p) => p.to), ["customer", "merchants"]);
  assert.deepEqual(paymentPlanFor({ ...base, paymentStatus: "proof_submitted" }, { ...base, paymentStatus: "awaiting_payment", paymentRejectReason: "صورة غير واضحة" }).map((p) => p.to), ["customer"]);
  // لا إشعار لتاجر طلب انتهى
  assert.deepEqual(paymentPlanFor({ ...base, paymentStatus: "proof_submitted" }, { ...base, status: "cancelled", paymentStatus: "verified" }).map((p) => p.to), ["customer"]);
  // لا شيء إن لم تتغير الحالة أو كان الدفع نقدًا
  assert.deepEqual(paymentPlanFor({ ...base, paymentStatus: "verified" }, { ...base, paymentStatus: "verified" }), []);
  assert.deepEqual(paymentPlanFor({ paymentStatus: "x" }, { paymentMethod: "cash_on_delivery", paymentStatus: "verified" }), []);
});

test("«تمت الموافقة» بالتحويل ينبّه الزبون ليحوّل، وبالنقد ينبّه التاجر فقط", () => {
  assert.deepEqual(planFor({ status: "confirmed", paymentMethod: "bank_transfer", paymentStatus: "awaiting_payment" }).map((p) => p.to), ["customer", "merchants"]);
  assert.deepEqual(planFor({ status: "confirmed", paymentMethod: "cash_on_delivery", paymentStatus: "not_required" }).map((p) => p.to), ["merchants"]);
  assert.deepEqual(planFor({ status: "confirmed" }).map((p) => p.to), ["merchants"]);
});

test("إشعارات الاسترداد", () => {
  assert.deepEqual(refundPlanFor(undefined, { source: "customer", amount: 1000, reason: "x" }).map((p) => p.to), ["admins"]);
  assert.deepEqual(refundPlanFor(undefined, { source: "auto", amount: 1000, reason: "x" }).map((p) => p.to), ["admins", "customer"]);
  assert.deepEqual(refundPlanFor({ status: "pending" }, { status: "refunded", approvedAmount: 1000 }).map((p) => p.to), ["customer"]);
  assert.deepEqual(refundPlanFor({ status: "pending" }, { status: "rejected", adminNote: "n" }).map((p) => p.to), ["customer"]);
  assert.deepEqual(refundPlanFor({ status: "refunded" }, { status: "refunded" }), []);
});
