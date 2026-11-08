# Stage 5: Walkthrough & Verification - ATT-5886: Persist Deduplicated Exceptions & Diagnostic Dialog

**Ticket**: [ATT-5886](https://atrainingtracker.atlassian.net/browse/ATT-5886)  
**Parent Epic**: [ATT-68](https://atrainingtracker.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*) | **Story Points**: 3 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5886`  
**Requirement Mapping**: `REQ-TRK-053` (`REQ-TRK-053.1`–`REQ-TRK-053.5`) | **Test Mapping**: `TST-TRK-053` (`TST-TRK-053.1`–`TST-TRK-053.6`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Executive Summary & Verification Overview
Persisted caught runtime exceptions during table init, 1-Hz sample recording, and post-workout finalization in `WorkoutSummaries.RECORDING_ERRORS` (`DB_VERSION = 28`) and surfaced an actionable, non-intrusive diagnostic flow (Option C):
* **Bounded Deduplication**: `WorkoutRecordingErrorCollector` deduplicates 1-Hz repeated errors by `(stage, exceptionClass, message)` with timestamps, counts, and an 8 KB payload cap.
* **Database & DTO Parity**: Upgraded `WorkoutSummaries.db` to v28 with symmetrical `onCreate`/`onUpgrade` migration and symmetrical cursor mapping in `WorkoutDataMapper` (`Rule 20`).
* **Actionable Diagnostic Bottom Sheet**: Surfaced via warning badge in `WorkoutHeader`, banner in `WorkoutDetailTabbedContent`, and card in `EditWorkoutScreen`. Provides plain-language explanations (`SQLiteFullException`, `SAMPLE_WRITE`, `FINALIZE`), expandable technical details with clipboard copy, 1-tap `mailto:rainer.blind@gmail.com` bug report, and "Warnung löschen" action.
* **Zero Overhead**: Error indicators and column data remain 100% invisible/null on clean workouts.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-053.1` | `TST-TRK-053.1` | `WorkoutSummariesRealSqliteMigrationTest`, `DatabaseMigrationIndexTest` | **PASSED** | `Verified` |
| `REQ-TRK-053.2` | `TST-TRK-053.2` | `WorkoutDiagnosticCollectorTest` (100 errors $\rightarrow$ 1 deduplicated entry) | **PASSED** | `Verified` |
| `REQ-TRK-053.3` | `TST-TRK-053.3` | `WorkoutDataMapperRecordingErrorsParityTest` (`Rule 20` single vs snapshot) | **PASSED** | `Verified` |
| `REQ-TRK-053.4` | `TST-TRK-053.4` | `WorkoutDiagnosticBottomSheetTest` (categorization, email intent, dismiss) | **PASSED** | `Verified` |
| `REQ-TRK-053.5` | `TST-TRK-053.5` | `TranslationParityTest` (15 keys $\times$ 9 locales, zero inline emojis) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope (Runtime: ~2m)
* **Executed Scope**:
  * `--tests "com.atrainingtracker.trainingtracker.database.*"` (including `WorkoutSummariesRealSqliteMigrationTest`, `DatabaseMigrationIndexTest`)
  * `--tests "com.atrainingtracker.trainingtracker.tracker.*"` (including `WorkoutDiagnosticCollectorTest`)
  * `--tests "com.atrainingtracker.trainingtracker.ui.aftermath.*"` (including `WorkoutDataMapperRecordingErrorsParityTest`)
  * `--tests "com.atrainingtracker.trainingtracker.build.*"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  * `--tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
* **Result**: 100% PASS across targeted database, tracker, aftermath, theme centralization, and localization test suites (1,062 tests).
* **Governance Check**: `python3 tools/verify_requirement_governance.py` $\rightarrow$ `PASS: Living documentation master tables match all 9 domain shards with 100% parity.`
* **Build Verification**: `./gradlew assembleDebug` succeeded with zero compiler errors.
* **On-Device Physical Inspection**: Deferred to Sprint Review (Ceremony 2) because no physical device is attached (`adb devices` is empty; emulator launch strictly prohibited).

---

## 4. Key Invariants & Preserved Behavior
1. **Clean Workouts**: `RECORDING_ERRORS` remains `NULL`; warning badge, detail banner, and edit card remain completely hidden.
2. **Foreground Isolation**: Diagnostic error collection and asynchronous SQLite updates never block 1-Hz telemetry recording.
3. **Symmetrical Evolution**: Upgraded databases from version $\le 27$ to 28 match fresh installations identically (`Rule 64`).
