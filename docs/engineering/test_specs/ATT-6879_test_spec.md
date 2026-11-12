# Stage 2: Requirement & Test Specification - ATT-6879: Track per-workout RPE & Feel in EditWorkoutScreen, Reorderable Top Section & FIT Parity

**Ticket**: [ATT-6879](https://rainerblind.atlassian.net/browse/ATT-6879)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3` | **Branch**: `feature/ATT-6879`  
**Requirement Mapping**: `REQ-WKO-035`–`REQ-WKO-039` | **Test Spec ID**: `TST-WKO-035`–`TST-WKO-039`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-WKO-035..039)

### 1.1 Functional & Architectural Requirements
* **REQ-WKO-035** (*Data Model & Persistence*): `WorkoutSummariesDatabaseManager` SHALL bump `DB_VERSION = 30` and add nullable `rpe` (`INTEGER`, `1..10`) and `feel` (`INTEGER`, `1..5`) columns with `onCreate` and `onUpgrade` parity. `WorkoutData` and `WorkoutDataMapper` SHALL map these fields symmetrically across single and batch queries.
* **REQ-WKO-036** (*Section Ordering & Progressive Disclosure*): `WorkoutSectionType` and `WorkoutDetailSectionType` SHALL declare `RPE_AND_FEEL` at `index 0` in `DEFAULT_ORDER` and `WorkoutDetailTabConfig` `OVERVIEW`. In Advanced Settings, RPE & Feel tracking SHALL default to `OFF` (`showRpeAndFeel = false`).
* **REQ-WKO-037** (*EditWorkoutScreen UI Placement*): When enabled or when values exist, `EditWorkoutScreen` SHALL display RPE (`1..10` Borg CR10 stepped chip/slider with verbal anchors) and Feel (`1..5` emoji selector: 😫 😕 😐 🙂 🤩) directly above the `Description` field.
* **REQ-WKO-038** (*Summary & Detail Presentation*): When enabled, `WorkoutRpeFeelSection` SHALL render a compact badge row displaying RPE, Borg verbal label, Feel emoji, and computed Session-RPE load (`sRPE = rpe * (activeTimeSeconds / 60.0)`).
* **REQ-WKO-039** (*FIT Parity, Training Load Fallback & Localization*): `FitFileWriter` and `FitWorkoutParser` SHALL round-trip FIT fields 192 (`workout_feel`) and 193 (`workout_rpe`). `PowerPhysiologyCalculator` SHALL provide `sRPE` fallback when Power TSS and hrTSS are null/0. All UI strings SHALL have 100% parity across all 9 supported locales (`values`, `de`, `es`, `fr`, `it`, `nl`, `pl`, `pt`, `sv`).

### 1.2 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Default OFF & Reorderable Position)**: *Given* fresh installs or upgraded profiles, *When* inspecting card/detail section configs, *Then* `RPE_AND_FEEL` defaults to `OFF` at `index 0` and is reorderable.
* **Criterion 2 (Edit Placement & Persistence)**: *Given* RPE & Feel enabled, *When* editing a workout, *Then* RPE chips and Feel emojis appear above `Description` and persist cleanly to SQLite.
* **Criterion 3 (FIT Round-Trip & sRPE Fallback)**: *Given* a workout with RPE and no power/HR, *When* exported/imported to FIT or queried for load, *Then* FIT fields 192/193 preserve values and `sRPE` is computed.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new `REQ-WKO-035`–`REQ-WKO-039`, refining `REQ-UI-255` (*Customizable Section Reordering*), `REQ-DAT-042` (*FIT Export/Import*), and `REQ-UI-401` (*Training Load Hierarchy*).
2. *Historical Origin & Commit Trace*: `ATT-2176` (Sprint 2026-34.1), `ATT-6213`, `ATT-3534` (`DB_VERSION` migration parity).
3. *Root Reason for Existing Formulation*: Previous sections were tied exclusively to physical telemetry streams; subjective athlete perception was stored only in unstructured description text.
4. *Preservation of Core Invariants*: Default `OFF` preserves existing user layouts; nullable SQLite columns guarantee zero migration crashes; standard FIT `SessionMesg` fields ensure cross-platform compatibility.

---

## 2. Test Specification (TST-WKO-035..039)

### Test Case 1: `testWorkoutSummariesDb_v30MigrationAndCursorParity` (`TST-WKO-035`)
* **Scope**: Database Migration & DTO Mapping Contract Test (`WorkoutSummariesDatabaseTest.kt`)
* **Action**: Upgrade DB from v29 to v30 and compare table schema with fresh v30 `onCreate`; verify symmetric `fromCursor` mapping of `rpe` and `feel`.

### Test Case 2: `testWorkoutSectionType_rpeFeelDefaultOrderAndSettingsToggle` (`TST-WKO-036`)
* **Scope**: Section Configuration Contract Test (`WorkoutSectionTypeTest.kt`, `WorkoutDetailTabConfigTest.kt`)
* **Action**: Assert `RPE_AND_FEEL` is at `index 0` in `DEFAULT_ORDER` and `showRpeAndFeel` preference defaults to `false`.

### Test Case 3: `testEditWorkoutScreen_rpeFeelPlacedAboveDescription` (`TST-WKO-037`)
* **Scope**: Compose UI & ViewModel Test (`EditWorkoutViewModelTest.kt`, `EditWorkoutScreenTest.kt`)
* **Action**: Verify RPE and Feel controls appear above `hint_workout_description` and update `workoutData` on user selection.

### Test Case 4: `testWorkoutRpeFeelSection_badgeRowAndSrpeCalculation` (`TST-WKO-038`)
* **Scope**: Unit & Composable Test (`PowerPhysiologyCalculatorTest.kt`, `WorkoutRpeFeelSectionTest.kt`)
* **Action**: Assert `sRPE = rpe * (duration_min)` computation and verify badge row formatting with verbal anchors.

### Test Case 5: `testFitRoundTripAndLocalizationParity` (`TST-WKO-039`)
* **Scope**: FIT Codec Test & 9-Language Parity (`FitExportImportTest.kt`, `TranslationParityTest.kt`)
* **Action**: Round-trip FIT `SessionMesg` fields 192/193; verify zero missing strings and valid `%s`/`%d` specifiers across all 9 locales.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-WKO-035` | DB / Contract | `WorkoutSummariesDbHelper.onUpgrade` / `fromCursor` | `REQ-WKO-035` | Specified |
| `TST-WKO-036` | Unit / Settings | `WorkoutSectionType.DEFAULT_ORDER` / `MyPreferenceManager` | `REQ-WKO-036` | Specified |
| `TST-WKO-037` | UI / ViewModel | `EditWorkoutViewModel` / `EditWorkoutScreen` | `REQ-WKO-037` | Specified |
| `TST-WKO-038` | Physiology / UI | `PowerPhysiologyCalculator.resolveWorkoutTrainingLoad` | `REQ-WKO-038` | Specified |
| `TST-WKO-039` | Exporter / L10n | `FitFileWriter` / `FitWorkoutParser` / `TranslationParityTest` | `REQ-WKO-039` | Specified |
