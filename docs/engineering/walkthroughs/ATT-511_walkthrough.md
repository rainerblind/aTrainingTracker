# Walkthrough: Edit Lap Details & Interactive Segment Map (ATT-511)

* **Parent Ticket**: [ATT-511](https://rainerblind.atlassian.net/browse/ATT-511) (*[Feature] Edit Lap details*)
* **Subtasks**: 
  - [ATT-899](https://rainerblind.atlassian.net/browse/ATT-899) (*[Subtask] [Implementation] Edit Lap details*)
  - [ATT-901](https://rainerblind.atlassian.net/browse/ATT-901) (*[Subtask] [Test] Edit Lap details*)
* **Requirement**: `REQ-UI-142` (Verified)
* **Test Specification**: `TST-UI-095` (Verified)
* **Target Version**: `V4.9.36`
* **Branch**: `feature/ATT-511`

---

## 1. Executive Summary

We have implemented an interactive, reactive editing and inspection experience for individual workout laps in compliance with `REQ-UI-142` and `TST-UI-095`.

Key capabilities delivered:
1. **Interactive Modal Bottom Sheet (`LapEditBottomSheet.kt`)**:
   - Triggered by tapping any individual lap row in `WorkoutLaps`.
   - Displays split summary header (lap duration, distance, pace for running or speed for cycling/other sports).
2. **One-Tap Quick-Tag Preset Chips**:
   - Horizontally scrollable chips for rapid naming: *Warm-up*, *Interval*, *Recovery*, *Hill Climb*, *Tempo*, *Sprint*, and *Cool-down*.
   - Single tap immediately populates the custom lap name text field.
3. **Custom Name & Multi-line Description**:
   - `OutlinedTextField` for custom lap name (with clear trailing icon and `"Lap X"` placeholder).
   - `OutlinedTextField` for multi-line notes and coaching descriptions.
4. **Sequential Lap Navigation with Auto-Save**:
   - `< Previous` and `Next >` navigation buttons allowing athletes to review and label consecutive intervals without repeatedly dismissing and reopening the sheet.
   - Automatically saves current lap modifications before shifting to the target lap.
   - Distinct `Cancel` and `Save` action buttons.
5. **Zero-IO Pure Geometrical Polyline Slicing (`LapSegmentUtils.kt`)**:
   - `calculateLapDistanceRange`: Computes cumulative start and end distances for the target lap $[D_{\text{start}}, D_{\text{end}}]$.
   - `sliceLapSegment`: Slices the exact coordinate points corresponding to the lap interval directly from in-memory polyline coordinates and distance stream, incorporating boundary anchor points to guarantee gap-free continuous rendering.
   - `calculateLapBounds`: Computes `LatLngBounds` with singular-point protection against zero-area camera crashes.
6. **Interactive Segment Map Visualization**:
   - Renders entire workout polyline in subtle secondary styling (`outlineVariant`).
   - Highlights the active lap segment in vibrant primary color (`strokeWidth = 10f`).
   - Renders Start (Green) and Stop (Red) markers at segment endpoints.
   - Automatically animates camera view to fit lap segment bounds with padding (`70px`).
   - Graceful fallback for stationary/zero-GPS sessions (`no_gps_track_available`).
7. **Reactive In-Memory & Database Persistence**:
   - `WorkoutRepository.updateLapDetails`: Offloads database mutation to `Dispatchers.IO` via `LapsDatabaseManager.updateLapDetails`.
   - Atomically updates `_allWorkouts` in memory, propagating changes immediately to `WorkoutSummary` and `WorkoutLaps` without full list reload.
8. **Universal Localization**:
   - 100% key parity enforced across all 9 supported languages (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

---

## 2. Changes Summary

| Area | Component | Description |
| :--- | :--- | :--- |
| **Geometry** | `LapSegmentUtils.kt` [NEW] | Pure zero-IO mathematical utility for distance intervals, segment slicing, and bounds calculation. |
| **UI Component** | `LapEditBottomSheet.kt` [NEW] | ModalBottomSheet with split metrics header, quick-tag chips, text fields, navigation, and segment map. |
| **UI Integration** | `WorkoutLaps.kt` [MODIFY] | Added `onLapClick: ((LapData) -> Unit)? = null` and wrapped individual `LapRow` items with click handlers. |
| **UI Integration** | `WorkoutSummary.kt` [MODIFY] | Managed `activeEditingLap` state, launched `LapEditBottomSheet`, and connected save callback. |
| **Repository** | `WorkoutRepository.kt` [MODIFY] | Added `suspend fun updateLapDetails` with `Dispatchers.IO` DB write and atomic `_allWorkouts` cache update. |
| **Theme / Core** | `Theme.kt` [MODIFY] | Safely unwrapped `ContextWrapper` chain to `Activity` for Dialog window status bar styling without `ClassCastException`. |
| **Localization** | `strings.xml` (9 locales) | Added `edit_lap_title`, `lap_name_label`, `lap_description_label`, 7 quick tags, `previous_lap`, `next_lap`, and `no_gps_track_available`. Reverted unintended modification in `values-ja/strings.xml`. |
| **Unit Tests** | `LapSegmentUtilsTest.kt` [NEW] | 5 unit tests covering range calculation, coordinate slicing, boundary anchors, bounds calculation, and zero-area safety. |
| **Unit Tests** | `WorkoutRepositoryLapUpdateTest.kt` [NEW] | 2 unit tests verifying DB persistence and atomic in-memory cache update. |

---

## 3. Iterative UI Layout & Styling Refinements (User Testing Feedback)

Following physical device evaluation on Google Pixel 10 (`66020DLCR002FL`):
1. **Scaffold & Status Bar Boundary**: Constrained the sheet container with `Modifier.statusBarsPadding()` so that when expanded/drawn up, the sheet is drawn strictly up to the status bar and never overlays system status bar elements.
2. **Header Simplification & Row Order Toggle**: Removed redundant close (`'X'`) button; toggled the header rows so that the primary action bar `(Cancel) (Store)` sits at the top, directly followed by the sequential navigation bar `< Previous | Lap X / Y | Next >`.
3. **Initial Popup Height & Map Revelation**: Configured partial expansion (`skipPartiallyExpanded = false`) so that the initial popup displays the edit controls down to the comments text field with its bottom aligned to the top of the navigation bar, keeping the map hidden until the user slides or scrolls upwards.
4. **Theme & Pure White Surface Color**:
   - Wrapped Dialog content in `ATrainingTrackerTheme`.
   - Set `Surface` background with `tonalElevation = 0.dp` and `shadowElevation = 8.dp`, eliminating Material 3 surface elevation overlays and ensuring pure crisp white `#FFFFFF`.
   - Transformed Metrics Card, Segment Map Container, and `NoGpsTrackCard` to `OutlinedCard(containerColor = surface)` to match the app's clean card aesthetic.

---

## 4. Verification & Validation Results

### Automated Clean-Room Unit Tests
Executed via `./gradlew testDebugUnitTest`:
* **Result**: `BUILD SUCCESSFUL in 1m 55s` (32 actionable tasks: 12 executed, 20 up-to-date)
* **Pass Rate**: 100% (0 failures, 0 errors across all test modules)
* Key test suites verified:
  1. `LapSegmentUtilsTest`: **5 / 5 passed** (100%)
  2. `WorkoutRepositoryLapUpdateTest`: **2 / 2 passed** (100%)
  3. `WorkoutLapsTest`: **7 / 7 passed** (100%)
  4. `TranslationParityTest`: **7 / 7 passed** (100% key parity across all 9 locales)

### Physical Device Verification (Google Pixel 10)
* **Collapsed Popup State**: Opens smoothly with pure white `#FFFFFF` surface; comments field bottom aligns with navigation bar; action buttons and quick-tags visible and responsive.
* **Expanded Map State**: Swiping up smoothly expands the popup to the status bar, rendering the workout polyline and highlighted lap segment within its outlined container.
* **Dismissal**: Tapping `Abbrechen` smoothly dismisses the popup and restores workout summary view.
* **Localization**: Japanese strings verified; zero regression across all 9 locales.

---

## 5. Traceability & ASPICE Sign-Off

| Artifact | Identifier | Status |
| :--- | :--- | :--- |
| **System Requirement** | `REQ-UI-142` | **Verified** |
| **Test Specification** | `TST-UI-095` | **Verified** |
| **Jira Feature** | `ATT-511` | In Test (V4.9.36) |
| **Jira Subtask (Test)** | `ATT-901` | **Freigabe (Human)** |
