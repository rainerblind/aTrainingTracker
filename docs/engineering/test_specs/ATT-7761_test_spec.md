# Stage 2: Requirement & Test Specification - ATT-7761: Decoupled Multi-Color Dynamic Zone Sparkline Rendering

**Ticket**: [ATT-7761](https://rainerblind.atlassian.net/browse/ATT-7761)  
**Sub-task**: [[Req & Test Spec]](https://rainerblind.atlassian.net/browse/ATT-7761)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1` | **Branch**: `feature/ATT-7761`  
**Requirement Mapping**: `REQ-TRK-082` | **Test Spec ID**: `TST-TRK-082`  
**Author**: AI Agent 2 (`agent2`, `subagent_flash`) | **Date**: 2026-11-16

---

## 1. Requirement Specification (`REQ-TRK-082`)

### 1.1 Problem Statement & Rationale
Currently `resolveSparklineLineColor` (`SensorSparklineView.kt`) retains a legacy `showTextColor` fallback (`sensorType == null`), callers (`SensorFieldView.kt`, `EditSensorFieldDialog.kt`) pass `showTextColor`, and `"DYNAMIC_ZONE"` resolves only to a single instantaneous `fieldState.zoneColor`. Decoupling sparkline color resolution completely from `showTextColor` and rendering threshold-mapped `Brush.verticalGradient` segments for HR, Power, and HRV DFA α1 gives athletes historical zone context across the full sparkline horizon with zero extra `List<Float>` ring-buffer memory (ATT-7761, refining `REQ-TRK-081` & `REQ-TRK-059`).

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. **Complete `showTextColor` Decoupling (`REQ-TRK-082.1`)**: `resolveSparklineLineColor` (`SensorSparklineView.kt`) SHALL NOT accept or depend on `showTextColor` across any resolution branch (`GLOBAL_DEFAULT`, `THEME_DEFAULT`, `DYNAMIC_ZONE`, or palette tokens); callers (`SensorFieldView.kt`, `EditSensorFieldDialog.kt`) SHALL NOT pass `showTextColor`. Numeric text coloring remains solely governed by `zoneDisplayOptions.showTextColor`.
2. **Multi-Color Historical `Brush.verticalGradient` & Terminal Dot (`REQ-TRK-082.2`)**:
   - `SensorFieldState` SHALL carry optional zone threshold stops without changing `List<Float>` ring buffers or adding per-sample color allocations.
   - `SensorSparklineView.kt` SHALL render `Brush.verticalGradient` stroke + fill with monotonically non-decreasing stops in `[0f, 1f]` mapped onto `[minVal, maxVal]` (`maxVal -> 0f` top, `minVal -> 1f` bottom within vertical padding) and tint the terminal dot to the latest sample's zone:
     - **HR (`SensorType.HR`)**: 4 cutoffs (`HR_RUN`/`HR_BIKE`) → `TTColor.Zone1..Zone5`.
     - **Power (`SensorType.POWER` family, excluding `POWER_TO_WEIGHT`)**: 6 cutoffs (`PWR_BIKE`) → `TTColor.Zone1..Zone7`; `POWER_TO_WEIGHT` (W/kg) MUST NOT receive raw Watt cutoffs.
     - **HRV DFA α1 (`SensorType.HRV_DFA_A1`)**: inverted polarity (`alpha1 > t1` → `TTColor.Zone2` green top; `t2 <= alpha1 <= t1` → `TTColor.Zone3` orange middle; `alpha1 < t2` → `TTColor.Zone4` red bottom); suppressed to solid fallback on `SMOOTHED_STREAM` or `QUANTIZED_SYNTHETIC`.
   - `EditSensorFieldDialog.kt` preview SHALL supply representative zone stops when `DYNAMIC_ZONE` is selected.
3. **Dark/AMOLED `Zone7` Contrast & Edge-Case Fallbacks (`REQ-TRK-082.3`)**: On dark/AMOLED surfaces (`isDarkSurface == true`), `TTColor.Zone7` (`#000000`) MUST substitute a high-contrast color (e.g., `colorScheme.onSurface`). `SensorSparklineView.kt` MUST fall back gracefully to solid `lineColor` when `samples.size == 1`, `range == 0f`, `isPlaceholder == true`, or thresholds are absent.
4. **Visual Compose Screenshot Verification (`REQ-TRK-082.4`, Rule 8.5)**: Capture `docs/engineering/walkthroughs/img/ATT-7761_dynamic_zone_sparkline.png` covering Light & Dark/AMOLED HR, Power (incl. Zone 7), DFA α1, decoupled fixed color, and placeholder/flat fallbacks.

### 1.4 Acceptance Criteria & Invariants
* *Given* an HR tile with `showTextColor = true` and fixed palette color, *When* in Zone 4, *Then* text tints to Zone 4 while sparkline stays in its fixed color (and `sensorType == null` ignores `showTextColor`).
* *Given* HR, Power (excluding `POWER_TO_WEIGHT`), or valid DFA α1 tile with `graphColor = "DYNAMIC_ZONE"` and `showTextColor = false`, *When* samples span multiple zones, *Then* stroke, fill, and terminal dot render threshold-aligned multi-color zone segments while numeric text stays `onSurface`.
* *Given* `isPlaceholder = true`, `samples.size == 1`, `minVal == maxVal`, `POWER_TO_WEIGHT`, or `SMOOTHED_STREAM`/`QUANTIZED_SYNTHETIC` DFA α1, *When* rendered, *Then* solid/dashed fallback renders with zero divide-by-zero or invalid gradient stop errors.
* *Invariants*: Zero ring-buffer or SQLite v22 schema changes; monotonically non-decreasing stops in `[0f, 1f]`; zero Compose snapshot state writes during composition.

---

## 2. Test Specification (`TST-TRK-082`)

1. **`TST-TRK-082.1` (`SensorSparklineViewTest.kt`)**: Verify `resolveSparklineLineColor` decouples from `showTextColor` across all paths (`sensorType == null`, `GLOBAL_DEFAULT`, `THEME_DEFAULT`, `DYNAMIC_ZONE`, palette tokens).
2. **`TST-TRK-082.2` (`SensorSparklineViewTest.kt`)**: Verify HR (4 cutoffs → Z1..5), Power (6 cutoffs → Z1..7), dark/AMOLED `Zone7` contrast fallback, DFA α1 inverted polarity (`alpha1 > t1` green top .. `alpha1 < t2` red bottom), monotonically non-decreasing `[0f, 1f]` stops, terminal dot zone color, and solid fallback on `samples.size == 1`, `range == 0f`, `isPlaceholder == true`, and null thresholds.
3. **`TST-TRK-082.3` (`TrackingViewModelDynamicZoneSparklineTest.kt`)**: Verify `TrackingViewModel` projects `HR_RUN`/`HR_BIKE`, `PWR_BIKE`, and `HRV_DFA_A1` stops onto `SensorFieldState`, guards `POWER_TO_WEIGHT` against Watt cutoffs, and suppresses DFA α1 zones on `SMOOTHED_STREAM` / `QUANTIZED_SYNTHETIC`.
4. **`TST-TRK-082.4` (`SensorFieldViewDynamicZoneScreenshotTest.kt`, Rule 8.5)**: Capture `docs/engineering/walkthroughs/img/ATT-7761_dynamic_zone_sparkline.png` (Light & Dark/AMOLED) and verify against 4-point visual checklist.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-TRK-082.1]` | Unit | Complete `showTextColor` decoupling in `resolveSparklineLineColor` | `REQ-TRK-082.1` | Planned |
| `[TST-TRK-082.2]` | Unit | Gradient stops (HR/Power/DFA α1), `Zone7` dark contrast, terminal dot & edge fallbacks | `REQ-TRK-082.2`, `REQ-TRK-082.3` | Planned |
| `[TST-TRK-082.3]` | ViewModel | Zone cutoff projection, `POWER_TO_WEIGHT` guard & DFA α1 stream suppression | `REQ-TRK-082.2` | Planned |
| `[TST-TRK-082.4]` | Screenshot | Compose PNG capture & 4-point visual checklist (Rule 8.5) | `REQ-TRK-082.4` | Planned |
