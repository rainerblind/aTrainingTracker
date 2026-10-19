# Stage 1 Analysis: ATT-4094 — Distinguish speed sensor standstill from in-motion signal loss (tunnel/dropout) in AutoPauseManager based on last valid speed

## 1. Executive Summary
- **Ticket**: `ATT-4094` — `[Verbesserung] [Medium] Distinguish speed sensor standstill from in-motion signal loss (tunnel/dropout) in AutoPauseManager based on last valid speed` (Epic: `ATT-1153` *Sensor Architecture & Concurrency Stability*)
- **Classification**: Sensor Telemetry Evaluation & State Machine Refinement (Defect Prevention / Ergonomics)
- **Context & Motivation**:
  In Sprint `2026-42.9`, ticket `ATT-3984` resolved an issue where Auto-Pause never triggered because `ProxySensor` inherited a timestamp of `0L`. As part of that change, `AutoPauseManager.evaluateTick(...)` was modified so that when a speed sensor is registered (`hasSpeedSource == true`) and packet updates become stale (`nowMs - packetTimestampMs > 5000L`), the effective speed was unconditionally treated as `0.0 m/s`.
  During the Sprint 2026-42.9 review, a key architectural shortcoming was identified:
  1. **False Auto-Pauses During In-Motion Signal Loss (Tunnel / Bridge / Underpass / RF Dropout)**:
     If an athlete is cruising at 30 km/h (8.3 m/s) on a road or cycling track and enters a tunnel, GPS signals abruptly drop. A physical athlete cannot decelerate instantaneously from 30 km/h to 0 km/h within a single millisecond without emitting intermediate decelerating speed readings. Treating sudden packet silence while cruising as standstill (`0.0 m/s`) causes `AutoPauseManager` to falsely advance its debounce counter and transition to `AUTO_PAUSED`. Consequently, active workout time (`TIME_ACTIVE`) halts, and route segments or splits inside the tunnel are missed.
     The correct state transition for sudden packet loss while in motion is `SENSOR_LOST`. In `SENSOR_LOST`, the workout continues recording active time while waiting for signal recovery.
  2. **Standstill Silence Detection (Deceleration Before Packet Silence)**:
     Conversely, when an athlete approaches a red light, building, or café, their speed slows down below the resume threshold before wheel/GPS sensors stop transmitting. In this scenario, the last valid speed was low ($\le$ resume threshold), so packet silence reliably signifies that the athlete came to a complete physical standstill. Treating this condition as `0.0 m/s` allows `AUTO_PAUSED` to trigger cleanly as intended by `ATT-3984`.
  3. **Static Coupling Decoupling & Exhaustive Call-Site Audit**:
     `AutoPauseManager.onManualResume()` statically called `TrainingApplication.isManualPauseLocked()`. Decoupling this domain engine by passing `manualPauseLocked: Boolean = false` explicitly makes the engine purely unit-testable and adheres to clean architecture principles.

---

## 2. Problem Domain & Forensic Investigation

### 2.1 Current Telemetry Evaluation in `AutoPauseManager.kt`
Currently, `AutoPauseManager.evaluateTick(...)` handles packet staleness as follows:
```kotlin
// Step 4: Stale Packet Check (TTL = 5000ms)
val isPacketStale = if (packetTimestampMs <= 0L) {
    true
} else {
    (currentTimestampMs - packetTimestampMs) > STALE_PACKET_TIMEOUT_MS
}

val hasFreshValidReading = !isSpeedCorrupt && !isPacketStale && currentSpeedMps != null

if (isSpeedCorrupt || !hasSpeedSource) {
    // Speed source unregistered or corrupt numeric reading -> SENSOR_LOST
    ...
}

if (hasFreshValidReading) {
    lastValidSpeedPacketTimestampMs = packetTimestampMs
}

// Step 5: SENSOR_LOST recovery contract...

// Step 6: Speed evaluation
val speedMps = if (hasFreshValidReading) currentSpeedMps else 0.0
```
Notice that when `hasSpeedSource == true` and `isPacketStale == true` (or `currentSpeedMps == null`), `speedMps` is unconditionally forced to `0.0 m/s`.
Regardless of whether the athlete was traveling at 40 km/h or 0 km/h prior to the packet dropout, it treats it as 0.0 m/s, advancing the debounce counter and auto-pausing.

### 2.2 Forensic Inspection of `onManualResume()` & Exhaustive Call-Site Audit
In `AutoPauseManager.kt`:
```kotlin
@Synchronized
fun onManualResume() {
    if (com.atrainingtracker.trainingtracker.TrainingApplication.isManualPauseLocked() ||
        currentState == AutoPauseState.MANUAL_PAUSED
    ) {
        return
    }
    debounceCounter = 0
    sensorLostRecoveryTicks = 0
    if (currentState == AutoPauseState.AUTO_PAUSED || currentState == AutoPauseState.PAUSE_DEBOUNCE_PENDING) {
        currentState = AutoPauseState.TRACKING
    }
}
```
An exhaustive audit across all source directories identifies exactly three call sites of `AutoPauseManager.onManualResume()` in production code (all located within `TrackerService.java`):
1. `TrackerService.java:429` inside static helper `notifyManualResume()`:
   Invoked when user taps Resume via notification actions or external broadcasts.
2. `TrackerService.java:835` inside `onStartCommand()` for action `RESUME_BY_USER`:
   Invoked when the user manually resumes tracking from the paused UI.
3. `TrackerService.java:2326` inside `evaluateAutoPauseTick(...)`:
   Invoked during periodic 1Hz evaluation when auto-pause was externally cleared.

