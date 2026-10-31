package com.stopaddict.admin.ui.invitation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopaddict.admin.Graph
import kotlinx.coroutines.launch

data class InvitationUiState(
    val label: String = "",
    val loading: Boolean = false,
    val code: String? = null,
    val expiresAt: String? = null,
    val error: String? = null,
)

class InvitationViewModel : ViewModel() {
    private val container = Graph.container
    var state by mutableStateOf(InvitationUiState())
        private set

    fun onLabel(v: String) { state = state.copy(label = v) }

    fun generate() {
        if (state.loading) return
        state = state.copy(loading = true, error = null, code = null)
        viewModelScope.launch {
            try {
                val token = container.session.requireToken()
                val r = container.client.createInvitation(token, state.label.ifBlank { null })
                state = state.copy(loading = false, code = r.code, expiresAt = r.expires_at)
            } catch (e: Exception) {
                state = state.copy(loading = false, error = e.message ?: "Échec de la génération")
            }
        }
    }
}
