# Stage 1 Analysis: ATT-2176 - User-Customizable Section Reordering and Modular Section Architecture for Workout Summaries and Details

**Ticket**: [ATT-2176](https://rainerblind.atlassian.net/browse/ATT-2176)  
**Sub-task**: [ATT-2200](https://rainerblind.atlassian.net/browse/ATT-2200) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-2176`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Statement & Motivation

Currently, athletes can toggle the visibility of individual sections (Description, Extrema, Laps, Strava, Map, Elevation Profile, Telemetry Charts, Zone Distribution) in Advanced Settings (`AdvancedTuningDialog.kt` per `REQ-UI-229` and `REQ-UI-240`). However, two architectural limitations exist:
1. **Hardcoded Vertical Order**:
   The display sequence of these sections is hard-coded in both `WorkoutSummary.kt` and `TrackOnMapScreen.kt` / `MapDetailLayout.kt`. Different athletes have different post-workout priorities (e.g. data-focused athletes want Telemetry Charts and Zones first, while trail runners prefer Map and Elevation first).
2. **Duplicated Section Layout Boilerplate**:
   The composition, dividing lines, section headings, and conditional wrapping of individual sections are redundantly implemented in both `WorkoutSummary.kt` and `TrackOnMapScreen.kt`. While the underlying leaf composables (`WorkoutExtrema`, `StravaActivitySection`, `TelemetryMetricGraph`) are shared, their orchestration is duplicated.

The goal of ATT-2176 is to introduce a user-customizable, persistent vertical section order with reorder controls in Advanced Settings, backed by a clean modular section architecture (`WorkoutSectionType`, `WorkoutDisplayContext`, `WorkoutSectionRenderer`) that respects the distinct interaction requirements of list cards (passive, navigation-oriented, 60 FPS lazy-load) and full details (interactive scrubbing, zoom/pan, map-cursor sync).

---

## 2. Root Cause Analysis (Architectural Gap Analysis)

### Current Architecture State
1. **Fixed Sequence**:
   - `WorkoutSummary.kt` renders sections sequentially: `Header` -> `Description` -> `Details` -> `Extrema` -> `Laps` -> `Strava` -> `Map/Elevation` -> `Charts` -> `Zones`.
   - `TrackOnMapScreen.kt` slots `Description`, `Extrema`, `Laps`, and `Strava` into `metadataContent` (above map), renders `Map`, `Elevation`, and `Charts` in `MapDetailLayout`, and slots `Zones` into `analyticsContent`.
2. **Preference Storage**:
   - `MyPreferenceManager.kt` persists boolean flags (`showDescription`, `showExtrema`, etc.) in `user_preferences` DataStore, but does not persist an ordered list of sections.
3. **Settings UI**:
   - `AdvancedTuningDialog.kt` renders a static 8-row matrix with check boxes for List vs. Detail visibility, but provides no affordance to change vertical order.

### Gap Analysis & Proposed Abstractions
- **Section Enum (`WorkoutSectionType`)**:
  `DESCRIPTION`, `EXTREMA`, `LAPS`, `STRAVA`, `MAP`, `ELEVATION`, `CHARTS`, `ZONES`.
  Default order: `[DESCRIPTION, EXTREMA, LAPS, STRAVA, MAP, ELEVATION, CHARTS, ZONES]`.
- **Display Context (`WorkoutDisplayContext`)**:
  `LIST_CARD` (passive touch, click opens details) vs. `FULL_DETAIL` (interactive scrubbing, zoom toolbar, lap map highlights).
- **Order Persistence**:
  Serialized ordered string in `MyPreferenceManager` (`WORKOUT_SECTIONS_ORDER`), with defensive fallback to the default order if uninitialized or corrupted, and automatic appending of any newly introduced section types.
- **Dynamic Slotting in `TrackOnMapScreen.kt`**:
  Sections appearing before `MAP` in the custom order are placed into `metadataContent` (above the map); sections appearing after `MAP` are placed below the map in `MapDetailLayout` / `analyticsContent`.
- **Reordering UI in `AdvancedTuningDialog.kt`**:
  Row reordering controls (Move Up / Move Down buttons) to reposition sections dynamically in the matrix, with immediate preview and reactive DataStore persistence.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Define `WorkoutSectionType` and `WorkoutDisplayContext`.
  2. Implement DataStore persistence for `List<WorkoutSectionType>` in `MyPreferenceManager.kt` with robust migration and default fallback.
  3. Modernize the section matrix in `AdvancedTuningDialog.kt` with accessible Move Up / Move Down reordering controls.
  4. Refactor `WorkoutSummary.kt` to iterate through the active section order and dynamically render enabled sections.
  5. Refactor `TrackOnMapScreen.kt` to dynamically slot sections relative to `MAP` according to the active section order.
  6. Maintain 100% backward compatibility for all existing visibility preferences and custom callers.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. No changes to Workout Header or Main Details (Distance, Duration, Pace/Speed) positioning; these remain permanently fixed identity anchors at the very top.
  2. No changes to bottom sheet presentation or `LiveSegmentSheet.kt`.
  3. No changes to database schema (SQLite tables); preferences are stored in Preferences DataStore.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Net-new requirement**: `REQ-UI-255` (*Aftermath: User-Customizable Section Reordering, Unified Order Persistence & Modular Section Architecture for Workout Summaries and Details*).
* **Refined Requirements**:
  - `REQ-UI-229` (*Workout Card Section Visibility Preferences Matrix in Advanced Settings*): Preserved 100%; reordering controls augment the existing matrix.
  - `REQ-UI-240` (*Independent Detail Screen Section Visibility Preferences Matrix*): Preserved 100%; two-column checkbox controls remain intact.
  - `REQ-UI-250` (*Collapsing Upper Metadata Architecture & Map Squashing Prevention*): Preserved 100%; collapsing behavior and minimum map height (120dp) remain intact regardless of which sections are slotted above the map.
  - `REQ-UI-252` (*Lap Split Visualizer Upper Metadata Slotting & Canonical Order Parity*): Refined from static hardcoded order to dynamic user-customizable order while preserving default parity.
* **Preservation of Core Invariants**:
  - Minimum map height (120dp) guaranteed.
  - Header and core metrics remain permanent identity anchors.
  - Passive list card navigation vs. interactive full-screen scrubbing strictly decoupled.
  - 100% 9-language localization parity (`REQ-LOC-001`).

---

## 5. Architectural Strategy & High-Level Solution

### Component 1: Domain Model & DataStore Persistence (`MyPreferenceManager.kt`)
- Add `WorkoutSectionType` enum with canonical values and string serialization.
- Add `WORKOUT_SECTIONS_ORDER` preference key in DataStore.
- Expose `workoutSectionsOrderFlow: Flow<List<WorkoutSectionType>>` with fallback to `DEFAULT_WORKOUT_SECTIONS_ORDER`.
- Provide `suspend fun setWorkoutSectionsOrder(order: List<WorkoutSectionType>)`.

### Component 2: Advanced Settings Reorder UI (`AdvancedTuningDialog.kt`)
- Display matrix rows ordered by `workoutSectionsOrder`.
- Provide accessible `▲` and `▼` reorder controls for each row, disabling `▲` on the first row and `▼` on the last row.
- On click, swap adjacent items and update DataStore.

### Component 3: Modular Section Rendering in `WorkoutSummary.kt`
- Read `workoutSectionsOrder` from preferences.
- Render the fixed header and primary metrics, then iterate over `workoutSectionsOrder` to render active sections.

### Component 4: Dynamic Slotting in `TrackOnMapScreen.kt`
- Partition the custom section order around `MAP`:
  - Pre-map sections render inside `metadataContent`.
  - Post-map sections render inside `analyticsContent` / lower column.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features and unit tests.
  2. Fixed header and main details anchor permanently at top.
  3. Interactive chart scrubbing decoupled from list card tap navigation.
  4. 100% clean-room test suite pass rate.
* **Risk Rating**: **MEDIUM**
  - *Justification*: Modifies core workout display orchestrators (`WorkoutSummary.kt`, `TrackOnMapScreen.kt`) and preferences UI. Risk is thoroughly mitigated by unit and contract test coverage, default fallback order preservation, and clean-room full suite regression testing.
