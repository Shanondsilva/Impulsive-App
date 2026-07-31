# V28 PathShift Verification Receipt

Date: 29 July 2026  
Branch: `feature/adaptive-moment-engine-v28`  
Verified HEAD: `aedc81ff407651d888aed680a137c2f00fdc3da0`

This is the consolidated current receipt. Historical schema checkpoints are confined to the appendix; they are not current values.

| Checkpoint | Room | Adaptive recovery payload | JVM | Samsung |
|---|---:|---:|---:|---:|
| Verified pre-PathShift baseline | 10 | 2 | 1,219/1,219 | 60/60 |
| Current PathShift state | 11 | 3 | 1,254/1,254 | 75/75 |

Current gates: focused JVM PASS, full JVM PASS, debug and release Kotlin compilation PASS, lint PASS, debug APK PASS, Android-test APK PASS, exact Samsung classes PASS, final debug installation PASS, `git diff --check` PASS.

## 1. Baseline verification receipt

Before PathShift editing, the source was verified at Room 10 and adaptive payload schema 2. Focused adaptive suites passed 128/128 domain, 175/175 session, and 93/93 frontend. The full JVM suite passed 1,219/1,219. Debug compilation, lint, debug assembly, and Android-test assembly passed. The established exact connected-device hardening checkpoint was 60/60; the same 13 classes were rerun after PathShift and again passed 60/60.

## 2. Source-audit correction

The audit in `V28_PATHSHIFT_SOURCE_AUDIT.md` records that PathShift did not exist in the inspected baseline, while the Private Moment Loop, Adaptive Moment Engine, exact Moment Plan content revisions, rehearsal, feedback, retention, recovery, privacy, LP, character, and protection boundaries did. This receipt supersedes the former mixed schema-9/schema-10 narrative.

## 3. PathShift architecture

PathShift is a local-only layer over existing adaptive root-decision history:

`Room decisions -> pure forecast policy -> immutable cycle snapshot -> optional exact plan revision -> unique finalisation work -> aggregate Path Review`

The pure domain policy has no Android, Room, WorkManager, Firebase, billing, or UI dependency. Persistence is behind a repository. Session coordinators own lifecycle and recovery. Compose owns presentation only.

## 4. Forecast policy

Policy version 1 uses the previous 28 local-calendar days to estimate the next seven local-calendar days. It validates injected time zone, timestamps, policy version, future tolerance, evidence thresholds, DST boundaries, and consumer range caps. It does not use remote inference, cross-user learning, URL/domain/package identity, or protected content.

## 5. Exact calculation rules

For oldest-to-newest seven-day bucket counts `c1..c4`:

`expected = (1*c1 + 2*c2 + 3*c3 + 4*c4) / 10`

`deviation = sum(weight * abs(bucket - expected)) / 10`

`buffer = max(1, ceil(deviation))`

`lower = max(0, floor(expected - buffer))`

`upper = ceil(expected + buffer)`

Both bounds are capped at 99 and the available range is forced to contain at least two integer values. The forecast starts at the next local midnight and ends seven local days later. A common time window is a two-hour bucket with at least three roots and at least 30% of eligible roots.

## 6. Evidence-strength rules

- Insufficient: fewer than 7 protected roots, fewer than 5 distinct local days, less than 14 days from first to last evidence, empty history, invalid timestamp, or unsupported policy version.
- Early Estimate: minimum availability thresholds pass but cautious thresholds do not.
- Cautious Estimate: at least 14 protected roots, at least 10 distinct local days, and at least a 27-day evidence span.

The UI labels evidence strength and uses cautious, non-causal language.

## 7. Root-incident counting rule

Eligible evidence is one distinct `incidentToken` per root App or Website protection decision. `ExplicitUserSupport` follow-ups are excluded. Duplicate observations and support continuations therefore cannot inflate forecast or observed protected-moment counts.

## 8. Migration 10 to 11

