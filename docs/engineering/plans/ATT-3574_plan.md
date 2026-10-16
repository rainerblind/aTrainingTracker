# Stage 3: Implementation Plan - ATT-3574: Centralize All 67 Hardcoded Compose Color Literals Across 20 Files into Color.kt and TTColor

**Ticket**: [ATT-3574](https://rainerblind.atlassian.net/browse/ATT-3574)  
**Sub-task**: [ATT-3742](https://rainerblind.atlassian.net/browse/ATT-3742) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Requirement Mapping**: `REQ-UI-381` (*Centralized Jetpack Compose Design System Colors & Zero Hardcoded Color Literals Architecture*)  
**Test Mapping**: `TST-UI-344` (*Compose Theme Color Centralization Audit & Climb Gradient Parity Verification Suite*)  
**Branch**: `feature/ATT-3574`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Problem Description & Background

Under **Rule 36 (Single Source of Truth & DRY)** and [docs/design_guidelines.md], all Jetpack Compose colors must be defined exclusively in `app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt` (and exposed via the `TTColor` design system object or Compose theme palettes).

Forensic investigation revealed **67 inline `Color(0x...)` instantiations across 23 production Kotlin files** outside `Color.kt`. These ad-hoc literals duplicate business logic (such as climb gradient color thresholds in `LiveClimbSheet.kt` and `ClimbDetailSheet.kt`), repeat existing design tokens (`TTColor.Gold`, `TTColor.ConnectionStatusGreen`), and repeatedly hardcode dark map styling (`Color(0xFF121212)`).

The objective of this task is to centralize all 67 literals into `Color.kt` and `TTColor`, establish a single shared helper `TTColor.getClimbGradeColor`, migrate all 23 consuming files, and enforce a static source audit test ensuring strictly **0 occurrences** of `Color(0x` outside `Color.kt`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-381` (*Centralized Jetpack Compose Design System Colors & Zero Hardcoded Color Literals Architecture*)
  * `REQ-UI-381.1`: Centralized token definitions in `Color.kt` and `TTColor`
  * `REQ-UI-381.2`: Purge of inline color literals across consuming UI components
  * `REQ-UI-381.3`: Zero hex literals outside `Color.kt` architecture enforcement
* **Test Mapping**: `TST-UI-344` (*Compose Theme Color Centralization Audit & Climb Gradient Parity Verification Suite*)
  * `TST-UI-344.1`: Architecture audit test verifying 0 occurrences of `Color(0x` outside `Color.kt`
  * `TST-UI-344.2`: Unit test verifying `TTColor.getClimbGradeColor` across explicit boundary and interior grade values
  * `TST-UI-344.3`: Static code inspection asserting delegation of gradient resolution to shared helper
  * `TST-UI-344.4`: Translation and resource parity verification
  * `TST-UI-344.5`: Full clean-room test suite pass (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Visual Fidelity Invariant**: 100% ARGB pixel-level visual fidelity: Every single hex value ($A, R, G, B$) migrated into `Color.kt` or `TTColor` is mathematically identical to the original literal. Zero color, tint, contrast, or alpha drift.
2. **Binary Stability**: No public method signatures or data classes outside UI theming are altered.
3. **DRY & Single Source of Truth**: `TTColor.getClimbGradeColor` is the sole source of truth for climb elevation profile colors.
4. **Zero Tolerance Contract**: Contract test enforces 0 occurrences of `Color(0x` outside `Color.kt`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `Color.kt` and `TTColor` Design Tokens (`ui/theme/Color.kt`)
* Define AMOLED tokens:
  ```kotlin
  val AmoledPrimaryContainer = Color(0xFF000000)
  val AmoledOnPrimaryContainer = Color(0xFFFFFFFF)
  val AmoledSurfaceBright = Color(0xFF1A1A1A)
  val AmoledSurfaceContainerHigh = Color(0xFF121212)
  val AmoledSurfaceContainerHighest = Color(0xFF000000)
  ```
* Define climb tokens and shared helper in `TTColor`:
  ```kotlin
  val ClimbCategoryHc = Color(0xFFB71C1C)
  val ClimbCategory1 = Color(0xFFD32F2F)
  val ClimbCategory2 = Color(0xFFEF6C00)
  val ClimbCategory3 = Color(0xFFFBC02D)
  val ClimbCategory4 = Color(0xFF388E3C)
  val ClimbCategoryUncategorized = Color(0xFF757575)

  val ClimbGradeFlat = Color(0xFF4CAF50)
  val ClimbGradeMild = Color(0xFF8BC34A)
  val ClimbGradeMedium = Color(0xFFFFEB3B)
  val ClimbGradeSteep = Color(0xFFFF9800)
  val ClimbGradeSevere = Color(0xFFF44336)
  val ClimbGradeExtreme = Color(0xFF9C27B0)

  fun getClimbGradeColor(gradePercent: Double): Color = when {
      gradePercent < 3.0 -> ClimbGradeFlat
      gradePercent < 6.0 -> ClimbGradeMild
      gradePercent < 9.0 -> ClimbGradeMedium
      gradePercent < 12.0 -> ClimbGradeSteep
      gradePercent < 15.0 -> ClimbGradeSevere
      else -> ClimbGradeExtreme
  }
  ```
* Define Map & Track tokens in `TTColor`:
  ```kotlin
  val DarkMapCanvas = Color(0xFF121212)
  val DarkMapTrackCyan = Color(0xFF00E5FF)
  ```
* Define Geofence & Location Badges in `TTColor`:
  ```kotlin
  val GeofenceFill = Color(0x332196F3)
  val GeofenceStroke = Color(0x882196F3)
  val GeofencePin = Color(0xFF2196F3)
  val LocationBadgeHomeContainer = Color(0xFFE8F5E9)
  val LocationBadgeHomeContent = Color(0xFF2E7D32)
  val LocationBadgeWorkContainer = Color(0xFFFFF8E1)
  val LocationBadgeWorkContent = Color(0xFFF57F17)
  val LocationBadgeOtherContainer = Color(0xFFFFE0B2)
  val LocationBadgeOtherContent = Color(0xFFE65100)
  ```
* Define Cloud & Connectivity tokens in `TTColor`:
  ```kotlin
  val DropboxBlue = Color(0xFF0061FE)
  val ConnectionConnected = Color(0xFF2E7D32)
  ```
* Define Cockpit Gauges & Sensor Borders in `TTColor`:
  ```kotlin
  val PauseAmber = Color(0xFFF59E0B)
  val SensorActiveBorder = Color(0xFF2196F3)
  val SensorBorderDark = Color(0xFF383838)
  val SensorBorderLight = Color(0xFFD6D6D6)
  val BalanceGaugeTrack = Color(0xFF1B1B1F)
  val ZoneGaugeTrack = Color(0xFF1B1B1F)
  val WarningBorderLight = Color(0xFFE0E0E0)
  ```

### Component 2: Consuming Compose UI Files Migration (23 Files)
Migrate each file's hardcoded `Color(0x...)` to reference the canonical token in `Color.kt` / `TTColor`:
1. `ui/theme/Theme.kt` (5 occurrences)
2. `ui/map/DarkMapStyle.kt` (1 occurrence)
3. `ui/map/PathPreviewMap.kt` (1 occurrence)
4. `ui/map/ATrainingTrackerMap.kt` (1 occurrence)
5. `ui/clusters/WorkoutClusterComponents.kt` (5 occurrences)
6. `ui/clusters/ManualClusterScreen.kt` (1 occurrence)
7. `ui/components/workoutlaps/LapEditBottomSheet.kt` (1 occurrence)
8. `ui/aftermath/periodlist/PeriodSummaryCard.kt` (1 occurrence)
9. `migration/ImportBackupTabsScreen.kt` (2 occurrences)
10. `ui/climbs/LiveClimbSheet.kt` (8 occurrences)
11. `ui/climbs/ClimbDetailSheet.kt` (8 occurrences)
12. `ui/knownlocations/KnownLocationsScreen.kt` (12 occurrences)
13. `ui/knownlocations/EditKnownLocationDialog.kt` (6 occurrences)
14. `ui/clusters/WorkoutClusterHeatmapScreen.kt` (1 occurrence)
15. `ui/settings/strava/StravaConnectionHeader.kt` (1 occurrence)
16. `ui/settings/dropbox/DropboxConnectionHeader.kt` (2 occurrences)
17. `ui/settings/googledrive/GoogleDriveConnectionHeader.kt` (1 occurrence)
18. `ui/tracking/controltracking/ControlTrackingButton.kt` (1 occurrence)
19. `ui/tracking/SensorFieldStyle.kt` (3 occurrences)
20. `ui/tracking/cockpit/pedalpower/PedalPowerBalanceBar.kt` (1 occurrence)
21. `ui/tracking/cockpit/ZoneGaugeBar.kt` (1 occurrence)
22. `ui/map/MapLayers.kt` (1 occurrence)
23. `ui/sensors/sensorgid/SensorGridScreen.kt` (4 occurrences)

### UI Consistency (Rule 23)
* **Reference screen / component**: Design tokens and themes in `ui/theme/Color.kt` and `ui/theme/Theme.kt`.
* **Reused components**: Standard `TTColor` constants and Compose `MaterialTheme.colorScheme`.
* **Theme tokens**: All colors are directly centralized into `TTColor` / `Color.kt`.
* **New one-off styles & justification**: None. All 67 migrated values preserve the exact existing visual specifications of the respective screens.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Centralize Tokens in `Color.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Color.kt`
* Action: Add AMOLED palette tokens, Climb category & gradient tokens, `TTColor.getClimbGradeColor`, Map tokens, Geofence/Location tokens, Cloud/Connectivity tokens, and Cockpit gauge tokens.

### Step 2: Migrate Theme Palette and Climb Sheets
* Files:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/theme/Theme.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheet.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/ClimbDetailSheet.kt`
* Action: Replace inline literals with `Color.kt` / `TTColor` references and delegate gradient color resolution to `TTColor.getClimbGradeColor`.

### Step 3: Migrate Dark Map Canvas Across Map, Cluster, Period, Lap, and Import Screens
* Files:
  - `ui/map/DarkMapStyle.kt`
  - `ui/map/PathPreviewMap.kt`
  - `ui/map/ATrainingTrackerMap.kt`
  - `ui/clusters/WorkoutClusterComponents.kt`
  - `ui/clusters/ManualClusterScreen.kt`
  - `ui/components/workoutlaps/LapEditBottomSheet.kt`
  - `ui/aftermath/periodlist/PeriodSummaryCard.kt`
  - `migration/ImportBackupTabsScreen.kt`
* Action: Replace `Color(0xFF121212)` with `TTColor.DarkMapCanvas` (and `Color(0xFFFFD700)` with `TTColor.Gold` in `WorkoutClusterComponents.kt`).

### Step 4: Migrate Known Locations Geofence, Badges & Cluster Heatmap
* Files:
  - `ui/knownlocations/KnownLocationsScreen.kt`
  - `ui/knownlocations/EditKnownLocationDialog.kt`
  - `ui/clusters/WorkoutClusterHeatmapScreen.kt`
* Action: Replace inline geofence, badge, and gold literals with `TTColor.*`.

### Step 5: Migrate Cloud Headers, Cockpit Controls, Gauges, Map Layers & Sensor Grid
* Files:
  - `ui/settings/strava/StravaConnectionHeader.kt`
  - `ui/settings/dropbox/DropboxConnectionHeader.kt`
  - `ui/settings/googledrive/GoogleDriveConnectionHeader.kt`
  - `ui/tracking/controltracking/ControlTrackingButton.kt`
  - `ui/tracking/SensorFieldStyle.kt`
  - `ui/tracking/cockpit/pedalpower/PedalPowerBalanceBar.kt`
  - `ui/tracking/cockpit/ZoneGaugeBar.kt`
  - `ui/map/MapLayers.kt`
  - `ui/sensors/sensorgid/SensorGridScreen.kt`
* Action: Replace remaining literals with `TTColor` tokens and theme tokens.

### Step 6: Create Architecture & Contract Tests
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/theme/ThemeColorCentralizationAuditTest.kt`
* Action:
  - Test 1: Recursive scan of `app/src/main/java/` verifying 0 occurrences of `Color(0x` outside `Color.kt`.
  - Test 2: Unit test verifying `TTColor.getClimbGradeColor` across all boundary values (`[-5.0, 0.0, 2.99, 3.0, 5.99, 6.0, 8.99, 9.0, 11.99, 12.0, 14.99, 15.0, 25.0]`).
  - Test 3: Contract test asserting `LiveClimbSheet.kt` and `ClimbDetailSheet.kt` call `TTColor.getClimbGradeColor` and contain zero duplicate `when` grade threshold branches.

### Step 7: Run Targeted Unit Tests
* Command:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Targeted unit test execution (`ThemeColorCentralizationAuditTest`).
  - Full clean-room regression test suite execution (`./gradlew testDebugUnitTest`).
  - Inspection of Compose previews in Light and AMOLED Dark modes.
* **Rollback Plan**:
  - Work is strictly isolated on branch `feature/ATT-3574`. If unresolvable issues occur, git checkout/revert to `sprint/2026-42.5` restores state cleanly without affecting production.
