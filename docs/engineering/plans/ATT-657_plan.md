# Implementation Plan - ATT-657: Rewrite MainActivityWithNavigation in Kotlin

## Problem Statement & Objective
`MainActivityWithNavigation.java` (1,172 lines of Java code) serves as the central navigation cockpit and lifecycle orchestration hub of **aTrainingTracker**. It coordinates:
1. Activity lifecycle and background service bindings (`BANALService`, `BANALServiceRepository`).
2. Declarative Jetpack Compose navigation drawer (`AppNavigationDrawer.kt`, `NavigationDrawerController`).
3. Fragment backstack discipline and modal dialog routing (21 drawer items across 5 hubs).
4. System broadcast receivers (tracking status changes, ANT+ dependency alerts, workout termination).
5. Android 14/16 runtime permissions and battery optimization handling.
6. Interrupted workout recovery and unfinished session detection (`handleIntent`, `StartOrResumeDialog`).

Migrating this foundational class to idiomatic Kotlin (`MainActivityWithNavigation.kt`) is necessary to eliminate nullability risks, unify the UI layer with modern Jetpack Compose paradigms, and enhance maintainability, while strictly preserving 100% binary and behavioral interoperability with all existing Java callers (`TrackerService.java`, `TrainingApplication.java`, `BaseExporter.java`).

---

## User Review Required
> [!IMPORTANT]
> - **Java Interoperability**: Companion object constants (`SELECTED_FRAGMENT_ID`, `SELECTED_FRAGMENT`, `EXTRA_RESUME_INTERRUPTED_WORKOUT`) MUST remain annotated with `@JvmField` so existing Java callers compile without code modifications.
> - **Enum Parity**: `SelectedFragment` enum (`START_OR_TRACKING`, `WORKOUT_LIST`) MUST be defined within `MainActivityWithNavigation`.
> - **Back Gesture Invariant**: The predictive back callback MUST prioritize closing the navigation drawer if open before popping the fragment backstack or navigating to root.

---

## Proposed Architectural Changes

### 1. [NEW] [MainActivityWithNavigation.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt)
Migrate `MainActivityWithNavigation` from Java to idiomatic Kotlin:
- **Class Signature & Interfaces**:
  ```kotlin
  class MainActivityWithNavigation : AppCompatActivity(),
      BANALService.GetBanalServiceInterface,
      PreferenceFragmentCompat.OnPreferenceStartScreenCallback,
      StartOrResumeInterface
  ```
- **Companion Object & Enums**:
  ```kotlin
  companion object {
      @JvmField
      val SELECTED_FRAGMENT_ID = "SELECTED_FRAGMENT_ID"
      @JvmField
      val SELECTED_FRAGMENT = "SELECTED_FRAGMENT"
      @JvmField
      val EXTRA_RESUME_INTERRUPTED_WORKOUT = "com.atrainingtracker.EXTRA_RESUME_INTERRUPTED_WORKOUT"

      private const val TAG = "MainActivityWithNavigat"
      private const val DEFAULT_SELECTED_FRAGMENT_ID = R.id.drawer_start_tracking
      private const val REQUEST_INSTALL_GOOGLE_PLAY_SERVICE = 2
      private const val MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1
      private const val WAITING_TIME_BEFORE_DISCONNECTING = 5L * 60 * 1000 // 5 min
      private const val CRITICAL_BATTERY_LEVEL = 30
  }

  enum class SelectedFragment {
      START_OR_TRACKING,
      WORKOUT_LIST
  }
  ```
- **State & Service Binding**:
  - `mBanalServiceComm: BANALService.BANALServiceComm? = null`
  - `mConnectionStatusListeners = LinkedList<BANALService.GetBanalServiceInterface.ConnectionStatusListener>()`
  - `mHandler = Handler(Looper.getMainLooper())`
  - `mDisconnectFromBANALServiceRunnable`: Posts delayed service disconnection in `onPause()`, canceled in `onResume()`, and executed immediately in `onDestroy()`.
- **Broadcast Receivers (6 Receivers)**:
  - `mStartTrackingReceiver`: Adapts drawer tracking label to `R.string.Tracking`.
  - `mPauseTrackingReceiver`: Adapts drawer tracking label to `R.string.Pause`.
  - `mStopTrackingReceiver`: Adapts drawer tracking label to `R.string.Start` and triggers `checkBatteryStatus()`.
  - `mTrackingStoppedReceiver`: Selects `R.id.drawer_workouts` and navigates to workouts list.
  - `mAntDependencyReceiver`: Invokes `showSpecificInstallANTDialog()`.
  - `mAntAdapterMissingReceiver`: Invokes `showANTAdapterMissingDialog()`.
  - Registered in `onResume()` via `ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)` and unregistered in `onPause()` with `IllegalArgumentException` guards.
- **Window Insets & Edge-to-Edge**:
  - `EdgeToEdge.enable(this)` in `onCreate()`.
  - Apply window insets listener to `DrawerLayout` applying horizontal system bars padding while delegating top insets to Compose.
  - Set light status bars via `WindowCompat.getInsetsController`.
  - Forward insets to `findViewById<View>(R.id.compose_nav_view)` via `ViewCompat.dispatchApplyWindowInsets`.
