# Stage 2 Requirement & Test Specification — ATT-9348

- **Ticket**: `ATT-9348` (`3 SP`, Feature, Epic `ATT-166`) | **Target Release**: `V5.0.0` | **Risk Rating**: `HIGH`
- **Reserved IDs**: `REQ-EXP-045` (`[Planned]`), `TST-EXP-045` (`[Planned]`)
- **System Traceability (`SYS.2`/`SYS.3`)**: Refines `SYSREQ-DEX-001`, `SYSREQ-DEX-004`, `SYSREQ-TRK-010` (no `SYS.2`/`SYS.3` changes; `docs/architecture.md` SWE.2 §2.3/§3.3.3/§4.9 update in Stage 3/5).
- **Living-Doc Shards**: `docs/requirements/req_data_migration_export_part2.md` (`v1.0.8`), `docs/tests/tst_data_migration_export.md` (`v1.0.9`).

## 1. Requirements Summary (`REQ-EXP-045` — Priority: `High`, Type: `Functional`, Verification: `Unit / Integration`)

| Sub-ID | Normative Contract (SHALL / MUST) |
|---|---|
| `REQ-EXP-045.1` | Opt-in `recordSmartphoneRawTelemetryEnabled` (`false` default in `TuningPreferencesDataStore`, guarded against plain-JVM MockK tests without DataStore/`filesDir`). When enabled, `TrackerService` (`<= 3,559` LoC) starts/resumes/stops `SmartphoneRawTelemetryRecorder` (`files/raw_sensor/<base>.phone_raw.csv.gz`, lazy open -> `0` empty files); `Channel(8192, DROP_OLDEST)` with `onUndeliveredElement` drop counter, reusable `StringBuilder` (`0` `String.format`), periodic `GZIPOutputStream(syncFlush=true)`, `scope.cancel()` on `stop()` (matching `ATT-9343`). `SpeedAndLocationDevice_ATTFused` (`<= 1,500` LoC) taps `ACC`, `GYRO` (registered when `isEffectiveGyroActive \|\| recorderActive`, recorded before the `!isEffectiveGyroActive` EKF guard), `ROTVEC` (`size >= 3`, deriving `qw` if `size == 3`), `BARO`, `STEP`, `GNSS`, `WHEEL`, `STATE`. |
| `REQ-EXP-045.2` | Self-describing `# metadata: {"schemaVersion":1,"appVersion":"...","deviceModel":"...","sportId":...,"sessionStartTimeMs":...}` + CSV `elapsedRealtimeNs,epochMs,channel,v0,v1,v2,v3,v4,v5` covering all 8 channels for `ATT-9349`. `PhoneRawSidecarRecovery` salvages truncated `.tmp` via `.salvage` + small-chunk `GZIPInputStream` + CSV validation on resume & export (matching `ATT-9344`). Pure-JVM `PhoneRawTelemetryReader` parses metadata & streams rows with truncated-EOF resilience. |
| `REQ-EXP-045.3` | Keep `FileFormat.RAW_SENSOR` enum & DB untouched. Shared `RawSensorSidecarFiles.resolveExportableSidecars()` salvages `.tmp` and resolves all present sidecars (`[.polar_raw.csv.gz, .phone_raw.csv.gz]`) across `RawSensorFileExporter`, `BaseExporter`, `DropboxUploader`, and `GoogleDriveUploader` (`Polar-only`, `Phone-only`, `Both`, graceful skip when `Neither`). |
| `REQ-EXP-045.4` | `PolarRawTelemetryPruner` (`hasSidecar`, `deleteSidecar`, `pruneExpiredSidecars`) manages both `.polar_raw.csv.gz*` and `.phone_raw.csv.gz*`. `SensorDiagnosticBundlePackager` omits `.phone_raw.csv.gz` when `includeGps == false`. `full_backup_content.xml` and `data_extraction_rules.xml` (`cloud-backup` & `device-transfer`) exclude `domain="file" path="raw_sensor"` (matching `ATT-9352`). |
| `REQ-EXP-045.5` | `SensorsGpsFilterSection` adds `TuningToggleItem` above `RawTelemetryRetentionControl`, wired in both `AdvancedTuningScreen` & `AdvancedTuningDialog` with `initial = null` async form hydration (`Rule 82`) and 9-locale `strings_devices.xml` parity (no emojis). |

## 2. Traceability Matrix & Verification Plan (`TST-EXP-045`)

