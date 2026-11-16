# Stage 3: Implementation Plan - ATT-8001: Swim Mode Backfill & Aftermath Adjustment

**Ticket**: [ATT-8001](https://rainerblind.atlassian.net/browse/ATT-8001)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`  
**Requirement Mapping**: `REQ-SET-111` (`REQ-SET-111.1`..`REQ-SET-111.4`)  
**Test Mapping**: `TST-SET-113` (`TST-SET-113.1`..`TST-SET-113.5`)  
**Risk / Stage 4 Classification**: `VISUAL/UI` (`subagent_pro` executes Stage 4 Doing + 4-point `read_file` screenshot verification per Rule 7.1 & 8.5)  

---

## 1. Problem Description & Traceability
ATT-8000 successfully reads White Swimming Mode sessions and extracts 1-Hz HR and turn timestamps.
ATT-8001 connects these turns to the app's configured pool length (`TuningPreferencesDataStore`), assigns the Swimming sport type (`ensureSwimSportTypeExists()`), backfills distance, speed, and lapNr samples into `WorkoutSamples.db`, writes discrete laps into `Laps.db` (`ATT-896` guard), updates summaries before auto-export, and provides an Aftermath `SwimWorkoutAdjustCard` for pool-length and `+1`/`-1` length corrections across all 9 UI locales.

---

## 2. Invariants & UI Consistency (docs/design_guidelines.md)
* **Invariants**: Zero `WorkoutRr.db` writes; zero `WorkoutSummaries.db` schema bumps; hold auto-exports until commit; zero emojis.
* **UI Consistency**: `SwimWorkoutAdjustCard` in Workout Details uses M3 `ElevatedCard`, `SingleChoiceSegmentedButtonRow` with `icon = {}` for 25m/50m/25yd presets, `FilledTonalIconButton` for `+`/`-` length adjustments, and single-line localized labels.

---

## 3. Step-by-Step Implementation Sequence

### Step 1: Swim SportType Assignment & Pool-Length Math in Controller
* Files: `PolarVeritySenseOfflineController.kt`, `WorkoutSamplesDatabaseManager.java`, `LapsDatabaseManager.java`
* In `applySwimBackfillAndCommit()`:
  * Invoke `SportTypesRepository.ensureSwimSportTypeExists()` and assign `sportId` + `BSportType.SWIMMING` to workout.
  * Retrieve `defaultPoolLengthMeters` (default 25.0m) and compute total distance from `(turnTimestampsMs.size + 1) * poolLengthM`.
  * Backfill 1-Hz cumulative `DISTANCE_m`, `SPEED_mps`, and `LAP_NR` into `WorkoutSamples.db`.
  * Insert discrete length records into `LapsDatabaseManager.saveLap()` (enforcing non-zero duration/distance).
  * Update `distanceTotal_m`, `laps`, and `speedAverage_mps` in `WorkoutSummariesDatabaseManager`.

### Step 2: Aftermath Swim Adjustment Card & Recalculation Engine
* Files: `SwimWorkoutAdjustCard.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutRepository.kt`
* Implement `SwimWorkoutAdjustCard` displaying current pool length and length count with `+1`/`-1` controls.
* Mount card inside `WorkoutDetailTabbedContent.kt` when workout sport is Swimming.
* Wire recalculation in `WorkoutRepository` to re-save laps, patch samples, update summaries, and mark re-export needed.

### Step 3: 9-Language Localization (`strings_swimming.xml`)
* Files: `res/values*/strings_swimming.xml` (all 9 locales: `en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`)
* Add keys: `swim_adjust_card_title`, `swim_adjust_pool_length_label`, `swim_adjust_lengths_label`, `swim_adjust_recalculating` without emojis.

### Step 4: Tier-1 Targeted Unit Tests & On-Device Visual Inspection
* Run targeted tests:
  `./gradlew :app:testDebugUnitTest --tests "*PolarVeritySenseOfflineControllerTest" --tests "*TranslationParityTest"`
* Capture PNG on `emulator-5554` to `docs/engineering/walkthroughs/ATT-8001_swim_adjust_card.png` and verify 4-point visual checklist via `read_file`.
