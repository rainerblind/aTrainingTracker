# Stage 2: Requirement & Test Specification - ATT-5679: Strava Bulk Export Import Card Title Parity

**Ticket**: [ATT-5679](https://rainerblind.atlassian.net/browse/ATT-5679)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5679`  
**Requirement Mapping**: `REQ-UI-458` (refining `REQ-UI-293`, `REQ-MIG-048`) | **Test Spec ID**: `TST-UI-458`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Requirement Specification (`REQ-UI-458`)

### 1.1 Problem Statement & Rationale
On the **„Import“** tab of *Import & Backup* (`ImportBackupTabsScreen.kt`), format import cards display actionable titles with explicit action verbs (e.g., `import_fit_title`: *„FIT-Dateien importieren“* / *„Import FIT files“*). In contrast, the top card (`import_strava_archive_title`) previously omitted the action verb (*„Strava-Bulk-Export (ZIP)“* / *„Strava Bulk Export (ZIP)“*). When skimming the screen, the word *„Export“* without the governing verb *„importieren“* / *„Import …“* creates cognitive confusion, causing athletes to mistake the card for an export generator rather than an archive ingestion tool (verified in `import_strava_bulk_screenshot.png`). The system SHALL provide uniform action-oriented card titles across all 9 supported languages.

### 1.2 Functional & Architectural Requirements
1. **9-Language Action Verb Parity (`REQ-UI-458.1`)**: String resource `import_strava_archive_title` SHALL feature the explicit action verb across all 9 locales:
   - `values/`: *„Import Strava Bulk Export (ZIP)“*
   - `values-de/`: *„Strava-Bulk-Export (ZIP) importieren“*
   - `values-es/`: *„Importar exportación masiva de Strava (ZIP)“*
   - `values-fr/`: *„Importer une exportation en bloc Strava (ZIP)“*
   - `values-it/`: *„Importa esportazione in blocco Strava (ZIP)“*
   - `values-ja/`: *„Strava一括エクスポート (ZIP) をインポート“*
   - `values-nl/`: *„Strava bulk-export (ZIP) importeren“*
   - `values-pl/`: *„Importuj zbiorczy eksport Strava (ZIP)“*
   - `values-pt/`: *„Importar exportação em massa do Strava (ZIP)“*
2. **Resource Key & Layout Stability (`REQ-UI-458.2`)**: The resource identifier `import_strava_archive_title` SHALL remain stable; `StravaArchiveImportCard` invocation in `ImportBackupTabsScreen.kt` remains invariant.
3. **Prohibition of Inline Emojis (`REQ-UI-458.3`)**: String resources SHALL NOT contain decorative inline emojis (`ATT-5400`).
4. **Grammatical Symmetry Across Format Cards (`REQ-UI-458.4`)**: The phrasing of the Strava card title SHALL match the grammatical structure of adjacent FIT, TCX, and GPX format import cards (`REQ-UI-293`).

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **AC-1 (German Parity)**: *Given* an athlete viewing the Import tab in German, *When* reading the Strava card title, *Then* it displays *„Strava-Bulk-Export (ZIP) importieren“*.
* **AC-2 (English Parity)**: *Given* an athlete viewing the Import tab in English, *When* reading the Strava card title, *Then* it displays *„Import Strava Bulk Export (ZIP)“*.
* **AC-3 (9-Locale Non-Empty & Action Verb Presence)**: *Given* any of the 9 supported locales, *When* `ImportFormatLocalizationTest` executes, *Then* `import_strava_archive_title` is non-empty, contains the localized action verb, and contains zero emojis.
* **Invariants**: Resource key `import_strava_archive_title` unchanged; zero logic changes to `StravaBulkExportImporter.kt` or `ImportBackupTabsScreen.kt`; zero regressions on adjacent format cards.

---

## 2. Test Specification (`TST-UI-458`)

### Test Case 1: 9-Language Action Verb Parity Unit Tests (`TST-UI-458.1`)
* **Scope**: Unit Test (`ImportFormatLocalizationTest.kt`)
* **Action**: Verify `import_strava_archive_title` is present, non-empty, contains the expected action verb, and has no emojis across all 9 locales.

### Test Case 2: Structural Composable Contract Test (`TST-UI-458.2`)
* **Scope**: Contract Test (`ImportFormatCardsContractTest.kt`)
* **Action**: Verify `ImportBackupTabsScreen.kt` binds `StravaArchiveImportCard` title with `R.string.import_strava_archive_title`.

### Test Case 3: Tier 1 Targeted Regression Suite (`TST-UI-458.3`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.localization.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Goal**: Verify 100% pass rate across migration, localization, and build test suites.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-458.1` | Unit | `ImportFormatLocalizationTest.kt` | `REQ-UI-458.1`, `REQ-UI-458.3`, `REQ-UI-458.4` | Specified |
| `TST-UI-458.2` | Contract | `ImportFormatCardsContractTest.kt` | `REQ-UI-458.2` | Specified |
| `TST-UI-458.3` | Regression | Migration & Localization Suite | `REQ-PRO-001` | Specified |
