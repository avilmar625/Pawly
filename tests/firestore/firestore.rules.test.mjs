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
    await setDoc(doc(db, "sharedPets/owner_pet"), {
      id: "owner_pet", ownerUid: "owner", name: "Chispita"
    });
    await setDoc(doc(db, "sharedPets/owner_pet/members/owner"), { role: "owner" });
    await setDoc(doc(db, "sharedPets/owner_pet/members/editor"), { role: "editor" });
    await setDoc(doc(db, "sharedPets/owner_pet/members/reader"), { role: "reader" });
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
  await assertSucceeds(getDoc(doc(authorDb, "communityPlaces/pending-place")));
  const ownPlaces = query(collection(authorDb, "communityPlaces"), where("authorUid", "==", "author"));
  await assertSucceeds(getDocs(ownPlaces));
  await assertSucceeds(updateDoc(doc(authorDb, "communityPlaces/approved-place"), {
    description: "Información actualizada por el autor.",
    status: "pending"
  }));
  await assertFails(updateDoc(doc(authorDb, "communityPlaces/pending-place"), {
    status: "approved"
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
  await assertFails(getDocs(collection(organizerDb, "events/event-1/interested")));
  const readerDb = env.authenticatedContext("reader", { email_verified: true }).firestore();
  await assertFails(getDoc(doc(readerDb, "events/event-1/interested/attendee")));
  await assertFails(getDocs(collection(readerDb, "events/event-1/interested")));
});

test("shared pet owners can change editor and reader roles but collaborators cannot", async () => {
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), "petInvitations/invite-editor"), {
      sharedPetId: "owner_pet", petName: "Chispita", ownerUid: "owner", ownerName: "Owner",
      inviteeUid: "editor", inviteeUsername: "editor", role: "editor", status: "accepted",
      createdAt: new Date()
    });
  });
  const ownerDb = env.authenticatedContext("owner", { email_verified: true }).firestore();
  const editorDb = env.authenticatedContext("editor", { email_verified: true }).firestore();
  const readerDb = env.authenticatedContext("reader", { email_verified: true }).firestore();
  await assertSucceeds(updateDoc(doc(ownerDb, "sharedPets/owner_pet/members/editor"), { role: "reader" }));
  await assertSucceeds(updateDoc(doc(ownerDb, "petInvitations/invite-editor"), { role: "reader" }));
  await assertFails(updateDoc(doc(editorDb, "petInvitations/invite-editor"), { role: "editor" }));
  await assertFails(updateDoc(doc(editorDb, "sharedPets/owner_pet/members/reader"), { role: "editor" }));
  await assertFails(updateDoc(doc(ownerDb, "sharedPets/owner_pet/members/owner"), { role: "reader" }));
  await assertFails(setDoc(doc(editorDb, "sharedPets/owner_pet/tasks/not-allowed"), {
    id: "not-allowed", title: "No escribir", responsible: "", frequency: "Diario",
    completed: false, dose: "", time: "", completedOn: "", action: "Otro"
  }));
  await assertSucceeds(updateDoc(doc(ownerDb, "sharedPets/owner_pet/members/editor"), { role: "editor" }));
  await assertSucceeds(deleteDoc(doc(ownerDb, "sharedPets/owner_pet/members/reader")));
  await assertFails(getDoc(doc(readerDb, "sharedPets/owner_pet")));
});

