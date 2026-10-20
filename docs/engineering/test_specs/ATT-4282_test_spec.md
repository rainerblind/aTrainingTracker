# Stage 2: Requirement & Test Specification - ATT-4282: [Bug] Fix runtime filter registration for 'Best Sensor' fields in EditSensorFieldViewModel and NaN division in TimedMovingAverageFilter

**Ticket**: [ATT-4282](https://rainerblind.atlassian.net/browse/ATT-4282)  
**Sub-task**: [ATT-4356](https://rainerblind.atlassian.net/browse/ATT-4356) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-FIL-014` (*Backend Filter Key Normalization, Runtime 'Best Sensor' Field Registration & Post-Trim Rolling Filter NaN Guard*)  
**Test Spec ID**: `TST-FIL-014`  
**Branch**: `feature/ATT-4282`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (REQ-FIL-014)

### 1.1 Problem Statement & Rationale
During on-device testing and Sprint Review 2026-42.10 (`ATT-1296`) on a physical Google Pixel 10, two critical telemetry filtering defects were discovered:
1. **Stuck Sensor Tiles on Runtime 'Best Sensor' Field Creation/Editing**: When an athlete configures a new telemetry tile (e.g. `W_PRIME_BAL`, `POWER`, or `SPEED_mps`) or modifies an existing tile in the Cockpit while retaining the default "Best Sensor" source (`selectedDeviceId = -1`), the tile remains permanently stuck displaying `--` throughout the active session. This occurs because `EditSensorFieldViewModel.saveChanges()` forwards `currentState.selectedDeviceName` (populated with localized string `context.getString(R.string.bestSensor)`, e.g. `"Bester Sensor"` in German or `"Best sensor"` in English) directly into `FilterData.deviceName`. Consequently, `FilterManager.createFilter()` searches for a paired physical hardware device named `"Bester Sensor"`, fails to find one, and stashes the filter into `mRequiredButNotAvailableFilters` indefinitely instead of resolving the `ProxySensor` via `mMySensorManager.getSensor(...)`. Furthermore, `TrackingViewsDatabaseManager` persists `SOURCE_DEVICE_ID = NULL`, causing `TrackingViewModel` to query `getFilteredSensorData(FilterData(..., deviceName = null))`, yielding a permanent hash mismatch.
2. **Division by Zero (`Double.NaN`) in Rolling Moving Average Filter**: In `TimedMovingAverageFilter.java`, `getFilteredValue()` evaluates `if (mTimestampedValues.size() == 0) return null;` *before* purging expired entries via `trimValues()`. If all buffered samples in the moving time window age out simultaneously between evaluations (e.g. coasting or pausing pedaling), `trimValues()` evacuates the queue. The method subsequently computes `sum / mTimestampedValues.size()`, evaluating `0.0 / 0 = Double.NaN`. Downstream telemetry consumers and formatters receive `Double.NaN` (which is non-null), rendering literal `"NaN"` or corrupting dependent calculations.

**Rationale**: Athletes must be able to adjust cockpit telemetry fields dynamically during workouts without needing an application restart, and telemetry filters must maintain strict mathematical integrity, returning `null` when no valid samples exist in a moving window so UI formatters render clean dashes (`"--"`).

### 1.2 Functional & Architectural Requirements
The system SHALL satisfy the following criteria:

1. **Sentinel ID Normalization in `EditSensorFieldViewModel.saveChanges()` (`REQ-FIL-014.1` — Rule 50)**:
   * When saving sensor field configuration in `EditSensorFieldViewModel.saveChanges()`, the system SHALL evaluate `currentState.selectedDeviceId`.
   * If `currentState.selectedDeviceId <= 0` (e.g. sentinel `-1L` representing "Best Sensor"):
     * `newSourceDeviceName` SHALL be normalized to `null`.
     * `newSourceDeviceId` SHALL be normalized to `null`.
   * If `currentState.selectedDeviceId > 0` (specific physical device selected):
     * `newSourceDeviceName` SHALL retain `currentState.selectedDeviceName`.
     * `newSourceDeviceId` SHALL retain `currentState.selectedDeviceId`.
   * `FilterData` passed to `banalServiceRepository.createFilter(filterData)` SHALL receive `newSourceDeviceName` (`null` for "Best Sensor"), ensuring `FilterManager.java` resolves the proxy sensor via `mMySensorManager.getSensor(filterData.sensorType)` and registers the filter under matching hash keys (`Objects.hash(..., null)`).
   * `trackingViewsRepository.insertSensorFieldConfig(...)` and `trackingViewsRepository.updateSensorFieldConfig(...)` SHALL receive the normalized `newSourceDeviceId` and `newSourceDeviceName`.

2. **Post-Trim Emptiness Guard & NaN Elimination in `TimedMovingAverageFilter` (`REQ-FIL-014.2` — Rule 50)**:
   * In `TimedMovingAverageFilter.java`, `getFilteredValue()` SHALL invoke `trimValues()` prior to checking collection emptiness.
   * Immediately following `trimValues()`, the method SHALL evaluate `if (mTimestampedValues.isEmpty()) return null;` before computing sample sums or dividing by collection size.
   * When all buffered samples age beyond `1000 * mSeconds` (e.g. athlete stops pedaling or signal silence occurs), `getFilteredValue()` SHALL return `null` instead of computing `0.0 / 0 = Double.NaN`.
   * Downstream telemetry layers and formatters SHALL render standard fallback dashes (`"--"`) rather than literal `"NaN"` or arithmetic distortions.

3. **Multi-Locale Parity & Zero UI Alterations (`REQ-FIL-014.3`)**:
   * Sentinel normalization SHALL operate strictly on numeric IDs (`selectedDeviceId <= 0`), eliminating any coupling to localized UI strings (`context.getString(R.string.bestSensor)`).
   * Existing string resources across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) SHALL remain untouched with zero new string resources required.

4. **Preservation of System Invariants (`REQ-FIL-014.4`)**:
   * Specific paired device selection (`selectedDeviceId > 0`) continues to register device-bound filters and persist non-null foreign keys to SQLite.
   * All filter operations in `TimedMovingAverageFilter` retain `synchronized` thread safety.
   * Unaffected filter implementations (`NumberedMovingAverageFilter`, `AverageFilter`, `ExponentialSmoothingFilter`, `MaxValueFilter`, `InstantaneousFilter`) remain strictly untouched.
   * Zero SQLite schema migrations or alterations.
   * 100% full clean-room unit regression test suite pass rate under `./gradlew testDebugUnitTest`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Runtime Best Sensor Creation)**:
  * *Given* an athlete configuring a new sensor field with "Best Sensor" (`selectedDeviceId = -1`),
  * *When* `saveChanges()` is invoked,
  * *Then* `banalServiceRepository.createFilter` receives `FilterData(deviceName = null, ...)`, registering the filter with `ProxySensor`, and the cockpit tile streams live data immediately without restarting the app.