- **Backstack & Navigation Dispatch**:
  - `navigateToDrawerItem(itemId: Int): Boolean`: Handles 21 items across 5 hubs, executing `popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)` prior to top-level fragment replacements.
  - `startPairing(protocol: Protocol, deviceType: DeviceType?)`: Public method retained for `TrackingTabsScreen.kt`.
  - Predictive back dispatcher callback: Dismisses drawer if open/visible $\rightarrow$ pops fragment backstack $\rightarrow$ navigates to root start tracking $\rightarrow$ finishes activity.
- **Resumption & Start/Resume Protocol**:
  - `handleIntent(intent)`: Detects `EXTRA_RESUME_INTERRUPTED_WORKOUT`, cancels notification, selects start tracking, activates `setResumeFromCrash(true)`, and triggers `chooseResume()`.
  - `checkUnfinishedWorkout()`: Detects `hasUnfinishedWorkout()` and displays `StartOrResumeDialog`.
  - `chooseStart()`: Discards unfinished workout and marks `setResumeFromCrash(false)`.
  - `chooseResume()`: Marks `setResumeFromCrash(true)` and broadcasts `REQUEST_START_TRACKING`.

### 2. [DELETE] [MainActivityWithNavigation.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.java)
- Remove the legacy Java source file once `MainActivityWithNavigation.kt` is established.

### 3. [NEW] [MainActivityWithNavigationInteropTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/MainActivityWithNavigationInteropTest.kt)
- Unit test suite verifying:
  1. Companion object `@JvmField` constants (`SELECTED_FRAGMENT_ID`, `SELECTED_FRAGMENT`, `EXTRA_RESUME_INTERRUPTED_WORKOUT`) accessible with expected string values.
  2. `SelectedFragment` enum values (`START_OR_TRACKING`, `WORKOUT_LIST`) and name matches for Intent extras.
  3. Interface compliance and public method accessibility.

---

## Traceability Matrix
| Requirement ID | Summary | Affected Components | Test Specification | Status |
| :--- | :--- | :--- | :--- | :--- |
| **REQ-UI-124** | **Kotlin Modernization of Navigation Activity (`MainActivityWithNavigation`).** Migrate activity to Kotlin with 100% binary/lifecycle parity, `@JvmField` interop, and interface compliance. | `MainActivityWithNavigation.kt`, `MainActivityWithNavigation.java` | **TST-NAV-008** (`ATT-658`) | In Progress |
| **REQ-UI-123** | **Declarative Compose Navigation Drawer & High-Density Presentation.** Compose drawer hosting and insets dispatching. | `AppNavigationDrawer.kt`, `MainActivityWithNavigation.kt` | **TST-NAV-007** | Verified |
| **REQ-SET-050** | **Navigation Drawer Structure.** Hub and item routing across 5 categories. | `MainActivityWithNavigation.kt` | **TST-NAV-001** | Verified |
| **REQ-SET-051..054** | **Modal Settings Interfaces.** Direct drawer access to Units, Display, Export, and Tracking Tabs dialogs. | `MainActivityWithNavigation.kt` | **TST-NAV-003..006** | Verified |
| **REQ-STB-003** | **Interrupted Workout Resumption & Unfinished Workout Recovery.** Intent handling and dialog restoration. | `MainActivityWithNavigation.kt` | **TST-STB-003** | Verified |
| **REQ-PRI-001** | **Permission Transparency.** Location and background permission requests. | `MainActivityWithNavigation.kt` | **TST-MAN-001** | Verified |

---

## Verification Plan

### Automated Tests
1. **Compilation & Build**:
   ```bash
   ./gradlew assembleDebug
   ```
2. **Dedicated Interoperability Unit Test**:
   ```bash
   ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.navigation.MainActivityWithNavigationInteropTest
   ```
3. **Full Regression Suite**:
   ```bash
   ./gradlew testDebugUnitTest
   ```

### On-Device Manual Verification (Google Pixel 10 - `66020DLCR002FL`)
1. **Install and Launch**:
   Install debug APK on Pixel 10 and launch app:
   `adb -s 66020DLCR002FL install -r app/build/outputs/apk/debug/app-debug.apk`
2. **Navigation Drawer & Dialog Verification**:
   - Open drawer: Verify smooth rendering of all 5 category hubs.
   - Test dialog dispatches: Units dialog, Display settings dialog, Export dialog, Activity Type selection.
   - Test fragment navigation: Workouts, Periods, Map, Segments, Routes, Locations, Sensors, Bikes, Shoes, Sport Types, Zones.
3. **Lifecycle & Resumption**:
   - Start tracking, background the app, resume from launcher.
   - Verify tracking status label reactively changes ("Start Tracking" $\rightarrow$ "Tracking" $\rightarrow$ "Pause" $\rightarrow$ "Start").
   - Test interrupted workout resumption via notification intent.
4. **Predictive Back Navigation**:
   - Open drawer, press back: Confirm drawer closes without app exiting.
   - Navigate to sub-fragment (e.g., Periods), press back: Confirm navigation returns to Start Tracking.
   - At root Start Tracking, press back: Confirm app cleanly exits.
