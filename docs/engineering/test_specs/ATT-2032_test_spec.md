# Stage 2: Requirement & Test Specification - ATT-2032: Unify X-Axis Domain & Time-vs-Distance Calculation Across Telemetry Components

**Ticket**: [ATT-2032](https://rainerblind.atlassian.net/browse/ATT-2032)  
**Sub-task**: [ATT-2243](https://rainerblind.atlassian.net/browse/ATT-2243) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-40.14`  
**Requirement Mapping**: `REQ-UI-261` (*Canonical Horizontal Domain Evaluation & Total Span Engine (`ProfileDomainMath`)*)  
**Test Spec ID**: `TST-UI-220`  
**Branch**: `feature/ATT-2032`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Requirement Specification (REQ-UI-261)

### 1.1 Problem Statement & Rationale
Evaluating whether an Aftermath chart composable renders along the Temporal Domain (Elapsed Time in seconds) versus Spatial Domain (Distance in meters), as well as calculating the maximum horizontal span (`totalSpan`), has historically been duplicated across `ElevationProfile.kt`, `TelemetryMetricGraph.kt`, and `MapDetailLayout.kt`.

Because each file implemented slightly different boolean checks, several edge cases produced visual defects:
1. `TelemetryMetricGraph.kt` checked `xAxisDomain == ProfileXAxisDomain.TIME || isTrackless` without validating whether timestamps actually exist. If an athlete selects Time domain on a GPS track recorded without timestamps (`timeSec == 0`), `TelemetryMetricGraph` remained in Time domain, resulting in collapsed zero-span rendering and invalid `0:00` ticks. In contrast, `ElevationProfile.kt` fell back to Distance domain, causing stacked charts to render on mismatched X-axes.
2. In `MapDetailLayout.kt`, `totalSpan` used for the global zoom toolbar and nested scroll container did not dynamically switch between `elevationTotalSpan` and `telemetryTotalSpan` when the elevation profile was disabled, leading to viewport mismatch on trackless workouts.
3. Linear $O(N)$ searches (`minByOrNull`) remained in `TelemetryMetricGraph.kt` for cursor distance resolution, rather than utilizing the optimized binary search engine (`TelemetryMetricUtils.findNearestPoint`).

`REQ-UI-261` establishes `ProfileDomainMath` as the canonical single source of truth for horizontal domain evaluation and horizontal span calculation across all telemetry visualizers.

### 1.2 Functional & Architectural Requirements
The system SHALL unify horizontal domain resolution (Distance vs. Time) and horizontal span calculation across all Aftermath telemetry visual analytics components via a single mathematical utility `ProfileDomainMath` (ATT-2032):
1. **Canonical Domain Evaluation Engine (`ProfileDomainMath.kt`)**:
   - `isTracklessWorkout(points: List<PathPoint>?): Boolean` and `isTracklessWorkout(totalDistance: Double, totalTimeSec: Long): Boolean`:
     SHALL return `true` if and only if `totalDistance <= 0.0 && totalTimeSec > 0L`.
   - `isEffectiveTimeDomain(domain: ProfileXAxisDomain, points: List<PathPoint>?): Boolean` and `isEffectiveTimeDomain(domain: ProfileXAxisDomain, totalDistance: Double, totalTimeSec: Long): Boolean`:
     SHALL return `true` if the workout is trackless, or if `domain == ProfileXAxisDomain.TIME` and `totalTimeSec > 0L`. If `totalTimeSec <= 0L`, it SHALL safely return `false` (falling back to distance domain) to prevent division-by-zero or collapsed zero-span rendering.
   - `calculateTotalSpan(domain: ProfileXAxisDomain, points: List<PathPoint>?): Double` and `calculateTotalSpan(domain: ProfileXAxisDomain, totalDistance: Double, totalTimeSec: Long): Double`:
     SHALL return `totalTimeSec.toDouble().coerceAtLeast(0.0)` if `isEffectiveTimeDomain(...)` is true, otherwise `totalDistance.coerceAtLeast(0.0)`.
2. **Unified Component Consumption**:
   - Ad-hoc domain checks and diverging total span calculations in `ElevationProfile.kt`, `TelemetryMetricGraph.kt`, and `MapDetailLayout.kt` SHALL be eliminated and replaced with unified calls to `ProfileDomainMath`.
3. **MapDetailLayout Viewport Coordination**:
   - In `MapDetailLayout.kt`, `totalSpan` SHALL dynamically evaluate `elevationTotalSpan` when `showElevationProfile == true` and `telemetryTotalSpan` when `showElevationProfile == false`, ensuring 100% viewport and zoom synchronization across all stacked charts and global zoom toolbars.
4. **Scrubbing Point Lookup Optimization**:
   - Remaining linear scans in `TelemetryMetricGraph.kt` (`minByOrNull { abs(it.distance - currentDistance) }`) SHALL be replaced with `TelemetryMetricUtils.findNearestPoint`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Single Source of Truth)**:
  * *Given* any telemetry chart composable (`ElevationProfile`, `TelemetryMetricGraph`, `MapDetailLayout`),
  * *When* evaluating the active horizontal domain,
  * *Then* `ProfileDomainMath.isEffectiveTimeDomain` SHALL be used universally.
* **AC-2 (Corrupt / Zero-Timestamp Track Safety)**:
  * *Given* a GPS track where `xAxisDomain == ProfileXAxisDomain.TIME` but `timeSec == 0L` across all points,
  * *When* rendering charts,
  * *Then* `isEffectiveTimeDomain` SHALL safely evaluate to `false` (Distance domain) and `calculateTotalSpan` SHALL return total distance, preventing collapsed zero-width canvases.
* **AC-3 (Trackless Parity)**:
  * *Given* a stationary/indoor workout without GPS coordinates (`distance <= 0.0` and `timeSec > 0L`),
  * *When* viewed in Aftermath,
  * *Then* all components SHALL consistently resolve to time-domain representation.
* **AC-4 (Null & Empty List Safety)**:
  * *Given* an empty `PathPoint` list or null input,
  * *When* calling `ProfileDomainMath` methods,
  * *Then* `isTracklessWorkout` and `isEffectiveTimeDomain` SHALL return `false`, and `calculateTotalSpan` SHALL return `0.0`.

### 1.4 System Invariants
1. **Zero Layout Shift**: Plot area paddings (`start = 50.dp, end = 25.dp`), Y-axis scales, and typography remain strictly identical.
2. **100% Pass Rate**: Full unit test suite (1550+ tests) must pass with zero failures.
3. **Human Gate Governance**: Parent ticket `ATT-2032` must NOT be moved to `Erledigt` autonomously.

---

## 2. Test Specification (TST-UI-220)

### Test Case 1: Pure Mathematical Domain & Span Tests (`TST-UI-220.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ProfileDomainMathTest.kt`
* **Preconditions**: Instantiated test suites with various track geometries:
  - Standard outdoor track (`distance = 5000.0, timeSec = 1200L`)
  - Stationary trackless workout (`distance = 0.0, timeSec = 1800L`)
  - Corrupt / non-timestamped track (`distance = 3000.0, timeSec = 0L`)
  - Empty track (`emptyList()`)
  - Null track reference (`null`)
  - Zero distance & zero time (`distance = 0.0, timeSec = 0L`)
* **Action**:
  - Evaluate `isTracklessWorkout` across all cases.
  - Evaluate `isEffectiveTimeDomain` across combinations of `ProfileXAxisDomain.DISTANCE` and `ProfileXAxisDomain.TIME`.
  - Evaluate `calculateTotalSpan` across combinations.
* **Expected Result**:
  - `isTracklessWorkout`: returns `true` only when `distance <= 0.0 && timeSec > 0L`.
  - `isEffectiveTimeDomain`: returns `true` for trackless sessions regardless of preference; returns `true` for `TIME` domain with positive timestamps; returns `false` when `timeSec <= 0L` (graceful fallback).
  - `calculateTotalSpan`: returns `timeSec.toDouble()` for effective time domain; returns `distance` for distance domain; returns `0.0` for empty/null tracks.

### Test Case 2: Component Integration & DRY Refactoring Audit (`TST-UI-220.2`)
* **Scope**: Structural Verification & Existing Map Test Suite
* **Target Files**:
  - `ElevationProfile.kt`
  - `TelemetryMetricGraph.kt`
  - `MapDetailLayout.kt`
  - `MapDetailLayoutCrossDomainPanTest.kt`
  - `TelemetryMetricGraphTracklessScrubbingTest.kt`
  - `ElevationProfileZoomMathTimeTest.kt`
* **Action**:
  - Verify all ad-hoc domain branching is replaced with `ProfileDomainMath`.
  - Verify `TelemetryMetricGraph.kt` replaces `minByOrNull` with `TelemetryMetricUtils.findNearestPoint`.
  - Execute existing 209 unit tests in `com.atrainingtracker.trainingtracker.ui.map.*`.
* **Expected Result**:
  - 100% pass rate across all existing map, gesture, zoom, and scrubbing unit tests.

### Test Case 3: 9-Language Localization & Specifier Audit (`TST-UI-220.3`)
* **Scope**: Localization Parity Test
* **Goal**: Verify string presence and matching tokens across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing entries.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-220.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite (1550+ tests).

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-220.1]` | Unit | `ProfileDomainMath.*` | `REQ-UI-261` (AC-1, AC-2, AC-3, AC-4) | Specified |
| `[TST-UI-220.2]` | Integration | `ElevationProfile`, `TelemetryMetricGraph`, `MapDetailLayout` | `REQ-UI-261` (Section 2, 3, 4) | Specified |
| `[TST-UI-220.3]` | Localization | `TranslationParityTest` | `REQ-UI-261`, `REQ-UI-106` | Specified |
| `[TST-UI-220.4]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
