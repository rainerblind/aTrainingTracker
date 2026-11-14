# Stage 2: Requirement & Test Specification - ATT-7756: Dedicated HRV Tab in Workout Details & Telemetry-Gated Power Zone Comparison

**Ticket**: [ATT-7756](https://rainerblind.atlassian.net/browse/ATT-7756)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.6` | **Branch**: `feature/ATT-7756`  
**Requirement Mapping**: `REQ-UI-527` (*Dedicated HRV Tab in Workout Details, Wire Migration & Telemetry-Gated Zone Comparison*)  
**Test Spec ID**: `TST-UI-527`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-UI-527)

### 1.1 Problem Statement & Rationale
Post-workout HRV analytics (`HrvAlpha1ScatterCard`, `HrvThresholdSummaryCard`, `HrvDurabilityCard`) are colocated under `WorkoutDetailSectionType.ZONES`, cluttering HR and Power distribution cards. In addition, `HrvThresholdSummaryCard.kt` renders Coggan Power headers, FTP delta chips, consensus badges, and update buttons when `configuredFtp > 0`, even when the workout has no power meter. The system SHALL extract a dedicated `HRV` section (`WorkoutDetailSectionType.HRV`, `WorkoutSectionType.HRV`, and `WorkoutDetailTabPreset.HRV`), define a canonical 5-tab default layout, auto-migrate legacy wire configurations, auto-hide the HRV tab when no HRV data exists, and gate Power and HR comparison blocks strictly on actual telemetry availability (refining `REQ-UI-408`, `REQ-PER-036.5`, `REQ-PER-037.4`).

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. **Dedicated HRV Section & 5-Tab Preset (`REQ-UI-527.1`)**: `WorkoutDetailSectionType` and `WorkoutSectionType` SHALL define `HRV(R.string.settings_workout_card_hrv)`; `WorkoutDetailTabPreset` SHALL define `HRV(R.string.workout_detail_tab_hrv)`. `WorkoutDetailTabConfig.DEFAULT_TABS` SHALL define 5 tabs: (1) `tab_overview` (`OVERVIEW`), (2) `tab_map_graphs` (`MAP_AND_GRAPHS`), (3) `tab_zones` (`ZONES`: `[ZONES, GRADE_DISTRIBUTION]`), (4) `tab_hrv` (`HRV`: `[HRV]`), (5) `tab_laps_segments` (`LAPS_AND_SEGMENTS`).
2. **Wire Migration (`REQ-UI-527.2`)**: In `deserializeTabs`, if `HRV` is absent from all tabs, auto-inject `tab_hrv` directly after `tab_zones`.
3. **Dynamic Availability (`REQ-UI-527.3`)**: `WorkoutDetailSectionAvailability` SHALL expose `hasHrv: Boolean`, populated from `TelemetryMetricUtils.hasHrvData(activeScrubPath)` in `TrackOnMapScreen.kt`. `filterVisibleWorkoutDetailTabs` SHALL auto-hide `tab_hrv` when `hasHrv == false`.
4. **Card Migration (`REQ-UI-527.4`)**: In `WorkoutDetailTabbedContent.kt`, render `HrvAlpha1ScatterCard`, `HrvThresholdSummaryCard`, and `HrvDurabilityCard` under `WorkoutDetailSectionType.HRV` (removed from `ZONES`).
5. **Telemetry-Gated Comparison (`REQ-UI-527.5`)**: In `HrvThresholdSummaryCard.kt`, gate Power comparison on `showPowerComparison = hasPower && configuredFtp != null && configuredFtp > 0`; gate HR comparison on `showHrComparison = hasHr && configuredHrMax != null && configuredHrMax > 0`; omit entire section when neither applies.
6. **9-Language Localization Parity (`REQ-UI-527.6`)**: `workout_detail_tab_hrv` and `settings_workout_card_hrv` SHALL exist across all 9 locales.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Dedicated HRV Tab)**: *Given* a workout with HRV telemetry and default tabs, *When* opened in Workout Details, *Then* `tab_hrv` is rendered at tab index 3 containing the 3 HRV cards and `tab_zones` contains only HR/Power/Grade distribution cards.
* **Criterion 2 (Auto-Hiding Empty Tab)**: *Given* a workout without HRV telemetry, *When* opened in Workout Details, *Then* `filterVisibleWorkoutDetailTabs` suppresses `tab_hrv`.
* **Criterion 3 (Wire Migration)**: *Given* a persisted tab configuration string omitting `HRV`, *When* deserialized, *Then* `tab_hrv` is automatically inserted after `tab_zones`.
* **Criterion 4 (Gated Power Comparison)**: *Given* a workout with HRV but no power data (`hasPower == false`), *When* viewing `HrvThresholdSummaryCard`, *Then* Power comparison header, FTP label, power deltas, consensus badge, and update button are omitted.

