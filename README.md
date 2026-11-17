# SPORT NIGER — Android Sports Streaming & Scores Platform

SPORT NIGER is a native **Android (Kotlin + Jetpack Compose + Material 3)** sports streaming, live scores, and traditional Nigerian wrestling (**Kokowa**) application built with a dark navy, broadcast blue, and crisp white television aesthetic.

---

## Key Features Implemented

### 1. Four-Language Support & Full Arabic RTL
- Supported languages: **English** (default), **Hausa**, **French**, and **Arabic** (complete RTL layout direction via `LocalLayoutDirection` + bundled `NotoSansArabic` font).
- First-launch language selection onboarding screen (`LanguageOnboardingScreen`) persisted with **Jetpack DataStore**.
- Instant runtime language switching from the Sign-In screen or **Profile → Language Settings** without restarting or reinstalling.

### 2. Navigation & Adaptive UI
- Bottom `NavigationBar` on phones (`< 600dp`) and side `NavigationRail` on tablets/foldables (`>= 600dp`):
  1. **Home** — Featured live broadcasts, live/upcoming/completed matches, Kokowa spotlight & wrestlers, popular tournaments, news/announcements, highlights, search, dynamic sport category filters, and sponsor banner slot.
  2. **Live** — Live channels & upcoming streams hub + full player view.
  3. **Scores** — Live scores, Fixtures, Results, Standings, Match Detail (possession/shots/corners stats & Goal/Card/Sub timeline), and external licensed Football API feed.
  4. **Tournaments** — Football & Kokowa competitions, knockout bracket rounds, participants, champions, and announcements.
  5. **Profile** — Favorites (teams, wrestlers, tournaments), notification preferences, language selector, sign-out, and Staff/Admin entry.
- Dedicated **Kokowa (Traditional Wrestling)** hub accessible from Home & category filters.

### 3. Live Video Streaming & Moderated Chat
- **Media3 ExoPlayer** HLS playback (`HlsVideoPlayer.kt`) with fullscreen mode, adaptive track quality selection (`Auto`, `1080p`, `720p`, `480p`, `360p`), loading/error/retry states, and live viewer count display.
- **YouTube Live** external launch support and **OBS Studio / RTMP / Mobile Live** stream metadata.
- **Zero-Exposure Stream Secrets**: Private RTMP stream keys and ingest credentials live exclusively in `/broadcast_secrets/{broadcastId}` (readable only by `SUPER_ADMIN`, `ADMIN`, and `STREAMING_MANAGER`), never exposed in public `/broadcasts` documents.
- **Live Chat & Moderation**: Real-time broadcast comments with user reporting, moderator soft-deletion, chat enable/disable toggle per broadcast, and user chat blocking.

### 4. Football Scores (Manual + Real External API)
- Authorized staff (`SCORE_MANAGER`, `SPORTS_MANAGER`, `ADMIN`, `SUPER_ADMIN`) can update match scores, status, minute, and timeline events directly in Cloud Firestore.
- Optional automatic live feed via **Retrofit + Moshi** (`https://api.football-data.org/v4/matches`) using `BuildConfig.FOOTBALL_DATA_API_KEY`.
- **No Fake Live Scores**: If `FOOTBALL_DATA_API_KEY` is unconfigured or the external API is unreachable, the app explicitly displays an honest unavailable/unconfigured status.

### 5. Google Sign-In, RBAC & Audit Logs
- Authentication uses **Google Sign-In via Jetpack Credential Manager** (`GetSignInWithGoogleOption` + silent auto sign-in via `GetGoogleIdOption`).
- Zero-trust Cloud Firestore security rules (`firestore.rules`) backed by `/staff/{uid}` role assignments and immutable `/audit_logs`.
- Bootstrapped **Super Admin**: `muneerzaki83@gmail.com` (verified email).
- Supported Staff Roles: `SUPER_ADMIN`, `ADMIN`, `SPORTS_MANAGER`, `SCORE_MANAGER`, `STREAMING_MANAGER`, `NEWS_EDITOR`, `MODERATOR`.
- First-time Super Admin convenience: In **Admin Console**, the Super Admin can trigger **Initialize Official Catalog** (`seedOfficialCatalog`) once to populate initial leagues, teams, wrestlers, matches, tournaments, and categories cleanly in Firestore.

---

## Configuration & Setup

### 1. Optional Licensed Football API Key
To enable the automatic Football Scores API feed (`api.football-data.org`):
1. Obtain an API token from `football-data.org`.
2. In AI Studio, open the **Secrets panel** and add `FOOTBALL_DATA_API_KEY` with your token (referenced in `.env.example` and injected via the Secrets Gradle Plugin into `BuildConfig.FOOTBALL_DATA_API_KEY`).
   *(Do not commit real API keys to source files or `local.properties`.)*

### 2. Firebase Configuration
- `app/google-services.json` and `app/src/main/res/values/firebase_applet_config.xml` are provisioned for Firebase project `gen-lang-client-0977858921` and named Firestore database `ai-studio-android-sportnig-5079ffaa-a494-462f-8296-8ef4d909901e`.
- Ensure **Google Sign-In** is enabled in Firebase Authentication in the Firebase Console if testing on a physical device outside AI Studio.

### 3. OBS Studio / RTMP Broadcast Workflow
- Configure your RTMP ingest media server (e.g., Mux, Cloudflare Stream, Livepeer, AWS IVS, or Nginx-RTMP) to output an authorized public **HLS `.m3u8`** playback URL.
- In **Admin Console → Streams**, create a broadcast with:
  - Public **Playback URL** (`.m3u8` or YouTube Live link)
  - Masked key display & private ingest credentials saved safely in `/broadcast_secrets`.

---

## Running Tests & Building APK

### Local Firestore Security Rules Tests
```bash
firebase emulators:start --only firestore
FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
```

### Android Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```

### Compile & Build Debug APK
```bash
gradle :app:compileDebugKotlin
gradle :app:assembleDebug
```
The generated APK will be output to `app/build/outputs/apk/debug/app-debug.apk`.

---

## Platform Scope Notes
- **Implemented & Verified in This Project**: Complete native **Android** application (Kotlin, Jetpack Compose, Material 3, Media3 ExoPlayer, DataStore, Credential Manager Google Sign-In, Retrofit), Cloud **Firestore** schema & security rules (`firestore.rules`, tested via local Firestore Emulator), and in-app Admin/Staff console.
- **Outside This Android Workspace**: iOS targets, standalone web dashboards, server-side Cloud Functions for automated FCM push triggers, and Firebase Storage bucket uploads are not provisioned inside this Android-only build container and can be added separately on the Firebase project if needed.
