# Stage 3: Implementation Plan - ATT-7312: Add context-aware Hamburger Menu and Back Arrow to Sensors, Equipment, Backup, Settings, and Support views

**Ticket**: [ATT-7312](https://rainerblind.atlassian.net/browse/ATT-7312)  
**Sub-task**: [[Impl-Plan]](https://rainerblind.atlassian.net/browse/ATT-7312)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.4`  
**Requirement Mapping**: `REQ-UI-518`  
**Test Mapping**: `TST-UI-518`  
**Branch**: `subagent-Lane-2-Agent---ATT-7312-self-c3867f51`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Description & Background
Building on Part 1 (`ATT-7313`), this plan completes the context-aware Hamburger Menu vs Back Arrow navigation contract across the remaining secondary drawer destinations (`My Stuff`, `Online Communities`, `Settings`, `Info & Support`):
1. Four tabbed drawer destinations currently lack a leading icon in their header:
   * `My Sensors` (`DevicesTabbedScreen.kt` in `:banalservice`)
   * `Equipment` (`EquipmentTabsScreen.kt` in `:app`, covering `EQUIPMENT`, `BIKES`, and `SHOES`)
   * `Import & Backup` (`ImportBackupTabsScreen.kt` in `:app`)
   * `Sport Types` (`SportTypesTabsScreen.kt` in `:banalservice`)
2. Three top-level drawer destinations unconditionally render `ArrowBack` even when opened from `AppNavigationDrawer`:
   * `Advanced Tuning` (`AdvancedTuningScreen.kt`)
   * `Supporter` (`SupporterScreen.kt`)
   * `Tips & Guide` (`TipsGuideScreen.kt`)
3. One destination unconditionally renders `Menu` even when pushed above another screen:
   * `Training Zones` (`ZoneSettingsScreen` in `ZonesSettingsActivity.kt` / `ATrainingTrackerApp.kt`) renders `Icons.Default.Menu` even when pushed above `My Sensors` via `onNavigateToZones`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-518` (*Standardize Context-Aware Hamburger Menu vs Back Arrow Contract Across Secondary Drawer Groups*)
* **Test Mapping**: `TST-UI-518` (*Context-Aware Hamburger Menu vs Back Arrow Contract Across Secondary Drawer Groups Verification Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Drawer Destinations**: All 21 navigation drawer destinations in `NavRoutes` preserved.
2. **Gesture Bounds (`REQ-UI-161`)**: 40 dp left-edge drawer interceptor preserved on root destinations; suppressed when pushed.
3. **Back Handling Hierarchy (`REQ-UI-162`)**: Clean LIFO back handling strictly preserved.
4. **Existing Deep Link Contracts**: Invariants in `ZoneOnboardingBannerContractTest` and `TipsGuideScreenContractTest` strictly preserved.
5. **Code Lock**: Code changes strictly locked until Gate 3 is verified.

---

## 4. Proposed Architectural Changes

### Component 1: Route Contracts (`NavRoutes.kt`)
* Extend `NavRoutes.trainingZones()`, `NavRoutes.supporter()`, and `NavRoutes.advancedSettings()` with optional `pushed: Boolean = false`.
* Define `ADVANCED_SETTINGS_PATTERN = "advanced_settings?pushed={pushed}"`, `SUPPORTER_PATTERN = "supporter?planId={planId}&pushed={pushed}"`, and `TRAINING_ZONES_PATTERN = "training_zones?initialTab={initialTab}&promptReason={promptReason}&pushed={pushed}"`.
* Preserve `toDrawerItemId()` and `fromDrawerItemId()` mappings.

### Component 2: Tabbed Collapsing Headers (:banalservice & :app)
* Add `onMenuClick: (() -> Unit)? = null` and `onBackClick: (() -> Unit)? = null` across:
  * `DevicesTabbedScreen.kt`
  * `EquipmentTabsScreen.kt`
  * `ImportBackupTabsScreen.kt`
  * `SportTypesTabsScreen.kt`
* Align header row: `padding(start = 4.dp, end = 16.dp)`, leading `IconButton` (48 dp target, `tint = onPrimaryContainer`), and title text with `start = 4.dp` padding (56 dp offset).

### Component 3: Context-Aware TopAppBar Screens (:app)
* `ZonesSettingsActivity.kt` (`ZoneSettingsScreen`):
  * Render `Icons.Default.Menu` with localized `navigation_drawer_open` description when `onOpenDrawer != null`.
  * Render `Icons.AutoMirrored.Filled.ArrowBack` with `back` description invoking `onFinish` when `onOpenDrawer == null`.
* `AdvancedTuningScreen.kt`:
  * Add `onOpenDrawer: (() -> Unit)? = null`.
  * Render `Menu` when `onOpenDrawer != null`; render `ArrowBack` when `onOpenDrawer == null`.
  * Keep bottom `Save / Cancel` actions fully operational.
* `SupporterScreen.kt` & `TipsGuideScreen.kt`:
  * Add `onOpenDrawer: (() -> Unit)? = null`.
  * Render `Menu` when `onOpenDrawer != null`; render `ArrowBack` when `onOpenDrawer == null`.

### Component 4: ATrainingTrackerApp Navigation & Interceptor State
* Update `isCurrentScreenPushed` to include secondary screens when pushed.
* Wire `onMenuClick = { drawerController.openDrawer() }` for drawer root destinations.
* Pass `onOpenDrawer = null` when screens are pushed.

### UI Consistency (Rule 23)
* **Reference screen**: `AutoPauseTabsScreen.kt` / `WorkoutTabsScreen.kt` (Part 1).
* **Reused components**: Standard Material 3 `IconButton`, `Icon`, `Text` inside `Surface(color = MaterialTheme.colorScheme.primaryContainer)`.
* **Theme tokens**: `MaterialTheme.colorScheme.onPrimaryContainer`, `titleLarge` typography, `HEADER_TITLE_ROW_HEIGHT = 64.dp`, `start = 4.dp` icon padding.
* **New one-off styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: NavRoutes Helper & Patterns
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/NavRoutes.kt`
* Add `advancedSettings(pushed)`, extend `supporter(planId, pushed)` and `trainingZones(initialTab, promptReason, pushed)`.

### Step 2: Tabbed Screen Header Alignment
* Files:
  * `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedScreen.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentTabsScreen.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`
  * `app/src/main/java/com/atrainingtracker/banalservice/ui/sporttype/SportTypesTabsScreen.kt`
* Implement standardized leading `IconButton` (Back or Menu) and 56 dp title alignment.

### Step 3: Context-Aware TopAppBar Screens
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreen.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/supporter/SupporterScreen.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/help/TipsGuideScreen.kt`
* Implement conditional `onOpenDrawer` handling with localized descriptions.

### Step 4: ATrainingTrackerApp Wiring & Caller Updates
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt`
* Update `isCurrentScreenPushed`, connect `onMenuClick` / `onOpenDrawer` for drawer roots, and pass back callbacks when pushed.
* Update callers in `WorkoutClustersScreen` and `DisplaySettingsDialog` to use `NavRoutes.advancedSettings(pushed = true)`.

### Step 5: Unit & Contract Tests
* Files:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/SingleActivityNavigationTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/zones/NavRoutesZoneTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningNavigationTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningScreenTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/help/TipsGuideScreenContractTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/SecondaryTabbedHeadersContractTest.kt` (New)
* Run all targeted tests and verify 100% pass rate.

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.navigation.*" --tests "com.atrainingtracker.trainingtracker.zones.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*" --tests "com.atrainingtracker.trainingtracker.ui.help.*"` under `parallel_sprint_lock.py`.
* **Rollback**: Worktree branch isolation guarantees zero cross-branch pollution.
