# Stage 3: Implementation Plan - ATT-5478: Prevent Repeated Map Reloads & Tab Jumping

**Ticket**: [ATT-5478](https://rainerblind.atlassian.net/browse/ATT-5478)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5478`  
**Requirement Mapping**: `REQ-UI-427` (`REQ-UI-427.2`), `REQ-UI-408` (`REQ-UI-408.4`)  
**Test Mapping**: `TST-UI-392` (`TST-UI-392.2`, `TST-UI-392.8`), `TST-PRO-034`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
When lap operations (split, move, delete) or background export worker broadcasts emit an updated `WorkoutData` instance, `WorkoutSummariesTabbedScreen.kt` re-triggers `LaunchedEffect`, restarting `loadAftermathData` 10+ times because `alreadyLoaded` permanently evaluates to false once Phase 4 assigns `MapTrack(id = type.ordinal.toLong())`. Phase 1 wipes `tracks = emptyList()`, momentarily collapsing `hasGpsTrack`/`hasMap`, auto-hiding *"Karte & Diagramme"*, and clamping `HorizontalPager` from index 2 down to 1 (`Rule 73`). This plan specifies tracking `loadedWorkoutId` in `AftermathMapUIState`, guarding `LaunchedEffect` in `WorkoutSummariesTabbedScreen.kt`, and preserving tracks in-place during same-workout updates.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-UI-427.2` (Loaded workout tracking, in-place track preservation, and stable process-death restoration), `REQ-UI-408.4` (Auto-hiding empty tabs without disruptive tab jumping).
* **Test Mapping**: `TST-UI-392.2` (`TrackOnMapAftermathViewModelTest`, `WorkoutSummariesTabbedScreenTest`), `TST-UI-392.8` (Clean-room full regression suite), `TST-PRO-034` (ASPICE SWE audit).

---

## 3. System Invariants & Preserved Behavior
1. **Ghost Data Prevention**: Switching to a *different* workout ID ($B \neq A$) strictly executes the Phase 1 state wipe, clearing previous tracks before loading new data.
2. **Process-Death Detail Restoration**: `selectedWorkoutIdForDetails` restored via `rememberSaveable` continues to trigger `loadAftermathData` on initial composition.
3. **Tab Selection Stability (`Rule 73`)**: Emitting in-place `WorkoutData` updates for the loaded workout keeps `tracks` populated, preserving `visibleTabs.size` and keeping `HorizontalPager` on its active tab.
4. **Code Lock (Rule 4)**: Zero production code edits in `app/src/` until Gate 3 passes audit.

---

## 4. Proposed Architectural Changes
### Component 1: `AftermathMapUIState` & `TrackOnMapAftermathViewModel.kt`
* Add `val loadedWorkoutId: Long? = null` to `AftermathMapUIState`.
* In `loadAftermathData(workoutData)`: Check `val isSameWorkout = (_uiState.value.loadedWorkoutId == workoutData.id)`.
  * If `!isSameWorkout`: Execute Phase 1 immediate reset (`tracks = emptyList()`, `markers = emptyList()`, `loadedWorkoutId = workoutData.id`).
  * If `isSameWorkout`: Retain existing `tracks` and `telemetryPath`, setting `loadedWorkoutId = workoutData.id`. In Phase 2, bypass fast-track polyline overwrite if tracks are already populated. Update markers and analytics in-place.

### Component 2: `WorkoutSummariesTabbedScreen.kt` (`ui/aftermath/workoutlist/`)
* Refactor lines 253–261: Key `LaunchedEffect` on `selectedWorkoutIdForDetails` and `selectedWorkoutForDetails != null`.
* Evaluate `alreadyLoaded` as `trackOnMapViewModel.uiState.value.loadedWorkoutId == selectedWorkoutIdForDetails`. Invoke `loadAftermathData` only when `!alreadyLoaded`.

### Component 3: Unit & Contract Tests
* `TrackOnMapAftermathViewModelTest.kt`: Test `loadedWorkoutId` tracking, in-place track preservation for same workout ID, and Phase 1 wipe for different workout ID.
* `WorkoutSummariesTabbedScreenTest.kt`: Test `LaunchedEffect` guard contract and `loadedWorkoutId` check.

### UI Consistency (Rule 23)
* Reused components: `WorkoutDetailTabbedContent`, `HorizontalPager`, `TrackOnMapScreen`. Theme tokens: Existing Material 3 theme. New one-off styles: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Update `AftermathMapUIState` & `TrackOnMapAftermathViewModel.kt` (`REQ-UI-427.2`)
* Add `loadedWorkoutId` to `AftermathMapUIState`. Implement `isSameWorkout` guard in `loadAftermathData` to bypass Phase 1 track wipe and preserve tracks/telemetry on in-place reloads.

### Step 2: Refactor `WorkoutSummariesTabbedScreen.kt` Restoration Guard (`REQ-UI-427.2`)
* Change `alreadyLoaded` check to compare `loadedWorkoutId == selectedWorkoutIdForDetails`, keying `LaunchedEffect(selectedWorkoutIdForDetails, selectedWorkoutForDetails != null)`.

### Step 3: Implement ViewModel & Contract Unit Tests (`TST-UI-392.2`)
* Add TC-1 (`loadedWorkoutId` state), TC-2 (in-place track preservation), and TC-3 (different workout Phase 1 wipe) to `TrackOnMapAftermathViewModelTest.kt`.
* Add TC-4 (architectural contract test for restoration guard) to `WorkoutSummariesTabbedScreenTest.kt`.

### Step 4: Verification Gate & Regression Suite (`TST-UI-392.8`)
* Run targeted unit test suite and governance verification:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: `TrackOnMapAftermathViewModelTest`, `WorkoutSummariesTabbedScreenTest`, and physical Pixel 10 verification (`Rule 71`/`Rule 73`).
* **Rollback**: `git checkout feature/ATT-5478 -- .` cleanly restores previous state.
