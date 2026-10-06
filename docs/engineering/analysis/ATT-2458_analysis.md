# Stage 1 Analysis: Show Route Selection Button Only on Control Tracking Screen with App-Consistent Branding (ATT-2458)

## 1. Issue Summary & Context
During the Sprint 2026-40.16 Joint Review and 2026-41.1 Sprint Planning, human evaluation of the quick route selector feature introduced in ATT-1835 (`REQ-MAP-024`) identified two key architectural and visual UX issues:
1. **Misplaced Cockpit Tab Entry Point**: ATT-1835 embedded `RouteActionChipRow` inside `SensorGridScreen.kt`. Because `SensorGridScreen` renders every individual cockpit tracking tab grid, the route chip was duplicated across every single tracking tab in the swipeable cockpit pager, cluttering the live sensor displays during rides.
2. **Visual Inconsistency & Generic Floating Chip**: The original `RouteActionChipRow` was styled as a generic floating chip (`RoundedCornerShape(8.dp)`, plain `surfaceVariant` / `primaryContainer`) rather than looking like an integrated app element that adheres to the established design system tokens (`docs/design_guidelines.md` Rule 23, Section 5).
3. **Synergy with ATT-2189**: In ATT-2189, pairing buttons were successfully relocated from `ControlTrackingScreen` into the dedicated sensor settings dialog, freeing up a flexible container slot (`bottomContent`) at the bottom of the control tracking screen.
4. **Human Guidance & Map Color Semantics**: The route selection button must be displayed **only** on the `ControlTrackingScreen` (page 0 of the tracking pager), occupying the space freed up by ATT-2189. Furthermore, because route geometry and polylines on the map are rendered in green (`TTColor.RouteSelected`), buttons and interactive elements for route selection or navigation must incorporate a subtle green touch (e.g. subtle green border accent, tinted icon, or gentle background blend as specified in `docs/design_guidelines.md` Section 5.4), establishing an intuitive semantic connection with the map while maintaining a minimal and elegant appearance.

---

## 2. Problem Domain & Root Cause Analysis

### 2.1 The Cockpit Grid Intrusion
In `SensorGridScreen.kt`:
```kotlin
// Quick Route Selector Action Button / Chip (REQ-MAP-024 / ATT-1835)
if (screenMode == ScreenMode.TRACKING) {
    RouteActionChipRow(
        activeRoute = routeSelectorUiState.activeRoute,
        returnNavState = returnNavState,
        onClick = { showRouteSelectorSheet = true },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}
```
Because `TrackingTabGridContent.kt` instantiates a `SensorGridScreen` for each tracking tab (`TrackingTabGridContent(viewInfo.tabViewId, screenMode)`), every tab displayed `RouteActionChipRow` at the top of its sensor column. This violated the scannability of athlete metrics during intense workouts.

### 2.2 Relocation Target: `ControlTrackingScreen`
`ControlTrackingScreen.kt` represents the pre-workout setup and in-workout control hub (Start / Pause / Resume / Stop, sport selection, sensor connectivity). This is the exact screen where athletes configure and control their session.
In ATT-2189, we introduced:
```kotlin
// Flexible bottom content slot (e.g. Route Selection in ATT-2458)
bottomContent()
```
Positioned below the centered `ControlTrackingButton` and `SportTypeSelector`.

### 2.3 Branding & Green Domain Semantic Accent
Per `docs/design_guidelines.md`:
- **Section 5.1 (Reuse Before Create)**: Copy structures, shapes, spacing, and colors from existing app cards.
- **Section 5.2 (Spacing Scale)**: `16.dp` horizontal padding, `12.dp` vertical padding, `12.dp` icon spacing.
- **Section 5.3 (Shapes)**: Standard card shape `RoundedCornerShape(12.dp)`.
- **Section 5.4 (Color & Domain Tints)**: Route visualization uses green tones (`TTColor.RouteSelected = Color(0xFF228B22)`). The route selection card must feature a subtle green touch—specifically:
  - Subtle green border accent: `BorderStroke(1.dp, TTColor.RouteSelected.copy(alpha = 0.35f))`.
  - Subtle branded icon container: 36.dp box with 8.dp rounded corners, `TTColor.RouteSelected.copy(alpha = 0.12f)` background, housing `R.drawable.ic_route` tinted with `TTColor.RouteSelected`.
  - Harmonious surface background: `MaterialTheme.colorScheme.surfaceVariant` when active, `MaterialTheme.colorScheme.surface` when inactive, ensuring full parity across Light, Dark, and AMOLED themes.
  - Active route state: Displays `"✓ <Route Name>"`, route distance + elevation (or dynamic remaining navigation metrics if return navigation is active), and a dedicated stop/clear action button (`Icons.Default.Close`).
  - Inactive route state: Displays `"Select Route"` (`R.string.route_action_select`) with descriptive subtitle and subtle trailing chevron (`Icons.AutoMirrored.Default.ArrowForwardIos`).

---

## 3. Chesterton's Fence & Requirement Archaeology

