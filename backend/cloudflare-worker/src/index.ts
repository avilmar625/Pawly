import { createRemoteJWKSet, importPKCS8, jwtVerify, SignJWT } from "jose";
import { isCurrentComment, parseActivityRequest, type ActivityRequest } from "./activity-validation";
import { shouldSuppressDispatch } from "./dispatch-state";

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
  const devices: Array<{ id: string; token: string }> = [];
  let pageToken = "";
  // Bound pagination so a corrupted or unexpectedly large token collection cannot
  // make a single notification request run indefinitely.
  for (let page = 0; page < 5; page++) {
    const url = new URL(`${firestoreRoot(env)}/users/${encodeURIComponent(uid)}/fcmTokens`);
    url.searchParams.set("pageSize", "100");
    if (pageToken) url.searchParams.set("pageToken", pageToken);
    const response = await fetch(url.toString(), {
      headers: { authorization: `Bearer ${accessToken}` }
    });
    if (!response.ok) throw new Error("No se pudieron consultar los dispositivos del destinatario.");
    const payload = await response.json() as { documents?: any[]; nextPageToken?: string };
    devices.push(...(payload.documents ?? []).flatMap((doc) => {
      const token = decodeFirestoreString(doc.fields?.token);
      const id = String(doc.name ?? "").split("/").pop() ?? "";
      return token && id ? [{ id, token }] : [];
    }));
    pageToken = payload.nextPageToken ?? "";
    if (!pageToken) break;
  }
  return devices;
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

function normalizePhone(raw: string): string | null {
  let value = raw.trim().replace(/[().\\s-]/g, "");
  if (value.startsWith("00")) value = "+" + value.slice(2);
  if (!value.startsWith("+") && /^\\d{9}$/.test(value)) value = "+34" + value;
  if (!/^\\+[1-9]\\d{7,14}$/.test(value)) return null;
  return value;
}

async function phoneDigest(secret: string, phone: string): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]
  );
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(phone));
  return Array.from(new Uint8Array(signature), byte => byte.toString(16).padStart(2, "0")).join("");
}

