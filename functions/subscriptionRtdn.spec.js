/* eslint-disable require-jsdoc, max-len */
const test = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const {
  createFirestoreRtdnStore,
  createRtdnProcessor,
  deriveRtdnEntitlement,
} = require("./subscriptionRtdn");
const {
  ENTITLEMENT_KIND,
  PLUS_MONTHLY_PRODUCT_ID,
  SAFE_BROWSE_PASS_PRODUCT_ID,
  productDefinition,
  userFieldForEntitlement,
  storedEntitlementKindForProductId,
} = require("./subscriptionCatalog");

const NOW = Date.parse("2026-01-01T00:00:00.000Z");
const FUTURE = "2026-02-01T00:00:00.000Z";
const PAST = "2025-12-01T00:00:00.000Z";
const PACKAGE = "com.impulsive.app";
const DELETE = {sentinel: "delete"};
const SERVER_TIME = {sentinel: "server-time"};

class Snapshot {
  constructor(data) {
    this.value = data;
    this.exists = data !== undefined;
  }

  get(fieldPath) {
    return fieldPath.split(".").reduce((value, key) =>
      value && value[key], this.value);
  }

  data() {
    return clone(this.value);
  }
}

class FakeFirestore {
  constructor(seed = {}) {
    this.documents = new Map(Object.entries(seed).map(([key, value]) =>
      [key, clone(value)]));
  }

  collection(name) {
    return {
      doc: (id) => ({path: `${name}/${id}`}),
    };
  }

  async runTransaction(callback) {
    const pending = [];
    const transaction = {
      get: async (ref) => new Snapshot(clone(this.documents.get(ref.path))),
      set: (ref, value, options) => pending.push({ref, value, options}),
    };
    const result = await callback(transaction);
    pending.forEach(({ref, value, options}) => {
      const current = options && options.merge ?
        clone(this.documents.get(ref.path) || {}) : {};
      this.documents.set(ref.path, merge(current, value));
    });
    return result;
  }
}

function clone(value) {
  if (value === undefined) return undefined;
  if (value instanceof Date) return new Date(value.getTime());
  if (Array.isArray(value)) return value.map(clone);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([key, item]) =>
      [key, clone(item)]));
  }
  return value;
}

function merge(target, source) {
  Object.entries(source).forEach(([key, value]) => {
    if (value && value.sentinel === "delete") {
      delete target[key];
    } else if (value && typeof value === "object" &&
        !Array.isArray(value) && !(value instanceof Date) &&
        !value.sentinel) {
      target[key] = merge(clone(target[key] || {}), value);
    } else {
      target[key] = clone(value);
    }
  });
  return target;
}

function hash(value) {
  return crypto.createHash("sha256").update(value).digest("hex");
}

function autoRenewingLineItem(productId, expiry, overrides = {}) {
  return Object.assign({
    productId,
    expiryTime: expiry,
    offerDetails: {basePlanId: "monthly"},
    autoRenewingPlan: {autoRenewEnabled: true},
  }, overrides);
}

function prepaidLineItem(productId, expiry, overrides = {}) {
  return Object.assign({
    productId,
    expiryTime: expiry,
    offerDetails: {basePlanId: "prepaid-30"},
    prepaidPlan: {allowExtendAfterTime: expiry},
  }, overrides);
}

function purchase(state, expiry = FUTURE, additions = {}) {
  return {
    subscriptionState: state,
    acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    lineItems: [autoRenewingLineItem(PLUS_MONTHLY_PRODUCT_ID, expiry)],
    ...additions,
  };
}

function passPurchase(state, expiry = FUTURE, additions = {}) {
  return {
    subscriptionState: state,
    acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    lineItems: [autoRenewingLineItem(SAFE_BROWSE_PASS_PRODUCT_ID, expiry)],
    ...additions,
  };
}

function prepaidPassPurchase(state, expiry = FUTURE, additions = {}) {
  return {
    subscriptionState: state,
    acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    lineItems: [prepaidLineItem(SAFE_BROWSE_PASS_PRODUCT_ID, expiry)],
    ...additions,
  };
}

function event(id, token = "purchase-token", type = 2, additions = {}) {
  return {
    id: `cloud-${id}`,
    data: {
      message: {
        messageId: id,
        json: {
          packageName: PACKAGE,
          subscriptionNotification: {
            purchaseToken: token,
            notificationType: type,
          },
          ...additions,
        },
      },
    },
  };
}

