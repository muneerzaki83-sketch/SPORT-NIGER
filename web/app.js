import {
  LANGUAGES,
  getLangMeta,
  t
} from './i18n.js';
import {
  completeRedirectSignInIfAny,
  signInWithGoogle,
  signOutUser,
  listenAuth,
  ensureUserProfileExists,
  subscribePublicCollection,
  subscribeMatchEvents,
  subscribeBroadcastComments,
  subscribeUserProfile,
  subscribeStaffRole,
  subscribeStaffDirectory,
  subscribeAuditLogs,
  getBroadcastSecret,
  updateUserLanguage,
  updateNotificationPreferences,
  toggleFavoriteId,
  postChatComment,
  reportChatComment,
  createBroadcast,
  updateBroadcastStatus,
  toggleBroadcastChat,
  moderateDeleteComment,
  blockUserFromChat,
  updateMatchScore,
  addMatchEvent,
  addWrestler,
  createKokowaEvent,
  createSportCategory,
  createTournament,
  publishNews,
  assignStaffMember,
  setStaffStatus,
  seedOfficialCatalog,
  rolePermissions,
  STAFF_ROLES
} from './firebase-client.js';

const LANG_STORAGE_KEY = 'sport_niger_lang';

const state = {
  lang: localStorage.getItem(LANG_STORAGE_KEY) || 'en',
  user: null,
  authReady: false,
  signingIn: false,
  authError: '',
  userProfile: null,
  staffRole: 'VIEWER',

  categories: [],
  broadcasts: [],
  matches: [],
  leagues: [],
  teams: [],
  wrestlers: [],
  kokowaEvents: [],
  tournaments: [],
  news: [],
  highlights: [],
  banners: [],
  staffDirectory: [],
  auditLogs: [],

  route: parseHash(),
  searchQuery: '',
  selectedCategory: 'ALL',
  scoresTab: 'live',
  adminTab: 'overview',

  activeComments: [],
  activeMatchEvents: [],
  activeBroadcastSecret: null,
  modal: null,
  toast: ''
};

let dataUnsubs = [];
let staffUnsubs = [];
let playerCommentsUnsub = null;
let matchEventsUnsub = null;
let activePlayerBroadcastId = null;
let activeDetailMatchId = null;
let hlsInstance = null;
let modalHlsInstance = null;
let toastTimer = null;

function esc(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function safeUrl(url) {
  if (!url || typeof url !== 'string') return '';
  try {
    const parsed = new URL(url.trim(), window.location.origin);
    if (parsed.protocol === 'https:' || parsed.protocol === 'http:') {
      return parsed.href;
    }
  } catch {
    return '';
  }
  return '';
}

function showToast(msg) {
  state.toast = msg;
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    state.toast = '';
    renderToast();
  }, 4000);
  renderToast();
}

function tr(key) {
  return t(state.lang, key);
}

function applyDocumentLanguage() {
  const meta = getLangMeta(state.lang);
  document.documentElement.lang = meta.code;
  document.documentElement.dir = meta.dir;
}

function setLanguage(code, syncToFirestore = true) {
  if (!['en', 'ha', 'fr', 'ar'].includes(code)) return;
  state.lang = code;
  localStorage.setItem(LANG_STORAGE_KEY, code);
  applyDocumentLanguage();
  if (syncToFirestore && state.user) {
    updateUserLanguage(state.user.uid, code).catch((err) => {
      console.warn('Could not sync language:', err);
    });
  }
  render();
}

