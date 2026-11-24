const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_789";
const ADMIN_EMAIL = "salmankamboh605@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function pastDate(msOffset = 1000) {
  return new Date(Date.now() - msOffset);
}

function validUserPayload(uid, username = "alice_creator") {
  const now = pastDate(5000);
  return {
    userId: uid,
    username,
    displayName: "Alice Creator",
    bio: "Filming short vertical stories.",
    followersCount: 0,
    followingCount: 0,
    totalLikes: 0,
    privateAccount: false,
    accountStatus: "active",
    createdAt: now,
    updatedAt: now,
  };
}

function validVideoPayload(uid, username = "alice_creator") {
  const now = pastDate(5000);
  return {
    userId: uid,
    creatorUsername: username,
    caption: "Neon night ride in downtown!",
    description: "Shot on phone with Neon Cyber filter.",
    hashtags: ["nightlife", "neon", "salmanvideo"],
    videoUrl: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
    audioTrackTitle: "Midnight Pulse",
    audioArtist: "Salman Sound Studio",
    filterPreset: "NEON_CYBER",
    overlayText: "NIGHT RIDE",
    trimStartMs: 0,
    trimEndMs: 15000,
    volumeLevel: 0.85,
    privacy: "PUBLIC",
    moderationStatus: "APPROVED",
    likesCount: 0,
    commentsCount: 0,
    sharesCount: 0,
    viewsCount: 0,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user: denied all reads and writes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("videos").where("privacy", "==", "PUBLIC").where("moderationStatus", "==", "APPROVED").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));
});

test("UserProfile: owner create/update works; PII private subcollection isolated; shadow & admin escalation blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));

  // Shadow update / status escalation rejected
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({
    accountStatus: "suspended",
    updatedAt: pastDate(1000),
  }));
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).update({
    isVerified: true,
    updatedAt: pastDate(1000),
  }));

  // Owner bio update succeeds
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).update({
    bio: "Updated creator bio",
    privateAccount: true,
    updatedAt: pastDate(1000),
  }));

  // Private PII doc scoped strictly to owner
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("info").set({
    userId: ALICE_UID,
    email: "alice@example.com",
    updatedAt: pastDate(1000),
  }));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("private").doc("info").get());

  // Filtered active user query succeeds; unfiltered users query fails for non-admin
  await assertSucceeds(bobDb.collection("users").where("accountStatus", "==", "active").get());
  await assertFails(bobDb.collection("users").get());
});

test("Videos: owner create, public filtered list, like/comment/follow, and cross-user protection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "alice")));
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).set(validUserPayload(BOB_UID, "bob")));

  // Create video (NO @DocumentId id key!)
  await assertSucceeds(aliceDb.collection("videos").doc("vid_1").set(validVideoPayload(ALICE_UID, "alice")));

  // Reject payload containing "id" field
  await assertFails(aliceDb.collection("videos").doc("vid_bad").set({
    ...validVideoPayload(ALICE_UID, "alice"),
    id: "vid_bad",
  }));

  // Filtered public feed query succeeds; unfiltered query fails
  await assertSucceeds(
    bobDb.collection("videos")
      .where("privacy", "==", "PUBLIC")
      .where("moderationStatus", "==", "APPROVED")
      .get()
  );
  await assertFails(bobDb.collection("videos").get());

  // Bob cannot hijack Alice's video caption
  await assertFails(bobDb.collection("videos").doc("vid_1").update({
    caption: "Hacked",
    updatedAt: pastDate(500),
  }));

  // Bob can like Alice's video & increment likesCount by 1
  await assertSucceeds(bobDb.collection("videos").doc("vid_1").collection("likes").doc(BOB_UID).set({
    userId: BOB_UID,
    videoId: "vid_1",
    createdAt: pastDate(500),
  }));
  await assertSucceeds(bobDb.collection("videos").doc("vid_1").update({
    likesCount: 1,
    updatedAt: pastDate(500),
  }));

  // Bob can comment & reply
  await assertSucceeds(bobDb.collection("videos").doc("vid_1").collection("comments").doc("c_1").set({
    videoId: "vid_1",
    userId: BOB_UID,
    username: "bob",
    text: "Awesome edit!",
    likesCount: 0,
    createdAt: pastDate(500),
    updatedAt: pastDate(500),
  }));
  await assertSucceeds(
    bobDb.collection("videos").doc("vid_1").collection("comments").where("videoId", "==", "vid_1").get()
  );

  // Follow edge
  await assertSucceeds(bobDb.collection("follows").doc(`${BOB_UID}_${ALICE_UID}`).set({
    followerId: BOB_UID,
    followingId: ALICE_UID,
    followerUsername: "bob",
    followingUsername: "alice",
    createdAt: pastDate(500),
  }));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).update({
    followersCount: 1,
    updatedAt: pastDate(400),
  }));
});

test("Direct messages, notifications, reports & admin moderation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_1", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  const realAdminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID, "alice")));
  await assertSucceeds(aliceDb.collection("videos").doc("vid_mod").set(validVideoPayload(ALICE_UID, "alice")));

  const convId = `${ALICE_UID}_${BOB_UID}`;
  await assertSucceeds(aliceDb.collection("conversations").doc(convId).set({
    participantIds: [ALICE_UID, BOB_UID],
    participantNames: { [ALICE_UID]: "alice", [BOB_UID]: "bob" },
    lastMessage: "Hey!",
    lastSenderId: ALICE_UID,
    createdAt: pastDate(500),
    updatedAt: pastDate(500),
  }));
  await assertSucceeds(bobDb.collection("conversations").doc(convId).collection("messages").doc("m_1").set({
    conversationId: convId,
    senderId: BOB_UID,
    participantIds: [ALICE_UID, BOB_UID],
    text: "Loved your video!",
    createdAt: pastDate(400),
  }));
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages")
      .where("participantIds", "array-contains", ALICE_UID)
      .get()
  );

  // Notification
  await assertSucceeds(bobDb.collection("users").doc(ALICE_UID).collection("notifications").doc("n_1").set({
    recipientId: ALICE_UID,
    actorId: BOB_UID,
    actorUsername: "bob",
    type: "LIKE",
    videoId: "vid_mod",
    message: "bob liked your video",
    read: false,
    createdAt: pastDate(400),
  }));

  // Report creation
  await assertSucceeds(bobDb.collection("reports").doc("rep_1").set({
    reporterId: BOB_UID,
    targetType: "VIDEO",
    targetId: "vid_mod",
    reason: "Spam",
    status: "OPEN",
    createdAt: pastDate(400),
    updatedAt: pastDate(400),
  }));

  // Spoofed unverified admin email MUST fail
  await assertFails(spoofedAdminDb.collection("reports").doc("rep_1").update({
    status: "RESOLVED",
    updatedAt: pastDate(200),
  }));

  // Verified admin succeeds in moderating report and video
  await assertSucceeds(realAdminDb.collection("reports").doc("rep_1").update({
    status: "RESOLVED",
    updatedAt: pastDate(200),
  }));
  await assertSucceeds(realAdminDb.collection("videos").doc("vid_mod").update({
    moderationStatus: "REMOVED",
    updatedAt: pastDate(200),
  }));
});
