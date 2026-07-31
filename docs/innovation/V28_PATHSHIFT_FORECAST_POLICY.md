# V28 PathShift forecast policy

Authoritative policy version: `PathShiftForecastPolicyVersion.Current = 1`.

## Input and exclusions

The policy accepts only opaque incident token, generic source kind, timestamp, generation time, and injected `ZoneId`. It excludes protected application, browser, URL, domain, page, search, package, email, UID, journal text, location, utility, and random state.

Root incidents are deduplicated by opaque token. `AdaptiveSourceKind.ExplicitUserSupport` is excluded.

## Calendar windows

- Lookback: the previous 28 device-local calendar days.
- Forecast: the next seven device-local calendar days.
- The lookback is split into four oldest-to-newest seven-day buckets.
- Local calendar boundaries come from the injected timezone. Location is neither requested nor stored.

## Evidence gate

No numerical range is available unless there are at least seven genuine root moments, at least five distinct local dates, at least a fourteen-day span, and no invalid or materially future timestamp.

`EarlyEstimate` means the gate passes but the cautious threshold does not.

`CautiousEstimate` requires at least 14 root moments, at least ten distinct dates, a full 27-day endpoint span across the 28-day lookback, and no invalid timestamps.

## Exact calculation

Let oldest-to-newest weekly counts be `c1`, `c2`, `c3`, `c4`.

`expected = (1*c1 + 2*c2 + 3*c3 + 4*c4) / 10`

`deviation = (1*|c1-expected| + 2*|c2-expected| + 3*|c3-expected| + 4*|c4-expected|) / 10`

`buffer = max(1, ceil(deviation))`

`lower = max(0, floor(expected-buffer))`

`upper = ceil(expected+buffer)`

Consumer values are capped at 99 and corrected to retain at least a one-count range. The UI never exposes formulas or a single exact count.

## Common time window

Local time is split into deterministic two-hour buckets. A window appears only if it has at least three root moments and at least 30 percent of eligible input. Equal counts select the earliest bucket.

## Limitations

This is a transparent weighted-rate estimate, not machine learning. It does not know the future, diagnose behaviour, establish effectiveness, or interpret a higher or lower observed count as success or failure.
