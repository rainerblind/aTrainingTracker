# ASPICE Stage 3: Technical Implementation Plan (ATT-1072)

## 1. Goal Description
Standardize the window decor, system bar transparency, and lifecycle behavior across all `DialogFragment` hosts for modal bottom sheets (`AppModalBottomSheet`) to eliminate black navigation bar flicker during back-button and gesture dismissals per `REQ-UI-156` and `TST-UI-109`.

---

## 2. Proposed Changes

### Component 1: Dedicated Bottom Sheet Theme Overlay
#### [MODIFY] [themes.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values/themes.xml)
- Define `<style name="ThemeOverlay.aTrainingTracker.BottomSheetDialogFragment" parent="android:Theme.Translucent.NoTitleBar">`:
  - `android:statusBarColor`: `@android:color/transparent`
  - `android:navigationBarColor`: `@android:color/transparent`
  - `android:enforceNavigationBarContrast`: `false` (targetApi 29)
  - `android:enforceStatusBarContrast`: `false` (targetApi 29)
  - `android:windowLightNavigationBar`: `true` (targetApi 27)
  - `android:windowLightStatusBar`: `true`
  - `android:windowBackground`: `@android:color/transparent`
  - `android:windowIsFloating`: `false`
  - `android:windowAnimationStyle`: `@null`

---

### Component 2: Centralized Base Class
#### [NEW] [AppBottomSheetDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/AppBottomSheetDialogFragment.kt)
- Create `abstract class AppBottomSheetDialogFragment : DialogFragment()`.
- In `onCreate(savedInstanceState: Bundle?)`:
  - Call `setStyle(STYLE_NORMAL, R.style.ThemeOverlay_aTrainingTracker_BottomSheetDialogFragment)`.
- In `onStart()`:
  - Call `super.onStart()`.
  - Obtain `dialog?.window`.
  - Configure defensive window flags:
    - `window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)`.
    - `window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))`.
    - `WindowCompat.setDecorFitsSystemWindows(window, false)`.
    - `window.statusBarColor = Color.TRANSPARENT`.
    - `window.navigationBarColor = Color.TRANSPARENT`.
    - Disable contrast enforcement: `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { window.isNavigationBarContrastEnforced = false; window.isStatusBarContrastEnforced = false }`.
    - Configure appearance light system bars via `WindowCompat.getInsetsController(window, window.decorView)`.
    - Suppress window-level transition animations: `window.setWindowAnimations(0)`.

---

### Component 3: DialogFragment Host Consolidation
#### [MODIFY] [SearchSettingsDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/search/SearchSettingsDialogFragment.kt)
- Inherit from `AppBottomSheetDialogFragment()`.
- Remove redundant `onCreate` with `Theme_Translucent_NoTitleBar`.

#### [MODIFY] [ExportSettingsDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/export/ExportSettingsDialogFragment.kt)
- Inherit from `AppBottomSheetDialogFragment()`.
- Remove redundant `onCreate` with `Theme_Translucent_NoTitleBar`.

#### [MODIFY] [UnitsSettingsDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/units/UnitsSettingsDialogFragment.kt)
- Inherit from `AppBottomSheetDialogFragment()`.
- Remove redundant `onCreate` with `Theme_Translucent_NoTitleBar`.

#### [MODIFY] [StravaSettingsDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialogFragment.kt)
- Inherit from `AppBottomSheetDialogFragment()`.
- Remove redundant `onCreate` with `Theme_Translucent_NoTitleBar`.

#### [MODIFY] [DropboxSettingsDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/dropbox/DropboxSettingsDialogFragment.kt)
- Inherit from `AppBottomSheetDialogFragment()`.
- Remove redundant `onCreate` with `Theme_Translucent_NoTitleBar`.

#### [MODIFY] [ActivityTypeSelectionDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/ActivityTypeSelectionDialogFragment.kt)
- Inherit from `AppBottomSheetDialogFragment()`.
- Remove redundant `onCreate` with `Theme_Translucent_NoTitleBar`.

#### [MODIFY] [DisplaySettingsDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialogFragment.kt)
- Inherit from `AppBottomSheetDialogFragment()`.
- Remove redundant `onCreate` with `Theme_Translucent_NoTitleBar`.

---

### Component 4: Test Suite & Reflection Verification
#### [MODIFY] [ModalBottomSheetDialogsIntegrityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/ModalBottomSheetDialogsIntegrityTest.kt)
- Add `testAppBottomSheetDialogFragment_isAbstractAndExtendsDialogFragment()`:
  - Asserts `AppBottomSheetDialogFragment` class exists, is abstract, and extends `DialogFragment`.
- Update tests for all 7 dialog fragments:
  - Assert that each fragment class is assignable to `AppBottomSheetDialogFragment` (which transitively verifies `DialogFragment`).
  - Assert `newInstance()` and `TAG` contracts remain intact.

---

## 3. Invariants & Guardrails
- **Caller Compatibility**: Invocations in `MainActivityWithNavigation.kt` (`R.id.drawer_*` and `onPreferenceStartScreen`), `StarredSegmentsFragment.kt`, and `ActivityTypeSelectionHelper.kt` remain 100% binary and source compatible without requiring any caller changes.
- **Companion Contracts**: `TAG` constants and `newInstance()` factory functions on all 7 companion objects remain public, unchanged, and test-verified.
- **Persistence & Synchronization**: SharedPreferences storage, WorkManager background task scheduling, and OAuth redirect callbacks remain completely unaffected.
- **Visual Design Integrity**: Scrim transparency and viewport layering remain intact, ensuring the active background screen (tracking, list, map) remains visible behind the modal sheet.

---

## 4. Verification Plan
### Automated Tests
- `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.ModalBottomSheetDialogsIntegrityTest"`
- `./gradlew testDebugUnitTest --no-daemon` (full-suite clean-room regression check).

### Physical Device Verification
- `./gradlew installDebug` on Google Pixel 10.
- Open each bottom popup from the navigation drawer and tracking screen.
- Dismiss via system back button and gesture navigation back gesture.
- Confirm zero black flicker in the navigation bar zone with seamless edge-to-edge transparency throughout the dismiss transition.
