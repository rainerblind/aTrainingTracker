# Stage 1: Problem Domain & Root Cause Analysis - ATT-6705: Fix RadarAlertManager <800ms FAST_APPROACHING escalation suppression, SYSTEM_DEFAULT speaker boost without headphones, and reactive config caching

**Ticket**: [ATT-6705](https://rainerblind.atlassian.net/browse/ATT-6705)  
**Sub-task**: [ATT-6984](https://rainerblind.atlassian.net/browse/ATT-6984) (`[Analysis]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-063.1`, `REQ-TRK-063.3`, `REQ-TRK-063.6`, `REQ-TRK-063.7`  
**Test Spec ID**: `TST-TRK-063`  
**Branch**: `feature/ATT-6705`  
**Author**: AI Agent 1 (Analyst - Pro) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Symptoms
Sprint 2026-46.1 code audit of `RadarAlertManager.kt` and `RadarSettingsSection.kt` identified three defects compromising rider safety, audio ergonomics, and UI layout:
1. **Suppressed High-Speed Threat Escalations (`< 800 ms`)**: When a target transitions `NONE -> APPROACHING` and rapidly escalates to `FAST_APPROACHING` within the 800 ms cooldown window (`now - lastAlertTimestampMs < 800 ms`), `RadarAlertManager.kt` prematurely overwrites `lastThreatLevel = currentThreat` without playing an alert. Subsequent frames match `currentThreat == lastThreatLevel` and return early, permanently silencing the urgent triple chime and haptic alert.
2. **Missing Speaker Volume Boost on `SYSTEM_DEFAULT` Route**: `playChime()` checks `if (config.speakerBoost && config.route == RadarAudioRoute.FORCE_SPEAKER)`, completely ignoring `SYSTEM_DEFAULT` when no external wired or Bluetooth headphones are connected, leaving phone-mounted cyclists without audible alert boost.
3. **Synchronous 8 Hz DataStore Polling, Dangling Stop Runnable & German SegmentedButton Word Wrapping**: `onRadarStateUpdated()` calls synchronous `store.getTuningConfig()` on every 8 Hz frame instead of caching from `tuningConfigFlow`; `stop()` omits `handler?.removeCallbacks(restoreRunnable)`; and `RadarSettingsSection.kt` places `"Telefon-Lautsprecher erzwingen"` (30 chars) into a half-width `SegmentedButton` without `maxLines = 1, softWrap = false`, causing ugly multi-line text wrapping (Design Guidelines §5.6 Rule 38).

---

## 2. Forensic Root Cause Analysis & Call Site Audit
1. **Debounce Premature State Advance in `RadarAlertManager.kt` (`lines 448–453`)**:
   `if (now - lastAlertTimestampMs < DEBOUNCE_COOLDOWN_MS) { lastThreatLevel = currentThreat; return }` runs *before* the `-> FAST_APPROACHING` escalation check. When an escalation occurs within 800 ms, it silently updates `lastThreatLevel` to `FAST_APPROACHING`. On next frames, `if (currentThreat == lastThreatLevel) return` halts processing, completely suppressing the urgent alert.
2. **Audio Route Condition in `RadarAlertManager.kt` (`line 625`)**:
   `playChime()` gates speaker boost strictly on `config.route == RadarAudioRoute.FORCE_SPEAKER`. In Android audio routing, `SYSTEM_DEFAULT` outputs to `AudioDeviceInfo.TYPE_BUILTIN_SPEAKER` when `!synthesizer.isHeadphonesOrBluetoothConnected()`.
3. **8 Hz DataStore Blocking & Dangling Handler in `RadarAlertManager.kt` (`lines 265–273, 497–514`)**:
   `getCurrentConfig()` calls `store.getTuningConfig()` on each 8 Hz frame inside `synchronized(lock)`. Furthermore, `stop()` calls `restoreSpeakerVolumeLocked()` without cancelling pending `restoreRunnable` on `handler`, allowing stale delayed callbacks to execute post-stop.
4. **Unconstrained Text in Half-Width SegmentedButton in `RadarSettingsSection.kt` (`lines 383–394`)**:
   The `SegmentedButton` for `FORCE_SPEAKER` renders `Text(text = stringResource(R.string.radar_alert_route_speaker))` without `maxLines = 1`, `softWrap = false`, or `TextOverflow.Ellipsis`. In German (`"Telefon-Lautsprecher erzwingen"`, 30 chars), Dutch (28 chars), and Portuguese (30 chars), the label wraps across 2–3 vertical lines.

---

## 3. Independent Peer Review & Verification (4-Augen-Prinzip)
- Confirmed that `currentThreat == ThreatLevel.FAST_APPROACHING && lastThreatLevel != ThreatLevel.FAST_APPROACHING` must bypass routine debounce or be evaluated prior to debounce, and debounced frames must NEVER overwrite `lastThreatLevel`.
- Verified `synthesizer.isHeadphonesOrBluetoothConnected()` is already implemented and accurately detects `TYPE_WIRED_HEADSET`, `TYPE_WIRED_HEADPHONES`, `TYPE_BLUETOOTH_A2DP`, and `TYPE_BLE_HEADSET`. Routing to phone speaker when `config.route == FORCE_SPEAKER || (config.route == SYSTEM_DEFAULT && !synthesizer.isHeadphonesOrBluetoothConnected())` cleanly activates boost for phone-mounted cyclists.
- Verified `tuningConfigFlow` can be collected into a `@Volatile` cached snapshot, making 8 Hz telemetry config queries instant and non-blocking.
- Verified that applying compact phrasing across all 9 locales (e.g., DE: `"Telefon-Lautsprecher"`, EN: `"Phone Speaker"`) paired with `maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis` completely eliminates button wrapping.

---

## 4. Chesterton's Fence Archaeology & Invariants
- **Original Formulation (`ATT-5086` / commit `90bd1445`)**: Debounce cooldown was added to prevent chime flutter during signal fluctuations. Overwriting `lastThreatLevel = currentThreat` was intended to track state, but erroneously swallowed safety-critical high-speed escalations.
- **System Invariants**:
  1. High-speed escalation priority: `-> FAST_APPROACHING` must NEVER be suppressed by routine debounce.
  2. Safe volume boost: Speaker boost strictly applies when audio routes to built-in speaker and guarantees auto-restore in `finally` and `stop()`.
  3. Non-blocking telemetry: 8 Hz radar processing must execute with zero disk/DataStore I/O.
  4. Design system compliance: SegmentedButtons strictly enforce single-line text (Rule 38); 100% 9-language translation parity.

---

## 5. Scope Bounding & Complexity Evaluation
- **In-Scope**:
  - Update `RadarAlertManager.kt`: Prioritize `-> FAST_APPROACHING` before routine debounce, prevent `lastThreatLevel` advance on debounced frames, extend speaker boost to `SYSTEM_DEFAULT` without headphones, cache `tuningConfigFlow`, and cancel `restoreRunnable` in `stop()`.
  - Update `RadarSettingsSection.kt`: Enforce `maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis` on audio route SegmentedButtons.
  - Compact `radar_alert_route_speaker` across all 9 `strings_radar.xml` files.
  - Add comprehensive unit tests in `RadarAlertManagerTest.kt`.
- **Out-of-Scope**: Modifications to spatial panning math (`ATT-5120`), snooze state machine (`ATT-6708`), or BLE GATT packet parsing (`ATT-6706`).
- **Complexity Estimate**: 2 SP (Standard bugfix and audio engine hardening).
