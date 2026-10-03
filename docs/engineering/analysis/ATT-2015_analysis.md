# Stage 1 Analysis: ATT-2015 - Render Training Zone Badge in Active Zone Color in ElevationProfile Scrubbing Card

**Ticket**: [ATT-2015](https://rainerblind.atlassian.net/browse/ATT-2015)  
**Sub-task**: [ATT-2097](https://rainerblind.atlassian.net/browse/ATT-2097) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.12`  
**Branch**: `feature/ATT-2015`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & Motivation

In the Aftermath workout inspection screens (`TrackOnMapScreen.kt`, `MapDetailLayout.kt`, and `ElevationProfile.kt`), the floating multi-metric telemetry card (`ScrubbingTelemetryBadge`) displays instantaneous sensor telemetry (Distance, Time, Altitude, Slope, HR, Power, Speed/Pace) whenever the athlete scrubs across recorded charts.

When heart rate or cycling power data is present alongside user-configured training zone thresholds, `ScrubbingTelemetryBadge` formats the metric with its active training zone tag:
- Heart Rate: `${point.hr} bpm • Z$hrZone` (e.g. `145 bpm • Z2`)
- Cycling Power: `${point.power} W • Z$powerZone` (e.g. `220 W • Z3`)

Currently, `ElevationProfile.kt` hardcodes a single static color for the entire `Text` composable:
- Heart Rate text is statically hardcoded to `color = TTColor.Zone4` (orange/yellow) regardless of whether the athlete is in Zone 1, 2, 3, 4, or 5.
- Cycling Power text is statically hardcoded to `color = TTColor.Zone5` (red/purple) regardless of the active power zone.

This creates severe visual dissonance and breaks training zone semantics. For instance, an athlete performing an easy base-building recovery ride in Zone 1 or Zone 2 sees `• Z1` or `• Z2` rendered in alerting orange (`TTColor.Zone4`), creating false alarm cues. The objective of ATT-2015 is to split the metric label and zone suffix so that the base metric value is rendered in neutral, high-contrast text (`MaterialTheme.colorScheme.onSurface`) while the zone suffix (`• Z1` .. `• Z5`) is rendered dynamically in that zone's specific active color (`TelemetryZoneMath.ZONE_COLORS[zone - 1]`).

---

## 2. Root Cause Analysis (Forensic Investigation)

1. **Evolutionary Origin**:
   - In Sprint 2026-40.5 (`ATT-1391` / `REQ-UI-201`), multi-metric scrubbing was first introduced. To distinguish metrics visually within the compact card, static accent colors were assigned: `TTColor.Zone4` for heart rate, `TTColor.Zone5` for cycling power, and `MaterialTheme.colorScheme.primary` for speed/pace.
   - Later, in Sprint 2026-40.7 (`ATT-1839` / `REQ-UI-230`), zone boundary detection and threshold guideline math (`TelemetryZoneMath.determineHeartRateZone`, `determinePowerZone`) were introduced for chart backgrounds and telemetry graphs. The zone indicator ` • Z$zone` was appended directly into the existing string template without refactoring the enclosing `Text` composable.
2. **Current Code Structure in `ElevationProfile.kt`**:
   ```kotlin
   if (hasHr) {
       val hrZone = effectiveHrThresholds?.let {
           TelemetryZoneMath.determineHeartRateZone(point.hr.toDouble(), it)
       }
       val hrText = if (hrZone != null) "${point.hr} bpm • Z$hrZone" else "${point.hr} bpm"
       Text(
           text = hrText,
           style = MaterialTheme.typography.labelSmall,
           fontWeight = FontWeight.SemiBold,
           color = TTColor.Zone4 // <-- Statically hardcoded to Zone 4!
       )
   }
   if (hasPower) {
       val powerZone = effectivePowerThresholds?.let {
           TelemetryZoneMath.determinePowerZone(point.power.toDouble(), it)
       }
       val powerText = if (powerZone != null) "${point.power} W • Z$powerZone" else "${point.power} W"
       Text(
           text = powerText,
           style = MaterialTheme.typography.labelSmall,
           fontWeight = FontWeight.SemiBold,
           color = TTColor.Zone5 // <-- Statically hardcoded to Zone 5!
       )
   }
   ```
3. **Architectural Gap**:
   - The label does not separate the numeric metric reading from the classification zone indicator.
   - The zone color palette (`TelemetryZoneMath.ZONE_COLORS` / `TTColor.Zone1` to `Zone5`) is not wired to the zone suffix text span or badge component.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. In `ElevationProfile.kt` (`ScrubbingTelemetryBadge`), separate the numeric metric string from the zone tag for both Heart Rate and Cycling Power.
  2. Render the base metric value (e.g. `145 bpm`, `220 W`) in standard high-contrast theme text color (`MaterialTheme.colorScheme.onSurface`).
  3. Render the zone indicator (e.g. `• Z1` .. `• Z5`) dynamically in the exact matching zone color using `TelemetryZoneMath.ZONE_COLORS[zone - 1]` (`TTColor.Zone1` .. `TTColor.Zone5`).
  4. Ensure graceful fallback: if thresholds are unconfigured or zone detection returns `null`, render only the base metric value in `MaterialTheme.colorScheme.onSurface` without trailing separators or dangling characters.
  5. Validate via targeted unit and visual contract tests.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Speed / Pace zone colorization: Speed/Pace does not use a 5-zone physiological model; its existing styling (`MaterialTheme.colorScheme.primary`) remains unchanged.
  2. Modifying `TelemetryZoneMath` zone boundaries or threshold calculations (`calculateHeartRateZoneBands`, `determinePowerZone`): Core zone math is sound and fully verified.
  3. Modifying graph header labels in `MapDetailLayout.kt`: ATT-2015 specifically targets the scrubbing badge card in `ElevationProfile.kt`.
  4. SQLite schema changes: Pure UI rendering enhancement; zero database mutations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

Net-new requirement only (`REQ-UI-242`). No existing requirements modified.

* **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-242`), refining `REQ-UI-201` (*Synchronized Multi-Metric Scrubbing on Elevation Profile*) and `REQ-UI-230` (*Telemetry Metric Graph Zone Visualization & Threshold Guides*) under Epic `ATT-111` (*Compact Post-Workout Visual Analytics & Graphs*).
* **Historical Origin & Commit Trace**: Commit `e7a6a273` (`ATT-1391`, Sprint 2026-40.5) and commit `677e43da` (`ATT-1839`, Sprint 2026-40.7).
* **Root Reason for Existing Formulation**: Static colors (`TTColor.Zone4`, `TTColor.Zone5`) were placeholder accents chosen before zone calculation was introduced. Retaining them after adding zone text caused semantic contradiction.
* **Preservation of Core Invariants**:
  - `ScrubbingTelemetryBadge` composable signature, parameter types, and null safety remain identical.
  - Existing contract test `TelemetryMetricGraphZoneTest.kt` assertions remain 100% satisfied.
  - Visual layout dimensions, typography size (`labelSmall`), and horizontal row arrangements are strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

### Component: `ElevationProfile.kt` (`ScrubbingTelemetryBadge`)
Utilize `AnnotatedString` via `buildAnnotatedString` within `ScrubbingTelemetryBadge`:
1. When `hasHr` is true:
   - Compute `hrZone = effectiveHrThresholds?.let { TelemetryZoneMath.determineHeartRateZone(point.hr.toDouble(), it) }`.
   - If `hrZone != null && hrZone in 1..5`:
     - Construct an `AnnotatedString` where `${point.hr} bpm` has `SpanStyle(color = MaterialTheme.colorScheme.onSurface)` and ` • Z$hrZone` has `SpanStyle(color = TelemetryZoneMath.ZONE_COLORS[hrZone - 1], fontWeight = FontWeight.Bold)`.
   - Else: render `${point.hr} bpm` with `color = MaterialTheme.colorScheme.onSurface`.
2. When `hasPower` is true:
   - Compute `powerZone = effectivePowerThresholds?.let { TelemetryZoneMath.determinePowerZone(point.power.toDouble(), it) }`.
   - If `powerZone != null && powerZone in 1..5`:
     - Construct an `AnnotatedString` where `${point.power} W` has `SpanStyle(color = MaterialTheme.colorScheme.onSurface)` and ` • Z$powerZone` has `SpanStyle(color = TelemetryZoneMath.ZONE_COLORS[powerZone - 1], fontWeight = FontWeight.Bold)`.
   - Else: render `${point.power} W` with `color = MaterialTheme.colorScheme.onSurface`.

This ensures seamless inline text layout without multi-element wrapping or baseline misalignment, keeping the badge compact and 100% compliant with design specifications.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regressions across existing unit test suite (1,380+ tests).
  2. Single-line inline presentation: No vertical wrapping or layout jitter when transitioning between zones during scrubbing.
  3. Dark mode & light mode legibility: Base metric in `onSurface` provides optimal contrast across themes; zone colors `TTColor.Zone1`..`Zone5` are curated for dark and light surfaces.
  4. Parent ticket Human Decision Gate remains strictly guarded.
* **Risk Rating**: **LOW**. Localized composable rendering change with no state mutations or database impacts.
