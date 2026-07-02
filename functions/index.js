const crypto = require("crypto");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");
const {google} = require("googleapis");

admin.initializeApp();

const db = admin.firestore();

const PACKAGE_NAME = "com.impulsive.app";
const PRODUCT_ID = "impulsive_plus_monthly";
const REGION = "us-central1";
const SERVICE_ACCOUNT =
  "impulsive-play-verifier@useimpulsive.iam.gserviceaccount.com";
const TOKEN_HASH_ALGORITHM = "sha256";
const MAX_PRODUCT_ID_LENGTH = 128;
const MAX_PURCHASE_TOKEN_LENGTH = 4096;
const DELETE_BATCH_SIZE = 450;
const ERASE_USER_DATA_TIMEOUT_SECONDS = 120;

const ENTITLED_STATES = new Set([
  "SUBSCRIPTION_STATE_ACTIVE",
  "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
]);

const CANCELED_STATE = "SUBSCRIPTION_STATE_CANCELED";
const ACKNOWLEDGEMENT_PENDING = "ACKNOWLEDGEMENT_STATE_PENDING";

const auth = new google.auth.GoogleAuth({
  scopes: ["https://www.googleapis.com/auth/androidpublisher"],
});

const publisher = google.androidpublisher({
  version: "v3",
  auth,
});

/**
 * Verifies a Google Play subscription purchase for the signed-in Firebase user.
 */
exports.verifyPlusSubscription = onCall(
    {
      region: REGION,
      serviceAccount: SERVICE_ACCOUNT,
      enforceAppCheck: true,
      maxInstances: 10,
      timeoutSeconds: 60,
      memory: "256MiB",
    },
    async (request) => {
      const uid = request.auth && request.auth.uid;

      if (!uid) {
        throw new HttpsError(
            "unauthenticated",
            "You must be signed in to verify a purchase.",
        );
      }

      const input = parseInput(request.data);
      const purchase = await verifyPurchaseWithGoogle(input.purchaseToken);
      const entitlement = deriveEntitlement(purchase);

      if (!entitlement.hasMatchingProduct) {
        logger.warn("Verified purchase did not contain Plus product.", {
          uid,
          subscriptionState: entitlement.subscriptionState,
        });

        throw new HttpsError(
            "permission-denied",
            "The purchase does not contain Impulsive Plus.",
        );
      }

      if (!entitlement.active) {
        logger.info("Verified purchase is not currently entitled.", {
          uid,
          subscriptionState: entitlement.subscriptionState,
          expiryTimeMillis: entitlement.expiryTimeMillis,
        });

        throw new HttpsError(
            "failed-precondition",
            "The purchase is not currently active.",
        );
      }

      await acknowledgeIfNeeded(input.purchaseToken, purchase);
      await saveEntitlement(uid, input.purchaseToken, purchase, entitlement);

      logger.info("Plus subscription verified.", {
        uid,
        subscriptionState: entitlement.subscriptionState,
        expiryTimeMillis: entitlement.expiryTimeMillis,
      });

      return {
        active: entitlement.active,
        productId: PRODUCT_ID,
        subscriptionState: entitlement.subscriptionState,
        expiryTimeMillis: entitlement.expiryTimeMillis,
      };
    },
);

/**
 * Deletes all known Firestore data owned by the signed-in user.
 */
exports.eraseUserData = onCall(
    {
      region: REGION,
      serviceAccount: SERVICE_ACCOUNT,
      enforceAppCheck: true,
      maxInstances: 5,
      timeoutSeconds: ERASE_USER_DATA_TIMEOUT_SECONDS,
      memory: "256MiB",
    },
    async (request) => {
      const uid = request.auth && request.auth.uid;

      if (!uid) {
        throw new HttpsError(
            "unauthenticated",
            "You must be signed in to erase user data.",
        );
      }

      try {
        const deleted = await eraseFirestoreDataForUser(uid);

        logger.info("User Firestore data erased.", {
          uid,
          checklistItems: deleted.checklistItems,
          journalNotes: deleted.journalNotes,
          recoverySessions: deleted.recoverySessions,
          syncTombstones: deleted.syncTombstones,
          playPurchaseTokens: deleted.playPurchaseTokens,
          userDocument: deleted.userDocument,
        });

        return {
          success: true,
          deleted,
        };
      } catch (error) {
        logger.error("Could not erase user Firestore data.", {
          uid,
          message: error && error.message,
        });

        throw new HttpsError(
            "internal",
            "User data could not be erased.",
        );
      }
    },
);

