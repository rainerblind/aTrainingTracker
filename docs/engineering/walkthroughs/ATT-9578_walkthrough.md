# Stage 5 Walkthrough — ATT-9578: Zero-`runBlocking`, Cold-Start-Safe Smartphone Raw Telemetry Opt-In

- **Ticket**: `ATT-9578` (`2 SP`, Bug, `tier-a`, Epic `ATT-166`) | **Target Release**: `V5.0.0` | **Branch**: `feature/ATT-9578` (base `sprint/2026-11-23_03_Nacht` @ `d070157f`)
- **Gate 3**: `ATT-9673` (`GATE_PASSED`) | **Traceability**: `REQ-SET-131` (`[Verified]`) <-> `TST-SET-133` (`[Verified]`), refining `REQ-SET-079` & `REQ-EXP-045.1`
- **Evidence Bundle**: `docs/engineering/test_evidence/tickets/ATT-9578/` (`evidence_summary.json`, `manifest.json`, 24 JUnit XMLs, smoke screenshot)

## 1. Summary of Implemented Changes

| File | LoC (Cap) | Change |
|---|---|---|
| `TuningPreferencesDataStore.kt` | `2,279` (`<= 2,410`) | Replaced `by lazy` `stateIn` `recordSmartphoneRawTelemetryEnabled` with eager `MutableStateFlow<Boolean>` declared strictly above `tuningConfigFlow` and `init`. Added exception-safe, delta-only default `SharedPreferences` cold-start mirror (strictly gated on `dataStoreOverride == null`) seeding both `_tuningConfigStateFlow` and `_recordSmartphoneRawTelemetryEnabled` on construction. Unified `editAndSync` and `init` collector through `syncCachedConfig(cfg)` and routed `updateRecordSmartphoneRawTelemetryEnabled()` through `editAndSync`. |
| `SettingsDataStoreJavaHelper.kt` | `370` (`<= 1,500`) | Updated `isRecordSmartphoneRawTelemetryEnabled(context)` to read `getTuningConfig(context).recordSmartphoneRawTelemetryEnabled` in $O(1)$ with zero `runBlocking`, preserving `@JvmStatic` & `mockkStatic` compatibility. |
| `SmartphoneRawTelemetryRecorder.kt` | `835` (`<= 1,500`) | Removed `runBlocking { TuningPreferencesDataStore.getInstance(context).recordSmartphoneRawTelemetryFlow.first() }` from `isEnabled(context)` and delegated directly to `SettingsDataStoreJavaHelper.isRecordSmartphoneRawTelemetryEnabled(context)`. Retained timeout-bounded `stopBlocking()`. |
| `DataStoreHotPathContractTest.kt` | — | Added `testSmartphoneRawTelemetryRecorder_dataStoreReadsDoNotContainRunBlocking_TST_SET_133_4` banning `runBlocking` in `SmartphoneRawTelemetryRecorder.kt` outside `stopBlocking()` (skipping imports/comments). |
| `SensorsGpsFilterSectionTest.kt`, `SettingsDataStoreJavaHelperNonBlockingTest.kt`, `SmartphoneRawTelemetryRecorderTest.kt` | — | Added `@Before`/`@After` singleton isolation in `SensorsGpsFilterSectionTest`; verified cold-start `saveTuningConfig`, `updateRecordSmartphoneRawTelemetryEnabled`, upgrade-path mirror backfill (awaiting `init` hydration), and `resetToDefaults` parity; verified pure-JVM `dataStoreOverride != null` isolation (`0` `getSharedPreferences` calls) and `mockkStatic` delegation. |

## 2. Verification Results

| Check | Outcome | Details |
|---|---|---|
| Tier-1 Targeted Unit & Contract Tests (`--rerun`) | **PASS (`122/122`)** | `0` fail, `0` err, `0` skip across 24 suites (`DataStoreHotPathContractTest` `4/4`, `SettingsDataStoreJavaHelperNonBlockingTest` `8/8`, `SensorsGpsFilterSectionTest` `4/4`, `SmartphoneRawTelemetryRecorderTest` `12/12`, `TuningPreferencesDataStore*` 14 suites `71/71`, `AdvancedTuning*`/`StopTrackingCoachmarkTest` `3/3`, `ArchitectureConformanceContractTest` `11/11`, `SourceTextReadingAuditContractTest` `6/6`, `InterfaceSignatureDriftContractTest` `3/3`). `0` new production warnings. |
| Static Safety Linters | **PASS (`3/3`)** | `lint_cursor_safety.py`, `lint_lifecycle_safety.py`, `lint_safe_enum.py` all PASS. |
| Living-Doc Governance | **PASS (`EXIT 0`)** | `verify_requirement_governance.py --sync-indexes` & `--base sprint/2026-11-23_03_Nacht` PASS (`REQ-SET-131` & `TST-SET-133` `[Verified]`, `v1.0.50`). |
| Live `emulator-5554` Smoke (`Pixel_8_Android_15`) | **PASS** | `installDebug` + cold launch `MainActivityWithNavigation` (`Status: ok`, `CRASH_LOGCAT_CLEAN`); `docs/engineering/walkthroughs/img/ATT-9578_emulator_smoke.png` visually inspected via `read_file` (`Rule 8.5`). |

## 3. Invariant Verification

- Zero `runBlocking` DataStore reads in `SettingsDataStoreJavaHelper.kt` & `SmartphoneRawTelemetryRecorder.kt`; zero main-thread blocking in `TrackerService`.
- `TrackerService.java`, `TrainingApplication.java`, UI composables, SQLite schemas, and string resources (`0` string changes) untouched; `ANT-Android-SDKs` remains clean git submodule (`160000`).
