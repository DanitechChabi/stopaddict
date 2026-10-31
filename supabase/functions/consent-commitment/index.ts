// consent-commitment — l'utilisateur donne son consentement explicite.
// L'administrateur ayant déjà confirmé à la proposition, l'engagement devient
// actif ; start_at/end_at sont fixés côté serveur, puis l'engagement et la
// politique v1 sont signés en Ed25519.

import { guard, json, fail } from "../_shared/http.ts";
import { requireCaller, requireRole, adminClient, HttpError } from "../_shared/clients.ts";
import { sign } from "../_shared/signing.ts";

Deno.serve(async (req) => {
  const pre = guard(req);
  if (pre) return pre;

  try {
    const caller = await requireCaller(req);
    requireRole(caller, "user");

    const body = await req.json().catch(() => ({}));
    const commitmentId = typeof body.commitment_id === "string" ? body.commitment_id : "";
    if (!commitmentId) return fail("missing_commitment_id", 400);

    const admin = adminClient();

    // Activation atomique (ne réussit que si en attente + admin déjà confirmé).
    const { data: c, error } = await admin.rpc("activate_on_user_consent", {
      p_commitment_id: commitmentId,
      p_user_id: caller.id,
    });
    if (error || !c) return fail("commitment_not_activatable", 409);

    // Signature de l'engagement sur sa forme canonique.
    const commitmentPayload = {
      id: c.id,
      device_id: c.device_id,
      user_id: c.user_id,
      admin_id: c.admin_id,
      duration_days: c.duration_days,
      start_at: c.start_at,
      end_at: c.end_at,
      status: c.status,
    };
    const signed = await sign(commitmentPayload);

    // Signature de la version 1 de la politique.
    const { data: policy } = await admin
      .from("protection_policies")
      .select("dns_filter_enabled, image_filter_enabled, video_filter_enabled, text_filter_enabled, strictness_level, blocked_categories")
      .eq("commitment_id", c.id)
      .single();
    const policyPayload = { commitment_id: c.id, version: 1, ...(policy ?? {}) };
    const policySigned = await sign(policyPayload);

    await admin.rpc("set_commitment_signature", {
      p_commitment_id: c.id,
      p_signed_payload: signed.signedPayload,
      p_signature: signed.signature,
      p_policy_payload: policyPayload,
      p_policy_signature: policySigned.signature,
      p_actor: caller.id,
    });

    return json({
      commitment_id: c.id,
      status: c.status,
      start_at: c.start_at,
      end_at: c.end_at,
    });
  } catch (e) {
    if (e instanceof HttpError) return fail(e.message, e.status);
    return fail("unexpected_error", 500);
  }
});
