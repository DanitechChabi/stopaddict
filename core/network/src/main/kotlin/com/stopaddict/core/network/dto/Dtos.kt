package com.stopaddict.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Authentification (GoTrue) ---
@Serializable
data class AuthSession(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String? = null,
    val user: AuthUser? = null,
)

@Serializable
data class AuthUser(val id: String, val email: String? = null)

// --- Profils / appareils / engagements ---
@Serializable
data class ProfileDto(val id: String, val email: String, val role: String, val display_name: String? = null)

@Serializable
data class DeviceDto(
    val id: String,
    val device_name: String,
    val device_status: String,
    val management_mode: String,
    val android_version: String? = null,
    val last_sync_at: String? = null,
    val user_id: String,
    val admin_id: String,
)

@Serializable
data class CommitmentDto(
    val id: String,
    val device_id: String,
    val duration_days: Int,
    val status: String,
    val start_at: String? = null,
    val end_at: String? = null,
    val signed_payload: String? = null,
    val signature: String? = null,
)

@Serializable
data class PolicyVersionDto(val version: Int, val payload: kotlinx.serialization.json.JsonObject, val signature: String)

@Serializable
data class ReleaseRequestDto(
    val id: String,
    val commitment_id: String,
    val kind: String,
    val status: String,
    val reason_category: String? = null,
    val auto_release_at: String? = null,
)

@Serializable
data class BlockStatDto(val day: String, val category: String, val block_count: Int)

@Serializable
data class SecurityEventDto(val event_type: String, val severity: String, val detail: String? = null, val created_at: String)

// --- Réponses des Edge Functions ---
@Serializable
data class CreateInvitationResult(val invitation_id: String, val code: String, val expires_at: String)

@Serializable
data class AdminIdentity(val display_name: String? = null, val email: String? = null)

@Serializable
data class RedeemResult(val device_id: String, val admin: AdminIdentity)

@Serializable
data class CommitmentResult(
    val commitment_id: String,
    val status: String,
    val start_at: String? = null,
    val end_at: String? = null,
)

@Serializable
data class SyncResult(
    val server_now: String,
    val commitment: CommitmentDto? = null,
    val policy: PolicyVersionDto? = null,
)

@Serializable
data class ApiError(val error: String)
