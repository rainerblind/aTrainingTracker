# Stage 3: Implementation Plan - ATT-6176: `ANTBikePowerDevice.onNewCalculatedWheelSpeed` Null Guard & Standstill Pace

**Ticket**: `ATT-6176` (`1 SP`, `Bug`) | **Parent Epic**: `ATT-6033`  
**Target Release**: `V5.0.0` (`Rule 8.3`) | **Active Sprint**: `2026-11-21_02_Spät` (Sprint 469)  
**Requirement**: `REQ-STB-026` (`REQ-STB-026.1`–`.2`) | **Test Spec**: `TST-STB-026` (`TST-STB-026.1`–`.3`)  
**Branch**: `feature/ATT-6176` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-21

---

## 1. SWE.2 Architectural Decomposition, Threading & Governance
* **Layering & Threading**: Guard executes synchronously on the ANT+ PCC callback thread inside `ANTBikePowerDevice.subscribeSpecificEvents` (`CalculatedWheelSpeedReceiver.onNewCalculatedWheelSpeed`). Preserves lazy sensor creation (`createWheelSpeedSensors()`), `DevicesDatabaseManager` power-flag persistence, and `ThresholdSensor(..., SPEED_THRESHOLD = 8)` smoothing.
* **SWE.2 / SWE.3 & UI Consistency**:
  * `docs/architecture.md` (`Rule 8.8`): **None** — single-callback guard inside existing `ANTBikePowerDevice`; zero new subsystems, repositories, DB managers, or prefix families.
  * `docs/architecture/detailed_design/` (`REQ-PRO-114`): **None** — zero `TrackerService`, `BANALService`, or `MyBTLEDevice` state-machine changes.
  * `UI Consistency` (`docs/design_guidelines.md`): **N/A** — zero UI/Compose or `values*/strings*.xml` edits (`Localization Parity: N/A`).

---

## 2. Atomic Construction Steps (Stage 4 — Post-Gate 3 Only)
| Step | Action & Target Files | Mapped Req / Test | Verification / Guardrail |
|---|---|---|---|
| **1** | **Null Guard & Standstill Pace Fix (`ANTBikePowerDevice.java:297-310`)**: At the very top of `onNewCalculatedWheelSpeed`, add `if (calculatedWheelSpeed == null) { return; }` *before* `if (mSpeedSensor == null) createWheelSpeedSensors()` (`:300-302`) and *before* `double speed_mps = calculatedWheelSpeed.doubleValue() / 3.6;` (`:304`, confirmed km/h -> m/s conversion). Publish `mSpeedSensor.newValue(speed_mps)`, and replace `:307-309` with `mPaceSensor.newValue(speed_mps > 0.0 ? 1.0 / speed_mps : null);` so standstill (`speed_mps <= 0.0`) passes `null` (never `Double.POSITIVE_INFINITY`) while `ThresholdSensor` (`SPEED_THRESHOLD = 8`) preserves initial `null` and 8-tick decay to `null`. | `REQ-STB-026.1`, `.2` | `ANTBikePowerDevice.java` stays `<= 1,500` LoC (`395 -> ~396`); zero `DevicesDatabaseManager` flag mutations on `null`; zero `Double.POSITIVE_INFINITY` emitted. |
| **2** | **Unit Tests & Tier-1 Verification (`ANTBikePowerDeviceTest.kt`, `Rule 7.5`)**: Extend `TestANTBikePowerDevice` with `getSpeedSensor()`/`getPaceSensor()` and capture `subscribeCalculatedWheelSpeedEvent` (`CalculatedWheelSpeedReceiver`) in `@Before`. Add unit tests annotated with `// Verifies REQ-STB-026 / TST-STB-026`: (a) `null` callback throws `0` NPE, leaves `mSpeedSensor == null` & `mPaceSensor == null`, `verify(exactly = 0) { mockDevicesDb.putBikePowerSensorFlags(any(), any()) }`; (b) initial `BigDecimal.ZERO` -> `speed == 0.0` & `pace == null` (never `Infinity`); (c) `BigDecimal.valueOf(36.0)` -> `speed == 10.0` & `pace == 0.1`; (d) `8` (`SPEED_THRESHOLD`) consecutive `BigDecimal.ZERO` ticks after motion -> `speed == 0.0` & `pace == null`. Run incremental `--rerun` Gradle tests (`-Dkotlin.daemon.jvm.options=-Xmx3072m`, NEVER `--rerun-tasks`): `./gradlew :app:testDebugUnitTest --rerun -Dkotlin.daemon.jvm.options=-Xmx3072m --tests "com.atrainingtracker.banalservice.devices.ant_plus.ANTBikePowerDeviceTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"`, plus Python linters & `verify_requirement_governance.py`. | `TST-STB-026.1`, `.2`, `.3` | `100%` targeted unit & contract pass rate (`0` failed, `0` skipped); `ANTBikePowerDevice.java <= 1,500` LoC; `0` linter/governance errors. |

---

## 3. System Invariants & Rollback Safety
* **Invariants**: `ANTBikePowerDevice.java <= 1,500` LoC; km/h -> m/s (`/ 3.6`) conversion preserved; `ThresholdSensor(..., SPEED_THRESHOLD = 8)` smoothing preserved; existing `onNewPedalPowerBalance` & `onNewPedalSmoothness` callbacks untouched; `0` `strings*.xml` edits.
* **Code Lock (`Rule 4.1`) & Rollback**: Zero `app/` edits until `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` returns `0` (`GATE_PASSED`). Isolated on `feature/ATT-6176`.
