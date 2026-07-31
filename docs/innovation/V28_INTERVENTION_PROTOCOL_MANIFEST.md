# V28 intervention protocol manifest

This manifest describes the versioned contracts implemented in `InterventionProtocolRegistry`. Status values are factual: implemented means present in source, tested means automated coverage exists, and manual means the connected-device manual plan remains outstanding.

All version-1 contracts:

- store decision ID, protocol identity, policy version, eligibility snapshot, lifecycle timestamps, feedback, repeat observation, and revision-bound plan references where applicable;
- prohibit protected source identity, package, URL, domain, page/search/notification text, journal content, account identity, and selected application label;
- support TalkBack, large text, a non-audio path, and non-colour-only state;
- preserve protection and fall back to `short_pause@1` except Short Pause itself, which offers generic support without reopening the protected source.

| Protocol | Version | Family | Route and start | Completion | Dismissal | Network | Status |
|---|---:|---|---|---|---|---|---|
| `short_pause` | 1 | Short Pause | In-app pause; timer starts | Pause duration elapses | Exit after presentation/start without completion | None | Implemented, tested; manual 6, 10, 53-56 outstanding |
| `pivot_game` | 1 | Pivot Game | In-app game; genuine session starts | Genuine game completion | Same exclusive dismissal rule | None | Implemented, tested; manual 7, 11 outstanding |
| `reset_reading` | 1 | Reset Reading | In-app reading; session starts | Minimum time and article end | Same exclusive dismissal rule | None | Implemented, tested; manual 12 outstanding |
| `moment_plan_text` | 1 | Moment Plan | Text action; explicit confirmation starts | Explicit manual confirmation | Same exclusive dismissal rule | None | Implemented, tested; manual 13, 36-38 outstanding |
| `moment_plan_external_app` | 1 | Moment Plan | Selected app; destination launch starts | Explicit manual confirmation after return | Same exclusive dismissal rule | External destination controlled | Implemented, tested; manual 13, 14 outstanding |
| `moment_plan_focus` | 1 | Moment Plan | Impulsive Focus; destination launch starts | Explicit manual confirmation after return | Same exclusive dismissal rule | None | Implemented, tested; manual 13, 14 outstanding |
| `moment_plan_journal` | 1 | Moment Plan | Impulsive Journal; destination launch starts | Explicit manual confirmation after return | Same exclusive dismissal rule | None | Implemented, tested; manual 13, 14 outstanding |
| `moment_plan_pivot_game` | 1 | Moment Plan | Plan-linked game; genuine session starts | Genuine game completion | Same exclusive dismissal rule | None | Implemented, tested; manual 13, 14 outstanding |
| `moment_plan_reset_reading` | 1 | Moment Plan | Plan-linked reading; session starts | Minimum time and article end | Same exclusive dismissal rule | None | Implemented, tested; manual 13, 14 outstanding |

Protocol IDs and versions are historical evidence. An unknown ID is rejected on restore. A known ID with a future positive version remains readable as non-executable history, and family compatibility is still enforced. No protocol contract diagnoses, treats, or guarantees an outcome.
