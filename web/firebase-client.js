import { initializeApp } from 'https://www.gstatic.com/firebasejs/11.0.1/firebase-app.js';
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signInWithRedirect,
  getRedirectResult,
  signOut,
  onAuthStateChanged
} from 'https://www.gstatic.com/firebasejs/11.0.1/firebase-auth.js';
import {
  getFirestore,
  collection,
  doc,
  getDoc,
  setDoc,
  updateDoc,
  query,
  where,
  onSnapshot,
  serverTimestamp,
  writeBatch
} from 'https://www.gstatic.com/firebasejs/11.0.1/firebase-firestore.js';

export const FIREBASE_CONFIG = {
  apiKey: 'AIzaSyCGWcK56O0yHGBXwWEIApbyvgQoH_21K9Q',
  authDomain: 'gen-lang-client-0977858921.firebaseapp.com',
  projectId: 'gen-lang-client-0977858921',
  storageBucket: 'gen-lang-client-0977858921.firebasestorage.app',
  messagingSenderId: '635812818318'
};

export const FIRESTORE_DATABASE_ID = 'ai-studio-android-sportnig-5079ffaa-a494-462f-8296-8ef4d909901e';
export const BOOTSTRAP_SUPER_ADMIN_EMAIL = 'muneerzaki83@gmail.com';

const app = initializeApp(FIREBASE_CONFIG);
export const auth = getAuth(app);
export const db = getFirestore(app, FIRESTORE_DATABASE_ID);

const googleProvider = new GoogleAuthProvider();
googleProvider.setCustomParameters({ prompt: 'select_account' });

export const STAFF_ROLES = [
  'SUPER_ADMIN',
  'ADMIN',
  'SPORTS_MANAGER',
  'SCORE_MANAGER',
  'STREAMING_MANAGER',
  'NEWS_EDITOR',
  'MODERATOR',
  'VIEWER'
];

export function rolePermissions(role) {
  const r = role || 'VIEWER';
  return {
    role: r,
    isStaff: r !== 'VIEWER',
    canManageBroadcasts: ['SUPER_ADMIN', 'ADMIN', 'STREAMING_MANAGER'].includes(r),
    canManageScores: ['SUPER_ADMIN', 'ADMIN', 'SPORTS_MANAGER', 'SCORE_MANAGER'].includes(r),
    canManageSports: ['SUPER_ADMIN', 'ADMIN', 'SPORTS_MANAGER'].includes(r),
    canManageNews: ['SUPER_ADMIN', 'ADMIN', 'NEWS_EDITOR'].includes(r),
    canModerateChat: ['SUPER_ADMIN', 'ADMIN', 'MODERATOR'].includes(r),
    canManageStaff: r === 'SUPER_ADMIN',
    canManageBanners: ['SUPER_ADMIN', 'ADMIN'].includes(r)
  };
}

export async function completeRedirectSignInIfAny() {
  try {
    await getRedirectResult(auth);
  } catch (err) {
    console.warn('Redirect sign-in check:', err?.message || err);
  }
}

export async function signInWithGoogle() {
  try {
    return await signInWithPopup(auth, googleProvider);
  } catch (err) {
    if (err?.code === 'auth/popup-blocked' || err?.code === 'auth/operation-not-supported-in-this-environment') {
      await signInWithRedirect(auth, googleProvider);
      return null;
    }
    throw err;
  }
}

export async function signOutUser() {
  await signOut(auth);
}

export function listenAuth(callback) {
  return onAuthStateChanged(auth, callback);
}

export async function ensureUserProfileExists(user, langCode) {
  if (!user) return;
  const userRef = doc(db, 'users', user.uid);
  const snap = await getDoc(userRef);
  if (!snap.exists()) {
    const safeName = (user.displayName || user.email?.split('@')[0] || 'Sport Niger Fan').slice(0, 80);
    const validLang = ['en', 'ha', 'fr', 'ar'].includes(langCode) ? langCode : 'en';
    const payload = {
      userId: user.uid,
      displayName: safeName || 'Sport Niger Fan',
      preferredLanguage: validLang,
      favoriteTeamIds: [],
      favoriteWrestlerIds: [],
      favoriteTournamentIds: [],
      notifyMatchStart: true,
      notifyGoals: true,
      notifyKokowa: true,
      notifyTournaments: true,
      notifyNews: false,
      chatBlocked: false,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    };
    if (user.email) payload.email = user.email.slice(0, 160);
    if (user.photoURL) payload.photoUrl = user.photoURL.slice(0, 500);
    await setDoc(userRef, payload);
  }
}

