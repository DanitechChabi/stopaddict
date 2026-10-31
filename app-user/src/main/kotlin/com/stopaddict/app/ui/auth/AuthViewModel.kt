package com.stopaddict.app.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopaddict.app.Graph
import com.stopaddict.core.network.ApiException
import kotlinx.coroutines.launch

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val isSignUp: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

class AuthViewModel : ViewModel() {
    private val container = Graph.container
    var state by mutableStateOf(AuthUiState())
        private set

    fun onEmail(v: String) { state = state.copy(email = v, error = null) }
    fun onPassword(v: String) { state = state.copy(password = v, error = null) }
    fun toggleMode() { state = state.copy(isSignUp = !state.isSignUp, error = null) }

    fun submit(onSuccess: () -> Unit) {
        if (state.loading) return
        state = state.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val session = if (state.isSignUp) {
                    container.client.signUp(state.email.trim(), state.password, role = "user")
                } else {
                    container.client.signIn(state.email.trim(), state.password)
                }
                val token = session.accessToken
                if (token.isBlank()) {
                    state = state.copy(loading = false, error = "Vérifiez votre e-mail puis connectez-vous.", isSignUp = false)
                    return@launch
                }
                val profile = runCatching { container.client.getMyProfile(token) }.getOrNull()
                container.session.set(token, session.user?.id ?: profile?.id, profile?.role ?: "user")
                state = state.copy(loading = false)
                onSuccess()
            } catch (e: ApiException) {
                state = state.copy(loading = false, error = e.message ?: "Erreur (${e.status})")
            } catch (e: Exception) {
                state = state.copy(loading = false, error = "Connexion impossible")
            }
        }
    }
}
