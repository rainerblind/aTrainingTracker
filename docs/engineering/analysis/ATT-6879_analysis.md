# Stage 1 Analysis: ATT-6879 - Per-Workout RPE (1–10) & Feel (1–5) Tracking in EditWorkoutScreen, Summary/Detail Reorderable Section & FIT Parity

**Ticket**: [ATT-6879](https://rainerblind.atlassian.net/browse/ATT-6879)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3`  
**Branch**: `feature/ATT-6879` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
- External telemetry (`Power`, `HR`) captures only external work and cardiovascular response, omitting internal perceived exertion (`1..10` Borg CR10) and workout satisfaction (`1..5` Feel).
- Sensorless sessions (no power meter or HR strap) produce zero training load (`TSS`/`hrTSS`) for PMC or readiness tracking.
- Athletes require optional RPE and Feel tracking (default `OFF`), editable directly in `EditWorkoutScreen` above `Description`, visible as a reorderable top section (`index 0`) in summary cards and details, supporting Foster's Session-RPE fallback (`sRPE = rpe * (duration_s / 60.0)`), and round-tripped via standard Garmin FIT `session` fields.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  - **EditWorkoutScreen Anchor Verified**: `EditWorkoutScreen.kt:371` places `OutlinedTextField` for `Description` directly after section 5b (Health Connect). RPE chips/stepped slider (`1..10`) and Feel selector (`1..5`) slot cleanly above `Description`.
  - **Garmin FIT SDK Fields Verified**: `SessionMesg.java` in `com.garmin:fit:21.141.0` defines field 192 (`workout_feel`, 0–100 scale, `getWorkoutFeel()`/`setWorkoutFeel()`) and field 193 (`workout_rpe`, Borg CR10 multiplied 10x, `getWorkoutRpe()`/`setWorkoutRpe()`). Clean mathematical mapping: FIT `workout_rpe = (rpe * 10).toShort()`; FIT `workout_feel = ((feel - 1) * 25).toShort()`.
  - **Database Migration Invariant Verified**: `WorkoutSummariesDatabaseManager.java:2317` is at `DB_VERSION = 29`. Bumping to `DB_VERSION = 30` with nullable `rpe INTEGER` and `feel INTEGER` in `WorkoutSummaries` adheres to `Rule 64` (`onCreate` and `onUpgrade` with `addColumnIfNotExists`).
  - **Section Reordering & Tab Invariants**: `WorkoutSectionType.kt:51` (`DEFAULT_ORDER`) and `WorkoutDetailTabConfig.kt:291` (`DEFAULT_TABS[0]` `tab_overview`) place `RPE_AND_FEEL` at `index 0`.
  - **Training Load Fallback Hierarchy**: `PowerPhysiologyCalculator.kt:953` (`resolveWorkoutTrainingLoad`) handles Tier 1 (Power TSS), Tier 2 (derived TSS), and Tier 3 (hrTSS). Adding Tier 4 Session-RPE (`sRPE = rpe * (activeTimeSeconds / 60.0)`) cleanly fills the gap when telemetry is absent.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add `rpe` (nullable `1..10`) and `feel` (nullable `1..5`) to `WorkoutSummaries` (`DB_VERSION = 30`), `WorkoutData`, and `WorkoutDataMapper`.
  2. Implement RPE (stepped chip/slider `1..10` with verbal anchors) and Feel (5-point emoji selector: 😫 😕 😐 🙂 🤩) above `Description` in `EditWorkoutScreen.kt`.
  3. Add `RPE_AND_FEEL` section to `WorkoutSectionType` and `WorkoutDetailSectionType` as reorderable section (default `OFF`, top index `0`), integrated into `WorkoutMasksAndCardsSection.kt` and `PhysiologyModelsSection.kt`.
  4. Render badge row (`WorkoutRpeFeelSection.kt`) on `WorkoutSummary` cards and `WorkoutDetailTabbedContent` when enabled.
  5. Round-trip `workout_rpe` and `workout_feel` in `FitFileWriter.java` and `FitWorkoutParser.kt`.
  6. Add Session-RPE fallback (`sRPE`) in `PowerPhysiologyCalculator.kt`.
  7. 9-language localization parity (`values`, `de`, `es`, `fr`, `it`, `nl`, `pl`, `pt`, `sv`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - No retroactive recalculation of PMC history for past workouts lacking RPE.
  - No export to TCX/GPX (standard Garmin FIT only; TCX/GPX lack native RPE fields).
  - No automatic prompt/notification dialog at workout recording stop (opt-in manual edit in `EditWorkoutScreen`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-508` (*Per-Workout RPE & Subjective Feel Tracking & Reorderable Section*), refining `REQ-UI-255` (*Customizable Section Reordering*), `REQ-DAT-042` (*Garmin FIT Export/Import Integrity*), and `REQ-UI-401` (*Training Load Hierarchy*).
* **Historical Origin & Commit Trace**: `REQ-UI-255` introduced in `ATT-2176` (Sprint 2026-34.1) established the 12-section reorderable list and permanent header anchor; `REQ-UI-499` in `ATT-6213` made `DETAILS` reorderable.
* **Root Reason for Existing Formulation**: Sections were keyed strictly to physical sensor telemetry (GPS, Speed, HR, Power, Cadence, Elevation). Subjective athlete state was previously confined to raw freeform text in `Description`.
* **Preservation of Core Invariants**: Adding `RPE_AND_FEEL` maintains `DEFAULT_ORDER` self-healing parsing in `WorkoutSectionType.fromSerializedString()`, preserves non-null schema defaults in `WorkoutSummaries`, and keeps default visibility `OFF` (`showRpeAndFeel = false`) so existing user card layouts remain 100% unaltered.

---

## 5. Architectural Strategy & High-Level Solution
- **Data Layer**:
  - `WorkoutSummariesDatabaseManager.java`: Bump `DB_VERSION` to 30; add `rpe INTEGER` and `feel INTEGER` columns with `onCreate` and `onUpgrade` parity.
  - `WorkoutData.kt` & `WorkoutDataMapper.kt`: Add `rpe: Int?` and `feel: Int?` fields; map cursor to domain model.
  - `FitFileWriter.java` & `FitWorkoutParser.kt`: Write/read Garmin FIT fields 192 (`workout_feel`) and 193 (`workout_rpe`).
- **UI & Presentation Layer**:
  - `EditWorkoutScreen.kt` & `EditWorkoutViewModel.kt`: Render RPE chips/slider (`1..10`) and Feel buttons (`1..5`) directly above `Description`.
  - `WorkoutSectionType.kt` & `WorkoutDetailTabConfig.kt`: Add `RPE_AND_FEEL` (default `index 0`), serialized/deserialized seamlessly.
  - `WorkoutMasksAndCardsSection.kt`: Add `showRpeAndFeel` toggle (default `false`) and reordering handle.
  - `WorkoutRpeFeelSection.kt`: Dedicated composable badge row displaying RPE, Borg verbal label, Feel emoji, and computed `sRPE` (`rpe * minutes`).
- **Physiology Layer**:
  - `PowerPhysiologyCalculator.kt`: Add `sRPE` fallback in `resolveWorkoutTrainingLoad()` when Power TSS and hrTSS are null/0.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Default `OFF`: Fresh installs and existing user preferences default `showRpeAndFeel = false`; zero visual disruption.
  2. Backward Schema Compatibility: `DB_VERSION = 30` migration safely uses `addColumnIfNotExists` without data loss.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **MEDIUM**
  - Justification: Involves SQLite database version increment (`DB_VERSION = 30`), FIT binary parser/writer updates, section enum serialization migrations, and UI changes across Edit, Summary Card, and Multi-tab Details.
