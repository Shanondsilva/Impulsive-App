# V28 connected innovation manual tests

Run these tests one at a time on the review device in the exact order below. Automation does not mark any item as passed. For every item, fill `PASS` or `FAIL` and attach the named screenshot, screen recording, log extract, or exported file.

## 1. Create and edit Moment Plan

- Precondition: Signed in or guest onboarding complete; app unlocked; fewer than six enabled plans.
- Exact action: Settings > Personal Support > My Plans > Create a plan. Enter title, cue, action, and future cue; save; reopen; edit the action; save.
- Expected screen: List shows one compact plan row; detail shows the edited text and enabled/preferred state.
- Expected persistence: Close and relaunch; the same plan UUID-backed record and edited revision remain.
- Protection expectation: Website Protection, VPN, monitoring, lease, and cooldown states do not change.
- Privacy expectation: Plan text appears only after app-lock clearance and never in a route or notification.
- PASS/FAIL:
- Evidence filename:

## 2. Guided rehearsal

- Precondition: At least one enabled Moment Plan; app unlocked.
- Exact action: Open the plan; tap guided practice; advance through each step once; tap complete.
- Expected screen: Calm guided sequence ends with completion and returns safely.
- Expected persistence: Exactly one completed rehearsal exists and the matching plan's last-practised time updates.
- Protection expectation: No protection decision, block, cooldown, points, or repeat observation is created.
- Privacy expectation: Only the private in-app screen displays plan text.
- PASS/FAIL:
- Evidence filename:

## 3. Quick rehearsal

- Precondition: An enabled Moment Plan exists; app unlocked.
- Exact action: Open the plan and tap quick practice once; complete it.
- Expected screen: Compact practice flow displays the plan action and completion control.
- Expected persistence: One Quick rehearsal is completed; double tapping does not create another event.
- Protection expectation: Protection services continue unchanged and no adaptive decision is created.
- Privacy expectation: No plan text appears in notification content or navigation arguments.
- PASS/FAIL:
- Evidence filename:

## 4. Rehearsal process recreation

- Precondition: Start a guided rehearsal but do not finish it.
- Exact action: Advance one step; background the app; terminate its process from developer tools; relaunch and return through Recents or My Plans.
- Expected screen: The rehearsal reloads its persisted event and safe progress behavior; it does not pretend to be completed.
- Expected persistence: The original rehearsal UUID remains; Back records dismissal only after confirmation.
- Protection expectation: No incident or intervention is fabricated during recreation.
- Privacy expectation: App lock is required again where configured.
- PASS/FAIL:
- Evidence filename:

## 5. Home rehearsal card

- Precondition: At least one enabled plan; one completed rehearsal preferred.
- Exact action: Open Home; use Practise My Plan; return; use Choose a Plan.
- Expected screen: One compact lavender-led “Practise for next time” card shows the last-practised fact and both actions without crowding Home.
- Expected persistence: Chosen practice writes one rehearsal only.
- Protection expectation: Home actions do not alter protection state.
- Privacy expectation: The card does not expose plan text while app lock is pending.
- PASS/FAIL:
- Evidence filename:

## 6. First protected moment

- Precondition: Website or app protection active; no open incident for the target.
- Exact action: Attempt one protected source once.
- Expected screen: Existing protection interruption appears and offers eligible personal support without duplicate overlays.
- Expected persistence: One decision is stored for the incident token with FirstAttempt intensity.
- Protection expectation: Lease and cooldown remain authoritative; fallback still blocks if adaptive persistence fails.
- Privacy expectation: No raw URL, domain, package, title, or search text appears in adaptive UI or logs.
- PASS/FAIL:
- Evidence filename:

## 7. Repeated protected moment

- Precondition: Complete or dismiss test 6; remain inside the 20-minute moment window.
- Exact action: Attempt the protected source again after the existing lease permits it.
- Expected screen: Repeated-moment support flow appears once and preserves Skip cue/rating behavior.
- Expected persistence: A distinct valid repeated decision or repeat observation is recorded according to the existing incident contract.
- Protection expectation: Cooldown and incident lease prevent duplicate interruption storms.
- Privacy expectation: History stores generic source kind only.
- PASS/FAIL:
- Evidence filename:

## 8. Recently rehearsed plan selection

- Precondition: Two equal eligible plans for the same cue; complete a rehearsal for only one current revision.
- Exact action: Trigger an eligible protected moment and choose the matching cue.
- Expected screen: If plan candidates are otherwise equal, the recently rehearsed current revision is offered; no family-level advantage is introduced.
- Expected persistence: Decision stores the selected plan ID and its exact revision time.
- Protection expectation: Existing 25/75 family assignment behavior is unchanged.
- Privacy expectation: Explanation uses cautious wording and does not expose plan text outside the private screen.
- PASS/FAIL:
- Evidence filename:

## 9. Why-this explanation

- Precondition: A suggestion has been presented.
- Exact action: Tap the Why this suggestion affordance.
- Expected screen: Short factual explanation references eligibility, cue match, recent practice, or limited evidence as applicable; it does not promise an outcome.
- Expected persistence: Opening the explanation does not mutate decision or feedback state.
- Protection expectation: Protection remains active behind the flow.
- Privacy expectation: No utility, probability, source identity, or medical language appears.
- PASS/FAIL:
- Evidence filename:

## 10. Short Pause completion and feedback

- Precondition: A decision with Short Pause available.
- Exact action: Select Short Pause; start; complete the timer; submit Helped; revise to Helped A Little.
- Expected screen: Completion transitions once, then feedback screen confirms the latest answer.
- Expected persistence: One decision is completed; one feedback field is revised rather than duplicated.
- Protection expectation: The protected source is not reopened automatically.
- Privacy expectation: Feedback is not paired with UID/email in logs or analytics.
- PASS/FAIL:
- Evidence filename:

## 11. Game completion and feedback

- Precondition: A decision with Pivot Game available.
- Exact action: Select a game; finish through its valid completion path; submit Did Not Help.
- Expected screen: Existing game completion UI appears, followed by adaptive feedback.
- Expected persistence: Actual choice is Pivot Game and completion is recorded once; dismissal remains null.
- Protection expectation: Plus/free rules and existing game rules remain unchanged.
- Privacy expectation: No source identity appears in the game or feedback record.
- PASS/FAIL:
- Evidence filename:

## 12. Reading completion and feedback

- Precondition: A decision with Reset Reading available.
- Exact action: Select reading; verify early exit does not complete; meet the 90-second and article-end requirements; submit Wrong Timing.
- Expected screen: Completion becomes available only under existing reading rules; feedback keeps Wrong Timing separate.
- Expected persistence: One completed Pivot Reading choice and one WrongTiming feedback value.
- Protection expectation: No protected source is reopened.
- Privacy expectation: Article content and source details are not copied into adaptive decisions.
- PASS/FAIL:
- Evidence filename:

## 13. Moment Plan completion and feedback

- Precondition: Enabled plan and Moment Plan intervention eligible.
- Exact action: Select Moment Plan; start the action; complete; submit Helped.
- Expected screen: Private plan action screen completes once and opens feedback.
- Expected persistence: Decision stores actual MomentPlan, plan ID, current revision, completion, and feedback.
- Protection expectation: Completion does not disable monitoring or VPN.
- Privacy expectation: Selected external-app label is not written into decision or export history.
- PASS/FAIL:
- Evidence filename:

## 14. Follow-up option after Back

- Precondition: An intervention is presented but not terminal.
- Exact action: Press Back; accept the follow-up support path; choose an alternate eligible option.
- Expected screen: Follow-up screen appears once and navigation does not return to the protected source.
- Expected persistence: Follow-up decision links safely through persisted IDs; actual choice reflects the alternate option.
- Protection expectation: Incident lease and cooldown remain intact.
- Privacy expectation: Back stack and route contain opaque IDs only.
- PASS/FAIL:
- Evidence filename:

## 15. What Works for Me empty state

- Precondition: Reset personal learning; preserve plans.
- Exact action: Settings > Personal Support > What Works for Me.
- Expected screen: Calm insufficient-history empty state with no fake chart or ranking.
- Expected persistence: Viewing the dashboard writes nothing.
- Protection expectation: Protection remains active.
- Privacy expectation: App-lock gates the screen; no source identity is displayed.
- PASS/FAIL:
- Evidence filename:

