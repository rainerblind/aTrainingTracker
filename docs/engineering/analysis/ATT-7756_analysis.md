# Stage 1 Analysis: ATT-7756 - Extract dedicated HRV tab in Workout Details and hide Power zone comparison when workout has no power data

**Ticket**: [ATT-7756](https://rainerblind.atlassian.net/browse/ATT-7756)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.6` | **Branch**: `feature/ATT-7756`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
Currently, post-workout HRV analytics (`HrvAlpha1ScatterCard`, `HrvThresholdSummaryCard`, `HrvDurabilityCard`) are bundled into `WorkoutDetailSectionType.ZONES` (`WorkoutDetailTabbedContent.kt:930-967`), cluttering the standard HR/Power zone distribution tab. In addition, `HrvThresholdSummaryCard.kt:460-763` gates its *Trainingszonen-Vergleich* solely on `configuredFtp != null` / `configuredHrMax != null`, rather than verifying telemetry data in `points`. Consequently, running or cycling workouts without power meter display misleading Coggan Power headers, FTP comparison deltas, consensus badges, and disabled update buttons.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
1. **Conflated Tab Assignment**: `WorkoutDetailSectionType` lacks an `HRV` constant. `WorkoutDetailTabbedContent.kt` conditionally renders all three HRV cards directly under `WorkoutDetailSectionType.ZONES`.
2. **Missing Telemetry Gating**: `HrvThresholdSummaryCard.kt` computes `hasPower = remember(points) { points.any { it.power != null && it.power > 0 } }` (line 111) but only checks profile `configuredFtp > 0` for rendering the power comparison section, consensus status, and zone update button.
* **Independent Peer Review & Verification**:
  - Confirmed `TelemetryMetricUtils.hasHrvData(activeScrubPath)` (`TelemetryMetricUtils.kt:127`) is already implemented and reliable.
  - Confirmed `TrackOnMapScreen.kt:244` passes flags into `WorkoutDetailSectionAvailability.fromWorkoutState` without `hasHrvData`.
  - Confirmed `HrvThresholdSummaryCard.kt:683-761` renders Power consensus badge (`hrv_consensus_badge`) and "Leistungszonen aktualisieren" button (`hrv_btn_update_power_zones`) even when `hasPower == false`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Add `HRV` to `WorkoutDetailSectionType`, `WorkoutSectionType`, and `WorkoutDetailTabPreset.HRV(R.string.workout_detail_tab_hrv)` across all 9 languages.
  - Update `WorkoutDetailTabConfig.DEFAULT_TABS` to 5 canonical tabs (inserting `tab_hrv` between `tab_zones` and `tab_laps_segments`) and auto-migrate persisted wire configurations lacking `HRV`.
  - Wire `hasHrvData` through `WorkoutDetailSectionAvailability` and move the 3 HRV cards from `ZONES` to `HRV` in `WorkoutDetailTabbedContent.kt`.
  - Gate Power comparison (`showPowerComparison = hasPower && configuredFtp != null && configuredFtp > 0`) and HR comparison (`showHrComparison = hasHr && configuredHrMax != null && configuredHrMax > 0`) in `HrvThresholdSummaryCard.kt`.
* **Out-of-Scope Non-Goals**:
  - No changes to threshold estimation math (`HrvThresholdEstimator`) or consensus logic (`HrvZoneComparisonMath`).
  - No reordering of the 3 HRV cards (`HrvAlpha1ScatterCard` -> `HrvThresholdSummaryCard` -> `HrvDurabilityCard`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-408.1` (Default Tab Archetype), `REQ-PER-036.5` (Threshold Card Host), `REQ-PER-037.4` (Zone Comparison Section).
* **Historical Origin & Commit Trace**: `ATT-3920` (Sprint 2026-44.1, 4-tab model), `ATT-3554` (Sprint 2026-46.4, HRV cards placed under `ZONES`), `ATT-3555` (Sprint 2026-46.5, zone comparison section).
* **Root Reason for Existing Formulation**: Dedicated `tab_hrv` did not exist during Sprint 2026-46.4, so HRV cards were temporarily colocated with training zones. `REQ-PER-037.4` assumed power was available if FTP was configured.
* **Preservation of Core Invariants**: Wire backward compatibility (auto-migration in `deserializeTabs`), gesture isolation invariants, map-at-index-0 invariant, and 100% localization parity across 9 languages.

---

## 5. Change-Request Impact Analysis (SUP.10 / REQ-PRO-064)
* **Affected Requirements**: `REQ-UI-408.1`, `REQ-PER-036.5`, `REQ-PER-037.4`.
* **Affected Architecture Elements**: `WorkoutDetailTabConfig.kt`, `WorkoutSectionType.kt`, `WorkoutDetailTabbedContent.kt`, `TrackOnMapScreen.kt`, `HrvThresholdSummaryCard.kt`, `strings*.xml`.
* **Verification Impact & Test Surface**: `WorkoutDetailTabsTest.kt`, `HrvThresholdSummaryCardTest.kt`, `TranslationParityTest.kt`, `verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: Minimal regression risk; existing user configs migrate cleanly; empty `tab_hrv` hides automatically on non-HRV workouts.

---

## 6. Architectural Strategy & High-Level Solution
1. **Enum & Config Extension**: Add `HRV` enum to `WorkoutDetailSectionType`, `WorkoutSectionType`, and `WorkoutDetailTabPreset.HRV(R.string.workout_detail_tab_hrv)`. Expand `DEFAULT_TABS` to 5 tabs. In `deserializeTabs`, if `HRV` is absent from all tabs, inject `tab_hrv` directly after `tab_zones`.
2. **Availability & Mounting**: Add `hasHrv: Boolean` to `WorkoutDetailSectionAvailability`. Feed from `TelemetryMetricUtils.hasHrvData(activeScrubPath)` in `TrackOnMapScreen.kt`. Move HRV cards in `WorkoutDetailTabbedContent.kt` to `WorkoutDetailSectionType.HRV`.
3. **Telemetry-Gated Comparison**: In `HrvThresholdSummaryCard.kt`, gate Power section, power consensus badge, and power update button on `showPowerComparison`; gate HR block on `showHrComparison`; omit entire section if neither applies.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: Zero regressions in tab persistence; map remains pinned at index 0; zero layout shifts on workouts lacking HRV/power; 9-language translation parity.
* **Risk Rating**: **LOW** (UI layout partitioning and display gating; no data schema or mathematical estimation changes).