Room advances explicitly from 10 to 11 through `Migration10To11`. The migration creates `path_shift_cycles`, four explicit indexes, the `pathShiftEnabled` preference defaulting to `0`, field checks, terminal-state checks, and one-active-cycle triggers. It preserves schema-10 rows, retains SQLCipher, registers all earlier migrations, and adds no destructive fallback. Samsung migration tests passed 2/2.

## 9. PathShift persistence

Cycles store opaque IDs, fixed lookback/forecast bounds, policy/evidence metadata, aggregate inputs, estimated range, optional common-window minutes, optional exact plan/revision IDs, aggregate review counts, and lifecycle timestamps/status. They do not store URLs, domains, package names, source identity, journal content, plan text, credentials, encryption material, utility scores, or random state. Insert, attach, finalise, and cancel operations use guarded exact-once DAO updates.

## 10. Lifecycle and WorkManager

Creation reuses an existing active cycle or stores one fixed snapshot and schedules unique work by cycle ID. Startup and restore recovery finalise overdue cycles or reschedule future cycles with stable unique identities. Disable, cancel, learning reset, and permanent deletion cancel PathShift work within their existing data contracts. Finalisation is idempotent.

## 11. Current Path UI

The private route is the constant `path_shift`; it has no personal navigation arguments. Home exposes a compact entry card. Settings provides an opt-in switch that defaults off and uses consent/disable confirmation. `Your Current Path` renders insufficient history, early/cautious estimates, common-window explanation, prepared plan, revision mismatch, cancellation, and Path Review states.

## 12. Prepare another path

An active forecast can attach an eligible Moment Plan and offer practice through the existing quick/guided rehearsal flow. Plan creation and editing remain in the existing Moment Plan UI. PathShift does not copy action text, cue text, or target text into its cycle.

## 13. Exact plan-revision connection

Preparation persists both opaque `planId` and exact `contentRevisionId`. Metadata-only edits retain the revision; meaningful content changes generate a new revision. The mismatch state requires an explicit choice to keep the prepared version or use the new version. Review attribution requires both identifiers to match.

## 14. Path Review

Finalisation stores the original estimate separately from observed root count and separately reports exact-revision Selected, Started, Completed, Dismissed, Wrong Timing, and final repeat-detected counts. Pending repeat observations are excluded rather than treated as false. Finalisation does not infer causation, effectiveness, or failure from relation to the forecast range.

## 15. LP preservation receipt

Forecast, preparation, cancellation, finalisation, review, positive/negative feedback attribution, repeat/no-repeat, and range comparison add and remove exactly zero LP through PathShift. Existing Game, Reading, Focus, and Journal reward ownership remains unchanged. JVM source contracts and Samsung `PathShiftLpNonInterferenceInstrumentedTest` passed.

## 16. Character integration

PathShift reuses the existing `MindCoreScene` and current level. It adds calm state-specific presentation, not a second mascot or progression store. A higher estimate does not select sad, shaming, alarmed, or failure-coded treatment. Reduced motion freezes ambient visual movement while keeping content usable.

## 17. Privacy and profiling controls

Future Path is explicit opt-in and defaults off. It is local-only, explainable, resettable, exportable under the existing contract, and removable. No Firebase PathShift collection or PathShift analytics collection was introduced. The profiling assessment documents purpose, inputs, exclusions, retention, user controls, and remaining manual checks.

## 18. Screen privacy

`path_shift` is registered with the route-sensitive secure-screen policy. It inherits app lock, secure capture handling, and recent-apps privacy without placing personal values in the route. Samsung privacy-route and existing route-hardening tests passed.

## 19. Retention

The existing adaptive retention coordinator now prunes expired finalised/cancelled PathShift cycles in bounded batches while preserving the active cycle. Retention never synthesises a review or turns pending observations into negative evidence. Samsung retention coverage passed.

## 20. Backup schema 3

