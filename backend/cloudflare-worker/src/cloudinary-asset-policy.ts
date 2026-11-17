export interface CloudinaryAssetOwner {
  uid: string;
  publicId: string;
  resourceType: string;
}

/** Only the uploader or a verified Pawly moderator may remove an indexed asset. */
export function canDeleteCloudinaryAsset(
  callerUid: string,
  isAdmin: boolean,
  asset: CloudinaryAssetOwner
): boolean {
  return /^[A-Za-z0-9_-]{1,128}$/.test(callerUid)
    && (callerUid === asset.uid || isAdmin)
    && /^[A-Za-z0-9_-]{1,128}$/.test(asset.uid)
    && asset.publicId.startsWith("pawly/")
    && asset.publicId.includes(`/${asset.uid}/`)
    && ["image", "video"].includes(asset.resourceType);
}

export function isCloudinarySecureUrl(value: unknown): value is string {
  if (typeof value !== "string" || value.length > 2048) return false;
  try {
    const url = new URL(value);
    return url.protocol === "https:" && url.hostname.length > 0;
  } catch {
    return false;
  }
}
