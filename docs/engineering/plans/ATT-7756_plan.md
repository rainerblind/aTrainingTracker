# Stage 3: Implementation Plan - ATT-7756: Dedicated HRV Tab & Telemetry-Gated Power Zone Comparison

**Ticket**: [ATT-7756](https://rainerblind.atlassian.net/browse/ATT-7756)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.6` | **Branch**: `feature/ATT-7756`  
**Requirement Mapping**: `REQ-UI-527` (`REQ-UI-527.1`..`REQ-UI-527.6`)  
**Test Mapping**: `TST-UI-527` (`TST-UI-527.1`..`TST-UI-527.7`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-14  

---

## 1. Problem Description & Background
Post-workout HRV analytics (`HrvAlpha1ScatterCard`, `HrvThresholdSummaryCard`, `HrvDurabilityCard`) are currently colocated under `WorkoutDetailSectionType.ZONES`, crowding the HR and Power zone distribution charts. In addition, `HrvThresholdSummaryCard` displays Coggan Power headers, FTP comparison deltas, consensus badges, and disabled update buttons when `configuredFtp > 0`, even when the workout lacks a power meter (`hasPower == false`). The plan extracts a dedicated `HRV` tab in Workout Details and gates Power/HR zone comparison strictly on active telemetry.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-527` (*Dedicated HRV Tab in Workout Details, Wire Migration & Telemetry-Gated Zone Comparison*)
* **Test Mapping**: `TST-UI-527` (`WorkoutDetailTabsTest`, `HrvThresholdSummaryCardTest`, `TranslationParityTest`)

---

## 3. System Invariants & Preserved Behavior
1. **Wire Deserialization Backward Compatibility**: Persisted tab configs lacking `HRV` auto-migrate `tab_hrv` after `tab_zones` with zero data loss.
2. **Gesture & Map Invariants**: Horizontal pager isolation contracts and map-at-index-0 pinning remain strictly intact (`REQ-UI-408.3`/`.4`).
3. **Empty Tab Auto-Hiding**: Workouts without HRV data (`hasHrv == false`) completely suppress `tab_hrv` with zero layout shift.
4. **Parent Human Gate**: Moving `ATT-7756` to `Erledigt` is reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes
* **Component 1 (`WorkoutDetailTabConfig.kt`, `WorkoutSectionType.kt`)**: Add `HRV` to `WorkoutDetailSectionType` and `WorkoutSectionType`; add `HRV` preset (`WorkoutDetailTabPreset.HRV`); update `DEFAULT_TABS` to 5 tabs (`tab_overview`, `tab_map_graphs`, `tab_zones`, `tab_hrv`, `tab_laps_segments`); add `hasHrv: Boolean` to `WorkoutDetailSectionAvailability`; implement `deserializeTabs` migration.
* **Component 2 (`TrackOnMapScreen.kt`, `WorkoutDetailTabbedContent.kt`)**: Feed `hasHrvData = TelemetryMetricUtils.hasHrvData(activeScrubPath)` into availability; move the 3 HRV cards from `ZONES` to `WorkoutDetailSectionType.HRV`.
* **Component 3 (`HrvThresholdSummaryCard.kt`)**: Define `showPowerComparison = hasPower && configuredFtp != null && configuredFtp > 0` and `showHrComparison = hasHr && configuredHrMax != null && configuredHrMax > 0`; gate respective UI blocks, consensus badges, and update buttons; omit entire comparison section when neither applies.
* **UI Consistency (Rule 23 & Rule 84)**: Reuses `ElevatedCard`, standard `HorizontalDivider`, and Material 3 `Button`/`OutlinedButton` hierarchy; 9-language localization parity for `workout_detail_tab_hrv` and `settings_workout_card_hrv`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Localization Strings in 9 Locales (`REQ-UI-527.6`)
* Add `workout_detail_tab_hrv` and `settings_workout_card_hrv` across `res/values*/strings.xml` (en, de, es, fr, it, ja, nl, pl, pt).

### Step 2: Tab Config, Enums & Deserialization Migration (`REQ-UI-527.1`, `REQ-UI-527.2`, `REQ-UI-527.3`)
* Extend `WorkoutDetailSectionType`, `WorkoutSectionType`, `WorkoutDetailTabPreset`, `DEFAULT_TABS`, and `deserializeTabs` in `WorkoutDetailTabConfig.kt` and `WorkoutSectionType.kt`.
* Add `hasHrv` to `WorkoutDetailSectionAvailability` and handle exhaustive callers in `TrackOnMapScreen.kt` and `WorkoutMasksAndCardsSection.kt`.

### Step 3: Card Relocation & Host Mounting (`REQ-UI-527.3`, `REQ-UI-527.4`)
* In `WorkoutDetailTabbedContent.kt`, move `HrvAlpha1ScatterCard`, `HrvThresholdSummaryCard`, and `HrvDurabilityCard` from `ZONES` to `WorkoutDetailSectionType.HRV`.
* In `TrackOnMapScreen.kt`, pass `hasHrvData = TelemetryMetricUtils.hasHrvData(activeScrubPath)` to `fromWorkoutState`.

### Step 4: Telemetry Gating in `HrvThresholdSummaryCard.kt` (`REQ-UI-527.5`)
* Guard Power and HR comparison blocks on `showPowerComparison` and `showHrComparison`; omit comparison section when both false.

### Step 5: Unit & Contract Verification (`TST-UI-527.1`..`TST-UI-527.7`)
* Update `WorkoutDetailTabsTest.kt`, `HrvThresholdSummaryCardTest.kt`, and `TranslationParityTest.kt`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.localization.*"`.

---

## 6. Verification & Rollback Plan
* **Verification**: Tier 1 targeted aftermath and localization unit suites followed by `./gradlew lintDebug` and `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Branch isolation allows instantaneous git revert of `feature/ATT-7756` with zero impact on sprint branch.
