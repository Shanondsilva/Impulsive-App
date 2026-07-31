# v28 Protection Coach Source Audit

Date: 2026-07-29

Scope: audit performed before implementing the final connected v28 functional package, `Impulsive Protection Coach`.

## Repository state

- Repository: `D:\Impulsive\Impulsive-App-GitHub`
- Branch: `feature/adaptive-moment-engine-v28`
- HEAD before this package: `aedc81ff407651d888aed680a137c2f00fdc3da0`
- Version before and during work: `versionCode=27`, `versionName=1.0.0`
- Room before work: `11`
- Adaptive recovery payload before work: `3`
- Exported schemas found before work: `3.json` through `11.json`
- Schema 11 SHA-256 before work: `CF7623F6B5342B187B94CC7D04822A00DA0CAFF76B95DF0E9DBDA5BD55763CA4`

## Staged and dirty-state receipt

The repository was already dirty before this task. The pre-existing staged rename was confirmed and left untouched:

`app/src/main/res/drawable/impulsive_logo.png -> app/src/main/res/drawable-xxxhdpi/impulsive_logo.png`

No reset, stash, clean, restore, stage, unstage, commit, push, merge, signing, upload, Play Console action, release AAB build, or version change was performed as part of this package.

## Located source systems

- Onboarding storage/models: `OnboardingPreferencesDataSource.kt`, `OnboardingAnswers.kt`, `OnboardingRepository.kt`
- Protection setup storage: `ProtectionSetupPreferencesDataSource.kt`, `ProtectionSetupRepository.kt`, `ProtectionSetupModels.kt`
- Protected-app storage: `selected_blocked_app_package_names`
- Legacy monitor preference: `app_protection_monitor_enabled`
- App monitoring service: `AppMonitorService.kt`
- Recovery watchdog: `ProtectionWatchdog.kt`
- Boot receiver: `BootCompletedReceiver.kt`
- Protection settings UI: `SettingsScreen.kt`, `ProtectionSetupOnboardingScreens.kt`
- PathShift time-window source: `PathShiftCycleDao.kt`, `PathShiftCycleEntity.kt`, `RoomPathShiftCycleRepository.kt`
- Moment Window classification: `AdaptiveDecisionDao.kt`, `AdaptiveDecisionEntity.kt`
- Schedule/window policy: `ReleasePlan.kt`, `ProtectionWindowEvaluator.kt`, `ProtectionWindowNotificationDataSource.kt`
- Subscription architecture: `PremiumEntitlementDataSource.kt`, `PremiumRepository.kt`, `BillingManager.kt`
- Analytics: no consent-aware app-wide abstraction was present before this package
- Backup/restore/export: `RestoreBundleWriter.kt`, `RestoreBundleImporter.kt`, `AdaptiveRestorePayloadCodec.kt`, `UserDataExporter.kt`

## Baseline verification snapshot

- Focused adaptive domain tests: passed
- Focused adaptive session tests: passed
- Full JVM unit suite: passed
- `compileDebugKotlin`: passed
- `compileReleaseKotlin`: passed
- `lintDebug`: passed
- `assembleDebug`: passed
- `assembleDebugAndroidTest`: passed
- `git diff --check`: no whitespace errors; Windows line-ending warnings only

The empty `backend.protection.*` and `backend.onboarding.*` focused filters produced the expected baseline "No tests found" result before new focused packages were added.
