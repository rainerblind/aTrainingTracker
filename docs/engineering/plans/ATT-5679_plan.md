# Stage 3: Implementation Plan - ATT-5679: Strava Bulk Export Import Card Title Parity

**Ticket**: [ATT-5679](https://rainerblind.atlassian.net/browse/ATT-5679)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5679`  
**Requirement Mapping**: `REQ-UI-458` (`REQ-UI-458.1`–`REQ-UI-458.4`) | **Test Mapping**: `TST-UI-458` (`TST-UI-458.1`–`TST-UI-458.3`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Description & Background
On the **„Import“** tab of *Import & Backup* (`ImportBackupTabsScreen.kt`), format import cards display actionable titles containing explicit action verbs (*„FIT-Dateien importieren“*, *„TCX-Dateien importieren“*, *„GPX-Dateien importieren“*). In contrast, the top card (`import_strava_archive_title`) previously omitted the verb (*„Strava-Bulk-Export (ZIP)“* / *„Strava Bulk Export (ZIP)“*), which creates cognitive confusion where athletes might mistake the card for an export generator rather than an import tool. This plan clarifies the title to include the action verb across all 9 supported application locales.

---

## 2. Invariants & Preserved Behavior
1. **Resource Key Stability (`REQ-UI-458.2`)**: The resource identifier `import_strava_archive_title` remains strictly unchanged, ensuring all Compose call sites (`ImportBackupTabsScreen.kt:1373`) and test suites compile and link without modifications.
2. **Grammatical Symmetry Across Format Cards (`REQ-UI-458.4`)**: The title phrasing matches the action-verb structure of adjacent FIT, TCX, and GPX format cards across all 9 locales.
3. **Emoji Prohibition (`REQ-UI-458.3`, `ATT-5400`)**: Zero decorative inline Unicode emojis in strings.
4. **Logic Invariance**: Ingestion engine (`StravaBulkExportImporter.kt`), file pickers, and view model flows remain 100% untouched.

---

## 3. UI Consistency & Architectural Strategy
* **Reference Screen**: `ImportBackupTabsScreen.kt` (`ImportTabContent`).
* **Theme & Typography**: Standard `MaterialTheme.typography.titleMedium` on card headers.
* **New One-Off Styles**: None. Pure string resource clarification and contract test verification.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `import_strava_archive_title` Across All 9 Locales (`REQ-UI-458.1`, `REQ-UI-458.3`)
* **Files**: `app/src/main/res/values*/strings.xml` (9 files)
* **Changes**:
  - `values/strings.xml`: `Import Strava Bulk Export (ZIP)`
  - `values-de/strings.xml`: `Strava-Bulk-Export (ZIP) importieren`
  - `values-es/strings.xml`: `Importar exportación masiva de Strava (ZIP)`
  - `values-fr/strings.xml`: `Importer une exportation en bloc Strava (ZIP)`
  - `values-it/strings.xml`: `Importa esportazione in blocco Strava (ZIP)`
  - `values-ja/strings.xml`: `Strava一括エクスポート (ZIP) をインポート`
  - `values-nl/strings.xml`: `Strava bulk-export (ZIP) importeren`
  - `values-pl/strings.xml`: `Importuj zbiorczy eksport Strava (ZIP)`
  - `values-pt/strings.xml`: `Importar exportação em massa do Strava (ZIP)`

### Step 2: Extend Unit & Contract Tests (`TST-UI-458.1`, `TST-UI-458.2`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/ImportFormatCardsContractTest.kt`
* **Changes**: Add unit/contract test verifying that `import_strava_archive_title` across all 9 locale directories is non-empty, contains the expected action verb, contains no emojis, and is bound by `StravaArchiveImportCard` in `ImportBackupTabsScreen.kt`.

---

## 5. Verification Commands (`TST-UI-458.3`)
* `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* `python3 tools/verify_requirement_governance.py`