export function subscribePublicCollection(collectionName, onData, onError) {
  const q = query(collection(db, collectionName), where('visibility', '==', 'PUBLIC'));
  return onSnapshot(
    q,
    (snapshot) => {
      const items = snapshot.docs.map((d) => ({ id: d.id, ...d.data() }));
      onData(items);
    },
    (err) => {
      console.error(`Firestore error (${collectionName}):`, err);
      if (onError) onError(err);
    }
  );
}

export function subscribeMatchEvents(matchId, onData, onError) {
  const q = query(collection(db, 'matches', matchId, 'events'), where('visibility', '==', 'PUBLIC'));
  return onSnapshot(
    q,
    (snapshot) => {
      const items = snapshot.docs
        .map((d) => ({ id: d.id, ...d.data() }))
        .sort((a, b) => (a.minute ?? 0) - (b.minute ?? 0));
      onData(items);
    },
    (err) => {
      console.error('Firestore match events error:', err);
      if (onError) onError(err);
    }
  );
}

export function subscribeBroadcastComments(broadcastId, onData, onError) {
  const q = query(collection(db, 'broadcasts', broadcastId, 'comments'), where('deleted', '==', false));
  return onSnapshot(
    q,
    (snapshot) => {
      const items = snapshot.docs
        .map((d) => ({ id: d.id, ...d.data() }))
        .sort((a, b) => (a.createdAt?.seconds ?? Number.MAX_SAFE_INTEGER) - (b.createdAt?.seconds ?? Number.MAX_SAFE_INTEGER));
      onData(items);
    },
    (err) => {
      console.error('Firestore comments error:', err);
      if (onError) onError(err);
    }
  );
}

export function subscribeUserProfile(uid, onData) {
  return onSnapshot(doc(db, 'users', uid), (snap) => {
    onData(snap.exists() ? { id: snap.id, ...snap.data() } : null);
  });
}

export function subscribeStaffRole(user, onData) {
  if (!user) {
    onData('VIEWER');
    return () => {};
  }
  if (user.emailVerified && user.email === BOOTSTRAP_SUPER_ADMIN_EMAIL) {
    onData('SUPER_ADMIN');
    return () => {};
  }
  return onSnapshot(
    doc(db, 'staff', user.uid),
    (snap) => {
      if (snap.exists()) {
        const data = snap.data();
        if (data.status === 'ACTIVE' && STAFF_ROLES.includes(data.role)) {
          onData(data.role);
          return;
        }
      }
      onData('VIEWER');
    },
    () => onData('VIEWER')
  );
}

export function subscribeStaffDirectory(onData, onError) {
  return onSnapshot(
    collection(db, 'staff'),
    (snap) => onData(snap.docs.map((d) => ({ id: d.id, ...d.data() }))),
    (err) => onError && onError(err)
  );
}

export function subscribeAuditLogs(onData, onError) {
  return onSnapshot(
    collection(db, 'audit_logs'),
    (snap) => {
      const items = snap.docs
        .map((d) => ({ id: d.id, ...d.data() }))
        .sort((a, b) => (b.createdAt?.seconds ?? 0) - (a.createdAt?.seconds ?? 0))
        .slice(0, 40);
      onData(items);
    },
    (err) => onError && onError(err)
  );
}

export async function getBroadcastSecret(broadcastId) {
  const snap = await getDoc(doc(db, 'broadcast_secrets', broadcastId));
  return snap.exists() ? { id: snap.id, ...snap.data() } : null;
}

export async function updateUserLanguage(uid, langCode) {
  await updateDoc(doc(db, 'users', uid), {
    preferredLanguage: langCode,
    updatedAt: serverTimestamp()
  });
}

export async function updateNotificationPreferences(uid, prefs) {
  await updateDoc(doc(db, 'users', uid), {
    notifyMatchStart: !!prefs.notifyMatchStart,
    notifyGoals: !!prefs.notifyGoals,
    notifyKokowa: !!prefs.notifyKokowa,
    notifyTournaments: !!prefs.notifyTournaments,
    notifyNews: !!prefs.notifyNews,
    updatedAt: serverTimestamp()
  });
}

export async function toggleFavoriteId(uid, field, itemId, currentIds) {
  const list = Array.isArray(currentIds) ? currentIds : [];
  const updated = list.includes(itemId)
    ? list.filter((x) => x !== itemId)
    : [...list, itemId].slice(0, 20);
  await updateDoc(doc(db, 'users', uid), {
    [field]: updated,
    updatedAt: serverTimestamp()
  });
}

export async function postChatComment(broadcastId, user, rawMessage) {
  const message = (rawMessage || '').trim().slice(0, 400);
  if (!message) throw new Error('Message cannot be empty');
  const commentRef = doc(collection(db, 'broadcasts', broadcastId, 'comments'));
  const userName = (user.displayName || user.email?.split('@')[0] || 'Viewer').slice(0, 80);
  const payload = {
    broadcastId,
    userId: user.uid,
    userName,
    message,
    deleted: false,
    reported: false,
    reportCount: 0,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  };
  if (user.photoURL) payload.userPhotoUrl = user.photoURL.slice(0, 500);
  await setDoc(commentRef, payload);
}

