# Stage 2: Requirement & Test Specification - ATT-2337: Navigate to imported workout in workouts list upon successful import

**Ticket**: [ATT-2337](https://atrainingtracker.atlassian.net/browse/ATT-2337)  
**Sub-task**: [ATT-2521](https://atrainingtracker.atlassian.net/browse/ATT-2521) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-MIG-032` (*Interactive Post-Import Workout Navigation & Decoupled List Auto-Focus*)  
**Test Spec ID**: `TST-MIG-029` (*Post-Import Workout Navigation & Decoupled List Auto-Focus Verification*)  
**Branch**: `feature/ATT-2337`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Requirement Specification (`REQ-MIG-032`)

### 1.1 Problem Statement & Rationale
When importing a workout file (TCX, GPX, or FIT) via the Import tab in `ImportBackupTabsScreen`, the system displays a top notification banner (`StateOverlaySection`) with a success message (e.g. "Successfully imported workout from TCX file") alongside a generic "OK" button. Tapping "OK" merely clears the banner, leaving the athlete stranded on the Import & Backup tab.
To inspect the imported route, telemetry, or laps, the athlete is forced to open the navigation drawer, switch to the Workouts screen, and hunt for the newly imported entry in their history.
Providing an immediate, actionable navigation trigger (`[Anzeigen]` / `[View]`) alongside a standard dismissal (`[OK]`) and auto-scrolling directly to the imported workout in `WorkoutSummariesTabbedScreen` eliminates workflow friction and provides immediate confirmation.

### 1.2 Functional & Architectural Requirements
The system SHALL provide interactive post-import workout navigation and decoupled list auto-focus upon successful activity file import (ATT-2337):

1. **Non-Breaking Return Types & Import Result Contract (`LegacyImportEngine.kt`)**:
   - The engine SHALL define:
     ```kotlin
     data class ImportResult(
         val status: ImportStatus,
         val workoutId: Long? = null
     )
     ```
   - The engine SHALL expose enriched import methods returning `ImportResult`:
     - `suspend fun importFromTcxResult(context: Context, inputStream: InputStream, uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()): ImportResult`
     - `suspend fun importFromGpxResult(context: Context, inputStream: InputStream, uploadToStrava: Boolean = TrainingApplication.uploadImportedWorkoutsToStrava()): ImportResult`
     - `suspend fun importFromFitResult(context: Context, inputStream: InputStream, fileBaseName: String): ImportResult`
   - Existing methods `importFromTcx`, `importFromGpx`, `importFromFit`, and `importFromFitInternal` returning `Boolean` or `ImportStatus` SHALL be preserved as delegating convenience wrappers returning `result.status == ImportStatus.SUCCESS` or `result.status`, guaranteeing 100% binary and behavioral compatibility for all existing tests and callers.

2. **ViewModel State Enrichment (`BackupRestoreViewModel.kt`)**:
   - `BackupRestoreViewModel.UiState.Success` SHALL be enriched with an optional workout ID:
     ```kotlin
     data class Success(
         val message: String,
         val importedWorkoutId: Long? = null
     ) : UiState()
     ```
   - Upon successful single-file import (`importLegacyFile`), the state SHALL be emitted as `UiState.Success(message, result.workoutId)`.
   - Upon completing a batch FIT import (`importFitFiles`), if at least one workout was successfully imported, the state SHALL be emitted as `UiState.Success(summaryMessage, latestImportedWorkoutId)`. If all files failed or were skipped duplicates, `importedWorkoutId` SHALL be `null`.

3. **Actionable Success Banner & Dismissal Agency (`ImportBackupTabsScreen.kt`)**:
   - In `StateOverlaySection`:
     - When `uiState is BackupRestoreViewModel.UiState.Success`:
       - If `state.importedWorkoutId != null`, the card SHALL render two distinct actions:
         1. Primary Action `[Anzeigen]` (`@string/action_view_workout`): Dismisses the overlay banner and triggers the navigation callback `onNavigateToWorkout(workoutId)`.
         2. Secondary / Dismissal Action `[OK]` (`@string/OK`): Dismisses the overlay banner without navigating, allowing the athlete to remain on the Import & Backup screen to perform further imports or backups.
       - If `state.importedWorkoutId == null` (e.g. database restore, manual backup creation, or zero imported items in batch), the card SHALL render the standard single `[OK]` button.

4. **Decoupled Navigation Event Bus (`WorkoutNavigationEvents.kt`)**:
   - `WorkoutNavigationEvents` SHALL expose:
     ```kotlin
     private val _navigateToWorkout = MutableSharedFlow<Long?>(
         replay = 1,
         onBufferOverflow = BufferOverflow.DROP_OLDEST
     )
     val navigateToWorkout: SharedFlow<Long?> = _navigateToWorkout.asSharedFlow()
     val navigateToWorkoutLiveData: LiveData<Long?> by lazy { _navigateToWorkout.asLiveData() }

     fun triggerNavigateToWorkout(workoutId: Long)
     fun resetNavigateToWorkout()
     ```
   - In `MainActivityWithNavigation.kt`: Observing `navigateToWorkoutLiveData` (when non-null) SHALL trigger drawer navigation to the Workouts list (`R.id.drawer_workouts`) if not already on the workouts screen.

5. **Tab Resolution & Animated Auto-Scroll (`WorkoutSummariesTabbedScreen.kt`)**:
   - `WorkoutSummariesTabbedScreen` SHALL collect `WorkoutNavigationEvents.navigateToWorkout`.
   - When a non-null `targetWorkoutId` is received:
     - The screen SHALL identify the matching workout in `workouts`.
     - The screen SHALL resolve the sport type tab:
       - `BSportType.BIKE` -> Page 1 ("Bike")
       - `BSportType.RUN` -> Page 2 ("Run")
       - `BSportType.UNKNOWN` -> Page 3 ("Other")
       - All other sports -> Page 0 ("All")
     - The screen SHALL execute `pagerState.animateScrollToPage(pageIndex)` and `listState.animateScrollToItem(itemIndex)` to bring the imported workout directly into view.
     - The screen SHALL call `WorkoutNavigationEvents.resetNavigateToWorkout()` after triggering the scroll to prevent re-scrolling on subsequent recompositions.

6. **100% 9-Language Localization Parity**:
   - The newly introduced string resource `action_view_workout` SHALL be translated across all 9 application locales with 100% parity:
     - EN: `View`
     - DE: `Anzeigen`
     - ES: `Ver`
     - FR: `Afficher`
     - IT: `Visualizza`
     - JA: `表示`
     - NL: `Bekijken`
     - PL: `Pokaż`
     - PT: `Visualizar`

---

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement (`REQ-MIG-032`) extending the import and migration subsystem (`REQ-MIG-011`, `REQ-MIG-020`, `REQ-MIG-024`, `REQ-DAT-019`) under Epic `ATT-281` (*Data Sovereignty & Migration*).
2. *Historical Origin & Commit Trace*: Sprint 2026-41.1, Ticket `ATT-2337`. Builds upon the decoupled navigation bus (`WorkoutNavigationEvents.kt`, ATT-503) and auto-scroll pattern (`WorkoutSummariesTabbedScreen.kt`, commit `ceaf4070`).
3. *Root Reason for Existing Formulation*: Originally, `LegacyImportEngine` returned simple booleans indicating whether SQLite insertion succeeded, discarding the generated `rowId` / `workoutId`. The UI banner was purely informative without navigation triggers because the import engine was completely decoupled from the navigation hierarchy.
4. *Preservation of Core Invariants*:
   - Existing caller signatures returning `Boolean` or `ImportStatus` MUST NOT break.
   - Deduplication rules (3-minute tolerance window, `REQ-MIG-031`) MUST NOT be altered.
   - The athlete MUST retain agency to stay on the Import screen via `[OK]`.
   - Thread safety on `Dispatchers.IO` and coroutine mutexes MUST NOT be degraded.

---

### 1.4 Acceptance Criteria (Given-When-Then)

- **AC-1 (Single-File Import Navigation Action)**:
  - *Given* an athlete on the Import & Backup tab who imports a TCX, GPX, or FIT file,
  - *When* the import finishes successfully,
  - *Then* `StateOverlaySection` SHALL display the success message, a `[View]` (`action_view_workout`) action button, and an `[OK]` button.
  - *When* the athlete taps `[View]`,
  - *Then* the banner SHALL dismiss, `WorkoutNavigationEvents.triggerNavigateToWorkout(workoutId)` SHALL fire, the app SHALL switch to the Workouts list, and animate the pager and list to focus on the newly imported workout.

- **AC-2 (Dismiss Without Navigation)**:
  - *Given* an athlete who just imported a workout and sees the success banner,
  - *When* the athlete taps `[OK]`,
  - *Then* the banner SHALL dismiss, NO navigation SHALL occur, and the athlete remains on the Import & Backup tab.

- **AC-3 (Batch FIT Import Navigation)**:
  - *Given* an athlete selecting multiple FIT files for batch import,
  - *When* the batch import finishes with at least one newly imported workout,
  - *Then* `StateOverlaySection` SHALL offer the `[View]` button referencing the latest imported workout.
  - *When* all files in the batch are skipped duplicates or fail,
  - *Then* `StateOverlaySection` SHALL NOT display `[View]`, rendering only `[OK]`.

- **AC-4 (Non-Breaking Return Types & Existing Tests)**:
  - *Given* existing tests asserting `LegacyImportEngine.importFromTcx(...) == true`,
  - *When* executed against the refactored engine,
  - *Then* all tests SHALL compile and pass without modification.

---

## 2. Test Specification (`TST-MIG-029`)

### 2.1 Test Verification Matrix

| Test ID | Test Scope | Verification Method | Target Class / File |
| :--- | :--- | :--- | :--- |
| `TST-MIG-029.1` | Import Engine Result & Workout ID Return | Unit Test | `LegacyImportEngineResultTest.kt` |
| `TST-MIG-029.2` | BackupRestoreViewModel Success State with Workout ID | Unit Test | `BackupRestoreViewModelNavigationTest.kt` |
| `TST-MIG-029.3` | WorkoutNavigationEvents Bus Emission & Reset | Unit Test | `WorkoutNavigationEventsTest.kt` |
| `TST-MIG-029.4` | 9-Language Localization Audit (`action_view_workout`) | Contract Test | `ImportNavigationLocalizationTest.kt` |
| `TST-MIG-029.5` | Clean-Room Full Suite Regression | Full Suite | `./gradlew testDebugUnitTest` |

---

### 2.2 Detailed Test Method Outlines

1. **`testImportFromTcxResult_returnsSuccessAndValidWorkoutId`**:
   - Execute `importFromTcxResult` on a valid TCX stream.
   - Assert `result.status == ImportStatus.SUCCESS`.
   - Assert `result.workoutId != null && result.workoutId > 0`.
   - Verify non-breaking delegate `importFromTcx(...)` returns `true`.

2. **`testImportFromGpxResult_returnsSuccessAndValidWorkoutId`**:
   - Execute `importFromGpxResult` on a valid GPX stream.
   - Assert `result.status == ImportStatus.SUCCESS`.
   - Assert `result.workoutId != null && result.workoutId > 0`.
   - Verify non-breaking delegate `importFromGpx(...)` returns `true`.

3. **`testImportFromFitResult_returnsSuccessAndValidWorkoutId`**:
   - Execute `importFromFitResult` on a valid FIT stream.
   - Assert `result.status == ImportStatus.SUCCESS`.
   - Assert `result.workoutId != null && result.workoutId > 0`.
   - Verify non-breaking delegate `importFromFit(...)` returns `ImportStatus.SUCCESS`.

4. **`testBackupRestoreViewModel_singleImportEmitsSuccessWithWorkoutId`**:
   - Mock/execute `importLegacyFile` with a successful TCX import returning `workoutId = 42L`.
   - Verify `viewModel.uiState.value` is `UiState.Success(..., importedWorkoutId = 42L)`.

5. **`testBackupRestoreViewModel_batchImportEmitsSuccessWithLatestWorkoutId`**:
   - Execute `importFitFiles` with a mix of new and duplicate files.
   - Verify `UiState.Success` contains the `importedWorkoutId` of the last successfully inserted activity.

6. **`testWorkoutNavigationEvents_triggerAndReset`**:
   - Call `WorkoutNavigationEvents.triggerNavigateToWorkout(101L)`.
   - Assert `WorkoutNavigationEvents.navigateToWorkout.first() == 101L`.
   - Call `WorkoutNavigationEvents.resetNavigateToWorkout()`.
   - Assert `WorkoutNavigationEvents.navigateToWorkout.first() == null`.

7. **`testImportNavigationLocalizationParity`**:
   - Inspect all 9 `strings.xml` resource directories (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
   - Assert `action_view_workout` exists, is non-empty, and contains no raw XML entity line breaks.
