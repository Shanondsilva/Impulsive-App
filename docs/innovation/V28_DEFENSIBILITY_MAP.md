# V28 defensibility map

This document describes engineering barriers created by the current implementation. It does not claim that the interface cannot be copied, does not claim monopoly, and makes no legal conclusion about patentability.

| Barrier | Factual implementation | Why quick replication is harder | Limitation |
|---|---|---|---|
| Genuine incident deduplication | Unique persisted protection-incident tokens, a bounded moment window, existing incident lease, and cooldown separate detection noise from decisions. | A visual imitation without the service, lease, and database semantics can double count or interrupt repeatedly. | Device and OEM behavior still needs manual coverage. |
| Encrypted longitudinal local evidence | SQLCipher Room 10 stores decisions, preferences, plans, rehearsals, revision lineage, protocol passports, privacy, and retention; optional recovery uses the existing client-side encrypted envelope. | Useful patterns require durable, correctly migrated, privacy-preserving history. | No claim is made about resistance to a fully compromised unlocked device. |
| Assignment versus actual choice | Assigned suggestion and actual intervention are separate persisted fields, with an explicit override flag. | The model can report what the person chose instead of attributing an assigned option that was not used. | This is factual association, not causal proof. |
| Completion and dismissal semantics | Presentation, start, completion, and dismissal have guarded idempotent transitions and exclusive terminal states. | Outcome data cannot be inferred reliably from navigation alone. | A force stop before a write may leave an earlier valid state. |
| Feedback and Wrong Timing separation | Helped, Helped A Little, Did Not Help, Wrong Timing, and Not Provided remain distinct and revisions update one decision. | Timing problems are not silently converted into negative efficacy evidence. | Feedback remains subjective and optional. |
| Near-term repeat observation | Independent WorkManager deadlines finalize a 20-minute repeat result and recover after process death. | Replication requires coordination across protection events, persistence, time, and background execution. | Absence of a detected repeat does not prove a broader outcome. |
| Factual rehearsal-to-use history | Completed rehearsal records include plan ID and revision; later use matches the same ID and revision within seven days. | It avoids claiming continuity when a plan was edited or only happened to share text. | It reports temporal association only. |
| Privacy-safe dashboard thresholds | Within-family statements require at least three terminal uses; comparisons use the established eight-use, four-feedback, and 25-point safeguards. | Sparse data is kept factual, separate, and non-ranking. | Thresholds are product safeguards, not validated clinical thresholds. |
| Safe multi-route integration | Short Pause, games, reading, and Moment Plans all use an explicit completion/dismissal contract while existing route rules remain intact. | Every path must cooperate with lifecycle, feedback, observation, app lock, and protection fallback. | Manual route and accessibility checks remain necessary. |
| Restore and deletion correctness | Adaptive restore is all-or-nothing, validates IDs/enums/invariants, preserves IDs, recovers deadlines, and full deletion cancels work before clearing Room. | Backup correctness spans encryption, ownership, validation, transactions, process recovery, and deletion. | Remote deletion timing remains governed by the existing provider contract. |
| Automated and physical-device testing | Pure policy tests, Room tests, source-policy regressions, exact Samsung instrumentation, and full build/lint gates cover separate layers. | A quick copy would need to reproduce both behavior and evidence across layers. | Automated success is not equivalent to completing the manual plan. |
| Accumulated user history | The system becomes more personally informative as valid on-device completed events accumulate. | The value is partly in correctly collected longitudinal history, not a static screen. | There is no network effect claim and no uploaded behavioural model. |
| Versioned intervention contracts | Nine version-1 protocol records bind family, route, start, completion, dismissal, data, accessibility, and fallback semantics. | New routes cannot silently change historical meaning without a versioned contract. | Registry versioning does not establish clinical validity. |
| Decision evidence passport | Policy version, protocol identity, eligible-plan count, assignment, actual choice, and exact plan revision travel together through Room, restore, export, and explanation. | It makes historical interpretation auditable across mutable content and policy changes. | It remains observational evidence. |
| Route-sensitive privacy and retention | Secure-window ownership and bounded age deletion are driven by encrypted preferences and coordinated with active navigation, restore, work cancellation, and backup refresh. | Privacy controls are connected to lifecycle state rather than being isolated settings. | Platform and backup-provider limitations remain. |
| Safe policy replay | A pure deterministic engine compares recorded and candidate assignments only when exact context is reconstructable; fixtures exist only in debug source. | It supports regression analysis without mutating production selection or user history. | It is not an online experiment or causal estimate. |

## Defensible implementation shape

The defensible element is the connected behavior across protection, lifecycle correctness, encrypted evidence, privacy thresholds, user control, recovery, and testing. The screen layout itself is intentionally simple and can be copied. The accumulated history belongs to the user and is portable through private export and encrypted recovery.

## PathShift addition

| Element | Connected implementation | Reproduction difficulty | Evidence boundary |
|---|---|---|---|
| Transparent forecast snapshot | Versioned deterministic policy, calendar buckets, evidence gates, immutable SQLCipher cycle | Requires policy, migration, lifecycle, recovery, privacy, and UI agreement | Estimate is not certainty or diagnosis |
| Exact preparation lineage | User-selected plan UUID and content revision connected to existing rehearsal and live decisions | Mutable content must not rewrite historical meaning | Preparation is not effectiveness evidence |
| Factual path review | Exact-window root counting with separated outcomes and final repeat state | Requires deduplication, finalisation, process recovery, and non-causal copy | Observational only |
| LP character continuity | Existing LP authority and `MindCoreScene` with presentation-only PathShift states | Avoids a parallel reward/character model | Character is not health or forecast severity |
# Protection Coach addition

Protection Coach strengthens the v28 defensibility story by joining broad onboarding intent, user-confirmed protection configuration, on-device protected-moment evidence, and versioned explanation receipts. The defensible boundary is the combination: advisory recommendations, explicit confirmation, broad local time windows, Room 12 ledger state, recovery/export compatibility, and no raw protected-source identity.
