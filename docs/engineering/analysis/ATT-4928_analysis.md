# Stage 1 Analysis: ATT-4928 - Zone Gauge Bar Height Invariance Across Telemetry States

**Ticket**: [ATT-4928](https://rainerblind.atlassian.net/browse/ATT-4928)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4928`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Motivation
Sprint Review 2026-44.1 of [ATT-4860] (Zone bar active zone highlight, commit `9d5fd7ff`) identified a layout jump in sensor tiles:
* In `ZoneGaugeBar.kt`, `totalBarHeight = activeTrackHeight + 4.dp` uses `calculateActiveTrackHeight(...)`, returning `trackHeight * 1.6f` when live, but dropping to `trackHeight` when stale or `activeZoneIndex == null`.
* The resulting height difference of `0.6 x trackHeight` (3.6 dp in normal mode, up to 13.2 dp in bar-only XL mode) causes the zone bar, its parent tile, neighboring row tiles, and subsequent sensor grid rows to visibly jump whenever telemetry connects, drops, or pauses.

---

## 2. Root Cause Analysis & Independent Peer Review (4-Augen-Prinzip)
Independent audit of `ZoneGaugeBar.kt`, `PedalPowerBalanceBar.kt`, and `WPrimeBatteryGaugeBar.kt` confirms:
1. **Dynamic Container Height Coupling**: In `ZoneGaugeBar.kt` (lines 88-94), `totalBarHeight` dynamically fluctuates because `activeTrackHeight` is conditioned on `!isStale && activeZoneIndex != null`.
2. **Height Reservation Invariant**: By unconditionally reserving `calculateReservedActiveTrackHeight(trackHeight) = trackHeight * 1.6f` and fixing `totalBarHeight = calculateReservedActiveTrackHeight(trackHeight) + 4.dp`, the outer container bounding box remains strictly constant. The base track (`height = trackHeight`) is centered inside `BoxWithConstraints(contentAlignment = Alignment.Center)`, while the elevated pill (`1.6x` height) renders only when active and live without altering outer dimensions.
3. **Sibling Component Audit**:
   * `PedalPowerBalanceBar.kt`: `totalBarHeight = trackHeight + 4.dp` is invariant of staleness or deviation. No jump defect exists.
   * `WPrimeBatteryGaugeBar.kt`: Container uses `barHeight: Dp = W_PRIME_BATTERY_GAUGE_HEIGHT` (14.dp), invariant across states.
4. **ATT-4860 Leftovers**:
   * Unused overload `calculateActiveTrackHeight(baseTrackHeight: Dp, isActive: Boolean)` in `ZoneGaugeBar.kt` (line 248).
   * Commented-out leftover `.height(barHeight) // .height(W_PRIME_BATTERY_GAUGE_HEIGHT)` in `WPrimeBatteryGaugeBar.kt` (line 98).
   * Strict string assertion in `WPrimeBatteryGaugeBarLayoutTest.kt` (line 65) requiring alignment to `barHeight`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Formalize `calculateReservedActiveTrackHeight(trackHeight: Dp): Dp = trackHeight * 1.6f` in `ZoneGaugeBar.kt`.
  * Unconditionally reserve `totalBarHeight = calculateReservedActiveTrackHeight(trackHeight) + 4.dp`, centering base track and conditional elevated pill.
  * Remove unused `calculateActiveTrackHeight(baseTrackHeight: Dp, isActive: Boolean)` overload.
  * Remove commented-out `// .height(W_PRIME_BATTERY_GAUGE_HEIGHT)` in `WPrimeBatteryGaugeBar.kt` and update layout contract test.
  * Unit tests in `SensorFieldZoneGaugeTest.kt` verifying identical measured bar heights for stale vs live across `ViewSize` and bar-only modes.
* **Out-of-Scope Non-Goals**:
  * Modifying visual appearance, color palettes, needle indicators, or under-segment badges.
  * Changing `ZoneGaugeMath` cutoff, weight, or linear fraction calculation algorithms.
  * Redesigning `SensorFieldView` layout architecture or grid reflow behavior.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-436.1` (*Active Zone Segment Elevation & Layout Height Invariance*), amending `REQ-UI-376`, `REQ-UI-390`, and `REQ-TRK-046`. Targets `ZoneGaugeBar.kt`, `WPrimeBatteryGaugeBar.kt`, and `SensorFieldZoneGaugeTest.kt`.
* **Historical Origin & Commit Trace**: Commit `9d5fd7ff` (`ATT-4860`) introduced $1.6\times$ active zone elevation and dynamic sizing across `ViewSizes`, but erroneously coupled `totalBarHeight` to live state.
* **Root Reason for Existing Formulation**: `ATT-4860` accommodated the elevated segment without clipping, but collapsed container height when inactive under the false assumption that stale bars should shrink.
* **Preservation of Core Invariants**: Visual active zone prominence ($1.6\times$ elevation, rounded corners) when live, dimming (`0.35f` alpha) when stale, proportional segment weights, and AMOLED compliance are 100% preserved.

---

## 5. Architectural Strategy & Technical Design
1. **Unconditional Height Reservation (`ZoneGaugeBar.kt`)**: Define `calculateReservedActiveTrackHeight(trackHeight: Dp): Dp = trackHeight * 1.6f`. Set `totalBarHeight = calculateReservedActiveTrackHeight(trackHeight) + 4.dp` unconditionally.
2. **Conditional Segment Rendering (`ZoneGaugeBar.kt`)**: Render elevated active zone pill (`height = calculateReservedActiveTrackHeight(trackHeight)`) only when `!isStale && activeZoneIndex != null && activeZoneIndex in 1..segmentColors.size`, centered vertically over the base track.
3. **Dead Code & Comment Cleanup**: Remove unused `calculateActiveTrackHeight(baseTrackHeight, isActive)` in `ZoneGaugeBar.kt` and remove commented code in `WPrimeBatteryGaugeBar.kt`.
4. **Contract & Parity Tests**: Update `SensorFieldZoneGaugeTest.kt` and `WPrimeBatteryGaugeBarLayoutTest.kt` to assert height invariance between `needleFraction == null` and live values across `ViewSize` configurations.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Layout height for any given `trackHeight` / `ViewSize` / bar-only mode is 100% identical between live and stale/null telemetry.
  2. Elevated active zone segment remains glance-legible with $1.6\times$ height only when data is live.
  3. Zero regressions in `PedalPowerBalanceBar` and `WPrimeBatteryGaugeBar`.
* **Risk Rating**: **LOW** — Pure layout dimension constant reservation; zero threading, SQLite, or lifecycle changes.
