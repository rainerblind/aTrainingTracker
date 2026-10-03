# Stage 2: Requirement & Test Specification - ATT-1839: Background Training Zone Bands & Right-Hand Zone Axis for Heart Rate and Power Graphs

**Ticket**: [ATT-1839](https://rainerblind.atlassian.net/browse/ATT-1839)  
**Sub-task**: [ATT-1944](https://rainerblind.atlassian.net/browse/ATT-1944) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Formal Requirement Specification: `REQ-UI-230`

### 1.1 Requirement Statement
The system SHALL render subtle horizontal training zone background bands and a right-hand secondary zone axis (`Z1`–`Z5`) in `TelemetryMetricGraph.kt` for Heart Rate and Cycling Power telemetry curves, providing athletes with instantaneous zone awareness while strictly preserving curve clarity and horizontal axis alignment with `ElevationProfile.kt` (ATT-1839):

1. **Zone Bands Geometry & Pure Math Engine (`TelemetryZoneMath.kt`)**:
   - The system SHALL calculate horizontal band geometry for Zones 1 through 5:
     - Zone 1: $[dataMin, \min(dataMax, z1Max)]$
     - Zone 2: $[\max(dataMin, z1Max), \min(dataMax, z2Max)]$
     - Zone 3: $[\max(dataMin, z2Max), \min(dataMax, z3Max)]$
     - Zone 4: $[\max(dataMin, z3Max), \min(dataMax, z4Max)]$
     - Zone 5: $[\max(dataMin, z4Max), dataMax]$
   - When a zone band has non-empty height ($minVal < maxVal$), the system SHALL render a solid filled rectangle across the full chart plot width ($startPaddingPx \dots startPaddingPx + chartWidthPx$) using `TTColor.Zone1`..`TTColor.Zone5` at calibrated subtle alpha ($0.10f$).
   - When $z_kMax \in (dataMin, dataMax)$ for $k \in \{1, 2, 3, 4\}$, the system SHALL draw a subtle horizontal dashed threshold boundary line across the plot area using `outlineVariant.copy(alpha = 0.35f)`.
2. **Right-Hand Secondary Zone Axis (`Z1`–`Z5`)**:
   - Along the right 25.dp margin ($startPaddingPx + chartWidthPx \dots size.width$), the system SHALL render centered zone labels `Z1`, `Z2`, `Z3`, `Z4`, `Z5`.
   - A zone label SHALL only be rendered IF AND ONLY IF the band vertical pixel height is $\ge 12\text{ dp}$. If band height is $< 12\text{ dp}$, the label MUST be suppressed to eliminate visual overlap.
   - Label text SHALL be styled using `MaterialTheme.typography.labelSmall` in muted tone (`onSurfaceVariant` or `ZONE_COLORS[i-1]`).
3. **Scrubbing Readout Zone Indicator**:
   - During synchronized scrubbing, when an instantaneous HR or Power sample is highlighted on the curve, the readout badge / tooltip SHALL indicate the active zone index (e.g. `165 bpm • Z4`, `280 W • Z3`).
4. **Decoupled Fallback & Speed/Pace Isolation**:
   - Speed and Pace metric types SHALL NOT render zone bands or right-hand zone axes, retaining their clean, plain canvas.
   - In `TelemetryMetricGraph`, `hrZoneThresholds` and `powerZoneThresholds` SHALL be accepted as optional parameters. If null, the composable SHALL defensively resolve athlete thresholds from `SettingsDataStoreJavaHelper.getZoneMax` via `LocalContext.current` with safe `runCatching` fallback. If thresholds are missing, 0, or corrupt, zone bands MUST be omitted without throwing unhandled exceptions.
5. **Preservation of Core Invariants**:
   - Horizontal plot paddings (`startPaddingPx = 50.dp`, `endPaddingPx = 25.dp`) MUST NOT be changed, guaranteeing pixel-perfect vertical alignment with `ElevationProfile.kt` and across all stacked charts during multi-chart scrubbing.
   - Single-finger vertical scroll gesture propagation (`REQ-UI-226`) and two-finger pinch zoom remain intact.
   - The primary curve stroke color (Red for HR, Dark Violet for Power) remains solid to ensure immediate visual distinction from the gradient-colored elevation profile.

---

### 1.2 Given-When-Then Acceptance Criteria

- **AC-1 (Heart Rate Graph Zone Bands)**:
  - *Given* a workout with recorded heart rate telemetry,
  - *When* viewing the Heart Rate graph in Aftermath,
  - *Then* the background SHALL render 5 distinct horizontal bands with 10% alpha tint corresponding to the athlete's configured HR zones (`TTColor.Zone1` through `Zone5`).

- **AC-2 (Power Graph Zone Bands)**:
  - *Given* a cycling activity with power meter telemetry,
  - *When* viewing the Cycling Power graph in Aftermath,
  - *Then* the background SHALL render 5 distinct horizontal bands with 10% alpha tint corresponding to the athlete's configured power zones.

- **AC-3 (Right-Hand Secondary Zone Axis)**:
  - *Given* the Heart Rate or Power graph,
  - *When* inspecting the vertical axes,
  - *Then* the left axis SHALL display numerical values (BPM or Watts) and the right axis SHALL display zone identifiers (`Z1`–`Z5`) centered vertically within their respective bands.
  - *And* any zone band narrower than 12 dp SHALL suppress its label to prevent overlap.

- **AC-4 (Scrubbing Readout Zone Tag)**:
  - *Given* an athlete scrubbing across the HR or Power graph,
  - *When* holding the cursor at any point,
  - *Then* the instantaneous readout badge / tooltip SHALL indicate the active zone (e.g. `168 bpm • Z4`).

- **AC-5 (Speed and Pace Isolation)**:
  - *Given* a Speed or Pace graph,
  - *When* rendered,
  - *Then* zone bands and right-hand zone axes SHALL NOT be displayed.

- **AC-6 (Padding and Alignment Invariance)**:
  - *Given* stacked charts in `MapDetailLayout`,
  - *When* rendering ElevationProfile and TelemetryMetricGraphs simultaneously,
  - *Then* `startPaddingPx` SHALL equal 50.dp and `endPaddingPx` SHALL equal 25.dp across all charts, ensuring exact vertical alignment.

---

## 2. Test Specification: `TST-UI-184`

### 2.1 Scope of Automated Verification
The test suite validates:
1. Pure math and geometry calculations in `TelemetryZoneMathTest.kt`.
2. Composable background rendering and axis placement in `TelemetryMetricGraphZoneTest.kt`.
3. Scrubbing zone determination and padding invariance.
4. Clean-room regression across all modules (`./gradlew testDebugUnitTest`).

---

### 2.2 Detailed Test Cases

#### `TST-UI-184.1`: Pure Zone Geometry & Math Engine Unit Tests
- **Test Class**: `com.atrainingtracker.trainingtracker.ui.map.TelemetryZoneMathTest`
- **Case 1.1 (`testCalculateZoneBands_fullSpan`)**:
  Given thresholds (z1=120, z2=140, z3=160, z4=180) and data range [100.0, 195.0]:
  Verify exactly 5 bands are generated with correct [minVal, maxVal] clamped to [100.0, 195.0].
- **Case 1.2 (`testCalculateZoneBands_partialSpan`)**:
  Given thresholds (z1=120, z2=140, z3=160, z4=180) and narrow data range [135.0, 155.0] (spanning only Z2 and Z3):
  Verify bands 1, 4, 5 are empty / zero-height, and bands 2 and 3 have valid non-empty geometry.
- **Case 1.3 (`testShouldRenderZoneLabel_clearanceThreshold`)**:
  Verify band height $\ge 12\text{ dp}$ returns `true`; band height $< 12\text{ dp}$ returns `false`.
- **Case 1.4 (`testDetermineZoneIndex_heartRateAndPower`)**:
  Verify correct 1-indexed zone mapping ($1..5$) for heart rate and power values matching `ZoneDistributionCalculator` boundaries.

#### `TST-UI-184.2`: Composable Background Bands & Axis Rendering Tests
- **Test Class**: `com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphZoneTest`
- **Case 2.1 (`testHeartRateGraph_displaysZoneBandsAndRightAxis`)**:
  Verify `TelemetryMetricType.HEART_RATE` enables zone bands rendering and right-hand axis drawing when thresholds exist.
- **Case 2.2 (`testPowerGraph_displaysZoneBandsAndRightAxis`)**:
  Verify `TelemetryMetricType.POWER` enables zone bands rendering and right-hand axis drawing when thresholds exist.
- **Case 2.3 (`testSpeedAndPaceGraphs_noZoneBandsOrAxis`)**:
  Verify `TelemetryMetricType.SPEED` and `TelemetryMetricType.PACE` do NOT enable zone bands or right-hand axis.
- **Case 2.4 (`testPaddingInvariants_matchElevationProfile`)**:
  Verify start padding is 50.dp, end padding is 25.dp, bottom padding is 24.dp.

#### `TST-UI-184.3`: Scrubbing Tooltip Readout Contract
- **Test Class**: `com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphZoneTest`
- **Case 3.1 (`testScrubbingReadout_includesZoneTag`)**:
  Verify formatted readout string for HR includes `• Z{n}` when thresholds are provided.

#### `TST-UI-184.4`: Clean-Room Regression Suite
- **Command**: `./gradlew testDebugUnitTest`
- **Pass Criteria**: 100% test pass rate across all modules.

---

## 3. Traceability Matrix

| Requirement Clause | Test Case | Target Artifact | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-230.1` (Zone Bands Geometry) | `TST-UI-184.1` | `TelemetryZoneMathTest.kt` | JVM Unit Test |
| `REQ-UI-230.2` (Right-Hand Zone Axis) | `TST-UI-184.1`, `TST-UI-184.2` | `TelemetryZoneMathTest.kt`, `TelemetryMetricGraphZoneTest.kt` | JVM Unit & Contract Test |
| `REQ-UI-230.3` (Scrubbing Readout) | `TST-UI-184.3` | `TelemetryMetricGraphZoneTest.kt` | Contract Test |
| `REQ-UI-230.4` (Speed/Pace Decoupling) | `TST-UI-184.2` | `TelemetryMetricGraphZoneTest.kt` | Contract Test |
| `REQ-UI-230.5` (Padding Invariance) | `TST-UI-184.2` | `TelemetryMetricGraphZoneTest.kt` | Invariant Test |
| `REQ-PRO-001` (Clean-Room Regression) | `TST-UI-184.4` | Full Test Suite | Clean-room Gradle run |
