// report-event — l'appareil remonte des événements techniques et des compteurs
// de blocages AGRÉGÉS. Jamais de contenu : ni URL, ni image, ni message.
// L'administrateur ne verra que des nombres par catégorie et des alertes techniques.

import { guard, json, fail } from "../_shared/http.ts";
import { requireCaller, requireRole, adminClient, HttpError } from "../_shared/clients.ts";

const SEVERITIES = new Set(["info", "warning", "critical"]);

interface EventInput {
  event_type?: string;
  severity?: string;
  detail?: string;
}
interface StatInput {
  day?: string;
  category?: string;
  count?: number;
}

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

    const events: EventInput[] = Array.isArray(body.events) ? body.events : [];
    const stats: StatInput[] = Array.isArray(body.block_stats) ? body.block_stats : [];

    // Événements techniques (on ne conserve que des champs non sensibles).
    const rows = events
      .filter((e) => typeof e.event_type === "string")
      .map((e) => ({
        device_id: deviceId,
        event_type: e.event_type as string,
        severity: SEVERITIES.has(e.severity ?? "") ? e.severity : "info",
        detail: typeof e.detail === "string" ? e.detail.slice(0, 500) : null,
      }));
    if (rows.length > 0) {
      await admin.from("security_events").insert(rows);
    }

    // Compteurs agrégés (incréments atomiques par jour et catégorie).
    for (const s of stats) {
      if (typeof s.category !== "string") continue;
      const day = typeof s.day === "string" ? s.day : new Date().toISOString().slice(0, 10);
      const count = Number.isFinite(s.count) ? Math.max(0, Math.trunc(s.count as number)) : 0;
      if (count === 0) continue;
      await admin.rpc("bump_block_stats", {
        p_device_id: deviceId,
        p_day: day,
        p_category: s.category,
        p_count: count,
      });
    }

    await admin.from("devices").update({ last_sync_at: new Date().toISOString() }).eq("id", deviceId);

    return json({ ok: true, events: rows.length, stats: stats.length });
  } catch (e) {
    if (e instanceof HttpError) return fail(e.message, e.status);
    return fail("unexpected_error", 500);
  }
});
