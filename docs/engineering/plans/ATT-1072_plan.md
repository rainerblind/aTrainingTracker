# ASPICE Stage 3: Technical Implementation Plan (ATT-1072)

## 1. Goal Description
Standardize all 7 bottom popup dialog fragments on a Single-Window architecture using Google Material's `BottomSheetDialogFragment`, eliminating the double-dialog window nesting, pitch-black status bars, and navigation bar dismiss flickering per `REQ-UI-156` and `TST-UI-109`.

---

## 2. Impact Analysis (SWE.1.BP.5)
- **Callers & Interfaces**: `MainActivityWithNavigation.kt` (`R.id.drawer_*` and `onPreferenceStartScreen`), `StarredSegmentsFragment.kt`, and `ActivityTypeSelectionHelper.kt` invoke `DialogFragment.newInstance().show(...)`. Because `BottomSheetDialogFragment` extends `androidx.fragment.app.DialogFragment`, 100% binary and source compatibility is preserved.
- **Pure Compose Screens**: `EditWorkoutScreen`, `EditRouteScreen`, `WorkoutClusterHeatmapScreen`, etc., invoke `AppModalBottomSheet` directly. By retaining `AppModalBottomSheet` as a wrapper around a shared `AppBottomSheetContent` composable, pure Compose screens remain completely unaffected.
- **System Bar Transparency**: Android 17 (API 37) enforces edge-to-edge on Activities but requires explicit configuration on Dialog windows. Material's `BottomSheetDialog` window will be configured with `FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS`, transparent system bar colors, and disabled contrast enforcement (`isNavigationBarContrastEnforced = false`, `isStatusBarContrastEnforced = false`).
- **Data Integrity & Side Effects**: SharedPreferences persistence, OAuth callbacks (`StravaOAuthCallbackActivity`), and WorkManager jobs remain 100% untouched.

---

## 3. Proposed Changes

### Component 1: Dedicated Material Theme Overlay in `themes.xml`
#### [MODIFY] [themes.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values/themes.xml)
- Redefine `<style name="ThemeOverlay.aTrainingTracker.BottomSheetDialogFragment" parent="ThemeOverlay.MaterialComponents.BottomSheetDialog">`:
  - `android:windowDrawsSystemBarBackgrounds`: `true`
  - `android:statusBarColor`: `@android:color/transparent`
  - `android:navigationBarColor`: `@android:color/transparent`
  - `android:enforceNavigationBarContrast`: `false` (targetApi 29)
  - `android:enforceStatusBarContrast`: `false` (targetApi 29)
  - `android:windowLightNavigationBar`: `true` (targetApi 27)
  - `android:windowLightStatusBar`: `true`
  - `bottomSheetStyle`: `@style/Widget.aTrainingTracker.BottomSheet.Modal`
