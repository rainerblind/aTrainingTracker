# Stage 2: Requirement & Test Specification - ATT-4292: [Medium] Fix double percent symbol (%%) in non-formatted Efficiency and W'bal string resources across all 9 locales

**Ticket**: [[ATT-4292]](https://rainerblind.atlassian.net/browse/ATT-4292)  
**Sub-task**: [[ATT-4301]](https://rainerblind.atlassian.net/browse/ATT-4301) (`[Req & Test Spec]`)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-UI-418` (*Single Percent Symbol (%) Parity in Non-Formatted Efficiency, Decoupling, and W'bal String Resources & Repository-Wide Percent Integrity Enforcement*)  
**Test Spec ID**: `TST-UI-381`  
**Branch**: `feature/ATT-4292`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Requirement Specification (REQ-UI-418)

### 1.1 Problem Statement & Rationale
On the Workout Summary card (`WorkoutDetails`), advanced aerobic efficiency and Critical Power / W'bal metric rows render an erroneous literal double percent symbol (`%%`) instead of a single percent symbol (`%`):
* Under **Aerobic Efficiency -> Pw:Hr / Pa:Hr** decoupling status:
  * Coupled state displays as **`Stabil (<5%%)`** instead of **`Stabil (<5%)`** (in German), **`Coupled (<5%%)`** instead of **`Coupled (<5%)`** (in English), etc.
  * High drift state displays as **`Hohe Drift (≥5%%)`** instead of **`Hohe Drift (≥5%)`** (in German), **`High Drift (≥5%%)`** instead of **`High Drift (≥5%)`** (in English), etc.
* Under **W'bal**:
  * Column header label displays as **`Min. %%`** / **`Min %%`** instead of **`Min. %`** / **`Min %`**.
  * Empty tank secondary alert displays as **`Tank leer (<10%%)`** instead of **`Tank leer (<10%)`** (or **`Empty Tank (<10%%)`** -> **`Empty Tank (<10%)`**).
* Under **W' Battery Alert**:
  * Low battery warning string `w_prime_warning_low_battery` displays as **`W'-Akku schwach (<20%%)`** instead of **`W'-Akku schwach (<20%)`** (or **`W' Battery Low (<20%%)`** -> **`W' Battery Low (<20%)`**).

In Android's resource compilation and resolution system (`Resources.java` / `AssetManager`), unformatted string resources resolved via `stringResource(id)` or `Resources.getString(id)` without format arguments are retrieved directly from the precompiled string pool without calling `String.format()`. Escaping `%` as `%%` is only evaluated and expanded to `%` when `String.format()` is executed. Because these 5 keys contain no format specifiers (`%s`, `%d`, `%1$d`, etc.), the escaped `%%` is never expanded and leaks directly into the user interface.

### 1.2 Functional & Architectural Requirements
1. **Aerobic Decoupling Status Correction (`REQ-UI-418.1`)**:
   * The system SHALL define `workout_decoupling_coupled` across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) with a single percent sign: `<5%` (e.g. `Coupled (<5%)`, `Stabil (<5%)`, `Acoplado (<5%)`, `Couplé (<5%)`, `Accoppiato (<5%)`, `安定 (<5%)`, `Gekoppeld (<5%)`, `Stabilne (<5%)`).
   * The system SHALL define `workout_decoupling_drift` across all 9 application locales with a single percent sign: `≥5%` (e.g. `High Drift (≥5%)`, `Hohe Drift (≥5%)`, `Deriva alta (≥5%)`, `Dérive élevée (≥5%)`, `Deriva elevata (≥5%)`, `高ドリフト (≥5%)`, `Hoge drift (≥5%)`, `Wysoki dryf (≥5%)`).
2. **W'bal Header, Empty Tank & Low Battery Alert Correction (`REQ-UI-418.2`)**:
   * The system SHALL define `workout_w_prime_min_pct_label` across all 9 application locales with a single percent sign: `Min %` (EN: `Min %`, DE/FR/IT/NL/PL: `Min. %`, ES/PT: `Mín. %`, JA: `最小 %`).
   * The system SHALL define `workout_w_prime_empty_tank` across all 9 application locales with a single percent sign: `<10%` (e.g. `Empty Tank (<10%)`, `Tank leer (<10%)`, `Tanque vacío (<10%)`, `Réservoir vide (<10%)`, `Serbatoio vuoto (<10%)`, `タンク空 (<10%)`, `Tank leeg (<10%)`, `Pusty bak (<10%)`).
   * The system SHALL define `w_prime_warning_low_battery` across all 9 application locales with a single percent sign: `<20%` (e.g. `W\' Battery Low (<20%)`, `W\'-Akku schwach (<20%)`, `Batería W\' baja (<20%)`, `Batterie W\' faible (<20%)`, `Batteria W\' scarica (<20%)`, `W\'バッテリー残量低下 (<20%)`, `W\'-accu bijna leeg (<20%)`, `Niski poziom baterii W\' (<20%)`, `Bateria W\' fraca (<20%)`).
3. **Preservation of Formatted String Escapes (`REQ-UI-418.3`)**:
   * All string resources that contain active positional format placeholders (`%1$d%%`, `%1$.1f%%`, `%2$.1f%%`, `%1$.0f%%`) SHALL retain `%%` so that `String.format()` produces a valid single `%` upon evaluation.
4. **Repository-Wide Percent Integrity Contract Test (`REQ-UI-418.4`)**:
   * The test suite SHALL introduce `LocalizationPercentIntegrityTest.kt` verifying that across all 9 locales:
     * (a) Every one of the 5 fixed keys contains `%` and does NOT contain `%%`.
     * (b) Any `<string>` element across all XML string resource files containing `%%` also contains at least one standard format specifier token (`%s`, `%d`, `%[0-9]+\$`, `%f`, etc.), guaranteeing that no unformatted string mistakenly uses `%%`.
   * `WPrimeBatteryLocalizationTest.kt` SHALL be hardened to assert that `w_prime_warning_low_battery` does NOT contain `%%`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Aerobic Decoupling Status Display)**:
  * *Given* `WorkoutDetails.kt` rendering the aerobic decoupling status in any of the 9 supported locales,
  * *When* `statusSubtitle` is resolved via `stringResource(R.string.workout_decoupling_coupled)` or `stringResource(R.string.workout_decoupling_drift)`,
  * *Then* the displayed text contains a single percent symbol (`<5%` or `≥5%`) and does NOT contain `%%`.