* **Criterion 2 (Runtime Best Sensor Update)**:
  * *Given* an existing sensor field modified to "Best Sensor" (`selectedDeviceId = -1`),
  * *When* `saveChanges()` is invoked,
  * *Then* `banalServiceRepository.createFilter` receives `FilterData(deviceName = null, ...)`, and `trackingViewsRepository.updateSensorFieldConfig` receives `newSourceDeviceId = null` and `newSourceDeviceName = null`.
* **Criterion 3 (Specific Paired Device Selection)**:
  * *Given* an athlete configuring a sensor field with a specific paired device (`selectedDeviceId = 123L`, `selectedDeviceName = "Stages Power"`),
  * *When* `saveChanges()` is invoked,
  * *Then* `banalServiceRepository.createFilter` receives `FilterData(deviceName = "Stages Power", ...)`, binding the filter to the specific device.
* **Criterion 4 (Expired Samples NaN Prevention)**:
  * *Given* a `TimedMovingAverageFilter` containing samples where all samples have aged past the time window threshold,
  * *When* `getFilteredValue()` is called,
  * *Then* `trimValues()` clears the expired samples, and the method returns `null` instead of `Double.NaN`.
* **Criterion 5 (Valid Window Sample Averaging)**:
  * *Given* a `TimedMovingAverageFilter` with fresh incoming samples within the time window,
  * *When* `getFilteredValue()` is called,
  * *Then* the arithmetic mean of the valid samples is returned accurately.
* **Criterion 6 (9-Language Neutrality)**:
  * *Given* any of the 9 supported application locales,
  * *When* saving sensor fields in `EditSensorFieldViewModel`,
  * *Then* filter registration behavior is 100% identical and independent of the locale's `R.string.bestSensor` translation.

