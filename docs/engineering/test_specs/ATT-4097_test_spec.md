# Stage 2: Requirement & Test Specification - ATT-4097: Buffer one-off navigation events via Channel in TrackingTabsViewModel and ControlTrackingViewModel across STOPPED lifecycle

**Ticket**: [ATT-4097](https://rainerblind.atlassian.net/browse/ATT-4097)  
**Sub-task**: [ATT-4132](https://rainerblind.atlassian.net/browse/ATT-4132) (`[Spec]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Stability & Resource Leaks*)  
**Target Release**: `None` (Assigned upon Sprint Review completion per Rule 14)  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-UI-412` (*Buffered One-Off Navigation Channels across STOPPED Lifecycle and Gesture Cancellation Safety*)  
**Test Spec ID**: `TST-UI-375`  
**Branch**: `feature/ATT-4097`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Requirement Specification (`REQ-UI-412`)

### 1.1 Problem Statement & Rationale
In `TrackingTabsScreen.kt`, navigation event collectors are embedded inside `lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)` to prevent background dialog creation crashes (`IllegalStateException`).
However, because `TrackingTabsViewModel._navigationEvent` and `ControlTrackingViewModel._navigationEvent` were implemented as `MutableSharedFlow(replay = 0)`, any event emitted while the Activity is in `STOPPED` (such as rising-edge tracking start navigation triggered from the notification action or Pebble watchapp, or clicking device edit right before backgrounding) is dropped because no subscriber is active.
Furthermore, in `navigateToPagerTarget`, when a user manually swipes while an automated scroll animation is in flight, Jetpack Compose throws `CancellationException`. Catching `Exception` broadly previously caused the catch block to run fallback `scrollToPage(target)`, overriding the user's swipe and hard-snapping the pager back to `target`.

### 1.2 Functional & Architectural Requirements
`TrackingTabsViewModel` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsViewModel.kt`), `ControlTrackingViewModel` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingViewModel.kt`), and `TrackingTabsScreen.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsScreen.kt`) SHALL buffer one-off navigation events via Kotlin coroutine `Channel`s across the Android `STOPPED` lifecycle state, consume each event exactly once upon resuming `STARTED` collection without duplicate replay, and cleanly respect user touch swipe interruptions during pager animations without triggering fallback instant snaps (`ATT-4097`, extending `REQ-UI-163` and `REQ-UI-410`):

1. **Channel-Buffered One-Off Navigation Events in ViewModels (`REQ-UI-412.1`)**:
   - In `TrackingTabsViewModel`, `_navigationEvent` SHALL be declared as `Channel<TabNavigationEvent>(Channel.BUFFERED)` and exposed as `val navigationEvent = _navigationEvent.receiveAsFlow()`.
   - In `ControlTrackingViewModel`, `_navigationEvent` SHALL be declared as `Channel<ControlNavigation>(Channel.BUFFERED)` and exposed as `val navigationEvent = _navigationEvent.receiveAsFlow()`.
   - Navigation event emissions (`onEditDevice`, rising-edge `TRACKING` start in `TrackingTabsViewModel`; `onDeviceClicked`, `onStartTracking` recovery dialog in `ControlTrackingViewModel`) SHALL send events via the channel buffer, guaranteeing that events emitted while subscribers are suspended (Activity in `Lifecycle.State.STOPPED`) remain buffered in memory without being dropped.

2. **Exactly-Once Lifecycle Delivery & Re-Subscription Safety (`REQ-UI-412.2`)**:
   - In `TrackingTabsScreen.kt`, `trackingTabsViewModel.navigationEvent` and `controlViewModel.navigationEvent` collectors running inside `lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)` SHALL consume buffered events as soon as the Activity enters `STARTED`.
   - Because channels do not broadcast or retain replay caches once consumed, re-subscribing (e.g. Activity recreation or screen navigation) SHALL NOT re-deliver previously handled events.

3. **User Gesture Cancellation Resilience in `navigateToPagerTarget` (`REQ-UI-412.3`)**:
   - In `navigateToPagerTarget` (`TrackingTabsScreen.kt`), `animateScrollToPage(target)` SHALL catch `kotlinx.coroutines.CancellationException` specifically.
   - When a scroll animation is cancelled by a user manual drag/swipe gesture or subsequent pager interaction (`CancellationException`), `navigateToPagerTarget` SHALL log the cancellation and SHALL NOT execute the fallback `scrollToPage(target)`, preserving the user's manual swipe position without hard snapping.
   - Non-cancellation exceptions (`Exception`) encountered during animation SHALL continue to invoke the fallback `scrollToPage(target)` while propagating coroutine cancellation via `currentCoroutineContext().ensureActive()`.

---

## 2. Test Specification (`TST-UI-375`)

### 2.1 Test Cases

#### `TST-UI-375.1`: Channel Background Buffering across `STOPPED` Lifecycle Test
- **Target**: `TrackingTabsNavigationTimeoutTest.kt`
- **Methodology**:
  1. Emit navigation event (`TabNavigationEvent.NavigateTo(0)` or `TabNavigationEvent.EditDevice(42L)`) in `TrackingTabsViewModel` while no collector is active (simulating an Activity in `STOPPED` where `repeatOnLifecycle(STARTED)` cancels collection).
  2. Start a collector simulating resumption to `Lifecycle.State.STARTED`.
  3. Verify that the buffered event is received and processed immediately upon subscription.

#### `TST-UI-375.2`: ControlTrackingViewModel Background Buffering Test
- **Target**: `TrackingTabsNavigationTimeoutTest.kt` (or `ControlTrackingNavigationTest.kt`)
- **Methodology**:
  1. Emit `ControlNavigation.ToEditDevice` and `ControlNavigation.ShowRecoveryDialog` in `ControlTrackingViewModel` with zero subscribers.
  2. Start collecting `viewModel.navigationEvent`.
  3. Verify each event is received in FIFO order without loss.

#### `TST-UI-375.3`: Exactly-Once Consumption & Re-Subscription Cleanliness Test
- **Target**: `TrackingTabsNavigationTimeoutTest.kt`
- **Methodology**:
  1. After the first collector consumes the event, cancel collection (simulating screen stop/recreation).
  2. Start a second collector.
  3. Verify that zero replay events are received (unlike `SharedFlow(replay = 1)`).

#### `TST-UI-375.4`: User Gesture Cancellation Resilience Test
- **Target**: `TrackingTabsNavigationTimeoutTest.kt`
- **Methodology**:
  1. In `navigateToPagerTarget`, simulate `animateScrollToPage(target)` throwing `kotlinx.coroutines.CancellationException` (e.g. user manual swipe interrupt).
  2. Verify `navigateToPagerTarget` returns cleanly without throwing an unhandled exception.
  3. Verify that fallback `scrollToPage(target)` is NOT invoked, preserving the user's manual swipe position.

#### `TST-UI-375.5`: Non-Cancellation Exception Fallback Scroll Test
- **Target**: `TrackingTabsNavigationTimeoutTest.kt`
- **Methodology**:
  1. In `navigateToPagerTarget`, simulate `animateScrollToPage(target)` throwing a standard `IllegalStateException` or `RuntimeException`.
  2. Verify that fallback `scrollToPage(target)` IS executed.

#### `TST-UI-375.6`: Clean-Room Full Suite Regression Execution
- **Target**: Clean-room environment
- **Methodology**:
  1. Execute `./gradlew testDebugUnitTest`.
  2. Assert 100% test pass rate across all 3,193+ tests with zero failures or regressions.

---

## 3. Requirement to Test Traceability Matrix

| Requirement Clause | Test Case | Target Test Class / File | Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-UI-412.1` (Channel Background Buffering) | `TST-UI-375.1`, `TST-UI-375.2` | `TrackingTabsNavigationTimeoutTest.kt` | Unit test verifying unconsumed events wait in channel buffer across `STOPPED` |
| `REQ-UI-412.2` (Exactly-Once Delivery) | `TST-UI-375.3` | `TrackingTabsNavigationTimeoutTest.kt` | Unit test verifying zero duplicate replays on re-subscription |
| `REQ-UI-412.3` (User Gesture Cancellation) | `TST-UI-375.4`, `TST-UI-375.5` | `TrackingTabsNavigationTimeoutTest.kt` | Unit test verifying gesture `CancellationException` skips fallback `scrollToPage` |
| `REQ-PRO-001` (Clean-Room Full Suite) | `TST-UI-375.6` | `./gradlew testDebugUnitTest` | 100% clean-room unit test suite pass rate |