* **Criterion 2 (W'bal Minimum Percentage Column Label & Empty Tank Warning)**:
  * *Given* `WorkoutDetails.kt` rendering the W'bal minimum percentage column in any of the 9 supported locales,
  * *When* the label is resolved via `stringResource(R.string.workout_w_prime_min_pct_label)` and the secondary value is resolved via `stringResource(R.string.workout_w_prime_empty_tank)`,
  * *Then* the label renders `Min %` / `Min. %` / `Mín. %` / `最小 %` and the secondary empty tank alert renders `<10%` without double percent symbols.
* **Criterion 3 (W' Battery Low Alert)**:
  * *Given* live tracking or post-workout alert mechanisms resolving `w_prime_warning_low_battery`,
  * *When* the warning string is evaluated across any of the 9 supported locales,
  * *Then* the text contains `<20%` with a single `%` character and does NOT contain `%%`.
* **Criterion 4 (Automated Percent Integrity Contract)**:
  * *Given* all XML string files in `app/src/main/res/values*/`,
  * *When* evaluated by `LocalizationPercentIntegrityTest`,
  * *Then* zero unformatted string resources contain `%%`, and all 5 fixed keys contain `%` without `%%`.

### 1.4 System Invariants
* **Invariant 1 (Format String Stability)**: All 10 existing formatted string resources containing format placeholders (`display_brightness_slider_format`, `routes_climb_avg_grade`, `routes_climb_max_grade`, `sensor_battery_snackbar_single`, `workout_w_prime_bal_min_format`, `w_prime_battery_pct`, `workout_ef_pwhr_format`, `workout_ef_pahr_format`, `workout_decoupling_pct_format`, `workout_w_prime_min_pct_value_format`) retain their `%%` escaping unchanged.
* **Invariant 2 (9-Language Parity)**: 100% translation parity across all 9 locales with zero missing keys, zero untranslated fallback strings, and identical placeholder semantics.
* **Invariant 3 (UI Layout & Business Logic Immutability)**: Zero changes to Kotlin layout composables in `WorkoutDetails.kt` or `WPrimeBatteryGaugeBar.kt`, zero changes to calculation models or SQLite schema.
* **Invariant 4 (Human Decision Gate)**: The parent ticket `ATT-4292` remains governed by Rule 3 (Human Decision Gate) and cannot transition to `Erledigt` without human sign-off.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**:
  Refines `REQ-UI-404.3` & `REQ-UI-404.4` (*AltitudeRow + MetricItem Grouped Row Layout for Power and Physiology Metrics in WorkoutDetails*, introduced in Sprint `2026-42.8` via `ATT-3930`) and `REQ-UI-416.5` (*Real-time W' Balance (W-Prime Battery) expenditure and recovery tracking*, introduced in Sprint `2026-42.8` via `ATT-1296`). Targets `app/src/main/res/values*/strings.xml`, `WPrimeBatteryLocalizationTest.kt`, and creates `LocalizationPercentIntegrityTest.kt`.
* **Historical Origin & Commit Trace**:
  - `ATT-3930` (commit `1bd468bf`): Added `workout_decoupling_coupled`, `workout_decoupling_drift`, `workout_w_prime_min_pct_label`, and `workout_w_prime_empty_tank`.
  - `ATT-1296` (commit `f519df49`): Added `w_prime_warning_low_battery`.
* **Root Reason for Existing Formulation**:
  When drafting the string resources, the author copied syntax patterns from neighboring formatted strings (`workout_decoupling_pct_format` = `%1$.1f%%`, `w_prime_battery_pct` = `%1$d%%`) or presumed that Android resource XML universally requires escaping `%` as `%%`. Notably, `REQ-UI-416.5` in `docs/requirements.md` originally specified `w_prime_warning_low_battery` as `W\' Battery Low (<20%)` with a single `%`, confirming that `%%` was an unintended copy-paste implementation artifact rather than an intentional design choice.
* **Preservation of Core Invariants**:
  Replacing `%%` with `%` in these 5 non-formatted strings leaves the semantic label text, translations, and punctuation completely intact while eliminating the erroneous second percent sign on the UI. All formatted strings remain untouched. AAPT2 parses single `%` in these non-format strings without error, and all UI composables continue to receive the expected localized text.

---

## 2. Test Specification (TST-UI-381)

### Test Case 1: `testWPrimeWarningLowBattery_doesNotContainDoublePercent` (`TST-UI-381.1`)
* **Scope**: Localization Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/WPrimeBatteryLocalizationTest.kt`
* **Preconditions**: Res directory located and XML strings parsed across all 9 locales.
* **Action**:
  ```kotlin
  val warning = parseStringResource(stringsFile, "w_prime_warning_low_battery")
  assertTrue("w_prime_warning_low_battery in $dirName must contain %", warning!!.contains("%"))
  assertFalse("w_prime_warning_low_battery in $dirName must not contain %%", warning.contains("%%"))
  ```
* **Expected Result**: Assertion passes across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

### Test Case 2: `testNonFormattedStrings_doNotContainDoublePercent` (`TST-UI-381.2`)
* **Scope**: Repository-Wide Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/LocalizationPercentIntegrityTest.kt`
* **Preconditions**: All string resources loaded via `LocalizationTestCache`.
* **Action**:
  Scan every `<string>` resource in all XML string files across all 9 locales. For each entry containing `%%`:
  1. Inspect whether the raw string text contains a format specifier matching regex `%(\d+\$)?[#+ 0,-]?[0-9]*(\.[0-9]+)?[a-zA-Z]`.
  2. If `%%` is found in a string without any format placeholder, fail the test with a descriptive failure message detailing the string key, locale, and content.
* **Expected Result**: Zero non-formatted string resources contain `%%`.

### Test Case 3: 9-Language Localization & 5-Key String Parity Audit (`TST-UI-381.3`)
* **Scope**: Localization Parity & Exact Value Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/LocalizationPercentIntegrityTest.kt`
* **Goal**: Validate that all 5 target keys exist, contain `%`, do not contain `%%`, and exactly match their intended localized translations across all 9 supported locales:

| Locale Directory | Language | Key: `workout_decoupling_coupled` | Key: `workout_decoupling_drift` | Key: `workout_w_prime_min_pct_label` | Key: `workout_w_prime_empty_tank` | Key: `w_prime_warning_low_battery` |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `values` | English (EN) | `Coupled (<5%)` | `High Drift (≥5%)` | `Min %` | `Empty Tank (<10%)` | `W\' Battery Low (<20%)` |
| `values-de` | German (DE) | `Stabil (<5%)` | `Hohe Drift (≥5%)` | `Min. %` | `Tank leer (<10%)` | `W\'-Akku schwach (<20%)` |
| `values-es` | Spanish (ES) | `Acoplado (<5%)` | `Deriva alta (≥5%)` | `Mín. %` | `Tanque vacío (<10%)` | `Batería W\' baja (<20%)` |
| `values-fr` | French (FR) | `Couplé (<5%)` | `Dérive élevée (≥5%)` | `Min. %` | `Réservoir vide (<10%)` | `Batterie W\' faible (<20%)` |
| `values-it` | Italian (IT) | `Accoppiato (<5%)` | `Deriva elevata (≥5%)` | `Min. %` | `Serbatoio vuoto (<10%)` | `Batteria W\' scarica (<20%)` |
| `values-ja` | Japanese (JA) | `安定 (<5%)` | `高ドリフト (≥5%)` | `最小 %` | `タンク空 (<10%)` | `W\'バッテリー残量低下 (<20%)` |
| `values-nl` | Dutch (NL) | `Gekoppeld (<5%)` | `Hoge drift (≥5%)` | `Min. %` | `Tank leeg (<10%)` | `W\'-accu bijna leeg (<20%)` |
| `values-pl` | Polish (PL) | `Stabilne (<5%)` | `Wysoki dryf (≥5%)` | `Min. %` | `Pusty bak (<10%)` | `Niski poziom baterii W\' (<20%)` |
| `values-pt` | Portuguese (PT) | `Acoplado (<5%)` | `Deriva alta (≥5%)` | `Mín. %` | `Tanque vazio (<10%)` | `Bateria W\' fraca (<20%)` |

* **Expected Result**: 100% parity across all 45 strings, 0 missing entries, 0 format specifier mismatches, and 0 unwanted `%%` symbols.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-381.4`)
* **Scope**: Automated Unit & Contract Test Suite
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Test Class / Command | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-381.1` | Unit (Localization) | `WPrimeBatteryLocalizationTest.testWPrimeWarningLowBatteryParityAcrossAllLocales` | `REQ-UI-418.2`, `REQ-UI-418.4` | Specified |
| `TST-UI-381.2` | Contract (Repository) | `LocalizationPercentIntegrityTest.testNonFormattedStrings_doNotContainDoublePercent` | `REQ-UI-418.3`, `REQ-UI-418.4` | Specified |
| `TST-UI-381.3` | Contract (9-Locale) | `LocalizationPercentIntegrityTest.testTargetKeysParityAndSinglePercent` | `REQ-UI-418.1`, `REQ-UI-418.2`, `REQ-UI-106` | Specified |
| `TST-UI-381.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
