import { afterEach, describe, expect, it, vi } from "vitest";
import { handleCommunityPlaceSubmission } from "./index";

const env = { FIREBASE_PROJECT_ID: "pawly-83dca" } as any;
const validBody = {
  submissionId: "12345678-1234-4234-8234-123456789abc",
  name: "Playa canina",
  category: "Playa",
  location: "Málaga",
  description: "Una playa amplia y tranquila para pasear con perros.",
  latitude: 36.7,
  longitude: -4.4
};
const dependencies = {
  verifyUser: vi.fn(async () => "verified-user-1"),
  getAccessToken: vi.fn(async () => "worker-access-token")
};

afterEach(() => vi.unstubAllGlobals());

describe("POST /v1/community-places", () => {
  it("publishes a verified contribution and never accepts a client-selected author or status", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const method = init?.method ?? "GET";
      if (url.endsWith("/users/verified-user-1")) return Response.json({ fields: {} });
      if (method === "GET" && url.includes("/communityPlaces/")) {
        return new Response("not found", { status: 404 });
      }
      if (method === "POST" && url.includes("/communityPlaceSubmissionLimits?documentId=")) {
        return new Response("{}", { status: 200 });
      }
      if (method === "POST" && url.includes("/communityPlaces?documentId=")) {
        const body = JSON.parse(String(init?.body));
        expect(body.fields.authorUid.stringValue).toBe("verified-user-1");
        expect(body.fields.status.stringValue).toBe("approved");
        expect(body.fields.latitude.doubleValue).toBe(36.7);
        return new Response("{}", { status: 201 });
      }
      throw new Error("Unexpected Worker subrequest: " + method + " " + url);
    });
    vi.stubGlobal("fetch", fetchMock);

    const response = await handleCommunityPlaceSubmission(
      new Request("https://pawly-api.test/v1/community-places", {
        method: "POST",
        headers: { authorization: "Bearer test-token", "content-type": "application/json" },
        body: JSON.stringify(validBody)
      }),
      env,
      dependencies
    );
    expect(response.status).toBe(201);
    await expect(response.json()).resolves.toMatchObject({ status: "approved" });
  });

  it("enforces the atomic one-proposal-per-day limit", async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const method = init?.method ?? "GET";
      if (url.endsWith("/users/verified-user-1")) return Response.json({ fields: {} });
      if (method === "GET" && url.includes("/communityPlaces/")) {
        return new Response("not found", { status: 404 });
      }
      if (method === "POST" && url.includes("/communityPlaceSubmissionLimits?documentId=")) {
        return new Response("already reserved", { status: 409 });
      }
      throw new Error("Place must not be created after quota conflict.");
    });
    vi.stubGlobal("fetch", fetchMock);
    const response = await handleCommunityPlaceSubmission(
      new Request("https://pawly-api.test/v1/community-places", {
        method: "POST",
        headers: { authorization: "Bearer test-token", "content-type": "application/json" },
        body: JSON.stringify({ ...validBody, submissionId: "22345678-1234-4234-8234-123456789abc" })
      }),
      env,
      dependencies
    );
    expect(response.status).toBe(429);
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it("returns an existing pending proposal idempotently on client retry", async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({
      fields: {
        authorUid: { stringValue: "verified-user-1" },
        status: { stringValue: "pending" }
      }
    }), { status: 200, headers: { "content-type": "application/json" } }));
    vi.stubGlobal("fetch", fetchMock);
    const response = await handleCommunityPlaceSubmission(
      new Request("https://pawly-api.test/v1/community-places", {
        method: "POST",
        headers: { authorization: "Bearer test-token", "content-type": "application/json" },
        body: JSON.stringify(validBody)
      }),
      env,
      dependencies
    );
    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toMatchObject({ status: "pending", duplicate: true });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("rejects unexpected fields and invalid coordinates before any Firestore request", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const response = await handleCommunityPlaceSubmission(
      new Request("https://pawly-api.test/v1/community-places", {
        method: "POST",
        headers: { authorization: "Bearer test-token", "content-type": "application/json" },
        body: JSON.stringify({ ...validBody, status: "approved", latitude: 95 })
      }),
      env,
      dependencies
    );
    expect(response.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
