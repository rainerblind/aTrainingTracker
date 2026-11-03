# Test Specification - ATT-4860

## 1. Overview & Scope
- **Ticket**: ATT-4860 ([Feature] Zone bar: highlight current zone)
- **Requirement**: `REQ-UI-436` (Active Training Zone Segment Height Emphasis & Bar-Only Sizing)
- **Living Test Spec**: `TST-UI-436` in `docs/tests/tst_ui_design_system.md`
- **Target Files**: `ZoneGaugeBar.kt`, `SensorFieldView.kt`, `PedalPowerBalanceBar.kt`, `WPrimeBatteryGaugeBar.kt`, `SensorFieldZoneGaugeTest.kt`

## 2. Test Cases (TST-UI-436)

### TST-UI-436.1: Active Segment Height Elevation & Inactive Base Height
- **Component**: `SensorFieldZoneGaugeTest.kt` / `ZoneGaugeBar.kt`
- **Objective**: Verify that when `state.activeZoneIndex` is present, the active zone segment height evaluates to $1.6\times$ `trackHeight`, while inactive segments evaluate to base `trackHeight`. When `activeZoneIndex == null` (stale/disconnected), verify all segments evaluate to uniform base `trackHeight` with `0.35f` opacity.
- **Assertions**:
  - `activeTrackHeight(baseHeight = 6.dp, activeIndex = 3, segmentIndex = 2) == 9.6.dp` ($1.6\times$).
  - `activeTrackHeight(baseHeight = 6.dp, activeIndex = 3, segmentIndex = 0) == 6.dp`.
  - When `activeIndex == null`, all segment heights evaluate to `6.dp`.

### TST-UI-436.2: Dynamic Track Height Scaling in Bar-Only Mode
- **Component**: `SensorFieldZoneGaugeTest.kt` / `SensorFieldView.kt`
- **Objective**: Verify that when `!fieldState.showValue && fieldState.showBar`, horizontal telemetry bars resolve elevated track heights according to `fieldState.viewSize`:
  - `ZoneGaugeBar`: `SMALL = 10.dp`, `NORMAL = 14.dp`, `LARGE = 18.dp`, `EXTRA_LARGE = 22.dp` (standard: `4.dp`, `6.dp`, `8.dp`, `10.dp`).
  - `PedalPowerBalanceBar`: `SMALL = 12.dp`, `NORMAL = 16.dp`, `LARGE = 20.dp`, `EXTRA_LARGE = 24.dp` (standard: `6.dp`, `8.dp`, `10.dp`, `12.dp`).
  - `WPrimeBatteryGaugeBar`: `SMALL = 14.dp`, `NORMAL = 20.dp`, `LARGE = 26.dp`, `EXTRA_LARGE = 32.dp` (standard: `16.dp`).
- **Assertions**:
  - `resolveBarOnlyTrackHeight(viewSize, isBarOnly = true)` matches expected elevated values for all 4 `ViewSize` tiers.
  - When `isBarOnly = false` (standard mode), resolves standard heights.

### TST-UI-436.3: Needle Container & Overlay Geometry Contract
- **Component**: `ZoneGaugeBar.kt`
- **Objective**: Verify that the outer track container accommodates the elevated active segment (`activeTrackHeight + 4.dp`) without clipping the expanded segment or needle.
- **Assertions**:
  - Container height >= `activeTrackHeight + 4.dp`.
  - Needle indicator height matches container height, spanning 2.dp above and below the elevated segment.

### TST-UI-436.4: Clean-Room Full Suite Regression Execution
- **Command**: `./gradlew testDebugUnitTest --tests "*SensorFieldZoneGaugeTest*" --tests "*ThemeColorCentralizationAuditTest*"`
- **Criteria**: 100% pass rate, zero regressions across living test suites.
