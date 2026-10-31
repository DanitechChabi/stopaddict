package com.stopaddict.admin.ui.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopaddict.admin.Graph
import com.stopaddict.core.network.dto.CommitmentDto
import com.stopaddict.core.network.dto.DeviceDto
import kotlinx.coroutines.launch

data class DashboardUiState(
    val loading: Boolean = true,
    val devices: List<DeviceDto> = emptyList(),
    val activeCommitments: Int = 0,
    val pendingReleases: Int = 0,
    val error: String? = null,
)

class DashboardViewModel : ViewModel() {
    private val container = Graph.container
    var state by mutableStateOf(DashboardUiState())
        private set

    init { refresh() }

    fun refresh() {
        state = state.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val token = container.session.requireToken()
                val devices = container.client.listDevices(token)
                val commitments = container.client.listCommitments(token)
                val releases = container.client.listReleaseRequests(token)
                state = DashboardUiState(
                    loading = false,
                    devices = devices,
                    activeCommitments = commitments.count { it.status == "active" },
                    pendingReleases = releases.size,
                )
            } catch (e: Exception) {
                state = state.copy(loading = false, error = e.message ?: "Erreur de chargement")
            }
        }
    }
}
