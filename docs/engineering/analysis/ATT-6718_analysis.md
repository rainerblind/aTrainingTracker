# Stage 1 Analysis: ATT-6718 - Localize Calibration Messages, Bounded 60-Day Query & Artifact Pin Guard

**Ticket**: [ATT-6718](https://rainerblind.atlassian.net/browse/ATT-6718)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6718` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
* In `ATT-3545` (commit `6bfb073e`), `HrvReadinessCalculator.kt` constructed `calibrationProgressMessage` with hardcoded English strings (`"Day $n60d of $MIN_60D_DAYS baseline calibration"`, `"Day $n7d of $MIN_7D_DAYS weekly readings"`, `"Calibration complete"`), rendered in `MorningHrvCheckScreen.kt`, violating 9-locale parity (`design_guidelines.md` §4).
* In `HrvReadinessRepository.kt`, `calculateReadiness()` calls `databaseManager.getAllEntries(posture)` loading years of historical records on every check, leaving `getPrimaryEntriesInWindow(posture, dMinus59Iso, targetDateIso)` unused.
* In `HrvReadinessDatabaseManager.kt`, `insertEntry()` and `setPrimaryReading()` allow entries with `artifactRatePct > 5.0%` to be pinned as primary, displacing valid morning readings and compromising baseline validity.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Call Site & Code Audit**:
  * `HrvReadinessCalculator.kt:165-173`: Builds hardcoded English strings for baseline/weekly calibration progress.
  * `HrvReadinessResult.kt:41-55`: Lacks structured calibration state/progress counts, exposing only raw `calibrationProgressMessage: String`.
  * `MorningHrvCheckScreen.kt:650`: Renders raw `readinessResult.calibrationProgressMessage` on status pill.
  * `HrvReadinessRepository.kt:68`: Unbounded `databaseManager.getAllEntries(posture)` query instead of `[D-59, D]` window query.
  * `HrvReadinessDatabaseManager.kt:152, 205, 248-258`: `pinnedId` unconditionally sets `is_primary_for_day = 1` without filtering `artifact_rate_pct <= 5.0%`.
* **Independent Peer Review & Verification**:
  * *Calibration State*: Introducing `enum class CalibrationProgressState { BASELINE_60D, WEEKLY_7D, COMPLETE }` in `HrvReadinessResult` allows `MorningHrvCheckScreen` to render localized strings across all 9 languages while preserving `calibrationProgressMessage` as fallback.
  * *Bounded Query*: Computing `dMinus59Iso = LocalDate.parse(targetDateIso).minusDays(59).toString()` and calling `getPrimaryEntriesInWindow(posture, dMinus59Iso, targetDateIso)` utilizes `idx_hrv_readiness_date_posture` and retrieves only necessary records.
  * *Artifact Gate*: Checking `artifactRatePct <= MAX_VALID_ARTIFACT_RATE_PCT` in `insertEntry`, `setPrimaryReading`, and `recomputePrimaryForDayInternal` ensures noisy readings never claim primary status.

---

## 3. Scope Grounding & Non-Goals
* **In-Scope Goals**:
  1. Add `CalibrationProgressState` to `HrvReadinessResult` and localized string keys in `strings_hrv.xml` across all 9 locales.
  2. Route `HrvReadinessRepository.calculateReadiness()` through `databaseManager.getPrimaryEntriesInWindow()`.
  3. Enforce `artifactRatePct <= 5.0%` gate on `pinnedId` in `HrvReadinessDatabaseManager.kt`.
  4. Expand unit tests in `HrvReadinessCalculatorTest`, `HrvReadinessRepositoryTest`, `HrvReadinessDatabaseManagerTest`, and `TranslationParityTest`.
* **Out-of-Scope Non-Goals**:
  1. Altering `HrvReadiness.db` SQLite schema (v1 schema strictly preserved).
  2. Modifying mathematical formulas for $\mu_{7d}$, $\mu_{60d}$, $\sigma_{60d}$ (Bessel's $N-1$), or SWC corridor boundaries.
  3. Modifying `PeriodsScreen` or chart rendering layouts.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PER-030` (`REQ-PER-030.3`, `REQ-PER-030.4`), targeting `HrvReadinessCalculator.kt`, `HrvReadinessRepository.kt`, `HrvReadinessDatabaseManager.kt`, and `strings_hrv.xml`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-3545` (commit `6bfb073e`).
* **Root Reason for Existing Formulation**: Early prototype concatenated English strings directly; `getAllEntries` was initial convenience query before windowed queries were integrated; primary pinning was designed without considering electrode noise.
* **Preservation of Core Invariants**:
  1. Zero SQLite schema changes; `HrvReadiness.db` remains at version 1.
  2. Strict posture isolation between `SUPINE`, `SEATED`, and `STANDING` preserved.
  3. Minimum sample thresholds ($\ge 3$ days for 7d, $\ge 14$ days for 60d) and SWC bounds preserved.
  4. Earliest valid morning reading preserved as primary if no valid pinned reading exists.

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**: Zero schema migration, 100% translation parity across 9 languages, strict posture segregation, and backwards compatibility for `HrvReadinessResult`.
* **Risk Rating**: **LOW** — Self-contained database query optimization, artifact validation guard, and localized string exposure with zero cross-component IPC side-effects (2 SP).