function environment(options = {}) {
  const token = options.token || "purchase-token";
  const tokenHash = hash(token);
  const uid = options.uid === undefined ? "user-1" : options.uid;
  const entitlementKind = options.entitlementKind || ENTITLEMENT_KIND.PLUS;
  const seed = {...options.seed};
  if (uid) {
    seed[`playPurchaseTokens/${tokenHash}`] = {
      uid,
      productId: entitlementKind === ENTITLEMENT_KIND.SAFE_BROWSE_PASS ?
        SAFE_BROWSE_PASS_PRODUCT_ID : PLUS_MONTHLY_PRODUCT_ID,
      entitlementKind,
    };
    seed[`users/${uid}`] = entitlementKind === ENTITLEMENT_KIND.SAFE_BROWSE_PASS ?
      {safeBrowsePass: {active: true, purchaseTokenHash: tokenHash}} :
      {plus: {active: true, purchaseTokenHash: tokenHash}};
  }
  const db = new FakeFirestore(seed);
  const logs = [];
  const logger = Object.fromEntries(["info", "warn", "error"].map((level) =>
    [level, (...args) => logs.push({level, args})]));
  const fieldValue = {
    delete: () => DELETE,
    serverTimestamp: () => SERVER_TIME,
  };
  const store = createFirestoreRtdnStore({
    db,
    fieldValue,
    packageName: PACKAGE,
    logger,
    userFieldForEntitlement,
    storedEntitlementKindForProductId,
  });
  let calls = 0;
  const responses = options.responses || [purchase("SUBSCRIPTION_STATE_ACTIVE")];
  const linkedResponses = options.linkedResponses || [];
  let linkedCalls = 0;
  const verifyPurchase = async (requestedToken) => {
    if (options.responsesByToken) {
      const response = options.responsesByToken[requestedToken];
      if (response instanceof Error) throw response;
      return response;
    }
    if (requestedToken !== token && linkedResponses.length > 0) {
      const response = linkedResponses[linkedCalls++];
      if (response instanceof Error) throw response;
      return response;
    }
    const response = responses[calls++];
    if (response instanceof Error) throw response;
    return response;
  };
  let ackCalls = 0;
  const acknowledgePurchase = options.acknowledgePurchase || (async () => true);
  const wrappedAcknowledge = async (t, p, productId) => {
    ackCalls++;
    return acknowledgePurchase(t, p, productId);
  };
  const processor = createRtdnProcessor({
    store,
    verifyPurchase,
    acknowledgePurchase: wrappedAcknowledge,
    logger,
    packageName: PACKAGE,
    productDefinition,
    hashToken: hash,
    now: () => NOW,
    createClaimId: () => `claim-${calls + 1}`,
    isRetryableError: (error) => error.retryable === true,
  });
  return {
    db, logs, processor, store, token, tokenHash,
    calls: () => calls, ackCalls: () => ackCalls,
  };
}

async function assertLifecycle(name, state, expiry, active) {
  const env = environment({responses: [purchase(state, expiry)]});
  await env.processor(event(name));
  assert.equal(env.db.documents.get("users/user-1").plus.active, active);
  assert.equal(env.calls(), 1);
}

test("active subscription remains active", async () => {
  await assertLifecycle("active", "SUBSCRIPTION_STATE_ACTIVE", FUTURE, true);
});

test("grace-period subscription remains active", async () => {
  await assertLifecycle(
      "grace", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", FUTURE, true,
  );
});

test("account hold becomes inactive", async () => {
  await assertLifecycle("hold", "SUBSCRIPTION_STATE_ON_HOLD", FUTURE, false);
});

test("cancelled subscription remains active before expiry", async () => {
  await assertLifecycle("cancel-future", "SUBSCRIPTION_STATE_CANCELED", FUTURE, true);
});

test("cancelled subscription becomes inactive after expiry", async () => {
  await assertLifecycle("cancel-past", "SUBSCRIPTION_STATE_CANCELED", PAST, false);
});

test("expired subscription becomes inactive", async () => {
  await assertLifecycle("expired", "SUBSCRIPTION_STATE_EXPIRED", PAST, false);
});

// ---------------------------------------------------------------------------
// 1-4: acknowledgement ordering
// ---------------------------------------------------------------------------

test("initial active Plus is acknowledged before apply", async () => {
  const order = [];
  const env = environment({
    responses: [purchase("SUBSCRIPTION_STATE_ACTIVE", FUTURE, {
      acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING",
    })],
    acknowledgePurchase: async () => {
      order.push("acknowledge"); return true;
    },
  });
  await env.processor(event("plus-ack"));
  order.push("applied:" + env.db.documents.get("users/user-1").plus.active);
  assert.deepEqual(order, ["acknowledge", "applied:true"]);
  assert.equal(env.ackCalls(), 1);
});

test("initial active Pass is acknowledged before apply", async () => {
  const order = [];
  const env = environment({
    token: "pass-only-token",
    uid: "user-1",
    entitlementKind: ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
    responses: [passPurchase("SUBSCRIPTION_STATE_ACTIVE", FUTURE, {
      acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING",
    })],
    acknowledgePurchase: async () => {
      order.push("acknowledge"); return true;
    },
  });
  await env.processor(event("pass-ack", env.token));
  order.push("applied:" + env.db.documents.get("users/user-1").safeBrowsePass.active);
  assert.deepEqual(order, ["acknowledge", "applied:true"]);
});

