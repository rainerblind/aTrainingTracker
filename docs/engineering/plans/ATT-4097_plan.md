# Stage 3: Implementation Plan - ATT-4097: Buffer one-off navigation events via Channel in TrackingTabsViewModel and ControlTrackingViewModel across STOPPED lifecycle

**Ticket**: [ATT-4097](https://rainerblind.atlassian.net/browse/ATT-4097)  
**Sub-task**: [ATT-4133](https://rainerblind.atlassian.net/browse/ATT-4133) (`[Plan]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Stability & Resource Leaks*)  
**Target Release**: `None` (Assigned upon Sprint Review completion per Rule 14)  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-UI-412` (*Buffered One-Off Navigation Channels across STOPPED Lifecycle and Gesture Cancellation Safety*)  
**Test Mapping**: `TST-UI-375` (*Buffered One-Off Navigation Channels across STOPPED Lifecycle and Gesture Cancellation Safety Verification Suite*)  
**Branch**: `feature/ATT-4097`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background

In `TrackingTabsScreen.kt`, navigation event collectors are embedded inside `lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)` to prevent background dialog creation crashes (`IllegalStateException`).
However, because `TrackingTabsViewModel._navigationEvent` and `ControlTrackingViewModel._navigationEvent` were implemented as `MutableSharedFlow(replay = 0)` (or `extraBufferCapacity = 64` without subscribers), any event emitted while the Activity is in `Lifecycle.State.STOPPED` (such as rising-edge tracking start navigation triggered from the notification action or Pebble watchapp, or clicking device edit right before backgrounding) is dropped because no subscriber is active.
Furthermore, in `navigateToPagerTarget`, when a user manually swipes while an automated scroll animation is in flight, Jetpack Compose throws `CancellationException`. Catching `Exception` broadly previously caused the catch block to run fallback `scrollToPage(target)`, overriding the user's swipe and hard-snapping the pager back to `target`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-412` (*Buffered One-Off Navigation Channels across STOPPED Lifecycle and Gesture Cancellation Safety*)
  * `REQ-UI-412.1`: Channel-buffered one-off navigation events in `TrackingTabsViewModel` and `ControlTrackingViewModel` using `Channel<T>(Channel.BUFFERED).receiveAsFlow()`.
  * `REQ-UI-412.2`: Exactly-once delivery and re-subscription safety without duplicate replays on configuration change or screen recreation.
  * `REQ-UI-412.3`: User gesture cancellation resilience in `navigateToPagerTarget` (`TrackingTabsScreen.kt`), catching `CancellationException` and skipping fallback `scrollToPage(target)`.
* **Test Mapping**: `TST-UI-375` (`TST-UI-375.1` – `TST-UI-375.6`)

---

## 3. System Invariants & Preserved Behavior

1. **Non-Blocking Coroutines**: Channel emissions (`send` / `trySend`) and flow collections remain non-blocking on the caller thread.
2. **Exactly-Once Consumption**: One-off events are consumed once; subsequent subscribers receive zero replay of already handled events.
3. **Operational Error Fallback**: If an animation fails due to Compose layout timing or view detached errors (non-cancellation `Exception`), fallback `scrollToPage(target)` remains active.
4. **Lifecycle and Dialog Safety**: `repeatOnLifecycle(STARTED)`, `canShowDialogFragments()`, and `tailrec Context.findActivity()` from `REQ-UI-410` remain 100% intact.
5. **UI Consistency (Rule 23)**: No UI layout, color, typography, or theme changes are introduced; interaction resilience and event plumbing are preserved.
6. **One Commit per Ticket (Rule 44)**: Changes remain on `feature/ATT-4097` without intermediate commits until squashed into `sprint/2026-42.10` in Stage 5.

---

## 4. Proposed Architectural Changes (SWE.2)

### 4.1 Component 1: `TrackingTabsViewModel.kt`
- Replace `MutableSharedFlow<TabNavigationEvent>(extraBufferCapacity = 64)` with:
  ```kotlin
  import kotlinx.coroutines.channels.Channel
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.receiveAsFlow

  private val _navigationEvent = Channel<TabNavigationEvent>(Channel.BUFFERED)
  val navigationEvent: Flow<TabNavigationEvent> = _navigationEvent.receiveAsFlow()
  ```
- Update emission points:
  - `onEditDevice(deviceId)`: `_navigationEvent.send(TabNavigationEvent.EditDevice(deviceId))` (inside `viewModelScope.launch`).
  - `init` block rising edge: `_navigationEvent.send(TabNavigationEvent.NavigateTo(0))` (inside `viewModelScope.launch`).
  - `onDeleteTab(tabViewId)`: `_navigationEvent.send(TabNavigationEvent.NavigateTo(newTarget))` (inside `viewModelScope.launch`).

### 4.2 Component 2: `ControlTrackingViewModel.kt`
- Replace `MutableSharedFlow<ControlNavigation>(replay = 0)` with:
  ```kotlin
  import kotlinx.coroutines.channels.Channel
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.receiveAsFlow

  private val _navigationEvent = Channel<ControlNavigation>(Channel.BUFFERED)
  val navigationEvent: Flow<ControlNavigation> = _navigationEvent.receiveAsFlow()
  ```
- Update emission points:
  - `onDeviceClicked(device)`: `_navigationEvent.send(ControlNavigation.ToEditDevice(device.id, device.deviceType))` (inside `viewModelScope.launch`).
  - `onStartTracking()`: `_navigationEvent.send(ControlNavigation.ShowRecoveryDialog)` (inside `viewModelScope.launch`).

### 4.3 Component 3: `TrackingTabsScreen.kt`
- In `navigateToPagerTarget`:
  ```kotlin
  if (target in 0 until finalPageCount) {
      onUpdateLastKnownPage(target)
      try {
          animateScrollToPage(target)
          Log.i(TAG, "Successfully animated to page $target")
      } catch (e: kotlinx.coroutines.CancellationException) {
          Log.i(TAG, "animateScrollToPage cancelled by user gesture or concurrent scroll; skipping fallback", e)
      } catch (e: Exception) {
          currentCoroutineContext().ensureActive()
          Log.e(TAG, "animateScrollToPage failed: page $target not ready yet", e)
          try {
              scrollToPage(target)
              Log.i(TAG, "Fallback scrollToPage succeeded for page $target")
          } catch (e2: kotlinx.coroutines.CancellationException) {
              Log.i(TAG, "Fallback scrollToPage cancelled", e2)
          } catch (e2: Exception) {
              currentCoroutineContext().ensureActive()
              Log.e(TAG, "Fallback scrollToPage also failed", e2)
          }
      }
      return true
  }
  ```

---

## 5. Atomic Implementation Steps

### Step 1: Update `TrackingTabsViewModel.kt`
- Convert `_navigationEvent` to `Channel<TabNavigationEvent>(Channel.BUFFERED).receiveAsFlow()`.
- Ensure all calls use `_navigationEvent.send(...)` in coroutine scope.
- Verify existing `TrackingTabsViewModelTest.kt` passes.

### Step 2: Update `ControlTrackingViewModel.kt`
- Convert `_navigationEvent` to `Channel<ControlNavigation>(Channel.BUFFERED).receiveAsFlow()`.
- Ensure all calls use `_navigationEvent.send(...)` in coroutine scope.
- Verify existing `ControlTrackingViewModelTest.kt` passes.

### Step 3: Update `TrackingTabsScreen.kt`
- Differentiate `CancellationException` from general `Exception` in `navigateToPagerTarget()`.
- Skip fallback `scrollToPage(target)` when cancelled by user gesture.

### Step 4: Author Comprehensive Verification Tests in `TrackingTabsNavigationTimeoutTest.kt`
- Add `TST-UI-375.1`: Background buffering across `STOPPED` lifecycle in `TrackingTabsViewModel`.
- Add `TST-UI-375.2`: Background buffering in `ControlTrackingViewModel`.
- Add `TST-UI-375.3`: Exactly-once consumption without replay.
- Add `TST-UI-375.4`: Gesture `CancellationException` resilience skipping fallback scroll.
- Add `TST-UI-375.5`: Non-cancellation exception fallback scroll execution.

### Step 5: Clean-Room Regression Verification
- Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 6. Verification Commands

1. Targeted tests:
   ```bash
   ./gradlew testDebugUnitTest --tests "*TrackingTabs*" --tests "*ControlTracking*"
   ```
2. Clean-room full suite:
   ```bash
   ./gradlew testDebugUnitTest
   ```
