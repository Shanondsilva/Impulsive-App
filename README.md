# Impulsive: Impulse Control

> Privacy-first habit interruption. No tracking. No cloud. Just friction.

![Platform](https://img.shields.io/badge/Platform-Android_14+-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4?logo=android&logoColor=white)
![Room](https://img.shields.io/badge/DB-Room-blue?logo=sqlite&logoColor=white)

Impulsive is an evidence-based, on-device Android application designed to break the automatic muscle-memory loop of doomscrolling. Rather than passively blocking apps (which often leads to frustration and immediate uninstallation) or relying on guilt-inducing streaks (which trigger the Abstinence Violation Effect), Impulsive introduces **active cognitive friction** precisely when the urge strikes.

### ⚠️ Note on Screenshots
*(Insert screenshots highlighting: 1. Intercept Shield 2. Trigger Routing 3. Walk Away Screen 4. Weekly Check-In Chart 5. Settings)*

---

## 🛑 The Problem with Screen Time Apps
1. **Passivity:** App blockers work... until you disable them. They demand no psychological growth.
2. **The "Streak" Trap:** Streaks enforce perfection. When a user inevitably breaks an 80-day streak, the resulting Abstinence Violation Effect ("AVE") causes them to binge and abandon the tool entirely. 
3. **Data Harvesting:** Many "wellness" apps collect massive amounts of intimate behavioral data and telemetry, syncing it to external clouds.

## 🧠 The Impulsive Solution
Impulsive intervenes *during the urge*, acting as a psychological speed bump:
1. **Active Friction:** Attempting to open a high-dopamine app triggers a 15-second physical press-and-hold requirement. You have to earn the distraction. 
2. **Trigger Routing:** Once the prefrontal cortex is online, you self-identify the underlying drive (Bored? Stressed? Lonely?). Each trigger provides a tailored cognitive intervention.
3. **The Anti-Streak Protocol:** "Sessions" are an explicit, time-boxed allowance. You are given a quota of sessions per week that gradually decreases via an automated tapering engine. Breaking a rule does not reset a punishing counter; the app refocuses entirely on your next decision.
4. **Complete Local Processing:** Impulsive functions entirely off the grid. Nothing leaves your device. 

---

## 🏗️ Architecture & Tech Stack

Impulsive adheres to Modern Android Development (MAD) guidelines, built with a robust `MVVM` architecture. 

| Layer | Technologies Used |
|-------|-------------------|
| **UI** | Jetpack Compose (Material 3), Navigation Compose, Vico Charts (M3) |
| **Presentation** | Kotlin Coroutines, StateFlow, ViewModel |
| **Logic/Domain** | Pure functional engines (`TaperingEngine`), Koin for DI (`AppModule`) |
| **Data/Persistence** | Room Database (Flow aggregations), AndroidX Security (`EncryptedFile`) |
| **Background/OS Interface**| `UsageStatsManager`, Foreground Services, `WorkManager` |
| **Testing** | JUnit4, Maestro (YAML E2E flow testing) |

**Key Engineering Highlights:**
- **Zero-Polling Foreground Monitor:** The `AppMonitorService` efficiently polls `UsageStatsManager` in a highly optimized loop with hardware impact minimalization in mind, deploying `setFullScreenIntent` to hijack the screen instantaneously when an addictive app foregrounds.
- **Encrypted Local Backup:** Room DB backups leverage `AES256_GCM_HKDF_4KB` Android Keystore encryption for manual portability without sacrificing privacy.
- **Custom Charting:** Data viz is driven by Vico, rendering real-time SQLite metric aggregations on the Weekly Reflection dashboard.  

---

## 📊 Evaluation Metrics

Impulsive ships with an `EvalMetrics` layer that logs behavioral outcomes locally for personal research. Every number in the app derives from one of the five Room tables — no fabricated stats.

Key metrics derivable from your own export:

| Metric | How to derive |
|--------|--------------|
| Friction effectiveness | WalkAway outcomes / total intercepts |
| Tapering velocity | Week-on-week delta in `allowedSessions` |
| Trigger distribution | Count of each trigger value in `TriggerLog` |
| Bypass rate | `BypassEvent` count / total session weeks |
| Stall reason breakdown | `stallReason` frequency in `WeeklyTarget` |

Export your data as JSON from **Settings → Export Data** to analyse your own patterns.

---

## 🛠️ Build and Run

1. Clone the repository.
2. Ensure you have Android Studio installed and Java 17 minimum.
3. Custom fonts (`Plus Jakarta Sans`, `Inter`, `JetBrains Mono`) are bundled in `app/src/main/res/font/`. If missing, place the TTF files there before building — see `docs/` for naming conventions.
4. Sync Gradle and build the `assembleDebug` variant.

**Note on Permissions:** On first launch, the app requires deep system-level permissions (Usage Access, Display over other apps, Notification Access, and Battery Optimization Exemption). 

---

## 🧪 Regression Testing

Three Maestro flows cover the critical paths:

```bash
# Requires device connected and debug APK installed
maestro test .maestro/
```

| Flow | Covers |
|------|--------|
| `01_onboarding.yaml` | Baseline picker → triggers → anchors → path → home |
| `02_intercept.yaml` | 15s hold → trigger routing → intervention → gateway |
| `03_walk_away.yaml` | Gateway → Walk Away → 2.8s auto-dismiss |

---

## 🔐 Privacy Statement
Impulsive strictly uses `UsageStatsManager` as a real-time behavioral trigger mechanism. The app tracks absolutely zero personal data, utilizes no telemetry SDKs, crashes are not reported, and **data never leaves the device**. Impulsive cannot be monetized via data sales by design.

Full policy: [docs/privacy-policy.md](docs/privacy-policy.md)
