# Stage 2: Requirement & Test Specification - ATT-4000: [High] Cockpit tab navigation stops working after page-count timeout

**Ticket**: [ATT-4000](https://rainerblind.atlassian.net/browse/ATT-4000)  
**Sub-task**: [ATT-4085](https://rainerblind.atlassian.net/browse/ATT-4085) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-232](https://rainerblind.atlassian.net/browse/ATT-232) (*Agile ASPICE Engineering & Quality*)  
**Target Release**: `TBD (Assigned at Sprint Review)`  
**Active Sprint**: `2026-42.9`  
**Requirement Mapping**: `REQ-UI-410` (*Timeout-Resilient Cockpit Tab Navigation Collector, Lifecycle-Safe Dialog Dispatch & Context Unwrapping*)  
**Test Spec ID**: `TST-UI-373`  
**Branch**: `feature/ATT-4000`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Requirement Specification (`REQ-UI-410`)

### 1.1 Problem Statement & Rationale
In `TrackingTabsScreen.kt`, the `LaunchedEffect(Unit)` collecting `trackingTabsViewModel.navigationEvent` previously awaited `currentPagerState.pageCount > target` using `withTimeout(2000)` and broke out of `snapshotFlow.collect` by throwing `CancellationException("PageCountReady")`, rethrowing any `CancellationException` whose message was not `"PageCountReady"`. Because `withTimeout(2000)` throws `TimeoutCancellationException` (a subclass of `CancellationException`), any timeout was rethrown into `LaunchedEffect(Unit)`, permanently terminating the `navigationEvent` collector coroutine for the rest of the screen's lifetime. Additionally, `DialogFragment.show(supportFragmentManager, ...)` was called without `repeatOnLifecycle(Lifecycle.State.STARTED)` or `!supportFragmentManager.isStateSaved` guards, and `LocalContext.current` was hard-cast via `as AppCompatActivity`.

### 1.2 Functional & Architectural Requirements
1. **Non-Cancelling Page-Count Timeout via `withTimeoutOrNull` & `first` (`REQ-UI-410.1`)**:
   * When handling `TabNavigationEvent.NavigateTo(index)` in `TrackingTabsScreen.kt` (via `navigateToPagerTarget`), the system SHALL calculate `target = index + (if (screenMode == ScreenMode.TRACKING) 1 else 0)`.
   * When `currentPageCount() <= target`, the system SHALL await `pageCount > target` using `kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { pageCountFlow.first { count -> count > target } }` (default `timeoutMs = 2000L`) instead of `withTimeout` + `CancellationException`.
   * If `withTimeoutOrNull` returns `null` (timeout), the system SHALL log a warning (`Log.w`) and return `false` cleanly without throwing `TimeoutCancellationException`, keeping the `navigationEvent` collector coroutine alive for all subsequent events, and SHALL NOT overwrite `lastKnownPage` with the unreachable `target`.
   * If `target in 0 until currentPageCount()`, the system SHALL update `lastKnownPage = target` and invoke `animateScrollToPage(target)` with fallback to `scrollToPage(target)` (propagating outer coroutine cancellation via `currentCoroutineContext().ensureActive()`).
2. **Lifecycle-Aware Event Collection & `isStateSaved` Dialog Guard (`REQ-UI-410.2`)**:
   * Both `trackingTabsViewModel.navigationEvent` and `controlViewModel.navigationEvent` collectors in `TrackingTabsScreen.kt` SHALL collect inside `lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED)`.
   * All `DialogFragment.show(...)` invocations (`EditDeviceDialog`, `StartOrResumeDialog`) SHALL be guarded by `activity.canShowDialogFragments()` (`this != null && !isFinishing && !isDestroyed && !supportFragmentManager.isStateSaved`).
3. **Safe `Context.findActivity()` Unwrapping (`REQ-UI-410.3`)**:
   * `TrackingTabsScreen.kt` SHALL replace the unsafe `LocalContext.current as AppCompatActivity` cast with `internal tailrec fun Context.findActivity(): AppCompatActivity?`, safely unwrapping `ContextWrapper` chains and returning `null` if no `AppCompatActivity` is present in the chain.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-410`, extending `REQ-UI-163` (*Cockpit Tab Navigation on Tracking Initiation*).
* **Historical Origin & Commit Trace**: Commit `3a9417e1b59fea6dd3e6556bad29a16f27a4011a` (`ATT-1340`).
* **Root Reason for Existing Formulation**: `ATT-1340` added a 2000 ms wait for `pageCount > target` using `withTimeout(2000)` and `CancellationException("PageCountReady")` to exit `collect`, overlooking that `TimeoutCancellationException` extends `CancellationException` and was therefore rethrown on timeout.
* **Preservation of Core Invariants**: Preserves all `REQ-UI-163` rising-edge tracking start navigation, `rememberUpdatedState(pagerState)` binding, `animateScrollToPage` with `scrollToPage` fallback, and outer scope cancellation semantics.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Post-Timeout Navigation Survival)**:
  * *Given* a `TabNavigationEvent.NavigateTo` event whose target page exceeds `pageCount` and times out after the bounded timeout window,
  * *When* a subsequent valid `TabNavigationEvent.NavigateTo` event arrives (or `pageCount` becomes ready),
  * *Then* the `navigationEvent` collector coroutine remains active and navigates the pager to the requested page.
* **Criterion 2 (Delayed Page-Count Readiness Within Timeout)**:
  * *Given* a `TabNavigationEvent.NavigateTo(0)` in `ScreenMode.TRACKING` (`target = 1`) where `pageCount` starts at `1` and increases to `4` before the timeout expires,
  * *When* `pageCount` updates to `4`,
  * *Then* `withTimeoutOrNull` + `first { it > target }` resolves immediately, updates `lastKnownPage = 1`, and scrolls the pager to page `1`.
* **Criterion 3 (Lifecycle & `isStateSaved` Dialog Guard)**:
  * *Given* an `AppCompatActivity` that is finishing, destroyed, or has `supportFragmentManager.isStateSaved == true`,
  * *When* `canShowDialogFragments()` is evaluated for `TabNavigationEvent.EditDevice` or `ControlNavigation`,
  * *Then* it returns `false` and skips `DialogFragment.show(...)` without throwing `IllegalStateException`.
* **Criterion 4 (`Context.findActivity()` Wrapper Unwrapping)**:
  * *Given* a `Context` wrapped in one or more `ContextWrapper` layers around an `AppCompatActivity` (or a plain non-Activity `Context`),
  * *When* `context.findActivity()` is called,
  * *Then* it returns the underlying `AppCompatActivity` (or `null` for a non-Activity `Context`) without throwing `ClassCastException`.

### 1.5 System Invariants
* Outer coroutine cancellation (when `TrackingTabsScreen` leaves composition or stops) MUST propagate via `ensureActive()`.
* Zero user-visible string changes; 9-language localization parity (`REQ-UI-106`) remains 100% intact.
* Zero regression across all existing unit and contract tests.

---

## 2. Test Specification (`TST-UI-373`)

### Test Case 1: `testNavigationCollector_survivesPageCountTimeout_andNavigatesOnSubsequentEvent` (`TST-UI-373.1`)
* **Scope**: Coroutine & Collector Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsNavigationTimeoutTest.kt`
* **Preconditions**: A `SharedFlow<TabNavigationEvent>` collector running `collectTabNavigationEvents` / `navigateToPagerTarget` with `pageCount = 1` in `ScreenMode.TRACKING`.
* **Action**:
  1. Emit `TabNavigationEvent.NavigateTo(2)` (`target = 3`), advance virtual time past the 2000 ms timeout while `pageCount` remains `1`.
  2. Update `pageCount = 4` and emit `TabNavigationEvent.NavigateTo(0)` (`target = 1`).
* **Expected Result**:
  1. First event times out cleanly (`scrolledPages` is empty, `lastKnownPage` is unchanged at `0`, collector job is still `isActive`).
  2. Second event is received by the same collector, updates `lastKnownPage = 1`, and scrolls the pager to page `1` (`scrolledPages == listOf(1)`).

### Test Case 2: `testNavigateToPagerTarget_waitsForPageCountIncreaseWithinTimeout_andFallsBackToScrollToPage` (`TST-UI-373.2`)
* **Scope**: Coroutine Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsNavigationTimeoutTest.kt`
* **Preconditions**: `pageCount` initially `1`, `target = 2`.
* **Action**:
  1. Launch `navigateToPagerTarget` with `target = 2`, advance 500 ms, then update `pageCountFlow` to `3`.
  2. Also test when `animateScrollToPage` throws a transient `IllegalStateException`, verifying fallback to `scrollToPage`.
* **Expected Result**: Resolves as soon as `pageCount` reaches `3`, updates `lastKnownPage = 2`, and executes `animateScrollToPage(2)` (or `scrollToPage(2)` on animation failure).

### Test Case 3: `testFindActivity_andCanShowDialogFragments_lifecycleAndWrapperSafety` (`TST-UI-373.3`)
* **Scope**: Unit & Structural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/TrackingTabsNavigationTimeoutTest.kt`
* **Preconditions**: Mocked `AppCompatActivity`, nested `ContextWrapper(ContextWrapper(activity))`, plain `Application` context, and mocked `FragmentManager` states (`isStateSaved = true/false`, `isFinishing = true/false`, `isDestroyed = true/false`).
* **Action**: Invoke `context.findActivity()` and `activity.canShowDialogFragments()`, and inspect `TrackingTabsScreen.kt` source contracts.
* **Expected Result**:
  * `findActivity()` unwraps nested `ContextWrapper`s to return `AppCompatActivity` and returns `null` for non-Activity `Context`s.
  * `canShowDialogFragments()` returns `false` when `activity == null`, `isFinishing`, `isDestroyed`, or `supportFragmentManager.isStateSaved == true`, and `true` only when active and state is not saved.
  * `TrackingTabsScreen.kt` uses `withTimeoutOrNull`, `repeatOnLifecycle(Lifecycle.State.STARTED)`, `findActivity()`, and `canShowDialogFragments()`, and contains zero `LocalContext.current as androidx.appcompat.app.AppCompatActivity` or `CancellationException("PageCountReady")` patterns.

### Test Case 4: 9-Language Localization & Specifier Audit (`TST-UI-373.4`)
* **Scope**: Localization Parity Test
* **Goal**: Verify zero regressions in `TranslationParityTest` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 5: Clean-Room Regression Suite (`TST-UI-373.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-373.1` | Unit | `navigateToPagerTarget` (timeout survival) | `REQ-UI-410.1`, `REQ-UI-163` | Specified |
| `TST-UI-373.2` | Unit | `navigateToPagerTarget` (readiness & fallback) | `REQ-UI-410.1`, `REQ-UI-163` | Specified |
| `TST-UI-373.3` | Unit / Contract | `Context.findActivity`, `canShowDialogFragments` | `REQ-UI-410.2`, `REQ-UI-410.3` | Specified |
| `TST-UI-373.4` | Localization | `TranslationParityTest` | `REQ-UI-106` | Specified |
| `TST-UI-373.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