### 3.1 Historical Trace: `REQ-MAP-024` (ATT-1835)
- **Origin**: Ticket ATT-1835 introduced the Quick Route Selector from the Cockpit.
- **Why was it placed in `SensorGridScreen`?**: At the time of ATT-1835, `ControlTrackingScreen` was heavily overloaded with pairing buttons (`PairingButtons(isAntSupported, isBluetoothSupported, ...)`), leaving no vertical space without pushing the Start button off-screen. Placing it in `SensorGridScreen` was an expedient stopgap.
- **What changed?**: ATT-2189 cleanly extracted the pairing triggers into the dedicated sensor settings dialog and added `bottomContent()` to `ControlTrackingScreen`.
- **What must be preserved?**:
  1. Automated Route Detection (`RouteAutoDetector`, `AutoDetectedRouteBanner`) must remain in `SensorGridScreen.kt` so that in-ride candidate prompts continue to appear seamlessly over live tracking.
  2. Bottom sheet functionality (`RouteSelectorModalBottomSheet`) must remain 100% operational when launched from the new entry point.
  3. Location propagation to `RouteSelectorViewModel` must remain continuous so that proximity ranking and candidate detection remain accurate.
  4. Active route selection and cancellation (`setActiveNavigatedRoute`, `stopRoute`) must remain functional.

---

## 4. Scope Bounding & Invariants

### 4.1 In Scope
- Remove `RouteActionChipRow` from `SensorGridScreen.kt` so no sensor grid tab renders the button.
- Create `RouteSelectionButton` (or `RouteSelectionCard`) adhering to Rule 23 design tokens and Section 5.4 green domain semantic accents.
- Integrate `RouteSelectionButton` into `ControlTrackingScreen.kt` (as default content for `bottomContent` and wired in `TrackingTabsScreen.kt`).
- Wire `RouteSelectorModalBottomSheet` launch and navigation cancellation from `ControlTrackingScreen` / `TrackingTabsScreen`.
- Update `SensorGridScreenRouteIntegrationTest.kt` to reflect removal of `RouteActionChipRow` from `SensorGridScreen`.
- Add comprehensive contract and unit tests (`ControlTrackingRouteSelectionContractTest.kt`) verifying layout, branding tokens, green border/accent, click handling, active/inactive states, and 9-language localization parity.

### 4.2 Out of Scope
- Modifying route selector sheet internals, tabs, or ranking algorithms (covered in ATT-2459 and ATT-2460).
- Modifying return navigation calculations (`ReturnNavigationRepository`, `ElevationAwareEtaCalculator`).
- Modifying GPS device instantiation or permissions (ATT-2357).

### 4.3 Invariants
- `AutoDetectedRouteBanner` in `SensorGridScreen.kt` must remain untouched.
- Full unit test suite regression (`./gradlew testDebugUnitTest`) must pass at 100%.
- Zero hardcoded colors; must use `TTColor.RouteSelected` and `MaterialTheme.colorScheme`.
- Zero unlocalized strings; 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 5. Architecture & Implementation Strategy

```
┌────────────────────────────────────────────────────────┐
│ TrackingTabsScreen                                     │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Page 0: ControlTrackingScreen                    │  │
│  │                                                  │  │
│  │   - Remote Devices Status Bar                    │  │
│  │   - Spacer(weight = 1f)                          │  │
│  │   - ControlTrackingButton (Start/Pause/Stop)     │  │
│  │   - SportTypeSelector                            │  │
│  │   - Spacer(weight = 1f)                          │  │
│  │   - bottomContent:                               │  │
│  │     ┌──────────────────────────────────────────┐ │  │
│  │     │ RouteSelectionButton                     │ │  │
│  │     │  - Border: TTColor.RouteSelected (35%)   │ │  │
│  │     │  - Icon: ic_route in green box           │ │  │
│  │     │  - Title: "✓ Route Name" / "Select Route"│ │  │
│  │     │  - Subtitle: Dist+Elev / "Select Route"  │ │  │
│  │     │  - Stop button / Chevron                 │ │  │
│  │     └──────────────────────────────────────────┘ │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  ┌──────────────────────────────────────────────────┐  │
│  │ Page 1..N: TrackingTabGridContent                │  │
│  │   - SensorGridScreen                             │  │
│  │     - AutoDetectedRouteBanner (KEPT)             │  │
│  │     - RouteActionChipRow (REMOVED)               │  │
│  │     - Sensor Fields Grid                         │  │
│  └──────────────────────────────────────────────────┘  │
│                                                        │
│  RouteSelectorModalBottomSheet (when open)             │
└────────────────────────────────────────────────────────┘
```

1. **Component**: `RouteSelectionButton` in `com.atrainingtracker.trainingtracker.ui.tracking.controltracking.RouteSelectionButton.kt`.
2. **Screen Integration**: Provide `RouteSelectionButton` as the default implementation of `bottomContent` in `ControlTrackingScreen.kt`, and connect state/events in `TrackingTabsScreen.kt`.
3. **Sheet Hosting**: `TrackingTabsScreen.kt` hosts `RouteSelectorModalBottomSheet` controlled by `showRouteSelectorSheet`.
4. **Cleanup**: Remove `RouteActionChipRow` call from `SensorGridScreen.kt`.

---

## 6. Risk Analysis & Mitigation
- **Risk 1: RouteSelectorViewModel lifecycle across tab swiping**: In `TrackingTabsScreen`, `RouteSelectorViewModel` is scoped to the screen/activity level, ensuring selected route and location state are preserved seamlessly.
- **Risk 2: Breaking existing contract test `SensorGridScreenRouteIntegrationTest`**: Update the contract test to assert that `SensorGridScreen` does NOT contain `RouteActionChipRow`, and verify new contract in `ControlTrackingRouteSelectionContractTest`.
- **Risk 3: Visual crowding on compact devices**: `RouteSelectionButton` uses standard `12.dp` vertical padding and balanced `Spacer(Modifier.weight(1f))` in `ControlTrackingScreen`, preserving centered controls and clean breathing room.