async function verifiedRequestUser(request: Request, env: Env): Promise<string | null> {
  const bearer = (request.headers.get("authorization") ?? "").match(/^Bearer\\s+(.+)$/i)?.[1];
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

async function handleContactDiscovery(request: Request, env: Env): Promise<Response> {
  const uid = await verifiedRequestUser(request, env);
  if (!uid) return json({ error: "Inicia sesión con una cuenta verificada." }, 401);
  if (!env.PHONE_MATCH_HMAC_SECRET || env.PHONE_MATCH_HMAC_SECRET.length < 32) {
    return json({ error: "La búsqueda privada por teléfono todavía no está configurada." }, 503);
  }
  try {
    const raw = await request.text();
    if (new TextEncoder().encode(raw).byteLength > 2048) return json({ error: "Petición demasiado grande." }, 413);
    const body = JSON.parse(raw) as { enabled?: boolean; phone?: string };
    const privatePath = `users/${uid}/privateData/contactDiscovery`;
    const accessToken = await googleAccessToken(env);
    const previous = await readDocument(env, accessToken, privatePath);
    const previousHash = decodeFirestoreString(previous?.fields?.phoneHash);
    if (body.enabled !== true) {
      if (previousHash) {
        const existing = await readDocument(env, accessToken, `phoneDirectory/${previousHash}`);
        if (decodeFirestoreString(existing?.fields?.uid) === uid) {
          await deleteFirestoreDocument(env, accessToken, `phoneDirectory/${previousHash}`);
        }
      }
      await deleteFirestoreDocument(env, accessToken, privatePath);
      return json({ enabled: false });
    }
    if (typeof body.phone !== "string") return json({ error: "Introduce un número de teléfono." }, 400);
    const normalized = normalizePhone(body.phone);
    if (!normalized) return json({ error: "Usa un teléfono internacional válido, por ejemplo +34 600 000 000." }, 400);
    const hash = await phoneDigest(env.PHONE_MATCH_HMAC_SECRET, normalized);
    const existing = await readDocument(env, accessToken, `phoneDirectory/${hash}`);
    const ownerUid = decodeFirestoreString(existing?.fields?.uid);
    if (ownerUid && ownerUid !== uid) return json({ error: "Ese teléfono ya está asociado a otra cuenta." }, 409);
    if (previousHash && previousHash !== hash) {
      const old = await readDocument(env, accessToken, `phoneDirectory/${previousHash}`);
      if (decodeFirestoreString(old?.fields?.uid) === uid) await deleteFirestoreDocument(env, accessToken, `phoneDirectory/${previousHash}`);
    }
    await writeFirestoreDocument(env, accessToken, `phoneDirectory/${hash}`, {
      uid: { stringValue: uid }, updatedAt: { timestampValue: new Date().toISOString() }
    });
    await writeFirestoreDocument(env, accessToken, privatePath, {
      phoneHash: { stringValue: hash }, enabled: { booleanValue: true },
      updatedAt: { timestampValue: new Date().toISOString() }
    });
    return json({ enabled: true });
  } catch {
    return json({ error: "No se pudo actualizar la búsqueda privada por teléfono." }, 502);
  }
}

async function handleContactMatches(request: Request, env: Env): Promise<Response> {
  const uid = await verifiedRequestUser(request, env);
  if (!uid) return json({ error: "Inicia sesión con una cuenta verificada." }, 401);
  if (!env.PHONE_MATCH_HMAC_SECRET || env.PHONE_MATCH_HMAC_SECRET.length < 32) {
    return json({ error: "La búsqueda privada por teléfono todavía no está configurada." }, 503);
  }
  try {
    const raw = await request.text();
    if (new TextEncoder().encode(raw).byteLength > 12000) return json({ error: "Petición demasiado grande." }, 413);
    const body = JSON.parse(raw) as { phoneNumbers?: unknown };
    if (!Array.isArray(body.phoneNumbers) || body.phoneNumbers.length < 1 || body.phoneNumbers.length > 30 ||
      body.phoneNumbers.some(value => typeof value !== "string" || value.length > 32)) {
      return json({ error: "Selecciona entre 1 y 30 teléfonos." }, 400);
    }
    const accessToken = await googleAccessToken(env);
    const blockedByMe = new Set<string>();
    const blocks = await fetch(`${firestoreRoot(env)}/users/${encodeURIComponent(uid)}/blocked?pageSize=100`, {
      headers: { authorization: `Bearer ${accessToken}` }
    });
    if (blocks.ok) {
      const payload = await blocks.json() as { documents?: any[] };
      (payload.documents ?? []).forEach(doc => blockedByMe.add(String(doc.name).split("/").pop() ?? ""));
    }
    const matches = new Map<string, { uid: string; displayName: string; username: string; photoUrl: string }>();
    for (const rawPhone of body.phoneNumbers as string[]) {
      const normalized = normalizePhone(rawPhone);
      if (!normalized) continue;
      const hash = await phoneDigest(env.PHONE_MATCH_HMAC_SECRET, normalized);
      const directory = await readDocument(env, accessToken, `phoneDirectory/${hash}`);
      const matchedUid = decodeFirestoreString(directory?.fields?.uid);
      if (!matchedUid || matchedUid === uid || blockedByMe.has(matchedUid)) continue;
      const [profile, theirBlock] = await Promise.all([
        readDocument(env, accessToken, `users/${matchedUid}`),
        readDocument(env, accessToken, `users/${matchedUid}/blocked/${uid}`)
      ]);
      if (!profile || theirBlock || profile.fields?.isPublic?.booleanValue !== true) continue;
      matches.set(matchedUid, {
        uid: matchedUid,
        displayName: decodeFirestoreString(profile.fields?.displayName) || "Usuario Pawly",
        username: decodeFirestoreString(profile.fields?.username),
        photoUrl: decodeFirestoreString(profile.fields?.photoUrl)
      });
    }
    return json({ matches: Array.from(matches.values()) });
  } catch {
    return json({ error: "No se pudieron consultar los contactos." }, 502);
  }
}

function distanceKm(aLat: number, aLon: number, bLat: number, bLon: number): number {
  const rad = (value: number) => value * Math.PI / 180;
  const dLat = rad(bLat - aLat);
  const dLon = rad(bLon - aLon);
  const h = Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(aLat)) * Math.cos(rad(bLat)) * Math.sin(dLon / 2) ** 2;
  return 6371 * 2 * Math.asin(Math.min(1, Math.sqrt(h)));
}

