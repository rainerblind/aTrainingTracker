# Stage 3: Implementation Plan - ATT-4029: Fix untranslated strings and redundant non-translatable keys in locales

**Ticket**: [ATT-4029](https://rainerblind.atlassian.net/browse/ATT-4029)  
**Sub-task**: `[Impl-Plan]` | **Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4029`  
**Requirement Mapping**: `REQ-LOC-002` (`REQ-LOC-002.1`–`REQ-LOC-002.5`)  
**Test Mapping**: `TST-STR-019` (`TST-STR-019.1`–`TST-STR-019.5`)  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-22  

---

## 1. Problem Description & Background

Audits revealed three localization defects across `res/values-*/`: (1) untranslated English strings in `values-de` (`pebble_build_in`, `tss`, `pedal_power_balance`), `values-nl` (`zone_2_label`, `zone_3_label`, `zone_5_label`), `values-pl` (`exporting_to_file*` 4 keys), and `values-{es,fr,it,ja,nl,pl,pt}` (`backup_restore`); (2) 7 keys marked `translatable="false"` in base `values/strings.xml` redeclared across all 8 locale files triggering Android `ExtraTranslation` lint warnings; and (3) redundant `string-array` elements in `values-de/arrays.xml` (8 arrays) and `values-pl/arrays.xml` (5 arrays) that duplicate base `@string/...` array definitions.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-LOC-002` (*Locale Hygiene, Translation Completeness, and ExtraTranslation De-duplication*), extending `REQ-LOC-001`.
* **Test Mapping**: `TST-STR-019` (`TST-STR-019.1`–`TST-STR-019.5` in `TranslationParityTest.kt` and full test suite).

---

## 3. System Invariants & Preserved Behavior

1. **Translation Parity Invariant**: 100% key parity across all 9 locales preserved under `TranslationParityTest`.
2. **Android Resource Fallback**: Non-translatable technical keys resolve canonically from default `values/strings.xml`.
3. **Array Structural Integrity**: Array queries in DE and PL resolve identical localized items via base `values/arrays.xml`.
4. **Parent Human Gate Invariance**: Terminal ticket closure remains reserved for human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Native String Translations (`res/values-*/strings.xml`)
* Replace raw English text with idiomatic translations in DE ("Integrierte Pebble-App", "Trainingsbelastungs-Score (TSS)", "Pedal-Leistungsbalance"), NL ("Zone 2 (Aeroob)", "Zone 3 (Tempo)", "Zone 5 (Anaeroob)"), PL (4 file export notifications), and 7 non-DE locales for `backup_restore`.

### Component 2: ExtraTranslation Non-Translatable Key Excision (`res/values-*/strings.xml`)
* Purge the 7 `translatable="false"` keys (`ant_service_plugin_name`, `ant_service_radio_name`, `ant_service_usb_name`, `intensity_factor_short`, `normalized_power_short`, `tss_short`, `work_kj_short`) from all 8 locale `strings.xml` files (56 removed declarations).

### Component 3: Redundant String-Array Excision (`res/values-{de,pl}/arrays.xml`)
* Remove duplicate arrays (`Basic_Sport_Types`, `Filter_Types_UI`, `PebbleWatchapps`, `Time_Units_UI`, `units`, `display_options_titles`, etc.) from DE and PL; base `values/arrays.xml` provides structural definitions.

### Component 4: Parity Test Governance Guard (`TranslationParityTest.kt`)
* Add `@Test fun testNoExtraTranslationsInLocales()` asserting zero base `translatable="false"` keys exist in any `values-<locale>/strings.xml`.

### UI Consistency (Rule 23)
* No UI changes (resource XML cleanup and test verification only).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

* **Step 1: Native Translations (`REQ-LOC-002.1`)**: Update target keys in `values-de`, `values-nl`, `values-pl`, and `values-{es,fr,it,ja,nl,pl,pt}` `strings.xml`.
* **Step 2: Purge ExtraTranslation Keys (`REQ-LOC-002.2`)**: Remove the 7 non-translatable keys from all 8 locale `strings.xml` files.
* **Step 3: Excise Redundant Arrays (`REQ-LOC-002.3`)**: Remove duplicate string-arrays from `values-de/arrays.xml` and `values-pl/arrays.xml`.
* **Step 4: Governance Test Guard (`REQ-LOC-002.4`, `TST-STR-019.2`)**: Add `testNoExtraTranslationsInLocales` to `TranslationParityTest.kt`.
* **Step 5: Automated Verification Suite (`TST-STR-019.1`–`TST-STR-019.5`)**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`.

---

## 6. Verification & Rollback Plan

* **Verification**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`.
* **Rollback**: Branch isolation on `feature/ATT-4029` allows full reset via `git checkout feature/ATT-4029`.