export async function reportChatComment(broadcastId, comment) {
  await updateDoc(doc(db, 'broadcasts', broadcastId, 'comments', comment.id), {
    reported: true,
    reportCount: (comment.reportCount || 0) + 1,
    updatedAt: serverTimestamp()
  });
}

async function recordAudit(actorRole, action, targetType, targetId, summary) {
  const user = auth.currentUser;
  if (!user) return;
  try {
    const auditRef = doc(collection(db, 'audit_logs'));
    await setDoc(auditRef, {
      actorUid: user.uid,
      actorEmail: (user.email || 'staff@sportniger.ne').slice(0, 160),
      actorRole: actorRole || 'STAFF',
      action: action.slice(0, 80),
      targetType: targetType.slice(0, 60),
      targetId: targetId.slice(0, 128),
      summary: summary.slice(0, 300),
      createdAt: serverTimestamp()
    });
  } catch (err) {
    console.warn('Audit log write skipped:', err?.message);
  }
}

export async function createBroadcast(actorRole, data) {
  const user = auth.currentUser;
  if (!user) throw new Error('Staff authentication required');
  const bcRef = doc(collection(db, 'broadcasts'));
  const endpoint = (data.ingestEndpoint || '').trim().slice(0, 300) || 'rtmp://ingest.sportniger.ne/live';
  const payload = {
    title: data.title.trim().slice(0, 120),
    description: (data.description || '').trim().slice(0, 1000) || 'Live SPORT NIGER coverage',
    sportCategory: (data.sportCategory || 'FOOTBALL').trim().toUpperCase().slice(0, 40),
    streamType: data.streamType || 'HLS',
    playbackUrl: data.playbackUrl.trim().slice(0, 600),
    ingestProtocol: data.ingestProtocol || 'OBS_RTMP',
    ingestEndpoint: endpoint,
    streamKeyConfigured: true,
    status: data.status || 'LIVE',
    viewerCount: data.status === 'LIVE' ? 120 : 0,
    chatEnabled: data.chatEnabled !== false,
    featured: !!data.featured,
    scheduledLabel: data.status === 'LIVE' ? 'LIVE NOW' : 'SCHEDULED',
    visibility: 'PUBLIC',
    createdBy: user.uid,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  };
  if (data.youtubeVideoId?.trim()) {
    payload.youtubeVideoId = data.youtubeVideoId.trim().slice(0, 64);
  }
  await setDoc(bcRef, payload);

  await setDoc(doc(db, 'broadcast_secrets', bcRef.id), {
    broadcastId: bcRef.id,
    rtmpServerUrl: endpoint,
    streamKeyMasked: (data.rtmpKeyMasked || '').trim().slice(0, 120) || 'sn_live_••••_secure',
    obsProfileNote: (data.obsProfileNote || '').trim().slice(0, 400) || '1080p60 H.264 CBR 4500kbps',
    updatedBy: user.uid,
    updatedAt: serverTimestamp()
  });

  await recordAudit(actorRole, 'CREATE_BROADCAST', 'broadcast', bcRef.id, `Created broadcast: ${payload.title}`);
}

export async function updateBroadcastStatus(actorRole, broadcastId, newStatus) {
  const labelMap = {
    LIVE: 'LIVE NOW',
    ENDED: 'ENDED',
    OFFLINE: 'OFFLINE',
    UPCOMING: 'UPCOMING'
  };
  await updateDoc(doc(db, 'broadcasts', broadcastId), {
    status: newStatus,
    scheduledLabel: labelMap[newStatus] || 'UPCOMING',
    updatedAt: serverTimestamp()
  });
  await recordAudit(actorRole, 'UPDATE_BROADCAST_STATUS', 'broadcast', broadcastId, `Set stream status -> ${newStatus}`);
}

export async function toggleBroadcastChat(actorRole, broadcastId, enabled) {
  await updateDoc(doc(db, 'broadcasts', broadcastId), {
    chatEnabled: !!enabled,
    updatedAt: serverTimestamp()
  });
  await recordAudit(actorRole, 'TOGGLE_CHAT', 'broadcast', broadcastId, `Chat enabled=${!!enabled}`);
}

export async function moderateDeleteComment(actorRole, broadcastId, commentId) {
  await updateDoc(doc(db, 'broadcasts', broadcastId, 'comments', commentId), {
    deleted: true,
    updatedAt: serverTimestamp()
  });
  await recordAudit(actorRole, 'DELETE_COMMENT', 'comment', commentId, 'Moderated chat comment');
}

