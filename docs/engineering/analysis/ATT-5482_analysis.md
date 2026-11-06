# Stage 1 Analysis: ATT-5482 - Aftermath: Delete / Merge Lap (Remove Lap Boundary and Merge Adjacent Laps)

**Ticket**: [ATT-5482](https://rainerblind.atlassian.net/browse/ATT-5482)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5482`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
Complementing *Create Lap on Map* (ATT-907, `REQ-UI-449`) and *Move Lap Boundary on Map* (ATT-908, `REQ-UI-450`), athletes currently have no mechanism in Aftermath (`WorkoutLaps`, `LapEditBottomSheet`, `TrackOnMapScreen`) to remove an erroneous, accidental, or unwanted lap boundary once recorded. When a workout has $N \ge 2$ laps, athletes cannot consolidate split intervals back into a unified lap, leaving post-ride analysis fragmented and forcing athletes to live with unintended splits.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Database Management Gap**: `WorkoutSamplesDatabaseManager` provides `incrementLapNumbersFromSample` (ATT-907) and `updateLapNumberForSampleRange` (ATT-908), but lacks an atomic decrement operation to collapse a boundary and shift subsequent sample lap numbers down by 1.
* **Domain Calculation Gap**: `LapSplitAndMoveCalculator` handles splitting and boundary shifts, but lacks merge preview calculation and lap summary consolidation logic (`recalculateLapsAfterMerge`).
* **UI Entry Point Missing**: `LapEditBottomSheet` exposes `onMoveBoundaryClick` when `totalLaps >= 2`, but has no Delete / Merge Lap action or direction selection confirmation dialog.
* **Independent Peer Review & Verification**:
  * Audited `WorkoutSamplesDatabaseManager.java`: Executing parameterized SQL `UPDATE <table> SET LAP_NR = LAP_NR - 1 WHERE LAP_NR > ?` with `? = removedBoundaryLapNr` in an atomic SQLite transaction decrements all samples after the boundary by 1. This merges Lap $k+1$ into Lap $k$ and renumbers subsequent samples $k+2 \dots N \rightarrow k+1 \dots N-1$, maintaining strictly contiguous $1 \dots N-1$ numbering with zero sample gaps.
  * Audited `WorkoutRepository.kt`: Follows the proven transactional pattern of `splitLapAtSample` (L1816–1880) and `moveLapBoundary` (L1890–1950) by invoking sample renumbering, recomputing lap summaries, persisting atomically via `LapsDatabaseManager.replaceCroppedLaps`, invalidating export caches, and reloading `WorkoutData`.
  * Audited Option A UI Strategy: For interior laps ($1 < k < N$), toggling between previous ($k-1 + k$, boundary $k-1$) and next ($k + k+1$, boundary $k$) using `SingleChoiceSegmentedButtonRow` strictly complies with `docs/design_guidelines.md` Section 1.1 for binary mutually exclusive choices.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  * Add transactional `decrementLapNumbersAbove(fileBaseName, boundaryLapNr)` in `WorkoutSamplesDatabaseManager.java`.
  * Extend `LapSplitAndMoveCalculator.kt` with `calculateMergePreview` and `recalculateLapsAfterMerge` (preserving/combining non-empty custom lap names and descriptions).
  * Implement `WorkoutRepository.deleteLap(workoutId, removedBoundaryLapNr)` coordinating sample updates, `replaceCroppedLaps`, export cache refresh, and reactive `WorkoutData` reload.
  * Expose "Delete / Merge Lap" CTA in `LapEditBottomSheet.kt` when `totalLaps >= 2`.
  * Implement `DeleteLapConfirmationDialog.kt` supporting Option A (fixed boundary for boundary laps $k=1$ and $k=N$; `SingleChoiceSegmentedButtonRow` for interior laps $1 < k < N$; live preview of duration, distance, pace/speed).
  * Maintain 100% 9-language translation parity (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
* **Out-of-Scope Non-Goals**:
  * Merging non-adjacent laps ($k \pm 2$).
  * Deleting the sole remaining lap when $N = 1$ (minimum 1 lap invariant per `REQ-TRK-002`).
  * Polyline sample trimming (governed by Crop Workout `REQ-UI-368`).
* **Complexity Rating**: **3 SP (Complex)** (Cross-database transactions in `WorkoutSamples.db` and `Laps.db`, reactive Compose dialog with live preview math, full 9-locale localization parity).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-UI-454` (*Aftermath Delete / Merge Lap and Adjacent Lap Consolidation*), extending `REQ-UI-449` (ATT-907), `REQ-UI-450` (ATT-908), `REQ-TRK-002` (Lap lifecycle & phantom lap elimination), and `REQ-UI-142` (Lap details editing). Targets `WorkoutRepository.kt`, `LapSplitAndMoveCalculator.kt`, `WorkoutSamplesDatabaseManager.java`, `LapsDatabaseManager.java`, and `LapEditBottomSheet.kt`.
* **Historical Origin & Commit Trace**: `ATT-907` (`f1663727`, map lap creation), `ATT-908` (Sprint 2026-45.6, boundary moving), `ATT-896` (phantom lap prevention), and `ATT-3274` (transactional sample pruning).
* **Root Reason for Existing Formulation**: Lap boundaries were historically immutable post-workout to prevent database desynchronization. ATT-907 added splitting and ATT-908 boundary shifting; deleting/merging closes the lifecycle gap by allowing boundary removal.
* **Preservation of Core Invariants**:
  1. Zero phantom laps (`REQ-TRK-002`): Merged lap aggregates two non-empty laps, guaranteeing positive duration and distance.
  2. Sequential continuity: Resulting laps strictly contiguous $1 \dots N-1$ with zero gaps in `WorkoutSamples.db` and `Laps.db`.
  3. Session summary invariance: Total workout duration, distance, polyline, and extrema remain 100% immutable.
  4. Minimum lap invariant: A workout must retain at least 1 lap; deletion disallowed when $N < 2$.

---

## 5. Architectural Strategy & High-Level Solution
* **Database Layer**: Add `decrementLapNumbersAbove(fileBaseName, boundaryLapNr)` in `WorkoutSamplesDatabaseManager.java` executing `UPDATE <table> SET LAP_NR = LAP_NR - 1 WHERE LAP_NR > ?` in an atomic SQLite transaction.
* **Domain Layer**: Add `calculateMergePreview` and `recalculateLapsAfterMerge` in `LapSplitAndMoveCalculator.kt` using `CropWorkoutMath.calculateAverageSpeed` and merging metadata.
* **Repository Layer**: Add `WorkoutRepository.deleteLap(workoutId, removedBoundaryLapNr)` coordinating sample updates, `LapsDatabaseManager.replaceCroppedLaps`, export cache refresh, and reactive emission.
* **UI Layer**: Expose Delete/Merge action in `LapEditBottomSheet.kt` when `totalLaps >= 2`. Display `DeleteLapConfirmationDialog.kt` implementing Option A direction toggling (`SingleChoiceSegmentedButtonRow`) and combined lap preview.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero phantom laps, contiguous $1 \dots N-1$ lap sequence, immutable session totals, atomic SQLite rollback, 9-language translation parity.
* **Risk Rating**: **MEDIUM** (Multi-database transaction modifying `WorkoutSamples.db` and `Laps.db`; mitigated by atomic transactions, proven `replaceCroppedLaps` pattern, and bounded preview calculations).
