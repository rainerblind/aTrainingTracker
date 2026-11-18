# Stage 5: Walkthrough & Verification - ATT-8717: Hardware-Grounded Polar Detection & Canonical ID

**Ticket**: `ATT-8717` (`3 SP`, Parent `ATT-5910`) | **Target Release**: `V5.0.0` | **Sprint**: `2026-11-17_03_Nacht`  
**Requirement Mapping**: `REQ-CON-049` (`REQ-CON-049.1`..`.5`) | **Test Mapping**: `TST-CON-049` (`TST-CON-049.1`..`.4`)  
**Branch**: `feature/ATT-8717` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18

---

## 1. Executive Summary & Verification Overview
* Replaced brittle display-name heuristics (`isPolarH10Name`, `isPolarVeritySenseName`, `POLAR_ID_REGEX`) with hardware-grounded BLE Service UUID (`UUID_SERVICE_POLAR_PMD`, `UUID_SERVICE_POLAR_PFTP`), DIS `0x180A` (`0x2A24` Model Number, `0x2A25` Serial Number), and Polar SDK feature/info persistence in `PolarOfflineCapabilityStore` (`PolarDeviceType`) without bumping `Devices.db` (`DB_VERSION = 14`).
* **Stage 6 Bounce 1/3 Fixes (`9ae841a5`)**: Gated DIS `0x2A24`/`0x2A25` reads & persistence strictly on `hasPmd || hasPftp`, wired `hasPolarServiceCapability()` across pairing, reconnect, coordinator, and SDK bridge, enqueued DIS reads **before** HR CCCD notification setup in `BTLEHeartRateDevice.configureServices()`, tightened `classifyModelNumber()` to reject bare `"sense"` (e.g., `"CardioSense"`), validated canonical 8-hex `polarDeviceId` (`^[0-9A-Fa-f]{8}$`) preserving `PolarDeviceInfo.deviceId` against factory serials, and guarded `startAccCollection()`/`startGyroCollection()` with `CancellationException` rethrow + `Throwable` catch.
* **UI / Physical Hardware Scope**: `N/A - no UI strings` (`values*/strings*.xml` and UI layouts unchanged). Physical Polar H10 / Verity Sense hardware verification on Pixel 10 is deferred to PO system test per `Rule 7.6` (`REQ-TST-004`).

---

## 2. Requirement & Test Verification Matrix
| Requirement | Test Spec | Verification Method | Result (Offline Mocked) | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-049.1`, `.2` | `TST-CON-049.1` | Offline Unit/Robolectric (`PolarOfflineCapabilityStoreTest` `4/4`, `PolarOfflineGattCoordinatorCoexistenceTest` `5/5`) | **PASSED** (`9/9`) | `Verified` |
| `REQ-CON-049.3` | `TST-CON-049.2` | Mocked GATT Unit (`BTSearchForNewDevicesEngineTest` `19/19`, `BTLEHeartRateDeviceTest` `12/12`) | **PASSED** (`31/31`) | `Verified` |
| `REQ-CON-049.3`, `.4` | `TST-CON-049.3` | Mocked SDK Unit (`PolarOnlineStreamingBridgeTest` `17/17`) | **PASSED** (`17/17`) | `Verified` |
| `REQ-CON-049.4`, `.5` | `TST-CON-049.4` | Offline Unit/Robolectric (`PolarH10OfflineControllerTest` `17/17`, `PolarVeritySenseOfflineControllerTest` `25/25`, `EditDevicePolarBackupTest` `4/4`, `EditDevicePolarLiveImuTest` `4/4`, `0` `src/main` reads) | **PASSED** (`50/50`) | `Verified` |

---

## 3. Automated Test Evidence (Tier 1 Targeted — Offline Mocked, Stage 4 SHA `9ae841a5`)
* **Command** (`python3 tools/parallel_sprint_lock.py -- ./gradlew :app:assembleDebug :app:testDebugUnitTest --rerun-tasks --tests "*Polar*" --tests "*BTLEHeartRateDevice*" --tests "*DeviceManager*" --tests "*BTSearchForNewDevices*" --tests "*EditDevicePolar*" --tests "*DevicesDatabaseManagerMigrationV14Test*" --tests "*DetailedDesignParityContractTest*" --tests "*InterfaceSignatureDriftContractTest*" --max-workers=2`):
  * `:app:assembleDebug` + `:app:testDebugUnitTest --rerun-tasks`: **BUILD SUCCESSFUL** — **34 suites, 166 tests, 0 failures, 0 errors, 0 skipped** (reused directly with zero code drift since Gate 4 Bounce 1 `ATT-8821` / `ATT-8030`).
  * Adjacent regression suites included in run: `DeviceManager*` (`19/19`), `DevicesDatabaseManagerMigrationV14Test` (`3/3`), `DetailedDesignParityContractTest` (`3/3`), `InterfaceSignatureDriftContractTest` (`3/3`), `PolarRawTelemetry*` / estimators (`21/21`).

---

## 4. Hardware / Physical Verification (Pixel 10)
* No UI changes (`N/A - no UI strings`). Physical BLE GATT/DIS verification against real Polar H10 and Verity Sense hardware is deferred to PO system test per `Rule 7.6` (`REQ-TST-004`).

---

## 5. Invariant & Governance Verification
* `Devices.db` remains at `DB_VERSION = 14`; `MyBTLEDevice.java` state machine/queue untouched; `REQ-PRO-049.1` (`0` `app/src/main` source reads in new/modified tests) verified.
* `python3 tools/verify_requirement_governance.py --sync-indexes`, `--text-file docs/engineering/walkthroughs/ATT-8717_walkthrough.md`, `--check-doc-headers --check-commit-hygiene --check-conflict-markers`, and full `python3 tools/verify_requirement_governance.py`: **PASSED** (exit `0`).
