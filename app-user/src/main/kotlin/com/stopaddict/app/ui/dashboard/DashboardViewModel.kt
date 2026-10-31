package com.stopaddict.app.ui.dashboard

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stopaddict.app.Graph
import com.stopaddict.core.crypto.Ed25519Verifier
import com.stopaddict.core.model.Commitment
import com.stopaddict.core.model.CommitmentProgress
import com.stopaddict.core.model.CommitmentStatus
import com.stopaddict.core.model.TrustedTime
import com.stopaddict.core.network.dto.CommitmentDto
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.OffsetDateTime

/** Statut de signature affiché — honnête : on ne prétend pas vérifié sans clé. */
enum class SignatureStatus { VALID, INVALID, UNVERIFIABLE }

data class DashboardUiState(
    val loading: Boolean = true,
    val commitment: CommitmentDto? = null,
    val progress: CommitmentProgress = CommitmentProgress.Unknown,
    val signature: SignatureStatus = SignatureStatus.UNVERIFIABLE,
    val working: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class DashboardViewModel : ViewModel() {
    private val container = Graph.container
    private var deviceId: String? = null
    var state by mutableStateOf(DashboardUiState())
        private set

    init { refresh() }

    fun refresh() {
        state = state.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val token = container.session.requireToken()
                val id = deviceId ?: container.client.listDevices(token).firstOrNull()?.id
                deviceId = id
                if (id == null) {
                    state = state.copy(loading = false, error = "Aucun appareil associé")
                    return@launch
                }
                val sync = container.client.sync(token, id)
                val serverNowMillis = parseTs(sync.server_now)
                val commitment = sync.commitment

                val progress = computeProgress(commitment, serverNowMillis)
                val signature = verifySignature(commitment)

                state = DashboardUiState(
                    loading = false,
                    commitment = commitment,
                    progress = progress,
                    signature = signature,
                )
            } catch (e: Exception) {
                state = state.copy(loading = false, error = e.message ?: "Erreur de synchronisation")
            }
        }
    }

    fun consent() {
        val c = state.commitment ?: return
        if (c.status != "pending_consent" || state.working) return
        state = state.copy(working = true, error = null)
        viewModelScope.launch {
            try {
                container.client.consentCommitment(container.session.requireToken(), c.id)
                state = state.copy(working = false, message = "Engagement activé.")
                refresh()
            } catch (e: Exception) {
                state = state.copy(working = false, error = e.message ?: "Échec de l'activation")
            }
        }
    }

    fun requestRelease(kind: String) {
        val c = state.commitment ?: return
        if (state.working) return
        state = state.copy(working = true, error = null)
        viewModelScope.launch {
            try {
                container.client.requestRelease(container.session.requireToken(), c.id, kind)
                state = state.copy(
                    working = false,
                    message = if (kind == "emergency")
                        "Demande d'urgence envoyée. Libération automatique après le délai de réflexion, sauf annulation."
                    else "Demande envoyée à l'administrateur.",
                )
            } catch (e: Exception) {
                state = state.copy(working = false, error = e.message ?: "Échec de la demande")
            }
        }
    }

    private fun computeProgress(c: CommitmentDto?, serverNowMillis: Long?): CommitmentProgress {
        if (c == null || c.status != "active") return CommitmentProgress.NotActivated
        val start = c.start_at?.let(::parseTs) ?: return CommitmentProgress.NotActivated
        val end = c.end_at?.let(::parseTs) ?: return CommitmentProgress.NotActivated
        if (serverNowMillis == null) return CommitmentProgress.Unknown
        val model = Commitment(
            id = c.id, deviceId = c.device_id, adminId = "", userId = "",
            durationDays = c.duration_days, status = CommitmentStatus.ACTIVE,
            startAtUtcMillis = start, endAtUtcMillis = end,
            userConsentAtUtcMillis = start, adminConfirmationAtUtcMillis = start,
            signaturePresent = c.signature != null,
        )
        return CommitmentProgress.of(model, TrustedTime.Known(serverNowMillis))
    }

    private fun verifySignature(c: CommitmentDto?): SignatureStatus {
        val payload = c?.signed_payload
        val sig = c?.signature
        val key = Graph.signingPublicKey
        if (payload == null || sig == null) return SignatureStatus.UNVERIFIABLE
        if (key.isBlank()) return SignatureStatus.UNVERIFIABLE
        return runCatching {
            if (Ed25519Verifier.verify(key, payload.toByteArray(Charsets.UTF_8), sig))
                SignatureStatus.VALID else SignatureStatus.INVALID
        }.getOrDefault(SignatureStatus.INVALID)
    }

    private fun parseTs(s: String): Long? = runCatching {
        OffsetDateTime.parse(s).toInstant().toEpochMilli()
    }.recoverCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
}
