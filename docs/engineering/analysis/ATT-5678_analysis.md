# Stage 1: Problem Domain & Root Cause Analysis - ATT-5678

**Ticket**: [ATT-5678](https://rainerblind.atlassian.net/browse/ATT-5678)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355): Good and consistent UI | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5678`  
**Requirement Mapping**: `REQ-UI-401` (`REQ-UI-401.4`), `REQ-UI-402` (`REQ-UI-402.2`), `REQ-UI-403` (`REQ-UI-403.4`), `REQ-TRK-020`, `REQ-TRK-021`  
**Test Mapping**: `TST-UI-366` (`TST-UI-366.7`), `TST-UI-367` (`TST-UI-367.2`), `TST-UI-372`, `TST-STR-019`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Domain & Background
Peaksware, LLC (parent company of TrainingPeaks) holds registered trademarks on cycling/endurance terms: `Normalized Power®` / `NP®`, `Intensity Factor®` / `IF®`, `Training Stress Score®` / `TSS®`, and `Performance Management Chart®` / `PMC®` (along with `CTL`, `ATL`, `TSB`). While the mathematical algorithms (Coggan / Banister) are public domain, using these protected trademark names/abbreviations exposes third-party apps to trademark enforcement. Like Strava, Intervals.icu, and GoldenCheetah, aTrainingTracker must migrate all user-facing UI labels across all 9 locales to trademark-free, descriptive terminology:
* `Normalized Power` / `NP` $\rightarrow$ **Weighted Power** (**WP**) / DE: **Gewichtete Leistung**
* `Intensity Factor` / `IF` $\rightarrow$ **Relative Intensity** (**RI**) / DE: **Relative Intensität**
* `Training Stress Score` / `TSS` $\rightarrow$ **Training Load** (**Load**) / DE: **Trainingsbelastung**
* `Performance Management (PMC)` $\rightarrow$ **Training Load & Form** (**Load / Form**) / DE: **Belastungssteuerung**
* `Fitness (CTL)` / `Fatigue (ATL)` / `Form (TSB)` $\rightarrow$ **Fitness (42d)** / **Fatigue (7d)** / **Form (Balance)**
* `%1$+.1f CTL/wk` $\rightarrow$ **`%1$+.1f/wk`** (DE: `%1$+.1f/Wo.`)

### Independent Peer Review & Hypothesis Verification (Rules 69 & 70, ATT-3534)
* **Codebase Audit**: Verified exactly 20 affected string keys across all 9 `strings.xml` resource files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
* **Non-Translatable Badge Keys**: `normalized_power_short` (`WP`), `intensity_factor_short` (`RI`), and `tss_short` (`Load`) reside in base `values/strings.xml` with `translatable="false"`. Per `TranslationParityTest` (`TST-STR-019.2`), these keys must NOT be declared in `values-*/strings.xml`.
* **Zero Schema & Zero DTO Regression**: Database columns, SQLite queries (`WorkoutSummariesDatabaseManager`), sensor types (`SensorType.NORMALIZED_POWER`, `SensorType.TSS`), and DTO fields (`normalizedPowerWatts`, `tss`) are non-user-facing and MUST remain strictly untouched to ensure 100% backup/restore and schema compatibility.

---

## 2. Chesterton's Fence & Invariant Archaeology
* **Original Requirement ID & Target**: Amends user-visible string presentation of `REQ-UI-401.4` (PMC), `REQ-UI-402.2` (Power Metrics), `REQ-UI-403.4` (Physiology Settings), `REQ-TRK-020` (Live NP/IF), and `REQ-TRK-021` (Live TSS).
* **Historical Origin & Commit Trace**: `ATT-1290` / `ATT-1291` (Live NP/IF/TSS), `ATT-3858` / `ATT-3859` (Workout Details & PMC, Sprint 2026-44.1), `ATT-3929` (Physiology Models Section, Sprint 2026-44.2).
* **Root Reason for Existing Formulation**: Original strings used Dr. Andrew Coggan's traditional terminology as coined in literature without separating proprietary trademarks from underlying mathematics.
* **Preservation of Core Invariants**:
  1. *Mathematical Equivalence*: Calculation algorithms in `PowerPhysiologyCalculator.kt` and `CyclingPowerMetricsDevice.java` remain 100% identical.
  2. *Positional Specifier Parity*: All format specifiers (`%1$+.1f`, `%1$.0f`, `%1$.2f`) remain intact across all 9 locales.
  3. *Translation Parity*: `TranslationParityTest` must pass with zero missing or extraneous keys.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail
* **In-Scope**:
  1. Update 20 string keys across all 9 `strings.xml` files to trademark-free terms (`WP`, `RI`, `Load`, `Fitness (42d)`, `Fatigue (7d)`, `Form (Balance)`).
  2. Update unit/contract test mock expectations (`AdvancedTuningAccordionTest.kt`).
  3. Update user guide documentation (`docs/guide/de/07-physiology-training-zones.md`, `docs/guide/en/07-physiology-training-zones.md`).
* **Out-of-Scope**: Renaming internal Kotlin properties, database columns, enum identifiers, or altering EWMA math.
* **Complexity Rating**: **2 SP (Standard)**. Multi-locale string updates across 9 files with localized phrasing.

---

## 4. Blast Radius & Side-Effect Audit (Rule 68)
| Affected Component | Impact / Verification |
| :--- | :--- |
| `values*/strings.xml` (9 locales) | 20 string keys updated to trademark-free terminology; format specifiers preserved. |
| `AthleteProfileScreen.kt` | Renders `athlete_profile_pmc_*` strings with new labels; layout & charts unchanged. |
| `WorkoutDetails.kt` & Cockpit | Displays `WP`, `RI`, `Load` via `normalized_power_short`, `intensity_factor_short`, `tss_short`. |
| `AdvancedTuningAccordionTest.kt` | Subtitle assertion updated for `tuning_physiology_pmc_short` (`"Load / Form"`). |
| `docs/guide/*/07-physiology-*.md` | User documentation updated to reflect trademark-free terminology. |

---

## 5. System Invariants & Risk Assessment
* **Core Invariants**: Mathematical invariance; SQLite schema and backup backward compatibility; 9-language translation parity (`TranslationParityTest`).
* **Risk Rating**: **LOW** (Presentation-only localization refactoring with zero logic or schema alterations).