test("prepaid top-up is acknowledged before apply", async () => {
  const order = [];
  const env = environment({
    token: "prepaid-only-token",
    uid: "user-1",
    entitlementKind: ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
    responses: [prepaidPassPurchase("SUBSCRIPTION_STATE_ACTIVE", FUTURE, {
      acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING",
    })],
    acknowledgePurchase: async () => {
      order.push("acknowledge"); return true;
    },
  });
  await env.processor(event("prepaid-ack", env.token));
  order.push("applied:" + env.db.documents.get("users/user-1").safeBrowsePass.planKind);
  assert.deepEqual(order, ["acknowledge", "applied:prepaid"]);
});

test("already acknowledged purchase skips acknowledgement", async () => {
  const env = environment({
    responses: [purchase("SUBSCRIPTION_STATE_ACTIVE")],
  });
  await env.processor(event("already-acked"));
  assert.equal(env.ackCalls(), 0);
  assert.equal(env.db.documents.get("users/user-1").plus.active, true);
});

// ---------------------------------------------------------------------------
// 5-7: acknowledgement failure handling
// ---------------------------------------------------------------------------

test("retryable acknowledgement error releases the RTDN claim", async () => {
  const retryableError = new Error("ack unavailable");
  retryableError.retryable = true;
  const env = environment({
    responses: [purchase("SUBSCRIPTION_STATE_ACTIVE", FUTURE, {
      acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING",
    })],
    acknowledgePurchase: async () => {
      throw retryableError;
    },
  });
  await assert.rejects(env.processor(event("ack-retry")), /ack unavailable/);
  assert.equal(env.db.documents.get(`playRtdnEvents/${hash("ack-retry")}`).status, "retryable");
});

test("permanent acknowledgement error writes no entitlement", async () => {
  const permanentError = new Error("ack rejected");
  const env = environment({
    responses: [purchase("SUBSCRIPTION_STATE_ACTIVE", FUTURE, {
      acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING",
    })],
    acknowledgePurchase: async () => {
      throw permanentError;
    },
  });
  await env.processor(event("ack-permanent"));
  assert.equal(env.db.documents.get(`playRtdnEvents/${hash("ack-permanent")}`).outcome, "acknowledgement_rejected");
});

test("no entitlement is written before acknowledgement", async () => {
  const permanentError = new Error("ack rejected");
  const env = environment({
    responses: [purchase("SUBSCRIPTION_STATE_ACTIVE", FUTURE, {
      acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING",
    })],
    acknowledgePurchase: async () => {
      throw permanentError;
    },
  });
  await env.processor(event("ack-no-write"));
  // The seeded pre-existing token/user documents must remain exactly as seeded.
  assert.equal(env.db.documents.get(`playPurchaseTokens/${env.tokenHash}`).uid, "user-1");
  assert.equal(env.db.documents.get("users/user-1").plus.active, true);
});

// ---------------------------------------------------------------------------
// 8-12: field isolation and token storage
// ---------------------------------------------------------------------------

test("active Pass writes only safeBrowsePass", async () => {
  const env = environment({
    token: "pass-field-token",
    uid: "user-1",
    entitlementKind: ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
    responses: [passPurchase("SUBSCRIPTION_STATE_ACTIVE")],
  });
  await env.processor(event("pass-only", env.token));
  assert.equal(env.db.documents.get("users/user-1").safeBrowsePass.active, true);
  assert.equal(Object.hasOwn(env.db.documents.get("users/user-1"), "plus"), false);
});

test("active Plus writes only plus", async () => {
  const env = environment({responses: [purchase("SUBSCRIPTION_STATE_ACTIVE")]});
  await env.processor(event("plus-only"));
  const user = env.db.documents.get("users/user-1");
  assert.equal(user.plus.active, true);
});

test("token document stores entitlementKind", async () => {
  const env = environment({
    token: "kind-store-token",
    uid: "user-1",
    entitlementKind: ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
    responses: [passPurchase("SUBSCRIPTION_STATE_ACTIVE")],
  });
  await env.processor(event("kind-store", env.token));
  assert.equal(
      env.db.documents.get(`playPurchaseTokens/${env.tokenHash}`).entitlementKind,
      ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
  );
});

test("token document stores basePlanId", async () => {
  const env = environment({
    token: "baseplan-store-token",
    uid: "user-1",
    entitlementKind: ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
    responses: [passPurchase("SUBSCRIPTION_STATE_ACTIVE")],
  });
  await env.processor(event("baseplan-store", env.token));
  assert.equal(
      env.db.documents.get(`playPurchaseTokens/${env.tokenHash}`).basePlanId,
      "monthly",
  );
});

test("token document stores planKind", async () => {
  const env = environment({
    token: "plankind-store-token",
    uid: "user-1",
    entitlementKind: ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
    responses: [prepaidPassPurchase("SUBSCRIPTION_STATE_ACTIVE")],
  });
  await env.processor(event("plankind-store", env.token));
  assert.equal(
      env.db.documents.get(`playPurchaseTokens/${env.tokenHash}`).planKind,
      "prepaid",
  );
});

