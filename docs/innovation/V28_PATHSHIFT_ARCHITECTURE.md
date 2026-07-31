# V28 PathShift Closed Loop architecture

## Purpose

PathShift connects an encrypted root protected-moment history snapshot, a cautious seven-day range, an exact user-prepared Moment Plan revision, real protected-moment decisions, and a later factual review. It does not claim prediction certainty, diagnosis, treatment, improvement, or causation.

## Layers

1. `PathShiftForecastPolicy` is pure Kotlin. It accepts opaque incident tokens, generic source kind, timestamps, generation time, and an injected timezone.
2. `PathShiftCycleEntity` persists one immutable forecast snapshot in SQLCipher Room schema 11.
3. `PathShiftCoordinator` creates one cycle, deliberately attaches an eligible plan revision, and cancels only PathShift.
4. `PathShiftReviewFinaliser` counts genuine root records in the fixed window and finalises once.
5. `WorkManagerPathShiftWorkScheduler` uses one unique job per opaque cycle UUID and startup recovery reschedules or finalises overdue work.
6. `PathShiftViewModel` maps repositories into disabled, insufficient, ready, active, awaiting-review, finalised, and unavailable states.
7. `PathShiftScreen` uses an opaque `path_shift` route, app lock, route-sensitive `FLAG_SECURE`, and the existing `MindCoreScene`.

## Fixed lifecycle

- A user explicitly enables Future Path and deliberately creates a cycle.
- Forecast input and policy output are calculated once and persisted.
- Opening the screen never regenerates an active forecast.
- New incidents do not change the stored estimate.
- Plan selection stores plan UUID plus exact `contentRevisionId` without rewriting the estimate or historical decisions.
- Finalisation counts only records inside the original window and runs idempotently.
- Cancellation leaves Moment history, plans, protection, personal suggestions, and LP intact.

## Root protected-moment rule

A root moment is a unique `protectionIncidentToken` whose `AdaptiveSourceKind` is `App` or `Website`. `ExplicitUserSupport` is a follow-up decision and is excluded. Duplicate tokens are counted once. Rehearsal, dashboard visits, plan edits, feedback screens, and follow-up choices do not create root moments.

## Exact review counting

- Observed: unique root records in `[forecast start, forecast end)`.
- Prepared-plan selected: actual intervention is Moment Plan and plan UUID plus actual content revision exactly match the stored prepared pair.
- Started, Completed, and Dismissed are separate fields.
- Wrong Timing is counted separately across root decisions.
- Repeat is counted only when `RepeatDetected` has a finalised observation. Pending is not false.

## Failure and recovery

Database constraints enforce valid ranges, paired plan revision fields, terminal exclusivity, non-negative counts, and one active cycle. Unique WorkManager identity prevents duplicate finalisation jobs. Startup and post-restore recovery finalise overdue valid cycles or reschedule future ones. Invalid recovery payloads fail validation before Room writes.

## Non-interference

No forecast, incident, plan selection, feedback, review, cancellation, or observation calls the LP store. The 25/75 adaptive boundary, family utility, Website Protection, VPN, monitoring, lease, cooldown, and protected-source reopening rules are unchanged.
