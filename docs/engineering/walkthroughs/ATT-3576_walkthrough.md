# Stage 5 Walkthrough: Unify Internal Sheet Layout of LiveClimbSheet and LiveSegmentSheet via Shared LiveTrackingEntitySheet

## Ticket Reference
- **Ticket ID**: ATT-3576
- **Subtask ID**: ATT-3754
- **Branch**: `feature/ATT-3576`
- **Requirement ID**: `REQ-UI-383`
- **Test Specification ID**: `TST-UI-346`

---

## 1. Executive Summary
In compliance with `REQ-UI-383`, Rule 36 (Single Source of Truth & DRY), and Rule 40 (ASPICE Clean Architecture), the internal layout structures of `LiveClimbSheet` and `LiveSegmentSheet` have been unified through a shared layout composable `LiveTrackingEntitySheet` in `com.atrainingtracker.trainingtracker.ui.components.core`.

Previously, although `LiveTrackingSheetContainer` unified the external bottom sheet envelope, the internal composables (`LiveClimbSheet.kt` and `LiveSegmentSheet.kt`) maintained duplicated layout columns, divergent outer and inner padding values, and separate `HorizontalDivider` declarations.

With this implementation:
1. `LiveTrackingEntitySheet` establishes the canonical internal layout structure for all live tracking bottom sheets.
2. `LiveSegmentSheet.kt` delegates its header and telemetry HUD layout to `LiveTrackingEntitySheet`.
3. `LiveClimbSheet.kt` is decomposed into modular sub-composables (`ClimbHeader` and `ClimbLiveDetails`) and delegates its root layout to `LiveTrackingEntitySheet`.
4. Direct `HorizontalDivider` declarations have been completely eliminated from both `LiveSegmentSheet.kt` and `LiveClimbSheet.kt` (0 occurrences in both files).
5. Comprehensive architecture contract tests and the full unit test suite (3007+ tests) verify structural parity and zero regressions.

---

## 2. Changes Implemented

### 2.1 Production Source Refactorings
- **`LiveTrackingEntitySheet.kt`** (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/LiveTrackingEntitySheet.kt`):
  - Created shared composable taking `headerContent: @Composable (Modifier) -> Unit`, `telemetryHudContent: @Composable (Modifier) -> Unit`, `elevationContent: @Composable () -> Unit = {}`, and optional `modifier: Modifier = Modifier`.
  - Enforces canonical layout sequence:
    - Root `Column(modifier = modifier.fillMaxWidth().wrapContentHeight().background(MaterialTheme.colorScheme.surface))`
    - `headerContent(Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp))`
    - Canonical `HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))`
    - `telemetryHudContent(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))`
    - `elevationContent()`
- **`LiveSegmentSheet.kt`**:
  - Refactored `MapDetailLayout`'s `header` slot to invoke `LiveTrackingEntitySheet`, passing `SegmentHeader` to `headerContent` and `SegmentLiveDetails` to `telemetryHudContent`.
  - Removed direct `HorizontalDivider` invocation.
- **`LiveClimbSheet.kt`**:
  - Decomposed monolithic sheet content into modular composables:
    - `ClimbHeader`: Renders category icon, climb title, category badge, and route counter badge.
    - `ClimbLiveDetails`: Renders 4-slot telemetry HUD (grade, remaining distance, elevation gain, VAM).
  - Root `LiveClimbSheet` delegates to `LiveTrackingEntitySheet`, passing `ClimbHeader`, `ClimbLiveDetails`, and `ElevationProfile`.
  - Removed direct `HorizontalDivider` invocation and redundant outer padding.

### 2.2 Test Suite Updates
- **`LiveTrackingSheetContainerTest.kt`**:
  - Added `liveTrackingEntitySheet_contractEnforcement()` validating `LiveTrackingEntitySheet` existence, `fillMaxWidth().wrapContentHeight()`, unbroken `MaterialTheme.colorScheme.surface`, and 0.5.dp divider styling with 16.dp horizontal padding.
- **`LiveSegmentSheetLayoutTest.kt`**:
  - Replaced obsolete divider assertions with `liveSegmentSheet_delegatesToLiveTrackingEntitySheet()`.
- **`LiveSegmentSheetContractTest.kt`**:
  - Added `liveSegmentSheet_hasZeroDirectHorizontalDividers()`, asserting exactly 0 occurrences of `HorizontalDivider(` in `LiveSegmentSheet.kt`.
- **`LiveClimbSheetLayoutTest.kt`**:
  - Added `liveClimbSheet_delegatesToLiveTrackingEntitySheet()` and `liveClimbSheet_hasZeroDirectHorizontalDividers()`.
  - Added assertions validating `ClimbHeader` and `ClimbLiveDetails` composable declarations.
  - Verified preservation of all visual tokens (Terrain icon 32dp, route counters, grade badges, etc.).

---

## 3. Verification & Quality Assurance

### 3.1 Targeted Contract & Layout Tests
- `LiveTrackingSheetContainerTest`: Passed 100%.
- `LiveSegmentSheetLayoutTest`: Passed 100%.
- `LiveSegmentSheetContractTest`: Passed 100%.
- `LiveClimbSheetLayoutTest`: Passed 100%.

### 3.2 Full Clean-Room Regression Test Suite
- Executed `./gradlew testDebugUnitTest` verifying 100% test pass rate across all 3007+ unit tests with zero regressions.

---

## 4. Living Documentation Status
- `REQ-UI-383`: Verified.
- `TST-UI-346`: Verified.