export async function blockUserFromChat(actorRole, targetUserId) {
  await updateDoc(doc(db, 'users', targetUserId), {
    chatBlocked: true,
    updatedAt: serverTimestamp()
  });
  await recordAudit(actorRole, 'BLOCK_CHAT_USER', 'user', targetUserId, 'Blocked user from live chat');
}

export async function updateMatchScore(actorRole, matchId, homeScore, awayScore, status, minute) {
  const h = Math.max(0, Math.min(99, parseInt(homeScore, 10) || 0));
  const a = Math.max(0, Math.min(99, parseInt(awayScore, 10) || 0));
  await updateDoc(doc(db, 'matches', matchId), {
    homeScore: h,
    awayScore: a,
    status,
    matchMinute: (minute || '').trim().slice(0, 30) || status,
    updatedAt: serverTimestamp()
  });
  await recordAudit(actorRole, 'UPDATE_SCORE', 'match', matchId, `Score ${h}-${a} (${status} ${minute})`);
}

export async function addMatchEvent(actorRole, matchId, data) {
  const evRef = doc(collection(db, 'matches', matchId, 'events'));
  const minute = Math.max(0, Math.min(150, parseInt(data.minute, 10) || 0));
  const payload = {
    matchId,
    minute,
    type: data.type || 'GOAL',
    teamSide: data.teamSide || 'HOME',
    playerName: data.playerName.trim().slice(0, 80),
    visibility: 'PUBLIC',
    createdAt: serverTimestamp()
  };
  if (data.detail?.trim()) payload.detail = data.detail.trim().slice(0, 120);
  await setDoc(evRef, payload);
  await recordAudit(actorRole, 'ADD_MATCH_EVENT', 'match', matchId, `${payload.type} ${minute}' ${payload.playerName}`);
}

export async function addWrestler(actorRole, data) {
  const ref = doc(collection(db, 'wrestlers'));
  const weight = Math.max(50, Math.min(220, parseInt(data.weightKg, 10) || 100));
  await setDoc(ref, {
    name: data.name.trim().slice(0, 80),
    nickname: (data.nickname || '').trim().slice(0, 60) || 'Zaki',
    village: (data.village || '').trim().slice(0, 80) || 'Niamey',
    region: (data.region || '').trim().slice(0, 60) || 'Niamey',
    weightKg: weight,
    wins: 0,
    losses: 0,
    draws: 0,
    titlesCount: 0,
    bio: (data.bio || '').trim().slice(0, 800) || `Kokowa competitor representing ${data.region}.`,
    featured: true,
    visibility: 'PUBLIC',
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  });
  await recordAudit(actorRole, 'ADD_WRESTLER', 'wrestler', ref.id, `Added Kokowa wrestler ${data.name}`);
}

export async function createKokowaEvent(actorRole, data) {
  const ref = doc(collection(db, 'kokowa_events'));
  const payload = {
    title: data.title.trim().slice(0, 120),
    location: data.location.trim().slice(0, 100),
    region: data.region.trim().slice(0, 60),
    status: data.status || 'UPCOMING',
    wrestlerAId: 'w_custom_a',
    wrestlerAName: data.wrestlerAName.trim().slice(0, 80),
    wrestlerBId: 'w_custom_b',
    wrestlerBName: data.wrestlerBName.trim().slice(0, 80),
    roundLabel: (data.roundLabel || '').trim().slice(0, 80) || 'Main Bout',
    scheduledLabel: (data.scheduledLabel || '').trim().slice(0, 80) || 'Upcoming',
    visibility: 'PUBLIC',
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  };
  if (data.winnerName?.trim()) payload.winnerName = data.winnerName.trim().slice(0, 80);
  await setDoc(ref, payload);
  await recordAudit(actorRole, 'CREATE_KOKOWA_EVENT', 'kokowa_event', ref.id, `Kokowa bout: ${payload.wrestlerAName} vs ${payload.wrestlerBName}`);
}

export async function createSportCategory(actorRole, data) {
  const user = auth.currentUser;
  if (!user) throw new Error('Staff authentication required');
  const safeSlug = data.slug.trim().toUpperCase().replace(/\s+/g, '_').slice(0, 40);
  const docId = `cat_${safeSlug.toLowerCase()}`;
  const en = data.nameEn.trim().slice(0, 60);
  await setDoc(doc(db, 'sport_categories', docId), {
    slug: safeSlug,
    nameEn: en,
    nameHa: (data.nameHa || en).trim().slice(0, 60),
    nameFr: (data.nameFr || en).trim().slice(0, 60),
    nameAr: (data.nameAr || en).trim().slice(0, 60),
    iconKey: data.traditional ? 'TRADITIONAL' : 'SPORTS',
    traditional: !!data.traditional,
    active: true,
    sortOrder: 50,
    visibility: 'PUBLIC',
    createdBy: user.uid,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  });
  await recordAudit(actorRole, 'ADD_CATEGORY', 'sport_category', docId, `Added category ${en}`);
}

