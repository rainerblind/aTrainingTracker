# Stage 3: Implementation Plan - ATT-52: Landscape Dual-Pane Cockpit & Widescreen Sensor Grid

**Ticket**: [ATT-52](https://rainerblind.atlassian.net/browse/ATT-52)  
**Parent Epic**: [ATT-2144](https://rainerblind.atlassian.net/browse/ATT-2144) (*Landscape Mode*)  
**Target Release**: `V4.9.41` | **Active Sprint**: `sprint/2026-43.9` | **Branch**: `feature/ATT-52`  
**Requirement Mapping**: `REQ-TRK-040` (`REQ-TRK-040.1`–`5`)  
**Test Mapping**: `TST-TRK-040` (`TST-TRK-040.1`–`6`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-10-23  

---

## 1. Problem Description & Architectural Context
Cyclists mounting smartphones horizontally on aero-bars, out-front mounts, or indoor smart trainers encounter compressed vertical layouts in portrait-only cockpit designs. In landscape (~360–400dp height), a single vertical `Column` squishes sensor cells, starves map context, and allows full-width bottom sheets (`LiveSegmentSheet`, `LiveClimbSheet`) to completely mask telemetry. This plan designs a responsive architecture: 50/50 dual-pane split when maps are active, full-width widescreen grid with column transformation when maps are hidden, sub-sheet docking within the map pane, and strict adherence to the athlete's `forcePortrait` display preference.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-040` (*Landscape Cockpit Dual-Pane Layout, Widescreen Sensor Grid & Sub-Pane Overlay Docking*)
* **Test Mapping**: `TST-TRK-040` (*Landscape Cockpit Dual-Pane Layout, Widescreen Sensor Grid & Sub-Pane Overlay Docking Verification Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Portrait Layout Fidelity**: 100% byte-for-byte fidelity in portrait orientation (`Configuration.ORIENTATION_PORTRAIT`).
2. **Orientation Lock Enforcement**: `forcePortrait` setting in `DisplaySettingsDialog` / `TrainingApplication` strictly locks to portrait via `ActivityInfo.SCREEN_ORIENTATION_PORTRAIT`.
3. **Unmasked Telemetry Invariant**: In landscape dual-pane mode, Live Segment and ClimbPro sheets dock exclusively within the map pane; telemetry fields remain 100% visible and interactive.
4. **Database & Gesture Neutrality**: Zero SQLite mutations (portrait configuration preserved in `FlexibleGenericView.db`); zero callback suppression (Rule 34); section dividers and double-tap bell preserved.

---

## 4. Proposed Architectural Changes
* **Row Packing Transformation (`SensorFieldState.kt`)**: Add `packVisibleRowsForLandscape(...)` transforming $N_{\text{portrait}}$ rows into paired widescreen columns ($N_{\text{landscape}} = \min(4, N_{\text{portrait}} \times 2)$) when `isLandscape && !showMap`.
* **Responsive Layout & Sheet Docking (`SensorGridScreen.kt`)**: Detect `LocalConfiguration.current.orientation`. When `isLandscape && state.mapState.showMap`, render horizontal 50/50 `Row`: Left Pane for Map + Elevation Profile with docked sheets; Right Pane for scrollable Sensor Grid + Lap/Bell buttons. When `isLandscape && !showMap`, render full-width widescreen grid.
* **Cockpit Action Overlays (`TrackingTabsScreen.kt`)**: Position Lap and Bell buttons responsively in landscape without occluding map canvas.
* **UI Consistency**: Reuses existing `SensorFieldView`, `TrackingMapContainer`, `ElevationProfile`, `LiveSegmentSheet`, and `LiveClimbSheet`. Applies `RoundedCornerShape(12.dp)` and Material 3 theme tokens (`MaterialTheme.colorScheme.*`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Code Lock Verification (Rule 4)
* Confirm exit code 0 via `python3 tools/jira_util.py check-gate <Impl-Plan-Subtask-Key>`.

### Step 2: Landscape Widescreen Column Transformation Utility
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldState.kt`
* **Change**: Implement `packVisibleRowsForLandscape(fields, tuningConfig, screenMode, isLandscape, showMap)`. Pairs consecutive 1-field rows into 2-column rows up to $\min(4, N \times 2)$ columns without modifying database records.

### Step 3: Responsive Dual-Pane Map Layout & Sub-Pane Sheet Docking
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* **Change**: Observe `LocalConfiguration.current.orientation`. Branch layout:
  * *Landscape + Map*: 50/50 `Row` (Map + Elevation Profile on left with docked `LiveSegmentSheet`/`LiveClimbSheet`; scrollable Sensor Grid on right).
  * *Landscape No Map*: Full-width widescreen grid using transformed rows with scalable numerals.
  * *Portrait*: Unmodified existing `Column` and root `BottomSheetScaffold`.
  * Add `@Preview` composables for Landscape Dual-Pane and Landscape Widescreen in Light/Dark themes.

### Step 4: Tracking Tabs Screen Controls Alignment
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`
* **Change**: Adjust Lap and Bike Bell button container alignment in landscape to float compactly over the telemetry pane.

### Step 5: Unit & Contract Tests
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridLandscapeLayoutTest.kt`
* **Tests**: Implement `TST-TRK-040.1`–`5`: widescreen column pairing, dual-pane partitioning, right-pane sheet docking, `forcePortrait` setting enforcement, and configuration mode gesture contracts.

### Step 6: Targeted Incremental Test Execution & Governance Verification
* Run:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Tier 1 targeted unit & contract tests (`SensorGridLandscapeLayoutTest`, `SensorGridScreenLayoutTest`), followed by Stage 5 on-device screenshot inspection.
* **Rollback**: Discard uncommitted changes via `git checkout -- . && git clean -fd`.
