# Stage 2: Requirement & Test Specification - ATT-2137: Strava Activity Data Not Displayed in Workout Details

**Ticket**: [ATT-2137](https://rainerblind.atlassian.net/browse/ATT-2137)  
**Sub-task**: [ATT-2149](https://rainerblind.atlassian.net/browse/ATT-2149) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2137`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Formal Requirement Specification

### REQ-UI-248: Strava Activity Data Upper Metadata Slotting & Structural Parity in Detailed Workout View
The system SHALL ensure that Strava activity results (PR badges, best efforts, segment achievements) in the detailed workout view (`TrackOnMapScreen` / `MapDetailLayout`) maintain structural order parity with the workout list summary (`WorkoutSummary.kt`):
1. **Upper Metadata Slotting (`metadataContent`)**:
   * `TrackOnMapScreen.kt` SHALL render `StravaActivitySection` within the `metadataContent` slot rather than `analyticsContent`.
   * `StravaActivitySection` SHALL be rendered immediately after `WorkoutExtrema` and prior to the map viewport container (`BoxWithConstraints`).
2. **Preference & Data Guards**:
   * `StravaActivitySection` SHALL only be rendered when:
     - `activeDetailPrefs.showStrava == true`, AND
     - `!workoutData.stravaActivityData.isNullOrBlank()`.
3. **Analytics Slot Cleanup**:
   * `StravaActivitySection` SHALL NOT be rendered in `analyticsContent`, eliminating redundant duplicate rendering below telemetry graphs.
4. **Preserved Invariants**:
   * Custom `metadataContent` callers (when `metadataContent != null`) retain complete control over upper slot rendering.

---

## 2. Formal Test Specification

### TST-UI-207: Strava Activity Data Upper Metadata Routing & Preference Verification
* **Target Classes**: `TrackOnMapScreen.kt`, `TrackOnMapScreenMetadataRoutingContractTest.kt`
* **Test Case 1 (Structural Slot Routing)**:
  - Verify that `StravaActivitySection` is called within the `metadataContent` lambda in `TrackOnMapScreen.kt`.
  - Verify that `StravaActivitySection` is invoked after `WorkoutExtrema`.
  - Verify that `StravaActivitySection` is absent from `analyticsContent`.
* **Test Case 2 (Preference Guard Enforcement)**:
  - Verify that `StravaActivitySection` is guarded by `activeDetailPrefs.showStrava && !workoutData.stravaActivityData.isNullOrBlank()`.
* **Test Case 3 (Zero Regressions)**:
  - Full clean-room unit test suite execution with 100% pass rate.

---

## 3. Acceptance Criteria (Given-When-Then)

* **AC-1 (Prominent Visibility Above Map)**:
  * **Given** a workout containing valid Strava activity JSON and `showStrava` enabled in workout detail preferences,
  * **When** the user opens the detailed workout view (`TrackOnMapScreen`),
  * **Then** `StravaActivitySection` is immediately visible above the map container without requiring the user to scroll through telemetry graphs.
* **AC-2 (Preference Suppression)**:
  * **Given** `showStrava` disabled in workout detail preferences,
  * **When** opening the detailed workout view,
  * **Then** `StravaActivitySection` is completely hidden.
* **AC-3 (Structural Parity with WorkoutSummary)**:
  * **Given** any workout viewed in both `WorkoutSummary` and `TrackOnMapScreen`,
  * **Then** the sequence of metadata cards (Description -> Extrema -> Strava -> Map) is identical.
