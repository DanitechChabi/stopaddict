// Fabrique les clients Supabase et identifie l'appelant.
//
// SUPABASE_URL et SUPABASE_SERVICE_ROLE_KEY sont injectées automatiquement dans
// les Edge Functions par Supabase. La clé service_role ne quitte jamais le
// serveur : elle n'est utilisée que pour les écritures validées côté serveur.

import { createClient, SupabaseClient } from "jsr:@supabase/supabase-js@2";

const url = Deno.env.get("SUPABASE_URL")!;
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;

/** Client administrateur (service_role). Contourne RLS. Écritures serveur uniquement. */
export function adminClient(): SupabaseClient {
  return createClient(url, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
}

export interface Caller {
  id: string;
  email: string;
  role: "user" | "admin";
}

/**
 * Valide le JWT de l'appelant et renvoie son profil.
 * Le token est vérifié côté serveur ; on ne fait pas confiance à un id fourni
 * dans le corps de la requête.
 */
export async function requireCaller(req: Request): Promise<Caller> {
  const authHeader = req.headers.get("Authorization") ?? "";
  const token = authHeader.replace(/^Bearer\s+/i, "").trim();
  if (!token) throw new HttpError("missing_token", 401);

  const admin = adminClient();
  const { data, error } = await admin.auth.getUser(token);
  if (error || !data.user) throw new HttpError("invalid_token", 401);

  const { data: profile, error: pErr } = await admin
    .from("profiles")
    .select("id, email, role")
    .eq("id", data.user.id)
    .single();
  if (pErr || !profile) throw new HttpError("profile_not_found", 403);

  return profile as Caller;
}

export function requireRole(caller: Caller, role: "user" | "admin"): void {
  if (caller.role !== role) throw new HttpError("forbidden_role", 403);
}

export { anonKey };

/** Erreur portant un code HTTP, convertie en réponse JSON par les handlers. */
export class HttpError extends Error {
  constructor(message: string, readonly status = 400) {
    super(message);
  }
}
