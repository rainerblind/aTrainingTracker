# Stage 2: Requirement & Test Specification - ATT-6703: Fix RadarEdgeOverlay inverted bottom-to-top kinematics, AUTO_HIDE_3S ghost tap target, always-on animation, and reconnect action

**Ticket**: [ATT-6703](https://rainerblind.atlassian.net/browse/ATT-6703)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-062.1`, `REQ-TRK-062.2`, `REQ-TRK-062.3`, `REQ-TRK-062.4`, `REQ-TRK-062.5`, `REQ-TRK-062.6`  
**Test Spec Mapping**: `TST-TRK-062.1`, `TST-TRK-062.2`, `TST-TRK-062.3`, `TST-TRK-062.4`, `TST-TRK-062.5`, `TST-TRK-062.6`, `TST-TRK-062.7`  
**Branch**: `feature/ATT-6703` | **Author**: AI Agent 1 (Flash) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-TRK-062 Refinement)

### 1.1 Problem Statement & Rationale
Sprint 2026-46.1 review of `RadarEdgeOverlay.kt` and `TrackingTabsScreen.kt` identified four usability, ergonomic, and performance defects:
1. **Inverted Rearview Vertical Axis (`Top -> Bottom` Inversion)**: Per standard rearview radar ergonomics (Garmin Varia, Wahoo ELEMNT, Trek CarBack), the cyclist is at the **top** ($0\text{ m}$, directly under `RadarStatusBadgePill`) and approaching vehicles enter from behind at the **bottom** ($140\text{ m}$) and move **upward** toward the cyclist ($y \to 0$). `RadarOverlayMath.normalizeRadarRange()` currently returns $1 - d/140$, mapping $140\text{ m}$ to $y = 0$ (top), displaying approaching traffic as moving downward like oncoming vehicles.
2. **`AUTO_HIDE_3S` Hides Status Badge & Leaves Ghost Touch Target**: Applying `.alpha(overlayAlpha)` to the outer `Column` hides both the vertical rail and `RadarStatusBadgePill` after 3s of all-clear road. This deprives the cyclist of connection status while leaving `RadarStatusBadgePill`'s `.clickable` interceptor active as an invisible $24\text{ dp}$ phantom touch target over cockpit tiles and maps.
3. **Unconditional 60 fps `rememberInfiniteTransition` Battery Drain**: `rememberInfiniteTransition` runs an active $350\text{ ms}$ reverse animation at 60 fps continuously throughout the entire ride, even when `overallThreatLevel == ThreatLevel.NONE`. This creates constant GPU composition overhead on long rides.
4. **Missing Tap-to-Reconnect, Navigation Clearance, Vector Icons & `@Preview`s**: Tapping the badge while in `DISCONNECTED_ERROR` opens `RadarLightQuickSwitchSheet` instead of triggering reconnect; the rail lacks `.navigationBarsPadding()` (Rule 41); the badge uses raw `"!"` text instead of vector icons; and `@Preview` composables are missing.

