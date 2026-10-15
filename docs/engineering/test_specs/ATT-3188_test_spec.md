# Stage 2: Requirement & Test Specification - ATT-3188: Calculate training zone boundaries automatically from FTP and Heart Rate baselines

**Ticket**: [ATT-3188](https://rainerblind.atlassian.net/browse/ATT-3188)  
**Sub-task**: [ATT-3611](https://rainerblind.atlassian.net/browse/ATT-3611) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*[Epic] Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Requirement Mapping**: `REQ-UI-373` (*Automatic Training Zone Boundary Calculation from FTP and Heart Rate Baselines*)  
**Test Spec ID**: `TST-UI-336`  
**Branch**: `feature/ATT-3188`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Requirement Specification (REQ-UI-373)

### 1.1 Problem Statement & Rationale
Currently, the Training Zones configuration screen (`ZonesSettingsActivity.kt`) requires athletes to enter 6 power zone cutoffs (for the 7-zone Coggan model established in `REQ-UI-370`) and 4 heart rate zone cutoffs (for the 5-zone HR model) manually into raw text fields. Users have no built-in method to calculate their zone boundaries from recognized physiological benchmarks: Functional Threshold Power (FTP) for cycling power, and Maximum Heart Rate ($HR_{\max}$) or Resting Heart Rate ($HR_{\text{rest}}$) for heart rate.

Athletes are forced to perform calculations externally on paper or spreadsheets and manually transfer raw numerical thresholds. Manual data entry is tedious, error-prone, and frequently results in non-monotonic zone configurations (e.g. $Z_3 \ge Z_4$) or inconsistent zone spans.

`REQ-UI-373` resolves this by introducing automated, deterministic zone calculation directly within the zones settings workflow, while strictly preserving manual customization, guaranteeing single-transaction ACID atomicity in DataStore, enforcing two-layer boundary inversion protection, guaranteeing historical analytics immutability, and maintaining full 9-language localization parity.

---

### 1.2 Functional & Architectural Requirements

#### 1.2.1 Pure Mathematical Calculation Engines (`ZoneCalculationMath.kt`)
1. **Cycling Power Model (Coggan 7-Zone Framework) & Zone 7 Ceiling** (`REQ-UI-373.1`):
   * The system SHALL compute cycling power zone upper boundaries from Functional Threshold Power (FTP, in Watts) using the canonical Coggan percentages:
     * Zone 1 (Active Recovery): Cutoff = $\text{round}(0.55 \times \text{FTP})$
     * Zone 2 (Endurance): Cutoff = $\text{round}(0.75 \times \text{FTP})$
     * Zone 3 (Tempo): Cutoff = $\text{round}(0.90 \times \text{FTP})$
     * Zone 4 (Lactate Threshold): Cutoff = $\text{round}(1.05 \times \text{FTP})$
     * Zone 5 (VO2 Max): Cutoff = $\text{round}(1.20 \times \text{FTP})$
     * Zone 6 (Anaerobic Capacity): Cutoff = $\text{round}(1.50 \times \text{FTP})$
     * Zone 7 (Neuromuscular Power): Lower bound is $Z_{6\text{max}} + 1\text{ W}$, and upper bound is capped at physical maximum $2500\text{ Watts}$.
   * The system SHALL apply deterministic half-up rounding (`Math.round(percent * ftp).toInt()`) with boundary rationalization guaranteeing strictly monotonic ascending boundaries ($Z_{n\text{max}} \ge Z_{n-1\text{max}} + 1$).
   * **Zone 7 Physical Upper Ceiling & Span Guarantee**:
     * The upper bound of Zone 7 is strictly clamped to $2500\text{ Watts}$ (finite storage invariant; zero `Infinity` values stored in DataStore or telemetry accumulators).
     * Allowed FTP input range SHALL be strictly constrained to $[50, 600]\text{ Watts}$. For maximum FTP = $600\text{ W}$, Zone 6 cutoff evaluates to $900\text{ W}$, leaving a valid Zone 7 span $[901\text{ W}, 2500\text{ W}]$.
     * If an arbitrary or malformed input attempts to set $Z_{6\text{max}} \ge 2500\text{ W}$, `ZoneCalculationMath` and `ZoneValidator` MUST throw `IllegalArgumentException("Zone 6 cutoff must be strictly less than Zone 7 physical ceiling (2500 W)")`, strictly guaranteeing a non-empty, valid interval for Zone 7.

2. **Heart Rate Models (% of HRmax and Karvonen HRR) & Primary Invariants** (`REQ-UI-373.2`):
   * **Model A (% of HRmax)**:
     * Zone 1: Cutoff = $\text{round}(0.59 \times HR_{\max})$
     * Zone 2: Cutoff = $\text{round}(0.69 \times HR_{\max})$
     * Zone 3: Cutoff = $\text{round}(0.79 \times HR_{\max})$
     * Zone 4: Cutoff = $\text{round}(0.89 \times HR_{\max})$
     * Zone 5: Upper bound = $HR_{\max}$.
   * **Model B (Karvonen Heart Rate Reserve)**:
     * Target HR = $\text{round}(HR_{\text{rest}} + (HR_{\max} - HR_{\text{rest}}) \times \text{intensity}\%)$.
     * Zone 1 (50-59% HRR): Cutoff = $\text{round}(HR_{\text{rest}} + 0.59 \times (HR_{\max} - HR_{\text{rest}}))$
     * Zone 2 (60-69% HRR): Cutoff = $\text{round}(HR_{\text{rest}} + 0.69 \times (HR_{\max} - HR_{\text{rest}}))$
     * Zone 3 (70-79% HRR): Cutoff = $\text{round}(HR_{\text{rest}} + 0.79 \times (HR_{\max} - HR_{\text{rest}}))$
     * Zone 4 (80-89% HRR): Cutoff = $\text{round}(HR_{\text{rest}} + 0.89 \times (HR_{\max} - HR_{\text{rest}}))$
     * Zone 5 (90-100% HRR): Upper bound = $HR_{\max}$.
   * Allowed baseline input ranges SHALL be strictly constrained: $HR_{\text{rest}} \in [30, 120]\text{ bpm}$, $HR_{\max} \in [80, 240]\text{ bpm}$.
   * **Primary Karvonen Invariant Check & Explicit Error Messaging**:
     * The system MUST enforce $HR_{\max} > HR_{\text{rest}}$ and $HR_{\max} - HR_{\text{rest}} \ge 20\text{ bpm}$.
     * If $HR_{\max} \le HR_{\text{rest}}$, the UI SHALL display localized error message `zone_calc_error_hr_max_greater_rest` (*"HFmax muss größer als HFruhe sein"* / *"HRmax must be greater than HRrest"*) and disable calculation.
     * If $HR_{\max} - HR_{\text{rest}} < 20\text{ bpm}$, the UI SHALL display localized error message `zone_calc_error_karvonen_delta` (*"HFmax muss mindestens 20 bpm über HFruhe liegen"* / *"HRmax must be at least 20 bpm higher than HRrest"*) and disable calculation.
     * At the pure domain math layer, `ZoneCalculationMath.calculateKarvonenHeartRateZones` MUST throw `IllegalArgumentException` on any violation.

#### 1.2.2 Two-Layer Monotonicity Validation & Boundary Inversion Prevention (`ZoneValidator.kt`)
1. **Layer 1 (UI Reactive Barrier & Boundary Step)** (`REQ-UI-373.3`):
   * During manual cutoff editing in `ZonesSettingsActivity.kt`, validation SHALL execute synchronously on every keystroke (`onValueChange`).
   * The UI SHALL enforce $Z_{n\text{max}} \ge Z_{n-1\text{max}} + 1$ and $Z_{n\text{max}} \le Z_{n+1\text{max}} - 1$.
   * A minimum integer delta $\Delta = 1$ is mandatory: adjacent zones cannot share the same cutoff, guaranteeing that Zone $n+1$ starts strictly at $Z_{n\text{max}} + 1$.
   * If a boundary inversion or duplicate value occurs, the field SHALL be marked with `isError = true`, an inline localized error message (*"Wert muss zwischen %d und %d liegen"*) SHALL be displayed, and the Save action button SHALL be disabled.
2. **Layer 2 (Domain-Level Blocking Guard)** (`REQ-UI-373.4`):
   * `ZoneValidator.validatePowerZoneStack(cutoffs)` and `validateHeartRateZoneStack(cutoffs)` SHALL validate that all cutoffs fall within valid physical bounds and are strictly monotonically ascending ($0 < Z_1 < Z_2 < \dots < Z_N \le 2500$ with $Z_i \ge Z_{i-1} + 1$).
   * Any violation at the repository/persistence layer MUST throw `IllegalArgumentException`, preventing persistence of inverted boundaries.

#### 1.2.3 Single-Transaction ACID Atomicity & DataStore Schema (`SettingsDataStore.kt`)
1. **Atomic Batch Persistence** (`REQ-UI-373.5`):
   * Persisting calculated zones SHALL occur inside a single atomic `dataStore.edit { }` block.
   * The baseline value (`cycling_ftp`, `hr_max`, `hr_rest`), baseline timestamp (`cycling_ftp_updated_at`), customization flag (`pwr_bike_zones_customized`, `hr_zones_customized`), and all zone cutoffs SHALL be written within this single transaction.
   * The UI layer SHALL observe state via Unidirectional Data Flow (`collectAsState()`) directly from DataStore, eliminating local disconnected mutable state and preventing split-brain conditions.
2. **Preference Keys Schema** (`REQ-UI-373.6`):
   * Power: `cycling_ftp: Int`, `cycling_ftp_updated_at: Long`, `pwr_bike_zones_customized: Boolean`, `pwr_bike_zone1_max` .. `pwr_bike_zone6_max: Int`.
   * Heart Rate: `hr_max: Int`, `hr_rest: Int`, `hr_calc_method: String`, `hr_zones_customized: Boolean`, `hr_bike_zone1_max` .. `hr_bike_zone4_max: Int`, `hr_run_zone1_max` .. `hr_run_zone4_max: Int`.
3. **UI Mutation Lock & Idempotent Transaction Concurrency Guard** (`REQ-UI-373.10`):
   * The UI layer SHALL maintain `isSaving: Boolean` state during transaction execution.
   * While an asynchronous DataStore write is in flight, the "Apply" / "Save" action button SHALL enter a disabled loading state, strictly preventing re-entrant or concurrent invocations.
   * If an unhandled exception or process termination occurs during `dataStore.edit { }`, DataStore's atomic temporary file write and filesystem rename guarantee that the transaction is cleanly aborted with zero partial disk corruption (native rollback).

#### 1.2.4 Formal Data Integrity Policy & Historical Analytics Immutability (`REQ-UI-373.7`)
1. **Prospective-Only Historical Policy**:
   * Modifying athlete baselines or zone profiles applies strictly prospectively to future workouts.
   * Past completed workout records and aggregates stored in SQLite (`WorkoutSummaries.db`, `Extrema.db`, `WorkoutSamples.db`) are immutable and MUST NEVER be retroactively modified or recalculated.
2. **Intra-Session Historical Fidelity**:
   * Mid-workout baseline or zone adjustments apply strictly to incoming telemetry ticks ($t \ge t_{\text{change}}$). Ticks recorded prior to the change maintain original zone categorization.

#### 1.2.5 Customization Lifecycle & Destructive Overwrite Warning (`REQ-UI-373.8`):
1. **State Machine**:
   * Profiles default to `CALCULATED` (`is_customized = false`) when populated via automated calculation.
   * Editing any single cutoff manually transitions the entire profile to `CUSTOMIZED` (`is_customized = true`).
2. **Destructive Overwrite Modal**:
   * If an athlete triggers automated recalculation when `is_customized == true`, the UI SHALL display a modal confirmation warning:
     *"Achtung: Dies überschreibt alle manuell festgelegten Zonen mit den Standard-Coggan-Zonen für FTP %d W. Möchtest du fortfahren?"*.
   * If the athlete cancels, custom zones SHALL remain 100% untouched. If confirmed, the calculated zones SHALL be applied and `is_customized` set to `false`.

#### 1.2.6 Locale-Aware Number Parsing & Resource Parity (`REQ-UI-373.9`)
1. **Locale Number Parsing (`ZoneNumberParser.kt`)**:
   * User numeric inputs SHALL be parsed using `ZoneNumberParser.parseIntOrNull()`, handling comma (`,`), period (`.`), and non-breaking space grouping characters gracefully across all device locales.
2. **9-Language Localization & Format Specifier Parity**:
   * All user-facing strings, dialog titles, button labels, warnings, and validation error messages MUST maintain 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, and PT.
   * The system SHALL guarantee exact matching of format specifiers (count, order, and type like `%d`, `%s`, `%1$d`) across all translations.

---

### 1.3 Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   * Net-new requirement `REQ-UI-373`. Amends and refines `REQ-UI-370` (*Decoupled Training Zone Architecture & 7-Zone Coggan Cycling Power Model*, Sprint 2026-42.4, ATT-3184) and complements `REQ-PRO-022` (*Requirement Archaeology & Chesterton's Fence Modification Hurdle*).
   * Targets `ZoneCalculationMath.kt`, `ZoneValidator.kt`, `ZoneNumberParser.kt`, `ZoneCalculatorDialog.kt`, `ZonesSettingsActivity.kt`, `SettingsDataStore.kt`, `SettingsDataStoreJavaHelper.kt`, and `res/values*/strings.xml`.
2. **Historical Origin & Commit Trace**:
   * Sprint 2026-40.5 (`ATT-1390`, commit `e3966ebf`) introduced manual zone configuration for 5 zones.
   * Sprint 2026-42.4 (`ATT-3184`, commit `0da639ba`) expanded power zones to the 7-zone Coggan model, but retained manual text entry for all 6 cutoffs.
   * Active Ticket `ATT-3188` (*Calculate training zone boundaries automatically from FTP and Heart Rate baselines*).
3. **Root Reason for Existing Formulation ("Why was this fence built?")**:
   * Previously, `ZonesSettingsActivity` solely supported manual cutoff entry because athlete baseline profiles (FTP, HRmax, HRrest) were not stored as discrete DataStore preferences. Cutoffs were treated as raw independent integer thresholds without an underlying physiological anchor.
4. **Preservation of Core Invariants ("Why is it safe to modify now?")**:
   * Manual cutoff customization is 100% preserved; athletes are never forced to use automated calculation.
   * Proportional fallback migration from `REQ-UI-370` remains active for legacy databases without FTP baselines.
   * Historical completed workout summaries in SQLite remain 100% immutable (prospective-only policy).
   * 100% unit test pass rate and backward-compatible DataStore schema.

---

### 1.4 Acceptance Criteria (Given-When-Then)

* **AC-1 (Power Zone Calculation from FTP & Zone 7 Bound)**:
  * *Given* an athlete entering an FTP of $250\text{ W}$ in the calculation dialog,
  * *When* tapping "Berechnen" / "Calculate",
  * *Then* the calculated preview displays: Z1 $\le 138\text{ W}$ (55%), Z2 $\le 188\text{ W}$ (75%), Z3 $\le 225\text{ W}$ (90%), Z4 $\le 263\text{ W}$ (105%), Z5 $\le 300\text{ W}$ (120%), Z6 $\le 375\text{ W}$ (150%), Z7 is $[376\text{ W}, 2500\text{ W}]$ (capped at physical ceiling $2500\text{ W}$).

* **AC-2 (Karvonen HR Zone Calculation & Explicit Invariant Error Messages)**:
  * *Given* an athlete entering $HR_{\max} = 190\text{ bpm}$ and $HR_{\text{rest}} = 50\text{ bpm}$ (HRR = $140\text{ bpm}$),
  * *When* calculating Karvonen zones,
  * *Then* boundaries resolve: Z1 $\le 133\text{ bpm}$ (59%), Z2 $\le 147\text{ bpm}$ (69%), Z3 $\le 161\text{ bpm}$ (79%), Z4 $\le 175\text{ bpm}$ (89%), Z5 $\le 190\text{ bpm}$.
  * *Given* an athlete entering $HR_{\max} = 60$ and $HR_{\text{rest}} = 50$ ($HR_{\max} - HR_{\text{rest}} = 10 < 20$),
  * *When* typing in the dialog,
  * *Then* the explicit error message `zone_calc_error_karvonen_delta` (*"HFmax muss mindestens 20 bpm über HFruhe liegen"*) is displayed, and calculation is disabled.
  * *Given* an athlete entering $HR_{\max} = 70$ and $HR_{\text{rest}} = 80$ ($HR_{\max} \le HR_{\text{rest}}$),
  * *When* typing in the dialog,
  * *Then* the explicit error message `zone_calc_error_hr_max_greater_rest` is displayed, and calculation is disabled.

* **AC-3 (Single-Transaction ACID Atomicity & Concurrency Lock)**:
  * *Given* calculated zones applied from the dialog,
  * *When* `SettingsDataStore` saves the boundaries,
  * *Then* FTP baseline, timestamp, customization flag (`false`), and all 6 cutoffs are written in a single `dataStore.edit { }` block with atomic file replacement, and the UI disables the action button with a loading indicator while saving.

* **AC-4 (Two-Layer Monotonicity Barrier on Manual Edit with +1 Step)**:
  * *Given* an athlete manually editing Zone 3 cutoff to $310\text{ W}$ while Zone 4 is $263\text{ W}$,
  * *When* typing in the text field,
  * *Then* Layer 1 flags the field with `isError = true`, shows error *"Wert muss zwischen %d und %d liegen"*, and disables the Save button.
  * *Given* an attempted write of adjacent cutoffs with zero delta ($Z_3 = 200, Z_4 = 200$),
  * *When* validated,
  * *Then* validation fails because $Z_4$ must be $\ge Z_3 + 1$.
  * *Given* an attempted programmatic write of non-monotonic cutoffs `[150, 140, 200, 250, 300, 350]`,
  * *When* calling `ZoneValidator.validatePowerZoneStack()`,
  * *Then* Layer 2 throws `IllegalArgumentException`.

* **AC-5 (Destructive Overwrite Confirmation)**:
  * *Given* an athlete with customized zones (`is_customized == true`),
  * *When* the athlete attempts to apply calculated zones from the dialog,
  * *Then* a modal confirmation dialog warns that custom zones will be overwritten.
  * *When* the athlete taps "Abbrechen" / "Cancel",
  * *Then* the dialog closes and custom zones remain untouched.

* **AC-6 (Historical Immutability Guarantee)**:
  * *Given* completed workouts in SQLite (`WorkoutSummaries.db`),
  * *When* FTP and zone cutoffs are recalculated and saved,
  * *Then* zero rows in `WorkoutSummaries.db` are altered or re-aggregated.

---

### 1.5 System Invariants
1. **Immutability of Historical SQLite Telemetry**: Completed activity records are never recalculated retroactively.
2. **Canonical SI Storage**: Baselines and cutoffs stored strictly in SI units (Watts and BPM).
3. **Finite Zone 7 Upper Bound**: Zone 7 upper bound is finite ($2500\text{ W}$ ceiling; zero `Infinity` values).
4. **Strict Integer Monotonicity**: $Z_{n\text{max}} \ge Z_{n-1\text{max}} + 1$.
5. **Clean-Room Test Pass Rate**: 100% test pass rate across the full application unit test suite.
6. **Zero Schema Migrations**: SQLite schema version remains unaltered; baseline keys added purely to DataStore.

---

## 2. Test Specification (TST-UI-336)

### Test Case 1: Pure Mathematical Model, Coggan Rounding & Zone 7 Ceiling (`[TST-UI-336.1]`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/ZoneCalculationMathTest.kt`
* **Preconditions**: Pure Kotlin mathematical engine.
* **Actions & Expected Results**:
  * Verify Coggan 7-zone calculation for FTP = 200W, 250W, 300W matches exact half-up rounded cutoffs.
  * Verify Zone 7 physical ceiling is capped at $2500\text{ W}$, with lower bound $Z_6 + 1\text{ W}$.
  * Verify baseline FTP boundary clamping: FTP < 50 throws `IllegalArgumentException`, FTP > 600 throws `IllegalArgumentException`.
  * Verify calculation when $Z_6 \ge 2500\text{ W}$ throws `IllegalArgumentException`.
  * Verify strict monotonicity: $Z_n \ge Z_{n-1} + 1$ for all valid FTP inputs in $[50, 600]$.

### Test Case 2: Karvonen & % HRmax Mathematical Models & Invariant Guards (`[TST-UI-336.2]`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/ZoneCalculationMathTest.kt`
* **Preconditions**: Pure Kotlin mathematical engine.
* **Actions & Expected Results**:
  * Verify % HRmax model for $HR_{\max} = 190\text{ bpm}$ produces monotonic 5-zone cutoffs.
  * Verify Karvonen model for $HR_{\max} = 190, HR_{\text{rest}} = 50$ produces correct HRR cutoffs.
  * Verify Primary Karvonen Invariant: $HR_{\max} \le HR_{\text{rest}}$ throws `IllegalArgumentException`.
  * Verify Karvonen delta constraint: $HR_{\max} - HR_{\text{rest}} < 20$ throws `IllegalArgumentException`.
  * Verify $HR_{\max} \notin [80, 240]$ and $HR_{\text{rest}} \notin [30, 120]$ throw `IllegalArgumentException`.

### Test Case 3: Two-Layer Monotonicity Validation & +1 Step (`[TST-UI-336.3]`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/ZoneValidatorTest.kt`
* **Preconditions**: Validator test harness.
* **Actions & Expected Results**:
  * Verify `validatePowerZoneStack` passes strictly monotonic ascending stacks ($Z_n \ge Z_{n-1} + 1$).
  * Verify `validatePowerZoneStack` throws `IllegalArgumentException` on inversions ($Z_i \ge Z_{i+1}$) or equal adjacent values ($Z_i == Z_{i+1}$).
  * Verify `validatePowerZoneStack` throws `IllegalArgumentException` when any value exceeds $2500\text{ W}$ or is $\le 0$.
  * Verify `validateHeartRateZoneStack` passes valid monotonic 4-cutoff stacks.
  * Verify `validateHeartRateZoneStack` throws `IllegalArgumentException` on inversions, equal values, or values $> 240\text{ bpm}$.

### Test Case 4: Single-Transaction ACID Atomicity, UI Lock & DataStore Baselines (`[TST-UI-336.4]`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/SettingsDataStoreBaselinesTest.kt`
* **Preconditions**: In-memory test DataStore.
* **Actions & Expected Results**:
  * Verify saving calculated power zones persists FTP, timestamp, `pwr_bike_zones_customized = false`, and all 6 cutoffs in a single transaction.
  * Verify manual cutoff mutation flips `pwr_bike_zones_customized` to `true`.
  * Verify `SettingsDataStoreJavaHelper.getFtp(context)` retrieves stored FTP or null when unconfigured.
  * Verify non-destructive migration: existing legacy cutoffs retain custom flag `true` and null FTP.

### Test Case 5: Locale-Aware Number Parsing (`[TST-UI-336.5]`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/ZoneNumberParserTest.kt`
* **Preconditions**: Pure Kotlin utility.
* **Actions & Expected Results**:
  * Verify parsing integer strings with standard ASCII digits (`"250"` -> `250`).
  * Verify parsing with German/European comma decimal separators (`"250,5"` -> `250`).
  * Verify parsing with grouping separators and spaces (`"1 200"`, `"1.200"`).
  * Verify invalid non-numeric strings (`"abc"`, `""`, `"-"`) return `null` safely without throwing exceptions.

### Test Case 6: Destructive Overwrite, Error Copy & Dialog Contract Verification (`[TST-UI-336.6]`)
* **Scope**: JVM Unit Test / Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/ZoneCalculatorDialogContractTest.kt`
* **Preconditions**: UI state model.
* **Actions & Expected Results**:
  * Verify dialog initial state populates existing stored FTP or default 200W.
  * Verify `shouldShowOverwriteWarning` returns `true` if and only if `isCustomized == true`.
  * Verify cancelling overwrite preserves existing cutoffs without DataStore writes.
  * Verify confirming overwrite applies calculated boundaries and resets `isCustomized` to `false`.
  * Verify that Karvonen delta < 20 bpm surfaces explicit localized error `zone_calc_error_karvonen_delta`.
  * Verify UI mutation lock (`isSaving == true`) disables Apply button during persistence.

### Test Case 7: 9-Language Localization & Format Specifier Token Audit (`[TST-UI-336.7]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/zones/ZoneCalculatorLocalizationTest.kt`
* **Preconditions**: Resource parser checking `res/values*/strings.xml`.
* **Actions & Expected Results**:
  * Verify presence of all zone calculation string keys across all 9 application locales:
    * EN, DE, ES, FR, IT, JA, NL, PL, PT.
  * Verify Format Specifier Token Audit: for every string containing format tokens (`%d`, `%s`, `%1$d`), assert that every translation contains exactly the identical count, argument types, and indexing order as base `res/values/strings.xml`.
  * Verify zero empty strings or missing resource keys.

### Test Case 8: Clean-Room Full Suite Regression Execution (`[TST-UI-336.8]`)
* **Scope**: Full Regression Suite
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% pass rate with zero test failures across all 436+ test classes.

---

## 3. Traceability Matrix

| Test Case ID | Test Class / Command | Target Method / Component | Requirement ID | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-336.1]` | `ZoneCalculationMathTest.kt` | `ZoneCalculationMath.calculateCogganPowerZones` | `REQ-UI-373.1` | Specified |
| `[TST-UI-336.2]` | `ZoneCalculationMathTest.kt` | `ZoneCalculationMath.calculateHeartRateZones` | `REQ-UI-373.2` | Specified |
| `[TST-UI-336.3]` | `ZoneValidatorTest.kt` | `ZoneValidator.validatePowerZoneStack` | `REQ-UI-373.3`, `REQ-UI-373.4` | Specified |
| `[TST-UI-336.4]` | `SettingsDataStoreBaselinesTest.kt` | `SettingsDataStore.saveCalculatedPowerZones` | `REQ-UI-373.5`, `REQ-UI-373.6`, `REQ-UI-373.10` | Specified |
| `[TST-UI-336.5]` | `ZoneNumberParserTest.kt` | `ZoneNumberParser.parseIntOrNull` | `REQ-UI-373.9` | Specified |
| `[TST-UI-336.6]` | `ZoneCalculatorDialogContractTest.kt` | `ZoneCalculatorViewModel` / Contract | `REQ-UI-373.2`, `REQ-UI-373.8`, `REQ-UI-373.10` | Specified |
| `[TST-UI-336.7]` | `ZoneCalculatorLocalizationTest.kt` | 9-Language `strings.xml` | `REQ-UI-373.9` | Specified |
| `[TST-UI-336.8]` | `./gradlew testDebugUnitTest` | Full Project Test Suite | `REQ-PRO-001`, `REQ-UI-373.7` | Specified |
