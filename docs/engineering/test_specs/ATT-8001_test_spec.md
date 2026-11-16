# Stage 2: Requirement & Test Specification - ATT-8001: Swim Mode Backfill & Aftermath Adjustment

**Ticket**: [ATT-8001](https://rainerblind.atlassian.net/browse/ATT-8001)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.8`  
**Requirement Mapping**: `REQ-SET-111` (*Polar Verity Sense Swim Mode Pool-Length Distance/Laps Backfill, Auto SportType & Aftermath Adjustment*)  
**Test Spec ID**: `TST-SET-113`  
**Branch**: `feature/ATT-8001`  
**Author**: AI Agent 2 (Specification / Subagent Flash)  
**Date**: 2026-11-16  

---

## 1. Requirement Specification (REQ-SET-111 [Planned])
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit / Integration
- **REQ-SET-111.1 (Automatic Swim SportType Provisioning & Assignment)**: When a White Swimming Mode session is backfilled, the controller SHALL call `SportTypesRepository.getInstance(context).ensureSwimSportTypeExists()` and assign the Swimming sport ID and `BSportType.SWIMMING` to the workout via `WorkoutSummariesDatabaseManager` and `WorkoutRepository.updateWorkoutSport()`, overriding speed-based heuristic fallbacks (`WALK`/`OTHER`).
- **REQ-SET-111.2 (Pool-Length Distance, Speed & Lap Backfill)**: The controller SHALL read configured pool length via `SettingsDataStoreJavaHelper.getDefaultPoolLengthMeters(context)`. For each length interval, cumulative `SensorType.DISTANCE_m`, segment `SensorType.SPEED_mps`, and `SensorType.LAP_NR` SHALL be backfilled into `WorkoutSamples.db` alongside 1-Hz `SensorType.HR`. Discrete laps SHALL be persisted into `Laps.db` via `LapsDatabaseManager.saveLap()` (enforcing the `ATT-896` non-zero lap duration/distance guard). Summary distance, speed, and HR zones SHALL be recomputed before releasing held automatic exports (`ExportManager`). `WorkoutRr.db` MUST NOT receive any optical PPG rows.
- **REQ-SET-111.3 (Aftermath Pool-Length & Length-Count Adjustment)**: Workout Details (Aftermath) SHALL render `SwimWorkoutAdjustCard` in `WorkoutDetailTabbedContent.kt` allowing swimmers to select standard pool lengths (`25 m`, `50 m`, `25 yd`, custom) or adjust total length count (`+1` / `-1`). Recalculations SHALL update SQLite distance, pace, lap splits, and sample streams, refresh `WorkoutRepository`, and trigger re-export.
- **REQ-SET-111.4 (9-Locale Localization Parity & Invariants)**: User-facing strings for `SwimWorkoutAdjustCard` SHALL be localized across all 9 locales with zero inline emojis.

### Acceptance Criteria & System Invariants
- **Given-When-Then**:
  1. *Given* Swimming is inactive in `SportType.db`, *When* a Verity Sense swim session is synced, *Then* `ensureSwimSportTypeExists()` activates Swimming and assigns it to the workout, avoiding Walking misclassification.
  2. *Given* a 50m pool length and 20 turn intervals, *When* stop backfill completes, *Then* 1000m total distance, 20 distinct laps, and 1-Hz HR are recorded in SQLite before automatic exports run, with zero writes to `WorkoutRr.db`.
  3. *Given* a synced swim workout in Aftermath, *When* pool length is switched or length count adjusted (`+1`), *Then* total distance, pace, and laps recalculate immediately in SQLite and refresh in UI across all 9 locales.
- **System Invariants**: Zero `WorkoutRr.db` writes; zero `WorkoutSummaries.db` schema bumps; automatic exports held until backfill commits; idempotent `ensureSwimSportTypeExists()`; zero emojis in strings.

---

## 2. 9-Locale Localization Parity Plan (`strings_swimming.xml`)
New keys across `en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt` (zero emojis, literal strings):
`swim_adjust_card_title`, `swim_adjust_pool_length_label`, `swim_adjust_lengths_label`, `swim_adjust_recalculating`.

---

## 3. Test Specification & Traceability (TST-SET-113 [Planned])

| Test ID | Scope | Target Component / Procedure | Traces To | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-113.1` | Unit | `PolarVeritySenseOfflineControllerTest`: verify `ensureSwimSportTypeExists()` resolves Swim sport type and assigns `BSportType.SWIMMING` and its `sportId` to workout. | `REQ-SET-111.1` | Specified |
| `TST-SET-113.2` | Unit | `PolarVeritySenseOfflineControllerTest`, `WorkoutSamplesHrBackfillTest`: verify backfill writes `DISTANCE_m`, `SPEED_mps`, `LAP_NR` samples and discrete laps via `LapsDatabaseManager.saveLap()` (honoring ATT-896). | `REQ-SET-111.2` | Specified |
| `TST-SET-113.3` | Unit | `PolarVeritySenseOfflineControllerTest`: verify summary total distance, speed extrema, and HR zones recompute before `ExportManager.workoutFinished()` runs; zero `WorkoutRr` writes. | `REQ-SET-111.2` | Specified |
| `TST-SET-113.4` | Unit & UI | `SwimWorkoutAdjustCardTest`: verify changing pool length or length count (`+1`/`-1`) recalculates total distance, pace, and laps in SQLite, refreshes `WorkoutRepository`, and triggers export invalidation. | `REQ-SET-111.3` | Specified |
| `TST-SET-113.5` | Localization & Visual | `TranslationParityTest`, Screenshot: verify 100% 9-locale parity with zero missing keys and zero emojis; UI layout verified on `emulator-5554` (Rule 8.5). | `REQ-SET-111.4`, `REQ-UI-106` | Specified |
