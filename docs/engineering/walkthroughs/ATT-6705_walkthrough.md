# Stage 4/5 Walkthrough: ATT-6705 - Fix RadarAlertManager <800ms FAST_APPROACHING escalation suppression, SYSTEM_DEFAULT speaker boost without headphones, and reactive config caching

**Ticket**: [ATT-6705](https://rainerblind.atlassian.net/browse/ATT-6705)  
**Sub-task**: [ATT-6991](https://rainerblind.atlassian.net/browse/ATT-6991) (`[Test]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-063.1`, `REQ-TRK-063.3`, `REQ-TRK-063.6`, `REQ-TRK-063.7`  
**Test Spec Mapping**: `TST-TRK-063.1`, `TST-TRK-063.3`, `TST-TRK-063.6`, `TST-TRK-063.7`, `TST-TRK-063.8`  
**Author**: AI Agent 1 (Flash) | **Date**: 2026-11-11  

---

## 1. Problem & Scope Overview
Sprint 2026-46.1 review identified three defects in `RadarAlertManager.kt` and `RadarSettingsSection.kt`:
1. High-speed threat escalations occurring within `< 800 ms` of initial approach (`NONE -> APPROACHING`) were permanently silenced because routine debouncing prematurely mutated `lastThreatLevel = currentThreat` without playing the alert.
2. Hardware speaker volume boost only triggered on `FORCE_SPEAKER`, missing cyclists on the default `SYSTEM_DEFAULT` route when riding without headphones.
3. Every 8 Hz telemetry frame performed a synchronous `getTuningConfig()` query inside a mutex, `stop()` left pending handler runnables active, and German SegmentedButton labels wrapped across multiple lines.

---

## 2. Key Changes Implemented

1. **High-Speed Threat Escalation Priority (`RadarAlertManager.kt`)**:
   - Reordered threat transition logic to evaluate `currentThreat == FAST_APPROACHING && lastThreatLevel != FAST_APPROACHING` prior to the routine `DEBOUNCE_COOLDOWN_MS` check (`800 ms`).
   - Removed `lastThreatLevel = currentThreat` mutation from the debounced return path, ensuring unalerted transient states never advance the tracked threat level.

2. **Speaker Volume Boost on `SYSTEM_DEFAULT` Route Without Headphones (`RadarAlertManager.kt`)**:
   - Updated `playChime()` to evaluate `isSpeaker = config.route == FORCE_SPEAKER || (config.route == SYSTEM_DEFAULT && !synthesizer.isHeadphonesOrBluetoothConnected())`.
   - Boosts hardware stream volume when `config.speakerBoost && isSpeaker`.
   - In `stop()`, added `handler?.removeCallbacks(restoreRunnable)` immediately prior to restoring volume.

3. **Reactive Configuration Caching (`RadarAlertManager.kt`)**:
   - Subscribed to `actualDataStore?.tuningConfigFlow` in `init` on `coroutineScope`, continuously caching the latest `TuningPreferencesConfigSnapshot` in a `@Volatile cachedConfig` field.
   - Refactored `getCurrentConfig()` to read from `cachedConfig`, eliminating per-frame synchronous DataStore queries during 8 Hz radar telemetry.
   - Cancelled `configJob` in `release()`.

4. **Compact Strings & Single-Line SegmentedButton Layout (`RadarSettingsSection.kt` & `strings_radar.xml`)**:
   - Enforced `maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis`, and `MaterialTheme.typography.labelMedium` on `SegmentedButton` labels in `RadarSettingsSection.kt` (Design Guidelines §5.6 Rule 38).
   - Compacted `radar_alert_route_speaker` across all 9 `strings_radar.xml` locales (e.g. DE: `"Telefon-Lautsprecher"` replacing 30-char `"Telefon-Lautsprecher erzwingen"`).

5. **Test Suite Expansion (`RadarAlertManagerTest.kt`)**:
   - Added `onRadarStateUpdated_fastApproachEscalationWithinDebounceCooldown_triggersFastThreatChime`: verifies that `< 800 ms` fast-threat escalation triggers the urgent triple chime.
   - Added `speakerBoost_onSystemDefaultWithoutHeadphones_boostsVolumeAndRestoresAfterwards`: verifies speaker boost triggers and restores on `SYSTEM_DEFAULT` without headphones.
   - Added `speakerBoost_onSystemDefaultWithHeadphones_doesNotBoostSpeakerVolume`: verifies speaker boost does not trigger when headphones are connected.
   - Added `stop_cleansUpPendingVolumeRestoreRunnableAndRestoresVolume`: verifies `stop()` removes pending runnable callbacks and restores volume immediately.
   - Added `reactiveConfigCaching_updatesConfigFromFlowWithoutSynchronousPerFrameQueries`: verifies reactive flow updates from DataStore.

---

## 3. Verification & Test Evidence

| Test Suite | Scope | Target | Result |
| :--- | :--- | :--- | :--- |
| `RadarAlertManagerTest` | Unit | Escalation debouncing, speaker boost on default route, stop cleanup, reactive caching | **PASS** |
| `RadarAudioSynthesizerTest` | Unit | AudioTrack PCM synthesis, device routing, external headphone detection | **PASS** |
| `RadarSpatialAudioMathTest` | Unit | Spatial stereo gain collapse without headphones | **PASS** |
| `TranslationParityTest` | Localization | 9-locale key, placeholder and parity audit | **PASS** |
| `verify_requirement_governance.py` | Governance | Living doc master index sync, Chesterton's Fence archaeology audit | **PASS** (100% parity) |

---

## 4. Conclusion & Next Steps
All requirements for `REQ-TRK-063.1`, `REQ-TRK-063.3`, `REQ-TRK-063.6`, and `REQ-TRK-063.7` are implemented and verified. Ready for Gate 4 Review by agent2 (`subagent_pro`), parent advancement to `Test`, and fused Stage 5 Verification.
