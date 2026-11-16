export type ActivityType = "LIKE" | "COMMENT" | "FOLLOW";

export interface ActivityRequest {
  type: ActivityType;
  recipientUid: string;
  relatedId: string;
  sourceEventId: string;
}

/** Validate the minimal notification payload; ownership is checked against Firestore. */
export function parseActivityRequest(value: unknown): ActivityRequest | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const body = value as Record<string, unknown>;
  const allowedKeys = ["type", "recipientUid", "relatedId", "sourceEventId"];
  const keys = Object.keys(body);
  if (keys.length !== allowedKeys.length || keys.some((key) => !allowedKeys.includes(key))) return null;
  if (body.type !== "LIKE" && body.type !== "COMMENT" && body.type !== "FOLLOW") return null;
  if (typeof body.recipientUid !== "string" || !/^[A-Za-z0-9_-]{1,128}$/.test(body.recipientUid)) return null;
  if (typeof body.relatedId !== "string" || (body.relatedId !== "" && !/^[A-Za-z0-9_-]{1,128}$/.test(body.relatedId))) return null;
  if (typeof body.sourceEventId !== "string" || (body.sourceEventId !== "" && !/^[A-Za-z0-9_-]{1,128}$/.test(body.sourceEventId))) return null;
  if (body.type === "FOLLOW" && (body.relatedId !== "" || body.sourceEventId !== "")) return null;
  if (body.type === "LIKE" && (!body.relatedId || body.sourceEventId !== body.relatedId)) return null;
  if (body.type === "COMMENT" && (!body.relatedId || !body.sourceEventId)) return null;
  return { type: body.type, recipientUid: body.recipientUid, relatedId: body.relatedId, sourceEventId: body.sourceEventId };
}

/** A comment notification is only eligible for the most recently committed comment. */
export function isCurrentComment(lastCommentId: unknown, sourceEventId: string): boolean {
  return typeof lastCommentId === "string" && lastCommentId.length > 0 && lastCommentId === sourceEventId;
}
