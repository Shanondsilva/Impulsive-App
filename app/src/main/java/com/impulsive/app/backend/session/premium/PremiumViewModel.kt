package com.impulsive.app.backend.session.premium

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.impulsive.app.backend.data.repository.PremiumRepository
import com.impulsive.app.backend.domain.model.premium.PremiumEntitlement
import com.impulsive.app.backend.domain.model.premium.PremiumFeature
import com.impulsive.app.backend.domain.model.premium.includes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PremiumViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PremiumRepository(application)

    val entitlement: StateFlow<PremiumEntitlement> = repository.entitlement.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PremiumEntitlement(),
    )

    fun hasFeature(feature: PremiumFeature): StateFlow<Boolean> =
        repository.entitlement
            .map { it.tier.includes(feature) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    fun setEntitlement(entitlement: PremiumEntitlement) {
        viewModelScope.launch { repository.setEntitlement(entitlement) }
    }
}
