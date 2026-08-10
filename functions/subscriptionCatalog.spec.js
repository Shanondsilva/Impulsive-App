/* eslint-disable require-jsdoc, max-len */
const test = require("node:test");
const assert = require("node:assert/strict");

const {
  ENTITLEMENT_KIND,
  PLUS_MONTHLY_PRODUCT_ID,
  PLUS_YEARLY_PRODUCT_ID,
  SAFE_BROWSE_PASS_PRODUCT_ID,
  PRODUCT_CATALOG,
  productDefinition,
  requireProductForEntitlement,
  productsForEntitlement,
  userFieldForEntitlement,
  storedEntitlementKindForProductId,
  allSupportedProductIds,
} = require("./subscriptionCatalog");

test("Plus monthly maps to Plus and plus", () => {
  const definition = productDefinition(PLUS_MONTHLY_PRODUCT_ID);
  assert.equal(definition.entitlementKind, ENTITLEMENT_KIND.PLUS);
  assert.equal(definition.userField, "plus");
});

test("Plus yearly maps to Plus and plus", () => {
  const definition = productDefinition(PLUS_YEARLY_PRODUCT_ID);
  assert.equal(definition.entitlementKind, ENTITLEMENT_KIND.PLUS);
  assert.equal(definition.userField, "plus");
});

test("safe_browse_pass maps to Safe Browse Pass and safeBrowsePass", () => {
  const definition = productDefinition(SAFE_BROWSE_PASS_PRODUCT_ID);
  assert.equal(definition.entitlementKind, ENTITLEMENT_KIND.SAFE_BROWSE_PASS);
  assert.equal(definition.userField, "safeBrowsePass");
});

test("exactly three supported products", () => {
  assert.equal(Object.keys(PRODUCT_CATALOG).length, 3);
  assert.equal(allSupportedProductIds().size, 3);
});

test("exactly one supported Pass product", () => {
  const passIds = productsForEntitlement(ENTITLEMENT_KIND.SAFE_BROWSE_PASS);
  assert.deepEqual(passIds, [SAFE_BROWSE_PASS_PRODUCT_ID]);
});

test("obsolete Pass IDs are unsupported", () => {
  assert.equal(productDefinition("safe_browse_pass_monthly"), null);
  assert.equal(productDefinition("safe_browse_pass_prepaid_30_day"), null);
  assert.equal(allSupportedProductIds().has("safe_browse_pass_monthly"), false);
  assert.equal(allSupportedProductIds().has("safe_browse_pass_prepaid_30_day"), false);
  assert.equal(
      requireProductForEntitlement(
          "safe_browse_pass_monthly", ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
      ),
      null,
  );
  assert.equal(
      requireProductForEntitlement(
          "safe_browse_pass_prepaid_30_day", ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
      ),
      null,
  );
});

test("obsolete Pass IDs resolve only through stored migration", () => {
  assert.equal(
      storedEntitlementKindForProductId("safe_browse_pass_monthly"),
      ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
  );
  assert.equal(
      storedEntitlementKindForProductId("safe_browse_pass_prepaid_30_day"),
      ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
  );
});

test("unknown product is unsupported", () => {
  assert.equal(productDefinition("not_a_real_product"), null);
  assert.equal(productDefinition(""), null);
  assert.equal(productDefinition(undefined), null);
  assert.equal(storedEntitlementKindForProductId("not_a_real_product"), null);
});

test("client cannot choose a user field", () => {
  // userFieldForEntitlement only accepts a server-known entitlement kind --
  // there is no path from an arbitrary client-supplied string to a field.
  assert.equal(userFieldForEntitlement("hijacked"), null);
  assert.equal(userFieldForEntitlement(ENTITLEMENT_KIND.PLUS), "plus");
});

test("client cannot choose entitlement kind", () => {
  // requireProductForEntitlement only ever returns the catalogue's own
  // entitlementKind for a given productId -- the caller-supplied
  // expectedEntitlementKind is a filter, never a value that gets stored.
  const definition = requireProductForEntitlement(
      PLUS_MONTHLY_PRODUCT_ID, ENTITLEMENT_KIND.PLUS,
  );
  assert.equal(definition.entitlementKind, ENTITLEMENT_KIND.PLUS);
  assert.equal(
      requireProductForEntitlement(PLUS_MONTHLY_PRODUCT_ID, "hijacked"),
      null,
  );
});

test("catalogue entries are frozen", () => {
  assert.equal(Object.isFrozen(PRODUCT_CATALOG), true);
  const entry = productDefinition(PLUS_MONTHLY_PRODUCT_ID);
  assert.equal(Object.isFrozen(entry), true);
  entry.entitlementKind = "hijacked";
  assert.equal(productDefinition(PLUS_MONTHLY_PRODUCT_ID).entitlementKind, ENTITLEMENT_KIND.PLUS);
});

test("Plus and Pass fields differ", () => {
  assert.notEqual(
      userFieldForEntitlement(ENTITLEMENT_KIND.PLUS),
      userFieldForEntitlement(ENTITLEMENT_KIND.SAFE_BROWSE_PASS),
  );
});
