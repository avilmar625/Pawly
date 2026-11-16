import { describe, expect, it } from "vitest";
import { communityPlaceDailyLimitId, parseCommunityPlaceInput } from "./community-place-policy";

const valid = {
  submissionId: "12345678-1234-4234-8234-123456789abc",
  name: "Playa canina",
  category: "Playa",
  location: "Málaga",
  description: "Una playa amplia para disfrutar con perros.",
  latitude: 36.7,
  longitude: -4.4
};

describe("parseCommunityPlaceInput", () => {
  it("accepts a complete place with a precise coordinate", () => {
    expect(parseCommunityPlaceInput(valid)).toMatchObject(valid);
  });
  it("rejects client-controlled status, author, unknown fields and invalid coordinates", () => {
    expect(parseCommunityPlaceInput({ ...valid, status: "approved" })).toBeNull();
    expect(parseCommunityPlaceInput({ ...valid, authorUid: "victim" })).toBeNull();
    expect(parseCommunityPlaceInput({ ...valid, latitude: 91 })).toBeNull();
    expect(parseCommunityPlaceInput({ ...valid, longitude: Number.NaN })).toBeNull();
  });
  it("rejects unsupported categories and underspecified places", () => {
    expect(parseCommunityPlaceInput({ ...valid, category: "Beach" })).toBeNull();
    expect(parseCommunityPlaceInput({ ...valid, description: "short" })).toBeNull();
  });
});

describe("communityPlaceDailyLimitId", () => {
  it("uses a stable UTC day bucket", () => {
    expect(communityPlaceDailyLimitId("user_1", new Date("2026-11-16T23:59:00-08:00")))
      .toBe("user_1_2026-11-17");
  });
});