## 16. What Works for Me after sample history

- Precondition: Create at least three terminal uses and enough finalized evidence for one allowed statement.
- Exact action: Open What Works for Me and inspect counts, family rows, feedback splits, and the optional primary comparison.
- Expected screen: Immediate factual counts appear; only threshold-qualified language appears; at most one primary comparison.
- Expected persistence: Dashboard reload after process recreation matches Room facts.
- Protection expectation: Dashboard observation does not change protection.
- Privacy expectation: No utility score, probability, raw source, causal claim, or medical claim appears.
- PASS/FAIL:
- Evidence filename:

## 17. Wrong Timing separation

- Precondition: At least one terminal decision awaiting feedback.
- Exact action: Submit Wrong Timing; open What Works for Me.
- Expected screen: Wrong Timing has its own count and is not merged into Did Not Help.
- Expected persistence: FeedbackCode remains WrongTiming on that decision.
- Protection expectation: Observation work remains independent.
- Privacy expectation: Feedback is shown only in aggregated/private history.
- PASS/FAIL:
- Evidence filename:

## 18. Repeat observation display

- Precondition: One decision with a finalized repeat and one future pending observation.
- Exact action: Open What Works for Me before and after the future deadline.
- Expected screen: Pending appears separately; only finalized outcomes enter repeat comparisons.
- Expected persistence: Deadline finalizes once and survives process death.
- Protection expectation: WorkManager observation does not reopen or block a source.
- Privacy expectation: Display contains only repeat detected, no repeat detected, or pending.
- PASS/FAIL:
- Evidence filename:

## 19. Rehearsal and later-use display

- Precondition: Complete a rehearsal, then use the same plan revision within seven days; also keep one edited-revision or older-than-seven-day example.
- Exact action: Open What Works for Me and inspect practice history.
- Expected screen: Same-plan, same-revision later use is reported factually; edited or out-of-window use is not counted.
- Expected persistence: History remains safe if the original plan is deleted.
- Protection expectation: Rehearsal still has no family utility effect.
- Privacy expectation: Generic history contains no plan text or source identity.
- PASS/FAIL:
- Evidence filename:

## 20. How suggestions work

- Precondition: App unlocked.
- Exact action: Settings > Personal Support > How suggestions work; read every section.
- Expected screen: Explanation covers on-device history, eligible options, limited exploration, feedback, privacy, reset, and deletion in simple language.
- Expected persistence: No setting changes by viewing.
- Protection expectation: No protection setting changes.
- Privacy expectation: No AI, diagnosis, treatment, guaranteed, or best-intervention wording.
- PASS/FAIL:
- Evidence filename:

## 21. Reset personal learning

- Precondition: Plans, non-default preferences, decisions, rehearsals, feedback, and observation work exist.
- Exact action: Settings > Reset personal learning; inspect dialog; cancel once; reopen and confirm.
- Expected screen: Confirmation is required; success says personal learning was reset.
- Expected persistence: Decisions, feedback, observations, and rehearsals are cleared; plans and preferences remain.
- Protection expectation: Adaptive observation work and pending recovery are canceled; protection itself remains active.
- Privacy expectation: Errors, if forced, are generic and reveal no exception text.
- PASS/FAIL:
- Evidence filename:

## 22. Delete all Moment data

- Precondition: Plans, preferences, decisions, and rehearsals exist; unrelated note or recovery data exists.
- Exact action: Settings > Delete all Moment data; cancel at first dialog; repeat, continue, then cancel final dialog; repeat and confirm both dialogs.
- Expected screen: Double confirmation is mandatory and permanent-deletion wording is clear.
- Expected persistence: All four adaptive tables are empty; unrelated data remains.
- Protection expectation: Observation work is canceled; website/VPN settings remain.
- Privacy expectation: Success and errors are generic; no deleted content remains on private screens.
- PASS/FAIL:
- Evidence filename:

## 23. Backup

- Precondition: Authenticated matching UID; completed onboarding owned by that UID; encrypted recovery enabled; adaptive sample data exists.
- Exact action: Change adaptive data; observe backup status; use Backup now if provided; wait for confirmed upload completion.
- Expected screen: Enabled alone is not shown as completed; completion appears only after confirmed upload state.
- Expected persistence: Coalesced snapshot contains all adaptive sections inside the encrypted recovery envelope.
- Protection expectation: Backup activity does not interrupt monitoring.
- Privacy expectation: Invalid account/ownership state schedules no upload; no behavioural Firebase record is created.
- PASS/FAIL:
- Evidence filename:

## 24. Uninstall and restore

- Precondition: Test 23 has a confirmed encrypted backup and password/recovery credentials; record IDs and counts.
- Exact action: Uninstall; reinstall debug build; authenticate as the same UID; restore the encrypted backup; also attempt wrong-account restore.
- Expected screen: Correct owner restores to a safe landing screen; wrong owner is rejected; no intervention opens automatically.
- Expected persistence: IDs/counts round-trip, future observations reschedule, overdue observations finalize, open rehearsals become dismissed.
- Protection expectation: User must re-establish OS protection permissions where Android requires it.
- Privacy expectation: Email is never accepted as ownership proof; app-lock remains.
- PASS/FAIL:
- Evidence filename:

## 25. App-lock privacy

- Precondition: App lock enabled; plans and dashboard history exist.
- Exact action: Lock the app; attempt My Plans, rehearsal, What Works for Me, and How suggestions work; unlock.
- Expected screen: Private content remains hidden until successful unlock.
- Expected persistence: Failed/canceled unlock does not mutate adaptive data.
- Protection expectation: Background protection continues according to existing app-lock contract.
- Privacy expectation: Recents and navigation do not reveal private plan or history content where existing controls apply.
- PASS/FAIL:
- Evidence filename:

## 26. Notification privacy

- Precondition: Protection notifications enabled; private plan and adaptive history exist.
- Exact action: Trigger protection and observation activity; inspect notification shade, lock screen, and notification details.
- Expected screen: Only existing generic protection wording appears.
- Expected persistence: Notification extras contain no plan text or adaptive private record.
- Protection expectation: Notification actions preserve existing protection flow.
- Privacy expectation: No URL, domain, package, cue, urge, feedback, source, or selected-app label appears.
- PASS/FAIL:
- Evidence filename:

## 27. Website Protection continuity

- Precondition: Website Protection enabled with a known test domain.
- Exact action: Exercise first and repeated attempts, complete/dismiss support, then retry after cooldown.
- Expected screen: Existing website interruption and fallback behavior remain intact.
- Expected persistence: Incident lease/cooldown and adaptive decisions do not duplicate.
- Protection expectation: Blocking remains active through all adaptive routes.
- Privacy expectation: Raw domain and URL are absent from adaptive records and export.
- PASS/FAIL:
- Evidence filename:

## 28. VPN continuity

- Precondition: VPN protection configured and active.
- Exact action: Run the protected flow, background/foreground the app, use one intervention, then inspect VPN status.
- Expected screen: Existing VPN indicators and controls remain correct.
- Expected persistence: Adaptive activity does not overwrite VPN configuration.
- Protection expectation: VPN remains active unless explicitly stopped through existing controls.
- Privacy expectation: VPN destination/source details do not enter adaptive history.
- PASS/FAIL:
- Evidence filename:

## 29. large font

- Precondition: Android font size and display size set to the largest supported review setting.
- Exact action: Visit Home practice card, plan editor/detail/rehearsal, protected choice, feedback, dashboard, explanation, and deletion dialogs.
- Expected screen: Text wraps, scrolls, and remains actionable without clipping critical controls.
- Expected persistence: No data changes from layout/recomposition alone.
- Protection expectation: Protection controls remain reachable.
- Privacy expectation: Labels and states remain explicit and do not rely only on color.
- PASS/FAIL:
- Evidence filename:

## 30. TalkBack

- Precondition: TalkBack enabled.
- Exact action: Navigate the same surfaces as test 29 using swipe focus and activation.
- Expected screen: Logical focus order, meaningful labels, announced state, and minimum touch targets are usable.
- Expected persistence: Each activation writes at most one intended event.
- Protection expectation: Urgent interruption actions remain discoverable.
- Privacy expectation: Hidden/locked private content is not announced.
- PASS/FAIL:
- Evidence filename:

