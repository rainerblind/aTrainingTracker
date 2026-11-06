# Stage 2: Requirement & Test Specification - ATT-5563: Steigungsverteilung

**Ticket**: [ATT-5563](https://rainerblind.atlassian.net/browse/ATT-5563)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5563`  
**Requirement Mapping**: `REQ-UI-453` (*Grade / Slope Distribution Card & 1% Telemetry Histogram*)  
**Test Spec ID**: `TST-UI-453` | **Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-UI-453`)

### 1.1 Problem Statement & Rationale
Refine `GradeZoneDistributionCard` post ATT-5487: (1) add independent visibility toggles in Expert Settings (default: `true` in Details, `false` in Summary); (2) rename terminology from "Zonen" to *Steigungsverteilung* / *Slope Distribution*; (3) replace `Z1`..`Z7` with mathematical thresholds `<2%`, `≥2%`, `≥5%`, `≥10%`, `≥15%`, `≥20%`, `≥25%`.

### 1.2 Functional & Architectural Requirements
1. **Threshold Labels (`REQ-UI-453.1`)**: Column Chart and Stacked Bar SHALL display `<2%`, `≥2%`, `≥5%`, `≥10%`, `≥15%`, `≥20%`, `≥25%` for slope categories via `ZoneTimeEntry.badgeLabel`. HR and Power zone cards SHALL strictly retain `Z1`..`Z5`/`Z7`.
2. **Nomenclature (`REQ-UI-453.3`)**: Title SHALL use localized string `aftermath_grade_zones_title` (*Steigungsverteilung* / *Slope Distribution*) across 9 locales without the term "Zonen".
3. **Visibility Toggles (`REQ-UI-453.4`)**: Add `showGradeDistribution` to `WorkoutDetailPreferences` (default: `true`) and `WorkoutCardSectionPreferences` (default: `false`). Expose subordinate toggle `SubordinateTuningToggleItem` under Zone Analysis in `WorkoutMasksAndCardsSection.kt`.
4. **Zonen Tab Guard (`REQ-UI-453.4`)**: In `TrackOnMapScreen.kt`, `hasZonesData` SHALL include `|| (gradeZoneDistribution != null && activeDetailPrefs.showGradeDistribution)`.
5. **Localization Parity (`REQ-UI-453.5`)**: Localize `aftermath_grade_zones_title` and `settings_workout_card_grade_distribution` across all 9 locales.

### 1.3. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-453` (Grade / Slope Distribution Card). Target: `ui/aftermath/zones/`, `WorkoutMasksAndCardsSection.kt`, `MyPreferenceManager.kt`.
* **Historical Origin & Commit Trace**: Sprint 2026-45.7 (`ATT-5487`, commit `36e00b64ef28827de43f2f04399de442ca9a6898`).
* **Root Reason for Existing Formulation**: Reused generic `ZoneDistributionData` and `Z1`..`Z7` strings to establish initial integration without adding preference schema overhead.
* **Preservation of Core Invariants**: Retains canonical 7-zone color palette (`TTColor.Zone1..Zone7`), preserves HR/Power zone badges (`Z1`..`Z5`/`Z7`), preserves `WorkoutMasksAndCardsSection.kt` < 400 lines constraint, and ensures DataStore backward compatibility via safe defaults (`true` in Details, `false` in Summary).

### 1.4 Acceptance Criteria & Invariants
* *Given* a workout with grade telemetry, *When* Details is opened with `showGradeDistribution = true`, *Then* `GradeZoneDistributionCard` displays with title "Steigungsverteilung" (DE) / "Slope Distribution" (EN) and threshold labels `<2%`, `≥2%`, `≥5%`, `≥10%`, `≥15%`, `≥20%`, `≥25%`.
* *Given* Workout Summary, *When* `showGradeDistribution` is `false`, *Then* the card is omitted; when `true`, it is rendered.
* *Invariants*: `WorkoutMasksAndCardsSection.kt` strictly < 400 lines (`TST-UI-377.5`), zero DB schema changes, HR/Power `Z1`..`Z7` preserved.

---

## 2. Test Specification (`TST-UI-453`)

### Test Case 1: Label Decoupling & Threshold Badges (`TST-UI-453.3`)
* **Target**: `GradeZoneDistributionCardTest.kt`, `ZoneDistributionColumnChartTest.kt`
* **Assert**: Slope entries have badges `["<2%", "≥2%", "≥5%", "≥10%", "≥15%", "≥20%", "≥25%"]`. HR/Power entries have badges `["Z1", "Z2", ...]`.

### Test Case 2: Visibility Preferences & Subordinate Switch (`TST-UI-453.4`)
* **Target**: `WorkoutDetailPreferencesTest.kt`, `WorkoutMasksAndCardsLayoutTest.kt`
* **Assert**: `WorkoutDetailPreferences().showGradeDistribution == true`, `WorkoutCardSectionPreferences().showGradeDistribution == false`. `WorkoutMasksAndCardsSection.kt` file line count strictly < 400 lines.

### Test Case 3: 9-Language Localization Parity (`TST-UI-453.5`)
* **Target**: `TranslationParityTest.kt` asserting 100% parity across all 9 locales:

| Locale | `aftermath_grade_zones_title` | `settings_workout_card_grade_distribution` |
| :--- | :--- | :--- |
| `values` (EN) | `Slope Distribution` | `Slope Distribution` |
| `values-de` (DE) | `Steigungsverteilung` | `Steigungsverteilung` |
| `values-es` (ES) | `Distribución de pendiente` | `Distribución de pendiente` |
| `values-fr` (FR) | `Distribution des pentes` | `Distribution des pentes` |
| `values-it` (IT) | `Distribuzione della pendenza` | `Distribuzione della pendenza` |
| `values-ja` (JA) | `勾配分布` | `勾配分布` |
| `values-nl` (NL) | `Hellingverdeling` | `Hellingverdeling` |
| `values-pl` (PL) | `Rozkład nachylenia` | `Rozkład nachylenia` |
| `values-pt` (PT) | `Distribuição de inclinação` | `Distribuição de inclinação` |

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-453.1` | Unit | `ZoneDistributionCalculator.calculateGradeDistribution` | `REQ-UI-453.1`, `REQ-UI-453.2` | Specified |
| `TST-UI-453.3` | Compose/UI | `GradeZoneDistributionCard`, `ZoneDistributionColumnChart` | `REQ-UI-453.1`, `REQ-UI-453.3` | Specified |
| `TST-UI-453.4` | Contract | `WorkoutDetailPreferencesTest`, `WorkoutMasksAndCardsLayoutTest` | `REQ-UI-453.4` | Specified |
| `TST-UI-453.5` | Localization | `TranslationParityTest` | `REQ-UI-453.5`, `REQ-LOC-001` | Specified |
| `TST-UI-453.6` | Regression | `./gradlew testDebugUnitTest` (Tier 1 targeted) | `REQ-PRO-001` | Specified |
