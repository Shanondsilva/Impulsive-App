# V28 source audit before hardening

Audit date: 29 July 2026

This receipt records the repository state before the innovation-integrity hardening work began. It is source and automated-build evidence, not a replacement for the connected manual test plan.

## Repository identity

- Repository: `D:\Impulsive\Impulsive-App-GitHub`
- Branch: `feature/adaptive-moment-engine-v28`
- HEAD: `aedc81ff407651d888aed680a137c2f00fdc3da0`
- Version code: `27`
- Version name: `1.0.0`
- Room version: `9`
- Staged files: `0`
- Modified tracked files: `37`
- Untracked files: `102`

No reset, stash, clean, restore, checkout, staging, commit, push, merge, upload, signing, release build, or Play Console action was performed.

## Schema verification

Schema files 3 through 9 exist. Schemas 1 and 2 remain absent and were not created.

| Schema | SHA-256 |
|---|---|
| 3 | `6B0323A212E6075345AD0232919827DBBAEB0D9EAEC6836BA9A3952C5954E89B` |
| 4 | `5500F1DD53F7636C802386338EC0EDAD0531108C2776E80BAAE76F18FD75CC7A` |
| 5 | `51A5C4D3E8E8C489299C69AD2EBB25F225694A695025B262714D351A4A44D357` |
| 6 | `D9A1A1AC44CFA5022E9C55408040E2BB9197BC33D3E0FD98115630CE56D919CC` |
| 7 | `C30524255B82C60FA34093679D8FAE21E9B63550E35B0F7E97B3BB4B4B13F874` |
| 8 | `8886D5F171F29805D287F2D3B3F3C354ED9A466B2C0EA558ACE7CB6280BB8ECD` |
| 9 | `1F039785F96DA5BA24711177D0B8467DBBEBE9F785D73CF79A99C4CB13B86E22` |

Schema 9 is the immutable migration baseline for the hardening package.

## Confirmed pre-existing implementation

Source inspection confirmed the connected Private Moment Loop already contains:

- adaptive recommendation policy and eligibility mask;
- encrypted adaptive decisions in SQLCipher Room;
- Moment Plans with enabled and preferred rules;
- guided and quick rehearsal with structured history;
- rehearsal plan-update timestamp and decision plan-update timestamp;
- factual rehearsal-to-later-use matching;
- Started, Completed, and Dismissed lifecycle semantics;
- feedback revision and separate Wrong Timing;
- 20-minute repeat observation and WorkManager recovery;
- follow-up decisions;
- What Works for Me with conservative thresholds;
- How suggestions work;
- reset personal learning and double-confirmed complete Moment-data deletion;
- encrypted recovery with optional all-or-nothing adaptive payload;
- Personal support data export;
- app-lock-protected private routes;
- migration 8 to 9 and schema 9;
- innovation architecture, defensibility, evidence, roadmap, verification, and 34-case manual-test documents.

The existing `eligibleInterventionsMask` is the eligible-family snapshot and must not be duplicated.

## Confirmed missing hardening layers

The audit found no completed implementation of:

1. versioned Intervention Contract Registry;
2. dedicated opaque Moment Plan content revision identity;
3. Decision Evidence Passport;
4. explicit evidence-quality tiers;
5. expanded per-decision explanation receipt;
6. selective route-sensitive screen privacy preference;
7. adaptive-history retention controls and cleanup worker;
8. safe debug-only Policy Replay Laboratory;
9. the new protocol, lineage, retention/privacy, invention, and engineering-know-how documents;
10. migration 9 to 10 and schema-10 durability integration.

This hardening task will extend the current architecture rather than rebuild it.

## Baseline commands and results

| Command | Result |
|---|---|
| `.\gradlew.bat :app:testDebugUnitTest --tests "com.impulsive.app.backend.domain.engine.adaptive.*"` | PASS, 52 tests |
| `.\gradlew.bat :app:testDebugUnitTest --tests "com.impulsive.app.backend.session.adaptive.*"` | PASS, 146 tests |
| `.\gradlew.bat :app:testDebugUnitTest --tests "com.impulsive.app.frontend.adaptive.*"` | PASS, 88 tests |
| `.\gradlew.bat :app:testDebugUnitTest` | PASS, 1,082 tests, 0 failures, 0 skipped |
| `.\gradlew.bat :app:compileDebugKotlin` | PASS |
| `.\gradlew.bat :app:lintDebug` | PASS, zero issues in the existing report |
| `.\gradlew.bat :app:assembleDebug` | PASS |
| `git diff --check` | PASS; existing LF-to-CRLF conversion notices only |

## Existing documents and tests

The architecture, defensibility map, endorsement evidence matrix, future roadmap, verification receipt, and 34-case manual plan all exist.

Adaptive JVM tests exist under domain-engine, session, and frontend namespaces. Instrumented coverage exists for persistence, migration 8 to 9, rehearsal, dashboard, reset, backup/restore, Phase 5 follow-up navigation, and Phase 6 outcomes.

## Source-only limitations

- This audit did not rerun physical-device tests because Workstream 0 requires JVM/build verification; exact Samsung gates are reserved for the completed schema-10 implementation.
- Source inspection cannot prove screenshot/Recents protection on every OEM.
- Local restore tests cannot prove external Google Drive or Android Auto Backup timing.
- Passing automation does not mark any manual test as passed.
- The audit does not establish patentability, clinical outcomes, partnerships, customers, commercial viability, or uniqueness across the market.

## Release safety

Version remains `27 / 1.0.0`. Room remains 9 at this checkpoint. No release AAB was built and no release preparation began.
