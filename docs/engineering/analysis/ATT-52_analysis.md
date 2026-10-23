# Stage 1 Analysis: ATT-52 - Landscape Dual-Pane Cockpit & Widescreen Sensor Grid

**Ticket**: [ATT-52](https://rainerblind.atlassian.net/browse/ATT-52)  
**Sub-task**: Pending creation (`[Analysis]`)  
**Parent Epic**: [ATT-2144](https://rainerblind.atlassian.net/browse/ATT-2144) (*Landscape Mode*)  
**Target Release**: `V4.9.41` | **Active Sprint**: `sprint/2026-43.9` | **Branch**: `feature/ATT-52`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-10-23  

---

## 1. Problem Statement & Motivation
Cyclists, triathletes, and rowers frequently mount devices horizontally (aero-bars, stems, smart trainers). While `forcePortrait` exists in Display Settings, tracking UI lacks horizontal viewport optimization:
1. *Vertical Squish with Map*: `SensorGridScreen.kt:676-799` stacks the sensor grid and map vertically inside a single `Column`. In landscape (~360–400dp height), the 50% max height cap (`heightIn(max = availableHeight * 0.5f)`) compresses sensor rows into ~180dp and starves map navigation context.
2. *Telemetry Occlusion by Sheets*: `BottomSheetScaffold` spans 100% screen width. In landscape, Live Segment (`LiveSegmentSheet.kt`) and ClimbPro (`LiveClimbSheet.kt`) sheets completely mask telemetry metrics (Speed, HR, Power, Cadence) during critical efforts.
3. *Underutilized Widescreen Canvas*: When maps are disabled (`showMap == false`), sensor columns retain narrow portrait column widths rather than expanding across the widescreen display.

---

## 2. Root Cause Analysis & Independent Peer Review (ATT-3534)
* **Live Code Audit**:
  * *Container Architecture*: Confirmed `SensorGridScreen.kt:676` uses a single `Column(Modifier.fillMaxSize())` for both map and sensor fields.
  * *Sheet Scope*: Confirmed `BottomSheetScaffold` at line 328 wraps the entire screen; sheets cannot dock to a sub-pane without layout branching.
  * *Orientation Locking*: Confirmed `MainActivityWithNavigation.kt:635` evaluates `TrainingApplication.forcePortrait()` and sets `SCREEN_ORIENTATION_PORTRAIT` vs `SCREEN_ORIENTATION_UNSPECIFIED`. When disabled, Android triggers configuration changes cleanly.
  * *Peer Review Finding on Sheet Docking*: Rather than a screen-wide scaffold in landscape, docking the sheet within the right (map) pane guarantees 100% unmasked visibility for the left telemetry pane while retaining peek/expanded touch ergonomics.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Responsive orientation branching via `LocalConfiguration.current.orientation`.
  * Dual-pane horizontal layout (`Row`, 50/50 split) in `SensorGridScreen.kt` when `showMap == true` in landscape: sensor grid on the left, map + elevation profile on the right.
  * Right-pane sheet docking for `LiveSegmentSheet` and `LiveClimbSheet` in landscape mode.
  * Full-width widescreen sensor grid layout when `showMap == false` in landscape mode.
  * Strict preservation of portrait behavior and `forcePortrait` display preference enforcement.
  * Configuration mode (`ScreenMode.CONFIGURATION`) operational in landscape.
* **Out-of-Scope Non-Goals**:
  * Post-workout telemetry graphs and zone bar horizontal alignment (handled under ATT-2145).
  * Database schema changes in `FlexibleGenericView.db` or service modifications in `TrackerService`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-358` (*Cockpit Viewport Allocation*), `REQ-UI-359` (*Live Segment/Climb Bottom Sheet*); complements `REQ-SET-052` (*Display Settings*); introduces `REQ-UI-423` (*Landscape Dual-Pane Architecture*).
* **Historical Origin & Commit Trace**: Commit `f48e24c7` (`ATT-3343`, asymmetric map weighting) and `69e4f1a2` (`ATT-3341`, bottom sheet scaffold re-expansion).
* **Root Reason for Existing Formulation**: Single-column vertical allocation was built exclusively for portrait viewports before horizontal aero mounts were addressed.
* **Preservation of Core Invariants**: 100% byte-for-byte portrait layout fidelity, `forcePortrait` setting enforcement, zero callback suppression (Rule 34), and 100% test pass rate.

---

## 5. Architectural Strategy & High-Level Solution
1. *Orientation Detection*: Read `LocalConfiguration.current.orientation` in `SensorGridScreen.kt`.
2. *Landscape Dual-Pane (`showMap == true`)*: Top-level `Row(Modifier.fillMaxSize())`:
   - *Left Pane (`weight(1f)`)*: Bounded `Column` with sensor tiles (`fillMaxHeight()`, vertical scroll if > 2 rows).
   - *Right Pane (`weight(1f)`)*: `Column` hosting `TrackingMapContainer` and `ElevationProfile`. Dock `LiveSegmentSheet` / `LiveClimbSheet` over the right pane via a localized sheet/overlay container.
3. *Landscape Widescreen (`showMap == false`)*: Full-screen `Column` with horizontal expanded grid cells and scalable `SensorFieldView` typography.
4. *Ambient Overlays & Overrides*: Center `NavigationHintsOverlay` and `AutoPauseHudBanner` across the active viewport.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero regression in portrait mode; `forcePortrait` lock preserved; gestures, callbacks, and section dividers preserved; 100% clean-room test pass rate.
* **Risk Rating**: **MEDIUM** (UI layout branch on central tracking screen; mitigated by clean separation of orientation pathways).
