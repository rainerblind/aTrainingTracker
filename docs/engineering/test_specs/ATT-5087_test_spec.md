# Stage 2: Requirement & Test Specification - ATT-5087: Cycling Radar Traffic Simulator

**Ticket**: [ATT-5087](https://rainerblind.atlassian.net/browse/ATT-5087) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-5087`  
**Requirement Mapping**: `REQ-CON-029` | **Test Spec ID**: `TST-CON-029`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-CON-029)

### 1.1 Problem Statement & Rationale
Developing cycling radar UI overlays (ATT-5085), audio/haptic alerts (ATT-5086), and traffic analytics (ATT-5088) without physical hardware on open roads requires a scriptable traffic simulator. `RadarTrafficSimulator` provides realistic traffic scenarios at 8 Hz feeding `RadarStateRepository` and `RadarWindowAggregator`, enabling indoor workouts, UI preview, and automated clean-room CI validation. Physical radar hardware retains strict precedence.

### 1.2 Functional & Architectural Requirements
* **REQ-CON-029.1** (*Scriptable Scenario Presets*): `RadarTrafficSimulator` SHALL implement 5 scriptable scenario presets: Single Overtaking Car (140m approach at 12 m/s, passing at 0m), Multi-Vehicle Convoy (3 simultaneous targets), High-Speed Threat (25 m/s, `FAST_APPROACHING`), Mid-Ride Disconnect (starvation to `DISCONNECTED_ERROR` then recovery), and Continuous Country Road Loop (randomized traffic intervals).
* **REQ-CON-029.2** (*8 Hz Stream Pipeline Integration*): The simulator SHALL emit synthetic target frames at 8 Hz into `RadarStateRepository` (`StateFlow<RadarState>`) and update `RadarWindowAggregator`, faithfully exercising vehicle passing hysteresis and scalar metrics.
* **REQ-CON-029.3** (*Hardware Preemption & Safety Invariants*): The simulator SHALL immediately halt when physical radar hardware connects and streams data (`isReceivingData() == true`), or when workout tracking stops. Zero phantom writes to database during inactive tracking.
* **REQ-CON-029.4** (*Settings & Preview Controls*): Interactive scenario selection, preview toggle, and status indicators SHALL be exposed in `RadarSettingsSection.kt`.
* **REQ-CON-029.5** (*9-Language Localization Parity*): All simulator UI strings and scenario names in `strings_radar.xml` SHALL maintain 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT (zero inline emojis).

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* *Given* Single Overtaking Car scenario selected, *When* simulation runs, *Then* a vehicle approaches from 140m at 12 m/s, passes at 0m, and increments `RADAR_VEHICLE_COUNT`.
* *Given* an active simulation, *When* physical radar connects and streams data, *Then* the simulator halts immediately and yields control to physical hardware.
* *Given* High-Speed Threat scenario, *When* vehicle closes at 25 m/s, *Then* threat level evaluates to `FAST_APPROACHING`.
* *Invariants*: Real hardware priority; coroutine job cleanup on stop; zero DB writes outside active tracking; 100% 9-language translation parity.

---

## 2. Test Specification (TST-CON-029)

### Test Case 1: Scenario Generation & 8 Hz Stream Unit Tests (`TST-CON-029.1`)
* **Scope**: Unit Test (`RadarTrafficSimulatorTest.kt`)
* **Assertion**: Verify all 5 scenarios emit proper target vectors at 8 Hz; verify Single Overtaking Car passing increments `RADAR_VEHICLE_COUNT`; verify Convoy 3 targets; verify High-Speed Threat `FAST_APPROACHING`; verify Disconnect error state and recovery.

### Test Case 2: Hardware Precedence & Preemption Unit Tests (`TST-CON-029.2`)
* **Scope**: Unit Test (`RadarTrafficSimulatorTest.kt`)
* **Assertion**: Verify simulator halts and cancels active jobs immediately upon physical radar data arrival.

### Test Case 3: Coroutine Lifecycle Cleanup Unit Tests (`TST-CON-029.3`)
* **Scope**: Unit Test (`RadarTrafficSimulatorTest.kt`)
* **Assertion**: Verify `stop()` cleanly cancels active coroutine jobs without leaking background loops.

### Test Case 4: 9-Language Localization Parity Test (`TST-CON-029.4`)
* **Scope**: Contract Test (`TranslationParityTest.kt`)
* **Assertion**: Verify 100% key and format parity in `strings_radar.xml` across all 9 locales.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-CON-029.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.radar.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertion**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-029.1` | Unit | `RadarTrafficSimulator.kt`, `RadarTrafficSimulatorTest.kt` | `REQ-CON-029.1`, `REQ-CON-029.2` | Specified |
| `TST-CON-029.2` | Unit | `RadarTrafficSimulator.kt`, `RadarTrafficSimulatorTest.kt` | `REQ-CON-029.3` | Specified |
| `TST-CON-029.3` | Unit | `RadarTrafficSimulator.kt`, `RadarTrafficSimulatorTest.kt` | `REQ-CON-029.3` | Specified |
| `TST-CON-029.4` | Parity | `strings_radar.xml`, `TranslationParityTest.kt` | `REQ-CON-029.5` | Specified |
| `TST-CON-029.5` | Regression | `devices.radar.*`, `build.*` | `REQ-PRO-001` | Specified |