## 31. light mode

- Precondition: System light theme.
- Exact action: Inspect every new adaptive surface and dialog.
- Expected screen: Calm pastel/lavender visual system has readable contrast, no neon, no fake charts, and no harsh red styling.
- Expected persistence: Theme changes do not mutate adaptive data.
- Protection expectation: Protection status remains legible.
- Privacy expectation: State is conveyed by text and semantics, not color alone.
- PASS/FAIL:
- Evidence filename:

## 32. dark mode

- Precondition: System dark theme.
- Exact action: Inspect every new adaptive surface and dialog.
- Expected screen: Backgrounds, text, fields, cards, focus, and disabled states remain readable and consistent.
- Expected persistence: Theme changes do not mutate adaptive data.
- Protection expectation: Protection status remains legible.
- Privacy expectation: Sensitive content is not exposed by previews or contrast artifacts.
- PASS/FAIL:
- Evidence filename:

## 33. process death

- Precondition: Open decision, scheduled observation, pending feedback, and an in-progress rehearsal available in separate passes.
- Exact action: For each pass, background the app, kill its process, relaunch, unlock, and return.
- Expected screen: Persisted state reloads safely; no fabricated completion; dashboard refreshes; no stale intervention auto-navigation.
- Expected persistence: Future observations reschedule, overdue ones finalize, and opaque IDs reconnect to Room records.
- Protection expectation: Monitoring and fallback recover through existing startup behavior.
- Privacy expectation: App lock is preserved and no private route payload appears.
- PASS/FAIL:
- Evidence filename:

## 34. device reboot

- Precondition: Protection enabled; future observation scheduled; adaptive history and plans present; app lock enabled.
- Exact action: Reboot the device; unlock Android; launch Impulsive; verify protection permissions/status and revisit private screens after app unlock.
- Expected screen: App reaches a safe normal landing state; dashboard and plans reload; no intervention opens automatically.
- Expected persistence: SQLCipher data remains; future/overdue observation recovery follows deadline rules.
- Protection expectation: Website/VPN/monitoring continuity follows existing reboot contract and clearly indicates any OS permission requiring action.
- Privacy expectation: Private content remains locked until app authentication and does not appear in notifications.
- PASS/FAIL:
- Evidence filename:

## 35. Protocol explanation consistency

- Precondition: Completed examples of Short Pause, game, reading, and each reachable Moment Plan action.
- Exact action: Open Why this suggestion for each decision and compare the shown protocol with the route actually used.
- Expected screen: Generic protocol name/version and lifecycle explanation match the manifest; unsupported history is described generically.
- Expected persistence: No record changes from opening explanations.
- Protection expectation: No protected source reopens.
- Privacy expectation: No package, URL, domain, app label, utility, or random state appears.
- Evidence filename: `v28-35-protocol-explanations.mp4`
- PASS/FAIL:

## 36. Exact plan revision after metadata edit

- Precondition: One saved plan with a recorded revision ID.
- Exact action: Edit title or cue only, save, then rehearse and use it.
- Expected screen: Updated metadata is shown.
- Expected persistence: Content revision remains unchanged; rehearsal and use reference that exact revision.
- Protection expectation: Protection state is unchanged except for the deliberately triggered use.
- Privacy expectation: Plan text remains on private routes.
- Evidence filename: `v28-36-metadata-revision.txt`
- PASS/FAIL:

## 37. Exact plan revision after action edit

- Precondition: One rehearsed plan with its original revision recorded.
- Exact action: Edit action text/type/target, save, rehearse, and later use it.
- Expected screen: New action content is shown.
- Expected persistence: A new content revision is created and new events reference it.
- Protection expectation: Existing lease/cooldown behavior remains.
- Privacy expectation: Selected app label/package is not exported as adaptive evidence.
- Evidence filename: `v28-37-action-revision.txt`
- PASS/FAIL:

## 38. Rehearsed old version not attributed to edited plan

- Precondition: Complete rehearsal, then make an action-affecting plan edit without rehearsing again.
- Exact action: Use the edited plan within seven days and open What Works for Me.
- Expected screen: No claim links the old rehearsal to the edited revision.
- Expected persistence: Old rehearsal and new use retain different revision IDs.
- Protection expectation: The intervention completes normally.
- Privacy expectation: Only generic historical facts appear.
- Evidence filename: `v28-38-old-revision-separation.png`
- PASS/FAIL:

## 39. Decision explanation receipt

- Precondition: A completed adaptive decision exists.
- Exact action: Open its Why this suggestion action, background/foreground, and rotate or recreate the Activity.
- Expected screen: A full receipt reloads by decision UUID with assignment, actual choice, reason, policy, protocol, and cautious evidence wording.
- Expected persistence: The decision is not rewritten.
- Protection expectation: No automatic source reopening or new decision.
- Privacy expectation: Secure-window behavior follows the preference.
- Evidence filename: `v28-39-decision-receipt.mp4`
- PASS/FAIL:

## 40. Count-only evidence tier

- Precondition: Fresh or reset learning history with fewer than three terminal uses.
- Exact action: Record one valid completed support event and open What Works for Me.
- Expected screen: “A few recorded moments” and factual counts only.
- Expected persistence: Tier is derived, not stored as a success score.
- Protection expectation: No protection settings change.
- Privacy expectation: No source identity appears.
- Evidence filename: `v28-40-count-only.png`
- PASS/FAIL:

## 41. Early-pattern evidence tier

- Precondition: At least three terminal uses of one option, insufficient guarded comparison evidence.
- Exact action: Open What Works for Me.
- Expected screen: “An early personal pattern” with a same-option factual statement.
- Expected persistence: Wrong Timing entries remain separate.
- Protection expectation: No protection settings change.
- Privacy expectation: No ranking or diagnostic language appears.
- Evidence filename: `v28-41-early-pattern.png`
- PASS/FAIL:

## 42. Comparison-supported evidence tier

- Precondition: Build history satisfying the existing eight-use, four-feedback, and comparison safeguards.
- Exact action: Open What Works for Me.
- Expected screen: “Enough recent history for a cautious comparison” and one guarded comparison.
- Expected persistence: Retained records remain unchanged by viewing.
- Protection expectation: No protection settings change.
- Privacy expectation: No utility values or source identity appear.
- Evidence filename: `v28-42-comparison-supported.png`
- PASS/FAIL:

## 43. Screen privacy enabled

- Precondition: Screen privacy enabled; open each sensitive adaptive route.
- Exact action: Attempt a screenshot on editor, detail, rehearsal, feedback, dashboard, and explanation.
- Expected screen: Android blocks or blanks capture consistently.
- Expected persistence: Preference remains enabled after relaunch.
- Protection expectation: Monitoring and blocking continue.
- Privacy expectation: No private-content flash occurs during navigation.
- Evidence filename: `v28-43-secure-routes.mp4`
- PASS/FAIL:

## 44. Screen privacy disabled

- Precondition: Disable Screen privacy in Settings.
- Exact action: Revisit the same routes and take a screenshot.
- Expected screen: Capture follows normal Android behavior; Settings explains the tradeoff.
- Expected persistence: Preference remains disabled after relaunch.
- Protection expectation: Protection remains active.
- Privacy expectation: Only route capture changes; app lock and data minimisation remain.
- Evidence filename: `v28-44-screen-privacy-off.mp4`
- PASS/FAIL:

## 45. Recent-apps preview privacy

- Precondition: Screen privacy enabled and private plan/explanation content visible.
- Exact action: Enter Android Recents from every sensitive route.
- Expected screen: Preview is blank or protected with no transient content.
- Expected persistence: No adaptive mutation.
- Protection expectation: Service state remains unchanged.
- Privacy expectation: Returning safely reapplies the route policy.
- Evidence filename: `v28-45-recents-privacy.mp4`
- PASS/FAIL:

## 46. 90-day retention

