# Stage 2: Requirement & Test Specification - ATT-5283: Stationary Floating NonRecordingStatusBanner & Cockpit Color Harmony

**Ticket**: [[ATT-5283]](https://rainerblind.atlassian.net/browse/ATT-5283)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.3`  
**Requirement Mapping**: `REQ-UI-421` (`REQ-UI-421.2`), `REQ-TRK-045` (`REQ-TRK-045.1`)  
**Test Mapping**: `TST-UI-384` (`TST-UI-384.5`), `TST-TRK-045` (`TST-TRK-045.1`, `TST-TRK-045.4`)  
**Branch**: `feature/ATT-5283`  
**Author**: AI Agent 1 (flash / Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-UI-421, REQ-TRK-045)

### 1.1 Problem Statement & Rationale
`BikeBellButton` and `LapButton` both currently use primary blue styling, causing visual identity confusion between the two Cockpit controls. Meanwhile, `NonRecordingStatusBanner` is embedded per-page inside `SensorGridScreen`, causing it to scroll horizontally during tab swipes and jump vertically based on per-tab navigation hints. Its amber warning styling also visually clashes once the bell button is orange. The banner must be hoisted as a stationary floating overlay in `TrackingTabsScreen` across tabs (`currentPage > 0`) styled in the app's primary blue palette, and `BikeBellButton` must use distinct orange theme tokens.

### 1.2 Functional & Architectural Requirements
1. *Orange Bike Bell Theme Tokens (`REQ-UI-421.2a`)*: `Color.kt` SHALL define `TTColor.BikeBellContainer` (`0xFFFF9800`) and `TTColor.BikeBellContent` (`0xFF000000`), providing WCAG AAA compliant contrast (> 10:1) across Light and Dark/AMOLED themes.
2. *Bike Bell Button Styling (`REQ-UI-421.2b`)*: `BikeBellButton.kt` SHALL apply `TTColor.BikeBellContainer` and `TTColor.BikeBellContent` when enabled (`isAvailable`), retaining `tint = if (isAvailable)` and gesture/audio contracts.
3. *Stationary Floating Banner Overlay (`REQ-TRK-045.1a`)*: `TrackingTabsScreen.kt` SHALL host `NonRecordingStatusBanner` as a stationary floating overlay at `Alignment.TopCenter` outside `HorizontalPager` whenever `screenMode == ScreenMode.TRACKING && pagerState.currentPage > 0 && cockpitStatus != null`. On the Control tab (`page == 0`) and in non-TRACKING modes, the banner SHALL remain hidden.
4. *Blue Palette Banner Styling (`REQ-TRK-045.1b`)*: `NonRecordingStatusBanner` SHALL use `MaterialTheme.colorScheme.primaryContainer` / `onPrimaryContainer` for the container and `MaterialTheme.colorScheme.primary` / `onPrimary` for the action button and border, aligning with `ControlTrackingScreen`.
5. *Clean-up of Per-Tab Duplicate Invocations (`REQ-TRK-045.1c`)*: `SensorGridScreen.kt` SHALL remove nested per-page invocations of `NonRecordingStatusBanner` in portrait and landscape layouts, while preserving `gridAlpha = 0.6f` opacity dimming and `TpmsAlertBanner` top padding clearance.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Orange Bell Button)**: *Given* tracking in `READY`, `TRACKING`, or `PAUSED`, *When* `BikeBellButton` renders, *Then* its container is `TTColor.BikeBellContainer` and icon is `TTColor.BikeBellContent`.
* **Criterion 2 (Stationary Banner Across Tabs)**: *Given* non-recording tracking mode (`READY`, `PAUSED`, `AUTO_PAUSED`), *When* swiping between telemetry, map, and lap tabs (`page > 0`), *Then* `NonRecordingStatusBanner` remains stationary at the top center of the viewport without scrolling horizontally.
* **Criterion 3 (Control Tab Suppression)**: *Given* `NonRecordingStatusBanner` is visible on sensor tabs, *When* switching to the Control tab (`page == 0`), *Then* the floating banner is cleanly hidden.
* **Criterion 4 (Blue Palette Banner)**: *Given* `NonRecordingStatusBanner` renders, *Then* it displays with `primaryContainer` surface and `primary` action button/border.

---

## 2. Test Specification (TST-UI-384, TST-TRK-045)

### Test Case 1: Bike Bell Color & Contract Compliance (`TST-UI-384.5`)
* **Scope**: Contract & Unit Test (`BikeBellButtonContractTest.kt`, `ThemeColorCentralizationAuditTest.kt`)
* **Procedure**: Assert `BikeBellButton.kt` references `TTColor.BikeBellContainer` and `TTColor.BikeBellContent`, preserves `tint = if (isAvailable)`, and passes zero hex color violations outside `Color.kt`.

### Test Case 2: Cockpit Status Resolution & Grid Dimming Alpha (`TST-TRK-045.1`, `TST-TRK-045.2`)
* **Scope**: Unit Test (`SensorGridStatusTest.kt`)
* **Procedure**: Assert `resolveCockpitStatus` and `resolveGridAlpha` contracts across `READY`, `PAUSED`, `AUTO_PAUSED`, `TRACKING`, and non-TRACKING modes.

### Test Case 3: Stationary Overlay & Color Centralization Audit (`TST-TRK-045.4`)
* **Scope**: Unit & Architectural Audit (`ThemeColorCentralizationAuditTest.kt`, `SensorGridStatusTest.kt`)
* **Procedure**: Verify `NonRecordingStatusBanner` uses Material Theme colorScheme tokens and zero hardcoded colors outside `Color.kt`.

### Test Case 4: Localization & Clean-Room Regression Suite (`TST-UI-384.8`, `TST-TRK-045.3`)
* **Scope**: Unit & Parity Tests (`TranslationParityTest.kt`, `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.*"`)
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-384.5` | Contract/Unit | `BikeBellButton.kt`, `Color.kt` | `REQ-UI-421.2a`, `REQ-UI-421.2b` | Specified |
| `TST-TRK-045.1` | Unit | `SensorGridStatusResolver.kt` | `REQ-TRK-045.1a` | Specified |
| `TST-TRK-045.4` | Architecture | `SensorGridScreen.kt`, `TrackingTabsScreen.kt` | `REQ-TRK-045.1b`, `REQ-TRK-045.1c` | Specified |
| `TST-UI-384.8` | Regression | Tier 1 Unit Test Suite | `REQ-PRO-001` | Specified |
