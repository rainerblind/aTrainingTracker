# Stage 2: Requirement & Test Specification - ATT-5483: LapEditBottomSheet in Workout Details

**Ticket**: [ATT-5483](https://rainerblind.atlassian.net/browse/ATT-5483)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5483`  
**Requirement Mapping**: `REQ-UI-142`, `REQ-UI-408`, `REQ-UI-450` | **Test Spec ID**: `TST-UI-095`, `TST-UI-371`, `TST-UI-450`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-UI-142`, `REQ-UI-408`, `REQ-UI-450`)

### 1.1 Problem Statement & Rationale
In Workout Details (`TrackOnMapScreen.kt`), clicking a lap in the *"Runden & Segmente"* tab (`WorkoutDetailTabbedContent.kt`) does not open `LapEditBottomSheet`. Athletes cannot inspect the lap segment map preview, edit custom lap names/descriptions, or trigger lap boundary adjustments from the detail view. The system SHALL host `LapEditBottomSheet` as an overlay in `TrackOnMapScreen.kt` alongside tabbed content (`Rule 47`), wire `onLapClick` from `WorkoutDetailTabbedContent.kt`, persist edits via `WorkoutRepository.updateLapDetails`, and route boundary moves to `MoveLapBoundaryOnMapSheet`.

### 1.2 Functional & Architectural Requirements
1. **Modal Overlay Retention (Rule 47 & `REQ-UI-142`)**: In `TrackOnMapScreen.kt`, host `activeEditingLap?.let { LapEditBottomSheet(...) }` as an `AppModalBottomSheet` overlay at the screen root, preserving active tab selection and pager state.
2. **Callback Delegation (`REQ-UI-408`)**: Forward `onLapClick: ((LapData) -> Unit)?` parameter from `TrackOnMapScreen.kt` through `WorkoutDetailTabbedContent.kt` to `WorkoutLaps.kt`, updating `selectedLapNr` while activating `activeEditingLap = lap`.
3. **Persistence & Boundary Move Handoff (`REQ-UI-450`)**: Connect `onSaveLap` to `WorkoutRepository.updateLapDetails(workoutData.id, lapNr, name, description)` and route `onMoveBoundaryClick` to dismiss `LapEditBottomSheet` and open `MoveLapBoundaryOnMapSheet`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Tabbed Lap Click Invocation)**: *Given* Workout Details open on the "Runden & Segmente" tab, *When* the athlete taps any lap row, *Then* `LapEditBottomSheet` opens as a modal overlay displaying the lap metrics and segment map preview.
* **AC-2 (Persistence Integrity)**: *Given* `LapEditBottomSheet` open for Lap $k$, *When* the athlete modifies name/description and saves, *Then* changes persist to `Laps.db` and reactively update the UI.
* **AC-3 (Boundary Move Transition)**: *Given* `LapEditBottomSheet` open for Lap $k < K$, *When* the athlete taps "Move Boundary on Map", *Then* the edit sheet dismisses and `MoveLapBoundaryOnMapSheet` opens pre-seeded at Lap $k$.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-142` (amended to explicitly mandate `LapEditBottomSheet` invocation in Workout Details `TrackOnMapScreen` / `WorkoutDetailTabbedContent` alongside `WorkoutSummary`); interacting with `REQ-UI-408` and `REQ-UI-450`.
* **Historical Origin & Commit Trace**: `c2f96cae` (`ATT-511`), `ea5fa705` (`ATT-3920`), `ATT-908`, `ATT-5483`.
* **Root Reason for Existing Formulation**: Conceived when `WorkoutSummary.kt` was the primary workout inspection surface; subsequent tabbed detail refactoring (`ATT-3920`) inadvertently omitted sheet integration, leaving `onLapClick` updating only `selectedLapNr` on the hidden Map tab.
* **Preservation of Core Invariants**: Retains single-page map highlighting contract (`selectedLapNr`) while adding sheet overlay; preserves quantitative metric immutability; enforces Rule 47 modal overlay alongside host tabs.

---

## 2. Test Specification (`TST-UI-095`, `TST-UI-371`, `TST-UI-450`)

### 2.1 Test Cases
1. **TC-1: Tabbed Lap Click Delegation (`TST-UI-095.4`, `TST-UI-371.3`)**:
   - *Scope*: Compose UI unit test (`WorkoutDetailTabbedLapsTest.kt`).
   - *Procedure*: Render `WorkoutDetailTabbedContent` with laps. Click lap row and assert `onLapClick(lap)` is invoked while `selectedLapNr` updates.
2. **TC-2: Root Screen Modal Overlay Hosting (`TST-UI-095.4`, `TST-UI-450.3`)**:
   - *Scope*: Compose UI unit test (`TrackOnMapScreenTest.kt`).
   - *Procedure*: Trigger lap tap; assert `LapEditBottomSheet` renders as overlay alongside tabs (Rule 47). Tap "Move Boundary"; assert edit sheet dismisses and `MoveLapBoundaryOnMapSheet` opens.
3. **TC-3: Targeted Test Suite Regression (`TST-UI-095.5`)**:
   - *Command*: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.build.AspiceSweAuditContractTest"`
   - *Expected*: 100% pass rate across aftermath UI and ASPICE contract suites.

---

## 3. Traceability Matrix
| Requirement ID | Test Case ID | Scope | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-142` | `TST-UI-095.4` (TC-1, TC-2) | UI / Overlay (`WorkoutDetailTabbedLapsTest`) | Planned (`ATT-5483`) |
| `REQ-UI-408` | `TST-UI-371.3` (TC-1) | Tabbed Content (`WorkoutDetailTabsTest`) | Planned (`ATT-5483`) |
| `REQ-UI-450` | `TST-UI-450.3` (TC-2) | Boundary Move (`MoveLapBoundaryOnMapSheetTest`) | Planned (`ATT-5483`) |
| `REQ-PRO-034` | `TST-PRO-034` (TC-3) | ASPICE SWE Audit (`AspiceSweAuditContractTest`) | Verified |
