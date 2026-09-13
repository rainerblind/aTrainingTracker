# Stage 1 Analysis: Edit Lap Details & Interactive Map Visualization (ATT-511)

* **Ticket**: [ATT-511](https://rainerblind.atlassian.net/browse/ATT-511) (*[Feature] Edit Lap details*)
* **Sub-task**: [ATT-890](https://rainerblind.atlassian.net/browse/ATT-890) (*[Analysis] Edit Lap details*)
* **Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*[Epic] Laps*)
* **Target Release Version**: `V4.9.36`
* **Branch**: `feature/ATT-511`

---

## 1. Feature Description & Problem Domain

### 1.1 Background & Motivation
In `ATT-510` (`REQ-UI-141`), we successfully introduced a structured overview table of recorded laps into `WorkoutSummary` cards with duration, distance, pace/speed, and rabbit/hedgehog badges. The database schema in `Laps.db` (`DB_VERSION = 2`) was upgraded with `name TEXT` and `description TEXT` columns, and `LapsDatabaseManager.updateLapDetails(...)` was implemented.

`ATT-511` builds upon this foundation to empower athletes to personalize their laps:
1. **Custom Naming & Notes**: Name laps (e.g., "Warm-up", "Interval 1", "Hill Climb") and add multi-line coaching notes or personal observations.
2. **Ergonomic Editing Interface (ModalBottomSheet)**: Tapping a lap row in `WorkoutLaps` launches a dedicated editing sheet.
3. **Quick-Tag Chips**: Predefined tap-to-select chips for common lap types to eliminate tedious typing on mobile devices after demanding training sessions.
4. **Sequential Lap Navigation**: In-sheet `< Previous` and `Next >` buttons allow athletes to sequentially inspect and label multiple laps without repeatedly opening and closing the dialog.
5. **Interactive Map Visualization with Zooming Strategy**:
   - Display the overall workout track in the background (subtle secondary stroke).
   - Emphasize the current selected lap with a prominent, vibrant highlight stroke and distinct start/end markers.
   - Employ an intelligent camera framing and zooming strategy:
     - Expandable sheet: By default, the bottom sheet opens showing editing fields; when slid upward (or expanded), the full map view is displayed.
     - Auto-fit bounds: The map camera dynamically pans and zooms to frame the bounding box of the active lap with comfortable padding (`60-80px`), giving the athlete immediate geographical context of where that split took place.

---

## 2. Technical Architecture & Component Analysis

### 2.1 UI Layer: `LapEditBottomSheet` Composable
- **Trigger**:
  - In `WorkoutLaps.kt`, individual lap rows currently inherit the card-level click behavior (`mapClickModifier`).
  - `WorkoutLaps` will be enhanced with `onLapClick: (LapData) -> Unit`.
  - Tapping a lap row triggers `onLapClick(lap)` to open `LapEditBottomSheet`.
- **Sheet Layout & Expansion Modes**:
  - Implemented using Material 3 `ModalBottomSheet` with `rememberModalBottomSheetState(skipPartiallyExpanded = false)`.
  - **Partially Expanded State (Default on open)**:
    - Focuses on rapid text entry and sequential navigation:
      - Header: Lap index, active time, distance, pace/speed (e.g. "Lap 2 • 0:03:45 • 1.20 km • 4:15 min/km").
      - Quick-Tag Chips: Horizontally scrollable row containing presets:
        `Warm-up`, `Interval`, `Recovery`, `Hill Climb`, `Tempo`, `Sprint`, `Cool-down`.
        Tapping a chip immediately sets the lap name text field.
      - Name Field: Single-line `OutlinedTextField` with clear icon; placeholder shows default `"Lap X"`.
      - Description Field: Multi-line `OutlinedTextField` (min 2, max 4 lines) for personal notes.
      - Navigation & Action Controls:
        - `< Previous` (disabled if first lap)
        - `Next >` (disabled if last lap)
        - `Save` button and `Dismiss` handling.
  - **Fully Expanded State (Dragged Upward)**:
    - Reveals the interactive `LapMapView`:
      - Background track: Full workout polyline rendered in muted neutral/secondary color (`width = 4.dp`, semi-transparent).
      - Highlighted track: Lap segment polyline rendered in vibrant primary color (`width = 8.dp`).
      - Start & End pins for the lap segment.
      - Camera bounding box smoothly animated to `LatLngBounds` of the active lap segment.

### 2.2 Segment Extraction & Geometry Strategy
- **Universal Zero-IO Slicing via `WorkoutData`**:
  - `WorkoutData` already holds `mapPolyline` and `encodedDistances` in memory.
  - Full polyline: `PolyUtil.decode(workoutData.mapPolyline)` $\rightarrow$ `List<LatLng>`.
  - Distances stream: `NumericalEncodingUtils.decodeDoubles(workoutData.encodedDistances)` $\rightarrow$ `List<Double>`.
  - Cumulative distance range for Lap $k$:
    $$D_{\text{start}} = \sum_{i=1}^{k-1} \text{lap}_i.\text{distanceTotalM}$$
    $$D_{\text{end}} = D_{\text{start}} + \text{lap}_k.\text{distanceTotalM}$$
  - Slicing logic selects trackpoints where `distance` $\in [D_{\text{start}}, D_{\text{end}}]$.
  - Includes boundary anchor points before and after the interval to prevent visual gaps in polyline continuity.
  - Works universally across all workout sessions (live-recorded and imported GPX/TCX) with 0ms database overhead.
- **Indoor / Zero-Distance Fallback**:
  - If a workout is an indoor trainer session or contains zero GPS trackpoints, the map gracefully displays an informative placeholder or defaults to the start coordinate without crashing.

### 2.3 Data Layer & Reactive State Management
- **Persistence**:
  - `LapsDatabaseManager.updateLapDetails(workoutId, lapNr, name, description)` executes SQLite `UPDATE` on `Laps.db`.
- **In-Memory Propagation**:
  - `WorkoutRepository` provides a repository method `updateLapDetails(workoutId, lapNr, name, description)`.
  - Updates the database in background IO dispatcher.
  - Concurrently updates the in-memory cache via `updateWorkoutInMemory(workoutId) { workout -> workout.copy(laps = updatedLaps) }`.
  - Reactive Compose state in `WorkoutTabsScreen` / `WorkoutList` updates instantly without reloading the entire workout list or losing scroll position.

### 2.4 Universal Localization Parity
All new user-facing strings must be defined across all 9 supported locales (`en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`):
- `edit_lap_title`: "Edit Lap %1$d" / "Runde %1$d bearbeiten"
- `lap_name_label`: "Lap Name" / "Rundenname"
- `lap_description_label`: "Notes & Description" / "Notizen & Beschreibung"
- `quick_tag_warmup`: "Warm-up" / "Aufwärmen"
- `quick_tag_interval`: "Interval" / "Intervall"
- `quick_tag_recovery`: "Recovery" / "Erholung"
- `quick_tag_hill_climb`: "Hill Climb" / "Berganstieg"
- `quick_tag_tempo`: "Tempo" / "Tempo"
- `quick_tag_sprint`: "Sprint" / "Sprint"
- `quick_tag_cooldown`: "Cool-down" / "Auslaufen"
- `previous_lap`: "Previous" / "Vorherige"
- `next_lap`: "Next" / "Nächste"

---

## 3. Call-Site & Affected Files Audit

| File | Role | Changes Planned |
| :--- | :--- | :--- |
| `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/WorkoutLaps.kt` | UI Split Table | Add row tap callback `onLapClick: (LapData) -> Unit`. |
| `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutlaps/LapEditBottomSheet.kt` | UI BottomSheet [NEW] | Implement editing bottom sheet with quick-tag chips, sequential navigation, and highlighted segment map. |
| `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummary.kt` | Summary Card | Wire `onLapClick` from `WorkoutLaps` to host dialog/sheet state. |
| `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt` | Data Layer | Add `updateLapDetails(...)` dispatching database mutation and updating memory cache. |
| `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/LapSegmentUtils.kt` [NEW] | Math / Geometry | Pure utility to extract lap track segment and calculate camera bounds from cumulative distances and `mapPolyline`. |
| `app/src/main/res/values*/strings.xml` (9 locales) | Strings | Externalize all user-facing labels and quick tags with 100% parity. |

---

## 4. Requirements & Invariant Checklist

### Mapped Requirements Cross-Check
- **`REQ-UI-141`** (*Workout Summary Lap Overview & Performance Highlights*):
  - Must remain 100% intact. Display name resolution (`getDisplayName`), Rabbit/Hedgehog performance badges, and collapsible threshold (`> 3` laps) MUST NOT be regressed.
- **`REQ-SET-071`** (*Map Click Routing to TrackOnMapScreen*):
  - Tapping non-interactive areas of the card or the main map/elevation profile MUST still route to `TrackOnMapScreen`. Only taps directly on an individual lap row in `WorkoutLaps` trigger the edit sheet.
- **`REQ-DAT-010`** (*Workout Deletion & Integrity*):
  - In-place lap updates MUST NOT alter foreign key relationships or cascade deletion behaviors.

### System Invariants
1. **Zero Data Loss**: Editing lap details only updates `name` and `description`; lap metrics (`timeTotalS`, `distanceTotalM`, `speedAverageMps`, `timeStart`) MUST remain immutable.
2. **List Scroll Performance**: Updating a lap MUST NOT trigger a full `LazyColumn` re-composition or list reload; updates must mutate in-memory state cleanly.
3. **Graceful Degradation**: Workouts without GPS tracks (e.g. indoor stationary training) MUST display the edit controls without map errors.

---

## 5. Risk Assessment & Recommendation

* **Complexity**: Medium.
* **Risk Level**: **LOW**.
* **Rationale**: The database schema and update query `updateLapDetails` were already validated and tested in `ATT-510`. The polyline slicing logic operates on purely immutable in-memory data structures (`PathPoint`, `LatLng`). The UI is encapsulated in a dedicated Compose `ModalBottomSheet`.
* **Recommendation**: **RECOMMEND PASS** for Stage 1 Analysis. Proceed to Stage 2 (Test Specification & Requirements Synchronization).
