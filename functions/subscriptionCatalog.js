"use strict";

const ENTITLEMENT_KIND = Object.freeze({
  PLUS: "plus",
  SAFE_BROWSE_PASS: "safeBrowsePass",
});

const PLUS_MONTHLY_PRODUCT_ID = "impulsive_plus_monthly";
const PLUS_YEARLY_PRODUCT_ID = "impulsive_plus_yearly";
const SAFE_BROWSE_PASS_PRODUCT_ID = "safe_browse_pass";

/*
 * These obsolete IDs are migration-only values for already stored token
 * documents. They are not supported products and must never be accepted by
 * a new callable request or RTDN purchase response.
 */
const LEGACY_STORED_PRODUCT_KIND = Object.freeze({
  safe_browse_pass_monthly: ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
  safe_browse_pass_prepaid_30_day: ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
});

const PRODUCT_CATALOG = Object.freeze({
  [PLUS_MONTHLY_PRODUCT_ID]: Object.freeze({
    productId: PLUS_MONTHLY_PRODUCT_ID,
    entitlementKind: ENTITLEMENT_KIND.PLUS,
    userField: "plus",
  }),

  [PLUS_YEARLY_PRODUCT_ID]: Object.freeze({
    productId: PLUS_YEARLY_PRODUCT_ID,
    entitlementKind: ENTITLEMENT_KIND.PLUS,
    userField: "plus",
  }),

  [SAFE_BROWSE_PASS_PRODUCT_ID]: Object.freeze({
    productId: SAFE_BROWSE_PASS_PRODUCT_ID,
    entitlementKind: ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
    userField: "safeBrowsePass",
  }),
});

/**
 * Looks up the catalogue definition for a Play product ID.
 *
 * @param {string} productId Play product ID.
 * @return {?object} Frozen catalogue definition, or null when unsupported.
 */
function productDefinition(productId) {
  return Object.prototype.hasOwnProperty.call(PRODUCT_CATALOG, productId) ?
    PRODUCT_CATALOG[productId] :
    null;
}

/**
 * Looks up a catalogue definition, requiring it to belong to one specific
 * entitlement kind.
 *
 * @param {string} productId Play product ID.
 * @param {string} entitlementKind Required entitlement kind.
 * @return {?object} Matching definition, or null when unsupported/mismatched.
 */
function requireProductForEntitlement(productId, entitlementKind) {
  const definition = productDefinition(productId);

  return definition && definition.entitlementKind === entitlementKind ?
    definition :
    null;
}

/**
 * Lists every currently supported product ID for one entitlement kind.
 *
 * @param {string} entitlementKind Entitlement kind.
 * @return {Array<string>} Product IDs of that kind.
 */
function productsForEntitlement(entitlementKind) {
  return Object.values(PRODUCT_CATALOG)
      .filter((definition) => definition.entitlementKind === entitlementKind)
      .map((definition) => definition.productId);
}

/**
 * The Firestore field on `users/{uid}` one entitlement kind may write.
 *
 * @param {string} entitlementKind Entitlement kind.
 * @return {?string} Firestore field name, or null when the kind is unknown.
 */
function userFieldForEntitlement(entitlementKind) {
  const matching = Object.values(PRODUCT_CATALOG)
      .find((definition) => definition.entitlementKind === entitlementKind);

  return matching ? matching.userField : null;
}

/**
 * Resolves the entitlement kind a stored product ID belongs to, including
 * migration-only legacy product IDs no longer accepted for new purchases.
 *
 * @param {string} productId Stored Play product ID.
 * @return {?string} Entitlement kind, or null when unresolvable.
 */
function storedEntitlementKindForProductId(productId) {
  const current = productDefinition(productId);

  if (current) {
    return current.entitlementKind;
  }

  return Object.prototype.hasOwnProperty.call(
      LEGACY_STORED_PRODUCT_KIND, productId,
  ) ?
    LEGACY_STORED_PRODUCT_KIND[productId] :
    null;
}

/**
 * Every currently supported product ID, for allowlist validation.
 *
 * @return {Set<string>} Supported product IDs.
 */
function allSupportedProductIds() {
  return new Set(Object.keys(PRODUCT_CATALOG));
}

module.exports = {
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
};
