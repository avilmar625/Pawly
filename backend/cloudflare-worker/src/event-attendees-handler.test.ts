import { afterEach, describe, expect, it, vi } from "vitest";
import { handleEventAttendees, handleRelationships } from "./index";

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
          { name: "events/event-1/interested/organizer" },
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
      if (url.endsWith("/users/blocked")) return new Response("missing", { status: 404 });
      if (url.endsWith("/users/organizer")) return Response.json({ fields: { username: { stringValue: "host" } } });
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
      attendees: [{ uid: "organizer", displayName: "Usuario privado", username: "host", photoUrl: "", isPublic: false }, {
        uid: "visible", isPublic: false, displayName: "Persona visible",
        username: "visible", photoUrl: "https://images.example/avatar.jpg"
      }],
      nextPageToken: "cursor-2"
    });
    expect(calls).toHaveLength(9);
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

it("exposes only minimal identities to the owner and counts all relationships", async () => {
  const request = (body: unknown) => new Request("https://worker.test/v1/me/relationships", { method: "POST", body: JSON.stringify(body) });
  const mock = vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.endsWith("/users/organizer:runAggregationQuery")) return Response.json([{ result: { aggregateFields: { total: { integerValue: "2" } } } }]);
    if (url.includes("/users/organizer/followers?")) return Response.json({ documents: [{ name: "users/organizer/followers/private" }, { name: "users/organizer/followers/deleted" }] });
    if (url.endsWith("/users/private")) return Response.json({ fields: { username: { stringValue: "private" }, email: { stringValue: "secret" } } });
    return new Response(null, { status: 404 });
  });
  vi.stubGlobal("fetch", mock);
  expect((await handleRelationships(request({ uid: "other", relation: "followers" }), env, dependencies)).status).toBe(400);
  expect(mock).not.toHaveBeenCalled();
  const response = await handleRelationships(request({ relation: "followers" }), env, dependencies);
  expect(await response.json()).toEqual({ total: 2, nextPageToken: "", people: [{ uid: "private", isPublic: false, username: "private", displayName: "Usuario privado", photoUrl: "" }] });
});
