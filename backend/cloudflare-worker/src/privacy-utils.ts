/** Normalize user-supplied numbers to E.164. Bare nine-digit Spanish numbers default to +34. */
export function normalizePhone(raw: string): string | null {
  let value = raw.trim().replace(/[().\\s-]/g, "");
  if (value.startsWith("00")) value = "+" + value.slice(2);
  if (!value.startsWith("+") && /^\\d{9}$/.test(value)) value = "+34" + value;
  if (!/^\\+[1-9]\\d{7,14}$/.test(value)) return null;
  return value;
}

/** Great-circle distance in kilometres using the haversine formula. */
export function distanceKm(aLat: number, aLon: number, bLat: number, bLon: number): number {
  const rad = (value: number) => value * Math.PI / 180;
  const dLat = rad(bLat - aLat);
  const dLon = rad(bLon - aLon);
  const h = Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(aLat)) * Math.cos(rad(bLat)) * Math.sin(dLon / 2) ** 2;
  return 6371 * 2 * Math.asin(Math.min(1, Math.sqrt(h)));
}
