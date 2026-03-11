<p align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_launcher_foreground.png" width="120" alt="WatchWhatWhere Logo" />
</p>

<h1 align="center">WatchWhatWhere</h1>

<p align="center">
  <strong>Find what to watch — and exactly where to stream it.</strong>
</p>

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.watchwhatwhere.app">
    <img src="https://img.shields.io/badge/Google_Play-Download-green?style=for-the-badge&logo=google-play" alt="Get it on Google Play" />
  </a>
  <a href="https://watchwhatwhere.com">
    <img src="https://img.shields.io/badge/Website-watchwhatwhere.com-blue?style=for-the-badge&logo=google-chrome" alt="Visit Website" />
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-brightgreen?logo=android" alt="Android" />
  <img src="https://img.shields.io/badge/Kotlin-2.0-purple?logo=kotlin" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-latest-4285F4?logo=jetpack-compose" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/License-Apache_2.0-blue" alt="License" />
</p>

---

## 📖 What Is WatchWhatWhere?

**WatchWhatWhere** is a free entertainment discovery app that answers the two questions every viewer asks: *"What should I watch?"* and *"Where can I stream it?"*

Browse thousands of movies, TV shows, and mini-series — then instantly see which streaming services carry each title, organized by cost (free, subscription, rent, or buy). No more switching between apps to compare availability.

> **🎬 See it in action:**
> - **[Download on Google Play](https://play.google.com/store/apps/details?id=com.watchwhatwhere.app)** — Install the app and start discovering.
> - **[Visit watchwhatwhere.com](https://watchwhatwhere.com)** — Explore the full platform on the web.

---

## ✨ Features

### 🔍 Discover & Search
- **Curated Home Feed** — Browse trending and categorized content carousels updated regularly.
- **Global Search** — Find any movie, show, or mini-series instantly with results grouped by type.
- **Browsable Categories** — Filter by genre with infinite scroll through tens of thousands of titles.

### 📺 Streaming Availability
- **Where-to-Watch Links** — See every streaming provider carrying a title, complete with provider logos and direct links.
- **Smart Sorting** — Watch options are grouped and prioritized: **Free → Subscription → Rent → Buy**, so you always see the best deal first.

### 🎥 Rich Detail Pages
- **Full Metadata** — Ratings (visual 5-star display), runtime, genres, budget, revenue, release dates, and status.
- **Trailers** — Watch trailers in an integrated player without leaving the app.
- **Cast & Crew** — Browse artist profiles with bios, filmography, and cross-navigation.
- **TV Series** — Season selectors, episode lists with thumbnails, and per-episode details.
- **Recommendations** — Discover similar and recommended titles from every detail page.

### 👤 Account & Personalization
- **Multi-Provider Sign-In** — Log in with **Google**, **Microsoft**, **Facebook**, or **Discord**.
- **Custom Lists** — Create, rename, and manage personal watchlists to track what you want to see.
- **Provider Preferences** — Set your preferred streaming services to personalize your experience.

### 📡 Offline Ready
- **Smart Caching** — Previously viewed content is cached for up to 7 days. Browse the app even without an internet connection.
- **Image Caching** — Posters, backdrops, and thumbnails are cached locally (250 MB) for offline availability.
- **Graceful Error Handling** — Friendly error screens with one-tap retry when connectivity is restored.

---

## 🔒 Security & Privacy

WatchWhatWhere is designed with security and user privacy as a priority:

| Area | Details |
| --- | --- |
| **No Ads, No Trackers** | The app does not serve advertisements or embed third-party tracking SDKs. |
| **HTTPS Everywhere** | All API communication is encrypted over HTTPS/TLS. |
| **OAuth 2.0 Authentication** | Login flows use industry-standard OAuth 2.0 via official provider SDKs and PKCE-secured web flows — your password is never seen or stored by the app. |
| **No User Data Storage** | The app does not collect or store personal data on-device beyond your session. Cached content is purely entertainment metadata. |
| **Server-Side Token Exchange** | Auth codes are securely relayed to the backend for token exchange — raw tokens are never persisted on the client. |
| **ProGuard / R8 Obfuscation** | Release builds are minified and obfuscated to protect against reverse engineering. |
| **Minimal Permissions** | Only `INTERNET` and `ACCESS_NETWORK_STATE` are requested — no access to contacts, camera, microphone, location, or storage. |

---

## 🏗️ Tech Stack

| Layer | Technology |
| --- | --- |
| **Language** | Kotlin 2.0 |
| **UI Framework** | Jetpack Compose (Material 3) |
| **Networking** | Retrofit 2.9 + OkHttp 4.12 |
| **Dependency Injection** | Hilt 2.52 |
| **Image Loading** | Coil 2.5 (with disk + memory caching) |
| **Local Persistence** | Room + Custom ResponseCache |
| **Analytics** | Firebase Analytics |
| **Auth** | Google Play Services Auth, OAuth 2.0 WebView (Microsoft, Facebook, Discord) |
| **Build** | Gradle (Kotlin DSL), AGP 8.7, compileSdk 35 |

---

## 🏛️ Architecture

```
com.watchwhatwhere.app/
├── data/
│   ├── api/              # Retrofit API interfaces
│   ├── cache/            # Custom ResponseCache for offline support
│   ├── model/            # Kotlinx Serialization data models
│   └── repository/       # Network-first, cache-fallback repositories
├── di/                   # Hilt dependency injection modules
└── ui/
    ├── components/       # Reusable composables (TitleCard, ErrorScreen, etc.)
    ├── navigation/       # NavHost and route definitions
    ├── screens/          # Full-page screen composables
    └── theme/            # Material 3 theming (colors, typography)
```

The app follows a clean **MVVM** pattern with a network-first, cache-fallback data strategy. All API responses are cached locally for offline browsing, and ViewModels expose UI state as observable flows to the Compose layer.

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio** Hedgehog (2023.1) or later
- **JDK 17+**
- **Android SDK** with `compileSdk 35`

### Build & Run

```bash
# Clone the repository
git clone https://github.com/your-org/watchwhatwhere-android.git
cd watchwhatwhere-android

# Build the debug APK
./gradlew assembleDebug

# Install on a connected device
adb install app/build/outputs/apk/debug/app-debug.apk
```

> **Note:** You will need a valid `google-services.json` file in the `app/` directory for Firebase and Google Sign-In to function.

---

## 🔗 Links

| Resource | URL |
| --- | --- |
| **Google Play Store** | [Download WatchWhatWhere](https://play.google.com/store/apps/details?id=com.watchwhatwhere.app) |
| **Official Website** | [watchwhatwhere.com](https://watchwhatwhere.com) |

---

## 📄 License

```
Copyright 2026 IZONEWE LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

<p align="center">
  Made with ❤️ by <a href="https://izonewe.com">IZONEWE LLC</a>
</p>
