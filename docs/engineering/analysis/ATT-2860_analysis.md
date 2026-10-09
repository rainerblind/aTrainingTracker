# Stage 1 Analysis: ATT-2860 - Visual and UX styling polish for Climb and Segment detail bottom sheets

**Ticket**: [ATT-2860](https://atrainingtracker.atlassian.net/browse/ATT-2860)  
**Sub-task**: [ATT-2890](https://atrainingtracker.atlassian.net/browse/ATT-2890) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-41.5`  
**Branch**: `improvement/ATT-2860`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Problem Statement & Motivation

During Sprint Review 2026-41.4 on a physical Google Pixel 10 (ATT-2774 verification), the athlete accepted the new segment detail modal bottom sheet but requested visual and UX styling polish across both the `ClimbDetailSheet` and `SegmentDetailSheet` components:
> *"Approved. Please create a follow-up ticket to make this, and the one for the climbs, even more nicer."*

Inspection of `ClimbDetailSheet.kt` and `SegmentDetailSheet.kt` reveals several visual and UX discrepancies between the two sheets and compared to modern Material 3 standards:
1. **Metric Typography & Layout Discrepancies**:
   - `ClimbDetailMetricsCard` uses plain text columns without icons, and its fourth metric ("Max. Steigung") erroneously uses `R.string.graph_heading_elevation` ("Höhe") as its label instead of a dedicated maximum grade label.
   - `SegmentDetailMetricsCard` embeds `SegmentDetails` which features icon-driven rows (`MetricItem`), creating an uneven visual rhythm between climb and segment popups.
2. **Map Viewport Theming & Polyline Contrast**:
   - In `SegmentDetailMapCard`, the segment path is rendered via `MapTrack` with `TrackType.BEST`, causing it to render in sport/route blue instead of authentic Strava orange (`TTColor.StravaOrange`).
   - In `ClimbDetailMapCard`, `climbs(listOf(climb))` is layered over a redundant blue `MapTrack` line, muddling the category color highlight.
3. **Elevation Profile Presentation**:
   - `ClimbDetailElevationProfile` renders a rich, color-coded slope profile with grade-based vertical fill (green/yellow/orange/red).
   - In contrast, `SegmentDetailElevationProfileCard` renders the generic whole-route `ElevationProfile` composable, which lacks gradient styling and visual parity with the climb profile.
4. **Card Container & Spacing Polish (Rule 23)**:
   - Both sheets use standard 12.dp radius cards. Upgrading to cohesive Material 3 rounded surfaces (16.dp), refined dividers, and subtle icon-accented metadata headers elevates visual luxury on modern high-DPI devices like the Pixel 10.

---

## 2. Root Cause Analysis & Architectural Gap Analysis

### 2.1 Component Structure Comparison
| Feature Dimension | ClimbDetailSheet (`ClimbDetailSheet.kt`) | SegmentDetailSheet (`SegmentDetailSheet.kt`) | Target Harmonized Standard |
| :--- | :--- | :--- | :--- |
| **Header Badges** | Counter badge + `ClimbCategoryChip` | Counter badge + `ClimbCategoryChip` + PR badge | Unified pill shapes, consistent typography & padding |
| **Metrics Card** | 4 text-only columns, incorrect Max Grade label | 3 rows with `MetricItem` + `PoweredByStrava` | Harmonized modern cards with clear iconography and correct localized labels |
| **Map Track Polyline** | Redundant `MapTrack` beneath `climbs(...)` highlight | `MapTrack` with `BEST` (blue line instead of orange) | Segment in `TTColor.StravaOrange`; Climb cleanly in Category Color |
| **Start/End Markers** | `control_start` and `control_stop` | `control_start` and `control_stop` | High-contrast markers with clear pin anchoring |
| **Elevation Profile** | Dynamic slope gradient canvas (`ClimbDetailElevationProfile`) | Flat route `ElevationProfile` | Rich gradient slope-colored profile for both sheets |
| **Elevation Extrema** | `minAlt m → maxAlt m` text header | `minAlt m → maxAlt m` text header | Crisp typography with `titleSmall` / `labelMedium` |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Fix the mislabeled Maximum Grade metric label in `ClimbDetailMetricsCard` using a proper localized string (`climb_max_grade`).
  2. Elevate `ClimbDetailMetricsCard` to use clean, modern icon-augmented metrics (distance, ascent, avg grade, max grade) harmonized with Material 3 design.
  3. Ensure `SegmentDetailMapCard` renders the segment path in authentic `TTColor.StravaOrange` (`MapSegment`).
  4. Ensure `ClimbDetailMapCard` cleanly highlights the climb polyline in its category color without conflicting base polyline tracks.
  5. Bring visual parity to `SegmentDetailElevationProfileCard` by applying the high-polish gradient slope profile canvas.
  6. Standardize card elevation, shape radii (16.dp), and padding across both sheets in compliance with Rule 23.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No changes to underlying climb detection algorithms (`ClimbDetector.kt`) or Strava segment matching algorithms.
  * No modification to database schemas (`Climbs.db` or `Routes.db`).
  * No alterations to main tracking cockpit overlays.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-300` (*Climb Detail Modal Bottom Sheet*) and `REQ-UI-303` (*Segment Detail Modal Bottom Sheet*).
* **Historical Origin & Commit Trace**: `ATT-2511` (climb detail sheet) and `ATT-2774` (segment detail sheet).
* **Root Reason for Existing Formulation**: Both sheets were developed to provide quick on-map breakdown inspection without full navigation away from the route.
* **Preservation of Core Invariants**: The dismiss lifecycle, bounds calculation, sport type awareness, and test contracts must remain completely intact. The amendments strictly polish UI layout, styling tokens, and visual harmony.

---

## 5. Architectural Strategy & High-Level Solution

1. **Shared Polished Elevation Profile Canvas**:
   - Generalize the grade-colored gradient elevation canvas into a reusable component `GradientSlopeElevationProfile(points: List<PathPoint>, modifier: Modifier)`.
   - Use it in both `ClimbDetailSheet` and `SegmentDetailSheet`, giving athletes the same gorgeous slope-colored visualization for both climbs and segments.
2. **Harmonized Metric Cards**:
   - Modernize `ClimbDetailMetricsCard` with iconic glyphs (`ic_distance`, `ic_ascent`, `ic_grade`, and max grade indicator) and correct string formatting.
   - Refine `SegmentDetailMetricsCard` layout spacing and header alignment.
3. **Map Card Polyline Parity**:
   - Render `MapSegment` with `TTColor.StravaOrange` in `SegmentDetailMapCard`.
   - Streamline `ClimbDetailMapCard` to render the climb path distinctly in its category color with start and summit pins.
4. **Card Polish**:
   - Apply `RoundedCornerShape(16.dp)`, `CardDefaults.elevatedCardColors()`, and consistent 12.dp internal padding across all cards.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing unit tests (`ClimbDetailSheetTest`, `SegmentDetailSheetTest`, `MapContentScopeTest`).
  2. Modularity constraint: Keep both files well under 500 lines.
  3. 9-language localization parity for any new/updated string resources.
* **Risk Assessment**:
  - Purely UI/presentation layer enhancement; minimal risk to application runtime stability.
