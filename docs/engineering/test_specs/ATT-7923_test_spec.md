# Stage 2 Requirement & Test Spec: ATT-7923

Target Release: V5.0.0

## Formal Requirements (`REQ-SET-108` [Planned])
- `REQ-SET-108.1` (`Priority: High`, `Type: Functional`, `Verification Method: Unit / Integration`): Detect Polar Verity Sense / `FEATURE_POLAR_OFFLINE_RECORDING` optical HRM devices (name/manufacturer + cached PFTP capability), reuse `Devices.db` v13 `polarH10OfflineRecording` (`DEFAULT 1`), and show the `@RequiresApi(33)` backup toggle in `EditDeviceDialog` with accurate 9-locale HR-only copy (no emojis).
- `REQ-SET-108.2` (`Priority: High`, `Type: Functional`, `Verification Method: Unit / Integration`): Extract shared GATT arbitration (`PolarOfflineGattCoordinator`) for H10 + Verity Sense; `PolarVeritySenseOfflineController` SHALL await `FEATURE_POLAR_OFFLINE_RECORDING` & `FEATURE_POLAR_DEVICE_TIME_SETUP`, call `setLocalTime()`, stop pre-existing `HR` if returned by `getOfflineRecordingStatus()`, start `PolarDeviceDataType.HR` (`settings = null, secret = null`), restore GATT in `finally`, and wire `TrackerService`.
- `REQ-SET-108.3` (`Priority: High`, `Type: Non-Functional`, `Verification Method: Unit`): Enforce single-arm `isArmed` invariant (mid-workout `0x180D` flaps never interrupt PFTP), handle `PbPFtpError.SYSTEM_BUSY` non-blockingly, and expose `stopAndFetchOfflineHr()` + `removeOfflineRecord()` for `ATT-7924`.

## Test Specification (`TST-SET-110` [Planned]) & Traceability
| Test ID | Traces To | Verification Procedure | Expected Outcome |
| :--- | :--- | :--- | :--- |
| `TST-SET-110.1` | `REQ-SET-108.1` | Unit tests for `isPolarVeritySenseDevice()`, PFTP capability cache, `< API 33` & disabled-pref guards | Optical Polar HRMs detected accurately; H10 unaffected; no-op when disabled or `< API 33` |
| `TST-SET-110.2` | `REQ-SET-108.2` | Mock `PolarBleApi` (`PolarOfflineRecordingApi`) + `MyBTLEDevice`: start workout with/without active `HR` | GATT paused -> `setLocalTime` -> status check -> optional `stopOfflineRecording(HR)` -> `startOfflineRecording(HR)` -> GATT resumed |
| `TST-SET-110.3` | `REQ-SET-108.3` | Re-trigger `onWorkoutStarted()` while `isArmed == true`; inject `PftpResponseError(SYSTEM_BUSY)` | Zero extra PFTP calls mid-workout; `SYSTEM_BUSY` non-blocking, resets `isArmed`, resumes GATT |
| `TST-SET-110.4` | `REQ-SET-108.3` | Unit test `stopAndFetchOfflineHr()` & `removeOfflineRecord()` with `HrOfflineRecording` | Stops `HR`, lists matching entry, returns `PolarHrData` samples, removes entry |
| `TST-SET-110.5` | `REQ-SET-108.1` | Compose test `EditDevicePolarBackupTest`, `TranslationParityTest`, `emulator-5554` screenshot | Verity Sense renders HR-only copy across 9 locales without emojis; toggle works on API 33+ |

## 9-Language Localization Matrix (`strings_devices.xml`)
- Keys: `device_polar_verity_backup_recording_title`, `device_polar_verity_backup_recording_desc` (plus shared `device_polar_h10_backup_recording_requires_android_13`).
- Audited across `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`: exact placeholder parity, zero emojis, literal `\n` only if multiline.
