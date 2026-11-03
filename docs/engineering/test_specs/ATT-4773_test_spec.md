# Stage 2: Requirement & Test Specification - ATT-4773: Optimize Landscape Cockpit Vertical Space: Sensor Icons in Center Status Bar and Auto-Hiding Floating Tab Row

**Ticket**: [ATT-4773](https://atrainingtracker.atlassian.net/browse/ATT-4773)  
**Parent Epic**: [ATT-2144](https://atrainingtracker.atlassian.net/browse/ATT-2144) (*[Epic] Landscape Mode*)  
**Target Release**: `v2.44.0`  
**Active Sprint**: `Sprint 2026-43.10`  
**Requirement Mapping**: `REQ-UI-434` (*Landscape Cockpit Vertical Space Optimization*)  
**Test Spec ID**: `TST-UI-399` (`TST-UI-399.1` .. `TST-UI-399.4`)  
**Branch**: `feature/ATT-4773`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-23  

---

## 1. Requirement Specification (REQ-UI-434)

### 1.1 Problem Statement & Rationale
In `TrackingTabsScreen` in landscape orientation (`Configuration.ORIENTATION_LANDSCAPE`) during `ScreenMode.TRACKING`, three stacked horizontal bars (`statusBarsPadding()`, `SensorStatus`, `PrimaryScrollableTabRow`) consume ~130–145dp of vertical height, squishing the live cockpit grid down to ~266dp on ~411dp screens. In landscape, the center of the Android status bar between the left clock/notifications and right status icons is completely empty. The system SHALL reclaim vertical space by centering sensor icons within the status bar strip and presenting the tab row as an auto-hiding floating overlay.

### 1.2 Functional & Architectural Requirements
1. **Center Status Bar Sensor Icons (`REQ-UI-434.1`)**: In landscape `ScreenMode.TRACKING`, render `SensorStatus` availability icons centered in the top status bar strip without vertical `statusBarsPadding()`. Position the drawer menu button (☰) without colliding with system insets.
2. **Auto-Hiding Floating Tab Row Overlay (`REQ-UI-434.2`)**: Render `PrimaryScrollableTabRow` as a floating top overlay (`zIndex(1f)`) over `HorizontalPager`. Smoothly animate (fade/slide) on swipe/scroll (`pagerState.isScrollInProgress`), page change, or top tap, and auto-dismiss after a 2.5–3.0 second timer once scrolling stops (and keep accessible on Page 0 `ControlTab`).
3. **Screen Mode & Portrait Invariants (`REQ-UI-434.3`)**: In portrait mode, preserve 100% byte-for-byte fidelity with persistent `statusBarsPadding()`, persistent `SensorStatus`, and persistent `PrimaryScrollableTabRow`. In `ScreenMode.CONFIGURATION` and `ScreenMode.PREVIEW`, preserve persistent headers.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines and amends `REQ-UI-163` (*Tracking Tabs Screen Navigation*), `REQ-UI-410` (*Tracking Tabs Sensor Status Bar*), and `REQ-UI-412` (*Tracking Screen Mode Switching*).
* **Historical Origin & Commit Trace**: Commit `e253c312` (`ATT-1340`), commit `de1c2965` (`ATT-4000`), and commit `d63529d8` (`ATT-4097`).
* **Root Reason for Existing Formulation**: Static `Column` with `statusBarsPadding()` and persistent `PrimaryScrollableTabRow` guaranteed discoverability and prevented status bar clipping on tall portrait screens (~800dp). In landscape (~411dp), permanent ~130–145dp chrome wastes ~35% of the viewport.
* **Preservation of Core Invariants**: 100% portrait layout fidelity, tab switching/swipe responsiveness, bottom button clearance (`LapButton`, `BikeBellButton`), and 100% clean-room test pass rate.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Landscape Status Bar)**: *Given* an athlete tracking a workout in landscape mode, *When* `ScreenMode.TRACKING` is active, *Then* sensor availability icons are centered inside the status bar strip and redundant vertical insets are omitted.
* **Criterion 2 (Auto-Hiding Floating Tab Row)**: *Given* the floating tab row is hidden in landscape mode, *When* the athlete swipes between tabs, *Then* the tab row animates into view as a floating overlay, remaining visible for 2.5–3s after scrolling stops before fading out.
* **Criterion 3 (Portrait & Config Invariants)**: *Given* the device is in portrait orientation or `ScreenMode.CONFIGURATION`/`PREVIEW`, *When* rendered, *Then* headers and status bar padding remain permanently visible with zero regressions.

---

## 2. Test Specification (TST-UI-399.1 .. TST-UI-399.4)

### Test Cases
* `TST-UI-399.1` (**Landscape Center Status Bar Sensor Icons Layout Test**): Verify `Configuration.ORIENTATION_LANDSCAPE` branch centers sensor icons in the status bar strip during `ScreenMode.TRACKING` without redundant `statusBarsPadding()`.
* `TST-UI-399.2` (**Auto-Hiding Floating Tab Row Overlay Test**): Verify `PrimaryScrollableTabRow` is styled as a floating overlay (`zIndex(1f)`) over `HorizontalPager`, conditioned on scroll state (`isScrollInProgress`) with 2.5–3s auto-dismiss.
* `TST-UI-399.3` (**Portrait & Screen Mode Invariants Preservation Test**): Verify portrait retains `statusBarsPadding()` and persistent tab row; verify `ScreenMode.CONFIGURATION` and `PREVIEW` retain persistent headers.
* `TST-UI-399.4` (**Clean-Room Full Suite Regression Execution**): Run targeted unit tests across tracking components and theme color centralization audit with 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-399.1` | Contract / UI | `TrackingTabsScreen.kt`, `SensorStatus.kt` | `REQ-UI-434.1` | Planned |
| `TST-UI-399.2` | Layout / UI | `TrackingTabsScreen.kt` | `REQ-UI-434.2` | Planned |
| `TST-UI-399.3` | Layout / UI | `TrackingTabsScreen.kt` | `REQ-UI-434.3` | Planned |
| `TST-UI-399.4` | Regression | Tracking Suite & Theme Audit | `REQ-PRO-001` | Planned |
