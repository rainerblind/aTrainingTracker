# Stage 2: Requirement & Test Specification - ATT-6256: Google Fused Doppler Velocity Plausibility & Movement Pinning Prevention

**Ticket**: [ATT-6256](https://rainerblind.atlassian.net/browse/ATT-6256) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.13` | **Branch**: `feature/ATT-6256`  
**Requirement Mapping**: `REQ-TRK-056` (`REQ-TRK-056.1`..`REQ-TRK-056.3`) | **Test Spec ID**: `TST-TRK-056` (`TST-TRK-056.1`..`TST-TRK-056.5`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-09  

---

## 1. Requirement Specification (`REQ-TRK-056`)

### 1.1 Functional Scope & Technical Invariants
* **Movement-Aware Pinning Gating (`REQ-TRK-056.1`)**: In `SpeedAndLocationDevice.onNewLocation()`, when `isDopplerStationary` is true (`hasSpeed && rawSpeed <= SPEED_ZERO_THRESHOLD`), the device SHALL establish stationary pinning ONLY IF `mPrevLocation == null` or `rawDelta <= escapeRadius` where `escapeRadius = Math.max(3.0, 1.5 * location.getAccuracy())`. When `rawDelta > escapeRadius`, active movement is preserved and pinning is bypassed.
* **Damped Doppler & Zero-Speed Fallback (`REQ-TRK-056.2`)**: When displacement exceeds the escape radius (`rawDelta > escapeRadius`) or unpinning occurs, and the provider reports zero or damped speed (`!hasSpeed || rawSpeed <= SPEED_ZERO_THRESHOLD || (rawSpeed < 0.2 * calcSpeed && calcSpeed > 1.0)`), `SpeedAndLocationDevice` SHALL assign `speed = calcSpeed` where `calcSpeed = delta_distance / (dt_ms / 1000.0)` (gated by $\le 70.0\text{ m/s}$ teleportation guard in `REQ-TRK-041.1`), eliminating 1-Hz toggling between 0.0 km/h and plausible velocity.
* **Standstill Pinning Preservation (`REQ-TRK-056.3`)**: For genuine stationary conditions (`rawDelta <= escapeRadius`), `SpeedAndLocationDevice` SHALL immediately clamp speed to 0.0 m/s, pin the anchor coordinate, suppress distance creep, and lock bearing, fully preserving `REQ-TRK-048.1`..`REQ-TRK-048.3`.
* **Localization Impact**: N/A (no user-facing strings or settings modified).

### 1.2 Given-When-Then Acceptance Criteria
* **AC-1 (Zero Doppler Active Movement)**: *Given* continuous 1-Hz location fixes advancing 8 m/s with `hasSpeed() == true` and `getSpeed() == 0.0f` (accuracy = 3.0 m, escapeRadius = 4.5 m), *When* processed by `onNewLocation()`, *Then* published `SPEED_mps` calculates geometric velocity (~8 m/s), `DISTANCE_m` increments steadily, and speed never toggles to 0.0 m/s across consecutive fixes.
* **AC-2 (Damped Doppler Fallback)**: *Given* continuous 1-Hz location fixes advancing 8 m/s with `hasSpeed() == true` and `getSpeed() == 0.15f`, *When* processed, *Then* geometric speed fallback triggers, outputting ~8 m/s instead of 0.15 m/s.
* **AC-3 (True Standstill Pinning)**: *Given* location fixes with Doppler zero speed within the escape radius (`rawDelta <= escapeRadius`), *When* processed, *Then* the stationary anchor pins immediately with `speed = 0.0 m/s`, distance creep is suppressed, and bearing is locked.

---

## 2. Test Specification & Traceability Matrix (`TST-TRK-056`)

| Req ID | Test Case | Target Component | Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-056.1` | `TST-TRK-056.1` | `SpeedAndLocationDeviceStationaryJitterTest` | Continuous movement with zero-speed provider: no toggling to 0.0 km/h, steady distance accumulation |
| `REQ-TRK-056.2` | `TST-TRK-056.2` | `SpeedAndLocationDeviceStationaryJitterTest` | Damped Doppler speed fallback: geometric velocity substituted when provider reports damped speed (< 0.2 * calcSpeed) |
| `REQ-TRK-056.3` | `TST-TRK-056.3` | `SpeedAndLocationDeviceStationaryJitterTest` | Standstill jitter pinning & unpinning multi-cycle: instant pinning on true standstill (`rawDelta <= escapeRadius`) and clean unpinning on movement |
| `REQ-TRK-041.1` | `TST-TRK-056.4` | `SpeedAndLocationDeviceStationaryJitterTest` | Teleportation spike protection: position jumps > 70.0 m/s are clamped to `delta_distance = 0.0` |
| `REQ-TRK-056` | `TST-TRK-056.5` | Targeted Test Suite | Targeted test execution: `com.atrainingtracker.banalservice.devices.*` |
