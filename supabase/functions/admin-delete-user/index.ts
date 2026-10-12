import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const OWNER_UID = "8889b1a6-7dbd-4d42-9dcc-b231ddd0c5f2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const adminClient = createClient(supabaseUrl, serviceRoleKey, {
  auth: { autoRefreshToken: false, persistSession: false },
});

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (request.method !== "POST") {
    return Response.json({ error: "Method not allowed" }, { status: 405, headers: corsHeaders });
  }

  try {
    const authorization = request.headers.get("Authorization") ?? "";
    const token = authorization.replace(/^Bearer\s+/i, "");
    if (!token) {
      return Response.json({ error: "Missing user session" }, { status: 401, headers: corsHeaders });
    }

    const { data: callerData, error: callerError } = await adminClient.auth.getUser(token);
    const caller = callerData.user;
    if (callerError || !caller) {
      return Response.json({ error: "Invalid or expired session" }, { status: 401, headers: corsHeaders });
    }

    // Only the fixed owner account may delete user accounts. Admin role alone is not enough.
    if (caller.id !== OWNER_UID) {
      return Response.json({ error: "Forbidden: owner account only" }, { status: 403, headers: corsHeaders });
    }

    const body = await request.json().catch(() => ({}));
    const userId = body?.userId;
    if (typeof userId !== "string" || !/^[0-9a-f-]{36}$/i.test(userId)) {
      return Response.json({ error: "A valid userId is required" }, { status: 400, headers: corsHeaders });
    }
    if (userId === caller.id) {
      return Response.json({ error: "You cannot delete your own admin account" }, { status: 400, headers: corsHeaders });
    }

    const { data: targetProfile, error: targetError } = await adminClient
      .from("profiles")
      .select("role")
      .eq("id", userId)
      .maybeSingle();

    if (targetError) throw targetError;
    if (!targetProfile) {
      return Response.json({ error: "User profile not found" }, { status: 404, headers: corsHeaders });
    }
    if (targetProfile.role === "admin") {
      return Response.json({ error: "Admin accounts cannot be deleted from this endpoint" }, { status: 403, headers: corsHeaders });
    }

    const { data: issues, error: issuesError } = await adminClient
      .from("issues")
      .select("id")
      .eq("user_id", userId);
    if (issuesError) throw issuesError;

    const issueIds = (issues ?? []).map((issue) => issue.id as string);
    if (issueIds.length > 0) {
      const { data: attachments, error: attachmentsError } = await adminClient
        .from("attachments")
        .select("storage_path")
        .in("issue_id", issueIds);
      if (attachmentsError) throw attachmentsError;

      const paths = (attachments ?? []).map((item) => item.storage_path as string);
      if (paths.length > 0) {
        const { error: storageError } = await adminClient.storage.from("issue-files").remove(paths);
        if (storageError) throw storageError;
      }
    }

    const { error: deleteError } = await adminClient.auth.admin.deleteUser(userId);
    if (deleteError) throw deleteError;

    return Response.json({ success: true }, { status: 200, headers: corsHeaders });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unexpected server error";
    return Response.json({ error: message }, { status: 500, headers: corsHeaders });
  }
});
