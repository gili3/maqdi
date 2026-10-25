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
