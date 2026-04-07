# Maestro Regression Flows

Three flows covering the critical paths. Run with a device/emulator connected and the debug APK installed.

## Prerequisites
- `maestro` CLI installed and in PATH
- Device connected (`adb devices` shows it)
- Debug APK installed: `./gradlew installDebug`
- UsageStats and notification permissions granted on the device

## Run all flows
```
maestro test .maestro/
```

## Run individually
```
maestro test .maestro/01_onboarding.yaml
maestro test .maestro/02_intercept.yaml
maestro test .maestro/03_walk_away.yaml
```

## Notes
- `01_onboarding` clears app state — run first or in isolation
- `02_intercept` uses `startActivity` to bypass the home screen; permissions must already be granted
- `03_walk_away` expects the auto-dismiss at 2.8s; the 3.5s wait gives 700ms tolerance
- `GatewayScreen` is a Composable, not an Activity — if `startActivity` fails for flow 03,
  run flow 02 end-to-end and tap Walk Away instead
