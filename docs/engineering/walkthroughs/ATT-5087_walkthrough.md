# ATT-5087 Walkthrough: Built-in Cycling Radar Traffic Simulator for Indoor Testing, UI Preview & Demo Mode

**Ticket**: [ATT-5087](https://rainerblind.atlassian.net/browse/ATT-5087)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Sprint**: `2026-46.1` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: `REQ-CON-029` | **Test Mapping**: `TST-CON-029`  

---

## 1. Executive Summary

Under `ATT-5087` (governed by `REQ-CON-029` / `TST-CON-029`):
- **Scriptable Traffic Scenarios (`REQ-CON-029.1`)**: Implemented `RadarTrafficSimulator.kt` with 5 presets: `SINGLE_CAR` (140m approach at 12 m/s, vehicle count increment), `CONVOY` (3 simultaneous targets), `HIGH_SPEED` (140m at 25 m/s, `FAST_APPROACHING`), `DISCONNECT_RECONNECT` (starvation to `DISCONNECTED_ERROR` followed by recovery), and `COUNTRY_ROAD_LOOP` (30s continuous randomized traffic loop).
- **8 Hz Stream Pipeline Integration (`REQ-CON-029.2`)**: Implemented continuous 8 Hz (125ms) frame emission into reactive `RadarStateRepository.kt` (`StateFlow<RadarState>`) and `RadarWindowAggregator.kt`, exercising vehicle passing hysteresis and scalar metrics.
- **Hardware Preemption & Safety Invariants (`REQ-CON-029.3`)**: Implemented hardware preemption guard automatically halting simulation and yielding repository when physical radar connects (`isReceivingData() == true`) or tracking stops. Structured coroutine job cancellation ensures zero phantom writes or background leaks.
- **Settings & Preview UI (`REQ-CON-029.4`)**: Authored Material 3 `RadarSettingsSection.kt` composable providing scenario selection chips, live simulation status indicators, and start/stop controls using `MaterialTheme.colorScheme`.
- **9-Language Localization Parity (`REQ-CON-029.5`)**: Authored 17 simulator string resources in domain-partitioned `strings_radar.xml` across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with zero inline emojis.

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Component | Verification Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-029.1` | `TST-CON-029.1` | `RadarTrafficSimulator.kt` | Kinematics, 5 Presets, Deterministic Frame Gen | **Verified** |
| `REQ-CON-029.2` | `TST-CON-029.1` | `RadarTrafficSimulator.kt`, `RadarStateRepository.kt` | 8 Hz Pipeline, Aggregator Integration | **Verified** |
| `REQ-CON-029.3` | `TST-CON-029.2`, `TST-CON-029.3` | `RadarTrafficSimulator.kt` | Hardware Preemption, Coroutine Job Cancellation | **Verified** |
| `REQ-CON-029.4` | `TST-CON-029.5` | `RadarSettingsSection.kt` | M3 Settings & Preview Composable | **Verified** |
| `REQ-CON-029.5` | `TST-CON-029.4` | `strings_radar.xml` (9 locales) | 9-Language Parity Contract Test | **Verified** |

---

## 3. Verification & Test Execution

- **Executed Test Scope**:
  - `Tier 1 targeted: RadarTrafficSimulatorTest, RadarWindowAggregatorTest, banalservice.devices.radar.*, localization.TranslationParityTest, ui.theme.ThemeColorCentralizationAuditTest, ui.components.core.*, build.*`
  - Suites run: `RadarTrafficSimulatorTest` (7 unit tests covering kinematics, passing hysteresis, convoy, high-speed threat, disconnect/reconnect, hardware preemption, and job cancellation), `RadarWindowAggregatorTest`, `TranslationParityTest`, `ThemeColorCentralizationAuditTest`, `AspiceSweAuditContractTest`.
  - Result: **100% PASS** (exit code 0, 0 failures, 0 errors, 0 regressions).
- **Physical Device / On-Device Checks**:
  - Checked `adb devices` (no live devices attached; emulator boot strictly avoided per Rule 79).
  - Pure simulation engine and Compose settings preview UI verified via JVM unit tests, coroutine test dispatchers, and Compose contract tests. Physical APK deployment omitted per Rule 60.
