package com.impulsive.app.backend.session.protection

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import com.impulsive.app.backend.data.local.device.DnsFilterGate
import com.impulsive.app.backend.data.local.device.PrivateDnsChecker
import com.impulsive.app.backend.domain.model.protection.DnsFilterGateEvaluator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Screen state for the DNS filter pre-enable gate. hasChecked is false until the first read,
 * so the screen can show a neutral state before it has looked. canEnable is true only when no
 * blocker is present.
 */
data class DnsFilterGateUiState(
    val hasChecked: Boolean = false,
    val canEnable: Boolean = false,
    val privateDnsActive: Boolean = false,
    val privateDnsHostname: String? = null,
    val anotherVpnActive: Boolean = false,
)

class DnsFilterGateViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val gate = DnsFilterGate(application)

    private val _state = MutableStateFlow(DnsFilterGateUiState())
    val state: StateFlow<DnsFilterGateUiState> = _state.asStateFlow()

    fun refresh() {
        val result = gate.evaluate()
        val dnsState = gate.privateDnsState()
        _state.value = DnsFilterGateUiState(
            hasChecked = true,
            canEnable = result.canEnable,
            privateDnsActive = result.blockers.contains(DnsFilterGateEvaluator.Blocker.PrivateDnsActive),
            privateDnsHostname = (dnsState as? PrivateDnsChecker.State.Strict)?.hostname,
            anotherVpnActive = result.blockers.contains(DnsFilterGateEvaluator.Blocker.AnotherVpnActive),
        )
    }

    fun privateDnsSettingsIntent(): Intent = gate.privateDnsSettingsIntent()
}
