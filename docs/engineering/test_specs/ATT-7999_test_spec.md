# Stage 2 Test Specification: ATT-7999

Target Release: V5.0.0

## Formal Requirement & Test Mapping
- **Requirement Added**: `REQ-SET-107` (`[Planned]`, Priority: `Medium`, Type: `Functional`, Verification Method: `Unit, Integration, On-Device Manual`) in `docs/requirements/req_settings_profiles.md`.
- **Test Case Added**: `TST-SET-109` (`[Planned]`) in `docs/tests/tst_settings_profiles.md`.
- **Governance Check**: `python3 tools/verify_requirement_governance.py` verifies traceability between `REQ-SET-107` and `TST-SET-109`.

## Sub-Requirement Contracts (`REQ-SET-107`)
- `REQ-SET-107.1`: `SportTypeDatabaseManager` SHALL define prepared `TTSportType.SWIM` (`BSportType.UNKNOWN`, `R.string.sport_type_swim`, `gcName = "swim"`, `stravaName = "Swim"`, `tcxName = "Other"`, `runkeeperName = "Swimming"`, `trainingPeaksName = "Swim"`, `minSpeed = 0.0`, `maxSpeed = 0.0`). Fresh `onCreate()` MUST keep strictly the 5 default rows at `DB_VERSION = 3` (referenced directly by `DatabaseSchemaRegistry`).
- `REQ-SET-107.2`: `SportTypeDatabaseManager` & `SportTypesRepository` SHALL provide idempotent `ensureSwimSportTypeExists(): Long` and `isSwimSportTypeActivated(): Boolean`, returning an existing active `stravaName == "Swim"` or `gcName == "swim"` row ID if present, otherwise inserting/activating the localized `SWIM` template.
- `REQ-SET-107.3`: `TuningPreferencesDataStore` SHALL persist `defaultPoolLengthMeters` (`25.0f` default, clamped `10f..100f`) and `defaultPoolLengthUnit` (`PoolLengthUnit.METERS` / `YARDS`); `AdvancedTuningScreen` (`TuningSection.SWIMMING`, `SwimmingSection.kt`) SHALL allow activating Swimming on Save and choosing presets (`25 m`, `50 m`, `25 yd`, or custom meters) adhering strictly to `docs/design_guidelines.md`.
- `REQ-SET-107.4`: `FitFileWriter` SHALL emit `Sport.SWIMMING` when `tcxName` contains `"swim"`, `stravaName` equals `"Swim"` (ignore case), or `gcName` equals `"swim"` (ignore case), and emit `SubSport.LAP_SWIMMING` with `SessionMesg` `pool_length` & `pool_length_unit` (`METRIC`/`STATUTE`) when pool length or indoor context applies.

## Traceability Matrix
| Req Sub-ID | Test Sub-ID | Procedure & Expected Result |
|---|---|---|
| `REQ-SET-107.1` | `TST-SET-109.1` | Fresh `onCreate()` seeds 5 rows (no Swim); `getSportTypesIdList(..., 1.4)` & `0.0` never return Swim ID; `DatabaseSchemaRegistry` uses `DB_VERSION`. |
| `REQ-SET-107.2` | `TST-SET-109.2` | `ensureSwimSportTypeExists()` inserts once and reuses existing `"Swim"`/`"swim"` row across repeated calls. |
| `REQ-SET-107.3` | `TST-SET-109.3`, `TST-SET-109.5` | `TuningConfig` defaults/clamping/reset; 9-locale `TranslationParityTest` & `TuningDesignGuidelinesContractTest`; emulator UI screenshot check. |
| `REQ-SET-107.4` | `TST-SET-109.4` | `FitFileWriterTest` round-trip FIT decode verifies `Sport.SWIMMING`, `SubSport.LAP_SWIMMING`, `pool_length`, and `DisplayMeasure` for `"Swim"`/`"swim"`. |

## Acceptance Criteria (Given-When-Then) & System Invariants
- **Given** fresh install without Swimming enabled, **When** querying `SportType.db`, **Then** only 5 default sport types exist.
- **Given** `ensureSwimSportTypeExists()` runs when a user `"Swim"` row already exists (or repeated calls), **When** queried, **Then** zero duplicate rows are created.
- **Given** `SWIM` row has `minSpeed = 0.0, maxSpeed = 0.0`, **When** resolving land walk `1.4 m/s`, **Then** `WALK` resolves and never `SWIM`.
- **Given** Swim workout (`tcxName = "Other"`, `stravaName = "Swim"`) with `25 m` / `25 yd` config, **When** FIT-exported, **Then** binary decode shows `Sport.SWIMMING`, `SubSport.LAP_SWIMMING`, `pool_length = 25.0` (`22.86`), and `METRIC` (`STATUTE`).
- **9-Locale Localization**: All new strings (`sport_type_swim`, `tuning_cat_swimming`, pool presets, activation toggle/summary) must exist in all 9 `values*/strings.xml` without emojis.
