# Stage 3: Implementation Plan - ATT-2337: Navigate to imported workout in workouts list upon successful import

**Ticket**: [ATT-2337](https://atrainingtracker.atlassian.net/browse/ATT-2337)  
**Sub-task**: [ATT-2522](https://atrainingtracker.atlassian.net/browse/ATT-2522) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-MIG-032` (*Interactive Post-Import Workout Navigation & Decoupled List Auto-Focus*)  
**Test Mapping**: `TST-MIG-029` (*Post-Import Workout Navigation & Decoupled List Auto-Focus Verification*)  
**Branch**: `feature/ATT-2337`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Description & Background

When importing an activity file (TCX, GPX, or FIT) via the Import tab in `ImportBackupTabsScreen`, the system displays a top notification banner (`StateOverlaySection`) containing a success message (e.g. "Successfully imported workout from TCX file") alongside a generic "OK" button. Tapping "OK" merely dismisses the banner, leaving the athlete on the Import & Backup tab. To review the imported workout, route map, or sensor streams, the athlete is forced to manually open the navigation drawer, select the Workouts list, and scan through history to locate the new session.

Furthermore, `LegacyImportEngine` previously returned simple booleans indicating success, discarding the generated SQLite row ID `workoutId`.

Providing an actionable `[Anzeigen]` (`action_view_workout`) action alongside the standard `[OK]` dismissal and dispatching navigation through the decoupled `WorkoutNavigationEvents` bus allows the application to smoothly transition to the Workouts list, switch to the correct sport tab (`BIKE`, `RUN`, `UNKNOWN`, `All`), and scroll the list directly to the imported workout.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-MIG-032` (*Interactive Post-Import Workout Navigation & Decoupled List Auto-Focus*)
  * Refines and extends `REQ-MIG-011` (*Tabbed Import & Backup UI*), `REQ-MIG-020` (*Positive Import Branding*), `REQ-MIG-024` (*High-Fidelity TCX Telemetry*), and `REQ-DAT-019` (*External FIT Workout Importer*).
  * Enriched import result contract with non-breaking backwards compatibility in `LegacyImportEngine.kt`.
  * ViewModel state enrichment in `BackupRestoreViewModel.kt`.
  * Dual-action success card (`[Anzeigen]` and `[OK]`) in `ImportBackupTabsScreen.kt`.
  * Decoupled navigation bus emission in `WorkoutNavigationEvents.kt`.
  * Drawer and fragment routing in `MainActivityWithNavigation.kt`.
  * Tab page resolution and animated list auto-scroll in `WorkoutSummariesTabbedScreen.kt`.
  * 100% 9-Language Localization Parity for `action_view_workout`.
* **Test Mapping**: `TST-MIG-029` (*Post-Import Workout Navigation & Decoupled List Auto-Focus Verification*)
  * `TST-MIG-029.1`: Unit tests for `ImportResult` return and backwards-compatible delegates (`LegacyImportEngineResultTest.kt`).
  * `TST-MIG-029.2`: ViewModel unit tests verifying `UiState.Success` carries `importedWorkoutId` for single and batch imports (`BackupRestoreViewModelNavigationTest.kt`).
  * `TST-MIG-029.3`: Unit tests for `WorkoutNavigationEvents` emission and reset lifecycle (`WorkoutNavigationEventsTest.kt`).
  * `TST-MIG-029.4`: Contract test verifying translation parity for `action_view_workout` across all 9 locales (`ImportNavigationLocalizationTest.kt`).
  * `TST-MIG-029.5`: Clean-room full suite regression execution (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Non-Breaking Import Signatures Invariant**: Existing callers and test cases asserting boolean returns directly from `LegacyImportEngine.importFromTcx(...)` or `importFromGpx(...)` MUST NOT break. The existing methods remain as delegating convenience wrappers.
2. **Athlete Sovereignty & Dismissal Agency**: The athlete MUST NOT be forcibly navigated away without consent. Tapping `[OK]` dismisses the success banner without navigation, preserving the ability to perform further imports or backups.
3. **Deduplication Invariant**: Multi-dimensional deduplication and 3-minute same-sport start time windows (`REQ-MIG-031`) remain strictly preserved.
4. **Thread Safety & Mutex Guarding**: SQLite insertion operations remain enclosed within `importMutex.withLock` on `Dispatchers.IO`.
5. **Human Gate Invariant**: Subtask [ATT-2522](https://atrainingtracker.atlassian.net/browse/ATT-2522) transitions directly to `Erledigt` upon passing Gate 3 audit via `freigabe`. Parent ticket [ATT-2337](https://atrainingtracker.atlassian.net/browse/ATT-2337) stops at `Final Review (Human)`.

---

## 4. UI Consistency (Rule 23 / Design Guidelines Section 5)

* **Closest Existing Reference Screen**: `ImportBackupTabsScreen.kt` (`StateOverlaySection`), lines 886–896.
* **Reused Components**:
  * `ElevatedCard` with `CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)`.
  * `TextButton` from Material 3.
  * Standard horizontal padding (`16.dp`), inner element spacing (`8.dp`).
  * Typography: `MaterialTheme.typography.bodyMedium`.
* **Theme Tokens**:
  * Shape: `RoundedCornerShape(12.dp)` / `CardDefaults.shape`.
  * Color: `MaterialTheme.colorScheme.primaryContainer` and `onPrimaryContainer`.
  * Spacing Scale: `8.dp`, `16.dp` exclusively.
* **Justification for Any New One-Off Styles**: None. All controls strictly reuse established Material 3 primitives and existing strings (`R.string.OK`, `R.string.action_view_workout`).

---

## 5. Proposed Architectural Changes (SWE.2 Architecture)

```
┌────────────────────────────────────────────────────────┐
│ UI Layer: ImportBackupTabsScreen (StateOverlaySection) │
│ - Displays [Anzeigen] (action_view_workout) & [OK]     │
│ - Tapping [Anzeigen] invokes onNavigateToWorkout(id)   │
│ - Tapping [OK] dismisses overlay without navigation    │
└──────────────────────────┬─────────────────────────────┘
                           │ triggers onNavigateToWorkout
┌──────────────────────────▼─────────────────────────────┐
│ Decoupled Event Bus: WorkoutNavigationEvents           │
│ - triggerNavigateToWorkout(workoutId: Long)            │
│ - Exposes navigateToWorkout SharedFlow & LiveData      │
└──────────────┬───────────────────────────┬─────────────┘
               │ observes                  │ collects
┌──────────────▼─────────────┐ ┌───────────▼─────────────┐
│ Activity / Drawer Router   │ │ WorkoutSummariesTabbed  │
│ MainActivityWithNavigation │ │ - Resolves Sport Tab    │
│ - Navigates drawer to      │ │   (Bike/Run/Other/All)  │
│   R.id.drawer_workouts     │ │ - Pager & List auto-    │
│   (if not already active)  │ │   scrolls to workoutId  │
└────────────────────────────┘ └─────────────────────────┘
```

---

## 6. Step-by-Step Implementation Sequence

### Step 1: 9-Language Localization Parity
* Add string resource `action_view_workout` across all 9 `strings.xml` files:
  * `values/strings.xml`: `View`
  * `values-de/strings.xml`: `Anzeigen`
  * `values-es/strings.xml`: `Ver`
  * `values-fr/strings.xml`: `Afficher`
  * `values-it/strings.xml`: `Visualizza`
  * `values-ja/strings.xml`: `表示`
  * `values-nl/strings.xml`: `Bekijken`
  * `values-pl/strings.xml`: `Pokaż`
  * `values-pt/strings.xml`: `Visualizar`

### Step 2: Enriched Import Result Contract in `LegacyImportEngine.kt`
* Define:
  ```kotlin
  data class ImportResult(
      val status: ImportStatus,
      val workoutId: Long? = null
  )
  ```
* Implement:
  * `suspend fun importFromTcxResult(context: Context, inputStream: InputStream, uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()): ImportResult`
  * `suspend fun importFromGpxResult(context: Context, inputStream: InputStream, uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()): ImportResult`
  * `suspend fun importFromFitResult(context: Context, inputStream: InputStream, fileBaseName: String): ImportResult`
* Preserve backwards-compatible delegates:
  * `importFromTcx(...)`: calls `importFromTcxResult(...)` and returns `result.status == ImportStatus.SUCCESS`
  * `importFromGpx(...)`: calls `importFromGpxResult(...)` and returns `result.status == ImportStatus.SUCCESS`
  * `importFromFit(...)`: calls `importFromFitResult(...)` and returns `result.status`

### Step 3: ViewModel Success State Enrichment in `BackupRestoreViewModel.kt`
* Update `UiState.Success`:
  ```kotlin
  data class Success(val message: String, val importedWorkoutId: Long? = null) : UiState()
  ```
* In `importLegacyFile`:
  * Use `importFromTcxResult` or `importFromGpxResult`.
  * Emit `UiState.Success(msg, result.workoutId)` on success.
* In `importFitFiles`:
  * Use `importFromFitResult`.
  * Track `lastImportedWorkoutId`.
  * Emit `UiState.Success(summary, lastImportedWorkoutId)` on completion if `successCount > 0`.

### Step 4: Actionable Success Banner in `ImportBackupTabsScreen.kt`
* In `ImportBackupTabsScreen`:
  * Add parameter `onNavigateToWorkout: ((Long) -> Unit)? = null` with default delegating to `WorkoutNavigationEvents.triggerNavigateToWorkout(it)`.
* In `StateOverlaySection`:
  * When `uiState is BackupRestoreViewModel.UiState.Success`:
    ```kotlin
    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = state.message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (state.importedWorkoutId != null) {
            TextButton(onClick = {
                val workoutId = state.importedWorkoutId
                onClearState()
                onNavigateToWorkout?.invoke(workoutId)
            }) {
                Text(stringResource(R.string.action_view_workout))
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        TextButton(onClick = onClearState) { Text(stringResource(R.string.OK)) }
    }
    ```

### Step 5: Decoupled Navigation Bus in `WorkoutNavigationEvents.kt`
* Add:
  ```kotlin
  private val _navigateToWorkout = MutableSharedFlow<Long?>(
      replay = 1,
      onBufferOverflow = BufferOverflow.DROP_OLDEST
  )
  val navigateToWorkout: SharedFlow<Long?> = _navigateToWorkout.asSharedFlow()

  @get:JvmStatic
  val navigateToWorkoutLiveData: LiveData<Long?> by lazy { _navigateToWorkout.asLiveData() }

  @JvmStatic
  fun triggerNavigateToWorkout(workoutId: Long) {
      _navigateToWorkout.tryEmit(workoutId)
  }

  @JvmStatic
  fun resetNavigateToWorkout() {
      _navigateToWorkout.tryEmit(null)
  }
  ```

### Step 6: Navigation Drawer Routing in `MainActivityWithNavigation.kt`
* Observe `WorkoutNavigationEvents.navigateToWorkoutLiveData`:
  ```kotlin
  WorkoutNavigationEvents.navigateToWorkoutLiveData.observe(this) { workoutId: Long? ->
      if (workoutId != null) {
          if (mCurrentNavigationItemId != R.id.drawer_workouts) {
              selectNavigationDrawerItem(R.id.drawer_workouts)
          }
      }
  }
  ```

### Step 7: Tab Resolution & Animated Auto-Scroll in `WorkoutSummariesTabbedScreen.kt`
* Collect `WorkoutNavigationEvents.navigateToWorkout`:
  ```kotlin
  LaunchedEffect(workouts) {
      WorkoutNavigationEvents.navigateToWorkout.collect { targetWorkoutId ->
          if (targetWorkoutId != null && workouts.isNotEmpty()) {
              val workout = workouts.find { it.id == targetWorkoutId }
              if (workout != null) {
                  val pageIndex = when (workout.bSportType) {
                      BSportType.BIKE -> 1
                      BSportType.RUN -> 2
                      BSportType.UNKNOWN -> 3
                      else -> 0
                  }
                  val listState = when (pageIndex) {
                      1 -> bikeListState
                      2 -> runListState
                      3 -> otherListState
                      else -> allListState
                  }
                  val filteredWorkouts = when (pageIndex) {
                      1 -> workouts.filter { it.bSportType == BSportType.BIKE }
                      2 -> workouts.filter { it.bSportType == BSportType.RUN }
                      3 -> workouts.filter { it.bSportType == BSportType.UNKNOWN }
                      else -> workouts
                  }
                  val itemIndex = filteredWorkouts.indexOfFirst { it.id == workout.id }
                  if (itemIndex != -1) {
                      scope.launch {
                          pagerState.animateScrollToPage(pageIndex)
                          listState.animateScrollToItem(itemIndex)
                      }
                  }
                  WorkoutNavigationEvents.resetNavigateToWorkout()
              }
          }
      }
  }
  ```

### Step 8: Targeted Unit & Contract Tests
* Create `LegacyImportEngineResultTest.kt` verifying `ImportResult` data structure and backward-compatible delegate returns.
* Create `BackupRestoreViewModelNavigationTest.kt` verifying `UiState.Success` emission with `importedWorkoutId`.
* Create `WorkoutNavigationEventsTest.kt` verifying event bus emission and reset lifecycle.
* Create `ImportNavigationLocalizationTest.kt` verifying `action_view_workout` in all 9 locales.
* Execute targeted tests with `./gradlew testDebugUnitTest --tests ...` (`BypassSandbox: true`).

### Step 9: Clean-Room Full Suite Regression Execution
* Run `./gradlew testDebugUnitTest` across all modules (`BypassSandbox: true`).
