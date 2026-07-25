const crypto = require("crypto");

const EVENT_COLLECTION = "playRtdnEvents";
const EVENT_LEASE_MILLIS = 5 * 60 * 1000;
const EVENT_RETENTION_MILLIS = 30 * 24 * 60 * 60 * 1000;
const MAX_PURCHASE_TOKEN_LENGTH = 4096;
const REVOKED_STATE = "SUBSCRIPTION_STATE_REVOKED";
const ENTITLED_STATES = new Set([
  "SUBSCRIPTION_STATE_ACTIVE",
  "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
  "SUBSCRIPTION_STATE_CANCELED",
]);

/**
 * Builds the transactional Firestore adapter used by the RTDN processor.
 *
 * @param {object} dependencies Store dependencies.
 * @return {object} RTDN store.
 */
function createFirestoreRtdnStore({db, fieldValue, packageName, logger}) {
  if (!db || !fieldValue || !packageName || !logger) {
    throw new TypeError("RTDN Firestore store dependencies are required.");
  }

  const eventRef = (eventHash) =>
    db.collection(EVENT_COLLECTION).doc(eventHash);
  const tokenRef = (tokenHash) =>
    db.collection("playPurchaseTokens").doc(tokenHash);
  const userRef = (uid) => db.collection("users").doc(uid);
  const auditFields = (nowMillis) => ({
    updatedAt: fieldValue.serverTimestamp(),
    expiresAt: new Date(nowMillis + EVENT_RETENTION_MILLIS),
  });
  const ownsClaim = (snapshot, claimId) => snapshot.exists &&
    snapshot.get("status") === "processing" &&
    snapshot.get("claimId") === claimId;

  return {
    async claimEvent(eventHash, claimId, nowMillis) {
      return db.runTransaction(async (transaction) => {
        const ref = eventRef(eventHash);
        const snapshot = await transaction.get(ref);
        if (snapshot.exists && snapshot.get("status") === "completed") {
          return "completed";
        }
        if (snapshot.exists && snapshot.get("status") === "processing" &&
            timestampMillis(snapshot.get("leaseExpiresAt")) > nowMillis) {
          return "processing";
        }
        const attemptCount = snapshot.exists ?
          numericAttempt(snapshot.get("attemptCount")) + 1 : 1;
        transaction.set(ref, {
          status: "processing",
          claimId,
          attemptCount,
          leaseExpiresAt: new Date(nowMillis + EVENT_LEASE_MILLIS),
          createdAt: snapshot.exists ?
            snapshot.get("createdAt") || fieldValue.serverTimestamp() :
            fieldValue.serverTimestamp(),
          ...auditFields(nowMillis),
        }, {merge: true});
        return "claimed";
      });
    },

    async completeNoop(eventHash, claimId, outcome, metadata, nowMillis) {
      return db.runTransaction(async (transaction) => {
        const ref = eventRef(eventHash);
        const snapshot = await transaction.get(ref);
        if (!ownsClaim(snapshot, claimId)) return "stale_claim";
        transaction.set(ref, completionFields(
            fieldValue, outcome, metadata, nowMillis,
        ), {merge: true});
        return outcome;
      });
    },

    async markRetryable(eventHash, claimId, nowMillis) {
      return db.runTransaction(async (transaction) => {
        const ref = eventRef(eventHash);
        const snapshot = await transaction.get(ref);
        if (!ownsClaim(snapshot, claimId)) return "stale_claim";
        transaction.set(ref, {
          status: "retryable",
          claimId: fieldValue.delete(),
          leaseExpiresAt: fieldValue.delete(),
          ...auditFields(nowMillis),
        }, {merge: true});
        return "retryable";
      });
    },

    async applySubscriptionAndComplete(input) {
      return db.runTransaction(async (transaction) => {
        const event = eventRef(input.eventHash);
        const eventSnapshot = await transaction.get(event);
        if (!ownsClaim(eventSnapshot, input.claimId)) return "stale_claim";

        const currentRef = tokenRef(input.tokenHash);
        const currentSnapshot = await transaction.get(currentRef);
        const linkedRef = input.linkedTokenHash &&
          input.linkedTokenHash !== input.tokenHash ?
          tokenRef(input.linkedTokenHash) : null;
        const linkedSnapshot = linkedRef ?
          await transaction.get(linkedRef) : null;
        const currentUid = cleanString(currentSnapshot.get("uid")) || null;
        const linkedUid = linkedSnapshot && linkedSnapshot.exists ?
          cleanString(linkedSnapshot.get("uid")) || null : null;

        if (currentUid && linkedUid && currentUid !== linkedUid) {
          logger.warn("RTDN purchase-token ownership conflict.");
          transaction.set(event, completionFields(
              fieldValue,
              "cross_user_token_conflict",
              {notificationType: input.notificationType},
              input.nowMillis,
          ), {merge: true});
          return "cross_user_token_conflict";
        }

        const owner = currentUid || linkedUid;
        const updatedAt = fieldValue.serverTimestamp();
        const tokenState = {
          packageName,
          productId: input.entitlement.productId,
          active: input.entitlement.active,
          subscriptionState: input.entitlement.subscriptionState,
          expiryTimeMillis: input.entitlement.expiryTimeMillis,
          linkedPurchaseTokenHash: input.linkedTokenHash || null,
          notificationType: input.notificationType,
          updatedAt,
        };
        if (owner) tokenState.uid = owner;
        transaction.set(currentRef, tokenState, {merge: true});

        if (linkedRef && linkedSnapshot.exists) {
          const linkedState = {
            active: false,
            supersededByTokenHash: input.tokenHash,
            updatedAt,
          };
          if (owner) linkedState.uid = owner;
          transaction.set(linkedRef, linkedState, {merge: true});
        }

        const supersededBy = cleanString(
            currentSnapshot.get("supersededByTokenHash"),
        );
        if (owner && !supersededBy) {
          transaction.set(userRef(owner), {
            plus: {
              active: input.entitlement.active,
              productId: input.entitlement.productId,
              subscriptionState: input.entitlement.subscriptionState,
              expiryTimeMillis: input.entitlement.expiryTimeMillis,
              purchaseTokenHash: input.tokenHash,
              updatedAt,
            },
          }, {merge: true});
        }

        transaction.set(event, completionFields(
            fieldValue,
            "subscription_applied",
            {notificationType: input.notificationType},
            input.nowMillis,
        ), {merge: true});
        return "subscription_applied";
      });
    },

    async applyVoidedPurchaseAndComplete(input) {
      return db.runTransaction(async (transaction) => {
        const event = eventRef(input.eventHash);
        const eventSnapshot = await transaction.get(event);
        if (!ownsClaim(eventSnapshot, input.claimId)) return "stale_claim";
        const purchaseRef = tokenRef(input.tokenHash);
        const purchaseSnapshot = await transaction.get(purchaseRef);
        if (!purchaseSnapshot.exists) {
          transaction.set(event, completionFields(
              fieldValue, "voided_unknown_token", {}, input.nowMillis,
          ), {merge: true});
          return "voided_unknown_token";
        }

        const uid = cleanString(purchaseSnapshot.get("uid"));
        const ownerRef = uid ? userRef(uid) : null;
        const ownerSnapshot = ownerRef ?
          await transaction.get(ownerRef) : null;
        const updatedAt = fieldValue.serverTimestamp();
        transaction.set(purchaseRef, {
          active: false,
          subscriptionState: REVOKED_STATE,
          updatedAt,
        }, {merge: true});
        let outcome = "voided_purchase_revoked";
        if (ownerRef && ownerSnapshot) {
          if (ownerSnapshot.get("plus.purchaseTokenHash") === input.tokenHash) {
            transaction.set(ownerRef, {
              plus: {
                active: false,
                subscriptionState: REVOKED_STATE,
                updatedAt,
              },
            }, {merge: true});
          } else {
            outcome = "voided_superseded_token";
          }
        }
        transaction.set(event, completionFields(
            fieldValue, outcome, {}, input.nowMillis,
        ), {merge: true});
        return outcome;
      });
    },
  };
}

