# Stage 1 Analysis: ATT-5838 - EditWorkoutScreen: Localize 'description/goal/method' hints, normalize 'Sport'/'Zu Strava hochladen' labels, and fix Strava checkbox icon layout & disabled alpha

**Ticket**: [ATT-5838](https://rainerblind.atlassian.net/browse/ATT-5838)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-5909](https://rainerblind.atlassian.net/browse/ATT-5909) (*Localization & Formatting Parity (i18n)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-5838`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
Visual UI audits of the **Edit Workout** dialog (`EditWorkoutScreen.kt`, `audit_04_einheiten.png`) identified three localization, layout, and visual defects:
1. **Untranslated & Lowercase Text Field Labels**: `hint_workout_description`, `hint_workout_goal`, and `hint_workout_method` remain untranslated lowercase English (`"description"`, `"goal"`, `"method"`) in `values-de/strings.xml:852-854` and lowercase across other locales, clashing with Title Case German fields (`Name der Einheit`, `Lieblingsstrecke`, `Ausrüstung`).
2. **Inconsistent Punctuation & Lowercase Checkbox Label**: `R.string.Sport` has a trailing colon across all 9 locales (`"Sport:"`, `"Deporte:"`, `"Sport :"`), whereas sibling fields have no colon. `R.string.stravaUpload` is lowercase (`"hochladen zu Strava"`, `"upload to Strava"`), contrasting with capitalized siblings (`Pendeln`, `Indoor Trainer`, `Wettkampf`).
3. **Strava Logo Rendered as Solid Grey Box (`■`) & Checkbox Misalignment**: In `EditWorkoutScreen.kt:307-323`, the Strava logo is placed to the *left* of the `Checkbox`. When disabled (`!stravaMappingAvailable`), `tint = Color.Gray` overwrites all pixels (including the white chevron), turning the logo into a solid grey square (`■`) resembling a phantom second checkbox (`■ ☐ hochladen zu Strava`) while shifting the real `Checkbox` out of vertical alignment with `☐ Pendeln` and `☐ Wettkampf`.

---

## 2. Root Cause Analysis & Visual Inspection
1. **Strings Inconsistencies (`res/values*/strings.xml`)**:
   - `values-de` inherited raw developer placeholders (`description`, `goal`, `method`) without German localization (`Beschreibung`, `Ziel`, `Methode`).
   - `Sport` was defined during legacy layout eras with a hardcoded colon, whereas Material3 `OutlinedTextField` and `DropdownSelector` render floating container labels without trailing punctuation.
   - `stravaUpload` was authored in lowercase infinitive without sentence-case capitalization.
2. **Strava Checkbox Ordering & Tint Bug (`EditWorkoutScreen.kt:307-318`)**:
   - `Icon(painterResource(R.drawable.logo_square_strava))` precedes `Checkbox(...)`.
   - `R.drawable.logo_square_strava` is a square raster bitmap with orange background and white chevron. Applying `tint = Color.Gray` invokes `BlendMode.SrcIn`, turning the entire bounding square solid grey.
   - Audited via `read_file` on `audit_04_einheiten.png` (`Rule 71`): Confirmed the phantom `■` box, checkbox misalignment, trailing colon on `Sport:`, and lowercase `description`/`goal` labels.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  - Localize `hint_workout_description` (`Beschreibung`), `hint_workout_goal` (`Ziel`), and `hint_workout_method` (`Methode`) in German and capitalize initial letters across all 9 locales.
  - Remove trailing colon from `R.string.Sport` across all 9 locales (`"Sport"`, `"Deporte"`, etc.).
  - Capitalize `R.string.stravaUpload` across all 9 locales (German: `"Zu Strava hochladen"`, English: `"Upload to Strava"`).
  - Reorder the Strava row in `EditWorkoutScreen.kt`: `Checkbox` first (left-aligned with `Pendeln`/`Wettkampf`), followed by Strava logo (`18.dp`, `tint = Color.Unspecified`), `Spacer(8.dp)`, and label `Text`.
  - Dim disabled Strava logo using `Modifier.alpha(0.38f)` instead of `Color.Gray`, preserving the white chevron.
* **Out-of-Scope Non-Goals**:
  - Modifying Strava OAuth, API synchronization, or upload payload logic.
  - Changing other `EditWorkoutScreen` fields or database schema.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Historical Trace & Prior Tickets**:
  - `REQ-LOC-001` & `REQ-UI-122`: 9-language localization parity and translation coverage.
  - `REQ-UI-239` (`ATT-2005`): Unconditional display of workout metadata fields.
  - `ATT-5313` / `Rule 71`: Mandatory visual inspection of UI dialogs and iconography.
* **Preservation of Core Invariants**:
  - `TranslationParityTest` 100% pass across all 9 locales (`de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`, `en`).
  - Dropdown functionality in `ManualClusterScreen` and `WorkoutClusterHeatmapScreen` consuming `R.string.Sport` preserved.
  - Strava upload state binding and disablement logic (`stravaMappingAvailable`) preserved.

---

## 5. Architectural Strategy & High-Level Solution
* **Localization Updates**:
  - Update all 9 `strings.xml` files with normalized, capitalized translations for `hint_workout_description`, `hint_workout_goal`, `hint_workout_method`, `Sport`, and `stravaUpload`.
* **Layout Adjustment (`EditWorkoutScreen.kt`)**:
  - Swap child order in the Strava row: render `Checkbox` first, then Strava logo with `alpha(if (stravaMappingAvailable) 1f else 0.38f)` and `Color.Unspecified`, followed by label `Text`.
* **Verification**:
  - Add contract tests verifying capitalization, non-colon `Sport`, and Strava row component order.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. 9-language resource key parity maintained without missing keys or dangling references.
  2. Vertical alignment of all checkboxes (`Pendeln`, `Indoor Trainer`, `Wettkampf`, `Strava`).
  3. Strava upload enablement strictly tied to `stravaMappingAvailable`.
* **Risk Rating**: **LOW** (Text resource updates and declarative layout reordering; zero database, state flow, or network impact).
