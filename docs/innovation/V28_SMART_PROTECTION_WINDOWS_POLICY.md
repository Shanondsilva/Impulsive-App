# v28 Smart Protection Windows Policy

Smart Protection Windows reuse genuine root protected-moment evidence and broad local-time windows. They do not alter PathShift forecasts and do not build a second incident counter.

## Eligibility

A timing recommendation requires:

- at least 7 genuine root protected moments;
- at least 5 distinct local dates;
- at least 14 days of history;
- one two-hour bucket with at least 30% of eligible incidents;
- at least 3 incidents inside that bucket;
- no materially future timestamps;
- no active equivalent suggestion;
- no adequate accepted schedule already covering the evidence window.

Duplicates, follow-up decisions, rehearsals, feedback screens, and non-root events are excluded. Timezone is injected; location is not stored.

## Recommendation shape

The policy proposes the smallest relevant adjustment, rounded to 30-minute boundaries. It avoids all-day schedules and declines to suggest stronger protection when an existing schedule already covers the evidence window.

## Cooldowns

- Dismissed equivalent timing suggestions cool down for 14 days.
- Suppressed suggestions do not repeat until the user resets or manually asks for review.
- Accepted suggestions do not repeat while the accepted configuration still covers the evidence window.
