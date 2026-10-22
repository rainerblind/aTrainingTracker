# Stage 2: Requirement & Test Specification - ATT-4029: Fix untranslated strings and redundant non-translatable keys in locales

**Ticket**: [ATT-4029](https://rainerblind.atlassian.net/browse/ATT-4029)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4029`  
**Requirement Mapping**: `REQ-LOC-002` (*Locale Hygiene, Translation Completeness, and ExtraTranslation De-duplication*)  
**Test Spec ID**: `TST-STR-019`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Requirement Specification (`REQ-LOC-002`)

### 1.1 Problem Statement & Rationale
Resource audits identified raw English strings in non-English locales (`values-de`, `values-nl`, `values-pl`, and 7 non-DE locales for `backup_restore`), 7 non-translatable keys redeclared in all 8 locale folders triggering Android `ExtraTranslation` lint warnings, and verbatim redundant string-arrays in DE and PL.

### 1.2 Functional & Architectural Requirements
1. **Translation Completeness (`REQ-LOC-002.1`)**: Untranslated English literals in `values-de` (`pebble_build_in`, `tss`, `pedal_power_balance`), `values-nl` (`zone_2_label`, `zone_3_label`, `zone_5_label`), `values-pl` (`exporting_to_file*` 4 keys), and `values-{es,fr,it,ja,nl,pl,pt}` (`backup_restore`) SHALL be translated into their respective native languages.
2. **ExtraTranslation De-duplication (`REQ-LOC-002.2`)**: The 7 keys marked `translatable="false"` in base `values/strings.xml` (`ant_service_network_key`, `ant_service_samico_key`, `ant_service_user_key`, `intensity_factor_short`, `normalized_power_short`, `tss_short`, `work_kj_short`) SHALL be purged from all 8 localized `values-*/strings.xml` files.
3. **Redundant Array Excision (`REQ-LOC-002.3`)**: Duplicate string-arrays in `values-de/arrays.xml` and `values-pl/arrays.xml` that merely reference `@string/...` tokens SHALL be removed, relying on base `values/arrays.xml`.
4. **Automated Governance Guard (`REQ-LOC-002.4`)**: `TranslationParityTest.kt` SHALL include `testNoExtraTranslationsInLocales` asserting zero non-translatable keys are redeclared in locale files.
5. **System Invariants (`REQ-LOC-002.5`)**: 100% translation parity preserved across all 9 locales; zero alteration of runtime strings or fallback behavior.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Native Translation)**: *Given* any non-default locale, *When* displaying pebble, zone, export, or backup strings, *Then* strings display in the native language without English text.
* **AC-2 (Zero ExtraTranslation)**: *Given* all 8 locale `strings.xml` files, *When* audited against base `translatable="false"` keys, *Then* zero redundant copies exist.
* **AC-3 (Array Integrity)**: *Given* array queries in DE and PL, *When* accessed at runtime, *Then* arrays resolve properly via base `values/arrays.xml`.
* **AC-4 (Test Suite)**: *Given* `TranslationParityTest`, *When* executed, *Then* 100% of parity and de-duplication tests pass.

---

## 2. Test Specification (`TST-STR-019`)

* **`TST-STR-019.1` (Translation Completeness Audit)**: Verify native translations for DE, NL, PL, and 7-locale `backup_restore`.
* **`TST-STR-019.2` (ExtraTranslation Non-Translatable Guard)**: Automated unit test `testNoExtraTranslationsInLocales` in `TranslationParityTest.kt`.
* **`TST-STR-019.3` (Redundant Array Excision Audit)**: Verify removal of `@string`-referencing duplicate arrays in `values-de/arrays.xml` and `values-pl/arrays.xml`.
* **`TST-STR-019.4` (9-Language Parity Audit)**: `testStringKeyParity` and format specifier tests in `TranslationParityTest.kt`.
* **`TST-STR-019.5` (Regression Execution)**: `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Verification Method | Requirement Clause | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-STR-019.1` | Localization | `res/values-*/strings.xml` inspection | `REQ-LOC-002.1` | Specified |
| `TST-STR-019.2` | Governance | `TranslationParityTest.testNoExtraTranslationsInLocales` | `REQ-LOC-002.2`, `REQ-LOC-002.4` | Specified |
| `TST-STR-019.3` | Localization | `res/values-{de,pl}/arrays.xml` inspection | `REQ-LOC-002.3` | Specified |
| `TST-STR-019.4` | Parity | `TranslationParityTest.testStringKeyParity` | `REQ-LOC-002.5`, `REQ-LOC-001` | Specified |
| `TST-STR-019.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-LOC-002.5`, `REQ-PRO-001` | Specified |
