# Stage 5: Walkthrough & Verification - ATT-3929: Add 'Physiology & Training Models' section in Advanced Settings to toggle training science schools (Coggan NP/IF/TSS, PMC, Aerobic Decoupling EF/Pw:Hr, Critical Power CP/W'/W'bal)

**Ticket**: [ATT-3929](https://rainerblind.atlassian.net/browse/ATT-3929)  
**Sub-task**: [ATT-3939](https://rainerblind.atlassian.net/browse/ATT-3939) (`[Test]`)  
**Parent Epic**: [ATT-3189](https://rainerblind.atlassian.net/browse/ATT-3189) (*Heart Rate & Power Zones & Physiology*)  
**Target Release**: `V4.9.40` (unassigned during in-sprint work per Rule 14)  
**Active Sprint**: `Sprint 2026-42.8`  
**Requirement Mapping**: `REQ-UI-403`  
**Test Mapping**: `TST-UI-366`  
**Branch**: `feature/ATT-3929`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Executive Summary & Verification Overview

Implemented **REQ-UI-403** (`ATT-3929`), adding a dedicated **Physiology & Training Models** (*Physiologie & Trainingsmodelle*) accordion category in **Advanced Settings** (`AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `AdvancedTuningAccordion.kt`, `PhysiologyModelsSection.kt`) backed by `TuningPreferencesDataStore` (`TuningConfig`) to progressively disclose four training science schools across **Profile & Zones** (`AthleteProfileScreen.kt`), **Workout Summaries & Details** (`WorkoutDetails.kt`, `WorkoutSummary.kt`, `WorkoutList.kt`, `MapDetailLayout.kt`, `TrackOnMapScreen.kt`), and **Live Cockpit Sensor Selector** (`EditSensorFieldViewModel.kt`):

1. **Coggan Power Metrics (NP, IF, TSS, VI)**: Default `true` (ON). Controls Coggan power metrics in `WorkoutDetails` and `PEDAL_SMOOTHNESS` / `TORQUE_EFFECTIVENESS` in `EditSensorFieldViewModel` while keeping raw power (`Avg/Max Power`, `Work kJ`, `W/kg`) unconditionally visible.
2. **Performance Management Chart (PMC — CTL, ATL, TSB, ACWR, Ramp Rate)**: Default `false` (OFF). Controls `PerformanceManagementChartCard` in `AthleteProfileScreen`.
3. **Aerobic Efficiency & Decoupling (EF, Pw:Hr %, Pa:Hr %)**: Default `false` (OFF). Controls the Aerobic Efficiency & Decoupling row in `WorkoutDetails`.
4. **Critical Power & Anaerobic Battery (CP, W', W'bal)**: Default `false` (OFF). Controls the 3rd summary card row (`CP` / `W'`), `CRITICAL_POWER` and `W_PRIME` plot selector pills, history filter chips, and `AthleteMetricEntryDialog` pills in `AthleteProfileScreen`, the `Min W'bal` / `Empty Tank` row in `WorkoutDetails`, the `W'bal` telemetry graph in `WorkoutSummary` / `MapDetailLayout` / `TrackOnMapScreen`, and `SensorType.W_PRIME_BALANCE` in `EditSensorFieldViewModel`.

Background recording (`CyclingPowerMetricsDevice`, `TrackerService`) and file import calculation (`LegacyImportEngine`) remain 100% active unconditionally so historical workouts retain complete telemetry whenever a school is enabled later.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-403.1` | `TST-UI-366.1` | `AdvancedTuningScreenTest`, `PhysiologyModelsVisibilityContractTest` (`TuningConfig` defaults & DataStore keys) | **PASSED** | `Verified` |
| `REQ-UI-403.2` | `TST-UI-366.1` | `AdvancedTuningAccordionTest`, `AdvancedTuningModularityTest` (`< 400` lines across all files in `ui/settings/tuning/`) | **PASSED** | `Verified` |
| `REQ-UI-403.3` | `TST-UI-366.2` | `PhysiologyModelsVisibilityContractTest`, `EditSensorFieldViewModelTest` (reactive `combine` flow filtering, Rule 39) | **PASSED** | `Verified` |
| `REQ-UI-403.4` | `TST-UI-366.3` | 9-Language Localization Parity Audit (`14` keys across `9` `values*/strings.xml` files) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-366.4` | Full Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL
100% pass rate across all unit and architectural contract tests.
```

### Targeted Unit & Integration Tests
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*" --tests "com.atrainingtracker.trainingtracker.ui.athleteprofile.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.*"
BUILD SUCCESSFUL in 17s
```

---

## 4. Hardware / Physical Verification (Pixel 10)

### Visual Consistency (Rule 23 — mandatory if UI is added or changed)

* **`PhysiologyModelsSection.kt`** reuses the exact `TuningSwitchItem` component, `Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)` intro banner, and `16.dp` / `8.dp` spacing scale of `WorkoutMasksAndCardsSection.kt` and `RecordingPauseSection.kt`.
* **`AdvancedTuningAccordion.kt`** reuses `AccordionCard` with `Icons.Default.Science` and a dynamic collapsed subtitle (`pref_tuning_sub_physiology_enabled_count` / `pref_tuning_sub_physiology_none`).
* **`AthleteProfileScreen.kt`** cleanly collapses the 3rd summary card row (`CP` / `W'`) to the standard 2x2 grid (`Weight`, `Resting HR`, `FTP`, `W/kg`) when `enableCriticalPowerModel == false` and hides `PerformanceManagementChartCard` when `enablePmcModel == false`.
* **Checked against `docs/design_guidelines.md` §5**: shapes ☑ spacing ☑ colors/themes ☑ typography/icons ☑ placement ☑
* **Deviations & justification**: None.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Clean-room unit test suite executed with 100% pass rate.
2. **Living Documentation Synchronized**: Status of `REQ-UI-403` in `docs/requirements.md` and `TST-UI-366` in `docs/tests.md` updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask (`ATT-3939`) transitioned to `Erledigt` via `freigabe`.
4. **Parent Ticket Final Review**: Parent ticket (`ATT-3929`) merged into `sprint/2026-42.8` (`--no-ff`) and transitioned to `Final Review (Human)`.
