# Stage 3 Implementation Plan: ATT-7999

Target Release: V5.0.0

## SWE.2 Architecture & Layer Boundaries
- **Database Layer (`SportTypeDatabaseManager`)**: Prepare `TTSportType.SWIM` template (`BSportType.UNKNOWN`, `R.string.sport_type_swim`, `gcName="swim"`, `stravaName="Swim"`, `tcxName="Other"`, `runkeeperName="Swimming"`, `trainingPeaksName="Swim"`). Keep `DB_VERSION = 3` and keep `onCreate()` seeding strictly the 5 existing rows. Implement idempotent `ensureSwimSportTypeExists()` and `isSwimSportTypeActivated()` using SQLite transactions/guards (`minSpeed = 0.0`, `maxSpeed = 0.0`, `is_placeholder = 0`). Update `DatabaseSchemaRegistry` to reference `SportTypeDbHelper.DB_VERSION`.
- **Repository Layer (`SportTypesRepository`)**: Expose `ensureSwimSportTypeExists(): Long` and `isSwimSportTypeActivated(): Boolean` without exposing SQLite `Cursor` or manager handles (`docs/coding_standards.md` §2.5).
- **Preferences & Java Bridge (`TuningPreferencesDataStore`, `SettingsDataStoreJavaHelper`)**: Add `PoolLengthUnit` (`METERS`, `YARDS`), `defaultPoolLengthMeters: Float = 25.0f` (`10f..100f`), `defaultPoolLengthUnit: PoolLengthUnit = PoolLengthUnit.METERS` to `TuningConfig`, `ALL_KEYS`, and `resetToDefaults()`. Expose safe `getDefaultPoolLengthMeters(Context)` / `getDefaultPoolLengthUnit(Context)` via `SettingsDataStoreJavaHelper.getTuningConfig(Context)`.
- **Exporter Layer (`FitFileWriter`)**: Resolve `Sport.SWIMMING` checking `tcxLower.contains("swim") || "Swim".equalsIgnoreCase(stravaName.trim()) || "swim".equalsIgnoreCase(gcName.trim())`; when pool length `> 0f` or indoor pool context applies, set `SubSport.LAP_SWIMMING` and write `SessionMesg.setPoolLength(...)` & `setPoolLengthUnit(DisplayMeasure.STATUTE / METRIC)`.

## Atomic Implementation Steps
1. **9-Locale Strings (`REQ-SET-107.1`, `REQ-SET-107.3` -> `TST-SET-109.2`, `TST-SET-109.3`)**:
   - Add `sport_type_swim`, `tuning_cat_swimming`, `tuning_swim_activate_title`, `tuning_swim_activate_summary`, `tuning_swim_activate_active_summary`, `tuning_swim_pool_length_title`, `tuning_swim_pool_preset_custom`, `tuning_swim_pool_custom_meters_label`, `tuning_swim_subtitle_active`, `tuning_swim_subtitle_inactive` across all 9 `res/values*/strings.xml` without emojis.
2. **DB Template & Repository (`REQ-SET-107.1`, `REQ-SET-107.2` -> `TST-SET-109.1`, `TST-SET-109.2`)**:
   - Update `SportTypeDatabaseManager.java`, `SportTypesRepository.kt`, and `DatabaseSchemaRegistry.kt` (`SportTypeDbHelper.DB_VERSION`).
3. **Tuning DataStore & Java Helper (`REQ-SET-107.3` -> `TST-SET-109.3`)**:
   - Update `TuningPreferencesDataStore.kt` and `SettingsDataStoreJavaHelper.kt`.
4. **Swimming Expert Section & Reachability Mounting (`REQ-SET-107.3`, `Rule 81` -> `TST-SET-109.3`, `TST-SET-109.5`)**:
   - Add `TuningSection.SWIMMING` & `TuningSubtitleFormatter.formatSwimmingSubtitle` in `AdvancedTuningAccordion.kt`.
   - Create `ui/settings/tuning/categories/SwimmingSection.kt` (with Light/Dark `@Preview`) and mount it inside `AdvancedTuningScreen.kt` (plus deprecated `AdvancedTuningDialog.kt` if mirrored cleanly), invoking `SportTypesRepository.ensureSwimSportTypeExists()` on Save when enabled.
5. **FIT Swimming Export (`REQ-SET-107.4` -> `TST-SET-109.4`)**:
   - Update `FitFileWriter.java` sport resolution and `SessionMesg` pool-length encoding.
6. **Verification & Living Docs Update (`TST-SET-109.1..5`)**:
   - Add/extend unit tests in `SportTypeSwimTemplateTest.kt`, `FitFileWriterTest.kt`, `TuningConfigTest.kt`, `AdvancedTuningAccordionTest.kt`.
   - Run targeted tests + `TranslationParityTest` + `TuningDesignGuidelinesContractTest` + `:app:lintDebug` + emulator UI screenshot capture (`emulator-5554`), then mark `REQ-SET-107` / `TST-SET-109` `[Implemented]`.

## UI Consistency (`docs/design_guidelines.md` §1)
- Uses standardized `TuningToggleItem` for Swimming activation (`isSwimEnabled`).
- Uses `TuningSegmentedButtonRow` (delegating to `SingleChoiceSegmentedButtonRow` with `icon = {}`) for 4 short presets (`25 m`, `50 m`, `25 yd`, `Custom` / `Eigene` fitting single-line 360dp widths) plus `TuningSliderItem` (`10..100 m`) when `Custom` is selected.
- Zero `FilterChip`, zero raw `Switch(`, zero nested `ElevatedCard(` inside accordion body (`TuningDesignGuidelinesContractTest` enforced).

## Invariants & Verification Mapping
- `SportType.db` keeps `DB_VERSION = 3` and seeds strictly 5 default rows; `SWIM` speed range `[0.0, 0.0)` never matches `getSportTypesIdList(BSportType, avgSpd)` (`1.4 m/s` -> `WALK`).
- Verification commands: `./gradlew :app:testDebugUnitTest --tests "...SportType*" --tests "...FitFileWriterTest" --tests "...tuning.*" --tests "...TuningConfigTest" --tests "...TranslationParityTest"`, `./gradlew :app:lintDebug`, `python3 tools/verify_requirement_governance.py`.
