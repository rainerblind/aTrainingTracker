# Stage 5: Walkthrough & Verification - ATT-4928: Zone Gauge Bar Height Invariance Across Telemetry States

**Ticket**: [ATT-4928](https://rainerblind.atlassian.net/browse/ATT-4928)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-UI-436` (`REQ-UI-436.1`..`REQ-UI-436.5`)  
**Test Mapping**: `TST-UI-436` (`TST-UI-436.1`..`TST-UI-436.5`)  
**Branch**: `feature/ATT-4928`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-11-01  

---

## 1. Executive Summary & Verification Overview
ATT-4928 resolves the sensor tile height jump occurring when telemetry transitions between live and stale/null states. In `ZoneGaugeBar.kt`, outer container height `totalBarHeight` now unconditionally reserves the elevated active track height plus needle overhang (`calculateReservedActiveTrackHeight(trackHeight) + 4.dp`, where `calculateReservedActiveTrackHeight(trackHeight) = trackHeight * 1.6f`), strictly invariant across all telemetry states. The base segmented track (`height = trackHeight`) is centered vertically within the container, and the elevated active zone pill renders only when data is live and active. ATT-4860 leftovers (unused `calculateActiveTrackHeight(baseTrackHeight, isActive)` overload in `ZoneGaugeBar.kt` and commented-out `.height` code in `WPrimeBatteryGaugeBar.kt`) were removed, and contract tests were updated.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Component | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-436.1` | `TST-UI-436.1` | `SensorFieldZoneGaugeTest.testCalculateActiveTrackHeight_elevatesActiveZone_andKeepsInactiveUniform` | **PASSED** | `Verified` |
| `REQ-UI-436.2` | `TST-UI-436.1` | `SensorFieldZoneGaugeTest` (stale/null height invariance & 0.35f alpha) | **PASSED** | `Verified` |
| `REQ-UI-436.3` | `TST-UI-436.3` | `SensorFieldZoneGaugeTest.testZoneGaugeBar_sourceContainsActiveZoneElevationAndContainerHeight` | **PASSED** | `Verified` |
| `REQ-UI-436.4` | `TST-UI-436.2` | `SensorFieldZoneGaugeTest.testResolveBarOnlyTrackHeights_scalesWithViewSize` | **PASSED** | `Verified` |
| `REQ-UI-436.5` | `TST-UI-436.5` | `WPrimeBatteryGaugeBarLayoutTest` & `SensorFieldZoneGaugeTest` source hygiene | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Verification Suite (Rule 60)
* **Explicit Test Scope**:
  - `com.atrainingtracker.trainingtracker.ui.tracking.*` (including `SensorFieldZoneGaugeTest`, `SensorFieldViewLayoutTest`, `BikePowerCockpitDefaultsAndGatingTest`)
  - `com.atrainingtracker.trainingtracker.ui.components.WPrimeBatteryGaugeBarLayoutTest`
  - `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`
  - `com.atrainingtracker.trainingtracker.build.*`
* **Execution**: `./gradlew testDebugUnitTest --tests "..."`
* **Result**: **BUILD SUCCESSFUL in 31s** (416 tests completed, 0 failures, 100% pass rate).
* **Requirement Governance**: `python3 tools/verify_requirement_governance.py` passed with exit code 0 (`PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields`).
* **ADB On-Device Verification**: `adb devices` reported no attached devices; verified via automated layout contract and unit tests.

---

## 4. Invariant & Governance Verification
1. **Container Height Invariance**: `totalBarHeight` is strictly identical between live and stale/null telemetry for any given `trackHeight` / `ViewSize`, eliminating tile jumps.
2. **Glance Legibility**: Active zone pill renders at $1.6\times$ `trackHeight` with rounded corners only when telemetry is live.
3. **Sibling Stability**: `PedalPowerBalanceBar` and `WPrimeBatteryGaugeBar` confirmed height-invariant.
4. **Living Documentation**: `REQ-UI-436` and `TST-UI-436` updated to `Verified`.
