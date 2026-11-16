import { describe, expect, it } from "vitest";
import { handlePhoneVerification, handlePhoneDiscoveryMatches } from "./index";

const env = {
  FIREBASE_PROJECT_ID: "pawly-83dca",
  FIREBASE_CLIENT_EMAIL: "worker@example.invalid",
  FIREBASE_PRIVATE_KEY: "not-used-by-these-tests"
} as any;

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