/**
 * Builds the RTDN CloudEvent processor.
 *
 * @param {object} dependencies Processor dependencies.
 * @return {function(object): Promise<void>} CloudEvent processor.
 */
function createRtdnProcessor({
  store,
  verifyPurchase,
  logger,
  packageName,
  supportedProductIds,
  hashToken,
  now = Date.now,
  createClaimId = crypto.randomUUID,
  isRetryableError,
}) {
  if (!store || typeof verifyPurchase !== "function" || !logger ||
      !packageName || !supportedProductIds || typeof hashToken !== "function" ||
      typeof now !== "function" || typeof createClaimId !== "function" ||
      typeof isRetryableError !== "function") {
    throw new TypeError("RTDN processor dependencies are required.");
  }

  return async function processRtdn(event) {
    const message = event && event.data && event.data.message;
    const rawIdentifier = cleanString(message && message.messageId) ||
      cleanString(event && event.id);
    if (!rawIdentifier) {
      logger.warn("RTDN event had no delivery identifier.");
      return;
    }
    const eventHash = crypto.createHash("sha256")
        .update(rawIdentifier).digest("hex");
    const claimId = createClaimId();
    const claimedAt = now();
    const claim = await store.claimEvent(eventHash, claimId, claimedAt);
    if (claim === "completed") return;
    if (claim === "processing") {
      const error = new Error("RTDN event is already being processed.");
      error.code = "rtdn-in-progress";
      throw error;
    }

    let payload;
    try {
      payload = message.json;
    } catch (error) {
      await store.completeNoop(
          eventHash, claimId, "malformed_json", {}, now(),
      );
      return;
    }
    if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
      await store.completeNoop(
          eventHash, claimId, "malformed_payload", {}, now(),
      );
      return;
    }
    if (payload.testNotification) {
      await store.completeNoop(
          eventHash, claimId, "test_notification", {}, now(),
      );
      return;
    }
    if (payload.packageName !== packageName) {
      await store.completeNoop(
          eventHash, claimId, "unexpected_package", {}, now(),
      );
      return;
    }

    if (payload.voidedPurchaseNotification) {
      const token = validToken(
          payload.voidedPurchaseNotification.purchaseToken,
      );
      if (!token) {
        await store.completeNoop(
            eventHash, claimId, "malformed_voided_purchase", {}, now(),
        );
        return;
      }
      await store.applyVoidedPurchaseAndComplete({
        eventHash,
        claimId,
        tokenHash: hashToken(token),
        nowMillis: now(),
      });
      return;
    }

    const notification = payload.subscriptionNotification;
    if (!notification || typeof notification !== "object" ||
        Array.isArray(notification)) {
      await store.completeNoop(
          eventHash, claimId, "unsupported_notification", {}, now(),
      );
      return;
    }
    const token = validToken(notification.purchaseToken);
    const notificationType = notification.notificationType;
    if (!token) {
      await store.completeNoop(
          eventHash, claimId, "invalid_subscription_token", {}, now(),
      );
      return;
    }
    if (!Number.isFinite(notificationType) ||
        !Number.isInteger(notificationType)) {
      await store.completeNoop(
          eventHash, claimId, "invalid_notification_type", {}, now(),
      );
      return;
    }

    let purchase;
    try {
      purchase = await verifyPurchase(token);
    } catch (error) {
      logger.error("RTDN Google Play verification failed.", {
        status: numericStatus(error),
        code: error && error.code,
        name: error && error.name,
      });
      if (isRetryableError(error)) {
        await store.markRetryable(eventHash, claimId, now());
        throw error;
      }
      await store.completeNoop(
          eventHash,
          claimId,
          "play_verification_rejected",
          {notificationType},
          now(),
      );
      return;
    }

    const entitlement = deriveRtdnEntitlement(
        purchase, supportedProductIds, now(),
    );
    if (!entitlement.hasMatchingProduct) {
      await store.completeNoop(
          eventHash,
          claimId,
          "unsupported_product",
          {notificationType},
          now(),
      );
      return;
    }
    const linkedTokenHash = entitlement.linkedPurchaseToken ?
      hashToken(entitlement.linkedPurchaseToken) : null;
    await store.applySubscriptionAndComplete({
      eventHash,
      claimId,
      tokenHash: hashToken(token),
      linkedTokenHash,
      entitlement: {
        active: entitlement.active,
        productId: entitlement.productId,
        subscriptionState: entitlement.subscriptionState,
        expiryTimeMillis: entitlement.expiryTimeMillis,
      },
      notificationType,
      nowMillis: now(),
    });
  };
}

