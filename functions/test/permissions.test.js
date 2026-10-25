const test = require("node:test");
const assert = require("node:assert");
const { effectivePermissions, hasPermission, permissionForStatusChange, validatePermissionList, PERMISSION_CATALOG } = require("../lib/permissions");

test("غياب الصلاحيات المخزّنة = كل صلاحيات الدور (السلوك القديم)", () => {
  for (const role of ["admin", "merchant", "courier"]) {
    assert.deepEqual(effectivePermissions(role, undefined), PERMISSION_CATALOG[role]);
    assert.deepEqual(effectivePermissions(role, null), PERMISSION_CATALOG[role]);
  }
});

test("القائمة المخزّنة تُقاطَع مع كتالوج الدور وتُهمل القيم الغريبة", () => {
  assert.deepEqual(effectivePermissions("merchant", ["orders", "finance", "bogus"]), ["orders"]);
  assert.deepEqual(effectivePermissions("courier", []), []);
});

test("المدير المقيَّد لا يملك المالية لكنه يملك ما أُعطيه", () => {
  const stored = ["orders", "stores", "reports"];
  assert.equal(hasPermission("admin", stored, "finance"), false);
  assert.equal(hasPermission("admin", stored, "stores"), true);
});

test("زبون بلا أي صلاحية إدارية", () => {
  assert.deepEqual(effectivePermissions("customer", undefined), []);
  assert.equal(hasPermission("customer", ["orders"], "orders"), false);
});

test("الصلاحية المطلوبة لتغيير حالة الطلب حسب الدور", () => {
  assert.equal(permissionForStatusChange("merchant", "preparing"), "orders");
  assert.equal(permissionForStatusChange("courier", "out_for_delivery"), "deliveries");
  assert.equal(permissionForStatusChange("admin", "delivered"), "deliveries");
  assert.equal(permissionForStatusChange("admin", "cancelled"), "orders");
  assert.equal(permissionForStatusChange("customer", "cancelled"), null);
});

test("التحقق من قائمة الصلاحيات المرسلة", () => {
  assert.deepEqual(validatePermissionList("courier", ["deliveries", "deliveries"]), ["deliveries"]);
  assert.equal(validatePermissionList("courier", ["finance"]), null);
  assert.equal(validatePermissionList("courier", "deliveries"), null);
  assert.equal(validatePermissionList("merchant", [1]), null);
});
