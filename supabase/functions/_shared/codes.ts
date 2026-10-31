// Génération et hachage des codes d'association.
// Le serveur ne stocke que le hash ; le code clair ne vit que dans le QR / l'écran.

// Alphabet sans caractères ambigus (ni 0/O, ni 1/I/L).
const ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
const CODE_LENGTH = 8;

export function generateCode(): string {
  const bytes = new Uint8Array(CODE_LENGTH);
  crypto.getRandomValues(bytes);
  let out = "";
  for (let i = 0; i < CODE_LENGTH; i++) {
    out += ALPHABET[bytes[i] % ALPHABET.length];
  }
  return out;
}

/** Normalise (majuscules, sans espaces) puis hache en SHA-256 hex. */
export async function hashCode(code: string): Promise<string> {
  const normalized = code.trim().toUpperCase().replace(/\s+/g, "");
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(normalized),
  );
  return [...new Uint8Array(digest)]
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}