- Precondition: Test fixture includes safe terminal history older/newer than 90 days plus open history.
- Exact action: Select 90 days and trigger cleanup.
- Expected screen: Settings remains responsive; dashboard refreshes.
- Expected persistence: Only old safe terminal decisions/rehearsals are deleted; open/new records remain.
- Protection expectation: Protection records/settings remain.
- Privacy expectation: One coalesced recovery refresh follows a change.
- Evidence filename: `v28-46-retention-90.txt`
- PASS/FAIL:

## 47. Six-month retention

- Precondition: Boundary fixtures around 183 days.
- Exact action: Select Six months and trigger cleanup.
- Expected screen: Six months remains selected after relaunch.
- Expected persistence: Exact cutoff behavior is observed; invalid negative timestamps remain.
- Protection expectation: No monitoring interruption.
- Privacy expectation: Active/pending records are protected.
- Evidence filename: `v28-47-retention-six-months.txt`
- PASS/FAIL:

## 48. Keep-until-reset retention

- Precondition: Old terminal history exists.
- Exact action: Select Keep until reset, run cleanup, then use Reset personal learning.
- Expected screen: Cleanup keeps history; explicit reset clears learning after confirmation.
- Expected persistence: Plans and preferences survive reset.
- Protection expectation: Protection settings survive.
- Privacy expectation: Pending navigation/feedback references and tied work are cleared.
- Evidence filename: `v28-48-keep-until-reset.mp4`
- PASS/FAIL:

## 49. Retention after restore

- Precondition: Encrypted schema-2 bundle contains old, new, and open adaptive history with 90-day preference.
- Exact action: Restore it and allow post-commit recovery/cleanup.
- Expected screen: Safe landing; no intervention auto-opens.
- Expected persistence: Restore commits atomically, overdue observations recover, then safe old history prunes.
- Protection expectation: Restored data does not reopen a source.
- Privacy expectation: Screen privacy preference also restores.
- Evidence filename: `v28-49-retention-after-restore.txt`
- PASS/FAIL:

## 50. Debug policy replay does not alter live history

- Precondition: Debug build with known adaptive record counts.
- Exact action: Run all ten replay fixtures twice and recreate the process.
- Expected screen: Deterministic aggregate comparison or explicit InsufficientContext.
- Expected persistence: Decision, feedback, observation, rehearsal, plan, preference, and protection counts are identical.
- Protection expectation: No live recommendation or protection state changes.
- Privacy expectation: Output is generic and contains no source identity.
- Evidence filename: `v28-50-debug-replay.txt`
- PASS/FAIL:

## 51. Schema 9 to 10 upgrade on retained user data

- Precondition: Install a schema-9 fixture containing plans, rehearsals, decisions, and preferences.
- Exact action: Upgrade in place and unlock the app.
- Expected screen: Normal landing and existing data available.
- Expected persistence: Deterministic legacy revisions/default passports exist; original schema-9 fields are preserved.
- Protection expectation: Website/VPN settings and service continuity remain.
- Privacy expectation: New privacy defaults on; no source identity is introduced.
- Evidence filename: `v28-51-migration-9-10.txt`
- PASS/FAIL:

## 52. Full backup, uninstall and schema-10 restore

- Precondition: Authenticated owner, encrypted recovery enabled, schema-10 data, confirmed uploaded snapshot.
- Exact action: Back up, record confirmation, uninstall, reinstall, sign in as the same owner, and restore.
- Expected screen: Safe landing; plans/dashboard become available only after normal unlock.
- Expected persistence: Adaptive schema 2 round-trips IDs, revisions, passports, preferences, decisions, and rehearsals transactionally.
- Protection expectation: No automatic source reopening; OS permissions are reported accurately.
- Privacy expectation: Strict UID/Google-subject ownership holds; no email ownership is used.
- Evidence filename: `v28-52-full-restore.mp4`
- PASS/FAIL:

## 53. Website Protection regression

- Precondition: Website Protection enabled with a known blocked domain.
- Exact action: Trigger first and repeated attempts, complete/dismiss support, then retry after cooldown.
- Expected screen: Established interruption, fallback, and follow-up behavior remains.
- Expected persistence: Incident lease/deduplication and decisions are accurate.
- Protection expectation: Blocking never fails open because adaptive support fails.
- Privacy expectation: Domain/URL never enters adaptive history/export.
- Evidence filename: `v28-53-website-regression.mp4`
- PASS/FAIL:

## 54. VPN regression

- Precondition: VPN protection configured and permission granted.
- Exact action: Exercise protected traffic, adaptive support, app backgrounding, and reconnect.
- Expected screen: VPN status remains accurate and support appears at most once per valid incident.
- Expected persistence: Adaptive lifecycle does not modify VPN configuration.
- Protection expectation: Monitoring, lease, cooldown, and fallback remain authoritative.
- Privacy expectation: Destination details never enter adaptive history.
- Evidence filename: `v28-54-vpn-regression.mp4`
- PASS/FAIL:

## 55. process death on a private route

- Precondition: App lock and screen privacy enabled; private editor/rehearsal/feedback/explanation open.
- Exact action: Kill and recreate the process for each route, then inspect Recents before unlocking.
- Expected screen: Opaque/secure state appears before content; persisted route reloads safely or returns to a safe landing.
- Expected persistence: No duplicate event or fabricated completion.
- Protection expectation: Monitoring recovers through existing startup logic.
- Privacy expectation: No content flash, preview leak, or route payload leak.
- Evidence filename: `v28-55-private-process-death.mp4`
- PASS/FAIL:

## 56. device reboot with retention and observation work

- Precondition: Future and overdue observations, retention cleanup, adaptive history, protection, privacy, and app lock enabled.
- Exact action: Reboot, unlock Android, launch Impulsive, wait for bounded workers, and inspect records.
- Expected screen: Safe landing with no automatic intervention.
- Expected persistence: Overdue work finalizes once, future work reschedules, safe retention cleanup is bounded, active/open state is preserved.
- Protection expectation: Website/VPN/monitoring status follows the established reboot contract.
- Privacy expectation: Private content remains unavailable until app unlock and secure routes reapply protection.
- Evidence filename: `v28-56-reboot-work.txt`
- PASS/FAIL:

## 57. Enable Future Path

- Precondition: Future Path off; app unlocked; Personal Support open.
- Exact action: Toggle Future Path on, read the explanation, then tap Turn On Future Path.
- Expected screen: Plain-language on-device estimate explanation appears before the switch becomes on.
- Expected persistence: `pathShiftEnabled` persists true after relaunch; no cycle is auto-created.
- Privacy expectation: Copy names used history and excluded source, URL, journal, email, camera, microphone, and location.
- Protection expectation: Website Protection, VPN, monitoring, lease, and cooldown remain unchanged.
- LP expectation: Level and LP do not change.
- Evidence filename: `v28-57-enable-future-path.mp4`
- PASS/FAIL:

## 58. Decline Future Path

- Precondition: Future Path off; enable explanation open.
- Exact action: Tap Not Now.
- Expected screen: Dialog closes and Future Path remains off.
- Expected persistence: No preference change, forecast, cycle, or worker exists.
- Privacy expectation: No PathShift data is created.
- Protection expectation: Protection remains unchanged.
- LP expectation: LP remains unchanged.
- Evidence filename: `v28-58-decline-future-path.mp4`
- PASS/FAIL:

## 59. Insufficient-history state

- Precondition: Future Path on; fewer than seven eligible root moments or other gate unmet.
- Exact action: Open Home Current Path, then open Your Current Path.
- Expected screen: Not enough history yet appears without a number or risk gauge.
- Expected persistence: No cycle is stored.
- Privacy expectation: No source identity is shown.
- Protection expectation: Protection remains active and unchanged.
- LP expectation: No LP is awarded or removed.
- Evidence filename: `v28-59-insufficient-history.png`
- PASS/FAIL:

## 60. Early estimate

- Precondition: Seven root moments across five dates and fourteen-day span; cautious threshold unmet.
- Exact action: Open PathShift and deliberately create the current path.
- Expected screen: A ranged estimate and An early estimate label appear with cautious copy.
- Expected persistence: One immutable policy-v1 cycle is stored and one finalisation job exists.
- Privacy expectation: Only counts, dates, broad window, and evidence are shown.
- Protection expectation: No protection setting changes.
- LP expectation: Forecast creation awards zero LP.
- Evidence filename: `v28-60-early-estimate.mp4`
- PASS/FAIL:

