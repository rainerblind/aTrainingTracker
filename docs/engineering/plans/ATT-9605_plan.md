# Stage 3: Implementation Plan - ATT-9605: `ANTBikeSpeedDevice` & `ANTBikeSpeedAndCadenceDevice` Null Speed Guard & Standstill Pace

**Ticket**: `ATT-9605` (`1 SP`, `Bug`, `tier-a`) | **Parent Epic**: `ATT-6033`  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Active Sprint**: `2026-11-23_03_Nacht` (Sprint 476)  
**Requirement**: `REQ-CON-058` (`REQ-CON-058.1`–`.2`) | **Test Spec**: `TST-CON-058` (`TST-CON-058.1`–`.3`)  
**Branch**: `feature/ATT-9605` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-23

---

## 1. SWE.2 Architectural Decomposition, Threading & Governance
* **Layering & Threading**: Guards execute synchronously on the ANT+ PCC callback thread inside `CalculatedSpeedReceiver.onNewCalculatedSpeed` in `ANTBikeSpeedDevice` (`:110-118`) and `ANTBikeSpeedAndCadenceDevice` (`:145-152`). Preserves eager sensor creation (`addSensors()`), `ThresholdSensor(..., SPEED_THRESHOLD = 8)` smoothing, direct `m/s` scaling (`AntPlusBikeSpeedDistancePcc.CalculatedSpeedReceiver`), and `onNewCalculatedAccumulatedDistance` wheel-rev forwarding.
* **SWE.2 / SWE.3 & UI Consistency**:
  * `docs/architecture.md` (`Rule 8.8`): **None** — single-callback guard inside two existing ANT+ devices; zero new subsystems, repositories, DB managers, or prefix families.
  * `docs/architecture/detailed_design/` (`REQ-PRO-114`): **None** — zero `TrackerService`, `BANALService`/`DeviceManager`, or `MyBTLEDevice` state-machine or concurrency changes.
  * `UI Consistency` (`docs/design_guidelines.md`): **N/A** — zero UI/Compose or `values*/strings*.xml` edits (`Localization Parity: N/A - 0 string changes`).

---

## 2. Atomic Construction Steps (Stage 4 — Post-Gate 3 Only)
| Step | Action & Target Files | Mapped Req / Test | Verification / Guardrail |
| :--- | :--- | :--- | :--- |
| **1** | **Fix `ANTBikeSpeedDevice.onNewCalculatedSpeed` (`app/src/main/java/com/atrainingtracker/banalservice/devices/ant_plus/ANTBikeSpeedDevice.java:110-118`)**: Insert `if (calculatedSpeed == null) { return; }` at callback entry *before* `myLog(...)` (`:113`). Extract `double speed = calculatedSpeed.doubleValue();`, pass `speed` to `myLog(...)` and `mSpeedSensor.newValue(speed)`, and replace `:116` with `mPaceSensor.newValue(speed > 0.0 ? 1.0 / speed : null);` (removing the stale inline comment). | `REQ-CON-058.1`, `.2` | `ANTBikeSpeedDevice.java <= 1,500` LoC (`145 -> ~149`); zero NPE on `null`; zero `Double.POSITIVE_INFINITY` emitted; direct `m/s` preserved. |
| **2** | **Fix `ANTBikeSpeedAndCadenceDevice.onNewCalculatedSpeed` (`app/src/main/java/com/atrainingtracker/banalservice/devices/ant_plus/ANTBikeSpeedAndCadenceDevice.java:145-152`)**: Insert `if (calculatedSpeed == null) { return; }` at callback entry. Extract `double speed = calculatedSpeed.doubleValue();`, publish `mSpeedSensor.newValue(speed);`, and replace `:150` with `mPaceSensor.newValue(speed > 0.0 ? 1.0 / speed : null);` (removing the stale inline comment). Keep `releaseAccess()`, `onNewCalculatedAccumulatedDistance`, and `onNewCalculatedCadence` untouched. | `REQ-CON-058.1`, `.2` | `ANTBikeSpeedAndCadenceDevice.java <= 1,500` LoC (`250 -> ~254`); zero NPE on `null`; zero `Double.POSITIVE_INFINITY` emitted; `REQ-CON-025.1` preserved. |
| **3** | **Unit Tests & Tier-1 Verification (`ANTBikeSpeedDeviceTest.kt` [new], `ANTBikeSpeedAndCadenceDeviceTest.kt` [extended], `Rule 7.5`)**: Create `ANTBikeSpeedDeviceTest.kt` (`TestANTBikeSpeedDevice` subclass exposing `subscribeSpecificEvents()`, `bikeSpeedDistancePcc`, `mSpeedSensor`, `mPaceSensor`) and extend `ANTBikeSpeedAndCadenceDeviceTest.kt` with `CalculatedSpeedReceiver` capture. Annotate tests with `// Verifies REQ-CON-058 / TST-CON-058` covering: (a) `null` callback (initial `null` stays `null`, post-motion value preserved, `0` NPE); (b) initial `BigDecimal.ZERO` -> `speed == 0.0`, `pace == null`; (c) positive `BigDecimal("10.0")` -> `speed == 10.0`, `pace == 0.1`; (d) `8` (`SPEED_THRESHOLD`) consecutive `BigDecimal.ZERO` ticks after motion hold `0.1` on ticks `1..7` and decay cleanly to `null` on tick `8`; (e) negative speed -> `pace == null`. Run incremental `--rerun` Gradle tests (`./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.banalservice.devices.ant_plus.ANTBikeSpeedDeviceTest" --tests "com.atrainingtracker.banalservice.devices.ant_plus.ANTBikeSpeedAndCadenceDeviceTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"`) + `tools/auto_gate.py --ticket ATT-9605 --tier tier-a --write-bundle`. | `TST-CON-058.1`, `.2`, `.3` | `100%` targeted unit & contract pass rate (`0` failed, `0` skipped); `0` linter/governance errors; SHA-256 evidence bundle written (`ATT-9544`). |

---

## 3. System Invariants & Rollback Safety
* **Invariants**: `ANTBikeSpeedDevice.java <= 1,500` LoC; `ANTBikeSpeedAndCadenceDevice.java <= 1,500` LoC; direct `m/s` scaling preserved (no `/ 3.6`); `ThresholdSensor(..., SPEED_THRESHOLD = 8)` smoothing preserved; `ANTBikeSpeedAndCadenceDevice.releaseAccess()` (`REQ-CON-025.1`) untouched; `0` `strings*.xml` edits.
* **Code Lock (`Rule 4.1`) & Rollback**: Zero `app/src/main/...` edits until `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` returns exit code `0` (`GATE_PASSED`). Work isolated cleanly on `feature/ATT-9605`.
