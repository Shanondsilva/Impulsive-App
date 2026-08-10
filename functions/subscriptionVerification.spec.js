/* eslint-disable require-jsdoc, max-len */
process.env.NODE_ENV = "test";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");
const path = require("path");

const {__subscriptionTest} = require("./index");
const {
  ENTITLEMENT_KIND,
  PLUS_MONTHLY_PRODUCT_ID,
  SAFE_BROWSE_PASS_PRODUCT_ID,
  requireProductForEntitlement,
} = require("./subscriptionCatalog");

const FUTURE = "2099-02-01T00:00:00.000Z";
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
}

// Transaction-capable Firestore fake, matching the one already proven out in
// subscriptionRtdn.spec.js -- saveEntitlement() runs inside runTransaction().
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

// Simple (non-transactional) fake for checkEntitlementForRequest, which only
// ever does a single doc get + a single merge set.
class FakeUserDocFirestore {
  constructor(seed = {}) {
    this.documents = new Map(Object.entries(seed).map(([key, value]) =>
      [key, clone(value)]));
  }

  collection(name) {
    return {
      doc: (id) => ({
        get: async () => new Snapshot(clone(this.documents.get(`${name}/${id}`))),
        set: async (value, options) => {
          const current = options && options.merge ?
            clone(this.documents.get(`${name}/${id}`) || {}) : {};
          this.documents.set(`${name}/${id}`, merge(current, value));
        },
      }),
    };
  }
}

function clone(value) {
  if (value === undefined) return undefined;
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
        !Array.isArray(value) && !value.sentinel) {
      target[key] = merge(clone(target[key] || {}), value);
    } else {
      target[key] = clone(value);
    }
  });
  return target;
}

function request(uid = "user-a", data = undefined) {
  return {auth: uid ? {uid} : undefined, data};
}

function activePlusPurchase(productId = PLUS_MONTHLY_PRODUCT_ID) {
  return {
    subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
    acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    lineItems: [{
      productId,
      expiryTime: FUTURE,
      offerDetails: {basePlanId: "monthly"},
      autoRenewingPlan: {autoRenewEnabled: true},
    }],
  };
}

function activeAutoRenewingPassPurchase() {
  return {
    subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
    acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    lineItems: [{
      productId: SAFE_BROWSE_PASS_PRODUCT_ID,
      expiryTime: FUTURE,
      offerDetails: {basePlanId: "monthly"},
      autoRenewingPlan: {autoRenewEnabled: true},
    }],
  };
}

function activePrepaidPassPurchase() {
  return {
    subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
    acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    lineItems: [{
      productId: SAFE_BROWSE_PASS_PRODUCT_ID,
      expiryTime: FUTURE,
      offerDetails: {basePlanId: "prepaid-30"},
      prepaidPlan: {allowExtendAfterTime: FUTURE},
    }],
  };
}

async function assertRejectsWithCode(action, code) {
  await assert.rejects(action, (error) => {
    assert.equal(error.code, code);
    return true;
  });
}

function newSave(db) {
  return async (uid, token, purchase, entitlement, definition) =>
    __subscriptionTest.saveEntitlement(
        uid, token, purchase, entitlement, definition, db,
        {serverTimestamp: () => SERVER_TIME},
    );
}

// ---------------------------------------------------------------------------
// parseInput / entitlement-kind gating
// ---------------------------------------------------------------------------

test("Pass callable accepts only safe_browse_pass", () => {
  const input = __subscriptionTest.parseInput(
      {productId: SAFE_BROWSE_PASS_PRODUCT_ID, purchaseToken: "t"},
      ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
  );
  assert.equal(input.productId, SAFE_BROWSE_PASS_PRODUCT_ID);
  assert.equal(input.definition.entitlementKind, ENTITLEMENT_KIND.SAFE_BROWSE_PASS);
});