## 61. Cautious estimate

- Precondition: At least 14 root moments, ten dates, and full 28-day coverage.
- Exact action: Create a PathShift.
- Expected screen: Range and Enough recent history for a cautious estimate appear.
- Expected persistence: Evidence strength and policy version 1 persist.
- Privacy expectation: No source identity or exact location appears.
- Protection expectation: Protection remains unchanged.
- LP expectation: LP remains unchanged.
- Evidence filename: `v28-61-cautious-estimate.png`
- PASS/FAIL:

## 62. Common time window

- Precondition: At least three eligible moments and 30 percent share in one two-hour bucket.
- Exact action: Create and inspect What Impulsive noticed.
- Expected screen: One broad local-time window appears; ties select the stable earlier window.
- Expected persistence: Only start and end local minute values persist.
- Privacy expectation: Exact location and source are absent.
- Protection expectation: Protection remains unchanged.
- LP expectation: LP remains unchanged.
- Evidence filename: `v28-62-common-window.png`
- PASS/FAIL:

## 63. Why this estimate

- Precondition: Forecast ready or active.
- Exact action: Open Why this estimate.
- Expected screen: Used and Not used sections list the documented factors and policy version.
- Expected persistence: Viewing writes no decision, cycle, or feedback.
- Privacy expectation: URL, domain, package, journal, email, cloud profile, sensors, and location are listed as unused.
- Protection expectation: Protection remains unchanged.
- LP expectation: Viewing awards zero LP.
- Evidence filename: `v28-63-why-estimate.png`
- PASS/FAIL:

## 64. Select prepared Moment Plan

- Precondition: Active PathShift and at least one enabled valid plan.
- Exact action: Choose a Moment Plan and select it once.
- Expected screen: Your plan is ready for this PathShift and the plan title appear.
- Expected persistence: Exact plan UUID and current `contentRevisionId` attach without changing the range.
- Privacy expectation: Plan text is visible only after app lock; route remains opaque.
- Protection expectation: Recommendation utility and 25/75 boundary are unchanged.
- LP expectation: Selection awards zero LP.
- Evidence filename: `v28-64-select-plan.mp4`
- PASS/FAIL:

## 65. Practise prepared plan

- Precondition: Active PathShift with a prepared plan.
- Exact action: Tap Practise this Plan and finish the existing guided rehearsal.
- Expected screen: Existing rehearsal UI opens and returns safely.
- Expected persistence: One rehearsal completes; cycle range and revision remain unchanged.
- Privacy expectation: Plan text remains on private screens only.
- Protection expectation: Rehearsal creates no protected incident.
- LP expectation: No new PathShift rehearsal LP is awarded.
- Evidence filename: `v28-65-practise-plan.mp4`
- PASS/FAIL:

## 66. Metadata-only plan edit

- Precondition: Active PathShift with prepared plan.
- Exact action: Rename the plan or change enabled/preferred metadata without meaningful content change.
- Expected screen: No changed-since-prepared warning appears.
- Expected persistence: Prepared content revision remains the same.
- Privacy expectation: No plan text enters the cycle record.
- Protection expectation: Existing plan eligibility rules remain.
- LP expectation: LP remains unchanged.
- Evidence filename: `v28-66-metadata-edit.mp4`
- PASS/FAIL:

## 67. Meaningful plan edit

- Precondition: Active PathShift with prepared plan.
- Exact action: Edit action, future cue, action type, target, or cue; return to PathShift.
- Expected screen: This Moment Plan has changed since it was prepared appears with both deliberate actions.
- Expected persistence: Old revision remains until Use the New Version is chosen.
- Privacy expectation: Cycle stores identifiers only, not edited text.
- Protection expectation: Earlier real decisions are unchanged.
- LP expectation: LP remains unchanged.
- Evidence filename: `v28-67-meaningful-edit.mp4`
- PASS/FAIL:

## 68. Active cycle survives process death

- Precondition: Active PathShift with fixed range and optional prepared plan.
- Exact action: Kill the app process, relaunch, unlock, and reopen PathShift.
- Expected screen: Same range, window, evidence, and prepared revision reload.
- Expected persistence: No second cycle or worker is created.
- Privacy expectation: No private content flashes before app lock and secure route.
- Protection expectation: Monitoring recovers normally.
- LP expectation: LP remains unchanged.
- Evidence filename: `v28-68-process-death.mp4`
- PASS/FAIL:

## 69. Active cycle survives reboot

- Precondition: Active future-due cycle and protection configured.
- Exact action: Reboot Samsung, unlock Android, launch Impulsive, and reopen PathShift.
- Expected screen: Same active cycle appears.
- Expected persistence: One future finalisation job is rescheduled.
- Privacy expectation: App lock and screen privacy apply.
- Protection expectation: Website/VPN/monitoring follow existing reboot behavior.
- LP expectation: LP remains unchanged.
- Evidence filename: `v28-69-reboot.txt`
- PASS/FAIL:

## 70. Path Review finalisation

- Precondition: Active cycle whose seven-day end has passed.
- Exact action: Launch app or run due work, then open PathShift.
- Expected screen: Path Review shows estimate, observed count, separated outcomes, Wrong Timing, repeat, and non-causal copy.
- Expected persistence: Cycle finalises once with fixed counts.
- Privacy expectation: No source identity or plan text appears.
- Protection expectation: Finalisation does not alter protection.
- LP expectation: Review awards zero LP.
- Evidence filename: `v28-70-path-review.mp4`
- PASS/FAIL:

## 71. Forecast versus observed range

- Precondition: Finalised cycle with observed count below, inside, or above range.
- Exact action: Open the review for each prepared fixture.
- Expected screen: Factual values appear without red arrow, failure label, or success claim.
- Expected persistence: Original estimate and observed count remain distinct.
- Privacy expectation: Aggregates only.
- Protection expectation: Protection remains unchanged.
- LP expectation: No LP change for any relationship to the range.
- Evidence filename: `v28-71-range-comparison.png`
- PASS/FAIL:

## 72. Exact plan revision selected count

- Precondition: Active cycle prepared with revision A; live decisions include A and revision B.
- Exact action: Finalise and open review.
- Expected screen: Selected count includes only exact plan ID plus revision A.
- Expected persistence: Older/newer mismatched revision decisions remain but are not attributed.
- Privacy expectation: Generic plan reference only.
- Protection expectation: Live delivery remains governed by existing selection.
- LP expectation: LP remains unchanged.
- Evidence filename: `v28-72-exact-selected.txt`
- PASS/FAIL:

## 73. Exact plan revision completion count

- Precondition: Exact-revision selections include Started, Completed, and Dismissed outcomes.
- Exact action: Finalise and inspect review.
- Expected screen: Selected, Started, Completed, and Dismissed counts remain separate.
- Expected persistence: No terminal outcome is inferred or duplicated.
- Privacy expectation: No plan text appears.
- Protection expectation: Protection remains unchanged.
- LP expectation: Completion is not double rewarded by PathShift.
- Evidence filename: `v28-73-exact-completion.txt`
- PASS/FAIL:

## 74. Wrong Timing review

- Precondition: One root decision has Wrong Timing.
- Exact action: Finalise the cycle and inspect review.
- Expected screen: Wrong Timing appears as its own factual count.
- Expected persistence: It is not converted to Did Not Help or effectiveness evidence.
- Privacy expectation: Feedback remains local.
- Protection expectation: Protection remains unchanged.
- LP expectation: Wrong Timing removes zero LP.
- Evidence filename: `v28-74-wrong-timing.png`
- PASS/FAIL:

## 75. Repeat observation review

- Precondition: One final repeat detected, one final no-repeat, and one pending observation.
- Exact action: Finalise and inspect review.
- Expected screen: Repeat count includes only the final detected record.
- Expected persistence: Pending is not written as false.
- Privacy expectation: No source identity appears.
- Protection expectation: Existing twenty-minute observation rules remain.
- LP expectation: No-repeat awards zero LP and repeat removes zero LP.
- Evidence filename: `v28-75-repeat-review.txt`
- PASS/FAIL:

## 76. Turn off Future Path

