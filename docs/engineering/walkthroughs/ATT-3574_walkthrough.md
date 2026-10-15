# Stage 5 Walkthrough: Centralize Compose Color Literals into Color.kt and TTColor

## Ticket Reference
- **Ticket ID**: ATT-3574
- **Subtask ID**: ATT-3744
- **Branch**: `feature/ATT-3574`
- **Requirement ID**: `REQ-UI-381`
- **Test Specification ID**: `TST-UI-344`

---

## 1. Executive Summary
In accordance with `REQ-UI-381`, all 67 hardcoded Compose `Color(0x...)` literals scattered across 23 Kotlin UI files outside `Color.kt` have been eliminated and consolidated into centralized, semantically named design tokens within `Color.kt` and `TTColor`.

A dedicated automated contract test (`ThemeColorCentralizationAuditTest`) statically validates that zero `Color(0x...)` or `Color(0X...)` invocations exist anywhere in `app/src/main/java/` outside of `Color.kt`. Furthermore, existing test suites (including `ElevationProfileClimbShadingTest` and `DarkMapStyleResolutionTest`) verify visual and functional parity across all themes, climb grades, and map renderers.

---

## 2. Changes Implemented

### 2.1 Color Tokens Centralized in `Color.kt` and `TTColor`
1. **AMOLED Theme Tokens**:
   - `AmoledPrimaryContainer` (`0xFF00363D`)
   - `AmoledOnPrimaryContainer` (`0xFF70F5FF`)
   - `AmoledSurfaceBright` (`0xFF1E1E1E`)
   - `AmoledSurfaceContainerHigh` (`0xFF121212`)
   - `AmoledSurfaceContainerHighest` (`0xFF181818`)
2. **Climb Category Tokens**:
   - `ClimbCategoryHc` (`0xFFB71C1C`)
   - `ClimbCategory1` (`0xFFD32F2F`)
   - `ClimbCategory2` (`0xFFF57C00`)
   - `ClimbCategory3` (`0xFFFBC02D`)
   - `ClimbCategory4` (`0xFF388E3C`)
   - `ClimbCategoryUncategorized` (`0xFF757575`)
3. **Climb Grade Gradient Tokens & Resolution Helper**:
   - `ClimbGradeFlat` (`0xFF4CAF50`)
   - `ClimbGradeModerate` (`0xFFFFEB3B`)
   - `ClimbGradeSteep` (`0xFFFF9800`)
   - `ClimbGradeVerySteep` (`0xFFF44336`)
   - `ClimbGradeExtreme` (`0xFF212121`)
   - Unified helper: `TTColor.getClimbGradeColor(gradePercent: Double): Color`
4. **Map Styling & Anti-Flash Tokens**:
   - `DarkMapCanvas` (`0xFF121212`)
   - `DarkMapTrackCyan` (`0xFF00E5FF`)
5. **Geofencing & Location Badge Tokens**:
   - `GeofenceFill` (`0x261976D2`)
   - `GeofenceStroke` (`0xFF1976D2`)
   - `GeofencePin` (`0xFFD32F2F`)
   - `LocationBadgeHomeContainer` / `LocationBadgeHomeContent`
   - `LocationBadgeWorkContainer` / `LocationBadgeWorkContent`
   - `LocationBadgeOtherContainer` / `LocationBadgeOtherContent`
6. **Cloud Providers & Tracking UI Tokens**:
   - `DropboxBlue` (`0xFF0061FE`)
   - `ConnectionConnected` (`0xFF4CAF50`)
   - `PauseAmber` (`0xFFFFA000`)
   - `SensorActiveBorder` (`0xFF00E5FF`)
   - `SensorBorderDark` (`0xFF333333`)
   - `SensorBorderLight` (`0xFFE0E0E0`)
   - `BalanceGaugeTrack` (`0xFF424242`)
   - `ZoneGaugeTrack` (`0xFF424242`)
   - `LightWarningBorder` (`0xFFFFA000`)

### 2.2 Consuming Files Refactored (23 Files)
1. `Theme.kt`: Replaced hardcoded AMOLED color tokens.
2. `DarkMapStyle.kt`: Replaced `Color(0xFF121212)` with `TTColor.DarkMapCanvas`.
3. `PathPreviewMap.kt`: Replaced `Color(0xFF121212)` with `TTColor.DarkMapCanvas`.
4. `ATrainingTrackerMap.kt`: Replaced `Color(0xFF121212)` with `TTColor.DarkMapCanvas`.
5. `WorkoutClusterComponents.kt`: Replaced `Color(0xFF121212)` and `Color(0xFFFFD700)`.
6. `ManualClusterScreen.kt`: Replaced `Color(0xFF121212)`.
7. `LapEditBottomSheet.kt`: Replaced `Color(0xFF121212)`.
8. `PeriodSummaryCard.kt`: Replaced `Color(0xFF121212)`.
9. `ImportBackupTabsScreen.kt`: Replaced `Color(0xFF121212)`.
10. `LiveClimbSheet.kt`: Replaced climb category and grade literals with `TTColor` tokens and `getClimbGradeColor`.
11. `ClimbDetailSheet.kt`: Unified gradient calculation with `TTColor.getClimbGradeColor`.
12. `KnownLocationsScreen.kt`: Replaced geofence overlay colors and badge colors.
13. `EditKnownLocationDialog.kt`: Replaced geofence overlay and anti-flash background colors.
14. `WorkoutClusterHeatmapScreen.kt`: Replaced heatmap selection outline `Color(0xFF1976D2)` with `TTColor.GeofenceStroke`.
15. `StravaConnectionHeader.kt`: Replaced connected indicator green with `TTColor.ConnectionConnected`.
16. `DropboxConnectionHeader.kt`: Replaced brand color with `TTColor.DropboxBlue` and connected green with `TTColor.ConnectionConnected`.
17. `GoogleDriveConnectionHeader.kt`: Replaced connected indicator green with `TTColor.ConnectionConnected`.
18. `ControlTrackingButton.kt`: Replaced paused amber with `TTColor.PauseAmber`.
19. `SensorFieldStyle.kt`: Replaced active border cyan with `TTColor.SensorActiveBorder`.
20. `PedalPowerBalanceBar.kt`: Replaced gauge track with `TTColor.BalanceGaugeTrack`.
21. `ZoneGaugeBar.kt`: Replaced gauge track with `TTColor.ZoneGaugeTrack`.
22. `MapLayers.kt`: Replaced live track dark polyline cyan with `TTColor.DarkMapTrackCyan`.
23. `SensorGridScreen.kt`: Replaced light/dark sensor borders and warning container/content/border colors with design tokens.

---

## 3. Verification & Quality Assurance

### 3.1 Automated Contract Audit
- `ThemeColorCentralizationAuditTest` scans every `.kt` file in `app/src/main/java/` (excluding `Color.kt`):
  - Invariant checked: Zero matches for `Color(0x` or `Color(0X`.
  - Result: **0 violations detected. PASSED.**

### 3.2 Regression & Functional Testing
- `ElevationProfileClimbShadingTest`: Verified that climb category and grade gradient colors match exact hex values across all thresholds.
- `DarkMapStyleResolutionTest`: Verified that all map composables reference dynamic style resolution and `DarkMapCanvas` anti-flash background protection.
- Full clean-room test suite (`./gradlew testDebugUnitTest`): 3001+ tests executed cleanly with 0 regressions.

---

## 4. Requirement & Test Status
- `REQ-UI-381`: Verified.
- `TST-UI-344`: Verified.
