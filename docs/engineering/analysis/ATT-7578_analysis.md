# Stage 1 Analysis: ATT-7578 - Fix ANT+ Bike Radar slot-index track IDs and per-packet window reset

**Ticket**: [ATT-7578](https://rainerblind.atlassian.net/browse/ATT-7578)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Branch**: `feat/ATT-7578`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Problem Statement & Motivation
Radar technical review findings M7/m7/m5 (Major):
1. **Slot-Index Track ID Churn (M7)**: In `ANTBikeRadarDevice.kt`, `targetId = baseId + i` uses raw ANT+ slot indices as track IDs. Because ANT+ continuously sorts targets by distance (slot 0 = closest), when a leading vehicle overtakes and drops out, trailing vehicles shift slots (e.g., slot 1 -> slot 0). `RadarWindowAggregator` interprets this as the trailing vehicle disappearing (falsely incrementing vehicle pass counts prematurely) while the overwritten slot later passes again, inflating pass counts and distorting FIT/Strava totals.
2. **Per-Packet Window Reset Defeating Rolling Latch (m7)**: In `ANTBikeRadarDevice.kt` and `BTLERadarDevice.java`, `sampleAndResetWindow()` is called on every packet (4–8 Hz / 125 ms) instead of at 1 Hz. While `frameBuffer` retains peak threats, closest distance, and approach speed, `latchedPassingDistance` was immediately cleared to `null` on each packet, causing transient close-pass clearances to be wiped before `TrackerService` 1 Hz DB poll ticks.
3. **0-Bin Truncation Dropping Close-Pass Vehicles (m5)**: `if (threatLevelBits > 0 && rawRange > 0)` drops vehicles in the 0-bin ($< 3.125\text{ m}$) right as they pass the cyclist. This prematurely deletes targets before the overtake point and prevents lateral passing clearance ($\le 3.0\text{ m}$) from ever being recorded over ANT+.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Codebase Verification**:
  * Inspected `ANTBikeRadarDevice.kt:333-369`: Targets in Page 0x30/0x31 are assigned `targetId = baseId + i`. When Car A (5m, slot 0) passes and Car B (15m, slot 1) moves to slot 0, `activeTargets.remove(0..3)` removes both, and Car B is re-inserted as `targetId = 0`. In `RadarWindowAggregator.kt:140-156`, `disappearedTargetIds` detects target 1 disappeared; since `minRange = 15m <= 18m`, a pass is falsely recorded. When Car B subsequently overtakes, target 0 disappears, recording a second pass for the same vehicle.
  * Inspected `ANTBikeRadarDevice.kt:392` and `BTLERadarDevice.java:476`: Both drivers invoke `sampleAndResetWindow()` immediately inside the high-rate packet callback. In `RadarWindowAggregator.kt:199-200`, `latchedPassingDistance` is wiped to `null` on every invocation rather than decaying over the 1,000 ms rolling window.
  * Inspected `ANTBikeRadarDevice.kt:339`: Guard `if (threatLevelBits > 0 && rawRange > 0)` excludes `rawRange == 0`. In the ANT+ Bike Radar profile, 6-bit range has 3.125m resolution; `rawRange == 0` represents the $[0.0\text{ m}, 3.125\text{ m})$ bin. Excluding `rawRange == 0` drops targets right at $0\text{--}3.125\text{ m}$, preventing `target.rangeMeters <= 3.0f` from ever triggering lateral clearance latching.
* **Independent Peer Review & Verification**:
  * Confirmed that ANT+ devices transmit up to 4 targets per page in range order. Maintaining stable track IDs across successive frames via range-continuity matching ($r_{new} \in [r_{old} - 16\text{m}, r_{old} + 4\text{m}]$) prevents slot-shift churn.
  * Confirmed that timestamping `latchedPassingDistance` with a 1,000 ms decay in `RadarWindowAggregator` preserves passing clearances across sub-second frames, and gating `sampleAndResetWindow()` on 1 Hz in both drivers aligns scalar sensor updates.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * In `ANTBikeRadarDevice.kt`: Implement range-continuity track matching for Page 0x30 and 0x31 targets to maintain stable track IDs across slot shifts.
  * In `ANTBikeRadarDevice.kt:339`: Permit `rawRange == 0` when `threatLevelBits > 0` ($0.0\text{ m}$ distance).
  * In `RadarWindowAggregator.kt`: Retain `latchedPassingDistance` for `WINDOW_DURATION_MS` (1,000 ms) decay instead of wiping immediately to `null`.
  * In `ANTBikeRadarDevice.kt` and `BTLERadarDevice.java`: Gate calling `sampleAndResetWindow()` and updating scalar sensors on a 1-second cadence.
* **Out-of-Scope Non-Goals**:
  * Changing BLE V2 2D payload decoding or modifying SQLite workout schema.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-032.3` (Page 0x30/0x31 bit unpacking) and `REQ-CON-028.5` (Scalar 1 Hz latch aggregator) in `docs/requirements/req_tracking_cockpit.md`.
* **Historical Origin & Commit Trace**: Sprints 2026-46.3 (`ATT-5083`, `ATT-5084`) and 2026-46.4 (`ATT-6700`).
* **Root Reason for Existing Formulation**: In `ATT-5084`, `targetId = baseId + i` assumed slot assignments were static tracks, unaware that ANT+ devices dynamically sort targets by proximity. `rawRange > 0` was defensive against unpopulated payload bytes. In `ATT-6700`, `sampleAndResetWindow()` was intended for 1 Hz polling, but drivers called it per packet, while `latchedPassingDistance` lacked windowed decay.
* **Preservation of Core Invariants**: Predefined Network Slot 1 isolation; 3.5s starvation watchdog; Rule 1.8 constructor parity; sensor decoupling; 100% 9-language localization parity.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-CON-032.3` (ANT+ track stability and 0-bin handling) and `REQ-CON-028.5` (1,000 ms window latching for passing clearance).
* **Affected Architecture Elements**: `ANTBikeRadarDevice.kt`, `BTLERadarDevice.java`, `RadarWindowAggregator.kt`.
* **Verification Impact & Test Surface**: `ANTBikeRadarDeviceTest.kt`, `RadarWindowAggregatorTest.kt`, `BTLERadarDeviceTest.kt`.
* **Risk Assessment & Invariants**: Low risk. Changes are isolated to track association, 0-bin range acceptance, and window latch decay time checks.

---

## 6. Architectural Strategy & High-Level Solution
1. **Range-Continuity Track Matching (`ANTBikeRadarDevice.kt`)**:
   * Maintain `activeTracks: MutableMap<Int, TrackRecord>` and `nextTrackId: Int`.
   * For incoming decoded targets, match to unassigned active tracks within range window $[r_{old} - 16.0\text{m}, r_{old} + 4.0\text{m}]$ minimizing $|r_{new} - r_{old}|$.
   * Unmatched incoming targets receive a new `trackId`. Unmatched existing tracks from the reporting page bank are removed.
2. **0-Bin Acceptance (`ANTBikeRadarDevice.kt`)**:
   * Change condition to `if (threatLevelBits > 0)` so `rawRange == 0` yields `rangeMeters = 0.0f`.
3. **1,000 ms Latch Decay (`RadarWindowAggregator.kt`)**:
   * Timestamp `latchedPassingDistanceTimestampMs = nowMs`.
   * In `sampleAndResetWindow(nowMs)`, retain `latchedPassingDistance` while `nowMs - latchedPassingDistanceTimestampMs <= WINDOW_DURATION_MS`.
4. **1 Hz Driver Gating (`ANTBikeRadarDevice.kt`, `BTLERadarDevice.java`)**:
   * Gate `sampleAndResetWindow()` and scalar sensor updates on `nowMs - lastWindowSampleMs >= 1000L`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Monotonic track continuity: Approaching vehicles retain track identity across ANT+ range sorts.
  2. Sub-second passing clearance preservation: Passing distances persist for 1,000 ms for 1 Hz DB logging.
  3. Seamless 0-bin passing: Close passes ($< 3.125\text{ m}$) are fully tracked until completion.
* **Risk Rating**: **LOW** — Self-contained mathematical matching and timestamp gating with zero database or UI schema changes.