- Precondition: Future Path on with active cycle.
- Exact action: Toggle off, read confirmation, and tap Turn Off Future Path.
- Expected screen: Setting becomes off and PathShift entry becomes disabled.
- Expected persistence: Active cycle becomes cancelled and PathShift work is cancelled; history remains.
- Privacy expectation: No new forecasts are created.
- Protection expectation: Protection and personal suggestions remain unchanged.
- LP expectation: No LP is removed.
- Evidence filename: `v28-76-turn-off.mp4`
- PASS/FAIL:

## 77. Cancel active PathShift

- Precondition: Active PathShift.
- Exact action: Tap Stop this PathShift, read the warning, and confirm twice rapidly.
- Expected screen: Current comparison stops once and safe ready/landing state appears.
- Expected persistence: One cancellation time; adaptive history and plan remain.
- Privacy expectation: No deleted history is hidden in aggregates.
- Protection expectation: Only PathShift work is cancelled.
- LP expectation: No LP is removed.
- Evidence filename: `v28-77-cancel.mp4`
- PASS/FAIL:

## 78. Reset personal learning

- Precondition: PathShift cycles, plan, preference, LP, and protection present.
- Exact action: Settings > Reset personal learning and confirm.
- Expected screen: Reset completion appears; plans and settings remain.
- Expected persistence: Cycles, reviews, PathShift work, and adaptive learning clear; plan and opt-in remain.
- Privacy expectation: No hidden PathShift aggregate remains.
- Protection expectation: Protection remains unchanged.
- LP expectation: LP and character level remain.
- Evidence filename: `v28-78-reset.txt`
- PASS/FAIL:

## 79. Delete all Moment data

- Precondition: PathShift, adaptive history, plans, preference, LP, and protection present.
- Exact action: Delete all Moment data through both confirmations.
- Expected screen: Moment data disappears and Future Path returns off.
- Expected persistence: Cycles, work, preference, plans, rehearsals, and adaptive history clear.
- Privacy expectation: Export and screen no longer expose deleted Moment data.
- Protection expectation: Protection remains according to existing deletion contract.
- LP expectation: LP remains unless full-account deletion separately erases all local state.
- Evidence filename: `v28-79-delete-moment.txt`
- PASS/FAIL:

## 80. PathShift backup

- Precondition: Valid active and finalised cycles with Future Path on.
- Exact action: Trigger encrypted recovery snapshot and inspect decoded test export.
- Expected screen: No navigation occurs.
- Expected persistence: Adaptive schema 3 contains allowed cycle fields and status.
- Privacy expectation: Source identity, URL, domain, package, plan text, email, UID, utility, random state, and keys are absent.
- Protection expectation: Backup does not alter protection.
- LP expectation: Backup does not alter LP.
- Evidence filename: `v28-80-pathshift-backup.json`
- PASS/FAIL:

## 81. Uninstall and restore

- Precondition: Encrypted recovery is current and contains Future Path preference, one active cycle, and one finalised review.
- Exact action: Uninstall Impulsive, reinstall the same debug APK, complete recovery, unlock, and open PathShift.
- Expected screen: The restored active path and earlier review are available without duplicated cards.
- Expected persistence: Adaptive schema 3 restores cycles once and reschedules only the active cycle.
- Privacy expectation: Recovery remains encrypted and no source identity or plan text is restored into a cycle.
- Protection expectation: Protection follows the existing recovery contract and is not silently weakened by PathShift.
- LP expectation: LP follows the existing recovery contract; PathShift adds or removes none.
- Evidence filename: `v28-81-uninstall-restore.mp4`
- PASS/FAIL:

## 82. Screen privacy

- Precondition: App lock and screen privacy are enabled with a PathShift forecast and review available.
- Exact action: Open PathShift, lock the app, then attempt a screenshot and screen recording.
- Expected screen: Protected content is obscured or capture-blocked according to the existing secure-screen policy.
- Expected persistence: No cycle, preference, or plan state changes.
- Privacy expectation: Forecast, review aggregates, and prepared-plan identity are not exposed through capture.
- Protection expectation: Website and app protection remain active.
- LP expectation: LP remains unchanged.
- Evidence filename: `v28-82-screen-privacy.png`
- PASS/FAIL:

## 83. Recent-apps privacy

- Precondition: A PathShift review is visible and screen privacy is enabled.
- Exact action: Send the app to the background and open Android Recents.
- Expected screen: The task preview is protected by the existing recent-apps privacy treatment.
- Expected persistence: The active or finalised cycle remains unchanged.
- Privacy expectation: No estimate, outcome count, or prepared-plan identity is legible in Recents.
- Protection expectation: Background protection continues.
- LP expectation: LP remains unchanged.
- Evidence filename: `v28-83-recents-privacy.png`
- PASS/FAIL:

## 84. No protected-source identity

- Precondition: Forecast evidence originates from both app and website protection decisions.
- Exact action: Open the forecast explanation, finalise the cycle, inspect the review, export, and decoded test backup.
- Expected screen: Only aggregate counts, range, dates, and generic evidence descriptions appear.
- Expected persistence: Cycle rows contain no URL, domain, package, title, or source identifier.
- Privacy expectation: Protected-source identity is absent from UI, Room, export, logs, and recovery payload.
- Protection expectation: Source matching still occurs only in the established live protection boundary.
- LP expectation: LP remains unchanged.
- Evidence filename: `v28-84-no-source-identity.txt`
- PASS/FAIL:

## 85. LP unchanged after forecast

- Precondition: Record the current LP total and character level before enabling and creating a forecast.
- Exact action: Enable Future Path, create an available forecast, and reopen the Home and PathShift screens.
- Expected screen: Forecast appears while the LP total and character level are identical to the precondition.
- Expected persistence: A cycle may be stored; no LP transaction is stored.
- Privacy expectation: Only allowed forecast aggregates persist.
- Protection expectation: Protection remains unchanged.
- LP expectation: Forecast creation awards and removes exactly zero LP.
- Evidence filename: `v28-85-lp-forecast.txt`
- PASS/FAIL:

## 86. LP unchanged after positive feedback

- Precondition: Record LP before submitting Helped on a decision inside an active PathShift window.
- Exact action: Complete the existing feedback flow with Helped, then open PathShift and the LP display.
- Expected screen: Feedback and PathShift counts update normally; LP does not change because of PathShift.
- Expected persistence: Existing decision feedback persists once; no PathShift LP transaction is added.
- Privacy expectation: Feedback stays on-device under the existing adaptive-data rules.
- Protection expectation: Live protection behavior remains unchanged.
- LP expectation: PathShift does not duplicate any reward owned by the existing intervention flow.
- Evidence filename: `v28-86-lp-positive-feedback.txt`
- PASS/FAIL:

## 87. LP unchanged after review

- Precondition: Record LP before finalising an active PathShift.
- Exact action: Run finalisation and open Path Review.
- Expected screen: Review aggregates appear with the same LP and character level.
- Expected persistence: Review fields finalise exactly once; no LP transaction is stored.
- Privacy expectation: Review exposes aggregates only.
- Protection expectation: Finalisation does not alter protection.
- LP expectation: Reviewing, matching, or missing the range changes LP by zero.
- Evidence filename: `v28-87-lp-review.txt`
- PASS/FAIL:

## 88. Existing Game LP

- Precondition: Record LP and identify an existing game action that legitimately awards LP.
- Exact action: Complete that game action while a PathShift cycle is active.
- Expected screen: The established game completion and LP feedback remain unchanged.
- Expected persistence: Exactly the existing game LP transaction is stored; PathShift stores none.
- Privacy expectation: Game data is not copied into the cycle.
- Protection expectation: Protection remains unchanged.
- LP expectation: Existing Game LP still awards exactly once.
- Evidence filename: `v28-88-game-lp.mp4`
- PASS/FAIL:

## 89. Existing Reading LP

- Precondition: Record LP and open an eligible existing Reset Reading action while PathShift is active.
- Exact action: Complete the eligible reading action once.
- Expected screen: The established reading completion and LP feedback remain unchanged.
- Expected persistence: Exactly the existing Reading LP transaction is stored; PathShift stores none.
- Privacy expectation: Reading content is not copied into the cycle.
- Protection expectation: Protection remains unchanged.
- LP expectation: Existing Reading LP still awards exactly once.
- Evidence filename: `v28-89-reading-lp.mp4`
- PASS/FAIL:

