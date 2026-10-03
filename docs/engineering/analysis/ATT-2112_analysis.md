# Stage 1 Analysis: ATT-2112 (Revision 2) - [Bug] [Aftermath/Details] Ensure Description and Extrema Cards Render in Detailed Workout View

**Ticket**: [ATT-2112](https://rainerblind.atlassian.net/browse/ATT-2112)  
**Sub-task**: [ATT-2125](https://rainerblind.atlassian.net/browse/ATT-2125) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2112`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Statement & Sprint Review Mandate

In Sprint 2026-40.12 ([ATT-2030](https://rainerblind.atlassian.net/browse/ATT-2030) / `REQ-UI-240`), the system introduced independent section configuration for the Workout Summary List (`WorkoutSummary.kt`) and the Detailed Workout inspection view ([TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt) / [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt)).

In iteration 1 of [ATT-2112](https://rainerblind.atlassian.net/browse/ATT-2112), `WorkoutDescription` and `WorkoutExtrema` cards were hoisted from the bottom of `analyticsContent` into an upper metadata slot in `lowerColumn` (positioned below the map and split divider, but above telemetry graphs).

During physical device verification on Google Pixel 10 in Sprint Review (Ceremony 2), the human reviewer rejected this placement (`n.i.O.`), stating:
> *"The order within the details workout must be identical to the order within the workout summary. I.e. the description and notes must be above the map. Thus, I moved the ticket back."*

---

## 2. Root Cause & Architectural Gap Analysis

### 2.1 Visual Hierarchy Discrepancy: Summary vs. Details
A structural audit of [WorkoutSummary.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt) reveals the canonical aftermath section sequence:
1. `WorkoutHeader` (Title, date, sport type)
2. **`WorkoutDescription`** (Description, goals, method notes)
3. `WorkoutDetails` (Primary stats: distance, time, pace/speed)
4. **`WorkoutExtrema`** (Min/Avg/Max table)
5. `WorkoutLaps` (Lap split overview)
6. `StravaActivitySection`
7. **Media Section (`PathPreviewMap` / `WorkoutMediaSection`)**
8. `TelemetryMetricGraph`s (Speed, HR, Power)
9. Zone Distribution Cards (HR & Power 5-zone distributions)

Notice the critical invariant:
* In `WorkoutSummary.kt`, **`WorkoutDescription` and `WorkoutExtrema` are placed ABOVE the map** (items 2 & 4, while Map is at item 7).
* In iteration 1 of `TrackOnMapScreen.kt` and `MapDetailLayout.kt`, the map was housed in the upper split pane (`mapBox`), with `metadataContent` placed inside `lowerColumn` beneath the map.
* Consequently, `WorkoutDescription` appeared below the map rather than above it, violating visual consistency across views.

### 2.2 Relocating `metadataContent` Above the Map in `MapDetailLayout.kt`
In [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt), the root layout Column renders:
```text
Root Column
├── Drag Handle (for sheets, if !useStatusBarsPadding)
├── Header Surface (hosting header())
├── [TARGET LOCATION FOR metadataContent] (Description & Extrema above the map)
└── Resizable Viewport (BoxWithConstraints, weight(1f))
    └── Column
        ├── mapBox (weight(splitFraction))
        ├── SplitPaneDivider
        └── Lower Viewport Box (weight(1f - splitFraction))
            └── Column
                ├── GlobalTelemetryZoomToolbar
                └── lowerColumn (verticalScroll: graphs + analyticsContent)
```
By placing `metadataContent` immediately below `header()` in the top Column before the resizable viewport:
1. `WorkoutDescription` and `WorkoutExtrema` render **above the map**, achieving 100% visual order parity with `WorkoutSummary.kt`.
2. The interactive map (`mapBox`) remains in the resizable split pane, preserving touch isolation and gesture handling.
3. The lower scrollable section (`lowerColumn`) remains dedicated to sticky zoom controls, synchronized graphs, and analytics distribution cards without metadata clutter.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Relocate `metadataContent` in [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt) from `lowerColumn` to the top static column immediately following `header()` and preceding `BoxWithConstraints` (above the map).
  2. Remove `metadataContent` invocation from `lowerColumn` to prevent duplicate card rendering.
  3. In [TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt), ensure `WorkoutDescription` (gated by `activeDetailPrefs.showDescription`) and `WorkoutExtrema` (gated by `activeDetailPrefs.showExtrema`) render cleanly within `metadataContent`.
  4. Preserve empty-state guards so that blank notes or empty extrema tables do not render blank whitespace above the map.
  5. Update structural contract tests in `MapDetailLayoutMetadataSlotContractTest.kt` to assert that `metadataContent` is rendered above `mapBox`.
* **Out-of-Scope Non-Goals**:
  * Do NOT alter `WorkoutSummary.kt` (already correct).
  * Do NOT modify the settings matrix UI in `AdvancedTuningDialog.kt`.
  * Do NOT move `analyticsContent` (zones, laps, Strava) above the map (they belong beneath the graphs).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-245` (*Aftermath/Details: Upper Metadata Slot Architecture & Reactive Detail Section Rendering in MapDetailLayout*).
* **Historical Origin & Commit Trace**:
  - `ATT-2030` (commit `4c5e9228`): Decoupled list preferences from detail preferences.
  - `ATT-2112` Iteration 1 (commit `a22df4b1`): Introduced `metadataContent` inside `lowerColumn`.
* **Root Reason for Revision**: Iteration 1 placed `metadataContent` inside `lowerColumn` below the map. The human reviewer rejected this during Sprint Review because `WorkoutSummary.kt` places Description and Extrema above the map.
* **Preservation of Core Invariants**:
  - Non-aftermath callers of `MapDetailLayout` (`RouteOnMapScreen`, `SegmentOnMapScreen`, `LIveSegmentSheet`) passing `metadataContent = null` remain 100% backward compatible.
  - SplitPaneDivider drag math (`SplitPaneMath`) operates on `constraints.maxHeight` without jitter.
  - 9-language localization parity and clean-room test suite pass rate are strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 MapDetailLayout Top-Section Placement
In [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt):
```kotlin
        Column(
            modifier = Modifier
                .fillMaxWidth()
                ...
        ) {
            if (!useStatusBarsPadding) {
                MinimumDragHandle()
            }

            // 1. HEADER (Slotted)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = if (useStatusBarsPadding) RectangleShape else BottomSheetDesign.SheetShape,
                modifier = if (useStatusBarsPadding) Modifier.statusBarsPadding() else Modifier
            ) {
                Box(modifier = Modifier.drawWithContent {
                    headerLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(headerLayer)
                }) {
                    header()
                }
            }

            // 2. UPPER METADATA (Description, Extrema ABOVE the map - REQ-UI-245 / ATT-2112)
            metadataContent?.let { content ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        content()
                    }
                }
            }
        }
```

### 5.2 Elimination from `lowerColumn`
Remove `metadataContent?.let { ... }` from `lowerColumn`, leaving `lowerColumn` to manage only `ElevationProfile`, `TelemetryMetricGraph`s, and `analyticsContent`.

---

## 6. System Invariants & Risk Assessment

1. **Invariants Preserved**:
   - Zero visual regression in non-aftermath `MapDetailLayout` callers.
   - Clean-room test suite pass rate (100%).
   - Material 3 theme styling and 9-language translation parity.
2. **Risk Analysis**:
   - Risk: Screen real estate compression on small landscape devices.
   - Mitigation: `WorkoutDescription` and `WorkoutExtrema` are guarded against empty states and render with compact padding. `BoxWithConstraints` dynamically allocates the remaining screen height `weight(1f)` to the map and graphs via `SplitPaneMath`.
