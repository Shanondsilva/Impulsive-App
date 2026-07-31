# V28 endorsement evidence matrix

| Claim | Implemented component | Source file | Test class | Device/manual evidence | Remaining limitation | Prohibited overclaim |
|---|---|---|---|---|---|---|
| Originality of the connected implementation | Private Moment Loop spanning preparation, protection, choice, action, observation, insight, control, recovery, and deletion | `backend/session/adaptive/*`, `frontend/screens/adaptive/*` | `AdaptivePhase5IntegrationTest`, `AdaptiveOutcomeCoordinatorTest`, `MomentPlanRehearsalCoordinatorTest` | Exact Samsung automation is recorded in the verification receipt; manual plan outstanding | Similar individual patterns may exist elsewhere; no market-wide novelty search was performed | Do not claim patentability, monopoly, or that the interface cannot be copied |
| Technical integration | Shared decision lifecycle across Short Pause, games, reading, Moment Plans, feedback, and follow-up | `AdaptiveDecisionLifecycle.kt`, `AdaptiveOutcomeCoordinator.kt`, `AppNavHost.kt` | `AdaptiveDecisionLifecycleTest`, `AdaptiveOutcomeCoordinatorTest`, `AdaptivePhase5FollowUpInstrumentedTest` | Samsung route-regression instrumentation; manual visual routes outstanding | OEM and process edge cases require continued device testing | Do not claim every Android device is verified |
| Privacy | SQLCipher Room, minimal adaptive schema, no raw protected-source column, guarded private screens, redacted export, encrypted recovery | `AppDatabase.kt`, `AdaptiveDecisionEntity.kt`, `AdaptiveRestorePayloadCodec.kt`, `UserDataExporter.kt` | `AdaptivePersistenceSourceTest`, `AdaptiveBackupRestoreSourceTest`, `PersonalSupportPrivacyControlsSourceTest` | SQLCipher schema opening and restore run on Samsung; notification/manual privacy checks outstanding | App security still depends on OS/device integrity and user lock choices | Do not claim absolute anonymity or unbreakable encryption |
| Barrier to quick replication | Deduplication, exclusive lifecycle, observation recovery, revision-safe rehearsal linkage, thresholded insights, transactional restore | `AdaptiveMomentCoordinator.kt`, `AdaptiveObservation.kt`, `PracticeToUseObservation.kt`, `WhatWorksForMeBuilder.kt`, `RestoreBundleImporter.kt` | `AdaptiveMomentCoordinatorTest`, `AdaptiveObservationAndResetTest`, `PracticeToUseObservationTest`, `WhatWorksForMeBuilderTest`, `AdaptiveRestorePayloadCodecTest` | Physical Room/lifecycle tests recorded; full manual loop outstanding | Engineering depth is evidence, not proof that competitors cannot reproduce it | Do not claim an insurmountable moat |
| Founder-led implementation evidence | Repository-local code, migrations, tests, and technical records are attributable through version control | This repository and this document set | Full automated suite and git diff receipt | Working tree and HEAD recorded in final receipt | Authorship identity and employment facts are outside the code evidence | Do not invent founder biography, team size, or exclusive authorship |
| Viability | Debug application compiles, lints, assembles, installs, persists, migrates, restores, and executes focused device tests | Android application module | Full JVM suite, lint, assemble, exact Samsung classes | Final receipt records factual outcomes and APK hash | No commercial demand, retention, revenue, or clinical outcome study is supplied | Do not claim customers, revenue, treatment efficacy, or product-market fit |
| Scalability foundation | Bounded queries, indexed Room tables, WorkManager deadlines, coalesced snapshot work, capped restore payloads | `AdaptiveDecisionDao.kt`, `MomentPlanRehearsalDao.kt`, `RestoreSnapshotRefreshScheduler.kt`, `AdaptiveRestorePayloadCodec.kt` | `AdaptivePersistenceInstrumentedTest`, `AdaptiveRestorePayloadCodecTest`, scheduling source tests | Device database tests recorded; no load benchmark performed | Local scale limits and cloud quota behavior have not been benchmarked at population scale | Do not claim proven hyperscale |
| User control | Explanation, family controls, reset learning, full Moment-data deletion, export, encrypted recovery | `HowSuggestionsWorkScreen.kt`, `PersonalSupportControlsViewModel.kt`, `SettingsScreen.kt`, `UserDataManager.kt` | `PersonalSupportPrivacyControlsSourceTest`, `AdaptiveObservationAndResetTest`, `AdaptiveResetControlsInstrumentedTest` | Samsung reset tests recorded; reviewer confirmation dialogs remain in manual plan | Remote backup replacement/deletion has provider timing constraints | Do not claim instantaneous erasure from every provider backup copy |

