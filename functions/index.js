const crypto = require("crypto");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {onRequest} = require("firebase-functions/v2/https");
const {defineSecret} = require("firebase-functions/params");
const {onMessagePublished} = require("firebase-functions/v2/pubsub");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");
const {google} = require("googleapis");
const {
  createFirestoreRtdnStore,
  createRtdnProcessor,
} = require("./subscriptionRtdn");
const {
  ENTITLEMENT_KIND,
  productDefinition,
  requireProductForEntitlement,
  userFieldForEntitlement,
  storedEntitlementKindForProductId,
} = require("./subscriptionCatalog");
const {
  cleanString,
  deriveExpectedProductEntitlement,
} = require("./subscriptionEntitlement");

admin.initializeApp();

const db = admin.firestore();

const PACKAGE_NAME = "com.impulsive.app";
const REGION = "us-central1";
const WEB_DELETE_SHARED_SECRET = defineSecret("WEB_DELETE_SHARED_SECRET");
const PLAY_RTDN_TOPIC = "play-rtdn";
const SERVICE_ACCOUNT =
  "impulsive-play-verifier@useimpulsive.iam.gserviceaccount.com";
const TOKEN_HASH_ALGORITHM = "sha256";
const MAX_PRODUCT_ID_LENGTH = 128;
const MAX_PURCHASE_TOKEN_LENGTH = 4096;
const DELETE_BATCH_SIZE = 450;
// Journal deletion performs a paged walk of notes and each checklist
// subcollection. Leave enough headroom for large, long-lived accounts.
const ERASE_USER_DATA_TIMEOUT_SECONDS = 540;

const ACKNOWLEDGEMENT_PENDING = "ACKNOWLEDGEMENT_STATE_PENDING";

const auth = new google.auth.GoogleAuth({
  scopes: ["https://www.googleapis.com/auth/androidpublisher"],
});

const publisher = google.androidpublisher({
  version: "v3",
  auth,
});

const rtdnStore = createFirestoreRtdnStore({
  db,
  fieldValue: admin.firestore.FieldValue,
  packageName: PACKAGE_NAME,
  logger,
  userFieldForEntitlement,
  storedEntitlementKindForProductId,
});

const processPlayRtdn = createRtdnProcessor({
  store: rtdnStore,
  verifyPurchase: verifyPurchaseWithGoogle,
  acknowledgePurchase: acknowledgeIfNeeded,
  logger,
  packageName: PACKAGE_NAME,
  productDefinition,
  hashToken,
  isRetryableError: (error) => {
    return error instanceof HttpsError && error.code === "unavailable";
  },
});

/**
 * Verifies a Google Play subscription purchase for the signed-in Firebase
 * user, scoped to exactly one entitlement kind. A purchase whose product
 * belongs to a different entitlement kind (for example a Safe Browse Pass
 * purchase token submitted to the Plus callable) is rejected before Google
 * is ever contacted -- entitlement kinds can never verify or credit each
 * other's purchases.
 *
 * @param {*} request Callable request.
 * @param {string} expectedEntitlementKind Entitlement kind this callable
 *   is scoped to.
 * @param {string} wrongProductMessage Message for a mismatched product.
 * @param {function(string): Promise<object>} verifyPurchase Play verifier.
 * @param {function(string, object, string): Promise<boolean>} acknowledge
 *   Acknowledgement dependency.
 * @param {function(...*): Promise<void>} save Entitlement-save dependency.
 * @return {Promise<object>} Verified entitlement summary.
 */
