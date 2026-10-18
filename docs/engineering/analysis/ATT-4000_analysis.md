# Stage 1 Analysis: ATT-4000 - [High] Cockpit tab navigation stops working after page-count timeout

**Ticket**: [ATT-4000](https://rainerblind.atlassian.net/browse/ATT-4000)  
**Sub-task**: [ATT-4084](https://rainerblind.atlassian.net/browse/ATT-4084) (`[Analysis]`)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Agile ASPICE Engineering & Quality*)  
**Target Release**: `TBD (Assigned at Sprint Review)`  
**Active Sprint**: `2026-42.9`  
**Branch**: `feature/ATT-4000`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Statement & Motivation

In [`TrackingTabsScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt), the `LaunchedEffect(Unit)` block collecting `trackingTabsViewModel.navigationEvent` (lines 431–491) handles two primary navigation events:
1. `TabNavigationEvent.NavigateTo(index)` — triggered when tracking starts/resumes (jumping from Page 0 Control tab to Page 1 Cockpit tab), or when tabs are added/deleted in `ScreenMode.CONFIGURATION`.
2. `TabNavigationEvent.EditDevice(deviceId)` — triggered when an athlete taps a sensor chip in the `SensorStatus` header to open `EditDeviceDialog`.

Currently, three lifecycle and coroutine bugs exist in [`TrackingTabsScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt):
1. **Permanent Coroutine Cancellation on Page-Count Timeout (Lines 445–460)**: When `currentPagerState.pageCount <= target`, the collector awaits `pageCount > target` using `kotlinx.coroutines.withTimeout(2000)` and exits the `snapshotFlow.collect` early by throwing `CancellationException("PageCountReady")`, catching `CancellationException` and rethrowing if `e.message != "PageCountReady"`. Because `withTimeout(2000)` throws `TimeoutCancellationException` (which is a subclass of `CancellationException` with message `"Timed out waiting for 2000 ms"`), a timeout is caught by `catch (e: CancellationException)`, fails the `e.message != "PageCountReady"` check, and is **rethrown** into `LaunchedEffect(Unit)`. This cancels the `LaunchedEffect(Unit)` coroutine permanently for the remainder of `TrackingTabsScreen`'s composition lifetime. All subsequent `TabNavigationEvent.NavigateTo` and `TabNavigationEvent.EditDevice` emissions are silently ignored.
2. **Unprotected Fragment Transaction After `onSaveInstanceState` (Lines 479–488 & 715–738)**: `editDeviceDialog.show(context.supportFragmentManager, "EditDeviceDialog")` (and `StartOrResumeDialog().show(...)` on line 732) is invoked from an unconditional `LaunchedEffect(Unit)` collector without `repeatOnLifecycle(Lifecycle.State.STARTED)` and without checking `!activity.supportFragmentManager.isStateSaved`. If an event is collected after the Activity has saved its instance state (e.g. backgrounding or configuration transition), `DialogFragment.show()` throws a fatal `IllegalStateException: Can not perform this action after onSaveInstanceState`.
3. **Unsafe `LocalContext.current as AppCompatActivity` Cast (Line 121)**: `val context = LocalContext.current as androidx.appcompat.app.AppCompatActivity` performs an unchecked hard cast at the top of `TrackingTabsScreen`. If `LocalContext.current` is wrapped in a `ContextWrapper` (such as `ContextThemeWrapper`, locale wrapper, or Compose test rule context), this throws a `ClassCastException`.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Coroutine Death on `withTimeout(2000)` (`TrackingTabsScreen.kt:445-460`)

Inspecting `TrackingTabsScreen.kt` lines 445–460 (introduced in commit `3a9417e1b` / `ATT-1340`):
```kotlin
if (currentPagerState.pageCount <= target) {
    try {
        kotlinx.coroutines.withTimeout(2000) {
            androidx.compose.runtime.snapshotFlow { currentPagerState.pageCount }
                .collect { count ->
                    if (count > target) {
                        throw kotlinx.coroutines.CancellationException("PageCountReady")
                    }
                }
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        if (e.message != "PageCountReady") throw e
    } catch (e: Exception) {
        Log.w(TAG, "Timed out waiting for pageCount > $target", e)
    }
}
```
* **Failure Mechanism**:
  * In `kotlinx.coroutines`, `public class TimeoutCancellationException : CancellationException`.
  * The author of `ATT-1340` assumed `TimeoutCancellationException` would be caught by `catch (e: Exception)` on line 457 while `catch (e: CancellationException)` on line 455 would only catch either the synthetic `"PageCountReady"` exception or parent scope cancellation.
  * However, since `TimeoutCancellationException` is a `CancellationException` whose message is `"Timed out waiting for 2000 ms"`, it enters `catch (e: kotlinx.coroutines.CancellationException)` where `e.message != "PageCountReady"` is `true`, and `throw e` rethrows the `TimeoutCancellationException` out of the `trackingTabsViewModel.navigationEvent.collect` lambda!
  * Rethrowing a `CancellationException` inside `SharedFlow.collect` immediately terminates the `collect` call and completes the enclosing `LaunchedEffect(Unit)` coroutine as cancelled.
  * Furthermore, `lastKnownPage = target` on line 442 was mutated *before* verifying that `target in 0 until currentPagerState.pageCount`, leaving `lastKnownPage` pointing to an out-of-range page index if the wait times out.
* **Confirmation Status**: **CONFIRMED** by code inspection and coroutine exception hierarchy semantics (`TimeoutCancellationException : CancellationException`). Replacing `withTimeout` + `collect` + exception-for-control-flow with `withTimeoutOrNull(2000) { snapshotFlow { currentPagerState.pageCount }.first { it > target } }` cleanly returns `Int?` (`null` on timeout, or the ready count on success) without throwing `TimeoutCancellationException` while preserving outer coroutine cancellation.

### 2.2 Lifecycle & `isStateSaved` Vulnerability (`TrackingTabsScreen.kt:431-491, 712-740`)

* Both `trackingTabsViewModel.navigationEvent` (line 431) and `controlViewModel.navigationEvent` (line 712) are collected directly inside `LaunchedEffect(Unit)` without `lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)` and without checking `activity.supportFragmentManager.isStateSaved`.
* When the Activity transitions to `STOPPED` (`onStop()` / `onSaveInstanceState()`), `LaunchedEffect(Unit)` remains active as long as the composition is kept in memory. If a sensor tap or service state transition emits `TabNavigationEvent.EditDevice` or `ControlNavigation.ToEditDevice` / `ControlNavigation.ShowRecoveryDialog` while stopped, `DialogFragment.show(FragmentManager, String)` calls `FragmentTransaction.commit()`, which throws `IllegalStateException: Can not perform this action after onSaveInstanceState`.
* Collecting within `lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)` and guarding `DialogFragment` transactions with a dedicated helper `AppCompatActivity?.canShowDialogs(): Boolean` (`this != null && !isFinishing && !isDestroyed && !supportFragmentManager.isStateSaved`) eliminates this crash vector completely.

### 2.3 Hard Cast of `LocalContext.current` (`TrackingTabsScreen.kt:121`)

* Line 121 executes `val context = LocalContext.current as androidx.appcompat.app.AppCompatActivity`.
* In Android / Jetpack Compose (compare `Theme.kt:155-160`), a `Context` may be a `ContextWrapper` wrapping the underlying `AppCompatActivity` (or in unit/Compose preview tests, a non-`AppCompatActivity` `Context`).
* Providing an idiomatic `tailrec fun Context.findActivity(): AppCompatActivity?` helper that unwraps `ContextWrapper`s safely avoids `ClassCastException` and allows `TrackingTabsScreen` to operate safely even when wrapped in `ContextWrapper`s.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Replace `withTimeout(2000)` + `CancellationException("PageCountReady")` control-flow in [`TrackingTabsScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt) with `withTimeoutOrNull(2000) { snapshotFlow { currentPagerState.pageCount }.first { it > target } }` (extracted into a testable suspend helper function `awaitPagerTargetAndNavigate`) so a page-count timeout returns `null` cleanly and never cancels the `navigationEvent` collector coroutine.
  2. Update `lastKnownPage = target` only when `target in 0 until currentPagerState.pageCount` (or clamp safely) so an unreachable timeout target does not corrupt `lastKnownPage`.
  3. Collect `navigationEvent` flows in `TrackingTabsScreen.kt` using `lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)` and guard `DialogFragment.show(...)` calls with `!activity.isFinishing && !activity.isDestroyed && !activity.supportFragmentManager.isStateSaved` (`canShowDialogFragments()`).
  4. Replace `LocalContext.current as AppCompatActivity` with a safe `Context.findActivity(): AppCompatActivity?` extension helper that unwraps `ContextWrapper` instances.
  5. Add unit tests verifying that after a navigation event whose target page times out, subsequent navigation events still navigate the pager to the requested page, and verifying `findActivity()` and `canShowDialogFragments()` lifecycle safety.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying `TrackingTabsViewModel`'s `TabNavigationEvent` sealed class hierarchy or database schema.
  * Refactoring unrelated screens outside `TrackingTabsScreen.kt`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-163` (*Cockpit Tab Navigation on Tracking Initiation*) in [`docs/requirements.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L342).
* **Historical Origin & Commit Trace**: Introduced in commit `3a9417e1b59fea6dd3e6556bad29a16f27a4011a` (`ATT-1340`: *fix(ui): use rememberUpdatedState for pagerState and screenMode in navigation collection*).
* **Root Reason for Existing Formulation**: In `ATT-1340`, `TrackingTabsScreen` was updated to use `rememberUpdatedState(pagerState)` and wait up to 2000 ms for `currentPagerState.pageCount > target` when `trackingViews` were still loading asynchronously from SQLite on tracking start or tab creation. The author used `withTimeout(2000)` and threw `CancellationException("PageCountReady")` to break out of `snapshotFlow.collect`, inadvertently causing `TimeoutCancellationException` to be rethrown by `catch (e: CancellationException)` when a timeout actually occurred.
* **Preservation of Core Invariants**: We preserve all `REQ-UI-163` invariants (`rememberUpdatedState(pagerState)`, rising-edge `TrackingMode.TRACKING` navigation, 2000 ms bounded wait for `pageCount > target`, `animateScrollToPage` with `scrollToPage` fallback, and `extraBufferCapacity = 64` on `_navigationEvent`) while extending `REQ-UI-163` (and adding net-new requirement `REQ-UI-410`) so timeouts return `null` via `withTimeoutOrNull` + `first { it > target }` without cancelling the collector coroutine, and `DialogFragment` / `Context` operations are lifecycle- and wrapper-safe.

---

## 5. Architectural Strategy & High-Level Solution

1. **`Context.findActivity()` & `AppCompatActivity?.canShowDialogFragments()` Helpers (`TrackingTabsScreen.kt`)**:
   * Define `internal tailrec fun Context.findActivity(): AppCompatActivity?`:
     ```kotlin
     internal tailrec fun Context.findActivity(): AppCompatActivity? = when (this) {
         is AppCompatActivity -> this
         is ContextWrapper -> baseContext?.findActivity()
         else -> null
     }
     ```
   * Define `internal fun AppCompatActivity?.canShowDialogFragments(): Boolean = this != null && !isFinishing && !isDestroyed && !supportFragmentManager.isStateSaved`.
2. **Resilient Page-Count Await & Navigation (`awaitPageCountAndNavigate` / `TrackingTabsScreen.kt`)**:
   * Extract the navigation handling for `TabNavigationEvent.NavigateTo` into an `internal suspend fun handleTabNavigateTo(...)` (or `awaitPageCountReady(...)`) that uses:
     ```kotlin
     val isReady = if (pageCountProvider() > target) {
         true
     } else {
         kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
             pageCountFlow.first { count -> count > target }
         } != null
     }
     ```
   * When `isReady && target in 0 until currentPagerState.pageCount`: update `onTargetValidated(target)` (`lastKnownPage = target`) and execute `animateScrollToPage(target)` with fallback to `scrollToPage(target)` (re-throwing `CancellationException` only if the outer scope itself is cancelled, or letting coroutine cancellation propagate naturally when `currentCoroutineContext().ensureActive()`).
   * When `!isReady`: log a warning (`Log.w(TAG, "Timed out waiting for pageCount > $target (current=${pageCountProvider()})")`) and return cleanly so the `navigationEvent.collect` loop remains alive for all future events.
3. **Lifecycle-Aware Event Collection (`repeatOnLifecycle(Lifecycle.State.STARTED)`)**:
   * Wrap both `trackingTabsViewModel.navigationEvent.collect` and `controlViewModel.navigationEvent.collect` inside `lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)` so navigation and dialog events are only processed while the host lifecycle is at least `STARTED`, and guard all `DialogFragment.show(...)` calls with `if (activity.canShowDialogFragments())`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features and unit tests (`TrackingTabsViewModelTest`, `TrackingTabsScreenLayoutTest`, `StopTrackingCoachmarkTest`).
  2. Outer coroutine cancellation (when `TrackingTabsScreen` leaves composition or stops) is never swallowed (`ensureActive()`).
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  * The change is localized to `TrackingTabsScreen.kt` and its unit test suite, replacing an exception-based loop break with standard `kotlinx.coroutines.withTimeoutOrNull` + `Flow.first` and standard Android `ContextWrapper` / `FragmentManager.isStateSaved` guards.
