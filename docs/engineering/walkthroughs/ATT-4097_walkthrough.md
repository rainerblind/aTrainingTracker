# Stage 5 Walkthrough: ATT-4097 - Buffer one-off navigation events via Channel in TrackingTabsViewModel and ControlTrackingViewModel across STOPPED lifecycle

**Ticket**: [ATT-4097](https://rainerblind.atlassian.net/browse/ATT-4097)  
**Sub-task**: [ATT-4135](https://rainerblind.atlassian.net/browse/ATT-4135) (`[Test]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Stability & Resource Leaks*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-UI-412` (*Buffered One-Off Navigation Channels across STOPPED Lifecycle and Gesture Cancellation Safety*)  
**Test Spec ID**: `TST-UI-375`  
**Branch**: `feature/ATT-4097`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Overview & Verification Summary

During the Sprint `2026-42.9` code review of `ATT-4000`, two related event lifecycle and user gesture issues were identified in the tracking cockpit navigation flow:
1. **Navigation Event Dropping Across `STOPPED`**: In `TrackingTabsScreen.kt`, navigation event collectors were placed inside `lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)`. Because both `TrackingTabsViewModel._navigationEvent` and `ControlTrackingViewModel._navigationEvent` used `MutableSharedFlow(replay = 0)` (or unbuffered emissions without subscribers), one-off navigation events fired while the app was backgrounded (`STOPPED`)—such as rising-edge tracking start navigation triggered from the Android notification action, Pebble watchapp, or auto-resume, or clicking device edit right before backgrounding—were permanently lost.
2. **User Gesture Override in Pager Navigation**: In `TrackingTabsScreen.kt:navigateToPagerTarget()`, automated tab scrolling via `animateScrollToPage(target)` caught `Exception` broadly. When an athlete manually touched and swiped the screen while an automated animation was running, Jetpack Compose cancelled the animation by throwing `CancellationException`. Catching this broadly resulted in executing the fallback `scrollToPage(target)`, violently snapping the pager back and overriding the user's manual swipe gesture.

This implementation resolves both issues cleanly without regression:
- ViewModels now buffer navigation events via `Channel<T>(Channel.BUFFERED)` and expose them via `receiveAsFlow()`, guaranteeing events wait safely during `STOPPED` and are consumed exactly once upon resuming `STARTED` collection without duplicate replay.
- `navigateToPagerTarget()` catches `CancellationException` specifically and skips fallback `scrollToPage(target)`, allowing athlete swipe gestures to govern pager position. Real runtime exceptions continue to execute the fallback.

---

## 2. Changes Made

### 2.1 Production Code
1. **`TrackingTabsViewModel.kt`**:
   - Replaced `MutableSharedFlow<TabNavigationEvent>(extraBufferCapacity = 64)` with `Channel<TabNavigationEvent>(Channel.BUFFERED)`.
   - Exposed `val navigationEvent: Flow<TabNavigationEvent> = _navigationEvent.receiveAsFlow()`.
   - Updated emission points (`onEditDevice`, rising edge in `init`, `onDeleteTab`) to use `_navigationEvent.send(...)` within `viewModelScope`.
2. **`ControlTrackingViewModel.kt`**:
   - Replaced `MutableSharedFlow<ControlNavigation>(replay = 0)` with `Channel<ControlNavigation>(Channel.BUFFERED)`.
   - Exposed `val navigationEvent: Flow<ControlNavigation> = _navigationEvent.receiveAsFlow()`.
   - Updated emission points (`onDeviceClicked`, `onStartTracking` recovery dialog) to use `_navigationEvent.send(...)` within `viewModelScope`.
3. **`TrackingTabsScreen.kt`**:
   - In `navigateToPagerTarget()`, added dedicated `catch (e: kotlinx.coroutines.CancellationException)` blocks before general `catch (e: Exception)`.
   - Preserves manual swipe position when `animateScrollToPage()` is interrupted by the user while preserving the fallback `scrollToPage()` on genuine layout or attachment exceptions.

### 2.2 Test Code & Verification
1. **`TrackingTabsNavigationTimeoutTest.kt`**:
   - `TST-UI-375.1`: Verified channel buffers one-off events across uncollected `STOPPED` lifecycle and delivers them immediately upon collection.
   - `TST-UI-375.3`: Verified channel guarantees exactly-once delivery with zero replay on re-subscription.
   - `TST-UI-375.4`: Verified `navigateToPagerTarget()` skips fallback `scrollToPage()` when animation is cancelled by user gesture (`CancellationException`).
   - `TST-UI-375.5`: Verified `navigateToPagerTarget()` executes fallback `scrollToPage()` when a non-cancellation `Exception` occurs.
2. **`ControlTrackingViewModelTest.kt`**:
   - `TST-UI-375.2`: Verified `ControlTrackingViewModel` buffers one-off navigation events across `STOPPED` lifecycle and delivers them in FIFO order on collection.

---

## 3. Test Results & Traceability

| Test Case | Scope | Status |
| :--- | :--- | :--- |
| `TST-UI-375.1` | `TrackingTabsViewModel` background channel buffering across `STOPPED` | **PASSED** |
| `TST-UI-375.2` | `ControlTrackingViewModel` background channel buffering across `STOPPED` | **PASSED** |
| `TST-UI-375.3` | Exactly-once consumption with zero replay on re-subscription | **PASSED** |
| `TST-UI-375.4` | User swipe gesture cancellation resilience skipping fallback scroll | **PASSED** |
| `TST-UI-375.5` | Non-cancellation exception fallback scroll execution | **PASSED** |
| `TST-UI-375.6` | Full clean-room unit test suite (`./gradlew testDebugUnitTest`) | **PASSED** |

---

## 4. Living Documentation Parity

- `docs/requirements.md`: Added `REQ-UI-412` (*Buffered One-Off Navigation Channels across STOPPED Lifecycle and Gesture Cancellation Safety*) marked `Verified`.
- `docs/tests.md`: Added `TST-UI-375` (*Buffered One-Off Navigation Channels across STOPPED Lifecycle and Gesture Cancellation Safety Verification Suite*) marked `Verified`.