test("shared agenda reminders are live collaborative records with reader-only access", async () => {
  const editorDb = env.authenticatedContext("editor", { email_verified: true }).firestore();
  const readerDb = env.authenticatedContext("reader", { email_verified: true }).firestore();
  const ownerDb = env.authenticatedContext("owner", { email_verified: true }).firestore();
  await assertSucceeds(setDoc(doc(editorDb, "sharedPets/owner_pet/agenda/reminder-1"), {
    id: "reminder-1", title: "Veterinario", date: 1900000000000,
    category: "Veterinario", createdBy: "editor", updatedAt: serverTimestamp()
  }));
  await assertSucceeds(getDoc(doc(readerDb, "sharedPets/owner_pet/agenda/reminder-1")));
  await assertFails(setDoc(doc(readerDb, "sharedPets/owner_pet/agenda/reminder-2"), {
    id: "reminder-2", title: "No permitido", date: 1900000000000,
    category: "Personal", createdBy: "reader", updatedAt: serverTimestamp()
  }));
  await assertSucceeds(updateDoc(doc(ownerDb, "sharedPets/owner_pet/agenda/reminder-1"), {
    title: "Veterinario actualizado", updatedAt: serverTimestamp()
  }));
  await assertFails(updateDoc(doc(editorDb, "sharedPets/owner_pet/agenda/reminder-1"), {
    createdBy: "reader", updatedAt: serverTimestamp()
  }));
  await assertFails(setDoc(doc(editorDb, "sharedPets/owner_pet/agenda/invalid-category"), {
    id: "invalid-category", title: "Recordatorio", date: 1900000000000,
    category: "Hack", createdBy: "editor", updatedAt: serverTimestamp()
  }));
  await assertSucceeds(deleteDoc(doc(editorDb, "sharedPets/owner_pet/agenda/reminder-1")));
});

test("shared profile and diary are editable by editors and read-only for readers", async () => {
  const editorDb = env.authenticatedContext("editor", { email_verified: true }).firestore();
  const readerDb = env.authenticatedContext("reader", { email_verified: true }).firestore();
  await assertSucceeds(setDoc(doc(editorDb, "sharedPets/owner_pet/profile/current"), {
    petId: "pet", name: "Chispita", photoUrl: "https://images.example/chispita.jpg"
  }));
  await assertFails(setDoc(doc(readerDb, "sharedPets/owner_pet/profile/current"), {
    petId: "pet", name: "Chispita 2", photoUrl: null
  }));
  await assertSucceeds(setDoc(doc(editorDb, "sharedPets/owner_pet/diary/entry_000"), {
    id: "entry_000", text: "Primer paseo juntos", position: 0
  }));
  await assertSucceeds(setDoc(doc(editorDb, "sharedPets/owner_pet/diary/entry_with_photos"), {
    id: "entry_with_photos", text: "x".repeat(12000), position: 0
  }));
  await assertFails(setDoc(doc(readerDb, "sharedPets/owner_pet/diary/entry_001"), {
    id: "entry_001", text: "No debería poder escribir", position: 1
  }));
});

test("the reciprocal block index can only be written by the blocker and read by its target", async () => {
  const blockerDb = env.authenticatedContext("blocker", { email_verified: true }).firestore();
  const targetDb = env.authenticatedContext("target", { email_verified: true }).firestore();
  const strangerDb = env.authenticatedContext("stranger", { email_verified: true }).firestore();
  await assertSucceeds(setDoc(doc(blockerDb, "users/target/blockedBy/blocker"), {
    uid: "blocker", createdAt: serverTimestamp()
  }));
  await assertSucceeds(getDoc(doc(targetDb, "users/target/blockedBy/blocker")));
  await assertFails(getDoc(doc(strangerDb, "users/target/blockedBy/blocker")));
  await assertFails(setDoc(doc(targetDb, "users/target/blockedBy/forged"), {
    uid: "forged", createdAt: serverTimestamp()
  }));
});

test("blocking either direction prevents private profile reads", async () => {
  await env.withSecurityRulesDisabled(async context => {
    const db = context.firestore();
    await setDoc(doc(db, "users/blocker"), { uid: "blocker", isPublic: true });
    await setDoc(doc(db, "users/blocked-user"), { uid: "blocked-user", isPublic: true });
    await setDoc(doc(db, "users/blocker/blocked/blocked-user"), { uid: "blocked-user" });
    await setDoc(doc(db, "users/blocked-user/blocked/blocker"), { uid: "blocker" });
  });
  const blockerDb = env.authenticatedContext("blocker", { email_verified: true }).firestore();
  const blockedDb = env.authenticatedContext("blocked-user", { email_verified: true }).firestore();
  await assertFails(getDoc(doc(blockerDb, "users/blocked-user")));
  await assertFails(getDoc(doc(blockedDb, "users/blocker")));
});

