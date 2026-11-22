# Stage 5: Walkthrough & Verification - ATT-8949: Polar Detection Non-Polar Bleed, Renamed Existing H10 & Empty-Name Fix

**Ticket**: `ATT-8949` (`3 SP`, `tier-a`, Parent `ATT-5910`) | **Target Release**: `V5.0.0` | **Sprint**: `2026-11-21_03_Nacht`  
**Requirement Mapping**: `REQ-CON-049` (`REQ-CON-049.1`–`.5`) | **Test Mapping**: `TST-CON-049` (`TST-CON-049.1`–`.4`)  
**Branch**: `feature/ATT-8949` | **Evidence Bundle**: [`docs/engineering/test_evidence/tickets/ATT-8949/`](../test_evidence/tickets/ATT-8949/) | **Date**: 2026-11-22

---

## 1. Executive Summary & Delivered Fixes (Incl. Stage 6 Bounce 1/5 Heal)
1. **Non-Polar Name-Heuristic Bleed Eliminated (`REQ-CON-049.1`, `.4`)**: `BTLEHeartRateDevice.configureServices()` and `BTSearchForNewDevicesEngine` persist `recordGattServiceCapabilities(hasPmd, hasPftp)` unconditionally on HRM `0x180D` discovery—including `(false, false)` for non-Polar HRMs. `PolarOfflineGattCoordinator` short-circuits `isConfirmedNonPolarGatt -> false` before any name fallback, rejects explicit non-Polar `manufacturer`, and `EditDeviceDialog` gates Polar controls on `Protocol.BLUETOOTH_LE` and immutable `EditDeviceViewModel.persistedDeviceIdentity`.
2. **Pre-Rename Legacy Polar Backfill & Crash-Safe Load (`REQ-CON-049.3`)**: `DeviceDataRepository` backfills a hardware-overridable legacy Polar type + 8-hex ID for pre-`ATT-8717` paired Polar H10, Verity Sense, and `offline_rec_`-only Verity Sense (`"Armband"`) BLE HRMs on load (wrapped per device in `runCatching` so a backfill error never aborts `loadAllDevices()`) and prior to rename in `updateDevice()`, while suppressing legacy name fallback when renaming an un-reconnected non-Polar HRM. Real GATT/DIS/SDK truth always supersedes legacy seeds/suppression.
3. **Empty-Name Verity Sense & Bare `"Sense"` DIS Persistence (`REQ-CON-049.2`, `.4`)**: Removed `&& name.isNotEmpty()` from `isPolarVeritySenseDevice()`; classified exact `"Sense"` DIS `0x2A24` as `VERITY_SENSE` strictly when Polar PMD/PFTP is confirmed (persisting `KEY_TYPE_PREFIX` cleanly across process restarts while rejecting unconfirmed `"Sense"` and `"CardioSense"`); unified `PolarOnlineStreamingBridge.sdkCallback.disInformationReceived` on `matchesBridgeIdentifier()` (`-8` LoC -> `1,477/1,500` LoC); updated `DeviceManagementUiTest` to stub `persistedDeviceIdentity`.

---

