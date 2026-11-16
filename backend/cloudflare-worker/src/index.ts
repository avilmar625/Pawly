import { createRemoteJWKSet, importPKCS8, jwtVerify, SignJWT } from "jose";
import { isCurrentComment, parseActivityRequest, type ActivityRequest } from "./activity-validation";
import { shouldSuppressDispatch } from "./dispatch-state";
import { parseCommunityPlaceInput, communityPlaceDailyLimitId } from "./community-place-policy";
import { distanceKm, parseBearerToken } from "./privacy-utils";

interface Env {
  FIREBASE_PROJECT_ID: string;
  FIREBASE_CLIENT_EMAIL: string;
  FIREBASE_PRIVATE_KEY: string;
  PHONE_MATCH_HMAC_SECRET?: string;
}

type ActivityType = "LIKE" | "COMMENT" | "FOLLOW";

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
  if (typeof payload.sub !== "string" || !/^[A-Za-z0-9_-]{1,128}$/.test(payload.sub) || payload.email_verified !== true) {
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
      if (!isCurrentComment(decodeFirestoreString(post.fields?.lastCommentId), input.sourceEventId)) return null;
    }
  }

  const actorName = decodeFirestoreString(actorProfile.fields?.displayName)
    || decodeFirestoreString(actorProfile.fields?.username)
    || "Usuario Pawly";
  return { actorName };
}

async function listDeviceTokens(env: Env, accessToken: string, uid: string): Promise<Array<{ id: string; token: string }>> {
  // The free Worker plan allows 50 external subrequests per invocation. Keep this
  // fan-out bounded to the two most recently returned devices for each recipient.
  const url = new URL(`${firestoreRoot(env)}/users/${encodeURIComponent(uid)}/fcmTokens`);
  url.searchParams.set("pageSize", "2");
  const response = await fetch(url.toString(), {
    headers: { authorization: `Bearer ${accessToken}` }
  });
  if (!response.ok) throw new Error("No se pudieron consultar los dispositivos del destinatario.");
  const payload = await response.json() as { documents?: any[] };
  return (payload.documents ?? []).flatMap((doc) => {
    const token = decodeFirestoreString(doc.fields?.token);
    const id = String(doc.name ?? "").split("/").pop() ?? "";
    return token && id ? [{ id, token }] : [];
  }).slice(0, 2);
}

const DISPATCH_LEASE_MS = 5 * 60 * 1000;

interface DispatchClaim {
  duplicate: boolean;
  path: string;
  updateTime: string;
}