Adaptive payload schema 3 adds `pathShiftEnabled` and allowed PathShift cycle fields. Encoding rejects invalid states and omits source identity, URLs, domains, packages, plan text, journal content, credentials, UID/email, utility, random state, and keys. The schema-3 round-trip and Samsung backup tests passed.

## 21. Legacy restore

The decoder accepts adaptive payload schemas 1, 2, and 3. Older bundles without PathShift restore with the feature off and no cycles. Import is transactional; corrupt cycles fail validation. After restore, one active future cycle reschedules and an overdue cycle finalises once.

## 22. Export

Readable and JSON exports include only allowed PathShift preference, forecast, lifecycle, exact opaque plan/revision, and aggregate review fields. Protected-source identity and private plan/journal text are excluded.

## 23. Reset and deletion

Reset personal learning clears cycles, reviews, adaptive learning, and PathShift work while preserving preference, plans, protection, LP, and character progression under the existing contract. Full adaptive/Moment deletion clears cycles and preference. Permanent local deletion also cancels PathShift work. Samsung reset/deletion coverage passed.

## 24. Subscription implementation

Existing subscription entitlements were preserved to avoid billing and protection risk. No PathShift control, privacy explanation, retention, reset, deletion, app lock, screen privacy, or required export was newly paywalled. No accuracy claim, LP multiplier, or cycle-comparison upsell was introduced. Commercial Free/Plus packaging remains a later configuration; manual cases 103 and 104 remain outstanding.

## 25. Documentation

Created PathShift-specific documents:

- `docs/innovation/V28_PATHSHIFT_SOURCE_AUDIT.md`
- `docs/innovation/V28_PATHSHIFT_ARCHITECTURE.md`
- `docs/innovation/V28_PATHSHIFT_FORECAST_POLICY.md`
- `docs/innovation/V28_PATHSHIFT_LP_CHARACTER_INTEGRATION.md`
- `docs/innovation/V28_PATHSHIFT_PRIVACY_AND_PROFILING_ASSESSMENT.md`
- `docs/innovation/V28_PATHSHIFT_ENDORSEMENT_EVIDENCE.md`

Updated architecture, defensibility, evidence, lineage, retention/privacy, invention disclosure, know-how, roadmap, this receipt, and the connected manual plan. The manual plan contains exactly 105 sequential, unpassed cases.

## 26. Exact files created

Files created for the PathShift workstream:

