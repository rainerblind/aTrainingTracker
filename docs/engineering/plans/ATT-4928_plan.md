# Stage 3: Implementation Plan - ATT-4928: Zone Gauge Bar Height Invariance Across Telemetry States

**Ticket**: [ATT-4928](https://rainerblind.atlassian.net/browse/ATT-4928)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-UI-436` (`REQ-UI-436.1`..`REQ-UI-436.5`)  
**Test Mapping**: `TST-UI-436` (`TST-UI-436.1`..`TST-UI-436.5`)  
**Branch**: `feature/ATT-4928`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-11-01  

---

## 1. Problem Description & Background
Sprint Review 2026-44.1 of [ATT-4860] (commit `9d5fd7ff`) identified that `ZoneGaugeBar.kt` dynamically computes `totalBarHeight = activeTrackHeight + 4.dp` based on conditional `activeTrackHeight` (`1.6x` track height when live, base `trackHeight` when stale/null). This causes a 3.6 dp to 13.2 dp jump whenever telemetry connects, disconnects, or pauses. `ATT-4928` eliminates this jump by unconditionally reserving elevated active track height (`1.6x` track height + 4.dp needle overhang) and centering the base track within it, while cleaning up `ATT-4860` leftovers across `ZoneGaugeBar.kt` and `WPrimeBatteryGaugeBar.kt`.

---

## 2. Traceability & Requirements Mapping
* **`REQ-UI-436.1` / `TST-UI-436.1`**: `calculateReservedActiveTrackHeight(trackHeight: Dp) = trackHeight * 1.6f`; outer container height unconditionally reserved as `calculateReservedActiveTrackHeight(trackHeight) + 4.dp`, invariant across all telemetry states.
* **`REQ-UI-436.2` / `TST-UI-436.1`**: Base track (`trackHeight`) vertically centered inside container; stale segments render at base height with 0.35f opacity without container resize.
* **`REQ-UI-436.3` / `TST-UI-436.3`**: Needle and halo overlay span the full reserved container height in both live and stale states.
* **`REQ-UI-436.4` / `TST-UI-436.2`**: Bar-only mode track height scaling across `ViewSize` tiers preserved.
* **`REQ-UI-436.5` / `TST-UI-436.5`**: Clean up dead overload `calculateActiveTrackHeight(baseTrackHeight, isActive)` and commented-out `.height` code; update contract tests.

---

## 3. System Invariants & Preserved Behavior
1. **Layout Height Invariance**: `ZoneGaugeBar` layout height for a given `trackHeight` / `ViewSize` is 100% invariant between live and stale telemetry.
2. **Glance Legibility**: Active segment elevation (`1.6x` height, rounded corners) renders only when data is live (`!isStale && activeZoneIndex != null`).
3. **Sibling Stability**: `PedalPowerBalanceBar` and `WPrimeBatteryGaugeBar` retain strictly invariant heights.
4. **Code Lock (Rule 4)**: Production source code in `app/src/main/...` remains locked until Gate 3 passes.

---

## 4. Proposed Architectural Changes (SWE.2)
* **`ZoneGaugeBar.kt`**:
  * Define `calculateReservedActiveTrackHeight(trackHeight: Dp): Dp = trackHeight * 1.6f`.
  * Define `totalBarHeight = calculateReservedActiveTrackHeight(trackHeight) + 4.dp` unconditionally.
  * Vertically center base track (`height = trackHeight`) inside `BoxWithConstraints(..., contentAlignment = Alignment.Center)`.
  * Render elevated segment pill (`height = calculateReservedActiveTrackHeight(trackHeight)`) only when `!isStale && state.activeZoneIndex != null && state.activeZoneIndex in 1..state.segmentColors.size`.
  * Delete obsolete overload `calculateActiveTrackHeight(baseTrackHeight: Dp, isActive: Boolean): Dp`.
* **`WPrimeBatteryGaugeBar.kt`**:
  * Remove commented-out leftover `// .height(W_PRIME_BATTERY_GAUGE_HEIGHT)` from line 98.
* **UI Consistency (Rule 23)**:
  * Component: `ZoneGaugeBar.kt` and `WPrimeBatteryGaugeBar.kt`.
  * Theme tokens: `MaterialTheme.colorScheme.primary`, `MaterialTheme.colorScheme.outlineVariant`, `TTColor.ZoneGaugeTrack`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`ZoneGaugeBar.kt`)**: Add `calculateReservedActiveTrackHeight(trackHeight: Dp)`, update `totalBarHeight`, center base track, and remove obsolete overload.
2. **Step 2 (`WPrimeBatteryGaugeBar.kt`)**: Remove commented-out leftover code on line 98.
3. **Step 3 (`WPrimeBatteryGaugeBarLayoutTest.kt`)**: Update contract assertion from checking commented-out text to checking `.height(barHeight)`.
4. **Step 4 (`SensorFieldZoneGaugeTest.kt`)**: Replace obsolete overload test with `calculateReservedActiveTrackHeight` test, assert height invariance for live vs stale states, and update source contract checks.
5. **Step 5 (Verification)**: Run targeted test suite and requirement governance check.

---

## 6. Verification & Rollback Plan
* **Targeted Verification**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldZoneGaugeTest" --tests "com.atrainingtracker.trainingtracker.ui.components.WPrimeBatteryGaugeBarLayoutTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  * `python3 tools/verify_requirement_governance.py`
* **Rollback Plan**: Revert changes via git on `feature/ATT-4928` prior to squash-merge.
