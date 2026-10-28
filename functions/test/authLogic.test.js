const test = require("node:test");
const assert = require("node:assert");
const { phoneFromToken } = require("../lib/authLogic");

test("أرقام E.164 صالحة من دول مختلفة مقبولة", () => {
  assert.equal(phoneFromToken("+249912345678"), "+249912345678"); // السودان
  assert.equal(phoneFromToken("+966501234567"), "+966501234567"); // السعودية
  assert.equal(phoneFromToken("+447912345678"), "+447912345678"); // بريطانيا
  assert.equal(phoneFromToken("+15551234567"), "+15551234567"); // أمريكا
});

test("صيغ غير صالحة مرفوضة", () => {
  assert.equal(phoneFromToken("249912345678"), null); // بلا +
  assert.equal(phoneFromToken("+0912345678"), null); // مفتاح يبدأ بصفر
  assert.equal(phoneFromToken("+2499"), null); // قصير
  assert.equal(phoneFromToken("+2499123456789012"), null); // أطول من 15
  assert.equal(phoneFromToken("+249 912345678"), null); // مسافة
  assert.equal(phoneFromToken("+249912345678 "), null);
  assert.equal(phoneFromToken(undefined), null);
  assert.equal(phoneFromToken(249912345678), null);
});
