# Stage 5: Walkthrough & Verification - ATT-1839: [Feature] [Aftermath/Graphs] Background Training Zone Bands & Right-Hand Zone Axis for Heart Rate and Power Graphs

**Ticket**: [[ATT-1839]](https://rainerblind.atlassian.net/browse/ATT-1839)  
**Sub-task**: [[ATT-1947]](https://rainerblind.atlassian.net/browse/ATT-1947) (`[Test]`)  
**Parent Epic**: [[ATT-111]](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Requirement Mapping**: `REQ-UI-230` (*Aftermath/Graphs: Background Training Zone Bands & Right-Hand Zone Axis for Heart Rate and Power Graphs*), `REQ-UI-206`  
**Test Mapping**: `TST-UI-184` (*Background Training Zone Bands & Right-Hand Zone Axis Verification*)  
**Branch**: `feature/ATT-1839`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Executive Summary & Verification Overview

Ticket ATT-1839 implements subtle horizontal training zone background bands and a right-hand secondary zone axis (`Z1`–`Z5`) in `TelemetryMetricGraph.kt` for Heart Rate and Cycling Power telemetry curves, providing athletes with instantaneous zone awareness while strictly preserving curve clarity and horizontal axis alignment with `ElevationProfile.kt`:

1. **Pure Geometry & Analytical Math Engine (`TelemetryZoneMath.kt`)**:
   - Implemented `calculateHeartRateZoneBands` and `calculatePowerZoneBands` partitioning telemetry plot areas into 5 horizontal zone bands clamped to `[dataMin, dataMax]`.
   - Implemented `calculateThresholdDashes` returning guideline positions at $z1Max$..$z4Max$ strictly within the visible data span.
   - Implemented `shouldRenderZoneLabel` enforcing a $\ge 12\text{ dp}$ vertical height clearance gate to eliminate label overlap.
   - Implemented `determineHeartRateZone` and `determinePowerZone` for 1-indexed zone mapping ($1..5$).
   - Exported zone colors `ZONE_COLORS` using `TTColor.Zone1`..`TTColor.Zone5` and calibrated subtle `ZONE_BAND_ALPHA = 0.10f`.

2. **TelemetryMetricGraph Enhancement (`TelemetryMetricGraph.kt`)**:
   - Accepted optional parameters `hrZoneThresholds` and `powerZoneThresholds` with defensive `LocalContext` fallback resolving athlete thresholds via `SettingsDataStoreJavaHelper.getZoneMax`.
   - Rendered 5 horizontal filled rectangles behind curve for `HEART_RATE` and `POWER` using `ZONE_BAND_ALPHA = 0.10f`.
   - Rendered dashed horizontal guidelines at $z1Max$..$z4Max$ using `outlineVariant.copy(alpha = 0.35f)`.
   - Rendered right-hand secondary axis labels `Z1`–`Z5` centered vertically within visible bands and horizontally within the 25.dp right margin (`startPaddingPx + chartWidthPx + endPaddingPx / 2f`).
   - Enhanced `TelemetryMetricUtils.formatValue` to format instantaneous values with active zone tag (`• Z{n}`).
   - Strictly preserved horizontal padding parity (`start = 50.dp, end = 25.dp`) and kept `SPEED` / `PACE` graphs plain.

3. **Multi-Metric Scrubbing Badge (`ElevationProfile.kt`)**:
   - Augmented `ScrubbingTelemetryBadge` to indicate active zone during synchronized scrubbing (e.g. `165 bpm • Z4`, `280 W • Z3`).

4. **Automated Verification Suite**:
   - Pure math engine unit tests (`TelemetryZoneMathTest.kt`): Full span, partial span, clearance threshold, and zone index determination.
   - Composable & invariant contract tests (`TelemetryMetricGraphZoneTest.kt`): HR/Power zone bands enablement, Speed/Pace isolation, padding invariance, and scrubbing readout formatting.
   - Clean-room regression suite (`./gradlew testDebugUnitTest`): 100% pass rate across all modules.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-230.1` | `[TST-UI-184.1]` | Automated Math Unit Tests (`TelemetryZoneMathTest`) | **PASSED** | `Verified` |
| `REQ-UI-230.2` | `[TST-UI-184.2]` | Automated Composable Contract Tests (`TelemetryMetricGraphZoneTest`) | **PASSED** | `Verified` |
| `REQ-UI-230.3` | `[TST-UI-184.3]` | Scrubbing Readout Contract Test (`TelemetryMetricGraphZoneTest`) | **PASSED** | `Verified` |
| `REQ-UI-230.4` | `[TST-UI-184.2]` | Speed/Pace Isolation Test (`TelemetryMetricGraphZoneTest`) | **PASSED** | `Verified` |
| `REQ-UI-230.5` | `[TST-UI-184.2]` | Padding Invariant Test (`TelemetryMetricGraphZoneTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `[TST-UI-184.4]` | Clean-Room Full Regression (`./gradlew testDebugUnitTest`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Contract Tests
```text
./gradlew testDebugUnitTest \
  --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryZoneMathTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphZoneTest" \
  --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphTest"

BUILD SUCCESSFUL in 13s
32 actionable tasks: 5 executed, 27 up-to-date
```

### Full Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
- Clean-room execution validating zero regressions across all core features and modules.

---

## 4. Invariant & Governance Verification

1. **Horizontal Alignment Invariant with ElevationProfile**: `startPaddingPx = 50.dp` and `endPaddingPx = 25.dp` are strictly preserved across all graphs. Right-hand zone labels (`Z1`–`Z5`) are drawn within the existing 25.dp right margin, ensuring pixel-perfect vertical alignment during synchronized scrubbing.
2. **Speed & Pace Isolation**: Speed and pace graphs remain plain and do not display zone bands or right-hand zone axes.
3. **Solid Primary Curve Stroke**: Heart rate (Red) and Power (Dark Violet) primary curve strokes remain solid, maintaining distinction from the gradient elevation profile.
4. **Defensive Threshold Fallback**: Null, missing, or unconfigured thresholds are handled safely via `runCatching`, omitting bands gracefully without crashes.
5. **Zero Database Mutations**: No SQLite schema or database table changes; purely UI-level Canvas rendering enhancement.
6. **Living Documentation Synchronized**: `docs/requirements.md` (`REQ-UI-230`) and `docs/tests.md` (`TST-UI-184`) updated to `Verified`.
7. **Subtask Completion**: Stage 5 subtask `ATT-1947` submitted for Gate 5 review audit.
8. **Continuous Sprint Branch Integration (Strategy A)**: Merging `feature/ATT-1839` into `sprint/2026-40.8` via `--no-ff` and deleting `feature/ATT-1839`.
9. **Parent Ticket Final Review**: `ATT-1839` transitioned to `Final Review (Human)` assigned to `rainer` for final release sign-off.