- `app/schemas/com.impulsive.app.backend.data.local.database.AppDatabase/11.json`
- `app/src/main/java/com/impulsive/app/backend/data/local/dao/PathShiftCycleDao.kt`
- `app/src/main/java/com/impulsive/app/backend/data/local/entity/PathShiftCycleEntity.kt`
- `app/src/main/java/com/impulsive/app/backend/data/repository/pathshift/RoomPathShiftCycleRepository.kt`
- `app/src/main/java/com/impulsive/app/backend/domain/pathshift/PathShiftCycle.kt`
- `app/src/main/java/com/impulsive/app/backend/domain/pathshift/PathShiftForecastPolicy.kt`
- `app/src/main/java/com/impulsive/app/backend/domain/repository/pathshift/PathShiftCycleRepository.kt`
- `app/src/main/java/com/impulsive/app/backend/session/pathshift/PathShiftDependencies.kt`
- `app/src/main/java/com/impulsive/app/backend/session/pathshift/PathShiftLifecycle.kt`
- `app/src/main/java/com/impulsive/app/backend/session/pathshift/PathShiftViewModel.kt`
- `app/src/main/java/com/impulsive/app/backend/session/pathshift/PathShiftWork.kt`
- `app/src/main/java/com/impulsive/app/frontend/pathshift/PathShiftCharacterPresentation.kt`
- `app/src/main/java/com/impulsive/app/frontend/screens/pathshift/PathShiftScreen.kt`
- `app/src/test/java/com/impulsive/app/backend/domain/pathshift/PathShiftForecastPolicyTest.kt`
- `app/src/test/java/com/impulsive/app/backend/session/pathshift/PathShiftLifecycleTest.kt`
- `app/src/test/java/com/impulsive/app/frontend/pathshift/PathShiftCharacterPresentationTest.kt`
- `app/src/test/java/com/impulsive/app/frontend/pathshift/PathShiftUiSourceTest.kt`
- `app/src/androidTest/java/com/impulsive/app/backend/data/local/database/PathShiftMigration10To11InstrumentedTest.kt`
- `app/src/androidTest/java/com/impulsive/app/backend/data/local/database/PathShiftSqlCipherSchema11InstrumentedTest.kt`
- `app/src/androidTest/java/com/impulsive/app/backend/data/restore/PathShiftBackupRestoreInstrumentedTest.kt`
- `app/src/androidTest/java/com/impulsive/app/pathshift/PathShiftInstrumentedFixtures.kt`
- `app/src/androidTest/java/com/impulsive/app/pathshift/PathShiftPersistenceInstrumentedTest.kt`
- `app/src/androidTest/java/com/impulsive/app/pathshift/PathShiftForecastTimezoneInstrumentedTest.kt`
- `app/src/androidTest/java/com/impulsive/app/pathshift/PathShiftFinalisationInstrumentedTest.kt`
- `app/src/androidTest/java/com/impulsive/app/pathshift/PathShiftRecoverySchedulingInstrumentedTest.kt`
- `app/src/androidTest/java/com/impulsive/app/pathshift/PathShiftPreparedRevisionInstrumentedTest.kt`
- `app/src/androidTest/java/com/impulsive/app/pathshift/PathShiftPrivacyRouteInstrumentedTest.kt`
- `app/src/androidTest/java/com/impulsive/app/pathshift/PathShiftRetentionInstrumentedTest.kt`
- `app/src/androidTest/java/com/impulsive/app/pathshift/PathShiftResetDeletionInstrumentedTest.kt`
- `app/src/androidTest/java/com/impulsive/app/pathshift/PathShiftLpNonInterferenceInstrumentedTest.kt`
- `app/src/androidTest/java/com/impulsive/app/pathshift/PathShiftCharacterPresentationInstrumentedTest.kt`
- `app/src/androidTest/java/com/impulsive/app/pathshift/PathShiftWebsiteProtectionContinuityInstrumentedTest.kt`
- the six documents listed in section 25.

The repository already contained other untracked Phase 0–6 files at this task's baseline; they are not falsely attributed to PathShift here.

## 27. Exact files modified

Files modified for PathShift integration:

- `app/src/main/java/com/impulsive/app/ImpulsiveApplication.kt`
- `app/src/main/java/com/impulsive/app/backend/data/UserDataExporter.kt`
- `app/src/main/java/com/impulsive/app/backend/data/UserDataManager.kt`
- `app/src/main/java/com/impulsive/app/backend/data/local/database/AppDatabase.kt`
- `app/src/main/java/com/impulsive/app/backend/data/local/dao/AdaptiveDecisionDao.kt`
- `app/src/main/java/com/impulsive/app/backend/data/local/dao/AdaptivePreferenceDao.kt`
- `app/src/main/java/com/impulsive/app/backend/data/local/entity/AdaptivePreferenceEntity.kt`
- `app/src/main/java/com/impulsive/app/backend/data/repository/adaptive/AdaptivePersistenceMappers.kt`
- `app/src/main/java/com/impulsive/app/backend/data/repository/adaptive/RoomAdaptiveDataRepository.kt`
- `app/src/main/java/com/impulsive/app/backend/data/restore/AdaptiveRestorePayloadCodec.kt`
- `app/src/main/java/com/impulsive/app/backend/data/restore/RestoreBundleImporter.kt`
- `app/src/main/java/com/impulsive/app/backend/data/restore/RestoreBundleWriter.kt`
- `app/src/main/java/com/impulsive/app/backend/domain/model/adaptive/AdaptiveMomentModels.kt`
- `app/src/main/java/com/impulsive/app/backend/domain/repository/adaptive/AdaptivePersistenceRepositories.kt`
- `app/src/main/java/com/impulsive/app/backend/session/adaptive/AdaptiveHistoryRetention.kt`
- `app/src/main/java/com/impulsive/app/backend/session/adaptive/AdaptivePhase4Dependencies.kt`
- `app/src/main/java/com/impulsive/app/frontend/components/MindCoreScene.kt`
- `app/src/main/java/com/impulsive/app/frontend/navigation/AppNavHost.kt`
- `app/src/main/java/com/impulsive/app/frontend/privacy/RouteSensitiveScreenPrivacy.kt`
- `app/src/main/java/com/impulsive/app/frontend/screens/dashboard/HomeScreen.kt`
- `app/src/main/java/com/impulsive/app/frontend/screens/settings/SettingsScreen.kt`
- `app/src/test/java/com/impulsive/app/backend/data/local/database/AdaptiveDatabaseSchemaSourceTest.kt`
- `app/src/test/java/com/impulsive/app/backend/data/restore/AdaptiveRestorePayloadCodecTest.kt`
- `app/src/test/java/com/impulsive/app/frontend/adaptive/AdaptivePhase5RepairSourceTest.kt`
- `app/src/test/java/com/impulsive/app/frontend/adaptive/AdaptivePhase6SourceTest.kt`
- `app/src/test/java/com/impulsive/app/frontend/adaptive/V28DocumentationTest.kt`
- `app/src/test/java/com/impulsive/app/frontend/privacy/RouteSensitiveScreenPrivacyTest.kt`
- `app/src/androidTest/java/com/impulsive/app/backend/data/local/database/AdaptivePersistenceInstrumentedTest.kt`
- `app/src/androidTest/java/com/impulsive/app/backend/session/adaptive/AdaptivePhase4InstrumentedTest.kt`
- `app/src/androidTest/java/com/impulsive/app/backend/session/adaptive/AdaptiveRehearsalInstrumentedTest.kt`
- the eight existing innovation documents and manual plan identified in section 25.

## 28. Every JVM command and result

| Command | Final result |
|---|---|
| `.\gradlew.bat :app:testDebugUnitTest --tests "com.impulsive.app.backend.domain.engine.adaptive.*"` | PASS, 128/128 |
| `.\gradlew.bat :app:testDebugUnitTest --tests "com.impulsive.app.backend.session.adaptive.*"` | PASS, 175/175 |
| `.\gradlew.bat :app:testDebugUnitTest --tests "com.impulsive.app.frontend.adaptive.*"` | PASS, 93/93 |
| `.\gradlew.bat :app:testDebugUnitTest --tests "com.impulsive.app.backend.domain.pathshift.*"` | PASS, 14/14 |
| `.\gradlew.bat :app:testDebugUnitTest --tests "com.impulsive.app.backend.session.pathshift.*"` | PASS, 9/9 |
| `.\gradlew.bat :app:testDebugUnitTest --tests "com.impulsive.app.frontend.pathshift.*"` | PASS, 10/10 |
| `.\gradlew.bat :app:testDebugUnitTest` | PASS, 1,254/1,254 across 130 classes |

Transparent corrections: the first frontend run found four stale pre-PathShift source assertions; the first full run found one stale Room-10 assertion. Their safety intent was preserved, expectations were advanced to the requested current state, and both exact commands then passed.

## 29. Every build command and result

| Command | Result |
|---|---|
| `.\gradlew.bat :app:compileDebugKotlin` | PASS |
| `.\gradlew.bat :app:compileReleaseKotlin` | PASS; no AAB produced |
| `.\gradlew.bat :app:lintDebug` | PASS |
| `.\gradlew.bat :app:assembleDebug` | PASS |
| `.\gradlew.bat :app:assembleDebugAndroidTest` | PASS |
| `git diff --check` | PASS |
| `git status --short` | PASS/read-only receipt; dirty intended worktree, nothing staged |

## 30. Every Samsung class and result

