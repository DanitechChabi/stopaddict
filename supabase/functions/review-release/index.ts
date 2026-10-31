// review-release — l'administrateur statue sur une demande de libération standard.
// 'approved' clôt l'engagement ; 'refused' et 'needs_confirmation' le laissent actif.

import { guard, json, fail } from "../_shared/http.ts";
import { requireCaller, requireRole, adminClient, HttpError } from "../_shared/clients.ts";

const DECISIONS = new Set(["approved", "refused", "needs_confirmation"]);

Deno.serve(async (req) => {
  const pre = guard(req);
  if (pre) return pre;

  try {
    const caller = await requireCaller(req);
    requireRole(caller, "admin");

    const body = await req.json().catch(() => ({}));
    const releaseId = typeof body.release_id === "string" ? body.release_id : "";
    const decision = typeof body.decision === "string" ? body.decision : "";
    if (!releaseId) return fail("missing_release_id", 400);
    if (!DECISIONS.has(decision)) return fail("invalid_decision", 400);

    const admin = adminClient();

    // La demande doit porter sur un engagement supervisé par cet administrateur.
    const { data: rr, error: rErr } = await admin
      .from("release_requests")
      .select("id, commitment_id, status, commitments!inner(admin_id)")
      .eq("id", releaseId)
      .single();
    if (rErr || !rr) return fail("release_not_found", 404);
    // deno-lint-ignore no-explicit-any
    const commitmentAdminId = (rr as any).commitments.admin_id;
    if (commitmentAdminId !== caller.id) return fail("not_your_commitment", 403);
    if (rr.status !== "pending") return fail("already_reviewed", 409);

    await admin
      .from("release_requests")
      .update({ status: decision, reviewed_at: new Date().toISOString(), reviewed_by: caller.id })
      .eq("id", releaseId);

    if (decision === "approved") {
      const { error: relErr } = await admin.rpc("release_commitment", {
        p_commitment_id: rr.commitment_id,
      });
      if (relErr) throw new HttpError("release_failed", 500);
    }

    return json({ release_id: releaseId, decision });
  } catch (e) {
    if (e instanceof HttpError) return fail(e.message, e.status);
    return fail("unexpected_error", 500);
  }
});
