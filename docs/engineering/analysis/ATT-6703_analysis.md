# Stage 1: Problem Domain & Root Cause Analysis - ATT-6703: Fix RadarEdgeOverlay inverted bottom-to-top kinematics, AUTO_HIDE_3S ghost tap target, always-on animation, and reconnect action

**Ticket**: [ATT-6703](https://rainerblind.atlassian.net/browse/ATT-6703)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-062.1`, `REQ-TRK-062.2`, `REQ-TRK-062.3`, `REQ-TRK-062.4`, `REQ-TRK-062.5`  
**Test Spec ID**: `TST-TRK-062`  
**Branch**: `feature/ATT-6703`  
**Author**: AI Agent 1 (Analyst - Flash) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Symptoms
Sprint 2026-46.1 review of `RadarEdgeOverlay.kt` and `TrackingTabsScreen.kt` identified four usability, ergonomic, and performance defects:
1. **Inverted Rearview Vertical Axis (`Top -> Bottom` Inversion)**:
   Per standard rearview radar ergonomics (Garmin Varia, Wahoo ELEMNT, Trek CarBack), the cyclist is at the **top** (`0 m`, directly adjacent to `RadarStatusBadgePill`) and approaching vehicles enter from behind at the **bottom** (`140 m`) and travel **upward** towards the top (`y -> 0`) as they close in. Currently, `RadarOverlayMath.normalizeRadarRange(rangeMeters)` evaluates `1.0f - (rangeMeters / 140f)`. At `140 m`, it returns `0.0f` (`y = 0`, top of rail), placing distant vehicles at the cyclist's position and moving them downward like head-on traffic.
2. **`AUTO_HIDE_3S` Hides Status Badge & Leaves Ghost Touch-Stealing Target**:
   Applying `.alpha(overlayAlpha)` (`0f`) to the outer container `Column` hides both the vertical rail and the top status badge (contrary to the specification *"Auto-Hide Rail After 3s Clear"*), while leaving `RadarStatusBadgePill`'s `.clickable` interceptor active as an invisible `24.dp` touch-stealing button over live cockpit tiles and maps.
3. **Unconditional 60 fps `rememberInfiniteTransition` Battery Drain**:
   `rememberInfiniteTransition` runs an active `350 ms` reverse animation at 60 fps continuously throughout the entire ride, even when `overallThreatLevel == ThreatLevel.NONE`. On typical 3–8 hour endurance rides, this creates perpetual GPU composition load and unnecessary battery consumption while the road is clear.
4. **Missing Tap-to-Reconnect, System Nav-Bar Clearance, Vector Icons, Localization & `@Preview`s**:
   - When in `RadarConnectionState.DISCONNECTED_ERROR`, tapping `RadarStatusBadgePill` in `TrackingTabsScreen.kt` opens `RadarLightQuickSwitchSheet` instead of initiating radar peripheral reconnection.
   - The overlay lacks `.navigationBarsPadding()`, causing the bottom rail border to collide with system navigation pill insets (Design Guidelines §5.6 Rule 41).
   - `RadarStatusBadgePill` renders raw text `"!"` instead of vector icons, hardcodes untranslated English strings, leaves the `edgeSide` parameter unused in `RadarEdgeOverlay`, and lacks Jetpack Compose `@Preview`s (Design Guidelines §4).

---

## 2. Forensic Root Cause Analysis & Call Site Audit
1. **Kinematics Projection in `RadarOverlayMath.kt` (`lines 94–97`) & `RadarEdgeOverlay.kt` (`lines 487–494`)**:
   `normalizeRadarRange(rangeMeters)` computes `(1.0f - (rangeMeters / maxRangeMeters))`.
   In Compose layout, `yOffsetPx = (animatedFraction * travelHeightPx).roundToInt()` is anchored at `Alignment.TopCenter`.
   When `range = 140 m`, `fraction = 0.0f`, placing the target dot at `y = 0` (top of screen, adjacent to status badge).
   When `range = 0 m`, `fraction = 1.0f`, placing the target dot at `y = travelHeightPx` (bottom of screen).
   This inverted mapping violates `REQ-TRK-062.1`.
2. **Outer Container Alpha Gating in `RadarEdgeOverlay.kt` (`lines 174–198`)**:
   `overlayAlpha` is applied to the root `Column` enclosing both `RadarStatusBadgePill` and `RadarVerticalRail`.
   When `clearMode == AUTO_HIDE_3S` and the road remains clear for >3 seconds, `overlayAlpha` fades to `0f`. The status badge becomes invisible, depriving the rider of radar connection status, yet retains its `.clickable` modifier, creating an invisible phantom hit target.
3. **Top-Level Animation Allocation in `RadarEdgeOverlay.kt` (`lines 181–190`)**:
   `val infiniteTransition = rememberInfiniteTransition(label = "pulse")` is allocated unconditionally on every composition. Compose schedules continuous frame draws for `pulseAlpha` regardless of whether `overallThreatLevel == ThreatLevel.FAST_APPROACHING`.
4. **Unconditional Sheet Trigger in `TrackingTabsScreen.kt` (`lines 1069–1071`)**:
   `onBadgeClick` unconditionally sets `showRadarLightQuickSwitch = true`. It fails to inspect `radarState.connectionState`, preventing cyclists from tapping the error badge to broadcast `REQUEST_START_SEARCH_FOR_PAIRED_DEVICES`.

---

## 3. Independent Peer Review & Verification (4-Augen-Prinzip)
- Confirmed that changing `normalizeRadarRange` to `(rangeMeters / maxRangeMeters).coerceIn(0.0f, 1.0f)` places `140 m` at `y = travelHeightPx` (bottom) and `0 m` at `y = 0` (top), matching physical Garmin Varia kinematics.
- Confirmed scoping auto-hide alpha strictly to `RadarVerticalRail` ensures `RadarStatusBadgePill` remains permanently visible, legible, and clickable, eliminating phantom hit-box traps.
- Confirmed scoping `rememberInfiniteTransition` to `FAST_APPROACHING` (or returning `1.0f` when clear/amber) halts all animation loops when road is clear, saving significant battery.
- Confirmed wiring `onBadgeClick` in `TrackingTabsScreen.kt` to broadcast `TrainingApplication.REQUEST_START_SEARCH_FOR_PAIRED_DEVICES` on `DISCONNECTED_ERROR` restores connection seamlessly.
- Confirmed adding `.navigationBarsPadding()` provides required system bar inset clearance per Design Guidelines §5.6 Rule 41.

---

## 4. Chesterton's Fence Archaeology & Invariants
- **Original Formulation (`ATT-5085` / commit `671c4c1a`)**:
  `RadarOverlayMath` was authored with `1 - range/maxRange` under the mental model that "0 = origin / zero-distance". However, in UI screen space, `y = 0` is the top of the viewport. Since the rider is at the top, `range = 0 m` corresponds to `y = 0` and `range = 140 m` corresponds to `y = travelHeightPx`.
- **System Invariants**:
  1. Ergonomic consistency: Target dots must enter at the bottom of the rail and move upward toward the rider.
  2. Touch pass-through: Vertical rail and outer container must remain completely transparent to touch events (zero gesture interception for maps or paging).
  3. Fail-safe disconnect indication: Amber hazard indicator on disconnect, strictly forbidding green clear state.
  4. Design system compliance: Material 3 tokens, vector icons, navigation bar padding (Rule 41), and 100% 9-language translation parity.

---

## 5. Scope Bounding & Complexity Evaluation
- **In-Scope**:
  - Update `RadarOverlayMath.normalizeRadarRange()` to map `140 m -> 1.0f` and `0 m -> 0.0f`, updating `RadarEdgeOverlayTest.kt`.
  - Refactor `AUTO_HIDE_3S` in `RadarEdgeOverlay.kt` to auto-hide only `RadarVerticalRail` while keeping `RadarStatusBadgePill` visible.
  - Guard `rememberInfiniteTransition` to animate only when `overallThreatLevel == ThreatLevel.FAST_APPROACHING`.
  - In `TrackingTabsScreen.kt`: wire badge tap on `DISCONNECTED_ERROR` to trigger radar reconnect broadcast.
  - In `RadarEdgeOverlay.kt`: add `.navigationBarsPadding()`, replace text `"!"` with vector icons, clean up unused `edgeSide`, and add Compose `@Preview`s.
  - Expand and update `RadarEdgeOverlayTest.kt`.
- **Out-of-Scope**: Modifying audio chime routing (`ATT-6705`) or scalar sensor bridge (`ATT-6704`).
- **Complexity Estimate**: 2 SP (Standard bugfix and UI overlay polish).