### 1.5 System Invariants
Zero schema migrations; backward-compatible tab deserialization; map pinned at index 0; zero layout shift on non-HRV workouts; 100% 9-language localization parity.

---

## 2. Test Specification (TST-UI-527)

### 2.1 Unit Test Suite (`WorkoutDetailTabsTest.kt`, `HrvThresholdSummaryCardTest.kt`)
* **TST-UI-527.1 (5-Tab Preset & Sizing)**: Verify `WorkoutDetailSectionType.entries.size == 14`, `WorkoutSectionType.entries.size == 14`, bidirectional enum mapping, and `DEFAULT_TABS` size == 5 with `tab_hrv` at index 3 (`WorkoutDetailTabsTest.kt`).
* **TST-UI-527.2 (Wire Round-Trip & Migration)**: Verify serialize/deserialize round-trip and auto-migration of `tab_hrv` after `tab_zones` when parsing legacy configs (`WorkoutDetailTabsTest.kt`).
* **TST-UI-527.3 (Availability & Empty Tab Auto-Hide)**: Verify `isSectionActive(HRV)` reflects `hasHrv` and `filterVisibleWorkoutDetailTabs` hides `tab_hrv` when `hasHrv == false` (`WorkoutDetailTabsTest.kt`).
* **TST-UI-527.4 (Card Mounting Under HRV)**: Verify HRV cards are mounted under `WorkoutDetailSectionType.HRV` and absent from `ZONES` (`HrvThresholdSummaryCardTest.kt`).
* **TST-UI-527.5 (Telemetry-Gated Zone Comparison)**: Verify power comparison block, deltas, and buttons are hidden when `hasPower == false`, and entire comparison section is omitted when neither power nor HR comparison applies (`HrvThresholdSummaryCardTest.kt`).
* **TST-UI-527.6 (9-Language Parity)**: Verify `workout_detail_tab_hrv` and `settings_workout_card_hrv` exist across all 9 `strings.xml` locales (`TranslationParityTest.kt`).
* **TST-UI-527.7 (Targeted Regression)**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.localization.*"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-527.1` | Unit | `WorkoutDetailTabConfig.DEFAULT_TABS` | `REQ-UI-527.1` | Specified |
| `TST-UI-527.2` | Unit | `WorkoutDetailTabConfig.deserializeTabs` | `REQ-UI-527.2` | Specified |
| `TST-UI-527.3` | Unit | `WorkoutDetailTabConfig.filterVisibleWorkoutDetailTabs` | `REQ-UI-527.3` | Specified |
| `TST-UI-527.4` | Contract | `WorkoutDetailTabbedContentKt` mounting | `REQ-UI-527.4` | Specified |
| `TST-UI-527.5` | UI Contract | `HrvThresholdSummaryCard` comparison gating | `REQ-UI-527.5` | Specified |
| `TST-UI-527.6` | Localization | `TranslationParityTest` (9 locales) | `REQ-UI-527.6` | Specified |
| `TST-UI-527.7` | Regression | Tier 1 Aftermath & Localization tests | `REQ-PRO-001` | Specified |