async function claimDispatch(
  env: Env,
  accessToken: string,
  actorUid: string,
  input: ActivityRequest
): Promise<DispatchClaim> {
  const material = new TextEncoder().encode([
    input.type, actorUid, input.recipientUid, input.relatedId, input.sourceEventId
  ].join("|"));
  const digest = await crypto.subtle.digest("SHA-256", material);
  const id = Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
  const path = `pushDispatches/${id}`;
  const now = Date.now();
  const leaseUntil = new Date(now + DISPATCH_LEASE_MS).toISOString();
  const created = await fetch(`${firestoreRoot(env)}/pushDispatches?documentId=${id}`, {
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
        status: { stringValue: "pending" },
        attempt: { integerValue: "1" },
        leaseUntil: { timestampValue: leaseUntil },
        createdAt: { timestampValue: new Date(now).toISOString() },
        updatedAt: { timestampValue: new Date(now).toISOString() },
        expiresAt: { timestampValue: new Date(now + 30 * 24 * 60 * 60 * 1000).toISOString() }
      }
    })
  });

  if (created.ok) {
    const document = await created.json() as { updateTime?: string };
    if (!document.updateTime) throw new Error("Firestore no devolvió la versión del envío.");
    return { duplicate: false, path, updateTime: document.updateTime };
  }
  if (created.status !== 409) throw new Error("No se pudo registrar el envío de forma segura.");

  const existing = await readDocument(env, accessToken, path);
  if (!existing) throw new Error("No se pudo recuperar el estado del envío.");
  const status = decodeFirestoreString(existing.fields?.status);
  const leaseValue = existing.fields?.leaseUntil?.timestampValue;
  const leaseMillis = typeof leaseValue === "string" ? Date.parse(leaseValue) : Number.NaN;
  // Legacy records without a status are treated as completed to avoid resending
  // notifications that may already have been delivered by the previous version.
  if (shouldSuppressDispatch(status, leaseMillis, now)) {
    return { duplicate: true, path, updateTime: String(existing.updateTime ?? "") };
  }

  const updateTime = String(existing.updateTime ?? "");
  if (!updateTime) return { duplicate: true, path, updateTime: "" };
  const rawAttempt = existing.fields?.attempt?.integerValue;
  const attempt = Number.parseInt(String(rawAttempt ?? "1"), 10);
  const patchUrl = new URL(`${firestoreRoot(env)}/${encodeDocumentPath(path)}`);
  for (const field of ["status", "attempt", "leaseUntil", "updatedAt"]) {
    patchUrl.searchParams.append("updateMask.fieldPaths", field);
  }
  patchUrl.searchParams.set("currentDocument.updateTime", updateTime);
  const reclaimed = await fetch(patchUrl.toString(), {
    method: "PATCH",
    headers: {
      authorization: `Bearer ${accessToken}`,
      "content-type": "application/json"
    },
    body: JSON.stringify({
      fields: {
        status: { stringValue: "pending" },
        attempt: { integerValue: String(Number.isFinite(attempt) ? attempt + 1 : 2) },
        leaseUntil: { timestampValue: leaseUntil },
        updatedAt: { timestampValue: new Date(now).toISOString() }
      }
    })
  });
  if (reclaimed.status === 409 || reclaimed.status === 412) {
    return { duplicate: true, path, updateTime };
  }
  if (!reclaimed.ok) throw new Error("No se pudo recuperar un envío pendiente.");
  const document = await reclaimed.json() as { updateTime?: string };
  if (!document.updateTime) throw new Error("Firestore no devolvió la nueva versión del envío.");
  return { duplicate: false, path, updateTime: document.updateTime };
}

async function finishDispatch(
  env: Env,
  accessToken: string,
  claim: DispatchClaim,
  status: "sent" | "failed",
  delivered: number
): Promise<void> {
  if (!claim.updateTime) return;
  const url = new URL(`${firestoreRoot(env)}/${encodeDocumentPath(claim.path)}`);
  for (const field of ["status", "delivered", "leaseUntil", "updatedAt"]) {
    url.searchParams.append("updateMask.fieldPaths", field);
  }
  url.searchParams.set("currentDocument.updateTime", claim.updateTime);
  const response = await fetch(url.toString(), {
    method: "PATCH",
    headers: {
      authorization: `Bearer ${accessToken}`,
      "content-type": "application/json"
    },
    body: JSON.stringify({
      fields: {
        status: { stringValue: status },
        delivered: { integerValue: String(delivered) },
        leaseUntil: { timestampValue: new Date(0).toISOString() },
        updatedAt: { timestampValue: new Date().toISOString() }
      }
    })
  });
  if (!response.ok) throw new Error("No se pudo actualizar el estado del envío.");
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

  let input: ActivityRequest | null;
  try {
    const rawBody = await request.text();
    if (new TextEncoder().encode(rawBody).byteLength > 4096) {
      return json({ error: "Petición demasiado grande." }, 413);
    }
    input = parseActivityRequest(JSON.parse(rawBody));
    if (!input) return json({ error: "Datos de actividad no válidos." }, 400);
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
    if (claim.duplicate) return json({ accepted: true, delivered: 0, duplicate: true });

    const copy = notificationCopy(input.type, verified.actorName);
    let delivered = 0;
    try {
      for (const device of devices) {
        const result = await sendPush(env, accessToken, device.token, copy, input);
        if (result === "sent") delivered++;
        else {
          await firestoreRequest(env, accessToken, `users/${input.recipientUid}/fcmTokens/${device.id}`, { method: "DELETE" });
        }
      }
      await finishDispatch(env, accessToken, claim, "sent", delivered);
      return json({ accepted: true, delivered });
    } catch (error) {
      // A failed or abandoned dispatch becomes retryable. FCM is at-least-once:
      // a retry after partial delivery may duplicate a push to an earlier device.
      try {
        await finishDispatch(env, accessToken, claim, "failed", delivered);
      } catch {
        // The five-minute lease still permits recovery if this update also fails.
      }
      throw error;
    }
  } catch {
    return json({ error: "No se pudo procesar la notificación." }, 502);
  }
}


