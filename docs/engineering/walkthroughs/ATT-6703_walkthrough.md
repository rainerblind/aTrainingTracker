# Stage 4/5 Walkthrough: ATT-6703 - Fix RadarEdgeOverlay inverted bottom-to-top kinematics, AUTO_HIDE_3S ghost tap target, always-on animation, and reconnect action

**Ticket**: [ATT-6703](https://rainerblind.atlassian.net/browse/ATT-6703)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-062.1`, `REQ-TRK-062.2`, `REQ-TRK-062.3`, `REQ-TRK-062.4`, `REQ-TRK-062.5`, `REQ-TRK-062.6`  
**Test Spec Mapping**: `TST-TRK-062.1a`, `TST-TRK-062.1b`, `TST-TRK-062.2a`, `TST-TRK-062.2b`, `TST-TRK-062.3a`, `TST-TRK-062.3b`, `TST-TRK-062.4a`, `TST-TRK-062.5a`, `TST-TRK-062.6a`, `TST-TRK-062.7a`  
**Author**: AI Agent 1 (Flash) | **Date**: 2026-11-11  

---

## 1. Problem & Scope Overview
Sprint 2026-46.1 review of `RadarEdgeOverlay.kt` and `TrackingTabsScreen.kt` identified four ergonomic, usability, and lifecycle defects:
1. **Inverted Rearview Vertical Axis**: `RadarOverlayMath.normalizeRadarRange(140f)` returned `0.0f`, placing $140\text{ m}$ at $y = 0$ (top of rail, adjacent to the rider's status badge) and moving approaching vehicles downward like oncoming head-on traffic.
2. **`AUTO_HIDE_3S` Hides Status Badge & Leaves Ghost Touch Target**: Applying `.alpha(overlayAlpha)` to the outer `Column` hid both the rail and `RadarStatusBadgePill` after 3s of all-clear road, depriving the cyclist of radar connection status while leaving `RadarStatusBadgePill`'s `.clickable` interceptor active as an invisible $24\text{ dp}$ phantom touch target over cockpit tiles and maps.
3. **Unconditional 60 fps `rememberInfiniteTransition` Battery Drain**: `rememberInfiniteTransition` ran continuously at 60 fps throughout the entire ride even when `overallThreatLevel == ThreatLevel.NONE`, wasting battery.
4. **Missing Tap-to-Reconnect, Navigation Inset, Vector Icons & Previews**: Tapping the badge in `DISCONNECTED_ERROR` opened `RadarLightQuickSwitchSheet` instead of triggering reconnect; the rail lacked `.navigationBarsPadding()` (Rule 41); the badge rendered plain text `"!"` and an $8\text{ dp}$ dot instead of vector icons; and Compose `@Preview`s were absent.

---

## 2. Key Changes Implemented

1. **Upward Kinematics Inversion (`RadarOverlayMath.kt` & `RadarEdgeOverlay.kt`)**:
   - Inverted `RadarOverlayMath.normalizeRadarRange(rangeMeters, maxRangeMeters)` to return `(rangeMeters / maxRangeMeters).coerceIn(0.0f, 1.0f)`.
   - When `maxRangeMeters <= 0f`, safely returns `0.0f` without division by zero.
   - Maps $140\text{ m} \to 1.0\text{f}$ (bottom of rail, entry from behind) and $0\text{ m} \to 0.0\text{f}$ (top of rail, cyclist position). Approaching vehicles travel smoothly upward toward the rider ($y \to 0$) as distance decreases, fully matching standard Garmin Varia kinematics (`REQ-TRK-062.1`).

2. **Rail-Only Auto-Hide & Ghost Click Elimination (`RadarEdgeOverlay.kt`)**:
   - Removed `.alpha(overlayAlpha)` from the root `Column`.
   - Scoped `railAlpha` strictly to `RadarVerticalRail` inside `showRail && railAlpha > 0f`.
   - Kept `RadarStatusBadgePill` permanently visible at full opacity (`1.0f`) and interactive at the top of the edge rail, completely eliminating phantom touch targets over cockpit grids and live maps (`REQ-TRK-062.3`).

3. **Conditional Pulsing Alert Scoping (`RadarEdgeOverlay.kt`)**:
   - Guarded `rememberInfiniteTransition`: only evaluated and composed when `overallThreatLevel == ThreatLevel.FAST_APPROACHING`.
   - When the road is clear or in standard approach, evaluates static `1.0f` alpha, executing 0 animation frames and eliminating background battery drain (`REQ-TRK-062.2`).

4. **1-Tap Reconnect, Vector Drawables, Inset Padding & Directional Styling (`TrackingTabsScreen.kt` & `RadarEdgeOverlay.kt`)**:
   - Added `onReconnectClick: (() -> Unit)? = null` parameter to `RadarEdgeOverlay`.
   - Routed badge click in `DISCONNECTED_ERROR` to `onReconnectClick ?: onBadgeClick`.
   - In `TrackingTabsScreen.kt`, wired `onReconnectClick` to broadcast `TrainingApplication.REQUEST_START_SEARCH_FOR_PAIRED_DEVICES`.
   - Replaced plain text `"!"` with `ic_device_not_available` vector drawable on disconnect badge, and replaced bare white dot with `ic_device_available` vector drawable on clear badge (`REQ-TRK-062.4`).
   - Added `.navigationBarsPadding()` to root overlay layout per Design Guidelines §5.6 Rule 41 (`REQ-TRK-062.5`).
   - Utilized `edgeSide` for directional track border rounding (`RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)` on right edge; `topEnd / bottomEnd` on left edge).

5. **Jetpack Compose Previews & 9-Language Localization**:
   - Added `@Preview` composables for `Clear Light`, `Approaching Dark`, `Fast Threat AMOLED`, and `Disconnected` states (Design Guidelines §4).
   - Added localized format template support to `RadarOverlayMath.buildThreatDescription()`.
   - Preserved 100% key, parameter, and translation parity across all 9 canonical languages (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) (`REQ-TRK-062.6`).

6. **Unit Test Suite Expansion (`RadarEdgeOverlayTest.kt`)**:
   - Updated kinematics assertions ($140\text{ m} \to 1.0$, $70\text{ m} \to 0.5$, $0\text{ m} \to 0.0$, boundary clamping, degenerate max range).
   - Added `buildThreatDescription_formatsWithLocalizedTemplate`.
   - Added `badgeClick_inDisconnectedError_routesToReconnectWhenAvailable`.

---

## 3. Verification & Test Evidence

| Test Suite | Scope | Target | Result |
| :--- | :--- | :--- | :--- |
| `RadarEdgeOverlayTest` | Unit | Upward kinematics, boundary clamping, threat colors, fail-safe disconnect, localized templates, tap-to-reconnect | **PASS** (16/16 tests) |
| `TranslationParityTest` | Localization | 9-locale key, placeholder and format parity audit (`strings_radar.xml`) | **PASS** (4/4 tests) |
| `verify_requirement_governance.py` | Governance | Living doc master table sync, Chesterton's Fence archaeology audit | **PASS** (100% parity) |

---

## 4. Conclusion & Next Steps
All requirements for `REQ-TRK-062.1`, `REQ-TRK-062.2`, `REQ-TRK-062.3`, `REQ-TRK-062.4`, `REQ-TRK-062.5`, and `REQ-TRK-062.6` have been implemented, tested, and verified.
Submitting Stage 4 Construction for Gate 4 Review by agent2 (`subagent_pro`), parent advancement to `Test`, and fused Stage 5 Verification.

---

## 5. Cold Carbon Review (Stage 6) Defect Resolution & Healing
* **Finding**: The Stage 3 implementation plan artifact was mistakenly named `docs/engineering/plans/ATT-6703_impl_plan.md` instead of the canonical naming pattern `docs/engineering/plans/ATT-6703_plan.md`.
* **Resolution**: The plan file was canonically renamed to `docs/engineering/plans/ATT-6703_plan.md` in commit `ee4b955c`, restoring full conformance with the agile ASPICE naming conventions. All requirements, kinematics, and test assertions verified with 100% test pass.
