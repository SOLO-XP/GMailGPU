import { createClient } from "npm:@supabase/supabase-js@2";
import { JWT } from "npm:google-auth-library@9.15.1";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return response({ error: "POST required" }, 405);

  try {
    const authorization = req.headers.get("Authorization") ?? "";
    if (!authorization.startsWith("Bearer ")) return response({ error: "Sign-in required" }, 401);

    const url = Deno.env.get("SUPABASE_URL");
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY");
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    const accountJson = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON");
    if (!url || !anonKey || !serviceKey) return response({ error: "Server configuration missing" }, 500);
    if (!accountJson) return response({ error: "Push delivery is not configured yet" }, 503);

    const accessToken = authorization.slice("Bearer ".length);
    const userClient = createClient(url, anonKey, {
      global: { headers: { Authorization: `Bearer ${accessToken}` } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: { user }, error: authError } = await userClient.auth.getUser();
    if (authError || !user) return response({ error: "Invalid session" }, 401);

    const body = await req.json();
    const messageId = String(body.message_id ?? "");
    if (!/^[0-9a-f-]{36}$/i.test(messageId)) return response({ error: "Invalid message id" }, 400);

    const admin = createClient(url, serviceKey, {
      auth: { persistSession: false, autoRefreshToken: false },
    });
    const { data: message, error: messageError } = await admin
      .from("issue_messages")
      .select("id, issue_id, sender_id, body")
      .eq("id", messageId)
      .maybeSingle();
    if (messageError || !message) return response({ error: "Message not found" }, 404);
    if (message.sender_id !== user.id) return response({ error: "You can notify only from your own new message" }, 403);

    const [{ data: senderProfile }, { data: issue }] = await Promise.all([
      admin.from("profiles").select("username, role").eq("id", user.id).maybeSingle(),
      admin.from("issues").select("id, issue_number, title, user_id").eq("id", message.issue_id).maybeSingle(),
    ]);
    if (!senderProfile || !issue) return response({ error: "Issue or profile not found" }, 404);

    let recipientIds: string[] = [];
    if (senderProfile.role === "admin") {
      if (issue.user_id !== user.id) recipientIds = [issue.user_id];
    } else {
      const { data: admins } = await admin.from("profiles").select("id").eq("role", "admin");
      recipientIds = (admins ?? []).map((item: { id: string }) => item.id).filter((id: string) => id !== user.id);
    }
    recipientIds = [...new Set(recipientIds)];
    if (recipientIds.length === 0) return response({ sent: 0, reason: "No recipient" }, 200);

    const { data: tokens, error: tokenError } = await admin
      .from("device_push_tokens")
      .select("token, user_id")
      .in("user_id", recipientIds);
    if (tokenError) return response({ error: "Could not load recipient devices" }, 500);
    if (!tokens?.length) return response({ sent: 0, reason: "No registered push devices" }, 200);

    const serviceAccount = JSON.parse(accountJson);
    const projectId = serviceAccount.project_id;
    if (!projectId || !serviceAccount.client_email || !serviceAccount.private_key) {
      return response({ error: "Invalid Firebase service account configuration" }, 500);
    }
    const googleJwt = new JWT({
      email: serviceAccount.client_email,
      key: serviceAccount.private_key,
      scopes: ["https://www.googleapis.com/auth/firebase.messaging"],
    });
    const authResult = await googleJwt.getAccessToken();
    const googleAccessToken = typeof authResult === "string" ? authResult : authResult?.token;
    if (!googleAccessToken) return response({ error: "Could not authorize with Firebase" }, 502);

    const title = senderProfile.role === "admin"
      ? `Developer replied • Issue #${issue.issue_number}`
      : `New user reply • Issue #${issue.issue_number}`;
    const preview = String(message.body ?? "").replace(/\s+/g, " ").slice(0, 150);
    const notificationBody = `${senderProfile.username}: ${preview}`;

    const results = await Promise.all(tokens.map(async (item: { token: string; user_id: string }) => {
      const fcmResponse = await fetch(
        `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
        {
          method: "POST",
          headers: {
            Authorization: `Bearer ${googleAccessToken}`,
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            message: {
              token: item.token,
              notification: { title, body: notificationBody },
              data: {
                issue_id: String(issue.id),
                notification_title: title,
                notification_body: notificationBody,
              },
              android: { priority: "HIGH" },
            },
          }),
        },
      );
      if (!fcmResponse.ok) {
        const errorText = await fcmResponse.text();
        if (fcmResponse.status === 404 || errorText.includes("UNREGISTERED")) {
          await admin.from("device_push_tokens").delete().eq("token", item.token);
        }
        return { ok: false, user_id: item.user_id, status: fcmResponse.status };
      }
      return { ok: true, user_id: item.user_id };
    }));
    return response({ sent: results.filter((item) => item.ok).length, devices: results.length }, 200);
  } catch (_error) {
    // Never return secrets, access tokens or raw Firebase credentials to the app.
    return response({ error: "Push request failed" }, 500);
  }
});

function response(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}
