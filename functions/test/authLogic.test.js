const test = require("node:test");
const assert = require("node:assert");
const { sudanPhoneFromToken } = require("../lib/authLogic");

test("الأرقام السودانية الصحيحة مقبولة", () => {
  assert.equal(sudanPhoneFromToken("+249912345678"), "+249912345678");
  assert.equal(sudanPhoneFromToken("+249112345678"), "+249112345678");
});

test("الأرقام غير السودانية مرفوضة", () => {
  assert.equal(sudanPhoneFromToken("+15551234567"), null); // أمريكا
  assert.equal(sudanPhoneFromToken("+966512345678"), null); // السعودية
  assert.equal(sudanPhoneFromToken("+201234567890"), null); // مصر
  assert.equal(sudanPhoneFromToken("+211912345678"), null); // جنوب السودان
});

test("صيغ غير صالحة مرفوضة", () => {
  assert.equal(sudanPhoneFromToken("+249812345678"), null); // لا يبدأ بـ 9/1
  assert.equal(sudanPhoneFromToken("+24991234567"), null); // أقصر من اللازم
  assert.equal(sudanPhoneFromToken("+2499123456789"), null); // أطول من اللازم
  assert.equal(sudanPhoneFromToken("249912345678"), null); // بلا +
  assert.equal(sudanPhoneFromToken("+249912345678 "), null);
  assert.equal(sudanPhoneFromToken(undefined), null);
  assert.equal(sudanPhoneFromToken(249912345678), null);
});
