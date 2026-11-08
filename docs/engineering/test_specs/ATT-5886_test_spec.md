# Stage 2: Requirement & Test Specification - ATT-5886: Persist Deduplicated Recording Exceptions & Surface Diagnostic Dialog

**Ticket**: [ATT-5886](https://atrainingtracker.atlassian.net/browse/ATT-5886)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-TRK-053` (*Persisted Deduplicated Workout Diagnostic Errors & Actionable Report Dialog*)  
**Test Spec ID**: `TST-TRK-053`  
**Branch**: `feature/ATT-5886`  
**Author**: AI Agent 1 (`subagent_flash`)  
**Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-TRK-053)

### 1.1 Problem Statement & Rationale
Runtime exceptions in `TrackerService.java` during table init, 1-Hz sample recording, and post-workout finalization are caught to prevent foreground crashes, but logged only to volatile Logcat. Outdoors, the ringbuffer rolls over within minutes, causing silent loss of diagnostic information. The system SHALL persist a compact, deduplicated diagnostic log in `WorkoutSummaries.RECORDING_ERRORS` (`DB_VERSION = 28`), maintain zero overhead when clean, and surface actionable diagnostics (plain explanation, technical details, 1-tap email report to Rainer, and dismiss action).

### 1.2 Functional & Architectural Requirements
1. *Database Schema & Helper (`REQ-TRK-053.1`)*: Increment `WorkoutSummariesDbHelper.DB_VERSION` to `28`. Add nullable `TEXT RECORDING_ERRORS` symmetrically in `onCreate` and `onUpgrade` (`Rule 64`). Update `DatabaseSchemaRegistry` (`Rule 62`). Provide `clearRecordingErrors(workoutId)` in DB manager.
2. *Bounded Collector & 1-Hz Deduplication (`REQ-TRK-053.2`)*: Maintain `WorkoutDiagnosticCollector` in `TrackerService` capturing `INIT`, `SAMPLE_WRITE`, and `FINALIZE` errors. Deduplicate by `(stage, exceptionClass, message)` with timestamps, count, and size cap ($\le 8\text{ KB}$).
3. *Symmetrical DTO & Repository Mapping (`REQ-TRK-053.3`)*: Map `recordingErrors` symmetrically across `WorkoutData`, `WorkoutHeaderData`, and `WorkoutDataMapper` (`fromCursor` and `fromSnapshot`, `Rule 20`). Expose `clearRecordingErrors` in `WorkoutRepository`.
4. *Actionable Non-Intrusive UI Flow (`REQ-TRK-053.4`)*: Invisible when clean (Option C). When non-empty: list warning badge, detail warning banner, edit workout card. Tapping opens `WorkoutDiagnosticBottomSheet` with plain explanation (`SQLiteFullException`, `SAMPLE_WRITE`, `FINALIZE`, generic), collapsible technical details + copy button, 1-tap email report (`mailto:rainer.blind@gmail.com`), and clear warning button.
5. *9-Language Localization & Parity (`REQ-TRK-053.5`)*: 15 string resources across all 9 locales with 100% parity and zero inline emojis (`ATT-5400`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (Clean Workout)**: *Given* a workout recorded without exceptions, *when* queried, *then* `RECORDING_ERRORS` is `NULL` and zero warning icons, banners, or cards appear.
* **AC 2 (1-Hz Deduplication)**: *Given* 100 repeated sample insert errors, *when* handled, *then* `TrackerService` records `count = 100`, bounds payload $\le 8\text{ KB}$, and updates `RECORDING_ERRORS`.
* **AC 3 (Lifecycle Coverage)**: *Given* an error during init or finalization, *when* caught, *then* it is appended to `RECORDING_ERRORS`.
* **AC 4 (Diagnostic Dialog & Actions)**: *Given* non-empty `RECORDING_ERRORS`, *when* banner is tapped, *then* bottom sheet presents plain explanation, copyable technical details, 1-tap `mailto:rainer.blind@gmail.com` bug report, and clear warning action that resets `RECORDING_ERRORS` to `NULL`.

### 1.4 System Invariants & Requirement Archaeology
* **Invariants**: Zero UI/DB overhead on clean workouts; non-blocking asynchronous persistence; uncorrupted summary commit invariants; symmetrical cursor mapping (`Rule 20`).
* **Archaeology**: Net-new requirement extending `REQ-TRK-029` (`ATT-3985`), `REQ-TRK-051.2` (`ATT-4968`), and `REQ-REV-003`.

---

## 2. Test Specification (TST-TRK-053)
* **`[TST-TRK-053.1]` Database Schema & Migration Tests**: Schema v28 migration, `clearRecordingErrors`, and `DatabaseSchemaRegistry` validation in `WorkoutSummariesRealSqliteMigrationTest.kt`.
* **`[TST-TRK-053.2]` Diagnostic Collector Unit Tests**: Deduplication of 100 errors (`count = 100`), stage separation, and 8 KB payload bounding in `WorkoutDiagnosticCollectorTest.kt`.
* **`[TST-TRK-053.3]` DTO Mapping Symmetry Tests**: `Rule 20` mapping symmetry in `WorkoutDataMapperTest.kt`.
* **`[TST-TRK-053.4]` Diagnostic Dialog & Action Flow UI Tests**: Category explanation selection, email intent creation, and warning reset in `WorkoutDiagnosticBottomSheetTest.kt`.
* **`[TST-TRK-053.5]` Localization Parity Audit**: All 15 string resources verified across all 9 locales in `TranslationParityTest.kt`.
* **`[TST-TRK-053.6]` Targeted Regression Suite**: 100% pass rate across database, tracker, aftermath UI, and build packages.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-TRK-053.1]` | Database | `WorkoutSummariesDatabaseManager`, `DatabaseSchemaRegistry` | `REQ-TRK-053.1` | Specified |
| `[TST-TRK-053.2]` | Unit | `WorkoutDiagnosticCollector` in `TrackerService` | `REQ-TRK-053.2` | Specified |
| `[TST-TRK-053.3]` | Contract | `WorkoutDataMapper` cursor symmetry | `REQ-TRK-053.3` | Specified |
| `[TST-TRK-053.4]` | UI Contract | `WorkoutDiagnosticBottomSheet`, `WorkoutDetailTabbedContent` | `REQ-TRK-053.4` | Specified |
| `[TST-TRK-053.5]` | Localization | `TranslationParityTest` (15 keys $\times$ 9 locales) | `REQ-TRK-053.5` | Verified |
| `[TST-TRK-053.6]` | Regression | Targeted package test suite | `REQ-PRO-001` | Specified |
