# Stage 2: Requirement & Test Specification - ATT-7162: Configurable FIETS Index & Strava/Garmin Climb Categorization

**Ticket**: [ATT-7162](https://rainerblind.atlassian.net/browse/ATT-7162)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3`  
**Requirement Mapping**: `REQ-CLM-010` (*Configurable FIETS Index & Strava/Garmin Climb Categorization*)  
**Test Spec ID**: `TST-CLM-010` | **Branch**: `feature/ATT-7162`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-CLM-010)

### 1.1 Problem Statement & Rationale
Strava/Garmin score ($\text{dist} \times \text{grade\%} = 100 \times H$) cancels distance entirely, ignoring gradient difficulty: a steep 1.3 km @ 9.6% wall scores the same as a gentle 3% false-flat. The FIETS Index ($H^2 / (10 \times D) + \max(0, (\text{summitAlt} - 1000)/1000)$) accurately weights elevation gain, gradient, and high-altitude difficulty. The system SHALL support FIETS as the default and Strava/Garmin as a selectable alternative in Advanced Tuning, dynamically updating displayed categories without database rebuild.

### 1.2 Functional & Architectural Requirements
1. **FIETS & Strava/Garmin Formula Calculations (`REQ-CLM-010.1`)**:
   - The system SHALL introduce `enum class ClimbScoringFormula { FIETS, STRAVA_GARMIN }` with default `FIETS`.
   - `ClimbCategory.fromFietsIndex(distanceMeters, elevationGainMeters, summitAltitudeMeters)` SHALL compute base score as $(H^2)/(10 \times D)$ and altitude bonus as $\max(0, (\text{summitAlt} - 1000)/1000)$, applying open thresholds: `HC >= 6.5`, `CAT_1 >= 5.0`, `CAT_2 >= 3.5`, `CAT_3 >= 2.0`, `CAT_4 >= 0.5`, `UNCATEGORIZED < 0.5`.
   - `ClimbCategory.fromScore(score)` SHALL retain Strava/Garmin thresholds (`HC >= 80000`, `CAT_1 >= 64000`, `CAT_2 >= 32000`, `CAT_3 >= 16000`, `CAT_4 >= 8000`).
2. **Dynamic Presentation Category Resolution (`REQ-CLM-010.2`)**:
   - `Climb.resolveCategory(formula: ClimbScoringFormula)` SHALL dynamically evaluate category from distance, elevation gain, and summit altitude without modifying persisted schema or requiring DB rebuilds.
3. **Preferences & 9-Language Localization Parity (`REQ-CLM-010.3`)**:
   - `TuningPreferencesDataStore` SHALL persist `climbScoringFormula` (default `FIETS`) with reactive `StateFlow` and reset support.
   - `NavigationSection.kt` SHALL expose a FilterChip selector with full 9-language localization parity (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-MAP-027` (*Climb Detection*) & `REQ-UI-281` (*Navigation Tuning*).
* **Historical Origin**: Commit `c7438e78` (`ATT-2565`).
* **Root Reason for Existing Formulation**: Strava/Garmin was originally adopted for 1:1 parity with Garmin Edge devices.
* **Preservation of Core Invariants**: Strava/Garmin formula is preserved as a user-selectable option; detection geometry and DB schema remain untouched.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* a 1 km climb @ 15% (150m gain, summit 500m), *When* evaluated under FIETS, *Then* score is 2.25 and category is `CAT_3` (versus `CAT_4` under Strava).
* *Given* a 15 km pass with 1,000m gain (summit 2,200m), *When* evaluated under FIETS, *Then* score is $6.67 + 1.20 = 7.87$ (`HC`).
* *Given* existing climbs in `Climbs.db`, *When* `climbScoringFormula` is toggled in Settings, *Then* UI badges update immediately without DB re-ingestion.

---

## 2. Test Specification (TST-CLM-010)

### Test Case 1: FIETS Index & Altitude Bonus Calculation (`TST-CLM-010.1`)
* **Scope**: JVM Unit Test (`ClimbCategorizationFormulaTest.kt`)
* **Assertion**: Base formula, high-altitude bonus, and category boundaries match open FIETS standards.

### Test Case 2: Strava/Garmin Formula Parity (`TST-CLM-010.2`)
* **Scope**: JVM Unit Test (`ClimbCategorizationFormulaTest.kt`)
* **Assertion**: Existing Strava/Garmin scoring and categories remain 100% bit-exact.

### Test Case 3: Dynamic Category Resolution Without DB Rebuild (`TST-CLM-010.3`)
* **Scope**: JVM Unit Test (`ClimbCategorizationFormulaTest.kt`)
* **Assertion**: `Climb.resolveCategory()` computes the active category dynamically with/without summit altitude.

### Test Case 4: Preferences Persistence & Reset (`TST-CLM-010.4`)
* **Scope**: JVM Unit Test (`TuningPreferencesDataStoreTest.kt`)
* **Assertion**: Formula defaults to `FIETS`, persists `STRAVA_GARMIN`, and resets to `FIETS`.

### Test Case 5: 9-Language Localization Parity (`TST-CLM-010.5`)
* **Scope**: Static Resource Audit
* **Assertion**: Zero missing string keys across all 9 locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