// ---------------------------------------------------------------------------
// 13-18: cross-user / cross-kind / legacy migration
// ---------------------------------------------------------------------------

test("cross-user linked token rejected", async () => {
  const current = "current-a";
  const linked = "linked-b";
  const currentHash = hash(current);
  const linkedHash = hash(linked);
  const env = environment({
    token: current,
    uid: null,
    seed: {
      [`playPurchaseTokens/${currentHash}`]: {uid: "user-a", entitlementKind: ENTITLEMENT_KIND.PLUS},
      [`playPurchaseTokens/${linkedHash}`]: {uid: "user-b", entitlementKind: ENTITLEMENT_KIND.PLUS, active: true},
      "users/user-a": {plus: {active: true, marker: "a"}},
      "users/user-b": {plus: {active: true, marker: "b"}},
    },
    responses: [purchase("SUBSCRIPTION_STATE_ACTIVE", FUTURE, {
      linkedPurchaseToken: linked,
    })],
  });
  await env.processor(event("conflict", current));
  assert.equal(env.db.documents.get("users/user-a").plus.marker, "a");
  assert.equal(env.db.documents.get("users/user-b").plus.marker, "b");
  assert.equal(env.db.documents.get(`playRtdnEvents/${hash("conflict")}`).outcome,
      "cross_user_token_conflict");
});

test("same-user cross-kind linked token rejected", async () => {
  const current = "same-user-current";
  const linked = "same-user-linked";
  const currentHash = hash(current);
  const linkedHash = hash(linked);
  const env = environment({
    token: current,
    uid: null,
    seed: {
      [`playPurchaseTokens/${currentHash}`]: {uid: "user-1", entitlementKind: ENTITLEMENT_KIND.SAFE_BROWSE_PASS},
      [`playPurchaseTokens/${linkedHash}`]: {uid: "user-1", entitlementKind: ENTITLEMENT_KIND.PLUS},
      "users/user-1": {plus: {active: true}, safeBrowsePass: {active: true}},
    },
    responses: [passPurchase("SUBSCRIPTION_STATE_ACTIVE", FUTURE, {
      linkedPurchaseToken: linked,
    })],
  });
  await env.processor(event("same-user-cross-kind", current));
  assert.equal(env.db.documents.get(`playRtdnEvents/${hash("same-user-cross-kind")}`).outcome,
      "cross_entitlement_token_conflict");
});

test("same-kind linked token superseded", async () => {
  const oldToken = "old-pass-token";
  const newToken = "new-pass-token";
  const oldHash = hash(oldToken);
  const newHash = hash(newToken);
  const env = environment({
    token: newToken,
    uid: null,
    seed: {
      [`playPurchaseTokens/${oldHash}`]: {uid: "owner", entitlementKind: ENTITLEMENT_KIND.SAFE_BROWSE_PASS},
      "users/owner": {safeBrowsePass: {active: true, purchaseTokenHash: oldHash}},
    },
    responses: [passPurchase("SUBSCRIPTION_STATE_ACTIVE", FUTURE, {
      linkedPurchaseToken: oldToken,
    })],
  });
  await env.processor(event("replacement-pass", newToken));
  assert.equal(env.db.documents.get(`playPurchaseTokens/${newHash}`).uid, "owner");
  assert.equal(env.db.documents.get(`playPurchaseTokens/${oldHash}`).active, false);
  assert.equal(env.db.documents.get(`playPurchaseTokens/${oldHash}`).supersededByTokenHash, newHash);
  assert.equal(env.db.documents.get("users/owner").safeBrowsePass.purchaseTokenHash, newHash);
});

test("legacy Plus token migrates", async () => {
  const env = environment({
    seed: {
      [`playPurchaseTokens/${hash("purchase-token")}`]: {uid: "user-1", productId: PLUS_MONTHLY_PRODUCT_ID},
      "users/user-1": {plus: {active: true, purchaseTokenHash: hash("purchase-token")}},
    },
    responses: [purchase("SUBSCRIPTION_STATE_ACTIVE")],
  });
  await env.processor(event("legacy-plus"));
  assert.equal(
      env.db.documents.get(`playPurchaseTokens/${env.tokenHash}`).entitlementKind,
      ENTITLEMENT_KIND.PLUS,
  );
});

test("legacy obsolete Pass token migrates", async () => {
  const env = environment({
    uid: null,
    seed: {
      [`playPurchaseTokens/${hash("purchase-token")}`]: {uid: "user-1", productId: "safe_browse_pass_monthly"},
      "users/user-1": {safeBrowsePass: {active: true, purchaseTokenHash: hash("purchase-token")}},
    },
    responses: [passPurchase("SUBSCRIPTION_STATE_ACTIVE")],
  });
  await env.processor(event("legacy-pass"));
  assert.equal(
      env.db.documents.get(`playPurchaseTokens/${env.tokenHash}`).entitlementKind,
      ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
  );
});

