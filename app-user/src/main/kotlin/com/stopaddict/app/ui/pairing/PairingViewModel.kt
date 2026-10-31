package com.stopaddict.app.ui.pairing

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopaddict.app.Graph
import com.stopaddict.core.network.dto.AdminIdentity
import kotlinx.coroutines.launch

data class PairingUiState(
    val code: String = "",
    val loading: Boolean = false,
    val admin: AdminIdentity? = null,
    val deviceId: String? = null,
    val error: String? = null,
)

class PairingViewModel : ViewModel() {
    private val container = Graph.container
    var state by mutableStateOf(PairingUiState())
        private set

    fun onCode(v: String) { state = state.copy(code = v.uppercase(), error = null) }

    fun redeem() {
        if (state.loading || state.code.isBlank()) return
        state = state.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val token = container.session.requireToken()
                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
                val r = container.client.redeemInvitation(token, state.code.trim(), deviceName, Build.VERSION.RELEASE)
                state = state.copy(loading = false, admin = r.admin, deviceId = r.device_id)
            } catch (e: Exception) {
                state = state.copy(loading = false, error = e.message ?: "Code invalide ou expiré")
            }
        }
    }
}
