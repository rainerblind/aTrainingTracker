# Stage 1 Analysis: ATT-5487 - [Feature] Workout Details (Zonen Tab): Add Grade Distribution Card

**Ticket**: [ATT-5487](https://rainerblind.atlassian.net/browse/ATT-5487)  
**Sub-task**: [ATT-5510](https://rainerblind.atlassian.net/browse/ATT-5510) (`[Analysis]`)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.7`  
**Branch**: `feature/ATT-5487`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-05  

---

## 1. Problem Statement & Motivation
Workout Details' **Zonen / Histogramm** tab (`WorkoutDetailSectionType.ZONES`) currently displays distribution cards only for **Heart Rate** (`HeartRateZoneDistributionCard`) and **Power** (`PowerZoneDistributionCard`). While the elevation profile provides a marginal altitude histogram (dwell time per sea-level meter), users lack a dedicated card to visualize time/distance spent across slope/grade categories. ATT-5487 adds a dedicated **`GradeZoneDistributionCard`** featuring 7 canonical slope zones matching `GradeLegend` in `ElevationProfile.kt` (`TTColor.Zone1..Zone7`), fine-grained 1% grade binning with `ElevationSmoothingMath` Loess smoothing, and the 3-way view toggle (Balken | Säulen | Histogramm).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Architectural Gap**: Current zone models and cards support HR (5 zones) and Power (7 zones). Grade telemetry requires:
  1. Calculation engine: `ZoneDistributionCalculator.calculateGradeDistribution(points, sigma)` mapping smoothed elevation segments into 7 zones and 1% histogram bins.
  2. UI Card: `GradeZoneDistributionCard.kt` reusing `ZoneDistributionStackedBar`, `ZoneDistributionColumnChart`, and `TelemetryHistogramChart`.
  3. Tab availability: Update `hasZonesData` in `TrackOnMapScreen.kt` to include `gradeZoneDistribution != null`.
* **Independent Peer Review & Verification (ATT-3534 / Rules 69 & 70)**:
  - Audited `ElevationProfile.kt` (L227-238): `getElevationGradeColor` defines canonical thresholds `< 2%`, `2–5%`, `5–10%`, `10–15%`, `15–20%`, `20–25%`, and `> 25%`, perfectly matching `GradeLegend` and pre-localized strings `grade_legend_under_2` .. `grade_legend_over_25`.
  - Audited `TelemetryHistogramChart.kt` (L64-466): Supports arbitrary `unit` (`"%"`), negative data ranges (`dataMin..dataMax`), and dark/light zone outlines (`TTColor.Zone6`/`Zone7`).
  - Audited `TrackOnMapScreen.kt` (L204): `hasZonesData = hrZoneDistribution != null || powerZoneDistribution != null` must be extended with `|| gradeZoneDistribution != null` so GPS/altitude workouts without HR/Power sensors surface the Zonen tab.
  - Audited smoothing: Applying `ElevationSmoothingMath.smoothAltitudes` ($\sigma = 21.0\text{m}$) eliminates GPS quantization noise and prevents artificial `> 25%` grade spikes.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add `ZoneDistributionCalculator.calculateGradeDistribution(...)` using Loess smoothing and 7 slope bands.
  2. Implement `GradeZoneDistributionCard.kt` in `ui/aftermath/zones/` with `R.drawable.ic_grade`, localized title, and 3-way toggle.
  3. Wire `gradeZoneDistribution` through `WorkoutDetailTelemetry` / `TrackOnMapAftermathViewModel`, `TrackOnMapScreen`, and `WorkoutDetailTabbedContent`.
  4. Ensure 9-language localization parity for `aftermath_grade_zones_title` (`TranslationParityTest`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. No changes to `ElevationProfile` canvas or marginal altitude histogram.
  2. No database schema migration; computed on-the-fly from existing `PathPoint` elevation/distance samples on `Dispatchers.IO`.
  3. No changes to Settings matrix or preference schemas beyond standard card display mode propagation.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Net-new requirement `REQ-UI-453`** (*Aftermath/Zones: Grade/Slope Distribution Card*), extending `REQ-UI-231` (Fine-Grained Telemetry Frequency Histogram) and `REQ-UI-372` (Zone Distribution Cards).
* **Historical Trace & Invariant Preservation**: Preserves `REQ-UI-371` (Elevation Profile 7-zone color bands) and `REQ-UI-408` (Tabbed aftermath navigation). Zero existing requirements modified or broken.

---

## 5. Architectural Strategy & High-Level Solution
* **Calculation Engine**: `ZoneDistributionCalculator.calculateGradeDistribution(pathPoints)` smooths altitudes via `ElevationSmoothingMath.smoothAltitudes(pathPoints)`, computes segment grades via `ElevationSmoothingMath.calculateGrade`, and bins duration $\Delta t \le 5\text{s}$ into 7 zones and 1% histogram bins.
* **Side-Effect & Cross-Caller Audit (Rule 68)**:
  - `AftermathMapUIState`: Add `gradeZoneDistribution: ZoneDistributionData?`.
  - `TrackOnMapAftermathViewModel`: Calculate `gradeZoneDistribution` during `loadAftermathData` off-main-thread (`ioDispatcher`).
  - `WorkoutDetailTabConfig`: Include `gradeZoneDistribution != null` in `hasZonesData`.
  - Workouts without altitude/distance data gracefully evaluate to `null` with zero UI regression.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Color and boundary parity with `ElevationProfile.kt` (`getElevationGradeColor`).
  2. 100% off-main-thread execution on `Dispatchers.IO`; zero frame drops on aftermath entry.
  3. Zero database schema migrations or SQLite table modifications.
  4. Zero regressions in existing HR/Power zone cards and unit tests.
* **Risk Rating**: **LOW** (Purely additive UI card and pure mathematical calculation engine reusing tested histogram/bar infrastructure).
