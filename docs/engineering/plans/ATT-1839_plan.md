# Stage 3: Implementation Plan - ATT-1839: Background Training Zone Bands & Right-Hand Zone Axis for Heart Rate and Power Graphs

**Ticket**: [ATT-1839](https://rainerblind.atlassian.net/browse/ATT-1839)  
**Sub-task**: [ATT-1945](https://rainerblind.atlassian.net/browse/ATT-1945) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Requirement Mapping**: `REQ-UI-230`  
**Test Mapping**: `TST-UI-184`  
**Branch**: `feature/ATT-1839`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Architecture Overview (SWE.2)

This plan implements subtle horizontal training zone background bands and a right-hand secondary zone axis (`Z1`–`Z5`) in `TelemetryMetricGraph.kt` for Heart Rate and Cycling Power telemetry curves.

```
┌────────────────────────────────────────────────────────────────────────┐
│                   MapDetailLayout / WorkoutSummary                     │
│                                                                        │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │                 TelemetryMetricGraph.kt                        │   │
│   │                                                                │   │
│   │  Left Axis        Plot Area (start=50dp, end=25dp)   Right Axis│   │
│   │  (Numeric)       ┌───────────────────────────────┐   (Z1-Z5)   │   │
│   │   185 bpm        │ Z5 (0.10 alpha, TTColor.Zone5)│     Z5      │   │
│   │   - - - - - - - -│- - - - - - - - - - - - - - - -│- - - - - -  │   │
│   │   165 bpm        │ Z4 (0.10 alpha, TTColor.Zone4)│     Z4      │   │
│   │   - - - - - - - -│- - - - - - - - - - - - - - - -│- - - - - -  │   │
│   │   145 bpm        │ Z3 (0.10 alpha, TTColor.Zone3)│     Z3      │   │
│   │   - - - - - - - -│- - - - - - - - - - - - - - - -│- - - - - -  │   │
│   │   125 bpm        │ Z2 (0.10 alpha, TTColor.Zone2)│     Z2      │   │
│   │   - - - - - - - -│- - - - - - - - - - - - - - - -│- - - - - -  │   │
│   │   105 bpm        │ Z1 (0.10 alpha, TTColor.Zone1)│     Z1      │   │
│   │                  │     Primary Solid Curve       │             │   │
│   │                  └───────────────────────────────┘             │   │
│   └────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────┘
                                    │
                         uses pure math & geometry
                                    ▼
                 ┌───────────────────────────────────────┐
                 │       TelemetryZoneMath.kt            │
                 │  - calculateHeartRateZoneBands(...)   │
                 │  - calculatePowerZoneBands(...)       │
                 │  - calculateThresholdDashes(...)      │
                 │  - shouldRenderZoneLabel(...)         │
                 │  - determineZoneIndex(...)            │
                 └───────────────────────────────────────┘
```

---

## 2. Invariants & Safety Guarantees

1. **Horizontal Alignment Invariant with ElevationProfile**:
   `startPaddingPx = 50.dp` and `endPaddingPx = 25.dp` MUST NOT be altered under any circumstances. Right-hand zone labels (`Z1`–`Z5`) MUST be drawn strictly within the existing 25.dp right padding margin (`startPaddingPx + chartWidthPx + 4.dp` to `startPaddingPx + chartWidthPx + endPaddingPx`).
2. **Speed and Pace Graphs Isolation**:
   `SPEED` and `PACE` metric graphs MUST NOT display zone bands, boundary dashes, or right-hand zone axes.
3. **Solid Primary Curve Stroke**:
   The primary metric stroke remains uniform solid (Red for HR, Dark Violet for Power), guaranteeing visual separation from the gradient-colored elevation profile.
4. **Defensive Threshold Fallback**:
   Missing, zero, or corrupt thresholds must be handled gracefully without throwing unhandled exceptions, omitting bands cleanly.
5. **No Database Schema Mutations**:
   Purely UI-level rendering enhancements with zero SQLite migrations.

---

## 3. Atomic Step-by-Step Implementation Sequence

### Step 1: Create Pure Geometry & Math Engine (`TelemetryZoneMath.kt`)
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryZoneMath.kt`
- **Details**:
  - Define `data class TelemetryZoneBand(val zoneIndex: Int, val minVal: Double, val maxVal: Double, val color: Color, val label: String)`.
  - Implement `calculateHeartRateZoneBands(thresholds, dataMin, dataMax)`.
  - Implement `calculatePowerZoneBands(thresholds, dataMin, dataMax)`.
  - Implement `calculateThresholdDashes(thresholds, dataMin, dataMax)`.
  - Implement `shouldRenderZoneLabel(bandHeightPx, minHeightPx)`.
  - Implement `determineZoneIndex(value, thresholds)`.
  - Export zone colors `ZONE_COLORS` using `TTColor.Zone1`..`TTColor.Zone5`.

### Step 2: Author Pure Math Engine Unit Tests (`TelemetryZoneMathTest.kt`)
- **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryZoneMathTest.kt`
- **Details**:
  - Test full 5-zone range partitioning for HR and Power.
  - Test partial range clamping (e.g. $[130, 160]$ with thresholds $120, 140, 160, 180$).
  - Test threshold dash calculation.
  - Test zone label height clearance gate ($\ge 12\text{ dp}$).
  - Test zone index classification ($1..5$).

### Step 3: Extend `TelemetryMetricGraph.kt` with Zone Backgrounds & Right Axis
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt`
- **Details**:
  - Add optional parameters:
    `hrZoneThresholds: HeartRateZoneThresholds? = null`,
    `powerZoneThresholds: PowerZoneThresholds? = null`.
  - In composable body, defensively resolve thresholds via `LocalContext.current` and `SettingsDataStoreJavaHelper.getZoneMax` if not provided.
  - In Canvas:
    - If `metricType == TelemetryMetricType.HEART_RATE` or `TelemetryMetricType.POWER`:
      - Draw 5 horizontal rectangles with `alpha = 0.10f` behind the curve.
      - Draw horizontal dashed guidelines at `z1Max`..`z4Max` using `outlineVariant.copy(alpha = 0.35f)`.
      - Draw right-hand `Z1`–`Z5` labels vertically centered within visible bands in the right 25.dp margin.
  - When scrubbing (`currentDistance != null`), format the readout tooltip or dot with zone tag (e.g. `165 bpm • Z4`).

### Step 4: Enhance Multi-Metric Scrubbing Badge (`ElevationProfile.kt`)
- **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
- **Details**:
  - In `ScrubbingTelemetryBadge`, resolve active HR/Power zone if thresholds exist, displaying `${point.hr} bpm • Z$zone` and `${point.power} W • Z$zone`.

### Step 5: Author Composable & Invariant Contract Tests (`TelemetryMetricGraphZoneTest.kt`)
- **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphZoneTest.kt`
- **Details**:
  - Verify `HEART_RATE` and `POWER` graphs enable zone bands and right axis.
  - Verify `SPEED` and `PACE` graphs do NOT render zone bands or right axis.
  - Verify padding invariance: `startPaddingPx = 50.dp` and `endPaddingPx = 25.dp`.
  - Verify scrubbing format contract.

### Step 6: Targeted Test Verification
- Run:
  ```bash
  ./gradlew testDebugUnitTest \
    --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryZoneMathTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphZoneTest"
  ```

### Step 7: Gate 3 Review Audit
- Prepare `scratch/ATT-1945_desc.md`.
- Update subtask `ATT-1945` description on Jira.
- Move `ATT-1945` to `in_review`.
- Run Gate 3 audit: `python3 tools/review_agent.py audit ATT-1945`.
