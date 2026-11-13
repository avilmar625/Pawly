import { describe, expect, it } from "vitest";
import { distanceKm, normalizePhone } from "./privacy-utils";

describe("normalizePhone", () => {
  it("normalizes Spanish national numbers and international prefixes", () => {
    expect(normalizePhone("600 123 456")).toBe("+34600123456");
    expect(normalizePhone("0034 600 123 456")).toBe("+34600123456");
    expect(normalizePhone("+1 (202) 555-0100")).toBe("+12025550100");
  });
  it("rejects ambiguous or malformed numbers", () => {
    expect(normalizePhone("12345")).toBeNull();
    expect(normalizePhone("not a phone")).toBeNull();
    expect(normalizePhone("+0 12345678")).toBeNull();
  });
});

describe("distanceKm", () => {
  it("returns zero for the same point", () => {
    expect(distanceKm(36.72, -4.42, 36.72, -4.42)).toBeCloseTo(0, 6);
  });
  it("calculates a plausible distance between Málaga and Torremolinos", () => {
    const distance = distanceKm(36.7213, -4.4214, 36.6203, -4.4994);
    expect(distance).toBeGreaterThan(10);
    expect(distance).toBeLessThan(20);
  });
});
