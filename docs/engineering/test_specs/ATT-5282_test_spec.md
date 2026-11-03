# Stage 2: Requirement & Test Specification - ATT-5282: Warm Brass BikeBell Refinement

**Ticket**: [ATT-5282](https://rainerblind.atlassian.net/browse/ATT-5282) | **Sub-task**: `[Req & Test Spec]`  
**Target Release**: `V4.9.41` | **Active Sprint**: `2026-45.3` | **Branch**: `feature/ATT-5282`  
**Requirement Mapping**: `REQ-UI-421.4`, `REQ-UI-421.7` | **Test Spec**: `TST-UI-384.3`, `TST-UI-384.8`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-UI-421.4)

### 1.1 Problem Statement & Rationale
Sprint 2026-45.2 user feedback on `ATT-5131` confirmed effective speaker routing and volume escalation, but noted that high-volume synthesis ($2400\text{--}7600\text{ Hz}$) sounded harsh and shrill on mobile speakers. Waveform synthesis requires acoustic refinement to produce a warmer brass dome resonance with smooth clapper attack envelopes.

### 1.2 Functional & Architectural Requirements
1. **5-Mode Inharmonic Brass Model**:
   - Sub-octave hum: $f_0 \approx 1020\text{ Hz}$, amp $= 0.32$, $\tau = 0.55\text{ s}$; Prime fundamental: $f_1 \approx 2040\text{ Hz}$, amp $= 0.46$, $\tau = 0.38\text{ s}$.
   - Tierce: $f_2 \approx 3120\text{ Hz}$, amp $= 0.14$, $\tau = 0.08\text{ s}$; Nominal: $f_3 \approx 4280\text{ Hz}$, amp $= 0.06$, $\tau = 0.03\text{ s}$; Clapper: $f_4 \approx 6200\text{ Hz}$, amp $= 0.02$, $\tau = 0.015\text{ s}$.
   - Strike 2 clapper rebound detuned by $+2.5\%$ ($f_0' = 1045\text{ Hz}$, $f_1' = 2091\text{ Hz}$).
2. **Attack Window Smoothing**: Apply $(1.0 - \exp(-dt / 0.0015))$ to eliminate step-derivative cone snapping at $t=0$.
3. **Roll Headroom Scaling**: Scale repeated strike sums by $0.85$ to prevent hard clipping distortion.
4. **Preserve Audio Infrastructure**: Maintain `USAGE_ALARM`, `TYPE_BUILTIN_SPEAKER` routing, 100% volume boost with auto-restore, and `AudioTrack.MODE_STATIC` zero-latency playback.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Warm Brass Synthesis)**:
  * *Given* `BikeBellAudioSynthesizer` generating PCM buffers,
  * *When* `synthesizeDoubleStrikePcm(44100)` is invoked,
  * *Then* it returns exactly 52,920 bytes (0.6s 16-bit mono) combining the 5 resonant modes with non-zero peak and smooth exponential decay.
* **Criterion 2 (Attack Window Smoothing)**:
  * *Given* `calculateStrikeSample(dt, f)`,
  * *When* evaluated at $dt = 0$,
  * *Then* sample is $0.0$ and rises smoothly without instantaneous step derivative.
* **Criterion 3 (Roll Headroom & Non-Clipping)**:
  * *Given* `synthesizeRollPcm(44100)`,
  * *When* summing repeated striker chimes over 2.5s,
  * *Then* it returns 220,500 bytes and peak amplitudes stay within $[-1.0, 1.0]$ without harsh clipping saturation.

### 1.4 System Invariants
- Zero disk I/O; pre-loaded in-memory `AudioTrack.MODE_STATIC`.
- Buffer size parity: 52,920 bytes (double strike) and 220,500 bytes (roll).
- Thread-safe lifecycle teardown via `close()`.

---

## 2. Test Specification (TST-UI-384.3)

### Test Case 1: `testPcmSynthesis_5ModeBrassAcousticPropertiesAndEnvelope` (`TST-UI-384.3a`)
* **Scope**: Unit Test (`BikeBellAudioSynthesizerTest.kt`)
* **Preconditions**: Default 44.1 kHz sample rate.
* **Action**: Synthesize `doubleStrikePcm` and `rollPcm`, evaluating modal frequency constants, attack factor, and exponential decay.
* **Expected Result**: Buffer dimensions match (52,920 & 220,500 bytes); initial sample is 0; sample at 0.5s is $< 0.15$; roll samples remain within valid linear range.

### Test Case 2: `testBikeBellAudioSynthesizer_concurrencyAndSpeakerRouting` (`TST-UI-384.3b`)
* **Scope**: Unit Test (`BikeBellAudioSynthesizerTest.kt`)
* **Preconditions**: Mock `AudioTrack`, `AudioManager`, and `AudioDeviceInfo`.
* **Action**: Multi-threaded ring/roll execution and speaker routing verification.
* **Expected Result**: 100% thread safety; `TYPE_BUILTIN_SPEAKER` verified; volume boost and restore timers intact.

### Test Case 3: Clean-Room Regression Suite (`TST-UI-384.8`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.audio.*"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-384.3a` | Unit | `BikeBellAudioSynthesizer.synthesize*` | `REQ-UI-421.4` | Specified |
| `TST-UI-384.3b` | Unit / Mock | `BikeBellAudioSynthesizer.ring/startRoll` | `REQ-UI-421.4` | Specified |
| `TST-UI-384.8` | Regression | Full audio package suite | `REQ-UI-421.7` | Specified |