async function verifySubscriptionPurchase(
    request,
    expectedEntitlementKind,
    wrongProductMessage,
    verifyPurchase = verifyPurchaseWithGoogle,
    acknowledge = acknowledgeIfNeeded,
    save = saveEntitlement,
) {
  const uid = request.auth && request.auth.uid;

  if (!uid) {
    throw new HttpsError(
        "unauthenticated",
        "You must be signed in to verify a purchase.",
    );
  }

  const input = parseInput(request.data, expectedEntitlementKind);
  const purchase = await verifyPurchase(input.purchaseToken);
  const entitlement = deriveExpectedProductEntitlement(
      purchase,
      input.definition,
      Date.now(),
  );

  if (!entitlement.hasMatchingProduct) {
    logger.warn("Verified purchase did not contain the expected product.", {
      uid,
      entitlementKind: expectedEntitlementKind,
      productId: entitlement.productId,
      subscriptionState: entitlement.subscriptionState,
    });

    throw new HttpsError("permission-denied", wrongProductMessage);
  }

  if (!entitlement.metadataValid) {
    logger.warn("Verified purchase had no valid base-plan metadata.", {
      uid,
      entitlementKind: expectedEntitlementKind,
      productId: entitlement.productId,
    });

    throw new HttpsError(
        "failed-precondition",
        "The subscription plan could not be verified.",
    );
  }

  if (!entitlement.active) {
    logger.info("Verified purchase is not currently entitled.", {
      uid,
      entitlementKind: expectedEntitlementKind,
      productId: entitlement.productId,
      subscriptionState: entitlement.subscriptionState,
      expiryTimeMillis: entitlement.expiryTimeMillis,
    });

    throw new HttpsError(
        "failed-precondition",
        "The purchase is not currently active.",
    );
  }

  await acknowledge(
      input.purchaseToken,
      purchase,
      entitlement.productId,
  );
  await save(
      uid,
      input.purchaseToken,
      purchase,
      entitlement,
      input.definition,
  );

  logger.info("Subscription verified.", {
    uid,
    entitlementKind: expectedEntitlementKind,
    productId: entitlement.productId,
    subscriptionState: entitlement.subscriptionState,
    expiryTimeMillis: entitlement.expiryTimeMillis,
  });

  return {
    active: true,
    productId: entitlement.productId,
    basePlanId: entitlement.basePlanId,
    planKind: entitlement.planKind,
    subscriptionState: entitlement.subscriptionState,
    expiryTimeMillis: entitlement.expiryTimeMillis,
  };
}

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
      memory: "512MiB",
    },
    async (request) => verifySubscriptionPurchase(
        request,
        ENTITLEMENT_KIND.PLUS,
        "The purchase does not contain Impulsive Plus.",
    ),
);

/**
 * Verifies a Google Play Safe Browse Pass purchase for the signed-in user.
 */
exports.verifySafeBrowsePassSubscription = onCall(
    {
      region: REGION,
      serviceAccount: SERVICE_ACCOUNT,
      enforceAppCheck: true,
      maxInstances: 10,
      timeoutSeconds: 60,
      memory: "512MiB",
    },
    async (request) => verifySubscriptionPurchase(
        request,
        ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
        "The purchase does not contain the Safe Browse Pass.",
    ),
);

/**
 * Deletes the signed-in user's known Firestore data and Firebase Auth account.
 */
exports.eraseUserData = onCall(
    {
      region: REGION,
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
        const cloudRecoveryFilesDeleted =
          await eraseCloudRecoveryForUser(uid);
        let authUserDeleted = false;

        try {
          await admin.auth().deleteUser(uid);
          authUserDeleted = true;
        } catch (error) {
          if (!error || error.code !== "auth/user-not-found") {
            throw error;
          }

          // Idempotent retry: the Firebase Auth user was already deleted.
          authUserDeleted = true;
        }

        logger.info("User data and Firebase Auth account erased.", {
          uid,
          authUserDeleted,
          checklistItems: deleted.checklistItems,
          journalNotes: deleted.journalNotes,
          recoverySessions: deleted.recoverySessions,
          syncTombstones: deleted.syncTombstones,
          playPurchaseTokens: deleted.playPurchaseTokens,
          userDocument: deleted.userDocument,
          cloudRecoveryFilesDeleted,
        });

        return {
          success: true,
          deleted,
          cloudRecoveryFilesDeleted,
          authUserDeleted,
        };
      } catch (error) {
        logger.error("Could not erase user account and data.", {
          uid,
          message: error && error.message,
          appCheckPresent: Boolean(request.app),
          appCheckAppId: request.app && request.app.appId,
        });

        throw new HttpsError(
            "internal",
            "User account and data could not be erased.",
        );
      }
    },
);

