import { createRemoteJWKSet, importPKCS8, jwtVerify, SignJWT } from "jose";

interface Env {
  FIREBASE_PROJECT_ID: string;
  FIREBASE_CLIENT_EMAIL: string;
  FIREBASE_PRIVATE_KEY: string;
}

type ActivityType = "LIKE" | "COMMENT" | "FOLLOW";

interface ActivityRequest {
  type: ActivityType;
  recipientUid: string;
  relatedId: string;
  sourceEventId: string;
}

const firebaseKeys = createRemoteJWKSet(
  new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com")
);

let cachedGoogleAccessToken = "";
let cachedGoogleAccessTokenExpiresAt = 0;

const json = (body: unknown, status = 200): Response =>
  new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "access-control-allow-origin": "*",
      "access-control-allow-headers": "authorization, content-type",
      "access-control-allow-methods": "POST, OPTIONS"
    }
  });

function decodeFirestoreString(value: any): string {
  return typeof value?.stringValue === "string" ? value.stringValue : "";
}

function decodeFirestoreStrings(value: any): string[] {
  return Array.isArray(value?.arrayValue?.values)
    ? value.arrayValue.values.map((item: any) => decodeFirestoreString(item))
    : [];
}

function firestoreRoot(env: Env): string {
  return `https://firestore.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/databases/(default)/documents`;
}

function encodeDocumentPath(path: string): string {
  return path.split("/").map(encodeURIComponent).join("/");
}

async function googleAccessToken(env: Env): Promise<string> {
  if (cachedGoogleAccessToken && Date.now() < cachedGoogleAccessTokenExpiresAt - 60_000) {
    return cachedGoogleAccessToken;
  }

  const privateKey = await importPKCS8(env.FIREBASE_PRIVATE_KEY.replace(/\\n/g, "\n"), "RS256");
  const assertion = await new SignJWT({
    scope: "https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging"
  })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(env.FIREBASE_CLIENT_EMAIL)
    .setSubject(env.FIREBASE_CLIENT_EMAIL)
    .setAudience("https://oauth2.googleapis.com/token")
    .setIssuedAt()
    .setExpirationTime("1h")
    .sign(privateKey);

  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion
    })
  });
  if (!response.ok) throw new Error("No se pudo obtener el token de servicio de Google.");
  const payload = await response.json() as { access_token?: string; expires_in?: number };
  if (!payload.access_token) throw new Error("Google no devolvió un token de servicio.");
  cachedGoogleAccessToken = payload.access_token;
  cachedGoogleAccessTokenExpiresAt = Date.now() + (payload.expires_in ?? 3600) * 1000;
  return cachedGoogleAccessToken;
}

async function firestoreRequest(
  env: Env,
  accessToken: string,
  path: string,
  init: RequestInit = {}
): Promise<Response> {
  const headers = new Headers(init.headers);
  headers.set("authorization", `Bearer ${accessToken}`);
  if (init.body) headers.set("content-type", "application/json");
  return fetch(`${firestoreRoot(env)}/${encodeDocumentPath(path)}`, { ...init, headers });
}

async function readDocument(env: Env, accessToken: string, path: string): Promise<any | null> {
  const response = await firestoreRequest(env, accessToken, path);
  if (response.status === 404) return null;
  if (!response.ok) throw new Error("No se pudo verificar el origen de la actividad.");
  return await response.json();
}

async function verifyFirebaseUser(token: string, env: Env): Promise<string> {
  const { payload } = await jwtVerify(token, firebaseKeys, {
    issuer: `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`,
    audience: env.FIREBASE_PROJECT_ID,
    algorithms: ["RS256"]
  });
  if (typeof payload.sub !== "string" || payload.sub.length === 0 || payload.sub.length > 128) {
    throw new Error("Token de usuario no válido.");
  }
  return payload.sub;
}