## 90. Existing Focus LP

- Precondition: Record LP and configure an eligible existing Focus session while PathShift is active.
- Exact action: Complete the Focus session through the normal flow.
- Expected screen: Existing Focus completion and LP feedback remain unchanged.
- Expected persistence: Exactly the existing Focus LP transaction is stored; PathShift stores none.
- Privacy expectation: Focus content is not copied into the cycle.
- Protection expectation: Protection remains unchanged.
- LP expectation: Existing Focus LP still awards exactly once.
- Evidence filename: `v28-90-focus-lp.mp4`
- PASS/FAIL:

## 91. Existing Journal LP

- Precondition: Record LP and prepare an eligible existing Journal completion while PathShift is active.
- Exact action: Complete the Journal action through the normal flow.
- Expected screen: Existing Journal completion and LP feedback remain unchanged.
- Expected persistence: Exactly the existing Journal LP transaction is stored; journal text does not enter PathShift.
- Privacy expectation: Journal content remains outside the cycle and its export.
- Protection expectation: Protection remains unchanged.
- LP expectation: Existing Journal LP still awards exactly once.
- Evidence filename: `v28-91-journal-lp.mp4`
- PASS/FAIL:

## 92. Character at low level

- Precondition: Use a fixture representing the existing character's low-level progression state.
- Exact action: Open Home and PathShift with an insufficient-history state, an active forecast, and a review.
- Expected screen: The same established character identity appears with calm, supportive presentation.
- Expected persistence: Character progression and PathShift state remain separate.
- Privacy expectation: Character visuals reveal no forecast source identity.
- Protection expectation: Protection remains unchanged.
- LP expectation: Opening PathShift does not change the low-level LP state.
- Evidence filename: `v28-92-character-low.png`
- PASS/FAIL:

## 93. Character at middle level

- Precondition: Use a fixture representing the existing character's middle-level progression state.
- Exact action: Open Home and PathShift across ready, active, and review states.
- Expected screen: Existing middle-level character assets remain recognisable and supportive without a new mascot.
- Expected persistence: Character progression and PathShift state remain separate.
- Privacy expectation: Character expression does not encode private source or outcome detail.
- Protection expectation: Protection remains unchanged.
- LP expectation: PathShift changes no middle-level LP.
- Evidence filename: `v28-93-character-middle.png`
- PASS/FAIL:

## 94. Character at high level

- Precondition: Use a fixture representing the existing character's high-level progression state.
- Exact action: Open Home and PathShift across ready, active, and review states.
- Expected screen: Existing high-level character assets remain recognisable and composed.
- Expected persistence: Character progression and PathShift state remain separate.
- Privacy expectation: Character expression does not disclose private source or review details.
- Protection expectation: Protection remains unchanged.
- LP expectation: PathShift changes no high-level LP.
- Evidence filename: `v28-94-character-high.png`
- PASS/FAIL:

## 95. Character does not become sad for higher estimate

- Precondition: Prepare low-range and higher-range forecast fixtures at the same character level.
- Exact action: Open each forecast and compare the character presentation.
- Expected screen: A higher estimate never produces shaming, sad, alarmed, or failure-coded character treatment.
- Expected persistence: Estimate values do not mutate character progression or mood state.
- Privacy expectation: Expression does not reveal hidden evidence or source identity.
- Protection expectation: Protection remains unchanged.
- LP expectation: Neither estimate changes LP.
- Evidence filename: `v28-95-character-no-sad.png`
- PASS/FAIL:

## 96. Large font

- Precondition: Set Android font size and display size to the largest supported accessibility settings.
- Exact action: Open every PathShift state, expand estimate explanation, select a plan, and open Path Review.
- Expected screen: Text remains readable, controls remain reachable, and important copy is not clipped or overlapped.
- Expected persistence: Accessibility layout changes no data.
- Privacy expectation: No private data is added to accessibility labels.
- Protection expectation: Protection controls remain reachable.
- LP expectation: LP remains unchanged.
- Evidence filename: `v28-96-large-font.mp4`
- PASS/FAIL:

## 97. TalkBack

- Precondition: Enable TalkBack on Samsung with PathShift states available.
- Exact action: Navigate Home entry, consent, forecast explanation, plan actions, cancellation, and review by swipe and activate gestures.
- Expected screen: Focus order is logical, labels state purpose and value, and dialogs announce their warnings.
- Expected persistence: Focus traversal changes no data; activated actions occur once.
- Privacy expectation: Spoken labels avoid protected-source identity and unnecessary sensitive detail.
- Protection expectation: Protection controls remain operable.
- LP expectation: LP remains unchanged.
- Evidence filename: `v28-97-talkback.mp4`
- PASS/FAIL:

## 98. Reduced motion

- Precondition: Enable Android Remove animations or reduced-motion preference.
- Exact action: Open Home and PathShift, change between ready, forecast, prepared plan, and review states.
- Expected screen: The existing character and PathShift content remain usable without continuous or decorative motion.
- Expected persistence: Motion preference changes no cycle or character progression.
- Privacy expectation: No privacy state is conveyed only through motion.
- Protection expectation: Protection remains unchanged.
- LP expectation: LP remains unchanged.
- Evidence filename: `v28-98-reduced-motion.mp4`
- PASS/FAIL:

## 99. Light mode

- Precondition: Set the app/device to light mode.
- Exact action: Inspect consent, insufficient history, forecast, explanation, prepared plan, mismatch, cancellation, and review screens.
- Expected screen: Text, controls, cards, dialogs, range, and character meet readable contrast and retain hierarchy.
- Expected persistence: Theme changes no data.
- Privacy expectation: Sensitive content is not exposed by a theme transition.
- Protection expectation: Protection remains unchanged.
- LP expectation: LP remains unchanged.
- Evidence filename: `v28-99-light-mode.png`
- PASS/FAIL:

## 100. Dark mode

- Precondition: Set the app/device to dark mode.
- Exact action: Inspect consent, insufficient history, forecast, explanation, prepared plan, mismatch, cancellation, and review screens.
- Expected screen: Text, controls, cards, dialogs, range, and character meet readable contrast and retain hierarchy.
- Expected persistence: Theme changes no data.
- Privacy expectation: Sensitive content is not exposed by a theme transition.
- Protection expectation: Protection remains unchanged.
- LP expectation: LP remains unchanged.
- Evidence filename: `v28-100-dark-mode.png`
- PASS/FAIL:

## 101. Website Protection continuity

- Precondition: Website Protection is active, Future Path is enabled, and a forecast cycle exists.
- Exact action: Trigger a protected website interruption, complete or dismiss support, then inspect PathShift.
- Expected screen: Existing interruption and follow-up navigation work; PathShift later shows only allowed aggregate evidence.
- Expected persistence: Existing root-decision lifecycle persists once and may contribute anonymously to the fixed window.
- Privacy expectation: URL and domain never enter the PathShift cycle, UI, export, log, or backup.
- Protection expectation: Blocking, overlay/notification fallback, and return navigation remain operational.
- LP expectation: Only existing intervention rules may award LP; PathShift adds none.
- Evidence filename: `v28-101-website-continuity.mp4`
- PASS/FAIL:

## 102. VPN continuity

- Precondition: VPN-based protection is active with Future Path on and a cycle available.
- Exact action: Exercise VPN start, protected traffic interruption, app background/foreground, and PathShift navigation.
- Expected screen: VPN status and existing protection UI remain correct before and after PathShift use.
- Expected persistence: PathShift never rewrites VPN/protection settings or stores network identity.
- Privacy expectation: URL, domain, IP, and traffic content do not enter a cycle.
- Protection expectation: VPN remains connected or follows its existing documented lifecycle without PathShift interference.
- LP expectation: LP remains unchanged by PathShift.
- Evidence filename: `v28-102-vpn-continuity.txt`
- PASS/FAIL:

## 103. Free-user behaviour

