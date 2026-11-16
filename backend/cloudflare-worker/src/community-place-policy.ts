export interface CommunityPlaceInput {
  submissionId: string;
  name: string;
  category: "Parque" | "Playa" | "Restaurante" | "Alojamiento" | "Ruta" | "Veterinario" | "Otro";
  location: string;
  description: string;
  latitude: number;
  longitude: number;
}

const categories = new Set(["Parque", "Playa", "Restaurante", "Alojamiento", "Ruta", "Veterinario", "Otro"]);

/** Validate the exact client payload before the Worker writes a pending place. */
export function parseCommunityPlaceInput(value: unknown): CommunityPlaceInput | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const body = value as Record<string, unknown>;
  const allowed = ["submissionId", "name", "category", "location", "description", "latitude", "longitude"];
  const keys = Object.keys(body);
  if (keys.length !== allowed.length || keys.some(key => !allowed.includes(key))) return null;
  if (typeof body.submissionId !== "string" || !/^[A-Za-z0-9_-]{16,64}$/.test(body.submissionId)) return null;
  if (typeof body.name !== "string" || body.name.trim().length < 3 || body.name.trim().length > 80) return null;
  if (typeof body.category !== "string" || !categories.has(body.category)) return null;
  if (typeof body.location !== "string" || body.location.trim().length < 3 || body.location.trim().length > 120) return null;
  if (typeof body.description !== "string" || body.description.trim().length < 10 || body.description.trim().length > 500) return null;
  if (typeof body.latitude !== "number" || !Number.isFinite(body.latitude) || body.latitude < -90 || body.latitude > 90) return null;
  if (typeof body.longitude !== "number" || !Number.isFinite(body.longitude) || body.longitude < -180 || body.longitude > 180) return null;
  return {
    submissionId: body.submissionId,
    name: body.name.trim(),
    category: body.category as CommunityPlaceInput["category"],
    location: body.location.trim(),
    description: body.description.trim(),
    latitude: body.latitude,
    longitude: body.longitude
  };
}

/** UTC day bucket used for one community-place submission per account per day. */
export function communityPlaceDailyLimitId(uid: string, date: Date): string {
  return uid + "_" + date.toISOString().slice(0, 10);
}