function parseHash() {
  const raw = (window.location.hash || '#home').replace(/^#/, '');
  const parts = raw.split('/').filter(Boolean);
  const name = parts[0] || 'home';
  const param = parts[1] ? decodeURIComponent(parts[1]) : null;
  return { name, param };
}

function navigate(hash) {
  if (window.location.hash === hash) {
    state.route = parseHash();
    syncRouteSubscriptions();
    render();
    window.scrollTo({ top: 0, behavior: 'smooth' });
  } else {
    window.location.hash = hash;
  }
}

function clearSubscriptions(list) {
  while (list.length) {
    const fn = list.pop();
    try { fn(); } catch {}
  }
}

function destroyHls() {
  if (hlsInstance) {
    try { hlsInstance.destroy(); } catch {}
    hlsInstance = null;
  }
}

function destroyModalHls() {
  if (modalHlsInstance) {
    try { modalHlsInstance.destroy(); } catch {}
    modalHlsInstance = null;
  }
}

function startAuthenticatedSubscriptions(user) {
  clearSubscriptions(dataUnsubs);

  dataUnsubs.push(subscribeUserProfile(user.uid, (profile) => {
    state.userProfile = profile;
    if (profile?.preferredLanguage && ['en', 'ha', 'fr', 'ar'].includes(profile.preferredLanguage)) {
      if (state.lang !== profile.preferredLanguage) {
        state.lang = profile.preferredLanguage;
        localStorage.setItem(LANG_STORAGE_KEY, state.lang);
        applyDocumentLanguage();
      }
    }
    render();
  }));

  dataUnsubs.push(subscribeStaffRole(user, (role) => {
    state.staffRole = role;
    clearSubscriptions(staffUnsubs);
    const perms = rolePermissions(role);
    if (perms.isStaff) {
      if (role === 'SUPER_ADMIN') {
        staffUnsubs.push(subscribeStaffDirectory((items) => {
          state.staffDirectory = items;
          render();
        }));
        staffUnsubs.push(subscribeAuditLogs((items) => {
          state.auditLogs = items;
          render();
        }));
      }
    } else {
      state.staffDirectory = [];
      state.auditLogs = [];
    }
    render();
  }));

  dataUnsubs.push(subscribePublicCollection('sport_categories', (items) => {
    state.categories = items.filter((c) => c.active !== false).sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
    render();
  }));

  dataUnsubs.push(subscribePublicCollection('broadcasts', (items) => {
    state.broadcasts = items.sort((a, b) => {
      if ((a.status === 'LIVE') !== (b.status === 'LIVE')) return a.status === 'LIVE' ? -1 : 1;
      if (!!a.featured !== !!b.featured) return a.featured ? -1 : 1;
      return (b.viewerCount ?? 0) - (a.viewerCount ?? 0);
    });
    render();
  }));

  dataUnsubs.push(subscribePublicCollection('matches', (items) => {
    state.matches = items.sort((a, b) => {
      const aLive = a.status === 'LIVE' || a.status === 'HALFTIME';
      const bLive = b.status === 'LIVE' || b.status === 'HALFTIME';
      if (aLive !== bLive) return aLive ? -1 : 1;
      if (!!a.featured !== !!b.featured) return a.featured ? -1 : 1;
      return (a.status === 'COMPLETED') - (b.status === 'COMPLETED');
    });
    render();
  }));

  dataUnsubs.push(subscribePublicCollection('leagues', (items) => {
    state.leagues = items;
    render();
  }));

  dataUnsubs.push(subscribePublicCollection('teams', (items) => {
    state.teams = items.sort((a, b) => {
      if ((b.points ?? 0) !== (a.points ?? 0)) return (b.points ?? 0) - (a.points ?? 0);
      const gdA = (a.goalsFor ?? 0) - (a.goalsAgainst ?? 0);
      const gdB = (b.goalsFor ?? 0) - (b.goalsAgainst ?? 0);
      if (gdB !== gdA) return gdB - gdA;
      return (b.goalsFor ?? 0) - (a.goalsFor ?? 0);
    });
    render();
  }));

  dataUnsubs.push(subscribePublicCollection('wrestlers', (items) => {
    state.wrestlers = items.sort((a, b) => {
      if (!!a.featured !== !!b.featured) return a.featured ? -1 : 1;
      if ((b.titlesCount ?? 0) !== (a.titlesCount ?? 0)) return (b.titlesCount ?? 0) - (a.titlesCount ?? 0);
      return (b.wins ?? 0) - (a.wins ?? 0);
    });
    render();
  }));

  dataUnsubs.push(subscribePublicCollection('kokowa_events', (items) => {
    state.kokowaEvents = items.sort((a, b) => {
      if ((a.status === 'LIVE') !== (b.status === 'LIVE')) return a.status === 'LIVE' ? -1 : 1;
      return (a.status === 'COMPLETED') - (b.status === 'COMPLETED');
    });
    render();
  }));

  dataUnsubs.push(subscribePublicCollection('tournaments', (items) => {
    state.tournaments = items.sort((a, b) => {
      if (!!a.popular !== !!b.popular) return a.popular ? -1 : 1;
      return (b.status === 'ONGOING') - (a.status === 'ONGOING');
    });
    render();
  }));

  dataUnsubs.push(subscribePublicCollection('news', (items) => {
    state.news = items.sort((a, b) => {
      if (!!a.breaking !== !!b.breaking) return a.breaking ? -1 : 1;
      return (b.createdAt?.seconds ?? 0) - (a.createdAt?.seconds ?? 0);
    });
    render();
  }));

  dataUnsubs.push(subscribePublicCollection('highlights', (items) => {
    state.highlights = items.sort((a, b) => {
      if (!!a.featured !== !!b.featured) return a.featured ? -1 : 1;
      return (b.viewCount ?? 0) - (a.viewCount ?? 0);
    });
    render();
  }));

  dataUnsubs.push(subscribePublicCollection('banners', (items) => {
    state.banners = items.filter((b) => b.active !== false);
    render();
  }));
}

function syncRouteSubscriptions() {
  if (!state.user) return;
  const { name, param } = state.route;

  if (name === 'player' && param) {
    if (activePlayerBroadcastId !== param) {
      if (playerCommentsUnsub) playerCommentsUnsub();
      activePlayerBroadcastId = param;
      state.activeComments = [];
      state.activeBroadcastSecret = null;
      playerCommentsUnsub = subscribeBroadcastComments(param, (comments) => {
        state.activeComments = comments;
        render();
      });
      if (rolePermissions(state.staffRole).canManageBroadcasts) {
        getBroadcastSecret(param).then((sec) => {
          state.activeBroadcastSecret = sec;
          render();
        }).catch(() => {});
      }
    }
  } else {
    if (playerCommentsUnsub) {
      playerCommentsUnsub();
      playerCommentsUnsub = null;
    }
    activePlayerBroadcastId = null;
    state.activeBroadcastSecret = null;
    destroyHls();
  }

  if (name === 'match' && param) {
    if (activeDetailMatchId !== param) {
      if (matchEventsUnsub) matchEventsUnsub();
      activeDetailMatchId = param;
      state.activeMatchEvents = [];
      matchEventsUnsub = subscribeMatchEvents(param, (events) => {
        state.activeMatchEvents = events;
        render();
      });
    }
  } else {
    if (matchEventsUnsub) {
      matchEventsUnsub();
      matchEventsUnsub = null;
    }
    activeDetailMatchId = null;
  }
}

function localizedCategoryName(cat) {
  if (!cat) return '';
  if (state.lang === 'ha') return cat.nameHa || cat.nameEn || cat.slug;
  if (state.lang === 'fr') return cat.nameFr || cat.nameEn || cat.slug;
  if (state.lang === 'ar') return cat.nameAr || cat.nameEn || cat.slug;
  return cat.nameEn || cat.slug;
}

function statusBadge(status) {
  const s = (status || '').toUpperCase();
  if (s === 'LIVE') return `<span class="badge badge-live"><span class="pulse-dot"></span>${esc(tr('statusLive'))}</span>`;
  if (s === 'HALFTIME') return `<span class="badge badge-saffron">${esc(tr('statusHalftime'))}</span>`;
  if (s === 'UPCOMING') return `<span class="badge badge-blue">${esc(tr('statusUpcoming'))}</span>`;
  if (s === 'ONGOING') return `<span class="badge badge-emerald">${esc(tr('ongoingStatus'))}</span>`;
  if (s === 'COMPLETED') return `<span class="badge badge-muted">${esc(tr('statusCompleted'))}</span>`;
  return `<span class="badge badge-muted">${esc(s || tr('statusOffline'))}</span>`;
}

function renderHeader() {
  const perms = rolePermissions(state.staffRole);
  const current = state.route.name;
  const navItems = [
    { id: 'home', hash: '#home', label: tr('navHome') },
    { id: 'live', hash: '#live', label: tr('navLive') },
    { id: 'scores', hash: '#scores', label: tr('navScores') },
    { id: 'kokowa', hash: '#kokowa', label: tr('navKokowa') },
    { id: 'tournaments', hash: '#tournaments', label: tr('navTournaments') },
    { id: 'profile', hash: '#profile', label: tr('navProfile') }
  ];

  return `
    <header class="top-header">
      <div class="niger-ribbon"></div>
      <div class="header-inner">
        <a href="#home" class="brand-link">
          <img src="./assets/sport-niger-logo.svg" alt="SPORT NIGER" class="brand-logo" />
          <div class="brand-wordmark">
            <span class="brand-title">SPORT <span>NIGER</span></span>
            <span class="brand-tagline">${esc(tr('tagline'))}</span>
          </div>
        </a>

        ${state.user ? `
          <nav class="desktop-nav" aria-label="Main navigation">
            ${navItems.map((item) => `
              <button class="nav-pill ${current === item.id ? 'active' : ''}" data-nav="${item.hash}">
                ${esc(item.label)}
              </button>
            `).join('')}
          </nav>
        ` : ''}

        <div class="header-actions">
          <label class="sr-only" for="lang-switcher">${esc(tr('languageSetting'))}</label>
          <select id="lang-switcher" class="lang-select" aria-label="${esc(tr('languageSetting'))}">
            ${LANGUAGES.map((l) => `
              <option value="${l.code}" ${state.lang === l.code ? 'selected' : ''}>${esc(l.nativeName)}</option>
            `).join('')}
          </select>

          ${state.user && perms.isStaff ? `
            <button class="staff-badge-btn" data-nav="#admin">${esc(perms.role.replace(/_/g, ' '))}</button>
          ` : ''}

          ${!state.user && state.authReady ? `
            <button class="btn btn-primary btn-sm" id="header-signin-btn">${esc(tr('signInWithGoogle'))}</button>
          ` : ''}
        </div>
      </div>
    </header>
  `;
}

function renderMobileBottomNav() {
  if (!state.user) return '';
  const current = state.route.name;
  const items = [
    { id: 'home', hash: '#home', label: tr('navHome'), icon: '⌂' },
    { id: 'live', hash: '#live', label: tr('navLive'), icon: '▶' },
    { id: 'scores', hash: '#scores', label: tr('navScores'), icon: '⚽' },
    { id: 'kokowa', hash: '#kokowa', label: tr('navKokowa'), icon: '🤼' },
    { id: 'tournaments', hash: '#tournaments', label: tr('navTournaments'), icon: '🏆' },
    { id: 'profile', hash: '#profile', label: tr('navProfile'), icon: '👤' }
  ];
  return `
    <nav class="mobile-bottom-nav" aria-label="Mobile navigation">
      ${items.map((item) => `
        <button class="mobile-nav-item ${current === item.id ? 'active' : ''}" data-nav="${item.hash}">
          <span aria-hidden="true">${item.icon}</span>
          <span>${esc(item.label)}</span>
        </button>
      `).join('')}
    </nav>
  `;
}

function renderLandingGateway() {
  return `
    <section class="landing-hero">
      <div>
        <span class="hero-pill">● ${esc(tr('appName'))} • WEB &amp; ANDROID</span>
        <h1>${esc(tr('welcomeTitle'))}</h1>
        <p class="lead">${esc(tr('welcomeSubtitle'))}</p>
        <div style="display:flex;flex-wrap:wrap;gap:0.75rem;align-items:center;">
          <button class="btn btn-primary" id="landing-signin-btn" ${state.signingIn ? 'disabled' : ''}>
            ${state.signingIn ? esc(tr('signingIn')) : esc(tr('signInWithGoogle'))}
          </button>
        </div>
        <div class="free-note-pill">✓ ${esc(tr('freePublicAccessNote'))}</div>
        <p style="color:var(--text-soft);font-size:0.88rem;margin-top:0.85rem;">${esc(tr('authRequiredNotice'))}</p>
        ${state.authError ? `
          <div class="card" style="margin-top:1rem;border-color:var(--live-crimson);background:rgba(255,51,88,0.12);">
            <strong style="color:#ff8fa3;">Sign-In Notice</strong>
            <span style="font-size:0.86rem;color:var(--text-white);">${esc(state.authError)}</span>
          </div>
        ` : ''}
      </div>

      <div class="hero-showcase-card">
        <div style="display:flex;align-items:center;gap:0.85rem;">
          <img src="./assets/sport-niger-logo.svg" alt="SPORT NIGER" style="width:60px;height:60px;border-radius:14px;" />
          <div>
            <div style="font-family:var(--font-display);font-size:1.4rem;font-weight:800;">SPORT NIGER</div>
            <div style="font-size:0.84rem;color:var(--bright-blue);">${esc(tr('tagline'))}</div>
          </div>
        </div>
        <div class="card" style="background:var(--bg-pitch-navy);">
          <div class="card-meta-row">
            ${statusBadge('LIVE')}
            <span class="badge badge-blue">SUPER LIGUE NIGER</span>
          </div>
          <div class="score-row">
            <span class="team-name-home">AS FAN Niamey</span>
            <span class="score-pill">2 - 1</span>
            <span class="team-name-away">USGN Niamey</span>
          </div>
        </div>
        <div class="card" style="background:var(--bg-pitch-navy);border-color:rgba(255,140,26,0.4);">
          <div class="card-meta-row">
            ${statusBadge('LIVE')}
            <span class="badge badge-saffron">SABRE NATIONAL KOKOWA</span>
          </div>
          <div style="font-weight:700;">Issaka Issaka (Dosso) vs Aibo Hassan (Maradi)</div>
        </div>
      </div>
    </section>

    <div class="feature-grid">
      <article class="feature-card">
        <span class="badge badge-live">HLS &amp; YOUTUBE</span>
        <h3>${esc(tr('featureLiveTitle'))}</h3>
        <p>${esc(tr('featureLiveDesc'))}</p>
      </article>
      <article class="feature-card">
        <span class="badge badge-blue">LIVE SCORES</span>
        <h3>${esc(tr('featureScoresTitle'))}</h3>
        <p>${esc(tr('featureScoresDesc'))}</p>
      </article>
      <article class="feature-card">
        <span class="badge badge-saffron">KOKOWA ARENA</span>
        <h3>${esc(tr('featureKokowaTitle'))}</h3>
        <p>${esc(tr('featureKokowaDesc'))}</p>
      </article>
      <article class="feature-card">
        <span class="badge badge-emerald">EN • HA • FR • AR</span>
        <h3>${esc(tr('featureLangTitle'))}</h3>
        <p>${esc(tr('featureLangDesc'))}</p>
      </article>
    </div>
  `;
}

function renderBroadcastCard(bc, hero = false) {
  return `
    <article class="card clickable ${hero ? 'hero-broadcast-card' : ''}" data-nav="#player/${encodeURIComponent(bc.id)}">
      <div class="card-meta-row">
        ${statusBadge(bc.status)}
        <span class="badge badge-blue">${esc(bc.sportCategory)} • ${esc(bc.streamType)}</span>
      </div>
      <h3 style="margin:0;font-size:1.12rem;">${esc(bc.title)}</h3>
      <p style="margin:0;color:var(--text-soft);font-size:0.88rem;">${esc(bc.description)}</p>
      <div class="card-meta-row" style="margin-top:auto;padding-top:0.35rem;">
        <span style="font-size:0.82rem;color:var(--text-soft);">👁 ${(bc.viewerCount ?? 0).toLocaleString()} ${esc(tr('viewersWatching'))}</span>
        <span class="btn btn-primary btn-sm">${esc(tr('watchLive'))}</span>
      </div>
    </article>
  `;
}

function renderMatchCard(m) {
  return `
    <article class="card clickable" data-nav="#match/${encodeURIComponent(m.id)}">
      <div class="card-meta-row">
        <span style="font-size:0.78rem;color:var(--bright-blue);font-weight:700;">${esc(m.leagueName)}</span>
        <div style="display:flex;gap:0.4rem;align-items:center;">
          ${statusBadge(m.status)}
          <span class="badge badge-muted">${esc(m.matchMinute)}</span>
        </div>
      </div>
      <div class="score-row">
        <span class="team-name-home">${esc(m.homeTeamName)}</span>
        <span class="score-pill">${m.status === 'UPCOMING' ? 'VS' : `${m.homeScore ?? 0} - ${m.awayScore ?? 0}`}</span>
        <span class="team-name-away">${esc(m.awayTeamName)}</span>
      </div>
      <div class="card-meta-row" style="font-size:0.8rem;color:var(--text-soft);">
        <span>📍 ${esc(m.venue)}</span>
        <span class="badge badge-muted">${esc(tr('manualSourceBadge'))}</span>
      </div>
    </article>
  `;
}

function renderWrestlerCard(w) {
  const initials = (w.name || 'K').split(' ').map((p) => p[0]).join('').slice(0, 2).toUpperCase();
  return `
    <article class="card clickable" data-nav="#wrestler/${encodeURIComponent(w.id)}">
      <div style="display:flex;align-items:center;gap:0.85rem;">
        <div class="wrestler-avatar">${esc(initials)}</div>
        <div>
          <h3 style="margin:0;font-size:1.05rem;">${esc(w.name)}</h3>
          <div style="font-size:0.82rem;color:var(--niger-saffron);font-weight:700;">“${esc(w.nickname)}” • ${esc(w.region)}</div>
        </div>
      </div>
      <div class="card-meta-row" style="font-size:0.84rem;color:var(--text-soft);">
        <span>${esc(tr('villageLabel'))}: <strong style="color:var(--text-white);">${esc(w.village)}</strong></span>
        <span>${esc(tr('weightLabel'))}: <strong style="color:var(--text-white);">${w.weightKg ?? 100} kg</strong></span>
      </div>
      <div class="card-meta-row">
        <span class="badge badge-emerald">${w.wins ?? 0}W - ${w.losses ?? 0}L - ${w.draws ?? 0}D</span>
        <span class="badge badge-saffron">🏆 ${w.titlesCount ?? 0} ${esc(tr('titlesLabel'))}</span>
      </div>
    </article>
  `;
}

function renderKokowaEventCard(ev) {
  return `
    <article class="card">
      <div class="card-meta-row">
        ${statusBadge(ev.status)}
        <span class="badge badge-saffron">${esc(ev.roundLabel)}</span>
      </div>
      <h3 style="margin:0;font-size:1.06rem;">${esc(ev.title)}</h3>
      <div style="font-weight:700;color:var(--text-white);">${esc(ev.wrestlerAName)} <span style="color:var(--niger-saffron);">VS</span> ${esc(ev.wrestlerBName)}</div>
      <div class="card-meta-row" style="font-size:0.84rem;color:var(--text-soft);">
        <span>📍 ${esc(ev.location)} (${esc(ev.region)})</span>
        <span>${esc(ev.scheduledLabel)}</span>
      </div>
      ${ev.winnerName ? `<div class="badge badge-emerald">${esc(tr('winnerLabel'))}: ${esc(ev.winnerName)}</div>` : ''}
      ${ev.broadcastId ? `
        <button class="btn btn-saffron btn-sm" data-nav="#player/${encodeURIComponent(ev.broadcastId)}">${esc(tr('watchLive'))}</button>
      ` : ''}
    </article>
  `;
}

function renderTournamentCard(tItem) {
  return `
    <article class="card clickable" data-nav="#tournament/${encodeURIComponent(tItem.id)}">
      <div class="card-meta-row">
        <span class="badge badge-blue">${esc(tItem.sportCategory)} • ${esc(tItem.season)}</span>
        ${statusBadge(tItem.status)}
      </div>
      <h3 style="margin:0;font-size:1.1rem;">${esc(tItem.name)}</h3>
      <div style="font-size:0.86rem;color:var(--text-soft);">📍 ${esc(tItem.location)}</div>
      ${tItem.announcement ? `<p style="margin:0;font-size:0.84rem;color:var(--text-soft);">${esc(tItem.announcement)}</p>` : ''}
      ${tItem.championName ? `<span class="badge badge-saffron">🏆 ${esc(tr('championLabel'))}: ${esc(tItem.championName)}</span>` : ''}
    </article>
  `;
}

function renderHomeView() {
  const q = state.searchQuery.trim().toLowerCase();
  const cat = state.selectedCategory;
  const matchesFilter = (category, text) => {
    const catOk = cat === 'ALL' || (category || '').toUpperCase() === cat.toUpperCase();
    const qOk = !q || (text || '').toLowerCase().includes(q);
    return catOk && qOk;
  };

  const filteredBroadcasts = state.broadcasts.filter((b) => matchesFilter(b.sportCategory, `${b.title} ${b.description}`));
  const filteredMatches = state.matches.filter((m) => matchesFilter(m.sportCategory, `${m.homeTeamName} ${m.awayTeamName} ${m.leagueName}`));
  const filteredWrestlers = state.wrestlers.filter((w) => (cat === 'ALL' || cat === 'KOKOWA') && (!q || `${w.name} ${w.nickname} ${w.village} ${w.region}`.toLowerCase().includes(q)));
  const filteredTournaments = state.tournaments.filter((tItem) => matchesFilter(tItem.sportCategory, `${tItem.name} ${tItem.location}`));
  const filteredNews = state.news.filter((n) => matchesFilter(n.sportCategory, `${n.title} ${n.summary}`));
  const filteredHighlights = state.highlights.filter((h) => matchesFilter(h.sportCategory, h.title));

  return `
    <div class="toolbar-row">
      <div class="search-box">
        <input id="home-search-input" class="search-input" type="search" value="${esc(state.searchQuery)}" placeholder="${esc(tr('searchPlaceholder'))}" />
      </div>
      <button class="btn btn-saffron" data-nav="#kokowa">🤼 ${esc(tr('openKokowaHub'))}</button>
    </div>

    <div class="chip-row">
      <button class="filter-chip ${cat === 'ALL' ? 'active' : ''}" data-cat="ALL">${esc(tr('allSports'))}</button>
      ${state.categories.map((c) => `
        <button class="filter-chip ${cat === c.slug ? 'active' : ''}" data-cat="${esc(c.slug)}">
          ${esc(localizedCategoryName(c))}
        </button>
      `).join('')}
    </div>

    ${state.banners.length ? `
      <section class="section-block">
        ${state.banners.map((b) => {
          const url = safeUrl(b.ctaUrl);
          return `
            <div class="sponsor-banner">
              <div>
                <span class="badge badge-blue">${esc(tr('sponsorLabel'))} • ${esc(b.sponsorName)}</span>
                <h3 style="margin:0.4rem 0 0.2rem;">${esc(b.title)}</h3>
                <p style="margin:0;font-size:0.88rem;color:var(--text-soft);">${esc(b.subtitle)}</p>
              </div>
              ${url ? `<a class="btn btn-outline btn-sm" href="${esc(url)}" target="_blank" rel="noopener noreferrer">${esc(tr('seeAll'))}</a>` : ''}
            </div>
          `;
        }).join('')}
      </section>
    ` : ''}

    <section class="section-block">
      <div class="section-header">
        <h2>${esc(tr('liveBroadcasts'))}</h2>
        <button class="btn btn-outline btn-sm" data-nav="#live">${esc(tr('seeAll'))}</button>
      </div>
      ${filteredBroadcasts.length ? `
        <div class="cards-grid">
          ${filteredBroadcasts.map((b, idx) => renderBroadcastCard(b, idx === 0 && b.status === 'LIVE')).join('')}
        </div>
      ` : `<div class="card">${esc(tr('noStreamsEmpty'))}</div>`}
    </section>

    <section class="section-block">
      <div class="section-header">
        <h2>${esc(tr('featuredLiveMatches'))}</h2>
        <button class="btn btn-outline btn-sm" data-nav="#scores">${esc(tr('seeAll'))}</button>
      </div>
      ${filteredMatches.length ? `
        <div class="cards-grid">
          ${filteredMatches.map(renderMatchCard).join('')}
        </div>
      ` : `<div class="card">${esc(tr('emptyListDefault'))}</div>`}
    </section>

    ${filteredWrestlers.length ? `
      <section class="section-block">
        <div class="section-header">
          <h2>${esc(tr('featuredWrestlers'))}</h2>
          <button class="btn btn-outline btn-sm" data-nav="#kokowa">${esc(tr('openKokowaHub'))}</button>
        </div>
        <div class="cards-grid">
          ${filteredWrestlers.map(renderWrestlerCard).join('')}
        </div>
      </section>
    ` : ''}

    ${filteredTournaments.length ? `
      <section class="section-block">
        <div class="section-header">
          <h2>${esc(tr('popularTournaments'))}</h2>
          <button class="btn btn-outline btn-sm" data-nav="#tournaments">${esc(tr('seeAll'))}</button>
        </div>
        <div class="cards-grid">
          ${filteredTournaments.map(renderTournamentCard).join('')}
        </div>
      </section>
    ` : ''}

    ${filteredNews.length ? `
      <section class="section-block">
        <div class="section-header">
          <h2>${esc(tr('sportsNews'))}</h2>
        </div>
        <div class="cards-grid">
          ${filteredNews.map((n) => `
            <article class="card clickable" data-open-news="${esc(n.id)}">
              <div class="card-meta-row">
                ${n.breaking ? `<span class="badge badge-live">${esc(tr('breakingNews'))}</span>` : `<span class="badge badge-blue">${esc(n.sportCategory)}</span>`}
                <span style="font-size:0.78rem;color:var(--text-soft);">${esc(n.authorName)}</span>
              </div>
              <h3 style="margin:0;font-size:1.05rem;">${esc(n.title)}</h3>
              <p style="margin:0;color:var(--text-soft);font-size:0.86rem;">${esc(n.summary)}</p>
            </article>
          `).join('')}
        </div>
      </section>
    ` : ''}

    ${filteredHighlights.length ? `
      <section class="section-block">
        <div class="section-header">
          <h2>${esc(tr('featuredHighlights'))}</h2>
        </div>
        <div class="cards-grid">
          ${filteredHighlights.map((h) => `
            <article class="card clickable" data-open-highlight="${esc(h.id)}">
              <div class="card-meta-row">
                <span class="badge badge-blue">${esc(h.sportCategory)}</span>
                <span class="badge badge-muted">▶ ${esc(h.durationLabel)}</span>
              </div>
              <h3 style="margin:0;font-size:1.05rem;">${esc(h.title)}</h3>
              <span style="font-size:0.82rem;color:var(--text-soft);">👁 ${(h.viewCount ?? 0).toLocaleString()}</span>
            </article>
          `).join('')}
        </div>
      </section>
    ` : ''}
  `;
}

function renderLiveHubView() {
  const liveNow = state.broadcasts.filter((b) => b.status === 'LIVE');
  const scheduled = state.broadcasts.filter((b) => b.status !== 'LIVE');
  return `
    <div class="section-header">
      <h2>${esc(tr('liveChannels'))}</h2>
    </div>
    ${liveNow.length ? `
      <div class="cards-grid" style="margin-bottom:2rem;">
        ${liveNow.map((b) => renderBroadcastCard(b, true)).join('')}
      </div>
    ` : `<div class="card" style="margin-bottom:1.5rem;">${esc(tr('noStreamsEmpty'))}</div>`}

    ${scheduled.length ? `
      <div class="section-header">
        <h2>${esc(tr('upcomingStreams'))}</h2>
      </div>
      <div class="cards-grid">
        ${scheduled.map((b) => renderBroadcastCard(b, false)).join('')}
      </div>
    ` : ''}
  `;
}

function renderPlayerView(broadcastId) {
  const bc = state.broadcasts.find((b) => b.id === broadcastId);
  if (!bc) {
    return `
      <div class="card">
        <p>${esc(tr('noStreamsEmpty'))}</p>
        <button class="btn btn-primary btn-sm" data-nav="#live">${esc(tr('back'))}</button>
      </div>
    `;
  }
  const perms = rolePermissions(state.staffRole);
  const isOffline = bc.status === 'OFFLINE' || bc.status === 'ENDED';
  const playbackSafe = safeUrl(bc.playbackUrl);
  const chatBlocked = !!state.userProfile?.chatBlocked;

  return `
    <div style="display:flex;align-items:center;justify-content:space-between;gap:1rem;flex-wrap:wrap;margin-bottom:1rem;">
      <div style="display:flex;align-items:center;gap:0.75rem;">
        <button class="btn btn-outline btn-sm" data-nav="#live">← ${esc(tr('back'))}</button>
        <h2 style="margin:0;font-family:var(--font-display);font-size:1.65rem;">${esc(bc.title)}</h2>
      </div>
      <div style="display:flex;gap:0.5rem;align-items:center;">
        ${statusBadge(bc.status)}
        <span class="badge badge-blue">👁 ${(bc.viewerCount ?? 0).toLocaleString()} ${esc(tr('viewersWatching'))}</span>
      </div>
    </div>

    <div class="player-layout">
      <div>
        <div class="video-shell">
          <div class="video-stage" id="video-stage">
            ${isOffline ? `
              <div style="display:grid;place-items:center;height:100%;color:var(--text-soft);font-weight:700;">
                ${esc(bc.status)} • ${esc(bc.scheduledLabel)}
              </div>
            ` : bc.streamType === 'YOUTUBE_LIVE' && bc.youtubeVideoId ? `
              <iframe
                src="https://www.youtube.com/embed/${encodeURIComponent(bc.youtubeVideoId)}?autoplay=1&rel=0"
                title="${esc(bc.title)}"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; fullscreen"
                allowfullscreen></iframe>
            ` : `
              <video id="hls-video" controls playsinline autoplay muted></video>
            `}
          </div>
          <div class="video-controls-bar">
            <div style="display:flex;align-items:center;gap:0.65rem;flex-wrap:wrap;">
              <span class="badge badge-muted">${esc(tr('ingestProtocolLabel'))}: ${esc(bc.ingestProtocol)}</span>
              ${bc.streamType !== 'YOUTUBE_LIVE' && !isOffline ? `
                <select id="hls-quality-select" class="lang-select" aria-label="${esc(tr('streamQuality'))}">
                  <option value="-1">${esc(tr('qualityAuto'))}</option>
                </select>
              ` : ''}
            </div>
            <div style="display:flex;gap:0.5rem;flex-wrap:wrap;">
              ${bc.streamType === 'YOUTUBE_LIVE' && playbackSafe ? `
                <a class="btn btn-danger btn-sm" href="${esc(playbackSafe)}" target="_blank" rel="noopener noreferrer">${esc(tr('openYoutubeLive'))}</a>
              ` : ''}
              ${!isOffline && bc.streamType !== 'YOUTUBE_LIVE' ? `
                <button class="btn btn-outline btn-sm" id="player-fullscreen-btn">${esc(tr('fullscreen'))}</button>
              ` : ''}
            </div>
          </div>
        </div>

        <div class="card" style="margin-top:1rem;">
          <p style="margin:0;color:var(--text-soft);">${esc(bc.description)}</p>
        </div>

        ${perms.canManageBroadcasts ? `
          <div class="card" style="margin-top:1rem;border-color:var(--primary-blue);">
            <div class="card-meta-row">
              <strong>🔒 Restricted Staff Ingest Credentials</strong>
              <button class="btn btn-outline btn-sm" id="toggle-bc-chat-btn" data-id="${esc(bc.id)}" data-enabled="${bc.chatEnabled ? 'true' : 'false'}">
                ${esc(tr('toggleChat'))}: ${bc.chatEnabled ? 'ON' : 'OFF'}
              </button>
            </div>
            ${state.activeBroadcastSecret ? `
              <div style="font-family:var(--font-mono);font-size:0.82rem;color:var(--bright-blue);">
                RTMP: ${esc(state.activeBroadcastSecret.rtmpServerUrl)}<br/>
                Key: ${esc(state.activeBroadcastSecret.streamKeyMasked)}<br/>
                OBS: ${esc(state.activeBroadcastSecret.obsProfileNote)}
              </div>
            ` : `<span style="font-size:0.84rem;color:var(--text-soft);">${esc(tr('streamSecretsNote'))}</span>`}
          </div>
        ` : ''}
      </div>

      <aside class="chat-panel">
        <div class="chat-header">
          <strong>💬 ${esc(tr('liveChatTitle'))}</strong>
          <span class="badge ${bc.chatEnabled ? 'badge-emerald' : 'badge-muted'}">
            ${bc.chatEnabled ? 'OPEN' : 'LOCKED'}
          </span>
        </div>

        <div class="chat-messages" id="chat-messages">
          ${state.activeComments.length ? state.activeComments.map((c) => `
            <div class="chat-bubble">
              <div class="chat-bubble-head">
                <span class="chat-author">${esc(c.userName)}</span>
                <div style="display:flex;gap:0.35rem;">
                  <button class="btn btn-outline btn-sm" style="min-height:24px;padding:0 0.45rem;font-size:0.7rem;" data-report-comment="${esc(c.id)}">${esc(tr('reportComment'))}</button>
                  ${perms.canModerateChat ? `
                    <button class="btn btn-danger btn-sm" style="min-height:24px;padding:0 0.45rem;font-size:0.7rem;" data-delete-comment="${esc(c.id)}">${esc(tr('deleteComment'))}</button>
                    <button class="btn btn-saffron btn-sm" style="min-height:24px;padding:0 0.45rem;font-size:0.7rem;" data-block-user="${esc(c.userId)}">${esc(tr('blockUser'))}</button>
                  ` : ''}
                </div>
              </div>
              <div style="font-size:0.88rem;word-break:break-word;">${esc(c.message)}</div>
            </div>
          `).join('') : `<div style="color:var(--text-muted);font-size:0.88rem;">${esc(tr('emptyListDefault'))}</div>`}
        </div>

        ${!bc.chatEnabled ? `
          <div style="padding:0.85rem;font-size:0.84rem;color:var(--text-soft);background:var(--bg-pitch-navy);border-top:1px solid var(--border-slate);">
            ${esc(tr('chatDisabled'))}
          </div>
        ` : chatBlocked ? `
          <div style="padding:0.85rem;font-size:0.84rem;color:#ff8fa3;background:var(--bg-pitch-navy);border-top:1px solid var(--border-slate);">
            ${esc(tr('chatBlockedNotice'))}
          </div>
        ` : `
          <form class="chat-form" id="chat-form">
            <input id="chat-input" class="input" type="text" maxlength="400" placeholder="${esc(tr('chatPlaceholder'))}" required />
            <button type="submit" class="btn btn-primary btn-sm">${esc(tr('send'))}</button>
          </form>
        `}
      </aside>
    </div>
  `;
}

function mountVideoForRoute() {
  destroyHls();
  if (state.route.name !== 'player' || !state.route.param) return;
  const bc = state.broadcasts.find((b) => b.id === state.route.param);
  if (!bc || bc.status === 'OFFLINE' || bc.status === 'ENDED' || bc.streamType === 'YOUTUBE_LIVE') return;

  const videoEl = document.getElementById('hls-video');
  const qualitySelect = document.getElementById('hls-quality-select');
  const streamUrl = safeUrl(bc.playbackUrl);
  if (!videoEl || !streamUrl) return;

  if (window.Hls && window.Hls.isSupported()) {
    hlsInstance = new window.Hls({ enableWorker: true });
    hlsInstance.loadSource(streamUrl);
    hlsInstance.attachMedia(videoEl);
    hlsInstance.on(window.Hls.Events.MANIFEST_PARSED, (_, data) => {
      if (qualitySelect && Array.isArray(data.levels)) {
        qualitySelect.innerHTML = `<option value="-1">${esc(tr('qualityAuto'))}</option>` +
          data.levels.map((lvl, i) => `<option value="${i}">${lvl.height ? `${lvl.height}p` : `Level ${i + 1}`}</option>`).join('');
      }
      videoEl.play().catch(() => {});
    });
    if (qualitySelect) {
      qualitySelect.onchange = (e) => {
        if (hlsInstance) hlsInstance.currentLevel = parseInt(e.target.value, 10);
      };
    }
  } else if (videoEl.canPlayType('application/vnd.apple.mpegurl')) {
    videoEl.src = streamUrl;
    videoEl.play().catch(() => {});
  }
}

function renderScoresView() {
  const tab = state.scoresTab;
  let content = '';
  if (tab === 'standings') {
    content = state.teams.length ? `
      <div class="table-wrap">
        <table class="standings-table">
          <thead>
            <tr>
              <th>#</th>
              <th>${esc(tr('teamCol'))}</th>
              <th class="num">${esc(tr('playedCol'))}</th>
              <th class="num">${esc(tr('wonCol'))}</th>
              <th class="num">${esc(tr('drawnCol'))}</th>
              <th class="num">${esc(tr('lostCol'))}</th>
              <th class="num">${esc(tr('gdCol'))}</th>
              <th class="num">${esc(tr('ptsCol'))}</th>
            </tr>
          </thead>
          <tbody>
            ${state.teams.map((team, idx) => {
              const gd = (team.goalsFor ?? 0) - (team.goalsAgainst ?? 0);
              return `
                <tr>
                  <td>${idx + 1}</td>
                  <td>
                    <span class="club-badge-dot" style="background:${esc(team.badgeColorHex || '#1565FF')}"></span>
                    <strong>${esc(team.name)}</strong>
                  </td>
                  <td class="num">${team.played ?? 0}</td>
                  <td class="num">${team.won ?? 0}</td>
                  <td class="num">${team.drawn ?? 0}</td>
                  <td class="num">${team.lost ?? 0}</td>
                  <td class="num">${gd > 0 ? `+${gd}` : gd}</td>
                  <td class="num" style="color:var(--bright-blue);font-weight:800;">${team.points ?? 0}</td>
                </tr>
              `;
            }).join('')}
          </tbody>
        </table>
      </div>
    ` : `<div class="card">${esc(tr('emptyListDefault'))}</div>`;
  } else {
    const filtered = state.matches.filter((m) => {
      if (tab === 'live') return m.status === 'LIVE' || m.status === 'HALFTIME';
      if (tab === 'fixtures') return m.status === 'UPCOMING';
      return m.status === 'COMPLETED';
    });
    content = filtered.length
      ? `<div class="cards-grid">${filtered.map(renderMatchCard).join('')}</div>`
      : `<div class="card">${esc(tr('emptyListDefault'))}</div>`;
  }

  return `
    <div class="section-header">
      <h2>${esc(tr('navScores'))}</h2>
    </div>
    <div class="tab-bar">
      <button class="tab-btn ${tab === 'live' ? 'active' : ''}" data-scores-tab="live">${esc(tr('tabLiveScores'))}</button>
      <button class="tab-btn ${tab === 'fixtures' ? 'active' : ''}" data-scores-tab="fixtures">${esc(tr('tabFixtures'))}</button>
      <button class="tab-btn ${tab === 'results' ? 'active' : ''}" data-scores-tab="results">${esc(tr('tabResults'))}</button>
      <button class="tab-btn ${tab === 'standings' ? 'active' : ''}" data-scores-tab="standings">${esc(tr('tabStandings'))}</button>
    </div>
    ${content}
  `;
}

function renderMatchDetailView(matchId) {
  const m = state.matches.find((x) => x.id === matchId);
  if (!m) {
    return `<div class="card"><p>${esc(tr('emptyListDefault'))}</p><button class="btn btn-primary btn-sm" data-nav="#scores">${esc(tr('back'))}</button></div>`;
  }
  const possHome = m.possessionHome ?? 50;
  const possAway = 100 - possHome;
  const totalShots = Math.max(1, (m.shotsHome ?? 0) + (m.shotsAway ?? 0));
  const totalCorners = Math.max(1, (m.cornersHome ?? 0) + (m.cornersAway ?? 0));

  return `
    <div style="margin-bottom:1rem;">
      <button class="btn btn-outline btn-sm" data-nav="#scores">← ${esc(tr('back'))}</button>
    </div>

    <div class="card hero-broadcast-card" style="margin-bottom:1.5rem;">
      <div class="card-meta-row">
        <span class="badge badge-blue">${esc(m.leagueName)}</span>
        ${statusBadge(m.status)}
      </div>
      <div class="score-row" style="padding:1rem 0;">
        <div class="team-name-home" style="font-size:1.3rem;">${esc(m.homeTeamName)}</div>
        <div class="score-pill" style="font-size:2rem;padding:0.45rem 1.25rem;">${m.homeScore ?? 0} - ${m.awayScore ?? 0}</div>
        <div class="team-name-away" style="font-size:1.3rem;">${esc(m.awayTeamName)}</div>
      </div>
      <div class="card-meta-row" style="color:var(--text-soft);font-size:0.88rem;">
        <span>⏱ ${esc(m.matchMinute)} • 📍 ${esc(m.venue)}</span>
        ${m.broadcastId ? `<button class="btn btn-primary btn-sm" data-nav="#player/${encodeURIComponent(m.broadcastId)}">${esc(tr('watchLive'))}</button>` : ''}
      </div>
    </div>

    <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:1.25rem;">
      <div class="card">
        <h3 style="margin:0 0 0.75rem;">${esc(tr('matchStats'))}</h3>
        <div class="stat-row">
          <div class="stat-labels"><span>${possHome}%</span><span>${esc(tr('possession'))}</span><span>${possAway}%</span></div>
          <div class="stat-bar-track"><div class="stat-bar-fill" style="width:${possHome}%"></div></div>
        </div>
        <div class="stat-row">
          <div class="stat-labels"><span>${m.shotsHome ?? 0}</span><span>${esc(tr('shots'))}</span><span>${m.shotsAway ?? 0}</span></div>
          <div class="stat-bar-track"><div class="stat-bar-fill" style="width:${Math.round(((m.shotsHome ?? 0) / totalShots) * 100)}%"></div></div>
        </div>
        <div class="stat-row">
          <div class="stat-labels"><span>${m.cornersHome ?? 0}</span><span>${esc(tr('corners'))}</span><span>${m.cornersAway ?? 0}</span></div>
          <div class="stat-bar-track"><div class="stat-bar-fill" style="width:${Math.round(((m.cornersHome ?? 0) / totalCorners) * 100)}%"></div></div>
        </div>
      </div>

      <div class="card">
        <h3 style="margin:0 0 0.75rem;">${esc(tr('matchTimeline'))}</h3>
        ${state.activeMatchEvents.length ? state.activeMatchEvents.map((ev) => `
          <div style="display:flex;justify-content:space-between;align-items:center;padding:0.55rem 0;border-bottom:1px solid rgba(35,59,102,0.5);">
            <div>
              <strong style="font-family:var(--font-mono);color:var(--bright-blue);">${ev.minute}'</strong>
              <span style="margin-inline-start:0.5rem;font-weight:700;">${esc(ev.playerName)}</span>
              ${ev.detail ? `<span style="color:var(--text-soft);font-size:0.82rem;"> (${esc(ev.detail)})</span>` : ''}
            </div>
            <span class="badge ${ev.type === 'GOAL' ? 'badge-emerald' : ev.type === 'RED_CARD' ? 'badge-live' : 'badge-saffron'}">${esc(ev.type)}</span>
          </div>
        `).join('') : `<p style="color:var(--text-soft);margin:0;">${esc(tr('emptyListDefault'))}</p>`}
      </div>
    </div>
  `;
}

function renderKokowaView() {
  const kokowaBroadcasts = state.broadcasts.filter((b) => (b.sportCategory || '').toUpperCase() === 'KOKOWA');
  return `
    <div class="kokowa-banner">
      <span class="badge badge-saffron">SABRE NATIONAL • NIGER</span>
      <h2 style="font-family:var(--font-display);font-size:2rem;margin:0.5rem 0 0.25rem;">${esc(tr('kokowaTitle'))}</h2>
      <p style="margin:0;color:var(--text-soft);">${esc(tr('kokowaSubtitle'))}</p>
    </div>

    ${kokowaBroadcasts.length ? `
      <section class="section-block">
        <div class="section-header"><h2>${esc(tr('liveKokowaBroadcasts'))}</h2></div>
        <div class="cards-grid">${kokowaBroadcasts.map((b) => renderBroadcastCard(b, true)).join('')}</div>
      </section>
    ` : ''}

    <section class="section-block">
      <div class="section-header"><h2>${esc(tr('wrestlerProfiles'))}</h2></div>
      ${state.wrestlers.length ? `<div class="cards-grid">${state.wrestlers.map(renderWrestlerCard).join('')}</div>` : `<div class="card">${esc(tr('emptyListDefault'))}</div>`}
    </section>

    <section class="section-block">
      <div class="section-header"><h2>${esc(tr('kokowaResults'))}</h2></div>
      ${state.kokowaEvents.length ? `<div class="cards-grid">${state.kokowaEvents.map(renderKokowaEventCard).join('')}</div>` : `<div class="card">${esc(tr('emptyListDefault'))}</div>`}
    </section>
  `;
}

function renderWrestlerDetailView(wrestlerId) {
  const w = state.wrestlers.find((x) => x.id === wrestlerId);
  if (!w) {
    return `<div class="card"><p>${esc(tr('emptyListDefault'))}</p><button class="btn btn-primary btn-sm" data-nav="#kokowa">${esc(tr('back'))}</button></div>`;
  }
  const favs = state.userProfile?.favoriteWrestlerIds || [];
  const isFav = favs.includes(w.id);

  return `
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:1rem;">
      <button class="btn btn-outline btn-sm" data-nav="#kokowa">← ${esc(tr('back'))}</button>
      <button class="btn ${isFav ? 'btn-primary' : 'btn-outline'} btn-sm" id="fav-wrestler-btn" data-id="${esc(w.id)}">
        ${isFav ? `★ ${esc(tr('favorited'))}` : `☆ ${esc(tr('favoriteWrestler'))}`}
      </button>
    </div>
    <div class="card">
      <span class="badge badge-saffron">${esc(w.region)} • ${esc(w.village)}</span>
      <h2 style="font-family:var(--font-display);font-size:2.1rem;margin:0.25rem 0;">${esc(w.name)} “${esc(w.nickname)}”</h2>
      <p style="color:var(--text-soft);margin:0;">${esc(w.bio)}</p>
      <div class="metrics-grid" style="margin-top:1rem;">
        <div class="metric-tile"><div style="color:var(--text-soft);font-size:0.8rem;">${esc(tr('weightLabel'))}</div><div class="metric-val">${w.weightKg ?? 100} kg</div></div>
        <div class="metric-tile"><div style="color:var(--text-soft);font-size:0.8rem;">${esc(tr('recordLabel'))}</div><div class="metric-val">${w.wins ?? 0}W-${w.losses ?? 0}L</div></div>
        <div class="metric-tile"><div style="color:var(--text-soft);font-size:0.8rem;">${esc(tr('titlesLabel'))}</div><div class="metric-val">${w.titlesCount ?? 0}</div></div>
      </div>
    </div>
  `;
}

function renderTournamentsView() {
  return `
    <div class="section-header"><h2>${esc(tr('tournamentsTitle'))}</h2></div>
    ${state.tournaments.length
      ? `<div class="cards-grid">${state.tournaments.map(renderTournamentCard).join('')}</div>`
      : `<div class="card">${esc(tr('emptyListDefault'))}</div>`}
  `;
}

function renderTournamentDetailView(tournamentId) {
  const item = state.tournaments.find((x) => x.id === tournamentId);
  if (!item) {
    return `<div class="card"><p>${esc(tr('emptyListDefault'))}</p><button class="btn btn-primary btn-sm" data-nav="#tournaments">${esc(tr('back'))}</button></div>`;
  }
  const favs = state.userProfile?.favoriteTournamentIds || [];
  const isFav = favs.includes(item.id);
  const brackets = Array.isArray(item.knockoutBracketSummary) ? item.knockoutBracketSummary : [];
  const participants = Array.isArray(item.participantNames) ? item.participantNames : [];

  return `
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:1rem;">
      <button class="btn btn-outline btn-sm" data-nav="#tournaments">← ${esc(tr('back'))}</button>
      <button class="btn ${isFav ? 'btn-primary' : 'btn-outline'} btn-sm" id="fav-tournament-btn" data-id="${esc(item.id)}">
        ${isFav ? `★ ${esc(tr('favorited'))}` : `☆ ${esc(tr('favoriteWrestler'))}`}
      </button>
    </div>

    <div class="card" style="margin-bottom:1.25rem;">
      <div class="card-meta-row">
        <span class="badge badge-blue">${esc(item.sportCategory)} • ${esc(item.season)}</span>
        ${statusBadge(item.status)}
      </div>
      <h2 style="font-family:var(--font-display);font-size:2rem;margin:0.25rem 0;">${esc(item.name)}</h2>
      <div style="color:var(--text-soft);">📍 ${esc(item.location)}</div>
      ${item.announcement ? `<p style="margin:0.5rem 0 0;color:var(--text-white);">${esc(item.announcement)}</p>` : ''}
      ${item.championName ? `<div style="margin-top:0.5rem;"><span class="badge badge-saffron">🏆 ${esc(tr('championLabel'))}: ${esc(item.championName)}</span></div>` : ''}
    </div>

    <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:1.25rem;">
      <div class="card">
        <h3 style="margin:0;">${esc(tr('knockoutBracket'))}</h3>
        ${brackets.length ? brackets.map((row) => {
          const [stage, label] = String(row).split('|');
          return `
            <div style="padding:0.65rem;border-radius:10px;background:var(--bg-pitch-navy);border:1px solid var(--border-slate);display:flex;justify-content:space-between;gap:0.75rem;">
              <span class="badge badge-blue">${esc(stage || 'ROUND')}</span>
              <span style="font-weight:600;text-align:end;">${esc(label || row)}</span>
            </div>
          `;
        }).join('') : `<p style="color:var(--text-soft);">${esc(tr('emptyListDefault'))}</p>`}
      </div>

      <div class="card">
        <h3 style="margin:0;">${esc(tr('participantsLabel'))}</h3>
        <div class="chip-row" style="margin:0;">
          ${participants.map((p) => `<span class="badge badge-muted">${esc(p)}</span>`).join('')}
        </div>
      </div>
    </div>
  `;
}

function renderProfileView() {
  const user = state.user;
  const p = state.userProfile || {};
  const perms = rolePermissions(state.staffRole);

  return `
    <div class="section-header"><h2>${esc(tr('profileTitle'))}</h2></div>
    <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(310px,1fr));gap:1.25rem;">
      <div class="card">
        <div style="display:flex;align-items:center;gap:1rem;">
          ${user.photoURL ? `<img src="${esc(safeUrl(user.photoURL))}" alt="" style="width:60px;height:60px;border-radius:50%;border:2px solid var(--bright-blue);" />` : `<div class="wrestler-avatar">${esc((user.displayName || 'S')[0])}</div>`}
          <div>
            <h3 style="margin:0;">${esc(user.displayName || 'Sport Niger Viewer')}</h3>
            <div style="font-size:0.86rem;color:var(--text-soft);">${esc(user.email || '')}</div>
            <span class="badge badge-blue" style="margin-top:0.35rem;">${esc(perms.role)}</span>
          </div>
        </div>

        <div class="metrics-grid" style="margin-top:0.75rem;margin-bottom:0;">
          <div class="metric-tile"><div style="font-size:0.78rem;color:var(--text-soft);">${esc(tr('favoriteTeamsCount'))}</div><div class="metric-val">${(p.favoriteTeamIds || []).length}</div></div>
          <div class="metric-tile"><div style="font-size:0.78rem;color:var(--text-soft);">${esc(tr('favoriteWrestlersCount'))}</div><div class="metric-val">${(p.favoriteWrestlerIds || []).length}</div></div>
          <div class="metric-tile"><div style="font-size:0.78rem;color:var(--text-soft);">${esc(tr('favoriteTournamentsCount'))}</div><div class="metric-val">${(p.favoriteTournamentIds || []).length}</div></div>
        </div>

        <div style="display:flex;flex-wrap:wrap;gap:0.65rem;margin-top:0.5rem;">
          ${perms.isStaff ? `<button class="btn btn-primary" data-nav="#admin">${esc(tr('openAdminDashboard'))}</button>` : ''}
          <button class="btn btn-danger" id="profile-signout-btn">${esc(tr('signOut'))}</button>
        </div>
      </div>

      <div class="card">
        <h3 style="margin:0;">${esc(tr('languageSetting'))}</h3>
        <div class="chip-row" style="margin:0.25rem 0 0.75rem;">
          ${LANGUAGES.map((l) => `
            <button class="filter-chip ${state.lang === l.code ? 'active' : ''}" data-set-lang="${l.code}">${esc(l.nativeName)}</button>
          `).join('')}
        </div>

        <h3 style="margin:0.5rem 0 0;">${esc(tr('notificationPreferences'))}</h3>
        ${[
          ['notifyMatchStart', tr('notifyMatchStart'), p.notifyMatchStart !== false],
          ['notifyGoals', tr('notifyGoals'), p.notifyGoals !== false],
          ['notifyKokowa', tr('notifyKokowa'), p.notifyKokowa !== false],
          ['notifyTournaments', tr('notifyTournaments'), p.notifyTournaments !== false],
          ['notifyNews', tr('notifyNews'), !!p.notifyNews]
        ].map(([field, label, checked]) => `
          <label style="display:flex;align-items:center;justify-content:space-between;padding:0.45rem 0;border-bottom:1px solid rgba(35,59,102,0.45);cursor:pointer;">
            <span>${esc(label)}</span>
            <input type="checkbox" class="notif-toggle" data-field="${field}" ${checked ? 'checked' : ''} />
          </label>
        `).join('')}

        <div style="margin-top:0.75rem;padding:0.85rem;border-radius:10px;background:var(--bg-pitch-navy);border:1px solid var(--border-slate);">
          <strong style="color:var(--victory-emerald);">${esc(tr('freeModelNoteTitle'))}</strong>
          <p style="margin:0.35rem 0 0;font-size:0.84rem;color:var(--text-soft);">${esc(tr('freeModelNoteBody'))}</p>
        </div>
      </div>
    </div>
  `;
}

function renderAdminView() {
  const perms = rolePermissions(state.staffRole);
  if (!perms.isStaff) {
    return `<div class="card"><p>Staff authorization required.</p><button class="btn btn-primary btn-sm" data-nav="#home">${esc(tr('back'))}</button></div>`;
  }
  const tab = state.adminTab;
  let body = '';

  if (tab === 'overview') {
    const totalViewers = state.broadcasts.reduce((sum, b) => sum + (b.viewerCount || 0), 0);
    body = `
      <div class="metrics-grid">
        <div class="metric-tile"><div style="font-size:0.8rem;color:var(--text-soft);">Broadcasts</div><div class="metric-val">${state.broadcasts.length}</div></div>
        <div class="metric-tile"><div style="font-size:0.8rem;color:var(--text-soft);">Live Viewers</div><div class="metric-val">${totalViewers.toLocaleString()}</div></div>
        <div class="metric-tile"><div style="font-size:0.8rem;color:var(--text-soft);">Matches</div><div class="metric-val">${state.matches.length}</div></div>
        <div class="metric-tile"><div style="font-size:0.8rem;color:var(--text-soft);">Wrestlers</div><div class="metric-val">${state.wrestlers.length}</div></div>
        <div class="metric-tile"><div style="font-size:0.8rem;color:var(--text-soft);">Tournaments</div><div class="metric-val">${state.tournaments.length}</div></div>
        <div class="metric-tile"><div style="font-size:0.8rem;color:var(--text-soft);">News</div><div class="metric-val">${state.news.length}</div></div>
      </div>
      <div class="card">
        <p style="margin:0;color:var(--text-soft);">Populate or refresh the official Super Ligue Niger, Sabre National Kokowa, broadcasts, teams, and tournaments catalog in Cloud Firestore.</p>
        <div>
          <button class="btn btn-primary" id="admin-seed-btn">${esc(tr('seedCatalogButton'))}</button>
        </div>
      </div>
    `;
  } else if (tab === 'broadcasts') {
    body = `
      ${perms.canManageBroadcasts ? `
        <form class="card" id="admin-create-broadcast-form" style="margin-bottom:1.25rem;">
          <h3 style="margin:0;">${esc(tr('createBroadcast'))}</h3>
          <div class="form-grid">
            <div class="field-group"><label>Title</label><input class="input" name="title" required /></div>
            <div class="field-group"><label>Sport Category</label><input class="input" name="sportCategory" value="FOOTBALL" required /></div>
            <div class="field-group"><label>Stream Type</label>
              <select class="select" name="streamType"><option value="HLS">HLS (.m3u8)</option><option value="YOUTUBE_LIVE">YOUTUBE_LIVE</option><option value="RTMP_HLS">RTMP_HLS</option></select>
            </div>
            <div class="field-group"><label>Ingest Protocol</label>
              <select class="select" name="ingestProtocol"><option value="OBS_RTMP">OBS_RTMP</option><option value="RTMP">RTMP</option><option value="SRT">SRT</option><option value="YOUTUBE">YOUTUBE</option><option value="MOBILE_LIVE">MOBILE_LIVE</option></select>
            </div>
            <div class="field-group"><label>Playback URL</label><input class="input" name="playbackUrl" value="https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" required /></div>
            <div class="field-group"><label>YouTube Video ID (optional)</label><input class="input" name="youtubeVideoId" /></div>
            <div class="field-group"><label>Status</label>
              <select class="select" name="status"><option value="LIVE">LIVE</option><option value="UPCOMING">UPCOMING</option></select>
            </div>
            <div class="field-group"><label>OBS / Stream Key Note</label><input class="input" name="obsProfileNote" value="1080p60 H.264 CBR 4500kbps" /></div>
          </div>
          <div class="field-group"><label>Description</label><textarea class="textarea" name="description"></textarea></div>
          <div><button class="btn btn-primary" type="submit">${esc(tr('createBroadcast'))}</button></div>
        </form>
      ` : ''}
      <div class="cards-grid">
        ${state.broadcasts.map((b) => `
          <div class="card">
            <div class="card-meta-row">${statusBadge(b.status)}<span class="badge badge-blue">${esc(b.sportCategory)}</span></div>
            <strong>${esc(b.title)}</strong>
            ${perms.canManageBroadcasts ? `
              <div style="display:flex;gap:0.5rem;flex-wrap:wrap;">
                <button class="btn btn-primary btn-sm" data-bc-status="${esc(b.id)}" data-next="LIVE">${esc(tr('startBroadcast'))}</button>
                <button class="btn btn-danger btn-sm" data-bc-status="${esc(b.id)}" data-next="ENDED">${esc(tr('stopBroadcast'))}</button>
              </div>
            ` : ''}
          </div>
        `).join('')}
      </div>
    `;
  } else if (tab === 'scores') {
    body = `
      <div class="cards-grid">
        ${state.matches.map((m) => `
          <div class="card">
            <div class="card-meta-row"><span class="badge badge-blue">${esc(m.leagueName)}</span>${statusBadge(m.status)}</div>
            <strong>${esc(m.homeTeamName)} vs ${esc(m.awayTeamName)}</strong>
            ${perms.canManageScores ? `
              <form class="admin-update-score-form" data-match-id="${esc(m.id)}">
                <div class="form-grid" style="grid-template-columns:repeat(2,1fr);">
                  <div class="field-group"><label>Home</label><input class="input" type="number" min="0" max="99" name="homeScore" value="${m.homeScore ?? 0}" /></div>
                  <div class="field-group"><label>Away</label><input class="input" type="number" min="0" max="99" name="awayScore" value="${m.awayScore ?? 0}" /></div>
                  <div class="field-group"><label>Status</label>
                    <select class="select" name="status">
                      ${['LIVE', 'HALFTIME', 'UPCOMING', 'COMPLETED'].map((st) => `<option value="${st}" ${m.status === st ? 'selected' : ''}>${st}</option>`).join('')}
                    </select>
                  </div>
                  <div class="field-group"><label>Minute</label><input class="input" name="minute" value="${esc(m.matchMinute)}" /></div>
                </div>
                <button class="btn btn-primary btn-sm" style="margin-top:0.6rem;" type="submit">${esc(tr('updateScore'))}</button>
              </form>
              <form class="admin-add-event-form" data-match-id="${esc(m.id)}" style="margin-top:0.5rem;padding-top:0.65rem;border-top:1px solid var(--border-slate);">
                <div class="form-grid" style="grid-template-columns:repeat(2,1fr);">
                  <div class="field-group"><label>Event Type</label>
                    <select class="select" name="type"><option value="GOAL">GOAL</option><option value="YELLOW_CARD">YELLOW_CARD</option><option value="RED_CARD">RED_CARD</option><option value="SUBSTITUTION">SUBSTITUTION</option></select>
                  </div>
                  <div class="field-group"><label>Side</label>
                    <select class="select" name="teamSide"><option value="HOME">HOME</option><option value="AWAY">AWAY</option></select>
                  </div>
                  <div class="field-group"><label>Minute</label><input class="input" type="number" min="0" max="150" name="minute" value="70" /></div>
                  <div class="field-group"><label>Player Name</label><input class="input" name="playerName" required /></div>
                </div>
                <button class="btn btn-outline btn-sm" style="margin-top:0.6rem;" type="submit">${esc(tr('addMatchEvent'))}</button>
              </form>
            ` : ''}
          </div>
        `).join('')}
      </div>
    `;
  } else if (tab === 'kokowa') {
    body = perms.canManageSports ? `
      <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:1.25rem;">
        <form class="card" id="admin-add-wrestler-form">
          <h3 style="margin:0;">${esc(tr('addWrestler'))}</h3>
          <div class="field-group"><label>Full Name</label><input class="input" name="name" required /></div>
          <div class="field-group"><label>Nickname</label><input class="input" name="nickname" required /></div>
          <div class="field-group"><label>Village</label><input class="input" name="village" required /></div>
          <div class="field-group"><label>Region</label><input class="input" name="region" required /></div>
          <div class="field-group"><label>Weight (kg)</label><input class="input" type="number" min="50" max="220" name="weightKg" value="105" /></div>
          <div class="field-group"><label>Bio</label><textarea class="textarea" name="bio"></textarea></div>
          <button class="btn btn-saffron" type="submit">${esc(tr('addWrestler'))}</button>
        </form>

        <form class="card" id="admin-add-kokowa-event-form">
          <h3 style="margin:0;">${esc(tr('addKokowaEvent'))}</h3>
          <div class="field-group"><label>Title</label><input class="input" name="title" required /></div>
          <div class="field-group"><label>Arena / Location</label><input class="input" name="location" required /></div>
          <div class="field-group"><label>Region</label><input class="input" name="region" required /></div>
          <div class="field-group"><label>Wrestler A</label><input class="input" name="wrestlerAName" required /></div>
          <div class="field-group"><label>Wrestler B</label><input class="input" name="wrestlerBName" required /></div>
          <div class="field-group"><label>Round Label</label><input class="input" name="roundLabel" value="Quarter-Final" /></div>
          <div class="field-group"><label>Schedule</label><input class="input" name="scheduledLabel" value="Saturday 16:00 WAT" /></div>
          <div class="field-group"><label>Status</label>
            <select class="select" name="status"><option value="UPCOMING">UPCOMING</option><option value="LIVE">LIVE</option><option value="COMPLETED">COMPLETED</option></select>
          </div>
          <button class="btn btn-saffron" type="submit">${esc(tr('addKokowaEvent'))}</button>
        </form>

        <form class="card" id="admin-add-category-form">
          <h3 style="margin:0;">${esc(tr('addSportCategory'))}</h3>
          <div class="field-group"><label>Slug (e.g. BASKETBALL)</label><input class="input" name="slug" required /></div>
          <div class="field-group"><label>English Name</label><input class="input" name="nameEn" required /></div>
          <div class="field-group"><label>Hausa Name</label><input class="input" name="nameHa" /></div>
          <div class="field-group"><label>French Name</label><input class="input" name="nameFr" /></div>
          <div class="field-group"><label>Arabic Name</label><input class="input" name="nameAr" /></div>
          <label style="display:flex;align-items:center;gap:0.5rem;"><input type="checkbox" name="traditional" /> Traditional Nigerien Sport</label>
          <button class="btn btn-primary" type="submit">${esc(tr('addSportCategory'))}</button>
        </form>
      </div>
    ` : `<div class="card">Sports Manager or Admin role required.</div>`;
  } else if (tab === 'tournaments_news') {
    body = `
      <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:1.25rem;">
        ${perms.canManageSports ? `
          <form class="card" id="admin-add-tournament-form">
            <h3 style="margin:0;">${esc(tr('addTournament'))}</h3>
            <div class="field-group"><label>Tournament Name</label><input class="input" name="name" required /></div>
            <div class="field-group"><label>Sport Category</label><input class="input" name="sportCategory" value="FOOTBALL" required /></div>
            <div class="field-group"><label>Season</label><input class="input" name="season" value="2026" required /></div>
            <div class="field-group"><label>Location</label><input class="input" name="location" value="Niamey, Niger" required /></div>
            <div class="field-group"><label>Participants (comma-separated)</label><input class="input" name="participantsCsv" value="AS FAN, USGN, Sahel SC, ASN Nigelec" /></div>
            <div class="field-group"><label>Announcement</label><textarea class="textarea" name="announcement"></textarea></div>
            <button class="btn btn-primary" type="submit">${esc(tr('addTournament'))}</button>
          </form>
        ` : ''}

        ${perms.canManageNews ? `
          <form class="card" id="admin-publish-news-form">
            <h3 style="margin:0;">${esc(tr('publishNews'))}</h3>
            <div class="field-group"><label>Headline</label><input class="input" name="title" required /></div>
            <div class="field-group"><label>Sport Category</label><input class="input" name="sportCategory" value="FOOTBALL" required /></div>
            <div class="field-group"><label>Summary</label><textarea class="textarea" name="summary" required></textarea></div>
            <div class="field-group"><label>Full Article Body</label><textarea class="textarea" name="body"></textarea></div>
            <label style="display:flex;align-items:center;gap:0.5rem;"><input type="checkbox" name="breaking" /> ${esc(tr('breakingNews'))}</label>
            <button class="btn btn-primary" type="submit">${esc(tr('publishNews'))}</button>
          </form>
        ` : ''}
      </div>
    `;
  } else if (tab === 'staff_audit') {
    body = `
      ${perms.canManageStaff ? `
        <form class="card" id="admin-assign-staff-form" style="margin-bottom:1.25rem;">
          <h3 style="margin:0;">${esc(tr('assignStaffRole'))}</h3>
          <div class="form-grid">
            <div class="field-group"><label>Firebase User UID</label><input class="input" name="targetUid" required /></div>
            <div class="field-group"><label>Email</label><input class="input" type="email" name="email" required /></div>
            <div class="field-group"><label>Display Name</label><input class="input" name="displayName" required /></div>
            <div class="field-group"><label>Role</label>
              <select class="select" name="role">
                ${STAFF_ROLES.filter((r) => r !== 'VIEWER').map((r) => `<option value="${r}">${r}</option>`).join('')}
              </select>
            </div>
          </div>
          <div><button class="btn btn-primary" type="submit">${esc(tr('assignStaffRole'))}</button></div>
        </form>

        <div class="card" style="margin-bottom:1.25rem;">
          <h3 style="margin:0 0 0.5rem;">Staff Directory</h3>
          ${state.staffDirectory.length ? state.staffDirectory.map((s) => `
            <div style="display:flex;justify-content:space-between;align-items:center;padding:0.55rem 0;border-bottom:1px solid rgba(35,59,102,0.5);flex-wrap:wrap;gap:0.5rem;">
              <div>
                <strong>${esc(s.displayName)}</strong> (${esc(s.email)}) — <span class="badge badge-blue">${esc(s.role)}</span>
                <span class="badge ${s.status === 'ACTIVE' ? 'badge-emerald' : 'badge-live'}">${esc(s.status)}</span>
              </div>
              <button class="btn btn-outline btn-sm" data-staff-toggle="${esc(s.userId)}" data-next="${s.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'}">
                ${s.status === 'ACTIVE' ? esc(tr('suspendAction')) : esc(tr('activateAction'))}
              </button>
            </div>
          `).join('') : `<p style="color:var(--text-soft);margin:0;">${esc(tr('emptyListDefault'))}</p>`}
        </div>
      ` : ''}

      <div class="card">
        <h3 style="margin:0 0 0.5rem;">${esc(tr('auditLogTitle'))}</h3>
        ${state.auditLogs.length ? state.auditLogs.map((log) => `
          <div style="padding:0.55rem 0;border-bottom:1px solid rgba(35,59,102,0.45);font-size:0.86rem;">
            <span class="badge badge-blue">${esc(log.actorRole)}</span>
            <strong style="margin-inline-start:0.4rem;">${esc(log.action)}</strong> — ${esc(log.summary)}
            <div style="font-size:0.76rem;color:var(--text-muted);">${esc(log.actorEmail)}</div>
          </div>
        `).join('') : `<p style="color:var(--text-soft);margin:0;">${esc(tr('emptyListDefault'))}</p>`}
      </div>
    `;
  }

  return `
    <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:0.75rem;margin-bottom:1rem;">
      <div>
        <button class="btn btn-outline btn-sm" data-nav="#home">← ${esc(tr('back'))}</button>
        <h2 style="font-family:var(--font-display);font-size:1.85rem;margin:0.5rem 0 0;">${esc(tr('adminDashboardTitle'))}</h2>
        <div style="font-size:0.85rem;color:var(--bright-blue);">${esc(tr('roleLabel'))}: ${esc(perms.role)}</div>
      </div>
    </div>

    <div class="tab-bar">
      <button class="tab-btn ${tab === 'overview' ? 'active' : ''}" data-admin-tab="overview">${esc(tr('adminTabOverview'))}</button>
      <button class="tab-btn ${tab === 'broadcasts' ? 'active' : ''}" data-admin-tab="broadcasts">${esc(tr('adminTabBroadcasts'))}</button>
      <button class="tab-btn ${tab === 'scores' ? 'active' : ''}" data-admin-tab="scores">${esc(tr('adminTabScores'))}</button>
      <button class="tab-btn ${tab === 'kokowa' ? 'active' : ''}" data-admin-tab="kokowa">${esc(tr('adminTabKokowa'))}</button>
      <button class="tab-btn ${tab === 'tournaments_news' ? 'active' : ''}" data-admin-tab="tournaments_news">${esc(tr('adminTabTournamentsNews'))}</button>
      <button class="tab-btn ${tab === 'staff_audit' ? 'active' : ''}" data-admin-tab="staff_audit">${esc(tr('adminTabStaffAudit'))}</button>
    </div>
    ${body}
  `;
}

function renderModal() {
  if (!state.modal) return '';
  if (state.modal.type === 'news') {
    const n = state.modal.data;
    return `
      <div class="modal-backdrop" id="modal-backdrop">
        <div class="modal-card" role="dialog" aria-modal="true">
          <div class="card-meta-row" style="margin-bottom:0.75rem;">
            <span class="badge badge-blue">${esc(n.sportCategory)}</span>
            <button class="btn btn-outline btn-sm" id="modal-close-btn">${esc(tr('close'))}</button>
          </div>
          <h2 style="font-family:var(--font-display);font-size:1.85rem;margin:0 0 0.5rem;">${esc(n.title)}</h2>
          <div style="font-size:0.84rem;color:var(--text-soft);margin-bottom:1rem;">${esc(n.authorName)}</div>
          <p style="font-weight:600;color:var(--text-white);">${esc(n.summary)}</p>
          <p style="color:var(--text-soft);white-space:pre-line;">${esc(n.body)}</p>
        </div>
      </div>
    `;
  }
  if (state.modal.type === 'highlight') {
    const h = state.modal.data;
    return `
      <div class="modal-backdrop" id="modal-backdrop">
        <div class="modal-card" role="dialog" aria-modal="true">
          <div class="card-meta-row" style="margin-bottom:0.75rem;">
            <span class="badge badge-blue">${esc(h.sportCategory)} • ${esc(h.durationLabel)}</span>
            <button class="btn btn-outline btn-sm" id="modal-close-btn">${esc(tr('close'))}</button>
          </div>
          <h2 style="font-family:var(--font-display);font-size:1.6rem;margin:0 0 0.75rem;">${esc(h.title)}</h2>
          <div class="video-stage" style="border-radius:12px;overflow:hidden;">
            <video id="modal-hls-video" controls autoplay playsinline></video>
          </div>
        </div>
      </div>
    `;
  }
  return '';
}

function mountModalHighlight() {
  destroyModalHls();
  if (state.modal?.type !== 'highlight') return;
  const videoEl = document.getElementById('modal-hls-video');
  const url = safeUrl(state.modal.data.videoUrl);
  if (!videoEl || !url) return;
  if (window.Hls && window.Hls.isSupported()) {
    modalHlsInstance = new window.Hls();
    modalHlsInstance.loadSource(url);
    modalHlsInstance.attachMedia(videoEl);
    modalHlsInstance.on(window.Hls.Events.MANIFEST_PARSED, () => videoEl.play().catch(() => {}));
  } else {
    videoEl.src = url;
    videoEl.play().catch(() => {});
  }
}

function renderToast() {
  const existing = document.getElementById('toast-box');
  if (existing) existing.remove();
  if (!state.toast) return;
  const div = document.createElement('div');
  div.id = 'toast-box';
  div.className = 'toast';
  div.textContent = state.toast;
  document.body.appendChild(div);
}

function renderMainContent() {
  if (!state.authReady) {
    return `<div class="card" style="text-align:center;padding:3rem;">Loading SPORT NIGER...</div>`;
  }
  if (!state.user) {
    return renderLandingGateway();
  }
  const { name, param } = state.route;
  switch (name) {
    case 'live': return renderLiveHubView();
    case 'player': return renderPlayerView(param);
    case 'scores': return renderScoresView();
    case 'match': return renderMatchDetailView(param);
    case 'kokowa': return renderKokowaView();
    case 'wrestler': return renderWrestlerDetailView(param);
    case 'tournaments': return renderTournamentsView();
    case 'tournament': return renderTournamentDetailView(param);
    case 'profile': return renderProfileView();
    case 'admin': return renderAdminView();
    default: return renderHomeView();
  }
}

function renderFooter() {
  return `
    <footer class="site-footer">
      <div class="footer-inner">
        <div>
          <strong style="font-family:var(--font-display);font-size:1.2rem;letter-spacing:0.04em;">SPORT <span style="color:var(--bright-blue);">NIGER</span></strong>
          <div style="font-size:0.86rem;color:var(--text-soft);margin-top:0.25rem;">${esc(tr('footerTagline'))}</div>
          <div style="font-size:0.8rem;color:var(--text-muted);margin-top:0.2rem;">${esc(tr('footerSyncNote'))}</div>
        </div>
        <div style="font-size:0.84rem;color:var(--victory-emerald);font-weight:600;">
          ✓ ${esc(tr('freePublicAccessNote'))}
        </div>
      </div>
    </footer>
  `;
}

function render() {
  const root = document.getElementById('app');
  if (!root) return;

  // Preserve active search/chat input focus if re-rendering
  const activeId = document.activeElement?.id;
  const activeSelStart = document.activeElement?.selectionStart;

  root.innerHTML = `
    ${renderHeader()}
    <main class="main-container">
      ${renderMainContent()}
    </main>
    ${renderFooter()}
    ${renderMobileBottomNav()}
    ${renderModal()}
  `;

  bindEvents();
  mountVideoForRoute();
  mountModalHighlight();
  renderToast();

  if (activeId && ['home-search-input', 'chat-input'].includes(activeId)) {
    const el = document.getElementById(activeId);
    if (el) {
      el.focus();
      if (typeof activeSelStart === 'number') {
        try { el.setSelectionRange(activeSelStart, activeSelStart); } catch {}
      }
    }
  }
}

async function handleSignIn() {
  state.signingIn = true;
  state.authError = '';
  render();
  try {
    await signInWithGoogle();
  } catch (err) {
    if (err?.code === 'auth/unauthorized-domain') {
      state.authError = `Domain "${window.location.hostname}" is not yet in Firebase Console → Authentication → Settings → Authorized domains. Deploy via Firebase Hosting or add this domain.`;
    } else {
      state.authError = err?.message || 'Google Sign-In failed.';
    }
  } finally {
    state.signingIn = false;
    render();
  }
}

function bindEvents() {
  document.getElementById('lang-switcher')?.addEventListener('change', (e) => {
    setLanguage(e.target.value, true);
  });

  document.getElementById('header-signin-btn')?.addEventListener('click', handleSignIn);
  document.getElementById('landing-signin-btn')?.addEventListener('click', handleSignIn);

  document.querySelectorAll('[data-nav]').forEach((el) => {
    el.addEventListener('click', () => {
      navigate(el.getAttribute('data-nav'));
    });
  });

  document.querySelectorAll('[data-cat]').forEach((el) => {
    el.addEventListener('click', () => {
      state.selectedCategory = el.getAttribute('data-cat');
      render();
    });
  });

  document.getElementById('home-search-input')?.addEventListener('input', (e) => {
    state.searchQuery = e.target.value;
    render();
  });

  document.querySelectorAll('[data-scores-tab]').forEach((el) => {
    el.addEventListener('click', () => {
      state.scoresTab = el.getAttribute('data-scores-tab');
      render();
    });
  });

  document.querySelectorAll('[data-admin-tab]').forEach((el) => {
    el.addEventListener('click', () => {
      state.adminTab = el.getAttribute('data-admin-tab');
      render();
    });
  });

  document.querySelectorAll('[data-set-lang]').forEach((el) => {
    el.addEventListener('click', () => setLanguage(el.getAttribute('data-set-lang'), true));
  });

  document.getElementById('profile-signout-btn')?.addEventListener('click', async () => {
    await signOutUser();
    navigate('#home');
  });

  document.querySelectorAll('.notif-toggle').forEach((el) => {
    el.addEventListener('change', async () => {
      if (!state.user) return;
      const p = state.userProfile || {};
      const next = {
        notifyMatchStart: p.notifyMatchStart !== false,
        notifyGoals: p.notifyGoals !== false,
        notifyKokowa: p.notifyKokowa !== false,
        notifyTournaments: p.notifyTournaments !== false,
        notifyNews: !!p.notifyNews
      };
      next[el.getAttribute('data-field')] = el.checked;
      try {
        await updateNotificationPreferences(state.user.uid, next);
        showToast('Preferences saved.');
      } catch (err) {
        showToast(err.message);
      }
    });
  });

  document.getElementById('fav-wrestler-btn')?.addEventListener('click', async (e) => {
    if (!state.user) return;
    const id = e.currentTarget.getAttribute('data-id');
    try {
      await toggleFavoriteId(state.user.uid, 'favoriteWrestlerIds', id, state.userProfile?.favoriteWrestlerIds || []);
    } catch (err) { showToast(err.message); }
  });

  document.getElementById('fav-tournament-btn')?.addEventListener('click', async (e) => {
    if (!state.user) return;
    const id = e.currentTarget.getAttribute('data-id');
    try {
      await toggleFavoriteId(state.user.uid, 'favoriteTournamentIds', id, state.userProfile?.favoriteTournamentIds || []);
    } catch (err) { showToast(err.message); }
  });

  document.getElementById('player-fullscreen-btn')?.addEventListener('click', () => {
    const stage = document.getElementById('video-stage');
    if (stage?.requestFullscreen) stage.requestFullscreen().catch(() => {});
  });

  document.getElementById('chat-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const input = document.getElementById('chat-input');
    if (!input || !state.user || !activePlayerBroadcastId) return;
    const msg = input.value;
    input.value = '';
    try {
      await postChatComment(activePlayerBroadcastId, state.user, msg);
    } catch (err) {
      showToast(err.message);
    }
  });

  document.querySelectorAll('[data-report-comment]').forEach((el) => {
    el.addEventListener('click', async () => {
      const cid = el.getAttribute('data-report-comment');
      const comment = state.activeComments.find((c) => c.id === cid);
      if (!comment || !activePlayerBroadcastId) return;
      try {
        await reportChatComment(activePlayerBroadcastId, comment);
        showToast('Comment reported.');
      } catch (err) { showToast(err.message); }
    });
  });

  document.querySelectorAll('[data-delete-comment]').forEach((el) => {
    el.addEventListener('click', async () => {
      const cid = el.getAttribute('data-delete-comment');
      if (!activePlayerBroadcastId) return;
      try {
        await moderateDeleteComment(state.staffRole, activePlayerBroadcastId, cid);
        showToast('Comment deleted.');
      } catch (err) { showToast(err.message); }
    });
  });

  document.querySelectorAll('[data-block-user]').forEach((el) => {
    el.addEventListener('click', async () => {
      const uid = el.getAttribute('data-block-user');
      try {
        await blockUserFromChat(state.staffRole, uid);
        showToast('User blocked from chat.');
      } catch (err) { showToast(err.message); }
    });
  });

  document.getElementById('toggle-bc-chat-btn')?.addEventListener('click', async (e) => {
    const id = e.currentTarget.getAttribute('data-id');
    const currentlyEnabled = e.currentTarget.getAttribute('data-enabled') === 'true';
    try {
      await toggleBroadcastChat(state.staffRole, id, !currentlyEnabled);
      showToast(`Chat ${!currentlyEnabled ? 'enabled' : 'disabled'}.`);
    } catch (err) { showToast(err.message); }
  });

  document.querySelectorAll('[data-open-news]').forEach((el) => {
    el.addEventListener('click', () => {
      const id = el.getAttribute('data-open-news');
      const article = state.news.find((n) => n.id === id);
      if (article) {
        state.modal = { type: 'news', data: article };
        render();
      }
    });
  });

  document.querySelectorAll('[data-open-highlight]').forEach((el) => {
    el.addEventListener('click', () => {
      const id = el.getAttribute('data-open-highlight');
      const hl = state.highlights.find((h) => h.id === id);
      if (hl) {
        state.modal = { type: 'highlight', data: hl };
        render();
      }
    });
  });

  document.getElementById('modal-close-btn')?.addEventListener('click', () => {
    destroyModalHls();
    state.modal = null;
    render();
  });

  document.getElementById('modal-backdrop')?.addEventListener('click', (e) => {
    if (e.target.id === 'modal-backdrop') {
      destroyModalHls();
      state.modal = null;
      render();
    }
  });

  // Admin Console Handlers
  document.getElementById('admin-seed-btn')?.addEventListener('click', async () => {
    try {
      await seedOfficialCatalog(state.staffRole);
      showToast('Official SPORT NIGER catalog initialized.');
    } catch (err) { showToast(err.message); }
  });

  document.getElementById('admin-create-broadcast-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    try {
      await createBroadcast(state.staffRole, Object.fromEntries(fd.entries()));
      e.target.reset();
      showToast('Broadcast created.');
    } catch (err) { showToast(err.message); }
  });

  document.querySelectorAll('[data-bc-status]').forEach((el) => {
    el.addEventListener('click', async () => {
      try {
        await updateBroadcastStatus(state.staffRole, el.getAttribute('data-bc-status'), el.getAttribute('data-next'));
        showToast('Broadcast status updated.');
      } catch (err) { showToast(err.message); }
    });
  });

  document.querySelectorAll('.admin-update-score-form').forEach((form) => {
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const fd = new FormData(form);
      try {
        await updateMatchScore(
          state.staffRole,
          form.getAttribute('data-match-id'),
          fd.get('homeScore'),
          fd.get('awayScore'),
          fd.get('status'),
          fd.get('minute')
        );
        showToast('Match score updated.');
      } catch (err) { showToast(err.message); }
    });
  });

  document.querySelectorAll('.admin-add-event-form').forEach((form) => {
    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const fd = new FormData(form);
      try {
        await addMatchEvent(state.staffRole, form.getAttribute('data-match-id'), Object.fromEntries(fd.entries()));
        form.reset();
        showToast('Match event logged.');
      } catch (err) { showToast(err.message); }
    });
  });

  document.getElementById('admin-add-wrestler-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    try {
      await addWrestler(state.staffRole, Object.fromEntries(fd.entries()));
      e.target.reset();
      showToast('Wrestler added.');
    } catch (err) { showToast(err.message); }
  });

  document.getElementById('admin-add-kokowa-event-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    try {
      await createKokowaEvent(state.staffRole, Object.fromEntries(fd.entries()));
      e.target.reset();
      showToast('Kokowa bout scheduled.');
    } catch (err) { showToast(err.message); }
  });

  document.getElementById('admin-add-category-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    const data = Object.fromEntries(fd.entries());
    data.traditional = fd.has('traditional');
    try {
      await createSportCategory(state.staffRole, data);
      e.target.reset();
      showToast('Sport category added.');
    } catch (err) { showToast(err.message); }
  });

  document.getElementById('admin-add-tournament-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    try {
      await createTournament(state.staffRole, Object.fromEntries(fd.entries()));
      e.target.reset();
      showToast('Tournament created.');
    } catch (err) { showToast(err.message); }
  });

  document.getElementById('admin-publish-news-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    const data = Object.fromEntries(fd.entries());
    data.breaking = fd.has('breaking');
    try {
      await publishNews(state.staffRole, data);
      e.target.reset();
      showToast('News article published.');
    } catch (err) { showToast(err.message); }
  });

  document.getElementById('admin-assign-staff-form')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const fd = new FormData(e.target);
    try {
      await assignStaffMember(
        state.staffRole,
        fd.get('targetUid'),
        fd.get('email'),
        fd.get('displayName'),
        fd.get('role')
      );
      e.target.reset();
      showToast('Staff role assigned.');
    } catch (err) { showToast(err.message); }
  });

  document.querySelectorAll('[data-staff-toggle]').forEach((el) => {
    el.addEventListener('click', async () => {
      try {
        await setStaffStatus(state.staffRole, el.getAttribute('data-staff-toggle'), el.getAttribute('data-next'));
        showToast('Staff status updated.');
      } catch (err) { showToast(err.message); }
    });
  });
}

window.addEventListener('hashchange', () => {
  state.route = parseHash();
  syncRouteSubscriptions();
  render();
  window.scrollTo({ top: 0, behavior: 'smooth' });
});

applyDocumentLanguage();
render();

completeRedirectSignInIfAny().then(() => {
  listenAuth(async (user) => {
    state.authReady = true;
    state.user = user;
    if (user) {
      try {
        await ensureUserProfileExists(user, state.lang);
      } catch (err) {
        console.warn('Profile bootstrap:', err);
      }
      startAuthenticatedSubscriptions(user);
      syncRouteSubscriptions();
    } else {
      clearSubscriptions(dataUnsubs);
      clearSubscriptions(staffUnsubs);
      if (playerCommentsUnsub) { playerCommentsUnsub(); playerCommentsUnsub = null; }
      if (matchEventsUnsub) { matchEventsUnsub(); matchEventsUnsub = null; }
      state.userProfile = null;
      state.staffRole = 'VIEWER';
      state.broadcasts = [];
      state.matches = [];
      state.wrestlers = [];
      state.tournaments = [];
      state.news = [];
      destroyHls();
    }
    render();
  });
});
