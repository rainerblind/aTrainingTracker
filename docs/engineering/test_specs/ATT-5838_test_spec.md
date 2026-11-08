# Stage 2: Requirement & Test Specification - ATT-5838: EditWorkoutScreen: Localize hints, normalize 'Sport'/'Zu Strava hochladen' labels, and fix Strava checkbox layout & disabled alpha

**Ticket**: [ATT-5838](https://rainerblind.atlassian.net/browse/ATT-5838)  
**Parent Epic**: [ATT-5909](https://rainerblind.atlassian.net/browse/ATT-5909) (*Localization & Formatting Parity (i18n)*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5838`  
**Requirement Mapping**: `REQ-UI-497` (`REQ-UI-497.1`–`REQ-UI-497.3`) | **Test Spec ID**: `TST-UI-497` (`TST-UI-497.1`–`TST-UI-497.4`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-08  

---

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Refines `REQ-LOC-001` (*9-Language Localization Parity*), `REQ-UI-122` (*Translation Coverage*), and `REQ-UI-239` (*Workout Metadata Presentation*).
* `Historical Origin & Commit Trace`: Introduced in `ATT-2005` (metadata inputs) and updated in `ATT-4801` (Strava upload integration).
* `Root Reason for Existing Formulation`: Lowercase hints were developer placeholders; `Sport:` colon was inherited from legacy pre-Compose layouts; `tint = Color.Gray` was used naively without accounting for multi-color bitmap masking.
* `Preservation of Core Invariants`: Strava upload state binding (`uploadToStrava`), enablement guard (`stravaMappingAvailable`), and dropdown functionality across all screens consuming `R.string.Sport` MUST be preserved.

---

## 1. Requirement Specification (`REQ-UI-497`)

### 1.1 Problem Statement & Rationale
Visual UI audits of the Edit Workout dialog (`audit_04_einheiten.png`) identified untranslated English placeholder hints in German (`description`, `goal`, `method`), an inconsistent trailing colon on `R.string.Sport`, uncapitalized `stravaUpload` ("hochladen zu Strava"), and a Strava row layout bug where the logo placed before the checkbox masks into a solid grey box (`■`) when disabled and misaligns the checkbox with `Commute` and `Race`.

### 1.2 Functional & Architectural Requirements
1. **German Workout Hint Localization & Capitalization (`REQ-UI-497.1`)**: `hint_workout_description`, `hint_workout_goal`, and `hint_workout_method` SHALL be localized in `values-de` as `Beschreibung`, `Ziel`, `Methode`, and capitalized across all 9 locales.
2. **Sport Label Colon Removal & Strava Upload Capitalization (`REQ-UI-497.2`)**: `R.string.Sport` SHALL NOT contain trailing colons across all 9 locales. `R.string.stravaUpload` SHALL be capitalized across all 9 locales.
3. **Strava Row Ordering & Disabled Alpha (`REQ-UI-497.3`)**: The Strava row in `EditWorkoutScreen.kt` SHALL place `Checkbox` first (left-aligned with `commute` and `race`), followed by `Icon(R.drawable.logo_square_strava)` (`size = 18.dp`, `tint = Color.Unspecified`, `Modifier.alpha(if (stravaMappingAvailable) 1f else 0.38f)`), `Spacer(8.dp)`, and label `Text`.

### 1.3 9-Language Localization Parity Matrix

| Locale | `hint_workout_description` | `hint_workout_goal` | `hint_workout_method` | `Sport` | `stravaUpload` |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `values` (EN) | `Description` | `Goal` | `Method` | `Sport` | `Upload to Strava` |
| `values-de` (DE) | `Beschreibung` | `Ziel` | `Methode` | `Sport` | `Zu Strava hochladen` |
| `values-es` (ES) | `Descripción` | `Objetivo` | `Método` | `Deporte` | `Subir a Strava` |
| `values-fr` (FR) | `Description` | `Objectif` | `Méthode` | `Sport` | `Téléverser sur Strava` |
| `values-it` (IT) | `Descrizione` | `Obiettivo` | `Metodo` | `Sport` | `Carica su Strava` |
| `values-ja` (JA) | `説明` | `目標` | `方法` | `スポーツ` | `Stravaへアップロード` |
| `values-nl` (NL) | `Beschrijving` | `Doel` | `Methode` | `Sport` | `Naar Strava uploaden` |
| `values-pl` (PL) | `Opis` | `Cel` | `Metoda` | `Sport` | `Prześlij do Strava` |
| `values-pt` (PT) | `Descrição` | `Objetivo` | `Método` | `Desporto` | `Carregar para Strava` |

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (Hint Localization & Capitalization)**: *Given* Edit Workout in German, *When* metadata inputs render, *Then* hints display `Beschreibung`, `Ziel`, `Methode`.
* **AC-2 (Punctuation & Capitalization)**: *Given* `R.string.Sport` and `R.string.stravaUpload` across all 9 locales, *When* rendered, *Then* `Sport` has no trailing colon and `stravaUpload` starts with a capital letter.
* **AC-3 (Strava Row Alignment & Alpha)**: *Given* Edit Workout, *When* rendered, *Then* `Checkbox` is left-aligned with `Commute`/`Race` checkboxes, and disabled logo retains white chevron at `0.38f` alpha (zero solid grey `■` boxes).

---

## 2. Test Specification (`TST-UI-497`) & Traceability Matrix

| Test Case | Scope | Target Class / File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-497.1` | 9-Locale Hint, Sport & Strava Upload String Parity | `EditWorkoutStringsParityContractTest.kt` | `REQ-UI-497.1`, `REQ-UI-497.2` | Specified |
| `TST-UI-497.2` | Strava Row Layout Ordering & Disabled Alpha Contract | `EditWorkoutLayoutContractTest.kt` | `REQ-UI-497.3` | Specified |
| `TST-UI-497.3` | 9-Language Localization & Translation Parity | `TranslationParityTest.kt` | `REQ-LOC-001` | Specified |
| `TST-UI-497.4` | Tier 1 Targeted Unit Test Regression | `ui.aftermath.editworkout.*`, `ui.theme.*` | `REQ-PRO-001` | Specified |
