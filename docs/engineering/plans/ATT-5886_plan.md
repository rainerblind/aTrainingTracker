# Stage 3: Implementation Plan - ATT-5886: Persist Deduplicated Exceptions & Diagnostic Dialog

**Ticket**: [ATT-5886](https://atrainingtracker.atlassian.net/browse/ATT-5886)  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5886`  
**Requirement Mapping**: `REQ-TRK-053` | **Test Mapping**: `TST-TRK-053`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-08  

---

## 1. Problem Description & Background
In `TrackerService.java`, caught runtime exceptions during table init, 1-Hz sample writes, and post-workout finalization are logged only to volatile Logcat, which evicts traces during outdoor workouts. Persisting a compact, deduplicated diagnostic log in `WorkoutSummaries.RECORDING_ERRORS` (`DB_VERSION = 28`) and presenting an actionable diagnostic dialog empowers athletes to understand issues, send 1-tap bug reports to Rainer, and dismiss acknowledged warnings.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-053` (*Persisted Deduplicated Workout Diagnostic Errors & Actionable Report Dialog*)
  * `REQ-TRK-053.1`: `WorkoutSummaries` schema migration to `DB_VERSION = 28` (`RECORDING_ERRORS TEXT`, `onCreate`/`onUpgrade` parity, `clearRecordingErrors`).
  * `REQ-TRK-053.2`: `WorkoutRecordingErrorCollector` with 1-Hz deduplication by `(stage, exceptionClass, message)` and 8 KB payload cap.
  * `REQ-TRK-053.3`: Symmetrical DTO & repository mapping (`WorkoutData`, `WorkoutHeaderData`, `WorkoutDataMapper`, `WorkoutRepository`, `Rule 20`).
  * `REQ-TRK-053.4`: Option C UI flow: warning badge in list/header, banner in details, card in edit screen, and `WorkoutDiagnosticBottomSheet`.
  * `REQ-TRK-053.5`: 9-language localization parity with zero inline emojis (`ATT-5400`).
* **Test Mapping**: `TST-TRK-053` (`TST-TRK-053.1` through `TST-TRK-053.6`).

---

## 3. System Invariants & Preserved Behavior
1. **Option C Zero Overhead**: When `recordingErrors` is `NULL` or blank, all warning badges, banners, and cards remain 100% invisible.
2. **Schema & Migration Parity (Rules 62 & 64)**: `onCreate` and `onUpgrade` (v27 -> v28) produce identical schemas; `DatabaseSchemaRegistry` reflects version 28 directly.
3. **DTO Mapping Symmetry (Rule 20)**: `fromCursor(Cursor)` and `readCursorSnapshot(Cursor)` -> `fromSnapshot` map `recordingErrors` identically.
4. **Non-Blocking Fault Isolation**: Error collection and persistence in `TrackerService` never block 1-Hz sensor loops or crash finalization.

---

## 4. Proposed Architectural Changes
* **Database (`WorkoutSummariesDatabaseManager.java`)**: Increment `DB_VERSION` to 28, add `RECORDING_ERRORS` column (`text DEFAULT NULL`) in `onCreate` and `onUpgrade`, add `updateRecordingErrors(workoutId, errorsJson)` and `clearRecordingErrors(workoutId)`.
* **Diagnostic Collector (`WorkoutRecordingErrorCollector.kt`)**: Thread-safe in-memory collector deduplicating errors by `(stage, exceptionClass, message)` with counts, timestamps, stack traces, $\le 8\text{ KB}$ cap, JSON serializer, and category resolver (`STORAGE_FULL`, `SAMPLE_WRITE`, `FINALIZE`, `GENERIC`).
* **Service Integration (`TrackerService.java`)**: Wire `mDiagnosticCollector` into table init (`INIT`), `logSampleInsertFailure` / `handleMissingColumns` (`SAMPLE_WRITE`), and `finalizeLiveSession` (`FINALIZE`), committing payload to `WorkoutSummaries.RECORDING_ERRORS`.
* **Data Flow & Repository (`WorkoutData.kt`, `WorkoutHeaderData.kt`, `WorkoutDataMapper.kt`, `WorkoutRepository.kt`)**: Add `recordingErrors: String?` symmetrically; expose `clearRecordingErrors(workoutId)` in `WorkoutRepository` and `EditWorkoutViewModel`.
* **UI Components (`WorkoutDiagnosticBottomSheet.kt`, `WorkoutHeader.kt`, `WorkoutDetailTabbedContent.kt`, `EditWorkoutScreen.kt`)**:
  * `WorkoutDiagnosticBottomSheet`: Plain explanation card, expandable technical details + Copy button, 1-tap `mailto:rainer.blind@gmail.com` bug report button, and `Warnung löschen` button.
  * Entry points: Warning icon in `WorkoutHeader`, banner in `WorkoutDetailTabbedContent`, warning card in `EditWorkoutScreen`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Database Schema Migration & DatabaseSchemaRegistry Validation
* Files: `WorkoutSummariesDatabaseManager.java`, `DatabaseSchemaRegistry.kt`
* Increment `DB_VERSION = 28`, add `RECORDING_ERRORS` column, and add `clearRecordingErrors` / `updateRecordingErrors`.

### Step 2: Bounded Diagnostic Collector
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/WorkoutRecordingErrorCollector.kt`
* Implement deduplication, $\le 8\text{ KB}$ truncation, JSON serialization, and cause classification.

### Step 3: Wire Collector into TrackerService Lifecycle
* File: `TrackerService.java`
* Route `INIT`, `SAMPLE_WRITE`, and `FINALIZE` catch blocks to `mDiagnosticCollector` and persist to `WorkoutSummaries.RECORDING_ERRORS`.

### Step 4: Symmetrical DTO & Repository Mapping
* Files: `WorkoutData.kt`, `WorkoutHeaderData.kt`, `WorkoutDataMapper.kt`, `WorkoutRepository.kt`, `EditWorkoutViewModel.kt`
* Symmetric cursor mapping (`Rule 20`) and reactive `clearRecordingErrors` method in repository.

### Step 5: Actionable Diagnostic Dialog & UI Entry Points
* Files: `WorkoutDiagnosticBottomSheet.kt`, `WorkoutHeader.kt`, `WorkoutDetailTabbedContent.kt`, `EditWorkoutScreen.kt`
* Implement bottom sheet and wire into header warning badge, detail banner, and edit card.

### Step 6: Automated Test Verification
* Files: `WorkoutSummariesRealSqliteMigrationTest.kt`, `WorkoutDiagnosticCollectorTest.kt`, `WorkoutDataMapperTest.kt`, `WorkoutDiagnosticBottomSheetTest.kt`
* Run targeted suite: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.tracker.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
* Run `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit, migration, DTO symmetry, and UI bottom sheet test suites + living doc governance check.
* **Rollback**: Clean git commit boundary on `feature/ATT-5886` allows instant reversal without affecting other sprint tickets.
