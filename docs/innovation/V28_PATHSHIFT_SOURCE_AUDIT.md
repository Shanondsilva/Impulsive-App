# V28 PathShift source audit

Audit date: 29 July 2026  
Repository: `D:\Impulsive\Impulsive-App-GitHub`  
Branch: `feature/adaptive-moment-engine-v28`  
HEAD: `aedc81ff407651d888aed680a137c2f00fdc3da0`

## Confirmed source before PathShift

- Android version remained `27 / 1.0.0`.
- Room was schema 10 and adaptive recovery payload was schema 2.
- Schema files 3 through 10 existed. Schema 9 SHA-256 was `1F039785F96DA5BA24711177D0B8467DBBEBE9F785D73CF79A99C4CB13B86E22`; schema 10 was `8BC1810D8136A975375F6BA5A144F3A310550C8012C853F946DC9C5FD829042E`.
- No files were staged.
- No `PathShift`, `path_shift`, or `Future Path` implementation existed.
- `TaskRewardDataSource` was the authoritative LP store. Adaptive outcomes did not call it or award LP.
- `MindCoreScene` rendered the main level character and reused level 1 through 5 scene assets.
- Home already displayed the existing level card and `MindCoreScene`.
- Private Moment Loop hardening classes, intervention contracts, decision passports, exact content revisions, privacy, retention, replay, and recovery schema 2 were present.

## Reported evidence

The preceding receipt reported 1,219 JVM tests, 60 Samsung tests, lint PASS, and successful debug installation. Reported evidence was treated as orientation only.

## Newly executed pre-edit evidence

| Gate | Result |
|---|---|
| adaptive domain JVM | PASS |
| adaptive session JVM | PASS |
| adaptive frontend JVM | PASS |
| full JVM | 1,219/1,219 PASS |
| `compileDebugKotlin` | PASS |
| `lintDebug` | PASS |
| `assembleDebug` | PASS |
| `assembleDebugAndroidTest` | PASS |
| `git diff --check` | PASS |

## Outstanding manual evidence at baseline

All existing 56 connected manual tests remained outstanding. None was marked passed.

## Safety finding

The source was a dirty, intentional schema-10 implementation worktree. It was preserved without reset, stash, clean, restore, stage, commit, push, merge, signing, upload, version change, or release AAB build.
