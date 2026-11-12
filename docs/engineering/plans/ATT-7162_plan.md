# Stage 3: Implementation Plan - ATT-7162: Configurable FIETS Index & Strava/Garmin Formulas

**Ticket**: [ATT-7162](https://rainerblind.atlassian.net/browse/ATT-7162)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3`  
**Requirement Mapping**: `REQ-CLM-010` (`REQ-CLM-010.1`..`REQ-CLM-010.3`)  
**Test Mapping**: `TST-CLM-010` (`TST-CLM-010.1`..`TST-CLM-010.5`)  
**Branch**: `feature/ATT-7162` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-12  

---

## 1. Problem Description & Background
Strava/Garmin climb scoring ($D \times \text{grade\%} = 100 \times H$) cancels distance entirely, ignoring gradient difficulty: a steep 1.3 km @ 9.6% wall or a 15% ramp receives the same category as a 3% false-flat. The FIETS Index ($\frac{H^2}{10 \times D} + \max(0, \frac{\text{summitAlt} - 1000}{1000})$) properly weights vertical ascent, gradient, and high-altitude difficulty. The system will support FIETS as the default and Strava/Garmin as a selectable alternative in Advanced Tuning.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-CLM-010` (*Configurable FIETS Index & Strava/Garmin Climb Categorization*)
* **Test Mapping**: `TST-CLM-010` (`TST-CLM-010.1`..`TST-CLM-010.5`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Database Schema Changes**: No SQLite table alters in `Climbs.db`; existing persisted rows remain intact.
2. **Dynamic Presentation Resolution**: Categories update immediately at render time without re-importing routes or workouts.
3. **Chesterton's Fence Parity**: Strava/Garmin formula is preserved bit-exact for users choosing 1:1 Garmin Edge / Strava parity.
4. **100% Localization Parity**: All new UI strings localized across all 9 supported locales without untranslated English fallbacks.

---

## 4. UI Consistency & Theme Tokens (Rule 23)
* **Reference UI**: `AftermathAnalysisSection.kt` (lines 110–121) FilterChip pair for X-axis domain selection.
* **Component Reused**: Material 3 `FilterChip` within `NavigationSection.kt` under Navigation tuning.
* **Theme Tokens**: Standard `MaterialTheme.typography` (`titleSmall`, `bodySmall`) and `colorScheme.onSurfaceVariant`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Domain Models & Formulas in `Climb.kt` & `ClimbDetector.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/climbs/Climb.kt`, `ClimbDetector.kt`
* Changes: Add `enum class ClimbScoringFormula { FIETS, STRAVA_GARMIN }`. Add `calculateFietsIndex()`, `fromFietsIndex()` with open thresholds (`6.5 / 5.0 / 3.5 / 2.0 / 0.5`), and `Climb.resolveCategory(formula)`. Update `ClimbDetector.createClimb()` to use `FIETS` scoring by default.

### Step 2: Preferences in `TuningConfig` & `TuningPreferencesDataStore.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`
* Changes: Add `KEY_CLIMB_SCORING_FORMULA`, `DEFAULT_CLIMB_SCORING_FORMULA = ClimbScoringFormula.FIETS`, flow mapping in `TuningConfig`, `updateClimbScoringFormula()`, and reset in `resetToDefaults()`.

### Step 3: UI Selector in `NavigationSection.kt`, `AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/NavigationSection.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`
* Changes: Expose `climbScoringFormula` / `onClimbScoringFormulaChange` with two `FilterChip` options (`FIETS Index` and `Strava / Garmin`) with descriptive helper text.

### Step 4: Dynamic Presentation Categorization Wiring
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheet.kt`, `RouteOnMapScreen.kt`, `ElevationProfile.kt`, `ClimbOnMapScreen.kt`
* Changes: Pass or resolve `climb.resolveCategory(activeFormula)` dynamically for `ClimbCategoryChip` and category badges.

### Step 5: 9-Language Localization Shard in `strings_climbs.xml`
* Files: `app/src/main/res/values*/strings_climbs.xml` across `values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`.
* Changes: Define `tuning_climb_formula_title`, `tuning_climb_formula_desc`, `tuning_climb_formula_fiets`, `tuning_climb_formula_strava_garmin`.

### Step 6: Unit Tests & Static Verification
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/climbs/ClimbCategorizationFormulaTest.kt`, `TuningPreferencesDataStoreTest.kt`
* Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.climbs.*" --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit test suite + `python3 tools/verify_requirement_governance.py`.
* **Rollback**: Revert working branch files via `git checkout -- .` and `git clean -fd`.
