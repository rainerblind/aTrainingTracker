# Stage 3: Implementation Plan - ATT-6703: Fix RadarEdgeOverlay inverted bottom-to-top kinematics, AUTO_HIDE_3S ghost tap target, always-on animation, and reconnect action

**Ticket**: [ATT-6703](https://rainerblind.atlassian.net/browse/ATT-6703)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-062.1`, `REQ-TRK-062.2`, `REQ-TRK-062.3`, `REQ-TRK-062.4`, `REQ-TRK-062.5`, `REQ-TRK-062.6`  
**Test Spec Mapping**: `TST-TRK-062.1a`, `TST-TRK-062.1b`, `TST-TRK-062.2a`, `TST-TRK-062.2b`, `TST-TRK-062.3a`, `TST-TRK-062.3b`, `TST-TRK-062.4a`, `TST-TRK-062.5a`, `TST-TRK-062.6a`, `TST-TRK-062.7a`  
**Branch**: `feature/ATT-6703`  
**Author**: AI Agent 1 (Implementer - Pro) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
Sprint 2026-46.1 review of `RadarEdgeOverlay.kt` and `TrackingTabsScreen.kt` identified four usability, kinematic, and lifecycle bugs:
1. `RadarOverlayMath.normalizeRadarRange(140f)` returns `0.0f`, placing 140m at $y = 0$ (top of rail) and moving approaching vehicles downwards like oncoming traffic instead of upward toward the rider.
2. `AUTO_HIDE_3S` fades the outer `Column`, hiding the top status badge while leaving an invisible 24dp `.clickable` interceptor that steals touches over cockpit grids and maps.
3. `rememberInfiniteTransition` runs continuously at 60 fps throughout the entire ride even when the road is clear (`ThreatLevel.NONE`), wasting battery.
4. Badge click during `DISCONNECTED_ERROR` opens the quick-switch sheet instead of triggering radar reconnect; uses text `"!"` instead of vector icons; lacks `.navigationBarsPadding()`; and has zero `@Preview`s.

---

## 2. Traceability & Requirements Mapping
* **REQ-TRK-062.1 (Upward Vertical Rail Kinematics & Dot Interpolation)**: Invert `normalizeRadarRange` to $(d / d_{\max})\text{.coerceIn(0.0f, 1.0f)}$ so vehicles travel upward ($y \to 0$) toward the cyclist -> `TST-TRK-062.1a`, `TST-TRK-062.1b`
* **REQ-TRK-062.2 (Conditional Pulsing Alert Scoping)**: Compose and evaluate pulsing alert animation only when `overallThreatLevel == ThreatLevel.FAST_APPROACHING`; evaluate static `1.0f` otherwise -> `TST-TRK-062.2b`
* **REQ-TRK-062.3 (Rail-Only Auto-Hide & Ghost Click Elimination)**: Scope auto-hide alpha to `RadarVerticalRail`; keep `RadarStatusBadgePill` visible, interactive, and click-safe -> `TST-TRK-062.4a`
* **REQ-TRK-062.4 (Fail-Safe Disconnect, Vector Icons & Tap-to-Reconnect)**: Wire badge tap in `DISCONNECTED_ERROR` to `controlViewModel.onSearchClicked()` / search broadcast; render `ic_device_not_available` icon -> `TST-TRK-062.3a`, `TST-TRK-062.3b`
* **REQ-TRK-062.5 (System Nav Clearance & Compose Previews)**: Add `.navigationBarsPadding()` (Rule 41) and Light/Dark/AMOLED `@Preview` composables -> `TST-TRK-062.5a`, `TST-TRK-062.7a`
* **REQ-TRK-062.6 (9-Language Parity)**: Maintain 100% key and parameter parity across all 9 localized string resource shards -> `TST-TRK-062.6a`

---

## 3. System Invariants & Preserved Behavior
1. **Touch Pass-Through**: The vertical rail container must NEVER intercept pointer touches, preserving `HorizontalPager` and map gestures.
2. **Fail-Safe Disconnect**: `DISCONNECTED_ERROR` must NEVER render green "All Clear".
3. **Ergonomic Directionality**: Approaching traffic always enters from the bottom of the display and moves upward.
4. **Design System & Localization Parity**: Material 3 tokens, vector drawables, Rule 41 insets, and 100% 9-language translation parity.

---

## 4. UI Consistency (Design Guidelines Section 5 & Rule 38)
* **Reference Component**: Material 3 edge rail in `RadarEdgeOverlay.kt` adjacent to `TrackingTabsScreen.kt`.
* **Styling & Theme Tokens**: `TTColor.getRadarThreatColor()`, `TTColor.RadarRailTrack`, `ic_device_not_available` vector drawable, `.navigationBarsPadding()`.
* **Previews**: Add `@Preview(name = "Light Mode")`, `@Preview(name = "Dark Mode")`, `@Preview(name = "AMOLED Black")` with standard `TTTheme` preview wrapper.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Invert Kinematics Projection & Max Range Guard (`RadarOverlayMath.kt`)
* Update `normalizeRadarRange(rangeMeters, maxRangeMeters)` to return `(rangeMeters / maxRangeMeters).coerceIn(0.0f, 1.0f)`.
* When `maxRangeMeters <= 0f`, return `0.0f`.
* Update `RadarEdgeOverlayTest.kt` assertions ($140\text{ m} \to 1.0\text{f}$, $70\text{ m} \to 0.5\text{f}$, $0\text{ m} \to 0.0\text{f}$).

### Step 2: Scope AUTO_HIDE_3S Alpha Strictly to Vertical Rail (`RadarEdgeOverlay.kt`)
* Remove `.alpha(overlayAlpha)` from the root `Column` in `RadarEdgeOverlay`.
* Apply `railAlpha` strictly to `RadarVerticalRail` inside `showRail` condition.
* Keep `RadarStatusBadgePill` permanently visible and interactive at the top of the edge overlay.

### Step 3: Guard rememberInfiniteTransition to FAST_APPROACHING (`RadarEdgeOverlay.kt`)
* Guard animation evaluation: if `overallThreatLevel == ThreatLevel.FAST_APPROACHING`, compose `rememberInfiniteTransition` and animate pulse alpha ($0.4\text{f} \leftrightarrow 0.95\text{f}$).
* Otherwise, evaluate static `1.0f`, executing 0 animation frames when the road is clear.

### Step 4: Wire Tap-to-Reconnect, Vector Icons, Navigation Inset & Previews
* In `RadarEdgeOverlay.kt`: add optional `onReconnectClick: (() -> Unit)? = null` parameter; route badge clicks in `DISCONNECTED_ERROR` to `onReconnectClick ?: onClick`.
* In `RadarStatusBadgePill`: render `ic_device_not_available` icon for `DISCONNECTED_ERROR` instead of text `"!"`.
* In `TrackingTabsScreen.kt`: pass `onReconnectClick = { controlViewModel.onSearchClicked() }` to `RadarEdgeOverlay`.
* Add `.navigationBarsPadding()` to the root overlay layout.
* Add Light/Dark/AMOLED `@Preview` composables.

### Step 5: Test Suite Expansion & Clean-Room Verification
* Update unit tests in `RadarEdgeOverlayTest.kt` verifying inverted kinematics, rail-only auto-hide, conditional pulse alpha, and reconnect callback.
* Run targeted tests and translation parity tests under `/tmp/att_build_merge.lock`.

---

## 6. Verification & Rollback Plan
* **Verification Command**:
  `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.radar.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
* **Rollback Plan**: `git checkout feature/ATT-6703` to restore clean state.
