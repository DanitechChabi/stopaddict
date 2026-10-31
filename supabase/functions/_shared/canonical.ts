// Sérialisation JSON canonique : clés triées récursivement, pas d'espaces.
// Indispensable pour que la signature serveur et la vérification côté app
// portent sur EXACTEMENT les mêmes octets.

export function canonicalize(value: unknown): string {
  if (value === null || typeof value !== "object") {
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) {
    return "[" + value.map(canonicalize).join(",") + "]";
  }
  const obj = value as Record<string, unknown>;
  const keys = Object.keys(obj).sort();
  const parts = keys.map(
    (k) => JSON.stringify(k) + ":" + canonicalize(obj[k]),
  );
  return "{" + parts.join(",") + "}";
}