/**
 * Server-to-server Firebase Authentication existence check for the website
 * account-deletion request flow.
 *
 * This endpoint is never called directly by the browser. The Cloudflare Worker
 * calls it with the same shared secret used by eraseUserByEmail.
 *
 * The Worker must keep the browser response identical whether the account
 * exists or not, preventing account enumeration.
 */
exports.checkUserExistsByEmail = onRequest(
    {
      region: REGION,
      maxInstances: 5,
      timeoutSeconds: 30,
      memory: "256MiB",
      secrets: [WEB_DELETE_SHARED_SECRET],
      cors: false,
    },
    async (req, res) => {
      if (req.method !== "POST") {
        res.status(405).json({
          success: false,
          error: "method_not_allowed",
        });
        return;
      }

      const authHeader = req.get("authorization") || "";
      const provided = authHeader.startsWith("Bearer ") ?
        authHeader.slice("Bearer ".length) :
        "";
      const expected = WEB_DELETE_SHARED_SECRET.value();

      const providedBuf = Buffer.from(provided);
      const expectedBuf = Buffer.from(expected);

      const secretOk =
        providedBuf.length === expectedBuf.length &&
        crypto.timingSafeEqual(providedBuf, expectedBuf);

      if (!expected || !secretOk) {
        res.status(401).json({
          success: false,
          error: "unauthorized",
        });
        return;
      }

      const email =
        req.body && typeof req.body.email === "string" ?
          req.body.email.trim().toLowerCase() :
          "";

      if (!email || email.length > 254 || !email.includes("@")) {
        res.status(400).json({
          success: false,
          error: "invalid_email",
        });
        return;
      }

      try {
        await admin.auth().getUserByEmail(email);

        res.status(200).json({
          success: true,
          exists: true,
        });
      } catch (error) {
        if (error && error.code === "auth/user-not-found") {
          res.status(200).json({
            success: true,
            exists: false,
          });
          return;
        }

        logger.error("checkUserExistsByEmail failed.", {
          message: error && error.message,
        });

        res.status(500).json({
          success: false,
          error: "internal",
        });
      }
    },
);

/**
 * Server-to-server account erasure for website-initiated deletion.
 *
 * The public website cannot call the App-Check-protected eraseUserData onCall.
 * Instead, its Cloudflare Worker (after verifying the user owns the email via a
 * confirmation link) calls THIS endpoint with a shared secret. We look up the
 * Firebase user by email and erase everything, reusing the same logic as the
 * in-app delete. Never expose this secret client-side.
 */
exports.eraseUserByEmail = onRequest(
    {
      region: REGION,
      maxInstances: 5,
      timeoutSeconds: 120,
      memory: "256MiB",
      secrets: [WEB_DELETE_SHARED_SECRET],
      cors: false,
    },
    async (req, res) => {
      // Only POST.
      if (req.method !== "POST") {
        res.status(405).json({success: false, error: "method_not_allowed"});
        return;
      }

      // Constant-time shared-secret check via Authorization: Bearer <secret>.
      const authHeader = req.get("authorization") || "";
      const provided = authHeader.startsWith("Bearer ") ?
        authHeader.slice("Bearer ".length) : "";
      const expected = WEB_DELETE_SHARED_SECRET.value();
      const providedBuf = Buffer.from(provided);
      const expectedBuf = Buffer.from(expected);
      const secretOk = providedBuf.length === expectedBuf.length &&
        crypto.timingSafeEqual(providedBuf, expectedBuf);
      if (!expected || !secretOk) {
        res.status(401).json({success: false, error: "unauthorized"});
        return;
      }

      const email = req.body && typeof req.body.email === "string" ?
        req.body.email.trim().toLowerCase() : "";
      if (!email || email.length > 254 || !email.includes("@")) {
        res.status(400).json({success: false, error: "invalid_email"});
        return;
      }

      try {
        let userRecord;
        try {
          userRecord = await admin.auth().getUserByEmail(email);
        } catch (error) {
          if (error && error.code === "auth/user-not-found") {
            // Idempotent + non-enumerating: report success even if no account.
            res.status(200).json({
              success: true,
              deleted: false,
              cloudRecoveryFilesDeleted: 0,
            });
            return;
          }
          throw error;
        }

        const uid = userRecord.uid;
        const deleted = await eraseFirestoreDataForUser(uid);
        const cloudRecoveryFilesDeleted =
          await eraseCloudRecoveryForUser(uid);
        try {
          await admin.auth().deleteUser(uid);
        } catch (error) {
          if (!error || error.code !== "auth/user-not-found") {
            throw error;
          }
        }

        logger.info("User erased via website deletion.", {
          uid,
          deleted,
          cloudRecoveryFilesDeleted,
        });
        res.status(200).json({
          success: true,
          deleted: true,
          cloudRecoveryFilesDeleted,
        });
      } catch (error) {
        logger.error("eraseUserByEmail failed.", {
          message: error && error.message,
        });
        res.status(500).json({success: false, error: "internal"});
      }
    },
);

