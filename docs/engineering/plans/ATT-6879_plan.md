# Stage 3: Implementation Plan - ATT-6879: Track per-workout RPE & Feel in EditWorkoutScreen, Reorderable Top Section & FIT Parity

**Ticket**: [ATT-6879](https://rainerblind.atlassian.net/browse/ATT-6879)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3` | **Branch**: `feature/ATT-6879`  
**Requirement Mapping**: `REQ-WKO-035`–`REQ-WKO-039` | **Test Mapping**: `TST-WKO-035`–`TST-WKO-039`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
Capture subjective athlete state (Borg CR10 RPE `1..10` and Feel `1..5`) in `EditWorkoutScreen` above `Description`, provide Foster Session-RPE (`sRPE = rpe * (duration_s / 60.0)`) as Tier 4 training load fallback for sensorless workouts, round-trip Garmin FIT session fields 192/193, and expose a reorderable summary/detail section (`RPE_AND_FEEL`, default `OFF`, index `0`).

---

## 2. Traceability & Requirements Mapping
* `REQ-WKO-035` (*DB Persistence & Cursor Mapping*) $\rightarrow$ `TST-WKO-035` (`WorkoutSummariesDatabaseTest`)
* `REQ-WKO-036` (*Section Ordering & Progressive Disclosure*) $\rightarrow$ `TST-WKO-036` (`WorkoutSectionOrderPersistenceTest`, `WorkoutDetailTabConfigTest`)
* `REQ-WKO-037` (*EditWorkoutScreen UI Placement*) $\rightarrow$ `TST-WKO-037` (`EditWorkoutViewModelTest`, `EditWorkoutScreenTest`)
* `REQ-WKO-038` (*Summary & Detail Badge Presentation*) $\rightarrow$ `TST-WKO-038` (`PowerPhysiologyCalculatorTest`, `WorkoutSummarySectionsTest`)
* `REQ-WKO-039` (*FIT Codec Parity, sRPE & 9-Locale L10n*) $\rightarrow$ `TST-WKO-039` (`FitFileWriterTest`, `FitWorkoutParserTest`, `TranslationParityTest`)

---

## 3. System Invariants & Preserved Behavior
1. **Default OFF Invariant**: `showRpeAndFeel = false` ensures zero visual disruption for existing profiles or unrated workouts.
2. **Schema Upgrade Safety**: SQLite `DB_VERSION = 30` uses `addColumnIfNotExists` with null default; zero crash risk.
3. **FIT Protocol Compliance**: Fields 192 (`workout_feel`, 0–100) and 193 (`workout_rpe`, 10x Borg) match official Garmin FIT SDK 21.141.0.
4. **Code Lock & Human Gate**: Production code edits locked until Gate 3; parent ticket terminal completion reserved for human.

---

## 4. Proposed Architectural Changes
* **Persistence Layer**: `WorkoutSummariesDatabaseManager.java` bumped to `DB_VERSION = 30` with `rpe` and `feel` columns; updated `WorkoutData.kt` and `WorkoutDataMapper.kt`.
* **Section Engine**: `WorkoutSectionType.kt` and `WorkoutDetailTabConfig.kt` declare `RPE_AND_FEEL` at `index 0`. `MyPreferenceManager.kt` adds `showRpeAndFeel`.
* **UI Layer**: `EditWorkoutScreen.kt` mounts RPE/Feel above `Description`. `WorkoutRpeFeelSection.kt` renders badges in `WorkoutSummary.kt` and `WorkoutDetailTabbedContent.kt`.
* **Physiology & FIT Codec**: `PowerPhysiologyCalculator.kt` integrates `sRPE` fallback; `FitFileWriter.java` and `FitWorkoutParser.kt` map FIT session fields.
* **UI Consistency (Rule 23)**:
  * Reference Screen: `EditWorkoutScreen.kt`, `PhysiologyModelsSection.kt`.
  * Reused Components: `FilterChip`, `Card`, `MaterialTheme.colorScheme`, `TTColor`.
  * Theme Tokens: `RoundedCornerShape(8.dp)`, 8/12/16dp spacing, standard typography tokens; zero one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Database Migration & Domain Mapping (`REQ-WKO-035`, `TST-WKO-035`)
* Files: `WorkoutSummariesDatabaseManager.java`, `WorkoutData.kt`, `WorkoutDataMapper.kt`, `WorkoutRepository.kt`
* Action: Bump `DB_VERSION = 30`. Add `rpe` (`INTEGER`) and `feel` (`INTEGER`) to `CREATE_TABLE` and `onUpgrade`. Map columns in `WorkoutData` and `WorkoutDataMapper`.

### Step 2: Section Architecture & Settings Wiring (`REQ-WKO-036`, `TST-WKO-036`)
* Files: `WorkoutSectionType.kt`, `WorkoutDetailTabConfig.kt`, `MyPreferenceManager.kt`, `WorkoutMasksAndCardsSection.kt`
* Action: Add `RPE_AND_FEEL` to `WorkoutSectionType.DEFAULT_ORDER` (index 0) and `DEFAULT_TABS[0]`. Add `showRpeAndFeel` (default `false`) to `WorkoutCardSectionPreferences` and wire into settings.

### Step 3: EditWorkoutScreen & Section Composables (`REQ-WKO-037`, `REQ-WKO-038`, `TST-WKO-037`, `TST-WKO-038`)
* Files: `EditWorkoutViewModel.kt`, `EditWorkoutScreen.kt`, `WorkoutRpeFeelSection.kt`, `WorkoutSummary.kt`, `WorkoutDetailTabbedContent.kt`
* Action: Add `updateRpe()`/`updateFeel()` in ViewModel; place RPE slider/chips (`1..10`) & Feel emojis (`1..5`) above `Description` in editor. Render `WorkoutRpeFeelSection` in summary card and details.

### Step 4: FIT Codec & sRPE Training Load Fallback (`REQ-WKO-039`, `TST-WKO-039`)
* Files: `WorkoutExportHeader.kt`, `FitFileWriter.java`, `FitWorkoutParser.kt`, `ParsedWorkoutData.kt`, `PowerPhysiologyCalculator.kt`
* Action: Write/read FIT fields 192/193. Add `sRPE = rpe * (duration_s / 60.0)` in `PowerPhysiologyCalculator.resolveWorkoutTrainingLoad()`.

### Step 5: 9-Language Localization & Parity Audit (`REQ-WKO-039`, `TST-WKO-039`)
* Files: `app/src/main/res/values*/strings_tuning.xml` (or `strings.xml`)
* Action: Add RPE, Feel, and Borg verbal anchor strings across `values`, `de`, `es`, `fr`, `it`, `nl`, `pl`, `pt`, `sv`. Run `TranslationParityTest.kt` and `verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Targeted Verification**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.physiology.*" --tests "com.atrainingtracker.trainingtracker.exporter.*"`
  * `python3 tools/verify_requirement_governance.py`
* **Rollback**: Branch isolation (`feature/ATT-6879`) allows complete `git revert` without impacting `develop` or sprint branch.