- Define `<style name="Widget.aTrainingTracker.BottomSheet.Modal" parent="Widget.MaterialComponents.BottomSheet.Modal">`:
  - `android:background`: `@android:color/transparent`
  - `backgroundTint`: `@android:color/transparent`
  (Allows Compose's Material 3 Surface to draw rounded top corners and elevation cleanly without duplicate background clipping).

---

### Component 2: Centralized Single-Window Base Class
#### [MODIFY] [AppBottomSheetDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/AppBottomSheetDialogFragment.kt)
- Refactor `AppBottomSheetDialogFragment` to inherit directly from `com.google.android.material.bottomsheet.BottomSheetDialogFragment()`.
- In `onCreate()`:
  - Apply `setStyle(STYLE_NORMAL, R.style.ThemeOverlay_aTrainingTracker_BottomSheetDialogFragment)`.
- In `onStart()`:
  - Configure defensive window flags on `dialog?.window`:
    - `WindowCompat.setDecorFitsSystemWindows(window, false)`
    - `window.statusBarColor = Color.TRANSPARENT`
    - `window.navigationBarColor = Color.TRANSPARENT`
    - `window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)`
    - Disable contrast enforcement: `if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { window.isNavigationBarContrastEnforced = false; window.isStatusBarContrastEnforced = false }`.
    - Configure appearance light status and navigation bars via `WindowCompat.getInsetsController(window, window.decorView)`.
  - Configure bottom sheet behavior:
    - Set `behavior.state = BottomSheetBehavior.STATE_EXPANDED`.
    - Set `behavior.skipCollapsed = true`.

---

### Component 3: Extract Shared `AppBottomSheetContent` in `AppModalBottomSheet.kt`
#### [MODIFY] [AppModalBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/AppModalBottomSheet.kt)
- Extract the core visual container into a standalone composable:
  ```kotlin
  @Composable
  fun AppBottomSheetContent(
      title: String,
      onDismissRequest: () -> Unit,
      modifier: Modifier = Modifier,
      icon: ImageVector? = null,
      iconPainter: Painter? = null,
      iconTint: Color = MaterialTheme.colorScheme.primary,
      showCloseButton: Boolean = true,
      scrollable: Boolean = true,
      headerActions: (@Composable RowScope.() -> Unit)? = null,
      actions: (@Composable RowScope.() -> Unit)? = null,
      content: @Composable ColumnScope.() -> Unit
  )
  ```
- `AppBottomSheetContent` wraps content in `Surface(shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), color = MaterialTheme.colorScheme.surface)` with drag handle, header row, body, and actions.
- `AppModalBottomSheet(...)` delegates to `AppBottomSheetContent(...)` inside `ModalBottomSheet` for pure Compose screens.

---

### Component 4: Direct Compose Content in Popup Dialogs
#### [MODIFY] [DisplaySettingsDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialog.kt)
#### [MODIFY] [UnitsSettingsDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/units/UnitsSettingsDialog.kt)
#### [MODIFY] [ExportSettingsDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/export/ExportSettingsDialog.kt)
#### [MODIFY] [SearchSettingsDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/search/SearchSettingsDialog.kt)
#### [MODIFY] [StravaSettingsDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialog.kt)
#### [MODIFY] [DropboxSettingsDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/dropbox/DropboxSettingsDialog.kt)
#### [MODIFY] [ActivityTypeSelectionDialog.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/ActivityTypeSelectionDialog.kt)
- Replace `AppModalBottomSheet(...)` with `AppBottomSheetContent(...)`.
- Eliminates the nested `ComponentDialog` window inside the `BottomSheetDialogFragment` host.

---

### Component 5: DialogFragment Host Verification
#### [MODIFY] [SearchSettingsDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/search/SearchSettingsDialogFragment.kt)
#### [MODIFY] [ExportSettingsDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/export/ExportSettingsDialogFragment.kt)
#### [MODIFY] [UnitsSettingsDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/units/UnitsSettingsDialogFragment.kt)
#### [MODIFY] [StravaSettingsDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialogFragment.kt)
#### [MODIFY] [DropboxSettingsDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/dropbox/DropboxSettingsDialogFragment.kt)
#### [MODIFY] [ActivityTypeSelectionDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/trackingtabs/ActivityTypeSelectionDialogFragment.kt)
#### [MODIFY] [DisplaySettingsDialogFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialogFragment.kt)
- All 7 dialog fragments extend `AppBottomSheetDialogFragment` (which extends `BottomSheetDialogFragment`).
- Retain companion `TAG`, `newInstance()`, and listener interfaces verbatim.

---

### Component 6: Test Suite & Reflection Verification
#### [MODIFY] [ModalBottomSheetDialogsIntegrityTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/ModalBottomSheetDialogsIntegrityTest.kt)
- Update `testAppBottomSheetDialogFragment_isAbstractAndExtendsDialogFragment()`:
  - Asserts `AppBottomSheetDialogFragment` exists, is abstract, and extends `com.google.android.material.bottomsheet.BottomSheetDialogFragment`.
- Assert that all 7 dialog fragments inherit from `AppBottomSheetDialogFragment` (and transitively from `BottomSheetDialogFragment`).
- Verify companion `TAG` and `newInstance()` contracts remain intact.

---

## 4. Invariants & Guardrails
- **Caller Compatibility**: All calls in `MainActivityWithNavigation.kt`, `StarredSegmentsFragment.kt`, and `ActivityTypeSelectionHelper.kt` remain 100% binary and source compatible.
- **Companion Contracts**: `TAG` and `newInstance()` signatures remain public and unchanged.
- **Pure Compose Screens**: `EditWorkoutScreen`, `EditRouteScreen`, etc. remain untouched, continuing to use `AppModalBottomSheet`.
- **System Bar Transparency**: Top status bar displays the underlying activity dimmed by a single scrim (never solid black). Bottom navigation bar remains 100% transparent without any black flash.

---

## 5. Verification Plan
### Automated Tests
- `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.ModalBottomSheetDialogsIntegrityTest"`
- `./gradlew testDebugUnitTest` (full-suite clean-room regression check).

### Physical Device Verification (Google Pixel 10, Android 17 / API 37)
- Deploy via `./gradlew installDebug`.
- Open each bottom popup (Display, Units, Export, Search, Strava, Dropbox, Activity Type).
- Inspect with `screencap`: verify status bar shows underlying activity with single dim scrim.
- Dismiss via back button, navigation gesture, scrim tap, and action buttons: verify smooth slide-down with zero black navigation bar flicker.
