# V28 PathShift LP and character integration

## Existing authority

`TaskRewardDataSource`, existing LP calculations, current level, Home level progress, `MindCoreScene`, and level 1 through 5 assets remain authoritative.

PathShift introduces no second currency, character, leaderboard, purchasable multiplier, streak punishment, or onboarding-avatar substitution.

## Presentation model

`PathShiftCharacterPresentation` accepts existing level, current level points, PathShift UI state, time of day, and reduced-motion state. It maps to `LookingAhead`, `PathPrepared`, `WalkingCurrentPath`, `ReviewingPath`, or `NotEnoughHistory`.

These states are visual only. They do not change LP, evidence, forecast, utility, recommendation, or outcomes. TalkBack describes continued participation and explicitly avoids interpreting health status or forecast severity.

`MindCoreScene` is reused. Reduced-motion mode freezes ambient scene motion while keeping the scene and controls usable.

## LP receipt

PathShift awards zero LP for enabling, forecast creation, viewing, an incident, plan selection, positive feedback, lower count, review, or no repeat. It removes zero LP for higher count, Wrong Timing, Did Not Help, dismissal, cancellation, insufficient history, or another incident.

Existing Game, Reset Reading, Focus, Journal, and task LP paths were not changed. Rehearsal LP was not added because no new exact-once reward ledger was justified for this scope.
