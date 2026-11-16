import { describe, expect, it } from "vitest";
import { handlePhoneVerification, handlePhoneDiscoveryMatches } from "./index";
import { afterEach, vi } from "vitest";

const env = {
  FIREBASE_PROJECT_ID: "pawly-83dca",
  FIREBASE_CLIENT_EMAIL: "worker@example.invalid",
  FIREBASE_PRIVATE_KEY: "not-used-by-these-tests"
} as any;

afterEach(() => vi.unstubAllGlobals());

describe("phone discovery request handlers", () => {
  it("rejects phone verification without a verified Firebase session", async () => {
    const response = await handlePhoneVerification(
      new Request("https://worker.test/v1/phone/verify", {
        method: "POST", headers: { "content-type": "application/json" },
        body: JSON.stringify({ phoneToken: "x".repeat(120) })
      }),
      env,
      { verifyUser: async () => null }
    );
    expect(response.status).toBe(401);
  });

  it("rejects malformed or invalid PNV tokens before creating directory records", async () => {
    const response = await handlePhoneVerification(
      new Request("https://worker.test/v1/phone/verify", {
        method: "POST", headers: { "content-type": "application/json" },
        body: JSON.stringify({ phoneToken: "x".repeat(120) })
      }),
      env,
      {
        verifyUser: async () => "verified-user",
        verifyPnv: async () => { throw new Error("Invalid signature"); }
      }
    );
    expect(response.status).toBe(400);
    expect(await response.json()).toMatchObject({ error: "Invalid signature" });
  });

  it("stores only an HMAC phone index and consumes each signed PNV token once", async () => {
    const calls: Array<{ url: string; method: string; body: any }> = [];
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      const method = init?.method ?? "GET";
      const body = init?.body ? JSON.parse(String(init.body)) : null;
      calls.push({ url, method, body });
      if (url.includes("/phoneVerificationTokens?documentId=token-unique") && method === "POST") {
        return new Response("{}", { status: 201 });
      }
      if (url.endsWith("/users/verified-user/privateData/phoneDiscovery") && method === "GET") {
        return new Response("missing", { status: 404 });
      }
      if (url.endsWith("/phoneDirectory/opaque-hmac") && method === "GET") {
        return new Response("missing", { status: 404 });
      }
      if (url.includes("/phoneDirectory?documentId=opaque-hmac") && method === "POST") {
        return new Response("{}", { status: 201 });
      }
      if (url.endsWith("/users/verified-user/privateData/phoneDiscovery") && method === "PATCH") {
        return new Response("{}", { status: 200 });
      }
      throw new Error("Unexpected request: " + method + " " + url);
    });
    vi.stubGlobal("fetch", fetchMock);
    const response = await handlePhoneVerification(
      new Request("https://worker.test/v1/phone/verify", {
        method: "POST", headers: { "content-type": "application/json" },
        body: JSON.stringify({ phoneToken: "signed-token".repeat(20) })
      }),
      env,
      {
        verifyUser: async () => "verified-user",
        getAccessToken: async () => "worker-token",
        verifyPnv: async () => ({
          phone: "+34612345678", testNumber: false,
          jti: "token-unique", expiresAt: new Date("2026-11-16T11:00:00Z")
        }),
        hashPhone: async () => "opaque-hmac"
      }
    );
    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toMatchObject({ verified: true, enabled: false });
    const indexWrite = calls.find(call => call.url.includes("/phoneDirectory?documentId="));
    expect(indexWrite?.body.fields.phoneHash.stringValue).toBe("opaque-hmac");
    expect(JSON.stringify(indexWrite?.body)).not.toContain("+34612345678");
  });

  it("rejects contact batches larger than 30 before making Firestore requests", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const response = await handlePhoneDiscoveryMatches(
      new Request("https://worker.test/v1/phone/discovery/matches", {
        method: "POST", headers: { "content-type": "application/json" },
        body: JSON.stringify({ phoneNumbers: Array.from({ length: 31 }, (_, i) => `+346123456${i % 10}`) })
      }),
      env,
      { verifyUser: async () => "verified-user" }
    );
    expect(response.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("requires a verified Firebase session before searching contacts", async () => {
    const response = await handlePhoneDiscoveryMatches(
      new Request("https://worker.test/v1/phone/discovery/matches", {
        method: "POST", headers: { "content-type": "application/json" },
        body: JSON.stringify({ phoneNumbers: ["+34612345678"] })
      }),
      env
    );
    expect(response.status).toBe(401);
  });
});
