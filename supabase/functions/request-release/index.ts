// request-release — l'utilisateur demande une libération anticipée.
//
//  - 'standard'  : l'administrateur décide (approve / refuse / needs_confirmation).
//  - 'emergency' : procédure indépendante de l'administrateur (sécurité, perte,
//                  changement de propriétaire). L'administrateur est alerté
//                  immédiatement, et la libération devient automatique après le
//                  délai de réflexion (RELEASE_DELAY_HOURS, 72 h par défaut),
//                  sauf annulation par l'utilisateur (cancel-release).

import { guard, json, fail } from "../_shared/http.ts";
import { requireCaller, requireRole, adminClient, HttpError } from "../_shared/clients.ts";

Deno.serve(async (req) => {
  const pre = guard(req);
  if (pre) return pre;

  try {
    const caller = await requireCaller(req);
    requireRole(caller, "user");

    const body = await req.json().catch(() => ({}));
    const commitmentId = typeof body.commitment_id === "string" ? body.commitment_id : "";
    const kind = body.kind === "emergency" ? "emergency" : "standard";
    const reasonCategory = typeof body.reason_category === "string" ? body.reason_category : null;
    if (!commitmentId) return fail("missing_commitment_id", 400);

    const admin = adminClient();

    // L'engagement doit être actif et appartenir à l'appelant.
    const { data: c, error: cErr } = await admin
      .from("commitments")
      .select("id, device_id, user_id, status")
      .eq("id", commitmentId)
      .single();
    if (cErr || !c) return fail("commitment_not_found", 404);
    if (c.user_id !== caller.id) return fail("not_your_commitment", 403);
    if (c.status !== "active") return fail("commitment_not_active", 409);

    let autoReleaseAt: string | null = null;
    if (kind === "emergency") {
      const hours = Number(Deno.env.get("RELEASE_DELAY_HOURS") ?? "72");
      autoReleaseAt = new Date(Date.now() + hours * 3_600_000).toISOString();
    }

    const { data: rr, error: rErr } = await admin
      .from("release_requests")
      .insert({
        commitment_id: commitmentId,
        requested_by: caller.id,
        kind,
        reason_category: reasonCategory,
        status: "pending",
        auto_release_at: autoReleaseAt,
      })
      .select("id, kind, status, auto_release_at")
      .single();
    if (rErr || !rr) throw new HttpError("insert_failed", 500);

    // Alerte technique pour l'administrateur (aucun contenu privé).
    await admin.from("security_events").insert({
      device_id: c.device_id,
      event_type: kind === "emergency" ? "release_request_emergency" : "release_request_standard",
      severity: kind === "emergency" ? "warning" : "info",
      detail: reasonCategory,
    });

    return json(rr);
  } catch (e) {
    if (e instanceof HttpError) return fail(e.message, e.status);
    return fail("unexpected_error", 500);
  }
});
