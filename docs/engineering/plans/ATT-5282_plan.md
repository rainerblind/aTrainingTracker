# Stage 3: Implementation Plan - ATT-5282: Warm Brass BikeBell Refinement

**Ticket**: [ATT-5282](https://rainerblind.atlassian.net/browse/ATT-5282)  
**Sub-task**: [[ATT-5292]](https://rainerblind.atlassian.net/browse/ATT-5292) (`[Impl-Plan]`)  
**Parent Epic**: Standalone Ticket  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.3`  
**Requirement Mapping**: `REQ-UI-421.4`, `REQ-UI-421.7`  
**Test Mapping**: `TST-UI-384.3a`, `TST-UI-384.3b`, `TST-UI-384.8`  
**Branch**: `feature/ATT-5282`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Description & Background
Sprint 2026-45.2 user feedback on `ATT-5131` confirmed effective speaker routing and volume escalation, but noted that high-volume synthesis ($2400\text{--}7600\text{ Hz}$) sounded harsh and shrill on phone micro-speakers. The synthesis lacks lower body resonance and smooth strike attack. This plan refines `BikeBellAudioSynthesizer.kt` with a 5-mode brass acoustic model (introducing a $1020\text{ Hz}$ sub-octave hum tone and $2040\text{ Hz}$ prime dome resonance), a $1.5\text{ ms}$ exponential attack smoothing envelope, and $0.85\times$ roll headroom scaling to prevent clipping distortion.

---

## 2. Traceability & Requirements Mapping
* **`REQ-UI-421.4` (Acoustic Brass Synthesis & Audio Routing)** $\leftrightarrow$ `TST-UI-384.3a`, `TST-UI-384.3b`: 5-mode modal frequencies, smooth attack window, roll headroom scaling, speaker routing, and alarm boost.
* **`REQ-UI-421.7` (System Invariants & Non-Regression)** $\leftrightarrow$ `TST-UI-384.8`: Buffer size parity (52,920 & 220,500 bytes), `MODE_STATIC` zero latency, and 100% test pass rate.

---

## 3. System Invariants & Preserved Behavior
1. **Buffer Size & Timing Parity**: Exactly 52,920 bytes (0.6s double strike) and 220,500 bytes (2.5s roll) at 44.1 kHz 16-bit mono.
2. **Zero Disk I/O & Latency**: Pre-generated in-memory buffers played via `AudioTrack.MODE_STATIC`.
3. **Platform Audio Routing**: `TYPE_BUILTIN_SPEAKER` routing, `STREAM_ALARM` 100% temporary volume boost, and delayed auto-restore.
4. **Escalation & UI Intact**: 3-stage escalation in `TrackingTabsViewModel` (`0.35f -> 0.65f -> 1.00f`) and `BikeBellButton` gestures unmodified.

---

## 4. Proposed Architectural Changes
* **Component: `BikeBellAudioSynthesizer.kt`**:
  - Modal constants: Hum ($1020\text{ Hz}$, $\tau = 0.55\text{ s}$, $A = 0.32$), Prime ($2040\text{ Hz}$, $\tau = 0.38\text{ s}$, $A = 0.46$), Tierce ($3120\text{ Hz}$, $\tau = 0.08\text{ s}$, $A = 0.14$), Nominal ($4280\text{ Hz}$, $\tau = 0.03\text{ s}$, $A = 0.06$), Clapper ($6200\text{ Hz}$, $\tau = 0.015\text{ s}$, $A = 0.02$).
  - Strike 2 rebound detuning: $+2.5\%$ ratio ($f_k' = 1.025 \cdot f_k$).
  - Attack factor: $(1.0 - \exp(-dt / 0.0015))$ applied to modal sums.
  - Headroom: $0.85\times$ scaling in `synthesizeRollPcm` before `.coerceIn(-1.0, 1.0)`.
* **UI Consistency (Rule 23)**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 5-Mode Modal Model & Attack Window (`BikeBellAudioSynthesizer.kt`, `TST-UI-384.3a`)
* Files: `BikeBellAudioSynthesizer.kt`
* Define modal constants (`HUM_FREQ = 1020.0`, `FUNDAMENTAL_FREQ = 2040.0`, `TIERCE_FREQ = 3120.0`, `NOMINAL_FREQ = 4280.0`, `CLAPPER_FREQ = 6200.0`, decay constants $\tau_0\dots\tau_4$, amplitudes $A_0\dots A_4$).
* Update `calculateStrikeSample` and `calculateRollStrikeSample` with 5 partials and smooth attack envelope.

### Step 2: Headroom Scaling in Roll Synthesis (`BikeBellAudioSynthesizer.kt`, `TST-UI-384.3a`)
* Files: `BikeBellAudioSynthesizer.kt`
* Update `synthesizeRollPcm` to scale cumulative strike sum by $0.85\times$ preventing hard-clipping distortion.

### Step 3: Synthesis & Acoustic Unit Tests (`BikeBellAudioSynthesizerTest.kt`, `TST-UI-384.3a/b`)
* Files: `BikeBellAudioSynthesizerTest.kt`
* Update `testPcmSynthesis_mathematicalPropertiesAndDuration` to assert buffer dimensions, zero $t=0$ sample, smooth attack rise, decay envelope, and roll headroom.

### Step 4: Verification Suite (`TST-UI-384.8`)
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.audio.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* Command: `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted audio unit tests, lifecycle/routing tests, and ASPICE requirement governance script.
* **Rollback**: Clean git revert of `feature/ATT-5282` branch; zero database schema alterations or shared state risks.
