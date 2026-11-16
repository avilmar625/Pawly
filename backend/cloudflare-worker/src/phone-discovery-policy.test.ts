import { describe, expect, it } from "vitest";
import { normalizeE164Phone, phoneDiscoveryReservationId, PHONE_CONTACT_BATCH_LIMIT, PHONE_DAILY_BATCH_LIMIT } from "./phone-discovery-policy";

describe("phone discovery policy", () => {
  it("normalizes common E.164 formatting without guessing a country", () => {
    expect(normalizeE164Phone("+34 612 345 678")).toBe("+34612345678");
    expect(normalizeE164Phone("0034-612-345-678")).toBe("+34612345678");
    expect(normalizeE164Phone("612345678")).toBeNull();
    expect(normalizeE164Phone("+00000000000")).toBeNull();
  });

  it("limits a single contact batch and reserves three daily slots", () => {
    expect(PHONE_CONTACT_BATCH_LIMIT).toBe(30);
    expect(PHONE_DAILY_BATCH_LIMIT).toBe(3);
    expect(phoneDiscoveryReservationId("user_1", new Date("2026-11-16T10:00:00Z"), 2))
      .toBe("user_1_20261116_2");
  });
});
