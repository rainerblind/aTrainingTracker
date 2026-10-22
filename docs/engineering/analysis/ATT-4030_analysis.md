# Stage 1 Analysis: ATT-4030 - Improve exception handling hygiene across main sources

**Ticket**: [ATT-4030](https://rainerblind.atlassian.net/browse/ATT-4030)  
**Sub-task**: [ATT-4647](https://rainerblind.atlassian.net/browse/ATT-4647) (`[Analysis]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `4.9.40`  
**Active Sprint**: `2026-43.6`  
**Branch**: `feature/ATT-4030`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-22  

---

## 1. Problem Statement & Motivation
An independent whole-app code review identified anti-patterns in exception handling across main sources:
1. **Silent/Empty Catches**: Exceptions swallowed without logging (e.g. FIT date/distance parsing in `FitFileWriter`, Strava date parsing in `StravaUploader`, and DB connection cleanup).
2. **`catch (Throwable)` Pitfalls**: Catching `Throwable` in `PeriodsRepository`, `FitFileWriter`, and `MigrationEngine`, which dangerously catches and suppresses fatal JVM errors (e.g. `OutOfMemoryError`, `VirtualMachineError`) and coroutine cancellation.
3. **`printStackTrace()` Residue**: Two legacy `e.printStackTrace()` calls in `MyBanalDebugHelper.java` violating Android logging standards.
4. **Unsafe Null Assertions**: Force-unwrap (`!!`) in `ForkNavigationRepository.kt` on mutable state.

---

## 2. Root Cause Analysis & Call Site Audit

| Component / File | Findings & Root Cause | Remediation Strategy | Affected Call Sites |
|---|---|---|---|
| `FitFileWriter.java` | Empty catches at L131, L331; `catch (Throwable)` at L457, L471. | Log warnings on parse failures (`DateTimeParseException`, `NumberFormatException`); replace `Throwable` with `Exception`. | `FitFileWriter.java:131,331,457,471` |
| `PeriodsRepository.kt` | `catch (Throwable)` at L114, L123 (test resets) and L825, L831 (DB queries hiding OOM). | Replace `Throwable` with `Exception`; log warnings for equipment/known-locations query failures. | `PeriodsRepository.kt:114,123,825,831` |
| `StravaUploader.kt` | Silent `catch (_: Exception)` at L800, L809, L818, L826 during date parsing; unshielded `sleepStrategy`. | Narrow to `DateTimeParseException` / `ParseException`; ensure `InterruptedException` preserves thread interrupt flag. | `StravaUploader.kt:52,800,809,818,826` |
| `MyBanalDebugHelper.java` | Legacy `e.printStackTrace()` at L54, L68 on `IOException`. | Replace with standard `Log.e(TAG, "...", e)`. | `MyBanalDebugHelper.java:54,68` |
| `ForkNavigationRepository.kt` | Unsafe `!!` on `lastSearchPos` at L121, L122. | Use safe local variable binding (`val pos = lastSearchPos; if (pos != null)`). | `ForkNavigationRepository.kt:121,122` |
| `WorkoutClusterDatabaseManager.kt` | Silent `catch (_: Exception)` in JSON deserialization at L190. | Narrow to `JSONException` and log warning before returning `emptyList()`. | `WorkoutClusterDatabaseManager.kt:190` |
| `MigrationEngine.kt` / `BackupManager.kt` | Silent cleanup catches and `catch (Throwable)` at `MigrationEngine:108`. | Replace `Throwable` with `Exception`; log warnings on close/release failures. | `MigrationEngine.kt:108`, `BackupManager.kt:43,78` |

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Eliminate empty silent catches across audited export, repository, database, and migration call sites.
  * Eliminate `catch (Throwable)` across main sources, preserving JVM error escalation.
  * Replace `printStackTrace()` with `Log.e` in `MyBanalDebugHelper`.
  * Replace unsafe `!!` on `lastSearchPos` in `ForkNavigationRepository`.
  * Add unit tests verifying exception logging hygiene and thread interrupt preservation.
* **Out-of-Scope Non-Goals**:
  * Do NOT refactor all 429 catches across third-party/legacy modules in a single ticket (bounds to audited critical paths).
  * Do NOT alter database query schemas or business logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **REQ-CLN-001** (*Exception Handling Hygiene, Fatal Error Propagation & Thread Interrupt Preservation*): Net-new requirement establishing zero silent catches in export/persistence, strict prohibition of `catch (Throwable)` on recoverable logic, and thread interrupt preservation on `InterruptedException`.
* **Chesterton's Fence Invariant**: Fallback sentinels (e.g. `emptyList()`, `null`, `emptyMap()`) on non-fatal query/parse errors must be strictly preserved so UI rendering and background sync do not regress into user-facing crashes.

---

## 5. Architectural Strategy & High-Level Solution
1. **Targeted Hardening**: Apply specific exception types (`DateTimeParseException`, `JSONException`, `IOException`) and replace empty blocks with diagnostic `Log.w` / `Log.e`.
2. **Fatal JVM Error Safety**: Downgrade all `catch (Throwable)` to `catch (Exception)` so `OutOfMemoryError` and VM errors bubble up naturally.
3. **Interrupt Hygiene**: In `StravaUploader.sleepStrategy`, re-assert `Thread.currentThread().interrupt()` upon catching `InterruptedException`.
4. **Regression Immunity**: Validate with targeted unit tests in `ExceptionHandlingHygieneTest.kt`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Graceful degradation: Corrupt dates or missing secondary DB tables continue to return safe defaults without aborting exports.
  2. Thread interrupt state must be preserved when interrupted during backoff sleep.
  3. 100% clean-room test pass rate across all existing unit test suites.
* **Risk Rating**: **LOW**. Hygiene-only improvements; preserves existing fallbacks and business logic while improving diagnosability.
