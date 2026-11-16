import { afterEach, describe, expect, it, vi } from "vitest";
import { handleEventAttendees } from "./index";

const env = { FIREBASE_PROJECT_ID: "pawly-83dca" } as any;
const dependencies = {
  verifyUser: vi.fn(async () => "organizer"),
  getAccessToken: vi.fn(async () => "worker-token")
};

afterEach(() => vi.unstubAllGlobals());

describe("POST /v1/events/attendees", () => {
  it("rejects requests without a verified Firebase user", async () => {
    const response = await handleEventAttendees(
      new Request("https://worker.test/v1/events/attendees", {
        method: "POST", body: JSON.stringify({ eventId: "event-1" })
      }),
      env,
      { verifyUser: async () => null, getAccessToken: dependencies.getAccessToken }
    );
    expect(response.status).toBe(401);
  });

  it("only returns attendees to the organizer and filters blocks in both directions", async () => {
    const calls: string[] = [];
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      calls.push(url);
      if (url.endsWith("/events/event-1")) {
        return new Response(JSON.stringify({ fields: { organizerUid: { stringValue: "organizer" } } }), { status: 200 });
      }
      if (url.includes("/events/event-1/interested?")) {
        expect(url).toContain("pageSize=12");
        expect(url).toContain("pageToken=cursor-1");
        return new Response(JSON.stringify({ documents: [
          { name: "projects/p/databases/(default)/documents/events/event-1/interested/visible", fields: { uid: { stringValue: "visible" } } },
          { name: "projects/p/databases/(default)/documents/events/event-1/interested/blocked", fields: { uid: { stringValue: "blocked" } } }
        ], nextPageToken: "cursor-2" }), { status: 200 });
      }
      if (url.endsWith("/users/organizer/blocked/visible") || url.endsWith("/users/visible/blocked/organizer")) {
        return new Response("missing", { status: 404 });
      }
      if (url.endsWith("/users/organizer/blocked/blocked")) {
        return new Response("missing", { status: 404 });
      }
      if (url.endsWith("/users/blocked/blocked/organizer")) {
        return new Response(JSON.stringify({ name: "blocked" }), { status: 200 });
      }
      if (url.endsWith("/users/visible")) {
        return new Response(JSON.stringify({ fields: {
          displayName: { stringValue: "Persona visible" },
          username: { stringValue: "visible" },
          photoUrl: { stringValue: "https://images.example/avatar.jpg" }
        } }), { status: 200 });
      }
      throw new Error("Unexpected subrequest: " + url);
    });
    vi.stubGlobal("fetch", fetchMock);

    const response = await handleEventAttendees(
      new Request("https://worker.test/v1/events/attendees", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ eventId: "event-1", pageToken: "cursor-1" })
      }),
      env,
      dependencies
    );
    expect(response.status).toBe(200);
    await expect(response.json()).resolves.toEqual({
      attendees: [{
        uid: "visible", displayName: "Persona visible",
        username: "visible", photoUrl: "https://images.example/avatar.jpg"
      }],
      nextPageToken: "cursor-2"
    });
    expect(calls).toHaveLength(8);
  });

  it("rejects a non-organizer before reading the attendee collection", async () => {
    const fetchMock = vi.fn(async () => new Response(JSON.stringify({
      fields: { organizerUid: { stringValue: "someone-else" } }
    }), { status: 200 }));
    vi.stubGlobal("fetch", fetchMock);
    const response = await handleEventAttendees(
      new Request("https://worker.test/v1/events/attendees", {
        method: "POST", body: JSON.stringify({ eventId: "event-1" })
      }),
      env,
      dependencies
    );
    expect(response.status).toBe(403);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
