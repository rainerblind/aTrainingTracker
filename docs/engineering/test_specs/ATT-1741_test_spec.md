# Stage 2: Requirement & Test Specification - ATT-1741: [Aftermath/Splits] Revert Lap & Interval Split Chart from Aftermath screens

**Ticket**: [ATT-1741](https://atrainingtracker.atlassian.net/browse/ATT-1741)  
**Sub-task**: [ATT-1756](https://atrainingtracker.atlassian.net/browse/ATT-1756) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Requirement Mapping**: `REQ-UI-204` (*Aftermath: Compact Lap & Interval Split Chart Revert & Deferral Architecture*)  
**Test Spec ID**: `TST-UI-158`  
**Branch**: `feature/ATT-1741`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Requirement Specification (REQ-UI-204)

### 1.1 Problem Statement & Rationale
During Sprint Review 2026-40.5, user review rejected the initial visual split chart implementation:
> *"I saw already saw this. It looks really bad. Thus, please create one ticket to revert this and one ticket to make this much more better."*

To restore visual excellence and unclutter the post-workout inspection experience, ticket ATT-1741 removes `LapSplitChartCard` from `TrackOnMapScreen` and `LapSplitChart` from `WorkoutLaps`, reverting the UI while deferring the redesign to backlog ticket `ATT-1742`.

### 1.2 Functional & Architectural Requirements
1. **Removal of `LapSplitChartCard` from `TrackOnMapScreen`**:
   - `LapSplitChartCard` SHALL be completely removed from `TrackOnMapScreen`'s default `analyticsContent`.
   - All state and calculation wiring for lap selection (`selectedLapNr`, `splitChartData`, `lapSegment`) SHALL be removed.
   - Map rendering SHALL render standard track polylines and markers without lap segment highlighting.
2. **Removal of `LapSplitChart` from `WorkoutLaps`**:
   - `LapSplitChart` SHALL be removed from above the laps table in `WorkoutLaps.kt`.
   - The table view SHALL render directly beneath the section header without displaying the split chart.
3. **Preservation of Core Data & Domain Structures**:
   - Lap storage in SQLite, lap editing via `LapEditBottomSheet`, and domain models/calculations (`LapSplitModels.kt`, `LapSplitCalculator.kt`, `LapSegmentUtils.kt`) SHALL be preserved.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**:
  - `REQ-UI-204` (*Aftermath: Compact Lap & Interval Split Chart Architecture*).
* **Historical Origin & Commit Trace**:
  - Ticket `ATT-1392`, Sprint `2026-40.5`. Refined/reverted in ticket `ATT-1741`, Sprint `2026-40.6`.
* **Root Reason for Existing Formulation**:
  - Originally introduced visual bar charts in Aftermath and WorkoutLaps. The human user reviewed the result on-device and mandated reverting it ("looks really bad... create one ticket to revert this and one ticket to make this much more better") so that a higher-aesthetic redesign can be refined outside active sprints (ATT-1742).
* **Preservation of Core Invariants**:
  - SQLite sample and lap storage, tabular lap inspection and editing in `WorkoutLaps`, map rendering in `MapDetailLayout`, and single-thread SQLite confinement remain 100% intact.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (TrackOnMapScreen Clean State)**:
  * *Given* a workout with recorded laps viewed in Aftermath (`TrackOnMapScreen`),
  * *When* the screen renders,
  * *Then* `LapSplitChartCard` SHALL NOT be displayed in `analyticsContent`, and the map SHALL render standard course tracks without lap highlighting.
* **Criterion 2 (WorkoutLaps Clean Table State)**:
  * *Given* a workout with recorded laps viewed in `WorkoutLaps`,
  * *When* the view renders,
  * *Then* `LapSplitChart` SHALL NOT be displayed above the table, and the standard tabular lap rows SHALL render cleanly.
* **Criterion 3 (Underlying Table Operations Intact)**:
  * *Given* an athlete viewing `WorkoutLaps`,
  * *When* tapping a lap row to edit or delete,
  * *Then* the lap editing sheet and deletion operations SHALL function normally without regression.

### 1.4 System Invariants
1. Single-thread SQLite confinement on `Dispatchers.IO` preserved.
2. Map rendering, track polyline, and elevation profile display continue to function without errors.
3. Zero regression in workout lap list inspection, editing, or deletion.

---

## 2. Test Specification (TST-UI-158)

### Test Case 1: Domain Calculator Tests Preservation (`[TST-UI-158.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitCalculatorTest.kt`
* **Goal**: Verify existing unit tests for `LapSplitCalculator` pass cleanly to safeguard logic for future ATT-1742 redesign.
* **Expected Result**: 100% pass rate across all calculator tests.

### Test Case 2: Clean-Room Full Suite Regression (`[TST-UI-158.2]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-158.1]` | Unit | `LapSplitCalculatorTest` | `REQ-UI-204` | Specified |
| `[TST-UI-158.2]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
