# Stage 2: Requirement & Test Specification - ATT-8436: DFA alpha 1 Sparkline Y-Axis Inversion

**Ticket**: [ATT-8436](https://rainerblind.atlassian.net/browse/ATT-8436)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-16_02_Spät`  
**Requirement Mapping**: `REQ-TRK-082` (`REQ-TRK-082.2`)  
**Test Spec ID**: `TST-TRK-082` (`TST-TRK-082.2`, `TST-TRK-082.4`)  
**Branch**: `feature/ATT-8436`  
**Author**: AI Agent 1 (Implementer, `Model: "flash"`, `--as agent1`)  
**Date**: 2026-11-16  

---

## 1. Requirement Specification (`REQ-TRK-082`)

### 1.1 Problem Statement & Rationale
Follow-up improvement from `ATT-7761` sprint review: in DFA alpha 1 sparklines, higher values ($\ge T1$, aerobic/green) are currently drawn at the top and lower values ($< T2$, anaerobic/red) at the bottom. This contradicts intuitive physical exertion dynamics where strain curves (HR, Power) rise upward into red. Inverting the Y-axis ensures the curve rises into red under anaerobic fatigue and falls into green during aerobic recovery.

### 1.2 Attributes
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit / Compose Screenshot

### 1.3 Functional & Architectural Requirements
1. `SensorSparklineView.kt` SHALL accept an `invertYAxis: Boolean = false` parameter (or determine inversion via `thresholds.invertedPolarity == true`).
2. When `invertYAxis == true`, canvas Y coordinates SHALL map `minVal` to top padding (`verticalPaddingPx`) and `maxVal` to bottom (`height - verticalPaddingPx`): `firstY/currentY = verticalPaddingPx + ((val - minVal) / range) * usableHeight` (`REQ-TRK-082.2`).
3. In `buildDynamicZoneVerticalGradientStops`, when `thresholds.invertedPolarity == true`, $0\text{f}$ (top) SHALL map to `Zone4` (red), $1\text{f}$ (bottom) SHALL map to `Zone2` (green), and cutoffs SHALL traverse ascending from $T2$ to $T1$ (`REQ-TRK-082.2`).
4. Both `SensorFieldView.kt` and `EditSensorFieldDialog.kt` SHALL wire `invertYAxis` for `HRV_DFA_A1` across both `DYNAMIC_ZONE` and fixed graph colors.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Canvas Y Inversion)**:
  * *Given* a DFA alpha 1 sample buffer with `minVal = 0.40` and `maxVal = 1.00`,
  * *When* rendered in `SensorSparklineView` with `invertYAxis = true`,
  * *Then* the sample `0.40` is rendered at `verticalPaddingPx` (top) and `1.00` at `height - verticalPaddingPx` (bottom).
* **Criterion 2 (Gradient Stop Order)**:
  * *Given* `buildDynamicZoneVerticalGradientStops` with `invertedPolarity = true` and cutoffs `[0.50f, 0.75f]`,
  * *When* evaluated over span `[0.35f, 1.05f]`,
  * *Then* `stops.first().second` is `TTColor.Zone4` (red at `0f`) and `stops.last().second` is `TTColor.Zone2` (green at `1f`).
* **Criterion 3 (Fixed Color Inversion)**:
  * *Given* an HRV DFA alpha 1 tile with a fixed palette color (e.g. `CRIMSON`),
  * *When* rendered in `SensorFieldView` or `EditSensorFieldDialog`,
  * *Then* the curve remains Y-inverted with low values at top and high values at bottom.

### 1.5 System Invariants
- HR and Power sparklines remain non-inverted with standard Cartesian Y orientation.
- Graceful solid fallback for `samples.size == 1` and `minVal == maxVal` preserved.
- Zero extra allocations in 1-Hz telemetry paths.

---

## 2. Test Specification (`TST-TRK-082`)

### Test Case 1: Inverted Y-Axis Canvas Coordinate Mapping (`TST-TRK-082.2`)
* **Scope**: Unit Test (`SensorSparklineViewTest.kt`)
* **Action**: Verify `computeSparklineY(value, minVal, maxVal, height, padding, invertYAxis = true)`.
* **Expected Result**: `computeSparklineY(minVal, ...)` returns `padding`; `computeSparklineY(maxVal, ...)` returns `height - padding`.

### Test Case 2: Inverted Gradient Stops Ordering (`TST-TRK-082.2`)
* **Scope**: Unit Test (`SensorSparklineViewTest.kt`)
* **Action**: Call `buildDynamicZoneVerticalGradientStops(0.35f, 1.05f, dfaThresholds, lightScheme)`.
* **Expected Result**: Stop `0f` = `TTColor.Zone4` (red); Stop `1f` = `TTColor.Zone2` (green); cutoffs at $T2$ and $T1$ transition monotonically.

### Test Case 3: Visual Screenshot Test (`TST-TRK-082.4`)
* **Scope**: Robolectric Compose Screenshot (`SensorFieldViewDynamicZoneScreenshotTest.kt`)
* **Action**: Render DFA alpha 1 tile (live sample 0.42 rising into red at top right).
* **Expected Result**: Verify Light and Dark themes display inverted Y curve and gradient matching Rule 8.5 checklist.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-082.2` | Unit | `SensorSparklineView`, `buildDynamicZoneVerticalGradientStops` | `REQ-TRK-082.2` | Specified |
| `TST-TRK-082.4` | Screenshot | `SensorFieldViewDynamicZoneScreenshotTest` | `REQ-TRK-082.4` | Specified |
