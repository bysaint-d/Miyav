// Sends queued notifications to phones. A statement trigger on public.notifications calls this after each
// insert; it claims small batches with claim_push_batch() and delivers them through FCM HTTP v1 (native
// Android, token platform "fcm") and the Expo push service (the Expo app, platform "expo").
//
// Calling it only flushes notifications that are already queued for their own recipients, so it needs no
// caller identity. FCM delivery needs the FCM_SERVICE_ACCOUNT secret (the Firebase service account JSON);
// without it FCM devices are skipped and the in-app list and the Android background check still show them.
import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const MAX_ROUNDS = 20;
const BATCH = 5;

type Row = {
  id: string;
  user_id: string;
  title: string;
  body: string;
  conversation_id: string | null;
  kind: string | null;
  created_at: string;
};

type Device = { token: string; user_id: string; platform: string };

type ServiceAccount = { client_email: string; private_key: string; project_id: string };

const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  auth: { persistSession: false, autoRefreshToken: false },
});

let googleToken: { value: string; expires: number } | null = null;

function reply(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function serviceAccount(): ServiceAccount | null {
  const raw = Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw);
    return parsed.client_email && parsed.private_key && parsed.project_id ? parsed : null;
  } catch {
    return null;
  }
}

function base64url(bytes: Uint8Array): string {
  let text = "";
  for (const byte of bytes) {
    text += String.fromCharCode(byte);
  }
  return btoa(text).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function pemToDer(pem: string): ArrayBuffer {
  const body = pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const binary = atob(body);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

async function accessToken(account: ServiceAccount): Promise<string> {
  if (googleToken && googleToken.expires > Date.now() + 60_000) {
    return googleToken.value;
  }
  const now = Math.floor(Date.now() / 1000);
  const encode = (value: unknown) => base64url(new TextEncoder().encode(JSON.stringify(value)));
  const unsigned = `${encode({ alg: "RS256", typ: "JWT" })}.${encode({
    iss: account.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  })}`;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToDer(account.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)));
  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${unsigned}.${base64url(signature)}`,
    }),
  });
  if (!response.ok) {
    throw new Error(`google oauth ${response.status}`);
  }
  const json = await response.json();
  googleToken = { value: json.access_token, expires: Date.now() + Number(json.expires_in ?? 3600) * 1000 };
  return googleToken.value;
}

/** True when delivered or the token is gone for good; false on a transient failure worth retrying. */
async function sendFcm(account: ServiceAccount, device: Device, row: Row): Promise<boolean> {
  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${await accessToken(account)}` },
    body: JSON.stringify({
      message: {
        token: device.token,
        data: {
          id: row.id,
          title: row.title,
          body: row.body,
          kind: row.kind ?? "",
          conversation_id: row.conversation_id ?? "",
          created_at: row.created_at,
        },
        android: { priority: "HIGH", ttl: "86400s" },
      },
    }),
  });
  if (response.ok) {
    return true;
  }
  const text = await response.text();
  if (response.status === 404 || text.includes("UNREGISTERED") || text.includes("registration-token-not-registered")) {
    await admin.from("push_devices").delete().eq("token", device.token);
    return true;
  }
  if (response.status === 400) {
    console.warn("fcm rejected", device.token.slice(0, 12), text.slice(0, 200));
    return true;
  }
  return false;
}

async function sendExpo(devices: Device[], row: Row): Promise<boolean> {
  if (devices.length === 0) {
    return true;
  }
  const response = await fetch("https://exp.host/--/api/v2/push/send", {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify(devices.map((device) => ({
      to: device.token,
      title: row.title,
      body: row.body,
      sound: "default",
      data: { notificationId: row.id, conversationId: row.conversation_id, kind: row.kind },
    }))),
  });
  if (!response.ok) {
    return response.status < 500;
  }
  const json = await response.json().catch(() => ({ data: [] }));
  const results: Array<{ status?: string; details?: { error?: string } }> = Array.isArray(json.data) ? json.data : [];
  for (let i = 0; i < results.length; i++) {
    if (results[i]?.details?.error === "DeviceNotRegistered") {
      await admin.from("push_devices").delete().eq("token", devices[i].token);
    }
  }
  return true;
}

async function deliver(row: Row, devices: Device[], account: ServiceAccount | null): Promise<boolean> {
  let done = await sendExpo(devices.filter((device) => device.platform === "expo"), row);
  if (account) {
    for (const device of devices.filter((item) => item.platform === "fcm")) {
      try {
        done = (await sendFcm(account, device, row)) && done;
      } catch (error) {
        console.error("fcm", error instanceof Error ? error.message : error);
        done = false;
      }
    }
  }
  return done;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return reply(405, { code: "method.not_allowed" });
  }
  const account = serviceAccount();
  let sent = 0;
  let retry = 0;
  for (let round = 0; round < MAX_ROUNDS; round++) {
    const { data, error } = await admin.rpc("claim_push_batch", { p_limit: BATCH });
    if (error) {
      return reply(500, { code: "claim.failed", message: error.message });
    }
    const rows = (data ?? []) as Row[];
    if (rows.length === 0) {
      break;
    }
    const users = [...new Set(rows.map((row) => row.user_id))];
    const { data: deviceRows } = await admin.from("push_devices").select("token,user_id,platform").in("user_id", users);
    const devices = (deviceRows ?? []) as Device[];
    for (const row of rows) {
      const ok = await deliver(row, devices.filter((device) => device.user_id === row.user_id), account);
      if (ok) {
        await admin.from("notifications").update({ push_sent_at: new Date().toISOString() }).eq("id", row.id);
        sent++;
      } else {
        retry++;
      }
    }
  }
  return reply(200, { sent, retry, fcm: account !== null });
});