function notificationCopy(type: ActivityType, actorName: string): { title: string; body: string } {
  const name = actorName.trim().slice(0, 80) || "Alguien";
  switch (type) {
    case "LIKE": return { title: "Nueva interacción en Pawly", body: `${name} ha indicado que le gusta tu publicación.` };
    case "COMMENT": return { title: "Nuevo comentario en Pawly", body: `${name} ha comentado tu publicación.` };
    case "FOLLOW": return { title: "Nuevo seguidor en Pawly", body: `${name} ha empezado a seguirte.` };
  }
}

async function verifyActivity(
  env: Env,
  accessToken: string,
  actorUid: string,
  input: ActivityRequest
): Promise<{ actorName: string } | null> {
  const recipientUid = input.recipientUid;
  if (actorUid === recipientUid) return null;

  const [recipientBlock, actorBlock, actorProfile, recipientProfile] = await Promise.all([
    readDocument(env, accessToken, `users/${recipientUid}/blocked/${actorUid}`),
    readDocument(env, accessToken, `users/${actorUid}/blocked/${recipientUid}`),
    readDocument(env, accessToken, `users/${actorUid}`),
    readDocument(env, accessToken, `users/${recipientUid}`)
  ]);
  if (recipientBlock || actorBlock || !actorProfile || !recipientProfile) return null;

  if (input.type === "FOLLOW") {
    if (input.relatedId !== "" || input.sourceEventId !== "") return null;
    const follower = await readDocument(env, accessToken, `users/${recipientUid}/followers/${actorUid}`);
    if (!follower) return null;
  } else {
    if (!input.relatedId || input.relatedId.length > 128) return null;
    const post = await readDocument(env, accessToken, `posts/${input.relatedId}`);
    if (!post || decodeFirestoreString(post.fields?.authorId) !== recipientUid) return null;

    if (input.type === "LIKE") {
      if (input.sourceEventId !== input.relatedId) return null;
      if (!decodeFirestoreStrings(post.fields?.likedBy).includes(actorUid)) return null;
    } else {
      if (!input.sourceEventId || input.sourceEventId.length > 128) return null;
      const comment = await readDocument(
        env, accessToken, `posts/${input.relatedId}/comments/${input.sourceEventId}`
      );
      if (!comment || decodeFirestoreString(comment.fields?.authorId) !== actorUid) return null;
    }
  }

  const actorName = decodeFirestoreString(actorProfile.fields?.displayName)
    || decodeFirestoreString(actorProfile.fields?.username)
    || "Usuario Pawly";
  return { actorName };
}

async function listDeviceTokens(env: Env, accessToken: string, uid: string): Promise<Array<{ id: string; token: string }>> {
  const response = await fetch(
    `${firestoreRoot(env)}/users/${encodeURIComponent(uid)}/fcmTokens?pageSize=20`,
    { headers: { authorization: `Bearer ${accessToken}` } }
  );
  if (!response.ok) throw new Error("No se pudieron consultar los dispositivos del destinatario.");
  const payload = await response.json() as { documents?: any[] };
  return (payload.documents ?? []).flatMap((doc) => {
    const token = decodeFirestoreString(doc.fields?.token);
    const id = String(doc.name ?? "").split("/").pop() ?? "";
    return token && id ? [{ id, token }] : [];
  });
}

async function claimDispatch(
  env: Env,
  accessToken: string,
  actorUid: string,
  input: ActivityRequest
): Promise<"created" | "duplicate"> {
  const material = new TextEncoder().encode([
    input.type, actorUid, input.recipientUid, input.relatedId, input.sourceEventId
  ].join("|"));
  const digest = await crypto.subtle.digest("SHA-256", material);
  const id = Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
  const response = await fetch(
    `${firestoreRoot(env)}/pushDispatches?documentId=${id}`,
    {
      method: "POST",
      headers: {
        authorization: `Bearer ${accessToken}`,
        "content-type": "application/json"
      },
      body: JSON.stringify({
        fields: {
          actorUid: { stringValue: actorUid },
          recipientUid: { stringValue: input.recipientUid },
          type: { stringValue: input.type },
          createdAt: { timestampValue: new Date().toISOString() },
          expiresAt: { timestampValue: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString() }
        }
      })
    }
  );
  if (response.status === 409) return "duplicate";
  if (!response.ok) throw new Error("No se pudo registrar el envío de forma segura.");
  return "created";
}

