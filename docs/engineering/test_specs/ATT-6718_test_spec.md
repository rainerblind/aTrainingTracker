# Stage 2 Requirement & Test Specification: ATT-6718 - Localize Calibration Messages, Bounded 60-Day Query & Artifact Pin Guard

**Ticket**: [ATT-6718](https://rainerblind.atlassian.net/browse/ATT-6718)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PER-030` (`REQ-PER-030.3`, `REQ-PER-030.4`), `REQ-UI-106`  
**Test Spec ID**: `TST-PER-038` (`TST-PER-038.1`..`TST-PER-038.7`)  
**Branch**: `feature/ATT-6718` | **Author**: AI Agent 2 (Reviewer / Specifier, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PER-030)

### 1.1 Problem Statement & User Scope
* In `ATT-3545` (commit `6bfb073e`), `HrvReadinessCalculator.kt` constructed `calibrationProgressMessage` with hardcoded English strings (`"Day $n60d of $MIN_60D_DAYS baseline calibration"`, `"Day $n7d of $MIN_7D_DAYS weekly readings"`, `"Calibration complete"`), displayed in `MorningHrvCheckScreen.kt`, violating 9-locale parity (`design_guidelines.md` §4).
* In `HrvReadinessRepository.kt`, `calculateReadiness()` queries `databaseManager.getAllEntries(posture)` loading all historical years instead of the indexed bounded 60-day window `[D-59, D]` via `getPrimaryEntriesInWindow()`.
* In `HrvReadinessDatabaseManager.kt`, `insertEntry()`, `setPrimaryReading()`, and `recomputePrimaryForDayInternal()` allow entries with `artifactRatePct > 5.0%` to be pinned as primary, displacing valid morning readings and corrupting SWC baselines.

### 1.2 Functional & Architectural Requirements
1. **Calibration Progress Localization (`HrvReadinessCalculator.kt`, `HrvReadinessResult.kt`, `strings_hrv.xml`)**: Expose structured `enum class CalibrationProgressState { BASELINE_60D, WEEKLY_7D, COMPLETE }` and day counts on `HrvReadinessResult`. Add localized strings (`morning_hrv_calibration_baseline_progress`, `morning_hrv_calibration_weekly_progress`, `morning_hrv_calibration_complete`) formatted with `%1$d` and `%2$d` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
2. **Bounded Window Query (`HrvReadinessRepository.kt`)**: In `calculateReadiness()`, compute `dMinus59Iso = LocalDate.parse(targetDateIso).minusDays(59).toString()` and retrieve records strictly via `databaseManager.getPrimaryEntriesInWindow(posture, dMinus59Iso, targetDateIso)`.
3. **Artifact Gate on Primary Pinning (`HrvReadinessDatabaseManager.kt`)**: In `insertEntry()`, `setPrimaryReading()`, and `recomputePrimaryForDayInternal()`, enforce `artifactRatePct <= MAX_VALID_ARTIFACT_RATE_PCT` (`5.0%`) before applying `pinnedId`. If the pinned entry exceeds 5.0% artifacts, reject the pin and preserve the earliest valid morning reading as primary.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (Artifact Gate on Pinning)**: *Given* an earlier valid morning reading (`is_primary_for_day = 1`, `artifact_rate_pct = 1.0%`) and a later noisy reading (`artifact_rate_pct = 8.5%`), *When* attempting to pin the noisy reading via `setPrimaryReading` or `insertEntry`, *Then* the noisy reading is rejected from receiving the primary flag and the earlier valid morning reading remains primary.
* **AC 2 (Bounded 60-Day Query)**: *Given* years of morning HRV records in `HrvReadiness.db`, *When* `calculateReadiness(targetDateIso, posture)` is executed, *Then* only primary valid records in `[D-59, D]` are queried via `getPrimaryEntriesInWindow()`.
* **AC 3 (9-Language Calibration Parity)**: *Given* any of the 9 supported locales, *When* viewing calibration progress in `MorningHrvCheckScreen`, *Then* localized progress strings render with zero raw English text.

### 1.4 System Invariants
* Zero schema migrations or alterations to `HrvReadiness.db` (v1 schema strictly preserved).
* Strict posture isolation (`SUPINE`, `SEATED`, `STANDING`) in SWC baseline math.
* Pure Kotlin mathematical evaluation with 100% clean-room test pass rate.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-PER-030` (`REQ-PER-030.3`, `REQ-PER-030.4`), targeting `HrvReadinessCalculator.kt`, `HrvReadinessRepository.kt`, `HrvReadinessDatabaseManager.kt`, `MorningHrvCheckScreen.kt`, and `strings_hrv.xml`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-3545` (commit `6bfb073e`); refined in `ATT-6718` under Epic `ATT-3539` (*Heart Rate Variability*).
3. *Root Reason for Existing Formulation*: Early prototype concatenated English strings directly; `getAllEntries` was an initial convenience query before windowed queries were integrated; primary pinning was designed without checking electrode artifact flags.
4. *Preservation of Core Invariants*: Zero SQLite schema changes (`HrvReadiness.db` remains at version 1), strict posture isolation, sample thresholds ($\ge 3$ days for 7d, $\ge 14$ days for 60d) and SWC bounds preserved, and 100% clean-room test pass rate.

---

## 3. Test Specification (TST-PER-038: Morning HRV Readiness Suite)

### Test Case 1: Artifact Gate on Primary Pinning (`TST-PER-038.2`)
* **Scope**: `HrvReadinessDatabaseManagerTest.kt`. Verify that inserting or pinning a reading with `artifactRatePct > 5.0%` does NOT displace an existing valid reading (`artifactRatePct <= 5.0%`), and that `is_primary_for_day` remains with the valid reading.

### Test Case 2: Bounded 60-Day Window Query (`TST-PER-038.6`)
* **Scope**: `HrvReadinessRepositoryTest.kt`. Populate records outside `[D-59, D]` (e.g. at D-65) and verify `calculateReadiness()` only queries entries within `[D-59, D]`.

### Test Case 3: Structured Calibration Progress State (`TST-PER-038.4`)
* **Scope**: `HrvReadinessCalculatorTest.kt`. Verify `CalibrationProgressState.BASELINE_60D` when $N_{60d} < 14$, `CalibrationProgressState.WEEKLY_7D` when $N_{7d} < 3$, and `CalibrationProgressState.COMPLETE` when fully calibrated.

### Test Case 4: 9-Language Localization Audit (`TST-PER-038.4`)
* **Scope**: `TranslationParityTest.kt`. Verify that `morning_hrv_calibration_baseline_progress`, `morning_hrv_calibration_weekly_progress`, and `morning_hrv_calibration_complete` exist and match format specifiers across all 9 locales.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-PER-038.7`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.hrv.*"`.

---

## 4. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PER-038.2]` | Unit / Database | `HrvReadinessDatabaseManagerTest`: Artifact gate on primary pinning | `REQ-PER-030.3` | Specified |
| `[TST-PER-038.6]` | Unit / Repository | `HrvReadinessRepositoryTest`: Bounded 60-day query routing | `REQ-PER-030.4` | Specified |
| `[TST-PER-038.4]` | Unit / Calculator | `HrvReadinessCalculatorTest`: Structured CalibrationProgressState | `REQ-PER-030.4` | Specified |
| `[TST-PER-038.4]` | Localization | `TranslationParityTest`: 9-language calibration strings parity | `REQ-PER-030.4`, `REQ-UI-106` | Specified |
| `[TST-PER-038.7]` | Regression | Tier 1 test execution across modified packages | `REQ-PRO-001` | Specified |
