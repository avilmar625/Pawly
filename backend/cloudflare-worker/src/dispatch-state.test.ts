import { describe, expect, it } from "vitest";
import { shouldSuppressDispatch } from "./dispatch-state";

describe("shouldSuppressDispatch", () => {
  it("suppresses completed sends", () => {
    expect(shouldSuppressDispatch("sent", 0, 1000)).toBe(true);
  });
  it("suppresses a pending send while its lease is active", () => {
    expect(shouldSuppressDispatch("pending", 2000, 1000)).toBe(true);
  });
  it("allows recovery after a pending lease expires", () => {
    expect(shouldSuppressDispatch("pending", 999, 1000)).toBe(false);
  });
  it("allows retry after a recorded failure", () => {
    expect(shouldSuppressDispatch("failed", 5000, 1000)).toBe(false);
  });
  it("treats legacy and unknown states conservatively", () => {
    expect(shouldSuppressDispatch("", 0, 1000)).toBe(true);
    expect(shouldSuppressDispatch(undefined, 0, 1000)).toBe(true);
  });
});