test("shared care logs are visible to members and can only be created by editors or owners", async () => {
  const editorDb = env.authenticatedContext("editor", { email_verified: true }).firestore();
  await assertSucceeds(setDoc(doc(editorDb, "sharedPets/owner_pet/careLogs/log-1"), {
    id: "log-1", taskTitle: "Medicación", action: "Medicación",
    actorUid: "editor", actorName: "Cuidador", occurredAt: serverTimestamp(), note: "Dosis de la mañana",
    distanceKm: 1.2, mapUrl: "https://maps.google.com/?q=36.72,-4.42",
    description: "Paseo tranquilo", walker: "Cuidador",
    photoUrls: ["https://images.example/pet-walk.jpg"]
  }));
  const readerDb = env.authenticatedContext("reader", { email_verified: true }).firestore();
  await assertSucceeds(getDocs(collection(readerDb, "sharedPets/owner_pet/careLogs")));
  await assertFails(setDoc(doc(readerDb, "sharedPets/owner_pet/careLogs/log-2"), {
    id: "log-2", taskTitle: "Paseo", action: "Paseo",
    actorUid: "reader", actorName: "Lector", occurredAt: serverTimestamp(), note: ""
  }));
});

test("shared care routines accept optional ISO weekdays and reject invalid schedules", async () => {
  const editorDb = env.authenticatedContext("editor", { email_verified: true }).firestore();
  // Legacy documents remain valid without the optional field.
  await assertSucceeds(setDoc(doc(editorDb, "sharedPets/owner_pet/tasks/medication"), {
    id: "medication", title: "Antibiótico", responsible: "Alex", frequency: "Diario",
    completed: false, dose: "1 comprimido", time: "08:30", completedOn: "", action: "Medicación"
  }));
  await assertSucceeds(setDoc(doc(editorDb, "sharedPets/owner_pet/tasks/walk-weekly"), {
    id: "walk-weekly", title: "Paseo largo", responsible: "Alex", frequency: "Semanal",
    completed: false, dose: "", time: "", completedOn: "", action: "Paseo",
    daysOfWeek: [1, 3, 5]
  }));
  await assertFails(setDoc(doc(editorDb, "sharedPets/owner_pet/tasks/invalid-weekday"), {
    id: "invalid-weekday", title: "Rutina inválida", responsible: "Alex", frequency: "Semanal",
    completed: false, dose: "", time: "", completedOn: "", action: "Otro",
    daysOfWeek: [1, 8]
  }));
  await assertFails(setDoc(doc(editorDb, "sharedPets/owner_pet/tasks/non-weekly-days"), {
    id: "non-weekly-days", title: "Rutina diaria", responsible: "Alex", frequency: "Diario",
    completed: false, dose: "", time: "", completedOn: "", action: "Otro",
    daysOfWeek: [1, 3]
  }));
  const readerDb = env.authenticatedContext("reader", { email_verified: true }).firestore();
  await assertFails(setDoc(doc(readerDb, "sharedPets/owner_pet/tasks/other"), {
    id: "other", title: "Paseo", responsible: "", frequency: "Diario",
    completed: false, dose: "", time: "", completedOn: "", action: "Paseo"
  }));
});

test("private passport accepts all supported health fields while remaining owner-only", async () => {
  const ownerDb = env.authenticatedContext("passport-owner", { email_verified: false }).firestore();
  await assertSucceeds(setDoc(doc(ownerDb, "users/passport-owner/petCare/nala/passport/entry-1"), {
    id: "entry-1", type: "Vacuna", title: "Rabia", detail: "Primera dosis",
    date: "2026-11-17", product: "Vacuna", dose: "1 dosis", frequency: "Anual",
    clinic: "Clínica", nextDoseDate: "2027-11-17", status: "REALIZADO",
    reminderEnabled: true, reminderTime: "09:00", createdAtOrder: 1234
  }));
  await assertSucceeds(setDoc(doc(ownerDb, "users/passport-owner/petCare/nala/passport/legacy"), {
    id: "legacy", type: "Peso", title: "13 kg", detail: "", date: "2026-11-17"
  }));
  const otherDb = env.authenticatedContext("someone-else", { email_verified: true }).firestore();
  await assertFails(getDoc(doc(otherDb, "users/passport-owner/petCare/nala/passport/entry-1")));
  await assertFails(setDoc(doc(ownerDb, "users/passport-owner/petCare/nala/passport/extra"), {
    id: "extra", type: "Peso", title: "13 kg", detail: "", date: "2026-11-17", unexpected: true
  }));
});