## Evidence boundary

The following v28 hardening evidence is suitable for an engineering review. "Automated" does not mean manually verified or clinically validated.

| Review question | Repository evidence | Status | Claim boundary |
|---|---|---|---|
| Are intervention semantics versioned? | Nine registry contracts plus validator and manifest | Implemented; automated tests | No clinical protocol claim |
| Can a decision be interpreted after policy/content change? | Decision passport, policy version, assigned/actual protocol, exact content revision | Implemented; automated tests | Historical fact, not causation |
| Is sparse history represented cautiously? | Count-only, early-pattern, comparison-supported tiers; Wrong Timing separation | Implemented; automated tests | No effectiveness ranking |
| Can the user inspect a recommendation? | Room-loaded explanation receipt by decision UUID | Implemented; automated tests; manual outstanding | Explains recorded inputs, not hidden utility |
| Are private screens protected? | Route policy, `FLAG_SECURE` owner, opaque transition, encrypted preference | Implemented; JVM and device automation; manual outstanding | Android control has platform limitations |
| Is old history minimized safely? | Four retention options, bounded transaction, active/restore exclusions | Implemented; JVM and device automation; manual outstanding | Does not govern unrelated provider logs |
| Can policy changes be replayed safely? | Pure replay engine, exact-context failure, ten debug fixtures, release compilation gate | Implemented; automated tests | Not a production experiment |
| Does recovery preserve lineage? | Outer format 3, adaptive schema 2, schema-1 deterministic defaults, all-or-nothing import | Implemented; automated tests; device/manual restore outstanding | Provider timing and full uninstall flow need manual evidence |
| Can the user erase adaptive data? | Scoped learning reset, Moment-data deletion, complete local clear, work/runtime cancellation | Implemented; automated tests; manual outstanding | Remote replacement timing follows provider contract |

Manual tests 1 through 56 remain unmarked and outstanding. No endorsement claim should represent them as passed.

There is no evidence in this repository for patents, registered IP, customers, partnerships, revenue, clinical validation, medical outcomes, health-provider adoption, or population-scale performance. Those claims must not be inferred from the implementation.

## PathShift evidence

| Question | Evidence | Status | Boundary |
|---|---|---|---|
| Is the estimate transparent and cautious? | Policy v1 formula, gates, range, cap, timezone and DST tests | Implemented; automated | Not prediction certainty |
| Does preparation preserve exact meaning? | Stored plan UUID plus `contentRevisionId`; mismatch flow | Implemented; automated | Not effectiveness evidence |
| Is later review factual? | Exact-window root count and separated lifecycle outcomes | Implemented; automated; manual outstanding | No causal claim |
| Is processing private? | SQLCipher, source exclusions, opaque route, app lock, `FLAG_SECURE`, no Firebase collection | Implemented; automated; manual outstanding | Compromised-device risk remains |
| Is LP preserved? | No PathShift reward calls; existing character presentation inputs only | Implemented; automated | LP is not recovery progress |

Manual tests 57 through 105 are also unmarked and outstanding.
# Protection Coach evidence row

Approved statement: Impulsive connects broad user-stated intentions with user-confirmed protection settings, then uses genuine encrypted on-device protected-moment evidence to identify recurring time patterns and propose the smallest relevant configuration adjustment. Every recommendation is explainable, versioned and subject to user confirmation.

Excluded claims: first in the world, patented, clinically validated, treatment effect, diagnosis, automatic prevention, guaranteed reduction, and competitor impossibility.
