# Stage 1 Analysis: ATT-5283 - Stationary Floating NonRecordingStatusBanner & Cockpit Color Harmony

**Ticket**: [ATT-5283](https://rainerblind.atlassian.net/browse/ATT-5283)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.3`  
**Branch**: `feature/ATT-5283`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Statement & Motivation
During Sprint 2026-45.2 review of `ATT-5131`, two Cockpit HUD refinements were requested:
1. `BikeBellButton`: Shift background from primary blue to orange to visually distinguish the acoustic bell from `LapButton` (which uses primary blue).
2. `NonRecordingStatusBanner` (`CockpitStatus.NOT_STARTED`, `PAUSED`, `AUTO_PAUSED`):
   - Currently embedded inside each `SensorGridScreen` tab, causing it to scroll horizontally during `HorizontalPager` swipes and jump vertically across tabs.
   - Uses amber warning tokens (`LightWarningContainer` / `DarkWarningContainer`), which visually clash once `BikeBellButton` is orange.
   - User requested stationary floating behavior across tabs (matching `BikeBellButton`) and blue palette styling (aligning with `ControlTrackingScreen` Start/Resume controls).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Root Cause**:
  - `BikeBellButton.kt#L182-L186`: Uses `primaryContainer` / `onPrimaryContainer` (blue), causing visual identity confusion with `LapButton.kt#L78-L82`.
  - `SensorGridScreen.kt#L569, #L989`: `NonRecordingStatusBanner` is rendered inside `SensorGridScreen` per-tab content inside `HorizontalPager`.
  - `SensorGridScreen.kt#L1117-L1120`: Uses `DarkWarningContainer` / `LightWarningContainer` amber tokens originally derived from legacy `AutoPauseHudBanner`.
* **Independent Peer Review & Verification**:
  - Confirmed in `TrackingTabsScreen.kt#L961-L1018`: `BikeBellButton` / `LapButton` are already hosted outside `HorizontalPager` as stationary floating overlays.
  - Confirmed in `SensorGridScreen.kt#L568-L576` (portrait) & `#L988-L996` (landscape): Removing nested calls eliminates per-page duplicate rendering when hoisted.
  - Confirmed in `SensorGridScreen.kt#L585, #L1005`: `TpmsAlertBanner` checks `cockpitStatus != null` for top padding (`72.dp`), maintaining clearance under the stationary overlay.
  - Confirmed in `Color.kt#L188-L341`: `TTColor` lacks tokens for `BikeBellButton`. Adding `BikeBellContainer` (`0xFFFF9800`) and `BikeBellContent` (`0xFF000000`) satisfies WCAG AAA (>10:1 contrast) and `ThemeColorCentralizationAuditTest`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Centralize `TTColor.BikeBellContainer` and `BikeBellContent` in `Color.kt`; update `BikeBellButton.kt` while preserving gesture/continuous-ring contracts.
  - Refactor `NonRecordingStatusBanner` in `SensorGridScreen.kt` to the blue palette (`primaryContainer` / `onPrimaryContainer` with `primary` / `onPrimary` button/border).
  - Hoist `NonRecordingStatusBanner` into `TrackingTabsScreen.kt` outside `HorizontalPager` when `screenMode == ScreenMode.TRACKING && pagerState.currentPage > 0 && cockpitStatus != null`.
  - Remove per-page invocations from `SensorGridScreen.kt` while preserving `gridAlpha = 0.6f` dimming and TPMS banner clearance.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - No changes to `ControlTrackingScreen` (page 0), which already hosts full-sized controls.
  - No changes to `SensorGridStatusResolver` state transitions or `BikeBellAudioSynthesizer` audio/volume logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-421.2` (`BikeBellButton.kt`) & `REQ-TRK-045.1` (`TrackingTabsScreen.kt`, `SensorGridScreen.kt`).
* **Historical Origin & Commit Trace**: `ATT-4281` / `ATT-4431` (`REQ-UI-421.2`) and `ATT-4856` (commit `69e90fbb`, Sprint 2026-44.1, `REQ-TRK-045`).
* **Root Reason for Existing Formulation**: `REQ-TRK-045.1` embedded the banner inside `SensorGridScreen` because it was the sole sensor tab container; amber styling mirrored `AutoPauseHudBanner`. `BikeBellButton` originally copied `LapButton`'s blue styling.
* **Preservation of Core Invariants**: Grid dimming (`gridAlpha = 0.6f`), action broadcasts (`REQUEST_START_TRACKING`, `REQUEST_RESUME_FROM_PAUSED`), continuous bell ring via `interactionSource`, and `isAvailable` multi-mode gating (`READY`, `TRACKING`, `PAUSED`) are 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. **Color Tokens (`Color.kt`)**: Add `TTColor.BikeBellContainer = Color(0xFFFF9800)` and `TTColor.BikeBellContent = Color(0xFF000000)` (WCAG AAA 10.6:1 contrast).
2. **Bike Bell Styling (`BikeBellButton.kt`)**: Apply `TTColor.BikeBellContainer` and `TTColor.BikeBellContent` when `isAvailable`, retaining `tint = if (isAvailable)` for contract test parity.
3. **Banner Palette (`NonRecordingStatusBanner`)**: Switch to `primaryContainer` / `onPrimaryContainer` container with `primary` / `onPrimary` action button and border.
4. **Overlay Hoisting (`TrackingTabsScreen.kt`)**: Host `NonRecordingStatusBanner` at `Alignment.TopCenter` outside `HorizontalPager` for `currentPage > 0`. Remove duplicate calls from `SensorGridScreen.kt`.
5. **Side-Effect & Cross-Caller Impact Audit (Rule 68)**:
   - `BikeBellButtonContractTest.kt`: `tint = if (isAvailable)` and touch contracts preserved.
   - `ThemeColorCentralizationAuditTest.kt`: All hex colors confined to `Color.kt`.
   - `SensorGridScreen.kt`: Opacity dimming (0.6f) and `TpmsAlertBanner` top padding intact; `AutoPauseHudBanner` delegate retained.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Stationary banner is hidden on Control Tab (`page == 0`) and in `CONFIGURATION` / `PREVIEW` modes.
  2. `BikeBellButton` press-and-hold shake animation, continuous ring, and single-tap disambiguation remain intact.
  3. Zero hex color literals introduced outside `Color.kt` (`ThemeColorCentralizationAuditTest`).
* **Risk Rating**: **LOW**. Pure UI layout hoisting and theme token centralization; no background service, SQLite, or audio synthesis logic modified.
