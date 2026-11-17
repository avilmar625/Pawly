import { describe, expect, it } from "vitest";
import { canDeleteCloudinaryAsset, isCloudinarySecureUrl } from "./cloudinary-asset-policy";

describe("Cloudinary asset deletion policy", () => {
  const asset = { uid: "owner-1", publicId: "pawly/post-photos/owner-1/photo", resourceType: "image" };

  it("allows the uploader to delete their own indexed asset", () => {
    expect(canDeleteCloudinaryAsset("owner-1", false, asset)).toBe(true);
  });

  it("allows a verified admin to remove a reported user's indexed asset", () => {
    expect(canDeleteCloudinaryAsset("moderator-1", true, asset)).toBe(true);
  });

  it("does not allow another ordinary user to delete the asset", () => {
    expect(canDeleteCloudinaryAsset("stranger", false, asset)).toBe(false);
  });

  it("rejects asset IDs outside the Pawly owner folder and unknown resource types", () => {
    expect(canDeleteCloudinaryAsset("owner-1", true, { ...asset, publicId: "pawly/post-photos/other/photo" })).toBe(false);
    expect(canDeleteCloudinaryAsset("owner-1", true, { ...asset, resourceType: "raw" })).toBe(false);
  });

  it("accepts only HTTPS media URLs", () => {
    expect(isCloudinarySecureUrl("https://res.cloudinary.com/demo/image/upload/a.jpg")).toBe(true);
    expect(isCloudinarySecureUrl("http://res.cloudinary.com/demo/a.jpg")).toBe(false);
    expect(isCloudinarySecureUrl("javascript:alert(1)")).toBe(false);
    expect(isCloudinarySecureUrl("https://attacker.example/image.jpg")).toBe(false);
  });
});
