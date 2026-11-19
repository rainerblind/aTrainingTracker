# Stage 5: Walkthrough & Verification - ATT-8998: Non-Blocking BLE Search-Queue & Background Reconnect

**DOC-ID**: `DOC-WLK-ATT-8998` | **Ticket**: `ATT-8998` (`2 SP`, Parent `ATT-5910`) | **Target Release**: `V5.0.0`  
**Sprint**: `2026-11-18_03_Nacht` | **Requirement Mapping**: `REQ-CON-051` (`.1`..`.3`) | **Test Mapping**: `TST-CON-051` (`.1`..`.3`)  
**Branch**: `feature/ATT-8998` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-19

---

## 1. Executive Summary (Incl. Stage 6 Bounce 1/3 Heal)
* **Non-Blocking Search-Queue Timeout Handoff (`REQ-CON-051.1`)**: Updated `MyBTLEDevice.mSearchTimeoutRunnable` (`MyBTLEDevice.java:470-523`) so when `mState != CONNECTED_TO_GATT && mState != CONNECTED_WITH_SERVICE` under `isMidSessionOrWorkoutActive()`, it transitions `mState` from `State.SEARCHING` to `State.DISCONNECTED`, sets `mIsBackgroundReconnecting = true`, schedules `mReconnectRunnable`, maintains `triggerGattConnect(dev, true)`, and invokes `notifyStopSearching(false)` whenever `isSearching()` is `true`—releasing `DeviceManager.cMyRemoteDeviceCurrentlySearchingFor` immediately. Guarded `mSearchTimeoutRunnable` under `mGattLock` against `CONNECTED_TO_GATT` / `CONNECTED_WITH_SERVICE` so Binder-thread connections racing timeout before `discoverServices()` / `notifyStopSearching(true)` abort cleanly.
* **Decoupled Background Reconnect & `tries <= 1` Exemption (`REQ-CON-051.2`)**: Extracted `MyBTLEDevice.attemptBackgroundReconnect()` (called by `mReconnectRunnable` and `resumeGattOperations()` when `mIsBackgroundReconnecting == true`) so `1s..16s` backoff and `autoConnect` (`isMidSessionOrWorkoutActive()`) run without `State.SEARCHING` or `MyRemoteDevice.mSearching = true`. Added `MyRemoteDevice.shouldKeepAliveOnSearchTimeout()` (overridden in `MyBTLEDevice`) so `DeviceManager.mSearchingStoppedForOneReceiver` preserves reconnecting devices on `tries <= 1` while shutting down `TrackingMode.READY` offline sensors.
* **Monolith Ceiling, SWE.3 Parity & Localization (`REQ-CON-051.3`)**: `DeviceManager.java` = **`1,707 <= 1,708` LoC**; `MyBTLEDevice.java` = `1,273` LoC; `MyRemoteDevice.java` = `159` LoC; `banal_service.md` (`v1.0.4`) & `my_btle_device.md` (`v1.0.4`) synchronized; `Localization Parity: N/A - no UI strings`.

## 2. Requirement & Test Verification Matrix
| Requirement | Test Spec | Verification Method (Tier 1 `--rerun-tasks` / Mocked HAL) | Result | Living Doc Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-051.1` | `TST-CON-051.1` | `BLEReconnectRobustnessTest` (`11/11`, incl. `CONNECTED_TO_GATT` race), `DeviceManagerSearchQueueTest` (`4/4`) | **PASSED** (`15/15`) | `Verified` |
| `REQ-CON-051.2` | `TST-CON-051.2` | `BLEReconnectRobustnessTest` (`11/11`), `DeviceManagerSearchQueueTest` (`4/4`) | **PASSED** (`15/15`) | `Verified` |
| `REQ-CON-051.3` | `TST-CON-051.3` | `MyBTLEDeviceAddressAndSearchTest` (`11/11`), `MyBTLEDeviceLifecycleTest` (`11/11`), `ArchitectureConformanceContractTest` (`11/11`), `DetailedDesignParityContractTest` (`3/3`), `SourceTextReadingAuditContractTest` (`4/4`) | **PASSED** (`40/40`) | `Verified` |

## 3. Automated Verification & Governance Evidence
* **Tier 1 `--rerun-tasks` Unit & Contract Suite** (`parallel_sprint_lock.py --timeout 1800 ./gradlew :app:testDebugUnitTest --rerun-tasks --console=plain --tests "*BLEReconnectRobustnessTest" --tests "*DeviceManagerSearchQueueTest" --tests "*MyBTLEDevice*" --tests "*BANALService*" --tests "*ArchitectureConformanceContractTest" --tests "*DetailedDesignParityContractTest" --tests "*InterfaceSignatureDriftContractTest" --tests "*SystemArchitectureDesignContractTest" --tests "*SourceTextReadingAuditContractTest" --tests "*UiReflectionOnlyTestAuditContractTest" --tests "*TranslationParityTest"`):
  * `BUILD SUCCESSFUL in 7m 30s` (**19 suites, 125 tests, 0 failures, 0 errors, 0 skipped**):
    * `DeviceManagerSearchQueueTest` (`4/4`), `BLEReconnectRobustnessTest` (`11/11`), `MyBTLEDeviceAddressAndSearchTest` (`11/11`), `MyBTLEDeviceLifecycleTest` (`11/11`), `MyBTLEDeviceQueueTest` (`8/8`).
    * `BANALServiceArbitrationCharacterisationTest` (`5/5`), `BANALServiceLifecycleTest` (`3/3`), `BANALServiceRepository*Test` (`28/28` across 5 suites).
    * Contract & Parity: `ArchitectureConformanceContractTest` (`11/11`), `DetailedDesignParityContractTest` (`3/3`), `InterfaceSignatureDriftContractTest` (`3/3`), `SystemArchitectureDesignContractTest` (`9/9`), `SourceTextReadingAuditContractTest` (`4/4`), `UiReflectionOnlyTestAuditContractTest` (`4/4`), `TranslationParityTest` (`10/10`).
* **Static Baselines, Safety Linters & Governance**:
  * `check_static_analysis_baselines.py --base-ref sprint/2026-11-18_03_Nacht`: `PASS` (`detekt` `9509/9516`, `lint` `238/238`).
  * `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`, `check_secrets_hygiene.py`: all `PASS` (`8733` tracked files clean).
  * `verify_requirement_governance.py --sync-indexes` & `--base-ref sprint/2026-11-18_03_Nacht`: `exit 0` (`Total=1258, Verified=1016`; `Total=1265, Verified=1051`).

## 4. Emulator (`emulator-5554`) & Hardware Boundary (`Rule 7.6`)
* **Emulator Deployment (`emulator-5554`, `Pixel_8_Android_15(AVD) - 15`)**: `ANDROID_SERIAL=emulator-5554 ... ./gradlew :app:installDebug` succeeded (`16s`); launched `com.atrainingtracker.debug/...MainActivityWithNavigation`, dispatched `REQUEST_START_SEARCH_FOR_PAIRED_DEVICES` (`result=0`), observed `BANALService: isSearching(): returning false` and `BANALServiceRepository` binding in `logcat`, and verified `adb -s emulator-5554 logcat -b crash -d` empty (`0` crashes).
* **Honest Scope Boundary**: `emulator-5554` lacks physical BLE peripheral radios/paired sensors; multi-sensor BLE timeout, `CONNECTED_TO_GATT` race immunity, and background reconnect are verified via mocked HAL unit tests (`TST-CON-051.1`–`.3`), while live Pixel 10 + BLE Radar/HRM verification remains PO-executed per `Rule 7.6`.
