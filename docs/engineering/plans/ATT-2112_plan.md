# Stage 3: Implementation Plan - ATT-2112 (Revision 2): Ensure Description and Extrema Cards Render in Detailed Workout View

**Ticket**: [ATT-2112](https://rainerblind.atlassian.net/browse/ATT-2112)  
**Sub-task**: [ATT-2128](https://rainerblind.atlassian.net/browse/ATT-2128) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Requirement Mapping**: `REQ-UI-245`  
**Test Mapping**: `TST-UI-204`  
**Branch**: `feature/ATT-2112`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Description & Background

In Sprint 2026-40.12 (ATT-2030 / `REQ-UI-240`), independent preference controls were established for Workout Summary List vs. Detailed Workout view. In the workout summary list (`WorkoutSummary.kt`), `WorkoutDescription` and `WorkoutExtrema` are positioned immediately beneath `WorkoutHeader` at the top of the card, well above the map preview.

In iteration 1 of ATT-2112, `metadataContent` was introduced inside `lowerColumn` below the map. During physical device review on Pixel 10 (Sprint Review Ceremony 2), the human reviewer rejected this placement (`n.i.O.`), stating:
> *"The order within the details workout must be identical to the order within the workout summary. I.e. the description and notes must be above the map. Thus, I moved the ticket back."*

This revised plan relocates `metadataContent` in `MapDetailLayout.kt` to the top static Column immediately following `header()` and above the interactive map (`BoxWithConstraints`), and removes it from `lowerColumn`. This achieves 100% visual order parity between Workout Summary and Detailed Workout.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-245` (*Aftermath/Details: Upper Metadata Slot Architecture & Reactive Detail Section Rendering in MapDetailLayout*)
* **Test Mapping**: `TST-UI-204` (*Upper Metadata Slot & WorkoutDetailPreferences Reactive Rendering Verification*)
  - `TST-UI-204.1`: Structural Contract Test in `MapDetailLayoutMetadataSlotContractTest.kt` (asserting placement above map and omission from `lowerColumn`)
  - `TST-UI-204.2`: Routing Contract Test in `TrackOnMapScreenMetadataRoutingContractTest.kt`
  - `TST-UI-204.3`: 9-Language Localization Audit via `TranslationParityTest.kt`
  - `TST-UI-204.4`: Clean-room test suite execution (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Layout Regressions on Existing Callers**: Callers of `MapDetailLayout` that do not pass `metadataContent` (`RouteOnMapScreen.kt`, `SegmentOnMapScreen.kt`, `LIveSegmentSheet.kt`) default `metadataContent = null` and retain 100% layout and visual equivalence.
2. **Visual Order Parity Invariant**: Detailed Workout section sequence matches `WorkoutSummary.kt`: Header -> Description -> Extrema -> Map -> Telemetry Graphs -> Analytics Cards.
3. **Gesture & Scrubbing Transparency**: The upper metadata slot sits in the top static column. It does NOT intercept scrubbing gestures on graphs or pan/zoom gestures on the map.
4. **Reactive Preference Synchronization**: Visibility responds immediately to DataStore preference changes via `MyPreferenceManager.workoutDetailPreferencesFlow`.
5. **Empty-State Protection**: If no notes exist, `WorkoutDescription` renders nothing; if no extrema exist, `WorkoutExtrema` renders nothing.
6. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
7. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `MapDetailLayout.kt` (Top Metadata Placement Above Map)
* In the root `Column`:
  Render `metadataContent` immediately below `header()` inside the top Column, preceding the resizable viewport:
  ```kotlin
        Column(
            modifier = Modifier.fillMaxWidth()...
        ) {
            if (!useStatusBarsPadding) {
                MinimumDragHandle()
            }

            // 1. HEADER (Slotted)
            Surface(...) {
                ...
                header()
            }

            // 2. UPPER METADATA (Description & Extrema ABOVE the map - REQ-UI-245 / ATT-2112)
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
* Remove `metadataContent?.let { ... }` from `lowerColumn`, avoiding duplicate rendering.

### Component 2: `TrackOnMapScreen.kt` (Component Routing)
* `TrackOnMapScreen` routes `WorkoutDescription` (gated by `activeDetailPrefs.showDescription`) and `WorkoutExtrema` (gated by `activeDetailPrefs.showExtrema`) into `metadataContent`.
* Retains `HeartRateZoneDistributionCard`, `PowerZoneDistributionCard`, `LapSplitVisualizerCard`, and `StravaActivitySection` in `analyticsContent`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Implementation Gate Check
* Command: `python3 tools/jira_util.py check-gate ATT-2128`
* Verification: Ensure exit code 0 (`GATE_PASSED`) before any source code edits.

### Step 2: Relocate `metadataContent` Above the Map in `MapDetailLayout.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
* Changes:
  1. Render `metadataContent` in top Column immediately following `header()` Surface.
  2. Remove `metadataContent` from `lowerColumn`.

### Step 3: Update `MapDetailLayoutMetadataSlotContractTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutMetadataSlotContractTest.kt`
* Changes:
  1. Update `testMapDetailLayout_rendersMetadataContentAtTopOfLowerColumnBeforeGraphs` to `testMapDetailLayout_rendersMetadataContentAboveMapBeforeViewport` asserting `metadataContent` renders between `header()` and `BoxWithConstraints`.
  2. Assert `metadataContent` is omitted from `lowerColumn`.

### Step 4: Targeted Unit Tests
* Verification Command:
  `./gradlew testDebugUnitTest --tests "*MetadataSlotContractTest*" --tests "*MetadataRoutingContractTest*" --tests "*MapDetailLayoutTest*"`

### Step 5: Full Clean-Room Regression Suite
* Command: `./gradlew testDebugUnitTest`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - JVM structural contract tests verifying method signatures, parameters, slot placement above the map, and preference gating.
  - Full `./gradlew testDebugUnitTest` verifying 100% pass rate.
* **Rollback Plan**:
  - `feature/ATT-2112` is isolated off `sprint/2026-40.13`. Discarding the branch restores the baseline cleanly.
