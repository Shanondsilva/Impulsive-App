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

const NOW = Date.parse("2026-01-01T00:00:00.000Z");
const FUTURE = "2026-02-01T00:00:00.000Z";
const PAST = "2025-12-01T00:00:00.000Z";
const PRODUCT = "impulsive_plus_monthly";
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

function purchase(state, expiry = FUTURE, additions = {}) {
  return {
    subscriptionState: state,
    lineItems: [{productId: PRODUCT, expiryTime: expiry}],
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
  const seed = {...options.seed};
  if (uid) {
    seed[`playPurchaseTokens/${tokenHash}`] = {uid};
    seed[`users/${uid}`] = {
      plus: {active: true, purchaseTokenHash: tokenHash},
    };
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
  });
  let calls = 0;
  const responses = options.responses || [purchase("SUBSCRIPTION_STATE_ACTIVE")];
  const processor = createRtdnProcessor({
    store,
    verifyPurchase: async () => {
      const response = responses[calls++];
      if (response instanceof Error) throw response;
      return response;
    },
    logger,
    packageName: PACKAGE,
    supportedProductIds: new Set([PRODUCT, "impulsive_plus_yearly"]),
    hashToken: hash,
    now: () => NOW,
    createClaimId: () => `claim-${calls + 1}`,
    isRetryableError: (error) => error.retryable === true,
  });
  return {db, logs, processor, store, token, tokenHash, calls: () => calls};
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

test("revoked subscription RTDN still verifies authoritative state", async () => {
  const env = environment({responses: [purchase("SUBSCRIPTION_STATE_EXPIRED", PAST)]});
  await env.processor(event("revoked", env.token, 12));
  assert.equal(env.calls(), 1);
  assert.equal(env.db.documents.get("users/user-1").plus.active, false);
});

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

test("recovered notification grants only authoritative active state", async () => {
  const env = environment({responses: [purchase("SUBSCRIPTION_STATE_ACTIVE", FUTURE)]});
  await env.processor(event("recovered", env.token, 1));
  assert.equal(env.db.documents.get("users/user-1").plus.active, true);
});

test("replacement token inherits linked-token owner", async () => {
  const oldToken = "old-token";
  const newToken = "new-token";
  const oldHash = hash(oldToken);
  const newHash = hash(newToken);
  const env = environment({
    token: newToken,
    uid: null,
    seed: {
      [`playPurchaseTokens/${oldHash}`]: {uid: "owner"},
      "users/owner": {plus: {active: true, purchaseTokenHash: oldHash}},
    },
    responses: [purchase("SUBSCRIPTION_STATE_ACTIVE", FUTURE, {
      linkedPurchaseToken: oldToken,
    })],
  });
  await env.processor(event("replacement", newToken));
  assert.equal(env.db.documents.get(`playPurchaseTokens/${newHash}`).uid, "owner");
  assert.equal(env.db.documents.get(`playPurchaseTokens/${oldHash}`).active, false);
  assert.equal(env.db.documents.get(`playPurchaseTokens/${oldHash}`).supersededByTokenHash, newHash);
  assert.equal(env.db.documents.get("users/owner").plus.purchaseTokenHash, newHash);
});

test("cross-user linked-token conflict transfers no ownership", async () => {
  const current = "current-a";
  const linked = "linked-b";
  const currentHash = hash(current);
  const linkedHash = hash(linked);
  const env = environment({
    token: current,
    uid: null,
    seed: {
      [`playPurchaseTokens/${currentHash}`]: {uid: "user-a"},
      [`playPurchaseTokens/${linkedHash}`]: {uid: "user-b", active: true},
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
  assert.equal(env.db.documents.get(`playPurchaseTokens/${currentHash}`).uid, "user-a");
  assert.equal(env.db.documents.get(`playRtdnEvents/${hash("conflict")}`).outcome,
      "cross_user_token_conflict");
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
    lineItems: [{productId: "unsupported", expiryTime: FUTURE}],
  }]});
  const input = event("unsupported");
  input.data.message.json.subscriptionNotification.subscriptionId = PRODUCT;
  await env.processor(input);
  assert.equal(env.db.documents.get("users/user-1").plus.purchaseTokenHash,
      env.tokenHash);
  assert.equal(env.db.documents.get(`playRtdnEvents/${hash("unsupported")}`).outcome,
      "unsupported_product");
});

test("duplicate completed RTDN verifies and applies exactly once", async () => {
  const env = environment();
  const input = event("duplicate");
  await env.processor(input);
  await env.processor(input);
  assert.equal(env.calls(), 1);
  assert.equal(env.db.documents.get(`playRtdnEvents/${hash("duplicate")}`).attemptCount, 1);
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

test("retryable Google failure releases claim and succeeds on redelivery", async () => {
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
    entitlement: {
      active: true,
      productId: PRODUCT,
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

test("voiding superseded token leaves newer user entitlement active", async () => {
  const oldToken = "void-old";
  const oldHash = hash(oldToken);
  const newHash = hash("void-new");
  const env = environment({
    token: oldToken,
    uid: null,
    seed: {
      [`playPurchaseTokens/${oldHash}`]: {uid: "owner", active: true},
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

test("unowned RTDN token persists without fake UID", async () => {
  const env = environment({uid: null});
  await env.processor(event("unowned"));
  const token = env.db.documents.get(`playPurchaseTokens/${env.tokenHash}`);
  assert.equal(Object.hasOwn(token, "uid"), false);
  assert.equal(token.active, true);
});

test("raw token and message ID never leak to logs or Firestore", async () => {
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

test("expiry equal to now is inactive", () => {
  const result = deriveRtdnEntitlement(
      purchase("SUBSCRIPTION_STATE_CANCELED", new Date(NOW).toISOString()),
      new Set([PRODUCT]),
      NOW,
  );
  assert.equal(result.active, false);
});

test("supported product comes only from verified line items", async () => {
  const env = environment();
  const input = event("verified-product");
  input.data.message.json.subscriptionNotification.subscriptionId = "unsupported-false-id";
  await env.processor(input);
  assert.equal(env.db.documents.get(`playPurchaseTokens/${env.tokenHash}`).productId,
      PRODUCT);
});

test("all existing callables preserve App Check enforcement", () => {
  const source = fs.readFileSync(path.join(__dirname, "index.js"), "utf8");
  ["verifyPlusSubscription", "eraseUserData", "checkPlusEntitlement"]
      .forEach((name) => {
        const start = source.indexOf(`exports.${name} = onCall(`);
        assert.notEqual(start, -1);
        const handler = source.slice(start, source.indexOf("async (request)", start));
        assert.match(handler, /enforceAppCheck:\s*true/);
      });
});
