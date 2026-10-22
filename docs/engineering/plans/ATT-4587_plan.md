# Stage 3: Implementation Plan - ATT-4587: [Verbesserung] Insert a newline before '(Dropbox & Google Drive)' on the unified manual cloud backup button

**Ticket**: [ATT-4587](https://rainerblind.atlassian.net/browse/ATT-4587) | **Sub-task**: Pending Submission (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4587`  
**Requirement Mapping**: `REQ-MIG-036` (Clause 11) | **Test Mapping**: `TST-MIG-036` (Clauses 7–9)  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Description & Background

On the **Backup** tab (`BackupTabContent.kt`), when both Dropbox and Google Drive are connected:
1. The label `R.string.create_and_upload_to_both` renders as a single 35–45 character linear string, causing awkward line wrapping mid-word on narrow screens or larger display zoom levels.
2. The button's `Text` composable lacks explicit `textAlign = TextAlign.Center`, leaving multi-line labels aligned to the start/left edge inside the centered button layout.

---

## 2. Proposed Architectural Changes (SWE.2)

| Component / Layer | Architectural Modification & Responsibility |
| :--- | :--- |
| **Compose UI**<br>`BackupTabContent.kt` | Set `textAlign = TextAlign.Center` on the cloud backup button `Text` composable to guarantee centered alignment when the button label wraps across multiple lines. |
| **String Resources**<br>`res/values*/strings.xml` | In all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`), update `create_and_upload_to_both` by inserting an explicit `\n` immediately before `(Dropbox \u0026 Google Drive)`. |
| **UI Tests**<br>`BackupTabContentTest.kt` | Verify centered text alignment (`textAlign = TextAlign.Center`) in `BackupTabContent.kt` manual cloud backup button. |
| **Parity Tests**<br>`TranslationParityTest.kt` | Verify that `create_and_upload_to_both` contains `\n` across all 9 localized resource files with 100% translation parity. |

### UI Consistency (Rule 23)
* **Reference Component**: `BackupTabContent.kt` manual cloud upload button.
* **Theme Tokens**: Standard filled `Button`, `textAlign = TextAlign.Center`, Material 3 primary theme colors. Zero one-off styles.

---

## 3. Step-by-Step Implementation Sequence (Stage 4)

### Step 1: Update String Resources Across All 9 Locales
* Files: `app/src/main/res/values*/strings.xml` (9 locales: `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`)
* Changes: Update `create_and_upload_to_both` to include literal `\n` before `(Dropbox \u0026 Google Drive)` (e.g. `Create \u0026 Upload\n(Dropbox \u0026 Google Drive)`).

### Step 2: Apply Centered Alignment in `BackupTabContent.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupTabContent.kt`
* Changes: Add `textAlign = TextAlign.Center` to `Text(stringResource(cloudButtonTextRes), ...)` in the manual cloud backup button.

### Step 3: Implement Unit and Parity Tests (`TST-MIG-036.7`, `TST-MIG-036.8`)
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/BackupTabContentTest.kt`, `TranslationParityTest.kt`
* Logic: Assert `textAlign == TextAlign.Center` in `BackupTabContent.kt` and assert literal `\n` is present in `create_and_upload_to_both` across all 9 resource directories.

### Step 4: Verification & Clean-Room Regression Execution
* Commands:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.BackupTabContentTest"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

---

## 4. System Invariants & Rollback Plan

* **Core Invariants**: 100% translation parity across 9 languages; zero modification to single-provider button strings (`create_and_upload_to_dropbox`, etc.); click and mutex dispatch unchanged; 100% test pass rate.
* **Rollback Plan**: Changes fully isolated to `feature/ATT-4587`; cleanly revertable via git if needed.
