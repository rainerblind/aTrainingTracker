# Stage 2: Requirement & Test Specification - ATT-2112 (Revision 2): Ensure Description and Extrema Cards Render in Detailed Workout View

**Ticket**: [ATT-2112](https://rainerblind.atlassian.net/browse/ATT-2112)  
**Sub-task**: [ATT-2127](https://rainerblind.atlassian.net/browse/ATT-2127) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Requirement Mapping**: `REQ-UI-245` (*Aftermath: Upper Metadata Slot Architecture & Reactive Detail Section Rendering in MapDetailLayout*)  
**Test Spec ID**: `TST-UI-204` (*Upper Metadata Slot & WorkoutDetailPreferences Reactive Rendering Verification*)  
**Branch**: `feature/ATT-2112`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Requirement Specification (REQ-UI-245 Revision 2)

### 1.1 Problem Statement & Rationale
In Sprint 2026-40.12 (ATT-2030 / `REQ-UI-240`), independent preference controls were established for Workout Summary List vs. Detailed Workout view. In iteration 1 of ATT-2112, `metadataContent` was introduced in `lowerColumn` below the map. During physical device review on Pixel 10 (Sprint Review Ceremony 2), this was rejected (`n.i.O.`) because `WorkoutSummary.kt` places Description and Extrema ABOVE the map. This revision relocates `metadataContent` in `MapDetailLayout.kt` to the top static Column immediately following `header()` and above the interactive map, achieving 100% visual order parity between Workout Summary and Detailed Workout.

### 1.2 Functional & Architectural Requirements
1. **Top Metadata Placement Above the Map in `MapDetailLayout.kt`**:
   - `MapDetailLayout` SHALL declare an optional slotted composable parameter:
     `metadataContent: (@Composable ColumnScope.() -> Unit)? = null`
   - When `metadataContent` is non-null, it SHALL be rendered in a `Surface` container immediately beneath `header()` in the top Column and above `BoxWithConstraints` (the map container).
   - `metadataContent` SHALL NOT be rendered in `lowerColumn`, avoiding duplicate rendering.
2. **Component Routing in `TrackOnMapScreen.kt`**:
   - `TrackOnMapScreen` SHALL route `WorkoutDescription` (gated by `activeDetailPrefs.showDescription`) and `WorkoutExtrema` (gated by `activeDetailPrefs.showExtrema`) into the `metadataContent` slot of `MapDetailLayout`.
   - `TrackOnMapScreen` SHALL retain `HeartRateZoneDistributionCard`, `PowerZoneDistributionCard`, `LapSplitVisualizerCard`, and `StravaActivitySection` in `analyticsContent` (below telemetry charts).
3. **Content Guard & Empty-State Resilience**:
   - `WorkoutDescription` SHALL only render when at least one field (`description`, `goal`, or `method`) is non-blank, preventing blank space when no notes exist.
   - `WorkoutExtrema` SHALL only render when `workoutData.extremaData.dataRows.isNotEmpty()`.
4. **Visual Order Parity Invariant**:
   - In Detailed Workout (`TrackOnMapScreen`), the section order SHALL be:
     1. Header (`WorkoutHeader`)
     2. Description & Notes (`WorkoutDescription`)
     3. Extrema (`WorkoutExtrema`)
     4. Map (`mapBox`)
     5. Telemetry Zoom Toolbar & Graphs (`ElevationProfile`, `TelemetryMetricGraph`s)
     6. Analytics Cards (Zones, Laps, Strava)
   - This order is strictly aligned with the section sequence of `WorkoutSummary.kt`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Placement Above the Map)**:
  * *Given* an athlete opens a workout with notes or extrema in the Detailed Workout view,
  * *When* the screen renders,
  * *Then* `WorkoutDescription` and `WorkoutExtrema` SHALL appear above the map, immediately following `WorkoutHeader`.
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

## 2. Test Specification (TST-UI-204 Revision 2)

### Test Case 1: `testMapDetailLayout_declaresAndRendersMetadataContentAboveMap` (`TST-UI-204.1`)
* **Scope**: Structural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutMetadataSlotContractTest.kt`
* **Preconditions**: `MapDetailLayout.kt` source code available.
* **Action**: Verify `metadataContent: (@Composable ColumnScope.() -> Unit)? = null` parameter declaration, placement in the top Column between `header()` and `BoxWithConstraints` (above the map), and absence from `lowerColumn`.
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
| `TST-UI-204.1` | Contract | `MapDetailLayout` metadata slot above map | `REQ-UI-245` | Specified |
| `TST-UI-204.2` | Contract | `TrackOnMapScreen` metadata routing | `REQ-UI-245` | Specified |
| `TST-UI-204.3` | Localization | `TranslationParityTest` | `REQ-UI-106` | Specified |
| `TST-UI-204.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-014` | Specified |