test("shared passport supports full health records for editors and rejects readers", async () => {
  const editorDb = env.authenticatedContext("editor", { email_verified: true }).firestore();
  await assertSucceeds(setDoc(doc(editorDb, "sharedPets/owner_pet/passport/vaccine"), {
    id: "vaccine", type: "Vacuna", title: "Rabia", detail: "",
    date: "2026-11-17", product: "Vacuna", dose: "1", frequency: "Anual",
    clinic: "", nextDoseDate: "", status: "REALIZADO",
    reminderEnabled: false, reminderTime: "09:00", createdAtOrder: 1234
  }));
  const readerDb = env.authenticatedContext("reader", { email_verified: true }).firestore();
  await assertFails(setDoc(doc(readerDb, "sharedPets/owner_pet/passport/other"), {
    id: "other", type: "Peso", title: "13 kg", detail: "", date: "2026-11-17"
  }));
});

test("private care logs accept walk details, map links and photo URLs", async () => {
  const ownerDb = env.authenticatedContext("walk-owner", { email_verified: false }).firestore();
  await assertSucceeds(setDoc(doc(ownerDb, "users/walk-owner/petCare/nala/careLogs/walk-1"), {
    id: "walk-1", taskTitle: "Paseo", action: "Paseo", actorUid: "walk-owner",
    actorName: "Alex", occurredAt: serverTimestamp(), note: "Buen paseo",
    distanceKm: 2.4, mapUrl: "https://maps.google.com/?q=36.72,-4.42",
    description: "Por el parque", walker: "Alex",
    photoUrls: ["https://images.example/walk.jpg"]
  }));
  await assertFails(setDoc(doc(ownerDb, "users/walk-owner/petCare/nala/careLogs/invalid"), {
    id: "invalid", taskTitle: "Paseo", action: "Paseo", actorUid: "walk-owner",
    actorName: "Alex", occurredAt: serverTimestamp(), note: "",
    distanceKm: 2000, mapUrl: "", description: "", walker: "", photoUrls: []
  }));
});

