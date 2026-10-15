# Stage 3: Implementation Plan - ATT-3188: Calculate training zone boundaries automatically from FTP and Heart Rate baselines

**Ticket**: [ATT-3188](https://rainerblind.atlassian.net/browse/ATT-3188)  
**Sub-task**: [ATT-3612](https://rainerblind.atlassian.net/browse/ATT-3612) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*[Epic] Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Requirement Mapping**: `REQ-UI-373` (*Automatic Training Zone Boundary Calculation from FTP and Heart Rate Baselines*)  
**Test Mapping**: `TST-UI-336` (*Automatic Training Zone Boundary Calculation from FTP and Heart Rate Baselines Verification*)  
**Branch**: `feature/ATT-3188`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Problem Description & Background

Athletes currently configure physiological training zones in `ZonesSettingsActivity.kt` through raw, unassisted cutoff entry across 6 power boundaries (established in `REQ-UI-370`) and 4 heart rate boundaries. This requires off-device arithmetic, creates potential for non-monotonic boundary inversions, and lacks integration with physiological baselines (Functional Threshold Power, Maximum Heart Rate, Resting Heart Rate).

This implementation plan specifies the construction of the automated calculation feature under ASPICE SWE.2:
1. Pure Kotlin mathematical modeling (`ZoneCalculationMath.kt`) implementing Dr. Andy Coggan's 7-zone power model and Karvonen/HRmax heart rate models with strict half-up rounding, boundary rationalization, and physical upper bounding ($2500\text{ W}$).
2. Two-layer monotonicity enforcement (`ZoneValidator.kt`) with strict minimum integer step $\Delta \ge 1$ ($Z_n \ge Z_{n-1} + 1$).
3. Single-transaction ACID persistence in Jetpack DataStore (`SettingsDataStore.kt`) with explicit algorithm versioning (`KEY_ZONE_CALC_VERSION = 1`) and UI mutation locking (`REQ-UI-373.10`) preventing re-entrant corruption.
4. Formal data integrity policy preserving historical completed workout records in SQLite (`WorkoutSummaries.db`).
5. Material 3 calculation modal dialog (`ZoneCalculatorDialog.kt`) with reactive validation, explicit destructive overwrite confirmation, and 9-language translation parity.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-373` (*Automatic Training Zone Boundary Calculation from FTP and Heart Rate Baselines*)
  * `REQ-UI-373.1`: Cycling Power Coggan 7-Zone Calculation & Zone 7 Ceiling ($2500\text{ W}$)
  * `REQ-UI-373.2`: Heart Rate Calculation (% of HRmax & Karvonen) & Invariant Error Messages
  * `REQ-UI-373.3` & `REQ-UI-373.4`: Two-Layer Monotonicity Validation & Step Increment ($Z_n \ge Z_{n-1} + 1$)
  * `REQ-UI-373.5` & `REQ-UI-373.6`: Single-Transaction ACID Atomicity Guarantee & Algorithm Versioning (`zone_calc_version`)
  * `REQ-UI-373.7`: Formal Data Integrity Policy & Historical Analytics Immutability
  * `REQ-UI-373.8`: Customization Lifecycle, Manual-to-Auto Transitions & Destructive Overwrite Warning Modal
  * `REQ-UI-373.9`: Locale-Aware Number Parsing & 9-Language Localization Token Parity
  * `REQ-UI-373.10`: UI Mutation Lock & Idempotent Transaction Concurrency Guard
* **Test Mapping**: `TST-UI-336` (*Automatic Training Zone Boundary Calculation Verification*)
  * `[TST-UI-336.1]`: Pure Mathematical Model, Coggan Rounding & Zone 7 Ceiling (`ZoneCalculationMathTest.kt`)
  * `[TST-UI-336.2]`: Karvonen & % HRmax Mathematical Models & Invariant Guards (`ZoneCalculationMathTest.kt`)
  * `[TST-UI-336.3]`: Two-Layer Monotonicity Validation & Step Increment (`ZoneValidatorTest.kt`)
  * `[TST-UI-336.4]`: Single-Transaction ACID Atomicity, Versioning, Interruption Rollback & DataStore Baselines (`SettingsDataStoreBaselinesTest.kt`)
  * `[TST-UI-336.5]`: Locale-Aware Number Parsing (`ZoneNumberParserTest.kt`)
  * `[TST-UI-336.6]`: Destructive Overwrite, Error Copy & Dialog Contract Verification (`ZoneCalculatorDialogContractTest.kt`)
  * `[TST-UI-336.7]`: 9-Language Localization & Format Specifier Token Audit (`ZoneCalculatorLocalizationTest.kt`)
  * `[TST-UI-336.8]`: Clean-Room Full Suite Regression Execution (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Historical Data Immutability**: Modifying athlete baselines or recalculating zones applies strictly prospectively to future activities. Past completed workout records and aggregates in SQLite (`WorkoutSummaries.db`, `Extrema.db`, `WorkoutSamples.db`) are immutable and MUST NEVER be recalculated retroactively.
2. **Canonical SI Storage**: Baselines and cutoffs stored strictly in SI units (Watts for power, BPM for heart rate).
3. **Finite Zone 7 Upper Bound**: Zone 7 upper bound is capped at physical ceiling $2500\text{ W}$ (zero `Infinity` values stored in DataStore).
4. **Strict Integer Monotonicity**: $Z_{n\text{max}} \ge Z_{n-1\text{max}} + 1$ enforced across UI and domain tiers. Adjacent zones cannot share identical cutoffs.
5. **Algorithm Versioning Invariant**: Calculation logic version is stored via `KEY_ZONE_CALC_VERSION = intPreferencesKey("zone_calc_version")` (`CURRENT_ZONE_CALC_VERSION = 1`). This provides schema longevity, preventing model drift and enabling future algorithm migrations without silent corruption.
6. **Zero SQLite Migrations**: SQLite schema version remains unaltered; baseline keys added purely to DataStore.
7. **Thread Safety & Dispatcher Confinement**: DataStore writes executed via Kotlin Coroutines on `Dispatchers.IO`; UI updates dispatched on `Dispatchers.Main`.
8. **Clean-Room Test Pass Rate**: 100% test pass rate across the full application unit test suite (0 regressions).
9. **ASPICE Governance**: Production code implementation is strictly deferred to Stage 4 after Gate 3 sign-off. Parent ticket completion remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes (SWE.2 Component Design)

### Component 1: Pure Mathematical Engine (`ZoneCalculationMath.kt`)
* **Package**: `com.atrainingtracker.trainingtracker.zones`
* **Layer**: Pure Domain / Mathematics (zero Android dependencies, headless JVM testable)
* **API Contracts**:
  ```kotlin
  object ZoneCalculationMath {
      const val MIN_FTP = 50
      const val MAX_FTP = 600
      const val MIN_HR_MAX = 80
      const val MAX_HR_MAX = 240
      const val MIN_HR_REST = 30
      const val MAX_HR_REST = 120
      const val MIN_KARVONEN_RESERVE = 20
      const val ZONE_7_POWER_CEILING = 2500

      fun calculateCogganPowerZones(ftp: Int): PowerZoneThresholds
      fun calculateHeartRateZonesMax(hrMax: Int): HeartRateZoneThresholds
      fun calculateKarvonenHeartRateZones(hrMax: Int, hrRest: Int): HeartRateZoneThresholds
  }
  ```
* **Behavior**:
  * Applies `Math.round(percent * baseline).toInt()` with rationalization loop: $Z_n = \max(Z_n, Z_{n-1} + 1)$.
  * Coggan percentages: 55%, 75%, 90%, 105%, 120%, 150%.
  * Zone 7 is defined as $[Z_6 + 1\text{ W}, 2500\text{ W}]$. If $Z_6 \ge 2500$, throws `IllegalArgumentException`.
  * Karvonen enforces $HR_{\max} > HR_{\text{rest}}$ and $HR_{\max} - HR_{\text{rest}} \ge 20$, throwing `IllegalArgumentException` on violation.

### Component 2: Centralized Blocking Validation Architecture (`ZoneValidator.kt`)
* **Package**: `com.atrainingtracker.trainingtracker.zones`
* **Layer**: Domain Validation Guard
* **API Contracts**:
  ```kotlin
  object ZoneValidator {
      fun validatePowerZoneStack(cutoffs: List<Int>)
      fun validateHeartRateZoneStack(cutoffs: List<Int>)
      fun validateFtp(ftp: Int): ValidationResult
      fun validateHrBaselines(hrMax: Int, hrRest: Int?): ValidationResult
  }

  sealed class ValidationResult {
      object Valid : ValidationResult()
      data class Invalid(val errorResId: Int) : ValidationResult()
  }
  ```
* **Behavior**:
  * Validates $0 < Z_1 < Z_2 < \dots < Z_N \le 2500$ with $Z_n \ge Z_{n-1} + 1$.
  * Throws `IllegalArgumentException` if any cutoff is out of bounds or non-monotonic.

### Component 3: Locale-Aware Number Parser (`ZoneNumberParser.kt`)
* **Package**: `com.atrainingtracker.trainingtracker.zones`
* **Layer**: Utility / Input Sanitization
* **API Contracts**:
  ```kotlin
  object ZoneNumberParser {
      fun parseIntOrNull(raw: String): Int?
  }
  ```
* **Behavior**:
  * Strips spaces, non-breaking spaces (`\u00A0`), grouping dots.
  * Handles comma decimal separator gracefully by truncating fractional parts.
  * Returns null on non-numeric or empty input without throwing.

### Component 4: DataStore Repository & Concurrency Control (`SettingsDataStore.kt`)
* **Package**: `com.atrainingtracker.trainingtracker.data`
* **Layer**: Persistence / Infrastructure
* **New Preference Keys & Versioning**:
  ```kotlin
  const val CURRENT_ZONE_CALC_VERSION = 1

  val KEY_ZONE_CALC_VERSION = intPreferencesKey("zone_calc_version")
  val KEY_CYCLING_FTP = intPreferencesKey("cycling_ftp")
  val KEY_CYCLING_FTP_UPDATED_AT = longPreferencesKey("cycling_ftp_updated_at")
  val KEY_PWR_BIKE_ZONES_CUSTOMIZED = booleanPreferencesKey("pwr_bike_zones_customized")
  val KEY_HR_MAX = intPreferencesKey("hr_max")
  val KEY_HR_REST = intPreferencesKey("hr_rest")
  val KEY_HR_CALC_METHOD = stringPreferencesKey("hr_calc_method")
  val KEY_HR_ZONES_CUSTOMIZED = booleanPreferencesKey("hr_zones_customized")
  ```
* **Mutators & Transaction Semantics**:
  * `suspend fun saveCalculatedPowerZones(ftp: Int, cutoffs: PowerZoneThresholds)`:
    * Executed inside a single atomic `dataStore.edit { }` block writing `cycling_ftp`, `cycling_ftp_updated_at = System.currentTimeMillis()`, `pwr_bike_zones_customized = false`, `zone_calc_version = CURRENT_ZONE_CALC_VERSION`, and all 6 cutoffs.
  * `suspend fun saveCalculatedHeartRateZones(hrMax: Int, hrRest: Int?, method: String, cutoffs: HeartRateZoneThresholds, zoneType: ZoneType)`:
    * Single atomic `dataStore.edit { }` block writing baselines, flag `false`, version, and cutoffs.
  * `suspend fun savePowerZoneMax(zoneIndex: Int, value: Int)`:
    * Validates stack via `ZoneValidator.validatePowerZoneStack()`.
    * Atomically updates cutoff and sets `pwr_bike_zones_customized = true`.
* **Atomic Failure & Crash Invariant**:
  * Jetpack DataStore performs atomic file write via temporary file swap (`.tmp` write followed by OS atomic rename). If the process is killed or an exception is thrown before commit, the `.tmp` file is unlinked, leaving the original DataStore file completely uncorrupted (all-or-nothing rollback).
* **Synchronous Java Helper (`SettingsDataStoreJavaHelper.kt`)**:
  * `getFtp(context: Context): Integer?` for backward-compatible Java service telemetry consumption.

### Component 5: Customization Lifecycle & State Transition Matrix
* **Auto-to-Manual Transition**:
  * When an athlete edits an individual zone cutoff in `ZonesSettingsActivity.kt`, the existing cutoffs serve as the starting base.
  * Upon saving, the preference `pwr_bike_zones_customized` / `hr_zones_customized` transitions to `true`.
  * The header badge updates to "Benutzerdefiniert" / "Customized".
* **Manual-to-Auto Transition**:
  * If an athlete taps "Zonen berechnen" when `is_customized == true`, the calculator dialog loads existing baseline FTP.
  * Tapping "Apply" / "Übernehmen" detects `is_customized == true` and opens a modal confirmation dialog:
    *"Achtung: Dies überschreibt alle manuell festgelegten Zonen mit den Standard-Coggan-Zonen für FTP %d W. Möchtest du fortfahren?"*.
  * **If Cancelled**: Dialog dismisses; manual cutoffs remain 100% untouched; 0 DataStore writes occur.
  * **If Confirmed**: `saveCalculatedPowerZones()` writes the standard Coggan zones, sets `pwr_bike_zones_customized = false`, and updates `cycling_ftp_updated_at`.
* **Stale Baseline Age Advisory**:
  * Below the FTP baseline readout in settings, the UI displays the formatted date of last update (`DateUtils.formatDateTime`).
  * If `System.currentTimeMillis() - cycling_ftp_updated_at > 180L * 24 * 60 * 60 * 1000` (older than 6 months), an informational chip *"Kalibrierung empfohlen"* is displayed.

### Component 6: UI & Dialog Workflow (`ZoneCalculatorDialog.kt` & `ZonesSettingsActivity.kt`)
* **Package**: `com.atrainingtracker.trainingtracker.ui.settings`
* **Layer**: Presentation (Jetpack Compose / Material 3)
* **UI Consistency (Rule 23)**:
  * **Reference screen / component**: `AdvancedTuningDialog.kt` and `EditSportTypeDialog.kt`.
  * **Reused components**: `ModalBottomSheetScaffold` / Material 3 `AlertDialog`, `OutlinedTextField`, `FilterChip` (for HR method selection: % HRmax vs Karvonen), `Button`, `TextButton`.
  * **Theme tokens**: `MaterialTheme.colorScheme.primary`, `MaterialTheme.colorScheme.error`, `RoundedCornerShape(16.dp)`, `spacing: 16.dp`.
  * **Interactive Preview**: Live zone preview displaying calculated cutoffs ($Z_1..Z_7$ or $Z_1..Z_5$) before committing.
  * **Destructive Overwrite Modal**: Confirmation dialog with title `zone_calc_overwrite_warning_title` and message `zone_calc_overwrite_warning_message`.
  * **Mutation Lock (`REQ-UI-373.10`)**: `isSaving` state disables action buttons with circular progress during async write.
  * **Customization Status Badge**: `ZonesSettingsActivity.kt` header renders "Berechnet" (primary color) or "Benutzerdefiniert" (outline) based on `pwr_bike_zones_customized` / `hr_zones_customized`.

### Component 7: Localization Parity (9 Locales)
* Define 23 new string resources across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
* Exact format specifier token parity (`%d`, `%s`, `%1$d`) enforced by `ZoneCalculatorLocalizationTest.kt`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pure Domain Mathematics Engine (`ZoneCalculationMath.kt`)
* Create `app/src/main/java/com/atrainingtracker/trainingtracker/zones/ZoneCalculationMath.kt`.
* Implement Coggan 7-zone calculation, Karvonen HRR calculation, % HRmax calculation, boundary rationalization, and 2500W ceiling.
* Create unit test: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/ZoneCalculationMathTest.kt` (`[TST-UI-336.1]`, `[TST-UI-336.2]`).
* Verify with: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.ZoneCalculationMathTest"`

### Step 2: Validation Guard & Locale Parser (`ZoneValidator.kt` & `ZoneNumberParser.kt`)
* Create `app/src/main/java/com/atrainingtracker/trainingtracker/zones/ZoneValidator.kt`.
* Create `app/src/main/java/com/atrainingtracker/trainingtracker/zones/ZoneNumberParser.kt`.
* Create unit tests:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/zones/ZoneValidatorTest.kt` (`[TST-UI-336.3]`)
  * `app/src/test/java/com/atrainingtracker/trainingtracker/zones/ZoneNumberParserTest.kt` (`[TST-UI-336.5]`)
* Verify with: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.ZoneValidatorTest" --tests "com.atrainingtracker.trainingtracker.zones.ZoneNumberParserTest"`

### Step 3: DataStore Repository Extension, Algorithm Versioning & Interruption Rollback
* Update `app/src/main/java/com/atrainingtracker/trainingtracker/data/SettingsDataStore.kt`:
  * Add keys (`KEY_ZONE_CALC_VERSION = 1`, `cycling_ftp`, `cycling_ftp_updated_at`, `pwr_bike_zones_customized`, etc.).
  * Add atomic batch save methods (`saveCalculatedPowerZones`, `saveCalculatedHeartRateZones`).
  * Update `savePowerZoneMax` to set `pwr_bike_zones_customized = true`.
* Update `app/src/main/java/com/atrainingtracker/trainingtracker/data/SettingsDataStoreJavaHelper.kt`:
  * Add `getFtp(context: Context): Integer?`.
* Create unit test: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/SettingsDataStoreBaselinesTest.kt` (`[TST-UI-336.4]`):
  * Test atomic batch save and retrieval.
  * Test manual cutoff edit sets `pwr_bike_zones_customized = true`.
  * Test transition from customized back to calculated.
  * Test interrupted/failed transaction rollback leaving previous state intact.
* Verify with: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.SettingsDataStoreBaselinesTest"`

### Step 4: 9-Language Localization Resources
* Add 23 zone calculation strings to:
  * `app/src/main/res/values/strings.xml` (EN)
  * `app/src/main/res/values-de/strings.xml` (DE)
  * `app/src/main/res/values-es/strings.xml` (ES)
  * `app/src/main/res/values-fr/strings.xml` (FR)
  * `app/src/main/res/values-it/strings.xml` (IT)
  * `app/src/main/res/values-ja/strings.xml` (JA)
  * `app/src/main/res/values-nl/strings.xml` (NL)
  * `app/src/main/res/values-pl/strings.xml` (PL)
  * `app/src/main/res/values-pt/strings.xml` (PT)
* Create localization audit test: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/ZoneCalculatorLocalizationTest.kt` (`[TST-UI-336.7]`).
* Verify with: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.ZoneCalculatorLocalizationTest"`

### Step 5: Compose UI Component (`ZoneCalculatorDialog.kt`) & Activity Integration (`ZonesSettingsActivity.kt`)
* Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/ZoneCalculatorDialog.kt`.
  * Support Power (FTP) and Heart Rate (% HRmax / Karvonen).
  * Keystroke-level reactive validation with localized error messages.
  * Live zone preview.
  * Overwrite confirmation modal.
  * Mutation lock (`isSaving`) preventing concurrent transactions.
* Update `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/ZonesSettingsActivity.kt`:
  * Integrate "Zonen berechnen" action button.
  * Integrate customization status badge ("Berechnet" / "Benutzerdefiniert").
  * Display last updated calibration date.
  * Wire dialog presentation and UDF state collection.
  * Enforce Layer 1 monotonicity ($Z_{n\text{max}} \ge Z_{n-1\text{max}} + 1$) in manual editing fields.
* Create UI contract test: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/ZoneCalculatorDialogContractTest.kt` (`[TST-UI-336.6]`).
* Verify with: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.ZoneCalculatorDialogContractTest"`

### Step 6: Full Clean-Room Regression Suite Execution
* Run `./gradlew testDebugUnitTest` verifying 100% pass rate across all 436+ test classes (`[TST-UI-336.8]`).

---

## 6. Verification & Rollback Plan

* **Verification**:
  * Each atomic step runs targeted JVM unit tests (5–15s).
  * Stage 5 clean-room regression runs `./gradlew testDebugUnitTest` with 100% pass rate.
* **Rollback**:
  * All work is isolated on `feature/ATT-3188`.
  * In case of any unresolvable defect, git branch isolation permits complete, clean rollback without polluting `sprint/2026-42.4` or `develop`.
