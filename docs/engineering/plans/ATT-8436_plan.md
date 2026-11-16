# Stage 3: Implementation Plan - ATT-8436: DFA alpha 1 Sparkline Y-Axis Inversion

**Ticket**: [ATT-8436](https://rainerblind.atlassian.net/browse/ATT-8436)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-16_02_Spät`  
**Requirement Mapping**: `REQ-TRK-082` (`REQ-TRK-082.2`)  
**Test Mapping**: `TST-TRK-082` (`TST-TRK-082.2`, `TST-TRK-082.4`)  
**Branch**: `feature/ATT-8436`  
**Author**: AI Agent 1 (Implementer, `Model: "flash"`, `--as agent1`)  
**Date**: 2026-11-16  

---

## 1. Problem Description & Background
In accordance with user feedback from `ATT-7761` sprint review, the Y-axis of the DFA alpha 1 sparkline must be inverted so that aerobic values ($\ge T1$, green) are drawn at the bottom and anaerobic values ($< T2$, red) are drawn at the top. This aligns the visual exertion direction with HR and Power sparklines (curves rise upwards into red under fatigue and drop downwards into green during recovery).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-082` (`REQ-TRK-082.2` Multi-Color Historical Zone Curve)
* **Test Mapping**: `TST-TRK-082` (`TST-TRK-082.2` Gradient & Coordinate Unit Tests, `TST-TRK-082.4` Visual Screenshot)

---

## 3. System Invariants & Preserved Behavior
1. **Standard Metrics Unaffected**: HR, Power, Cadence, and Speed sparklines remain strictly non-inverted.
2. **Submodule Immutability**: `ANT-Android-SDKs` remains untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
3. **Allocation Neutrality**: Zero extra allocations in 1-Hz telemetry paths or sample buffers.
4. **Boundary Gracefulness**: Single-point (`samples.size == 1`), flat-line (`minVal == maxVal`), and placeholder modes fall back without divide-by-zero crashes.

---

## 4. Proposed Architectural Changes
* **`SensorSparklineView.kt`**: Expose `computeSparklineY` helper and `invertYAxis: Boolean = false` parameter in `SensorSparklineView`; invert canvas Y coordinate mapping and gradient stop order in `buildDynamicZoneVerticalGradientStops` when `invertedPolarity == true`.
* **`SensorFieldView.kt` & `EditSensorFieldDialog.kt`**: Pass `invertYAxis = (sensorType == SensorType.HRV_DFA_A1 || thresholds?.invertedPolarity == true)` across both dynamic zone and fixed-color modes.
* **UI Consistency**: Follows Jetpack Compose M3 guidelines; no new controls or styling required.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Canvas Coordinate & Gradient Stop Inversion (`REQ-TRK-082.2`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorSparklineView.kt`
* **Changes**:
  - Add `computeSparklineY(value, minVal, maxVal, height, padding, invertYAxis)` helper function.
  - Add `invertYAxis: Boolean = false` to `SensorSparklineView` and apply to `firstY` / `currentY`.
  - In `buildDynamicZoneVerticalGradientStops`, when `thresholds.invertedPolarity == true`:
    - Top ($0\text{f}$) = `resolveSparklineZoneColorForValue(minVal)` (red, Zone 4).
    - Bottom ($1\text{f}$) = `resolveSparklineZoneColorForValue(maxVal)` (green, Zone 2).
    - Traverse cutoffs ascending from $T2$ to $T1$ with `fraction = (cutoff - minVal) / range`.

### Step 2: Caller Integration for Cockpit & Preview Dialog (`REQ-TRK-082.2`)
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldView.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt`
* **Changes**: Pass `invertYAxis = (fieldState.sensorType == SensorType.HRV_DFA_A1 || sparklineZoneThresholds?.invertedPolarity == true)` to `SensorSparklineView`.

### Step 3: Unit Tests & Compose Screenshot Re-generation (`TST-TRK-082.2`, `TST-TRK-082.4`)
* **Files**:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorSparklineViewTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldViewDynamicZoneScreenshotTest.kt`
* **Changes**:
  - Add unit tests for `computeSparklineY` with inverted Y mapping and updated gradient stops ($0\text{f}$ red, $1\text{f}$ green).
  - Execute screenshot test to re-generate `ATT-7761_dynamic_zone_sparkline.png` with inverted DFA alpha 1 sparkline.

### Step 4: Verification & Governance Audit
* **Command**: `./gradlew testDebugUnitTest --tests "*SensorSparklineViewTest*" --tests "*SensorFieldViewDynamicZoneScreenshotTest*"`
* **Governance**: `python3 tools/verify_requirement_governance.py --check-code-orphans --check-req-description --verify-sys-trace --check-reverse-sys-trace`

---

## 6. Verification & Rollback Plan
* **Verification**: Unit tests confirm mathematical Y-inversion; visual `read_file` verifies inverted tile layout.
* **Rollback**: Clean git commit isolation enables clean rollback if visual regressions emerge.
