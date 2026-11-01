# Stage 3: Implementation Plan - ATT-4860: [Feature] Zone bar: highlight current zone

**Ticket**: [ATT-4860](https://rainerblind.atlassian.net/browse/ATT-4860)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Active Sprint**: `2026-44.1`  
**Requirement Mapping**: `REQ-UI-436`  
**Test Mapping**: `TST-UI-436`  
**Author**: AI Agent 1 (Software Architect)  
**Date**: 2026-11-01  

---

## 1. Architectural Decomposition (SWE.2)

- **UI Component Layer (`ZoneGaugeBar.kt`)**:
  - Compute active segment height: `val activeTrackHeight = if (!isStale && state.activeZoneIndex != null) trackHeight * 1.6f else trackHeight`.
  - Outer container Box height: `val totalBarHeight = activeTrackHeight + 4.dp` (providing 2dp top/bottom overhang for needle).
  - Track row: `Row(modifier = Modifier.fillMaxWidth().height(activeTrackHeight), verticalAlignment = Alignment.CenterVertically)`.
  - Each segment rendered in Box with weight `segmentWeight`:
    - If active (`index == state.activeZoneIndex - 1`): height `activeTrackHeight`, corner radius `RoundedCornerShape(activeTrackHeight / 4)`.
    - If inactive: height `trackHeight`, corners rounded for boundary ends (`topStart/bottomStart` for index 0, `topEnd/bottomEnd` for last index) or `0.dp`.
    - Telemetry stale (`activeZoneIndex == null`): uniform base `trackHeight` with `0.35f` opacity.
  - Needle overlay spans `totalBarHeight` centered on `clampedCenterX`.

- **Telemetry Bar Sizing Layer (`SensorFieldView.kt`, `WPrimeBatteryGaugeBar.kt`, `PedalPowerBalanceBar.kt`)**:
  - Parameterize `WPrimeBatteryGaugeBar` with `barHeight: Dp = W_PRIME_BATTERY_GAUGE_HEIGHT`.
  - In `SensorFieldView.kt`, when in bar-only mode (`!fieldState.showValue && fieldState.showBar`):
    - `ZoneGaugeBar`: `SMALL = 10.dp`, `NORMAL = 14.dp`, `LARGE = 18.dp`, `EXTRA_LARGE = 22.dp` (vs standard `4.dp`, `6.dp`, `8.dp`, `10.dp`).
    - `PedalPowerBalanceBar`: `SMALL = 12.dp`, `NORMAL = 16.dp`, `LARGE = 20.dp`, `EXTRA_LARGE = 24.dp` (vs standard `6.dp`, `8.dp`, `10.dp`, `12.dp`).
    - `WPrimeBatteryGaugeBar`: `SMALL = 14.dp`, `NORMAL = 20.dp`, `LARGE = 26.dp`, `EXTRA_LARGE = 32.dp`.

- **Test Suite (`SensorFieldZoneGaugeTest.kt`)**:
  - Add unit tests verifying active zone segment elevation math, stale uniform height, and bar-only sizing resolution across all `ViewSize` tiers.

---

## 2. Atomic Implementation Steps

| Step | Action | Target File(s) | Verification / Test |
| :--- | :--- | :--- | :--- |
| **Step 1** | Support `barHeight` parameter in `WPrimeBatteryGaugeBar` | `WPrimeBatteryGaugeBar.kt` | Compilation check |
| **Step 2** | Implement active zone elevation and needle span in `ZoneGaugeBar` | `ZoneGaugeBar.kt` | `SensorFieldZoneGaugeTest` |
| **Step 3** | Implement dynamic bar height resolution in `SensorFieldView` | `SensorFieldView.kt` | `SensorFieldZoneGaugeTest` |
| **Step 4** | Add unit tests for `TST-UI-436.1`..`TST-UI-436.3` | `SensorFieldZoneGaugeTest.kt` | `SensorFieldZoneGaugeTest` |
| **Step 5** | Clean-room regression & theme audit | Whole project | `./gradlew testDebugUnitTest` |

---

## 3. UI Consistency & Invariants

- **UI Consistency**: Adheres to Material 3 tokens, AMOLED True-Black contrast guidelines, and existing `ZoneGaugeBar` color specs.
- **Invariants**: 100% backward compatibility for standard dual-element mode (`showValue = true, showBar = true`), stale alpha preservation, and zero regression in layout sizing.
