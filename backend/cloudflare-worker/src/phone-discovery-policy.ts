export const PHONE_CONTACT_BATCH_LIMIT = 30;
export const PHONE_DAILY_BATCH_LIMIT = 3;
export const PHONE_MATCH_RESULT_LIMIT = 10;

export function normalizeE164Phone(value: unknown): string | null {
  if (typeof value !== "string") return null;
  let phone = value.trim().replace(/[().\s-]/g, "");
  if (phone.startsWith("00")) phone = "+" + phone.slice(2);
  if (!/^\+[1-9]\d{7,14}$/.test(phone)) return null;
  return phone;
}

export function phoneDiscoveryReservationId(uid: string, date: Date, slot: number): string {
  const day = date.toISOString().slice(0, 10).replace(/-/g, "");
  return `${uid}_${day}_${slot}`;
}