test("Plus callable rejects safe_browse_pass", () => {
  assert.throws(
      () => __subscriptionTest.parseInput(
          {productId: SAFE_BROWSE_PASS_PRODUCT_ID, purchaseToken: "t"},
          ENTITLEMENT_KIND.PLUS,
      ),
      (error) => error.code === "invalid-argument",
  );
});

test("Pass callable rejects Plus", () => {
  assert.throws(
      () => __subscriptionTest.parseInput(
          {productId: PLUS_MONTHLY_PRODUCT_ID, purchaseToken: "t"},
          ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
      ),
      (error) => error.code === "invalid-argument",
  );
});

test("obsolete Pass IDs rejected", () => {
  ["safe_browse_pass_monthly", "safe_browse_pass_prepaid_30_day"].forEach((productId) => {
    assert.throws(
        () => __subscriptionTest.parseInput(
            {productId, purchaseToken: "t"},
            ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
        ),
        (error) => error.code === "invalid-argument",
    );
  });
});

// ---------------------------------------------------------------------------
// verifySubscriptionPurchase: response shape
// ---------------------------------------------------------------------------

test("verify response contains basePlanId", async () => {
  const result = await __subscriptionTest.verifySubscriptionPurchase(
      request("user-a", {productId: SAFE_BROWSE_PASS_PRODUCT_ID, purchaseToken: "t"}),
      ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
      "wrong product",
      async () => activeAutoRenewingPassPurchase(),
      async () => true,
      async () => {},
  );
  assert.equal(result.basePlanId, "monthly");
});

test("verify response contains planKind", async () => {
  const result = await __subscriptionTest.verifySubscriptionPurchase(
      request("user-a", {productId: SAFE_BROWSE_PASS_PRODUCT_ID, purchaseToken: "t"}),
      ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
      "wrong product",
      async () => activeAutoRenewingPassPurchase(),
      async () => true,
      async () => {},
  );
  assert.equal(result.planKind, "autoRenewing");
});

test("prepaid maps prepaid", async () => {
  const result = await __subscriptionTest.verifySubscriptionPurchase(
      request("user-a", {productId: SAFE_BROWSE_PASS_PRODUCT_ID, purchaseToken: "t"}),
      ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
      "wrong product",
      async () => activePrepaidPassPurchase(),
      async () => true,
      async () => {},
  );
  assert.equal(result.planKind, "prepaid");
});

test("auto-renewing maps autoRenewing", async () => {
  const result = await __subscriptionTest.verifySubscriptionPurchase(
      request("user-a", {productId: SAFE_BROWSE_PASS_PRODUCT_ID, purchaseToken: "t"}),
      ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
      "wrong product",
      async () => activeAutoRenewingPassPurchase(),
      async () => true,
      async () => {},
  );
  assert.equal(result.planKind, "autoRenewing");
});

test("missing metadata rejected", async () => {
  const purchaseWithNoPlan = {
    subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
    acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
    lineItems: [{
      productId: SAFE_BROWSE_PASS_PRODUCT_ID,
      expiryTime: FUTURE,
      offerDetails: {basePlanId: "monthly"},
    }],
  };
  await assertRejectsWithCode(
      () => __subscriptionTest.verifySubscriptionPurchase(
          request("user-a", {productId: SAFE_BROWSE_PASS_PRODUCT_ID, purchaseToken: "t"}),
          ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
          "wrong product",
          async () => purchaseWithNoPlan,
          async () => true,
          async () => {},
      ),
      "failed-precondition",
  );
});

test("pending purchase rejected", async () => {
  const pendingPurchase = {
    subscriptionState: "SUBSCRIPTION_STATE_PENDING",
    acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING",
    lineItems: [{
      productId: SAFE_BROWSE_PASS_PRODUCT_ID,
      expiryTime: FUTURE,
      offerDetails: {basePlanId: "monthly"},
      autoRenewingPlan: {autoRenewEnabled: true},
    }],
  };
  await assertRejectsWithCode(
      () => __subscriptionTest.verifySubscriptionPurchase(
          request("user-a", {productId: SAFE_BROWSE_PASS_PRODUCT_ID, purchaseToken: "t"}),
          ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
          "wrong product",
          async () => pendingPurchase,
          async () => true,
          async () => {},
      ),
      "failed-precondition",
  );
});

