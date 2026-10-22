# Stage 3: Implementation Plan - ATT-4292: [Medium] Fix double percent symbol (%%) in non-formatted Efficiency and W'bal string resources across all 9 locales

**Ticket**: [[ATT-4292]](https://rainerblind.atlassian.net/browse/ATT-4292)  
**Sub-task**: [[ATT-4302]](https://rainerblind.atlassian.net/browse/ATT-4302) (`[Impl-Plan]`)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-UI-418` (*Single Percent Symbol (%) Parity in Non-Formatted Efficiency, Decoupling, and W'bal String Resources & Repository-Wide Percent Integrity Enforcement*)  
**Test Mapping**: `TST-UI-381` (`TST-UI-381.1`, `TST-UI-381.2`, `TST-UI-381.3`, `TST-UI-381.4`)  
**Branch**: `feature/ATT-4292`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Description & Background

On the Workout Summary card (`WorkoutDetails`), advanced aerobic efficiency (`Pw:Hr` / `Pa:Hr`) and Critical Power / W'bal metric rows render an erroneous literal double percent symbol (`%%`) instead of a single percent symbol (`%`):
* Under **Aerobic Efficiency -> Pw:Hr / Pa:Hr** decoupling status:
  * Coupled state displays as **`Stabil (<5%%)`** instead of **`Stabil (<5%)`** (in German), **`Coupled (<5%%)`** instead of **`Coupled (<5%)`** (in English), etc.
  * High drift state displays as **`Hohe Drift (≥5%%)`** instead of **`Hohe Drift (≥5%)`** (in German), **`High Drift (≥5%%)`** instead of **`High Drift (≥5%)`** (in English), etc.
* Under **W'bal**:
  * Column header label displays as **`Min. %%`** / **`Min %%`** instead of **`Min. %`** / **`Min %`**.
  * Empty tank secondary alert displays as **`Tank leer (<10%%)`** instead of **`Tank leer (<10%)`** (or **`Empty Tank (<10%%)`** -> **`Empty Tank (<10%)`**).
* Under **W' Battery Alert**:
  * Low battery warning string `w_prime_warning_low_battery` displays as **`W'-Akku schwach (<20%%)`** instead of **`W'-Akku schwach (<20%)`** (or **`W' Battery Low (<20%%)`** -> **`W' Battery Low (<20%)`**).

### Technical Root Cause
In Android's resource compilation and runtime resolution system (`Resources.java` / `AssetManager`), string resources are loaded through two distinct execution paths:
1. **Unformatted Strings (`Resources.getString(id)` / Compose `stringResource(id)`)**:
   The Android framework retrieves the string directly from the precompiled string pool table as a raw character sequence without passing it to `String.format()`.
2. **Formatted Strings (`Resources.getString(id, Object... formatArgs)` / Compose `stringResource(id, *formatArgs)`)**:
   The Android framework retrieves the format template and invokes `String.format(currentLocale, rawString, *formatArgs)`. In standard Java/Android format syntax, `%%` is the escape token that evaluates to a single literal `%`.

When a string resource contains **no format placeholders** (such as `%s`, `%d`, `%1$d`, or `%1$.1f`) and is fetched via `stringResource(id)` or `getString(id)`, `String.format` is never executed. Consequently, the literal characters `%%` remain unexpanded in the final UI text.

A comprehensive audit revealed exactly 5 keys containing `%%` without any format specifiers (45 string entries across all 9 locales), while 10 genuine formatted keys legitimately use `%%` to escape `%` when formatted with dynamic arguments.

---

## 2. Traceability & Requirements Mapping

| Requirement | Test Mapping | Description | Scope |
| :--- | :--- | :--- | :--- |
| **`REQ-UI-418.1`** | `TST-UI-381.3` | Aerobic Decoupling Status Correction (`workout_decoupling_coupled`, `workout_decoupling_drift`) across all 9 locales | XML String Resources |
| **`REQ-UI-418.2`** | `TST-UI-381.1`, `TST-UI-381.3` | W'bal Header, Empty Tank & Low Battery Alert Correction (`workout_w_prime_min_pct_label`, `workout_w_prime_empty_tank`, `w_prime_warning_low_battery`) across all 9 locales | XML String Resources & Unit Test |
| **`REQ-UI-418.3`** | `TST-UI-381.2` | Preservation of Formatted String Escapes (10 keys with format specifiers retain `%%` for `String.format`) | XML String Resources |
| **`REQ-UI-418.4`** | `TST-UI-381.2`, `TST-UI-381.3` | Repository-Wide Percent Integrity Contract Test & 9-Locale Parity Enforcement (`LocalizationPercentIntegrityTest.kt`) | Automated Contract Test Suite |
| **`REQ-PRO-001`** | `TST-UI-381.4` | Clean-room full regression suite execution (`./gradlew testDebugUnitTest`) with 100% pass rate | Full Test Suite |

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions & Format String Stability**: All 10 existing formatted string resources containing format placeholders (`display_brightness_slider_format`, `routes_climb_avg_grade`, `routes_climb_max_grade`, `sensor_battery_snackbar_single`, `workout_w_prime_bal_min_format`, `w_prime_battery_pct`, `workout_ef_pwhr_format`, `workout_ef_pahr_format`, `workout_decoupling_pct_format`, `workout_w_prime_min_pct_value_format`) retain their `%%` escaping unchanged so `String.format()` produces `%`.
2. **100% 9-Language Localization Parity**: Translations across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) maintain identical keys, correct semantic translations, and zero missing entries.
3. **UI Layout & Kotlin Immutability**: No changes to composables in `WorkoutDetails.kt`, `WPrimeBatteryGaugeBar.kt`, or other UI components. The existing layout, typography, and spacing remain unchanged.
4. **Thread Safety & Database Stability**: Zero database modifications, zero schema changes, zero SQLite migrations.
5. **Subtask Self-Sufficiency**: Subtask `ATT-4302` transitions directly to `Erledigt` upon passing Gate 3 audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-4292` remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: XML String Resources (`app/src/main/res/values*/strings.xml`)
* **Layer**: Presentation / Android Resources (Declarative XML).
* **Changes**: Update 5 string keys across 9 locale files (45 entries total) replacing `%%` with `%`.

#### 9-Locale Exact Values Mapping
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

### Component 2: Existing Localization Unit Test (`WPrimeBatteryLocalizationTest.kt`)
* **Layer**: Unit Test (`app/src/test/java/com/atrainingtracker/trainingtracker/localization/`).
* **Changes**:
  In `testWPrimeWarningLowBatteryParityAcrossAllLocales`:
  Add assertion:
  ```kotlin
  assertFalse(
      "w_prime_warning_low_battery in $dirName must not contain %%",
      warning.contains("%%")
  )
  ```

### Component 3: Repository-Wide Contract Test (`LocalizationPercentIntegrityTest.kt`)
* **Layer**: Automated Contract Test (`app/src/test/java/com/atrainingtracker/trainingtracker/localization/`).
* **Changes**:
  Create new test suite `LocalizationPercentIntegrityTest`:
  1. `testNonFormattedStrings_doNotContainDoublePercent()` (`TST-UI-381.2`):
     - Parses all XML string resources (`strings.xml`, `strings_devices.xml`, `strings_filters.xml`) across all 9 locales.
     - For every `<string>` entry containing `%%`:
       - Checks that the string contains at least one standard format specifier token matching regex `%(\\d+\\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z])`.
       - If no format specifier exists, fails the test with detailed diagnostics (key name, locale directory, raw text).
  2. `testTargetKeysParityAndSinglePercent()` (`TST-UI-381.3`):
     - Verifies all 5 target keys (`workout_decoupling_coupled`, `workout_decoupling_drift`, `workout_w_prime_min_pct_label`, `workout_w_prime_empty_tank`, `w_prime_warning_low_battery`) across all 9 locales.
     - Asserts that each key exists, contains `%`, does not contain `%%`, and matches the exact expected translation.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: `WorkoutDetails.kt` (lines 383, 385, 431, 434) and `WPrimeBatteryGaugeBar.kt`.
* **Reused components**: Existing `MetricItem` rows, `AltitudeRow` layout.
* **Theme tokens**: Existing Material 3 typography and colors (`MaterialTheme.typography.bodySmall`, `MaterialTheme.colorScheme.onSurfaceVariant`).
* **New one-off styles & justification**: None. No UI layout, composables, styling, or custom colors are introduced or altered; this change strictly corrects resource string content.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update 9-Locale XML String Resources (45 Entries across 9 `strings.xml` Files)
* **Target Files**:
  * `app/src/main/res/values/strings.xml`
  * `app/src/main/res/values-de/strings.xml`
  * `app/src/main/res/values-es/strings.xml`
  * `app/src/main/res/values-fr/strings.xml`
  * `app/src/main/res/values-it/strings.xml`
  * `app/src/main/res/values-ja/strings.xml`
  * `app/src/main/res/values-nl/strings.xml`
  * `app/src/main/res/values-pl/strings.xml`
  * `app/src/main/res/values-pt/strings.xml`
* **Changes**:
  In each file, replace `%%` with `%` in:
  1. `workout_decoupling_coupled`
  2. `workout_decoupling_drift`
  3. `workout_w_prime_min_pct_label`
  4. `workout_w_prime_empty_tank`
  5. `w_prime_warning_low_battery`

### Step 2: Harden Existing Localization Test (`WPrimeBatteryLocalizationTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/WPrimeBatteryLocalizationTest.kt`
* **Changes**:
  In `testWPrimeWarningLowBatteryParityAcrossAllLocales`:
  Add assertion:
  ```kotlin
  assertFalse(
      "w_prime_warning_low_battery in $dirName must not contain %%",
      warning.contains("%%")
  )
  ```

### Step 3: Implement Automated Contract Test (`LocalizationPercentIntegrityTest.kt`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/LocalizationPercentIntegrityTest.kt`
* **Changes**:
  Implement test class with:
  1. `testNonFormattedStrings_doNotContainDoublePercent()`
  2. `testTargetKeysParityAndSinglePercent()`

### Step 4: Targeted Unit Tests
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.WPrimeBatteryLocalizationTest" --tests "com.atrainingtracker.trainingtracker.localization.LocalizationPercentIntegrityTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
  ```
* **Expected Result**: All targeted tests compile and pass with 0 failures (~5-15s).

---

## 6. Verification & Rollback Plan

* **Verification**:
  1. **Stage 4 Construction**: Targeted localization tests (`WPrimeBatteryLocalizationTest`, `LocalizationPercentIntegrityTest`, `TranslationParityTest`).
  2. **Stage 5 Verification**: Full clean-room regression test suite (`./gradlew testDebugUnitTest`) across all modules.
* **Rollback**:
  Branch isolation ensures zero risk to the active sprint or develop branch. Reverting the feature branch (`git checkout feature/ATT-4292 && git reset --hard origin/feature/ATT-4292`) completely restores the pre-ticket state if necessary.
