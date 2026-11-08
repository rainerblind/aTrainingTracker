# Stage 5: Walkthrough & Verification - ATT-5838: EditWorkoutScreen: Localize hints, normalize 'Sport'/'Zu Strava hochladen' labels, and fix Strava checkbox layout & disabled alpha

**Ticket**: [ATT-5838](https://rainerblind.atlassian.net/browse/ATT-5838)  
**Sub-task**: `[Test]`  
**Parent Epic**: [ATT-5909](https://rainerblind.atlassian.net/browse/ATT-5909) (*Localization & Formatting Parity (i18n)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-UI-497` (`REQ-UI-497.1`–`REQ-UI-497.3`)  
**Test Mapping**: `TST-UI-497` (`TST-UI-497.1`–`TST-UI-497.4`)  
**Branch**: `feature/ATT-5838`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Executive Summary & Verification Overview
Resolved localization, punctuation, and layout defects identified in the Edit Workout screen (`audit_04_einheiten.png`):
1. **German Hint Localization & Capitalization**: Translated `hint_workout_description` (`Beschreibung`), `hint_workout_goal` (`Ziel`), and `hint_workout_method` (`Methode`) in `values-de/strings.xml`, ensuring proper initial capitalization across all 9 locales matching title-cased sibling fields.
2. **Sport Label Colon Removal & Strava Label Capitalization**: Stripped the trailing colon from `R.string.Sport` across all 9 locales (`Sport`, `Deporte`, `スポーツ`, `Desporto`) for clean borderless dropdown labels across `EditWorkoutScreen`, `ManualClusterScreen`, and `WorkoutClusterHeatmapScreen`. Capitalized `R.string.stravaUpload` across all 9 locales (German: `Zu Strava hochladen`, English: `Upload to Strava`).
3. **Strava Row Checkbox Alignment & Non-Destructive Alpha**: Reordered the Strava upload row in `EditWorkoutScreen.kt` with `Checkbox` first (left-aligned with `Commute` and `Race`), followed by `Icon(R.drawable.logo_square_strava)` (`size = 18.dp`, `tint = Color.Unspecified`, `Modifier.alpha(if (stravaMappingAvailable) 1f else 0.38f)`), `Spacer(8.dp)`, and label `Text`. Disabled dimming retains the white Strava chevron without raster solid grey square (`■`) masking.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-497.1` | `TST-UI-497.1` | Contract Test (`EditWorkoutStringsParityContractTest`) | **PASSED** | `Verified` |
| `REQ-UI-497.2` | `TST-UI-497.1` | Contract Test (`EditWorkoutStringsParityContractTest`) | **PASSED** | `Verified` |
| `REQ-UI-497.3` | `TST-UI-497.2` | Contract Test (`EditWorkoutLayoutContractTest`) | **PASSED** | `Verified` |
| `REQ-LOC-001` | `TST-UI-497.3` | 9-Locale Parity Test (`TranslationParityTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-497.4` | Tier 1 Targeted Regression (`ui.aftermath.*`, `ui.theme.*`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope
* **Command**: `./gradlew assembleDebug testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*" --tests "*TranslationParityTest*" --max-workers=2 && python3 tools/verify_requirement_governance.py`
* **Output**:
  ```text
  BUILD SUCCESSFUL in 1m 26s
  50 actionable tasks: 5 executed, 45 up-to-date
  PASS: Living documentation master tables match all 9 domain shards with 100% parity.
  Net-new requirement(s) detected: REQ-UI-497. Bypassing archaeology check cleanly.
  ```

---

## 4. Hardware / Physical Verification (Pixel 10)
* **On-Device Status**: No physical USB test device attached (`adb devices` reported no active targets). Live on-device inspection across `EditWorkoutScreen` is deferred to Ceremony 2 joint review.
* **Layout Invariant Audit**: Verified Compose layout tree structure, Checkbox-first alignment, `tint = Color.Unspecified`, and disabled `0.38f` alpha dimming via automated unit and contract tests.

---

## 5. Invariant & Governance Verification
1. **Enablement Binding Invariant**: `uploadToStrava` binding, `stravaMappingAvailable` guard, and `TrainingApplication.uploadToCommunity(FileFormat.STRAVA)` condition preserved.
2. **Vertical Column Alignment**: All option checkboxes (`Commute`, `Trainer`, `Race`, `Strava`) align in the same left column.
3. **Living Documentation Parity**: `REQ-UI-497` and `TST-UI-497` marked `[Verified]` in living doc shards; 100% parity verified in master indices.
4. **Governance Verification**: `tools/verify_requirement_governance.py` confirmed 0 errors.
