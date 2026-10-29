# Solis — Smart Display for Android Tablets

An always-on DAKboard & Google Nest Hub–inspired Smart Display Android application built with Kotlin and Jetpack Compose (`minSdk = 31`).

## Key Features
1. **Non-Overlapping Split Bento Dashboard** (`SolisDashboardScreen.kt`):
   - **Left Column**:
     - **Top Row**: Live Clock & Date alongside a compact Weather card with current & multi-day forecast icons.
     - **Photo Frame**: Dedicated single-photo display frame directly below Time/Weather (no widget overlap, no collage), with 60-second auto-advance, left/right horizontal swipe navigation, Ken Burns transition, EXIF taken time & location (`City, State`), and auto-hiding controls.
   - **Right Column**:
     - **Top Half (44%)**: Scrollable 7-day **Upcoming Schedule** reading from Android's `CalendarContract.Instances` with instant `ContentObserver` updates and deep-linking into Google Calendar Weekly View.
     - **Bottom Half (56%)**: Scrollable **Important Gmail Brief** synced every 15 minutes via IMAP (`GmailImapClient.kt`), parsing daily email digests into concise action items, filtering out bank/credit card statements, and providing a full detail modal.
2. **Google Photos Favorites & Shared/Live Album Sync** (`SolisPhotoManager.kt`, `SharedAlbumPhotoRepository.kt`, `GooglePhotosAccountRepository.kt`):
   - Supports on-device Starred photos (`IS_FAVORITE = 1`), Google Photos Cloud Picker imports, and automatic background polling of Google Photos Shared / Live Albums with delta injection of newly added photos.
3. **Live Weather & Multi-Day Forecast** (`OpenMeteoWeatherRepository.kt`):
   - Current temperature, weather condition icons, high/low, and upcoming daily forecast via Open-Meteo API, plus deep-linking to the native tablet Weather app.
4. **Tablet Kiosk & Screen Saver Support**:
   - Immersive fullscreen `MainActivity`, OLED burn-in pixel-shift protection, automatic night dimming (11 PM – 6 AM), and `SolisDreamService` (Android Daydream/Screen Saver).

## Build & Install
```bash
./gradlew assembleDebug
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
```
