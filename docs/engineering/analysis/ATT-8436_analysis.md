# Stage 1 Analysis: ATT-8436 - DFA alpha 1 Sparkline Y-Achse invertieren (aerob unten, anaerob oben)

**Ticket**: [ATT-8436](https://rainerblind.atlassian.net/browse/ATT-8436)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-16_02_Spät`  
**Branch**: `feature/ATT-8436`  
**Author**: AI Agent 1 (Implementer, `Model: "flash"`, `--as agent1`)  
**Date**: 2026-11-16  

---

## 1. Problem Statement & Motivation
During the Sprint Review ceremony of `ATT-7761`, a visual polarity discrepancy was identified in the multi-color dynamic zone sparkline: for *DFA alpha 1* (`SensorType.HRV_DFA_A1`), higher values ($\ge T1$, aerobic/green) are plotted at the top, while lower values ($< T2$, anaerobic/red) are plotted at the bottom.
In physical training, higher strain corresponds to visual elevation (HR and Power curves rise upwards towards Red under load). For DFA alpha 1, fatigue drops the metric, making the curve paradoxically plummet into red at the bottom. The Y-axis for DFA alpha 1 must be inverted so the curve rises toward Red (anaerobic, top) and falls toward Green (aerobic, bottom).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Mechanism**: `SensorSparklineView.kt` maps samples to $Y$ via `firstY/currentY = size.height - verticalPaddingPx - ((value - minVal) / range) * usableHeight` and gradient stops via `topColor = resolveSparklineZoneColorForValue(maxVal)` ($0\text{f}$ top) and `bottomColor = resolveSparklineZoneColorForValue(minVal)` ($1\text{f}$ bottom).
- **Independent Peer Review & Verification**:
  - Live inspection of `SensorSparklineView.kt:205-224` and `buildDynamicZoneVerticalGradientStops` confirms Cartesian mapping assigns `maxVal` to top ($Y=0$) and `minVal` to bottom ($Y=\text{height}$).
  - For DFA alpha 1, `maxVal` ($\ge T1$, e.g. 1.05) is green (`Zone2`), placing Green at the top, while `minVal` ($< T2$, e.g. 0.35) is red (`Zone4`), placing Red at the bottom.
  - Call sites: In `SensorFieldView.kt:488` and `EditSensorFieldDialog.kt:450`, `SensorSparklineView` does not currently receive an `invertYAxis` flag or check `sensorType == SensorType.HRV_DFA_A1` for fixed-color rendering.

---

## 3. User Scope Grounding (ATT-1250)
- **In-Scope Goals**:
  1. Add `invertYAxis: Boolean = false` to `SensorSparklineView` (triggered when `isDfaA1 || zoneThresholds?.invertedPolarity == true`).
  2. Invert curve Y coordinates (`firstY/currentY = verticalPaddingPx + ((value - minVal) / range) * usableHeight`) when `invertYAxis == true`.
  3. Invert `buildDynamicZoneVerticalGradientStops` when `thresholds.invertedPolarity == true`: map `minVal` (red, high strain) to $0\text{f}$ (top) and `maxVal` (green, low strain) to $1\text{f}$ (bottom), traversing cutoffs from $T2$ to $T1$.
  4. Ensure consistent inversion in `SensorFieldView.kt`, `EditSensorFieldDialog.kt` (dynamic and fixed graph colors), and update unit/screenshot tests (`Rule 8.5`).
- **Out-of-Scope Non-Goals (Scope Bounding)**:
  - No changes to DFA alpha 1 calculation algorithms, thresholds ($T1$, $T2$), or telemetry database schemas.
  - No changes to standard polarity sensors (HR, Power, Cadence, Speed).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
- **Original Requirement ID & Target**: `REQ-TRK-082` (`REQ-TRK-082.2`) in `docs/requirements/req_tracking_cockpit.md`.
- **Historical Origin & Commit Trace**: Introduced in Sprint `2026-11-16_02_Spät` via `ATT-7761` (`9b5bfaef`).
- **Root Reason for Existing Formulation**: Assumed standard Cartesian $Y$-mapping for all sensors; inverted polarity was only partially handled for color index resolution without inverting spatial canvas coordinates.
- **Preservation of Core Invariants**: Spatial Y inversion restores perceptual consistency with HR/Power curves (rising into red strain zone). Mathematical values, threshold cutoffs, and artifact gating are 100% preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)
- **Affected Requirements**: `REQ-TRK-082` (`REQ-TRK-082.2`), `TST-TRK-082` (`TST-TRK-082.2`).
- **Affected Architecture Elements**: `SensorSparklineView.kt`, `SensorFieldView.kt`, `EditSensorFieldDialog.kt`.
- **Verification Impact & Test Surface**: `SensorSparklineViewTest.kt`, `SensorFieldViewDynamicZoneScreenshotTest.kt`, `TrackingViewModelDynamicZoneSparklineTest.kt`.
- **Risk Assessment & Invariants**: LOW risk. Localized Jetpack Compose canvas coordinate inversion; zero background service or database impact.

---

## 6. Architectural Strategy & High-Level Solution
1. **Canvas Coordinate Inversion**: In `SensorSparklineView.kt`, when `invertYAxis` is true, invert normalized sample offset: `verticalPaddingPx + ((value - minVal) / range) * usableHeight`.
2. **Gradient Stop Inversion**: In `buildDynamicZoneVerticalGradientStops`, when `thresholds.invertedPolarity == true`, anchor $0\text{f}$ (top) to `resolveSparklineZoneColorForValue(minVal)` (red) and $1\text{f}$ (bottom) to `resolveSparklineZoneColorForValue(maxVal)` (green), projecting cutoffs ascending from $T2$ to $T1$.
3. **Caller Integration**: Wire `invertYAxis = (fieldState.sensorType == SensorType.HRV_DFA_A1 || fieldState.sparklineZoneThresholds?.invertedPolarity == true)` in `SensorFieldView.kt` and `EditSensorFieldDialog.kt`.

---

## 7. System Invariants & Risk Assessment
- **Core Invariants**:
  1. Standard sensor sparklines (HR, Power) remain strictly non-inverted.
  2. Single-sample and flat-line fallbacks remain graceful without divide-by-zero crashes.
  3. Visual screenshot test captures inverted DFA alpha 1 sparkline per Rule 8.5.
- **Risk Rating**: **LOW** (Scope: 1 SP localized Compose canvas rendering enhancement).
