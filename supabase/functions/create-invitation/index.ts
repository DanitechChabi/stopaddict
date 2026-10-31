// create-invitation — l'administrateur génère un code d'association à usage unique.
// Le code clair n'est renvoyé qu'ici (à afficher en QR / à l'écran). Le serveur
// n'en garde que le hash.

import { guard, json, fail } from "../_shared/http.ts";
import { requireCaller, requireRole, adminClient, HttpError } from "../_shared/clients.ts";
import { generateCode, hashCode } from "../_shared/codes.ts";

Deno.serve(async (req) => {
  const pre = guard(req);
  if (pre) return pre;

  try {
    const caller = await requireCaller(req);
    requireRole(caller, "admin");

    const body = await req.json().catch(() => ({}));
    const label: string | null = typeof body.label === "string" ? body.label : null;

    const ttlMinutes = Number(Deno.env.get("INVITATION_TTL_MINUTES") ?? "15");
    const expiresAt = new Date(Date.now() + ttlMinutes * 60_000).toISOString();

    const code = generateCode();
    const codeHash = await hashCode(code);

    const admin = adminClient();
    const { data, error } = await admin
      .from("admin_invitations")
      .insert({
        admin_id: caller.id,
        invitation_code_hash: codeHash,
        label,
        expires_at: expiresAt,
        status: "pending",
      })
      .select("id, expires_at")
      .single();

    if (error) throw new HttpError("insert_failed", 500);

    // Le code CLAIR n'est renvoyé qu'ici, une seule fois.
    return json({ invitation_id: data.id, code, expires_at: data.expires_at });
  } catch (e) {
    if (e instanceof HttpError) return fail(e.message, e.status);
    return fail("unexpected_error", 500);
  }
});
