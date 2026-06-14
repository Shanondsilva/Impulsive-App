package com.impulsive.app.backend.data.repository

import android.content.Context
import com.impulsive.app.backend.data.local.preferences.TaperPreferencesDataSource
import com.impulsive.app.backend.domain.model.release.TaperProposal
import com.impulsive.app.backend.domain.model.release.TaperStoreState
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

class TaperRepository(context: Context) {
    private val dataSource = TaperPreferencesDataSource(context)

    val state: Flow<TaperStoreState> = dataSource.state

    suspend fun recordAccepted(
        proposal: TaperProposal,
        acceptedAt: LocalDateTime = LocalDateTime.now(),
    ) {
        dataSource.recordAccepted(
            fromCount = proposal.fromCount,
            toCount = proposal.toCount,
            acceptedAt = acceptedAt,
        )
    }

    suspend fun recordDeclined(
        declinedAt: LocalDateTime = LocalDateTime.now(),
    ) {
        dataSource.recordDeclined(declinedAt)
    }

    suspend fun setProposalsDisabled(disabled: Boolean) {
        dataSource.setProposalsDisabled(disabled)
    }
}
