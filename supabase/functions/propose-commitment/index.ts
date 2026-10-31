// propose-commitment — l'administrateur propose un engagement pour un appareil
// qu'il supervise. L'engagement reste 'pending_consent' : il ne devient actif
// qu'après le consentement explicite de l'utilisateur (consent-commitment).

import { guard, json, fail } from "../_shared/http.ts";
import { requireCaller, requireRole, adminClient, HttpError } from "../_shared/clients.ts";

interface PolicyInput {
  dns_filter_enabled?: boolean;
  image_filter_enabled?: boolean;
  video_filter_enabled?: boolean;
  text_filter_enabled?: boolean;
  strictness_level?: number;
  blocked_categories?: string[];
}

Deno.serve(async (req) => {
  const pre = guard(req);
  if (pre) return pre;

  try {
    const caller = await requireCaller(req);
    requireRole(caller, "admin");

    const body = await req.json().catch(() => ({}));
    const deviceId = typeof body.device_id === "string" ? body.device_id : "";
    const durationDays = Number(body.duration_days);
    const policy: PolicyInput = body.policy ?? {};
    if (!deviceId) return fail("missing_device_id", 400);
    if (!Number.isInteger(durationDays) || durationDays < 1 || durationDays > 366) {
      return fail("invalid_duration", 400);
    }

    const admin = adminClient();

    // L'appareil doit être supervisé par cet administrateur.
    const { data: device, error: dErr } = await admin
      .from("devices")
      .select("id, user_id, admin_id")
      .eq("id", deviceId)
      .single();
    if (dErr || !device) return fail("device_not_found", 404);
    if (device.admin_id !== caller.id) return fail("not_your_device", 403);

    const { data: commitment, error: cErr } = await admin
      .from("commitments")
      .insert({
        device_id: deviceId,
        admin_id: caller.id,
        user_id: device.user_id,
        duration_days: durationDays,
        status: "pending_consent",
        admin_confirmation_at: new Date().toISOString(),
      })
      .select("id, duration_days, status")
      .single();
    // 23505 = violation d'unicité : un engagement vit déjà pour cet appareil.
    if (cErr?.code === "23505") return fail("commitment_already_live", 409);
    if (cErr || !commitment) throw new HttpError("insert_failed", 500);

    await admin.from("protection_policies").insert({
      commitment_id: commitment.id,
      dns_filter_enabled: policy.dns_filter_enabled ?? true,
      image_filter_enabled: policy.image_filter_enabled ?? false,
      video_filter_enabled: policy.video_filter_enabled ?? false,
      text_filter_enabled: policy.text_filter_enabled ?? false,
      strictness_level: policy.strictness_level ?? 2,
      blocked_categories: policy.blocked_categories ?? ["pornography", "adult_sites"],
    });

    return json({ commitment_id: commitment.id, status: commitment.status });
  } catch (e) {
    if (e instanceof HttpError) return fail(e.message, e.status);
    return fail("unexpected_error", 500);
  }
});
