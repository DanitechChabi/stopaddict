// Signature Ed25519 des engagements et des politiques, via Web Crypto.
//
// La clé privée (PKCS8, base64) est un secret serveur (SIGNING_PRIVATE_KEY_PKCS8_B64).
// L'application n'embarque que la clé publique (SPKI, base64) pour vérifier.

import { canonicalize } from "./canonical.ts";

function b64ToBytes(b64: string): Uint8Array {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function bytesToB64(bytes: Uint8Array): string {
  let bin = "";
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin);
}

let privateKeyPromise: Promise<CryptoKey> | null = null;

function loadPrivateKey(): Promise<CryptoKey> {
  if (!privateKeyPromise) {
    const b64 = Deno.env.get("SIGNING_PRIVATE_KEY_PKCS8_B64");
    if (!b64) throw new Error("SIGNING_PRIVATE_KEY_PKCS8_B64 manquante");
    privateKeyPromise = crypto.subtle.importKey(
      "pkcs8",
      b64ToBytes(b64),
      { name: "Ed25519" },
      false,
      ["sign"],
    );
  }
  return privateKeyPromise;
}

/**
 * Signe la forme canonique de `payload` et renvoie
 * { signedPayload, signature } où signature est en base64.
 */
export async function sign(
  payload: Record<string, unknown>,
): Promise<{ signedPayload: string; signature: string }> {
  const key = await loadPrivateKey();
  const signedPayload = canonicalize(payload);
  const sig = await crypto.subtle.sign(
    { name: "Ed25519" },
    key,
    new TextEncoder().encode(signedPayload),
  );
  return { signedPayload, signature: bytesToB64(new Uint8Array(sig)) };
}

/** Vérifie une signature (utilisé par les tests ; l'app fait l'équivalent en Kotlin). */
export async function verify(
  publicKeySpkiB64: string,
  signedPayload: string,
  signatureB64: string,
): Promise<boolean> {
  const pub = await crypto.subtle.importKey(
    "spki",
    b64ToBytes(publicKeySpkiB64),
    { name: "Ed25519" },
    false,
    ["verify"],
  );
  return crypto.subtle.verify(
    { name: "Ed25519" },
    pub,
    b64ToBytes(signatureB64),
    new TextEncoder().encode(signedPayload),
  );
}
