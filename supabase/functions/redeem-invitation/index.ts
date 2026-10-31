// redeem-invitation — l'utilisateur protégé consomme un code et associe son appareil.
// L'appareil est créé en statut 'pending'. L'identité de l'administrateur est
// renvoyée pour que l'utilisateur la confirme avant de poursuivre.

import { guard, json, fail } from "../_shared/http.ts";
import { requireCaller, requireRole, adminClient, HttpError } from "../_shared/clients.ts";
import { hashCode } from "../_shared/codes.ts";

Deno.serve(async (req) => {
  const pre = guard(req);
  if (pre) return pre;

  try {
    const caller = await requireCaller(req);
    requireRole(caller, "user");

    const body = await req.json().catch(() => ({}));
    const code = typeof body.code === "string" ? body.code : "";
    const deviceName = typeof body.device_name === "string" ? body.device_name : "Appareil";
    const androidVersion = typeof body.android_version === "string" ? body.android_version : null;
    if (!code) return fail("missing_code", 400);

    const admin = adminClient();
    const codeHash = await hashCode(code);

    const { data, error } = await admin.rpc("redeem_invitation", {
      p_code_hash: codeHash,
      p_user_id: caller.id,
      p_device_name: deviceName,
      p_android_version: androidVersion,
    });
    if (error || !data || data.length === 0) return fail("invitation_invalid", 400);

    const { device_id, admin_id } = data[0];

    // Identité de l'administrateur, à confirmer côté utilisateur.
    const { data: adminProfile } = await admin
      .from("profiles")
      .select("display_name, email")
      .eq("id", admin_id)
      .single();

    return json({
      device_id,
      admin: {
        display_name: adminProfile?.display_name ?? null,
        email: adminProfile?.email ?? null,
      },
    });
  } catch (e) {
    if (e instanceof HttpError) return fail(e.message, e.status);
    return fail("unexpected_error", 500);
  }
});
