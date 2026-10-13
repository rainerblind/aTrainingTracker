# Stage 1 Analysis: ATT-2337 - Navigate to Imported Workout in Workouts List upon Successful Import

**Ticket**: [ATT-2337](https://atrainingtracker.atlassian.net/browse/ATT-2337)  
**Sub-task**: [ATT-2520](https://atrainingtracker.atlassian.net/browse/ATT-2520) (`[Analysis]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Branch**: `feature/ATT-2337`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Statement & User Value Proposition

When importing a workout file (TCX, GPX, or FIT) via the Import & Backup tab (`ImportBackupTabsScreen.kt`), the application performs file ingestion, SQLite database insertion (`WorkoutSummaries.TABLE`), period aggregation, and clustering. Upon completion, a top banner (`StateOverlaySection`) renders an elevated card displaying a success confirmation message (e.g., *"Successfully imported workout from TCX file."*) alongside a solitary `[OK]` button (`TextButton(onClick = onClearState)`).

### Identified Pain Points & Friction:
1. **Dead-End Success Banner**:
   - Tapping `[OK]` dismisses the banner but leaves the athlete stranded on the Import & Backup screen.
   - To inspect or verify the imported workout (checking speed curves, heart rate, power, route map, or split laps), the athlete must manually open the navigation drawer, scroll and select "Workouts", switch to the appropriate sport tab (All, Bike, Run, Other), and manually hunt down the imported session in their workout history.
2. **Missing Workout Identification in Import State**:
   - `LegacyImportEngine` already captures the generated SQLite row ID `workoutId = summaryDb.database.insert(...)` across TCX, GPX, and FIT import pathways, but the public entry points (`importFromTcx`, `importFromGpx`, `importFromFit`) discard this ID and return a simple `Boolean` or `ImportStatus`.
   - `BackupRestoreViewModel.UiState.Success` only carries `message: String`, completely erasing the context of which workout was just created.
3. **Absence of Contextual Action in UI**:
   - The athlete is offered no direct action to inspect the newly imported session immediately after import.
   - In batch FIT file imports (`importFitFiles`), the user has no direct path to view the latest imported activity among multiple files.

Providing a direct action affordance (`[Anzeigen]` / `[View]`) alongside `[Schließen]` / `[Dismiss]` that automatically navigates to the Workouts view, selects the correct sport tab, and scrolls directly to the imported workout eliminates cognitive overhead and friction.

---

## 2. Root Cause Analysis (Forensic Investigation & Architecture Gaps)

1. **`LegacyImportEngine.kt` Return Type Truncation**:
   - In `importFromTcxInternal` (line 720), `importFromGpxInternal` (line 1130), and `importFromFitInternal` (line 1480), `workoutId = summaryDb.database.insert(WorkoutSummaries.TABLE, null, summaryValues)` is computed and used for stats recalculation.
   - However, the methods return `ImportStatus.SUCCESS` without propagating `workoutId` to the caller.
   - Public methods `importFromTcx`, `importFromGpx`, `importFromFit` return `Boolean` (`== ImportStatus.SUCCESS`).
2. **`BackupRestoreViewModel.kt` State Representation**:
   - `UiState.Success` is declared as:
     ```kotlin
     data class Success(val message: String) : UiState()
     ```
   - In `importLegacyFile`:
     ```kotlin
     _uiState.value = UiState.Success("Successfully imported workout from ${fileExt.uppercase()} file.")
     ```
   - In `importFitFiles`:
     ```kotlin
     _uiState.value = UiState.Success(resultMsg)
     ```
   - In neither case is any `importedWorkoutId: Long?` captured or communicated to the UI.
3. **`ImportBackupTabsScreen.kt` Overlay Presentation**:
   - `StateOverlaySection` displays:
     ```kotlin
     is BackupRestoreViewModel.UiState.Success -> {
         ElevatedCard(...) {
             Row(...) {
                 Text(text = state.message, modifier = Modifier.weight(1f))
                 TextButton(onClick = onClearState) { Text(stringResource(R.string.OK)) }
             }
         }
     }
     ```
   - There is no callback `onNavigateToWorkout: (Long) -> Unit` passed to `StateOverlaySection` or `ImportBackupTabsScreen`.
4. **Decoupled Workout Navigation (`WorkoutNavigationEvents`)**:
   - `WorkoutNavigationEvents` currently provides `navigateToEdit` and `navigateToCluster`.
   - It lacks an event stream `navigateToWorkout: SharedFlow<Long?>` allowing child composables (like `ImportBackupTabsScreen`) to request navigation to a specific workout ID without tightly coupling to `MainActivityWithNavigation` or `NavController`.
5. **`WorkoutSummariesTabbedScreen.kt` List Focusing**:
   - `WorkoutSummariesTabbedScreen` already contains logic for scrolling to a target file name on intent (`LaunchedEffect(workouts)` lines 101–138), animating `pagerState.animateScrollToPage(pageIndex)` and `listState.animateScrollToItem(itemIndex)`.
   - It needs to be wired to react to `WorkoutNavigationEvents.navigateToWorkout` so that whenever a workout ID is targeted, it automatically switches tabs and animates to the item.

---

## 3. User Scope Grounding (ATT-1250)

### In-Scope Objectives
1. **Engine Result Enrichment (`LegacyImportEngine.kt`)**:
   - Introduce `data class ImportResult(val status: ImportStatus, val workoutId: Long? = null)`.
   - Add/adapt `importFromTcxResult`, `importFromGpxResult`, and `importFromFitResult` returning `ImportResult` with the generated `workoutId`.
   - Preserve existing `importFromTcx(...) : Boolean`, `importFromGpx(...) : Boolean`, `importFromFit(...) : Boolean`, and `importFromFitInternal(...) : ImportStatus` signatures for strict backward compatibility with existing unit tests.
2. **ViewModel State Enhancement (`BackupRestoreViewModel.kt`)**:
   - Update `UiState.Success`:
     ```kotlin
     data class Success(val message: String, val importedWorkoutId: Long? = null) : UiState()
     ```
   - In `importLegacyFile`: store the created `workoutId` in `UiState.Success(message, workoutId)`.
   - In `importFitFiles`: store the latest imported `workoutId` in `UiState.Success(message, latestWorkoutId)`.
3. **Actionable UI Banner (`ImportBackupTabsScreen.kt`)**:
   - When `state.importedWorkoutId != null`, `StateOverlaySection` renders:
     - `[Anzeigen]` / `[View]`: triggers `onNavigateToWorkout(importedWorkoutId)` and clears the overlay state.
     - `[Schließen]` / `[Dismiss]`: clears the overlay state and keeps the athlete on the Import tab.
   - When `state.importedWorkoutId == null` (e.g. database backup operations), retains the standard single `[OK]` button.
4. **Navigation Event Pipeline (`WorkoutNavigationEvents.kt`, `ATrainingTrackerApp.kt`, `WorkoutSummariesTabbedScreen.kt`)**:
   - Add `navigateToWorkout` shared flow in `WorkoutNavigationEvents`.
   - Wire `ImportBackupTabsScreen` in `ATrainingTrackerApp.kt` to trigger navigation to `NavRoutes.WORKOUTS`.
   - In `WorkoutSummariesTabbedScreen.kt`, collect `WorkoutNavigationEvents.navigateToWorkout`:
     - Resolve workout from `workouts` flow.
     - Determine target sport tab (`BIKE -> 1`, `RUN -> 2`, `UNKNOWN -> 3`, `else -> 0`).
     - Animate horizontal pager to target page.
     - Find item index in that tab's workout list and animate list scroll to target item.
     - Consume/reset event via `WorkoutNavigationEvents.resetNavigateToWorkout()`.
5. **100% 9-Language Localization Parity**:
   - Introduce `action_view_workout` (EN: "View", DE: "Anzeigen", ES: "Ver", FR: "Afficher", IT: "Visualizza", JA: "表示", NL: "Bekijken", PL: "Pokaż", PT: "Ver").
   - Introduce `action_dismiss` (EN: "Dismiss", DE: "Schließen", ES: "Cerrar", FR: "Fermer", IT: "Chiudi", JA: "閉じる", NL: "Sluiten", PL: "Zamknij", PT: "Fechar").
6. **Comprehensive Unit & Contract Tests**:
   - Test `BackupRestoreViewModel` populating `importedWorkoutId` on single TCX/GPX/FIT and batch FIT imports.
   - Test `ImportBackupTabsScreenContractTest` verifying action button existence and navigation callback triggering.
   - Test `WorkoutNavigationEventsTest` verifying event dispatch and consumption.
   - Test `9-Language Localization Parity` across all 9 locales.

### Out-of-Scope (Forbidden Scope Creep)
- Modifying full database restore logic (`.attbackup` backup/restore mechanics).
- Altering mathematical clustering algorithms (`WorkoutClusterEngine`).
- Changing existing workout list filtering criteria (Periods, Sports, Tags).

---

## 4. Architectural Invariants & ASPICE Constraints

1. **Zero Backward Compatibility Regressions**:
   - All existing tests invoking `LegacyImportEngine.importFromTcx`, `importFromGpx`, `importFromFit`, and `importFromFitInternal` must compile and pass with zero changes.
2. **Dismiss Sovereignty**:
   - If the athlete taps Dismiss (`Schließen`) or chooses not to navigate, the overlay state is cleared and the athlete remains on the Import tab to perform further imports or backups.
3. **Clean-Room Test Pass Rate**:
   - 100% pass rate must be maintained across all unit and contract tests (`./gradlew testDebugUnitTest`).
4. **Localization Parity**:
   - All newly introduced string keys must be localized with 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, and PT.

---

## 5. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement Target**:
   - Net-new requirement: `REQ-MIG-032` (*Post-Import Direct Navigation and Visual Focus in Workout History*).
   - Test Specification: `TST-MIG-029` (*Post-Import Direct Navigation and Visual Focus in Workout History Verification*).
2. **Historical Origin**:
   - Extends Epic `ATT-281` (*Data Sovereignty & Migration*).
   - Builds on `REQ-MIG-011` (*Tabbed Import & Backup UI*) and `REQ-DAT-019` (*External FIT Workout Importer*).
3. **Preservation of Core Invariants**:
   - Deduplication rules (`REQ-MIG-031`), sparse stream parsing (`REQ-MIG-018`), clustering heuristics (`REQ-MIG-029`), and optional Strava upload (`REQ-MIG-028`) remain untouched.
