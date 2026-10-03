# Stage 1 Analysis: ATT-1739 - [Aftermath/Zones] Redesign Zone Distribution (HR & Power) from horizontal stacked bar to vertical column chart

**Ticket**: [ATT-1739](https://atrainingtracker.atlassian.net/browse/ATT-1739)  
**Sub-task**: [ATT-1749](https://atrainingtracker.atlassian.net/browse/ATT-1749) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Branch**: `feature/ATT-1739`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Statement & Motivation

During the Sprint Review of Sprint `2026-40.5` for tickets [ATT-1389](https://atrainingtracker.atlassian.net/browse/ATT-1389) and [ATT-1390](https://atrainingtracker.atlassian.net/browse/ATT-1390), the human user reviewed the Aftermath post-workout visual analytics and rejected the single horizontal stacked bar presentation:
> *"The zones (Zone 1, Zone 2, .., Zone 5) must be on the x-Axis; the time in the corresponding Zone must be on the y-Axis."*

In sports telemetry and exercise physiology (Garmin, TrainingPeaks, WKO5, GoldenCheetah), athletes intuitively expect time-in-zones to be depicted as a **histogram / vertical column chart**. A single horizontal stacked bar suffers from visual compression when certain zones have small durations, lacks an intuitive vertical baseline, and fails to give an immediate impression of training intensity distribution.

The goal of ATT-1739 is to redesign [HeartRateZoneDistributionCard.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/HeartRateZoneDistributionCard.kt) and [PowerZoneDistributionCard.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneDistributionCard.kt) to display a vertical column chart (histogram) with Zones 1–5 on the X-axis and duration on the Y-axis.

---

## 2. Root Cause Analysis (Forensic Investigation & Gap Analysis)

### 2.1 Current Implementation
Currently, `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard` render:
1. Header row: Icon (`R.drawable.ic_heart_rate` or `R.drawable.ic_power`), title (`R.string.aftermath_hr_zones_title` / `R.string.aftermath_power_zones_title`), and total active duration.
2. A single horizontal stacked bar (`Row` with `Modifier.height(14.dp)` where each segment has `Modifier.weight(entry.percentage)`).
3. A separate legend row below the bar showing colored dots, "Z1"–"Z5", durations, and percentages.

### 2.2 Visual and Ergonomic Deficiencies
- **Horizontal Compression**: Short interval bursts (e.g. Zone 5 VO2max) result in narrow 1–2mm slivers that are hard to inspect.
- **Mental Model Mismatch**: The Y-axis universally denotes magnitude/duration in statistical charts, while discrete categories (Zones 1 to 5) belong on the X-axis.
- **Redundant Visual Elements**: Having a stacked bar plus a separate legend below duplicates information without providing an intuitive histogram profile.

### 2.3 Required Histogram Architecture
- **X-Axis**: 5 discrete columns corresponding to Zone 1 through Zone 5 (`entries[0]` to `entries[4]`).
- **Y-Axis**: Vertical column height strictly proportional to time spent in that zone.
  - To maximize visual clarity across workouts of varying length, the column height should scale relative to the maximum duration among the 5 zones:
    $$\text{heightFraction}_i = \begin{cases} \frac{t_i}{\max_{j}(t_j)} & \text{if } \max_{j}(t_j) > 0 \\ 0 & \text{otherwise} \end{cases}$$
  - A subtle minimum height (e.g. 4.dp) ensures that non-zero durations remain visible even if tiny compared to the dominant zone.
- **Labels per Column**:
  - Above/within the column: Formatted duration (`m:ss` or `h:mm:ss`) and percentage (`X%`).
  - Below each column: Zone label (`Z1`..`Z5`) with corresponding zone color indicator (`TTColor.Zone1` through `TTColor.Zone5`).
- **Baseline**: A clean baseline divider to ground the columns.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Redesign [HeartRateZoneDistributionCard.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/HeartRateZoneDistributionCard.kt) to render a vertical column chart (histogram).
  * Redesign [PowerZoneDistributionCard.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneDistributionCard.kt) to render a vertical column chart (histogram).
  * Reusable column chart composable component ([ZoneDistributionColumnChart.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionColumnChart.kt)) to eliminate code duplication between HR and Power cards.
  * Preserving header icons, localized titles, total active duration, card container styling, and null/empty telemetry suppression.
  * Adding composable/unit tests for column chart formatting and layout.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying [ZoneDistributionCalculator.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionCalculator.kt) (mathematical classification and pause clamping logic remain 100% intact).
  * Modifying [WorkoutRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt) or [TrackOnMapAftermathViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TrackOnMapAftermathViewModel.kt).
  * Adding interactive scrubbing or touch gestures to the zone histogram.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**:
  - `REQ-UI-202` (Aftermath: Heart Rate 5-Zone Distribution Architecture)
  - `REQ-UI-203` (Aftermath: Cycling Power 5-Zone Distribution Architecture)
* **Historical Origin & Commit Trace**:
  - Sprint `2026-40.5`, Tickets `ATT-1389` & `ATT-1390`.
* **Root Reason for Existing Formulation**:
  - The original specification prescribed a "rounded horizontal stacked bar with segments filled with `TTColor.Zone1` through `TTColor.Zone5`". This was chosen for minimal vertical footprint, but sprint review feedback revealed it fails user expectations and visual ergonomics.
* **Preservation of Core Invariants**:
  - Domain models (`ZoneDistributionData`, `ZoneTimeEntry`), calculation math, database extraction on `Dispatchers.IO`, ViewModel state flow, and 9-language localization parity (`R.string.aftermath_hr_zones_title`, `R.string.aftermath_power_zones_title`) are strictly preserved. Only section 5 ("Visual UI Component") of both requirements is updated to mandate a vertical column chart (histogram).

---

## 5. Architectural Strategy & High-Level Solution

1. **Shared Column Chart Component (`ZoneDistributionColumnChart.kt`)**:
   - Create a reusable `@Composable fun ZoneDistributionColumnChart(distribution: ZoneDistributionData, modifier: Modifier = Modifier)`:
     - Fixed height (e.g. 120.dp) to maintain a compact Aftermath footprint while providing sufficient height for vertical bars.
     - Row of 5 columns with `Arrangement.SpaceEvenly`.
     - Each column contains:
       - Duration & percentage text (e.g. `12:35`, `42%`) in `labelSmall`.
       - Vertical bar with rounded top corners (`RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)`), filled with `entry.color`. Height is proportional to `entry.durationSec / maxDurationSec` (with min height 4.dp if `durationSec > 0`, 0.dp if `durationSec == 0`).
       - Baseline horizontal divider (`1.dp` height in `outlineVariant`).
       - Zone badge: "Z1" through "Z5" in `titleSmall` / `labelMedium` with bold font weight.
2. **Refactor `HeartRateZoneDistributionCard` and `PowerZoneDistributionCard`**:
   - Both cards delegate their chart rendering to `ZoneDistributionColumnChart`.
   - Card container, header row (heart/power icon, title, total active time), and margin/padding are preserved.
3. **Traceability**:
   - Update `REQ-UI-202` and `REQ-UI-203` in `docs/requirements.md`.
   - Update `TST-UI-156` and `TST-UI-157` in `docs/tests.md`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features, tests, and database access.
  2. Single-thread SQLite confinement on `Dispatchers.IO` preserved.
  3. Parent ticket Human Decision Gate remains strictly enforced.
  4. 100% 9-language localization parity maintained.
* **Risk Rating**: **LOW**
  - Justification: Pure visual composable refactoring without database schema, network, or threading changes. Covered by unit tests and Compose previews.
