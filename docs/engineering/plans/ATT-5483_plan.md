# Stage 3: Implementation Plan - ATT-5483: LapEditBottomSheet in Workout Details

**Ticket**: [ATT-5483](https://rainerblind.atlassian.net/browse/ATT-5483)  
**Sub-task**: `[Impl-Plan]` (submitting via `submit-stage`)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5483`  
**Requirement Mapping**: `REQ-UI-142`, `REQ-UI-408`, `REQ-UI-450`  
**Test Mapping**: `TST-UI-095`, `TST-UI-371`, `TST-UI-450`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
In Workout Details (`TrackOnMapScreen.kt`), clicking a lap in the *"Runden & Segmente"* tab (`WorkoutDetailTabbedContent.kt` / `WorkoutDetailSectionType.LAPS`) does not open `LapEditBottomSheet`. Athletes cannot inspect the lap segment map preview, edit custom lap names/descriptions, or trigger lap boundary adjustments from the detail view. The system must host `LapEditBottomSheet` as an overlay in `TrackOnMapScreen.kt` alongside tabbed content (`Rule 47`), wire `onLapClick` from `WorkoutDetailTabbedContent.kt`, persist edits via `WorkoutRepository.updateLapDetails`, and route boundary moves to `MoveLapBoundaryOnMapSheet`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-142` (*Interactive Lap Detail Editing, Quick-Tag Presets, Sequential Navigation, and Segment Map Highlighting*)
* **Requirement**: `REQ-UI-408` (*Individually Configurable Multi-Tab Workout Details Layout, Mode A Card Scroll*)
* **Requirement**: `REQ-UI-450` (*Interactive Map Lap Boundary Moving in Workout Aftermath*)
* **Test Mapping**: `TST-UI-095` (`TST-UI-095.4`), `TST-UI-371` (`TST-UI-371.3`), `TST-UI-450` (`TST-UI-450.3`)

---

## 3. System Invariants & Preserved Behavior
1. **Modal Overlay Retention (Rule 47 & REQ-UI-149)**: `LapEditBottomSheet` must render alongside host screens/tabs, never unmounting tabs or resetting pager scroll states.
2. **Quantitative Metric Immutability**: Lap times, distances, speeds, and GPS coordinates remain strictly immutable during name/description updates.
3. **Database Threading & Dispatcher Affinity**: Lap detail updates execute asynchronously on `Dispatchers.IO` via `WorkoutRepository.updateLapDetails`.
4. **Boundary Move Parity**: Moving boundaries from `LapEditBottomSheet` dismisses the edit sheet and pre-seeds `MoveLapBoundaryOnMapSheet` (`REQ-UI-450.1`).

---

## 4. Proposed Architectural Changes

### Component 1: `WorkoutDetailTabbedContent.kt`
* Add optional callback parameter: `onLapClick: ((LapData) -> Unit)? = null`.
* In `WorkoutDetailSectionType.LAPS`, forward click events: `onLapClick = { lap -> onSelectLapNr(if (selectedLapNr == lap.lapNr) null else lap.lapNr); onLapClick?.invoke(lap) }`.

### Component 2: `TrackOnMapScreen.kt`
* Introduce state `var activeEditingLap by remember { mutableStateOf<LapData?>(null) }`.
* Pass `onLapClick = { lap -> activeEditingLap = lap; selectedLapNr = lap.lapNr }` to `WorkoutDetailTabbedContent` and update fallback `MapDetailLayout` `LapSplitVisualizerCard` clicks similarly.
* Render `LapEditBottomSheet` overlay alongside `CreateLapOnMapSheet` and `MoveLapBoundaryOnMapSheet` at the composable root.
* Wire `onSaveLap` to `repository.updateLapDetails(workoutData.id, lapNr, name, description)` in `coroutineScope.launch` and `onMoveBoundaryClick` to `moveBoundaryLapNr = boundaryLapNr; showMoveBoundarySheet = true`.

### UI Consistency (Rule 23)
* **Reference screen / component**: `WorkoutSummary.kt` (lines 546–565).
* **Reused components**: `LapEditBottomSheet`, `MoveLapBoundaryOnMapSheet`, `WorkoutLaps`.
* **Theme tokens**: `MaterialTheme.colorScheme.*`, `TTColor.*`, standard sheet corner radius and padding.
* **New one-off styles & justification**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Forward Lap Click in `WorkoutDetailTabbedContent.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt`
* Add `onLapClick: ((LapData) -> Unit)? = null` parameter; invoke `onLapClick?.invoke(lap)` in `WorkoutDetailSectionType.LAPS`.

### Step 2: Host & Wire `LapEditBottomSheet` in `TrackOnMapScreen.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt`
* Add `activeEditingLap` state, wire `onLapClick` on `WorkoutDetailTabbedContent`, and render `LapEditBottomSheet` overlay.

### Step 3: Extend Architectural Contract Tests
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreenLapRoutingContractTest.kt`
* Assert `WorkoutDetailTabbedContent` exposes `onLapClick` and `TrackOnMapScreen` hosts `LapEditBottomSheet` alongside other sheets (`Rule 47`).

### Step 4: Execute Targeted Unit Tests
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Verify contract tests pass, living doc governance passes (`verify_requirement_governance.py`), and Pixel 10 landscape/portrait walkthrough confirms sheet opening and editing.
* **Rollback**: Atomic revert of commits on `feature/ATT-5483` leaves `sprint/2026-45.7` intact.
