# Stage 3 Implementation Plan: ATT-5087 - Cycling Radar Traffic Simulator, Scenario Engine & Preview UI

**Ticket**: [ATT-5087](https://rainerblind.atlassian.net/browse/ATT-5087) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-5087`  
**Requirement Mapping**: `REQ-CON-029` (`029.1`–`029.5`) | **Test Spec**: `TST-CON-029` (`029.1`–`029.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Technical Architecture & SWE.2 Design
* **Traffic Scenario Engine (`RadarTrafficSimulator.kt`)**:
  - Implements `RadarTrafficSimulator` singleton under `com.atrainingtracker.banalservice.devices.radar`.
  - Defines `RadarScenario` enum: `SINGLE_CAR` (140m $\rightarrow$ 0m at 12 m/s, vehicle count increment), `CONVOY` (3 simultaneous targets), `HIGH_SPEED` (140m at 25 m/s, `FAST_APPROACHING`), `DISCONNECT_RECONNECT` (starvation to `DISCONNECTED_ERROR` then recovery), and `COUNTRY_ROAD_LOOP` (periodic randomized traffic).
  - Emits 8 Hz frames (125 ms ticks) into `RadarStateRepository.updateState()` and `RadarWindowAggregator.onTargetsUpdated()`.
  - Exposes deterministic stepping methods (`generateFrame(scenario, elapsedMs)`) for synchronous unit test assertion without clock delays.
* **Hardware Preemption & Safety Invariants**:
  - Hardware preemption guard: automatically halts and releases repository when physical `BIKE_RADAR` connects (`isReceivingData() == true`).
  - Coroutine job safety: maintains internal `Job?` launched on dedicated/caller dispatcher; calling `stop()` cancels job and restores clean state (`RadarStateRepository.reset()`). Zero writes to DB outside active workouts.
* **Settings & Preview UI (`RadarSettingsSection.kt`)**:
  - Composable in `com.atrainingtracker.trainingtracker.ui.settings.radar` providing scenario selector, start/stop toggle, and live simulation status indicator.
  - UI Consistency: Material 3 `SingleChoiceSegmentedButtonRow` / `FilterChip`, standard `ElevatedCard`, dynamic typography and colors from `MaterialTheme.colorScheme`.
* **Localization**:
  - Add simulator controls and scenario strings to domain-partitioned `strings_radar.xml` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero inline emojis.

---

## 2. Atomic Implementation Steps

| Step | Component | Target Files | Action & Core Invariants | Traceability |
| :--- | :--- | :--- | :--- | :--- |
| **1** | Localization | `res/values*/strings_radar.xml` | Add simulator titles, scenario names, and state descriptions across all 9 locales (zero emojis). | `REQ-CON-029.5`<br>`TST-CON-029.4` |
| **2** | Scenario Engine | `RadarTrafficSimulator.kt` | Implement `RadarScenario` enum and kinematic frame generators for all 5 scenarios with deterministic `generateFrame()`. | `REQ-CON-029.1`<br>`TST-CON-029.1` |
| **3** | 8 Hz Pipeline & Preemption | `RadarTrafficSimulator.kt` | Implement 8 Hz coroutine loop feeding `RadarStateRepository` & `RadarWindowAggregator`; add hardware preemption and `stop()` cleanup. | `REQ-CON-029.2,3`<br>`TST-CON-029.1-3` |
| **4** | Preview Settings UI | `RadarSettingsSection.kt` | Author Material 3 settings section with scenario picker and preview toggle. | `REQ-CON-029.4`<br>`TST-CON-029.5` |
| **5** | Unit Tests | `RadarTrafficSimulatorTest.kt` | Author unit test suite covering kinematics, overtake count, convoy, high-speed, disconnect, preemption, and job cancellation. | `REQ-CON-029.1-3`<br>`TST-CON-029.1-3` |
| **6** | Parity & Regression | `TranslationParityTest.kt`<br>Test Suite | Run `TranslationParityTest` (100% parity) and targeted test suite (`devices.radar.*`, `build.*`). | `REQ-PRO-001`<br>`TST-CON-029.4,5` |

---

## 3. System Invariants & Rollback Safety
* **Code Lock (Rule 4)**: No production source code modified until Gate 3 passes audit (`python3 tools/jira_util.py check-gate`).
* **Real Hardware Priority**: Physical radar packets immediately preempt and terminate active simulation.
* **Zero Database Corruption**: Simulation operates purely in-memory via `RadarStateRepository`; zero phantom DB samples recorded when tracking is inactive.
* **Risk Rating**: **LOW** (Encapsulated simulation engine and settings UI; zero schema alterations or sensor driver regressions).
