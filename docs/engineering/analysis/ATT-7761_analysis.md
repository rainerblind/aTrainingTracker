# Stage 1 Analysis: ATT-7761 - Decoupled multi-color Dynamic Zone sparkline rendering

**Ticket**: [ATT-7761](https://rainerblind.atlassian.net/browse/ATT-7761) | **Sub-task**: `[Analysis]` (Pending `submit-stage`)
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1` | **Branch**: `feature/ATT-7761`
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Problem Statement & Motivation
In `SensorSparklineView.kt` (`ATT-7760`), `"DYNAMIC_ZONE"` resolves only to a single instantaneous `fieldState.zoneColor` (`line 319`), and `resolveSparklineLineColor` retains a `showTextColor` fallback (`line 332`). Athletes cannot view historical zone transitions along HR, Power, or HRV DFA α1 sparklines without changing numeric text coloring, nor see multi-zone progression across the sparkline horizon.

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Gap Analysis**: `SensorFieldState` lacks HR/Power zone cutoffs (only `dfaA1ThresholdT1`/`T2` exist), and `SensorSparklineView` draws stroke, fill, and terminal dot with a single `Color` rather than threshold-mapped `Brush.verticalGradient` stops.
* **Independent Peer Review & Verification** (audited against `sprint/2026-47.1` @ `f62f2283`):
  * *Ticket vs. Live Codebase*: Ticket cites `SensorFieldView.kt` coupling sparkline color to `showTextColor`. Live code (`65d2c2d5`, `ATT-7760`) already delegated resolution to `SensorSparklineView.kt:308-347`; only a legacy `showTextColor` fallback remains when `sensorType == null` (`line 332`), and `SensorFieldView.kt:470` / `EditSensorFieldDialog.kt:441` still pass `showTextColor`.
  * *Zone Threshold Sources*: `TrackingViewModel.kt` (`240-244, 362-364, 975-1002`) holds `zoneCutoffsMap` for `HR_RUN`/`HR_BIKE` (4 cutoffs -> `TTColor.Zone1..5`) and `PWR_BIKE` (6 cutoffs -> `TTColor.Zone1..7`). `SensorFieldState` (`85-86`) already carries `dfaA1ThresholdT1` (`0.75f`) & `dfaA1ThresholdT2` (`0.50f`).
  * *Blind Spots Identified*:
    1. **Inverted DFA α1 Polarity & Gating**: `HrvZoneIndicator.kt:55-73` maps *higher* `alpha1 > t1` to `TTColor.Zone2` (top) and *lower* `alpha1 < t2` to `TTColor.Zone4` (bottom), and suppresses zones (`Color.Transparent`) when `hrvSignalQualityStatus` is `SMOOTHED_STREAM` or `QUANTIZED_SYNTHETIC`.
    2. **Power Family vs. W/kg**: `EditSensorFieldViewModel:168-180` enables `DYNAMIC_ZONE` for `POWER`, `NORMALIZED_POWER`, `POWER_5S..20M`, `POWER_PEAK_*`, and `POWER_TO_WEIGHT`, while `TrackingViewModel.getZoneType` (`1026-1039`) maps only `SensorType.POWER` (Bike). `POWER_TO_WEIGHT` (W/kg) must NOT use Watt cutoffs.
    3. **Dark/AMOLED Zone 7 & Gradient Geometry**: `TTColor.Zone7` is `#000000` (`Color.kt:208`), invisible on dark/AMOLED tiles without contrast fallback. Canvas `y` maps `maxVal -> 0f` (top) and `minVal -> 1f` (bottom) within `[verticalPaddingPx, height - verticalPaddingPx]`; `samples.size == 1`, `range == 0f`, and `isPlaceholder` must bypass multi-stop gradients.

## 3. Scope Grounding & Non-Goals (`ATT-1250`, `2 SP`)
* **In-Scope Goals**:
  1. Remove `showTextColor` coupling from `resolveSparklineLineColor` (`SensorSparklineView.kt`) and callers (`SensorFieldView.kt`, `EditSensorFieldDialog.kt`).
  2. Model threshold stops (`HR` 5-zone, `POWER` 7-zone, `HRV_DFA_A1` 3-zone) in `SensorFieldState` / `TrackingViewModel` and render `Brush.verticalGradient` stroke + fill + terminal dot zone color in `SensorSparklineView.kt` (including `EditSensorFieldDialog` preview when `DYNAMIC_ZONE` is selected).
  3. Confirm Stage 4 `Rule 8.5` Robolectric Compose PNG screenshot (`SensorSparklineView` / Cockpit tiles, e.g. `docs/engineering/walkthroughs/img/ATT-7761_dynamic_zone_sparkline.png`) inspecting light/dark/HR/Power/DFA α1/placeholder rendering.
* **Out-of-Scope Non-Goals**: Changing `List<Float>` ring buffers; altering SQLite v22 schema or color-picker UI (`ATT-7760`); modifying post-workout `TelemetryMetricChart`. Complexity verified at **2 SP** (no split needed).

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-081` (`REQ-TRK-081.2`) in `docs/requirements/req_tracking_cockpit.md` (`5854-5908`), refining `REQ-TRK-059`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-7760` (`65d2c2d5`) for per-tile color persistence and `"DYNAMIC_ZONE"` single-color token resolution.
* **Root Reason for Existing Formulation**: `ATT-7760` focused on DB v22 persistence and editor UI, intentionally deferring multi-stop vertical gradients to `ATT-7761`.
* **Preservation of Core Invariants**: Fixed palette tokens, `THEME_DEFAULT`, `GLOBAL_DEFAULT`, `showTextColor`, and `List<Float>` buffers remain intact; `"DYNAMIC_ZONE"` upgrades cleanly from flat `zoneColor` to threshold-aligned multi-color segments with solid fallback.

## 5. Change-Request Impact Analysis (SUP.10 / `REQ-PRO-064`)
* **Affected Requirements**: Refine `REQ-TRK-081.2` / add `REQ-TRK-081.6` (Dynamic Zone multi-color gradient rendering & decoupling) in `docs/requirements/req_tracking_cockpit.md`.
* **Affected Architecture Elements**: `SensorSparklineView.kt`, `SensorFieldView.kt`, `SensorFieldState.kt`, `TrackingViewModel.kt`, `EditSensorFieldDialog.kt`.
* **Verification Impact & Test Surface**: Extend `SensorSparklineViewTest.kt` (`TST-TRK-081`), `TrackingViewModel` / `SensorFieldView` unit tests, and Robolectric Compose screenshot test (`Rule 8.5`) for `SensorSparklineView` & Cockpit tile rendering.
* **Risk Assessment & Invariants**: `Brush.verticalGradient` requires monotonically non-decreasing stops in `[0f, 1f]` and zero-range guards; `TTColor.Zone7` contrast and DFA α1 polarity inversion must be unit-tested.

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero extra per-sample allocations in `BANALServiceRepository` ring buffers; DB v22 schema unchanged; `isPlaceholder` & `range == 0f` never crash `Brush.verticalGradient`; numeric text color remains controlled solely by `zoneDisplayOptions.showTextColor`.
* **Risk Rating**: **MEDIUM** — Pure UI/ViewModel zone-threshold projection + Compose Canvas gradient math with `Rule 8.5` PNG verification; zero database or service-layer risk.
