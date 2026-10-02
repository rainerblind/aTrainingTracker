# Stage 1: Problem Domain & Root Cause Analysis - ATT-1845: Fine-Grained Telemetry Frequency Histogram (BPM & Wattage Bins) with Zone Color Shading

**Ticket**: [ATT-1845](https://rainerblind.atlassian.net/browse/ATT-1845)  
**Sub-task**: [ATT-1948](https://rainerblind.atlassian.net/browse/ATT-1948) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Branch**: `feature/ATT-1845`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Executive Summary & Problem Formulation

### 1.1 Problem Statement
The workout aftermath inspection currently visualizes metabolic and physiological training zones in `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt` using `ZoneDistributionColumnChart.kt`. This component renders only 5 coarse aggregate vertical bars (Zones 1 through 5).

While these 5 aggregate bars provide an immediate high-level summary of time spent in each zone, ambitious athletes and coaches cannot determine the fine structure of the workout effort:
1. **No Granular Distribution Shape**: It is impossible to distinguish between polarized, sweet-spot, threshold, or pyramidal distributions.
2. **Boundary Ambiguity**: An athlete cannot see whether time in Zone 2 was accumulated near the lower recovery floor (e.g. 115 bpm) or right at the upper aerobic threshold (e.g. 138 bpm).
3. **Power Spikes and Clusters**: In cycling workouts with power meters, 5 coarse zones conceal localized wattage clustering (e.g., micro-bursts, cadence sweet-spots, or FTP plateauing).

### 1.2 Proposed Enhancement
Deliver a continuous, fine-grained telemetry frequency histogram for Heart Rate (BPM bins) and Cycling Power (Wattage bins) embedded within the existing aftermath zone cards:
- **Narrow Metric Bins**: Configured at $\Delta = 2\text{ bpm}$ for Heart Rate and $\Delta = 10\text{ W}$ for Power across the workout's active telemetry span $[val_{min}, val_{max}]$.
- **Zone Shading Consistency**: Every individual histogram bar is shaded using the athlete's personalized zone color (`TTColor.Zone1`..`TTColor.Zone5`), determined by evaluating the bin midpoint against active thresholds (`HeartRateZoneThresholds` / `PowerZoneThresholds`).
- **Seamless View Mode Switching**: The card header provides a sleek, compact segmented toggle ("5 Zones" vs. "Histogram") allowing athletes to switch effortlessly between the classic 5-column overview and the detailed frequency histogram.
- **Interactive Inspection**: Scrubbing or tapping along the histogram displays an instantaneous readout badge (e.g. `142–144 bpm: 14:20 (Z2)` or `220–230 W: 8:45 (Z3)`).

---

## 2. Technical Investigation & Architecture Forensic Audit

### 2.1 Existing Zone Distribution Architecture
- **Data Models (`ZoneDistributionModels.kt`)**:
  - `HeartRateZoneThresholds(z1Max, z2Max, z3Max, z4Max)`
  - `PowerZoneThresholds(z1Max, z2Max, z3Max, z4Max)`
  - `ZoneSample(timeActiveSec, value)`
  - `ZoneTimeEntry(zoneIndex, zoneLabelResId, durationSec, percentage, color)`
  - `ZoneDistributionData(totalActiveTimeSec, entries)`
- **Calculation Engine (`ZoneDistributionCalculator.kt`)**:
  - Computes `dt` between adjacent samples (clamped to `MAX_DELTA_T_SEC = 5L` to avoid inflation during sensor dropouts or pauses).
  - Accumulates total seconds per zone and generates normalized percentages.
- **Data Retrieval (`WorkoutRepository.kt`)**:
  - `getHeartRateZoneDistribution(workoutId, bSportType)`: Queries `samplesTable` for `SensorType.HR`, extracts `ZoneSample` list, and runs calculation on `Dispatchers.IO`.
  - `getPowerZoneDistribution(workoutId)`: Queries `samplesTable` for `SensorType.POWER`, extracts `ZoneSample` list, and runs calculation on `Dispatchers.IO`.
- **Card UI & Composition**:
  - `HeartRateZoneDistributionCard.kt` & `PowerZoneDistributionCard.kt`: Render header (icon, title, total time) and `ZoneDistributionColumnChart`.
  - Embedded in `WorkoutSummary.kt` (summary card list) and `TrackOnMapScreen.kt` (detailed aftermath tab).

### 2.2 Binning Engine Formulation
To guarantee numerical stability, boundary consistency, and high rendering performance:
1. **Span Clamping & Bin Alignment**:
   - Given active samples $S = \{ (t_i, v_i) \mid v_i > 0 \}$:
     $$v_{min} = \min(v_i), \quad v_{max} = \max(v_i)$$
   - Align start and end to bin width $\Delta$:
     $$bin_{start} = \lfloor v_{min} / \Delta \rfloor \cdot \Delta, \quad bin_{end} = \lfloor v_{max} / \Delta \rfloor \cdot \Delta$$
     $$N_{bins} = \frac{bin_{end} - bin_{start}}{\Delta} + 1$$
2. **Duration Accumulation**:
   - For each sample $(t_i, v_i)$:
     $$\Delta t_i = \min(t_{i+1} - t_i, 5\text{s})$$
     $$k = \left\lfloor \frac{v_i - bin_{start}}{\Delta} \right\rfloor$$
     $$D_k \mathrel{+}= \Delta t_i$$
3. **Zone Color Assignment**:
   - For bin $k$ with interval $[v_k^{start}, v_k^{end}]$ where $v_k^{mid} = v_k^{start} + \frac{\Delta}{2}$:
     $$zone = \text{determineZone}(v_k^{mid}, thresholds)$$
     $$color_k = \text{TTColor.Zone}[zone]$$

---

## 3. Chesterton's Fence & Requirement Archaeology

### 3.1 Prior Art & Historical Commit Trace
1. **`ATT-1389` & `ATT-1390` (Sprint 2026-40.2)**:
   Introduced 5-zone time distribution calculation and horizontal/columnar representations.
2. **`ATT-1740` (Sprint 2026-40.5)**:
   Introduced `TelemetryMetricGraph` with continuous time/distance curves.
3. **`ATT-1839` (Sprint 2026-40.8)**:
   Introduced background zone bands and right-hand zone axes for HR and Power continuous curves.

### 3.2 Invariants to Protect
1. **Zero Degradation of Existing 5-Zone View**:
   The default or toggleable 5-zone column view must remain 100% pixel-perfect and backward compatible.
2. **Zero Database Migrations**:
   Binning is computed dynamically from existing `samplesTable` telemetry streams without schema mutations.
3. **Asynchronous Execution**:
   Binning must occur asynchronously off the main thread (`Dispatchers.IO` / `Dispatchers.Default`) to ensure zero jank during screen load.
4. **Theme Parity & Readability**:
   Histogram bars and axes must be crisp in both Dark and Light themes with adequate contrast.
5. **Localization Parity**:
   All new labels ("5 Zones", "Histogram", tooltip tokens) must be localized across all 9 supported languages.

---

## 4. Scope Bounding (In-Scope vs. Out-of-Scope)

### 4.1 In-Scope
1. **Data Model Extensions (`ZoneDistributionModels.kt`)**:
   - Define `TelemetryHistogramBin` and `TelemetryHistogramData`.
   - Incorporate optional `histogram: TelemetryHistogramData?` into `ZoneDistributionData`.
2. **Pure Math Engine (`TelemetryHistogramCalculator.kt` or in `ZoneDistributionCalculator.kt`)**:
   - Pure function computing fine-grained bins with configurable/default bin width ($\Delta = 2\text{ bpm}$ for HR, $\Delta = 10\text{ W}$ for Power).
   - Mapping bin midpoints to zone index and zone color.
3. **Dedicated Histogram Composable (`TelemetryHistogramChart.kt`)**:
   - High-performance Canvas/Composable rendering discrete bars with zone colors.
   - Interactive touch/drag scrubbing displaying active bin readout (range, duration, percentage, zone).
4. **Card UI Integration (`HeartRateZoneDistributionCard.kt` & `PowerZoneDistributionCard.kt`)**:
   - Add segmented control / mini toggle buttons in the card header allowing athletes to switch between 5-Zone summary and Detailed Histogram.
5. **Unit & Contract Testing**:
   - Comprehensive unit tests validating binning, edge clamping, zone shading, and toggle behavior.

### 4.2 Out-of-Scope
- Frequency histograms for Speed or Pace (which do not use metabolic 5-zone models).
- Interactive bin-width resizing slider in the card (fixed curated bin widths provide optimal mobile density).
- Raw sample database schema changes.

---

## 5. UI/UX Design & Aesthetic Blueprint

```
┌────────────────────────────────────────────────────────────────────────┐
│ [♥] Heart Rate Zones             [ 5 Zones | Histogram ]     1:24:15   │
├────────────────────────────────────────────────────────────────────────┤
│ Readout: 142–144 bpm • 14:20 (17.0%) • Z2 (Aerob)                      │
│                                                                        │
│   ▲                                                                    │
│ 15m│          █ █                                                      │
│    │        █ █ █ █                                                    │
│ 10m│      █ █ █ █ █ █ █                                                │
│    │    █ █ █ █ █ █ █ █ █ █                                            │
│  5m│  █ █ █ █ █ █ █ █ █ █ █ █ █                                        │
│    │  █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █                            │
│  0m└──┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴─┴───────────────────► │
│       [   Z1   ][     Z2     ][    Z3    ][  Z4  ][ Z5 ]               │
│       90      115           140         160     175  190 bpm           │
└────────────────────────────────────────────────────────────────────────┘
```

1. **Card Header Toggle**:
   - A compact `SingleChoiceSegmentedButtonRow` or two mini rounded pill buttons:
     - `5 Zonen` / `5 Zones`
     - `Histogramm` / `Histogram`
   - Preserves total workout duration display.
2. **Histogram Visual Presentation**:
   - Discrete vertical bars rendered with 1 dp spacing.
   - Height scaled proportionally to the maximum bin duration.
   - Each bar colored with corresponding zone color (`TTColor.Zone1`..`TTColor.Zone5`).
3. **Interactive Scrubbing / Tooltip**:
   - Touching any bar highlights it with an outline and updates the compact readout line above the chart:
     `${minBpm}–${maxBpm} bpm • ${formattedDuration} (${percentage}%) • Z${zone}`.

---

## 6. 9-Language Localization Strategy

| Resource Key | EN | DE | ES | FR | IT | JA | NL | PL | PT |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `zone_mode_5_zones` | 5 Zones | 5 Zonen | 5 zonas | 5 zones | 5 zone | 5ゾーン | 5 zones | 5 stref | 5 zonas |
| `zone_mode_histogram` | Histogram | Histogramm | Histograma | Histogramme | Istogramma | ヒストグラム | Histogram | Histogram | Histograma |

---

## 7. Risk Analysis & Mitigation

| Risk | Impact | Likelihood | Mitigation Strategy |
| :--- | :--- | :--- | :--- |
| High sample count (multi-hour workout) causing UI lag | Low | Low | Binning is performed asynchronously in `WorkoutRepository` (`Dispatchers.IO`); the resulting histogram has at most 40–60 bins, rendering instantaneously in Compose. |
| Narrow or degenerate telemetry (e.g. constant HR or single sample) | Med | Low | Fallback gracefully: if span $\le \Delta$, render a single centered bin or default back to 5-zone view cleanly. |
| Zero power readings in cycling workout | Med | Med | Filter out 0 W non-pedaling coasting samples or allocate to Z1 based on standard training models. |

---

## 8. Acceptance Criteria Alignment

- **AC-1 (Frequency Distribution Computation)**: Accurate binning of recorded HR (2 bpm bins) and Power (10 W bins) with time spent on Y-axis.
- **AC-2 (Zone Shading Consistency)**: Every histogram bar is shaded with the exact zone color matching athlete's active thresholds.
- **AC-3 (View Mode Switching)**: Athlete can toggle seamlessly between the 5-zone aggregate chart and the detailed histogram.
- **AC-4 (Dark & Light Theme Parity)**: Complies with TTColor tokens across dark and light modes.
- **AC-5 (Performance & Threading)**: Binning executed asynchronously off the main thread with zero frame drops.
