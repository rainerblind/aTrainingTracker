# Walkthrough - ATT-657: Rewrite MainActivityWithNavigation in Kotlin

## Problem & Objective
Migrate `MainActivityWithNavigation.java` (1,172 lines of Java code) to idiomatic Kotlin (`MainActivityWithNavigation.kt`) to eliminate platform nullability hazards, modernize the core application navigation hub, and integrate smoothly with Jetpack Compose while strictly preserving 100% binary and behavioral interoperability with all existing Java callers (`TrackerService.java`, `TrainingApplication.java`, `BaseExporter.java`).

Fulfills requirement [`REQ-UI-124`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L244) and test specification [`TST-NAV-008`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L51).

---

## Key Changes

### 1. Activities & Navigation

#### [NEW] [`MainActivityWithNavigation.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt)
- **Hierarchy & Interface Compliance**:
  - Extends `AppCompatActivity`.
  - Implements `BANALService.GetBanalServiceInterface`, `PreferenceFragmentCompat.OnPreferenceStartScreenCallback`, and `StartOrResumeInterface`.
- **Java Interoperability**:
  - Constants `SELECTED_FRAGMENT_ID`, `SELECTED_FRAGMENT`, and `EXTRA_RESUME_INTERRUPTED_WORKOUT` defined as `@JvmField` in `companion object`.
  - Nested `SelectedFragment` enum (`START_OR_TRACKING`, `WORKOUT_LIST`) preserved.
- **Service Connection & Lifecycle**:
  - `BANALService` bound via `ServiceConnection` (`mBanalConnection`) with automatic filter registration.
  - 5-minute delayed unbind runnable (`mDisconnectFromBANALServiceRunnable`) posted in `onPause()`, removed in `onResume()`, and explicitly executed in `onDestroy()`.
- **Broadcast Receivers (6 Receivers)**:
  - `mStartTrackingReceiver`, `mPauseTrackingReceiver`, `mStopTrackingReceiver`, `mTrackingStoppedReceiver`, `mAntDependencyReceiver`, `mAntAdapterMissingReceiver` registered via `ContextCompat.RECEIVER_NOT_EXPORTED` in `onResume()` and unregistered in `onPause()` with `IllegalArgumentException` shielding.
- **Window Insets & Edge-to-Edge**:
  - `enableEdgeToEdge()` initialized in `onCreate()`.
  - Window insets listener applies horizontal system bar padding to `DrawerLayout` while delegating top status bar insets to Compose.
  - Forwards window insets to `compose_nav_view`.
- **Navigation Routing & Backstack**:
  - `navigateToDrawerItem(itemId: Int): Boolean`: Handles 21 drawer items across 5 hubs, executing `popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)` prior to top-level fragment transactions.
  - `OnBackPressedCallback`: Prioritizes closing navigation drawer if open $\rightarrow$ popping fragment backstack $\rightarrow$ returning to root Start tracking screen $\rightarrow$ finishing activity.
  - Public method `startPairing(protocol: Protocol, deviceType: DeviceType?)` preserved for `TrackingTabsScreen.kt`.
- **Workout Resumption Protocol**:
  - `handleIntent(intent)` detects `EXTRA_RESUME_INTERRUPTED_WORKOUT`, cancels notification, selects start tracking, activates `setResumeFromCrash(true)`, and triggers `chooseResume()`.
  - `checkUnfinishedWorkout()` displays `StartOrResumeDialog` if unfinalized sessions exist in SQLite.

#### [DELETE] [`MainActivityWithNavigation.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.java)
- Removed legacy Java implementation once Kotlin implementation was validated.

---

### 2. Unit Testing

#### [NEW] [`MainActivityWithNavigationInteropTest.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/MainActivityWithNavigationInteropTest.kt)
- Verifies companion object constants are accessible as public static fields via `@JvmField`.
- Verifies `SelectedFragment` enum constants and string representations.
- Verifies class hierarchy and interface compliance (`GetBanalServiceInterface`, `OnPreferenceStartScreenCallback`, `StartOrResumeInterface`).
- Verifies presence and visibility of public methods (`startPairing`, `navigateToDrawerItem`).

---

## Verification & Test Results

### 1. Unit Tests
* **Interoperability Unit Test**:
  ```bash
  ./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.navigation.MainActivityWithNavigationInteropTest
  # Result: BUILD SUCCESSFUL (PASSED)
  ```
* **Full Regression Suite**:
  ```bash
  ./gradlew testDebugUnitTest
  # Result: BUILD SUCCESSFUL (PASSED) - 100% test pass rate across entire project
  ```

### 2. Physical Device Validation (Google Pixel 10 - `66020DLCR002FL`)
* **Debug APK Compilation & Installation**:
  ```bash
  ./gradlew assembleDebug
  adb -s 66020DLCR002FL install -r app/build/outputs/apk/debug/app-debug.apk
  # Result: Success
  ```
* **Runtime Verification**:
  1. *Activity Launch*: Activity initialized cleanly with Google Maps `LATEST` renderer and `BANALService` connected (`onServiceConnected` logged).
  2. *Navigation Drawer*: Drawer opened via swipe gesture with high-density compact presentation, header image, and crisp typography.
  3. *Back Gesture Invariant*: Back gesture dismissed the open drawer without exiting the app.
  4. *Fragment Navigation*: Tapping "Einheiten" successfully navigated to `WorkoutSummariesTabbedFragment`.
  5. *Back Navigation Hierarchy*: Back gesture from "Einheiten" returned cleanly to the root "Start tracking" screen.
  6. *Activity Termination*: Back gesture from root cleanly paused, stopped `BANALService`, disconnected, and destroyed the activity.
