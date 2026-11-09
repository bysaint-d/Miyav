// Deletes the caller's account: their storage files, every row they own, and the auth user.
// The caller is identified only by their own access token; nobody can delete another account here.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const headers = {
  "Content-Type": "application/json",
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function reply(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), { status, headers });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers });
  }
  if (req.method !== "POST") {
    return reply(405, { code: "method.not_allowed" });
  }
  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "").trim();
  if (!token) {
    return reply(401, { code: "auth.required" });
  }

  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: who, error: whoError } = await admin.auth.getUser(token);
  if (whoError || !who?.user) {
    return reply(401, { code: "auth.session_expired" });
  }
  const userId = who.user.id;

  const { data: files, error: filesError } = await admin.rpc("account_files", { p_user: userId });
  if (filesError) {
    console.error("account_files", filesError.message);
    return reply(500, { code: "account.delete_failed" });
  }

  const { error: dataError } = await admin.rpc("delete_account_data", { p_user: userId });
  if (dataError) {
    if (dataError.message.includes("ACCOUNT_ADMIN")) {
      return reply(403, { code: "account.admin" });
    }
    if (dataError.message.includes("ACCOUNT_NOT_FOUND")) {
      return reply(401, { code: "auth.session_expired" });
    }
    console.error("delete_account_data", dataError.message);
    return reply(500, { code: "account.delete_failed" });
  }

  // The account is gone at this point; leftover files are logged, never shown to the user.
  const byBucket = new Map<string, string[]>();
  for (const file of (files ?? []) as { bucket_id: string; name: string }[]) {
    const names = byBucket.get(file.bucket_id) ?? [];
    names.push(file.name);
    byBucket.set(file.bucket_id, names);
  }
  for (const [bucket, names] of byBucket) {
    for (let start = 0; start < names.length; start += 100) {
      const { error } = await admin.storage.from(bucket).remove(names.slice(start, start + 100));
      if (error) {
        console.error("storage.remove", bucket, error.message);
      }
    }
  }

  return reply(200, { deleted: true });
});
