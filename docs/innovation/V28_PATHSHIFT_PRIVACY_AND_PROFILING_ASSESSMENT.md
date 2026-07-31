# V28 PathShift privacy and profiling assessment

Status: engineering DPIA-style draft for human privacy and legal review. This is not formal legal approval.

## Purpose

Provide an optional, on-device cautious range from recent protected-moment history, connect it to a deliberately selected plan revision, and compare the stored estimate with later factual records.

## Data used

- opaque protected-incident token for deduplication;
- generic App, Website, or excluded Explicit User Support source kind;
- event time;
- counts and distinct local dates;
- broad two-hour window;
- forecast policy version;
- opaque plan and content-revision IDs;
- generic lifecycle outcomes and final repeat result.

## Data excluded

Protected source identity, URL, domain, package, page, search, journal content, plan text in cycle records, account email, UID, location, camera, microphone, emotion inference, cloud behavioural profile, utility, random state, and encryption material.

## Profiling explanation

PathShift is optional profiling in the plain-language sense that it evaluates recent on-device event patterns to show a range. It uses a documented deterministic formula, not an opaque model. The setting defaults off and requires an affirmative action.

## Lawful-basis decision

Placeholder for human/legal review. Engineering does not select or assert the production lawful basis.

## Necessity and proportionality

Only generic, bounded fields needed for counting, exact revision linkage, scheduling, and factual review are retained. Numerical output is gated by minimum evidence, always ranged, capped, labelled cautiously, and accompanied by used/not-used disclosure.

## User control

Users can decline, enable, deliberately create a cycle, change or remove a prepared plan, stop a cycle, turn the feature off, report an estimate locally, reset learning, export, or delete all Moment data. Turning off cancels PathShift work but leaves protection, suggestions, history, and LP unchanged unless separately reset or deleted.

## Retention

Cycles follow the selected adaptive retention period. Active cycles and unfinalised reviews are preserved. Expired finalised cycles are removed without hidden aggregates, and backup refresh is coalesced.

## Accuracy limitations

Recorded moments can be incomplete, timing can be affected by device state, and the estimate is not a promise. A lower count is not proof of improvement; a higher count is not failure. Plan preparation and rehearsal are not effectiveness evidence.

## Vulnerable-user considerations

The UI avoids clinical labels, red risk gauges, fear copy, sad/damaged character states, and punitive LP. Controls remain available without payment. App lock and route-sensitive screen privacy reduce casual disclosure.

## Risk mitigations

SQLCipher, opaque UUIDs, strict decoding, explicit migration, one-active-cycle invariant, exact revision matching, local-only calculation, no Firebase/analytics PathShift collection, no sensitive permission, conservative evidence gate, bounded retention, reset/deletion, and non-causal review wording.

## Residual risks

Shoulder surfing after unlock, device compromise, incomplete protected-moment capture, misunderstood estimates, timezone changes, OEM worker timing, and user-export handling remain possible. Accessibility, translation, consumer comprehension, lawful basis, and retention configuration require human review.