// ---------------------------------------------------------------------------
// Acknowledgement ordering
// ---------------------------------------------------------------------------

test("acknowledgement occurs before save", async () => {
  const order = [];
  await __subscriptionTest.verifySubscriptionPurchase(
      request("user-a", {productId: SAFE_BROWSE_PASS_PRODUCT_ID, purchaseToken: "t"}),
      ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
      "wrong product",
      async () => activeAutoRenewingPassPurchase(),
      async () => {
        order.push("acknowledge"); return true;
      },
      async () => {
        order.push("save");
      },
  );
  assert.deepEqual(order, ["acknowledge", "save"]);
});

test("acknowledgement failure writes no entitlement", async () => {
  let saveCalled = false;
  await assertRejectsWithCode(
      () => __subscriptionTest.verifySubscriptionPurchase(
          request("user-a", {productId: SAFE_BROWSE_PASS_PRODUCT_ID, purchaseToken: "t"}),
          ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
          "wrong product",
          async () => activeAutoRenewingPassPurchase(),
          async () => {
            const error = new Error("ack failed"); error.code = "failed-precondition"; throw error;
          },
          async () => {
            saveCalled = true;
          },
      ),
      "failed-precondition",
  );
  assert.equal(saveCalled, false);
});

// ---------------------------------------------------------------------------
// Field isolation
// ---------------------------------------------------------------------------

test("active Pass writes safeBrowsePass only", async () => {
  const db = new FakeFirestore();
  await __subscriptionTest.verifySubscriptionPurchase(
      request("user-a", {productId: SAFE_BROWSE_PASS_PRODUCT_ID, purchaseToken: "pass-token"}),
      ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
      "wrong product",
      async () => activeAutoRenewingPassPurchase(),
      async () => true,
      newSave(db),
  );
  assert.equal(db.documents.get("users/user-a").safeBrowsePass.active, true);
  assert.equal(Object.hasOwn(db.documents.get("users/user-a"), "plus"), false);
});

test("active Plus writes plus only", async () => {
  const db = new FakeFirestore();
  await __subscriptionTest.verifySubscriptionPurchase(
      request("user-a", {productId: PLUS_MONTHLY_PRODUCT_ID, purchaseToken: "plus-token"}),
      ENTITLEMENT_KIND.PLUS,
      "wrong product",
      async () => activePlusPurchase(),
      async () => true,
      newSave(db),
  );
  assert.equal(db.documents.get("users/user-a").plus.active, true);
  assert.equal(Object.hasOwn(db.documents.get("users/user-a"), "safeBrowsePass"), false);
});

// ---------------------------------------------------------------------------
// Token document storage
// ---------------------------------------------------------------------------

test("token document stores entitlementKind", async () => {
  const db = new FakeFirestore();
  await __subscriptionTest.verifySubscriptionPurchase(
      request("user-a", {productId: SAFE_BROWSE_PASS_PRODUCT_ID, purchaseToken: "pass-token"}),
      ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
      "wrong product",
      async () => activeAutoRenewingPassPurchase(),
      async () => true,
      newSave(db),
  );
  const tokenHash = require("crypto").createHash("sha256").update("pass-token").digest("hex");
  assert.equal(db.documents.get(`playPurchaseTokens/${tokenHash}`).entitlementKind, ENTITLEMENT_KIND.SAFE_BROWSE_PASS);
});

test("token document stores basePlanId", async () => {
  const db = new FakeFirestore();
  await __subscriptionTest.verifySubscriptionPurchase(
      request("user-a", {productId: SAFE_BROWSE_PASS_PRODUCT_ID, purchaseToken: "pass-token"}),
      ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
      "wrong product",
      async () => activeAutoRenewingPassPurchase(),
      async () => true,
      newSave(db),
  );
  const tokenHash = require("crypto").createHash("sha256").update("pass-token").digest("hex");
  assert.equal(db.documents.get(`playPurchaseTokens/${tokenHash}`).basePlanId, "monthly");
});

