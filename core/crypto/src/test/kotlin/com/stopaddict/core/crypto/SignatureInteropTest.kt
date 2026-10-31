package com.stopaddict.core.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Interopérabilité serveur → app. Le vecteur ci-dessous a été produit par la MÊME
 * logique que le serveur (WebCrypto Ed25519 + JSON canonique de canonical.ts).
 * Ce test prouve que la vérification côté Kotlin accepte une signature serveur et
 * rejette toute altération.
 */
class SignatureInteropTest {

    // Vecteur généré par WebCrypto (voir signing.ts / canonical.ts).
    private val spki = "MCowBQYDK2VwAyEAjPTgiNHLvluoAER+Ab54RojLE1LelSdgF/5OfYNHKqU="
    private val signature = "OipN2pHXgnAuGDHTXEOFypKSLG52rACFXQ16qJHokglFvoOlW4XeK1gc94LaoPZ3kUTu+QkkrfeE6mEyPnMKBQ=="
    private val expectedCanonical =
        """{"admin_id":"a1","device_id":"d1","duration_days":90,"end_at":"2027-04-10T00:00:00.000Z","id":"cccccccc","start_at":"2027-01-10T00:00:00.000Z","status":"active","user_id":"u1"}"""

    // Le même payload, dans un ordre d'insertion volontairement différent.
    private val payload: Map<String, Any?> = linkedMapOf(
        "id" to "cccccccc",
        "device_id" to "d1",
        "user_id" to "u1",
        "admin_id" to "a1",
        "duration_days" to 90,
        "start_at" to "2027-01-10T00:00:00.000Z",
        "end_at" to "2027-04-10T00:00:00.000Z",
        "status" to "active",
    )

    @Test
    fun `le JSON canonique Kotlin correspond octet pour octet au serveur`() {
        assertEquals(expectedCanonical, CanonicalJson.stringify(payload))
    }

    @Test
    fun `une signature serveur valide est acceptée`() {
        assertTrue(Ed25519Verifier.verifyPayload(spki, payload, signature))
    }

    @Test
    fun `un payload altéré (durée changée) est rejeté`() {
        val tampered = payload.toMutableMap().apply { this["duration_days"] = 7 }
        assertFalse(Ed25519Verifier.verifyPayload(spki, tampered, signature))
    }

    @Test
    fun `une signature altérée est rejetée`() {
        val badSig = signature.replaceFirst('O', 'P')
        assertFalse(Ed25519Verifier.verifyPayload(spki, payload, badSig))
    }
}