async function sendPush(
  env: Env,
  accessToken: string,
  token: string,
  copy: { title: string; body: string },
  input: ActivityRequest
): Promise<"sent" | "invalid"> {
  const response = await fetch(
    `https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`,
    {
      method: "POST",
      headers: {
        authorization: `Bearer ${accessToken}`,
        "content-type": "application/json"
      },
      body: JSON.stringify({
        message: {
          token,
          notification: copy,
          data: {
            type: input.type,
            relatedId: input.relatedId,
            sourceEventId: input.sourceEventId
          }
        }
      })
    }
  );
  if (response.ok) return "sent";
  const errorBody = await response.text();
  if (response.status === 404 || errorBody.includes("UNREGISTERED")) return "invalid";
  throw new Error("FCM no pudo entregar la notificación.");
}

async function handleActivityNotification(request: Request, env: Env): Promise<Response> {
  const authorization = request.headers.get("authorization") ?? "";
  const bearer = authorization.match(/^Bearer\s+(.+)$/i)?.[1];
  if (!bearer) return json({ error: "Falta el token de autenticación." }, 401);

  let actorUid: string;
  try {
    actorUid = await verifyFirebaseUser(bearer, env);
  } catch {
    return json({ error: "La sesión no es válida o ha caducado." }, 401);
  }

  const contentLength = Number(request.headers.get("content-length") ?? "0");
  if (contentLength > 4096) return json({ error: "Petición demasiado grande." }, 413);

  let input: ActivityRequest;
  try {
    const body = await request.json() as Record<string, unknown>;
    if (
      !["LIKE", "COMMENT", "FOLLOW"].includes(String(body.type)) ||
      typeof body.recipientUid !== "string" || body.recipientUid.length < 1 || body.recipientUid.length > 128 ||
      typeof body.relatedId !== "string" || body.relatedId.length > 128 ||
      typeof body.sourceEventId !== "string" || body.sourceEventId.length > 128
    ) return json({ error: "Datos de actividad no válidos." }, 400);
    input = body as unknown as ActivityRequest;
  } catch {
    return json({ error: "El cuerpo de la petición no es JSON válido." }, 400);
  }

  try {
    const accessToken = await googleAccessToken(env);
    const verified = await verifyActivity(env, accessToken, actorUid, input);
    if (!verified) return json({ accepted: false, reason: "La actividad no se ha podido verificar." }, 403);

    const devices = await listDeviceTokens(env, accessToken, input.recipientUid);
    if (devices.length === 0) return json({ accepted: true, delivered: 0, reason: "Sin dispositivos registrados." });

    const claim = await claimDispatch(env, accessToken, actorUid, input);
    if (claim === "duplicate") return json({ accepted: true, delivered: 0, duplicate: true });

    const copy = notificationCopy(input.type, verified.actorName);
    let delivered = 0;
    for (const device of devices) {
      const result = await sendPush(env, accessToken, device.token, copy, input);
      if (result === "sent") delivered++;
      else {
        await firestoreRequest(env, accessToken, `users/${input.recipientUid}/fcmTokens/${device.id}`, { method: "DELETE" });
      }
    }
    return json({ accepted: true, delivered });
  } catch {
    return json({ error: "No se pudo procesar la notificación." }, 502);
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    if (request.method === "OPTIONS") return json({ ok: true });
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") {
      return json({ service: "pawly-api", status: "ok" });
    }
    if (request.method === "POST" && url.pathname === "/v1/activity-notifications") {
      return handleActivityNotification(request, env);
    }
    return json({ error: "Ruta no encontrada." }, 404);
  }
};