| Test ID | Target Test Class(es) | Requirement(s) | Key Verification Procedure |
|---|---|---|---|
| `TST-EXP-045.1` | `SmartphoneRawTelemetryRecorderTest.kt`, `SpeedAndLocationDevice_ATTFusedTest.kt` | `REQ-EXP-045.1` | Disabled = `0` files/writers; `DROP_OLDEST` overflow > `8,192` reports exact `getDroppedFrameCount()`; empty-session discard; `ROTVEC` `size == 3` derives `qw`; raw `GYRO` recorded while `isEffectiveGyroActive == false` without altering EKF; plain-JVM MockK test safety. |
| `TST-EXP-045.2` | `PhoneRawSidecarRecoveryTest.kt`, `PhoneRawTelemetryReaderTest.kt` | `REQ-EXP-045.2` | Round-trip all 8 channels (`ACC`..`STATE`) & `# metadata` JSON; mid-stream truncated `.phone_raw.csv.gz.tmp` recovers intact rows via `.salvage` and appends cleanly on resume; pure-JVM reader handles truncated EOF gracefully. |
| `TST-EXP-045.3` | `RawSensorFileExporterTest.kt`, `DropboxUploaderRawSensorTest.kt`, `GoogleDriveUploaderRawSensorTest.kt` | `REQ-EXP-045.3` | Matrix `(Polar-only, Phone-only, Both, Neither)` + `.tmp` export-time salvage across local Downloads, Dropbox, and Google Drive; standard formats unaffected. |
| `TST-EXP-045.4` | `PolarRawTelemetryPrunerTest.kt`, `SensorDiagnosticBundlePackagerTest.kt`, `BackupRulesXmlValidationTest.kt` | `REQ-EXP-045.4` | `hasSidecar`, `deleteSidecar`, and retention pruning cover `.phone_raw.csv.gz*`; `includeGps=false` leaks `0` lat/lon (omits `.phone_raw.csv.gz`); `raw_sensor` excluded in all 3 backup XML sections. |
| `TST-EXP-045.5` | `SensorsGpsFilterSectionTest.kt`, `TranslationParityTest`, `TuningDesignGuidelinesContractTest`, `ArchitectureConformanceContractTest` | `REQ-EXP-045.5`, `REQ-EXP-045.1` | Dual-host toggle persistence + `initial = null` hydration; 9-locale parity (`0` emojis); `SpeedAndLocationDevice_ATTFused.kt <= 1,500`, `TrackerService.java <= 3,559`, `TuningPreferencesDataStore.kt <= 2,410`, `TrainingApplication.java` untouched. |

## 3. Acceptance Criteria (Given-When-Then)

- **Given** `recordSmartphoneRawTelemetryEnabled == false` (default), **When** a workout runs on `SpeedAndLocationDevice_ATTFused`, **Then** `0` `.phone_raw.csv.gz*` files or writer coroutines are created.
- **Given** recording is enabled and a workout streams `ACC`, `GYRO` (including when `isEffectiveGyroActive == false`), `ROTVEC` (`3` or `4` floats), `BARO`, `STEP`, `GNSS`, `WHEEL`, and `STATE`, **When** stopped cleanly or killed mid-stream leaving a truncated `.tmp`, **Then** valid RFC-1952 `.phone_raw.csv.gz` is produced/salvaged and parsed losslessly by pure-JVM `PhoneRawTelemetryReader`.
- **Given** a finished workout with Polar-only, Phone-only, or both sidecars and `export_raw_sensor == true`, **When** exported to Local Downloads, Dropbox, or Google Drive, **Then** every present sidecar is exported/uploaded without failing standard exports; when neither exists, export skips cleanly.
- **Given** a workout with `.phone_raw.csv.gz`, **When** pruned by retention or deleted, **Then** `.phone_raw.csv.gz*` is deleted; **When** packaged with `includeGps == false`, **Then** zero GNSS coordinates leak; **When** Android Auto Backup runs, **Then** `raw_sensor` is excluded.

## 4. 9-Locale Localization Matrix (`app/src/main/res/values*/strings_devices.xml`, Zero Emojis)

| Locale | `tuning_record_smartphone_raw_title` | `tuning_record_smartphone_raw_desc` |
|---|---|---|
| `values` (EN) | `Record smartphone raw sensors` | `Save compressed IMU, barometer, step and GNSS sidecar per workout for offline replay` |
| `values-de` (DE) | `Smartphone-Rohsensoren aufzeichnen` | `Komprimierte IMU-, Barometer-, Schritt- und GNSS-Rohdaten pro Training fuer Offline-Replay speichern` |
| `values-es` (ES) | `Grabar sensores brutos del telefono` | `Guardar archivo comprimido de IMU, barometro, pasos y GNSS por sesion para reproduccion sin conexion` |
| `values-fr` (FR) | `Enregistrer les capteurs bruts du smartphone` | `Enregistrer un fichier compresse IMU, barometre, pas et GNSS par seance pour la lecture hors ligne` |
| `values-it` (IT) | `Registra sensori grezzi dello smartphone` | `Salva file compresso di IMU, barometro, passi e GNSS per allenamento per analisi offline` |
| `values-ja` (JA) | `スマホ生センサーを記録` | `オフライン再生用にワークアウトごとのIMU・気圧・歩数・GNSS圧縮ファイルを保存` |
| `values-nl` (NL) | `Ruwe smartphonesensoren opnemen` | `Sla gecomprimeerde IMU-, barometer-, stap- en GNSS-gegevens per training op voor offline herhaling` |
| `values-pl` (PL) | `Rejestruj surowe czujniki smartfona` | `Zapisuj skompresowane dane IMU, barometru, krokow i GNSS dla kazdego treningu do odtwarzania offline` |
| `values-pt` (PT) | `Gravar sensores brutos do smartphone` | `Salvar arquivo comprimido de IMU, barometro, passos e GNSS por treino para reproducao offline` |

*(Note: Native diacritics `für`, `teléfono`, `barómetro`, `sesión`, `conexión`, `compressé`, `baromètre`, `séance`, `kroków`, `każdego`, `barômetro`, `reprodução` will be written in UTF-8 XML during Stage 4.)*

## 5. System Invariants & Chesterton's Fence

- Net-new `REQ-EXP-045` extends `REQ-EXP-044` & `REQ-SET-117` without relaxing any existing requirement; Polar `.polar_raw.csv.gz` format/path/export/recovery stays 100% byte-compatible, `FileFormat.RAW_SENSOR` enum & SQLite schema stay unchanged, and `ANT-Android-SDKs` stays `160000`.
