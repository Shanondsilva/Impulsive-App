# v28 Protection Monitor Transition

The permanent user-facing `Protection Monitor` master toggle is removed. App protection now follows explicit protected-app configuration plus required Android permissions and service health.

## New behavior

One or more explicitly selected protected apps means app monitoring operates automatically when Usage Access, overlay requirements, and service health are satisfied.

Users stop app monitoring by removing protected apps or revoking Usage Access. Impulsive does not add newly installed apps automatically and does not monitor every installed app.

Website Protection remains separate and continues to use its existing VPN consent path.

## Legacy behavior

The stored legacy `appProtectionMonitorEnabled` preference remains recoverable for v28. If a legacy user has the monitor off but selected apps remain, Impulsive shows:

- `App protection has changed`
- `Impulsive now monitors automatically whenever you keep protected apps selected.`
- `You can keep those apps protected or review your selection.`

`Keep protection` records `protectionMonitorTransitionCompleted`. `Review protected apps` opens the existing selector. Dismissal does not silently enable monitoring.
