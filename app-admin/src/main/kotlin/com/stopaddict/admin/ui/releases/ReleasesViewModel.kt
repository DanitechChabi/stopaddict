package com.stopaddict.admin.ui.releases

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopaddict.admin.Graph
import com.stopaddict.core.network.dto.ReleaseRequestDto
import kotlinx.coroutines.launch

data class ReleasesUiState(
    val loading: Boolean = true,
    val requests: List<ReleaseRequestDto> = emptyList(),
    val working: String? = null,
    val error: String? = null,
)

class ReleasesViewModel : ViewModel() {
    private val container = Graph.container
    var state by mutableStateOf(ReleasesUiState())
        private set

    init { refresh() }

    fun refresh() {
        state = state.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val token = container.session.requireToken()
                state = ReleasesUiState(loading = false, requests = container.client.listReleaseRequests(token))
            } catch (e: Exception) {
                state = state.copy(loading = false, error = e.message ?: "Erreur")
            }
        }
    }

    fun review(releaseId: String, decision: String) {
        state = state.copy(working = releaseId, error = null)
        viewModelScope.launch {
            try {
                val token = container.session.requireToken()
                container.client.reviewRelease(token, releaseId, decision)
                state = state.copy(working = null)
                refresh()
            } catch (e: Exception) {
                state = state.copy(working = null, error = e.message ?: "Échec de la décision")
            }
        }
    }
}
