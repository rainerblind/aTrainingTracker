# Stage 2: Requirement & Test Specification - ATT-5478: Prevent Repeated Map Reloads & Tab Jumping

**Ticket**: [ATT-5478](https://rainerblind.atlassian.net/browse/ATT-5478)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5478`  
**Requirement Mapping**: `REQ-UI-427` (`REQ-UI-427.2`) | **Test Spec ID**: `TST-UI-392` (`TST-UI-392.2`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-UI-427.2`)

### 1.1 Problem Statement & Rationale
In `WorkoutSummariesTabbedScreen.kt`, `alreadyLoaded` checks `currentTracks.any { it.id == selectedWorkoutForDetails.id }`. Because Phase 4 assigns `MapTrack(id = type.ordinal.toLong())` (`0L..3L`), this condition permanently evaluates to false once loaded. Consequently, any subsequent emission of `WorkoutData` for the same workout (e.g. from lap splits/moves or background export worker broadcasts) triggers `loadAftermathData` 10+ times. Phase 1 wipes `tracks = emptyList()`, momentarily collapsing `hasGpsTrack`/`hasMap`, auto-hiding *"Karte & Diagramme"*, shrinking `visibleTabs` from 3 to 2, and clamping `HorizontalPager` from index 2 down to 1 (`Rule 73`). The system SHALL expose `loadedWorkoutId: Long?` in `AftermathMapUIState`, guard `LaunchedEffect` in `WorkoutSummariesTabbedScreen.kt`, and perform in-place updates without Phase 1 track wiping when `loadedWorkoutId == workoutData.id`.

### 1.2 Functional & Architectural Requirements
1. **Explicit Loaded Workout State (`REQ-UI-427.2`)**: `AftermathMapUIState` SHALL include `loadedWorkoutId: Long? = null`, set by `TrackOnMapAftermathViewModel` upon starting/completing workout telemetry loading.
2. **Stable LaunchedEffect Guard (`REQ-UI-427.2`)**: `WorkoutSummariesTabbedScreen.kt` SHALL guard `loadAftermathData` by checking `uiState.loadedWorkoutId != selectedWorkoutIdForDetails`, preventing redundant reloads when updated `WorkoutData` instances are emitted for the already-loaded workout.
3. **In-Place Track Preservation (`REQ-UI-427.2`, `Rule 73`)**: When `loadAftermathData(workoutData)` is called for `loadedWorkoutId == workoutData.id`, it SHALL bypass the Phase 1 state wipe (`tracks = emptyList()`, `telemetryPath = emptyList()`), updating metadata and laps in place and preventing `visibleTabs` collapse or `HorizontalPager` tab clamping. When `loadedWorkoutId != workoutData.id`, Phase 1 state wipe is strictly executed to prevent ghost data.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (In-Place Lap Split/Move Tab Stability)**: *Given* Workout Details open on *"Runden & Segmente"* (tab index 2) for workout $W$, *When* a lap split, lap move, or export worker broadcast emits an updated `WorkoutData` for workout $W$, *Then* map tracks and `telemetryPath` remain populated, `visibleTabs` count remains 3, and `HorizontalPager` remains at index 2 without tab jumping.
* **AC-2 (Ghost Data Prevention)**: *Given* Workout Details open for workout A, *When* the user switches to workout B ($B \neq A$), *Then* Phase 1 state wipe is executed, clearing workout A's tracks and telemetry before loading workout B.
* **AC-3 (Process-Death Detail Restoration)**: *Given* `selectedWorkoutIdForDetails` restored after process death or configuration change, *When* the detail screen opens, *Then* `loadAftermathData` is triggered reactively and loads aftermath data.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-427.2` (*Process-Death Detail Restoration*), interacting with `REQ-UI-408.4` (*Auto-Hiding Empty Tabs*). Targets `TrackOnMapAftermathViewModel.kt` and `WorkoutSummariesTabbedScreen.kt`.
* **Historical Origin & Commit Trace**: `ATT-4018` (`145ee346`, process-death restoration check), `e9e54e36` (`MapTrack` ordinal IDs), `ATT-3920` (`REQ-UI-408`, multi-tab pager layout), and `ATT-907`/`ATT-908` (lap editing exports).
* **Root Reason for Existing Formulation**: `ATT-4018` introduced `alreadyLoaded` to restore detail state after process recreation. It assumed `tracks` retained `workoutId` (broken by Phase 4 ordinal IDs) and did not anticipate repeated `WorkoutData` emissions from background export workers.
* **Preservation of Core Invariants**: Process-death restoration (`REQ-UI-427.2`) is fully preserved; ghost data prevention between different workouts is strictly preserved via Phase 1 wipe; in-place metadata/lap updates for the same workout preserve active map tracks and `HorizontalPager` tab selection without tab jumping (`Rule 73`); 100% clean-room test pass rate.

---

## 2. Test Specification (`TST-UI-392.2`, `TST-UI-392.8`)

### 2.1 Test Cases
1. **TC-1: TrackOnMapAftermathViewModel Loaded Workout ID State (`TST-UI-392.2`)**:
   - *Scope*: ViewModel unit test (`TrackOnMapAftermathViewModelTest.kt`).
   - *Procedure*: Verify `uiState.loadedWorkoutId` matches `workoutData.id` after `loadAftermathData(workoutData)`.
2. **TC-2: In-Place WorkoutData Reload Bypasses Phase 1 Track Wipe (`TST-UI-392.2`)**:
   - *Scope*: ViewModel unit test (`TrackOnMapAftermathViewModelTest.kt`).
   - *Procedure*: Load workout $W$ until tracks are populated. Call `loadAftermathData(updatedW)` with same ID. Verify `uiState.tracks` remains populated throughout and is never wiped to `emptyList()`.
3. **TC-3: Workout Switching Executes Phase 1 Track Wipe (`TST-UI-392.2`)**:
   - *Scope*: ViewModel unit test (`TrackOnMapAftermathViewModelTest.kt`).
   - *Procedure*: Load workout A until tracks are populated. Call `loadAftermathData(workoutB)` with ID $B \neq A$. Verify `uiState.tracks` is cleared in Phase 1 before workout B data loads, preventing ghost data.
4. **TC-4: Tab Stability on Repeated WorkoutData Emissions (`TST-UI-392.2`)**:
   - *Scope*: Compose UI contract test (`WorkoutSummariesTabbedScreenTest.kt`).
   - *Procedure*: Select workout details on tab index 2 (*Runden & Segmente*). Emit updated `WorkoutData` with identical `id`. Verify `visibleTabs` retains *"Karte & Diagramme"* and pager remains on tab index 2.
5. **TC-5: Clean-Room Full Regression Suite (`TST-UI-392.8`)**:
   - *Command*: `./gradlew testDebugUnitTest`
   - *Expected*: 100% pass rate with zero failures.

---

## 3. Traceability Matrix
| Requirement ID | Test Case ID | Scope | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-427.2` | `TST-UI-392.2` (TC-1, TC-2, TC-3) | In-place track preservation & loadedWorkoutId (`TrackOnMapAftermathViewModelTest`) | Planned (`ATT-5478`) |
| `REQ-UI-427.2` | `TST-UI-392.2` (TC-4) | Tab stability & LaunchedEffect guard (`WorkoutSummariesTabbedScreenTest`) | Planned (`ATT-5478`) |
| `REQ-UI-427.6` | `TST-UI-392.8` (TC-5) | Clean-room regression suite | Planned (`ATT-5478`) |
| `REQ-PRO-034` | `TST-PRO-034` (TC-5) | ASPICE SWE Audit (`AspiceSweAuditContractTest`) | Verified |
