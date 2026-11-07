# Stage 2: Requirement & Test Specification - ATT-5677: Brand Compliance & Feature Title Neutralization

**Ticket**: [ATT-5677](https://rainerblind.atlassian.net/browse/ATT-5677)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*) | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.10` | **Branch**: `feature/ATT-5677`  
**Requirement Mapping**: `REQ-UI-460` (refining `REQ-UI-010`, `REQ-UI-293`, `REQ-UI-432`) | **Test Spec ID**: `TST-UI-460`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-07  

---

## 1. Requirement Specification (`REQ-UI-460`)

### 1.1 Problem Statement & Rationale
Official Strava API Brand Guidelines mandate: *"Never use Strava in the title of your app or feature"*. Naming features *"Strava Live Segments"* or *"Strava Segments"* in app navigation violates these terms. Furthermore, explicitly naming third-party hardware trademarks (*"Garmin, Wahoo, Hammerhead"*) in `import_fit_description` risks automated store listing rejections. The system SHALL neutralize `tip_live_segments_title`, `segments`, and `import_fit_description` across all 9 locales while preserving factual Strava integration in body descriptions and maintaining 100% navigation stability.

### 1.2 Functional & Architectural Requirements
1. **Live Segments Tip Title Neutralization (`REQ-UI-460.1`)**: Across all 9 locales, `tip_live_segments_title` SHALL omit the "Strava" brand prefix (e.g. EN: *"Live Segments &amp; Scrubbing"*, DE: *"Live-Segmente &amp; Scrubbing"*). Factual explanation of Strava segment sync in `tip_live_segments_desc` SHALL remain intact.
2. **Navigation Header & Drawer Title Neutralization (`REQ-UI-460.2`)**: In the navigation drawer (`AppNavigationDrawer.kt`) and segment tabs (`SegmentsTabsScreen.kt`), `segments` SHALL render neutral category titles without the "Strava" prefix across all 9 locales (e.g. EN: *"Segments"*, DE: *"Segmente"*, ES: *"Segmentos"*).
3. **FIT Hardware Brand Neutralization (`REQ-UI-460.3`)**: In `ImportBackupTabsScreen.kt`, `import_fit_description` SHALL replace specific brand names (*Garmin, Wahoo, Hammerhead*) with generic hardware categories (*bike computers, sports watches, or smartwatches* / DE: *Fahrradcomputern, Sportuhren oder Smartwatches*) across all 9 locales.
4. **Localization Parity & Invariants (`REQ-UI-460.4`)**: All 3 string resources SHALL maintain 100% parity across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) with zero inline emojis (`ATT-5400`) and zero navigation route changes (`NavRoutes.SEGMENTS`).

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **AC-1 (Tip Title)**: *Given* an athlete viewing the Tips & Guide screen in any locale, *When* reading the live segments card header, *Then* it displays *"Live Segments & Scrubbing"* (or localized) without the *"Strava"* brand prefix.
* **AC-2 (Drawer & Tabs Title)**: *Given* an athlete opening the navigation drawer or segments screen, *When* viewing the segments item, *Then* the label displays *"Segments"* (DE: *"Segmente"*) without *"Strava"*.
* **AC-3 (FIT Import Description)**: *Given* an athlete viewing the Import tab, *When* inspecting the FIT card subtitle, *Then* it references bike computers, sports watches, or smartwatches without naming Garmin, Wahoo, or Hammerhead.
* **AC-4 (9-Locale Parity)**: *Given* `TranslationParityTest`, *When* validating all 9 locales, *Then* all 3 keys are non-empty with zero format errors and zero emojis.
* **Invariants**: `NavRoutes.SEGMENTS` and drawer ID `R.id.drawer_segments` unchanged; Strava OAuth synchronization logic untouched.

---

## 2. Test Specification (`TST-UI-460`)

### Test Case 1: 9-Language Localization & Brand Absence Audit (`TST-UI-460.1`)
* **Scope**: Localization Contract Test (`TranslationParityTest.kt`, `ImportFormatLocalizationTest.kt`)
* **Action**: Verify that `tip_live_segments_title`, `segments`, and `import_fit_description` across all 9 locales exist, are non-empty, contain zero inline emojis, and do NOT contain unauthorized brand names in titles or hardware lists.

### Test Case 2: Drawer & Top Bar Title Contract Test (`TST-UI-460.2`)
* **Scope**: UI Contract Test (`AppNavigationDrawerTest.kt`, `TipsGuideScreenContractTest.kt`)
* **Action**: Verify `AppNavigationDrawer` binds `R.string.segments` to `R.id.drawer_segments` and `TipsGuideScreen` binds `R.string.tip_live_segments_title`.

### Test Case 3: FIT Card Composable Binding Contract Test (`TST-UI-460.3`)
* **Scope**: UI Contract Test (`ImportFormatCardsContractTest.kt`)
* **Action**: Verify `ImportBackupTabsScreen.kt` binds `R.string.import_fit_description` in the FIT format import card.

### Test Case 4: Tier 1 Targeted Regression Suite (`TST-UI-460.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.help.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Action**: Confirm 100% test pass rate across all modified packages.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-460.1` | Localization | 9 `strings.xml` files, `TranslationParityTest` | `REQ-UI-460.1`..`REQ-UI-460.4` | Specified |
| `TST-UI-460.2` | Contract | `AppNavigationDrawer`, `TipsGuideScreen` | `REQ-UI-460.1`, `REQ-UI-460.2` | Specified |
| `TST-UI-460.3` | Contract | `ImportBackupTabsScreen` | `REQ-UI-460.3` | Specified |
| `TST-UI-460.4` | Regression | Affected Package Test Suites | `REQ-UI-460.1`..`REQ-UI-460.4` | Specified |
