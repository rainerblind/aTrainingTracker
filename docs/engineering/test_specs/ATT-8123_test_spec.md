# Stage 2 Requirement & Test Specification: ATT-8123

- **Ticket**: `ATT-8123` (`tier-a`, `2 SP`, Target Release `V5.0.0`)
- **Living Docs Updated**: `docs/requirements/req_tracking_cockpit_part1.md` (`REQ-CON-055` `[Planned]`), `docs/tests/tst_tracking_cockpit_part2.md` (`TST-CON-055` `[Planned]`; continuation shard because `tst_tracking_cockpit.md` is `3,995/4,000` lines), `docs/requirements.md`, `docs/tests.md`
- **System Requirement(s)**: `SYSREQ-TRK-002`, `SYSREQ-TRK-007` | **Localization Parity**: `N/A - 0 string changes`

## Formal Requirement (`REQ-CON-055`)
1. **`REQ-CON-055.1` (Compile-Time Required Action Callbacks & Wrapper Forwarding)**:
   - Both `RadarLightQuickSwitchSheet` and `RadarLightQuickSwitchSheetContent` SHALL declare `onModeSelected`, `onAutoSelected`, `onSnoozeSelected`, `onResumeAudio`, and `onDismiss` as required parameters with zero silent `= {}` no-op defaults, and `RadarLightQuickSwitchSheet` SHALL forward all 5 callbacks verbatim to `RadarLightQuickSwitchSheetContent`.
   - Tapping any snooze preset (`SNOOZE_15M`, `SNOOZE_30M`, `FAST_ONLY`, `REST_OF_RIDE`) or `Resume Alerts` inside `RadarLightQuickSwitchSheet` SHALL invoke `onSnoozeSelected(exactMode)` / `onResumeAudio()` strictly prior to `onDismiss()`.
2. **`REQ-CON-055.2` (StVZO-Safe Deferred GATT Registration & Clamped Initial Mode Dispatch)**:
   - When `BTLERadarDevice.configureServices(gatt)` discovers light control (`6a4e2f00` or `6a4e3202`) AND `UUID_SERVICE_DEVICE_INFORMATION` exposes `UUID_CHARACTERISTIC_MODEL_NUMBER`, `BTLERadarDevice` SHALL enqueue the model-number GATT read and defer `RadarLightController.registerRadarDevice(...)` and initial active light-mode dispatch until the model-number read resolves.
   - Upon non-empty model string resolution (e.g., `"Garmin Varia RTL516"` or `"010-03180-10"`), `BTLERadarDevice` SHALL register once with exact `(isLightControlSupported, isStvzoModel(model), isRearVue820Model(model))` and dispatch any active non-`OFF` workout mode via `setLightMode(safeMode)` where non-StVZO modes (`DAY_FLASH`, `NIGHT_FLASH`, `PELOTON`) are clamped to `RadarLightMode.SOLID` (updating `RadarLightController` if clamped).
3. **`REQ-CON-055.3` (Bounded Fallbacks for Absent Characteristic, Read Failure, and Read Timeout)**:
   - When DIS or `UUID_CHARACTERISTIC_MODEL_NUMBER` is absent, `configureServices` SHALL register and apply any active mode immediately (preserving FIFO CCCD-before-write order).
   - When the model-number GATT read returns `null`/empty (read failure) or expires after `MODEL_NUMBER_READ_TIMEOUT_MS` (`3000L`), `BTLERadarDevice` SHALL fall back idempotently once to `(isStvzo = false, hasHardwareBrake = false)` and dispatch any active mode.
4. **`REQ-CON-055.4` (Disconnect / Shutdown / Stale-GATT Race Isolation & Monolith Cap)**:
   - `disconnectFromGatt()` and `shutDown()` SHALL cancel pending fallback timers, clear pending registration and StVZO/model state (`mIsStvzo = false`, `mHasHardwareBrakeSensor = false`, `mModelNumber = null`), and reject any subsequent late `onOtherCharacteristicUpdate` or fallback timer invocations for disconnected/stale GATT sessions.
   - `BTLERadarDevice.java` SHALL remain `<= 1,500` LoC (`ArchitectureConformanceContractTest`).

## Verification Matrix (`TST-CON-055`)
| Sub-ID | Test Class & Method | Scenario & Pass Criteria |
| :--- | :--- | :--- |
| `TST-CON-055.1` | `RadarLightQuickSwitchSheetTest` (new modal wrapper tests + updated callers) | Render `RadarLightQuickSwitchSheet(...)` directly via Robolectric Compose; click `SNOOZE_15M`, `SNOOZE_30M`, `FAST_ONLY`, `REST_OF_RIDE`, and `RadarSnooze_Resume`; assert exact callback fires strictly before `onDismiss()` (`events == listOf("snooze:<mode>", "dismiss")` / `listOf("resume", "dismiss")`). |
| `TST-CON-055.2` | `BTLERadarDeviceTest` (deferred StVZO RTL516 & RearVue `010-03180-10` tests) | With active `DAY_FLASH` and `UUID_CHARACTERISTIC_MODEL_NUMBER` present: after `configureServices`, assert `RadarLightController.isLightControlAvailable.value == false` and zero light-mode writes queued; deliver `"RTL516"` (legacy) and `"010-03180-10"` (RearVue); assert single registration with `isStvzo == true` (and `hasHardwareBrakeSensor == true` for RearVue) and clamped `SOLID` write (never `DAY_FLASH`). |
| `TST-CON-055.3` | `BTLERadarDeviceTest` (absent DIS, null/error read, timeout fallback tests) | Verify (a) absent DIS registers & queues immediately (existing FIFO order intact), (b) `getStringValue(0) == null` triggers single fallback registration (`isStvzo == false`) + active mode dispatch, (c) `modelNumberFallbackRunnableForTesting.run()` triggers single idempotent fallback after timeout and cancels cleanly on earlier read. |
| `TST-CON-055.4` | `BTLERadarDeviceTest` + `RadarLightModeGridTest` + `RadarLightControllerTest` + `ArchitectureConformanceContractTest` + `JavaCoreMigrationStrategyContractTest` | Verify `disconnectFromGatt()` / `shutDown()` clears state and drops late model-number update & fallback timer (never re-registers); verify `BTLERadarDevice.java <= 1,500` LoC and `0` regressions across all 5 suites. |
