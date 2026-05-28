package com.impulsive.app.backend.session.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.PatternBreakSessionRepository
import com.impulsive.app.backend.domain.model.tasks.PatternBreakSession
import kotlinx.coroutines.launch

class PatternBreakViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = PatternBreakSessionRepository(application)

    fun saveSession(session: PatternBreakSession) {
        viewModelScope.launch {
            repository.saveSession(session)
        }
    }
}
