# Stage 2: Requirement & Test Specification - ATT-7515: Discrete Borg CR10 Slider, Canonical Wording & Refined Feel Vector Cards

**Ticket**: [ATT-7515](https://rainerblind.atlassian.net/browse/ATT-7515)  
**Parent Epic**: [ATT-68](https://rainerblind.atlassian.net/browse/ATT-68) (*Workouts & Aftermath*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.5` | **Branch**: `feature/ATT-7515`  
**Requirement Mapping**: `REQ-WKO-037.1`–`REQ-WKO-037.2`, `REQ-WKO-038.1`–`REQ-WKO-038.2`  
**Test Spec ID**: `TST-WKO-037.1`–`TST-WKO-037.2`, `TST-WKO-038.1`–`TST-WKO-038.2`  
**Author**: AI Agent 2 (Flash Agent) | **Date**: 2026-11-13  

---

## 1. Requirement Specification (REQ-WKO-037 & REQ-WKO-038)

### 1.1 Problem Statement & Rationale
In `WorkoutRpeFeelSection.kt`, the initial prototype from ATT-6879 presents RPE 1–10 as fixed 36dp pill buttons inside a horizontal scroll, clipping button 9 in half and hiding button 10 offscreen on standard portrait viewports (360–412dp). Verbal anchors use colloquial phrasing rather than canonical sports-scientific Borg CR10 definitions. Furthermore, Feel 1–5 relies on raw unicode emojis rather than Material 3 vector iconography. The system SHALL upgrade RPE to an interactive discrete slider with edge-to-edge non-clipping buttons, modernize Feel with vector sentiment cards, and align descriptors across 9 locales.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit, Review

### 1.3 Functional & Architectural Requirements
* **REQ-WKO-037.1** (*Discrete Borg CR10 Slider & Edge-to-Edge Steps*): `WorkoutRpeFeelSection.kt` (`RpeFeelEditorControls`) SHALL render an interactive discrete Material 3 `Slider` (range `1f..10f`, `steps = 8`), paired with 10 equal-width edge-to-edge buttons (`Modifier.weight(1f)`, `Arrangement.spacedBy(4.dp)`, no horizontal scroll) and an optional clear action when a rating is active. Slider track and active step SHALL be tinted using `TTColor.rpeColor(score)`.
* **REQ-WKO-037.2** (*Refined Feel (1–5) Tonal Cards & Sentiment Vectors*): `WorkoutRpeFeelSection.kt` (`RpeFeelEditorControls`) SHALL replace raw unicode emojis with 5 equal-width Material 3 tonal cards (`Modifier.weight(1f)`, `RoundedCornerShape(8.dp)`, min height `42.dp`) rendering vector iconography (`Icons.Filled.Sentiment*`) and clearable upon re-tapping the active card.
* **REQ-WKO-038.1** (*Canonical Sports-Scientific Borg CR10 Descriptors*): `strings_rpe_feel.xml` SHALL align verbal descriptors across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PT, SV/PL) to canonical Borg CR10 scientific definitions (1: Sehr leicht, 2: Leicht, 3: Mäßig, 4: Etwas schwer, 5: Schwer, 6: Schwer bis sehr schwer, 7: Sehr schwer, 8: Extrem schwer, 9: Nahezu maximal, 10: Maximal).
* **REQ-WKO-038.2** (*Aligned Feel Descriptors Across 9 Locales*): `strings_rpe_feel.xml` SHALL define subjective workout feel descriptors across all 9 locales without emojis (1: Miserabel / Sehr schwach, 2: Schwerfällig / Mäßig, 3: Normal / Okay, 4: Gut / Stark, 5: Großartig / Im Flow).

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Non-Clipping Edge-to-Edge RPE & Slider)**: *Given* `RpeFeelEditorControls` rendered on a 360–412dp portrait screen, *When* displayed in `EditWorkoutScreen`, *Then* all 10 RPE buttons are visible edge-to-edge without horizontal scroll or right-edge clipping, and dragging the slider updates the score.
* **Criterion 2 (Vector Sentiment Feel Cards)**: *Given* `RpeFeelEditorControls`, *When* viewing Feel, *Then* 5 equal-width tonal cards render vector sentiment icons with localized descriptors, and tapping an active card toggles it off (`null`).
* **Criterion 3 (9-Locale Parity)**: *Given* `TranslationParityTest`, *When* validating `strings_rpe_feel.xml`, *Then* 100% parity across all 9 locales with zero inline emojis.

### 1.5 System Invariants & Scope Bounding
* Nullability (`rpe: Int?`, `feel: Int?`) and numeric range (`1..10`, `1..5`) strictly preserved.
* Foster Session-RPE calculation (`PowerPhysiologyCalculator.calculateSessionRpe`) and Garmin FIT codec parity (fields 192/193) unchanged.

---

## 2. Test Specification (TST-WKO-037 & TST-WKO-038)

### Test Case 1: `testRpeEditor_edgeToEdgeButtonsAndSliderControl` (`TST-WKO-037.1`)
* **Scope**: Composable & Unit Test (`WorkoutRpeFeelTest.kt`)
* **Action**: Verify `RpeFeelEditorControls` renders 10 buttons using `weight(1f)`, discrete `Slider` (steps 8), and clear selection callback.

### Test Case 2: `testFeelEditor_tonalCardsAndVectorIcons` (`TST-WKO-037.2`)
* **Scope**: Composable & Unit Test (`WorkoutRpeFeelTest.kt`)
* **Action**: Verify 5 equal-width tonal cards, vector sentiment drawables, and re-tap clear toggle.

### Test Case 3: `testBorgCr10AndFeelLocalizationParityAcross9Locales` (`TST-WKO-038.1` / `TST-WKO-038.2`)
* **Scope**: Localization Parity Test (`WorkoutRpeFeelTest.kt`, `TranslationParityTest.kt`)
* **Action**: Assert `rpe_anchor_1..10` and `feel_anchor_1..5` across all 9 locales with canonical wording and zero emojis.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-WKO-037.1` | Unit / UI | `WorkoutRpeFeelSection.kt` | `REQ-WKO-037.1` | Specified |
| `TST-WKO-037.2` | Unit / UI | `WorkoutRpeFeelSection.kt` | `REQ-WKO-037.2` | Specified |
| `TST-WKO-038.1` | L10n | `res/values*/strings_rpe_feel.xml` | `REQ-WKO-038.1` | Specified |
| `TST-WKO-038.2` | L10n | `res/values*/strings_rpe_feel.xml` | `REQ-WKO-038.2` | Specified |
