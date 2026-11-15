# Stage 1 Analysis: ATT-7999

Target Release: V5.0.0

## Problem Domain & Forensic RCA
- **Gap 1 (Missing Prepared Swim Template & Speed Collision)**: `SportTypeDatabaseManager` seeds 5 rows (`OTHER=1`, `RUN=2`, `BIKE=3`, `WALK=4`, `MTB=5`) at `DB_VERSION = 3` (`DatabaseSchemaRegistry.kt` hardcodes `3` instead of referencing `SportTypeDbHelper.DB_VERSION`). Speed matching queries `minSpeed <= avgSpd AND maxSpeed > avgSpd`, so adding Swim with positive speeds risks colliding with walking (~1.0–1.8 m/s), while eager seeding clutters `SportTypesTabsScreen` for non-swimmers.
- **Gap 2 (No Expert Pre-Activation or Pool Length Setting)**: `TuningPreferencesDataStore` / `AdvancedTuningScreen` have no swimming configuration (`defaultPoolLengthMeters`, unit, or one-tap Swim sport-type creation) before a locker/pool swim workout starts.
- **Gap 3 (FIT `Sport.SWIMMING` Export Gap)**: `FitFileWriter.java` lines 179–202 only checks `tcxName.toLowerCase(Locale.US).contains("swim")`. Because Garmin TCX forces `tcxName = "Other"` for swimming while `stravaName = "Swim"` and `gcName = "swim"`, FIT exports emit `Sport.GENERIC` instead of `Sport.SWIMMING` / `SubSport.LAP_SWIMMING` and omit `SessionMesg` pool length fields.

## Independent Peer Review & Verification
- Verified `SportTypeDatabaseManager.java` (`TTSportType` lines 612–672, `SYSTEM_SPORT_ID_THRESHOLD = 5L`, `canDelete(id)` line 571, `DB_VERSION = 3` line 706) and `SportTypeDatabaseUpgradeTest.kt` line 152 asserting `DB_VERSION == 3`. Because no table columns change, `DB_VERSION` remains `3` and existing v1→v3 migrations remain untouched.
- Verified Garmin FIT SDK `21.141.0`: `SessionMesg` defines `setPoolLength(Float)` (meters, field 44) and `setPoolLengthUnit(DisplayMeasure)` (field 46: `METRIC`/`STATUTE`), plus `SubSport.LAP_SWIMMING`.
- Verified `SportTypeSelector.kt` selects coarse `BSportType` (`RUN`, `BIKE`, `UNKNOWN`), whereas `SportTypesTabsScreen.kt` / `SportTypesRepository.kt` list concrete `SportType.TABLE` rows; keeping Swim unseeded until `ensureSwimSportTypeExists()` preserves clean default UI.
- Verified `FitFileWriter.java` lines 175–202 & 646–770 and `SettingsDataStoreJavaHelper.getTuningConfig(Context)` safe JVM/MockK guard (lines 103–116) so FIT pool lookup stays non-blocking and unit-test safe.

## Scope Bounding & Complexity
- **Story Points**: `2 SP` (verified accurate: 3 localized seams, no SQLite schema bump, no multi-service refactor).
- **In Scope**: `TTSportType.SWIM` + `ensureSwimSportTypeExists()` + `isSwimSportTypeActivated()` in `SportTypeDatabaseManager` / `SportTypesRepository`; `DatabaseSchemaRegistry` direct `DB_VERSION` ref; `sport_type_swim` + pool settings across 9 locales; `TuningPreferencesDataStore` + `SwimmingSection` in `AdvancedTuningScreen`; `FitFileWriter` sport/subsport/pool-length export; unit & UI tests.
- **Out of Scope**: Automatic stroke/turn detection algorithms or live BLE swim sensor drivers (`ATT-7999` prepares template, settings, and FIT export only).

## System Invariants
- `SportType.db` keeps `DB_VERSION = 3`; fresh `onCreate()` seeds strictly the 5 existing default rows (`OTHER`, `RUN`, `BIKE`, `WALK`, `MTB`).
- `SWIM` sets `minSpeed = 0.0`, `maxSpeed = 0.0`, so `minSpeed <= avgSpd AND maxSpeed > avgSpd` is mathematically false for all speeds (zero walk/other collision).
- `ensureSwimSportTypeExists()` is idempotent: any existing non-placeholder row with `stravaName = "Swim"` or `gcName = "swim"` (case-insensitive) is reused without duplicate insertion.
- `TuningDesignGuidelinesContractTest` compliance: no `FilterChip`, raw `Switch`, or nested `ElevatedCard` in category composables; 9-locale parity without emojis.

## Change Impact Analysis
- **Affected Requirements**: Adds `REQ-SET-107` in `docs/requirements/req_settings_profiles.md` and `TST-SET-109` in `docs/tests/tst_settings_profiles.md` (preserving existing `REQ-SET-*` and `REQ-DAT-018` contracts).
- **Affected Architecture Elements**: `SportTypeDatabaseManager`, `SportTypesRepository`, `DatabaseSchemaRegistry`, `TuningPreferencesDataStore`, `SettingsDataStoreJavaHelper`, `AdvancedTuningAccordion`, `AdvancedTuningScreen`, `SwimmingSection` (new), `FitFileWriter`, `res/values*/strings.xml` (9 locales).
- **Verification Impact & Test Surface**: Extend `FitFileWriterTest`, `SportTypeDatabaseManagerStravaTest` (or new `SportTypeSwimTemplateTest`), `TuningConfigTest`, `AdvancedTuningAccordionTest`, `AdvancedTuningScreenTest`; run `TranslationParityTest`, `TuningDesignGuidelinesContractTest`, `DisplayAndExpertSettingsLocalizationContractTest`, `:app:lintDebug`, and UI screenshot capture on `emulator-5554`.
- **Risk Assessment & Invariants**: `LOW` risk — schema version unchanged, Java FIT bridge uses `SettingsDataStoreJavaHelper.getTuningConfig()` fallback-safe wrapper, and Swim remains absent until explicitly activated or auto-created.