test("unknown legacy token fails closed", async () => {
  const env = environment({
    uid: null,
    seed: {
      [`playPurchaseTokens/${hash("purchase-token")}`]: {uid: "user-1", productId: "totally_unknown_product"},
      "users/user-1": {plus: {active: true}},
    },
    responses: [purchase("SUBSCRIPTION_STATE_ACTIVE")],
  });
  await env.processor(event("unknown-legacy"));
  assert.equal(
      env.db.documents.get(`playRtdnEvents/${hash("unknown-legacy")}`).outcome,
      "cross_entitlement_token_conflict",
  );
});

// ---------------------------------------------------------------------------
// 19-22: voided-purchase isolation
// ---------------------------------------------------------------------------

test("voided Plus revokes only Plus", async () => {
  const env = environment();
  const input = event("voided-plus");
  delete input.data.message.json.subscriptionNotification;
  input.data.message.json.voidedPurchaseNotification = {purchaseToken: env.token};
  await env.processor(input);
  assert.equal(env.db.documents.get(`playPurchaseTokens/${env.tokenHash}`).active, false);
  assert.equal(env.db.documents.get("users/user-1").plus.active, false);
});

test("voided Pass revokes only Pass", async () => {
  const passToken = "pass-voided-token";
  const passHash = hash(passToken);
  const env = environment({
    token: passToken,
    uid: null,
    seed: {
      [`playPurchaseTokens/${passHash}`]: {uid: "user-1", entitlementKind: ENTITLEMENT_KIND.SAFE_BROWSE_PASS},
      "users/user-1": {
        plus: {active: true, marker: "untouched"},
        safeBrowsePass: {active: true, purchaseTokenHash: passHash},
      },
    },
  });
  const input = event("voided-pass");
  delete input.data.message.json.subscriptionNotification;
  input.data.message.json.voidedPurchaseNotification = {purchaseToken: passToken};
  await env.processor(input);
  assert.equal(env.db.documents.get(`playPurchaseTokens/${passHash}`).active, false);
  assert.equal(env.db.documents.get("users/user-1").safeBrowsePass.active, false);
  assert.equal(env.db.documents.get("users/user-1").plus.marker, "untouched");
  assert.equal(env.db.documents.get("users/user-1").plus.active, true);
});

test("unknown-kind voided token changes neither field", async () => {
  const unknownToken = "unknown-kind-token";
  const unknownHash = hash(unknownToken);
  const env = environment({
    token: unknownToken,
    uid: null,
    seed: {
      [`playPurchaseTokens/${unknownHash}`]: {uid: "user-1"},
      "users/user-1": {plus: {active: true}, safeBrowsePass: {active: true}},
    },
  });
  const input = event("voided-unknown");
  delete input.data.message.json.subscriptionNotification;
  input.data.message.json.voidedPurchaseNotification = {purchaseToken: unknownToken};
  await env.processor(input);
  assert.equal(env.db.documents.get("users/user-1").plus.active, true);
  assert.equal(env.db.documents.get("users/user-1").safeBrowsePass.active, true);
  assert.equal(
      env.db.documents.get(`playRtdnEvents/${hash("voided-unknown")}`).outcome,
      "voided_unknown_entitlement_kind",
  );
});

test("superseded voided token leaves newer entitlement active", async () => {
  const oldToken = "void-old";
  const oldHash = hash(oldToken);
  const newHash = hash("void-new");
  const env = environment({
    token: oldToken,
    uid: null,
    seed: {
      [`playPurchaseTokens/${oldHash}`]: {uid: "owner", entitlementKind: ENTITLEMENT_KIND.PLUS, active: true, productId: PLUS_MONTHLY_PRODUCT_ID},
      "users/owner": {plus: {active: true, purchaseTokenHash: newHash}},
    },
  });
  const input = event("void-superseded");
  delete input.data.message.json.subscriptionNotification;
  input.data.message.json.voidedPurchaseNotification = {purchaseToken: oldToken};
  await env.processor(input);
  assert.equal(env.db.documents.get(`playPurchaseTokens/${oldHash}`).active, false);
  assert.equal(env.db.documents.get("users/owner").plus.active, true);
  assert.equal(env.db.documents.get(`playRtdnEvents/${hash("void-superseded")}`).outcome,
      "voided_superseded_token");
});

// ---------------------------------------------------------------------------
// 23-28: pending-purchase-cancelled handling
// ---------------------------------------------------------------------------

test("pending-purchase-cancelled initial transaction grants nothing", async () => {
  const newToken = "cancelled-new";
  const oldToken = "cancelled-old";
  const oldHash = hash(oldToken);
  const env = environment({
    token: newToken,
    uid: null,
    seed: {
      [`playPurchaseTokens/${oldHash}`]: {uid: "owner", entitlementKind: ENTITLEMENT_KIND.PLUS},
      "users/owner": {plus: {active: true, productId: PLUS_MONTHLY_PRODUCT_ID, expiryTimeMillis: Date.parse(FUTURE), purchaseTokenHash: oldHash}},
    },
    responses: [purchase("SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED", FUTURE, {
      linkedPurchaseToken: oldToken,
    })],
    linkedResponses: [purchase("SUBSCRIPTION_STATE_ACTIVE", FUTURE)],
  });
  await env.processor(event("pending-cancel-initial", newToken));
  const newTokenHash = hash(newToken);
  assert.equal(env.db.documents.get(`playPurchaseTokens/${newTokenHash}`).active, false);
});

