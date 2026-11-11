# Stage 1 Analysis: ATT-6715 - DFA a1 Thresholds UI, Artifact Warning Icon & HR ZoneDisplayOptions Parity

**Ticket**: [ATT-6715](https://rainerblind.atlassian.net/browse/ATT-6715)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6715` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
Sprint `2026-46.1` follow-up of ATT-3542 revealed five UI, formatting, and configuration gaps in live HRV tracking:
1. **Uncalled Threshold Setters & Missing UI**: `TuningPreferencesDataStore.kt` provides `setDfaA1ThresholdT1`, `setDfaA1ThresholdT2`, and `setDfaA1Thresholds`, but they have **zero callers** across `app/src/main/`. No controls exist in `AdvancedTuningDialog` / `PhysiologyModelsSection` for athlete customization, and the ordering invariant $T_1 > T_2$ (e.g. $T_1 \ge T_2 + 0.05f$) is unenforced.
2. **Hardcoded Thresholds in Info Sheet**: `HrvInfoBottomSheet.kt` hardcodes static `0.75` and `0.50` text for Zone 2/3/4 descriptions, diverging when athletes configure custom physiological thresholds.
3. **Artifact Suppression Ambiguity**: In `SensorFieldView.kt`, initial rolling-window warm-up and artifact-gated suppression ($>5.0\%$ artifacts or strap dropouts) both show plain `--.--`. Athletes cannot tell if the algorithm is warming up or if the chest strap needs re-wetting/re-positioning.
4. **Unconditional Field Exposure**: `ActivityType.getSensorTypeArray()` unconditionally appends HRV metrics to all profiles (including non-HR profiles like `GENERIC`, `RUN_SPEED`, `BIKE_SPEED_AND_CADENCE`), cluttering the field selector.
5. **Formatting & Zone Options Parity**: `HRV_RMSSD` in `SensorType.java` uses `DefaultNumberFormatter` (`0.0 ms`) instead of integer milliseconds (`0 ms`, `IntegerFormatter`). `TrackingViewModel.getZoneType` omits `HRV_DFA_A1`, causing tiles to lose the athlete's configured HR `ZoneDisplayOptions` (background, left/right bar, text color, zone gauge).

---

## 2. Root Cause Analysis & Independent Forensic Verification
* **Settings & Preferences**: In `TuningPreferencesDataStore.kt:1282-1289`, `clampedDfaA1T1` and `clampedDfaA1T2` clamp to `[MIN, MAX]` independently without checking $T_1 \ge T_2 + 0.05f$. In `PhysiologyModelsSection.kt`, only models 1–5 are present; DFA $\alpha_1$ threshold sliders are missing.
* **Info Sheet**: In `HrvInfoBottomSheet.kt:98`, `R.string.hrv_info_zones_desc` is hardcoded with literal 0.75 and 0.50 numbers instead of parameterizing active thresholds. It also lacks `@Preview` composables.
* **Cockpit Tile Artifact Indicator**: `SensorFieldState` lacks an `isArtifactGated` boolean flag. When `HRV_ARTIFACT_RATE > 5.0%` (or `HrvMetricsDevice.isArtifactGated`), `SensorFieldView.kt` renders `--.--` identically to uninitialized data without an amber warning icon.
* **Activity Profile Pollution**: `ActivityType.java:210-218` appends HRV sensor types unconditionally without verifying `SensorType.HR` in the profile or `DevicesDatabaseManager.haveHeartRateDevice()`.
* **SensorType Formatting & Zone Options**: `SensorType.HRV_RMSSD` specifies `DefaultNumberFormatter` instead of `IntegerFormatter`. `TrackingViewModel.getZoneType` only maps `HR` and `POWER`, returning `null` for `HRV_DFA_A1`, which defaults to empty `ZoneDisplayOptions`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Add DFA $\alpha_1$ threshold controls ($T_1$ default `0.75`, $T_2$ default `0.50`) in `PhysiologyModelsSection.kt` / `AdvancedTuningScreen.kt` with scale-direction hints and $T_1 \ge T_2 + 0.05f$ validation in `TuningPreferencesDataStore.kt`.
  * Parameterize `HrvInfoBottomSheet.kt` with active $T_1 / T_2$ thresholds and add Light/Dark/AMOLED `@Preview` composables wrapped in `TrainingTrackerTheme`.
  * Add `isArtifactGated: Boolean` to `SensorFieldState`, surface from `TrackingViewModel.kt` when artifact rate $>5.0\%$, and render a subtle warning icon next to `--.--` in `SensorFieldView.kt`.
  * Gate HRV fields in `ActivityType.getSensorTypeArray()` on HR presence or paired HR device.
  * Switch `HRV_RMSSD` to `IntegerFormatter` and map `HRV_DFA_A1` in `TrackingViewModel.getZoneType` to inherit HR `ZoneDisplayOptions`.
* **Out-of-Scope Non-Goals**:
  * Changing artifact filter math (`RrArtifactFilter.kt`) or FIT import/export codecs.
  * Modifying post-workout scatter plots (`HrvAlpha1ScatterChart.kt`) or morning readiness baselines.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID**: `REQ-TRK-061` (`REQ-TRK-061.1`..`REQ-TRK-061.5`) in `docs/requirements/req_tracking_cockpit.md`.
* **Historical Origin**: Introduced in Sprint `2026-46.1` (`ATT-3542`, commit `4bad7272`).
* **Root Reason for Existing Formulation**: ATT-3542 established live tile rendering and basic zone colors. Threshold editing was deferred to backend datastore keys; `ActivityType` appended sensors globally during prototype bring-up.
* **Preservation of Core Invariants**: Population defaults ($0.75 / 0.50$) remain unchanged; theme color tokens (`TTColor.Zone2`, `Zone3`, `Zone4`) preserved; Rule 71 and Rule 72 compliance strictly maintained.

---

## 5. Architectural Strategy & High-Level Solution
1. **Threshold Settings & Invariant**: In `TuningPreferencesDataStore.kt`, enforce `t1 >= t2 + 0.05f` in `setDfaA1Thresholds`. In `PhysiologyModelsSection.kt`, add threshold sliders ($T_1 \in [0.60, 0.90]$, $T_2 \in [0.40, 0.70]$) with scale-direction hints ("Higher = more fractal / aerobic").
2. **Dynamic Info Sheet**: Pass $T_1 / T_2$ into `HrvInfoBottomSheet.kt` and format zone descriptions dynamically. Add `@Preview` functions for Light, Dark, and AMOLED themes.
3. **Artifact-Gated Warning Indicator**: Add `isArtifactGated: Boolean = false` to `SensorFieldState`. In `TrackingViewModel.kt`, set `isArtifactGated = true` when metric is null and `artifactRate > 5.0%`. In `SensorFieldView.kt`, render a small warning icon beside `--.--`.
4. **Profile Gating**: In `ActivityType.getSensorTypeArray()`, append HRV sensors only if `sensors` contains `SensorType.HR` or `DevicesDatabaseManager.haveHeartRateDevice()`.
5. **Formatter & ZoneDisplayOptions Parity**: Set `SensorType.HRV_RMSSD` formatter to `new IntegerFormatter()`. In `TrackingViewModel.getZoneType`, map `HRV_DFA_A1` to the sport's HR zone type (`HR_RUN` / `HR_BIKE`).

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero regressions in tracking loop performance; backward-compatible settings persistence; strict ThemeColor centralization (Rule 71).
* **Risk Rating**: **LOW**. Strictly confined to UI presentation, formatting, and preferences validation with zero database migrations or background service threading alterations.
