package com.impulsive.app.backend.session.progress

import com.impulsive.app.backend.domain.model.score.PivotGameSafeExitIdentity
import com.impulsive.app.backend.domain.model.score.SafeExitProgressSnapshot
import com.impulsive.app.backend.domain.model.score.ScoreRange
import com.impulsive.app.backend.domain.model.score.ScoreSessionRecord
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

internal data class ScoreAndSafeExitSignals(
    val sessions:
        List<ScoreSessionRecord>,
    val safeExitProgress:
        SafeExitProgressSnapshot,
    val selectedRange:
        ScoreRange,
    val now:
        LocalDateTime,
)

private data class ScoreProgressRefreshInput(
    val selectedRange:
        ScoreRange,
    val sessions:
        List<ScoreSessionRecord>,
)

@OptIn(
    ExperimentalCoroutinesApi::class,
)
internal fun observeScoreAndSafeExitSignals(
    selectedRange:
        Flow<ScoreRange>,
    sessions:
        Flow<List<ScoreSessionRecord>>,
    ledgerChanges:
        Flow<Unit>,
    observeSafeExitProgress:
        (
            ScoreRange,
            LocalDateTime,
            Set<String>,
        ) -> Flow<SafeExitProgressSnapshot>,
    nowProvider:
        () -> LocalDateTime,
): Flow<ScoreAndSafeExitSignals> {
    return combine(
        selectedRange,
        sessions,
        ledgerChanges,
    ) {
            range,
            currentSessions,
            _,
        ->
        ScoreProgressRefreshInput(
            selectedRange =
                range,
            sessions =
                currentSessions,
        )
    }
        .flatMapLatest { input ->
            /*
             * now is regenerated whenever the selected range, score-session
             * store or Safe Exit ledger emits.
             */
            val now =
                nowProvider()

            val pivotCandidateSourceKeys =
                input.sessions
                    .mapNotNull(
                        PivotGameSafeExitIdentity::
                            sourceKey,
                    )
                    .toSet()

            observeSafeExitProgress(
                input.selectedRange,
                now,
                pivotCandidateSourceKeys,
            )
                .map { safeExitProgress ->
                    ScoreAndSafeExitSignals(
                        sessions =
                            input.sessions,
                        safeExitProgress =
                            safeExitProgress,
                        selectedRange =
                            input.selectedRange,
                        now =
                            now,
                    )
                }
        }
}