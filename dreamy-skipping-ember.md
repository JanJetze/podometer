# Podometer - Requirements Document

## Context

There's a gap in the F-Droid ecosystem for a modern, well-designed step counter that also detects cycling. Existing options are either outdated, ugly, or depend on Google Play Services. Podometer will be a **privacy-first, fully FOSS Android app** that counts steps and detects cycling using only on-device sensors - no Google dependencies, no cloud, no tracking.

**Target distribution:** F-Droid (primary), GitHub Releases (APK)
**License:** GPLv3

---

## Tech Stack

| Layer | Choice | Rationale |
|-------|--------|-----------|
| Language | Kotlin | Google's preferred language, null safety, coroutines |
| UI | Jetpack Compose + Material Design 3 | Modern declarative UI, theming support |
| Build | Gradle (Kotlin DSL) | Standard Android tooling |
| Local DB | Room + Kotlin Flow | Compile-time verified queries, reactive data |
| Async | Kotlin Coroutines | Lightweight, structured concurrency |
| DI | Hilt | Standard Android DI, lifecycle-aware |
| Min SDK | 33 (Android 13) | Simplifies permissions model, fewer edge cases |
| Target SDK | 35 (Android 15) | Latest platform |

**Explicitly excluded:** Google Play Services, Firebase, any proprietary dependencies (F-Droid requirement).

---

## Core Features (MVP)

### 1. Step Counting

**How it works:**
- Use `TYPE_STEP_COUNTER` sensor as primary source (hardware-backed, battery-efficient, filters false positives)
- Fall back to `TYPE_STEP_DETECTOR` if step counter unavailable
- Last resort: accelerometer-based custom algorithm (peak detection on magnitude signal)
- Run in a **foreground service** (`foregroundServiceType="health"`) with persistent notification showing today's step count

**Data model:**
- Store hourly step aggregates (not raw events) to minimize storage
- Track: timestamp, step count delta, detected activity (walking/cycling/still)
- **Activity transitions table:** Record each transition event (walking→cycling, cycling→walking) with precise timestamp. This powers the transition log and timeline UI.
- Daily summary table for fast dashboard queries

**Key behaviors:**
- Auto-start on boot (with user opt-in)
- Survive app kill / battery optimization via foreground service
- Respect Doze mode by using sensor batching (`maxReportLatencyUs`)
- Show running total in notification, update every ~30 seconds

### 2. Cycling Detection

**Approach:** On-device sensor fusion (no Google Activity Recognition API)

**Algorithm:**
- Sample accelerometer at `SENSOR_DELAY_NORMAL` (~200ms)
- Compute features over sliding windows (10-15 seconds):
  - Acceleration variance (cycling has rhythmic, lower-amplitude patterns vs walking)
  - Step frequency (walking: 1.5-2.5 Hz, cycling: near-zero step events)
  - Dominant frequency via simple FFT or zero-crossing rate
- Classification logic:
  - **Cycling detected** when: step frequency drops to near-zero AND acceleration shows sustained rhythmic pattern consistent with pedaling
  - **Simpler heuristic (v1):** If device detects movement (accelerometer variance above threshold) but step counter reports zero/very few steps for >60 seconds, classify as "non-walking movement" (likely cycling)
- Confidence threshold to avoid false positives (require 2+ consecutive windows)

**Key behaviors:**
- When cycling detected: pause step counting, start logging cycling session
- When cycling ends: resume step counting, save cycling session (start time, end time, duration)
- Allow manual override (user can mark/unmark cycling sessions)

### 3. Dashboard UI

**Single-screen MVP layout:**
- **Today card:** Step count (large number), progress ring toward daily goal, distance estimate
- **Activity indicator:** Current activity state (walking / cycling / still)
- **Transition log:** Timestamped list of activity transitions throughout the day (e.g. "09:15 - Started cycling", "09:42 - Resumed walking", "10:30 - Started cycling", "10:55 - Resumed walking"). Shows the moments when the user switches between stepping and cycling, and back.
- **Today's timeline:** Horizontal bar showing activity segments throughout the day, with color-coded sections for walking (green) and cycling (blue) and transition markers at the boundaries
- **Week chart:** Simple bar chart showing daily steps for the past 7 days
- **Cycling log:** List of today's cycling sessions with duration

