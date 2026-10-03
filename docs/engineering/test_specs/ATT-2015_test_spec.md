# Stage 2: Requirement & Test Specification - ATT-2015: Render Training Zone Badge in Active Zone Color in ElevationProfile Scrubbing Card

**Ticket**: [ATT-2015](https://rainerblind.atlassian.net/browse/ATT-2015)  
**Sub-task**: [ATT-2098](https://rainerblind.atlassian.net/browse/ATT-2098) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.12`  
**Requirement Mapping**: `REQ-UI-242` (*Aftermath/Scrubbing: Render Training Zone Badge Suffix in Active Zone Color in Scrubbing Telemetry Badge*)  
**Test Spec ID**: `TST-UI-201`  
**Branch**: `feature/ATT-2015`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (REQ-UI-242)

### 1.1 Problem Statement & Rationale
In the Aftermath workout inspection screens (`TrackOnMapScreen.kt`, `MapDetailLayout.kt`, and `ElevationProfile.kt`), the floating multi-metric telemetry card (`ScrubbingTelemetryBadge`) displays instantaneous sensor telemetry.

When heart rate or cycling power data is present alongside user-configured training zone thresholds, `ScrubbingTelemetryBadge` displays the active zone tag (e.g. `145 bpm • Z2` or `220 W • Z3`). Previously, the entire label was given a static hardcoded color (`TTColor.Zone4` for HR and `TTColor.Zone5` for Power). This produced visual dissonance where recovery or aerobic zones (`• Z1`, `• Z2`) were rendered in alerting orange or red colors.

Splitting the label into a neutral base metric and an active zone suffix rendered directly in that zone's specific semantic color (`TTColor.Zone1` .. `TTColor.Zone5`) restores physiological meaning, improves scannability, and eliminates confusing false alarms.

### 1.2 Functional & Architectural Requirements
The system SHALL render instantaneous training zone suffixes in their active zone color within `ScrubbingTelemetryBadge` (`ElevationProfile.kt`) (ATT-2015):

1. **Separated Metric Value & Dynamic Zone Suffix**:
   * In `ScrubbingTelemetryBadge` (`ElevationProfile.kt`), when Heart Rate (`hasHr`) or Cycling Power (`hasPower`) is displayed:
     - The base numeric value and unit (e.g. `${point.hr} bpm`, `${point.power} W`) SHALL be rendered in high-contrast neutral text color (`MaterialTheme.colorScheme.onSurface`).
     - When an active zone (`hrZone`, `powerZone` in `1..5`) is resolved from thresholds via `TelemetryZoneMath`, the zone suffix (` • Z$hrZone`, ` • Z$powerZone`) SHALL be rendered directly in that zone's color using `TelemetryZoneMath.ZONE_COLORS[zone - 1]`:
       - Zone 1: `TTColor.Zone1` (Grey / Blue)
       - Zone 2: `TTColor.Zone2` (Blue)
       - Zone 3: `TTColor.Zone3` (Green)
       - Zone 4: `TTColor.Zone4` (Yellow / Orange)
       - Zone 5: `TTColor.Zone5` (Red)
     - The zone suffix text SHALL be styled with `FontWeight.Bold` for optimal readability.

2. **Inline Annotated String Representation**:
   * The base metric and zone suffix SHALL be combined within an `AnnotatedString` (`buildAnnotatedString`), ensuring single-line inline layout without baseline jitter, multi-line wrapping, or unexpected layout reflows during continuous scrubbing.

3. **Neutral Graceful Fallback**:
   * If zone thresholds are not configured or zone resolution evaluates to `null` (or outside `1..5`), the composable SHALL render only the base metric value (e.g. `145 bpm`, `220 W`) in `MaterialTheme.colorScheme.onSurface` without trailing delimiters (`•`) or dangling characters.

4. **Preservation of Core Invariants**:
   * Speed/Pace metric styling (`MaterialTheme.colorScheme.primary`) SHALL remain unchanged.
   * `ScrubbingTelemetryBadge` public signature, parameters (`hrZoneThresholds: HeartRateZoneThresholds? = null`, `powerZoneThresholds: PowerZoneThresholds? = null`), and placement invariants SHALL remain 100% backward compatible.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Heart Rate Zone Color Matching)**:
  * *Given* a workout with heart rate telemetry and configured HR zone thresholds,
  * *When* the athlete scrubs across points in Zone 1, 2, 3, 4, or 5,
  * *Then* the `• Z<n>` suffix SHALL be rendered in the corresponding `TTColor.Zone<n>` color (`TelemetryZoneMath.ZONE_COLORS[n - 1]`).
* **AC-2 (Power Zone Color Matching)**:
  * *Given* a cycling workout with power telemetry and configured power zone thresholds,
  * *When* the athlete scrubs across power points in Zone 1, 2, 3, 4, or 5,
  * *Then* the `• Z<n>` suffix SHALL be rendered in the corresponding `TTColor.Zone<n>` color (`TelemetryZoneMath.ZONE_COLORS[n - 1]`).
* **AC-3 (Neutral Base Metric Text)**:
  * *Given* any scrubbed telemetry point with HR or Power,
  * *When* rendered in `ScrubbingTelemetryBadge`,
  * *Then* the numeric value and unit (e.g. `142 bpm`, `215 W`) SHALL be rendered in `MaterialTheme.colorScheme.onSurface`.
* **AC-4 (Graceful Fallback Without Zone Data)**:
  * *Given* a telemetry point where zone thresholds are null or zone calculation yields null,
  * *When* rendered in `ScrubbingTelemetryBadge`,
  * *Then* only the base value SHALL be shown (e.g. `142 bpm`) in `MaterialTheme.colorScheme.onSurface` without any dangling separator or miscolored suffix.

### 1.4 System Invariants
* Single-line inline presentation: zero vertical wrapping or badge height fluctuation during scrubbing.
* Contrast accessibility: `onSurface` and `TTColor.Zone1`..`Zone5` comply with WCAG accessibility guidelines.
* Zero SQLite schema changes.
* Parent ticket Human Decision Gate remains strictly guarded.

---

## 2. Test Specification (TST-UI-201)

### Test Case 1: `testScrubbingTelemetryBadge_rendersZoneSuffixInZoneColor` (`TST-UI-201.1`)
* **Scope**: Contract & Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ScrubbingBadgeZoneColorContractTest.kt`
* **Preconditions**: Project checked out on branch `feature/ATT-2015`.
* **Action**:
  1. Inspect `ElevationProfile.kt` source code to verify `buildAnnotatedString` is utilized for HR and Power in `ScrubbingTelemetryBadge`.
  2. Verify base metric value is styled with `MaterialTheme.colorScheme.onSurface`.
  3. Verify zone suffix (` • Z$hrZone`, ` • Z$powerZone`) is styled with `TelemetryZoneMath.ZONE_COLORS`.
  4. Verify fallback branch renders plain base metric string without `• Z` when zone is null.
* **Expected Result**: 100% assertions pass.

### Test Case 2: Existing Invariant Tests (`TST-UI-201.2`)
* **Scope**: Regression Unit Tests
* **Target Files**:
  * `ElevationProfileLayoutTest.kt`
  * `TelemetryMetricGraphZoneTest.kt`
  * `MapDetailLayoutScrubbingBadgeContractTest.kt`
* **Action**: Run targeted test suite via Gradle.
* **Expected Result**: 100% pass rate. `TelemetryMetricGraphZoneTest.kt` verifies zone calculation and threshold parameter preservation.

### Test Case 3: 9-Language Localization Audit (`TST-UI-201.3`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Action**: Run `TranslationParityTest` to confirm all localized strings remain 100% synchronized across all 9 locales.
* **Expected Result**: 100% pass rate.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-201.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite (1,380+ tests passing).

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-201.1` | Contract / Unit | `ScrubbingBadgeZoneColorContractTest` | `REQ-UI-242` | Specified |
| `TST-UI-201.2` | Regression / Unit | `ElevationProfileLayoutTest`, `TelemetryMetricGraphZoneTest` | `REQ-UI-242`, `REQ-UI-230` | Specified |
| `TST-UI-201.3` | Localization | `TranslationParityTest` | `REQ-UI-242`, `REQ-UI-106` | Specified |
| `TST-UI-201.4` | Full Suite Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-014` | Specified |
