# Stage 3: Implementation Plan - ATT-4773: Optimize Landscape Cockpit Vertical Space

**Ticket**: [ATT-4773](https://rainerblind.atlassian.net/browse/ATT-4773)  
**Sub-task**: [ATT-4798](https://rainerblind.atlassian.net/browse/ATT-4798) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2144](https://rainerblind.atlassian.net/browse/ATT-2144) (*[Epic] Landscape Mode*)  
**Target Release**: `v2.44.0` | **Active Sprint**: `Sprint 2026-43.10` | **Branch**: `feature/ATT-4773`  
**Requirement Mapping**: `REQ-UI-434` (refining `REQ-UI-163`, `REQ-UI-410`, `REQ-UI-412`)  
**Test Mapping**: `TST-UI-399` (`TST-UI-399.1` .. `TST-UI-399.4`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-23  

---

## 1. Problem Description & Background
In `TrackingTabsScreen` in landscape orientation (`Configuration.ORIENTATION_LANDSCAPE`), three stacked horizontal elements (`statusBarsPadding()`, `SensorStatus`, `PrimaryScrollableTabRow`) permanently consume ~130–145dp (>35% of ~411dp vertical height). Because the center of the landscape status bar is empty, vertical space can be reclaimed by centering sensor availability icons inside the status bar strip and presenting `PrimaryScrollableTabRow` as an auto-hiding floating overlay on scroll/swipe, leaving maximum vertical height for live cockpit data tiles and maps.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-434` (*Landscape Cockpit Vertical Space Optimization*)
  * `REQ-UI-434.1`: Centered Sensor Availability Icons in Landscape Status Bar -> `TST-UI-399.1`
  * `REQ-UI-434.2`: Auto-Hiding Floating Tab Row Overlay (`PrimaryScrollableTabRow`) -> `TST-UI-399.2`
  * `REQ-UI-434.3`: Preservation of Portrait & Configuration Mode Invariants -> `TST-UI-399.3`
  * `REQ-PRO-001`: Clean-Room Full Suite Regression Execution -> `TST-UI-399.4`

---

## 3. System Invariants & Preserved Behavior
1. **Zero Unintended Regressions**: Portrait orientation layout and behavior remain 100% byte-for-byte unchanged.
2. **Configuration & Preview Mode Fidelity**: `ScreenMode.CONFIGURATION` and `PREVIEW` retain persistent headers.
3. **Gesture & Button Clearance**: Pager swipe slop, `LapButton`, `BikeBellButton`, and battery snackbars remain unhindered.
4. **Parent Human Decision Gate**: Terminal ticket closure strictly reserved for human in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes
* **UI Consistency (Rule 23)**:
  * Reference screen: `TrackingTabsScreen.kt` and `SensorStatus.kt`.
  * Reused components: `SensorStatus`, `PrimaryScrollableTabRow`, `Tab`, `AnimatedVisibility`.
  * Theme tokens: `MaterialTheme.colorScheme.surfaceContainerHighest`, `primary`, `onSurfaceVariant`, `onSurface`.
  * New one-off styles: None.

| Component | Target File | Architectural Changes |
| :--- | :--- | :--- |
| **Status Bar Icons** | `SensorStatus.kt` | Support compact landscape centering (`isCompactLandscape = true`), rendering 8 sensor icons centered without vertical padding. |
| **Cockpit Layout** | `TrackingTabsScreen.kt` | In landscape `ScreenMode.TRACKING`, omit root `statusBarsPadding()`; embed centered sensor icons inside status bar strip; float `PrimaryScrollableTabRow` via `zIndex(1f)` over pager. |
| **Auto-Hide State** | `TrackingTabsScreen.kt` | Manage floating tab row visibility: show on scroll (`pagerState.isScrollInProgress`), page switch, top tap, or Page 0 (`ControlTab`); auto-dismiss after 2.5–3.0s timer. |
| **Contract Tests** | `TrackingTabsLandscapeLayoutTest.kt` | Add architectural and contract assertions for landscape floating overlay, status bar centering, and portrait invariants. |

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Compact Landscape Centering in `SensorStatus.kt` (`REQ-UI-434.1`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/SensorStatus.kt`
* Action: Add optional `isCompactLandscape` parameter to render icons in a compact centered row without redundant padding.

### Step 2: Landscape Status Bar & Floating Tab Row Overlay in `TrackingTabsScreen.kt` (`REQ-UI-434.1`, `REQ-UI-434.2`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`
* Action: Branch on landscape orientation; place sensor icons in the top status bar strip; place `PrimaryScrollableTabRow` in an `AnimatedVisibility` overlay (`zIndex(1f)`) anchored to `Alignment.TopCenter`.

### Step 3: Auto-Hide Timer & Interaction Controller (`REQ-UI-434.2`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`
* Action: Drive visibility via `pagerState.isScrollInProgress`, `pagerState.currentPage`, and top tap with a 2.5–3.0s auto-dismiss coroutine (remain visible on Page 0).

### Step 4: Unit & Contract Tests Execution (`TST-UI-399.1` .. `TST-UI-399.4`)
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsLandscapeLayoutTest.kt`
* Action: Implement test suite verifying landscape overlay, status bar insets, auto-hide state, and portrait invariants:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted tests in Stage 4, followed by clean-room regression suite and Theme Audit in Stage 5.
* **Rollback**: Branch isolation (`feature/ATT-4773`); revert cleanly via `git checkout sprint/2026-43.10`.
