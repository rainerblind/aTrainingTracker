# Stage 5 Walkthrough - ATT-3188: Calculate training zone boundaries automatically from FTP and Heart Rate baselines

**Ticket**: [ATT-3188](https://rainerblind.atlassian.net/browse/ATT-3188)  
**Stage 5 Subtask**: [ATT-3614](https://rainerblind.atlassian.net/browse/ATT-3614) (`[Test]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*[Epic] Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Requirement Mapping**: `REQ-UI-373` (`Verified`)  
**Test Mapping**: `TST-UI-336` (`Verified`)  
**Branch**: `feature/ATT-3188`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-15  

---

## 1. Executive Summary & Root Cause

Prior to ATT-3188, athletes configuring physiological training zones in `ZonesSettingsActivity.kt` were required to perform off-device manual arithmetic to determine zone thresholds for power (6 cutoff boundaries across 7 Coggan zones) and heart rate (4 cutoff boundaries across 5 zones). This unassisted input mode suffered from three primary architectural and usability deficiencies:
1. **Friction & Error Prone**: Entering raw boundaries by hand without baseline integration (FTP, HRmax, HRrest) frequently led to inverted or non-monotonic boundaries.
2. **Missing Physiological Baselines**: Key athlete baselines like Functional Threshold Power (FTP) and Heart Rate Reserve (Karvonen) were not modeled or stored in DataStore.
3. **No Overwrite Protection**: Users modifying boundaries had no distinction between standard calculated profiles and customized configurations, risking accidental data loss.

ATT-3188 resolves these issues with an end-to-end ASPICE SWE.3/SWE.4 implementation:
- **Pure Mathematical Engine (`ZoneCalculationMath.kt`)**: Implements Coggan 7-Zone cycling power calculation (55%, 75%, 90%, 105%, 120%, 150% FTP) with strict half-up rounding, physical ceiling capping ($2500\text{ W}$ finite storage invariant), and Karvonen / % HRmax models with strict invariant guards ($HR_{\max} > HR_{\text{rest}}$, $HR_{\max} - HR_{\text{rest}} \ge 20\text{ bpm}$).
- **Two-Layer Monotonicity Validation (`ZoneValidator.kt`)**: Guarantees strict integer increments ($Z_n \ge Z_{n-1} + 1$) at both the keystroke presentation layer (Layer 1) and the domain blocking guard layer (Layer 2).
- **Locale-Aware Number Parser (`ZoneNumberParser.kt`)**: Transparently handles European commas, grouping separators, and non-breaking spaces.
- **Single-Transaction ACID Persistence (`SettingsDataStore.kt`)**: Persists baselines, algorithm version (`KEY_ZONE_CALC_VERSION = 1`), customization status, and all zone cutoffs atomically via atomic file swap.
- **Material 3 Calculation Dialog (`ZoneCalculatorDialog.kt`)**: Reactive Compose dialog with live preview, UI mutation locking (`isSaving`), and destructive overwrite confirmation modal.
- **Living Documentation & 9-Language Localization**: Full format specifier token parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-373` (*Automatic Training Zone Boundary Calculation from FTP and Heart Rate Baselines*) -> **Status: Verified**
  * `REQ-UI-373.1`: Cycling Power Coggan 7-Zone Calculation & Zone 7 Ceiling ($2500\text{ W}$) -> **Verified**
  * `REQ-UI-373.2`: Heart Rate Calculation (% of HRmax & Karvonen) & Invariant Error Messages -> **Verified**
  * `REQ-UI-373.3` & `REQ-UI-373.4`: Two-Layer Monotonicity Validation & Step Increment ($Z_n \ge Z_{n-1} + 1$) -> **Verified**
  * `REQ-UI-373.5` & `REQ-UI-373.6`: Single-Transaction ACID Atomicity Guarantee & Algorithm Versioning (`zone_calc_version = 1`) -> **Verified**
  * `REQ-UI-373.7`: Formal Data Integrity Policy & Historical Analytics Immutability -> **Verified**
  * `REQ-UI-373.8`: Customization Lifecycle, Transitions & Destructive Overwrite Warning Modal -> **Verified**
  * `REQ-UI-373.9`: Locale-Aware Number Parsing & 9-Language Localization Token Parity -> **Verified**
  * `REQ-UI-373.10`: UI Mutation Lock & Idempotent Transaction Concurrency Guard -> **Verified**
* **Test Mapping**: `TST-UI-336` (*Automatic Training Zone Boundary Calculation Verification*) -> **Status: Verified**

---

## 3. Preserved Invariants & Invariant Verification

1. **Historical Data Immutability**: Modifying athlete baselines or recalculating zones applies strictly prospectively to future activities. Past completed workout records and aggregates in SQLite (`WorkoutSummaries.db`, `Extrema.db`, `WorkoutSamples.db`) are immutable and NEVER recalculated retroactively.
2. **Canonical SI Storage**: Baselines and cutoffs stored strictly in SI units (Watts for power, BPM for heart rate).
3. **Finite Zone 7 Upper Bound**: Zone 7 upper bound is capped at physical ceiling $2500\text{ W}$ (zero `Infinity` values stored in DataStore).
4. **Strict Integer Monotonicity**: $Z_{n\text{max}} \ge Z_{n-1\text{max}} + 1$ enforced across UI and domain tiers. Adjacent zones cannot share identical cutoffs.
5. **Algorithm Versioning Invariant**: Calculation logic version is stored via `KEY_ZONE_CALC_VERSION = 1`, providing schema longevity and preventing silent algorithm drift.
6. **Zero SQLite Migrations**: SQLite schema version remains unaltered; baseline keys added purely to DataStore.
7. **Thread Safety & Dispatcher Confinement**: DataStore writes executed via Kotlin Coroutines on `Dispatchers.IO`; UI updates dispatched on `Dispatchers.Main`.
8. **Clean-Room Test Pass Rate**: 100% test pass rate across the full application unit test suite (0 regressions).

---

## 4. Test Verification Results

### Summary of Targeted Unit Tests

| Test Class | Spec ID | Tests | Pass Rate | Key Validations |
| :--- | :--- | :--- | :--- | :--- |
| `ZoneCalculationMathTest` | `[TST-UI-336.1]`, `[TST-UI-336.2]` | 12 | **100% PASS** | Coggan 7-zone rounding (200W, 250W, 300W), 2500W ceiling, Karvonen HRR, % HRmax, Karvonen $\Delta \ge 20$ guard, out-of-bounds baseline rejection. |
| `ZoneValidatorTest` | `[TST-UI-336.3]` | 11 | **100% PASS** | Power & HR monotonic ascending stack validation, inversion rejection, adjacent duplicate rejection, baseline validation (FTP, HR baselines). |
| `ZoneNumberParserTest` | `[TST-UI-336.5]` | 8 | **100% PASS** | Standard ASCII integers, comma decimals, thousand separators (space, dot, NBSP), negative/empty/invalid input rejection. |
| `SettingsDataStoreBaselinesTest` | `[TST-UI-336.4]` | 5 | **100% PASS** | Single-transaction atomic save of power & HR zones, baseline versioning (`zone_calc_version = 1`), manual edit customized flag transition, Java helper `getFtp()`. |
| `ZoneCalculatorDialogContractTest` | `[TST-UI-336.6]` | 6 | **100% PASS** | Initial state baseline population, overwrite confirmation triggering on customized profiles, cancel safety (no DataStore writes), Karvonen delta error presentation, UI mutation lock (`isSaving`). |
| `ZoneCalculatorLocalizationTest` | `[TST-UI-336.7]` | 2 | **100% PASS** | 100% string key existence and format specifier token parity (`%d`, `%s`, `%1$d`) across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT). |

### Clean-Room Regression Run
* Command: `./gradlew testDebugUnitTest`
* Result: **100% Tests Passed** (0 failures, 0 errors, 0 regressions across all modules).

---

## 5. Visual Consistency Verification (Rule 23)

* **Reference Screen**: `AdvancedTuningDialog.kt`, `ZonesSettingsActivity.kt`
* **Components Reused**: Material 3 `AlertDialog`, `OutlinedTextField`, `FilterChip` (for HR calculation method selection), `Button`, `TextButton`, `Badge`.
* **Theme Tokens**: `MaterialTheme.colorScheme.primary`, `MaterialTheme.colorScheme.error`, `RoundedCornerShape(16.dp)`, `16.dp` content padding.
* **Layout Consistency**:
  - Calculation Dialog uses standard dialog width, surface container color, and rounded corners conforming to `docs/design_guidelines.md`.
  - Zones Settings Activity integrates customization status badges (`Berechnet` / `Benutzerdefiniert`) and calibration age advisory chip (`Kalibrierung empfohlen`) matching the app-wide chip styling.
