# Stage 1 Analysis: ATT-7972 - Cockpit Radar Tail Light Button & Action Row

**Ticket**: [ATT-7972](https://rainerblind.atlassian.net/browse/ATT-7972)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Radar*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-46.7` | **Branch**: `feature/ATT-7972`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Problem Statement & Motivation
Cyclists currently have no direct 1-tap handlebar control to cycle radar tail light modes from the Cockpit tracking screen. While ATT-7991 unified persistence and added `Automatik` to `RadarLightQuickSwitchSheet`, opening the sheet requires tapping the radar status badge in the top bar. A dedicated Cockpit action button with live physical status, `AUTO` micro-badge, tap-to-cycle, long-press menu, and canonical integration into a responsive `[Licht, Lap, Klingel]` bottom row is required.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
Currently, `TrackingTabsScreen.kt` only renders `LapButton` and `BikeBellButton` in the floating bottom container. The tracking views database (`TrackingViewsDbHelper`, DB version 20) lacks a `ShowRadarLight` toggle column, and no `RadarLightButton.kt` component exists to expose `RadarLightController.cycleNextMode()` and sheet invocation at the bottom of the Cockpit.
* **Independent Peer Review & Verification**:
  * Verified `TrackingTabsScreen.kt` lines 1185–1226: button container only evaluates `shouldShowLapButton` and `shouldShowBikeBell`.
  * Verified `SensorGridScreen.kt` lines 962 & 979: `fabBottomClearance` only checks `state.showBikeBell || state.showLapButton`.
  * Verified `TrackingViewsDatabaseManager.java`: `DB_VERSION = 20` has `SHOW_MMP_CURVE` as latest column; needs clean upgrade to version 21 with idempotent migration and cycling default `1`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Upgrade `TrackingViewsDatabaseManager` to DB_VERSION 21 with `ShowRadarLight` (int DEFAULT 1 for cycling tabs).
  * Build `RadarLightButton.kt` (M3 `ElevatedButton`, 72.dp, semantic mode icons/colors, bottom `AUTO` label, tap-to-cycle, long-press menu via `MutableInteractionSource`).
  * Integrate into `TrackingTabsScreen.kt` with canonical order `[Licht, Lap, Klingel]` fitting 360.dp at 130% font scale.
  * Update tab configuration in `SensorGridScreen.kt` and `fabBottomClearance`.
  * Maintain 100% 9-language localization parity.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying BLE peripheral drivers or GATT queues (completed in ATT-7991).
  * Audio chime synthesis or threat edge overlays (governed by ATT-5085/ATT-5086).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-031` (Radar Mode Control) and `REQ-UI-421` (Floating Cockpit Action Buttons).
* **Historical Origin & Commit Trace**: `ATT-4281` (`40af1814`) introduced `BikeBellButton`; `ATT-4371` (`bac64ff9`) resolved touch contention via `interactionSource`; `ATT-7991` (`c422e1b6`) added debounced `cycleNextMode()`.
* **Root Reason for Existing Formulation**: `ElevatedButton` pointer input contention was eliminated by avoiding nested `pointerInput` and using `interactionSource` press/release tracking with `longPressTimeoutMillis`.
* **Preservation of Core Invariants**: Retain `MutableInteractionSource` press tracking; button disabled/hidden when light control unavailable; preserve StVZO pruning during cycling.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-CON-033` extending `REQ-CON-031` and `REQ-UI-421`.
* **Affected Architecture Elements**: `TrackingViewsDatabaseManager.java`, `TrackingViewsRepository.kt`, `TrackingViewModel.kt`, `TrackingTabsViewModel.kt`, `TrackingTabsScreen.kt`, `SensorGridScreen.kt`, new `RadarLightButton.kt`.
* **Verification Impact & Test Surface**: Unit tests for DB migration, button interactions, tab configuration toggles, localization parity test, and emulator visual check.
* **Risk Assessment & Invariants**: Medium risk (UI responsive budget + DB migration). Preserve SQLite idempotency, zero-regression on existing tabs, and non-blocking GATT cycling.

---

## 6. Architectural Strategy & High-Level Solution
Bump DB to v21 adding `ShowRadarLight`. Construct `RadarLightButton` mimicking `BikeBellButton` architecture (M3 `ElevatedButton`, height 72.dp, `interactionSource` demuxing short tap vs long press). Layout buttons as `[RadarLightButton, LapButton, BikeBellButton]` in `TrackingTabsScreen.kt` with adaptive padding on 360.dp portrait screens.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Fail-safe visibility: Button hidden if `isLightControlAvailable == false` or tab disables it.
  2. Interaction safety: Short tap cycles mode; long press opens sheet without mode advance.
  3. Responsive fit: Single-row 3-button layout on 360.dp width without truncation.
* **Risk Rating**: **MEDIUM** (Visual UI layout + Database schema version bump).