/**
 * Consumes Google Play Real-time Developer Notifications and refreshes
 * the affected user's Plus entitlement from the authoritative Play state.
 */
exports.handlePlayRtdn = onMessagePublished(
    {
      topic: PLAY_RTDN_TOPIC,
      region: REGION,
      serviceAccount: SERVICE_ACCOUNT,
      maxInstances: 5,
      memory: "256MiB",
      retry: true,
    },
    async (event) => {
      return processPlayRtdn(event);
    },
);

/**
 * Returns the server-held entitlement for one entitlement kind for the
 * signed-in user and lazily downgrades records whose expiry has already
 * passed. Reads only the one Firestore field that entitlement kind owns --
 * it can never see or report another kind's entitlement.
 *
 * @param {*} request Callable request.
 * @param {string} expectedEntitlementKind Entitlement kind this callable
 *   is scoped to.
 * @param {*} firestore Firestore dependency.
 * @param {*} fieldValue Firestore FieldValue dependency.
 * @return {Promise<object>} Entitlement summary.
 */
async function checkEntitlementForRequest(
    request,
    expectedEntitlementKind,
    firestore = db,
    fieldValue = admin.firestore.FieldValue,
) {
  const uid = request.auth && request.auth.uid;

  if (!uid) {
    throw new HttpsError(
        "unauthenticated",
        "You must be signed in to check your subscription.",
    );
  }

  const userField = userFieldForEntitlement(expectedEntitlementKind);

  if (!userField) {
    throw new HttpsError(
        "internal",
        "The subscription definition is invalid.",
    );
  }

  const userRef = firestore.collection("users").doc(uid);
  const snapshot = await userRef.get();
  const entitlement = snapshot.exists ? snapshot.get(userField) : null;

  if (!entitlement || typeof entitlement !== "object") {
    return {active: false};
  }

  const expiryTimeMillis = Number(entitlement.expiryTimeMillis) || 0;
  const storedActive = entitlement.active === true;
  const activeNow = storedActive && expiryTimeMillis > Date.now();

  if (storedActive && !activeNow) {
    await userRef.set(
        {
          [userField]: {
            active: false,
            productId: entitlement.productId || null,
            basePlanId: entitlement.basePlanId || null,
            planKind: entitlement.planKind || null,
            expiryTimeMillis,
            updatedAt: fieldValue.serverTimestamp(),
          },
        },
        {merge: true},
    );

    logger.info("Stale entitlement lazily downgraded.", {uid, userField});
  }

  return {
    active: activeNow,
    productId: entitlement.productId || null,
    basePlanId: entitlement.basePlanId || null,
    planKind: entitlement.planKind || null,
    subscriptionState: entitlement.subscriptionState ||
      "SUBSCRIPTION_STATE_UNSPECIFIED",
    expiryTimeMillis,
  };
}

