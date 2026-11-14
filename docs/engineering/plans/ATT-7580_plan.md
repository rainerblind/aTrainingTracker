# Stage 3: Implementation Plan - ATT-7580: Use monotonic elapsedRealtime clock in RadarAlertManager and RadarSnoozeController

**Ticket**: [ATT-7580](https://rainerblind.atlassian.net/browse/ATT-7580)  
**Sub-task**: [ATT-7913](https://rainerblind.atlassian.net/browse/ATT-7913) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-TRK-063` (`REQ-TRK-063.1`), `REQ-TRK-064` (`REQ-TRK-064.1`)  
**Test Mapping**: `TST-TRK-063` (`TST-TRK-063.1`), `TST-TRK-064` (`TST-TRK-064.1`, `TST-TRK-064.8`)  
**Branch**: `feat/ATT-7580`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Description & Background
In `RadarAlertManager.kt` and `RadarSnoozeController.kt`:
1. **RadarAlertManager Debounce Wall-Clock Vulnerability**: `timeProvider` defaults to `{ System.currentTimeMillis() }`. Cooldowns (`DEBOUNCE_COOLDOWN_MS = 800L`, `CHIME_DURATION_MS = 350L`, lane-change cooldown `3000L`, urban congestion timestamps) rely on `timeProvider()`. When the Android system wall clock is adjusted backwards (e.g. via NTP synchronization, cellular network time updates, or manual timezone changes), `now - lastChimeTimeMs` evaluates to a negative number, indefinitely blocking subsequent alert chimes and debouncing until elapsed wall time catches up.
2. **RadarSnoozeController Expiry Wall-Clock Vulnerability & Test Leaks**: `RadarSnoozeController.timeProvider` defaults to `{ System.currentTimeMillis() }`. Calculating 15-minute / 30-minute snooze expiry via wall-clock time causes snoozes to fail to expire or expire prematurely upon wall-clock shifts. In addition, when test suites inject a mock `timeProvider` via `setTimeProvider { ... }`, invoking `reset()` fails to restore `timeProvider` back to default, causing state pollution across test classes.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-063` (*Radar Audio Chime and Haptic Alert Engine*)
  * `REQ-TRK-063.1`: Threat Transition Alerts, Target Selection & Monotonic Debouncing using `SystemClock.elapsedRealtime()`.
* **Requirement**: `REQ-TRK-064` (*1-Tap Radar Audio Snooze, Peloton-Mode Audio Sync & Smart Urban Alert Suppression*)
  * `REQ-TRK-064.1`: Lifecycle-Decoupled 1-Tap Cockpit Audio Snooze with strictly monotonic expiry tracking and clean reset.
* **Test Mapping**: `TST-TRK-063` & `TST-TRK-064`
  * `TST-TRK-063.1`: RadarAlertManager unit tests for monotonic clock resilience against backward wall-clock shifts.
  * `TST-TRK-064.1`: RadarSnoozeController unit tests for monotonic snooze expiry and test isolation reset.
  * `TST-TRK-064.8`: Targeted radar clean-room unit test suite.

---

## 3. System Invariants & Preserved Behavior
1. **Strict Monotonicity**: Time evaluation counts continuous elapsed real time since system boot, including deep sleep (`SystemClock.elapsedRealtime()`).
2. **JVM Test Environment Resilience**: In plain JUnit JVM test runners where `SystemClock` is not mocked, calling Android framework methods throws `RuntimeException`. The canonical `DEFAULT_TIME_PROVIDER` pattern gracefully catches exceptions and falls back to `System.nanoTime() / 1_000_000L`, ensuring tests run seamlessly without requiring Robolectric.
3. **Escalation Priority Preservation**: High-speed threat escalation (`-> FAST_APPROACHING`) continues to bypass routine debounce windows.
4. **Test Isolation**: Invoking `RadarSnoozeController.reset()` resets `timeProvider` back to `DEFAULT_TIME_PROVIDER`.
5. **Zero SQLite / Architecture Modifications**: No database migrations or architectural boundary changes.

---

## 4. Proposed Architectural Changes

### Component 1: `RadarAlertManager.kt`
* Add import `android.os.SystemClock`.
* Define canonical `DEFAULT_TIME_PROVIDER` in `companion object`:
  ```kotlin
  @Suppress("TooGenericExceptionCaught")
  val DEFAULT_TIME_PROVIDER: () -> Long = {
      try {
          SystemClock.elapsedRealtime()
      } catch (_: Throwable) {
          System.nanoTime() / 1_000_000L
      }
  }
  ```
* Update constructor parameter default value:
  ```kotlin
  private val timeProvider: () -> Long = DEFAULT_TIME_PROVIDER
  ```

### Component 2: `RadarSnoozeController.kt`
* Add import `android.os.SystemClock`.
* Define canonical `DEFAULT_TIME_PROVIDER` in `companion object`:
  ```kotlin
  @Suppress("TooGenericExceptionCaught")
  val DEFAULT_TIME_PROVIDER: () -> Long = {
      try {
          SystemClock.elapsedRealtime()
      } catch (_: Throwable) {
          System.nanoTime() / 1_000_000L
      }
  }
  ```
* Initialize `@Volatile private var timeProvider: () -> Long = DEFAULT_TIME_PROVIDER`.
* In `reset()`: add `timeProvider = DEFAULT_TIME_PROVIDER` inside `synchronized(lock)`.

### UI Consistency (Rule 23)
* No UI components modified.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `RadarAlertManager.kt` (`REQ-TRK-063.1`)
* Add `android.os.SystemClock` import.
* Define `DEFAULT_TIME_PROVIDER` in `companion object`.
* Change constructor default argument `timeProvider: () -> Long = DEFAULT_TIME_PROVIDER`.

### Step 2: Update `RadarSnoozeController.kt` (`REQ-TRK-064.1`)
* Add `android.os.SystemClock` import.
* Define `DEFAULT_TIME_PROVIDER` in `companion object`.
* Initialize `timeProvider = DEFAULT_TIME_PROVIDER`.
* Update `reset()` to restore `timeProvider = DEFAULT_TIME_PROVIDER`.

### Step 3: Add Unit Tests in `RadarAlertManagerTest.kt` (`TST-TRK-063.1`)
* Add unit test verifying default `timeProvider` uses monotonic clock and handles backward wall-clock shifts without freezing debouncing.

### Step 4: Add Unit Tests in `RadarSnoozeControllerTest.kt` (`TST-TRK-064.1`)
* Add unit test verifying default monotonic clock behavior and resilience against wall-clock backward jumps.
* Add unit test verifying `reset()` restores `timeProvider` to `DEFAULT_TIME_PROVIDER`.

### Step 5: Clean-Room Regression Verification (`TST-TRK-064.8`)
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.audio.radar.*" --tests "com.atrainingtracker.banalservice.devices.radar.*"`.
* Confirm 100% test pass rate.

---

## 6. Verification & Rollback Plan
* **Verification**: Clean execution of targeted unit test suites.
* **Rollback**: Isolated branch `feat/ATT-7580`.
