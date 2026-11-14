# Stage 1 Analysis: ATT-7580 - Use monotonic elapsedRealtime clock in RadarAlertManager and RadarSnoozeController

**Ticket**: [ATT-7580](https://rainerblind.atlassian.net/browse/ATT-7580)  
**Sub-task**: [ATT-7911](https://rainerblind.atlassian.net/browse/ATT-7911) (`[Analysis]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Branch**: `feat/ATT-7580`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
In `RadarAlertManager.kt:89` and `RadarSnoozeController.kt:46`, the default `timeProvider` is set to `{ System.currentTimeMillis() }`.
Because `System.currentTimeMillis()` tracks wall-clock time, external adjustments (NTP synchronization, cellular tower time updates, daylight saving transitions, or manual timezone changes) during a ride cause non-monotonic time deltas:
1. **Debounce and Cooldown Freezing**: In `RadarAlertManager.kt`, state transition debouncing (`DEBOUNCE_COOLDOWN_MS = 800L`), chime playback durations (`350L`), lane change cooldown (`3000L`), and smart urban congestion accumulators (`30_000L` congestion, `20_000L` clear) compare `now - lastTimestamp`. If the wall clock jumps backward (e.g. by 1 hour), `now - lastTimestamp` becomes negative, permanently freezing alert debouncing or congestion suppression for the duration of the jump.
2. **Snooze Expiration Invalidation**: In `RadarSnoozeController.kt`, timed snooze modes (15 min, 30 min) calculate `snoozeEndTimeMs = now + durationMs`. A forward wall-clock jump causes active snooze to expire prematurely, while a backward wall-clock jump extends snooze artificially.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Codebase Verification**:
  * Inspected `RadarAlertManager.kt:89`: Constructor parameter `private val timeProvider: () -> Long = { System.currentTimeMillis() }`.
  * Inspected `RadarSnoozeController.kt:46`: Property `@Volatile private var timeProvider: () -> Long = { System.currentTimeMillis() }`.
  * All internal timing comparisons across both classes evaluate relative elapsed durations ($\Delta t = t_2 - t_1$) rather than absolute wall-clock calendar times.
* **Independent Peer Review & Verification**:
  * Confirmed that Android's `SystemClock.elapsedRealtime()` provides a strictly monotonic millisecond time source that advances monotonically and includes deep sleep.
  * In standard JVM unit test environments, un-mocked `SystemClock` calls throw `RuntimeException`. `RadarTrafficSimulator.kt:88-95` already establishes the canonical monotonic pattern with JVM fallback:
    ```kotlin
    val DEFAULT_TIME_PROVIDER: () -> Long = {
        try {
            SystemClock.elapsedRealtime()
        } catch (_: Throwable) {
            System.nanoTime() / 1_000_000L
        }
    }
    ```
  * In `RadarSnoozeController.reset()`, `timeProvider` was not reset back to default, creating a potential leak of test time providers between unit test methods. `reset()` must restore `timeProvider = DEFAULT_TIME_PROVIDER`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Define `DEFAULT_TIME_PROVIDER` in `RadarAlertManager` companion object and `RadarSnoozeController`.
  * Default `timeProvider` parameter in `RadarAlertManager` constructor and `timeProvider` property in `RadarSnoozeController` to `DEFAULT_TIME_PROVIDER`.
  * In `RadarSnoozeController.reset()`, restore `timeProvider = DEFAULT_TIME_PROVIDER`.
  * Add unit tests in `RadarAlertManagerTest.kt` and `RadarSnoozeControllerTest.kt` verifying monotonic resilience against simulated clock adjustments and JVM fallback stability.
* **Out-of-Scope Non-Goals**:
  * Changing chime durations, alert thresholds, urban speed filters, or UI settings.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-063.1` (Alert Transition Debouncing) and `REQ-TRK-064.1` / `REQ-TRK-064.5` (Snooze Expiry & Urban Congestion Timing) in `docs/requirements/req_tracking_cockpit.md`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-5086` and `ATT-5119`.
* **Root Reason for Existing Formulation**: `{ System.currentTimeMillis() }` was used as a standard Java timestamp provider, overlooking non-monotonic wall-clock jumps on mobile devices.
* **Preservation of Core Invariants**: 800ms debounce cooldown invariant preserved; 15m/30m snooze duration preserved; test injection via `setTimeProvider` and constructor parameter preserved; zero audio or haptic regressions.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-TRK-063.1` (Monotonic debouncing and alert timing), `REQ-TRK-064.1` (Monotonic snooze duration tracking), `REQ-TRK-064.5` (Monotonic urban congestion suppression).
* **Affected Architecture Elements**: `RadarAlertManager.kt`, `RadarSnoozeController.kt`.
* **Verification Impact & Test Surface**: `RadarAlertManagerTest.kt`, `RadarSnoozeControllerTest.kt`.
* **Risk Assessment & Invariants**: Low risk. Internal clock provider replacement with established JVM fallback mechanism.

---

## 6. Architectural Strategy & High-Level Solution
1. **Canonical Monotonic Time Provider (`DEFAULT_TIME_PROVIDER`)**:
   * Implement `DEFAULT_TIME_PROVIDER: () -> Long` returning `SystemClock.elapsedRealtime()` on Android with `System.nanoTime() / 1_000_000L` fallback for JVM test execution.
2. **Class Integration**:
   * Update `RadarAlertManager` constructor: `timeProvider: () -> Long = DEFAULT_TIME_PROVIDER`.
   * Update `RadarSnoozeController`: `timeProvider = DEFAULT_TIME_PROVIDER`.
   * In `RadarSnoozeController.reset()`: reset `timeProvider = DEFAULT_TIME_PROVIDER`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  - Exact cooldown and snooze durations preserved.
  - Zero disruption to existing test suite clock injection APIs.
* **Risk Rating**: LOW. Fully localized change with established fallback safety.