/**
 * Returns the server-held Plus entitlement for the signed-in user and
 * lazily downgrades records whose expiry has already passed.
 */
exports.checkPlusEntitlement = onCall(
    {
      region: REGION,
      enforceAppCheck: true,
      maxInstances: 10,
      memory: "256MiB",
    },
    async (request) => checkEntitlementForRequest(
        request, ENTITLEMENT_KIND.PLUS,
    ),
);

/**
 * Returns the server-held Safe Browse Pass entitlement for the signed-in
 * user and lazily downgrades records whose expiry has already passed.
 */
exports.checkSafeBrowsePassEntitlement = onCall(
    {
      region: REGION,
      enforceAppCheck: true,
      maxInstances: 10,
      memory: "256MiB",
    },
    async (request) => checkEntitlementForRequest(
        request, ENTITLEMENT_KIND.SAFE_BROWSE_PASS,
    ),
);


// ---------------------------------------------------------------------------
// Account-scoped onboarding completion
// ---------------------------------------------------------------------------

exports.getOnboardingCompletion = onCall(
    {
      region: REGION,
      enforceAppCheck: true,
      maxInstances: 10,
      memory: "256MiB",
    },
    async (request) => getOnboardingCompletionForRequest(request),
);

exports.markOnboardingCompleted = onCall(
    {
      region: REGION,
      enforceAppCheck: true,
      maxInstances: 10,
      memory: "256MiB",
    },
    async (request) => markOnboardingCompletedForRequest(request),
);

/**
 * Returns account-scoped onboarding completion for the authenticated user.
 *
 * @param {*} request Callable request.
 * @param {*} firestore Firestore dependency.
 * @return {Promise<{onboardingCompleted: boolean}>} Completion state.
 */
async function getOnboardingCompletionForRequest(request, firestore = db) {
  const uid = request.auth && request.auth.uid;

  if (!uid) {
    throw new HttpsError(
        "unauthenticated",
        "You must be signed in to check onboarding status.",
    );
  }

  assertEmptyCallableData(request.data);

  const snapshot = await firestore.collection("users").doc(uid).get();

  const onboardingCompleted = snapshot.exists &&
    snapshot.get("account.onboardingCompleted") === true;

  return {
    onboardingCompleted,
  };
}

/**
 * Marks onboarding complete for the authenticated user.
 *
 * @param {*} request Callable request.
 * @param {*} firestore Firestore dependency.
 * @param {*} fieldValue Firestore FieldValue dependency.
 * @return {Promise<{success: boolean, onboardingCompleted: boolean}>} Result.
 */
async function markOnboardingCompletedForRequest(
    request,
    firestore = db,
    fieldValue = admin.firestore.FieldValue,
) {
  const uid = request.auth && request.auth.uid;

  if (!uid) {
    throw new HttpsError(
        "unauthenticated",
        "You must be signed in to update onboarding status.",
    );
  }

  assertEmptyCallableData(request.data);

  await firestore.collection("users").doc(uid).set(
      {
        account: {
          onboardingCompleted: true,
          onboardingCompletedAt:
            fieldValue.serverTimestamp(),
        },
      },
      {merge: true},
  );

  return {
    success: true,
    onboardingCompleted: true,
  };
}

/**
 * Rejects all client-controlled callable payload fields.
 *
 * @param {*} data Callable request data.
 */
function assertEmptyCallableData(data) {
  if (data == null) {
    return;
  }

  if (
    typeof data !== "object" ||
    Array.isArray(data) ||
    Object.keys(data).length !== 0
  ) {
    throw new HttpsError(
        "invalid-argument",
        "This operation does not accept client data.",
    );
  }
}
/**
 * Validates and normalizes callable input, rejecting any product ID that is not
 * catalogued under the expected entitlement kind.
 *
 * @param {*} data Callable request data.
 * @param {string} expectedEntitlementKind Entitlement kind this callable
 *   is scoped to.
 * @return {{productId: string, purchaseToken: string, definition: object}}
 *   Normalized input.
 */
