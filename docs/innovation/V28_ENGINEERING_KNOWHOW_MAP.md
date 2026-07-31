# V28 engineering know-how map

This map identifies repository knowledge that should be shared on a need-to-know basis. It contains no credentials, API keys, encryption keys, or secret values.

| Know-how | Representative source | Control reason |
|---|---|---|
| Decision lifecycle invariants | `AdaptiveDecisionLifecycleCoordinator`, DAO guarded updates | Prevent double start, conflicting terminal states, and false attribution |
| Policy thresholds and exploration boundary | `AdaptiveRecommendationPolicy`, insight builders | Preserve deterministic eligibility and cautious evidence behavior |
| Migration and restore validation | migration 9-to-10, `AdaptiveRestorePayloadCodec`, importer transaction | Avoid partial, ambiguous, or incompatible historical data |
| Protocol validation | `InterventionProtocolRegistry` and validator | Keep route semantics, accessibility, data policy, and fallback aligned |
| Privacy-safe aggregation | `WhatWorksForMeBuilder`, `AdaptiveInsightBuilder` | Prevent sparse-data ranking and Wrong Timing contamination |
| Replay fixtures | debug `AdaptiveReplayDebugScenarios` | Expose comparison mechanics without leaking user data or changing production |
| Incident deduplication | protection lease, cooldown, incident-token uniqueness | Prevent one difficult moment becoming multiple decisions |
| Recovery scheduling | observation recovery, retention worker, snapshot refresh scheduler | Avoid races among process death, deletion, restore, and cloud refresh |

Recommended controls are repository access control, least-privilege review, protected branches, auditable change review, no fixture copying into public demonstrations, and release checks proving debug replay surfaces are absent. Security material remains in the existing platform keystore and recovery-key mechanisms, never in this documentation.

## PathShift know-how

| Know-how | Representative source | Control reason |
|---|---|---|
| Forecast calculation and evidence gates | `PathShiftForecastPolicy` | Prevent unstable, exact, negative, or sparse-history claims |
| Cycle invariants and exact-once finalisation | schema 11, `PathShiftCycleDao`, finaliser | Prevent snapshot drift and duplicate review |
| Exact revision preparation | coordinator and content-revision policy | Prevent edited content from rewriting historical preparation |
| Recovery and retention | unique PathShift work, schema 3, retention store | Avoid reboot, restore, and deletion races |
| Character non-interference | `PathShiftCharacterPresentation`, `MindCoreScene` | Keep LP and health interpretation outside forecasting |
# Protection Coach engineering know-how

Implementation know-how added in v28:

- pure Kotlin recommendation policies before persistence or UI;
- Room 11→12 migration with explicit ledger invariants and indexes;
- configuration-driven app-monitoring policy that preserves legacy-off transition safety;
- payload 4 recovery compatibility with payload 1/2/3 restore support;
- source-level UI route checks ensuring only opaque suggestion IDs enter navigation.
