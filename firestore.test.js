const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const VIEWER_UID = "viewer_1";
const OTHER_UID = "viewer_2";
const SUPER_ADMIN_UID = "super_admin_1";
const SCORE_MGR_UID = "score_mgr_1";
const STREAM_MGR_UID = "stream_mgr_1";
const MODERATOR_UID = "mod_1";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
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

test("Unauthenticated users are rejected across all collections", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("broadcasts").where("visibility", "==", "PUBLIC").get());
  await assertFails(unauth.collection("matches").where("visibility", "==", "PUBLIC").get());
  await assertFails(unauth.collection("users").doc(VIEWER_UID).get());
  await assertFails(unauth.collection("staff").doc(VIEWER_UID).get());
});

test("User profile isolation, ghost fields, and chatBlocked self-escalation protection", async () => {
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID, { email: "fan@example.com", email_verified: true }).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID, { email: "other@example.com", email_verified: true }).firestore();
  const now = pastDate();

  await assertSucceeds(
    viewerDb.collection("users").doc(VIEWER_UID).set({
      userId: VIEWER_UID,
      displayName: "Ibrahim Moussa",
      email: "fan@example.com",
      preferredLanguage: "ha",
      favoriteTeamIds: ["team_as_fan"],
      favoriteWrestlerIds: [],
      favoriteTournamentIds: [],
      notifyMatchStart: true,
      notifyGoals: true,
      notifyKokowa: true,
      notifyTournaments: true,
      notifyNews: false,
      chatBlocked: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Ghost field rejected
  await assertFails(
    viewerDb.collection("users").doc(OTHER_UID).set({
      userId: OTHER_UID,
      displayName: "Hacker",
      preferredLanguage: "en",
      favoriteTeamIds: [],
      favoriteWrestlerIds: [],
      favoriteTournamentIds: [],
      notifyMatchStart: true,
      notifyGoals: true,
      notifyKokowa: true,
      notifyTournaments: true,
      notifyNews: true,
      chatBlocked: false,
      role: "SUPER_ADMIN",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Self-update allowed fields succeeds
  await assertSucceeds(
    viewerDb.collection("users").doc(VIEWER_UID).update({
      preferredLanguage: "ar",
      updatedAt: pastDate(1000),
    })
  );

  // Self-update of chatBlocked or createdAt rejected
  await assertFails(
    viewerDb.collection("users").doc(VIEWER_UID).update({
      chatBlocked: true,
      updatedAt: pastDate(1000),
    })
  );

  // Cross-user read rejected
  await assertFails(otherDb.collection("users").doc(VIEWER_UID).get());
});

test("Email spoofing test: unverified bootstrap admin email is rejected", async () => {
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: "muneerzaki83@gmail.com",
    email_verified: false,
  }).firestore();

  await assertFails(
    spoofDb.collection("staff").doc("staff_1").set({
      userId: "staff_1",
      email: "staff@sportniger.ne",
      displayName: "Fake Staff",
      role: "ADMIN",
      status: "ACTIVE",
      permissions: ["MANAGE_ALL"],
      assignedBy: "spoof_uid",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("Verified bootstrap Super Admin can manage staff, broadcasts, secrets, and immutable audit logs", async () => {
  const adminDb = testEnv.authenticatedContext(SUPER_ADMIN_UID, {
    email: "muneerzaki83@gmail.com",
    email_verified: true,
  }).firestore();
  const viewerDb = testEnv.authenticatedContext(VIEWER_UID, {
    email: "viewer@example.com",
    email_verified: true,
  }).firestore();
  const now = pastDate();

  // Assign staff roles
  await assertSucceeds(
    adminDb.collection("staff").doc(SCORE_MGR_UID).set({
      userId: SCORE_MGR_UID,
      email: "scores@sportniger.ne",
      displayName: "Moussa Scores",
      role: "SCORE_MANAGER",
      status: "ACTIVE",
      permissions: ["UPDATE_SCORES"],
      assignedBy: SUPER_ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Create broadcast & restricted secret
  await assertSucceeds(
    adminDb.collection("broadcasts").doc("b_kokowa_1").set({
      title: "Sabre National Final",
      description: "Live from Maradi Arena",
      sportCategory: "KOKOWA",
      streamType: "HLS",
      playbackUrl: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
      ingestProtocol: "OBS_RTMP",
      ingestEndpoint: "rtmp://ingest.sportniger.ne/live",
      streamKeyConfigured: true,
      status: "LIVE",
      viewerCount: 4200,
      chatEnabled: true,
      featured: true,
      scheduledLabel: "LIVE NOW",
      visibility: "PUBLIC",
      createdBy: SUPER_ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb.collection("broadcast_secrets").doc("b_kokowa_1").set({
      broadcastId: "b_kokowa_1",
      rtmpServerUrl: "rtmp://ingest.sportniger.ne/live",
      streamKeyMasked: "sn_live_••••_9f2a",
      obsProfileNote: "1080p60 CBR 4500kbps",
      updatedBy: SUPER_ADMIN_UID,
      updatedAt: now,
    })
  );

  // Public viewer CAN query PUBLIC broadcasts with filter, CANNOT run unfiltered query, CANNOT read secrets
  await assertSucceeds(viewerDb.collection("broadcasts").where("visibility", "==", "PUBLIC").get());
  await assertFails(viewerDb.collection("broadcasts").get());
  await assertFails(viewerDb.collection("broadcast_secrets").doc("b_kokowa_1").get());

  // Audit log is create-only, never update/delete
  await assertSucceeds(
    adminDb.collection("audit_logs").doc("log_1").set({
      actorUid: SUPER_ADMIN_UID,
      actorEmail: "muneerzaki83@gmail.com",
      actorRole: "SUPER_ADMIN",
      action: "CREATE_BROADCAST",
      targetType: "broadcast",
      targetId: "b_kokowa_1",
      summary: "Created Sabre National Final stream",
      createdAt: now,
    })
  );
  await assertFails(adminDb.collection("audit_logs").doc("log_1").update({ summary: "Tampered" }));
  await assertFails(adminDb.collection("audit_logs").doc("log_1").delete());
});

test("RBAC role isolation & terminal match locking", async () => {
  const now = pastDate();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("staff").doc(SCORE_MGR_UID).set({
      userId: SCORE_MGR_UID,
      email: "score@sportniger.ne",
      displayName: "Score Mgr",
      role: "SCORE_MANAGER",
      status: "ACTIVE",
      permissions: ["UPDATE_SCORES"],
      assignedBy: SUPER_ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("staff").doc(STREAM_MGR_UID).set({
      userId: STREAM_MGR_UID,
      email: "stream@sportniger.ne",
      displayName: "Stream Mgr",
      role: "STREAMING_MANAGER",
      status: "ACTIVE",
      permissions: ["MANAGE_STREAMS"],
      assignedBy: SUPER_ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("matches").doc("m_1").set({
      leagueId: "ligue1_niger",
      leagueName: "Super Ligue Niger",
      sportCategory: "FOOTBALL",
      homeTeamId: "as_fan",
      homeTeamName: "AS FAN",
      awayTeamId: "sahel_sc",
      awayTeamName: "Sahel SC",
      homeScore: 1,
      awayScore: 0,
      status: "LIVE",
      matchMinute: "54'",
      venue: "Stade Général Seyni Kountché",
      sourceType: "MANUAL",
      possessionHome: 56,
      shotsHome: 8,
      shotsAway: 4,
      cornersHome: 5,
      cornersAway: 2,
      featured: true,
      visibility: "PUBLIC",
      createdAt: now,
      updatedAt: now,
    });
  });

  const scoreDb = testEnv.authenticatedContext(SCORE_MGR_UID).firestore();
  const streamDb = testEnv.authenticatedContext(STREAM_MGR_UID).firestore();
  const adminDb = testEnv.authenticatedContext(SUPER_ADMIN_UID, {
    email: "muneerzaki83@gmail.com",
    email_verified: true,
  }).firestore();

  // Score Manager can update live score & add goal event
  await assertSucceeds(
    scoreDb.collection("matches").doc("m_1").update({
      homeScore: 2,
      matchMinute: "68'",
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    scoreDb.collection("matches").doc("m_1").collection("events").doc("ev_1").set({
      matchId: "m_1",
      minute: 68,
      type: "GOAL",
      teamSide: "HOME",
      playerName: "Boubacar Issa",
      visibility: "PUBLIC",
      createdAt: pastDate(1000),
    })
  );

  // Score Manager CANNOT create staff or broadcasts
  await assertFails(
    scoreDb.collection("staff").doc("new_staff").set({
      userId: "new_staff",
      email: "x@y.com",
      displayName: "X",
      role: "ADMIN",
      status: "ACTIVE",
      permissions: [],
      assignedBy: SCORE_MGR_UID,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Streaming Manager CANNOT update match scores
  await assertFails(
    streamDb.collection("matches").doc("m_1").update({
      homeScore: 9,
      updatedAt: pastDate(1000),
    })
  );

  // Lock match to COMPLETED
  await assertSucceeds(
    scoreDb.collection("matches").doc("m_1").update({
      status: "COMPLETED",
      matchMinute: "FT",
      updatedAt: pastDate(500),
    })
  );

  // Score Manager CANNOT modify a COMPLETED match, but Admin can correct it
  await assertFails(
    scoreDb.collection("matches").doc("m_1").update({
      homeScore: 3,
      updatedAt: pastDate(100),
    })
  );
  await assertSucceeds(
    adminDb.collection("matches").doc("m_1").update({
      homeScore: 3,
      updatedAt: pastDate(100),
    })
  );
});

test("Live chat commenting, reporting, and moderation", async () => {
  const now = pastDate();
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("staff").doc(MODERATOR_UID).set({
      userId: MODERATOR_UID,
      email: "mod@sportniger.ne",
      displayName: "Mod",
      role: "MODERATOR",
      status: "ACTIVE",
      permissions: ["MODERATE_CHAT"],
      assignedBy: SUPER_ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("broadcasts").doc("b_1").set({
      title: "AS FAN vs USGN",
      description: "Live football",
      sportCategory: "FOOTBALL",
      streamType: "HLS",
      playbackUrl: "https://example.com/stream.m3u8",
      ingestProtocol: "RTMP",
      ingestEndpoint: "rtmp://ingest.sportniger.ne/live",
      streamKeyConfigured: true,
      status: "LIVE",
      viewerCount: 1500,
      chatEnabled: true,
      featured: true,
      scheduledLabel: "LIVE",
      visibility: "PUBLIC",
      createdBy: SUPER_ADMIN_UID,
      createdAt: now,
      updatedAt: now,
    });
  });

  const viewerDb = testEnv.authenticatedContext(VIEWER_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  const modDb = testEnv.authenticatedContext(MODERATOR_UID).firestore();

  await assertSucceeds(
    viewerDb.collection("broadcasts").doc("b_1").collection("comments").doc("c_1").set({
      broadcastId: "b_1",
      userId: VIEWER_UID,
      userName: "Amina",
      message: "Allez le Mena !",
      deleted: false,
      reported: false,
      reportCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Public query with deleted == false succeeds
  await assertSucceeds(
    otherDb.collection("broadcasts").doc("b_1").collection("comments").where("deleted", "==", false).get()
  );

  // Another user can report comment
  await assertSucceeds(
    otherDb.collection("broadcasts").doc("b_1").collection("comments").doc("c_1").update({
      reported: true,
      reportCount: 1,
      updatedAt: pastDate(500),
    })
  );

  // Other user cannot soft-delete someone else's comment, moderator can
  await assertFails(
    otherDb.collection("broadcasts").doc("b_1").collection("comments").doc("c_1").update({
      deleted: true,
      updatedAt: pastDate(400),
    })
  );
  await assertSucceeds(
    modDb.collection("broadcasts").doc("b_1").collection("comments").doc("c_1").update({
      deleted: true,
      updatedAt: pastDate(300),
    })
  );
});
