package com.stopaddict.core.network

import com.stopaddict.core.network.dto.*
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class SupabaseConfig(val url: String, val anonKey: String)

/** Erreur d'API portant le code HTTP et le message renvoyé par le serveur. */
class ApiException(val status: Int, message: String) : Exception(message)

/**
 * Client Supabase : authentification (GoTrue), Edge Functions et lectures
 * PostgREST. Aucune clé secrète ici — seulement la clé publique (anon). Les
 * écritures sensibles passent par les Edge Functions, qui valident côté serveur.
 */
class SupabaseClient(
    private val config: SupabaseConfig,
    engine: HttpClientEngine? = null,
) {
    private val jsonFormat = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val http: HttpClient = run {
        val block: HttpClient.() -> Unit = {}
        val configure: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
            install(ContentNegotiation) { json(jsonFormat) }
        }
        if (engine != null) HttpClient(engine, configure) else HttpClient(OkHttp, configure)
    }

    private val base get() = config.url.trimEnd('/')

    // --- Authentification -----------------------------------------------------
    suspend fun signUp(email: String, password: String, role: String, displayName: String? = null): AuthSession {
        val resp = http.post("$base/auth/v1/signup") {
            commonHeaders(config.anonKey)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("email", email)
                put("password", password)
                put("data", buildJsonObject {
                    put("role", role)
                    if (displayName != null) put("display_name", displayName)
                })
            })
        }
        return resp.decodeOrThrow()
    }

    suspend fun signIn(email: String, password: String): AuthSession {
        val resp = http.post("$base/auth/v1/token?grant_type=password") {
            commonHeaders(config.anonKey)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("email", email)
                put("password", password)
            })
        }
        return resp.decodeOrThrow()
    }

    // --- Edge Functions -------------------------------------------------------
    suspend fun createInvitation(token: String, label: String? = null): CreateInvitationResult =
        callFunction("create-invitation", token, buildJsonObject { if (label != null) put("label", label) })

    suspend fun redeemInvitation(token: String, code: String, deviceName: String, androidVersion: String? = null): RedeemResult =
        callFunction("redeem-invitation", token, buildJsonObject {
            put("code", code); put("device_name", deviceName)
            if (androidVersion != null) put("android_version", androidVersion)
        })

    suspend fun proposeCommitment(token: String, deviceId: String, durationDays: Int, policy: JsonObject? = null): CommitmentResult =
        callFunction("propose-commitment", token, buildJsonObject {
            put("device_id", deviceId); put("duration_days", durationDays)
            if (policy != null) put("policy", policy)
        })

    suspend fun consentCommitment(token: String, commitmentId: String): CommitmentResult =
        callFunction("consent-commitment", token, buildJsonObject { put("commitment_id", commitmentId) })

    suspend fun requestRelease(token: String, commitmentId: String, kind: String, reasonCategory: String? = null): ReleaseRequestDto =
        callFunction("request-release", token, buildJsonObject {
            put("commitment_id", commitmentId); put("kind", kind)
            if (reasonCategory != null) put("reason_category", reasonCategory)
        })

    suspend fun reviewRelease(token: String, releaseId: String, decision: String): JsonElement =
        callFunction("review-release", token, buildJsonObject { put("release_id", releaseId); put("decision", decision) })

    suspend fun cancelRelease(token: String, releaseId: String): JsonElement =
        callFunction("cancel-release", token, buildJsonObject { put("release_id", releaseId) })

    suspend fun sync(token: String, deviceId: String): SyncResult =
        callFunction("sync", token, buildJsonObject { put("device_id", deviceId) })

    suspend fun reportEvent(token: String, deviceId: String, events: List<Triple<String, String, String?>>, stats: List<Triple<String, String, Int>>): JsonElement =
        callFunction("report-event", token, buildJsonObject {
            put("device_id", deviceId)
            put("events", buildJsonArray {
                events.forEach { (type, sev, detail) ->
                    add(buildJsonObject { put("event_type", type); put("severity", sev); if (detail != null) put("detail", detail) })
                }
            })
            put("block_stats", buildJsonArray {
                stats.forEach { (day, cat, count) ->
                    add(buildJsonObject { put("day", day); put("category", cat); put("count", count) })
                }
            })
        })

    private suspend inline fun <reified R> callFunction(name: String, token: String, body: JsonObject): R {
        val resp = http.post("$base/functions/v1/$name") {
            commonHeaders(config.anonKey, token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return resp.decodeOrThrow()
    }

    // --- Lectures PostgREST ---------------------------------------------------
    suspend fun listDevices(token: String): List<DeviceDto> =
        selectList("devices", token, "select=*&order=created_at.desc")

    suspend fun listCommitments(token: String): List<CommitmentDto> =
        selectList("commitments", token, "select=*")

    suspend fun listReleaseRequests(token: String): List<ReleaseRequestDto> =
        selectList("release_requests", token, "select=*&status=eq.pending")

    suspend fun listBlockStats(token: String, deviceId: String): List<BlockStatDto> =
        selectList("block_stats_daily", token, "select=day,category,block_count&device_id=eq.$deviceId")

    suspend fun listSecurityEvents(token: String, deviceId: String): List<SecurityEventDto> =
        selectList("security_events", token, "select=event_type,severity,detail,created_at&device_id=eq.$deviceId&order=created_at.desc&limit=50")

    private suspend inline fun <reified R> selectList(table: String, token: String, query: String): List<R> {
        val resp = http.get("$base/rest/v1/$table?$query") {
            commonHeaders(config.anonKey, token)
        }
        return resp.decodeOrThrow()
    }

    // --- Communs --------------------------------------------------------------
    private fun io.ktor.client.request.HttpRequestBuilder.commonHeaders(apiKey: String, token: String? = null) {
        header("apikey", apiKey)
        header("Authorization", "Bearer ${token ?: apiKey}")
    }

    private suspend inline fun <reified R> HttpResponse.decodeOrThrow(): R {
        if (!status.isSuccess()) {
            val text = runCatching { bodyAsText() }.getOrDefault("")
            val message = runCatching { jsonFormat.decodeFromString<ApiError>(text).error }.getOrDefault(text.ifBlank { status.description })
            throw ApiException(status.value, message)
        }
        return body()
    }

    fun close() = http.close()
}
