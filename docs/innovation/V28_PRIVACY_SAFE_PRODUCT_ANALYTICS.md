# v28 Privacy-Safe Product Analytics

Protection Coach introduces a narrow analytics abstraction with a no-op implementation when analytics are unavailable or not consented.

## Allowed events

- `onboarding_recommendation_shown`
- `onboarding_recommendation_accepted`
- `onboarding_recommendation_edited`
- `onboarding_recommendation_dismissed`
- `timing_suggestion_shown`
- `timing_suggestion_accepted`
- `timing_suggestion_edited`
- `timing_suggestion_dismissed`
- `timing_suggestion_suppressed`
- `protection_transition_shown`
- `protection_transition_completed`
- `plus_promotion_viewed`
- `plus_promotion_dismissed`
- `paywall_viewed`
- `purchase_started`
- `purchase_completed`
- `purchase_restored`

## Allowed parameters

- `entry_point`
- `suggestion_type`
- `result`
- `policy_version`
- `subscription_surface`

## Excluded fields

No onboarding answer values, trigger category, cue, broad time window, exact time, Moment Plan ID, plan revision, package name, URL/domain, journal text, feedback answer, urge rating, decision ID, suggestion ID, UID/email, PathShift estimate, LP, or character level is sent.
