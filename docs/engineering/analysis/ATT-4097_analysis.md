# Stage 1 Analysis: ATT-4097 - Buffer one-off navigation events via Channel in TrackingTabsViewModel and ControlTrackingViewModel across STOPPED lifecycle

**Ticket**: [ATT-4097](https://rainerblind.atlassian.net/browse/ATT-4097)  
**Sub-task**: [ATT-4131](https://rainerblind.atlassian.net/browse/ATT-4131) (`[Analysis]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Stability & Resource Leaks*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-UI-412` (*Buffered One-Off Navigation Channels across STOPPED Lifecycle and Gesture Cancellation Safety*)  
**Branch**: `feature/ATT-4097`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Statement & Motivation

During the Sprint `2026-42.9` code review of `ATT-4000` (commit `84f8d3e8`, Finding 1 & 2), two lifecycle and interaction flaws in the Cockpit navigation event pipeline were identified:

1. **SharedFlow(replay = 0) Event Dropping During `STOPPED` Lifecycle**:
   In `TrackingTabsScreen.kt`, navigation event collectors are wrapped inside `lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)`:
   - `trackingTabsViewModel.navigationEvent.collect { ... }` (lines 446–478)
   - `controlViewModel.navigationEvent.collect { ... }` (lines 699–750)
   
   However, both ViewModels expose navigation events via `MutableSharedFlow`:
   - `TrackingTabsViewModel._navigationEvent = MutableSharedFlow<TabNavigationEvent>(extraBufferCapacity = 64)` (line 210)
   - `ControlTrackingViewModel._navigationEvent = MutableSharedFlow<ControlNavigation>(replay = 0)` (line 74)

   When the Activity enters the background (e.g. screen turned off, user switches to another app, or device is locked), Android's Lifecycle moves to `STOPPED`. Under `repeatOnLifecycle(Lifecycle.State.STARTED)`, the collection coroutine is cancelled and subscription stops.
   
   If a navigation event is triggered while in `STOPPED`—most notably:
   - Rising-edge tracking transition (`TrackingMode.TRACKING` triggered via Android notification action, Pebble smartwatch, or auto-resume) in `TrackingTabsViewModel.init` emitting `TabNavigationEvent.NavigateTo(0)` to switch the pager to the Cockpit tab;
   - Clicking a device or triggering recovery dialog in `ControlTrackingViewModel` right before or during a background pause;
   these events are emitted into a `SharedFlow` with `replay = 0` (or subscribers = 0). Because no active collector exists, the events are permanently lost. When the user resumes the app into `STARTED`, the pager does not navigate to the Cockpit tab or dialogs do not appear.

2. **Aggressive Fallback Overriding User Manual Swipe Interruption in `navigateToPagerTarget`**:
   In `TrackingTabsScreen.kt:963-976`:
   ```kotlin
   try {
       animateScrollToPage(target)
       Log.i(TAG, "Successfully animated to page $target")
   } catch (e: Exception) {
       currentCoroutineContext().ensureActive()
       Log.e(TAG, "animateScrollToPage failed: page $target not ready yet", e)
       try {
           scrollToPage(target)
           Log.i(TAG, "Fallback scrollToPage succeeded for page $target")
       } catch (e2: Exception) {
           currentCoroutineContext().ensureActive()
           Log.e(TAG, "Fallback scrollToPage also failed", e2)
       }
   }
   ```
   When `animateScrollToPage(target)` is in flight and the user touches the screen to swipe away, Jetpack Compose's `PagerState` cancels the scroll animation by throwing a `CancellationException`.
   The `catch (e: Exception)` block catches `CancellationException` (which extends `IllegalStateException` / `Exception` in Kotlin coroutines when caught broadly)! Although `currentCoroutineContext().ensureActive()` is checked, if the parent coroutine collecting navigation is still active, execution continues straight into the `catch` body and executes `scrollToPage(target)`!
   This hard-snaps the pager to `target`, completely overriding the user's manual swipe gesture and fighting user intent.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Concurrency & Lifecycle Semantics of Kotlin Coroutine Flow vs Channel

- `SharedFlow` is a broadcast hotspot designed for state broadcasting or multi-subscriber fan-out. With `replay = 0`, any emit while subscriber count is 0 is dropped immediately. Even with `extraBufferCapacity = 64`, unconsumed emissions are dropped when there are no active subscribers.
- In contrast, a Kotlin `Channel<T>(Channel.BUFFERED)` (or `Channel(capacity = Channel.BUFFERED)`) is a 1-to-1 queue with backpressure buffer. When an event is sent (`trySend` or `send`), it waits safely in the channel buffer regardless of whether a collector is currently connected.
- Exposing `Channel` as `channel.receiveAsFlow()` ensures:
  1. The collector consumes each event exactly once;
  2. Events emitted while the UI is `STOPPED` wait in the buffer and are delivered immediately when the collector re-subscribes upon entering `STARTED`;
  3. Subsequent re-subscriptions (e.g. Activity recreation or tab switching) do NOT re-deliver already consumed events (avoiding the duplicate replay pitfall of `SharedFlow(replay = 1)`).

### 2.2 Gesture Cancellation in Compose `PagerState`

- In Jetpack Compose `PagerState.animateScrollToPage(page)`:
  - If interrupted by user touch gesture (`pointerInput` drag/swipe), `animateScrollToPage` throws `kotlinx.coroutines.CancellationException`.
  - In Kotlin coroutines, `CancellationException` is an exception that should either be re-thrown or handled distinctly from standard operational errors (like layout index bounds).
  - The fallback `scrollToPage(target)` was originally introduced in `ATT-1340` / `ATT-4000` to handle rare cases where animation failed because a Compose sub-node was not yet attached. It was never intended to override user swipe interactions.
  - By catching `CancellationException` specifically and skipping the fallback `scrollToPage`, the user's manual gesture is respected while real operational exceptions still trigger the safe instant `scrollToPage` fallback.

---

## 3. Chesterton's Fence Requirement Archaeology

In accordance with `REQ-PRO-022`:

1. **Original Requirement ID & Target**:
   - `REQ-UI-163` (`ATT-1340`): Rising-edge automatic navigation to cockpit tab on tracking start.
   - `REQ-UI-410` (`ATT-4000`): Timeout-resilient cockpit tab navigation collector and lifecycle-safe dialog dispatch using `repeatOnLifecycle(Lifecycle.State.STARTED)`.
   - Targets: `TrackingTabsViewModel.kt`, `ControlTrackingViewModel.kt`, `TrackingTabsScreen.kt`, and `TrackingTabsNavigationTimeoutTest.kt`.

2. **Historical Origin & Commit Trace**:
   - `ATT-1340` (`3a9417e1b59fea6dd3e6556bad29a16f27a4011a`): Introduced `_navigationEvent = MutableSharedFlow(extraBufferCapacity = 64)`.
   - `ATT-4000` (`84f8d3e8`, Oct 18, 2026): Introduced `repeatOnLifecycle(STARTED)` to prevent background dialog crashes (`IllegalStateException: Can not perform this action after onSaveInstanceState`).
   - Sprint `2026-42.9` Code Review flagged that pairing `repeatOnLifecycle(STARTED)` with `MutableSharedFlow(replay = 0)` drops events during background mode transitions.

3. **Root Reason for Existing Formulation**:
   - `MutableSharedFlow` was chosen originally because it is the standard Kotlin Coroutines construct for UI events.
   - However, `SharedFlow` does not buffer events for future subscribers when `replay = 0`.
   - `CancellationException` was caught by broad `catch (e: Exception)` because in standard Java/Kotlin code, developers catch `Exception` without considering Compose's drag cancellation mechanism.

4. **Preservation of Core Invariants**:
   - Exactly-once consumption: One-off navigation events must NOT replay on configuration change or screen recreation.
   - Non-cancelling collector: Timeout or gesture cancellation must NOT cancel the parent collector coroutine.
   - Operational fallback: If animation fails due to Compose layout timing (non-cancellation `Exception`), fallback `scrollToPage` must still execute.
   - `canShowDialogFragments()` and `tailrec Context.findActivity()` from `REQ-UI-410` must be preserved 100%.

---

## 4. Proposed Architectural Solution

### 4.1 ViewModel Channel Architecture

In both `TrackingTabsViewModel.kt` and `ControlTrackingViewModel.kt`:
1. Replace `_navigationEvent = MutableSharedFlow<...>` with:
   ```kotlin
   import kotlinx.coroutines.channels.Channel
   import kotlinx.coroutines.flow.receiveAsFlow

   private val _navigationEvent = Channel<TabNavigationEvent>(Channel.BUFFERED)
   val navigationEvent = _navigationEvent.receiveAsFlow()
   ```
   and
   ```kotlin
   private val _navigationEvent = Channel<ControlNavigation>(Channel.BUFFERED)
   val navigationEvent = _navigationEvent.receiveAsFlow()
   ```
2. Update emission sites:
   - In `TrackingTabsViewModel`:
     - `onEditDevice(deviceId)`: `_navigationEvent.send(TabNavigationEvent.EditDevice(deviceId))` (or `trySend`).
     - `init` block rising-edge observer: `_navigationEvent.send(TabNavigationEvent.NavigateTo(0))` (or `trySend`).
   - In `ControlTrackingViewModel`:
     - `onDeviceClicked(device)`: `_navigationEvent.send(ControlNavigation.ToEditDevice(device.id, device.deviceType))` (or `trySend`).
     - `onStartTracking()`: `_navigationEvent.send(ControlNavigation.ShowRecoveryDialog)` (or `trySend`).

### 4.2 Safe Gesture Cancellation in `navigateToPagerTarget`

In `TrackingTabsScreen.kt:navigateToPagerTarget`:
```kotlin
if (target in 0 until finalPageCount) {
    onUpdateLastKnownPage(target)
    try {
        animateScrollToPage(target)
        Log.i(TAG, "Successfully animated to page $target")
    } catch (e: kotlinx.coroutines.CancellationException) {
        Log.i(TAG, "animateScrollToPage cancelled by user interaction or newer scroll; skipping fallback", e)
        // Do not execute fallback scrollToPage; preserve athlete's manual swipe position
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

## 5. Scope Bounding & Invariants

- **In Scope**:
  - `TrackingTabsViewModel.kt`: Convert `_navigationEvent` to `Channel<TabNavigationEvent>(Channel.BUFFERED).receiveAsFlow()`.
  - `ControlTrackingViewModel.kt`: Convert `_navigationEvent` to `Channel<ControlNavigation>(Channel.BUFFERED).receiveAsFlow()`.
  - `TrackingTabsScreen.kt`: Update `navigateToPagerTarget` to catch `CancellationException` and skip fallback `scrollToPage`.
  - Unit tests in `TrackingTabsNavigationTimeoutTest.kt`: Verify events emitted prior to collection (or during simulated unsubscription) are preserved and delivered upon collection; verify gesture cancellation does not trigger fallback scroll.
  - Living documentation updates (`docs/requirements.md` -> `REQ-UI-412`, `docs/tests.md` -> `TST-UI-375`).

- **Out of Scope**:
  - Modifying Pager animation physics, curves, or tween durations.
  - Changing dialog UI layouts or fragment hierarchies.
  - Modifying `BANALServiceRepository` tracking mode state emissions.

- **Invariants**:
  - Exactly-once delivery: No duplicate navigation events on recomposition or configuration change.
  - Non-blocking UI: Coroutine channel sends and receives must remain asynchronous.
  - 100% clean-room unit test suite pass rate.

---

## 6. Risk Assessment & Mitigation

| Risk | Impact | Likelihood | Mitigation Strategy |
| :--- | :--- | :--- | :--- |
| Channel buffer overflow if UI never attaches | Low | Low | `Channel.BUFFERED` has capacity 64, while navigation events are low-frequency user/lifecycle actions (typically 1–2 per session). |
| Collector cancellation rethrows unexpectedly | Medium | Low | `CancellationException` in `navigateToPagerTarget` is logged and cleanly absorbed so caller continues; coroutine hierarchy cancellation is respected via `ensureActive()`. |
| Regression in existing Cockpit navigation tests | Medium | Low | Run targeted and clean-room test suites (`./gradlew testDebugUnitTest`). |

---

## 7. Verification Strategy

1. **Targeted Unit Tests (`TrackingTabsNavigationTimeoutTest.kt`)**:
   - `testBufferedNavigationEvents_persistedAcrossStoppedLifecycle_deliveredOnNextCollection`: Emit navigation event while no collector is active (simulating `STOPPED` lifecycle state), start collector, assert event is received.
   - `testBufferedNavigationEvents_consumedExactlyOnce`: Verify that when a second collection occurs, previously received one-off events are not re-delivered.
   - `testNavigateToPagerTarget_whenAnimateCancelledByGesture_doesNotTriggerFallbackScroll`: Simulate `animateScrollToPage` throwing `CancellationException`, assert fallback `scrollToPage` is NOT invoked and function completes cleanly.
2. **Clean-Room Full Suite Regression Execution**:
   - Run `./gradlew testDebugUnitTest` across all 3,193+ tests asserting 100% pass rate.
