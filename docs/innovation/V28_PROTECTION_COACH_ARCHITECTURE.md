# v28 Protection Coach Architecture

Impulsive Protection Coach is a privacy-first bridge between setup intentions, user-confirmed protection settings, genuine protected-moment evidence, and small explainable configuration recommendations.

Approved endorsement wording:

> Impulsive connects broad user-stated intentions with user-confirmed protection settings, then uses genuine encrypted on-device protected-moment evidence to identify recurring time patterns and propose the smallest relevant configuration adjustment.

> Every recommendation is explainable, versioned and subject to user confirmation.

## Boundary

Protection Coach is advisory. It does not automatically protect apps, change Website Protection, change VPN consent, create schedules, select Moment Plans, interrupt protected moments, or show purchase surfaces as the first action in a protected moment.

## Layers

- Pure domain: `ProtectionCoachSuggestion`, `ProtectionCoachEvidence`, `ProtectionCoachPolicy`, `ProtectionCoachValidator`
- Onboarding bridge: `OnboardingRecommendationPolicy`
- Temporary adaptive prior: `OnboardingColdStartPriorPolicy`
- Smart windows: `SmartProtectionWindowPolicy`
- Ledger: `protection_coach_suggestions` in Room 12
- UI: `suggested_setup`, `protection_coach`, `protection_coach_suggestion/{suggestionId}`, `protection_transition`
- Recovery/export: adaptive payload 4, readable export section, reset/deletion hooks

## Privacy invariant

The ledger stores only broad evidence and opaque IDs. It excludes package names, URLs, domains, browser history, journal text, account email, UID, diagnosis, and raw trigger content.
