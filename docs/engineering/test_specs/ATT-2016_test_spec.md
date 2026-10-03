# Stage 2: Requirement & Test Specification - ATT-2016: Persistent Floating Scrubbing Telemetry Badge Pinned Above Scrollable Graphs in MapDetailLayout

**Ticket**: [ATT-2016](https://rainerblind.atlassian.net/browse/ATT-2016)  
**Sub-task**: [ATT-2093](https://rainerblind.atlassian.net/browse/ATT-2093) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.12`  
**Requirement Mapping**: `REQ-UI-241` (*Aftermath/Scrubbing: Persistent Floating Scrubbing Telemetry Badge Pinned Above Scrollable Graphs in MapDetailLayout*)  
**Test Spec ID**: `TST-UI-200`  
**Branch**: `feature/ATT-2016`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (REQ-UI-241)

### 1.1 Problem Statement & Rationale
In Aftermath detailed inspection screens ([TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt), [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt)), multiple synchronized telemetry graphs (Elevation Profile, Speed/Pace, Heart Rate, Cycling Power) are stacked vertically inside a scrollable column below the map. 

Previously, the multi-metric readout badge (`ScrubbingTelemetryBadge`) was embedded within `ElevationProfile.kt`. When scrolling down to inspect or scrub the Speed/Pace, Heart Rate, or Power graphs, `ElevationProfile` scrolled out of the viewport, taking the telemetry badge with it. Athletes scrubbing the lower graphs could not view numeric telemetry values, timestamps, and zone labels. Furthermore, if Elevation Profile was hidden via settings (`showElevationProfile = false`), the badge was completely absent during scrubbing.

Lifting `ScrubbingTelemetryBadge` to `MapDetailLayout.kt` as a persistent floating overlay pinned at the top of the lower viewport guarantees that instantaneous telemetry remains visible and readable during all scrubbing interactions, independent of scroll position or elevation profile visibility.

### 1.2 Functional & Architectural Requirements
The system SHALL hoist `ScrubbingTelemetryBadge` from within `ElevationProfile.kt` into `MapDetailLayout.kt`, ensuring that instantaneous telemetry readouts remain pinned, elevated, and fully visible at the top of the lower graph viewport during active scrubbing:

1. **Persistent Container Viewport Overlay (`MapDetailLayout.kt`)**:
   * In `MapDetailLayout.kt`, the lower content section (both in the resizable split pane `showMap && hasScrollableContent` and in full-column view `!showMap && hasScrollableContent`) SHALL be wrapped in a viewport `Box` positioned directly below `GlobalTelemetryZoomToolbar` / `SplitPaneDivider`.
   * The scrollable `lowerColumn` (`Modifier.fillMaxSize().verticalScroll(...)`) SHALL be contained within this `Box`.
   * An overlay `ScrubbingTelemetryBadge` SHALL be anchored at `Alignment.TopCenter` with `padding(top = 4.dp)` within this `Box`.
   * The overlay badge SHALL render IF AND ONLY IF `showZoomControls == true`, `selectedDistance != null`, and `activeScrubPoint != null`.
   * When `selectedDistance == null`, the badge SHALL be dismissed cleanly without layout jitter.

2. **Universal Cross-Graph Scrubbing Ingestion**:
   * Touching, scrubbing, or holding on any visible graph (`ElevationProfile`, Speed/Pace `TelemetryMetricGraph`, Heart Rate `TelemetryMetricGraph`, or Cycling Power `TelemetryMetricGraph`) SHALL update `selectedDistance`.
   * `MapDetailLayout` SHALL resolve the active `PathPoint` (`activeScrubPoint`) and interpolated altitude (`activeScrubAltitude`) and dispatch them to `ScrubbingTelemetryBadge`.
   * The pinned badge SHALL display instantaneous Distance, Time, Altitude, Grade/Slope, Heart Rate (with active zone), Power (with active zone), and Speed/Pace.

3. **Elevation-Independent & Trackless Support**:
   * When `showElevationProfile == false` (e.g. toggled off via `WorkoutDetailPreferences`), scrubbing across the remaining telemetry graphs (Speed, HR, Power) SHALL still display the pinned `ScrubbingTelemetryBadge`.
   * For trackless workouts (`(path.lastOrNull()?.distance ?: 0.0) == 0.0`), `selectedDistance` represents elapsed seconds; the badge SHALL format elapsed time on the primary tracking row and display HR/Power telemetry along the time axis.

4. **ElevationProfile Decoupling & Backward Compatibility (`ElevationProfile.kt`)**:
   * `ElevationProfile` SHALL accept `showScrubbingBadge: Boolean = true` across both composable overloads.
   * In `MapDetailLayout.kt`, `ElevationProfile` SHALL be supplied with `showScrubbingBadge = false` to eliminate redundant duplicate badges when `ElevationProfile` is at the top of the scroll container.
   * Standalone invocations of `ElevationProfile` (where `showScrubbingBadge == true`) SHALL retain their internal badge rendering, preserving 100% backward compatibility and test invariants.

5. **Layering & Touch Transparency**:
   * The pinned `ScrubbingTelemetryBadge` overlay SHALL NOT consume or intercept horizontal drag gestures meant for graph scrubbing or vertical scroll gestures meant for `lowerColumn`.

6. **Preservation of Core Invariants**:
   * Directional gesture disambiguation (`REQ-UI-226`), single-finger vertical scroll freedom, synchronized cross-domain scrubbing (`REQ-UI-233`), and 9-language localization parity MUST NOT be broken.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Persistent Badge on Scroll)**:
  * *Given* an athlete viewing post-workout graphs in `MapDetailLayout`,
  * *When* the athlete scrolls the graph container so `ElevationProfile` is scrolled off-screen,
  * *Then* the scrubbing telemetry badge SHALL remain anchored and visible at the top of the graph viewport whenever scrubbing is active (`selectedDistance != null`).
* **AC-2 (Universal Telemetry Scrubbing)**:
  * *Given* the athlete touches or scrubs the Speed/Pace, Heart Rate, or Power graph,
  * *When* dragging the scrub pointer horizontally,
  * *Then* the pinned telemetry badge SHALL update in real time with instantaneous Distance, Time, Altitude, Slope, and metric values.
* **AC-3 (Non-Occluding Layering & Touch Pass-Through)**:
  * *Given* the pinned badge overlay,
  * *When* dragging or scrubbing over the chart area,
  * *Then* the badge SHALL NOT occlude the map divider or global zoom controls and SHALL NOT swallow chart drag or vertical scroll events.
* **AC-4 (Elevation-Independent Scrubbing)**:
  * *Given* a workout where `showElevationProfile == false`,
  * *When* scrubbing across any active telemetry graph (Speed/Pace, Heart Rate, Power),
  * *Then* the pinned telemetry badge SHALL render and display telemetry data.
* **AC-5 (Dismissal Behavior)**:
  * *Given* the athlete releases their finger from the graph,
  * *When* `selectedDistance` becomes null,
  * *Then* the pinned badge SHALL be dismissed cleanly without layout jitter.

### 1.4 System Invariants
* Single-finger vertical swipes ($|\Delta y| > |\Delta x|$) must pass through unconsumed to `lowerColumn` per `REQ-UI-226`.
* Plot area padding parity (`start = 50.dp, end = 25.dp`) must remain strictly preserved.
* 9-language localization parity across all supported application locales.
* Zero SQLite schema mutations.
* Parent ticket Human Decision Gate remains strictly guarded.

---

## 2. Test Specification (TST-UI-200)

### Test Case 1: Structural & Parameter Contract Tests (`TST-UI-200.1`)
* **Scope**: Contract & Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutScrubbingBadgeContractTest.kt`
* **Preconditions**: Project source tree is checked out on branch `feature/ATT-2016`.
* **Action**:
  1. Inspect `ElevationProfile.kt` source code to verify both overloads declare parameter `showScrubbingBadge: Boolean = true`.
  2. Inspect `ElevationProfile.kt` source code to verify `ScrubbingTelemetryBadge` invocation is gated by `showScrubbingBadge`.
  3. Inspect `MapDetailLayout.kt` source code to verify it passes `showScrubbingBadge = false` to `ElevationProfile`.
  4. Inspect `MapDetailLayout.kt` source code to verify it hosts `ScrubbingTelemetryBadge` inside the lower viewport Box at `Alignment.TopCenter`.
  5. Inspect `MapDetailLayout.kt` source code to verify `activeScrubPoint` and `activeScrubAltitude` are hoisted with trackless time-domain fallback.
* **Expected Result**: All assertions pass 100%.

### Test Case 2: Existing Layout & Invariant Tests (`TST-UI-200.2`)
* **Scope**: Regression Unit Tests
* **Target Files**:
  * `ElevationProfileLayoutTest.kt`
  * `TelemetryMetricGraphZoneTest.kt`
  * `MapDetailLayoutTest.kt`
* **Action**: Run targeted test suite via Gradle.
* **Expected Result**: 100% tests pass. `ElevationProfileLayoutTest.kt` assertions for `showZoomControls == false` and top padding 44.dp / 16.dp remain 100% green.

### Test Case 3: 9-Language Localization Audit (`TST-UI-200.3`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Action**: Verify all graph headings, metric labels, and zoom controls exist with 100% parity across all 9 locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
* **Expected Result**: 100% parity, zero missing entries.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-200.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite (1,374+ tests passing).

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-200.1` | Contract / Unit | `MapDetailLayoutScrubbingBadgeContractTest` | `REQ-UI-241` | Specified |
| `TST-UI-200.2` | Regression / Unit | `ElevationProfileLayoutTest`, `MapDetailLayoutTest` | `REQ-UI-241`, `REQ-UI-225`, `REQ-UI-226` | Specified |
| `TST-UI-200.3` | Localization | `TranslationParityTest` | `REQ-UI-241`, `REQ-UI-106` | Specified |
| `TST-UI-200.4` | Full Suite Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-014` | Specified |
