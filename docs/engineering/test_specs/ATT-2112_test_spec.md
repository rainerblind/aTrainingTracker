# Stage 2: Requirement & Test Specification - ATT-2112: Ensure Description and Extrema Cards Render in Detailed Workout View

**Ticket**: [ATT-2112](https://rainerblind.atlassian.net/browse/ATT-2112)  
**Sub-task**: [ATT-2116](https://rainerblind.atlassian.net/browse/ATT-2116) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Requirement Mapping**: `REQ-UI-245` (*Aftermath: Upper Metadata Slot Architecture & Reactive Detail Section Rendering in MapDetailLayout*)  
**Test Spec ID**: `TST-UI-204` (*Upper Metadata Slot & WorkoutDetailPreferences Reactive Rendering Verification*)  
**Branch**: `feature/ATT-2112`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Requirement Specification (REQ-UI-245)

### 1.1 Problem Statement & Rationale
In Sprint 2026-40.12 (ATT-2030 / `REQ-UI-240`), independent preference controls were established for Workout Summary List vs. Detailed Workout view. However, in `TrackOnMapScreen.kt` / `MapDetailLayout.kt`, `WorkoutDescription` and `WorkoutExtrema` were placed at the very end of `analyticsContent`, beneath up to 840dp of telemetry graphs and 580dp of zone/split cards (total >1,420dp scroll depth). Athletes inspecting a workout on-device could not locate the cards, creating the impression of broken preferences. This requirement establishes an upper metadata slot positioned immediately beneath the map and above the graphs, restoring visual hierarchy and instant access to workout notes and extrema.

### 1.2 Functional & Architectural Requirements
1. **Upper Metadata Slot in `MapDetailLayout.kt`**:
   - `MapDetailLayout` SHALL declare an optional slotted composable parameter:
     `metadataContent: (@Composable ColumnScope.() -> Unit)? = null`
   - In `lowerColumn`, when `metadataContent` is non-null, it SHALL be rendered in a `Surface` container at the **top** of the scrollable container, immediately preceding `ElevationProfile` and `TelemetryMetricGraph`s.
   - `val hasScrollableContent` SHALL evaluate:
     `metadataContent != null || analyticsContent != null || hasTelemetryGraphs`
2. **Component Routing in `TrackOnMapScreen.kt`**:
   - `TrackOnMapScreen` SHALL route `WorkoutDescription` (gated by `activeDetailPrefs.showDescription`) and `WorkoutExtrema` (gated by `activeDetailPrefs.showExtrema`) into the new `metadataContent` slot of `MapDetailLayout`.
   - `TrackOnMapScreen` SHALL retain `HeartRateZoneDistributionCard`, `PowerZoneDistributionCard`, `LapSplitVisualizerCard`, and `StravaActivitySection` in `analyticsContent` (below the telemetry charts).
3. **Content Guard & Empty-State Resilience**:
   - `WorkoutDescription` SHALL only render when at least one field (`description`, `goal`, or `method`) is non-blank, preventing blank space when no notes exist.
   - `WorkoutExtrema` SHALL only render when `workoutData.extremaData.dataRows.isNotEmpty()`.
4. **Reactive Preference Synchronization**:
   - `TrackOnMapScreen` SHALL observe `MyPreferenceManager.workoutDetailPreferencesFlow` reactively, updating visibility immediately upon DataStore preference updates.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Immediate Metadata Visibility)**:
  * *Given* an athlete opens a workout with notes or extrema in the Detailed Workout view,
  * *When* the screen renders,
  * *Then* `WorkoutDescription` and `WorkoutExtrema` SHALL appear at the top of the lower pane directly beneath the map/divider and above the elevation profile and telemetry charts without requiring deep scrolling.
* **AC-2 (Preference Gating in Detail Screen)**:
  * *Given* an athlete disables "Beschreibung & Notizen" or "Extremwerte" in Advanced Settings Section 5 ("In Details" column),
  * *When* viewing the detailed workout,
  * *Then* the disabled section SHALL be completely omitted from the layout.
* **AC-3 (Zero Regression on Non-Aftermath Callers)**:
  * *Given* route inspection (`RouteOnMapScreen`) or segment inspection (`SegmentOnMapScreen`),
  * *When* invoking `MapDetailLayout` with default `metadataContent = null`,
  * *Then* the layout SHALL render cleanly with zero blank spacing or layout regression.

### 1.4 System Invariants
1. `MapDetailLayout` callers without `metadataContent` remain 100% backward compatible.
2. Scrubbing gestures, zoom toolbar operations, and map gestures MUST NOT be intercepted or degraded.
3. 100% clean-room test suite pass rate.

---

## 2. Test Specification (TST-UI-204)

### Test Case 1: `testMapDetailLayout_declaresAndRendersMetadataContentAboveGraphs` (`TST-UI-204.1`)
* **Scope**: Structural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutMetadataSlotContractTest.kt`
* **Preconditions**: `MapDetailLayout.kt` source code available.
* **Action**: Verify `metadataContent: (@Composable ColumnScope.() -> Unit)? = null` parameter declaration, `hasScrollableContent` inclusion, and placement in `lowerColumn` before `showElevationProfile || hasTelemetryGraphs`.
* **Expected Result**: Assertions pass.

### Test Case 2: `testTrackOnMapScreen_routesDescriptionAndExtremaToMetadataContent` (`TST-UI-204.2`)
* **Scope**: Architecture & Routing Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreenMetadataRoutingContractTest.kt`
* **Preconditions**: `TrackOnMapScreen.kt` source code available.
* **Action**: Verify that `metadataContent` passes `WorkoutDescription` and `WorkoutExtrema` gated on `activeDetailPrefs.showDescription` and `activeDetailPrefs.showExtrema`. Verify that `analyticsContent` does not duplicate them.
* **Expected Result**: Assertions pass.

### Test Case 3: 9-Language Localization Audit (`TST-UI-204.3`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Action**: Verify all translation keys across EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Expected Result**: 100% parity.

### Test Case 4: Full Clean-Room Regression Suite (`TST-UI-204.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% pass rate across all unit and contract tests with zero failures.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-204.1` | Contract | `MapDetailLayout` metadata slot architecture | `REQ-UI-245` | Specified |
| `TST-UI-204.2` | Contract | `TrackOnMapScreen` metadata routing | `REQ-UI-245` | Specified |
| `TST-UI-204.3` | Localization | `TranslationParityTest` | `REQ-UI-106` | Specified |
| `TST-UI-204.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-014` | Specified |
