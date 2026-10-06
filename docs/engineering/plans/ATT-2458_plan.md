# Stage 3: Implementation Plan - ATT-2458: Relocate Route Selection Button from Cockpit Sensor Grid Tabs to Exclusively Control Tracking Screen

**Ticket**: [ATT-2458](https://atrainingtracker.atlassian.net/browse/ATT-2458)  
**Sub-task**: [ATT-2562](https://atrainingtracker.atlassian.net/browse/ATT-2562) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-66](https://atrainingtracker.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.1`  
**Requirement Mapping**: `REQ-UI-279` (*Branded Route Selection Entry Point on Control Tracking Screen & Cockpit Sensor Grid Decoupling*)  
**Test Mapping**: `TST-UI-239` (*Branded Route Selection Button on Control Tracking Screen & Cockpit Sensor Grid Decoupling Verification*)  
**Branch**: `feature/ATT-2458`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-06  

---

## 1. Problem Description & Background

In live tracking mode, athletes monitor real-time workout telemetry across swipeable cockpit tabs (`SensorGridScreen.kt`).
Previously, under `REQ-MAP-024` (ATT-1835), a floating quick route action chip (`RouteActionChipRow`) was rendered above the sensor grid on every cockpit tab.
This introduced several issues:
1. **Vertical Screen Waste**: Taking up 48–56dp of vertical height on every single sensor grid tab reduced the glanceability of sensor data.
2. **Context Mismatch**: Route selection is a pre-ride/setup workflow that belongs on the setup screen (`ControlTrackingScreen.kt`), rather than being redundantly duplicated across every telemetry tab.
3. **Visual Brand Integration**: The legacy chip lacked distinct route-semantic identity.

With ATT-2189 (`REQ-UI-278`), pairing buttons were relocated to the "Meine Sensoren" tabs, creating a dedicated `bottomContent` slot on `ControlTrackingScreen.kt`.
ATT-2458 relocates route selection to exclusively this slot as an app-consistent, branded `RouteSelectionButton` with green domain accents (`TTColor.RouteSelected`), while removing the chip from `SensorGridScreen.kt`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-279` (*Branded Route Selection Entry Point on Control Tracking Screen & Cockpit Sensor Grid Decoupling*)
  * Amends `REQ-MAP-024` Clause 1 (*Cockpit 1-Tap Entry Point*).
  * Fulfills `REQ-UI-278` slot utilization on `ControlTrackingScreen.kt`.
  * `REQ-UI-279.1`: Clean removal of `RouteActionChipRow` from `SensorGridScreen.kt`.
  * `REQ-UI-279.2`: Exclusive entry point on `ControlTrackingScreen.kt` via `TrackingTabsScreen.kt` `bottomContent` slot.
  * `REQ-UI-279.3`: Branded green design tokens and styling in `RouteSelectionButton.kt`.
  * `REQ-UI-279.4`: Full functional parity (modal sheet, active route display, 1-tap route cancellation, auto-detect preserved).
  * `REQ-UI-279.5`: 100% 9-language localization parity for all strings.
* **Test Mapping**: `TST-UI-239` (*Branded Route Selection Button on Control Tracking Screen & Cockpit Sensor Grid Decoupling Verification*)
  * `TST-UI-239.1`: Unit & Contract tests in `ControlTrackingRouteSelectionContractTest.kt`.
  * `TST-UI-239.2`: Sensor grid integration assertions in `SensorGridScreenRouteIntegrationTest.kt`.
  * `TST-UI-239.3`: 9-language localization assertions across all 9 locales.
  * `TST-UI-239.4`: Full test suite regression execution (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Auto-Detection Invariant**: In-ride automated route detection (`RouteAutoDetector`, `AutoDetectedRouteBanner`) remains active and undisturbed in `SensorGridScreen.kt`.
2. **Bottom Sheet & Repository Contract Invariant**: `RouteSelectorModalBottomSheet`, `RouteSelectorViewModel`, and `RoutesRepository.stopRoute()` / `setActiveNavigatedRoute()` remain functionally identical.
3. **Sensor Tab Density**: Removing the chip from `SensorGridScreen.kt` must cleanly return vertical height to the sensor grid without leaving empty padding or gaps.
4. **Theme Adaptability**: `RouteSelectionButton` must support Light, Dark, and AMOLED themes without hardcoded hex colors, utilizing `MaterialTheme.colorScheme` and `TTColor.RouteSelected`.
5. **Human Gate Governance Invariant**: Subtask [ATT-2562](https://atrainingtracker.atlassian.net/browse/ATT-2562) transitions to `Erledigt` via reviewer audit. Parent ticket [ATT-2458](https://atrainingtracker.atlassian.net/browse/ATT-2458) stops at `Final Review (Human)` and is never marked `Erledigt` by agents.

---

## 4. UI Consistency (Rule 23 / Design Guidelines Section 5)

* **Closest Existing Reference Screen**:
  * `ControlTrackingScreen.kt` bottom slot (`bottomContent`).
  * `SensorDetailCards.kt` and `WorkoutSummariesTabbedScreen.kt` elevated surface cards.
* **Reused Components & Design Tokens**:
  * Shape: `RoundedCornerShape(12.dp)`.
  * Surface: `Surface` or `OutlinedCard` with `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)`.
  * Border: `BorderStroke(1.dp, TTColor.RouteSelected.copy(alpha = 0.35f))`.
  * Semantic Icon Container: 36.dp container with 8.dp rounded corners, `TTColor.RouteSelected.copy(alpha = 0.12f)` background, housing `R.drawable.ic_route` tinted with `TTColor.RouteSelected`.
  * Trailing Navigation Icon: `Icons.AutoMirrored.Default.ArrowForwardIos`.
  * Trailing Clear Icon: `Icons.Default.Close` for 1-tap route cancellation.
  * Typography: `MaterialTheme.typography.titleMedium` (headline) and `MaterialTheme.typography.bodySmall` (subtitle/metrics).
* **Justification for Any New One-Off Styles**: None. Adheres strictly to Rule 23 design tokens and theme palettes.

---

## 5. SWE.2 Architecture & Component Interaction

```
┌────────────────────────────────────────────────────────────────────────┐
│ TrackingTabsScreen (HorizontalPager)                                   │
│                                                                        │
│ Page 0: ControlTrackingScreen                                          │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │ Telemetry / Map / Workout Controls                                 │ │
│ │                                                                    │ │
│ │ bottomContent = {                                                  │ │
│ │   RouteSelectionButton(                                            │ │
│ │     activeRoute = activeNavigatedRoute,                            │ │
│ │     onClick = { showRouteSelectorSheet = true },                   │ │
│ │     onClearRoute = { routesRepository.stopRoute() }                │ │
│ │   )                                                                │ │
│ │ }                                                                  │ │
│ └────────────────────────────────────────────────────────────────────┘ │
│                                                                        │
│ Pages 1..N: SensorGridScreen                                           │
│ ┌────────────────────────────────────────────────────────────────────┐ │
│ │ Telemetry Sensor Grid (MAXIMUM DENSITY - NO ROUTE ACTION CHIP)      │ │
│ │ AutoDetectedRouteBanner (Renders overlay ONLY when detected)       │ │
│ └────────────────────────────────────────────────────────────────────┘ │
│                                                                        │
│ RouteSelectorModalBottomSheet (Shown when showRouteSelectorSheet = true)│
└────────────────────────────────────────────────────────────────────────┘
```

---

## 6. Step-by-Step Implementation Sequence

### Step 1: Localization Resource Definition (9 Locales)
- Verify and add strings to `values/strings.xml` and the 8 translation files (`values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`):
  - `route_action_select` (existing or verify)
  - `route_action_select_desc` ("Choose from file or history" / "Aus Datei oder Verlauf wählen")
  - `route_action_clear` ("Clear route" / "Route entfernen")
  - `route_metrics_format` ("%1$s • %2$s")

### Step 2: Implementation of `RouteSelectionButton.kt`
- Create `de.tadris.trainingtracker.ui.components.RouteSelectionButton.kt`:
  - Support two states:
    - **Inactive (No route selected)**:
      - Shows green-tinted route icon (`R.drawable.ic_route`), title `route_action_select`, subtitle `route_action_select_desc`, and chevron forward.
      - Whole card clickable -> opens sheet.
    - **Active (Route selected & followed)**:
      - Shows green-tinted route icon, title "✓ <Route Name>", subtitle with route distance + elevation, and a distinct close/clear button (`Icons.Default.Close`) invoking `onClearRoute`.
      - Tapping card body opens sheet to view or switch route.
  - Apply `RoundedCornerShape(12.dp)`, `BorderStroke(1.dp, TTColor.RouteSelected.copy(alpha = 0.35f))`, and smooth padding (`16.dp` horizontal, `10.dp` vertical).

### Step 3: Decouple `SensorGridScreen.kt`
- Remove `RouteActionChipRow` call from `SensorGridScreen.kt`.
- Clean up unused route selector sheet states or parameters passed down exclusively for the chip if no longer needed in `SensorGridScreen`.
- Preserve `AutoDetectedRouteBanner` and route detection hooks.

### Step 4: Wire `TrackingTabsScreen.kt` & `ControlTrackingScreen.kt`
- In `TrackingTabsScreen.kt`, pass `RouteSelectionButton` into `ControlTrackingScreen`'s `bottomContent` parameter.
- Connect `showRouteSelectorSheet` state toggle to the button's `onClick`.
- Connect `routesRepository.stopRoute()` to `onClearRoute`.
- Pass current location flow to `RouteSelectorViewModel` as established.

### Step 5: Unit & Contract Tests
- Create `ControlTrackingRouteSelectionContractTest.kt` asserting:
  - `SensorGridScreen.kt` does not reference or render `RouteActionChipRow`.
  - `RouteSelectionButton.kt` adheres to shape, border, and semantic tokens.
  - Active and inactive states render appropriate titles and actions.
  - `AutoDetectedRouteBanner` remains in `SensorGridScreen.kt`.
- Create `RouteSelectionLocalizationTest.kt` verifying all strings exist in all 9 languages.
- Run targeted tests via Gradle (`BypassSandbox: true`).

### Step 6: Gate 3 Review & Transition
- Audit subtask [ATT-2562](https://atrainingtracker.atlassian.net/browse/ATT-2562) via `review_agent.py audit ATT-2562`.
- Verify gate pass and transition to `Erledigt`.

---

## 7. Quality Gate Checklist (Stage 3)

- [x] Traceability: `REQ-UI-279` -> `TST-UI-239` fully articulated.
- [x] Rule 23 Design Compliance: Verified against `docs/design_guidelines.md` tokens.
- [x] Backward Compatibility: No disruption to route tracking engine or auto-detection.
- [x] Atomic Implementation Steps: Detailed across 6 clear sequential steps.
- [x] 9-Language Localization: Complete translation strategy defined.