/**
 * Validates and normalizes callable input.
 *
 * @param {*} data Callable request data.
 * @return {{productId: string, purchaseToken: string}} Normalized input.
 */
function parseInput(data) {
  if (!data || typeof data !== "object" || Array.isArray(data)) {
    throw new HttpsError(
        "invalid-argument",
        "A valid purchase verification request is required.",
    );
  }

  const allowedKeys = new Set(["productId", "purchaseToken"]);
  const unexpectedKey = Object.keys(data).find((key) => !allowedKeys.has(key));

  if (unexpectedKey) {
    throw new HttpsError(
        "invalid-argument",
        "Only productId and purchaseToken are accepted.",
    );
  }

  const productId = cleanString(data.productId);
  const purchaseToken = cleanString(data.purchaseToken);

  if (
    productId.length === 0 ||
    productId.length > MAX_PRODUCT_ID_LENGTH
  ) {
    throw new HttpsError(
        "invalid-argument",
        "A valid product ID is required.",
    );
  }

  if (productId !== PRODUCT_ID) {
    throw new HttpsError(
        "invalid-argument",
        "The subscription product is not supported.",
    );
  }

  if (
    purchaseToken.length === 0 ||
    purchaseToken.length > MAX_PURCHASE_TOKEN_LENGTH
  ) {
    throw new HttpsError(
        "invalid-argument",
        "A valid purchase token is required.",
    );
  }

  return {
    productId,
    purchaseToken,
  };
}

/**
 * Returns a trimmed string only when the value is already a string.
 *
 * @param {*} value Input value.
 * @return {string} Trimmed value or an empty string.
 */
function cleanString(value) {
  return typeof value === "string" ? value.trim() : "";
}

/**
 * Hashes a Play purchase token for storage.
 *
 * @param {string} token Raw purchase token.
 * @return {string} SHA-256 token hash.
 */
function hashToken(token) {
  return crypto
      .createHash(TOKEN_HASH_ALGORITHM)
      .update(token)
      .digest("hex");
}

/**
 * Verifies a subscription purchase with Google Play subscriptions v2.
 *
 * @param {string} purchaseToken Play purchase token.
 * @return {Promise<object>} Google Play subscription purchase.
 */
async function verifyPurchaseWithGoogle(purchaseToken) {
  try {
    const response =
      await publisher.purchases.subscriptionsv2.get({
        packageName: PACKAGE_NAME,
        token: purchaseToken,
      });

    return response.data || {};
  } catch (error) {
    const status = getErrorStatus(error);

    logger.error("Google Play verification failed.", {
      status,
      message: error && error.message,
    });

    if (status >= 500 || status === 429) {
      throw new HttpsError(
          "unavailable",
          "Google Play could not verify this purchase right now.",
      );
    }

    throw new HttpsError(
        "failed-precondition",
        "Google Play could not verify this purchase.",
    );
  }
}

/**
 * Derives the current Plus entitlement from a Google subscription purchase.
 *
 * @param {object} purchase Google Play subscription purchase.
 * @return {object} Safe entitlement summary.
 */
function deriveEntitlement(purchase) {
  const lineItems = Array.isArray(purchase.lineItems) ?
    purchase.lineItems :
    [];

  const matchingItems = lineItems.filter(
      (item) => item && item.productId === PRODUCT_ID,
  );

  const expiryTimeMillis = getLatestExpiryTimeMillis(matchingItems);
  const subscriptionState =
    purchase.subscriptionState ||
    "SUBSCRIPTION_STATE_UNSPECIFIED";
  const nowMillis = Date.now();

  let active = false;

  if (ENTITLED_STATES.has(subscriptionState)) {
    active = expiryTimeMillis > nowMillis;
  } else if (subscriptionState === CANCELED_STATE) {
    active = expiryTimeMillis > nowMillis;
  }

  return {
    active,
    hasMatchingProduct: matchingItems.length > 0,
    productId: PRODUCT_ID,
    subscriptionState,
    expiryTimeMillis,
  };
}

/**
 * Calculates the latest valid expiry from matching subscription line items.
 *
 * @param {Array<object>} lineItems Matching Google Play line items.
 * @return {number} Latest expiry time in millis, or 0 when missing.
 */
