# Stage 5 Walkthrough: ATT-8731 - User-Facing Bluetooth Bonding Hint & Pair Action on V2-Capable Radar V1 Fallback

**Ticket**: [ATT-8731](https://issue-tracker/ATT-8731) | **Parent Epic**: [ATT-1286](https://issue-tracker/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-24_02_Spät` | **Branch**: `feature/ATT-8731` (rebased onto `27436048` / `Devices.db` v15)  
**Requirement Mapping**: `REQ-CON-061` (`[Verified]`) | **Test Mapping**: `TST-CON-061` (`[Verified]`)  
**Evidence Bundle**: [`docs/engineering/test_evidence/tickets/ATT-8731/manifest.json`](../test_evidence/tickets/ATT-8731/manifest.json) | **Date**: 2026-11-24

---

## 1. Summary of Implemented Changes (incl. Stage 6 Bounce 1/5 Heals)
* **SSoT Domain State (`RadarState.kt` `148`, `RadarStateRepository.kt` `398/1,500`)**: Added `RadarV2BondingStatus` and trailing `@JvmOverloads` defaults `isV2BondingRequired: Boolean = false`, `v2BondingDeviceAddress: String? = null` on `RadarThreatState` and `RadarState`. `RadarStateRepository` (under leaf `threatLock`) enforces `sourceStates[BLE]?.connectionState == CONNECTED` before accepting `setV2BondingRequired(true, ...)` (Bounce 1 fix), preserves BLE bonding status across 4–8 Hz `CONNECTED` updates without resurrecting cleared state, auto-clears + resets the one-shot prompt latch on `DISCONNECTED`/`DISCONNECTED_ERROR`/`CONNECTING`, and exposes `.distinctUntilChanged()` `observeV2BondingStatus()`.
* **BLE Driver (`BTLERadarDevice.java`, `1,480/1,500` LoC)**: Sets bonding-required strictly when `v2Char != null && !isBonded && !isV1Override`, lazily registers `ACTION_BOND_STATE_CHANGED` (`RECEIVER_EXPORTED`, `SDK_INT == 0` guarded) under `mBondReceiverLock`, guards activation & post-check against concurrent remote disconnects and `shutDown()` via `mBondSessionActive` / `mBondDeviceShutDown` (Bounce 1 fix), and clears state + unregisters receiver via `LifecycleUtils.safeUnregisterReceiver` on `configureServices()` entry, trimmed case-insensitive `BOND_BONDED`, V2 packet receipt, `unregisterSensors()`, `resetConnectionState()`, `shutDown()`, and 3.5 s watchdog expiry.
* **Permission-Safe Pairing (`RadarBluetoothBondingHelper.kt`, `158` LoC)**: Verifies API 31+ `BLUETOOTH_CONNECT`, uses API 33+ `getRemoteLeDevice(mac, ADDRESS_TYPE_RANDOM|PUBLIC)`, clears repo on `ALREADY_BONDED`, short-circuits `BOND_BONDING -> BONDING_INITIATED` without calling `createBond()` or opening Settings (Bounce 1 fix), and falls back safely to `Settings.ACTION_BLUETOOTH_SETTINGS` adding `FLAG_ACTIVITY_NEW_TASK` strictly when `context !is Activity`.
* **Compose UI & 9-Locale Strings (`RadarV2BondingHintCard.kt` `228`, `RadarV2BondingSnackbarEffect.kt` `93`, `EditDeviceDialog.kt` `1,853/2,004`, `DevicesTabbedScreen.kt` `815/1,500`, `TrackingTabsScreen.kt` `1,461/1,500`, 9× `values*/strings_radar.xml`)**: MAC-matched inline M3 warning `OutlinedCard(12.dp)` using WCAG AAA `LightWarning*`/`DarkWarning*` tokens + single-line Pair CTA (`maxLines = 1`) and `READY`-only one-shot Snackbar tagged with private `RadarV2BondingSnackbarVisuals` so `!isAllowedToShow` dismisses strictly its own snackbar without disturbing unrelated battery/PMD alerts (Bounce 1 fix).

## 2. Acceptance Criteria & Verification Matrix (`261/261 PASS` Across 24 Tier-1 Targeted Suites)

| AC / Req | Scope Verified | Evidence | Verdict |
| :--- | :--- | :--- | :---: |
| AC-1 / `REQ-CON-061.1` | Strict unbonded V2 detection, legacy V1 & override silence, zero 4–8 Hz resurrection, `BLE == CONNECTED` guard, concurrent `unregisterSensors()`/`shutDown()` race safety, `BOND_BONDED`/V2/watchdog teardown, BLE>ANT+ arbitration | `BTLERadarDeviceTest` (`68/68`), `RadarStateRepositoryTest` (`10/10`) | **PASS** |
| AC-2 / `REQ-CON-061.2` | API 33+ Random Static/Public `getRemoteLeDevice`, API <33 `getRemoteDevice`, `ALREADY_BONDED` repo clear, `BOND_BONDING` short-circuit, permission/`SecurityException`/`false` fallback with non-`Activity` `FLAG_ACTIVITY_NEW_TASK` | `RadarBluetoothBondingHelperTest` (`4/4`) | **PASS** |
| AC-2/3 / `REQ-CON-061.3` | Trimmed case-insensitive MAC gating, Light/Dark/DE-360dp `RadarV2BondingHintCard`, `TRACKING`/`PAUSED` suppression, owned-`RadarV2BondingSnackbarVisuals` dismissal preserving unrelated snackbars | `EditDeviceDialogRadarBondingTest` (`5/5` `createComposeRule()`), `EditDeviceDialogRadarLightTest` (`5/5`), + `15` adjacent radar/editdevice suites (`135/135`) | **PASS** |
| AC-3 / `REQ-CON-061.4` | 9/9 `strings_radar.xml` parity (`6` keys, 0 emojis), color centralization, LoC & SWE.3/ABI contracts, `0` new production Kotlin warnings | `TranslationParityTest` (`11/11`), `ThemeColorCentralizationAuditTest` (`5/5`), `ArchitectureConformanceContractTest` (`11/11`), `DetailedDesignParityContractTest` (`3/3`), `InterfaceSignatureDriftContractTest` (`3/3`) | **PASS** |

* **Explicit Test Scope**: Tier 1 targeted verification executed via `./gradlew :app:testDebugUnitTest --rerun` across `24` suites (`261` tests, `0` failed, `0` errors, `0` skipped); fresh JUnit XMLs archived in `docs/engineering/test_evidence/tickets/ATT-8731/junit/`.

## 3. Visual & Emulator Verification (`Rule 8.5` & `Rule 7.6`)
* **Light & Dark Compose Card Composite**: [`ATT-8731_radar_v2_bonding_card_light_dark.png`](img/ATT-8731_radar_v2_bonding_card_light_dark.png) — inspected via `read_file`: crisp `12.dp` `OutlinedCard` geometry, WCAG AAA warm amber Light/Dark tokens, single-line `"Pair via Bluetooth"` CTA + clear initiated feedback (DE-360dp verified single-line `<= 56.dp`), ergonomic full-width touch target.
* **Live `emulator-5554` Smoke (`agent_emulator.py exec --ensure`, `Devices.db` v15)**: [`ATT-8731_emulator_smoke.png`](img/ATT-8731_emulator_smoke.png) — `installDebug` + cold launch of `com.atrainingtracker.debug` into `TrackingTabsScreen` (`READY`), inspected via `read_file`, `adb logcat -b crash -d` completely empty (`0` crashes, zero spurious snackbars).

## 4. Branch & Governance Status
* `python3 tools/verify_requirement_governance.py` passed (`exit 0`); `feature/ATT-8731` preserved intact (NOT merged onto `sprint/2026-11-24_02_Spät`) for independent Stage 6 Cold Review re-audit.
