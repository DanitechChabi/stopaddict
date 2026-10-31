package com.stopaddict.admin.ui.device

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopaddict.admin.Graph
import com.stopaddict.core.network.dto.CommitmentDto
import com.stopaddict.core.network.dto.DeviceDto
import kotlinx.coroutines.launch

data class DeviceUiState(
    val loading: Boolean = true,
    val device: DeviceDto? = null,
    val commitment: CommitmentDto? = null,
    val proposing: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class DeviceViewModel(private val deviceId: String) : ViewModel() {
    private val container = Graph.container
    var state by mutableStateOf(DeviceUiState())
        private set

    val durations = listOf(7, 30, 60, 90, 180, 365)

    init { refresh() }

    fun refresh() {
        state = state.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val token = container.session.requireToken()
                val device = container.client.listDevices(token).firstOrNull { it.id == deviceId }
                val commitment = container.client.listCommitments(token)
                    .firstOrNull { it.device_id == deviceId && it.status in setOf("pending_consent", "active") }
                state = DeviceUiState(loading = false, device = device, commitment = commitment)
            } catch (e: Exception) {
                state = state.copy(loading = false, error = e.message ?: "Erreur")
            }
        }
    }

    fun propose(durationDays: Int) {
        if (state.proposing) return
        state = state.copy(proposing = true, error = null, message = null)
        viewModelScope.launch {
            try {
                val token = container.session.requireToken()
                container.client.proposeCommitment(token, deviceId, durationDays)
                state = state.copy(proposing = false, message = "Engagement proposé ($durationDays jours). En attente du consentement de l'utilisateur.")
                refresh()
            } catch (e: Exception) {
                state = state.copy(proposing = false, error = e.message ?: "Échec de la proposition")
            }
        }
    }
}
