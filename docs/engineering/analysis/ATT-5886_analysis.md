# Stage 1 Analysis: ATT-5886 - Persist Deduplicated Exceptions & Diagnostic Dialog

**Ticket**: [ATT-5886](https://atrainingtracker.atlassian.net/browse/ATT-5886)  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5886`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
* In `TrackerService.java`, runtime exceptions during table init (`mTableInitializationFuture`), 1-Hz sample writes (`logSampleInsertFailure`, `handleMissingColumns`), and post-workout finalization (`finalizeLiveSession`) are caught to prevent foreground crashes.
* Caught errors are currently written only to volatile Android `Log.e` (Logcat). During outdoor workouts without an attached debugger, the Logcat buffer rolls over rapidly, leaving athletes with missing samples and zero actionable diagnostics.
* Athletes need caught exceptions persisted to `WorkoutSummaries.TABLE` (which commits independently from sample insertion) and surfaced via an intuitive diagnostic dialog (plain explanation, 1-tap email report to Rainer, and dismiss/clear action).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: `TrackerService` isolates database failures defensively, but lacks a persistent telemetry buffer tied to the workout record.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Inspected `TrackerService.java:2498-2508`: `logSampleInsertFailure` logs to Logcat with burst rate limiting (`mSampleInsertFailureCount`), but discards structured metadata.
  * Inspected `TrackerService.java:2650-2985`: `finalizeLiveSession` wraps map polyline, extrema, aerobic efficiency, commute/trainer, clustering, and milestone auto-naming in independent `try-catch` blocks that catch `Throwable` and log to Logcat without persisting failure flags.
  * Inspected `WorkoutSummariesDatabaseManager.java:1976-2044`: `DB_VERSION = 27`. Adding nullable `RECORDING_ERRORS text` requires upgrading to `DB_VERSION = 28` with symmetrical `onCreate` and `onUpgrade` (`Rule 64`) and `DatabaseSchemaRegistry` parity (`Rule 62`).
  * Inspected `WorkoutDataMapper.kt:80-200` & `565-630`: To comply with `Rule 20`, `recordingErrors` must be mapped symmetrically across single `fromCursor` and batch `readCursorSnapshot` -> `fromSnapshot`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * DB schema migration: Add `RECORDING_ERRORS` column to `WorkoutSummaries.TABLE` (`DB_VERSION = 28`), migration test in `WorkoutSummariesRealSqliteMigrationTest.kt`.
  * Exception collector: Bounded in-memory deduplication collector in `TrackerService` grouping by `(stage, exceptionClass, message)` with timestamps, counts, and size cap ($\le 8\text{ KB}$).
  * Hook capture points: Table init (`INIT`), 1-Hz writes (`SAMPLE_WRITE`), and `finalizeLiveSession` blocks (`FINALIZE`).
  * Data layer: Symmetrical DTO mapping in `WorkoutData`, `WorkoutHeaderData`, `WorkoutDataMapper`, and `clearRecordingErrors(workoutId)` in DB manager and `WorkoutRepository`.
  * UI layer: Option C (invisible when clean). Warning badge in list/header, banner in `WorkoutDetailTabbedContent`, card in `EditWorkoutScreen`, and `WorkoutDiagnosticBottomSheet` (plain-language explanation, collapsible details + copy, 1-tap `mailto:rainer.blind@gmail.com` bug report, and clear warning button).
* **Out-of-Scope**: Altering sample schema in `WorkoutSamples.db`, modifying export file formats, or altering foreground service lifecycle.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Net-new requirement `REQ-TRK-053` (*Persisted Deduplicated Workout Diagnostic Errors & Actionable Report Dialog*), extending `REQ-TRK-029` (*Sample Insert Failure Telemetry*), `REQ-TRK-051.2` (*Decoupled Summary Persistence*), and `REQ-REV-003` (*Jubilee Action Flow*).
* **Historical Origin & Commit Trace**: `ATT-3985` (`f4650d74`) rate-limited Logcat inserts; `ATT-4968` decoupled summary updates from sample insert failures.
* **Root Reason for Existing Formulation**: Logcat-only logging was lightweight, but proved ineffective for field triage without developer access.
* **Preservation of Core Invariants**: Zero UI clutter for error-free workouts; non-blocking asynchronous persistence; uncorrupted workout summary commit invariants intact.

---

## 5. Architectural Strategy & High-Level Solution
* **Collector**: Introduce `WorkoutDiagnosticCollector` in `TrackerService` tracking `(stage, exceptionClass, message)` with first/last timestamps and count, formatted to JSON/compact text $\le 8\text{ KB}$.
* **Persistence**: Write non-empty payload to `WorkoutSummaries.RECORDING_ERRORS` at new error occurrence and during `finalizeLiveSession`.
* **Symmetry (`Rule 20`)**: Populate `recordingErrors` in `WorkoutData` and `WorkoutHeaderData` via `WorkoutDataMapper.fromCursor` and `readCursorSnapshot`.
* **UI**: Surface `WorkoutDiagnosticBottomSheet` via header warning badge, detail banner, and edit dialog. Provide `FirstJubileeActionHelper`-styled `mailto` intent with device/app metadata and `clearRecordingErrors` callback.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: 100% invisible when `recordingErrors == null`; existing query performance unaffected; zero crash regressions on full/corrupt databases.
* **Risk Rating**: **HIGH** (`TrackerService` foreground recording thread + SQLite schema migration on `WorkoutSummaries.TABLE` to `DB_VERSION = 28`).