### 1.4 System Invariants
* **Rule 50 Compliance (`docs/coding_standards.md` §1.7)**: Backend filter keys must never receive localized display strings (`"Best Sensor"` / `"Bester Sensor"`). Sentinel IDs (`<= 0`) must normalize to `null`.
* **Mathematical Filter Integrity**: Rolling filters must never return `Double.NaN`. An empty window must strictly return `null`.
* **Thread Safety**: `TimedMovingAverageFilter` operations remain `synchronized` across `newValue()`, `reset()`, and `getFilteredValue()`.
* **Non-Regression on Other Filters**: Fixed-size ring buffers (`NumberedMovingAverageFilter`), exponential filters, and cumulative max/average filters remain unmodified.
* **Zero Schema Alterations**: `FlexibleGenericView.db` / `TrackingViews.db` schemas already store `NULL` for `SOURCE_DEVICE_ID` when `sourceDeviceId <= 0`. No schema upgrade is required.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-FIL-014`, amending `REQ-FIL-011` (*Null-Safe Sensor Filtering*) and `REQ-UI-167` (*Intuitive Smoothing Presets, Simplified Filter Configuration & Smart Athletic Defaults*). Targets `EditSensorFieldViewModel.kt`, `TrackingViewsRepository.kt`, and `TimedMovingAverageFilter.java`.
* **Historical Origin & Commit Trace**: `REQ-FIL-011` was introduced in baseline architecture and formalized in Sprint 2026-41.10 (`ATT-3222`). `REQ-UI-167` was introduced in Sprint 2026-40.4 (`ATT-1276`, commit `69e4f1a2`) when Compose-based sensor field editing was created. Discovered during on-device testing and Sprint Review 2026-42.10 (`ATT-1296`, commit `889ebd18` / `c8939a3f`) on Google Pixel 10 (attached `w_prime_cockpit.png`), where `W_PRIME_BAL` added at runtime remained stuck displaying `--` and `TimedMovingAverageFilter` evaluated `0.0 / 0 = Double.NaN`. Codified into Document Triad Rule 50 (`docs/coding_standards.md` §1.7).
* **Root Reason for Existing Formulation**: In `EditSensorFieldViewModel`, `selectedDeviceId` was assigned `-1` as a sentinel for "Best Sensor", while `selectedDeviceName` was populated with `context.getString(R.string.bestSensor)` to supply a display string for the dropdown spinner. When `saveChanges()` was wired to notify `BANALService.createFilter()`, the display string was forwarded without normalizing sentinel IDs to `null`. In `TimedMovingAverageFilter`, `getFilteredValue()` placed the check `if (mTimestampedValues.size() == 0) return null;` before `trimValues()` under the assumption that `trimValues()` would only discard older samples while leaving at least one fresh sample, overlooking the boundary condition where all samples in the window age out simultaneously between evaluations.
* **Preservation of Core Invariants**: Explicit device selection when `selectedDeviceId > 0` is strictly preserved; "Best Sensor" proxy resolution when `selectedDeviceId <= 0` is restored; mathematical moving average contract returning `null` on empty windows is preserved; 9-locale parity is preserved with zero new strings; 100% clean-room test pass rate is strictly maintained.

---

## 2. Test Specification (TST-FIL-014)

### 2.1 Unit Tests

#### Test Case 1: Runtime 'Best Sensor' Field Creation Normalization (`[TST-FIL-014.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldViewModelTest.kt`
* **Preconditions**:
  * `EditSensorFieldViewModel` instantiated in "New Field" mode (`sensorFieldId = -1L`).
  * `_uiState` holds `selectedDeviceId = -1L` and `selectedDeviceName = "Bester Sensor"`.
* **Action**: Invoke `viewModel.saveChanges()`.
* **Expected Result**:
  * `banalServiceRepo.createFilter(capture(filterSlot))` is invoked with `filterSlot.captured.deviceName == null`.
  * `trackingViewsRepo.insertSensorFieldConfig(...)` is invoked with `newSourceDeviceId == null` and `newSourceDeviceName == null`.

#### Test Case 2: Runtime 'Best Sensor' Field Update Normalization (`[TST-FIL-014.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldViewModelTest.kt`
* **Preconditions**:
  * `EditSensorFieldViewModel` instantiated in "Edit Field" mode (`sensorFieldId = 42L`).
  * `_uiState` holds `selectedDeviceId = -1L` and `selectedDeviceName = "Bester Sensor"`.
* **Action**: Invoke `viewModel.saveChanges()`.
* **Expected Result**:
  * `banalServiceRepo.createFilter(...)` is invoked with `deviceName == null`.
  * `trackingViewsRepo.updateSensorFieldConfig(...)` is invoked with `newSourceDeviceId == null` and `newSourceDeviceName == null`.

#### Test Case 3: Specific Paired Device Selection Preservation (`[TST-FIL-014.3]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldViewModelTest.kt`
* **Preconditions**:
  * `EditSensorFieldViewModel` instantiated with `selectedDeviceId = 123L` and `selectedDeviceName = "Wahoo KICKR"`.
* **Action**: Invoke `viewModel.saveChanges()`.
* **Expected Result**:
  * `banalServiceRepo.createFilter(...)` is invoked with `deviceName == "Wahoo KICKR"`.
  * `trackingViewsRepo.insertSensorFieldConfig(...)` (or update) is invoked with `newSourceDeviceId == 123L` and `newSourceDeviceName == "Wahoo KICKR"`.

#### Test Case 4: TimedMovingAverageFilter Post-Trim Expired Samples Empty Guard (`[TST-FIL-014.4]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/filters/FilterNullSafetyTest.kt`
* **Preconditions**:
  * `filter = TimedMovingAverageFilter("power_filter", SensorType.POWER, 3)` with 3-second window.
  * Add sample `filter.newValue(250)`.
* **Action**:
  * Advance time past window threshold (>3000ms, or simulate timestamp older than `System.currentTimeMillis() - 3000`).
  * Invoke `filter.getFilteredValue()` (or `filter.filteredValue`).
* **Expected Result**:
  * `filter.filteredValue == null`.
  * `filter.filteredValue != Double.NaN`.
  * Zero division-by-zero occurrences.

#### Test Case 5: TimedMovingAverageFilter Fresh Window Moving Average Accuracy (`[TST-FIL-014.5]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/filters/FilterNullSafetyTest.kt`
* **Preconditions**:
  * `filter = TimedMovingAverageFilter("power_filter", SensorType.POWER, 10)` with 10-second window.
* **Action**:
  * Add sample `filter.newValue(100)`.
  * Add sample `filter.newValue(200)`.
  * Invoke `filter.filteredValue`.
* **Expected Result**:
  * `filter.filteredValue == 150.0` (Double, delta 0.01).

### 2.2 9-Language Localization & Specifier Audit (`[TST-FIL-014.6]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Goal**:
  * Confirm that zero new UI strings are required for this backend filter key normalization and math fix.
  * Confirm that `bestSensor` exists, is non-blank, and contains matching semantic translations across all 9 supported locales:
    * `values` (EN): `best`
    * `values-de` (DE): `Bester`
    * `values-es` (ES): `mejor`
    * `values-fr` (FR): `meilleur`
    * `values-it` (IT): `migliore`
    * `values-ja` (JA): `最適`
    * `values-nl` (NL): `beste`
    * `values-pl` (PL): `najlepszy`
    * `values-pt` (PT): `melhor`
* **Expected Result**: 100% localization parity, zero missing translations, zero format specifier mismatches.

### 2.3 Clean-Room Regression Suite (`[TST-FIL-014.7]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the complete project test suite with zero broken invariants or regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-FIL-014.1]` | Unit | `EditSensorFieldViewModel.saveChanges()` | `REQ-FIL-014.1`, Rule 50 | Specified |
| `[TST-FIL-014.2]` | Unit | `EditSensorFieldViewModel.saveChanges()` | `REQ-FIL-014.1`, Rule 50 | Specified |
| `[TST-FIL-014.3]` | Unit | `EditSensorFieldViewModel.saveChanges()` | `REQ-FIL-014.1`, `REQ-FIL-014.4` | Specified |
| `[TST-FIL-014.4]` | Unit | `TimedMovingAverageFilter.getFilteredValue()` | `REQ-FIL-014.2`, `REQ-FIL-011`, Rule 50 | Specified |
| `[TST-FIL-014.5]` | Unit | `TimedMovingAverageFilter.getFilteredValue()` | `REQ-FIL-014.2`, `REQ-FIL-001` | Specified |
| `[TST-FIL-014.6]` | Localization | `TranslationParityTest` | `REQ-FIL-014.3`, `REQ-UI-106` | Specified |
| `[TST-FIL-014.7]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001`, `REQ-FIL-014.4` | Specified |
