# Stage 3: Implementation Plan - ATT-6705: Fix RadarAlertManager <800ms FAST_APPROACHING escalation suppression, SYSTEM_DEFAULT speaker boost without headphones, and reactive config caching

**Ticket**: [ATT-6705](https://rainerblind.atlassian.net/browse/ATT-6705)  
**Sub-task**: [ATT-6987](https://rainerblind.atlassian.net/browse/ATT-6987) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-063.1`, `REQ-TRK-063.3`, `REQ-TRK-063.6`, `REQ-TRK-063.7`  
**Test Mapping**: `TST-TRK-063.1a`, `TST-TRK-063.3a`, `TST-TRK-063.3b`, `TST-TRK-063.6a`, `TST-TRK-063.7a`, `TST-TRK-063.8a`  
**Branch**: `feature/ATT-6705`  
**Author**: AI Agent 1 (Implementer - Pro) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
Sprint 2026-46.1 review identified three defects in `RadarAlertManager.kt` and `RadarSettingsSection.kt`:
1. High-speed threat escalations occurring within `< 800 ms` of initial approach (`NONE -> APPROACHING`) are permanently silenced because routine debouncing prematurely mutates `lastThreatLevel = currentThreat` without playing the alert.
2. Hardware speaker volume boost only triggers on `FORCE_SPEAKER`, missing cyclists on the default `SYSTEM_DEFAULT` route when riding without headphones.
3. Every 8 Hz telemetry frame performs a synchronous `getTuningConfig()` query inside a mutex, `stop()` leaves pending handler runnables active, and German SegmentedButton labels wrap across multiple lines.

---

## 2. Traceability & Requirements Mapping
* **REQ-TRK-063.1 (High-Speed Threat Escalation Priority & Debounce Invariance)**: Prioritize `-> FAST_APPROACHING` escalation before routine debouncing; prevent `lastThreatLevel` mutation on suppressed frames -> `TST-TRK-063.1a`
* **REQ-TRK-063.3 (Speaker Volume Boost on SYSTEM_DEFAULT Without Headphones & Stop Cleanup)**: Trigger speaker boost on `SYSTEM_DEFAULT` when `!synthesizer.isHeadphonesOrBluetoothConnected()`; cancel `restoreRunnable` in `stop()` -> `TST-TRK-063.3a`, `TST-TRK-063.3b`
* **REQ-TRK-063.6 (Reactive Configuration Caching from tuningConfigFlow)**: Subscribe to `tuningConfigFlow` and maintain a `@Volatile cachedConfig` snapshot, eliminating 8 Hz synchronous DataStore queries -> `TST-TRK-063.6a`
* **REQ-TRK-063.7 (Compact Phrasing & Single-Line Enforcement Across 9 Locales)**: Enforce `maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis` in `RadarSettingsSection.kt` and compact `radar_alert_route_speaker` across all 9 `strings_radar.xml` files -> `TST-TRK-063.7a`

---

## 3. System Invariants & Preserved Behavior
1. **Safety Escalation Guarantee**: `FAST_APPROACHING` alerts must NEVER be suppressed by routine debounce cooldowns.
2. **Guaranteed Volume Restoration**: Stream volume restoration in `finally`, `stop()`, and service destruction remains strictly preserved.
3. **Non-Blocking 8 Hz Pipeline**: Radar telemetry processing executes purely in-memory with zero disk/DataStore blocking.
4. **Design System & Localization Parity**: Single-line text enforcement on SegmentedButtons (Rule 38); 100% 9-language translation parity.

---

## 4. UI Consistency (Design Guidelines Section 5 & Rule 38)
* **Reference Component**: Material 3 `SingleChoiceSegmentedButtonRow` in `RadarSettingsSection.kt` (Audio Output Route).
* **Styling & Theme Tokens**: Uses `SegmentedButtonDefaults.itemShape()`, `MaterialTheme.typography.labelMedium`, `contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)`.
* **Single-Line Text Enforcement**: `maxLines = 1`, `softWrap = false`, `overflow = TextOverflow.Ellipsis` applied to button labels; compacted strings across all 9 locales (e.g. DE: `"Telefon-Lautsprecher"` replacing 30-char `"Telefon-Lautsprecher erzwingen"`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Escalation Priority & Debounce State Preservation (`RadarAlertManager.kt`)
* Move `currentThreat == FAST_APPROACHING && lastThreatLevel != FAST_APPROACHING` evaluation *before* the routine `DEBOUNCE_COOLDOWN_MS` check.
* Remove `lastThreatLevel = currentThreat` from the debounced early return branch, ensuring unalerted states do not advance `lastThreatLevel`.

### Step 2: Speaker Volume Boost on SYSTEM_DEFAULT & Stop Handler Cleanup (`RadarAlertManager.kt`)
* In `playChime()`, evaluate `isSpeaker = config.route == FORCE_SPEAKER || (config.route == SYSTEM_DEFAULT && !synthesizer.isHeadphonesOrBluetoothConnected())`. Activate `boostSpeakerVolumeLocked` when `config.speakerBoost && isSpeaker`.
* In `stop()`, invoke `handler?.removeCallbacks(restoreRunnable)` immediately prior to `restoreSpeakerVolumeLocked()`.

### Step 3: Reactive Configuration Caching (`RadarAlertManager.kt`)
* Define `@Volatile private var cachedConfig: TuningPreferencesConfigSnapshot`.
* In `init`, collect `actualDataStore?.tuningConfigFlow` on `coroutineScope` to keep `cachedConfig` continuously updated.
* Update `getCurrentConfig()` to return `cachedConfig` directly without per-frame `getTuningConfig()` calls.

### Step 4: Compact Button Strings & Single-Line Layout (`RadarSettingsSection.kt` & `strings_radar.xml`)
* In `RadarSettingsSection.kt`, add `maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis`, `labelMedium` typography, and compact padding to `SegmentedButton` text.
* In `values/` and 8 localized `strings_radar.xml` shards, compact `radar_alert_route_speaker` (e.g., DE: `"Telefon-Lautsprecher"`, EN: `"Phone Speaker"`, ES: `"Altavoz del teléfono"`, FR: `"Haut-parleur"`, IT: `"Altoparlante telefono"`, JA: `"スマホのスピーカー"`, NL: `"Telefoonluidspreker"`, PL: `"Głośnik telefonu"`, PT: `"Alto-falante do celular"`).

### Step 5: Test Suite Expansion & Clean-Room Verification
* In `RadarAlertManagerTest.kt`, add tests verifying: (1) `< 800 ms` fast-threat escalation; (2) `SYSTEM_DEFAULT` speaker boost when headphones are disconnected; (3) `stop()` cancels pending `restoreRunnable`; (4) non-blocking reactive configuration caching.
* Execute targeted test suite and translation parity tests under `/tmp/att_build_merge.lock`.

---

## 6. Verification & Rollback Plan
* **Verification Command**:
  `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.audio.radar.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
* **Rollback Plan**: `git checkout feature/ATT-6705` to restore clean state.
