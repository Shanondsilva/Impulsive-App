# V28 technical invention disclosure

This is an internal engineering record, not a legal patent opinion. Patentability has not been assessed.

## Problem addressed

Protection events are noisy, private, asynchronous, and spread across service, navigation, intervention, persistence, background-work, and recovery boundaries. The implementation aims to provide useful personal support history without storing the protected source or overstating sparse observational evidence.

## Connected system architecture

The system combines incident leasing and deduplication, a pure versioned policy, generic intervention contracts, encrypted Room persistence, exclusive lifecycle transitions, a 20-minute observation worker, revision-bound rehearsal evidence, tiered private summaries, route-sensitive screen privacy, bounded retention, transactional restore, and user-controlled deletion.

## Potentially distinctive technical combinations

- A versioned protocol registry joined to a per-decision evidence passport.
- Separate assignment and actual-choice lineage across multiple intervention routes.
- Exact revision-bound rehearsal-to-use attribution after mutable plan edits.
- Privacy-safe evidence tiers that can decrease when retained evidence decreases.
- Debug-only deterministic historical policy replay that returns `InsufficientContext` rather than guessing.
- SQLCipher, app-lock, `FLAG_SECURE`, authenticated recovery ownership, and protected-source minimisation as connected boundaries.
- Work cancellation, pending-navigation cleanup, observation recovery, and coalesced backup refresh coordinated with reset, deletion, retention, and restore.

These are engineering observations only. They do not establish novelty, non-obviousness, validity, ownership, or infringement.

## Inventor contribution and implementation dates

Repository evidence identifies commits, not legal inventors. No inventor names are inferred here.

- `6434734` dated 2026-07-02 records protection, sync, and onboarding hardening.
- `84a91f3` dated 2026-07-25 records same-account recovery baseline work.
- `aedc81f` dated 2026-07-27 records cloud-restore and protection stabilization and is the current HEAD.
- The adaptive v28 implementation is present only in the uncommitted local worktree observed on 2026-07-29. That observation is not immutable source history until the owner chooses to commit it.

## Known prior-art categories

Relevant categories for later professional review include digital wellbeing blockers, just-in-time adaptive interventions, habit plans and implementation intentions, contextual bandits, spaced rehearsal, personal analytics dashboards, encrypted local databases, Android secure-window controls, retention policies, event-sourced audit records, and counterfactual/policy replay systems.

## Limitations and excluded claims

No claim is made that a patent is granted or pending, that the implementation creates a monopoly, that competitors lack similar features, or that clinical validation, treatment effect, product-market fit, or guaranteed outcomes exist. The current evidence is engineering test evidence and private observational history only.

## PathShift implementation disclosure

The added engineering combination is a versioned transparent range snapshot linked to an exact user-prepared content revision, existing live protected-moment delivery, genuine lifecycle outcomes, and a later non-causal review. Supporting mechanisms include root-incident deduplication, calendar/DST policy, one-active-cycle persistence, unique finalisation work, recovery schema 3, route privacy, retention, export, reset/deletion, and existing LP character continuity.

This description records engineering facts for later professional review. It does not assert novelty, inventorship determination, patentability, filing status, freedom to operate, clinical validity, or commercial differentiation.
# Protection Coach technical disclosure

The v28 Protection Coach package adds a versioned, privacy-preserving recommendation ledger that binds broad onboarding intent, explicit user configuration, and encrypted on-device protected-moment timing evidence. Its novelty posture is architectural and safety-focused: recommendations are advisory, explainable, and reversible; evidence is broad and local; and accepted edits are stored separately from the original recommendation.