function getLatestExpiryTimeMillis(lineItems) {
  return lineItems.reduce((latest, item) => {
    const expiryTime = item && item.expiryTime;
    const expiry = Date.parse(expiryTime || "");

    if (!Number.isFinite(expiry)) {
      return latest;
    }

    return Math.max(latest, expiry);
  }, 0);
}

/**
 * Acknowledges verified purchases still pending acknowledgement.
 *
 * @param {string} purchaseToken Play purchase token.
 * @param {object} purchase Google Play subscription purchase.
 * @return {Promise<void>}
 */
async function acknowledgeIfNeeded(purchaseToken, purchase) {
  if (purchase.acknowledgementState !== ACKNOWLEDGEMENT_PENDING) {
    return;
  }

  try {
    await publisher.purchases.subscriptions.acknowledge({
      packageName: PACKAGE_NAME,
      subscriptionId: PRODUCT_ID,
      token: purchaseToken,
      requestBody: {},
    });
  } catch (error) {
    logger.error("Google Play acknowledgement failed.", {
      status: getErrorStatus(error),
      message: error && error.message,
    });

    throw new HttpsError(
        "unavailable",
        "The purchase was verified but could not be acknowledged.",
    );
  }
}

/**
 * Saves purchase-token ownership and the user's Plus entitlement atomically.
 *
 * @param {string} uid Firebase Authentication user ID.
 * @param {string} purchaseToken Raw Play purchase token.
 * @param {object} purchase Google Play subscription purchase.
 * @param {object} entitlement Safe entitlement summary.
 * @return {Promise<void>}
 */
async function saveEntitlement(uid, purchaseToken, purchase, entitlement) {
  const tokenHash = hashToken(purchaseToken);
  const linkedPurchaseToken = cleanString(purchase.linkedPurchaseToken);
  const linkedTokenHash = linkedPurchaseToken ?
    hashToken(linkedPurchaseToken) :
    null;
  const linkedTokenRef = linkedTokenHash && linkedTokenHash !== tokenHash ?
    db.collection("playPurchaseTokens").doc(linkedTokenHash) :
    null;

  const tokenRef = db.collection("playPurchaseTokens").doc(tokenHash);
  const userRef = db.collection("users").doc(uid);

  try {
    await db.runTransaction(async (transaction) => {
      const tokenSnapshot = await transaction.get(tokenRef);
      let linkedTokenSnapshot = null;

      if (linkedTokenRef) {
        linkedTokenSnapshot = await transaction.get(linkedTokenRef);
      }

      assertTokenOwner(tokenSnapshot, uid, "purchase");
      assertLinkedTokenOwner(linkedTokenSnapshot, uid);

      const updatedAt = admin.firestore.FieldValue.serverTimestamp();

      transaction.set(
          tokenRef,
          {
            uid,
            packageName: PACKAGE_NAME,
            productId: PRODUCT_ID,
            active: entitlement.active,
            subscriptionState: entitlement.subscriptionState,
            expiryTimeMillis: entitlement.expiryTimeMillis,
            linkedPurchaseTokenHash: linkedTokenHash,
            updatedAt,
          },
          {merge: true},
      );

      if (linkedTokenRef && linkedTokenSnapshot.exists) {
        transaction.set(
            linkedTokenRef,
            {
              active: false,
              supersededByTokenHash: tokenHash,
              updatedAt,
            },
            {merge: true},
        );
      }

      transaction.set(
          userRef,
          {
            plus: {
              active: entitlement.active,
              productId: PRODUCT_ID,
              subscriptionState: entitlement.subscriptionState,
              expiryTimeMillis: entitlement.expiryTimeMillis,
              purchaseTokenHash: tokenHash,
              updatedAt,
            },
          },
          {merge: true},
      );
    });
  } catch (error) {
    if (error instanceof HttpsError) {
      throw error;
    }

    logger.error("Could not save the Plus entitlement.", {
      message: error && error.message,
    });

    throw new HttpsError(
        "internal",
        "The verified entitlement could not be saved.",
    );
  }
}

/**
 * Rejects tokens already owned by another Firebase user.
 *
 * @param {FirebaseFirestore.DocumentSnapshot} snapshot Token document.
 * @param {string} uid Firebase Authentication user ID.
 * @param {string} label Log-safe token label.
 */