test("token document stores planKind", async () => {
  const db = new FakeFirestore();
  await __subscriptionTest.verifySubscriptionPurchase(
      request("user-a", {productId: SAFE_BROWSE_PASS_PRODUCT_ID, purchaseToken: "pass-token"}),
      ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
      "wrong product",
      async () => activeAutoRenewingPassPurchase(),
      async () => true,
      newSave(db),
  );
  const tokenHash = require("crypto").createHash("sha256").update("pass-token").digest("hex");
  assert.equal(db.documents.get(`playPurchaseTokens/${tokenHash}`).planKind, "autoRenewing");
});

// ---------------------------------------------------------------------------
// Cross-kind and cross-user token protection
// ---------------------------------------------------------------------------

test("current cross-kind token rejected", async () => {
  const crypto = require("crypto");
  const tokenHash = crypto.createHash("sha256").update("shared-token").digest("hex");
  const db = new FakeFirestore({
    [`playPurchaseTokens/${tokenHash}`]: {uid: "user-a", entitlementKind: ENTITLEMENT_KIND.PLUS},
  });
  await assertRejectsWithCode(
      () => __subscriptionTest.verifySubscriptionPurchase(
          request("user-a", {productId: SAFE_BROWSE_PASS_PRODUCT_ID, purchaseToken: "shared-token"}),
          ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
          "wrong product",
          async () => activeAutoRenewingPassPurchase(),
          async () => true,
          newSave(db),
      ),
      "permission-denied",
  );
});

test("linked cross-kind token rejected", async () => {
  const crypto = require("crypto");
  const linkedTokenHash = crypto.createHash("sha256").update("old-token").digest("hex");
  const db = new FakeFirestore({
    [`playPurchaseTokens/${linkedTokenHash}`]: {uid: "user-a", entitlementKind: ENTITLEMENT_KIND.PLUS},
  });
  const purchaseWithLink = Object.assign(activeAutoRenewingPassPurchase(), {linkedPurchaseToken: "old-token"});
  await assertRejectsWithCode(
      () => __subscriptionTest.verifySubscriptionPurchase(
          request("user-a", {productId: SAFE_BROWSE_PASS_PRODUCT_ID, purchaseToken: "new-token"}),
          ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
          "wrong product",
          async () => purchaseWithLink,
          async () => true,
          newSave(db),
      ),
      "permission-denied",
  );
});

test("same-kind linked token allowed", async () => {
  const crypto = require("crypto");
  const linkedTokenHash = crypto.createHash("sha256").update("old-pass-token").digest("hex");
  const db = new FakeFirestore({
    [`playPurchaseTokens/${linkedTokenHash}`]: {uid: "user-a", entitlementKind: ENTITLEMENT_KIND.SAFE_BROWSE_PASS},
  });
  const purchaseWithLink = Object.assign(
      activePrepaidPassPurchase(), {linkedPurchaseToken: "old-pass-token"},
  );
  await __subscriptionTest.verifySubscriptionPurchase(
      request("user-a", {productId: SAFE_BROWSE_PASS_PRODUCT_ID, purchaseToken: "new-pass-token"}),
      ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
      "wrong product",
      async () => purchaseWithLink,
      async () => true,
      newSave(db),
  );
  assert.equal(db.documents.get(`playPurchaseTokens/${linkedTokenHash}`).active, false);
  assert.equal(db.documents.get("users/user-a").safeBrowsePass.active, true);
});

