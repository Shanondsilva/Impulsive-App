# V28 data retention and screen privacy

## Retention options

| User option | Stored policy | Boundary |
|---|---|---|
| 90 days | `NinetyDays` | Terminal, observation-finalised history older than 90 days |
| Six months | `SixMonths` | Terminal, observation-finalised history older than 183 days |
| One year | `OneYear` | Terminal, observation-finalised history older than 365 days |
| Keep until reset | `KeepUntilReset` | No age-based deletion |

Six months is the default. It balances a useful comparison window with data minimisation. Cleanup is bounded, weekly, and also requested after startup, preference change, and restore. Open observations, open rehearsals, active routes, pending feedback, pending navigation, negative/corrupt timestamps, and a restore in progress are never age-pruned.

## Deletion boundaries

Reset personal learning removes decisions, feedback contained in those decisions, observations, rehearsals, pending adaptive navigation/feedback references, and tied work. It preserves plans, their revisions, adaptive preferences, and protection settings.

Delete all Moment data additionally removes plans and adaptive preferences and cancels retention state. Complete local/account deletion uses `clearAllTables`, cancels adaptive observation and retention work first, and clears pending runtime references.

## Backup interaction

Age-based deletion requests one coalesced recovery snapshot refresh only when records changed. Restore validates the entire adaptive section transactionally, brackets cleanup with restore state, recovers observation deadlines after commit, and then requests bounded retention cleanup. The outer automatic bundle remains format 3; adaptive payload schema is 2 with schema-1 compatibility.

## Screen privacy and user control

Screen privacy defaults on. Sensitive Moment Plan, rehearsal, feedback, What Works for Me, and decision-explanation routes apply Android `FLAG_SECURE`. An opaque Compose layer remains until the flag transition is applied so private content is not briefly shown. The controller preserves a secure flag owned by another feature and removes only the flag it owns.

The compact Settings control can disable this behavior. The preference is stored in encrypted Room and round-trips through encrypted recovery. Disabling it permits screenshots and recent-app previews on those routes.

## Limitations

`FLAG_SECURE` is an Android platform control and cannot protect a compromised device, accessibility service with elevated access, or information photographed by another camera. Retention applies to adaptive Room history, not unrelated operating-system logs or provider-managed historical backup copies. Remote backup replacement timing remains subject to the existing recovery provider.

## PathShift

The `path_shift` route joins the sensitive route set. It contains no personal navigation argument. PathShift defaults off and its consent copy names used and excluded inputs. Turning it off cancels the active cycle and PathShift work while preserving underlying history, protection, suggestions, and LP.

Finalised cycles follow the selected adaptive retention period. Active cycles and unfinalised reviews are excluded. Deletion removes the source cycle without retaining a hidden aggregate. Reset learning clears cycles and PathShift work but preserves plans, preferences, LP, character level, and protection. Delete all Moment data also clears the PathShift preference.
# Protection Coach retention and screen privacy

Protection Coach history follows the existing personal-support retention policy. Reset personal learning clears coach history, cold-start prior usage, timing cooldowns, and suppressed timing recommendations while preserving confirmed protection configuration. Full Moment data deletion clears coach data and preferences. Coach screens that expose personal timing evidence remain inside authenticated, screen-privacy-protected surfaces.
