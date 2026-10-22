# Stage 1 Analysis: ATT-4029 - Fix untranslated strings and redundant non-translatable keys in locales

**Ticket**: [ATT-4029](https://rainerblind.atlassian.net/browse/ATT-4029)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4029`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Statement & Root Cause Analysis (RCA)

* **Problem**: Resource audits identified three categories of localization debt across `res/values-*/`:
  1. *Untranslated English strings*: English text left in `values-de` (`pebble_build_in`, `tss`, `pedal_power_balance`), `values-nl` (`zone_2_label`, `zone_3_label`, `zone_5_label`), `values-pl` (`exporting_to_file*` 4 keys), and `values-{es,fr,it,ja,nl,pl,pt}` (`backup_restore`).
  2. *Redundant non-translatable keys*: 7 keys marked `translatable="false"` in base `values/strings.xml` are redeclared in all 8 locale files, triggering Android `ExtraTranslation` lint warnings.
  3. *Redundant string-arrays*: `values-de/arrays.xml` (8 arrays) and `values-pl/arrays.xml` (5 arrays) contain verbatim duplicate `string-array` elements that merely reference `@string/...` tokens already resolved by base resources.
* **Root Cause**: Early manual file replication copied base XML blocks wholesale across locales to silence missing-resource build warnings before automated parity gating (`TranslationParityTest`) was introduced.
* **Impact**: Unprofessional mixed-language UI for non-English users and build-time lint noise.

---

## 2. Call Site Audit & Forensic Verification

| Category | File(s) & Keys | Current Finding | Proposed Remediation |
|---|---|---|---|
| **Untranslated (DE)** | `values-de/strings.xml`: `pebble_build_in`, `tss`, `pedal_power_balance` | Raw English literals ("Standard Pebble Watchapp", "Training Stress Score", "pedal power balance") | Translate to German: "Integrierte Pebble-App", "Trainingsbelastungs-Score (TSS)", "Pedal-Leistungsbalance" |
| **Untranslated (NL)** | `values-nl/strings.xml`: `zone_2_label`, `zone_3_label`, `zone_5_label` | English qualifiers ("(Aerobic)", "(Tempo)", "(Anaerobic)") | Translate to Dutch: "Zone 2 (Aeroob)", "Zone 3 (Tempo)", "Zone 5 (Anaeroob)" |
| **Untranslated (PL)** | `values-pl/strings.xml`: `exporting_to_file*` (4 keys) | Raw English export progress and failure strings | Translate to Polish ("Eksport do pliku nie jest wymagany.", "Eksportowanie do pliku:...", etc.) |
| **Untranslated (7 Locales)** | `values-{es,fr,it,ja,nl,pl,pt}/strings.xml`: `backup_restore` | English "Backup & Restore" in all non-DE locales | Translate to ES, FR, IT, JA, NL, PL, PT ("Copia de seguridad y restauración", "Sauvegarde et restauration", etc.) |
| **ExtraTranslation (7 Keys)** | `values-*/strings.xml`: `ant_service_*` (3), `intensity_factor_short`, `normalized_power_short`, `tss_short`, `work_kj_short` | Declared in all 8 locales despite `translatable="false"` in base | Remove from all 8 `values-*/strings.xml` files; let Android fall back to default |
| **Redundant Arrays** | `values-de/arrays.xml` (8), `values-pl/arrays.xml` (5) | Duplicate arrays (`units`, `Basic_Sport_Types`, `Filter_Types_UI`, etc.) referencing `@string` | Remove redundant arrays from DE & PL; base `values/arrays.xml` handles resolution |
| **Test Infrastructure** | `TranslationParityTest.kt` | `testStringKeyParity` checks only `isTranslatable` keys | Confirmed: removing non-translatable keys from locales passes cleanly; add test asserting 0 `ExtraTranslation` keys |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Translate all confirmed English string literals in `values-{de,nl,pl,es,fr,it,ja,pt}/strings.xml`.
  2. Remove the 7 redeclared non-translatable keys across all 8 locale `strings.xml` files.
  3. Purge redundant `@string`-referencing `string-array` elements from `values-de/arrays.xml` and `values-pl/arrays.xml`.
  4. Extend `TranslationParityTest.kt` to assert that no non-translatable keys are redeclared in locale files.
* **Out-of-Scope Non-Goals**:
  1. Modifying base `values/strings.xml` or `values/arrays.xml`.
  2. Altering UI layouts, Compose code, ViewModels, or database logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Requirement ID & Target**: Net-new requirement `REQ-LOC-002` (*Locale Hygiene, Translation Completeness, and ExtraTranslation De-duplication*), extending `REQ-LOC-001` (*Global Translation Parity*).
* **Historical Origin & Commit Trace**: Code review finding (`feature/ATT-3921` @ `0234bfc0`). `ant_service_*` keys originated from commit `5a8d102e` ANT+ radio refactor; arrays were duplicated during legacy Eclipse-to-Gradle migration.
* **Root Reason for Existing Formulation**: Keys and arrays were duplicated to guarantee all locales had matching XML structure before AGP supported sophisticated resource fallbacks.
* **Preservation of Core Invariants**: Android resource fallback ensures 100% availability of non-translatable strings. Zero translatable keys are removed.

---

## 5. Architectural Strategy & High-Level Solution

1. **Locale Translation & Normalization**: Update target strings in all 8 locales with natural, idiomatically accurate translations.
2. **Redundant Key & Array Elimination**: Remove 56 redundant string tags (7 keys × 8 locales) and 13 redundant array declarations.
3. **Automated Governance Gating**: Add `@Test fun testNoExtraTranslationsInLocales()` in `TranslationParityTest.kt` asserting that no key with `translatable="false"` in base exists in any `values-<locale>/strings.xml`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. `TranslationParityTest` passes with 100% success across all 9 locales.
  2. Fallback resolution for non-translatable technical strings (`GPS`, `W`, `IF`, `NP`, `TSS`, ANT services) remains identical at runtime.
  3. Zero regressions in UI rendering or full test suite.
* **Risk Rating**: **LOW** — Pure XML resource maintenance and test guard enhancement with zero executable code modifications.
