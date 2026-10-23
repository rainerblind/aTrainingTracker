# Stage 1 Analysis: ATT-4773 - Optimize Landscape Cockpit Vertical Space: Sensor Icons in Center Status Bar and Auto-Hiding Floating Tab Row

**Ticket**: [ATT-4773](https://rainerblind.atlassian.net/browse/ATT-4773)  
**Sub-task**: [ATT-4796](https://rainerblind.atlassian.net/browse/ATT-4796) (`[Analysis]`)  
**Parent Epic**: [ATT-2144](https://rainerblind.atlassian.net/browse/ATT-2144) (*[Epic] Landscape Mode*)  
**Target Release**: `v2.44.0`  
**Active Sprint**: `Sprint 2026-43.10`  
**Branch**: `feature/ATT-4773`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-23  

---

## 1. Problem Statement & Motivation
* In `TrackingTabsScreen` in landscape orientation (`Configuration.ORIENTATION_LANDSCAPE`) during `ScreenMode.TRACKING`, three stacked horizontal bars consume ~130–145dp of vertical space:
  1. System status bar insets (`statusBarsPadding()` ~24–32dp).
  2. `SensorStatus` row (~36dp) containing the drawer button (☰) and 8 sensor availability icons.
  3. `PrimaryScrollableTabRow` (~48dp) containing tabs (`Start | Standard | Karte | Runden | ...`).
* On landscape viewports (~411dp height), top chrome permanently occludes ~35% of the screen, squeezing the live cockpit grid (`SensorGridScreen`) and maps down to ~266–280dp.
* In landscape mode, the center of the Android status bar is vacant (camera punch-hole shifts to the side margin), leaving ample space (~200–400dp) between the left clock/notifications and right system icons.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Root Cause Analysis**:
  * `TrackingTabsScreen.kt`: Wraps `SensorStatus` and `PrimaryScrollableTabRow` in an outer `Column` with `statusBarsPadding()`, permanently reserving vertical space above `HorizontalPager` regardless of orientation or user interaction.
  * `SensorStatus.kt`: Allocates a full-width row with hamburger icon (24dp) at `Alignment.CenterStart` and 8 sensor icons in a centered Box (~240–272dp total width). Rendering this below `statusBarsPadding()` is redundant in landscape.
  * `PrimaryScrollableTabRow`: Fixed in the outer `Column` hierarchy; lacks auto-hide overlay behavior during live tracking, permanently sacrificing 48dp of vertical cockpit height.
* **Independent Peer Review & Verification**:
  * Audited `TrackingTabsScreen.kt` (lines 554–662): Confirmed static top `Column` with `statusBarsPadding()`, `SensorStatus`, and `PrimaryScrollableTabRow` stacked vertically above `Box(Modifier.weight(1f).fillMaxWidth()) { HorizontalPager(...) }`.
  * Audited `SensorStatus.kt` (lines 72–121): Confirmed row structure, 24dp hamburger button, and 8 sensor icons (`TIME_ACTIVE`, `ACCURACY`, `ALTITUDE`, `DISTANCE_m`, `SPEED_mps`, `CADENCE`, `HR`, `POWER`).
  * Verified status bar clearance: 8 icons * ~30–34dp = ~240–272dp, fitting comfortably in the vacant center of a 640–900dp wide landscape status bar.
  * Audited `HorizontalPager` interaction: Verified `pagerState.isScrollInProgress`, page 0 Control tab, and gesture clearance.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Landscape Mode Only: Portrait mode behavior remains 100% unchanged (`statusBarsPadding()`, persistent `SensorStatus`, persistent `PrimaryScrollableTabRow`).
  * Sensor Icons in Center Status Bar: In landscape `ScreenMode.TRACKING`, place `SensorStatus` availability icons centered in the top status bar strip, removing redundant vertical padding.
  * Drawer Button (☰) Positioning: Place drawer button without colliding with top-left system clock (at the leading edge of the floating Tab Row overlay or just left of centered sensor icons).
  * Auto-Hiding Floating Tab Row: Render `PrimaryScrollableTabRow` as a floating top overlay (`zIndex`) over `HorizontalPager`, showing on swipe/scroll (`pagerState.isScrollInProgress`), tab switch, or top tap, lingering for 2.5–3s before fading out (and accessible on Page 0 Control tab).
  * Preserve persistent headers in `ScreenMode.CONFIGURATION` and `ScreenMode.PREVIEW`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying portrait tracking layout or status bar behavior.
  * Modifying `SensorGridScreen` tile layout, drag-and-drop, or configuration logic (`ATT-4010`).
  * Modifying sensor data streaming, BLE/ANT+ connection logic, or database storage.
  * Altering `LapButton`, `BikeBellButton`, or battery alert snackbar bottom overlays.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-434`, extending Epic `ATT-2144` (*Landscape Mode*) and refining `TrackingTabsScreen.kt` layout from `REQ-UI-163` (`ATT-1340`), `REQ-UI-410` (`ATT-4000`), and `REQ-UI-412` (`ATT-4097`).
* **Historical Origin & Commit Trace**: Commit `e253c312` (`ATT-1340`), commit `de1c2965` (`ATT-4000`), and commit `d63529d8` (`ATT-4097`).
* **Root Reason for Existing Formulation**: Static `Column` with `statusBarsPadding()` and persistent `PrimaryScrollableTabRow` guaranteed discoverability and prevented status bar clipping on tall portrait screens (~800dp). In landscape (~411dp), permanent ~130–145dp chrome wastes ~35% of the viewport.
* **Preservation of Core Invariants**: 100% portrait layout fidelity, tab switching/swipe responsiveness, bottom button clearance (`LapButton`, `BikeBellButton`), and 100% clean-room test pass rate.

---

## 5. Architectural Strategy & High-Level Solution
| Component | Architectural Strategy |
| :--- | :--- |
| `TrackingTabsScreen.kt` (Status Bar) | In landscape `ScreenMode.TRACKING`, omit `statusBarsPadding()` from root `Column`. Render compact top status bar row with centered sensor icons (~24–28dp height). |
| `TrackingTabsScreen.kt` (Floating Tab Row) | Move `PrimaryScrollableTabRow` into a floating overlay (`Modifier.zIndex(1f).align(Alignment.TopCenter)`) wrapped in `AnimatedVisibility` with fade/slide. Visible during scroll (`pagerState.isScrollInProgress`), page changes, top tap, or on Page 0 (`ControlTab`), with a 2.5–3s auto-dismiss timer. |
| `SensorStatus.kt` | Support landscape compact layout option for centering 8 icons in status bar strip without vertical padding. |
| `TrackingTabsScreenLayoutTest.kt` | Add architectural and contract tests verifying landscape overlay properties, portrait invariants, and auto-hide timing states. |

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression in portrait mode: persistent `statusBarsPadding()`, persistent `SensorStatus`, and persistent `PrimaryScrollableTabRow`.
  2. Touch gestures, pager scroll slop, and bottom action button clearance (`LapButton`, `BikeBellButton`, snackbar) remain unoccluded.
  3. Parent ticket Human Decision Gate strictly enforced (`Final Review (Human)`).
* **Risk Rating**: **LOW** (Declarative Compose UI layout refinement isolated strictly to `Configuration.ORIENTATION_LANDSCAPE` during `ScreenMode.TRACKING`; zero database, service, or concurrency changes).