test("cross-user token rejected", async () => {
  const crypto = require("crypto");
  const tokenHash = crypto.createHash("sha256").update("owned-token").digest("hex");
  const db = new FakeFirestore({
    [`playPurchaseTokens/${tokenHash}`]: {uid: "user-b", entitlementKind: ENTITLEMENT_KIND.SAFE_BROWSE_PASS},
  });
  await assertRejectsWithCode(
      () => __subscriptionTest.verifySubscriptionPurchase(
          request("user-a", {productId: SAFE_BROWSE_PASS_PRODUCT_ID, purchaseToken: "owned-token"}),
          ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
          "wrong product",
          async () => activeAutoRenewingPassPurchase(),
          async () => true,
          newSave(db),
      ),
      "permission-denied",
  );
});

// ---------------------------------------------------------------------------
// Legacy migration
// ---------------------------------------------------------------------------

test("legacy Plus token migrates to Plus", () => {
  const legacySnapshot = new Snapshot({uid: "user-a", productId: PLUS_MONTHLY_PRODUCT_ID});
  assert.equal(
      __subscriptionTest.entitlementKindFromSnapshot(legacySnapshot),
      ENTITLEMENT_KIND.PLUS,
  );
});

test("legacy obsolete Pass token migrates to Safe Browse Pass", () => {
  ["safe_browse_pass_monthly", "safe_browse_pass_prepaid_30_day"].forEach((productId) => {
    const legacySnapshot = new Snapshot({uid: "user-a", productId});
    assert.equal(
        __subscriptionTest.entitlementKindFromSnapshot(legacySnapshot),
        ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
    );
  });
});

test("unknown legacy product fails closed", () => {
  const unknownSnapshot = new Snapshot({uid: "user-a", productId: "totally_unknown_product"});
  assert.equal(__subscriptionTest.entitlementKindFromSnapshot(unknownSnapshot), null);
});

// ---------------------------------------------------------------------------
// checkEntitlementForRequest
// ---------------------------------------------------------------------------

test("checkEntitlementForRequest rejects unauthenticated requests", async () => {
  await assertRejectsWithCode(
      () => __subscriptionTest.checkEntitlementForRequest(
          request(null), ENTITLEMENT_KIND.PLUS, new FakeUserDocFirestore(),
      ),
      "unauthenticated",
  );
});

test("checkEntitlementForRequest returns inactive when no entitlement document exists", async () => {
  const result = await __subscriptionTest.checkEntitlementForRequest(
      request("user-a", {}), ENTITLEMENT_KIND.SAFE_BROWSE_PASS, new FakeUserDocFirestore(),
  );
  assert.deepEqual(result, {active: false});
});

test("checkEntitlementForRequest never reads the other kind's field", async () => {
  const firestore = new FakeUserDocFirestore({
    "users/user-a": {
      plus: {active: true, productId: PLUS_MONTHLY_PRODUCT_ID, expiryTimeMillis: Date.parse(FUTURE)},
    },
  });
  const result = await __subscriptionTest.checkEntitlementForRequest(
      request("user-a", {}), ENTITLEMENT_KIND.SAFE_BROWSE_PASS, firestore,
  );
  assert.deepEqual(result, {active: false});
});

test("checkSafeBrowsePass returns basePlanId and planKind", async () => {
  const firestore = new FakeUserDocFirestore({
    "users/user-a": {
      safeBrowsePass: {
        active: true,
        productId: SAFE_BROWSE_PASS_PRODUCT_ID,
        basePlanId: "prepaid-30",
        planKind: "prepaid",
        expiryTimeMillis: Date.parse(FUTURE),
      },
    },
  });
  const result = await __subscriptionTest.checkEntitlementForRequest(
      request("user-a", {}), ENTITLEMENT_KIND.SAFE_BROWSE_PASS, firestore,
  );
  assert.equal(result.basePlanId, "prepaid-30");
  assert.equal(result.planKind, "prepaid");
});

test("stale Pass entitlement is inactive at exact expiry", async () => {
  const exactNow = Date.now();
  const firestore = new FakeUserDocFirestore({
    "users/user-a": {
      safeBrowsePass: {
        active: true,
        productId: SAFE_BROWSE_PASS_PRODUCT_ID,
        basePlanId: "monthly",
        planKind: "autoRenewing",
        expiryTimeMillis: exactNow,
      },
    },
  });
  const result = await __subscriptionTest.checkEntitlementForRequest(
      request("user-a", {}), ENTITLEMENT_KIND.SAFE_BROWSE_PASS, firestore,
  );
  assert.equal(result.active, false);
});

