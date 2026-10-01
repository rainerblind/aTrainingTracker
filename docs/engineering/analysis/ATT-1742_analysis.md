# Stage 1 Analysis: ATT-1742 - [Aftermath/Splits] High-Aesthetic Redesign of Lap & Interval Split Visualizer

**Ticket**: [[ATT-1742]](https://rainerblind.atlassian.net/browse/ATT-1742)  
**Sub-task**: [[ATT-1822]](https://rainerblind.atlassian.net/browse/ATT-1822) (`[Analysis]`)  
**Parent Epic**: [[ATT-111]](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Branch**: `feature/ATT-1742`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Statement & Motivation

During the Sprint Review of `2026-40.5` for ticket [ATT-1392](https://atrainingtracker.atlassian.net/browse/ATT-1392), the user reviewed the initial lap split visualization on-device and explicitly rejected it:
> *"I saw already saw this. It looks really bad. Thus, please create one ticket to revert this and one ticket to make this much more better."*

In Sprint `2026-40.6` ([ATT-1741](https://atrainingtracker.atlassian.net/browse/ATT-1741)), the unsatisfactory visual component was promptly removed to keep the user interface clean and production-ready.

Now in Sprint `2026-40.7`, **ATT-1742** delivers the complete aesthetic and architectural redesign of the Lap & Interval Split Visualizer, transforming it into a high-impact, professional athletic digest aligned with Material 3 design principles, typography, and cohesive Aftermath integration.

---

## 2. Root Cause Analysis (Forensic Investigation & Aesthetic Post-Mortem)

A forensic inspection of the reverted implementation ([`LapSplitChart.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitChart.kt) and [`LapSplitChartCard.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitChartCard.kt)) revealed five fundamental aesthetic and structural flaws that led to the user's rejection:

1. **Childish & Unprofessional Glyphs**:
   - The component used literal rabbit and hedgehog emojis (`🐇`, `🦔`) to denote fastest and slowest laps. In a serious endurance sport tracker used by ambitious amateur athletes ("Halb-Profis"), emoji badges look cartoonish, juvenile, and out of place.
2. **Clashing, Oversaturated Rainbow Bars**:
   - Each lap row contained a harsh, saturated horizontal progress bar colored according to HR zone colors (`TTColor.Zone1` to `Zone5`). When displayed in a vertical list, this generated an overwhelming "fruit salad" of colors with zero visual calming baseline.
3. **Redundant Card-in-Card Clutter**:
   - The component wrapped every individual lap row inside an elevated nested `Card` inside an outer `LapSplitChartCard`, introducing visual heaviness, excessive borders, and inconsistent padding.
4. **Lack of Clear Typographic Hierarchy & Metric Alignment**:
   - Distance, time, lap badge, and speed were crammed into a single unaligned text flow (`L1 • 1.00 km • 4:12 • 4:12 /km`). Columns did not line up vertically across rows, making pace comparisons across laps visually tiring.
5. **Disconnected Interactive Feedback**:
   - Tapping a lap split highlighted the card with an intense primary border but did not provide smooth elevation transitions, subtle contrast changes, or clear synchronization with the map track polyline.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * **Complete Visual Redesign (`LapSplitVisualizer.kt`)**:
    * Design a sleek, modern Jetpack Compose split comparison component with clean typographic hierarchy.
    * Replace emojis with refined, subtle typographic pill badges (e.g. clean "Best" / "Top Split" chip or subtle accent dot) or benchmark deltas against average workout pace/speed.
    * Use harmonious, subtle tonal palettes (Material 3 `surfaceContainer`, `secondaryContainer`, tonal alpha bars) rather than harsh multi-colored neon bars.
    * Tabular vertical alignment: Lap number, Distance/Duration, Pace/Speed, and Relative Visual Bar cleanly aligned across all rows.
  * **Integration into Aftermath Screens**:
    * Cleanly embed the redesigned card in [`TrackOnMapScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/de/rainerblind/atrainingtracker/ui/map/TrackOnMapScreen.kt) within `analyticsContent`, harmonizing with the existing Elevation Profile, Zone Distribution, and Telemetry graphs.
    * Cleanly embed the visualizer in [`WorkoutLaps.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/WorkoutLaps.kt) above the detailed table.
  * **Smooth Map Highlighting**:
    * Tapping a split smoothly highlights the corresponding route section on the map track polyline.
  * **Localization Parity**:
    * Full 9-language localization for all headers, chips, and accessibility labels.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying underlying SQLite lap storage, manual lap creation, or lap deletion logic.
  * Modifying the Zone Distribution cards (ATT-1811) or continuous telemetry line graph reordering (ATT-1813).
  * Adding complex desktop quadrant analytics or CP curves.

---

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: REQ-UI-204 (Aftermath: Compact Lap & Interval Split Chart Architecture)
* **Historical Origin & Commit Trace**: Ticket ATT-1392 (Sprint 2026-40.5, commit 1c8ef184). Reverted in ATT-1741 (Sprint 2026-40.6, commit 81cf2958).
* **Root Reason for Existing Formulation**: REQ-UI-204 was reverted in ATT-1741 following user review rejection ("looks really bad") to eliminate cartoon emojis and garish bars from production code.
* **Preservation of Core Invariants**: Pure domain models (LapSplitModels.kt), mathematical engine (LapSplitCalculator.kt), SQLite lap tables, map detail layouts, and single-thread database confinement are 100% preserved.
  - Re-activating and refining `REQ-UI-204` to specify the new, premium visual architecture fulfills the original user intent while addressing 100% of the aesthetic deficiencies that caused the earlier rejection.

---

## 5. Architectural Strategy & High-Level Solution

```
┌─────────────────────────────────────────────────────────────┐
│  LapSplitVisualizerCard (Material 3 Tonal Card)             │
│  Header: [Icon] Splits & Runden  •  Avg: 4:32 /km           │
├─────────────────────────────────────────────────────────────┤
│  Row 1: [L1]  1.00 km   4:28 /km  [   ====|    ]            │
│  Row 2: [L2]  1.00 km   4:12 /km  [   ======== ]  [Best]    │
│  Row 3: [L3]  1.00 km   4:45 /km  [   ==|      ]            │
└─────────────────────────────────────────────────────────────┘
                              │ tap
                              ▼
        Highlight segment on Map + subtle card tint
```

1. **New Visual Component (`LapSplitVisualizer.kt`)**:
   - Replaces `LapSplitChart.kt` and `LapSplitChartCard.kt`.
   - Uses subtle proportional horizontal split bars with smooth rounded geometry and curated theme tokens (`primary.copy(alpha = 0.7f)` for baseline, `tertiary` for best split).
   - Strict columnar layout using Compose `Row` and fixed-width modifier slots for numerals and units.
   - Elegant "Best" badge with subtle typography and minimal container tint.
2. **Screen Integration**:
   - Reactive consumption of `splitChartData` via `remember(laps, sportType)`.
   - Seamless map segment slicing (`LapSegmentUtils.sliceLapSegment`) when a lap is selected, rendering the highlighted polyline and start/end pins on the map without UI stutter.
3. **Robust Safety**:
   - Graceful fallback: If workout has < 2 laps, the card is cleanly omitted without occupying screen space.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing tabular lap inspection, manual lap addition, and lap deletion in `WorkoutLaps.kt`.
  2. Tabular and map rendering performance remains smooth (60+ FPS) even for workouts with 50+ laps.
  3. Single-thread database confinement and parent ticket Human Decision Gate remain strictly enforced.
* **Risk Rating**: **LOW**
  - Justification: Pure UI rendering enhancement consuming existing, well-tested calculation utilities (`LapSplitCalculator`). No database schema changes, no background service changes.