export async function createTournament(actorRole, data) {
  const ref = doc(collection(db, 'tournaments'));
  const participants = (data.participantsCsv || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
    .slice(0, 32);
  const payload = {
    name: data.name.trim().slice(0, 120),
    sportCategory: (data.sportCategory || 'FOOTBALL').trim().toUpperCase().slice(0, 40),
    season: (data.season || '2026').trim().slice(0, 40),
    location: (data.location || 'Niamey, Niger').trim().slice(0, 100),
    status: 'ONGOING',
    participantNames: participants,
    knockoutBracketSummary: ['QF|Stage Pending', 'SF|Stage Pending', 'FINAL|Schedule Pending'],
    popular: true,
    visibility: 'PUBLIC',
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  };
  if (data.announcement?.trim()) payload.announcement = data.announcement.trim().slice(0, 400);
  await setDoc(ref, payload);
  await recordAudit(actorRole, 'CREATE_TOURNAMENT', 'tournament', ref.id, `Created tournament: ${payload.name}`);
}

export async function publishNews(actorRole, data) {
  const user = auth.currentUser;
  const ref = doc(collection(db, 'news'));
  const summary = data.summary.trim().slice(0, 300);
  await setDoc(ref, {
    title: data.title.trim().slice(0, 160),
    summary,
    body: (data.body || summary).trim().slice(0, 4000),
    sportCategory: (data.sportCategory || 'FOOTBALL').trim().toUpperCase().slice(0, 40),
    authorName: (user?.displayName || 'SPORT NIGER Editorial').slice(0, 80),
    breaking: !!data.breaking,
    visibility: 'PUBLIC',
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  });
  await recordAudit(actorRole, 'PUBLISH_NEWS', 'news', ref.id, `Published news: ${data.title}`);
}

export async function assignStaffMember(actorRole, targetUid, email, displayName, role) {
  const user = auth.currentUser;
  if (!user) throw new Error('Authentication required');
  const cleanUid = targetUid.trim();
  await setDoc(doc(db, 'staff', cleanUid), {
    userId: cleanUid,
    email: email.trim().slice(0, 160),
    displayName: displayName.trim().slice(0, 80),
    role,
    status: 'ACTIVE',
    permissions: [role],
    assignedBy: user.uid,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  });
  await recordAudit(actorRole, 'ASSIGN_STAFF_ROLE', 'staff', cleanUid, `Assigned ${role} to ${email}`);
}

export async function setStaffStatus(actorRole, staffUid, status) {
  await updateDoc(doc(db, 'staff', staffUid), {
    status,
    updatedAt: serverTimestamp()
  });
  await recordAudit(actorRole, 'UPDATE_STAFF_STATUS', 'staff', staffUid, `Staff status -> ${status}`);
}

export async function seedOfficialCatalog(actorRole) {
  const user = auth.currentUser;
  if (!user) throw new Error('Staff authentication required');
  const uid = user.uid;
  const batch = writeBatch(db);
  const ts = serverTimestamp();

  const categories = [
    ['FOOTBALL', 'Football', 'Kwallon Ƙafa', 'Football', 'كرة القدم'],
    ['KOKOWA', 'Kokowa Wrestling', 'Kokowa', 'Lutte Kokowa', 'مصارعة كوكوا'],
    ['DAMBE', 'Dambe Boxing', 'Dambe', 'Boxe Dambe', 'ملاكمة دامبي'],
    ['CAMEL_RACING', 'Camel Racing', 'Tseren Raƙuma', 'Course de Chameaux', 'سباق الهجن']
  ];
  categories.forEach(([slug, nameEn, nameHa, nameFr, nameAr], idx) => {
    batch.set(doc(db, 'sport_categories', `cat_${slug.toLowerCase()}`), {
      slug,
      nameEn,
      nameHa,
      nameFr,
      nameAr,
      iconKey: slug === 'FOOTBALL' ? 'FOOTBALL' : 'TRADITIONAL',
      traditional: slug !== 'FOOTBALL',
      active: true,
      sortOrder: idx,
      visibility: 'PUBLIC',
      createdBy: uid,
      createdAt: ts,
      updatedAt: ts
    });
  });

  batch.set(doc(db, 'leagues', 'super_ligue_niger'), {
    name: 'Super Ligue Niger',
    country: 'Niger',
    season: '2026/27',
    sportCategory: 'FOOTBALL',
    visibility: 'PUBLIC',
    createdAt: ts,
    updatedAt: ts
  });

  const teams = [
    ['team_as_fan', 'AS FAN Niamey', 'FAN', 'Niamey', '#1565FF', 14, 10, 3, 1, 26, 9, 33],
    ['team_usgn', 'USGN Niamey', 'USGN', 'Niamey', '#0DB02B', 14, 9, 3, 2, 22, 10, 30],
    ['team_sahel', 'Sahel SC', 'SAH', 'Niamey', '#FF8C1A', 14, 8, 4, 2, 21, 12, 28],
    ['team_nigelec', 'ASN Nigelec', 'NIG', 'Niamey', '#00B4FF', 14, 7, 3, 4, 18, 14, 24],
    ['team_douanes', 'AS Douanes', 'DOU', 'Niamey', '#D9381E', 14, 6, 4, 4, 16, 13, 22],
    ['team_janger', 'Janghorzo FC', 'JAN', 'Maradi', '#8E44AD', 14, 5, 3, 6, 14, 17, 18]
  ];
  teams.forEach(([id, name, shortName, city, badgeColorHex, played, won, drawn, lost, goalsFor, goalsAgainst, points]) => {
    batch.set(doc(db, 'teams', id), {
      name,
      shortName,
      city,
      leagueId: 'super_ligue_niger',
      badgeColorHex,
      played,
      won,
      drawn,
      lost,
      goalsFor,
      goalsAgainst,
      points,
      visibility: 'PUBLIC',
      createdAt: ts,
      updatedAt: ts
    });
  });

  batch.set(doc(db, 'broadcasts', 'bc_super_ligue_live'), {
    title: 'AS FAN vs USGN — Super Ligue Derby',
    description: 'Live broadcast from Stade Général Seyni Kountché in Niamey with multi-bitrate HLS delivery.',
    sportCategory: 'FOOTBALL',
    streamType: 'HLS',
    playbackUrl: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8',
    ingestProtocol: 'OBS_RTMP',
    ingestEndpoint: 'rtmp://ingest.sportniger.ne/live',
    streamKeyConfigured: true,
    status: 'LIVE',
    viewerCount: 6420,
    chatEnabled: true,
    featured: true,
    matchId: 'match_fan_usgn',
    scheduledLabel: 'LIVE NOW',
    visibility: 'PUBLIC',
    createdBy: uid,
    createdAt: ts,
    updatedAt: ts
  });

  batch.set(doc(db, 'broadcasts', 'bc_kokowa_sabre'), {
    title: 'Sabre National Kokowa — Maradi Arena',
    description: 'Traditional Nigerien wrestling championship live stream from Arène de Lutte Traditionnelle, Maradi.',
    sportCategory: 'KOKOWA',
    streamType: 'HLS',
    playbackUrl: 'https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8',
    ingestProtocol: 'MOBILE_LIVE',
    ingestEndpoint: 'rtmp://ingest.sportniger.ne/kokowa',
    streamKeyConfigured: true,
    status: 'LIVE',
    viewerCount: 9150,
    chatEnabled: true,
    featured: true,
    scheduledLabel: 'LIVE NOW',
    visibility: 'PUBLIC',
    createdBy: uid,
    createdAt: ts,
    updatedAt: ts
  });

  batch.set(doc(db, 'broadcasts', 'bc_mena_upcoming'), {
    title: 'Mena Niger vs Mali — Pre-Match Studio',
    description: 'Tactical preview and warm-up coverage on SPORT NIGER YouTube Live & HLS.',
    sportCategory: 'FOOTBALL',
    streamType: 'YOUTUBE_LIVE',
    playbackUrl: 'https://www.youtube.com/watch?v=jfKfPfyJRdk',
    youtubeVideoId: 'jfKfPfyJRdk',
    ingestProtocol: 'YOUTUBE',
    ingestEndpoint: 'rtmp://a.rtmp.youtube.com/live2',
    streamKeyConfigured: true,
    status: 'UPCOMING',
    viewerCount: 480,
    chatEnabled: true,
    featured: false,
    scheduledLabel: 'Tonight 20:00 WAT',
    visibility: 'PUBLIC',
    createdBy: uid,
    createdAt: ts,
    updatedAt: ts
  });

  batch.set(doc(db, 'matches', 'match_fan_usgn'), {
    leagueId: 'super_ligue_niger',
    leagueName: 'Super Ligue Niger',
    sportCategory: 'FOOTBALL',
    homeTeamId: 'team_as_fan',
    homeTeamName: 'AS FAN Niamey',
    awayTeamId: 'team_usgn',
    awayTeamName: 'USGN Niamey',
    homeScore: 2,
    awayScore: 1,
    status: 'LIVE',
    matchMinute: "67'",
    venue: 'Stade Général Seyni Kountché, Niamey',
    sourceType: 'MANUAL',
    broadcastId: 'bc_super_ligue_live',
    possessionHome: 54,
    shotsHome: 11,
    shotsAway: 7,
    cornersHome: 6,
    cornersAway: 3,
    featured: true,
    visibility: 'PUBLIC',
    createdAt: ts,
    updatedAt: ts
  });

  const events = [
    ['ev_1', 19, 'GOAL', 'HOME', 'Moussa Soumana', 'Header from corner'],
    ['ev_2', 38, 'GOAL', 'AWAY', 'Amadou Wonkoye', 'Penalty kick'],
    ['ev_3', 52, 'YELLOW_CARD', 'AWAY', 'Zakari Saley', 'Tactical foul'],
    ['ev_4', 61, 'GOAL', 'HOME', 'Harouna Garba', 'Low drive']
  ];
  events.forEach(([id, minute, type, teamSide, playerName, detail]) => {
    batch.set(doc(db, 'matches', 'match_fan_usgn', 'events', id), {
      matchId: 'match_fan_usgn',
      minute,
      type,
      teamSide,
      playerName,
      detail,
      visibility: 'PUBLIC',
      createdAt: ts
    });
  });

  batch.set(doc(db, 'matches', 'match_sahel_nigelec'), {
    leagueId: 'super_ligue_niger',
    leagueName: 'Super Ligue Niger',
    sportCategory: 'FOOTBALL',
    homeTeamId: 'team_sahel',
    homeTeamName: 'Sahel SC',
    awayTeamId: 'team_nigelec',
    awayTeamName: 'ASN Nigelec',
    homeScore: 0,
    awayScore: 0,
    status: 'UPCOMING',
    matchMinute: '17:00 WAT',
    venue: 'Stade Municipal, Niamey',
    sourceType: 'MANUAL',
    possessionHome: 50,
    shotsHome: 0,
    shotsAway: 0,
    cornersHome: 0,
    cornersAway: 0,
    featured: false,
    visibility: 'PUBLIC',
    createdAt: ts,
    updatedAt: ts
  });

  batch.set(doc(db, 'matches', 'match_douanes_janger'), {
    leagueId: 'super_ligue_niger',
    leagueName: 'Super Ligue Niger',
    sportCategory: 'FOOTBALL',
    homeTeamId: 'team_douanes',
    homeTeamName: 'AS Douanes',
    awayTeamId: 'team_janger',
    awayTeamName: 'Janghorzo FC',
    homeScore: 3,
    awayScore: 1,
    status: 'COMPLETED',
    matchMinute: 'FT',
    venue: 'Stade de Maradi',
    sourceType: 'MANUAL',
    possessionHome: 58,
    shotsHome: 14,
    shotsAway: 6,
    cornersHome: 7,
    cornersAway: 2,
    featured: false,
    visibility: 'PUBLIC',
    createdAt: ts,
    updatedAt: ts
  });

  const wrestlers = [
    ['w_issaka', 'Issaka Issaka', 'El Hadji', 'Dogondoutchi', 'Dosso', 112, 42, 3, 2, 6, 'Six-time Sabre National champion renowned for explosive hip throws and tactical balance.'],
    ['w_kadri', 'Kadri Abdou', 'Issa', 'Konni', 'Tahoua', 108, 36, 5, 1, 3, 'Former Sabre National winner from Tahoua celebrated for speed and endurance in marathon bouts.'],
    ['w_aibo', 'Aibo Hassan', 'Zaki', 'Tessaoua', 'Maradi', 115, 29, 4, 2, 2, 'Powerhouse of the Maradi arena with undefeated streaks in regional championships.'],
    ['w_abdou', 'Abdou Salam', 'Damagaram', 'Mirriah', 'Zinder', 104, 25, 6, 3, 1, 'Technical specialist from Zinder known for rapid leg sweeps and defensive counters.']
  ];
  wrestlers.forEach(([id, name, nickname, village, region, weightKg, wins, losses, draws, titlesCount, bio]) => {
    batch.set(doc(db, 'wrestlers', id), {
      name,
      nickname,
      village,
      region,
      weightKg,
      wins,
      losses,
      draws,
      titlesCount,
      bio,
      featured: true,
      visibility: 'PUBLIC',
      createdAt: ts,
      updatedAt: ts
    });
  });

  batch.set(doc(db, 'kokowa_events', 'ke_sabre_final'), {
    title: 'Sabre National 46e Édition — Grande Finale',
    location: 'Arène de Maradi',
    region: 'Maradi',
    status: 'LIVE',
    wrestlerAId: 'w_issaka',
    wrestlerAName: 'Issaka Issaka (Dosso)',
    wrestlerBId: 'w_aibo',
    wrestlerBName: 'Aibo Hassan (Maradi)',
    roundLabel: 'Final — Sabre National',
    broadcastId: 'bc_kokowa_sabre',
    scheduledLabel: 'LIVE NOW',
    visibility: 'PUBLIC',
    createdAt: ts,
    updatedAt: ts
  });

  batch.set(doc(db, 'kokowa_events', 'ke_tahoua_cup'), {
    title: "Coupe de l'Unité — Semi-Final",
    location: 'Arène de Tahoua',
    region: 'Tahoua',
    status: 'UPCOMING',
    wrestlerAId: 'w_kadri',
    wrestlerAName: 'Kadri Abdou (Tahoua)',
    wrestlerBId: 'w_abdou',
    wrestlerBName: 'Abdou Salam (Zinder)',
    roundLabel: 'Semi-Final',
    scheduledLabel: 'Friday 16:30 WAT',
    visibility: 'PUBLIC',
    createdAt: ts,
    updatedAt: ts
  });

  batch.set(doc(db, 'tournaments', 't_sabre_national'), {
    name: 'Sabre National de Kokowa',
    sportCategory: 'KOKOWA',
    season: '2026',
    location: 'Maradi, Niger',
    status: 'ONGOING',
    participantNames: ['Dosso', 'Tahoua', 'Maradi', 'Zinder', 'Niamey', 'Agadez', 'Tillabéri', 'Diffa'],
    knockoutBracketSummary: [
      'QF|Dosso def. Agadez',
      'QF|Maradi def. Diffa',
      'SF|Issaka Issaka (Dosso) def. Kadri Abdou (Tahoua)',
      'SF|Aibo Hassan (Maradi) def. Abdou Salam (Zinder)',
      'FINAL|Issaka Issaka vs Aibo Hassan — LIVE'
    ],
    announcement: 'Official 8-region championship hosted in Maradi. Free live coverage on SPORT NIGER.',
    popular: true,
    visibility: 'PUBLIC',
    createdAt: ts,
    updatedAt: ts
  });

  batch.set(doc(db, 'tournaments', 't_coupe_du_niger'), {
    name: 'Coupe Nationale du Niger',
    sportCategory: 'FOOTBALL',
    season: '2026',
    location: 'Niamey, Niger',
    status: 'ONGOING',
    participantNames: ['AS FAN', 'USGN', 'Sahel SC', 'ASN Nigelec', 'AS Douanes', 'Janghorzo FC'],
    knockoutBracketSummary: [
      'QF|AS FAN 2-0 ASN Nigelec',
      'QF|USGN 1-0 AS Douanes',
      'SF|AS FAN vs Sahel SC — Nov 24',
      'SF|USGN vs Janghorzo FC — Nov 25'
    ],
    announcement: 'Semi-finals take place at Stade Général Seyni Kountché.',
    popular: true,
    visibility: 'PUBLIC',
    createdAt: ts,
    updatedAt: ts
  });

  batch.set(doc(db, 'news', 'news_sabre_opening'), {
    title: 'Maradi Packed as Sabre National Reaches Historic Final',
    summary: 'Thousands gather at the traditional wrestling arena while viewers across Niger stream free on SPORT NIGER.',
    body: "The 46th Sabre National brings together elite champions from all eight regions of Niger. Dosso's Issaka Issaka faces Maradi's Aibo Hassan in an electrifying final broadcast live in HD.",
    sportCategory: 'KOKOWA',
    authorName: 'SPORT NIGER Desk',
    breaking: true,
    visibility: 'PUBLIC',
    createdAt: ts,
    updatedAt: ts
  });

  batch.set(doc(db, 'news', 'news_super_ligue'), {
    title: 'AS FAN Leads Super Ligue Niger After Derby Thrills',
    summary: 'Niamey derby delivers goals and tactical intensity as race for the title heats up.',
    body: 'AS FAN Niamey maintained top spot with clinical finishing in front of home supporters, while USGN stays three points behind.',
    sportCategory: 'FOOTBALL',
    authorName: 'Amadou Bello',
    breaking: false,
    visibility: 'PUBLIC',
    createdAt: ts,
    updatedAt: ts
  });

  batch.set(doc(db, 'highlights', 'hl_kokowa_throw'), {
    title: 'Best Throws — Sabre National Semi-Finals',
    sportCategory: 'KOKOWA',
    durationLabel: '04:18',
    videoUrl: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8',
    viewCount: 28400,
    featured: true,
    visibility: 'PUBLIC',
    createdAt: ts,
    updatedAt: ts
  });

  batch.set(doc(db, 'banners', 'banner_partner'), {
    title: 'Free Public Sports Coverage Across Niger',
    subtitle: 'Supported by community partners to keep football & Kokowa streaming free for everyone.',
    sponsorName: 'SPORT NIGER Partners',
    active: true,
    visibility: 'PUBLIC',
    createdAt: ts,
    updatedAt: ts
  });

  await batch.commit();
  await recordAudit(actorRole, 'SEED_CATALOG', 'system', 'official_catalog', 'Initialized official SPORT NIGER catalog');
}