test("pending prepaid top-up preserves old Pass entitlement", async () => {
  const newToken = "topup-new";
  const oldToken = "topup-old";
  const oldHash = hash(oldToken);
  const env = environment({
    token: newToken,
    uid: null,
    seed: {
      [`playPurchaseTokens/${oldHash}`]: {uid: "owner", entitlementKind: ENTITLEMENT_KIND.SAFE_BROWSE_PASS},
      "users/owner": {safeBrowsePass: {active: true, productId: SAFE_BROWSE_PASS_PRODUCT_ID, basePlanId: "prepaid-30", planKind: "prepaid", expiryTimeMillis: Date.parse(FUTURE), purchaseTokenHash: oldHash}},
    },
    responses: [prepaidPassPurchase("SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED", FUTURE, {
      linkedPurchaseToken: oldToken,
    })],
    linkedResponses: [prepaidPassPurchase("SUBSCRIPTION_STATE_ACTIVE", FUTURE)],
  });
  await env.processor(event("pending-topup-cancel", newToken));
  assert.equal(env.db.documents.get("users/owner").safeBrowsePass.active, true);
  assert.equal(env.db.documents.get("users/owner").safeBrowsePass.planKind, "prepaid");
});

test("pending Plus change preserves old Plus entitlement", async () => {
  const newToken = "plus-change-new";
  const oldToken = "plus-change-old";
  const oldHash = hash(oldToken);
  const env = environment({
    token: newToken,
    uid: null,
    seed: {
      [`playPurchaseTokens/${oldHash}`]: {uid: "owner", entitlementKind: ENTITLEMENT_KIND.PLUS},
      "users/owner": {plus: {active: true, productId: PLUS_MONTHLY_PRODUCT_ID, expiryTimeMillis: Date.parse(FUTURE), purchaseTokenHash: oldHash}},
    },
    responses: [purchase("SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED", FUTURE, {
      linkedPurchaseToken: oldToken,
    })],
    linkedResponses: [purchase("SUBSCRIPTION_STATE_ACTIVE", FUTURE)],
  });
  await env.processor(event("pending-plus-cancel", newToken));
  assert.equal(env.db.documents.get("users/owner").plus.active, true);
});

test("pending cancellation never supersedes the linked token", async () => {
  const newToken = "no-supersede-new";
  const oldToken = "no-supersede-old";
  const oldHash = hash(oldToken);
  const env = environment({
    token: newToken,
    uid: null,
    seed: {
      [`playPurchaseTokens/${oldHash}`]: {uid: "owner", entitlementKind: ENTITLEMENT_KIND.PLUS},
      "users/owner": {plus: {active: true, productId: PLUS_MONTHLY_PRODUCT_ID, expiryTimeMillis: Date.parse(FUTURE), purchaseTokenHash: oldHash}},
    },
    responses: [purchase("SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED", FUTURE, {
      linkedPurchaseToken: oldToken,
    })],
    linkedResponses: [purchase("SUBSCRIPTION_STATE_ACTIVE", FUTURE)],
  });
  await env.processor(event("no-supersede", newToken));
  assert.equal(Object.hasOwn(env.db.documents.get(`playPurchaseTokens/${oldHash}`), "supersededByTokenHash"), false);
});

test("pending cancellation never uses the cancelled expiry", async () => {
  const newToken = "expiry-new";
  const oldToken = "expiry-old";
  const oldHash = hash(oldToken);
  const oldExpiry = "2027-06-01T00:00:00.000Z";
  const env = environment({
    token: newToken,
    uid: null,
    seed: {
      [`playPurchaseTokens/${oldHash}`]: {uid: "owner", entitlementKind: ENTITLEMENT_KIND.PLUS},
      "users/owner": {plus: {active: true, productId: PLUS_MONTHLY_PRODUCT_ID, expiryTimeMillis: Date.parse(oldExpiry), purchaseTokenHash: oldHash}},
    },
    // The cancelled new purchase carries a totally different (irrelevant) expiry.
    responses: [purchase("SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED", "2030-01-01T00:00:00.000Z", {
      linkedPurchaseToken: oldToken,
    })],
    linkedResponses: [purchase("SUBSCRIPTION_STATE_ACTIVE", oldExpiry)],
  });
  await env.processor(event("no-cancelled-expiry", newToken));
  assert.equal(env.db.documents.get("users/owner").plus.expiryTimeMillis, Date.parse(oldExpiry));
});

