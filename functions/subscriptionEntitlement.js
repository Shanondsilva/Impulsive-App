"use strict";

const ENTITLED_STATES = new Set([
  "SUBSCRIPTION_STATE_ACTIVE",
  "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
]);

const CANCELED_STATE = "SUBSCRIPTION_STATE_CANCELED";

const PENDING_PURCHASE_CANCELED_STATE =
  "SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED";

const UNSPECIFIED_STATE = "SUBSCRIPTION_STATE_UNSPECIFIED";

/**
 * @param {*} value Input value.
 * @return {string} Trimmed value or an empty string.
 */
function cleanString(value) {
  return typeof value === "string" ? value.trim() : "";
}

/**
 * @param {*} value RFC 3339 timestamp.
 * @return {number} Milliseconds, or 0 when unparseable.
 */
function parseExpiryTimeMillis(value) {
  const parsed = Date.parse(cleanString(value));

  return Number.isFinite(parsed) ? parsed : 0;
}

/**
 * Extracts the verified base plan and prepaid/auto-renewing plan kind from
 * one Google Play subscription line item.
 *
 * @param {*} lineItem Google Play subscription line item.
 * @return {?{basePlanId: string, planKind: string}} Metadata, or null when
 *   the line item lacks a base plan or has an ambiguous/missing plan type.
 */
function planMetadataForLineItem(lineItem) {
  if (!lineItem || typeof lineItem !== "object" || Array.isArray(lineItem)) {
    return null;
  }

  const basePlanId = cleanString(
      lineItem.offerDetails && lineItem.offerDetails.basePlanId,
  );

  if (!basePlanId) {
    return null;
  }

  const hasAutoRenewingPlan =
    lineItem.autoRenewingPlan &&
    typeof lineItem.autoRenewingPlan === "object" &&
    !Array.isArray(lineItem.autoRenewingPlan);

  const hasPrepaidPlan =
    lineItem.prepaidPlan &&
    typeof lineItem.prepaidPlan === "object" &&
    !Array.isArray(lineItem.prepaidPlan);

  if (Boolean(hasAutoRenewingPlan) === Boolean(hasPrepaidPlan)) {
    return null;
  }

  return {
    basePlanId,
    planKind: hasPrepaidPlan ? "prepaid" : "autoRenewing",
  };
}

/**
 * @param {string} subscriptionState Verified Google subscription state.
 * @param {number} expiryTimeMillis Verified expiry, in milliseconds.
 * @param {number} nowMillis Current time, in milliseconds.
 * @return {boolean} Whether the subscription grants access right now.
 */
function isEntitledAt(subscriptionState, expiryTimeMillis, nowMillis) {
  if (!Number.isFinite(nowMillis) || expiryTimeMillis <= nowMillis) {
    return false;
  }

  return ENTITLED_STATES.has(subscriptionState) ||
    subscriptionState === CANCELED_STATE;
}

/**
 * @param {*} purchase Google Play subscription purchase.
 * @return {Array<object>} Line items, or an empty array.
 */
function lineItems(purchase) {
  return Array.isArray(purchase && purchase.lineItems) ?
    purchase.lineItems :
    [];
}

/**
 * @param {Array<object>} items Line items with a parsed expiry.
 * @return {?object} The item with the latest expiry, or null when empty.
 */
function selectLatestItem(items) {
  return items
      .map((item) => ({
        item,
        expiryTimeMillis: parseExpiryTimeMillis(item && item.expiryTime),
      }))
      .reduce((selected, candidate) => {
        if (!selected) {
          return candidate;
        }

        return candidate.expiryTimeMillis > selected.expiryTimeMillis ?
          candidate :
          selected;
      }, null);
}

/**
 * Derives the caller's entitlement for one expected catalogue product from
 * a verified Google Play subscription purchase.
 *
 * @param {*} purchase Verified Google Play subscription purchase.
 * @param {object} definition Trusted catalogue product definition.
 * @param {number} nowMillis Current time, in milliseconds.
 * @return {object} Derived entitlement.
 */
