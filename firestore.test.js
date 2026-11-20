const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const NARSINH_UID = "uid_narsinh_1";
const VISHWARAJ_UID = "uid_vishwaraj_2";
const INTRUDER_UID = "uid_intruder_99";
const CIRCLE_ID = "narsinh_vishwaraj_duo";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTime(msAgo = 5000) {
  return new Date(Date.now() - msAgo);
}

function futureTime(msAhead = 60000) {
  return new Date(Date.now() + msAhead);
}

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

test("Unauthenticated user: denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(NARSINH_UID).get());
  await assertFails(unauthDb.collection("circles").doc(CIRCLE_ID).get());
  await assertFails(
    unauthDb
      .collection("circles")
      .doc(CIRCLE_ID)
      .collection("readers")
      .doc("reader_narsinh")
      .get()
  );
  await assertFails(
    unauthDb.collection("circles").doc(CIRCLE_ID).collection("sessions").get()
  );
});

test("Users collection: owner create, partial update, isolation, and anti-spoofing", async () => {
  const narsinhDb = testEnv.authenticatedContext(NARSINH_UID).firestore();
  const vishwarajDb = testEnv.authenticatedContext(VISHWARAJ_UID).firestore();
  const now = pastTime(10000);

  await assertSucceeds(
    narsinhDb.collection("users").doc(NARSINH_UID).set({
      userId: NARSINH_UID,
      activeReaderId: "reader_narsinh",
      reminderEnabled: true,
      reminderHour: 20,
      reminderMinute: 30,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(narsinhDb.collection("users").doc(NARSINH_UID).get());
  await assertFails(vishwarajDb.collection("users").doc(NARSINH_UID).get());
  await assertFails(narsinhDb.collection("users").get());

  // Partial update succeeds
  await assertSucceeds(
    narsinhDb.collection("users").doc(NARSINH_UID).update({
      reminderHour: 21,
      reminderMinute: 0,
      updatedAt: pastTime(1000),
    })
  );

  // Reject invalid readerId
  await assertFails(
    narsinhDb.collection("users").doc(NARSINH_UID).update({
      activeReaderId: "reader_someone_else",
      updatedAt: pastTime(500),
    })
  );

  // Reject ghost field (shadow update)
  await assertFails(
    narsinhDb.collection("users").doc(NARSINH_UID).update({
      isAdmin: true,
      updatedAt: pastTime(500),
    })
  );

  // Reject future timestamp
  await assertFails(
    narsinhDb.collection("users").doc(NARSINH_UID).update({
      reminderHour: 19,
      updatedAt: futureTime(),
    })
  );
});

test("Circles collection: create, join action, query alignment, and capacity guard", async () => {
  const narsinhDb = testEnv.authenticatedContext(NARSINH_UID).firestore();
  const vishwarajDb = testEnv.authenticatedContext(VISHWARAJ_UID).firestore();
  const now = pastTime(10000);

  // Narsinh initializes the shared reading circle
  await assertSucceeds(
    narsinhDb.collection("circles").doc(CIRCLE_ID).set({
      name: "Narsinh & Vishwaraj",
      ownerId: NARSINH_UID,
      memberUids: [NARSINH_UID],
      dailyGoalMinutes: 30,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Vishwaraj joins the circle via Join Action
  await assertSucceeds(
    vishwarajDb.collection("circles").doc(CIRCLE_ID).update({
      memberUids: [NARSINH_UID, VISHWARAJ_UID],
      updatedAt: pastTime(2000),
    })
  );

  // Filtered list query matches Kotlin repository
  await assertSucceeds(
    vishwarajDb
      .collection("circles")
      .where("memberUids", "array-contains", VISHWARAJ_UID)
      .get()
  );

  // Unfiltered list query rejected
  await assertFails(vishwarajDb.collection("circles").get());

  // Join action cannot modify dailyGoalMinutes or ownerId
  const extraDb = testEnv.authenticatedContext("uid_extra_3").firestore();
  await assertFails(
    extraDb.collection("circles").doc(CIRCLE_ID).update({
      memberUids: [NARSINH_UID, VISHWARAJ_UID, "uid_extra_3"],
      dailyGoalMinutes: 5,
      updatedAt: pastTime(1000),
    })
  );
});

test("Readers subcollection: parent-gated get/create/update, list forbidden, non-members blocked", async () => {
  const narsinhDb = testEnv.authenticatedContext(NARSINH_UID).firestore();
  const vishwarajDb = testEnv.authenticatedContext(VISHWARAJ_UID).firestore();
  const intruderDb = testEnv.authenticatedContext(INTRUDER_UID).firestore();
  const now = pastTime(10000);

  await narsinhDb.collection("circles").doc(CIRCLE_ID).set({
    name: "Narsinh & Vishwaraj",
    ownerId: NARSINH_UID,
    memberUids: [NARSINH_UID],
    dailyGoalMinutes: 30,
    createdAt: now,
    updatedAt: now,
  });

  await vishwarajDb.collection("circles").doc(CIRCLE_ID).update({
    memberUids: [NARSINH_UID, VISHWARAJ_UID],
    updatedAt: pastTime(5000),
  });

  const narsinhReaderRef = narsinhDb
    .collection("circles")
    .doc(CIRCLE_ID)
    .collection("readers")
    .doc("reader_narsinh");

  await assertSucceeds(
    narsinhReaderRef.set({
      readerId: "reader_narsinh",
      displayName: "Narsinh Chad",
      claimedByUid: NARSINH_UID,
      currentStreak: 1,
      longestStreak: 1,
      todayMinutes: 30,
      todayDateKey: "2026-11-19",
      weeklyMinutes: 30,
      totalMinutes: 30,
      lastBookTitle: "Atomic Habits",
      dailyTotals: { "2026-11-19": 30 },
      createdAt: now,
      updatedAt: now,
    })
  );

  // Vishwaraj can directly get Narsinh's reader stats
  await assertSucceeds(
    vishwarajDb
      .collection("circles")
      .doc(CIRCLE_ID)
      .collection("readers")
      .doc("reader_narsinh")
      .get()
  );

  // Listing readers collection is strictly forbidden
  await assertFails(
    narsinhDb.collection("circles").doc(CIRCLE_ID).collection("readers").get()
  );

  // Intruder cannot read or write readers
  await assertFails(
    intruderDb
      .collection("circles")
      .doc(CIRCLE_ID)
      .collection("readers")
      .doc("reader_narsinh")
      .get()
  );

  // Invalid third reader ID is rejected
  await assertFails(
    narsinhDb
      .collection("circles")
      .doc(CIRCLE_ID)
      .collection("readers")
      .doc("reader_third_person")
      .set({
        readerId: "reader_third_person",
        displayName: "Third Person",
        claimedByUid: NARSINH_UID,
        currentStreak: 0,
        longestStreak: 0,
        todayMinutes: 0,
        todayDateKey: "2026-11-19",
        weeklyMinutes: 0,
        totalMinutes: 0,
        dailyTotals: {},
        createdAt: now,
        updatedAt: now,
      })
  );

  // Delete is forbidden
  await assertFails(narsinhReaderRef.delete());
});

test("Sessions subcollection: Kotlin create/update/list alignment, @DocumentId guard, author isolation", async () => {
  const narsinhDb = testEnv.authenticatedContext(NARSINH_UID).firestore();
  const vishwarajDb = testEnv.authenticatedContext(VISHWARAJ_UID).firestore();
  const intruderDb = testEnv.authenticatedContext(INTRUDER_UID).firestore();
  const now = pastTime(10000);

  await narsinhDb.collection("circles").doc(CIRCLE_ID).set({
    name: "Narsinh & Vishwaraj",
    ownerId: NARSINH_UID,
    memberUids: [NARSINH_UID],
    dailyGoalMinutes: 30,
    createdAt: now,
    updatedAt: now,
  });

  await vishwarajDb.collection("circles").doc(CIRCLE_ID).update({
    memberUids: [NARSINH_UID, VISHWARAJ_UID],
    updatedAt: pastTime(5000),
  });

  const sessionRef = narsinhDb
    .collection("circles")
    .doc(CIRCLE_ID)
    .collection("sessions")
    .doc("sess_morning_1");

  // Valid Kotlin create payload (no @DocumentId `id` field, optional fields included or omitted cleanly)
  await assertSucceeds(
    sessionRef.set({
      circleId: CIRCLE_ID,
      readerId: "reader_narsinh",
      readerName: "Narsinh Chad",
      authorUid: NARSINH_UID,
      memberUids: [NARSINH_UID, VISHWARAJ_UID],
      dateKey: "2026-11-19",
      minutes: 15,
      loggedTimeLabel: "10:00 AM",
      bookTitle: "Deep Work",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Both Narsinh and Vishwaraj can query sessions using rule-aligned whereArrayContains filter
  await assertSucceeds(
    vishwarajDb
      .collection("circles")
      .doc(CIRCLE_ID)
      .collection("sessions")
      .where("memberUids", "array-contains", VISHWARAJ_UID)
      .get()
  );

  // Unfiltered session query is denied
  await assertFails(
    narsinhDb.collection("circles").doc(CIRCLE_ID).collection("sessions").get()
  );

  // Intruder cannot list Narsinh's sessions, cannot read session directly, and gets 0 docs for own filter
  await assertFails(
    intruderDb
      .collection("circles")
      .doc(CIRCLE_ID)
      .collection("sessions")
      .where("memberUids", "array-contains", NARSINH_UID)
      .get()
  );
  const intruderEmptySnap = await assertSucceeds(
    intruderDb
      .collection("circles")
      .doc(CIRCLE_ID)
      .collection("sessions")
      .where("memberUids", "array-contains", INTRUDER_UID)
      .get()
  );
  if (intruderEmptySnap.size !== 0) {
    throw new Error("Expected 0 sessions leaked to intruder");
  }
  await assertFails(
    intruderDb
      .collection("circles")
      .doc(CIRCLE_ID)
      .collection("sessions")
      .doc("sess_morning_1")
      .get()
  );

  // Author can update minutes
  await assertSucceeds(
    sessionRef.update({
      minutes: 20,
      updatedAt: pastTime(1000),
    })
  );

  // Non-author (even circle member) cannot edit or delete someone else's session
  await assertFails(
    vishwarajDb
      .collection("circles")
      .doc(CIRCLE_ID)
      .collection("sessions")
      .doc("sess_morning_1")
      .update({
        minutes: 5,
        updatedAt: pastTime(500),
      })
  );
  await assertFails(
    vishwarajDb
      .collection("circles")
      .doc(CIRCLE_ID)
      .collection("sessions")
      .doc("sess_morning_1")
      .delete()
  );

  // Reject payload with forbidden @DocumentId `id` key
  await assertFails(
    narsinhDb
      .collection("circles")
      .doc(CIRCLE_ID)
      .collection("sessions")
      .doc("sess_bad_id")
      .set({
        id: "sess_bad_id",
        circleId: CIRCLE_ID,
        readerId: "reader_narsinh",
        readerName: "Narsinh Chad",
        authorUid: NARSINH_UID,
        memberUids: [NARSINH_UID, VISHWARAJ_UID],
        dateKey: "2026-11-19",
        minutes: 15,
        loggedTimeLabel: "10:15 AM",
        createdAt: now,
        updatedAt: now,
      })
  );

  // Reject invalid minutes (0 or > 720)
  await assertFails(
    narsinhDb
      .collection("circles")
      .doc(CIRCLE_ID)
      .collection("sessions")
      .doc("sess_zero")
      .set({
        circleId: CIRCLE_ID,
        readerId: "reader_narsinh",
        readerName: "Narsinh Chad",
        authorUid: NARSINH_UID,
        memberUids: [NARSINH_UID, VISHWARAJ_UID],
        dateKey: "2026-11-19",
        minutes: 0,
        loggedTimeLabel: "10:15 AM",
        createdAt: now,
        updatedAt: now,
      })
  );

  // Author can delete own session
  await assertSucceeds(sessionRef.delete());
});
