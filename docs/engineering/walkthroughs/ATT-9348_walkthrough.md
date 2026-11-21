# Stage 5 Verification Walkthrough (Bounce 1) — ATT-9348: Smartphone Raw Sensor Sidecar & Cloud Export

- **Ticket**: `ATT-9348` (`3 SP`, Feature, Epic `ATT-166`) | **Target Release**: `V5.0.0` | **Branch**: `feature/ATT-9348` (intact for Stage 6 Bounce 1 Cold Re-Audit; NOT merged per `Rule 3.3`)
- **Verification Path**: `Rule 7.5` Doc-Only Fast Path (`0` files modified under `app/` since Gate 4 Bounce 1 verified SHA `94e7f204`; reused Gate 4 Bounce 1 `ATT-9471` independently verified `141/141 PASS` across 21 suites)
- **Living Documentation Promoted**: `REQ-EXP-045` (`[Verified]`, `req_data_migration_export_part2.md` `v1.0.12`) & `TST-EXP-045` (`[Verified]`, `tst_data_migration_export.md` `v1.0.13`); master indexes synchronized (`--check-living-doc-layout --check-verified-evidence` exit `0`).

## 1. Delivered Capabilities & Stage 6 Bounce 1 Remediations

| Capability | Implemented Files / Symbols | Verified Invariants |
|---|---|---|
| Direct Opt-In Sensor Capture & Pre-EKF GNSS (`REQ-EXP-045.1`) | `SmartphoneRawTelemetryRecorder.kt`, `SmartphoneRawSensorCapture.kt`, `SettingsDataStoreJavaHelper.kt`, `SpeedAndLocationDevice.java`, `SpeedAndLocationDevice_ATTFused.kt`, `TrackerService.java` | Default `false` -> `0` files/writers; `SmartphoneRawSensorCapture` owns its own background `HandlerThread` (`SensorManager` `ACC`/`GYRO`/`ROTVEC`/`BARO`/`STEP`, auto-cleanup on `0` sensors) so recording works on default Google Fused, GPS, indoor, and post-startup toggle-ON workouts; pre-EKF raw GNSS recorded exactly once via `recordsRawLocationDirectly()` (no EKF mutation, no duplicates, includes rejected/stationary fixes); cold-start `recordSmartphoneRawTelemetryFlow.first()` with MockK guard; independent `TrackerService` `try/catch` blocks; `.isFinite()` GNSS guards. |
| Unified `CLOCK_BOOTTIME`, Authoritative `STATE` & Crash Salvage (`REQ-EXP-045.2`) | `PhoneRawMetadata.kt`, `PhoneRawTelemetryRow.kt`, `PhoneRawSidecarRecovery.kt`, `PhoneRawTelemetryReader.kt` | All 8 channels (`ACC`, `GYRO`, `ROTVEC`, `BARO`, `STEP`, `GNSS`, `WHEEL`, `STATE`) share `SystemClock.elapsedRealtimeNanos()` (`CLOCK_BOOTTIME`); `STATE(sportId, bSportCode, carriageCode)` emitted at start/resume, sport-type switches, and carriage changes; `.tmp -> .salvage` & reader strictly drop non-newline-terminated trailing bytes; in-memory `resumeOrStartActiveSession` reuses active session; `stopBlocking(timeoutMs = 5000L)` timeout-bounded. |
| Multi-File `RAW_SENSOR` Export & Cloud Upload (`REQ-EXP-045.3`) | `RawSensorSidecarFiles.kt`, `BaseExporter.java`, `RawSensorFileExporter.kt`, `DropboxUploader.java`, `GoogleDriveUploader.kt` | `FileFormat.RAW_SENSOR` enum & SQLite schema unchanged; exports `(Polar-only, Phone-only, Both)` with graceful skip on `Neither`; partial upload failure returns `ExportResult(false, shallRetry, ...)` without masking failure. |
| Retention, Privacy, Backup & Dual-Host 9-Locale UI (`REQ-EXP-045.4`, `.5`) | `PolarRawTelemetryPruner.kt`, `SensorDiagnosticBundlePackager.kt`, `full_backup_content.xml`, `data_extraction_rules.xml`, `TuningPreferencesDataStore.kt`, `SensorsGpsFilterSection.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, 9x `strings_devices.xml` | Pruner/cascade delete covers `.phone_raw.csv.gz*`; `.phone_raw.csv.gz` omitted when `includeGps == false` (`0` lat/lon leakage) + locale-independent float CSV formatting; `raw_sensor` excluded from Auto Backup; dual-host `TuningToggleItem` (`Rule 82` `initial = null` hydration) + 9 locales (`EN, DE, ES, FR, IT, JA, NL, PL, PT`, `0` emojis). |

## 2. Verification Evidence Matrix (`94e7f204` — `141/141 PASS` Across 21 Suites, `0` New Warnings)

| Verification Group | Suites & Exact Counts | Result |
|---|---|---|
| Core Recorder, Capture, Recovery & Pure-JVM Reader (`TST-EXP-045.1`, `.2`) | `SmartphoneRawTelemetryRecorderTest` (`10/10`), `PhoneRawSidecarRecoveryTest` (`3/3`), `PhoneRawTelemetryReaderTest` (`2/2`), `SpeedAndLocationDevice_ATTFusedTest` (`36/36`), `PolarRawTelemetryRecorderTest` (`9/9`) | `60/60 PASS` |
| Multi-File Export, Pruner, Privacy & Backup (`TST-EXP-045.3`, `.4`) | `RawSensorFileExporterTest` (`5/5`), `DropboxUploaderRawSensorTest` (`4/4`), `GoogleDriveUploaderRawSensorTest` (`4/4`), `PolarRawTelemetryPrunerTest` (`5/5`), `SensorDiagnosticBundlePackagerTest` (`1/1`), `SensorDiagnosticBundlePrivacyTest` (`3/3`), `BackupRulesXmlValidationTest` (`4/4`) | `26/26 PASS` |
| Compose UI, 9-Locale Localization & Design (`TST-EXP-045.5`) | `SensorsGpsFilterSectionTest` (`3/3`, behavioral `createComposeRule()` + cold-start persistence), `TuningDesignGuidelinesContractTest` (`5/5`), `DisplayAndExpertSettingsLocalizationContractTest` (`5/5`), `TranslationParityTest` (`11/11`) | `24/24 PASS` |
| Architecture, Detailed Design & System Contracts | `DetailedDesignParityContractTest` (`3/3`), `SystemDataExchangeRequirementsContractTest` (`4/4`), `SystemLiveTrackingRequirementsContractTest` (`4/4`), `SystemArchitectureDesignContractTest` (`9/9`), `ArchitectureConformanceContractTest` (`11/11`) | `31/31 PASS` |
| Static Safety Linters & Governance | `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py`, `verify_requirement_governance.py --check-living-doc-layout --check-verified-evidence` | `4/4 PASS` |

## 3. Monolith Budgets, Traceability & Branch State

- **Monolith LoC Ceilings**: `SpeedAndLocationDevice_ATTFused.kt` `1,495 / 1,500`, `TrackerService.java` `3,551 / 3,559`, `TuningPreferencesDataStore.kt` `2,170 / 2,410`, `TrainingApplication.java` `2,134 / 2,135` (`0` edits); `ANT-Android-SDKs` intact (`160000`).
- **SWE.2 / SWE.3 / SYS.2 Sync**: `docs/architecture.md` (`v1.0.19`), `docs/architecture/detailed_design/tracker_service.md` (`v1.0.7`), `docs/system/{sysreq_data_exchange.md,sysreq_live_tracking.md,system_software_traceability.md}` (`SYSREQ-DEX-001`, `SYSREQ-DEX-004`, `SYSREQ-TRK-010`).
- **Stage 6 Readiness**: `feature/ATT-9348` preserved intact for Stage 6 Bounce 1 Cold Re-Audit (`Rule 3.3`).
