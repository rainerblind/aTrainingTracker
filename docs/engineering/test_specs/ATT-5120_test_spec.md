# Stage 2: Requirement & Test Specification - ATT-5120: 3D Stereo Audio Panning & Speed Pitch Modulation

**Ticket**: [ATT-5120](https://rainerblind.atlassian.net/browse/ATT-5120)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1`  
**Requirement Mapping**: `REQ-TRK-065` (*3D Stereo Lateral-Offset Panning & Approach-Speed Pitch Modulation for Radar Audio*)  
**Test Spec ID**: `TST-TRK-065`  
**Branch**: `feature/ATT-5120`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-TRK-065)

### 1.1 Problem Statement & Rationale
Mono radar beeps require cyclists with headphones to divert their eyes from the road to determine if an approaching car is directly behind them or safely passing in the adjacent lane, and how rapidly it is closing. Decoding BLE V2 lateral lane offset $x$ and approach speed $v_{\text{rel}}$ enables directional audio panning and pitch modulation, delivering intuitive eyes-free spatial awareness.

### 1.2 Functional & Architectural Requirements
1. *Stereo Lateral-Offset Panning (`REQ-TRK-065.1`)*: When stereo audio is active, the system SHALL calculate left/right gains `(leftGain, rightGain)` based on primary target `lateralOffsetMeters` ($x$) and traffic orientation:
   - In-lane ($|x| < 1.0\text{ m}$): Centered `(1.0, 1.0)`.
   - Passing lane ($|x| \ge 1.5\text{ m}$): Panned `(1.0, 0.20)` for overtaking ear in Right-Hand Traffic (left ear) or `(0.20, 1.0)` in Left-Hand Traffic (right ear).
   - Linear attenuation between $1.0\text{ m}$ and $1.5\text{ m}$.
   - When `route == FORCE_SPEAKER` or spatial audio is disabled, stereo gains SHALL collapse to centered `(1.0, 1.0)`.
2. *Approach-Speed Pitch Modulation (`REQ-TRK-065.2`)*: When enabled, the system SHALL map `approachSpeedMps` linearly across `3.0 .. 30.0 m/s` (`11 .. 108 km/h`) to `0.95x .. 1.35x` playback pitch/tempo via `AudioTrack.setPlaybackParams()` without rebuilding static PCM buffers.
3. *Lane-Change Confirmation Spatial Cue (`REQ-TRK-065.3`)*: When enabled, the system SHALL emit a subtle spatial tick cue in the overtaking ear when an in-lane vehicle transitions to the safe passing lane.
4. *DataStore Preferences & Traffic Orientation (`REQ-TRK-065.4`)*: `TuningPreferencesDataStore` SHALL persist `radarSpatialAudioEnabled` (Boolean, default `true`), `radarSpeedPitchModulation` (Boolean, default `true`), `radarTrafficOrientation` (String/enum `RIGHT_HAND`/`LEFT_HAND`, default `RIGHT_HAND`), and `radarLaneChangeTickEnabled` (Boolean, default `true`).
5. *Interactive Audio Preview Controls (`REQ-TRK-065.5`)*: `RadarSettingsSection.kt` SHALL provide preview buttons (Centered, Passing, Slow, Fast, Lane Change) for instant auditory verification.
6. *9-Language Localization Parity (`REQ-TRK-065.6`)*: All spatial audio and preview string resources SHALL maintain 100% key and format parity across EN, DE, ES, FR, IT, JA, NL, PL, PT (zero inline emojis).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Stereo Panning)**: *Given* spatial audio enabled and RHT configured, *When* target shifts from $x = 0.0\text{ m}$ to $x = -2.0\text{ m}$, *Then* chime pans from centered `(1.0, 1.0)` to left ear `(1.0, 0.20)`.
* **Criterion 2 (Speed-Adaptive Pitch)**: *Given* pitch modulation enabled, *When* vehicle closes at `25 m/s` vs `5 m/s`, *Then* alert chime pitch scales from `~1.0x` to `~1.28x`.
* **Criterion 3 (Force Speaker Fallback)**: *Given* `FORCE_SPEAKER` route selected, *When* passing target alert triggers, *Then* gains remain centered `(1.0, 1.0)` to preserve max volume.

### 1.4 System Invariants
* Warm `MODE_STATIC` replay latency preserved; zero SQLite schema modifications; 100% 9-language translation parity.

---

## 2. Test Specification (TST-TRK-065)

### Test Case 1: Stereo Lateral Gain Computation (`TST-TRK-065.1`)
* **Scope**: Unit Test (`RadarSpatialAudioMathTest.kt` / `RadarAlertManagerTest.kt`)
* **Action**: Assert gain pairs for in-lane, passing, boundary transitions, and RHT vs. LHT orientation.

### Test Case 2: Speed-to-Pitch Linear Mapping (`TST-TRK-065.2`)
* **Scope**: Unit Test (`RadarSpatialAudioMathTest.kt`)
* **Action**: Verify pitch calculation across `3.0 .. 30.0 m/s`, boundary clamping at `0.95x` and `1.35x`.

### Test Case 3: Lane-Change Spatial Cue Triggering (`TST-TRK-065.3`)
* **Scope**: Unit Test (`RadarAlertManagerTest.kt`)
* **Action**: Verify spatial tick emission on `IN_RIDER_LANE` to `CHANGING_LANE_SAFE` transition and suppression when disabled.

### Test Case 4: Force Speaker Centered Fallback Invariant (`TST-TRK-065.4`)
* **Scope**: Unit Test (`RadarAlertManagerTest.kt`)
* **Action**: Assert stereo gains collapse to `(1.0, 1.0)` when `route == FORCE_SPEAKER` regardless of lateral offset.

### Test Case 5: Preferences Persistence & DataStore Parity (`TST-TRK-065.5`)
* **Scope**: Unit Test (`TuningConfigTest.kt`)
* **Action**: Verify DataStore persistence and defaults for all 4 new radar spatial audio preferences.

### Test Case 6: 9-Language Localization Parity (`TST-TRK-065.6`)
* **Scope**: Contract Test (`TranslationParityTest.kt`)
* **Action**: Verify 100% key and format specifier parity across all 9 locales in `strings_radar.xml`.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-065.1` | Unit | `RadarSpatialAudioMath.kt`, `RadarAlertManager.kt` | `REQ-TRK-065.1` | Specified |
| `TST-TRK-065.2` | Unit | `RadarSpatialAudioMath.kt`, `RadarAudioSynthesizer.kt` | `REQ-TRK-065.2` | Specified |
| `TST-TRK-065.3` | Unit | `RadarAlertManager.kt` | `REQ-TRK-065.3` | Specified |
| `TST-TRK-065.4` | Unit | `RadarAlertManager.kt` | `REQ-TRK-065.1`, `REQ-TRK-065.4` | Specified |
| `TST-TRK-065.5` | Unit | `TuningPreferencesDataStore.kt` | `REQ-TRK-065.4` | Specified |
| `TST-TRK-065.6` | Localization | `TranslationParityTest.kt` | `REQ-TRK-065.6` | Specified |
| `TST-TRK-065.7` | Regression | Tier 1 Targeted Test Suite | `REQ-PRO-001` | Specified |
