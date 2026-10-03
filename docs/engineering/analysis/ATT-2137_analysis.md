# Stage 1: Problem Domain & Root Cause Analysis - ATT-2137: Strava Activity Data Not Displayed in Workout Details

**Ticket**: [ATT-2137](https://rainerblind.atlassian.net/browse/ATT-2137)  
**Sub-task**: [ATT-2148](https://rainerblind.atlassian.net/browse/ATT-2148) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2137`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Domain & Forensic Investigation

### 1.1 Physical Device Observation & User Feedback
In `AdvancedTuningDialog.kt` (`TuningPreferences`), athletes can configure section visibility independently for the list view (`workoutCardPrefs`) and the detailed view (`workoutDetailPrefs`).

While Strava activity data (PR badges, best efforts, segment achievements) renders cleanly in `WorkoutSummary.kt` in the list, on physical devices (Pixel 10) in the detailed workout view (`TrackOnMapScreen` / `MapDetailLayout`), the Strava section was reported missing or not visible, even when `showStrava` was checked and valid Strava JSON was stored in `StravaUpload.db`.

### 1.2 Architectural Root Cause
1. **Misalignment of Layout Order & Viewport Slotting**:
   * In `WorkoutSummary.kt`, the canonical layout order is:
     1. Header (`WorkoutSummaryHeader`)
     2. Description & Notes (`WorkoutDescription`)
     3. Key Stats Matrix
     4. Extrema (`WorkoutExtrema`)
     5. Laps (`WorkoutLaps`)
     6. **Strava Activity Section (`StravaActivitySection`)**
     7. Map Preview & Elevation Profile
     8. Telemetry Metric Graphs (Speed, HR, Power)
     9. Zone Distributions (HR, Power)
   * In `TrackOnMapScreen.kt`, `metadataContent` (slotted above the map in `MapDetailLayout.kt` per `REQ-UI-245` / `ATT-2112`) only contained `WorkoutDescription` and `WorkoutExtrema`.
   * `StravaActivitySection` was erroneously relegated to the bottom of `analyticsContent` (line 316) in `lowerColumn`, positioned below:
     * Elevation profile
     * Interactive scrubbing bar
     * All telemetry graphs (Speed/Pace, HR, Power, Cadence)
     * HR zone distribution card
     * Power zone distribution card
     * Lap split visualizer card
2. **Impact on User Experience**:
   * On mobile viewports, having Strava placed at the very bottom of the lower split container buried it beneath multiple tall charts and cards.
   * Furthermore, it violated the core sprint mandate established by the user in `ATT-2112`:
     *"The order within the details workout must be identical to the order within the workout summary. I.e. the description and notes must be above the map."*

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement Target**:
   `REQ-UI-240` (`ATT-2030`: Two-column section visibility configuration matrix) and `REQ-EXP-013` (Strava activity results display).
2. **Historical Origin**:
   When `TrackOnMapScreen` was first extended with aftermath cards in Sprint 2026-40.12, all cards were lumped into `analyticsContent` inside `lowerColumn`. In `ATT-2112`, description and extrema were moved above the map (`metadataContent`), but Strava was left behind in `analyticsContent`.
3. **Chesterton's Fence Rationale**:
   Leaving Strava in `analyticsContent` was an oversight during the `ATT-2112` refactoring. Moving `StravaActivitySection` into `metadataContent` immediately after `WorkoutExtrema` restores structural symmetry with `WorkoutSummary.kt` without breaking any existing data flows.
4. **Preserved Invariants**:
   * `activeDetailPrefs.showStrava` toggle strictly controls visibility.
   * Empty/null `workoutData.stravaActivityData` renders nothing (zero visual clutter).
   * Slotted customization (`metadataContent != null`) remains supported.

---

## 3. Scope Bounding & Proposed Architecture

* **Relocation in `TrackOnMapScreen.kt`**:
  * Move `StravaActivitySection` from `analyticsContent` to the default `metadataContent` lambda.
  * Render order in `metadataContent`:
    1. `WorkoutDescription` (if `activeDetailPrefs.showDescription`)
    2. `WorkoutExtrema` (if `activeDetailPrefs.showExtrema && workoutData.extremaData.dataRows.isNotEmpty()`)
    3. `StravaActivitySection` (if `activeDetailPrefs.showStrava && !workoutData.stravaActivityData.isNullOrBlank()`)
  * Remove `StravaActivitySection` from `analyticsContent` to prevent redundant rendering.
* **Contract Test**:
  * Implement architectural contract test asserting that `StravaActivitySection` is slotted within `metadataContent` and is completely absent from `analyticsContent`.
