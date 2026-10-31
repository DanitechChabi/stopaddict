// sync — l'appareil récupère son engagement signé et sa politique courante, et
// l'heure SERVEUR qui sert d'ancre temporelle fiable (voir core:model/TrustedTime).
// Enregistre la dernière synchronisation. Ne renvoie aucun contenu privé.

import { guard, json, fail } from "../_shared/http.ts";
import { requireCaller, requireRole, adminClient, HttpError } from "../_shared/clients.ts";

Deno.serve(async (req) => {
  const pre = guard(req);
  if (pre) return pre;

  try {
    const caller = await requireCaller(req);
    requireRole(caller, "user");

    const body = await req.json().catch(() => ({}));
    const deviceId = typeof body.device_id === "string" ? body.device_id : "";
    if (!deviceId) return fail("missing_device_id", 400);

    const admin = adminClient();

    const { data: device, error: dErr } = await admin
      .from("devices")
      .select("id, user_id")
      .eq("id", deviceId)
      .single();
    if (dErr || !device) return fail("device_not_found", 404);
    if (device.user_id !== caller.id) return fail("not_your_device", 403);

    // Engagement vivant (actif ou en attente de consentement).
    const { data: commitment } = await admin
      .from("commitments")
      .select("id, status, start_at, end_at, duration_days, signed_payload, signature")
      .eq("device_id", deviceId)
      .in("status", ["pending_consent", "active"])
      .maybeSingle();

    let policy = null;
    if (commitment) {
      const { data: pv } = await admin
        .from("policy_versions")
        .select("version, payload, signature")
        .eq("commitment_id", commitment.id)
        .order("version", { ascending: false })
        .limit(1)
        .maybeSingle();
      policy = pv;
    }

    const serverNow = new Date().toISOString();

    await admin.from("devices").update({ last_sync_at: serverNow }).eq("id", deviceId);
    await admin.from("device_sync").upsert(
      {
        device_id: deviceId,
        last_policy_version: policy?.version ?? 0,
        sync_status: "ok",
        updated_at: serverNow,
      },
      { onConflict: "device_id" },
    );

    // server_now : l'app l'associe à SystemClock.elapsedRealtime() pour construire
    // une ancre temporelle insensible à l'horloge murale.
    return json({ server_now: serverNow, commitment, policy });
  } catch (e) {
    if (e instanceof HttpError) return fail(e.message, e.status);
    return fail("unexpected_error", 500);
  }
});
