package com.impulsive.app.backend.session.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.local.preferences.GameAccessState
import com.impulsive.app.backend.data.repository.GameStoreManager
import com.impulsive.app.backend.data.repository.ScoreRepository
import com.impulsive.app.backend.data.repository.StoreResult
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GameStoreViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = GameStoreManager(application)
    private val scoreRepository = ScoreRepository(application)

    val spendablePoints = manager.spendablePoints
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val accessByGame = manager.accessByGame
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap<String, GameAccessState>())
    val playedGameTypeIds = scoreRepository.sessions
        .map { sessions -> sessions.map { it.gameType.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet<String>())

    fun buy(id: String, onResult: (StoreResult) -> Unit) {
        viewModelScope.launch { onResult(manager.buy(id)) }
    }

    fun rent(id: String, onResult: (StoreResult) -> Unit) {
        viewModelScope.launch { onResult(manager.rent(id)) }
    }
}