function decodeFirestoreNumber(value: any): number {
  const raw = value?.doubleValue ?? value?.integerValue;
  const number = typeof raw === "string" ? Number(raw) : raw;
  return typeof number === "number" && Number.isFinite(number) ? number : 0;
}

function decodeFirestoreBoolean(value: any): boolean {
  return value?.booleanValue === true;
}

async function verifiedRequestUser(request: Request, env: Env): Promise<string | null> {
  const bearer = parseBearerToken(request.headers.get("authorization"));
  if (!bearer) return null;
  try { return await verifyFirebaseUser(bearer, env); } catch { return null; }
}

async function writeFirestoreDocument(env: Env, token: string, path: string, fields: Record<string, unknown>): Promise<void> {
  const response = await firestoreRequest(env, token, path, {
    method: "PATCH",
    body: JSON.stringify({ fields })
  });
  if (!response.ok) throw new Error("No se pudo guardar el registro privado.");
}

async function deleteFirestoreDocument(env: Env, token: string, path: string): Promise<void> {
  const response = await firestoreRequest(env, token, path, { method: "DELETE" });
  if (!response.ok && response.status !== 404) throw new Error("No se pudo retirar el registro privado.");
}

async function handleCommunityPlaceSubmission(request: Request, env: Env): Promise<Response> {
  const uid = await verifiedRequestUser(request, env);
  if (!uid) return json({ error: "Inicia sesión con una cuenta verificada." }, 401);
  try {
    const raw = await request.text();
    if (new TextEncoder().encode(raw).byteLength > 4096) return json({ error: "Petición demasiado grande." }, 413);
    const input = parseCommunityPlaceInput(JSON.parse(raw));
    if (!input) return json({ error: "La propuesta de lugar no es válida." }, 400);
    const accessToken = await googleAccessToken(env);
    const placeId = uid + "_" + input.submissionId;
    const existing = await readDocument(env, accessToken, `communityPlaces/${placeId}`);
    if (existing) {
      if (decodeFirestoreString(existing.fields?.authorUid) !== uid) return json({ error: "Identificador de propuesta no válido." }, 409);
      return json({ id: placeId, status: decodeFirestoreString(existing.fields?.status) || "pending", duplicate: true });
    }

    // One proposal per verified account per UTC day. The create-only document is
    // an atomic reservation: concurrent requests cannot bypass the daily limit.
    const limitId = communityPlaceDailyLimitId(uid, new Date());
    const limitResponse = await fetch(`${firestoreRoot(env)}/communityPlaceSubmissionLimits?documentId=${encodeURIComponent(limitId)}`, {
      method: "POST",
      headers: { authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
      body: JSON.stringify({ fields: {
        uid: { stringValue: uid },
        createdAt: { timestampValue: new Date().toISOString() },
        expiresAt: { timestampValue: new Date(Date.now() + 90 * 24 * 60 * 60 * 1000).toISOString() }
      } })
    });
    if (limitResponse.status === 409) return json({ error: "Ya has enviado una propuesta de lugar hoy. Podrás aportar otra mañana." }, 429);
    if (!limitResponse.ok) return json({ error: "No se pudo reservar el envío de la propuesta." }, 502);

    const created = await fetch(`${firestoreRoot(env)}/communityPlaces?documentId=${encodeURIComponent(placeId)}`, {
      method: "POST",
      headers: { authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
      body: JSON.stringify({ fields: {
        name: { stringValue: input.name },
        category: { stringValue: input.category },
        location: { stringValue: input.location },
        latitude: { doubleValue: input.latitude },
        longitude: { doubleValue: input.longitude },
        description: { stringValue: input.description },
        authorUid: { stringValue: uid },
        status: { stringValue: "pending" },
        createdAt: { timestampValue: new Date().toISOString() }
      } })
    });
    if (!created.ok) {
      await deleteFirestoreDocument(env, accessToken, `communityPlaceSubmissionLimits/${limitId}`);
      if (created.status === 409) {
        const duplicate = await readDocument(env, accessToken, `communityPlaces/${placeId}`);
        if (duplicate && decodeFirestoreString(duplicate.fields?.authorUid) === uid) {
          return json({ id: placeId, status: decodeFirestoreString(duplicate.fields?.status) || "pending", duplicate: true });
        }
      }
      return json({ error: "No se pudo guardar la propuesta. Inténtalo de nuevo." }, 502);
    }
    return json({ id: placeId, status: "pending" }, 201);
  } catch {
    return json({ error: "No se pudo enviar la propuesta de lugar." }, 502);
  }
}

async function listLostPetSubscriptions(
  env: Env, accessToken: string, pageToken: string
): Promise<{ documents: any[]; nextPageToken: string }> {
  // Read one bounded page per invocation; the client follows the opaque cursor.
  const url = new URL(`${firestoreRoot(env)}/lostPetSubscriptions`);
  url.searchParams.set("pageSize", "500");
  if (pageToken) url.searchParams.set("pageToken", pageToken);
  const response = await fetch(url.toString(), { headers: { authorization: `Bearer ${accessToken}` } });
  if (!response.ok) throw new Error("No se pudieron consultar las suscripciones de avisos.");
  const payload = await response.json() as { documents?: any[]; nextPageToken?: string };
  return { documents: payload.documents ?? [], nextPageToken: payload.nextPageToken ?? "" };
}

async function sendGenericPush(env: Env, accessToken: string, token: string, title: string, body: string, data: Record<string, string>): Promise<"sent" | "invalid"> {
  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${env.FIREBASE_PROJECT_ID}/messages:send`, {
    method: "POST",
    headers: { authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
    body: JSON.stringify({ message: { token, notification: { title, body }, data } })
  });
  if (response.ok) return "sent";
  const errorBody = await response.text();
  if (response.status === 404 || errorBody.includes("UNREGISTERED")) return "invalid";
  throw new Error("FCM no pudo entregar el aviso.");
}

async function handleLostPetAlert(request: Request, env: Env): Promise<Response> {
  const uid = await verifiedRequestUser(request, env);
  if (!uid) return json({ error: "Inicia sesión con una cuenta verificada." }, 401);
  try {
    const raw = await request.text();
    if (new TextEncoder().encode(raw).byteLength > 8192) return json({ error: "Petición demasiado grande." }, 413);
    const body = JSON.parse(raw) as { alertId?: unknown; pageToken?: unknown; offset?: unknown };
    if (typeof body.alertId !== "string" || !/^[A-Za-z0-9_-]{1,128}$/.test(body.alertId) ||
      (body.pageToken !== undefined && (typeof body.pageToken !== "string" || body.pageToken.length > 6000)) ||
      (body.offset !== undefined && (!Number.isSafeInteger(body.offset) || Number(body.offset) < 0 || Number(body.offset) > 500))) {
      return json({ error: "Aviso o cursor no válido." }, 400);
    }
    const pageToken = typeof body.pageToken === "string" ? body.pageToken : "";
    const offset = typeof body.offset === "number" ? body.offset : 0;
    const accessToken = await googleAccessToken(env);
    const [alert, post] = await Promise.all([
      readDocument(env, accessToken, `lostPetAlerts/${body.alertId}`),
      readDocument(env, accessToken, `posts/${body.alertId}`)
    ]);
    if (!alert || !post || decodeFirestoreString(alert.fields?.authorUid) !== uid ||
      decodeFirestoreString(alert.fields?.status) !== "active" ||
      decodeFirestoreString(post.fields?.category) !== "LOST" ||
      decodeFirestoreString(post.fields?.authorId) !== uid) return json({ error: "No se pudo verificar el aviso." }, 403);
    const latitude = decodeFirestoreNumber(alert.fields?.latCell) / 100;
    const longitude = decodeFirestoreNumber(alert.fields?.lonCell) / 100;
    const dogName = decodeFirestoreString(alert.fields?.dogName) || "Una mascota";
    const page = await listLostPetSubscriptions(env, accessToken, pageToken);
    if (offset > page.documents.length) return json({ error: "El cursor de avisos ha caducado." }, 400);
    let delivered = 0;
    let processedRecipients = 0;
    let nextOffset = offset;
    for (let index = offset; index < page.documents.length; index++) {
      nextOffset = index + 1;
      const fields = page.documents[index].fields ?? {};
      const recipientUid = decodeFirestoreString(fields.uid);
      if (!recipientUid || recipientUid === uid || !decodeFirestoreBoolean(fields.enabled)) continue;
      const radius = decodeFirestoreNumber(fields.radiusKm);
      const subLat = decodeFirestoreNumber(fields.latCell) / 100;
      const subLon = decodeFirestoreNumber(fields.lonCell) / 100;
      if (![5, 10, 20].includes(radius) || distanceKm(latitude, longitude, subLat, subLon) > radius) continue;
      const [blockA, blockB] = await Promise.all([
        readDocument(env, accessToken, `users/${uid}/blocked/${recipientUid}`),
        readDocument(env, accessToken, `users/${recipientUid}/blocked/${uid}`)
      ]);
      if (blockA || blockB) continue;
      const claim = await fetch(`${firestoreRoot(env)}/lostPetPushes?documentId=${encodeURIComponent(body.alertId + "_" + recipientUid)}`, {
        method: "POST", headers: { authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
        body: JSON.stringify({ fields: {
          alertId: { stringValue: body.alertId }, recipientUid: { stringValue: recipientUid },
          createdAt: { timestampValue: new Date().toISOString() }
        } })
      });
      if (claim.status === 409) continue;
      if (!claim.ok) throw new Error("No se pudo registrar el envío del aviso.");
      const devices = await listDeviceTokens(env, accessToken, recipientUid);
      for (const device of devices) {
        const result = await sendGenericPush(env, accessToken, device.token, "Mascota perdida cerca de ti",
          `${dogName}: se ha publicado un aviso en tu zona. Abre Pawly para ver los detalles.`,
          { type: "LOST_PET", relatedId: body.alertId });
        if (result === "sent") delivered++;
        else await firestoreRequest(env, accessToken, `users/${recipientUid}/fcmTokens/${device.id}`, { method: "DELETE" });
      }
      processedRecipients++;
      // Four recipients × bounded Firestore/FCM calls stays below the 50
      // external-subrequest ceiling even when each has two registered devices.
      if (processedRecipients >= 4) break;
    }
    const pageFinished = nextOffset >= page.documents.length;
    const complete = pageFinished && !page.nextPageToken;
    return json({
      accepted: true,
      delivered,
      complete,
      pageToken: pageFinished ? page.nextPageToken : pageToken,
      nextOffset: pageFinished ? 0 : nextOffset
    });
  } catch {
    return json({ error: "No se pudieron enviar los avisos de proximidad." }, 502);
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
    if (request.method === "POST" && url.pathname === "/v1/lost-pet-alerts") {
      return handleLostPetAlert(request, env);
    }
    if (request.method === "POST" && url.pathname === "/v1/community-places") {
      return handleCommunityPlaceSubmission(request, env);
    }
    return json({ error: "Ruta no encontrada." }, 404);
  }
};
