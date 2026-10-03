# Stage 1 Analysis: ATT-2112 - [Bug] [Aftermath/Details] Ensure Description and Extrema Cards Render in Detailed Workout View

**Ticket**: [ATT-2112](https://rainerblind.atlassian.net/browse/ATT-2112)  
**Sub-task**: [ATT-2115](https://rainerblind.atlassian.net/browse/ATT-2115) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2112`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Statement & Motivation

In Sprint 2026-40.12 ([ATT-2030](https://rainerblind.atlassian.net/browse/ATT-2030) / `REQ-UI-240`), the system introduced a consolidated 2-Column Matrix Table presentation in Advanced Tuning (`AdvancedTuningDialog.kt`) to independently configure section visibility for the Workout Summary List (`WorkoutSummary.kt`) and the full-screen Detailed Workout view ([TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt) / [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt)).

During physical hardware verification on Google Pixel 10 (Android 16), the human tester reported:
> *"We have the matrix visualization and this is great. However, the settings do not work 100% correct. E.g, the 'Beschreibung & Notizen' and the 'Extremwerte' are not shown in the Detailed workout although I selected them. Please make sure that these settings work."*

While the preferences were persisted in DataStore (`workout_detail_show_description` and `workout_detail_show_extrema`), athletes inspecting a workout on-device could not see or locate the Description & Notes or Extrema cards, leading to the perception of non-functional settings.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of [TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt) and [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt) identified three technical flaws responsible for this behavior:

### 2.1 Flaw 1: Flawed Vertical Slot Ordering in MapDetailLayout lowerColumn
In [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt) (lines 296–493), `lowerColumn` arranges content vertically as follows:
```text
lowerColumn
├── 1. ElevationProfile (~200dp)
├── 2. TelemetryMetricGraphs:
│      ├── Speed / Pace Graph (~160dp)
│      ├── Heart Rate Graph (~160dp)
│      ├── Power Graph (~160dp)
│      └── Cadence Graph (~160dp)
│      Total Graphs Stack Height: ~840dp
└── 3. analyticsContent (slotted from TrackOnMapScreen):
       ├── Heart Rate Zone Card (~180dp)
       ├── Power Zone Card (~180dp)
       ├── LapSplitVisualizerCard (~220dp)
       ├── WorkoutDescription (Notes, Goal, Method)
       └── WorkoutExtrema (Min, Avg, Max table)
```
* **Severe Layout Displacement**: In `TrackOnMapScreen.kt` (lines 261–317), `WorkoutDescription` and `WorkoutExtrema` were placed at the very end of `analyticsContent`, beneath Zone distributions and Laps.
* Combined with the Elevation Profile and Telemetry charts, the total vertical scroll distance required to reach `WorkoutDescription` and `WorkoutExtrema` exceeded **1,420dp** (more than 1.8 full screen heights on a Google Pixel 10).
* An athlete opening the detailed view expecting to inspect workout notes or extrema stats is presented with the map and elevation profile, while the metadata is pushed completely out of view.

### 2.2 Flaw 2: Inconsistency with WorkoutSummary.kt Visual Hierarchy
In the summary list card ([WorkoutSummary.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt) lines 185–206), the visual hierarchy is:
1. Header
2. **`WorkoutDescription`** (top-level summary)
3. Main Details (Distance, Duration, Pace)
4. **`WorkoutExtrema`** (Min/Avg/Max table)
5. Laps
6. Media (Map & Elevation)
7. Telemetry Charts
8. Zone Distribution

In `WorkoutSummary.kt`, core metadata sits prominently at the top. In `TrackOnMapScreen.kt`, it was relegated to the bottom, breaking UI consistency and user expectations established across Epic `ATT-111`.

### 2.3 Flaw 3: Content Guard Invariant on Empty Descriptions
In [WorkoutDescription.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutdescription/WorkoutDescription.kt) (line 38):
```kotlin
if (!data.description.isNullOrBlank() ||
    !data.goal.isNullOrBlank() ||
    !data.method.isNullOrBlank()) {
    Column(...) { ... }
}
```
If a workout entity has empty description, goal, and method strings, `WorkoutDescription` renders nothing. If `WorkoutExtrema` was also buried beneath 1,420dp of graphs, the entire metadata section appeared completely missing.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Introduce a dedicated `metadataContent` slot in [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt) positioned at the **top** of `lowerColumn` (immediately above `ElevationProfile` and `TelemetryMetricGraph`s).
  2. In [TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt), route `WorkoutDescription` (gated by `activeDetailPrefs.showDescription`) and `WorkoutExtrema` (gated by `activeDetailPrefs.showExtrema`) into `metadataContent`.
  3. Update `hasScrollableContent` in `MapDetailLayout` to recognize `metadataContent != null`.
  4. Ensure `WorkoutDetailPreferences` flow is reactively observed across all detail view entry points (`WorkoutSummariesTabbedScreen`, `PeriodMapScreen`).
  5. Preserve full backward compatibility and existing snapshot sharing functionality.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do NOT modify the settings matrix UI in `AdvancedTuningDialog.kt` (already approved in ATT-2030).
  * Do NOT alter `WorkoutSummary.kt` list card layout or preferences.
  * Do NOT force placeholder text into `WorkoutDescription` when fields are blank (preserving the established non-blank guard invariant).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-240` (*Matrix Table Presentation for Configurable Aftermath Sections: Workout Summary List vs. Workout Details*), `REQ-UI-205` (*Slotted Analytics Content in MapDetailLayout*), and `REQ-UI-213` (*Resilient Map Preview Visibility & Scrollable Telemetry Layout*).
* **Historical Origin & Commit Trace**: Commit `4c5e9228` (Sprint 2026-40.12, ATT-2030) decoupled list preferences from detail preferences; commit `69286d4e` (Sprint 2026-40.5, ATT-1393) created `analyticsContent` in `MapDetailLayout`.
* **Root Reason for Existing Formulation**: `analyticsContent` was originally designed as a single terminal slot for metabolic zone bars and split charts. When `WorkoutDescription` and `WorkoutExtrema` were added to the detail screen in ATT-2030, they were lumped into `analyticsContent` without recognizing the severe scroll starvation caused by stacked telemetry graphs.
* **Preservation of Core Invariants**:
  - Gating toggles in `WorkoutDetailPreferences` remain 100% intact.
  - Interactive map gestures, scrubbing synchronization, and zoom toolbar operations remain unaffected.
  - When `metadataContent` is rendered, it participates in scroll state without breaking edge-to-edge system insets.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 MapDetailLayout: Dedicated `metadataContent` Slot
Extend [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt):
```kotlin
fun MapDetailLayout(
    ...
    metadataContent: (@Composable ColumnScope.() -> Unit)? = null,
    analyticsContent: (@Composable ColumnScope.() -> Unit)? = null,
    ...
)
```
In `lowerColumn`:
```kotlin
val lowerColumn: @Composable (Modifier) -> Unit = { colModifier ->
    Column(modifier = colModifier) {
        // 1. TOP METADATA (Description, Notes, Extrema)
        metadataContent?.let { metadata ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    metadata()
                }
            }
        }

        // 2. ELEVATION & TELEMETRY GRAPHS
        if (showElevationProfile || hasTelemetryGraphs) {
            ...
        }

        // 3. BOTTOM ANALYTICS (Zone Distributions, Laps, Strava)
        analyticsContent?.let { content ->
            ...
        }
    }
}
```
Update detection:
```kotlin
val hasScrollableContent = metadataContent != null || analyticsContent != null || hasTelemetryGraphs
```

### 5.2 TrackOnMapScreen: Slotted Placement
In [TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt):
* Slot `WorkoutDescription` and `WorkoutExtrema` into `metadataContent`.
* Keep `HeartRateZoneDistributionCard`, `PowerZoneDistributionCard`, `LapSplitVisualizerCard`, and `StravaActivitySection` in `analyticsContent`.
* When an athlete inspects a workout, Description & Notes and Extrema appear immediately at the top of the lower pane, directly below the map and split divider.

---

## 6. System Invariants & Risk Assessment

1. **Invariants Preserved**:
   - Zero regression in `MapDetailLayout` callers without `metadataContent` (e.g. `RouteOnMapScreen`, `SegmentOnMapScreen`).
   - Clean-room test suite pass rate (100%).
   - Material 3 theme styling and 9-language translation parity.
2. **Risk Analysis**:
   - Risk: Telemetry badge scrubbing overlay obscuring metadata.
   - Mitigation: The floating badge only appears when scrubbing is active (`currentDistance != null`). In resting state, metadata is completely unobscured.
