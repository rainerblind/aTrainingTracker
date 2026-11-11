# Stage 2: Requirement & Test Specification - ATT-6709: Disable phone-speaker stereo attenuation on SYSTEM_DEFAULT, select FAST_APPROACHING target kinematics, add per-target lane-change hysteresis, and release preview AudioTracks

**Ticket**: [ATT-6709](https://rainerblind.atlassian.net/browse/ATT-6709)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `Sprint 2026-46.2` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-063.1`, `REQ-TRK-065.1`, `REQ-TRK-065.3`, `REQ-TRK-065.5`  
**Test Spec Mapping**: `TST-TRK-063.1`, `TST-TRK-065.1`, `TST-TRK-065.3`, `TST-TRK-065.4`  
**Branch**: `feature/ATT-6709` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-TRK-063 & REQ-TRK-065 Refinement)

### 1.1 Problem Statement & Rationale
Sprint 2026-46.1 review identified three defects: (1) `computeStereoGains()` only bypassed panning on `FORCE_SPEAKER`, unnecessarily attenuating outdoor speaker output on `SYSTEM_DEFAULT` when headphones are disconnected; (2) `primaryTarget` selected solely by distance, using slow car kinematics during `FAST_APPROACHING` escalation, while lane-change tracking lacked multi-target state and hysteresis; (3) `previewSynthesizer` leaked native `AudioTrack`s on Settings exit, and UI controls used stateless `FilterChip`s and full-size `Switch` rows.

### 1.2 Functional & Architectural Requirements
1. **Speaker Panning Collapse (`REQ-TRK-065.1`)**: `computeStereoGains()` SHALL collapse channel gains to centered `(1.0f, 1.0f)` when `route == FORCE_SPEAKER` OR (`route == SYSTEM_DEFAULT` when `hasHeadphones == false`), preserving full phone-speaker loudness.
2. **Fast Threat Kinematics Selection (`REQ-TRK-063.1`)**: On `FAST_APPROACHING` threat escalation, `RadarAlertManager` SHALL select the target with highest approach speed among `FAST_APPROACHING` targets to compute pitch and panning.
3. **Multi-Target Lane Tracking & Hysteresis (`REQ-TRK-065.3`)**: `RadarAlertManager` SHALL track per-target `LaneTrackState` across multi-car convoys with spatial boundary hysteresis ($|x| < 1.0\text{ m}$ to $|x| \ge 1.5\text{ m}$) and a $3,000\text{ ms}$ per-target cooldown, pruning departed targets to prevent 8 Hz jitter fluttering.
4. **AudioTrack Lifecycle & UI Cleanliness (`REQ-TRK-065.5`)**: `RadarAlertManager` SHALL expose `releasePreviewSynthesizer()`, invoked via `DisposableEffect` upon `RadarSettingsSection` disposal. Preview actions SHALL use `AssistChip` (Design Guidelines §1.2), subordinate toggles SHALL use `SubordinateTuningToggleItem` (Design Guidelines §3.3), and deprecated `setStereoVolume()` on mono tracks SHALL be replaced with `setVolume()`.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-TRK-063` (Alert Engine) and `REQ-TRK-065` (Spatial Audio & Pitch Modulation) targeting `RadarSpatialAudioMath.kt`, `RadarAlertManager.kt`, `RadarAudioSynthesizer.kt`, and `RadarSettingsSection.kt`.
* `Historical Origin & Commit Trace`: `ATT-5120` (commit `28fb9956`) and `ATT-5086` (commit `85790a1b`). `ATT-6709` refines speaker fallback, fast-threat target kinematics, multi-car lane tracking, and resource lifecycle.
* `Root Reason for Existing Formulation`: Initial spatial implementation only considered `FORCE_SPEAKER` as a phone speaker route; single-target tracking was a simplifying prototype assumption; stateless `FilterChip` and `Switch` rows preceded modern design guidelines.
* `Preservation of Core Invariants`: Full speaker volume preserved; zero regression on headphone stereo panning; zero native `AudioTrack` leaks; 100% 9-language translation parity.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* route `SYSTEM_DEFAULT` without headphones connected, *When* an alert chime triggers, *Then* stereo gains collapse to `(1.0f, 1.0f)`.
* *Given* a slow trailing car and a fast approaching threat, *When* escalating to `FAST_APPROACHING`, *Then* the fast threat's kinematics drive pitch and panning.
* *Given* `RadarSettingsSection` leaves composition, *When* disposed, *Then* `releasePreviewSynthesizer()` releases all preview `AudioTrack`s.

---

## 2. Test Specification (TST-TRK-063 & TST-TRK-065 Refinement)

### Test Case 1: Stereo Gains Collapse Without Headphones (`TST-TRK-065.1a`)
* **Scope**: JVM Unit Test (`RadarSpatialAudioMathTest.kt`)
* **Assertions**: Verifies `computeStereoGains()` returns `(1.0f, 1.0f)` when `hasHeadphones == false` on `SYSTEM_DEFAULT` and `FORCE_SPEAKER`.

### Test Case 2: Fast Threat Kinematics Attribution (`TST-TRK-063.1a`)
* **Scope**: JVM Unit Test (`RadarAlertManagerTest.kt`)
* **Assertions**: Verifies `triggerFastThreatAlert` selects the `FAST_APPROACHING` target kinematics over a closer slow-moving car.

### Test Case 3: Multi-Target Lane Change Hysteresis & Cooldown (`TST-TRK-065.3a`)
* **Scope**: JVM Unit Test (`RadarAlertManagerTest.kt`)
* **Assertions**: Verifies per-target lane state tracking across multi-car convoys, respecting spatial thresholds and 3,000 ms per-target cooldown, and pruning departed IDs.

### Test Case 4: Preview Synthesizer Release Lifecycle (`TST-TRK-065.4a`)
* **Scope**: JVM Unit Test (`RadarAlertManagerTest.kt`)
* **Assertions**: Verifies `releasePreviewSynthesizer()` closes and nulls `previewSynthesizer` without leaking `AudioTrack` native handles.

### Test Case 5: Tier 1 Targeted Audio Radar Suite (`TST-TRK-065.7a`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.audio.radar.*"`

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-065.1a` | Unit | `RadarSpatialAudioMathTest.kt` | `REQ-TRK-065.1` | Specified |
| `TST-TRK-063.1a` | Unit | `RadarAlertManagerTest.kt` | `REQ-TRK-063.1` | Specified |
| `TST-TRK-065.3a` | Unit | `RadarAlertManagerTest.kt` | `REQ-TRK-065.3` | Specified |
| `TST-TRK-065.4a` | Unit | `RadarAlertManagerTest.kt` | `REQ-TRK-065.5` | Specified |
| `TST-TRK-065.7a` | Suite | Full Audio Radar Suite | `REQ-TRK-063.1`, `REQ-TRK-065.1..5` | Specified |
