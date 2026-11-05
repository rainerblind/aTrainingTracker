# Stage 1 Analysis: ATT-907 - Aftermath: Create lap based on the map

**Ticket**: [ATT-907](https://rainerblind.atlassian.net/browse/ATT-907)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (Laps)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-907`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-05  

---

## 1. Problem Statement & User Motivation
In post-workout Aftermath (`WorkoutDetails` / `TrackOnMapScreen`), athletes can only inspect existing recorded laps or edit textual metadata (`LapEditBottomSheet`). When an athlete forgets to press the lap trigger during an interval, sprint, or terrain transition, no mechanism exists to split an existing lap retroactively along the recorded GPS route, leaving analysis monolithic.

---

## 2. Forensic Root Cause & Gap Analysis
* **Sample Table Isolation**: Telemetry points in `WorkoutSamples.db` track cumulative values and `LAP_NR`, but `WorkoutSamplesDatabaseManager` lacks transactional APIs to increment or reassign `LAP_NR` from an arbitrary sample row ID onwards.
* **Lap Summary Replacement Gap**: `LapsDatabaseManager.replaceCroppedLaps` successfully persists recomputed laps in SQLite transactions for cropped workouts, but no dedicated `WorkoutRepository.splitLapAtSample` method exists to coordinate samples and lap tables.
* **Missing Map Interaction**: Neither `WorkoutLaps` / `LapSplitVisualizerCard` nor the scrubbing toolbar in `MapDetailLayout` provide an action to launch an interactive map lap creation sheet anchored to a chosen sample index.

---

## 3. Independent Peer Review & Verification (ATT-908 Synergy)
* Audited `WorkoutSamplesDatabaseManager.java`: Adding `splitLapAtSampleRow(baseFileName, splitRowId)` executes `UPDATE <table> SET LAP_NR = LAP_NR + 1 WHERE _id >= ?`. If legacy tables lack `LAP_NR`, defensive column auto-creation ensures safe fallback.
* Audited `WorkoutRepository.kt`: Loading points via `getCropSamplePoints(workoutId)` provides sample `rowId`, `distanceTotalM`, `timeTotalS`, and `latLng`. Splitting a target lap at `splitIndex` recalculates `LapData` for Lap $k$ and Lap $k+1$ using `CropWorkoutMath.calculateAverageSpeed`. Calling `replaceCroppedLaps` updates `Laps.db` atomically, followed by `reloadWorkoutData(workoutId)` to reactively notify Compose UI.
* Synergistic Architecture for `ATT-908` (*Move Lap Boundary*): Both ATT-907 (splitting 1 lap into 2) and ATT-908 (shifting boundary between adjacent laps $k$ and $k+1$) share the exact same preview math (`LapEditorPreviewMath`), sample model, and bottom-sheet UI pattern (`CreateLapOnMapSheet`).

---

## 4. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  * Implement `WorkoutSamplesDatabaseManager.splitLapAtSampleRow`.
  * Implement `WorkoutRepository.splitLapAtSample` (with transactional rollback and export cache invalidation).
  * Build interactive `CreateLapOnMapSheet` (`AppModalBottomSheet`) with interactive map track, lap markers, movable split pin, distance slider, and live metric preview of Lap $k$ & $k+1$.
  * Provide entry triggers in `WorkoutLaps` / `LapSplitVisualizerCard` and `MapDetailLayout` scrubbing overlay.
  * Maintain 100% 9-language translation parity (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
* **Out-of-Scope Non-Goals**:
  * Moving existing lap boundaries (strictly reserved for `ATT-908`).
  * Lap deletion or merging (future backlog tickets).
* **Complexity Rating**: **3 SP (Complex)** (Cross-DB transactions, reactive UI updates, Compose map overlay).

---

## 5. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-UI-449` (*Interactive Map Lap Creation & Splitting in Workout Aftermath*), extending `REQ-TRK-002` (Lap lifecycle & phantom lap elimination), `REQ-UI-368` (Workout cropping & sample pruning), and `REQ-UI-204` (Lap split visualizer).
* **Historical Origin & Commit Trace**: `ATT-896` (Sprint 2026-44.1, phantom lap elimination) and `ATT-3274` (Sprint 2026-42.3, transactional workout cropping).
* **Root Reason for Existing Formulation**: Previously, laps were generated exclusively in-ride via `TrackerService` broadcasts to prevent retrospective database desynchronization.
* **Preservation of Core Invariants**:
  1. No phantom zero-duration laps: resulting split laps MUST have duration $> 0$ or distance $> 0.0$ (`REQ-TRK-002`).
  2. Sequential continuity: `LAP_NR` remains strictly contiguous $1 \dots K$ with zero gaps in `Laps.db` and `WorkoutSamples.db`.
  3. Workout summary invariance: Total workout duration, distance, active time, polyline, and extrema remain unchanged.

---

## 6. Architectural Strategy & High-Level Solution
* Create domain helper `LapEditorMath` for validating split bounds ($\text{start}_k < \text{splitIndex} \le \text{end}_k$) and computing preview lap metrics.
* Add transactional `splitLapAtSampleRow` in `WorkoutSamplesDatabaseManager.java`.
* Implement `WorkoutRepository.splitLapAtSample(workoutId, splitIndex)` coordinating sample updates, `replaceCroppedLaps`, and cache invalidation.
* Implement `CreateLapOnMapSheet.kt` (`AppModalBottomSheet`) embedding track map, split pin, slider, and preview cards.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Zero phantom laps, contiguous $1 \dots K$ lap sequence, immutable total workout summary metrics, atomic rollback on SQLite failure, 9-locale parity.
* **Risk Rating**: **MEDIUM** (Multi-database transaction modifying `WorkoutSamples.db` and `Laps.db`; mitigated by strict index bounds validation and atomic transactions).