Device: Samsung SM-S928B, serial `R3CXA0L38CK`.

| Exact class | Result |
|---|---:|
| `com.impulsive.app.backend.data.local.database.PathShiftMigration10To11InstrumentedTest` | PASS 2/2 |
| `com.impulsive.app.backend.data.local.database.PathShiftSqlCipherSchema11InstrumentedTest` | PASS 1/1 |
| `com.impulsive.app.pathshift.PathShiftPersistenceInstrumentedTest` | PASS 1/1 |
| `com.impulsive.app.pathshift.PathShiftForecastTimezoneInstrumentedTest` | PASS 1/1 |
| `com.impulsive.app.pathshift.PathShiftFinalisationInstrumentedTest` | PASS 1/1 |
| `com.impulsive.app.pathshift.PathShiftRecoverySchedulingInstrumentedTest` | PASS 1/1 |
| `com.impulsive.app.pathshift.PathShiftPreparedRevisionInstrumentedTest` | PASS 1/1 |
| `com.impulsive.app.pathshift.PathShiftPrivacyRouteInstrumentedTest` | PASS 1/1 |
| `com.impulsive.app.pathshift.PathShiftRetentionInstrumentedTest` | PASS 1/1 |
| `com.impulsive.app.backend.data.restore.PathShiftBackupRestoreInstrumentedTest` | PASS 1/1 |
| `com.impulsive.app.pathshift.PathShiftResetDeletionInstrumentedTest` | PASS 1/1 |
| `com.impulsive.app.pathshift.PathShiftLpNonInterferenceInstrumentedTest` | PASS 1/1 |
| `com.impulsive.app.pathshift.PathShiftCharacterPresentationInstrumentedTest` | PASS 1/1 |
| `com.impulsive.app.pathshift.PathShiftWebsiteProtectionContinuityInstrumentedTest` | PASS 1/1 |
| `com.impulsive.app.backend.data.local.database.MomentPlanContentRevisionMigration9To10Test` | PASS 2/2 |
| `com.impulsive.app.backend.data.local.database.AdaptivePersistenceInstrumentedTest` | PASS 9/9 |
| `com.impulsive.app.backend.session.adaptive.AdaptivePhase4InstrumentedTest` | PASS 9/9 |
| `com.impulsive.app.backend.session.adaptive.AdaptiveRehearsalInstrumentedTest` | PASS 4/4 |
| `com.impulsive.app.backend.session.adaptive.AdaptiveDashboardInstrumentedTest` | PASS 4/4 |
| `com.impulsive.app.frontend.privacy.RouteSensitiveScreenPrivacyInstrumentedTest` | PASS 1/1 |
| `com.impulsive.app.backend.session.adaptive.AdaptiveHistoryRetentionInstrumentedTest` | PASS 2/2 |
| `com.impulsive.app.backend.data.restore.AdaptiveBackupRestoreInstrumentedTest` | PASS 5/5 |
| `com.impulsive.app.backend.session.adaptive.AdaptiveResetControlsInstrumentedTest` | PASS 3/3 |
| `com.impulsive.app.backend.session.adaptive.AdaptivePhase5FollowUpNavigationInstrumentedTest` | PASS 1/1 |
| `com.impulsive.app.backend.session.adaptive.AdaptivePhase6InstrumentedTest` | PASS 4/4 |
| `com.impulsive.app.backend.session.adaptive.AdaptivePhase5InstrumentedTest` | PASS 10/10 |
| `com.impulsive.app.backend.session.adaptive.AdaptivePhase5RepairInstrumentedTest` | PASS 6/6 |

Total: 27/27 classes and 75/75 tests. The first new-class run exposed non-void coroutine test methods and one implicit-index counting fixture; the first hardening rerun exposed three stale schema-10 assertions. The test harnesses were corrected, rebuilt, reinstalled, and every exact class was rerun to PASS.

## 31. Schema-10 hash

`8BC1810D8136A975375F6BA5A144F3A310550C8012C853F946DC9C5FD829042E`