test("pending cancellation cross-kind link rejected", async () => {
  const newToken = "cross-kind-cancel-new";
  const oldToken = "cross-kind-cancel-old";
  const oldHash = hash(oldToken);
  const env = environment({
    token: newToken,
    uid: null,
    seed: {
      [`playPurchaseTokens/${oldHash}`]: {uid: "owner", entitlementKind: ENTITLEMENT_KIND.PLUS},
      "users/owner": {plus: {active: true}},
    },
    // The new (cancelled) purchase is a Pass purchase, but its linked token
    // verifies as a Plus purchase -- entitlement kinds must never cross.
    responses: [passPurchase("SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED", FUTURE, {
      linkedPurchaseToken: oldToken,
    })],
    linkedResponses: [purchase("SUBSCRIPTION_STATE_ACTIVE", FUTURE)],
  });
  await env.processor(event("pending-cross-kind", newToken));
  assert.equal(
      env.db.documents.get(`playRtdnEvents/${hash("pending-cross-kind")}`).outcome,
      "cross_entitlement_token_conflict",
  );
});

// ---------------------------------------------------------------------------
// 29-34: general processor behaviour
// ---------------------------------------------------------------------------

test("notification type 20 still verifies Google state", async () => {
  const env = environment({responses: [purchase("SUBSCRIPTION_STATE_EXPIRED", PAST)]});
  await env.processor(event("type-20", env.token, 20));
  assert.equal(env.calls(), 1);
  assert.equal(env.db.documents.get("users/user-1").plus.active, false);
});

test("ambiguous cross-family verified purchase is rejected", async () => {
  const env = environment({
    responses: [{
      subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
      acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
      lineItems: [
        autoRenewingLineItem(PLUS_MONTHLY_PRODUCT_ID, FUTURE),
        autoRenewingLineItem(SAFE_BROWSE_PASS_PRODUCT_ID, FUTURE),
      ],
    }],
  });
  await env.processor(event("ambiguous"));
  assert.equal(
      env.db.documents.get(`playRtdnEvents/${hash("ambiguous")}`).outcome,
      "ambiguous_entitlement_kind",
  );
});

test("duplicate completed event remains idempotent", async () => {
  const env = environment();
  const input = event("duplicate");
  await env.processor(input);
  await env.processor(input);
  assert.equal(env.calls(), 1);
  assert.equal(env.db.documents.get(`playRtdnEvents/${hash("duplicate")}`).attemptCount, 1);
});

test("retryable Play verification remains retryable", async () => {
  const retryable = new Error("temporary");
  retryable.retryable = true;
  const env = environment({responses: [
    retryable,
    purchase("SUBSCRIPTION_STATE_ACTIVE"),
  ]});
  const input = event("retryable");
  await assert.rejects(env.processor(input), /temporary/);
  assert.equal(env.db.documents.get(`playRtdnEvents/${hash("retryable")}`).status,
      "retryable");
  await env.processor(input);
  assert.equal(env.calls(), 2);
  assert.equal(env.db.documents.get(`playRtdnEvents/${hash("retryable")}`).status,
      "completed");
});

test("raw event ID and purchase token never leak", async () => {
  const rawToken = "DISTINCTIVE_RAW_TOKEN_SECRET";
  const rawId = "DISTINCTIVE_RAW_EVENT_ID";
  const error = new Error(`request contained ${rawToken}`);
  const env = environment({token: rawToken, uid: null, responses: [error]});
  await env.processor(event(rawId, rawToken));
  const serialized = JSON.stringify({
    logs: env.logs,
    documents: [...env.db.documents.entries()],
  });
  assert.equal(serialized.includes(rawToken), false);
  assert.equal(serialized.includes(rawId), false);
  assert.equal(env.db.documents.has(`playRtdnEvents/${rawId}`), false);
});

test("App Check remains enabled on all subscription callables", () => {
  const source = fs.readFileSync(path.join(__dirname, "index.js"), "utf8");
  [
    "verifyPlusSubscription",
    "verifySafeBrowsePassSubscription",
    "checkPlusEntitlement",
    "checkSafeBrowsePassEntitlement",
  ].forEach((name) => {
    const start = source.indexOf(`exports.${name} = onCall(`);
    assert.notEqual(start, -1, `${name} is not exported`);
    const handler = source.slice(start, source.indexOf("async (request)", start));
    assert.match(handler, /enforceAppCheck:\s*true/);
  });
});

// ---------------------------------------------------------------------------
// Pre-existing coverage retained
// ---------------------------------------------------------------------------

test("voided purchase revokes current token and user without Google", async () => {
  const env = environment();
  const input = event("voided");
  delete input.data.message.json.subscriptionNotification;
  input.data.message.json.voidedPurchaseNotification = {purchaseToken: env.token};
  await env.processor(input);
  assert.equal(env.calls(), 0);
  assert.equal(env.db.documents.get(`playPurchaseTokens/${env.tokenHash}`).active, false);
  assert.equal(env.db.documents.get("users/user-1").plus.active, false);
  assert.equal(env.db.documents.get("users/user-1").plus.subscriptionState,
      "SUBSCRIPTION_STATE_REVOKED");
});

