# Stage 5 Walkthrough: ATT-8123 — `RadarLightQuickSwitchSheet` Required Callbacks & StVZO-Safe Deferred BLE GATT Registration

- **Ticket**: `ATT-8123` (`tier-a`, `2 SP`, `severity-major`, Target Release `V5.0.0`)
- **Branch**: `feature/ATT-8123` (Gate 3 `ATT-9606` **PASSED**)
- **Requirements / Tests**: `REQ-CON-055` (`SYSREQ-TRK-002`, `SYSREQ-TRK-007`) / `TST-CON-055` (`[Verified]`)
- **Localization Parity**: `N/A - 0 string changes`

## Summary of Changes
1. **Compile-Time Required Action Callbacks (`RadarLightQuickSwitchSheet.kt`, `REQ-CON-055.1`)**:
   - Removed all silent `= {}` no-op defaults from `onAutoSelected`, `onSnoozeSelected`, `onResumeAudio`, and `onDismiss` on both `RadarLightQuickSwitchSheet` and `RadarLightQuickSwitchSheetContent`, preserving explicit forwarding of all 5 action callbacks and updating the 3 `@Preview`s + test callers (`TrackingTabsScreen.kt:1144` already passed all 5 callbacks).
2. **StVZO-Safe Deferred BLE GATT Registration (`BTLERadarDevice.java`, `1,467/1,500` LoC, `REQ-CON-055.2`–`.4`)**:
   - Added atomic `mLightRegistrationLock` state machine (`mAwaitingModelNumberForRegistration`, `mDisconnectedSinceConfigure`, `mActiveRegistrationGatt`, `mIsStvzo`, `mHasHardwareBrakeSensor`, `mModelNumber`) and `completeLightControlRegistration(...)` helper; marked `mLightControlCharacteristic`, `mRadarControlWriteChar`, and `mRadarControlNotifyChar` `volatile` with local snapshot in `setLightMode()`.
   - Deferred `RadarLightController.registerRadarDevice(...)` and initial active light-mode dispatch in `configureServices(gatt)` whenever light control (`6a4e2f00`/`6a4e3202`) and `UUID_CHARACTERISTIC_MODEL_NUMBER` are both present (gating fallback timer posting on `!mIsShutDown && !mDisconnectedSinceConfigure` under `mLightRegistrationLock`), clamping non-StVZO active modes (`DAY_FLASH`/`NIGHT_FLASH`) to `SOLID` and synchronizing `RadarLightController` on legacy clamping only when `writeQueued && clamped && isLegacyLightOnly` with post-write disconnect re-verification under `mLightRegistrationLock`.
   - Added bounded fallbacks for absent DIS/Model-Number (immediate registration preserving FIFO CCCD order), `null`/empty read failure (immediate single fallback), and `MODEL_NUMBER_READ_TIMEOUT_MS = 3000L` timeout (idempotent guard + late StVZO self-healing clamp).
   - Extracted shared `resetConnectionState()` for `disconnectFromGatt()`/`shutDown()` to cancel fallback timers, clear model/StVZO state, and drop stale-GATT or post-disconnect callbacks while keeping `BTLERadarDevice.java` at `1,467/1,500` LoC.

## Verification Evidence
- **Targeted Unit, Compose UI & Contract Tests (`./gradlew :app:testDebugUnitTest --rerun`, `141/141` PASS across 8 suites, `0` fail, `0` err, `0` skip, `0` new compiler warnings)**:
  - `RadarLightQuickSwitchSheetTest` (`11/11` PASS incl. `testModalWrapper_clickingAllSnoozePresets_triggersOnSnoozeSelectedBeforeDismiss_TST_CON_055_1` & `testModalWrapper_activeSnooze_clickingResume_triggersOnResumeAudioBeforeDismiss_TST_CON_055_1`)
  - `BTLERadarDeviceTest` (`64/64` PASS incl. 7 `TST-CON-055.2`–`.4` tests covering legacy RTL516, RearVue `010-03180-10`, non-StVZO RTL515, read failure, timeout + late StVZO self-heal + idempotent stale timeout, disconnect/shutdown/stale-GATT isolation, and Bounce 1 concurrent disconnect during clamped `setLightMode()`)
  - `RadarLightModeGridTest` (`3/3`), `RadarLightControllerTest` (`39/39`), `UiReflectionOnlyTestAuditContractTest` (`4/4`), `ArchitectureConformanceContractTest` (`11/11`), `JavaCoreMigrationStrategyContractTest` (`5/5`), `SystemLiveTrackingRequirementsContractTest` (`4/4`).
- **Visual & Live Emulator Verification (`Rule 8.5`)**:
  - Visually inspected rendered Light/Dark Compose preview (`../test_evidence/tickets/ATT-8123/screenshots/att8123_quick_switch_compose.png`) and live `emulator-5554` launch (`MainActivityWithNavigation`, `CRASH_LOGCAT_CLEAN`, `../test_evidence/tickets/ATT-8123/screenshots/att8123_emulator_smoke.png`).
- **Evidence Bundle**: [ATT-8123 Evidence Manifest](../test_evidence/tickets/ATT-8123/manifest.json) | [Compose Quick-Switch Preview](../test_evidence/tickets/ATT-8123/screenshots/att8123_quick_switch_compose.png) | [Emulator Smoke Screenshot](../test_evidence/tickets/ATT-8123/screenshots/att8123_emulator_smoke.png).