// ---------------------------------------------------------------------------
// Raw secret handling
// ---------------------------------------------------------------------------

test("raw purchase token is never stored", async () => {
  const db = new FakeFirestore();
  await __subscriptionTest.verifySubscriptionPurchase(
      request("user-a", {productId: SAFE_BROWSE_PASS_PRODUCT_ID, purchaseToken: "super-secret-token"}),
      ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
      "wrong product",
      async () => activeAutoRenewingPassPurchase(),
      async () => true,
      newSave(db),
  );
  for (const value of db.documents.values()) {
    assert.equal(JSON.stringify(value).includes("super-secret-token"), false);
  }
});

test("raw purchase token is never logged", () => {
  const source = fs.readFileSync(path.join(__dirname, "index.js"), "utf8");
  const ackStart = source.indexOf("async function acknowledgeIfNeeded(");
  const ackEnd = source.indexOf("async function saveEntitlement(", ackStart);
  const ackBody = source.slice(ackStart, ackEnd);
  const loggerErrorCalls = ackBody.match(/logger\.error\("Google Play acknowledgement failed\.",\s*\{[^}]*\}/s);
  assert.notEqual(loggerErrorCalls, null);
  assert.equal(loggerErrorCalls[0].includes("purchaseToken"), false);
});

// ---------------------------------------------------------------------------
// Source-level structural guarantees
// ---------------------------------------------------------------------------

test("both verify callables enforce App Check and are exported", () => {
  const source = fs.readFileSync(path.join(__dirname, "index.js"), "utf8");
  ["verifyPlusSubscription", "verifySafeBrowsePassSubscription"].forEach((name) => {
    const start = source.indexOf(`exports.${name} = onCall(`);
    assert.notEqual(start, -1, `${name} is not exported`);
    const handler = source.slice(start, source.indexOf("async (request)", start));
    assert.match(handler, /enforceAppCheck:\s*true/);
  });
});

test("both check-entitlement callables enforce App Check and are exported", () => {
  const source = fs.readFileSync(path.join(__dirname, "index.js"), "utf8");
  ["checkPlusEntitlement", "checkSafeBrowsePassEntitlement"].forEach((name) => {
    const start = source.indexOf(`exports.${name} = onCall(`);
    assert.notEqual(start, -1, `${name} is not exported`);
    const handler = source.slice(start, source.indexOf("async (request)", start));
    assert.match(handler, /enforceAppCheck:\s*true/);
  });
});

test("index.js never hard-codes an obsolete Safe Browse Pass product ID", () => {
  const source = fs.readFileSync(path.join(__dirname, "index.js"), "utf8");
  assert.equal(source.includes("safe_browse_pass_monthly"), false);
  assert.equal(source.includes("safe_browse_pass_prepaid_30_day"), false);
});

test("saveEntitlement writes the definition-provided field, never a hard-coded plus key", () => {
  const source = fs.readFileSync(path.join(__dirname, "index.js"), "utf8");
  const start = source.indexOf("async function saveEntitlement(");
  const end = source.indexOf("async function assertTokenOwner", start);
  const body = source.slice(start, end);
  assert.match(body, /\[definition\.userField\]:\s*\{/);
  assert.doesNotMatch(body, /\n\s*plus:\s*\{/);
});

test("requireProductForEntitlement still rejects an obsolete Pass id for either kind", () => {
  assert.equal(requireProductForEntitlement("safe_browse_pass_monthly", ENTITLEMENT_KIND.SAFE_BROWSE_PASS), null);
  assert.equal(requireProductForEntitlement("safe_browse_pass_monthly", ENTITLEMENT_KIND.PLUS), null);
});
