import { describe, expect, it } from "vitest";
import { isCurrentComment, parseActivityRequest } from "./activity-validation";

describe("parseActivityRequest", () => {
  it("accepts a valid like with matching post identifiers", () => {
    expect(parseActivityRequest({ type: "LIKE", recipientUid: "recipient_1", relatedId: "post_1", sourceEventId: "post_1" })?.type).toBe("LIKE");
  });
  it("accepts a valid comment", () => {
    expect(parseActivityRequest({ type: "COMMENT", recipientUid: "recipient", relatedId: "post", sourceEventId: "comment" })?.type).toBe("COMMENT");
  });
  it("accepts a follow without content identifiers", () => {
    expect(parseActivityRequest({ type: "FOLLOW", recipientUid: "recipient", relatedId: "", sourceEventId: "" })?.type).toBe("FOLLOW");
  });
  it.each([
    null, [], {},
    { type: "ADMIN", recipientUid: "recipient", relatedId: "", sourceEventId: "" },
    { type: "FOLLOW", recipientUid: "recipient", relatedId: "post", sourceEventId: "" },
    { type: "LIKE", recipientUid: "recipient", relatedId: "post", sourceEventId: "other" },
    { type: "COMMENT", recipientUid: "recipient", relatedId: "", sourceEventId: "comment" },
    { type: "FOLLOW", recipientUid: "../users", relatedId: "", sourceEventId: "" },
    { type: "FOLLOW", recipientUid: "recipient", relatedId: "", sourceEventId: "", actorName: "spoofed" }
  ])("rejects malformed or over-privileged payloads: %j", (payload) => {
    expect(parseActivityRequest(payload)).toBeNull();
  });
});

describe("isCurrentComment", () => {
  it("accepts only the current comment id", () => {
    expect(isCurrentComment("comment_1", "comment_1")).toBe(true);
    expect(isCurrentComment("comment_2", "comment_1")).toBe(false);
    expect(isCurrentComment(undefined, "comment_1")).toBe(false);
    expect(isCurrentComment("", "comment_1")).toBe(false);
  });
});
