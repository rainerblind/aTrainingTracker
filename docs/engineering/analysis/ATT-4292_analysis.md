# Stage 1 Analysis: ATT-4292 - Fix double percent symbol (%%) in non-formatted Efficiency and W'bal string resources across all 9 locales

**Ticket**: [[ATT-4292]](https://rainerblind.atlassian.net/browse/ATT-4292)  
**Sub-task**: [[ATT-4300]](https://rainerblind.atlassian.net/browse/ATT-4300) (`[Analysis]`)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Branch**: `feature/ATT-4292`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Statement & Motivation

On the Workout Summary card (`WorkoutDetails`), advanced aerobic efficiency and Critical Power / W'bal metric rows render an erroneous literal double percent symbol (`%%`) instead of a single percent symbol (`%`):
* Under **Aerobic Efficiency -> Pw:Hr / Pa:Hr** decoupling status:
  * Coupled state renders as **`Stabil (<5%%)`** instead of **`Stabil (<5%)`** (in German), **`Coupled (<5%%)`** instead of **`Coupled (<5%)`** (in English), etc.
  * High drift state renders as **`Hohe Drift (≥5%%)`** instead of **`Hohe Drift (≥5%)`** (in German), **`High Drift (≥5%%)`** instead of **`High Drift (≥5%)`** (in English), etc.
* Under **W'bal**:
  * Column header label renders as **`Min. %%`** / **`Min %%`** instead of **`Min. %`** / **`Min %`**.
  * Empty tank secondary alert renders as **`Tank leer (<10%%)`** instead of **`Tank leer (<10%)`** (or **`Empty Tank (<10%%)`** -> **`Empty Tank (<10%)`**).
* Under **W' Battery Alert**:
  * Low battery warning string `w_prime_warning_low_battery` renders as **`W'-Akku schwach (<20%%)`** instead of **`W'-Akku schwach (<20%)`** (or **`W' Battery Low (<20%%)`** -> **`W' Battery Low (<20%)`**).

This issue was identified during on-device physical testing on Google Pixel 10 (recorded in Jira attachment `advanced_power_double_percent.png`). It creates an unpolished and unprofessional visual impression on core telemetry summary screens.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Android Framework Resource Resolution Mechanics
In Android's resource compilation and runtime resolution system (`Resources.java` / `AssetManager`), string resources are loaded through two distinct execution paths:
1. **Unformatted Strings (`Resources.getString(id)` / Compose `stringResource(id)`)**:
   The Android framework retrieves the string directly from the precompiled string pool table as a raw character sequence. It does **not** pass the string to `String.format(Locale, format, *args)`.
2. **Formatted Strings (`Resources.getString(id, Object... formatArgs)` / Compose `stringResource(id, *formatArgs)`)**:
   The Android framework retrieves the format template and invokes `String.format(currentLocale, rawString, *formatArgs)`. In standard Java/Android format syntax, `%%` is the escape token that evaluates to a single literal `%`.

When a string resource contains **no format placeholders** (such as `%s`, `%d`, `%1$d`, or `%1$.1f`) and is fetched via `stringResource(id)` or `getString(id)`, `String.format` is never executed. Consequently, the literal characters `%%` remain unexpanded in the final UI text.

### 2.2 Repository-Wide 9-Locale Audit
A comprehensive audit of all XML string files across the codebase (`app/src/main/res/values*/strings*.xml`) revealed **135 total strings containing `%%`** across 15 unique keys, present identically across all 9 application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`):

* **10 keys legitimately contain format placeholders and require `%%` for `String.format`**:
  1. `display_brightness_slider_format`: `Brightness: %1$d%%`
  2. `routes_climb_avg_grade`: `Avg. %1$.1f%%`
  3. `routes_climb_max_grade`: `Max. %1$.1f%%`
  4. `sensor_battery_snackbar_single`: `⚠️ %1$s: Low battery (%2$d%%)`
  5. `workout_w_prime_bal_min_format`: `Min W\'bal: %1$.1f kJ (%2$.0f%%)`
  6. `w_prime_battery_pct`: `%1$d%%`
  7. `workout_ef_pwhr_format`: `EF: %1$.2f W/bpm • Pw:Hr: %2$.1f%%`
  8. `workout_ef_pahr_format`: `EF: %1$.2f m/min/bpm • Pa:Hr: %2$.1f%%`
  9. `workout_decoupling_pct_format`: `%1$.1f%%`
  10. `workout_w_prime_min_pct_value_format`: `%1$.0f%%`

* **Exactly 5 keys contain `%%` WITHOUT any format placeholders**:
  1. `workout_decoupling_coupled`: e.g., `Coupled (<5%%)` (DE: `Stabil (<5%%)`)
  2. `workout_decoupling_drift`: e.g., `High Drift (≥5%%)` (DE: `Hohe Drift (≥5%%)`)
  3. `workout_w_prime_min_pct_label`: e.g., `Min %%` (DE: `Min. %%`, JA: `最小 %%`)
  4. `workout_w_prime_empty_tank`: e.g., `Empty Tank (<10%%)` (DE: `Tank leer (<10%%)`)
  5. `w_prime_warning_low_battery`: e.g., `W\' Battery Low (<20%%)` (DE: `W\'-Akku schwach (<20%%)`)

Across all 9 locales, these 5 keys account for **45 string entries** displaying unwanted `%%`.

### 2.3 Call Site Inspection
The call sites in the application code confirm that all 5 keys are resolved without format arguments:
* **`WorkoutDetails.kt` (lines 383 & 385)**:
  ```kotlin
  val statusSubtitle = if (data.isAerobicDecouplingHighDrift) {
      stringResource(R.string.workout_decoupling_drift)
  } else {
      stringResource(R.string.workout_decoupling_coupled)
  }
  ```
* **`WorkoutDetails.kt` (lines 431 & 434)**:
  ```kotlin
  MetricItem(
      iconRes = null,
      label = stringResource(R.string.workout_w_prime_min_pct_label),
      value = stringResource(R.string.workout_w_prime_min_pct_value_format, minPct),
      secondaryValue = if (data.isEmptyTankWarning) {
          stringResource(R.string.workout_w_prime_empty_tank)
      } else {
          null
      },
      ...
  )
  ```
* **`WPrimeBatteryLocalizationTest.kt` (lines 93–106)**:
  Validates `w_prime_warning_low_battery` across locales.

### 2.4 Why Existing Tests Did Not Catch the Defect
In `WPrimeBatteryLocalizationTest.kt` (lines 104–106):
```kotlin
assertTrue(
    "w_prime_warning_low_battery in $dirName must contain %",
    warning.contains("%")
)
```
The assertion evaluated `warning.contains("%")`. Because `"W\' Battery Low (<20%%)".contains("%")` is `true`, the test passed despite the erroneous double percent.
Furthermore, Android AAPT/AAPT2 compilation only raises errors for unescaped `%` characters when they mimic unescaped format specifiers (e.g. `%s`). Non-formatted strings with literal `%` preceded by spaces, numbers, or comparator symbols (`<5%`, `20%`, `Min %`) compile cleanly without warnings or errors. Existing production resources such as `units_percent` (`%`), `filter_battery_critical` (`Critical (≤ 20%)`), and `grade_legend_under_2` (`< 2%`) already demonstrate this.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Fix the double percent symbol (`%%` $\rightarrow$ `%`) across all 5 non-formatted string keys (`workout_decoupling_coupled`, `workout_decoupling_drift`, `workout_w_prime_min_pct_label`, `workout_w_prime_empty_tank`, `w_prime_warning_low_battery`) across all 9 application locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) (45 entries total).
  2. Tighten `WPrimeBatteryLocalizationTest.kt` to assert that `w_prime_warning_low_battery` contains `%` and does **not** contain `%%`.
  3. Introduce a general, repository-wide automated contract test (`LocalizationPercentIntegrityTest.kt`) verifying across all 9 locales that no `<string>` resource lacking format specifiers contains `%%`.
  4. Ensure 100% clean-room test pass rate (`./gradlew testDebugUnitTest`).

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Modifying string resources containing genuine format placeholders (`%1$d%%`, `%1$.1f%%`, etc.), where `%%` is legitimately required for `String.format`.
  2. Altering UI layouts, typography, styling, or component structure in `WorkoutDetails.kt` or `WPrimeBatteryGaugeBar.kt`.
  3. Changing database schemas, DTO models, or training telemetry math algorithms.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  Refines `REQ-UI-404.3` & `REQ-UI-404.4` (*AltitudeRow + MetricItem Grouped Row Layout for Power and Physiology Metrics in WorkoutDetails*, introduced in Sprint `2026-42.8` via `ATT-3930`) and `REQ-UI-416.5` (*Real-time W' Balance (W-Prime Battery) expenditure and recovery tracking*, introduced in Sprint `2026-42.8` via `ATT-1296`).
  Target: `app/src/main/res/values*/strings.xml`.

* **Historical Origin & Commit Trace**:
  - `ATT-3930` (commit `1bd468bf`): Added `workout_decoupling_coupled`, `workout_decoupling_drift`, `workout_w_prime_min_pct_label`, and `workout_w_prime_empty_tank`.
  - `ATT-1296` (commit `f519df49`): Added `w_prime_warning_low_battery`.

* **Root Reason for Existing Formulation**:
  When drafting the string resources, the author copied patterns from neighboring formatted strings (`workout_decoupling_pct_format` = `%1$.1f%%`, `w_prime_battery_pct` = `%1$d%%`) or presumed that Android resource XML universally requires escaping `%` as `%%`. Notably, `REQ-UI-416.5` in `docs/requirements.md` originally specified `w_prime_warning_low_battery` as `W\' Battery Low (<20%)` with a single `%`, confirming that `%%` was an unintended copy-paste implementation artifact rather than an intentional design choice.

* **Preservation of Core Invariants**:
  Replacing `%%` with `%` in these 5 non-formatted strings leaves the semantic label text, translations, and punctuation completely intact while eliminating the erroneous second percent sign on the UI. All formatted strings remain untouched. AAPT2 parses single `%` in these non-format strings without error, and all UI composables continue to receive the expected localized text.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 XML String Resource Modifications (All 9 Locales)
For each of the 9 locale directories (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`):
1. `workout_decoupling_coupled`: Replace `<5%%` with `<5%`
2. `workout_decoupling_drift`: Replace `≥5%%` with `≥5%`
3. `workout_w_prime_min_pct_label`: Replace `Min %%` / `Min. %%` / `Mín. %%` / `最小 %%` with `Min %` / `Min. %` / `Mín. %` / `最小 %`
4. `workout_w_prime_empty_tank`: Replace `<10%%` with `<10%`
5. `w_prime_warning_low_battery`: Replace `<20%%` with `<20%`

### 5.2 Unit Test Hardening & Contract Enforcement
1. **Update `WPrimeBatteryLocalizationTest.kt`**:
   Add `assertFalse("w_prime_warning_low_battery in $dirName must not contain %%", warning.contains("%%"))`.
2. **Author `LocalizationPercentIntegrityTest.kt`**:
   Parse all `strings*.xml` across all 9 locales. For every `<string>` element:
   - If the text contains `%%`:
     - Assert that the string contains at least one standard format specifier (`%1$`, `%s`, `%d`, etc.).
     - If it does not contain format specifiers, fail the test with a descriptive message identifying the key and file.
   - Assert that the 5 target keys across all 9 locales contain a single `%` and do not contain `%%`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features and unit tests.
  2. 100% 9-language translation parity across all locales.
  3. Formatted string resources with format placeholders (`%1$...`) retain `%%` for `String.format`.
  4. Parent ticket Human Decision Gate remains strictly enforced.

* **Risk Rating**: **LOW**
  * Justification: Purely declarative XML string resource corrections (45 entries across 9 XML files) and unit test contract verification. Zero Kotlin UI changes, zero backend business logic changes, zero database migrations, zero concurrency or lifecycle interactions.
