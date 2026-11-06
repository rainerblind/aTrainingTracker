# Stage 1 Analysis: ATT-5478 — Aftermath Detail View: Prevent Repeated Map Reloads and Tab Jumping When WorkoutData Updates

**Ticket**: [ATT-5478](https://rainerblind.atlassian.net/browse/ATT-5478)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5478`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & User Observation
During Sprint Review 2026-45.6 (ATT-907 / ATT-908), creating/splitting a lap or moving a lap boundary on the *"Runden & Segmente"* tab caused:
1. An unexpected tab jump from *"Runden & Segmente"* (tab index 2) back to *"Karte & Diagramme"* (tab index 1).
2. Continuous UI and map flickering on *"Karte & Diagramme"* as background export workers completed over several seconds.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Broken `alreadyLoaded` Check in `WorkoutSummariesTabbedScreen.kt` (L253–260)**:
   * `LaunchedEffect(selectedWorkoutIdForDetails, selectedWorkoutForDetails)` checks `currentTracks.any { it.id == selectedWorkoutForDetails.id }`.
   * In `TrackOnMapAftermathViewModel.kt` Phase 2 (L145), `fastTrack` is initialized with `id = workoutId`. However, Phase 4 (L208) replaces `tracks` with full-resolution `MapTrack(id = type.ordinal.toLong(), ...)` (`0L..3L`).
   * Once Phase 4 completes, `it.id == selectedWorkoutForDetails.id` evaluates permanently to `false`.
2. **Repeated Re-Triggers on Every `WorkoutData` Emission**:
   * `splitLapAtSample` and `moveLapBoundary` invoke `exportManager.exportWorkout(...)` (spawning ~10 background `ExportWorker` tasks) and `reloadWorkoutData(workoutId)`.
   * Each `ExportWorker` completion broadcasts `ExportStatusChangedBroadcaster` -> `reloadExportStatusesFor`, emitting a fresh `WorkoutData` instance into `workouts`.
   * Because `selectedWorkoutForDetails` is a `LaunchedEffect` key and `alreadyLoaded` is permanently `false`, `loadAftermathData` is restarted 10+ times in rapid succession.
3. **Phase 1 State Wipe Shrinks `visibleTabs` and Clamps `HorizontalPager`**:
   * Phase 1 of `loadAftermathData` (L113–120) resets `tracks = emptyList()` to avoid ghost data across different workouts.
   * In `TrackOnMapScreen.kt` (L140), `hasGpsTrack` drops to `false`, causing `hasMap` in `WorkoutDetailSectionAvailability` to become `false`.
   * *"Karte & Diagramme"* is auto-hidden (`REQ-UI-408.4`), shrinking `visibleTabs` from 3 to 2 tabs.
   * `rememberPagerState(pageCount = { visibleTabs.size })` clamps `currentPage` from 2 (*Runden & Segmente*) down to 1. When Phase 2 re-inserts *"Karte & Diagramme"* at index 1 milliseconds later, the user remains stuck on *"Karte & Diagramme"*.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  * Add explicit `loadedWorkoutId: Long?` to `AftermathMapUIState` and `TrackOnMapAftermathViewModel`.
  * Update `WorkoutSummariesTabbedScreen.kt` to key `LaunchedEffect` on `selectedWorkoutIdForDetails` and check `uiState.loadedWorkoutId == selectedWorkoutIdForDetails`.
  * In `loadAftermathData`, skip Phase 1 state wipe when `loadedWorkoutId == workoutData.id`, preserving tracks and preventing `visibleTabs` tab count shrinkage.
  * Audit downstream consumers to ensure lap and export status updates continue to propagate reactively via Compose state.
* **Out-of-Scope Non-Goals**:
  * Modifying export worker mechanics or disabling export broadcasts.
  * Altering `HorizontalPager` gesture isolation or layout modes (`REQ-UI-408.3`).
* **Complexity Rating**: **2 SP (Standard)** (Targeted lifecycle and state coordination fix).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-427.2` (*Process-Death Detail Restoration*), interacting with `REQ-UI-408.4` (*Auto-Hiding Empty Tabs*). Targets `WorkoutSummariesTabbedScreen.kt` and `TrackOnMapAftermathViewModel.kt`.
* **Historical Origin & Commit Trace**: `ATT-4018` (`145ee346`, process-death restoration check), `e9e54e36` (`MapTrack` ordinal IDs), `ATT-3920` (`REQ-UI-408`, multi-tab pager layout), and `ATT-907`/`ATT-908` (lap editing exports).
* **Root Reason for Existing Formulation**: `ATT-4018` introduced `alreadyLoaded` to restore detail state after process recreation. It assumed `tracks` would retain `workoutId`, unaware that Phase 4 used `type.ordinal.toLong()`, and did not anticipate repeated `WorkoutData` emissions from background export workers.
* **Preservation of Core Invariants**:
  1. Process-death restoration (`REQ-UI-427.2`): Initial entry or process restore still triggers `loadAftermathData`.
  2. Ghost data prevention: Switching to a *different* workout ID still executes Phase 1 wipe.
  3. Reactive metadata update: Laps, photos, and export statuses update in place without reloading map tracks.

---

## 5. Architectural Strategy & High-Level Solution
* **State Flow Tracking**: Expose `loadedWorkoutId: Long?` in `AftermathMapUIState`. Set `loadedWorkoutId = workoutId` in `uiState`.
* **Idempotent Reload Guard**: In `loadAftermathData(workoutData)`, check if `_uiState.value.loadedWorkoutId == workoutData.id`. If true, bypass Phase 1 `tracks = emptyList()` state wipe.
* **Stable Detail Keying**: In `WorkoutSummariesTabbedScreen.kt`, key the restore effect on `selectedWorkoutIdForDetails` and compare `trackOnMapViewModel.uiState.value.loadedWorkoutId != selectedWorkoutIdForDetails`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: 100% test pass rate, zero ghost data when switching workouts, seamless lap updates without tab jumping.
* **Risk Rating**: **LOW** (Defensive guard on existing ViewModel state; zero DB schema or repository changes).