test("an unverified account can save only its own lost-pet alert preferences", async () => {
  const ownerDb = env.authenticatedContext("lost-owner", { email_verified: false }).firestore();
  await assertSucceeds(setDoc(doc(ownerDb, "lostPetSubscriptions/lost-owner"), {
    uid: "lost-owner", enabled: true, radiusKm: 20, latCell: 3672,
    lonCell: -442, updatedAt: serverTimestamp()
  }));
  await assertFails(setDoc(doc(ownerDb, "lostPetSubscriptions/other-user"), {
    uid: "other-user", enabled: true, radiusKm: 20, latCell: 3672,
    lonCell: -442, updatedAt: serverTimestamp()
  }));
  await assertFails(setDoc(doc(ownerDb, "lostPetSubscriptions/lost-owner"), {
    uid: "lost-owner", enabled: true, radiusKm: 17, latCell: 3672,
    lonCell: -442, updatedAt: serverTimestamp()
  }));
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

test("Cloudinary asset indexes can only point inside the caller's own Pawly upload folders", async () => {
  const db = env.authenticatedContext("asset-owner", { email_verified: false }).firestore();
  await assertSucceeds(setDoc(doc(db, "cloudinaryAssets/owned"), {
    uid: "asset-owner", publicId: "pawly/diary-photos/asset-owner/photo-1",
    resourceType: "image", secureUrl: "https://res.cloudinary.com/pawly/image/upload/photo-1",
    createdAt: serverTimestamp()
  }));
  await assertFails(setDoc(doc(db, "cloudinaryAssets/insecure-url"), {
    uid: "asset-owner", publicId: "pawly/diary-photos/asset-owner/photo-4",
    resourceType: "image", secureUrl: "http://example.com/photo.jpg", createdAt: serverTimestamp()
  }));
  await assertFails(setDoc(doc(db, "cloudinaryAssets/foreign"), {
    uid: "asset-owner", publicId: "pawly/diary-photos/someone-else/photo-2",
    resourceType: "image", createdAt: serverTimestamp()
  }));
  await assertFails(setDoc(doc(db, "cloudinaryAssets/untrusted-folder"), {
    uid: "asset-owner", publicId: "pawly/other/asset-owner/photo-3",
    resourceType: "image", createdAt: serverTimestamp()
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


test("pet invitation list queries are scoped to the owner or invitee required by the security rule", async () => {
  const ownerDb = env.authenticatedContext("owner", { email_verified: true }).firestore();
  const inviteeDb = env.authenticatedContext("invitee", { email_verified: true }).firestore();

  await env.withSecurityRulesDisabled(async context => {
    await setDoc(doc(context.firestore(), "petInvitations/owner-invite"), {
      sharedPetId: "owner_pet",
      ownerUid: "owner",
      inviteeUid: "invitee",
      status: "pending",
      createdAt: new Date()
    });
  });

  const ownerScopedQuery = query(
    collection(ownerDb, "petInvitations"),
    where("sharedPetId", "==", "owner_pet"),
    where("ownerUid", "==", "owner"),
    where("inviteeUid", "==", "invitee"),
    where("status", "==", "pending")
  );
  await assertSucceeds(getDocs(ownerScopedQuery));

  const unscopedOwnerQuery = query(
    collection(ownerDb, "petInvitations"),
    where("sharedPetId", "==", "owner_pet"),
    where("inviteeUid", "==", "invitee"),
    where("status", "==", "pending")
  );
  await assertFails(getDocs(unscopedOwnerQuery));

  const inviteeScopedQuery = query(
    collection(inviteeDb, "petInvitations"),
    where("inviteeUid", "==", "invitee"),
    where("status", "==", "pending")
  );
  await assertSucceeds(getDocs(inviteeScopedQuery));
});


test("an owner can atomically create a shared pet, its membership and its initial profile", async () => {
  const db = env.authenticatedContext("owner", { email_verified: true }).firestore();
  const batch = writeBatch(db);
  const root = doc(db, "sharedPets/owner_newpet");

  batch.set(root, {
    id: "owner_newpet",
    petId: "newpet",
    name: "Chispita",
    ownerUid: "owner",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  });
  batch.set(doc(db, "sharedPets/owner_newpet/members/owner"), {
    uid: "owner",
    role: "owner",
    displayName: "Owner",
    username: "owner",
    joinedAt: serverTimestamp()
  });
  batch.set(doc(db, "sharedPets/owner_newpet/profile/current"), {
    petId: "newpet",
    name: "Chispita",
    photoUrl: null,
    updatedAt: serverTimestamp()
  });

  await assertSucceeds(batch.commit());
  await assertSucceeds(getDoc(doc(db, "sharedPets/owner_newpet/profile/current")));
});

test("events accept legacy documents and an optional registered organizer photo", async () => {
  const db = env.authenticatedContext("author", { email_verified: true }).firestore();
  const event = {
    title: "Paseo", description: "Ruta para mascotas.", category: "Ruta", location: "Parque",
    dateText: "Mañana", startAt: new Date(Date.now() + 86400000), timeZoneId: "Europe/Madrid",
    organizerUid: "author", organizerName: "Autor", interestedCount: 0, createdAt: serverTimestamp()
  };
  const photoUrl = "https://res.cloudinary.com/test/image/upload/v1/pawly/post-photos/author/test.jpg";
  const photoAssetId = "a".repeat(64);
  await assertSucceeds(setDoc(doc(db, "events/legacy"), event));
  await assertFails(setDoc(doc(db, "events/unregistered"), { ...event, photoUrl, photoAssetId }));
  await assertSucceeds(setDoc(doc(db, "cloudinaryAssets", photoAssetId), {
    uid: "author", resourceType: "image", publicId: "pawly/post-photos/author/test", secureUrl: photoUrl, createdAt: serverTimestamp()
  }));
  await assertSucceeds(setDoc(doc(db, "events/with-photo"), { ...event, photoUrl, photoAssetId }));
  await assertFails(setDoc(doc(db, "events/mismatch"), { ...event, photoUrl: photoUrl + "x", photoAssetId }));
  const other = env.authenticatedContext("other", { email_verified: true }).firestore();
  await assertFails(setDoc(doc(other, "events/wrong-owner"), { ...event, organizerUid: "other", photoUrl, photoAssetId }));
});