/**
 * Derives a safe entitlement from SubscriptionPurchaseV2.
 *
 * @param {object} purchase Verified Google Play response.
 * @param {Set<string>} supportedProductIds Supported product IDs.
 * @param {number} nowMillis Current time.
 * @return {object} Derived entitlement.
 */
function deriveRtdnEntitlement(purchase, supportedProductIds, nowMillis) {
  const subscriptionState = cleanString(
      purchase && purchase.subscriptionState,
  ) || "SUBSCRIPTION_STATE_UNSPECIFIED";
  const linkedPurchaseToken = cleanString(
      purchase && purchase.linkedPurchaseToken,
  ) || null;
  const lineItems = Array.isArray(purchase && purchase.lineItems) ?
    purchase.lineItems : [];
  const supported = lineItems.map((item, index) => ({
    item,
    index,
    productId: cleanString(item && item.productId),
    expiryTimeMillis: parseExpiry(item && item.expiryTime),
  })).filter((entry) => supportedProductIds.has(entry.productId));
  if (supported.length === 0) {
    return {
      hasMatchingProduct: false,
      active: false,
      productId: null,
      subscriptionState,
      expiryTimeMillis: 0,
      linkedPurchaseToken,
    };
  }
  const validExpiryItems = supported.filter((entry) =>
    entry.expiryTimeMillis > 0,
  );
  const selected = validExpiryItems.reduce((latest, entry) =>
    !latest || entry.expiryTimeMillis > latest.expiryTimeMillis ?
      entry : latest, null) || supported[0];
  const expiryTimeMillis = supported
      .filter((entry) => entry.productId === selected.productId)
      .reduce((latest, entry) => Math.max(latest, entry.expiryTimeMillis), 0);
  return {
    hasMatchingProduct: true,
    active: ENTITLED_STATES.has(subscriptionState) &&
      expiryTimeMillis > nowMillis,
    productId: selected.productId,
    subscriptionState,
    expiryTimeMillis,
    linkedPurchaseToken,
  };
}

