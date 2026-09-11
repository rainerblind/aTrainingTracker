# Stage 3 Implementation Plan: Edit Lap Details & Interactive Map (ATT-511)

* **Ticket**: [ATT-511](https://rainerblind.atlassian.net/browse/ATT-511) (*[Feature] Edit Lap details*)
* **Sub-task**: [ATT-898](https://rainerblind.atlassian.net/browse/ATT-898) (*[Impl-Plan] Edit Lap details*)
* **Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*[Epic] Laps*)
* **Requirement**: `REQ-UI-142`
* **Test Specification**: `TST-UI-095`
* **Target Version**: `V4.9.36`
* **Branch**: `feature/ATT-511`

---

## 1. Overview & Objectives

This implementation plan defines the architectural changes, component additions, and testing procedures required to fulfill `REQ-UI-142` and `TST-UI-095`:
1. Provide an ergonomic, reactive editing bottom sheet (`LapEditBottomSheet`) launched by tapping any lap row in `WorkoutLaps`.
2. Support single-tap preset assignment via quick-tag suggestion chips (`Warm-up`, `Interval`, `Recovery`, `Hill Climb`, `Tempo`, `Sprint`, `Cool-down`).
3. Support custom lap names (with fallback placeholder `"Lap X"`) and multi-line notes/description.
4. Support sequential lap navigation (`< Previous` and `Next >`) to iteratively label multiple laps without closing the sheet.
5. In expanded sheet state, display an interactive segment map rendering the entire workout polyline (subtle secondary stroke) and the selected lap segment (vibrant primary stroke) with start/end markers and dynamic camera bounding box auto-zoom.
6. Persist changes to `Laps.db` via `LapsDatabaseManager.updateLapDetails` and update in-memory cache in `WorkoutRepository` to reflect edits immediately in `WorkoutSummary` without list reloading.
7. Maintain 100% localization parity across all 9 supported languages.

---

## 2. Component Breakdown & Detailed Modifications

### 2.1 Pure Geometry Utility: `LapSegmentUtils.kt` [NEW]
* **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/LapSegmentUtils.kt`
* **Requirement**: `REQ-UI-142` (Section 5)
* **Test**: `TST-UI-095` (Section 1)
* **Design**:
  - `calculateLapDistanceRange(laps: List<LapData>, targetLapNr: Long): Pair<Double, Double>`:
    Computes cumulative start distance $D_{\text{start}} = \sum_{i=1}^{k-1} \text{lap}_i.\text{distanceTotalM}$ and end distance $D_{\text{end}} = D_{\text{start}} + \text{lap}_k.\text{distanceTotalM}$.
  - `sliceLapSegment(points: List<LatLng>, dists: List<Double>, startDistM: Double, endDistM: Double): List<LatLng>`:
    Pure zero-IO algorithm extracting coordinates where `dists[i]` is between `startDistM` and `endDistM`. Includes immediate preceding and following points as boundary anchors to guarantee continuous polyline rendering without visual gaps.
  - `calculateLapBounds(points: List<LatLng>): LatLngBounds?`:
    Calculates `LatLngBounds` enclosing the lap segment. Returns `null` if empty.

---

### 2.2 UI Layer: `LapEditBottomSheet.kt` [NEW]
* **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/LapEditBottomSheet.kt`
* **Requirement**: `REQ-UI-142` (Sections 1, 2, 3, 4, 5)
* **Test**: `TST-UI-095` (Section 2)
* **Design**:
  - Material 3 `ModalBottomSheet` with `rememberModalBottomSheetState(skipPartiallyExpanded = false)`.
  - **Header**: Lap index, active time, distance, pace/speed formatted via `formatters`.
  - **Quick-Tag Chips**: Horizontally scrollable row of `SuggestionChip` / `AssistChip` items (`Warm-up`, `Interval`, `Recovery`, `Hill Climb`, `Tempo`, `Sprint`, `Cool-down`). Tapping replaces current name text.
  - **Text Fields**:
    - `OutlinedTextField` for custom lap name (single-line, clear icon, placeholder `"Lap X"`).
    - `OutlinedTextField` for description notes (multi-line, 2–4 lines).
  - **Navigation Bar**:
    - `< Previous`: Disabled when on first lap (`currentIndex == 0`).
    - `Next >`: Disabled when on last lap (`currentIndex == laps.size - 1`).
    - Tapping navigation persists active lap modifications and updates current lap index.
    - `Save` and `Close` buttons.
  - **Segment Map Section (Visible when expanded or toggled)**:
    - Renders `GoogleMap` with terrain type.
    - Background Polyline: Full workout points (color: `MaterialTheme.colorScheme.outlineVariant`, width: `4f`).
    - Foreground Polyline: Highlighted lap segment (color: `MaterialTheme.colorScheme.primary`, width: `10f`).
    - Start Marker (Green icon) and Stop Marker (Red icon) on the lap segment.
    - `LaunchedEffect(lapSegment)`: Calls `CameraUpdateFactory.newLatLngBounds(bounds, paddingPx)` with 70px padding.
    - Stationary / Zero-GPS Fallback: Informative text if workout contains no GPS coordinates.

---

### 2.3 UI Integration: `WorkoutLaps.kt` & `WorkoutSummary.kt` [MODIFY]
* **Location**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/WorkoutLaps.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt`
* **Requirement**: `REQ-UI-142` (Section 1)
* **Test**: `TST-UI-095` (Section 2)
* **Design**:
  - In `WorkoutLaps.kt`:
    - Add parameter `onLapClick: (LapData) -> Unit = {}`.
    - Wrap individual lap rows with `Modifier.clickable { onLapClick(lap) }`.
  - In `WorkoutSummary.kt`:
    - Maintain local state `var activeEditingLap by remember { mutableStateOf<LapData?>(null) }`.
    - Pass `onLapClick = { lap -> activeEditingLap = lap }` into `WorkoutLaps`.
    - When `activeEditingLap != null`, render `LapEditBottomSheet(workoutData = workoutData, laps = workoutData.laps, initialLap = it, onDismiss = { activeEditingLap = null }, onSave = { updatedLap -> ... })`.

---

### 2.4 Data Layer: `WorkoutRepository.kt` [MODIFY]
* **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`
* **Requirement**: `REQ-UI-142` (Section 3)
* **Test**: `TST-UI-095` (Section 3)
* **Design**:
  - Add method `suspend fun updateLapDetails(workoutId: Long, lapNr: Long, name: String?, description: String?)`:
    - Dispatches to `withContext(Dispatchers.IO)`: invokes `lapsDatabaseManager.updateLapDetails(workoutId, lapNr, name, description)`.
    - Updates in-memory cached `WorkoutData`:
      ```kotlin
      updateWorkoutInMemory(workoutId) { current ->
          val updatedLaps = current.laps.map { lap ->
              if (lap.lapNr == lapNr) lap.copy(name = name, description = description) else lap
          }
          current.copy(laps = updatedLaps)
      }
      ```
    - Guarantees immediate UI reactivity without list re-queries or scroll disturbance.

---

### 2.5 Universal Localization: `strings.xml` (9 Locales) [MODIFY]
* **Locales**: `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`.
* **String Resources**:
  - `edit_lap_title`: "Edit Lap %1$d"
  - `lap_name_label`: "Lap Name"
  - `lap_description_label`: "Notes & Description"
  - `quick_tag_warmup`: "Warm-up"
  - `quick_tag_interval`: "Interval"
  - `quick_tag_recovery`: "Recovery"
  - `quick_tag_hill_climb`: "Hill Climb"
  - `quick_tag_tempo`: "Tempo"
  - `quick_tag_sprint`: "Sprint"
  - `quick_tag_cooldown`: "Cool-down"
  - `previous_lap`: "Previous"
  - `next_lap`: "Next"
  - `no_gps_track_available`: "No GPS track recorded for this workout"

---

## 3. Traceability Matrix & Verification Plan

| Requirement ID | Component / File | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-142` (1, 2, 3) | `LapEditBottomSheet.kt`, `WorkoutLaps.kt` | `TST-UI-095` (2) | Unit test: Quick-tag chips, text fields, formatting |
| `REQ-UI-142` (4) | `LapEditBottomSheet.kt` | `TST-UI-095` (2) | Unit test: Sequential navigation & boundary states |
| `REQ-UI-142` (5) | `LapSegmentUtils.kt`, `LapEditBottomSheet.kt` | `TST-UI-095` (1) | Unit test: Polyline slicing, bounds calculation, fallback |
| `REQ-UI-142` (3) | `WorkoutRepository.kt`, `LapsDatabaseManager.java` | `TST-UI-095` (3) | Unit test: Database update & in-memory cache reactivity |
| System Invariants | All Modules | `TST-UI-095` (4) | Clean-room `./gradlew testDebugUnitTest` suite |

---

## 4. Invariant Protection Checklist

- [x] **Metric Immutability**: Lap quantitative metrics (`timeTotalS`, `distanceTotalM`, `speedAverageMps`, `timeStart`) are strictly untouched; only `name` and `description` are updated.
- [x] **Card Map Navigation (`REQ-SET-071`)**: Non-lap clicks on the summary card and clicks on the summary map/elevation profile continue to route to `TrackOnMapScreen`.
- [x] **Lap Overview Table (`REQ-UI-141`)**: Table columns, Rabbit/Hedgehog badges, and `> 3` collapsible threshold remain intact.
- [x] **Scroll Performance**: In-memory `WorkoutData` mutation avoids full `LazyColumn` invalidation.
