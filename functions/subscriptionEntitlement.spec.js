/* eslint-disable require-jsdoc, max-len */
const test = require("node:test");
const assert = require("node:assert/strict");

const {
  deriveExpectedProductEntitlement,
  deriveRtdnEntitlement,
} = require("./subscriptionEntitlement");

const PLUS_DEFINITION = Object.freeze({
  productId: "impulsive_plus_monthly",
  entitlementKind: "plus",
  userField: "plus",
});

const PASS_DEFINITION = Object.freeze({
  productId: "safe_browse_pass",
  entitlementKind: "safeBrowsePass",
  userField: "safeBrowsePass",
});

function definitionFor(productId) {
  if (productId === PLUS_DEFINITION.productId) return PLUS_DEFINITION;
  if (productId === PASS_DEFINITION.productId) return PASS_DEFINITION;
  return null;
}

function autoRenewingLineItem(overrides = {}) {
  return Object.assign({
    productId: PASS_DEFINITION.productId,
    expiryTime: "2999-01-01T00:00:00Z",
    offerDetails: {basePlanId: "monthly"},
    autoRenewingPlan: {autoRenewEnabled: true},
  }, overrides);
}

function prepaidLineItem(overrides = {}) {
  return Object.assign({
    productId: PASS_DEFINITION.productId,
    expiryTime: "2999-01-01T00:00:00Z",
    offerDetails: {basePlanId: "prepaid-30"},
    prepaidPlan: {allowExtendAfterTime: "2999-01-01T00:00:00Z"},
  }, overrides);
}

function purchase(subscriptionState, lineItems, overrides = {}) {
  return Object.assign({
    subscriptionState,
    lineItems,
  }, overrides);
}

test("auto-renewing plan is detected", () => {
  const entitlement = deriveExpectedProductEntitlement(
      purchase("SUBSCRIPTION_STATE_ACTIVE", [autoRenewingLineItem()]),
      PASS_DEFINITION,
      Date.now(),
  );
  assert.equal(entitlement.planKind, "autoRenewing");
});

test("prepaid plan is detected", () => {
  const entitlement = deriveExpectedProductEntitlement(
      purchase("SUBSCRIPTION_STATE_ACTIVE", [prepaidLineItem()]),
      PASS_DEFINITION,
      Date.now(),
  );
  assert.equal(entitlement.planKind, "prepaid");
});

test("basePlanId is returned", () => {
  const entitlement = deriveExpectedProductEntitlement(
      purchase("SUBSCRIPTION_STATE_ACTIVE", [prepaidLineItem()]),
      PASS_DEFINITION,
      Date.now(),
  );
  assert.equal(entitlement.basePlanId, "prepaid-30");
});

test("both plan objects are invalid", () => {
  const item = autoRenewingLineItem({prepaidPlan: {allowExtendAfterTime: "x"}});
  const entitlement = deriveExpectedProductEntitlement(
      purchase("SUBSCRIPTION_STATE_ACTIVE", [item]),
      PASS_DEFINITION,
      Date.now(),
  );
  assert.equal(entitlement.metadataValid, false);
});

test("no plan object is invalid", () => {
  const item = {
    productId: PASS_DEFINITION.productId,
    expiryTime: "2999-01-01T00:00:00Z",
    offerDetails: {basePlanId: "monthly"},
  };
  const entitlement = deriveExpectedProductEntitlement(
      purchase("SUBSCRIPTION_STATE_ACTIVE", [item]),
      PASS_DEFINITION,
      Date.now(),
  );
  assert.equal(entitlement.metadataValid, false);
});

test("missing basePlanId is invalid", () => {
  const item = autoRenewingLineItem({offerDetails: {}});
  const entitlement = deriveExpectedProductEntitlement(
      purchase("SUBSCRIPTION_STATE_ACTIVE", [item]),
      PASS_DEFINITION,
      Date.now(),
  );
  assert.equal(entitlement.metadataValid, false);
});

test("active future expiry grants", () => {
  const entitlement = deriveExpectedProductEntitlement(
      purchase("SUBSCRIPTION_STATE_ACTIVE", [autoRenewingLineItem()]),
      PASS_DEFINITION,
      Date.now(),
  );
  assert.equal(entitlement.active, true);
});

test("grace-period future expiry grants", () => {
  const entitlement = deriveExpectedProductEntitlement(
      purchase("SUBSCRIPTION_STATE_IN_GRACE_PERIOD", [autoRenewingLineItem()]),
      PASS_DEFINITION,
      Date.now(),
  );
  assert.equal(entitlement.active, true);
});

