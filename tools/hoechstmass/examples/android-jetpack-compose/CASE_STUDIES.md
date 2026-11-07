# Real-World Adversarial Review Case Studies (Agile Agentic ASPICE)

This document details three authentic, production case studies from **aTrainingTracker** where the **Höchstmaß (Agile Agentic ASPICE)** methodology and dual-agent adversarial auditing prevented critical regressions, performance cliffs, and hallucinated APIs from reaching production.

---

## Case Study 1: Mader-Heck Metabolic Model & Simulation Drift (ATT-4786 / ATT-4785)

### 1. The Engineering Challenge
The Mader-Heck metabolic model simulates non-linear biological kinetics in endurance athletes: lactate production ($\dot{V}La_{max}$ glycolytic rate), oxidative phosphorylation ($\dot{V}O_2$ aerobic uptake), and pyruvate accumulation based on coupled non-linear differential equations (Mader 1984, Heck 2003).

### 2. Solo LLM Failure Mode
When tasked with implementing continuous lactate accumulation curves, single-pass LLM prompts consistently introduced **floating-point equation drift**:
* Inverting physiological exponents in non-linear Michaelis-Menten kinetics.
* Off-by-one errors in Euler/Runge-Kutta numerical integration steps.
* Using naive Newton-Raphson root-finding without boundary clamping, leading to numerical divergence and `NaN` lactate predictions at supra-threshold wattages.

### 3. The Höchstmaß Defense
* **Stage 2 Formal Requirements**: `REQ-MET-001` formally bound the mathematical equations against peer-reviewed physiology papers with exact tolerance margins ($\epsilon < 10^{-5}$).
* **Gate 2 & Gate 4 Adversarial Audit (`subagent_pro`)**: Agent 2 mathematically audited Agent 1's numerical implementation against the literature formula, rejecting draft code when step-size approximations drifted beyond theoretical tolerance.
* **Automated Unit Testing**: Comprehensive numerical regression tests asserted exact metabolic steady-state outputs across 100W–500W load steps.

---

## Case Study 2: Beat-to-Beat RR Intervals & DFA alpha-1 HRV (ATT-3539)

### 2.1 The Engineering Challenge
Real-time computation of Detrended Fluctuation Analysis ($\alpha_1$) from beat-to-beat RR intervals to detect the aerobic threshold ($a_1 \approx 0.75$) during high-intensity exercise. High-precision Bluetooth Low Energy (BLE) heart rate monitors transmit RR intervals at millisecond accuracy.

### 2.2 Solo LLM Failure Mode
When constructing the real-time HRV pipeline, generative LLMs generated code with severe architectural flaws:
* **Memory Allocation Explosions**: Allocating new `ArrayList` and `DoubleArray` instances on every 1,000 Hz incoming sensor packet, triggering frequent Android Garbage Collection (GC) pauses and dropped Bluetooth packets.
* **Race Conditions**: Updating UI state directly from asynchronous Bluetooth GATT callback threads without synchronization.
* **Detrending Boundary Glitches**: Failing to handle non-stationary detrending window transitions during sudden heart rate spikes.

### 2.3 The Höchstmaß Defense
* **Zero-Allocation Invariant**: Gate 3 and Gate 4 enforced a strict circular buffer architecture (`RingBuffer`) with pre-allocated primitive arrays on the critical sensor path.
* **Thread Isolation & StateFlow**: State updates were strictly decoupled from Bluetooth callbacks and funneled via `Dispatchers.Default` into a single thread-safe `StateFlow`.
* **Empirical Validation**: Automated tests cross-verified the output of the real-time algorithm against gold-standard Kubios HRV reference exports.

---

## Case Study 3: Cycling Rearview Radar Bytecode Auditing (ATT-1286, ATT-5111, ATT-5117)

### 3.1 The Engineering Challenge
Integration of ANT+ Bike Radar (Garmin Varia) target tracking, displaying real-time vehicle proximity, approach speed, and threat levels over low-level ANT+ radio channels.

### 3.2 Solo LLM Failure Mode
When generating sensor integration code, the LLM suffered from **SDK API Hallucinations**:
* Inventing plausible-sounding ANT+ SDK listener callbacks (e.g. `onTargetVelocityChanged`) that did not exist in the official Dynastream `AntRadioService` library.
* Generating synthetic overload methods that compiled against local stubs but failed at runtime with `NoSuchMethodError` on real hardware.

### 3.3 The Höchstmaß Defense
* **Bytecode Ground-Truth Verification (`javap`)**: Invariant audits used bytecode disassembly (`javap -c -p`) to inspect the compiled `.class` files against the authentic binary JAR of the ANT+ SDK.
* **Automated Contract Tests**: Tests such as `AspiceSweAuditContractTest` and `DeadCodeEliminationContractTest` asserted that all SDK references resolved cleanly against genuine runtime binaries.
* **Gate 4 Review**: Detected and eliminated phantom method calls before release candidates were built.
