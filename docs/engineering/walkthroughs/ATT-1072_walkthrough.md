# ASPICE Stage 4: Implementation Walkthrough (ATT-1072)

## 1. Executive Summary
Implemented `AppBottomSheetDialogFragment` and `ThemeOverlay.aTrainingTracker.BottomSheetDialogFragment` to eliminate the black navigation bar flicker during back-button and gesture dismissals of bottom popups per `REQ-UI-156` and `TST-UI-109`. Refactored all 7 bottom popup dialog fragments to extend the new base class and verified reflection and inheritance contracts in `ModalBottomSheetDialogsIntegrityTest`.

---

## 2. Changes Made

### Component 1: Declarative Theme Overlay in `themes.xml`
- **[themes.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values/themes.xml)**:
  Declared `ThemeOverlay.aTrainingTracker.BottomSheetDialogFragment` inheriting from `android:Theme.Translucent.NoTitleBar` with:
  - Transparent `navigationBarColor` and `statusBarColor`.
  - Contrast enforcement disabled (`enforceNavigationBarContrast = false`, `enforceStatusBarContrast = false`).
  - Light system bars (`windowLightNavigationBar = true`, `windowLightStatusBar = true`).
  - Floating disabled (`windowIsFloating = false`).
  - Window animations suppressed (`windowAnimationStyle = @null`).

### Component 2: Centralized `AppBottomSheetDialogFragment` Base Class
- **[AppBottomSheetDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/AppBottomSheetDialogFragment.kt)**:
  Created abstract base class extending `androidx.fragment.app.DialogFragment`:
  - Applies `ThemeOverlay_aTrainingTracker_BottomSheetDialogFragment` in `onCreate()`.
  - In `onStart()`, defensively configures the window: `MATCH_PARENT` sizing, `WindowCompat.setDecorFitsSystemWindows(window, false)`, transparent colors, disabled contrast enforcement on API 29+, appearance light system bars via `WindowCompat.getInsetsController`, and disables window transition animations (`setWindowAnimations(0)`).

### Component 3: Consolidated DialogFragment Hosts
Refactored 7 dialog fragments to extend `AppBottomSheetDialogFragment`:
1. **[SearchSettingsDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/search/SearchSettingsDialogFragment.kt)**
2. **[ExportSettingsDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/export/ExportSettingsDialogFragment.kt)**
3. **[UnitsSettingsDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/units/UnitsSettingsDialogFragment.kt)**
4. **[StravaSettingsDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialogFragment.kt)**
5. **[DropboxSettingsDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/dropbox/DropboxSettingsDialogFragment.kt)**
6. **[ActivityTypeSelectionDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/ActivityTypeSelectionDialogFragment.kt)**
7. **[DisplaySettingsDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialogFragment.kt)**

### Component 4: Test Suite & Reflection Verification
- **[ModalBottomSheetDialogsIntegrityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/ModalBottomSheetDialogsIntegrityTest.kt)**:
  - Added `testAppBottomSheetDialogFragment_isAbstractAndExtendsDialogFragment()`.
  - Updated all 7 dialog test assertions to verify inheritance from `AppBottomSheetDialogFragment`.

---

## 3. Verification & Validation
- **Automated Unit Tests**:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.ModalBottomSheetDialogsIntegrityTest"`: **PASS** (100% success).
- **Git Commit**:
  - `aa9cd877`: `feat(ui): eliminate bottom popup navigation bar flicker with AppBottomSheetDialogFragment (ATT-1072)`.
