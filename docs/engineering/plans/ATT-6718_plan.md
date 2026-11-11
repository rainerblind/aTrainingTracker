# Stage 3: Implementation Plan - ATT-6718: Localize Calibration Messages, Bounded 60-Day Query & Artifact Pin Guard

**Ticket**: [ATT-6718](https://rainerblind.atlassian.net/browse/ATT-6718)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PER-030` (`REQ-PER-030.3`, `REQ-PER-030.4`), `REQ-UI-106`  
**Test Mapping**: `TST-PER-038` (`TST-PER-038.2`, `TST-PER-038.4`, `TST-PER-038.6`, `TST-PER-038.7`)  
**Branch**: `feature/ATT-6718` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
* `HrvReadinessCalculator.kt` produces hardcoded English calibration progress strings, displayed directly in `MorningHrvCheckScreen.kt`, violating 9-locale parity.
* `HrvReadinessRepository.calculateReadiness()` queries all historical entries via `getAllEntries(posture)` instead of utilizing `databaseManager.getPrimaryEntriesInWindow(posture, dMinus59Iso, targetDateIso)`.
* `HrvReadinessDatabaseManager.kt` allows entries exceeding `5.0%` artifacts to be pinned as primary, displacing valid readings.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-PER-030.3` (*Artifact-Gated Primary Pinning*) -> `TST-PER-038.2` (`HrvReadinessDatabaseManagerTest`).
* **Requirement**: `REQ-PER-030.4` (*Bounded 60-Day Window & Localized Calibration Progress*) -> `TST-PER-038.4`, `TST-PER-038.6` (`HrvReadinessCalculatorTest`, `HrvReadinessRepositoryTest`).
* **Requirement**: `REQ-UI-106` (*9-Language Localization Parity*) -> `TST-PER-038.4` (`TranslationParityTest`).

---

## 3. System Invariants & Preserved Behavior
1. **Zero Database Schema Migrations**: `HrvReadiness.db` strictly remains at version 1 (zero column or table changes).
2. **Mathematical Baseline Parity**: Bessel's $N-1$ denominator, SWC corridor $[\mu_{60d} \pm 0.5\sigma_{60d}]$, and strict posture segregation (`SUPINE`, `SEATED`, `STANDING`) are preserved 100%.
3. **Earliest Valid Reading Invariant**: If no valid pinned reading exists, the earliest valid reading for `(date_iso, posture)` remains primary.
4. **Backwards Compatibility**: `HrvReadinessResult.calibrationProgressMessage` is preserved as a fallback string for existing callers and non-Compose consumers.

---

## 4. Proposed Architectural Changes
### Component 1: `HrvReadinessCalculator.kt` & `HrvReadinessResult.kt`
* Define `enum class CalibrationProgressState { BASELINE_60D, WEEKLY_7D, COMPLETE }`.
* Expose `val calibrationState: CalibrationProgressState` on `HrvReadinessResult`.
* Maintain `calibrationProgressMessage: String` with English defaults for fallback compatibility.

### Component 2: `strings_hrv.xml` (9 Locales) & `MorningHrvCheckScreen.kt`
* Define localized string resources across all 9 locales:
  * `morning_hrv_calibration_baseline_progress`: `"Day %1$d of %2$d baseline calibration"`
  * `morning_hrv_calibration_weekly_progress`: `"Day %1$d of %2$d weekly readings"`
  * `morning_hrv_calibration_complete`: `"Calibration complete"`
* Update `MorningHrvCheckScreen.kt` to resolve localized text via `when (readinessResult.calibrationState)`.

### Component 3: `HrvReadinessRepository.kt`
* In `calculateReadiness(targetDateIso, posture)`, compute `dMinus59Iso = LocalDate.parse(targetDateIso).minusDays(59).toString()`.
* Query `databaseManager.getPrimaryEntriesInWindow(posture, dMinus59Iso, targetDateIso)` instead of `getAllEntries(posture)`.

### Component 4: `HrvReadinessDatabaseManager.kt`
* In `insertEntry()`: If `entry.artifactRatePct > 5.0%`, do not pin (`pinnedId = null`).
* In `setPrimaryReading()`: If entry `artifactRatePct > 5.0%`, return `false` without pinning.
* In `recomputePrimaryForDayInternal()`: Validate that `pinnedId` has `artifact_rate_pct <= 5.0%`; if noisy, fallback to automatic valid primary selection.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Calculator & Result)**: Add `CalibrationProgressState` to `HrvReadinessResult` and populate in `HrvReadinessCalculator.kt`.
2. **Step 2 (Localization & UI)**: Add localized calibration keys across 9 locales in `strings_hrv.xml` and wire into `MorningHrvCheckScreen.kt`.
3. **Step 3 (Repository Bounded Query)**: Route `HrvReadinessRepository.calculateReadiness()` through `getPrimaryEntriesInWindow()`.
4. **Step 4 (Database Artifact Pin Guard)**: Guard `insertEntry`, `setPrimaryReading`, and `recomputePrimaryForDayInternal` against `artifact_rate_pct > 5.0%`.
5. **Step 5 (Unit Tests & Audit)**: Expand `HrvReadinessCalculatorTest`, `HrvReadinessRepositoryTest`, `HrvReadinessDatabaseManagerTest`, and execute `TranslationParityTest`.

---

## 6. Verification & Rollback Plan
* **Verification Suite**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.hrv.*"
  ```
* **Rollback**: Discard feature branch commits (`git checkout feature/ATT-6718`).
