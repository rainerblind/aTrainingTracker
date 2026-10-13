# Stage 2: Requirement & Test Specification - ATT-3177

## 1. Overview & Test Objectives
* **Issue**: [ATT-3177](https://atrainingtracker.atlassian.net/browse/ATT-3177): `[Verbesserung] Configure standard tracking tabs with targeted navigation, segment, and climb defaults on initial install`
* **Requirement**: `REQ-UI-344`
* **Verification Specification**: `TST-UI-304`
* **Sprint**: `2026-41.9`
* **Primary Objective**: Verify that when `TrackingViewsDatabaseManager.TrackingViewsDbHelper.onCreate()` runs on a clean application install or database creation, the generated default tracking views for each `ActivityType` strictly conform to the canonical toggle matrix codified in `docs/design_guidelines.md` §5.9.

---

## 2. Traceability Matrix

| Requirement ID | Test Case ID | Test Class / Implementation | Method / Target |
| :--- | :--- | :--- | :--- |
| `REQ-UI-344.1` | `TST-UI-304.1` | `TrackingViewsDatabaseManagerTest.kt` | `testOnCreate_seedsCanonicalDefaultTabConfigurationMatrix` |
| `REQ-UI-344.2` | `TST-UI-304.2` | `TrackingViewsDatabaseManagerTest.kt` | `testAddDefaultTab_propagatesAllConfigurableToggles` |
| `REQ-UI-344.3` | `TST-UI-304.3` | `TrackingViewsDatabaseManagerTest.kt` | `testAddDefaultTab_insertsViewSize` (backward compatibility & row configuration) |
| `REQ-UI-344.4` | `TST-UI-304.4` | `./gradlew testDebugUnitTest` | Full clean-room regression suite |

---

## 3. Detailed Test Specification

### 3.1 Initial Database Creation Seeding Contract (`TST-UI-304.1`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManagerTest.kt`
* **Given**: A mock SQLite database passed to `TrackingViewsDbHelper.onCreate(mockDb)`.
* **When**: Default tabs are inserted into `LayoutViewsTable` (`VIEWS_TABLE`).
* **Then**:
  - Captured `ContentValues` for each `ActivityType` (e.g. `GENERIC_HR`, `RUN`, `BIKE`, `BIKE_POWER`) are validated against the canonical matrix:
    - **Standard Tab** (`layoutNr = 1`):
      - `SHOW_NAVIGATION_HINTS` = 1
      - `SHOW_LIVE_SEGMENTS` = 1
      - `SHOW_LIVE_CLIMBS` = 1
      - `SHOW_MAP` = 0
      - `SHOW_ELEVATION_PROFILE` = 0
      - `SHOW_LAP_BUTTON` = 0
    - **Map Tab** (`layoutNr = 2`):
      - `SHOW_NAVIGATION_HINTS` = 1
      - `SHOW_LIVE_SEGMENTS` = 0
      - `SHOW_LIVE_CLIMBS` = 0
      - `SHOW_MAP` = 1
      - `SHOW_ELEVATION_PROFILE` = 1
      - `SHOW_LAP_BUTTON` = 0
    - **Laps Tab** (`layoutNr = 3`):
      - `SHOW_NAVIGATION_HINTS` = 0
      - `SHOW_LIVE_SEGMENTS` = 0
      - `SHOW_LIVE_CLIMBS` = 0
      - `SHOW_MAP` = 0
      - `SHOW_ELEVATION_PROFILE` = 0
      - `SHOW_LAP_BUTTON` = 1

### 3.2 Explicit Parameter Passing Contract (`TST-UI-304.2`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/TrackingViewsDatabaseManagerTest.kt`
* **Given**: Direct invocation of `TrackingViewsDbHelper.addDefaultTab(...)`.
* **When**: Explicit booleans for `showLiveClimbs` and `showNavigationHints` are passed as `false` or `true`.
* **Then**:
  - `ContentValues` receives `SHOW_LIVE_CLIMBS` and `SHOW_NAVIGATION_HINTS` matching the boolean argument (0 for false, 1 for true), verifying elimination of hardcoded `1` values.

### 3.3 Localization & String Resource Audit
* **Audit Target**: Existing tab titles (`R.string.text_default`, `R.string.tab_map`, `R.string.tab_laps`).
* **Verification**: All 3 string resources exist and maintain 100% parity across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
* **Zero New Strings**: No new string resources are introduced by ATT-3177.

---

## 4. Acceptance Criteria & Given-When-Then Mapping

1. **Standard Tab Defaults**:
   - *Given* a fresh installation or database creation,
   - *When* default views are created,
   - *Then* the Standard tab has Navigation Hints, Live Segments, and Live Climbs enabled, with Map, Elevation Profile, and Lap Button disabled.
2. **Map Tab Defaults**:
   - *Given* a fresh installation or database creation,
   - *When* default views are created,
   - *Then* the Map tab has Navigation Hints, Map Canvas, and Elevation Profile enabled, with Live Segments, Live Climbs, and Lap Button disabled.
3. **Laps Tab Defaults**:
   - *Given* a fresh installation or database creation,
   - *When* default views are created,
   - *Then* the Laps tab has the Lap Button enabled, with Navigation Hints, Live Segments, Live Climbs, Map Canvas, and Elevation Profile disabled.

---

## 5. Clean-Room Regression Strategy
* Targeted unit test: `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.database.TrackingViewsDatabaseManagerTest`.
* Clean-room full suite regression: `./gradlew testDebugUnitTest`.
* Verification mandate: 100% test pass rate with zero regressions.