*UI Interaction Call Sites*:
In `SensorGridScreen.kt:787`, `AutoPauseHudBanner(onManualResume = { ... })` does **not** call `AutoPauseManager` directly; it dispatches a system broadcast `Intent(TrainingApplication.REQUEST_RESUME_FROM_PAUSED)`, which routes through `TrainingApplication.mResumeFromPaused` -> `TrainingApplication.resumeFromPaused()` -> `TrackerService.notifyManualResume()`.

Thus, updating `AutoPauseManager.onManualResume(manualPauseLocked: Boolean = false)` with `@JvmOverloads` maintains complete binary compatibility for external callers while allowing `TrackerService.java` to explicitly pass `TrainingApplication.isManualPauseLocked()`, eliminating the hidden static coupling from domain logic.

---

## 3. Scope of Changes & Architecture Design

### 3.1 Speed-Dependent Staleness Classification
1. **Track `lastValidSpeedMps: Double?`**:
   * Maintain `var lastValidSpeedMps: Double? = null; private set` in `AutoPauseManager`.
   * When `hasFreshValidReading == true`, update `lastValidSpeedMps = currentSpeedMps`.
   * Reset `lastValidSpeedMps = null` in `setState()` when resetting the engine to `TRACKING` or `MANUAL_PAUSED`.
2. **Dynamic Sensor Unpairing & Disconnection Behavior**:
   * If a speed sensor is dynamically unpaired, powered off, or disconnected mid-session such that `hasSpeedSource == false`:
     * The evaluation immediately branches to `if (isSpeedCorrupt || !hasSpeedSource)` at Step 4, triggering immediate transition to `SENSOR_LOST` (`auditLogEvent = "SENSOR_LOST_STALE_TTL_EXCEEDED"`).
     * `lastValidSpeedMps` is explicitly reset to `null` upon sensor unpairing (`!hasSpeedSource`), preventing a stale historic speed from lingering across distinct sensor lifecycle sessions.
3. **Classify Stale Packets (`hasSpeedSource == true && !hasFreshValidReading`)**:
   * Calculate `resumeThresholdMps` based on active sport configuration.
   * If `lastValidSpeedMps != null && lastValidSpeedMps > resumeThresholdMps`:
     * The athlete was in motion at high speed before packets stopped arriving (e.g. entered a tunnel, GPS multipath shadow, or BLE wheel sensor battery dropout).
     * Transition to `SENSOR_LOST` (from `TRACKING` or `PAUSE_DEBOUNCE_PENDING`), with `auditLogEvent = "SENSOR_LOST_IN_MOTION_DROPOUT"`.
     * Reset `debounceCounter = 0`.
   * If `lastValidSpeedMps == null || lastValidSpeedMps <= resumeThresholdMps`:
     * The athlete was already stationary or decelerated to walking/standing speed before packets stopped.
     * Evaluate effective `speedMps = 0.0` for pause debounce, allowing transition to `AUTO_PAUSED`.
4. **Recovery from `SENSOR_LOST`**:
   * Preserve the existing 2-consecutive-second recovery contract: once 2 consecutive fresh valid speed readings arrive, transition back to `TRACKING`.

### 3.2 Decouple Static `TrainingApplication.isManualPauseLocked()` Access
1. Update `onManualResume`:
   ```kotlin
   @JvmOverloads
   @Synchronized
   fun onManualResume(manualPauseLocked: Boolean = false) {
       if (manualPauseLocked || currentState == AutoPauseState.MANUAL_PAUSED) {
           return
       }
       debounceCounter = 0
       sensorLostRecoveryTicks = 0
       if (currentState == AutoPauseState.AUTO_PAUSED || currentState == AutoPauseState.PAUSE_DEBOUNCE_PENDING) {
           currentState = AutoPauseState.TRACKING
       }
   }
   ```
2. Update callers in `TrackerService.java`:
   Pass `TrainingApplication.isManualPauseLocked()` at the three audited call sites in `TrackerService`.

---

## 4. Requirement & Test Specification Mapping

- **Target Requirement**: `REQ-TRK-037` (extending `REQ-TRK-018` and `REQ-TRK-028`): *Distinguish Speed Sensor Standstill from In-Motion Signal Loss (Tunnel / Dropout) in AutoPauseManager*.
  *Note on Scope Hygiene*: Only `REQ-TRK-037` will be added to `docs/requirements.md` in Stage 2. All prior ticket requirements (`REQ-DAT-031`, `REQ-DAT-032`, `REQ-SET-078`, `REQ-UI-416`) belong to their respective tickets and will not be touched.
- **Target Test Specification**: `TST-TRK-030`: *AutoPauseManager In-Motion Dropout vs. Standstill Silence Verification Suite*.

---

## 5. Risk & Side-Effect Assessment
- **Risk 1: Boundary Edge Case (Decelerating in a Tunnel)**:
  If an athlete decelerates inside a tunnel after GPS is already lost, `lastValidSpeedMps` remains high and the session remains in `SENSOR_LOST`. This is the desired behavior for athletic GPS tracking (Strava, Garmin, Wahoo standard): recording continues through tunnels rather than pausing.
- **Risk 2: Backward Compatibility with Existing Unit Tests**:
  Existing test `TST-TRK-020_4` tested stale speed of 5.0 m/s decaying to 0. Under the new requirement, stale speed of 5.0 m/s (which is > resume threshold of ~1.0 m/s) is correctly recognized as in-motion dropout (`SENSOR_LOST`). That test will be refined to reflect `REQ-TRK-037` semantics while verifying `SENSOR_LOST`.
- **Risk 3: Concurrency**:
  All state mutations in `AutoPauseManager` are `@Synchronized`.
