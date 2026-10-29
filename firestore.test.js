const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-nexus-fc3834c6-deca-4e2b-b7db-ea6bca7d4ffb";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

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

test("Unauthenticated user: cannot read user profile or messages", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("messages").get());
});

test("PII Isolation: authenticated user cannot list /users or read another user's profile", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      email: "bob@example.com",
      creatorSupportVisited: true,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
});

test("Shadow Update / Ghost Field rejected on UserProfile create and update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      creatorSupportVisited: false,
      isAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      creatorSupportVisited: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      creatorSupportVisited: true,
      ghostField: "hack",
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      creatorSupportVisited: true,
      updatedAt: new Date(),
    })
  );
});

test("Cross-user isolation & client query alignment on creations, workflows, and clips", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("creations").doc("app_1").set({
      userId: ALICE_UID,
      title: "Neon Orbit Game",
      prompt: "Create an arcade space dodge game",
      category: "dropship",
      summary: "Interactive storefront & game",
      htmlCode: "<!DOCTYPE html><html><body><canvas></canvas></body></html>",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Instant rebrand update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("creations").doc("app_1").update({
      title: "PulseStore Pro",
      summary: "Rebranded dropshipping storefront",
      htmlCode: "<!DOCTYPE html><html><body><h1>PulseStore Pro</h1></body></html>",
      updatedAt: new Date(),
    })
  );

  // Bob cannot read Alice's creation
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("creations").doc("app_1").get()
  );

  // Alice can query her own creations with matching userId filter
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("creations").where("userId", "==", ALICE_UID).get()
  );

  // Identity spoofing rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("clips").doc("clip_1").set({
      userId: BOB_UID,
      title: "Spoof Clip",
      description: "Spoofed owner",
      prompt: "Make clip",
      sourceType: "generated",
      durationSec: 15,
      localFilePath: "/data/clip.mp4",
      youtubeStatus: "ready",
      tags: ["ai"],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid clip creation and YouTube status update succeed
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("clips").doc("clip_1").set({
      userId: ALICE_UID,
      title: "3 AI Productivity Tricks",
      description: "Created with Nexus Studio #shorts",
      prompt: "Create a 12s AI productivity clip",
      sourceType: "generated",
      durationSec: 12,
      localFilePath: "/data/clip_1.mp4",
      youtubeStatus: "ready",
      youtubeVideoId: null,
      tags: ["ai", "shorts"],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("clips").doc("clip_1").update({
      youtubeStatus: "uploaded",
      youtubeVideoId: "dQw4w9WgXcQ",
      updatedAt: new Date(),
    })
  );
});
