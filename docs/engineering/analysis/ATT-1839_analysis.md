# Stage 1: Problem Domain & Root Cause Analysis - ATT-1839: Background Training Zone Bands & Right-Hand Zone Axis for Heart Rate and Power Graphs

**Ticket**: [ATT-1839](https://rainerblind.atlassian.net/browse/ATT-1839)  
**Sub-task**: [ATT-1943](https://rainerblind.atlassian.net/browse/ATT-1943) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Background

### 1.1 Context & Evolution (Chesterton's Fence Archaeology)
In Sprint 2026-40.5, ticket **ATT-1740** (*Continuous Metric Graphs & Headings*, `REQ-UI-206`) introduced `TelemetryMetricGraph.kt` to render high-performance continuous time-series curves for Speed/Pace, Heart Rate, and Cycling Power in the Aftermath detailed workout inspection view (`MapDetailLayout.kt` and `WorkoutSummary.kt`).

Currently, `TelemetryMetricGraph.kt` plots Heart Rate and Cycling Power against a plain, monochromatic canvas with an accent stroke (Red for Heart Rate, Dark Violet for Power):
```kotlin
val accentColor = remember(metricType, colorScheme) {
    when (metricType) {
        TelemetryMetricType.HEART_RATE -> TTColor.Zone4
        TelemetryMetricType.SPEED, TelemetryMetricType.PACE -> colorScheme.primary
        TelemetryMetricType.POWER -> TTColor.Zone5
    }
}
```

While athletes can inspect their cumulative time-in-zones distribution via the 5-zone summary cards (`HeartRateZoneDistributionCard.kt` / `PowerZoneDistributionCard.kt`, introduced in ATT-1389 / `REQ-UI-202` and ATT-1390 / `REQ-UI-203`), they have no spatial or temporal correlation between the continuous telemetry curve and their personalized training zones:
1. **Lack of Instantaneous Zone Awareness**:
   An athlete inspecting a steep climb or a tempo segment cannot see at a glance whether their heart rate stayed within Zone 2 (Aerobic Base) or drifted into Zone 3/4 (Tempo/Threshold).
2. **Visual Conflict with Slope Gradient**:
   The adjacent `ElevationProfile.kt` already uses dynamic segment coloring based on slope gradient (green/yellow/orange/red/purple). Dynamically coloring the telemetry line itself by zone would cause visual confusion and line clash with the elevation profile.
3. **Ergonomic Solution**:
   Providing subtle, horizontal **training zone background bands** (using `TTColor.Zone1` through `TTColor.Zone5` at calibrated 8–12% opacity) combined with a **right-hand secondary zone axis (`Z1`–`Z5`)** gives athletes instant zone awareness without compromising curve clarity or breaking visual harmony with the elevation profile.

---

## 2. Forensic Investigation of Affected Subsystems

### 2.1 Telemetry Graph Rendering Architecture (`TelemetryMetricGraph.kt`)
In `TelemetryMetricGraph.kt`:
- **Plot Area Layout**:
  - `startPaddingPx = 50.dp.toPx()`: Left Y-axis area for numeric labels (`bpm`, `W`, `km/h`, `min/km`).
  - `endPaddingPx = 25.dp.toPx()`: Right margin padding.
  - `topPaddingPx = 10.dp.toPx()`, `bottomPaddingPx = 24.dp.toPx()`.
  - `chartWidthPx = (size.width - startPaddingPx - endPaddingPx).coerceAtLeast(1f)`.
  - `chartHeightPx = (size.height - topPaddingPx - bottomPaddingPx).coerceAtLeast(1f)`.
- **Horizontal Alignment Invariant**:
  - `startPaddingPx = 50.dp` and `endPaddingPx = 25.dp` strictly match `ElevationProfile.kt`! This guarantees that the visible X-axes and scrubbing cursors align in 100% pixel-perfect vertical alignment across all stacked charts.
  - The right-hand 25.dp margin (`endPaddingPx`) currently sits unused in `TelemetryMetricGraph`. It provides ideal space (~62.5px at standard densities) to render compact `Z1`–`Z5` zone labels without expanding chart width or shifting horizontal alignment!

### 2.2 Athlete Training Zone Thresholds Architecture
Training zones are configured per athlete and stored in `SettingsDataStore`:
- **Heart Rate Zones**: `HeartRateZoneThresholds(z1Max, z2Max, z3Max, z4Max)` defined in `ZoneDistributionModels.kt`. Zone 1: $\le z1Max$, Zone 2: $z1Max < hr \le z2Max$, Zone 3: $z2Max < hr \le z3Max$, Zone 4: $z3Max < hr \le z4Max$, Zone 5: $> z4Max$.
- **Cycling Power Zones**: `PowerZoneThresholds(z1Max, z2Max, z3Max, z4Max)`.
- **Sport-Specific Profiles**:
  - Heart Rate has separate thresholds for Cycling (`SettingsDataStore.ZoneType.HR_BIKE`) and Running (`SettingsDataStore.ZoneType.HR_RUN`), selected by `bSportType == BSportType.BIKE`.
  - Power uses `SettingsDataStore.ZoneType.PWR_BIKE`.
- **Retrieval Engine**:
  - `SettingsDataStoreJavaHelper.getZoneMax(context, zoneType, zoneIndex)` provides safe synchronous access.
  - `WorkoutRepository` retrieves thresholds and calculates 5-zone distribution data via `ZoneDistributionCalculator.kt`.

### 2.3 Color Tokens & Alpha Calibration
Zone colors are defined in `com.atrainingtracker.trainingtracker.ui.theme.TTColor`:
- `TTColor.Zone1` = `Color(0xFF7FFF00)` (Chartreuse)
- `TTColor.Zone2` = `Color(0xFF008000)` (Green)
- `TTColor.Zone3` = `Color(0xFFFFA500)` (Orange)
- `TTColor.Zone4` = `Color(0xFFFF0000)` (Red)
- `TTColor.Zone5` = `Color(0xFF9400D3)` (Dark Violet)

For background bands, alpha must be carefully calibrated to ensure high contrast against the solid primary stroke:
- Background bands: `alpha = 0.10f` (light/dark theme resilient, providing distinct tint without glare).
- Zone boundary threshold guidelines: `outlineVariant.copy(alpha = 0.35f)` with `dashPathEffect(floatArrayOf(6f, 6f), 0f)` at $z1Max, z2Max, z3Max, z4Max$.
- Right-hand axis labels: `Z1`–`Z5` rendered using `ZONE_COLORS[i-1]` or `onSurfaceVariant` at vertical center of each band.

### 2.4 Scrubbing Readout Enhancement
During synchronized scrubbing:
- In `TelemetryMetricGraph.kt`, when `currentDistance != null`, the active metric value can be resolved to its corresponding zone (`Z1`–`Z5`).
- In `ElevationProfile.kt`, `ScrubbingTelemetryBadge` currently displays `${point.hr} bpm` and `${point.power} W`. Enhancing this with zone indicator (e.g. `${point.hr} bpm • Z4` and `${point.power} W • Z3`) delivers instant zone awareness during timeline inspection.

---

## 3. Requirement Archaeology & Chesterton's Fence Audit

### 3.1 Prior Art & Historical Commit Trace
1. **`ATT-111` / `REQ-UI-206` (Sprint 2026-40.5, Commit `7a0cf5a4`)**:
   Introduced `TelemetryMetricGraph.kt` with solid accent lines and synchronized cursor scrubbing.
2. **`ATT-1389` / `REQ-UI-202` & `ATT-1390` / `REQ-UI-203` (Sprint 2026-40.2)**:
   Introduced 5-zone time distribution calculation and horizontal bars.
3. **`ATT-1872` / `REQ-UI-226` (Sprint 2026-40.8)**:
   Added directional touch disambiguation to `TelemetryMetricGraph` to enable smooth vertical scroll while preserving horizontal scrubbing.

### 3.2 Invariants to Protect
1. **Horizontal Alignment Invariant**:
   `startPaddingPx = 50.dp` and `endPaddingPx = 25.dp` MUST NOT be altered. All secondary zone labels (`Z1`–`Z5`) must be drawn within the existing 25.dp right padding area.
2. **Speed & Pace Graphs Decoupling**:
   Speed and Pace graphs do not use 5-zone heart rate or power thresholds and MUST remain on their existing clean, plain background.
3. **Robust Clamping & Edge-Case Safety**:
   If workout data spans only a subset of zones (e.g. recovery session in Z1/Z2 only, or intense race in Z4/Z5 only), band geometry must clamp safely to `[dataMin, dataMax]` without negative heights or out-of-bounds painting. If zone band height is $< 12\text{ dp}$, suppress the text label to prevent visual overlap.
4. **Defensive Threshold Resolution**:
   If athlete thresholds are missing, unconfigured (0), or invalid, `TelemetryMetricGraph` must fall back gracefully to standard rendering without throwing exceptions.
5. **9-Language Localization Parity**:
   Ensure all zone strings and readout tokens adhere to the 9 supported locales.

---

## 4. Scope Bounding (In-Scope vs. Out-of-Scope)

### 4.1 In-Scope
1. **Pure Zone Band Geometry & Math Engine (`TelemetryZoneMath.kt`)**:
   - Calculate vertical Y-coordinates and heights for each of the 5 zones clamped to `[dataMin, dataMax]`.
   - Determine whether each zone label (`Z1`–`Z5`) has sufficient vertical clearance ($\ge 12\text{ dp}$) to render.
   - Pure function mapping heart rate or power value to zone index ($1..5$).
2. **Background Zone Bands & Boundary Dashes in `TelemetryMetricGraph.kt`**:
   - Draw horizontal colored rectangles for Zones 1–5 with subtle 10% alpha tint behind the curve for `HEART_RATE` and `POWER`.
   - Draw horizontal dashed guidelines at `z1Max`, `z2Max`, `z3Max`, `z4Max`.
3. **Right-Hand Secondary Zone Axis (`Z1`–`Z5`)**:
   - Render `Z1`–`Z5` labels in the right margin (between `startPaddingPx + chartWidthPx + 4.dp` and `startPaddingPx + chartWidthPx + endPaddingPx`).
4. **Scrubbing Readout Zone Indicator**:
   - Augment scrubbing tooltip / `ScrubbingTelemetryBadge` with zone identifier (e.g. `165 bpm • Z4`, `280 W • Z3`).
5. **Defensive Threshold Wiring**:
   - Allow optional explicit thresholds in `TelemetryMetricGraph(hrZoneThresholds = ..., powerZoneThresholds = ...)`, with automatic fallback via `LocalContext` and `SettingsDataStoreJavaHelper`.

### 4.2 Out-of-Scope
- Changing the primary stroke color from solid to multi-colored rainbow line (out-of-scope to avoid visual conflict with elevation slope gradient).
- Modifying SQLite database schemas or `WorkoutSamplesDatabaseManager`.
- Modifying Speed or Pace graph rendering.

---

## 5. Risk Analysis & Mitigation

| Risk | Impact | Mitigation Strategy |
| :--- | :--- | :--- |
| **Misaligned X-Axes across Stacked Charts** | High | Strictly preserve `startPaddingPx = 50.dp` and `endPaddingPx = 25.dp`; draw right-hand zone labels inside the existing 25.dp margin. |
| **Bands Overwhelming Primary Curve Contrast** | Medium | Calibrate zone band alpha strictly to `0.10f`, preserving full clarity of the solid stroke and gradient fill. |
| **Compressed Zone Overlap on Narrow Y Ranges** | Medium | Enforce minimum vertical clearance threshold ($\ge 12\text{ dp}$) before rendering right-hand zone label. |
| **Null/Missing/Zero Thresholds in Tests or Cold Starts** | High | Implement pure fallback in `TelemetryZoneMath` and defensive `runCatching` in threshold resolution. |

---

## 6. Conclusion & Next Steps
Stage 1 forensic investigation confirms that `TelemetryMetricGraph.kt` can seamlessly host horizontal zone bands and right-hand zone axis within its existing padding geometry, fully respecting Chesterton's Fence invariants.

Proceed to:
1. Update Stage 1 subtask `ATT-1943` description and transition to `in_review`.
2. Run Gate 1 audit (`review_agent.py audit ATT-1943`).
3. Transition to Stage 2 (`stage2-req-test-spec`) upon Gate 1 approval.