test("malformed subscription notification completes without Google", async () => {
  const env = environment();
  await env.processor(event("malformed", "", "not-an-integer"));
  assert.equal(env.calls(), 0);
  assert.equal(env.db.documents.get(`playRtdnEvents/${hash("malformed")}`).status,
      "completed");
});

test("unsupported verified product completes without entitlement transition", async () => {
  const env = environment({responses: [{
    subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
    acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    lineItems: [{productId: "unsupported", expiryTime: FUTURE}],
  }]});
  const input = event("unsupported");
  await env.processor(input);
  assert.equal(env.db.documents.get("users/user-1").plus.purchaseTokenHash,
      env.tokenHash);
  assert.equal(env.db.documents.get(`playRtdnEvents/${hash("unsupported")}`).outcome,
      "unsupported_product");
});

test("CloudEvent ID is used when PubSub message ID is absent", async () => {
  const env = environment();
  const input = event("unused");
  delete input.data.message.messageId;
  input.id = "fallback-id";
  await env.processor(input);
  assert.equal(env.db.documents.get(`playRtdnEvents/${hash("fallback-id")}`).status,
      "completed");
});

test("missing both identifiers creates no records or Google calls", async () => {
  const env = environment();
  const input = event("unused");
  delete input.data.message.messageId;
  delete input.id;
  await env.processor(input);
  assert.equal(env.calls(), 0);
  assert.equal([...env.db.documents.keys()].some((key) =>
    key.startsWith("playRtdnEvents/")), false);
});

test("active processing lease throws without contacting Google", async () => {
  const id = "leased";
  const env = environment({seed: {
    [`playRtdnEvents/${hash(id)}`]: {
      status: "processing",
      claimId: "other",
      attemptCount: 1,
      leaseExpiresAt: new Date(NOW + 1000),
    },
  }});
  await assert.rejects(env.processor(event(id)), /already being processed/);
  assert.equal(env.calls(), 0);
});

test("expired processing lease is reclaimed and attempt increments", async () => {
  const id = "expired-lease";
  const env = environment({seed: {
    [`playRtdnEvents/${hash(id)}`]: {
      status: "processing",
      claimId: "old",
      attemptCount: 3,
      leaseExpiresAt: {toMillis: () => NOW - 1},
    },
  }});
  await env.processor(event(id));
  assert.equal(env.calls(), 1);
  assert.equal(env.db.documents.get(`playRtdnEvents/${hash(id)}`).attemptCount, 4);
});

test("permanent Google rejection completes without retry loop", async () => {
  const env = environment({responses: [new Error("permanent")]});
  const input = event("permanent");
  await env.processor(input);
  await env.processor(input);
  assert.equal(env.calls(), 1);
  assert.equal(env.db.documents.get(`playRtdnEvents/${hash("permanent")}`).outcome,
      "play_verification_rejected");
});

test("stale claim fencing blocks entitlement and completion writes", async () => {
  const env = environment({uid: null});
  const eventHash = hash("stale");
  await env.store.claimEvent(eventHash, "old-claim", NOW);
  env.db.documents.set(`playRtdnEvents/${eventHash}`, {
    status: "processing",
    claimId: "new-claim",
    leaseExpiresAt: new Date(NOW + 1000),
  });
  const result = await env.store.applySubscriptionAndComplete({
    eventHash,
    claimId: "old-claim",
    tokenHash: hash("stale-token"),
    linkedTokenHash: null,
    userField: userFieldForEntitlement(ENTITLEMENT_KIND.PLUS),
    entitlement: {
      active: true,
      productId: PLUS_MONTHLY_PRODUCT_ID,
      entitlementKind: ENTITLEMENT_KIND.PLUS,
      basePlanId: "monthly",
      planKind: "autoRenewing",
      subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
      expiryTimeMillis: Date.parse(FUTURE),
    },
    notificationType: 2,
    nowMillis: NOW,
  });
  assert.equal(result, "stale_claim");
  assert.equal(env.db.documents.has(`playPurchaseTokens/${hash("stale-token")}`), false);
  assert.equal(env.db.documents.get(`playRtdnEvents/${eventHash}`).claimId, "new-claim");
});

test("unowned RTDN token persists without fake UID", async () => {
  const env = environment({uid: null});
  await env.processor(event("unowned"));
  const token = env.db.documents.get(`playPurchaseTokens/${env.tokenHash}`);
  assert.equal(Object.hasOwn(token, "uid"), false);
  assert.equal(token.active, true);
});

test("expiry equal to now is inactive", () => {
  const result = deriveRtdnEntitlement(
      purchase("SUBSCRIPTION_STATE_CANCELED", new Date(NOW).toISOString()),
      productDefinition,
      NOW,
  );
  assert.equal(result.active, false);
});

test("supported product comes only from verified line items", async () => {
  const env = environment();
  const input = event("verified-product");
  await env.processor(input);
  assert.equal(env.db.documents.get(`playPurchaseTokens/${env.tokenHash}`).productId,
      PLUS_MONTHLY_PRODUCT_ID);
});