function parseInput(data, expectedEntitlementKind) {
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

  const definition = requireProductForEntitlement(
      productId, expectedEntitlementKind,
  );

  if (!definition) {
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
    definition,
  };
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
      code: error && error.code,
      name: error && error.name,
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
 * Acknowledges verified purchases still pending acknowledgement. Idempotent
 * because it runs only when the verified Google response itself reports
 * ACKNOWLEDGEMENT_STATE_PENDING.
 *
 * @param {string} purchaseToken Play purchase token.
 * @param {object} purchase Google Play subscription purchase.
 * @param {string} productId Server-verified Play product ID.
 * @param {*} publisherClient Android Publisher API client dependency.
 * @return {Promise<boolean>} Whether acknowledgement was newly performed.
 */
async function acknowledgeIfNeeded(
    purchaseToken, purchase, productId, publisherClient = publisher,
) {
  if (purchase.acknowledgementState !== ACKNOWLEDGEMENT_PENDING) {
    return false;
  }

  try {
    await publisherClient.purchases.subscriptions.acknowledge({
      packageName: PACKAGE_NAME,
      token: purchaseToken,
      requestBody: {},
    });

    return true;
  } catch (error) {
    const status = getErrorStatus(error);

    logger.error("Google Play acknowledgement failed.", {
      status,
      code: error && error.code,
      name: error && error.name,
      productId,
    });

    if (status >= 500 || status === 429) {
      throw new HttpsError(
          "unavailable",
          "The purchase was verified but could not be acknowledged.",
      );
    }

    throw new HttpsError(
        "failed-precondition",
        "Google Play rejected purchase acknowledgement.",
    );
  }
}

/**
 * Resolves the entitlement kind recorded on a purchase-token document,
 * falling back to migrating an unmigrated legacy product ID.
 *
 * @param {?FirebaseFirestore.DocumentSnapshot} snapshot Token document.
 * @return {?string} Entitlement kind, or null when unresolvable.
 */
function entitlementKindFromSnapshot(snapshot) {
  if (!snapshot || !snapshot.exists) {
    return null;
  }

  const explicit = cleanString(snapshot.get("entitlementKind"));

  if (
    explicit === ENTITLEMENT_KIND.PLUS ||
    explicit === ENTITLEMENT_KIND.SAFE_BROWSE_PASS
  ) {
    return explicit;
  }

  return storedEntitlementKindForProductId(
      cleanString(snapshot.get("productId")),
  );
}

/**
 * Rejects a stored token whose entitlement kind is unknown or conflicts
 * with the entitlement kind currently being verified -- even when the
 * Firebase UID is the same, a Plus token can never be linked into a Safe
 * Browse Pass write or vice versa.
 *
 * @param {?FirebaseFirestore.DocumentSnapshot} snapshot Token document.
 * @param {string} expectedEntitlementKind Entitlement kind being verified.
 * @param {string} label Log-safe token label.
 */
function assertTokenEntitlementKind(snapshot, expectedEntitlementKind, label) {
  if (!snapshot || !snapshot.exists) {
    return;
  }

  const existingKind = entitlementKindFromSnapshot(snapshot);

  if (!existingKind) {
    logger.warn("Stored purchase token has no resolvable entitlement kind.", {
      label,
    });

    throw new HttpsError(
        "permission-denied",
        "The stored purchase cannot be safely linked.",
    );
  }

  if (existingKind !== expectedEntitlementKind) {
    logger.warn("Purchase token entitlement conflict.", {
      label,
      expectedEntitlementKind,
      existingKind,
    });

    throw new HttpsError(
        "permission-denied",
        "The purchase belongs to another subscription.",
    );
  }
}

/**
 * Saves purchase-token ownership and the user's entitlement atomically,
 * writing only the one Firestore field the trusted catalogue definition
 * owns, and rejecting any current or linked token whose entitlement kind
 * does not match.
 *
 * @param {string} uid Firebase Authentication user ID.
 * @param {string} purchaseToken Raw Play purchase token.
 * @param {object} purchase Google Play subscription purchase.
 * @param {object} entitlement Safe entitlement summary.
 * @param {object} definition Trusted catalogue product definition.
 * @param {*} firestore Firestore dependency.
 * @param {*} fieldValue Firestore FieldValue dependency.
 * @return {Promise<void>}
 */
async function saveEntitlement(
    uid,
    purchaseToken,
    purchase,
    entitlement,
    definition,
    firestore = db,
    fieldValue = admin.firestore.FieldValue,
) {
  const validPlanKind =
    entitlement.planKind === "prepaid" ||
    entitlement.planKind === "autoRenewing";
  const definitionValid = definition &&
    definition.productId === entitlement.productId &&
    definition.entitlementKind === entitlement.entitlementKind &&
    cleanString(definition.userField).length > 0 &&
    cleanString(entitlement.basePlanId).length > 0 &&
    validPlanKind;

  if (!definitionValid) {
    throw new HttpsError(
        "internal",
        "The subscription definition is invalid.",
    );
  }

  const tokenHash = hashToken(purchaseToken);
  const linkedPurchaseToken = cleanString(purchase.linkedPurchaseToken);
  const linkedTokenHash = linkedPurchaseToken ?
    hashToken(linkedPurchaseToken) :
    null;
  const linkedTokenRef = linkedTokenHash && linkedTokenHash !== tokenHash ?
    firestore.collection("playPurchaseTokens").doc(linkedTokenHash) :
    null;

  const tokenRef = firestore.collection("playPurchaseTokens").doc(tokenHash);
  const userRef = firestore.collection("users").doc(uid);

  try {
    await firestore.runTransaction(async (transaction) => {
      const tokenSnapshot = await transaction.get(tokenRef);
      let linkedTokenSnapshot = null;

      if (linkedTokenRef) {
        linkedTokenSnapshot = await transaction.get(linkedTokenRef);
      }

      assertTokenOwner(tokenSnapshot, uid, "purchase");
      assertLinkedTokenOwner(linkedTokenSnapshot, uid);
      assertTokenEntitlementKind(
          tokenSnapshot, definition.entitlementKind, "purchase",
      );
      assertTokenEntitlementKind(
          linkedTokenSnapshot, definition.entitlementKind, "linked",
      );

      const updatedAt = fieldValue.serverTimestamp();

      transaction.set(
          tokenRef,
          {
            uid,
            packageName: PACKAGE_NAME,
            productId: entitlement.productId,
            entitlementKind: definition.entitlementKind,
            basePlanId: entitlement.basePlanId,
            planKind: entitlement.planKind,
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
              uid,
              entitlementKind: definition.entitlementKind,
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
            [definition.userField]: {
              active: entitlement.active,
              productId: entitlement.productId,
              basePlanId: entitlement.basePlanId,
              planKind: entitlement.planKind,
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

    logger.error("Could not save the entitlement.", {
      userField: definition.userField,
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

  const existingUid = cleanString(snapshot.get("uid"));

  if (existingUid && existingUid !== uid) {
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

  const existingUid = cleanString(snapshot.get("uid"));

  if (existingUid && existingUid !== uid) {
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

if (process.env.NODE_ENV === "test") {
  exports.__onboardingCompletionTest = {
    assertEmptyCallableData,
    getOnboardingCompletionForRequest,
    markOnboardingCompletedForRequest,
  };
  exports.__subscriptionTest = {
    parseInput,
    verifySubscriptionPurchase,
    checkEntitlementForRequest,
    saveEntitlement,
    acknowledgeIfNeeded,
    entitlementKindFromSnapshot,
  };
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
 * Deletes Storage recovery files owned by one Firebase Auth user.
 *
 * @param {string} uid Firebase Authentication user ID.
 * @return {Promise<number>} Deleted Storage object count.
 */
async function eraseCloudRecoveryForUser(uid) {
  const prefix = `cloud_recovery/${uid}/`;
  const [files] = await admin.storage().bucket().getFiles({prefix});
  await Promise.all(files.map((file) => file.delete({ignoreNotFound: true})));
  return files.length;
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
