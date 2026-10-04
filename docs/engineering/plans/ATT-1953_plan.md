# Stage 3: Implementation Plan - ATT-1953: "Take Me Home" Return Navigation, Remaining Distance & ETA HUD

**Ticket**: [ATT-1953](https://rainerblind.atlassian.net/browse/ATT-1953)  
**Sub-task**: [ATT-2423](https://rainerblind.atlassian.net/browse/ATT-2423) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-MAP-029`  
**Test Mapping**: `TST-MAP-031`  
**Branch**: `feature/ATT-1953`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Description & Background

Endurance cyclists and runners often ride long distances and become fatigued or encounter deteriorating weather mid-workout. When exhausted or pressed for time, athletes need instantaneous, reliable answers to two vital questions: *"How much further to get home along my familiar route?"* and *"What is my estimated time of arrival (ETA)?"* Crucially, a flat distance-only calculation is wildly inaccurate in hilly terrain (e.g. climbing $300\,\text{m}$ over the final $5\,\text{km}$ takes more than twice as long as descending or traversing flat terrain at the same nominal effort). Athletes need an elevation-aware ETA that factors in both remaining distance and remaining climb.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MAP-029` (*"Take Me Home" Return Navigation, Remaining Distance & Elevation-Aware ETA HUD*)
* **Test Mapping**: `TST-MAP-031` (*Return Navigation & Elevation-Aware Dynamic ETA Verification*)
  * `TST-MAP-031.1`: Home base resolution tests (`HomeLocationResolverTest.kt`)
  * `TST-MAP-031.2`: Elevation-aware ETA calculation tests (`ElevationAwareEtaCalculatorTest.kt`)
  * `TST-MAP-031.3`: Return corridor snapping tests (`ReturnCorridorSnapperTest.kt`)
  * `TST-MAP-031.4`: Cockpit HUD banner contract tests (`ReturnNavigationHudContractTest.kt`)
  * `TST-MAP-031.5`: 9-language translation parity audit (`TranslationParityTest.kt`)
  * `TST-MAP-031.6`: Full clean-room regression suite (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Database Regressions**: SQLite schemas for `Routes.db` (v10) and `StartLocation2Altitude.db` (v5) must remain untouched.
2. **Offline Sovereignty**: Calculations must be 100% local without network requests.
3. **Thread Safety & Dispatcher Affinity**: Background location calculations and database reads must not block the main UI thread.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance (Rule 1)**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `HomeLocationResolver.kt`
* Path: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/HomeLocationResolver.kt`
* Data class: `HomeDestination(val id: Long, val name: String, val latLng: LatLng, val altitude: Double)`
* Evaluates locations in `KnownLocationsDatabaseManager.getAllLocations()`:
  - First checks case-insensitive name match (`"haus"`, `"home"`, `"zuhause"`).
  - Second checks location with `extremaType == ExtremaType.START` sorted by `hitCount DESC`.
  - Returns `HomeDestination?` or null if no known locations exist.

### Component 2: `ElevationAwareEtaCalculator.kt`
* Path: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/ElevationAwareEtaCalculator.kt`
* Pure, thread-safe calculation engine implementing:
  $$T_{\text{remaining}} = \frac{D_{\text{remaining}}}{V_{\text{flat}}} + \frac{H_{\text{climb}}}{\text{VAM}_{\text{climb}}}$$
* Computes positive vertical climbing ascent ($H_{\text{climb}}$) along remaining path segments.
* Formats clock arrival time (`HH:mm`) and remaining duration (`~X min` or `~X h Y min`).

### Component 3: `ReturnCorridorSnapper.kt`
* Path: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/ReturnCorridorSnapper.kt`
* Evaluates athlete's current location relative to:
  1. The active navigated route (forward to destination or reverse back to start).
  2. Candidate saved routes terminating or originating near Home ($\le 500\,\text{m}$).
  3. Direct geodesic vector to Home destination when unrouted.

### Component 4: `ReturnNavigationRepository.kt` & `ReturnNavigationState.kt`
* Path: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/ReturnNavigationRepository.kt`
* Coordinates `RoutesRepository`, `BANALServiceRepository`, and `KnownLocationsDatabaseManager`.
* Exposes reactive `val navigationState: StateFlow<ReturnNavigationState>`.
* Provides controls: `startTakeMeHome()`, `stopTakeMeHome()`, `setReverseReturn(enabled: Boolean)`.

### Component 5: Cockpit HUD Integration
* `ReturnNavigationHud.kt`: Glanceable HUD banner rendering destination, remaining km, remaining +m, and dynamic ETA.
* `SensorGridScreen.kt`: Renders `ReturnNavigationHud` below `TurnPromptBanner`; updates `RouteActionChipRow` with navigation metrics.
* `RouteSelectorSheet.kt`: Prominent "Take Me Home" ("Heimweg") card at the top.

### Component 6: 9-Language Localization
* Add strings for Take Me Home, remaining distance, climb, ETA, and Home destination across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Living String Resources (9 Locales)
* Files:
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/res/values-de/strings.xml`
  - `app/src/main/res/values-es/strings.xml`
  - `app/src/main/res/values-fr/strings.xml`
  - `app/src/main/res/values-it/strings.xml`
  - `app/src/main/res/values-ja/strings.xml`
  - `app/src/main/res/values-nl/strings.xml`
  - `app/src/main/res/values-pl/strings.xml`
  - `app/src/main/res/values-pt/strings.xml`
* Add keys: `take_me_home_title`, `take_me_home_desc`, `nav_remaining_dist`, `nav_remaining_climb`, `nav_eta_label`, `nav_destination_home`, `nav_stop_return`, `nav_route_reverse`.

### Step 2: Home Base Resolution (`HomeLocationResolver.kt`) & Tests
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/routes/HomeLocationResolver.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/routes/HomeLocationResolverTest.kt`
* Tests: Test explicit name matching, start location hit count fallback, empty handling.

### Step 3: Elevation-Aware ETA Engine (`ElevationAwareEtaCalculator.kt`) & Tests
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/routes/ElevationAwareEtaCalculator.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/routes/ElevationAwareEtaCalculatorTest.kt`
* Tests: Flat terrain test, climbing terrain test, descent clipping test, formatting test.

### Step 4: Return Corridor Snapper (`ReturnCorridorSnapper.kt`) & Tests
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/routes/ReturnCorridorSnapper.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/routes/ReturnCorridorSnapperTest.kt`
* Tests: Active route forward/reverse remaining calculations, saved route matching.

### Step 5: Return Navigation Repository (`ReturnNavigationRepository.kt`)
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/routes/ReturnNavigationRepository.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/routes/ReturnNavigationState.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java` (provide repository accessor or singleton)

### Step 6: Cockpit HUD Banner & Route Selector Integration
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/ReturnNavigationHud.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheet.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/ReturnNavigationHudContractTest.kt`

### Step 7: Verification Suite
* Execute targeted unit tests and localization parity test:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.routes.HomeLocationResolverTest" \
                              --tests "com.atrainingtracker.trainingtracker.routes.ElevationAwareEtaCalculatorTest" \
                              --tests "com.atrainingtracker.trainingtracker.routes.ReturnCorridorSnapperTest" \
                              --tests "com.atrainingtracker.trainingtracker.ui.routes.ReturnNavigationHudContractTest" \
                              --tests "com.atrainingtracker.trainingtracker.translations.TranslationParityTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests and clean-room full suite regression (`./gradlew testDebugUnitTest`) verifying 100% pass rate.
* **Rollback**: Branch `feature/ATT-1953` can be cleanly reverted or discarded without impacting `sprint/2026-40.16` or `develop`.
