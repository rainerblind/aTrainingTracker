# Stage 5 Walkthrough: ATT-6709 - Disable phone-speaker stereo attenuation on SYSTEM_DEFAULT, select FAST_APPROACHING target kinematics, add per-target lane-change hysteresis, and release preview AudioTracks

**Ticket**: [ATT-6709](https://rainerblind.atlassian.net/browse/ATT-6709)  
**Sub-task**: [ATT-6938](https://rainerblind.atlassian.net/browse/ATT-6938) (`[Test]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-063.1`, `REQ-TRK-065.1`, `REQ-TRK-065.3`, `REQ-TRK-065.5`  
**Test Spec Mapping**: `TST-TRK-063.1`, `TST-TRK-065.1`, `TST-TRK-065.3`, `TST-TRK-065.4`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-11  

---

## 1. Problem & Scope Overview
Sprint 2026-46.1 review identified three defects: (1) `computeStereoGains()` attenuated one channel down to `0.20f` on `SYSTEM_DEFAULT` when no headphones were connected, reducing outdoor built-in speaker loudness; (2) `triggerFastThreatAlert()` bound to the closest slow vehicle instead of the high-speed threat, while lane-change tracking lacked multi-target tracking and jitter hysteresis; (3) `previewSynthesizer` leaked native `AudioTrack` handles on Settings exit, and UI controls used stateless `FilterChip`s and full-size `Switch` rows. `ATT-6709` addresses all three areas.

---

## 2. Key Changes Implemented

1. **Speaker Panning Collapse (`RadarSpatialAudioMath.kt`)**:
   - Added `hasHeadphones: Boolean = true` parameter to `computeStereoGains()`.
   - Collapses channel gains to centered `(1.0f, 1.0f)` when `route == FORCE_SPEAKER` or (`route == SYSTEM_DEFAULT && !hasHeadphones`), preserving 100% phone-speaker volume.
   - Clarified KDoc documenting that Cartesian lateral offset ($x < 0$ = left, $x > 0$ = right) directly drives physical left/right stereo panning.

2. **Headphone Detection & Stereo Panning Volume API (`RadarAudioSynthesizer.kt`)**:
   - Added `isHeadphonesOrBluetoothConnected(): Boolean` inspecting active audio outputs against `EXTERNAL_DEVICE_TYPES`.
   - Restored `track.setStereoVolume(finalLeft, finalRight)` when `finalLeft != finalRight` while using non-deprecated `track.setVolume(finalLeft)` for symmetric gains, preserving true 3D spatial panning on headphones (REQ-TRK-065.1).

3. **Fast Threat Kinematics & Multi-Target Lane Hysteresis (`RadarAlertManager.kt`)**:
   - On `FAST_APPROACHING` escalation, prioritizes the `FAST_APPROACHING` target kinematics with highest approach speed over closer slow-moving vehicles.
   - Implemented per-target `LaneTrackState` map with multi-car convoy support, spatial hysteresis ($|x| < 1.0\text{ m}$ to $|x| \ge 1.5\text{ m}$), 3,000 ms per-target cooldown, and departed ID pruning.
   - Added `releasePreviewSynthesizer()` in companion object to cleanly close and release all standalone preview `AudioTrack`s.
   - Passes `synthesizer.isHeadphonesOrBluetoothConnected()` across all alert triggers and preview actions.

4. **Settings Lifecycle & Material3 UI Hygiene (`RadarSettingsSection.kt`)**:
   - Attached `DisposableEffect(Unit) { onDispose { RadarAlertManager.releasePreviewSynthesizer() } }`, eliminating native `AudioTrack` resource leaks.
   - Converted stateless `FilterChip` preview buttons to `AssistChip` action buttons (Design Guidelines §1.2).
   - Replaced full-size `Switch` row for `radarSpeedPitchModulation` with `SubordinateTuningToggleItem` (Design Guidelines §3.3).

---

## 3. Verification & Test Evidence

| Test Suite | Scope | Target | Result |
| :--- | :--- | :--- | :--- |
| `RadarSpatialAudioMathTest` | Unit | Speaker collapse without headphones, headphone stereo panning | **PASS** (6/6 tests) |
| `RadarAudioSynthesizerTest` | Unit | Headphone detection, non-deprecated setVolume, track cleanup | **PASS** (6/6 tests) |
| `RadarAlertManagerTest` | Unit | Fast threat kinematics selection, multi-car lane hysteresis, speaker collapse | **PASS** (17/17 tests) |
| `RadarAccordionSectionTest` | Integration | Tuning accordion section mounting and ordering | **PASS** (6/6 tests) |
| `verify_requirement_governance.py` | Governance | Living doc master sync, 0 unmounted islands, Chesterton audit | **PASS** (100% parity) |

---

## 4. Conclusion & Next Steps
All requirements for `REQ-TRK-063.1`, `REQ-TRK-065.1`, `REQ-TRK-065.3`, and `REQ-TRK-065.5` are verified. Ready for Gate 5 Review by agent2 (`subagent_pro`), parent advancement to `Final Review (Human)`, and squash merge onto `sprint/2026-46.2`.

---

## 5. Cold Carbon Review (Stage 6) Defect Resolution & Healing
1. **Stereo Volume Panning Restoration**:
   - In `RadarAudioSynthesizer.kt` (`playTrack`), restored `@Suppress("DEPRECATION") track.setStereoVolume(finalLeft, finalRight)` when `finalLeft != finalRight` and `track.setVolume(finalLeft)` when equal. This ensures that 3D spatial panning is preserved across left/right channels when headphones are connected (REQ-TRK-065.1).
   - In `RadarAudioSynthesizerTest.kt`, updated `playTrack_setsAsymmetricStereoVolumeToPreserveSpatialPanning` verifying `mockTrack.setStereoVolume` is invoked with asymmetric gains.