## 32. Schema-11 hash

`CF7623F6B5342B187B94CC7D04822A00DA0CAFF76B95DF0E9DBDA5BD55763CA4`

## 33. APK details and SHA-256

- Path: `D:\Impulsive\Impulsive-App-GitHub\app\build\outputs\apk\debug\app-debug.apk`
- Size: 50,004,808 bytes
- Last write: `2026-07-29 03:31:32 +01:00`
- SHA-256: `E67BD8200C059A18589F7171808617BF54D2E323B2B808859ADCBAC3421D9CC4`
- Package: `com.impulsive.app.debug`
- versionCode: `27`
- versionName: `1.0.0`
- minSdk: `26`
- targetSdk: `36`
- Samsung installation: `Success`; installed package metadata matched.

## 34. Manual tests outstanding

All 105 cases in `docs/testing/V28_CONNECTED_INNOVATION_MANUAL_TESTS.md` remain deliberately unmarked. Cases 1–56 are preserved; cases 57–105 cover PathShift, privacy, LP, character/accessibility/themes, protection/VPN, subscription behavior, and complete schema-11 recovery. Instrumented PASS is not represented as manual PASS.

## 35. Version safety

Version remains exactly versionCode 27 and versionName 1.0.0. No version bump was made.

## 36. Confirmation no release AAB was built

No bundle/release command was invoked. A pre-existing `app-release.aab` remains in the build directory with last-write time `2026-07-27 05:29:22 +01:00`; this PathShift work did not build or replace it.

## 37. Confirmation nothing was staged, committed, pushed, merged, signed or uploaded

`git diff --cached --name-only` is empty. No staging, commit, push, merge, release signing, store upload, or external upload was performed. Debug signing occurred only as the normal local debug-APK build step.

## 38. Git diff summary

Tracked diff at receipt time: 38 files changed, 3,078 insertions, 239 deletions. The worktree also contains 174 untracked files from the cumulative uncommitted Phase 0–PathShift work, including 38 PathShift-specific created files listed in section 26. Line-ending conversion notices are warnings only; `git diff --check` passes.

## 39. Remaining risks

- All 105 manual UX/device scenarios remain outstanding, especially consent comprehension, TalkBack, largest font, Recents capture behavior, reboot/uninstall recovery, VPN continuity, and Free/Plus product behavior.
- Instrumented Website Protection coverage checks component/permission continuity and source isolation; it does not replace a real protected-site manual journey.
- Commercial Free/Plus packaging was intentionally not changed because doing so would create billing risk.
- No release artifact, release signing, store validation, or production rollout was performed.

## 40. Unsupported claims excluded

No claim is made that PathShift predicts with medical or statistical certainty, prevents relapse, causes outcomes, proves intervention effectiveness, improves accuracy for Plus, learns across users, uses federated/cloud ML, has clinical endorsement, or has a third-party partnership. Forecasts are transparent local estimates from the user's own eligible root-event history.

## Historical appendix — not current

Earlier V28 checkpoints used Room 8, then Room 9, and adaptive payload schema 1. A later pre-PathShift checkpoint established Room 10, payload schema 2, 1,219 JVM tests, and 60 Samsung tests. Those values are retained only as historical lineage. The current values are Room 11, payload schema 3, 1,254 JVM tests, and 75 Samsung tests as stated at the top of this receipt.
# Protection Coach verification addendum

Final verification for the Protection Coach package is recorded in the task receipt. Required pinned values:

- Version remains `27 / 1.0.0`.
- Room moves from `11` to `12`.
- Adaptive recovery payload moves from `3` to `4`.
- Schema 11 SHA-256 remains `CF7623F6B5342B187B94CC7D04822A00DA0CAFF76B95DF0E9DBDA5BD55763CA4`.
- Schema 12 SHA-256 is `4BCB83988077C640B7CB003E4CCF7485EDFDD7AF0794B1AB7D7BEA77C3AADA6A`.
