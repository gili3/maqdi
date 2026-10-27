const test = require("node:test");
const assert = require("node:assert");
const { phoneFromAuthEmail } = require("../lib/authLogic");

test("بريد الهاتف الداخلي يُستخرج منه الرقم", () => {
  assert.equal(phoneFromAuthEmail("249912345678@phone.maqdi.app"), "+249912345678");
  assert.equal(phoneFromAuthEmail(" 249912345678@PHONE.MAQDI.APP "), "+249912345678");
});

test("أي بريد آخر ليس حساب هاتف", () => {
  assert.equal(phoneFromAuthEmail("a@gmail.com"), null);
  assert.equal(phoneFromAuthEmail("abc@phone.maqdi.app"), null);
  assert.equal(phoneFromAuthEmail("1234@phone.maqdi.app"), null);
  assert.equal(phoneFromAuthEmail("x.249912345678@phone.maqdi.app"), null);
  assert.equal(phoneFromAuthEmail("249912345678@phone.maqdi.app.evil.com"), null);
  assert.equal(phoneFromAuthEmail(undefined), null);
});
