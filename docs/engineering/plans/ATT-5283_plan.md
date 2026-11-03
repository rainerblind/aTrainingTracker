# Stage 3: Implementation Plan - ATT-5283: Stationary Floating NonRecordingStatusBanner & Cockpit Color Harmony

**Ticket**: [ATT-5283](https://rainerblind.atlassian.net/browse/ATT-5283)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.3`  
**Requirement Mapping**: `REQ-UI-421.2`, `REQ-TRK-045.1`  
**Test Mapping**: `TST-UI-384.5`, `TST-TRK-045.1`, `TST-TRK-045.4`, `TST-UI-384.8`  
**Branch**: `feature/ATT-5283`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Description & Background
`BikeBellButton` and `LapButton` both currently use primary blue styling, causing visual confusion between the two Cockpit controls. Meanwhile, `NonRecordingStatusBanner` is embedded per-page inside `SensorGridScreen`, causing it to scroll horizontally during tab swipes and jump vertically based on per-tab navigation hints. Its amber warning styling also visually clashes once the bell button is orange. The banner must be hoisted as a stationary floating overlay in `TrackingTabsScreen` across tabs (`currentPage > 0`) styled in the app's primary blue palette, and `BikeBellButton` must use distinct orange theme tokens.

---

## 2. Traceability & Requirements Mapping
* **`REQ-UI-421.2` (Floating Cockpit Bell Button)** $\leftrightarrow$ `TST-UI-384.5`: Orange tokens in `Color.kt` and `BikeBellButton.kt`.
* **`REQ-TRK-045.1` (Stationary Floating HUD Banner)** $\leftrightarrow$ `TST-TRK-045.1`, `TST-TRK-045.4`: Hoisted overlay, blue palette, per-tab cleanup.
* **`REQ-PRO-001` (Core Regression Gate)** $\leftrightarrow$ `TST-UI-384.8`: Tier 1 targeted unit & contract test suite.

---

## 3. System Invariants & Preserved Behavior
1. **Multi-Mode Bell Availability**: `BikeBellButton` remains enabled across `READY`, `TRACKING`, `PAUSED` without pointer input contention.
2. **Overlay Suppression**: `NonRecordingStatusBanner` is strictly hidden on Control tab (`page == 0`) and in non-`TRACKING` modes.
3. **Sensor Grid Dimming**: Sensor tiles continue dimming to `alpha = 0.6f` when not recording; TPMS alert banner clearance preserved.
4. **Theme Centralization**: Zero hardcoded hex colors outside `Color.kt` (enforced by `ThemeColorCentralizationAuditTest`).

---

## 4. Proposed Architectural Changes
* **UI Consistency (Rule 23)**:
  * Reference screens: `ControlTrackingScreen` (Start/Resume blue palette) and `LapButton` (72.dp elevated button).
  * Reused components: `NonRecordingStatusBanner`, `BikeBellButton`, `ElevatedButton`, `FilledTonalButton`.
  * Theme tokens: `TTColor.BikeBellContainer` (`0xFFFF9800`), `TTColor.BikeBellContent` (`0xFF000000`), `MaterialTheme.colorScheme.primaryContainer`, `onPrimaryContainer`, `primary`, `onPrimary`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Centralized Orange Tokens (`Color.kt`) & Audit Test (`TST-UI-384.5`)
* Files: `Color.kt`, `ThemeColorCentralizationAuditTest.kt`
* Add `TTColor.BikeBellContainer = Color(0xFFFF9800)` and `TTColor.BikeBellContent = Color(0xFF000000)`.
* Add audit test `verifyBikeBellThemeTokensInTTColor()` asserting ARGB hex values and zero hex outside `Color.kt`.

### Step 2: Orange Button Styling (`BikeBellButton.kt`, `TST-UI-384.5`)
* Files: `BikeBellButton.kt`, `BikeBellButtonContractTest.kt`
* Bind `containerColor = TTColor.BikeBellContainer`, `contentColor = TTColor.BikeBellContent`, and icon tint when `isAvailable`.
* Update contract test to verify `TTColor.BikeBellContainer` references and ensure touch/press-roll contracts pass.

### Step 3: Blue Palette & Remove Nested Calls (`SensorGridScreen.kt`, `TST-TRK-045.4`)
* Files: `SensorGridScreen.kt`
* Update `NonRecordingStatusBanner` to `primaryContainer` / `onPrimaryContainer` with `primary` / `onPrimary` button and border.
* Remove nested calls in portrait (L569) and landscape (L989); retain `alpha = 0.6f` dimming and TPMS banner clearance.

### Step 4: Stationary Floating Overlay Hoisting (`TrackingTabsScreen.kt`, `TST-TRK-045.1`)
* Files: `TrackingTabsScreen.kt`
* Track `isAutoPaused` and resolve `cockpitStatus = SensorGridStatusResolver.resolveCockpitStatus(screenMode, trackingMode, isAutoPaused)`.
* Render stationary `NonRecordingStatusBanner` at `Alignment.TopCenter` outside `HorizontalPager` when `screenMode == ScreenMode.TRACKING && pagerState.currentPage > 0 && cockpitStatus != null`. Cleanly hidden on `page == 0`.

### Step 5: Verification & Contract Tests (`TST-UI-384.8`)
* Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.BikeBellButtonContractTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridStatusTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* Run: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Tier 1 targeted unit & contract tests, followed by ASPICE governance verification.
* **Rollback**: Atomic revert of `feature/ATT-5283` branch; zero schema alterations or persistent state migrations.
