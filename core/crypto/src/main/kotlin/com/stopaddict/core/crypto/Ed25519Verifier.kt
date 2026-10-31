package com.stopaddict.core.crypto

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Vérifie une signature Ed25519 produite par le serveur (voir
 * `supabase/functions/_shared/signing.ts`). L'app n'embarque que la clé PUBLIQUE
 * (SPKI, base64) ; elle ne signe jamais, elle vérifie seulement.
 *
 * Disponible sans Android : utilise `java.security` (Ed25519 standard depuis JDK 15).
 */
object Ed25519Verifier {

    private val b64 = Base64.getDecoder()

    /** Vérifie que `signatureB64` couvre bien `message` pour la clé publique donnée. */
    fun verify(publicKeySpkiB64: String, message: ByteArray, signatureB64: String): Boolean {
        val publicKey = KeyFactory.getInstance("Ed25519")
            .generatePublic(X509EncodedKeySpec(b64.decode(publicKeySpkiB64)))
        return Signature.getInstance("Ed25519").run {
            initVerify(publicKey)
            update(message)
            verify(b64.decode(signatureB64))
        }
    }

    /** Variante pratique : vérifie la forme canonique d'un payload structuré. */
    fun verifyPayload(publicKeySpkiB64: String, payload: Map<String, Any?>, signatureB64: String): Boolean =
        verify(publicKeySpkiB64, CanonicalJson.stringify(payload).toByteArray(Charsets.UTF_8), signatureB64)
}
