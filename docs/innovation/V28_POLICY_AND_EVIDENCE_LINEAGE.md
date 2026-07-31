# V28 policy and evidence lineage

## Recommendation policy

`AdaptiveRecommendationPolicyVersion.Current` is version 1. Every new decision passport records the positive policy version, assignment mode, reason code, eligible-family mask, eligible Moment Plan count, and protocol identity. A policy version is evidence about the rule set used; it is not a score or outcome claim.

The existing behavior remains: first attempt offers Short Pause, repeated attempts use the chooser, and controlled exploration retains the 25/75 boundary. Candidate replay policy is debug-only and cannot participate in live selection.

## Decision evidence passport

The passport binds:

1. the assigned generic family and protocol;
2. the actual generic family and protocol;
3. whether the person chose differently;
4. the recommendation policy version and eligible-plan count;
5. lifecycle, feedback, and repeat-observation facts;
6. exact plan content revision when a Moment Plan was assigned or used.

Assignment is never treated as actual use. Completion and dismissal are exclusive. Wrong Timing is reported separately and excluded from substantive comparison feedback.

## Plan and rehearsal lineage

Every plan has an opaque UUID content revision. Metadata-only edits preserve it. Action-affecting edits create a new revision. Rehearsal start captures both plan ID and content revision. A later real use is attributed to practice only when both match and the use falls within the existing seven-day window.

Legacy schema-9 rows receive deterministic UUID revisions derived from plan ID and historical update timestamp. This is compatibility lineage, not a claim that content was independently observed.

## Evidence tiers

| Tier | Meaning |
|---|---|
| `CountOnly` | A few recorded moments; factual counts only |
| `EarlyPattern` | Enough same-option terminal history for an early personal pattern |
| `ComparisonSupported` | Existing guarded thresholds support a cautious comparison |

The tier is recalculated from retained local history. It can decrease after retention or reset. Utility values, random state, and protected source identity are not displayed or exported.

## Restore and historical interpretation

Adaptive schema 2 carries revisions, policy version, protocols, eligible-plan count, screen privacy, and retention. Schema 1 remains readable with deterministic legacy revisions, policy version 1, and protocol version 1 where the family and route can be established. A deleted or otherwise unavailable historical Moment Plan is retained as generic Moment Plan history without fabricating a route protocol.

Invalid UUIDs, revision shape, protocol pairing, protocol-family compatibility, terminal states, observation state, rehearsal state, plan limits, preferred-plan consistency, privacy preference, or retention preference reject the complete adaptive section before Room is written.

## PathShift lineage

Forecast policy version 1 is independent of app version. A cycle records the exact input counts, distinct days, local lookback/window boundaries, estimate range, evidence strength, broad common window, and policy version. Plan preparation adds an exact plan UUID and content revision without changing that snapshot. Review uses only decisions inside the stored forecast window and requires exact actual plan revision matching.

Adaptive recovery schema 3 carries PathShift opt-in, cycles, evidence, policy, preparation, status, and final counts. Schemas 1 and 2 restore with Future Path off and no fabricated forecast. Restoring a valid active cycle reschedules or finalises it; malformed cycles reject the adaptive section transactionally.
# Protection Coach policy lineage

- `ProtectionCoachPolicyVersion.Current = 1` is independent of app version.
- Onboarding suggestions use broad semantic reasons only.
- The cold-start prior is weak and expires after 10 genuine root moments, 3 substantive feedback answers, or EarlyPattern evidence.
- Smart window suggestions require 7 root moments, 5 dates, 14 days, 30% bucket share, 3 incidents in bucket, and no duplicate/suppressed/covered equivalent.
- Room 12 stores suggestion decisions so historical recommendations remain interpretable after policy changes.
