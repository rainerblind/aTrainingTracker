# Stage 1 Analysis: ATT-7034 - Track Morning Sleep & Minimal Wellness with or without HR strap

**Ticket**: [ATT-7034](https://rainerblind.atlassian.net/browse/ATT-7034)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3` | **Branch**: `subagent-ATT-7034---ATT-3539-Pro-Agent-self-b201a997`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  
**Requirement Mapping**: `REQ-PER-030` (`REQ-PER-030.1`, `REQ-PER-030.3`), `REQ-PER-032` (`REQ-PER-032.1`, `REQ-PER-032.5`), `REQ-UI-403`, `REQ-UI-424`  
**Test Mapping**: `TST-PER-038`, `TST-PER-040`, `TST-UI-388`  

---

## 1. Problem Statement & Motivation
* **Context**: Autonomic morning readiness (`rMSSD`/`ln(rMSSD)`) achieves optimal athletic efficacy when triangulated with subjective sleep duration, sleep quality, and morning wellness metrics.
* **Gaps**:
  1. During the 60s/120s morning HRV countdown on `MorningHrvCheckScreen.kt`, athletes wait idly.
  2. Athletes cannot log morning sleep/wellness on non-strap days or without an HRV strap.
  3. Athletes who do not want subjective tracking require an entirely clean, uncluttered interface.
* **Objective**: Add a progressive disclosure master toggle in Advanced Settings (`PhysiologyModelsSection.kt`, default `OFF`). When enabled, expose minimal default metrics (Sleep Duration/Quality, Morning Energy) and optional sub-metrics (Muscle Soreness, Context Tags). Support logging both standalone (no-strap) and in-countdown on `MorningHrvCheckScreen.kt`. Evolve `HrvReadiness.db` to `DB_VERSION = 2` with nullable HRV metrics and optional Health Connect `SleepSessionRecord` prefill.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA / Architectural Gap**:
  * `HrvReadinessDbHelper` V1 enforces `NOT NULL` on `resting_hr_bpm`, `rmssd_ms`, `ln_rmssd`, `hrv_score_100`, and `duration_sec`, preventing non-strap entries.
  * `MorningHrvCheckScreen.kt` currently couples the save button to `MorningHrvPhase.COMPLETED` with non-null `computedEntry`.
* **Independent Peer Review & Verification (ATT-3534, Rules 68 & 70)**:
  * *SQLite Schema Evolution (Rule 64)*: SQLite cannot drop `NOT NULL` via `ALTER TABLE`. `onUpgrade` (V1 $\rightarrow$ V2) must perform a structured table migration (create new table, copy existing rows, swap, and recreate indices). `onCreate` defines nullable columns directly.
  * *Downstream Math & Corridor Safety (Rule 68)*: `HrvReadinessTrendMath.kt` and `HrvReadinessCalculator.kt` must explicitly guard against null `rmssdMs`/`lnRmssd` (`WHERE rmssd_ms IS NOT NULL` / `it.rmssdMs != null`) so standalone wellness entries never pollute baseline corridors or cause null arithmetic.
  * *Health Connect Guard*: `SleepSessionRecord` prefill must check availability and `READ_SLEEP` permission, failing gracefully to manual defaults without crash.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Master toggle `enableMorningSleepAndWellness` (default `OFF`) and 4 subordinate checkboxes in `PhysiologyModelsSection.kt` and `TuningPreferencesDataStore.kt` / `SettingsDataStore.kt`.
  * `HrvReadiness.db` migration to `DB_VERSION = 2` with nullable HRV columns and 5 new columns (`sleep_duration_min`, `sleep_quality_1_5`, `morning_energy_1_5`, `muscle_soreness_1_5`, `context_tags`).
  * Updated `HrvReadinessEntry` DTO and `HrvReadinessDatabaseManager` cursor mapping, preserving `DatabaseSchemaRegistry` parity.
  * Standalone (no-strap) and in-measurement UX on `MorningHrvCheckScreen.kt` & `MorningHrvCheckViewModel.kt`.
  * Optional Health Connect `SleepSessionRecord` duration auto-prefill in `HealthConnectManager.kt`.
  * 9-language localization parity in `strings_hrv.xml` and `strings_tuning.xml`.
* **Out-of-Scope**: Modifications to `WorkoutSummaries.db`, `WorkoutRrIntervals.db`, or live tracking screens.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022, Rule 68)
* **Original Requirement ID & Target**: `REQ-PER-030` (Persistence) & `REQ-PER-032` (Morning Screen).
* **Historical Origin & Commit Trace**: `ATT-3545` (`669146df`) established `HrvReadiness.db` V1 with strict non-null HRV constraints; `ATT-3556` (`dc034c08`) implemented `MorningHrvCheckScreen.kt`.
* **Root Reason for Existing Formulation**: V1 assumed morning readiness originated exclusively from chest-strap ECG measurements; mandatory fields guarded against incomplete records.
* **Preservation of Core Invariants**: Retaining `WHERE rmssd_ms IS NOT NULL` in trend algorithms safely allows nullable HRV fields for standalone wellness entries without degrading existing SWC calculations.

---

## 5. Architectural Strategy & High-Level Solution
* **Settings & State**: Add toggle flags to `TuningConfig` / `SettingsDataStore`. `MorningHrvCheckViewModel` observes state and binds wellness inputs.
* **Database & Repository**: Upgrade `HrvReadinessDbHelper` to `DB_VERSION = 2` with full `onCreate`/`onUpgrade` parity. Update `HrvReadinessEntry` and `HrvReadinessRepository`.
* **UI**: Render `MorningWellnessInputCard` when master toggle is enabled. Provide `Save Morning Wellness` action when standalone.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Default OFF: When disabled, `MorningHrvCheckScreen` remains 100% unchanged.
  2. Baseline Isolation: Standalone wellness entries never corrupt `ln(rMSSD)` SWC baselines.
  3. Zero Workout Overhead: No rows generated in `WorkoutSummaries.db`.
  4. Registry Parity: Direct `DB_VERSION` reference in `DatabaseSchemaRegistry`.
  5. 100% 9-language localization parity.
* **Risk Rating**: **MEDIUM** — Schema migration (`DB_VERSION = 2`) and UI state handling; mitigated by automated migration and contract unit tests.