### 4. Settings

- Daily step goal (default: 10,000)
- Step length / stride calibration (for distance estimation)
- Auto-start on boot toggle
- Notification style (minimal / detailed)
- Data export (JSON)
- About / license info

---

## Permissions

| Permission | Why | When Requested |
|-----------|-----|----------------|
| `BODY_SENSORS` | Access step counter/detector sensors | On first launch |
| `ACTIVITY_RECOGNITION` | Activity detection (API 29+) | On first launch |
| `FOREGROUND_SERVICE` | Background step tracking | Manifest only (no runtime prompt) |
| `FOREGROUND_SERVICE_HEALTH` | Health-type foreground service (API 34+) | Manifest only |
| `RECEIVE_BOOT_COMPLETED` | Auto-start on boot | Manifest only |
| `POST_NOTIFICATIONS` | Show step count notification (API 33+) | On first launch |

**No location permission required** - cycling detection uses accelerometer only, no GPS.

---

## Architecture

```
app/
  src/main/java/com/podometer/
    data/
      db/              # Room database, entities, DAOs
      repository/      # Data repositories
      sensor/          # Sensor managers (step, accelerometer)
    domain/
      model/           # Domain models
      usecase/         # Business logic (step tracking, cycling detection)
    service/
      StepTrackingService.kt   # Foreground service
    ui/
      dashboard/       # Main dashboard screen
      settings/        # Settings screen
      theme/           # Material 3 theme
    di/                # Hilt modules
    PodometerApp.kt    # Application class
```

**Key architectural decisions:**
- **MVVM** with Compose: ViewModel holds UI state, Compose observes via `collectAsStateWithLifecycle`
- **Repository pattern:** Single source of truth for step/cycling data
- **Foreground service** owns sensor lifecycle, writes to Room DB
- **UI reads from Room** via Flow - reactive updates without polling

---

## F-Droid Compliance

- [ ] No Google Play Services dependencies
- [ ] No proprietary libraries
- [ ] No tracking / analytics
- [ ] No non-free network services
- [ ] GPLv3 license with SPDX headers
- [ ] Reproducible builds (Gradle lockfiles)
- [ ] Fastlane metadata structure for F-Droid listing
- [ ] No Firebase, Crashlytics, or ads SDKs

---

## Future Scope (Post-MVP)

These are explicitly **not** in v1 but worth designing for:
- Home screen widget (step count + progress ring)
- Weekly / monthly statistics views
- Achievements / streaks
- Wear OS companion
- CSV/GPX export
- Theming (dark/light/dynamic color)
- Accessibility improvements (TalkBack, large text)
- Localization (i18n)

---

## Implementation Plan

### Phase 1: Project Setup
- Initialize Android project (Kotlin, Compose, Gradle KTS)
- Configure Room database with entities and DAOs
- Set up Hilt dependency injection
- Create basic app scaffold with navigation

### Phase 2: Step Counting Core
- Implement foreground service with sensor registration
- Build step counter sensor integration (primary + fallbacks)
- Wire sensor data to Room database via repository
- Add boot receiver for auto-start

### Phase 3: Cycling Detection
- Implement accelerometer sampling in foreground service
- Build sliding window feature extraction
- Implement cycling classification heuristic
- Add cycling session logging to database

### Phase 4: Dashboard UI
- Build today card with step count and progress ring
- Add activity state indicator
- Build weekly bar chart
- Add cycling session list

### Phase 5: Settings & Polish
- Settings screen with preferences
- JSON data export
- Notification management
- Battery optimization handling

### Phase 6: F-Droid Release
- Add Fastlane metadata (screenshots, descriptions)
- GPLv3 license headers on all files
- Write F-Droid inclusion request
- Create GitHub releases workflow

---

## Verification

- **Step counting:** Walk with phone, verify count matches actual steps within ~5% margin
- **Cycling detection:** Ride a bike, verify activity switches to cycling and steps pause
- **Battery:** Run overnight, verify <5% battery drain from app
- **Persistence:** Kill app, verify service restarts and data is preserved
- **Boot:** Restart phone, verify tracking resumes automatically
- **Export:** Export data, verify JSON is valid and complete
