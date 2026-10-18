# Stage 3: Implementation Plan - ATT-4000: [High] Cockpit tab navigation stops working after page-count timeout

**Ticket**: [ATT-4000](https://rainerblind.atlassian.net/browse/ATT-4000)  
**Sub-task**: [ATT-4086](https://rainerblind.atlassian.net/browse/ATT-4086) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Agile ASPICE Engineering & Quality*)  
**Target Release**: `TBD (Assigned at Sprint Review)`  
**Active Sprint**: `2026-42.9`  
**Requirement Mapping**: `REQ-UI-410`, `REQ-UI-163`  
**Test Mapping**: `TST-UI-373`  
**Branch**: `feature/ATT-4000`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background

In [`TrackingTabsScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt), the `LaunchedEffect(Unit)` collecting `trackingTabsViewModel.navigationEvent` previously awaited `currentPagerState.pageCount > target` using `withTimeout(2000)` and threw `CancellationException("PageCountReady")` to break out of `snapshotFlow.collect`, rethrowing any `CancellationException` whose message was not `"PageCountReady"`. Because `withTimeout(2000)` throws `TimeoutCancellationException` (a subclass of `CancellationException`), a timeout was rethrown into `LaunchedEffect(Unit)`, permanently cancelling the `navigationEvent` collector coroutine for the rest of the screen's lifetime. Furthermore, `DialogFragment.show(supportFragmentManager, ...)` was called without `repeatOnLifecycle(Lifecycle.State.STARTED)` or `!supportFragmentManager.isStateSaved` guards, and `LocalContext.current` was hard-cast via `as AppCompatActivity`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-410` (*Timeout-Resilient Cockpit Tab Navigation Collector, Lifecycle-Safe Dialog Dispatch & Context Unwrapping (`TrackingTabsScreen.kt`)*), extending `REQ-UI-163`
* **Test Mapping**: `TST-UI-373` (*Timeout-Resilient Cockpit Tab Navigation Collector, Lifecycle-Safe Dialog Dispatch & `Context.findActivity()` Verification Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites (`TrackingTabsViewModelTest`, `TrackingTabsScreenLayoutTest`, `StopTrackingCoachmarkTest`) continue to pass cleanly.
2. **Outer Coroutine Cancellation Propagation**: Replacing `withTimeout` + `CancellationException` with `withTimeoutOrNull` + `Flow.first` and `currentCoroutineContext().ensureActive()` guarantees that outer scope cancellation (when `TrackingTabsScreen` leaves composition or transitions below `Lifecycle.State.STARTED`) is never swallowed.
3. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
4. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `Context.findActivity()` & `AppCompatActivity?.canShowDialogFragments()` (`TrackingTabsScreen.kt`)
* Add KDoc-documented `internal tailrec fun Context.findActivity(): AppCompatActivity?`:
  ```kotlin
  /**
   * Safely unwraps nested [ContextWrapper] instances to locate the hosting [AppCompatActivity],
   * returning `null` if this [Context] is not backed by an [AppCompatActivity] (REQ-UI-410.3).
   */
  internal tailrec fun Context.findActivity(): AppCompatActivity? = when (this) {
      is AppCompatActivity -> this
      is ContextWrapper -> baseContext?.findActivity()
      else -> null
  }
  ```
* Add KDoc-documented `internal fun AppCompatActivity?.canShowDialogFragments(): Boolean`:
  ```kotlin
  /**
   * Returns `true` if and only if the [AppCompatActivity] is non-null, not finishing, not destroyed,
   * and its [androidx.fragment.app.FragmentManager] has not yet saved its instance state (REQ-UI-410.2).
   */
  internal fun AppCompatActivity?.canShowDialogFragments(): Boolean =
      this != null && !isFinishing && !isDestroyed && !supportFragmentManager.isStateSaved
  ```
* In `TrackingTabsScreen`:
  * Obtain `val context = LocalContext.current` and `val activity = remember(context) { context.findActivity() }`.
  * Pass `activity` to `BatterySaverController(activity = activity)` (`BatterySaverController` already accepts `Activity?`).
  * Use `(activity as? MainActivityWithNavigation)` for `navigateToDrawerItem` and `openDrawer()`.
  * Use `context.applicationContext as Application` in `ControlTrackingViewModel` factory.
  * Guard window insets restoration in `DisposableEffect(activity, isSystemDark)` with `if (activity != null && !activity.isFinishing && !activity.isDestroyed)`.

### Component 2: Timeout-Resilient Pager Navigation Helper `navigateToPagerTarget` (`TrackingTabsScreen.kt`)
* Extract the `TabNavigationEvent.NavigateTo` handling into an `internal suspend fun navigateToPagerTarget(...)` in `TrackingTabsScreen.kt`:
  ```kotlin
  /**
   * Calculates the target pager index for [tabIndex] in [screenMode], awaits `pageCount > target`
   * up to [timeoutMs] using [withTimeoutOrNull] and [first] so a page-count timeout returns `false`
   * cleanly without throwing [kotlinx.coroutines.TimeoutCancellationException] (REQ-UI-410.1),
   * updates [onUpdateLastKnownPage], and scrolls the pager with fallback from `animateScrollToPage`
   * to `scrollToPage`.
   */
  internal suspend fun navigateToPagerTarget(
      tabIndex: Int,
      screenMode: ScreenMode,
      currentPageCount: () -> Int,
      pageCountFlow: Flow<Int>,
      onUpdateLastKnownPage: (Int) -> Unit,
      animateScrollToPage: suspend (Int) -> Unit,
      scrollToPage: suspend (Int) -> Unit,
      timeoutMs: Long = 2000L
  ): Boolean
  ```
* Implementation details:
  1. `val offset = if (screenMode == ScreenMode.TRACKING) 1 else 0`
  2. `val target = tabIndex + offset`
  3. If `target < 0`, log warning and return `false`.
  4. If `currentPageCount() <= target`:
     ```kotlin
     val readyCount = withTimeoutOrNull(timeoutMs) {
         pageCountFlow.first { count -> count > target }
     }
     if (readyCount == null) {
         Log.w(TAG, "Timed out waiting for pageCount > $target (current=${currentPageCount()})")
         return false
     }
     ```
  5. Verify `target in 0 until currentPageCount()`. If true:
     * Call `onUpdateLastKnownPage(target)` (`lastKnownPage = target`).
     * Call `animateScrollToPage(target)` inside `try / catch (e: Exception)` where `currentCoroutineContext().ensureActive()` is called first in `catch` so outer coroutine cancellation is never swallowed, followed by fallback `scrollToPage(target)` (also checking `currentCoroutineContext().ensureActive()`).
     * Return `true`.

### Component 3: Lifecycle-Aware Navigation Collectors (`TrackingTabsScreen.kt`)
* Wrap `trackingTabsViewModel.navigationEvent.collect` (lines 431–491) in `lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)`:
  * For `TabNavigationEvent.NavigateTo`: delegate to `navigateToPagerTarget(...)` with `pageCountFlow = snapshotFlow { currentPagerState.pageCount }`.
  * For `TabNavigationEvent.EditDevice`: guard with `if (activity.canShowDialogFragments())` before creating and showing `EditDeviceDialog`.
* Wrap `controlViewModel.navigationEvent.collect` (lines 712–740) in `lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)` and guard `EditDeviceDialog` and `StartOrResumeDialog` with `if (activity.canShowDialogFragments())`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **No visual UI layout, spacing, shape, or color changes.** This ticket fixes coroutine cancellation, `FragmentManager` lifecycle safety, and `ContextWrapper` unwrapping in `TrackingTabsScreen.kt`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Gate 3 Pre-Check (Rule 4)
* Command: `python3 tools/jira_util.py check-gate ATT-4086`
* Verify exit code `0` (`GATE_PASSED`) before modifying any production code under `app/src/...`.

### Step 2: Update `TrackingTabsScreen.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`
* Changes:
  1. Add `Context.findActivity(): AppCompatActivity?`, `AppCompatActivity?.canShowDialogFragments(): Boolean`, and `navigateToPagerTarget(...)`.
  2. Replace `val context = LocalContext.current as androidx.appcompat.app.AppCompatActivity` with `val context = LocalContext.current` and `val activity = remember(context) { context.findActivity() }`.
  3. Collect `trackingTabsViewModel.navigationEvent` and `controlViewModel.navigationEvent` inside `lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)` using `navigateToPagerTarget` and `activity.canShowDialogFragments()`.

### Step 3: Add Targeted Unit & Contract Tests (`TrackingTabsNavigationTimeoutTest.kt`)
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsNavigationTimeoutTest.kt`
* Changes:
  1. Implement `TST-UI-373.1` (`testNavigationCollector_survivesPageCountTimeout_andNavigatesOnSubsequentEvent`).
  2. Implement `TST-UI-373.2` (`testNavigateToPagerTarget_waitsForPageCountIncreaseWithinTimeout_andFallsBackToScrollToPage`).
  3. Implement `TST-UI-373.3` (`testFindActivity_andCanShowDialogFragments_lifecycleAndWrapperSafety`).

### Step 4: Execute Targeted Unit Tests
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.*"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests in Stage 4 (`TrackingTabsNavigationTimeoutTest`, `TrackingTabsViewModelTest`, `TrackingTabsScreenLayoutTest`, `StopTrackingCoachmarkTest`), followed by full clean-room regression suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-4000` allows clean revert without affecting `sprint/2026-42.9` or `develop`.
