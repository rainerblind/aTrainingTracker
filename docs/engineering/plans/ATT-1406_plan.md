# Stage 3: Implementation Plan - ATT-1406: Pro Supporter Badges

**Ticket**: [ATT-1406](https://rainerblind.atlassian.net/browse/ATT-1406)  
**Sub-task**: [ATT-5352](https://rainerblind.atlassian.net/browse/ATT-5352) (`[Impl-Plan]`)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.4` | **Branch**: `feature/ATT-1406`  
**Requirement Mapping**: `REQ-UI-445` | **Test Mapping**: `TST-UI-445`, `TST-LOC-001`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-04  

---

## 1. Problem Description & Background
Athletes supporting development via Supporter subscriptions (Bronze, Silver, Gold, Diamond) or Pioneer early adopters (`ATT-51`) require visible homescreen and in-app recognition. This plan implements 6 adaptive launcher icons (Option A: Top-Right Medal/Diamond/Star + Bottom Ribbon), deferred non-disruptive activation via `<activity-alias>`, a live Cockpit status emblem in `SensorStatus.kt`, Navigation Drawer branding, and a workout share watermark, strictly preserving authentic Cervélo P3C and stopwatch colors (`ATT-1482`).

---

## 2. Traceability & System Invariants
* **Requirement**: `REQ-UI-445` (*Supporter Badges: Launcher Icons, Cockpit Status, Drawer, Watermark*)  
* **Test Mapping**: `TST-UI-445` (Contract suites), `TST-LOC-001` (9-locale parity)  
* **Core Invariants**:
  1. 100% preservation of authentic Cervélo P3C (`#FF0000`/`#000000`) and classic stopwatch (`#FFC700`/`#000090`/`#009000`) colors.
  2. All foreground vector geometry contained within the 66dp circular safe zone ($r \le 33\text{dp}$).
  3. Zero session disruption: alias switching deferred via `PackageManager.DONT_KILL_APP` to `ProcessLifecycleOwner.onStop` when `!isTracking()`.
  4. Core workout tracking and export features remain 100% free and unmonetized forever.

---

## 3. Proposed Architectural Changes

### Component 1: Adaptive Launcher Icons & Manifest Aliases
* **Files**: `res/drawable/ic_launcher_foreground_<tier>.xml`, `res/mipmap-anydpi-v26/ic_launcher_<tier>.xml`, `AndroidManifest.xml`
* Define 6 adaptive icons with Option A top-right emblem pocket (`cx=67.6, cy=31.8`) and bottom ribbon (`y=74.8..83.0`). Declare 6 `<activity-alias>` entries targeting `MainActivityWithNavigation` (`.MainActivityStandard` enabled by default).

### Component 2: Deferred Activation Engine & DataStore
* **Files**: `LauncherIconManager.kt`, `SettingsDataStore.kt`
* Persist `KEY_SELECTED_LAUNCHER_ICON` in DataStore. `LauncherIconManager` observes `ProcessLifecycleOwner.onStop`: when `!TrainingApplication.isTracking()`, applies `setComponentEnabledSetting(..., DONT_KILL_APP)`.

### Component 3: In-App UI Badges & Share Watermark
* **Files**: `SensorStatus.kt`, `SupporterQuickIconPickerDialog.kt`, `AppNavigationDrawer.kt`, `ShareUtils.kt`
* `SensorStatus.kt`: Add right-aligned 24dp vector emblem for `effectiveTier.isPro` balancing the 24dp menu button; tapping opens `SupporterQuickIconPickerDialog`.
* `DrawerHeader`: Show active Cervélo icon (46dp) and supporter tier pill.
* `ShareUtils.drawFooter`: Add Cervélo icon, "Tracked with aTrainingTracker", and tier pill watermark.

### UI Consistency (Rule 23)
* **Reference screen**: `SupporterScreen.kt` & `SensorStatus.kt` (cockpit top bar).
* **Reused components**: Material 3 `Surface`, `AlertDialog`, `Icon`, `Text`, `FilterChip`.
* **Theme tokens**: `MaterialTheme.colorScheme.primaryContainer`, `onPrimaryContainer`, `surfaceVariant`, `8.dp`/`12.dp` rounded shapes.
* **New one-off styles**: None; reuses authentic brand tokens per `REQ-UI-444`.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Vector Drawables & Adaptive Icon Configurations
* Add Option A vector overlays and adaptive mipmaps for `pioneer`, `bronze`, `silver`, `gold`, `diamond`.
* Test safe-zone containment ($r \le 33\text{dp}$) and color fidelity.

### Step 2: Activity Aliases & SettingsDataStore Persistence
* Configure 6 `<activity-alias>` in `AndroidManifest.xml`. Add `selectedLauncherIcon` Flow and setter in `SettingsDataStore.kt`.

### Step 3: Non-Disruptive Lifecycle Engine (`LauncherIconManager.kt`)
* Implement `LauncherIconManager` singleton with `ProcessLifecycleOwner` observer and `isTracking()` guard.

### Step 4: Cockpit Emblem, Drawer Header, Share Watermark & Icon Picker
* Update `SensorStatus.kt`, `AppNavigationDrawer.kt`, `ShareUtils.kt`, and provide `SupporterQuickIconPickerDialog.kt`.

### Step 5: Localization & Test Suite
* Add 9-language strings in `res/values*/strings.xml`.
* Implement `LauncherIconManagerTest` and contract tests; run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.*"`.

---

## 5. Verification & Rollback Plan
* **Verification**: `testDebugUnitTest` (all tests passing), `TranslationParityTest`, on-device visual verification on Pixel 10.
* **Rollback**: Branch isolation (`feature/ATT-1406`) allows zero-impact rollback.
