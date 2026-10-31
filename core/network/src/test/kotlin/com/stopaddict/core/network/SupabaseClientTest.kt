package com.stopaddict.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SupabaseClientTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val config = SupabaseConfig("https://demo.supabase.co", "anon-key")

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): SupabaseClient =
        SupabaseClient(config, MockEngine(handler))

    @Test
    fun `signIn parse la session`() = runTest {
        val c = client { respond("""{"access_token":"tok123","user":{"id":"u1","email":"a@x.co"}}""", HttpStatusCode.OK, jsonHeaders) }
        val session = c.signIn("a@x.co", "pw")
        assertEquals("tok123", session.accessToken)
        assertEquals("u1", session.user?.id)
    }

    @Test
    fun `createInvitation envoie le token et parse le code`() = runTest {
        var seenAuth: String? = null
        var seenPath: String? = null
        val c = client { req ->
            seenAuth = req.headers[HttpHeaders.Authorization]
            seenPath = req.url.encodedPath
            respond("""{"invitation_id":"inv1","code":"ABCD2345","expires_at":"2027-01-01T00:00:00Z"}""", HttpStatusCode.OK, jsonHeaders)
        }
        val r = c.createInvitation("admintok", label = "Tel de Jean")
        assertEquals("ABCD2345", r.code)
        assertEquals("Bearer admintok", seenAuth)
        assertTrue(seenPath!!.endsWith("/functions/v1/create-invitation"))
    }

    @Test
    fun `listDevices parse la liste`() = runTest {
        val c = client { respond("""[{"id":"d1","device_name":"Tel","device_status":"active","management_mode":"standard","user_id":"u1","admin_id":"a1"}]""", HttpStatusCode.OK, jsonHeaders) }
        val devices = c.listDevices("tok")
        assertEquals(1, devices.size)
        assertEquals("Tel", devices[0].device_name)
    }

    @Test
    fun `une erreur serveur est convertie en ApiException avec le message`() = runTest {
        val c = client { respond("""{"error":"invitation_invalid"}""", HttpStatusCode.BadRequest, jsonHeaders) }
        val ex = assertFailsWith<ApiException> { c.redeemInvitation("tok", "BADCODE", "Tel") }
        assertEquals(400, ex.status)
        assertEquals("invitation_invalid", ex.message)
    }
}