function assertTokenOwner(snapshot, uid, label) {
  if (!snapshot.exists) {
    return;
  }

  if (snapshot.get("uid") !== uid) {
    logger.warn("Purchase token ownership conflict.", {
      label,
    });

    throw new HttpsError(
        "permission-denied",
        "This purchase is linked to another account.",
    );
  }
}

/**
 * Rejects linked tokens owned by another Firebase user.
 *
 * @param {?FirebaseFirestore.DocumentSnapshot} snapshot Linked document.
 * @param {string} uid Firebase Authentication user ID.
 */
function assertLinkedTokenOwner(snapshot, uid) {
  if (!snapshot || !snapshot.exists) {
    return;
  }

  if (snapshot.get("uid") !== uid) {
    logger.warn("Linked purchase token ownership conflict.");

    throw new HttpsError(
        "permission-denied",
        "The previous subscription belongs to another account.",
    );
  }
}

/**
 * Extracts a numeric HTTP status from Google API errors.
 *
 * @param {*} error Google API error.
 * @return {number} HTTP status code or 0.
 */
function getErrorStatus(error) {
  return Number(
      (error && error.code) ||
      (error && error.response && error.response.status) ||
      0,
  );
}

/**
 * Deletes all known Firestore records owned by one Firebase Auth user.
 *
 * @param {string} uid Firebase Authentication user ID.
 * @return {Promise<object>} Deleted document counts.
 */
async function eraseFirestoreDataForUser(uid) {
  const userRef = db.collection("users").doc(uid);
  const userSnapshot = await userRef.get();
  const deleted = {
    checklistItems: 0,
    journalNotes: 0,
    recoverySessions: 0,
    syncTombstones: 0,
    playPurchaseTokens: 0,
    userDocument: userSnapshot.exists ? 1 : 0,
  };

  const journalDeleteCounts = await deleteJournalNotes(userRef);
  deleted.checklistItems = journalDeleteCounts.checklistItems;
  deleted.journalNotes = journalDeleteCounts.journalNotes;

  deleted.recoverySessions = await deleteQueryPageByPage(
      userRef.collection("recoverySessions"),
  );

  deleted.syncTombstones = await deleteQueryPageByPage(
      userRef.collection("syncTombstones"),
  );

  deleted.playPurchaseTokens = await deleteQueryPageByPage(
      db.collection("playPurchaseTokens").where("uid", "==", uid),
  );

  await userRef.delete();

  return deleted;
}

/**
 * Deletes journal notes and their known checklist item subcollections.
 *
 * @param {FirebaseFirestore.DocumentReference} userRef User document ref.
 * @return {Promise<{checklistItems: number, journalNotes: number}>}
 */
async function deleteJournalNotes(userRef) {
  const deleted = {
    checklistItems: 0,
    journalNotes: 0,
  };

  let snapshot = await userRef
      .collection("journalNotes")
      .limit(DELETE_BATCH_SIZE)
      .get();

  while (!snapshot.empty) {
    for (const note of snapshot.docs) {
      deleted.checklistItems += await deleteQueryPageByPage(
          note.ref.collection("checklistItems"),
      );
    }

    deleted.journalNotes += await deleteSnapshotDocuments(snapshot);

    snapshot = await userRef
        .collection("journalNotes")
        .limit(DELETE_BATCH_SIZE)
        .get();
  }

  return deleted;
}

/**
 * Deletes all documents returned by a Firestore query in safe pages.
 *
 * @param {FirebaseFirestore.Query} query Query or collection reference.
 * @return {Promise<number>} Number of deleted documents.
 */
async function deleteQueryPageByPage(query) {
  let deleted = 0;
  let snapshot = await query
      .limit(DELETE_BATCH_SIZE)
      .get();

  while (!snapshot.empty) {
    deleted += await deleteSnapshotDocuments(snapshot);

    snapshot = await query
        .limit(DELETE_BATCH_SIZE)
        .get();
  }

  return deleted;
}

/**
 * Deletes all documents in one query snapshot using a write batch.
 *
 * @param {FirebaseFirestore.QuerySnapshot} snapshot Query snapshot.
 * @return {Promise<number>} Number of deleted documents.
 */
async function deleteSnapshotDocuments(snapshot) {
  const batch = db.batch();

  snapshot.docs.forEach((documentSnapshot) => {
    batch.delete(documentSnapshot.ref);
  });

  await batch.commit();

  return snapshot.size;
}
