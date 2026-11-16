import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import test, { after, before, beforeEach } from "node:test";
import assert from "node:assert/strict";
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails
} from "@firebase/rules-unit-testing";
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  writeBatch
} from "firebase/firestore";

const projectId = "demo-pawly";
let env;

before(async () => {
  const rules = readFileSync(resolve(process.cwd(), "../../firestore.rules"), "utf8");
  env = await initializeTestEnvironment({ projectId, firestore: { rules } });
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async context => {
    const db = context.firestore();
    await setDoc(doc(db, "communityPlaces/approved-place"), {
      name: "Playa canina", category: "Playa", location: "Málaga",
      latitude: 36.7, longitude: -4.4, description: "Una playa para perros con acceso sencillo.",
      authorUid: "author", status: "approved", createdAt: new Date()
    });
    await setDoc(doc(db, "communityPlaces/pending-place"), {
      name: "Parque canino", category: "Parque", location: "Málaga",
      latitude: 36.72, longitude: -4.42, description: "Un parque pendiente de revisión.",
      authorUid: "author", status: "pending", createdAt: new Date()
    });
    await setDoc(doc(db, "events/event-1"), {
      title: "Paseo", description: "Paseo comunitario de prueba.",
      category: "Quedada", location: "Málaga", dateText: "Mañana",
      startAt: new Date(Date.now() + 86400000), timeZoneId: "Europe/Madrid",
      organizerUid: "author", organizerName: "Autor", interestedCount: 1, createdAt: new Date()
    });
    await setDoc(doc(db, "events/event-1/interested/attendee"), {
      uid: "attendee", createdAt: new Date()
    });
  });
});

after(async () => {
  await env.cleanup();
});

test("a new unverified email account can atomically create its private starter profile", async () => {
  const db = env.authenticatedContext("new-user", { email_verified: false }).firestore();
  const batch = writeBatch(db);
  batch.set(doc(db, "usernameReservations/new-user"), { username: "pawlyuser" });
  batch.set(doc(db, "usernames/pawlyuser"), { uid: "new-user" });
  batch.set(doc(db, "users/new-user"), {
    uid: "new-user",
    username: "pawlyuser",
    usernameLower: "pawlyuser",
    displayName: "pawlyuser",
    bio: "",
    photoUrl: "",
    isPublic: false,
    followersCount: 0,
    followingCount: 0,
    accountType: "personal",
    professionalCategory: "",
    professionalLocation: "",
    professionalDescription: "",
    professionalContactPublic: false,
    dogProfiles: [],
    createdAt: serverTimestamp()
  });
  await assertSucceeds(batch.commit());
});

test("only approved community places are readable by signed-in users", async () => {
  const db = env.authenticatedContext("reader", { email_verified: true }).firestore();
  await assertSucceeds(getDoc(doc(db, "communityPlaces/approved-place")));
  await assertFails(getDoc(doc(db, "communityPlaces/pending-place")));
  const approvedQuery = query(collection(db, "communityPlaces"), where("status", "==", "approved"));
  await assertSucceeds(getDocs(approvedQuery));
});

test("clients cannot create a place directly; author can edit and delete their own place", async () => {
  const authorDb = env.authenticatedContext("author", { email_verified: true }).firestore();
  const newPlace = doc(authorDb, "communityPlaces/client-created");
  await assertFails(setDoc(newPlace, {
    name: "Lugar nuevo", category: "Parque", location: "Málaga",
    latitude: 36.7, longitude: -4.4, description: "Un lugar propuesto por un usuario.",
    authorUid: "author", status: "pending", createdAt: serverTimestamp()
  }));
  await assertSucceeds(updateDoc(doc(authorDb, "communityPlaces/pending-place"), {
    description: "Descripción corregida para la revisión."
  }));
  await assertSucceeds(deleteDoc(doc(authorDb, "communityPlaces/pending-place")));
  const otherDb = env.authenticatedContext("other", { email_verified: true }).firestore();
  await assertFails(deleteDoc(doc(otherDb, "communityPlaces/approved-place")));
});

test("verified users can report a real community place", async () => {
  const db = env.authenticatedContext("reporter", { email_verified: true }).firestore();
  await assertSucceeds(setDoc(doc(db, "reports/place-report"), {
    reporterId: "reporter", targetId: "approved-place", targetType: "place",
    reason: "La ubicación parece incorrecta.", status: "open", createdAt: serverTimestamp()
  }));
});

test("only the event organizer can list attendees; each member can read only their own RSVP", async () => {
  const attendeeDb = env.authenticatedContext("attendee", { email_verified: true }).firestore();
  await assertSucceeds(getDoc(doc(attendeeDb, "events/event-1/interested/attendee")));
  const organizerDb = env.authenticatedContext("author", { email_verified: true }).firestore();
  await assertSucceeds(getDocs(collection(organizerDb, "events/event-1/interested")));
  const readerDb = env.authenticatedContext("reader", { email_verified: true }).firestore();
  await assertFails(getDoc(doc(readerDb, "events/event-1/interested/attendee")));
  await assertFails(getDocs(collection(readerDb, "events/event-1/interested")));
});

test("phone discovery hashes and rate-limit reservations are inaccessible to clients", async () => {
  const db = env.authenticatedContext("verified-user", { email_verified: true }).firestore();
  await assertFails(getDoc(doc(db, "phoneDirectory/private-hash")));
  await assertFails(setDoc(doc(db, "phoneDirectory/forged-hash"), {
    uid: "verified-user", phoneHash: "forged-hash", enabled: true
  }));
  await assertFails(getDoc(doc(db, "phoneDiscoveryLimits/verified-user_20261116_0")));
  await assertFails(setDoc(doc(db, "phoneDiscoveryLimits/forged"), {
    uid: "verified-user", day: "20261116"
  }));
});

test("account deletion requests must be pending and belong to the caller", async () => {
  const db = env.authenticatedContext("requester", { email_verified: true }).firestore();
  await assertSucceeds(setDoc(doc(db, "accountDeletionRequests/requester"), {
    uid: "requester", email: "requester@example.test", requestedAt: serverTimestamp(),
    status: "pending", source: "android"
  }));
  await assertFails(setDoc(doc(db, "accountDeletionRequests/another"), {
    uid: "another", email: "another@example.test", requestedAt: serverTimestamp(),
    status: "pending", source: "android"
  }));
  await assertFails(setDoc(doc(db, "accountDeletionRequests/requester-2"), {
    uid: "requester", email: "requester@example.test", requestedAt: serverTimestamp(),
    status: "approved", source: "android"
  }));
});