## 2. Requirement & Test Verification Matrix
| Requirement | Test Spec | Verification Scope (`auto_gate.py` `d7d42580` + Targeted Suites) | Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-049.1`, `.2` | `TST-CON-049.1` | `PolarOfflineCapabilityStoreTest` (`7/7`, incl. restart `KEY_TYPE_PREFIX`), `PolarOfflineGattCoordinatorCoexistenceTest` (`10/10`) | **PASS** (`17/17`) | `Verified` |
| `REQ-CON-049.1`, `.3` | `TST-CON-049.2` | `BTLEHeartRateDeviceTest` (`18/18`), `BTSearchForNewDevicesEngineTest` (`21/21`) | **PASS** (`39/39`) | `Verified` |
| `REQ-CON-049.2`, `.3` | `TST-CON-049.3` | `PolarOnlineStreamingBridgeTest` (`57/57`), `PolarPmdStreamCoordinatorTest` (`11/11`), `DeviceDataRepository*` (`16/16`) | **PASS** (`84/84`) | `Verified` |
| `REQ-CON-049.3`–`.5` | `TST-CON-049.4` | `PolarH10OfflineControllerTest` (`21/21`), `PolarVeritySenseOfflineControllerTest` (`29/29`), `EditDevicePolarBackupTest` (`5/5`), `EditDevicePolarLiveImuTest` (`10/10`, behavioral `createComposeRule()` UI tests), `DeviceManagementUiTest` (`2/2`) | **PASS** (`67/67`) | `Verified` |

---

## 3. Machine-Generated Evidence Bundle (`ATT-9544`, `auto_gate.py` WITHOUT `--skip-tests`)
* **Command & Verdict**: `python3 tools/auto_gate.py --ticket ATT-8949 --tier tier-a --base-ref sprint/2026-11-21_03_Nacht --write-bundle ...` -> **`[auto-gate:ATT-8949:d7d42580:PASS]`** (`commit_sha`: `d7d425805284`, UTC `2026-11-22T03:18:10Z`).
* **Bundled Verification**: **566/566 Android unit tests PASS across 68 JUnit XML suites** (`0` fail, `0` err, `0` skip) + 2 clean screenshots = **71 SHA-256 hashed files** in [`docs/engineering/test_evidence/tickets/ATT-8949/manifest.json`](../test_evidence/tickets/ATT-8949/manifest.json) and [`evidence_summary.json`](../test_evidence/tickets/ATT-8949/evidence_summary.json).
* **Prior Gate 4 Bounce 1 (`ATT-9593`)**: `43/43 PASS` (`--as agent2`) + Stage 4 Bounce 1 `198/198 PASS` across 15 suites (`0` new warnings).

---

## 4. Visual Verification (`Rule 8.5`), Emulator Launch Smoke & Hardware Scope (`Rule 7.6`)
* **Light/Dark Compose UI Visual Audit (`Rule 8.5`)**: Inspected [`ATT-8949_edit_device_polar_detection.png`](img/ATT-8949_edit_device_polar_detection.png) via `read_file` verifying side-by-side Light & Dark rendering of all 3 target states: (a) renamed H10 cards, (b) empty-name Verity Sense cards, and (c) negative non-Polar HRM renamed `"Polar H10"` (`0` Polar cards rendered below header; `N/A - no UI strings`).
* **Locked Emulator Launch Smoke (`emulator-5554`, `Pixel_8_Android_15`, `Rule 9.3`)**: Installed `com.atrainingtracker.debug` via `agent_emulator.py exec --ensure`, launched `MainActivityWithNavigation` cleanly (`0` crashes in `logcat -b crash -d`), and visually verified [`att8949_emu_smoke_clean.png`](../test_evidence/tickets/ATT-8949/screenshots/att8949_emu_smoke_clean.png) via `read_file` (unobstructed Start screen, zero stray modal dialogs; `EditDeviceDialog` Polar interactive states are verified by Robolectric Compose `createComposeRule()` since emulator lacks physical BLE Polar sensors).
* **Physical Hardware**: Real Polar H10 / Verity Sense BLE GATT verification on Pixel 10 is deferred to PO system test per `Rule 7.6` (`REQ-TST-004`).

---

## 5. Invariants & Governance Verification
* `DevicesDatabaseManager.java` (`DB_VERSION = 14`), `MyBTLEDevice.java`, `DeviceManager.java`, `TrackerService.java`, and `values*/strings*.xml` remain **untouched** (`PolarOnlineStreamingBridge.kt` = `1,477/1,500` LoC; `EditDeviceDialog.kt` = `1,782/2,004` LoC; `0` `app/src/main` source reads in tests).
* Safety linters (`lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`), `auto_gate.py --write-bundle`, and `verify_requirement_governance.py --sync-indexes` / `--base-ref sprint/2026-11-21_03_Nacht`: **ALL PASS (exit `0`)**.
