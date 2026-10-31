// cancel-release — l'utilisateur annule sa propre demande de libération d'urgence
// pendant le délai de réflexion. La libération automatique n'a alors pas lieu.

import { guard, json, fail } from "../_shared/http.ts";
import { requireCaller, requireRole, adminClient, HttpError } from "../_shared/clients.ts";

Deno.serve(async (req) => {
  const pre = guard(req);
  if (pre) return pre;

  try {
    const caller = await requireCaller(req);
    requireRole(caller, "user");

    const body = await req.json().catch(() => ({}));
    const releaseId = typeof body.release_id === "string" ? body.release_id : "";
    if (!releaseId) return fail("missing_release_id", 400);

    const admin = adminClient();

    const { data: rr, error } = await admin
      .from("release_requests")
      .select("id, status, requested_by, auto_release_at")
      .eq("id", releaseId)
      .single();
    if (error || !rr) return fail("release_not_found", 404);
    if (rr.requested_by !== caller.id) return fail("not_your_request", 403);
    if (rr.status !== "pending") return fail("already_resolved", 409);

    // Annulation : la demande ne déclenchera pas de libération automatique.
    await admin
      .from("release_requests")
      .update({ status: "refused", reviewed_at: new Date().toISOString(), reviewed_by: caller.id, auto_release_at: null })
      .eq("id", releaseId);

    return json({ release_id: releaseId, status: "cancelled" });
  } catch (e) {
    if (e instanceof HttpError) return fail(e.message, e.status);
    return fail("unexpected_error", 500);
  }
});