async function listLostPetSubscriptions(env: Env, accessToken: string): Promise<any[]> {
  const all: any[] = [];
  let pageToken = "";
  for (let page = 0; page < 10; page++) {
    const url = new URL(`${firestoreRoot(env)}/lostPetSubscriptions`);
    url.searchParams.set("pageSize", "500");
    if (pageToken) url.searchParams.set("pageToken", pageToken);
    const response = await fetch(url.toString(), { headers: { authorization: `Bearer ${accessToken}` } });
    if (!response.ok) throw new Error("No se pudieron consultar las suscripciones de avisos.");
    const payload = await response.json() as { documents?: any[]; nextPageToken?: string };
    all.push(...(payload.documents ?? []));
    pageToken = payload.nextPageToken ?? "";
    if (!pageToken) break;
  }
  return all;
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
    if (new TextEncoder().encode(raw).byteLength > 2048) return json({ error: "Petición demasiado grande." }, 413);
    const body = JSON.parse(raw) as { alertId?: unknown };
    if (typeof body.alertId !== "string" || !/^[A-Za-z0-9_-]{1,128}$/.test(body.alertId)) return json({ error: "Aviso no válido." }, 400);
    const accessToken = await googleAccessToken(env);
    const [alert, post] = await Promise.all([
      readDocument(env, accessToken, `lostPetAlerts/${body.alertId}`),
      readDocument(env, accessToken, `posts/${body.alertId}`)
    ]);
    if (!alert || !post || decodeFirestoreString(alert.fields?.authorUid) !== uid ||
      decodeFirestoreString(alert.fields?.status) !== "active" ||
      decodeFirestoreString(post.fields?.category) !== "LOST" ||
      decodeFirestoreString(post.fields?.authorId) !== uid) return json({ error: "No se pudo verificar el aviso." }, 403);
    const latitude = decodeFirestoreNumber(alert.fields?.latitude);
    const longitude = decodeFirestoreNumber(alert.fields?.longitude);
    const dogName = decodeFirestoreString(alert.fields?.dogName) || "Una mascota";
    const subscriptions = await listLostPetSubscriptions(env, accessToken);
    let delivered = 0;
    for (const subscription of subscriptions) {
      const fields = subscription.fields ?? {};
      const recipientUid = decodeFirestoreString(fields.uid);
      if (!recipientUid || recipientUid === uid || !decodeFirestoreBoolean(fields.enabled)) continue;
      const radius = decodeFirestoreNumber(fields.radiusKm);
      const subLat = decodeFirestoreNumber(fields.latitude);
      const subLon = decodeFirestoreNumber(fields.longitude);
      if (![5, 10, 20].includes(radius) || distanceKm(latitude, longitude, subLat, subLon) > radius) continue;
      const [blockA, blockB] = await Promise.all([
        readDocument(env, accessToken, `users/${uid}/blocked/${recipientUid}`),
        readDocument(env, accessToken, `users/${recipientUid}/blocked/${uid}`)
      ]);
      if (blockA || blockB) continue;
      const dispatchPath = `lostPetPushes/${body.alertId}_${recipientUid}`;
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
      void dispatchPath;
    }
    return json({ accepted: true, delivered });
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
    if (request.method === "POST" && url.pathname === "/v1/contact-discovery") {
      return handleContactDiscovery(request, env);
    }
    if (request.method === "POST" && url.pathname === "/v1/contact-matches") {
      return handleContactMatches(request, env);
    }
    return json({ error: "Ruta no encontrada." }, 404);
  }
};