/**
 * @param {*} value Input.
 * @return {string} Trimmed string.
 */
function cleanString(value) {
  return typeof value === "string" ? value.trim() : "";
}

/**
 * @param {*} value Timestamp.
 * @return {number} Milliseconds.
 */
function timestampMillis(value) {
  if (value && typeof value.toMillis === "function") return value.toMillis();
  if (value instanceof Date) return value.getTime();
  return Number.isFinite(value) ? Number(value) : 0;
}

/**
 * @param {*} value Attempt value.
 * @return {number} Safe count.
 */
function numericAttempt(value) {
  return Number.isFinite(value) ? Number(value) : 0;
}

/**
 * @param {*} token Token.
 * @return {?string} Valid token.
 */
function validToken(token) {
  const cleaned = cleanString(token);
  return cleaned && cleaned.length <= MAX_PURCHASE_TOKEN_LENGTH ?
    cleaned : null;
}

/**
 * @param {*} expiry Expiry.
 * @return {number} Milliseconds.
 */
function parseExpiry(expiry) {
  const parsed = Date.parse(cleanString(expiry));
  return Number.isFinite(parsed) ? parsed : 0;
}

/**
 * @param {*} error Error.
 * @return {number} Numeric status.
 */
function numericStatus(error) {
  const status = Number(error && (error.status ||
    (error.response && error.response.status)));
  return Number.isFinite(status) ? status : 0;
}

/**
 * Creates completed event fields.
 *
 * @param {object} fieldValue Firestore field values.
 * @param {string} outcome Safe outcome.
 * @param {object} metadata Safe metadata.
 * @param {number} nowMillis Current time.
 * @return {object} Completion fields.
 */
function completionFields(fieldValue, outcome, metadata, nowMillis) {
  return {
    status: "completed",
    outcome,
    ...metadata,
    claimId: fieldValue.delete(),
    leaseExpiresAt: fieldValue.delete(),
    completedAt: fieldValue.serverTimestamp(),
    updatedAt: fieldValue.serverTimestamp(),
    expiresAt: new Date(nowMillis + EVENT_RETENTION_MILLIS),
  };
}

module.exports = {
  createFirestoreRtdnStore,
  createRtdnProcessor,
  deriveRtdnEntitlement,
};
