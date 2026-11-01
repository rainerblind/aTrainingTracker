# Stage 2: Requirement & Test Specification - ATT-4928: Zone gauge bar layout height invariance

**Ticket**: [ATT-4928](https://rainerblind.atlassian.net/browse/ATT-4928) | **Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41` | **Active Sprint**: `2026-44.2` | **Branch**: `feature/ATT-4928`  
**Requirement Mapping**: `REQ-UI-436` (*Active Training Zone Segment Height Emphasis & Bar-Only Sizing*)  
**Test Spec ID**: `TST-UI-436` | **Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-01  

---

## 1. Requirement Specification (REQ-UI-436)
### 1.1 Problem Statement & Rationale
In `ZoneGaugeBar.kt`, `totalBarHeight` dynamically fluctuates between `trackHeight * 1.6f + 4.dp` (live) and `trackHeight + 4.dp` (stale/null). This 3.6 dp to 13.2 dp jump causes sensor tiles and neighboring rows to jump whenever telemetry connects, disconnects, or pauses.

### 1.2 Functional & Architectural Requirements
* `REQ-UI-436.1`: `ZoneGaugeBar` outer container height (`totalBarHeight`) SHALL unconditionally reserve elevated track height plus needle overhang (`calculateReservedActiveTrackHeight(trackHeight) + 4.dp`, where `calculateReservedActiveTrackHeight(trackHeight) = trackHeight * 1.6f`), strictly invariant of telemetry staleness or active zone state. Base track (`trackHeight`) is centered vertically within the container.
* `REQ-UI-436.2`: Stale/null telemetry SHALL render all segments at uniform base `trackHeight` with `0.35f` opacity without changing `totalBarHeight`.
* `REQ-UI-436.3`: Needle overlay and halo SHALL span full reserved container height (`calculateReservedActiveTrackHeight(trackHeight) + 4.dp`).
* `REQ-UI-436.4`: Bar-only mode track height scaling across `ViewSize` tiers SHALL remain preserved.
* `REQ-UI-436.5`: `PedalPowerBalanceBar` and `WPrimeBatteryGaugeBar` SHALL remain height-invariant. Obsolete overload `calculateActiveTrackHeight(baseTrackHeight, isActive)` and commented-out `.height` code SHALL be removed.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* `ZoneGaugeBar` with `trackHeight = 6.dp` in live state (`needleFraction = 0.5f, activeZoneIndex = 3`),
  * *When* telemetry becomes stale (`needleFraction = null, activeZoneIndex = null`),
  * *Then* the measured outer container height remains 13.6 dp (`6.dp * 1.6f + 4.dp`) with zero layout jump.
* *Given* `ZoneGaugeBar` in bar-only XL mode (`trackHeight = 22.dp`),
  * *When* switching between live and stale telemetry,
  * *Then* outer container height evaluates to 39.2 dp (`22.dp * 1.6f + 4.dp`) in both states.

### 1.4 System Invariants
Zero sensor tile layout jump on telemetry transitions, True-Black AMOLED compliance, 100% test pass rate.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-UI-436.1`, amending `REQ-UI-376`, `REQ-UI-390`, and `REQ-TRK-046`. Targets `ZoneGaugeBar.kt`, `WPrimeBatteryGaugeBar.kt`, and `SensorFieldZoneGaugeTest.kt`.
2. *Historical Origin & Commit Trace*: `ATT-4860` (commit `9d5fd7ff`) introduced $1.6\times$ active zone elevation, but coupled `totalBarHeight` to live state.
3. *Root Reason for Existing Formulation*: Accommodated elevated segment without clipping, but collapsed container height when inactive.
4. *Preservation of Core Invariants*: Visual active zone prominence ($1.6\times$ elevation, rounded corners) when live, dimming (`0.35f` alpha) when stale, proportional segment weights, and AMOLED compliance are 100% preserved.

---

## 3. Test Specification (TST-UI-436)
* **TST-UI-436.1 (Unit)**: `calculateReservedActiveTrackHeight(trackHeight) == trackHeight * 1.6f`; measured container height is identical for live vs stale/null across `trackHeight` values (`6.dp`, `10.dp`, `14.dp`, `18.dp`, `22.dp`).
* **TST-UI-436.2 (Unit)**: `resolveZoneGaugeTrackHeight`, `resolvePedalBalanceTrackHeight`, `resolveWPrimeBatteryHeight` scale with `ViewSize` in bar-only mode.
* **TST-UI-436.3 (Contract)**: Needle spans container height; `PedalPowerBalanceBar` and `WPrimeBatteryGaugeBar` have fixed container heights independent of staleness.
* **TST-UI-436.4 (Regression)**: `./gradlew testDebugUnitTest --tests "*SensorFieldZoneGaugeTest*" --tests "*WPrimeBatteryGaugeBarLayoutTest*" --tests "*ThemeColorCentralizationAuditTest*"` passes 100%.
* **TST-UI-436.5 (Hygiene)**: Removal of unused `calculateActiveTrackHeight` overload; `WPrimeBatteryGaugeBarLayoutTest` verifies `.height(barHeight)` without dead comments.

---

## 4. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-436.1` | Unit | `ZoneGaugeBar.kt` | `REQ-UI-436.1`, `REQ-UI-436.2` | Specified |
| `TST-UI-436.2` | Unit | `SensorFieldView.kt` | `REQ-UI-436.4` | Specified |
| `TST-UI-436.3` | Contract | `ZoneGaugeBar.kt`, `PedalPowerBalanceBar.kt` | `REQ-UI-436.3`, `REQ-UI-436.5` | Specified |
| `TST-UI-436.4` | Regression | Tier 1 test suite | `REQ-PRO-001` | Specified |
| `TST-UI-436.5` | Hygiene | `ZoneGaugeBar.kt`, `WPrimeBatteryGaugeBar.kt` | `REQ-UI-436.5` | Specified |