- Precondition: Use a free-user entitlement with Future Path preference initially off.
- Exact action: Navigate Home and Settings, enable Future Path if offered, create a forecast, prepare a plan, and open a review.
- Expected screen: Behaviour matches the implemented subscription policy with no accidental paywall loop or entitlement leak.
- Expected persistence: Entitlement state is not changed by PathShift and cycle data follows normal rules.
- Privacy expectation: No subscription identifier enters PathShift persistence.
- Protection expectation: Existing free-user protection remains unchanged.
- LP expectation: PathShift adds or removes zero LP.
- Evidence filename: `v28-103-free-user.mp4`
- PASS/FAIL:

## 104. Plus-user behaviour

- Precondition: Use a Plus-user entitlement with Future Path preference initially off.
- Exact action: Navigate Home and Settings, enable Future Path, create a forecast, prepare a plan, and open a review.
- Expected screen: Behaviour matches the implemented subscription policy and existing Plus surfaces remain stable.
- Expected persistence: Entitlement state is not changed by PathShift and cycle data follows normal rules.
- Privacy expectation: No subscription identifier enters PathShift persistence.
- Protection expectation: Existing Plus-user protection remains unchanged.
- LP expectation: PathShift adds or removes zero LP.
- Evidence filename: `v28-104-plus-user.mp4`
- PASS/FAIL:

## 105. Complete schema-11 recovery

- Precondition: Prepare a version-11 encrypted database with preference, plans/revisions, rehearsals, adaptive history, active/finalised PathShift cycles, LP, and protection state; create schema-3 recovery.
- Exact action: Remove local app data, restore the bundle, unlock, and inspect every restored feature plus scheduled work.
- Expected screen: Current Path, prepared exact revision, review, settings, LP, and protection surfaces recover without duplication or crash.
- Expected persistence: All allowed schema-11 state restores transactionally; one active cycle reschedules, overdue work finalises once, and repeated import is idempotent.
- Privacy expectation: Source identity, plan text in cycles, journal content, credentials, keys, and unsupported identifiers remain absent.
- Protection expectation: Protection state follows its existing recovery contract and website/VPN continuity passes.
- LP expectation: Existing LP restores exactly; PathShift adds, removes, or duplicates none.
- Evidence filename: `v28-105-schema11-recovery.mp4`
- PASS/FAIL:

# Final UI and Accessibility Repair Retest

Leave every item in this section unmarked until it has been manually retested on device.

## 106. Dark-mode Short Pause and feedback

- Precondition: Set device/app to dark mode and trigger Short Pause support.
- Exact action: Complete the Short Pause, answer feedback, skip/change answer, and inspect saved acknowledgement.
- Expected screen: Short Pause active/completed copy, feedback title, every option, saved acknowledgement, and Change answer are readable.
- PASS/FAIL:

## 107. Wrong Timing readability

- Precondition: Dark mode; complete a support flow and choose Wrong Timing.
- Exact action: Reopen feedback-related state and What Works for Me recent records.
- Expected screen: Wrong Timing display and recent support record text remain readable.
- PASS/FAIL:

## 108. Moment Plan list card

- Precondition: Have at least one enabled Moment Plan and one disabled/non-preferred plan.
- Exact action: Open My Moment Plans in light and dark mode.
- Expected screen: Card surface matches surrounding list cards; Open plan action has clear contrast.
- PASS/FAIL:

## 109. Home card alignment

- Precondition: Home shows both Moment Plan and Future Path cards.
- Exact action: Compare icon position, eyebrow baseline, title/description/action alignment.
- Expected screen: Cards align exactly and differ only by icon/accent/copy.
- PASS/FAIL:

## 110. Keyboard and focused Moment Plan field

- Precondition: Samsung keyboard enabled; font scale normal and then maximum.
- Exact action: Edit `How I want to feel`, then every other Moment Plan text field.
- Expected screen: Focused field is brought fully above keyboard; Save/Continue remains reachable.
- PASS/FAIL:

## 111. Immediate practice preview

- Precondition: Open an existing Moment Plan detail screen.
- Exact action: Edit meaningful plan content, save, and immediately inspect Practise My Plan preview.
- Expected screen: Preview shows the new content revision without restarting the app; metadata-only edit keeps same content revision.
- PASS/FAIL:

## 112. Guided Practice at maximum font

- Precondition: System font scale maximum.
- Exact action: Open guided practice and inspect every step, especially step 2.
- Expected screen: Step content scrolls; Previous and Continue remain independently reachable.
- PASS/FAIL:

## 113. About dialog at maximum font

- Precondition: System font scale maximum.
- Exact action: Open Settings > Support > About Impulsive.
- Expected screen: Body scrolls, title remains visible, Close remains reachable, no clipped text.
- PASS/FAIL:

## 114. Data-storage dialog at maximum font

- Precondition: System font scale maximum.
- Exact action: Open Settings > Support > How your data is stored.
- Expected screen: Body scrolls, title remains visible, Got it remains reachable, no clipped text.
- PASS/FAIL:

## 115. Pivot game selection at maximum font

- Precondition: System font scale maximum.
- Exact action: Open Pivot Games and inspect control points, cards, and locked game actions.
- Expected screen: Text reflows vertically, no isolated-letter wrapping, artwork and unlock rules remain visible.
- PASS/FAIL:

## 116. Mind/Body/Soul hub at maximum font

- Precondition: System font scale maximum.
- Exact action: Long-press/open the mode hub from bottom navigation.
- Expected screen: Mind, Body, and Soul labels are visible, multi-line if needed, and tap targets remain intact.
- PASS/FAIL:

## 117. Progress statistics at maximum font

- Precondition: System font scale maximum with at least one recent pivot game.
- Exact action: Open Progress and inspect Personal Best, Recent session, and Reset Reading metrics.
- Expected screen: Labels and values remain associated; no overlap or content behind navigation.
- PASS/FAIL:

## 118. Mind Pivot at maximum font

- Precondition: System font scale maximum and reduced motion both off/on.
- Exact action: Open Mind Mode explanation and inspect the decision diagram/sequence.
- Expected screen: Meaning is preserved in a readable vertical sequence where needed; primary action remains reachable.
- PASS/FAIL:

## 119. Bottom-navigation overlap

- Precondition: System font scale maximum; test gesture and three-button navigation if available.
- Exact action: Scroll Progress and Mind Pivot to the bottom.
- Expected screen: Bottom navigation does not obscure cards, actions, or explanatory text.
- PASS/FAIL:

## 120. Screen-privacy regression

- Precondition: Screen privacy enabled.
- Exact action: Open private support routes, recents, and screenshots/screen recording surfaces.
- Expected screen: Existing screenshot and Recents privacy behaviour remains intact.
- PASS/FAIL:

## 121. Website Protection regression

- Precondition: Website Protection and app protection are enabled.
- Exact action: Trigger protected website/app interruptions after the UI repair.
- Expected screen: Existing protection, fallback notification privacy, and return navigation remain unchanged.
- PASS/FAIL:
# Protection Coach Final Manual Verification

Manual verification items for the final connected v28 Protection Coach package. These are intentionally not marked as passed by source or automation work.

- [ ] Social-media onboarding recommendation
- [ ] Browser onboarding recommendation
- [ ] Late-night onboarding recommendation
- [ ] Review setup
- [ ] Accept setup
- [ ] Edit setup
- [ ] Dismiss setup
- [ ] No silent app selection
- [ ] No silent schedule change
- [ ] Cold-start explanation
- [ ] Real evidence supersedes onboarding
- [ ] Smart-window insufficient history
- [ ] Smart-window eligible history
- [ ] Why this timing suggestion
- [ ] Accept suggested time
- [ ] Edit suggested time
- [ ] Dismiss and cooldown
- [ ] Suppress suggestion
- [ ] Existing adequate schedule
- [ ] Monitor-toggle removal
- [ ] Legacy monitor transition
- [ ] No selected apps
- [ ] Missing Usage Access
- [ ] AppMonitorService restart
- [ ] Device reboot
- [ ] Website Protection continuity
- [ ] VPN continuity
- [ ] Plus promotion after support
- [ ] Plus promotion frequency cap
- [ ] Free-user support
- [ ] Plus-user schedules
- [ ] Privacy-safe analytics
- [ ] Screen privacy
- [ ] Backup and restore through Internal Testing
- [ ] Reset personal learning
- [ ] Delete all Moment data