### 1.2 Functional & Architectural Requirements
1. **Upward Vertical Rail Kinematics & Dot Interpolation (`REQ-TRK-062.1`)**: The system SHALL project approaching targets along the vertical rail with $140\text{ m}$ mapped to the bottom of the rail ($y = 1.0$) and $0\text{ m}$ mapped to the top ($y = 0.0$), computing $y = (d / d_{\max})\text{.coerceIn(0.0f, 1.0f)}$ so vehicles travel upward toward the rider as distance decreases.
2. **Conditional Pulsing Alert Scoping (`REQ-TRK-062.2`)**: The pulsing red alert animation SHALL be composed and evaluated ONLY when `overallThreatLevel == ThreatLevel.FAST_APPROACHING`. When threat level is `NONE` or `APPROACHING`, the pulse alpha SHALL evaluate to a static $1.0\text{f}$, completely stopping infinite animation frame scheduling.
3. **Rail-Only Auto-Hide & Ghost Click Prevention (`REQ-TRK-062.3`)**: In `AUTO_HIDE_3S` mode, auto-hiding after 3 seconds of all-clear road SHALL be scoped strictly to `RadarVerticalRail`. `RadarStatusBadgePill` SHALL remain permanently visible, interactive, and accessible at the top of the edge rail.
4. **Fail-Safe Disconnect, Vector Icons & 1-Tap Reconnect (`REQ-TRK-062.4`)**: When in `DISCONNECTED_ERROR`, the badge SHALL display `ic_device_not_available` (never indicating green "All Clear"). Tapping `RadarStatusBadgePill` in `DISCONNECTED_ERROR` SHALL broadcast `TrainingApplication.REQUEST_START_SEARCH_FOR_PAIRED_DEVICES`.
5. **System Navigation Clearance & Compose `@Preview`s (`REQ-TRK-062.5`)**: `RadarEdgeOverlay` SHALL apply `.navigationBarsPadding()` per Design Guidelines §5.6 Rule 41 to ensure bottom border clearance above system navigation bars. The composable SHALL provide Light, Dark, and AMOLED `@Preview` variants (Rule 4).
6. **9-Language Localization & Accessibility Parity (`REQ-TRK-062.6`)**: All radar overlay accessibility descriptions and status labels in `strings_radar.xml` SHALL maintain 100% key and format parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-TRK-062` (Live Rearview Radar Vertical Edge Overlay Across Tracking Tabs), targeting `RadarEdgeOverlay.kt`, `TrackingTabsScreen.kt`, `RadarOverlayMath`, `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`, and `strings_radar.xml`.
* `Historical Origin & Commit Trace`: Introduced in `ATT-5085` (commit `671c4c1a`); refined in `ATT-6703` to correct inverted vertical rail kinematics ($140\text{ m}$ bottom $\to 0\text{ m}$ top), isolate auto-hide alpha to the vertical rail, conditionally gate `rememberInfiniteTransition` to `FAST_APPROACHING`, wire 1-tap reconnect broadcast on `DISCONNECTED_ERROR`, add `.navigationBarsPadding()`, and replace raw text with vector icons.
* `Root Reason for Existing Formulation`: In the initial prototype, $y = 1 - d/d_{\max}$ was chosen under an inverted coordinate assumption ("0 = origin distance"), resulting in vehicles traveling downward from top to bottom. `AUTO_HIDE_3S` alpha was applied to the root `Column` for simplicity, inadvertently hiding the status badge and leaving a phantom clickable area. `rememberInfiniteTransition` was allocated unconditionally at the top of the composable function. Badge clicks were unconditionally routed to `RadarLightQuickSwitchSheet` without checking connection state.
* `Preservation of Core Invariants`: Cyclist safety through unambiguous, physical rearview perspective (vehicles approach from behind); zero touch interception along the rail; fail-safe amber disconnect indication (zero false green); system nav-bar clearance; 100% 9-language translation parity.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* an active tracking session with a connected radar, *When* vehicles approach at 140m, 70m, and 0m, *Then* the overlay renders dots at $y = 1.0$ (bottom), $0.5$ (mid), and $0.0$ (top), traveling upward as distance decreases.
* *Given* `overallThreatLevel == ThreatLevel.NONE` or `APPROACHING`, *When* `RadarEdgeOverlay` composes, *Then* zero infinite animation transitions run and pulse alpha remains static at 1.0f.
* *Given* `clearMode == AUTO_HIDE_3S` and 3 seconds of clear road, *When* the auto-hide timer elapses, *Then* the vertical rail fades out while `RadarStatusBadgePill` remains visible and interactive.
* *Given* `connectionState == RadarConnectionState.DISCONNECTED_ERROR`, *When* tapping `RadarStatusBadgePill`, *Then* `TrainingApplication.REQUEST_START_SEARCH_FOR_PAIRED_DEVICES` is broadcast.

---

## 2. Test Specification (TST-TRK-062 Refinement)

### Test Case 1: Bottom-to-Top Upward Kinematics (`TST-TRK-062.1a`)
* **Scope**: JVM Unit Test (`RadarEdgeOverlayTest.kt`)
* **Procedure**: Pass distances $140\text{ m}$, $70\text{ m}$, $0\text{ m}$ into `RadarOverlayMath.normalizeRadarRange()`.
* **Expected Result**: Returns $1.0\text{f}$ for $140\text{ m}$ (bottom of rail), $0.5\text{f}$ for $70\text{ m}$ (midpoint), and $0.0\text{f}$ for $0\text{ m}$ (top / adjacent to rider badge).

### Test Case 2: Boundary Clamping & Max Range Guards (`TST-TRK-062.1b`)
* **Scope**: JVM Unit Test (`RadarEdgeOverlayTest.kt`)
* **Procedure**: Pass distances $> 140\text{ m}$ ($200\text{ m}$), negative distances ($-10\text{ m}$), and degenerate max range ($0\text{ m}$).
* **Expected Result**: $200\text{ m}$ clamps to $1.0\text{f}$; negative distance clamps to $0.0\text{f}$; degenerate max range returns $0.0\text{f}$ safely without division by zero.

### Test Case 3: Semantic Threat Color Mapping (`TST-TRK-062.2a`)
* **Scope**: JVM Unit Test (`RadarEdgeOverlayTest.kt`)
* **Procedure**: Verify `RadarOverlayMath.resolveThreatColor()` mapping for `NONE`, `APPROACHING`, and `FAST_APPROACHING`.
* **Expected Result**: Returns `RadarClearGreen`, `RadarThreatAmber`, and `RadarThreatRed`.

### Test Case 4: Conditional Pulsing Animation Gating (`TST-TRK-062.2b`)
* **Scope**: JVM Unit Test (`RadarEdgeOverlayTest.kt`)
* **Procedure**: Verify pulse alpha resolution: when threat level is not `FAST_APPROACHING`, pulse alpha evaluates to static $1.0\text{f}$.
* **Expected Result**: Infinite animation loop is not executed when road is clear or in standard approach.

### Test Case 5: Fail-Safe Disconnect Warning & Vector Icon (`TST-TRK-062.3a`)
* **Scope**: JVM Unit Test (`RadarEdgeOverlayTest.kt`)
* **Procedure**: Evaluate `isFailSafeDisconnect()` with `DISCONNECTED_ERROR` and verify badge color is never `RadarClearGreen`.
* **Expected Result**: Returns `true` for `DISCONNECTED_ERROR`, renders amber hazard color and `ic_device_not_available` icon.

### Test Case 6: Tap-to-Reconnect on Disconnect Error (`TST-TRK-062.3b`)
* **Scope**: JVM Unit Test (`TrackingTabsScreenTest.kt` / `RadarEdgeOverlayTest.kt`)
* **Procedure**: Trigger badge click handler when `radarState.connectionState == RadarConnectionState.DISCONNECTED_ERROR`.
* **Expected Result**: Dispatches `TrainingApplication.REQUEST_START_SEARCH_FOR_PAIRED_DEVICES` broadcast instead of opening `RadarLightQuickSwitchSheet`.

### Test Case 7: AUTO_HIDE_3S Rail Scoping & Status Badge Click Retention (`TST-TRK-062.4a`)
* **Scope**: JVM Unit Test (`RadarEdgeOverlayTest.kt`)
* **Procedure**: Verify layout scoping: auto-hide alpha is applied strictly to `RadarVerticalRail`, while `RadarStatusBadgePill` remains visible and interactive.
* **Expected Result**: Status badge remains visible and interactive when rail is hidden; zero ghost touch interception.

### Test Case 8: DataStore Edge Side & Clear Mode Defaults (`TST-TRK-062.5a`)
* **Scope**: JVM Unit Test (`RadarEdgeOverlayTest.kt`)
* **Procedure**: Assert `TuningPreferencesDefaults.RADAR_EDGE_SIDE` and `RADAR_CLEAR_MODE`.
* **Expected Result**: Defaults to `RadarEdgeSide.RIGHT` and `RadarClearMode.SLIM_RAIL`.

### Test Case 9: 9-Language Localization & Accessibility Parity (`TST-TRK-062.6a`)
* **Scope**: Static Resource Audit (`TranslationParityTest.kt`)
* **Procedure**: Audit all `radar_overlay_*` strings across `values/` and all 8 localized `values-<locale>/` directories.
* **Expected Result**: 100% key and format parameter parity across all 9 canonical languages without missing entries.

### Test Case 10: Clean-Room Radar Test Suite Execution (`TST-TRK-062.7a`)
* **Scope**: Targeted Package Test Execution
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.radar.*"`
* **Expected Result**: 100% pass rate across all radar overlay tests.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-062.1a` | Unit | `RadarOverlayMath.kt` / `RadarEdgeOverlay.kt` | `REQ-TRK-062.1` | Specified |
| `TST-TRK-062.1b` | Unit | `RadarOverlayMath.kt` / `RadarEdgeOverlay.kt` | `REQ-TRK-062.1` | Specified |
| `TST-TRK-062.2a` | Unit | `RadarOverlayMath.kt` | `REQ-TRK-062.2` | Specified |
| `TST-TRK-062.2b` | Unit | `RadarEdgeOverlay.kt` | `REQ-TRK-062.2` | Specified |
| `TST-TRK-062.3a` | Unit | `RadarEdgeOverlay.kt` | `REQ-TRK-062.4` | Specified |
| `TST-TRK-062.3b` | Unit | `TrackingTabsScreen.kt` | `REQ-TRK-062.4` | Specified |
| `TST-TRK-062.4a` | Unit | `RadarEdgeOverlay.kt` | `REQ-TRK-062.3` | Specified |
| `TST-TRK-062.5a` | Unit | `TuningPreferencesDefaults.kt` / `RadarEdgeOverlay.kt` | `REQ-TRK-062.5` | Specified |
| `TST-TRK-062.6a` | Resource | `strings_radar.xml` (9 locales) | `REQ-TRK-062.6` | Specified |
| `TST-TRK-062.7a` | Suite | Full Radar Overlay Test Suite | `REQ-TRK-062.1..6` | Specified |
