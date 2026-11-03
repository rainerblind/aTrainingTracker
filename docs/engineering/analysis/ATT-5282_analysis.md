# Stage 1 Analysis: ATT-5282 - BikeBellAudioSynthesizer Warm Natural Brass Timbre Refinement

**Ticket**: [ATT-5282](https://rainerblind.atlassian.net/browse/ATT-5282)  
**Sub-task**: [[ATT-5290]](https://rainerblind.atlassian.net/browse/ATT-5290) (`[Analysis]`)  
**Parent Epic**: Standalone Ticket  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.3`  
**Branch**: `feature/ATT-5282`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Statement & Motivation
During Sprint 2026-45.2 on-device review of `ATT-5131`, speaker routing and 3-stage volume escalation (`0.35f -> 0.65f -> 1.00f`) succeeded, but the synthesized bell tone was found harsh and shrill:
1. **Harsh High-Frequency Bias**: Current synthesis uses a high fundamental ($2400\text{ Hz}$) with overtones at $3840\text{ Hz}$, $5280\text{ Hz}$, and $7600\text{ Hz}$. At high volume with 100% hardware `STREAM_ALARM`, these overtones excite small phone micro-speaker resonances and ear sensitivity (2.5–5 kHz), producing a piercing, metallic chirp.
2. **Missing Sub-Octave Hum Body**: Classic brass bicycle bells (e.g. Spurcycle/Crane) derive warmth from a lower dome body resonance (hum tone) around $950\text{--}1050\text{ Hz}$ ringing out with long sustain. Without this hum, the sound lacks physical body and depth.
3. **Envelope Decay Imbalance & Transient Impulse**: Overtones decay too slowly ($\tau_2 = 0.18\text{ s}$), sustaining shrill 4 kHz energy, while the instantaneous velocity jump at $t=0$ acts as a harsh broadband transient click.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Root Cause**:
  - `BikeBellAudioSynthesizer.kt#L60-L83`: Modal distribution lacks lower body resonance ($< 2400\text{ Hz}$); $3.84\text{ kHz}$ overtone has disproportionate amplitude ($0.28$) and duration ($\tau = 0.18\text{ s}$).
  - `BikeBellAudioSynthesizer.kt#L91-L104`: Sine phase starts at $t=0$ with maximal derivative ($ds/dt \approx 22,600\text{ s}^{-1}$), causing micro-speaker cone snapping.
  - `BikeBellAudioSynthesizer.kt#L164`: `synthesizeRollPcm` sums repeated strikes into hard clipping (`coerceIn(-1.0, 1.0)`), introducing buzz-saw odd harmonics.
* **Independent Peer Review & Verification (ATT-3534, Rule 69)**:
  - *Hypothesis Audit*: Ticket hypothesized presence of a 2 ms click and `tanh(sample * 1.4)` saturator. Code inspection reveals no explicit `tanh` or click code in production; the harshness stems from the instantaneous step-derivative at $t=0$ and potential `coerceIn` clipping in rolls. Overdrive saturators like `tanh` would worsen odd-harmonic distortion on phone speakers and must be strictly avoided.
  - *Acoustic Verification*: Introducing a 5-partial modal model—sub-octave hum ($1020\text{ Hz}$, $\tau \approx 0.55\text{ s}$, amp $0.32$), brass fundamental ($2040\text{ Hz}$, $\tau \approx 0.38\text{ s}$, amp $0.46$), nominal overtone ($3120\text{ Hz}$, $\tau \approx 0.08\text{ s}$, amp $0.14$), high chime ($4280\text{ Hz}$, $\tau \approx 0.03\text{ s}$, amp $0.06$), and strike shimmer ($6200\text{ Hz}$, $\tau \approx 0.015\text{ s}$, amp $0.02$) with smooth attack window $(1 - e^{-dt / 0.0015})$—produces an authentic, warm brass chime.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Refine modal frequencies, amplitudes, and decay constants in `BikeBellAudioSynthesizer.kt` for a warm classic brass bell.
  - Add smooth exponential/raised-cosine clapper attack envelope ($\sim 1.5\text{ ms}$) eliminating transient snaps.
  - Scale roll summation headroom to prevent hard clipping distortion.
  - Retain exact buffer durations ($0.6\text{ s}$ double-strike, $2.5\text{ s}$ roll), `AudioTrack.MODE_STATIC`, and thread safety.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - No changes to `TrackingTabsViewModel.kt` volume curve or 3-stage escalation (`0.35f -> 0.65f -> 1.00f`).
  - No changes to UI layer (`BikeBellButton.kt`, `SensorGridScreen.kt`) or audio stream routing (`USAGE_ALARM`, `TYPE_BUILTIN_SPEAKER`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-421` (`REQ-UI-421.4`, `REQ-UI-421.7`).
* **Historical Origin & Commit Trace**: `ATT-4281` (Sprint 2026-43.1, commit `40af18141387`) introduced initial 4-partial synthesis; `ATT-4431` (commit `8158870686a4`) added continuous roll; `ATT-5131` (commit `0a1bd4da89a5`) added speaker routing, alarm volume boost, and 3-stage escalation.
* **Root Reason for Existing Formulation**: 4-partial synthesis ($2.4\text{--}7.6\text{ kHz}$) was an initial mathematical approximation. Without on-device hardware tuning, high-frequency micro-speaker distortion was not apparent until `ATT-5131` boosted volume to 100%.
* **Preservation of Core Invariants**: `USAGE_ALARM`, `TYPE_BUILTIN_SPEAKER` routing, 100% volume boost with auto-restore, `AudioTrack.MODE_STATIC` zero-latency replay, and contract tests (`doubleStrikePcm.size == 52920`, `rollPcm.size == 220500`) are 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. **5-Mode Brass Acoustic Model (`BikeBellAudioSynthesizer.kt`)**:
   - $f_0 = 1020\text{ Hz}$ (Hum): amp $= 0.32$, $\tau = 0.55\text{ s}$ (warm sustaining body).
   - $f_1 = 2040\text{ Hz}$ (Prime): amp $= 0.46$, $\tau = 0.38\text{ s}$ (clear dome resonance).
   - $f_2 = 3120\text{ Hz}$ (Tierce): amp $= 0.14$, $\tau = 0.08\text{ s}$ (rapidly decaying brass color).
   - $f_3 = 4280\text{ Hz}$ (Nominal): amp $= 0.06$, $\tau = 0.03\text{ s}$ (brief initial strike shimmer).
   - $f_4 = 6200\text{ Hz}$ (Clapper): amp $= 0.02$, $\tau = 0.015\text{ s}$ (soft crisp impact).
   - Strike 2 clapper rebound detuned by $+2.5\%$ ($1045\text{ Hz}$, $2091\text{ Hz}$, etc.).
2. **Attack Smoothing**: Apply attack factor $(1.0 - \exp(-dt / 0.0015))$ to eliminate impulse step artifacts.
3. **Headroom & Roll Normalization**: Normalize sum in `synthesizeRollPcm` with 0.85 multiplier to guarantee linear headroom without saturation.
4. **Side-Effect & Cross-Caller Impact Audit (Rule 68)**:
   - `TrackingTabsViewModel`: Consumes `synthesizer.ring()` and `synthesizer.startRoll()` unchanged.
   - `BikeBellAudioSynthesizerTest`: Preserves byte array sizes (`52920` and `220500`), $t=0$ zero value, peak presence, and decay contracts.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero latency: In-memory `AudioTrack.MODE_STATIC` pre-generation without file I/O.
  2. Speaker routing & volume boost: `TYPE_BUILTIN_SPEAKER` and hardware alarm boost remain intact.
  3. Buffer contract parity: Exact sample rates and PCM byte array dimensions preserved.
* **Risk Rating**: **LOW**. Pure mathematical synthesis refinement inside `BikeBellAudioSynthesizer.kt`; no database, UI, service, or Android IPC modifications.