test("cancelled future expiry grants", () => {
  const entitlement = deriveExpectedProductEntitlement(
      purchase("SUBSCRIPTION_STATE_CANCELED", [autoRenewingLineItem()]),
      PASS_DEFINITION,
      Date.now(),
  );
  assert.equal(entitlement.active, true);
});

test("exact expiry does not grant", () => {
  const nowMillis = Date.parse("2500-01-01T00:00:00Z");
  const item = autoRenewingLineItem({expiryTime: "2500-01-01T00:00:00Z"});
  const entitlement = deriveExpectedProductEntitlement(
      purchase("SUBSCRIPTION_STATE_ACTIVE", [item]),
      PASS_DEFINITION,
      nowMillis,
  );
  assert.equal(entitlement.active, false);
});

test("pending does not grant", () => {
  const entitlement = deriveExpectedProductEntitlement(
      purchase("SUBSCRIPTION_STATE_PENDING", [autoRenewingLineItem()]),
      PASS_DEFINITION,
      Date.now(),
  );
  assert.equal(entitlement.active, false);
});

test("paused does not grant", () => {
  const entitlement = deriveExpectedProductEntitlement(
      purchase("SUBSCRIPTION_STATE_PAUSED", [autoRenewingLineItem()]),
      PASS_DEFINITION,
      Date.now(),
  );
  assert.equal(entitlement.active, false);
});

test("hold does not grant", () => {
  const entitlement = deriveExpectedProductEntitlement(
      purchase("SUBSCRIPTION_STATE_ON_HOLD", [autoRenewingLineItem()]),
      PASS_DEFINITION,
      Date.now(),
  );
  assert.equal(entitlement.active, false);
});

test("expired does not grant", () => {
  const entitlement = deriveExpectedProductEntitlement(
      purchase("SUBSCRIPTION_STATE_EXPIRED", [autoRenewingLineItem()]),
      PASS_DEFINITION,
      Date.now(),
  );
  assert.equal(entitlement.active, false);
});

test("pending-purchase-cancelled does not grant", () => {
  const entitlement = deriveExpectedProductEntitlement(
      purchase(
          "SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED",
          [autoRenewingLineItem()],
      ),
      PASS_DEFINITION,
      Date.now(),
  );
  assert.equal(entitlement.active, false);
});

test("expected-product derivation rejects another product", () => {
  const entitlement = deriveExpectedProductEntitlement(
      purchase("SUBSCRIPTION_STATE_ACTIVE", [
        autoRenewingLineItem({productId: "some_other_product"}),
      ]),
      PASS_DEFINITION,
      Date.now(),
  );
  assert.equal(entitlement.hasMatchingProduct, false);
});

test("RTDN rejects cross-family line items as ambiguous", () => {
  const item1 = autoRenewingLineItem({productId: PASS_DEFINITION.productId});
  const item2 = autoRenewingLineItem({productId: PLUS_DEFINITION.productId});
  const entitlement = deriveRtdnEntitlement(
      purchase("SUBSCRIPTION_STATE_ACTIVE", [item1, item2]),
      definitionFor,
      Date.now(),
  );
  assert.equal(entitlement.ambiguousEntitlementKind, true);
  assert.equal(entitlement.hasMatchingProduct, false);
});

test("RTDN permits multiple same-family Plus items", () => {
  const item1 = autoRenewingLineItem({
    productId: PLUS_DEFINITION.productId,
    expiryTime: "2999-01-01T00:00:00Z",
  });
  const item2 = autoRenewingLineItem({
    productId: PLUS_DEFINITION.productId,
    expiryTime: "2998-01-01T00:00:00Z",
  });
  const entitlement = deriveRtdnEntitlement(
      purchase("SUBSCRIPTION_STATE_ACTIVE", [item1, item2]),
      definitionFor,
      Date.now(),
  );
  assert.equal(entitlement.ambiguousEntitlementKind, false);
  assert.equal(entitlement.hasMatchingProduct, true);
  assert.equal(entitlement.entitlementKind, "plus");
});

test("top-up linked token is preserved in result", () => {
  const entitlement = deriveExpectedProductEntitlement(
      purchase(
          "SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED",
          [prepaidLineItem()],
          {linkedPurchaseToken: "old-token"},
      ),
      PASS_DEFINITION,
      Date.now(),
  );
  assert.equal(entitlement.linkedPurchaseToken, "old-token");
});

test("expiry is taken from verified line items only", () => {
  const item = autoRenewingLineItem({expiryTime: "2777-06-15T00:00:00Z"});
  const entitlement = deriveExpectedProductEntitlement(
      purchase("SUBSCRIPTION_STATE_ACTIVE", [item]),
      PASS_DEFINITION,
      Date.now(),
  );
  assert.equal(entitlement.expiryTimeMillis, Date.parse("2777-06-15T00:00:00Z"));
});
