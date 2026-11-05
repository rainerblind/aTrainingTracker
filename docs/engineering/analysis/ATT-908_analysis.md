# Stage 1 Analysis: ATT-908 - Aftermath: Move lap based on the map

**Ticket**: [ATT-908](https://rainerblind.atlassian.net/browse/ATT-908)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-908`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-05  

---

## 1. Problem Statement & User Motivation
Athletes often trigger lap splits slightly early or late during a ride/run, or auto-lap thresholds split mid-segment. While ATT-907 enabled splitting a single lap into two, athletes cannot adjust an existing boundary between two adjacent recorded laps ($k$ and $k+1$), requiring a precision tool to shift the boundary on the GPS track without recreating laps from scratch.

---

## 2. Root Cause & Gap Analysis
* **Bounded Sample Shift**: Shifting an existing boundary between Lap $k$ and Lap $k+1$ requires modifying `LAP_NR` only for samples between the current boundary and the desired new boundary ($s_{\text{old}} \dots s_{\text{new}}$). `WorkoutSamplesDatabaseManager` currently only has `incrementLapNumbersFromSample` for unconditional splits.
* **Range Clamping Invariant**: Unlike splitting (which operates within one lap), boundary moving spans two existing adjacent laps. The movable boundary pin must be strictly constrained to $[startIndex(k) + 1, endIndex(k+1) - 1]$ to prevent either lap from collapsing into a zero-duration phantom lap (`REQ-TRK-002`).
* **UI Entry Point Missing**: Neither `LapEditBottomSheet` nor `WorkoutLaps` expose an action to shift an adjacent boundary on the map for workouts with $\ge 2$ laps.

---

## 3. Independent Peer Review & Verification (ATT-907 Synergy)
* Audited `LapSplitAndMoveCalculator.kt`: Specifically designed in ATT-907 to house both split and boundary-move operations. Adding `validateBoundaryMove`, `calculateBoundaryMovePreview`, and `recalculateLapsAfterBoundaryMove` reuses existing pure math and `CropWorkoutMath.calculateAverageSpeed`.
* Audited `WorkoutSamplesDatabaseManager.java`: Adding `updateLapNumberForSampleRange(baseFileName, startRowId, endRowId, newLapNr)` executes a single parameterized SQL `UPDATE <table> SET LAP_NR = ? WHERE _id >= ? AND _id <= ?` within an atomic SQLite transaction.
* Audited `CreateLapOnMapSheet.kt`: Can be paired with or adapted into `MoveLapBoundaryOnMapSheet.kt` (sharing `PathPreviewMap`, slider, and `LapPreviewCard`), pre-seeded with the adjacent boundary index.
* Audited `LapEditBottomSheet.kt`: Adding a "Move Boundary on Map" CTA on Lap $k$ allows direct transition into the map boundary editor.

---

## 4. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  * Add range update method `updateLapNumberForSampleRange` in `WorkoutSamplesDatabaseManager.java`.
  * Implement `WorkoutRepository.moveLapBoundary(workoutId, lapNr, newBoundaryIndex)`.
  * Extend `LapSplitAndMoveCalculator` with boundary move validation and preview calculation.
  * Implement `MoveLapBoundaryOnMapSheet.kt` (`AppModalBottomSheet`) with range slider, map pin, and live 2-lap preview.
  * Add entry point in `LapEditBottomSheet.kt` and `WorkoutLaps.kt` when workout has $\ge 2$ laps.
  * Maintain 100% 9-language translation parity (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
* **Out-of-Scope Non-Goals**:
  * Merging or deleting laps (future enhancement).
  * Boundary shifts crossing non-adjacent laps ($k \pm 2$).
* **Complexity Rating**: **2 SP (Standard)** (Direct extension of ATT-907 architectural primitives).

---

## 5. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-UI-450` (*Interactive Map Lap Boundary Moving in Workout Aftermath*), extending `REQ-UI-449` (ATT-907) and `REQ-TRK-002`. Targets `WorkoutRepository.kt`, `LapSplitAndMoveCalculator.kt`, and `MoveLapBoundaryOnMapSheet.kt`.
* **Historical Origin & Commit Trace**: `ATT-907` (`f1663727`, map lap creation) and `ATT-896` (phantom lap prevention).
* **Root Reason for Existing Formulation**: Lap boundaries were historically static once recorded to prevent desynchronization between samples and lap summary tables.
* **Preservation of Core Invariants**:
  1. No phantom laps: both Lap $k$ and Lap $k+1$ retain positive samples and duration $> 0$ (`REQ-TRK-002`).
  2. Contiguous $1 \dots K$ lap sequence without gaps.
  3. Total workout duration, distance, polyline, and extrema remain completely invariant.

---

## 6. Architectural Strategy & High-Level Solution
* Add `updateLapNumberForSampleRange` in `WorkoutSamplesDatabaseManager.java`.
* Implement `LapSplitAndMoveCalculator.validateBoundaryMove` enforcing $startIndex(k) < s_{\text{new}} \le endIndex(k+1) - 1$.
* Implement `WorkoutRepository.moveLapBoundary` updating samples, replacing laps via `replaceCroppedLaps`, invalidating export cache, and reloading `WorkoutData`.
* Build `MoveLapBoundaryOnMapSheet.kt` using `AppModalBottomSheet` with slider constrained to combined lap range.
* Wire launch CTA into `LapEditBottomSheet.kt` and `WorkoutLaps.kt`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Zero phantom laps, contiguous $1 \dots K$ numbering, unchanged global workout summary, atomic SQLite rollback, 9-language translation parity.
* **Risk Rating**: **LOW** (Builds on proven ATT-907 transactional and mathematical infrastructure).