function deriveExpectedProductEntitlement(purchase, definition, nowMillis) {
  const subscriptionState =
    cleanString(purchase && purchase.subscriptionState) || UNSPECIFIED_STATE;

  const linkedPurchaseToken =
    cleanString(purchase && purchase.linkedPurchaseToken) || null;

  const matching = lineItems(purchase).filter(
      (item) => cleanString(item && item.productId) === definition.productId,
  );

  if (matching.length === 0) {
    return {
      hasMatchingProduct: false,
      metadataValid: false,
      active: false,
      productId: definition.productId,
      entitlementKind: definition.entitlementKind,
      userField: definition.userField,
      basePlanId: null,
      planKind: null,
      subscriptionState,
      expiryTimeMillis: 0,
      linkedPurchaseToken,
    };
  }

  const selected = selectLatestItem(matching);

  const metadata = selected ? planMetadataForLineItem(selected.item) : null;

  const expiryTimeMillis = matching.reduce(
      (latest, item) =>
        Math.max(latest, parseExpiryTimeMillis(item && item.expiryTime)),
      0,
  );

  return {
    hasMatchingProduct: true,
    metadataValid: metadata !== null,
    active: metadata !== null &&
      isEntitledAt(subscriptionState, expiryTimeMillis, nowMillis),
    productId: definition.productId,
    entitlementKind: definition.entitlementKind,
    userField: definition.userField,
    basePlanId: metadata ? metadata.basePlanId : null,
    planKind: metadata ? metadata.planKind : null,
    subscriptionState,
    expiryTimeMillis,
    linkedPurchaseToken,
  };
}

/**
 * Derives the caller's entitlement from an RTDN-verified Google Play
 * subscription purchase whose expected product is not known in advance.
 * Only line items resolving to a catalogued product are considered, and a
 * purchase whose supported line items span more than one entitlement kind
 * is reported ambiguous rather than resolved by whichever item merely has
 * the latest expiry.
 *
 * @param {*} purchase Verified Google Play subscription purchase.
 * @param {function(string): ?object} productDefinition Catalogue lookup.
 * @param {number} nowMillis Current time, in milliseconds.
 * @return {object} Derived entitlement.
 */
function deriveRtdnEntitlement(purchase, productDefinition, nowMillis) {
  const subscriptionState =
    cleanString(purchase && purchase.subscriptionState) || UNSPECIFIED_STATE;

  const linkedPurchaseToken =
    cleanString(purchase && purchase.linkedPurchaseToken) || null;

  const supported = lineItems(purchase)
      .map((item) => {
        const productId = cleanString(item && item.productId);

        const definition = productDefinition(productId);

        return definition ?
          {
            item,
            definition,
            expiryTimeMillis: parseExpiryTimeMillis(item && item.expiryTime),
          } :
          null;
      })
      .filter(Boolean);

  if (supported.length === 0) {
    return {
      hasMatchingProduct: false,
      ambiguousEntitlementKind: false,
      metadataValid: false,
      active: false,
      productId: null,
      entitlementKind: null,
      userField: null,
      basePlanId: null,
      planKind: null,
      subscriptionState,
      expiryTimeMillis: 0,
      linkedPurchaseToken,
    };
  }

  const entitlementKinds = new Set(
      supported.map((entry) => entry.definition.entitlementKind),
  );

  if (entitlementKinds.size !== 1) {
    return {
      hasMatchingProduct: false,
      ambiguousEntitlementKind: true,
      metadataValid: false,
      active: false,
      productId: null,
      entitlementKind: null,
      userField: null,
      basePlanId: null,
      planKind: null,
      subscriptionState,
      expiryTimeMillis: 0,
      linkedPurchaseToken,
    };
  }

  const selected = supported.reduce((latest, entry) => {
    if (!latest) {
      return entry;
    }

    return entry.expiryTimeMillis > latest.expiryTimeMillis ? entry : latest;
  }, null);

  const metadata = selected ? planMetadataForLineItem(selected.item) : null;

  const selectedProductId = selected.definition.productId;
  const expiryTimeMillis = supported
      .filter((entry) => entry.definition.productId === selectedProductId)
      .reduce((latest, entry) => Math.max(latest, entry.expiryTimeMillis), 0);

  return {
    hasMatchingProduct: true,
    ambiguousEntitlementKind: false,
    metadataValid: metadata !== null,
    active: metadata !== null &&
      isEntitledAt(subscriptionState, expiryTimeMillis, nowMillis),
    productId: selected.definition.productId,
    entitlementKind: selected.definition.entitlementKind,
    userField: selected.definition.userField,
    basePlanId: metadata ? metadata.basePlanId : null,
    planKind: metadata ? metadata.planKind : null,
    subscriptionState,
    expiryTimeMillis,
    linkedPurchaseToken,
  };
}

module.exports = {
  CANCELED_STATE,
  PENDING_PURCHASE_CANCELED_STATE,
  cleanString,
  parseExpiryTimeMillis,
  planMetadataForLineItem,
  isEntitledAt,
  deriveExpectedProductEntitlement,
  deriveRtdnEntitlement,
};
