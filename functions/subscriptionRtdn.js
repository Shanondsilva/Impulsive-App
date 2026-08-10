const crypto = require("crypto");
const {
  PENDING_PURCHASE_CANCELED_STATE,
  cleanString,
  deriveRtdnEntitlement,
} = require("./subscriptionEntitlement");

const EVENT_COLLECTION = "playRtdnEvents";
const EVENT_LEASE_MILLIS = 5 * 60 * 1000;
const EVENT_RETENTION_MILLIS = 30 * 24 * 60 * 60 * 1000;
const MAX_PURCHASE_TOKEN_LENGTH = 4096;
const REVOKED_STATE = "SUBSCRIPTION_STATE_REVOKED";
const ACKNOWLEDGEMENT_PENDING = "ACKNOWLEDGEMENT_STATE_PENDING";

/**
 * Builds the transactional Firestore adapter used by the RTDN processor.
 *
 * @param {object} dependencies Store dependencies.
 * @return {object} RTDN store.
 */
function createFirestoreRtdnStore({
  db, fieldValue, packageName, logger,
  userFieldForEntitlement, storedEntitlementKindForProductId,
}) {
  if (!db || !fieldValue || !packageName || !logger ||
      typeof userFieldForEntitlement !== "function" ||
      typeof storedEntitlementKindForProductId !== "function") {
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

  const storedKind = (snapshot) => {
    if (!snapshot || !snapshot.exists) {
      return null;
    }

    const explicit = cleanString(snapshot.get("entitlementKind"));

    if (explicit) {
      return explicit;
    }

    return storedEntitlementKindForProductId(
        cleanString(snapshot.get("productId")),
    );
  };

  const entitlementConflict = (snapshot, expectedKind) => {
    if (!snapshot || !snapshot.exists) {
      return false;
    }

    const kind = storedKind(snapshot);

    return !kind || kind !== expectedKind;
  };

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

    /**
     * Applies a normally-verified (non-cancelled) subscription purchase.
     * Rejects any cross-user or cross-entitlement-kind token linkage before
     * writing anything, and writes only the entitlement kind's own
     * catalogue-resolved Firestore field.
     *
     * @param {object} input Derived entitlement and token identity.
     * @return {Promise<string>} Outcome.
     */
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

        const expectedKind = input.entitlement.entitlementKind;
        if (
          entitlementConflict(currentSnapshot, expectedKind) ||
          entitlementConflict(linkedSnapshot, expectedKind)
        ) {
          logger.warn("RTDN purchase-token entitlement-kind conflict.");
          transaction.set(event, completionFields(
              fieldValue,
              "cross_entitlement_token_conflict",
              {notificationType: input.notificationType},
              input.nowMillis,
          ), {merge: true});
          return "cross_entitlement_token_conflict";
        }

        const expectedUserField = userFieldForEntitlement(
            input.entitlement.entitlementKind,
        );

        if (!expectedUserField || expectedUserField !== input.userField) {
          logger.warn("RTDN entitlement user-field mismatch.");
          transaction.set(event, completionFields(
              fieldValue,
              "invalid_entitlement_definition",
              {notificationType: input.notificationType},
              input.nowMillis,
          ), {merge: true});
          return "invalid_entitlement_definition";
        }

        const owner = currentUid || linkedUid;
        const updatedAt = fieldValue.serverTimestamp();
        const tokenState = {
          packageName,
          productId: input.entitlement.productId,
          entitlementKind: input.entitlement.entitlementKind,
          basePlanId: input.entitlement.basePlanId,
          planKind: input.entitlement.planKind,
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
            entitlementKind: input.entitlement.entitlementKind,
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
            [input.userField]: {
              active: input.entitlement.active,
              productId: input.entitlement.productId,
              basePlanId: input.entitlement.basePlanId,
              planKind: input.entitlement.planKind,
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

    /**
     * Handles a new purchase transaction verified as
     * SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED (a pending top-up or plan
     * change the user backed out of): the cancelled new token is recorded
     * inactive, but the linked (old) subscription's authoritative current
     * state is preserved untouched -- never superseded, never revoked, and
     * never replaced by the cancelled purchase's own expiry.
     *
     * @param {object} input Cancelled + linked entitlement identity.
     * @return {Promise<string>} Outcome.
     */
    async applyPendingPurchaseCanceledAndComplete(input) {
      return db.runTransaction(async (transaction) => {
        const event = eventRef(input.eventHash);
        const eventSnapshot = await transaction.get(event);
        if (!ownsClaim(eventSnapshot, input.claimId)) return "stale_claim";

        const canceledRef = tokenRef(input.canceledTokenHash);
        const canceledSnapshot = await transaction.get(canceledRef);
        const linkedRef = input.linkedTokenHash &&
          input.linkedTokenHash !== input.canceledTokenHash ?
          tokenRef(input.linkedTokenHash) : null;
        const linkedSnapshot = linkedRef ?
          await transaction.get(linkedRef) : null;

        const canceledUid = cleanString(canceledSnapshot.get("uid")) || null;
        const linkedUid = linkedSnapshot && linkedSnapshot.exists ?
          cleanString(linkedSnapshot.get("uid")) || null : null;

        if (canceledUid && linkedUid && canceledUid !== linkedUid) {
          logger.warn("RTDN pending-cancellation token ownership conflict.");
          transaction.set(event, completionFields(
              fieldValue,
              "cross_user_token_conflict",
              {notificationType: input.notificationType},
              input.nowMillis,
          ), {merge: true});
          return "cross_user_token_conflict";
        }

        if (
          entitlementConflict(canceledSnapshot, input.entitlementKind) ||
          entitlementConflict(linkedSnapshot, input.entitlementKind)
        ) {
          logger.warn(
              "RTDN pending-cancellation token entitlement-kind conflict.",
          );
          transaction.set(event, completionFields(
              fieldValue,
              "cross_entitlement_token_conflict",
              {notificationType: input.notificationType},
              input.nowMillis,
          ), {merge: true});
          return "cross_entitlement_token_conflict";
        }

        const owner = canceledUid || linkedUid;
        const updatedAt = fieldValue.serverTimestamp();

        const canceledState = {
          packageName,
          active: false,
          subscriptionState: PENDING_PURCHASE_CANCELED_STATE,
          entitlementKind: input.entitlementKind,
          productId: input.canceledEntitlement.productId,
          basePlanId: input.canceledEntitlement.basePlanId,
          planKind: input.canceledEntitlement.planKind,
          linkedPurchaseTokenHash: input.linkedTokenHash || null,
          notificationType: input.notificationType,
          updatedAt,
        };
        if (owner) canceledState.uid = owner;
        transaction.set(canceledRef, canceledState, {merge: true});

        if (linkedRef) {
          const linkedState = {
            packageName,
            entitlementKind: input.entitlementKind,
            productId: input.linkedEntitlement.productId,
            basePlanId: input.linkedEntitlement.basePlanId,
            planKind: input.linkedEntitlement.planKind,
            active: input.linkedEntitlement.active,
            subscriptionState: input.linkedEntitlement.subscriptionState,
            expiryTimeMillis: input.linkedEntitlement.expiryTimeMillis,
            updatedAt,
          };
          if (owner) linkedState.uid = owner;
          // Deliberately no supersededByTokenHash: a cancelled pending
          // top-up or plan change must never supersede the subscription it
          // was going to replace.
          transaction.set(linkedRef, linkedState, {merge: true});
        }

        const linkedAlreadySuperseded =
          linkedSnapshot && linkedSnapshot.exists &&
          Boolean(cleanString(linkedSnapshot.get("supersededByTokenHash")));

        if (owner && !linkedAlreadySuperseded) {
          transaction.set(userRef(owner), {
            [input.userField]: {
              active: input.linkedEntitlement.active,
              productId: input.linkedEntitlement.productId,
              basePlanId: input.linkedEntitlement.basePlanId,
              planKind: input.linkedEntitlement.planKind,
              subscriptionState: input.linkedEntitlement.subscriptionState,
              expiryTimeMillis: input.linkedEntitlement.expiryTimeMillis,
              purchaseTokenHash: input.linkedTokenHash,
              updatedAt,
            },
          }, {merge: true});
        }

        transaction.set(event, completionFields(
            fieldValue,
            "pending_purchase_canceled_linked_entitlement_preserved",
            {notificationType: input.notificationType},
            input.nowMillis,
        ), {merge: true});
        return "pending_purchase_canceled_linked_entitlement_preserved";
      });
    },

    /**
     * Applies a voided-purchase notification, resolving which entitlement
     * kind (and therefore which single Firestore field) the voided token
     * belongs to strictly from what was recorded when the token was first
     * applied -- never defaulting to Plus.
     *
     * @param {object} input Voided token identity.
     * @return {Promise<string>} Outcome.
     */
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

        const entitlementKind = storedKind(purchaseSnapshot);
        const userField = entitlementKind ?
          userFieldForEntitlement(entitlementKind) : null;
        const updatedAt = fieldValue.serverTimestamp();

        transaction.set(purchaseRef, {
          active: false,
          subscriptionState: REVOKED_STATE,
          entitlementKind: entitlementKind || fieldValue.delete(),
          updatedAt,
        }, {merge: true});

        if (!entitlementKind || !userField) {
          transaction.set(event, completionFields(
              fieldValue,
              "voided_unknown_entitlement_kind",
              {},
              input.nowMillis,
          ), {merge: true});
          return "voided_unknown_entitlement_kind";
        }

        let outcome = "voided_purchase_revoked";
        if (ownerRef && ownerSnapshot) {
          if (ownerSnapshot.get(`${userField}.purchaseTokenHash`) ===
              input.tokenHash) {
            transaction.set(ownerRef, {
              [userField]: {
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
  acknowledgePurchase,
  logger,
  packageName,
  productDefinition,
  hashToken,
  now = Date.now,
  createClaimId = crypto.randomUUID,
  isRetryableError,
}) {
  if (!store || typeof verifyPurchase !== "function" ||
      typeof acknowledgePurchase !== "function" || !logger ||
      !packageName || typeof productDefinition !== "function" ||
      typeof hashToken !== "function" ||
      typeof now !== "function" || typeof createClaimId !== "function" ||
      typeof isRetryableError !== "function") {
    throw new TypeError("RTDN processor dependencies are required.");
  }

  /**
   * Acknowledges one verified purchase, translating a retryable failure
   * into an RTDN retry and a permanent failure into a terminal, no-write
   * completion. Returns false only when the caller must stop (already
   * completed/retried); true means it is safe to proceed to writing state.
   *
   * @param {string} token Play purchase token.
   * @param {object} purchase Verified Google Play purchase.
   * @param {string} productId Verified Play product ID.
   * @param {string} eventHash RTDN event hash.
   * @param {string} claimId RTDN claim ID.
   * @param {number} notificationType Play notification type.
   * @return {Promise<boolean>} Whether processing may continue.
   */
  async function acknowledgeOrStop(
      token, purchase, productId, eventHash, claimId, notificationType,
  ) {
    try {
      await acknowledgePurchase(token, purchase, productId);
      return true;
    } catch (error) {
      if (isRetryableError(error)) {
        await store.markRetryable(eventHash, claimId, now());
        throw error;
      }

      await store.completeNoop(
          eventHash,
          claimId,
          "acknowledgement_rejected",
          {notificationType},
          now(),
      );
      return false;
    }
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

    const entitlement =
      deriveRtdnEntitlement(purchase, productDefinition, now());

    if (entitlement.ambiguousEntitlementKind) {
      await store.completeNoop(
          eventHash,
          claimId,
          "ambiguous_entitlement_kind",
          {notificationType},
          now(),
      );
      return;
    }

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

    if (!entitlement.metadataValid) {
      await store.completeNoop(
          eventHash,
          claimId,
          "invalid_plan_metadata",
          {notificationType},
          now(),
      );
      return;
    }

    // A pending top-up or plan change the user backed out of: the linked
    // (old) subscription must be preserved untouched, never superseded and
    // never replaced by the cancelled purchase's own (irrelevant) expiry.
    if (entitlement.subscriptionState === PENDING_PURCHASE_CANCELED_STATE) {
      const linkedToken = entitlement.linkedPurchaseToken;

      if (!linkedToken) {
        await store.completeNoop(
            eventHash,
            claimId,
            "pending_purchase_canceled_without_linked_token",
            {notificationType},
            now(),
        );
        return;
      }

      let linkedPurchase;
      try {
        linkedPurchase = await verifyPurchase(linkedToken);
      } catch (error) {
        logger.error("RTDN linked Google Play verification failed.", {
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
            "pending_purchase_canceled_invalid_linked_purchase",
            {notificationType},
            now(),
        );
        return;
      }

      const linkedEntitlement = deriveRtdnEntitlement(
          linkedPurchase, productDefinition, now(),
      );

      if (
        !linkedEntitlement.hasMatchingProduct ||
        !linkedEntitlement.metadataValid
      ) {
        await store.completeNoop(
            eventHash,
            claimId,
            "pending_purchase_canceled_invalid_linked_purchase",
            {notificationType},
            now(),
        );
        return;
      }

      if (linkedEntitlement.entitlementKind !== entitlement.entitlementKind) {
        await store.completeNoop(
            eventHash,
            claimId,
            "cross_entitlement_token_conflict",
            {notificationType},
            now(),
        );
        return;
      }

      if (
        linkedPurchase.acknowledgementState === ACKNOWLEDGEMENT_PENDING &&
        linkedEntitlement.active
      ) {
        const canProceed = await acknowledgeOrStop(
            linkedToken,
            linkedPurchase,
            linkedEntitlement.productId,
            eventHash,
            claimId,
            notificationType,
        );
        if (!canProceed) return;
      }

      const linkedTokenHash = hashToken(linkedToken);

      await store.applyPendingPurchaseCanceledAndComplete({
        eventHash,
        claimId,
        canceledTokenHash: hashToken(token),
        linkedTokenHash,
        entitlementKind: entitlement.entitlementKind,
        userField: entitlement.userField,
        canceledEntitlement: {
          productId: entitlement.productId,
          basePlanId: entitlement.basePlanId,
          planKind: entitlement.planKind,
        },
        linkedEntitlement: {
          productId: linkedEntitlement.productId,
          basePlanId: linkedEntitlement.basePlanId,
          planKind: linkedEntitlement.planKind,
          active: linkedEntitlement.active,
          subscriptionState: linkedEntitlement.subscriptionState,
          expiryTimeMillis: linkedEntitlement.expiryTimeMillis,
        },
        notificationType,
        nowMillis: now(),
      });
      return;
    }

    // Only an active purchase pending acknowledgement is acknowledged here
    // -- an inactive authoritative state (expired, on hold, revoked, etc.)
    // is stored without ever contacting the acknowledgement endpoint, so
    // expiry/hold/revocation is still applied even for an unacknowledged
    // purchase.
    if (
      entitlement.active &&
      purchase.acknowledgementState === ACKNOWLEDGEMENT_PENDING
    ) {
      const canProceed = await acknowledgeOrStop(
          token,
          purchase,
          entitlement.productId,
          eventHash,
          claimId,
          notificationType,
      );
      if (!canProceed) return;
    }

    const linkedTokenHash = entitlement.linkedPurchaseToken ?
      hashToken(entitlement.linkedPurchaseToken) : null;
    await store.applySubscriptionAndComplete({
      eventHash,
      claimId,
      tokenHash: hashToken(token),
      linkedTokenHash,
      userField: entitlement.userField,
      entitlement: {
        active: entitlement.active,
        productId: entitlement.productId,
        entitlementKind: entitlement.entitlementKind,
        basePlanId: entitlement.basePlanId,
        planKind: entitlement.planKind,
        subscriptionState: entitlement.subscriptionState,
        expiryTimeMillis: entitlement.expiryTimeMillis,
      },
      notificationType,
      nowMillis: now(),
    });
  };
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
