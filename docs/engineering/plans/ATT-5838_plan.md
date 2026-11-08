# Stage 3: Implementation Plan - ATT-5838: EditWorkoutScreen: Localize hints, normalize 'Sport'/'Zu Strava hochladen' labels, and fix Strava checkbox layout & disabled alpha

**Ticket**: [ATT-5838](https://rainerblind.atlassian.net/browse/ATT-5838)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-5909](https://rainerblind.atlassian.net/browse/ATT-5909) (*Localization & Formatting Parity (i18n)*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5838`  
**Requirement Mapping**: `REQ-UI-497` (`REQ-UI-497.1`–`REQ-UI-497.3`)  
**Test Mapping**: `TST-UI-497` (`TST-UI-497.1`–`TST-UI-497.4`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Description & Scope
Visual UI audits of the Edit Workout screen (`audit_04_einheiten.png`) revealed untranslated placeholder hints in German (`description`, `goal`, `method` in `values-de/strings.xml:852-854`), an inconsistent trailing colon on `R.string.Sport` across all 9 locales, lowercase `stravaUpload` ("hochladen zu Strava"), and a Strava row layout bug where placing the logo before the checkbox with `tint = Color.Gray` turns the square bitmap into a solid grey box (`■`) that looks like a second checkbox and shifts the real checkbox out of alignment with `Commute` and `Race`.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-497` (*EditWorkoutScreen Hint Localization, Punctuation Normalization, Label Capitalization & Strava Row Layout Parity*)
  * `REQ-UI-497.1`: In `values-de`, localize hints as `Beschreibung`, `Ziel`, `Methode`; capitalize hints across all 9 locales.
  * `REQ-UI-497.2`: Remove trailing colon from `R.string.Sport` across all 9 locales; capitalize `R.string.stravaUpload` across all 9 locales (German: `Zu Strava hochladen`, English: `Upload to Strava`).
  * `REQ-UI-497.3`: In `EditWorkoutScreen.kt:307-323`, place `Checkbox` first (left-aligned with `commute`/`race`), followed by Strava logo (`18.dp`, `tint = Color.Unspecified`, `Modifier.alpha(if (stravaMappingAvailable) 1f else 0.38f)`), `Spacer(8.dp)`, and label `Text`.
* **Test Mapping**: `TST-UI-497` (*EditWorkoutScreen Verification Suite*)
  * `TST-UI-497.1`: 9-Locale Hint, Sport & Strava Upload String Parity Contract Test (`EditWorkoutStringsParityContractTest.kt`).
  * `TST-UI-497.2`: Strava Row Layout Ordering & Disabled Alpha Contract Test (`EditWorkoutLayoutContractTest.kt`).
  * `TST-UI-497.3`: 9-Language Localization & Translation Parity Suite (`TranslationParityTest.kt`).
  * `TST-UI-497.4`: Tier 1 targeted unit test regression (`ui.aftermath.editworkout.*`, `ThemeColorCentralizationAuditTest`).

---

## 3. System Invariants & UI Consistency
1. **Enablement & Binding Invariant**: Checkbox binding to `viewModel.updateUploadToStrava(it)` and guard `stravaMappingAvailable` preserved; community Strava condition (`TrainingApplication.uploadToCommunity(FileFormat.STRAVA)`) preserved.
2. **Visual Alignment & Dimming**: Checkbox left-aligns with `Commute` and `Race` checkboxes above it. Applying `Modifier.alpha(0.38f)` with `tint = Color.Unspecified` preserves the white Strava chevron inside the orange square even when disabled (zero solid grey rectangles).
3. **UI Consistency**: Reused Material3 components (`Checkbox`, `Icon`, `Text`, `Spacer`) and tokens (`MaterialTheme.colorScheme.onSurface`, `18.dp`, `8.dp`). Zero new one-off styles.

---

## 4. Proposed Architectural Changes & Atomic Implementation Steps
* **Step 1: 9-Locale String Parity (`res/values*/strings.xml`)**:
  * Localize `hint_workout_description`, `hint_workout_goal`, `hint_workout_method` in `values-de` and capitalize in all 9 locales.
  * Remove trailing colon from `Sport` in all 9 locales (`Sport`, `Deporte`, `スポーツ`, `Desporto`).
  * Capitalize `stravaUpload` in all 9 locales (`Zu Strava hochladen`, `Upload to Strava`, etc.).
* **Step 2: Component Ordering & Non-Destructive Alpha (`EditWorkoutScreen.kt:307-323`)**:
  * Place `Checkbox` before `Icon`.
  * Set `Icon` to `tint = Color.Unspecified` with `Modifier.alpha(if (stravaMappingAvailable) 1f else 0.38f)`.
  * Insert `Spacer(Modifier.width(8.dp))` before `Text`.
* **Step 3: Automated Contract Tests (`app/src/test/.../editworkout/`)**:
  * Author `EditWorkoutStringsParityContractTest.kt` for `TST-UI-497.1`.
  * Author `EditWorkoutLayoutContractTest.kt` for `TST-UI-497.2`.
* **Step 4: Verification**:
  * Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.editworkout.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`.
  * Run `python3 tools/verify_requirement_governance.py`.

---

## 5. Verification & Rollback Plan
* **Verification**: Targeted unit and contract tests in Stage 4; on-device UI walkthrough in Stage 5.
* **Rollback Plan**: Atomic commit on `feature/ATT-5838`; revert via `git reset --hard` if regression occurs.
