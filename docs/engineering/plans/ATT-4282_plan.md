# Stage 3: Implementation Plan - ATT-4282: [Bug] Fix runtime filter registration for 'Best Sensor' fields in EditSensorFieldViewModel and NaN division in TimedMovingAverageFilter

**Ticket**: [ATT-4282](https://rainerblind.atlassian.net/browse/ATT-4282)  
**Sub-task**: [ATT-4357](https://rainerblind.atlassian.net/browse/ATT-4357) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-FIL-014` (`REQ-FIL-014.1`, `REQ-FIL-014.2`, `REQ-FIL-014.3`, `REQ-FIL-014.4`)  
**Test Mapping**: `TST-FIL-014` (`TST-FIL-014.1`, `TST-FIL-014.2`, `TST-FIL-014.3`, `TST-FIL-014.4`, `TST-FIL-014.5`, `TST-FIL-014.6`, `TST-FIL-014.7`)  
**Branch**: `feature/ATT-4282`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

During on-device testing and the Sprint Review of `ATT-1296` on a physical Google Pixel 10 test device (demonstrated in attached artifact `w_prime_cockpit.png`), two critical runtime bugs were discovered in the sensor field configuration and telemetry filtering pipeline:

1. **Stuck Sensor Tiles on Runtime 'Best Sensor' Field Creation/Editing**:  
   When an athlete configures a new telemetry field (e.g. `W_PRIME_BAL`, `POWER`, or `SPEED_mps`) or modifies an existing tile in the live Cockpit while retaining the default "Best Sensor" source (`selectedDeviceId = -1`), the tile remains permanently stuck displaying `--` (or empty gauge tracks) throughout the session. The tile only starts displaying live data after the application is completely killed and restarted.  
   *Root Cause*: `EditSensorFieldViewModel.saveChanges()` forwards `currentState.selectedDeviceName` (which contains the localized UI string from `R.string.bestSensor`, e.g. `"Bester Sensor"` in German or `"Best sensor"` in English) directly into `FilterData(deviceName = newSourceDeviceName, ...)`. In `FilterManager.createFilter()`, a non-null `deviceName` bypasses `mMySensorManager.getSensor(...)` (which resolves the best sensor via `ProxySensor`) and instead searches `mDeviceManager.getMyDeviceByName(...)`. Because no physical device is named `"Bester Sensor"`, `myDevice` and `mySensor` resolve to `null`, and the filter is never registered in `myFilterMap` (stashed indefinitely in `mRequiredButNotAvailableFilters`). Furthermore, `TrackingViewsDatabaseManager` persists `SOURCE_DEVICE_ID = NULL`, causing `TrackingViewModel` to query incoming data under `Objects.hash(sensorType, filterType, filterConstant, null)`, creating an irreconcilable hash mismatch. On app cold start, the database initialization helper maps `sourceDeviceId = -1` to `deviceName = null`, explaining why the bug magically vanished after restarting the app.

2. **Division by Zero (`Double.NaN`) in Rolling Moving Average Filter**:  
   In `TimedMovingAverageFilter.java`, `getFilteredValue()` verifies whether samples exist *before* purging expired entries via `trimValues()`. If all buffered samples in the moving time window age out simultaneously between evaluations (for instance, when an athlete stops pedaling or coasting occurs), `trimValues()` polls all entries from `mTimestampedValues`. The method then computes `sum / mTimestampedValues.size()`, evaluating `0.0 / 0 = Double.NaN`. In Java, `Double.NaN` is a non-null object that bypasses standard null checks in downstream mathematical calculations (such as `CyclingPowerMetricsDevice.calculateMetrics()`) and formatters, rendering literal `"NaN"` or corrupting dependent metrics.

**Motivation & Goal**:  
Eliminate both defects cleanly in Stage 4 by normalizing sentinel IDs (`selectedDeviceId <= 0`) to `null` before constructing backend filter data and database configs (pursuant to Rule 50), and ensuring `TimedMovingAverageFilter` invokes `trimValues()` before checking collection emptiness, returning `null` when no samples remain in the time window.

---

## 2. Traceability & Requirements Mapping

The implementation fulfills all requirements specified in `docs/engineering/test_specs/ATT-4282_test_spec.md`:

| Requirement ID | Requirement Summary | Target Component | Verifying Test Case |
| :--- | :--- | :--- | :--- |
| **`REQ-FIL-014.1`** | **Sentinel ID Normalization**: When `selectedDeviceId <= 0` (sentinel `-1L`), `saveChanges()` must normalize `newSourceDeviceName = null` and `newSourceDeviceId = null` for `FilterData` and repository calls. When `selectedDeviceId > 0`, retain explicit device values. (Rule 50) | `EditSensorFieldViewModel.kt` | `[TST-FIL-014.1]`, `[TST-FIL-014.2]`, `[TST-FIL-014.3]` |
| **`REQ-FIL-014.2`** | **Post-Trim Emptiness Guard & NaN Elimination**: In `TimedMovingAverageFilter.java`, invoke `trimValues()` first, then check `if (mTimestampedValues.isEmpty()) return null;`. When all samples expire, return `null` instead of `Double.NaN`. (Rule 50) | `TimedMovingAverageFilter.java` | `[TST-FIL-014.4]`, `[TST-FIL-014.5]` |
| **`REQ-FIL-014.3`** | **Multi-Locale Neutrality & Zero String Alterations**: Sentinel normalization must operate on numeric IDs (`<= 0`) without depending on localized display strings. Zero changes to strings in all 9 supported locales. | `EditSensorFieldViewModel.kt`, `res/values*/strings.xml` | `[TST-FIL-014.6]` (`TranslationParityTest`) |
| **`REQ-FIL-014.4`** | **System Invariant Preservation**: Retain thread safety (`synchronized`), preserve explicit device binding for `selectedDeviceId > 0`, leave unaffected filters unmodified, zero DB schema changes, 100% test pass rate. | Entire codebase | `[TST-FIL-014.7]` (`./gradlew testDebugUnitTest`) |

### Detailed Test Mapping:
* **`[TST-FIL-014.1]`**: `EditSensorFieldViewModelTest.testSaveChanges_newField_bestSensor_normalizesDeviceNameToNull()`  
  Asserts that creating a new field with `selectedDeviceId = -1L` passes `FilterData.deviceName = null` to `banalServiceRepository.createFilter()` and `newSourceDeviceId = null`, `newSourceDeviceName = null` to `trackingViewsRepository.insertSensorFieldConfig()`.
* **`[TST-FIL-014.2]`**: `EditSensorFieldViewModelTest.testSaveChanges_editField_bestSensor_normalizesDeviceNameToNull()`  
  Asserts that updating an existing field with `selectedDeviceId = -1L` passes `FilterData.deviceName = null` to `banalServiceRepository.createFilter()` and `newSourceDeviceId = null`, `newSourceDeviceName = null` to `trackingViewsRepository.updateSensorFieldConfig()`.
* **`[TST-FIL-014.3]`**: `EditSensorFieldViewModelTest.testSaveChanges_specificDevice_preservesDeviceNameAndId()`  
  Asserts that selecting a specific paired device (`selectedDeviceId = 123L`, `selectedDeviceName = "Wahoo KICKR"`) passes `FilterData.deviceName = "Wahoo KICKR"` to `banalServiceRepository.createFilter()` and `newSourceDeviceId = 123L`, `newSourceDeviceName = "Wahoo KICKR"` to the repository.
* **`[TST-FIL-014.4]`**: `FilterNullSafetyTest.timedMovingAverageFilter_expiredSamples_shouldReturnNullNotNaN()`  
  Asserts that when all samples in `TimedMovingAverageFilter` age past the time window threshold, `getFilteredValue()` returns `null` rather than `Double.NaN`.
* **`[TST-FIL-014.5]`**: `FilterNullSafetyTest.timedMovingAverageFilter_freshSamples_shouldCalculateAccurateAverage()`  
  Asserts that fresh incoming samples within the time window produce the exact mathematical mean without distortion.
* **`[TST-FIL-014.6]`**: `TranslationParityTest`  
  Validates that zero new UI strings are introduced and all 9 supported locales maintain 100% translation consistency.
* **`[TST-FIL-014.7]`**: `./gradlew testDebugUnitTest`  
  Full project clean-room regression test suite.

---

## 3. System Invariants & Preserved Behavior

1. **Rule 50 Compliance (`docs/coding_standards.md` §1.7)**:  
   Backend filter keys and service identifiers must never receive localized display strings (such as `"Best Sensor"`, `"Bester Sensor"`). Sentinel IDs (`selectedDeviceId <= 0`, e.g. `-1L`) must strictly normalize to `null` before calling `banalServiceRepository.createFilter(FilterData)`.
2. **Mathematical Filter Integrity**:  
   Rolling-window filters must never return `Double.NaN`. When all samples in a time window expire or no samples exist, the filter must strictly return `null` so downstream telemetry formatters and UI components render clean dashes (`"--"`).
3. **Thread Safety & Dispatcher Affinity**:  
   `TimedMovingAverageFilter` methods (`newValue()`, `reset()`, `getFilteredValue()`) remain strictly `synchronized`. In `EditSensorFieldViewModel`, database repository interactions remain dispatched to `Dispatchers.IO`, while UI mutations occur on `Dispatchers.Main`.
4. **Zero SQLite Schema Alterations**:  
   `TrackingViewsDatabaseManager` and `DevicesDatabaseManager` schemas already treat `SOURCE_DEVICE_ID <= 0` as `NULL`. No schema upgrade or migration script is required.
5. **Chesterton's Fence & Scope Bounding**:  
   - `NumberedMovingAverageFilter`, `AverageFilter`, `ExponentialSmoothingFilter`, and `MaxValueFilter` were forensically proven to be immune and remain completely untouched.
   - `FilterManager.java` logic for `filterData.deviceName == null` resolving best sensors via `mMySensorManager.getSensor(...)` is architecturally correct and remains unchanged.
6. **Subtask Self-Sufficiency & Human Gate Invariance (Rule 3 / Rule 45)**:  
   Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`. Parent ticket `ATT-4282` advances synchronously through lifecycle stages and halts exclusively at `Final Review (Human)`.
7. **Single Atomic Commit per Ticket (Rule 12 / Rule 44)**:  
   No intermediate git commits during Stages 1–4. All deliverables, code changes, and tests will be committed in a single atomic commit at the end of Stage 5.

---

## 4. Proposed Architectural Changes

```
┌────────────────────────────────────────────────────────────────────────┐
│ UI / Presentation Layer                                                │
│ EditSensorFieldViewModel.kt                                            │
│ • UI state retains selectedDeviceId = -1L,                             │
│   selectedDeviceName = context.getString(R.string.bestSensor)          │
│ • saveChanges(): Normalizes selectedDeviceId <= 0 to null              │
│   ──► newSourceDeviceName = if (id > 0) name else null                 │
│   ──► newSourceDeviceId   = if (id > 0) id else null                   │
└───────────────────┬────────────────────────────────┬───────────────────┘
                    │                                │
                    ▼ FilterData                     ▼ Config
┌──────────────────────────────────────┐   ┌─────────────────────────────┐
│ BANALService / FilterManager         │   │ TrackingViewsRepository     │
│ • FilterData(deviceName = null, ...) │   │ • insertSensorFieldConfig(  │
│ • FilterManager resolves ProxySensor │   │     newSourceDeviceId=null, │
│   via mMySensorManager.getSensor()   │   │     newSourceDeviceName=null│
│ • Hash: Objects.hash(..., null)      │   │   )                         │
└───────────────────┬──────────────────┘   └──────────────┬──────────────┘
                    │                                     │
                    ▼                                     ▼
┌──────────────────────────────────────┐   ┌─────────────────────────────┐
│ TimedMovingAverageFilter.java        │   │ SQLite TrackingViews.db     │
│ • getFilteredValue():                │   │ • SOURCE_DEVICE_ID = NULL   │
│   1. trimValues()                    │   │ • TrackingViewModel queries │
│   2. if (mTimestampedValues.isEmpty()│   │   hash(..., null)           │
│          return null;                │   │ ──► Immediate Match!        │
│   3. sum / size()                    │   └─────────────────────────────┘
└──────────────────────────────────────┘
```

### Component 1: `EditSensorFieldViewModel.kt` (Presentation / ViewModel Layer)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldViewModel.kt`
* **Changes in `saveChanges()`**:
  Normalize `newSourceDeviceName` and `newSourceDeviceId` before constructing `FilterData` and calling `TrackingViewsRepository`:
  ```kotlin
  val newSourceDeviceName = if (currentState.selectedDeviceId > 0) currentState.selectedDeviceName else null
  val newSourceDeviceId = if (currentState.selectedDeviceId > 0) currentState.selectedDeviceId else null
  val newSensorType = sensorType
  val newFilterType = getFinalFilterType()
  val newFilterConstant = getFinalFilterConstant()

  val filterData = FilterData(newSourceDeviceName, newSensorType, newFilterType, newFilterConstant)
  banalServiceRepository.createFilter(filterData)

  viewModelScope.launch {
      if (isNewField) {
          trackingViewsRepository.insertSensorFieldConfig(
              tabViewId = tabViewId,
              rowNr = rowNr,
              colNr = colNr,
              newSensorType = newSensorType,
              newViewSize = currentState.selectedViewSize,
              newSourceDeviceId = newSourceDeviceId,
              newSourceDeviceName = newSourceDeviceName,
              newFilterType = newFilterType,
              newFilterConstant = newFilterConstant
          )
      } else {
          trackingViewsRepository.updateSensorFieldConfig(
              sensorFieldId = sensorFieldId,
              newSensorType = newSensorType,
              newViewSize = currentState.selectedViewSize,
              newSourceDeviceId = newSourceDeviceId,
              newSourceDeviceName = newSourceDeviceName,
              newFilterType = newFilterType,
              newFilterConstant = newFilterConstant
          )
      }
  }
  ```
* **Thread Safety**: `saveChanges()` runs on `Dispatchers.Main` for state reads and launches coroutines onto `trackingViewsRepository` (which switches to `Dispatchers.IO` for database writes).

### Component 2: `TimedMovingAverageFilter.java` (Domain Telemetry Filter Layer)
* **File**: `app/src/main/java/com/atrainingtracker/banalservice/filters/TimedMovingAverageFilter.java`
* **Changes in `getFilteredValue()`**:
  Swap the order so `trimValues()` is executed first, followed immediately by an `isEmpty()` guard:
  ```java
  @Override
  public synchronized Number getFilteredValue() {
      trimValues();

      if (mTimestampedValues.isEmpty()) {
          return null;
      }

      double sum = 0;
      for (TimestampedValue timestampedValue : mTimestampedValues) {
          sum += timestampedValue.value == null ? 0 : timestampedValue.value.doubleValue();
      }
      return sum / mTimestampedValues.size();
  }
  ```
* **Thread Safety**: Synchronized on the filter instance monitor, protecting `mTimestampedValues` from concurrent mutation across `newValue()`, `reset()`, and `getFilteredValue()`.

### UI Consistency (Rule 23)
* **Status**: No UI layout or visual changes. The existing Jetpack Compose dialog `EditSensorFieldDialog.kt` and dropdown spinner remain visually and functionally identical. This change is strictly backend identifier normalization and mathematical division-by-zero prevention.
* **Reference Screen / Component**: `EditSensorFieldDialog.kt` (`app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt`).
* **Reused Components**: Existing `EditSensorFieldContent`, `Spinner`, and M3 `Dialog`.
* **Theme Tokens**: Standard theme tokens and typography unchanged.
* **New One-Off Styles & Justification**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Fix Post-Trim Emptiness Guard in `TimedMovingAverageFilter.java`
* **File**: `app/src/main/java/com/atrainingtracker/banalservice/filters/TimedMovingAverageFilter.java`
* **Action**:
  Replace lines 70–82:
  ```java
  @Override
  public synchronized Number getFilteredValue() {
      trimValues();

      if (mTimestampedValues.isEmpty()) {
          return null;
      }

      double sum = 0;
      for (TimestampedValue timestampedValue : mTimestampedValues) {
          sum += timestampedValue.value == null ? 0 : timestampedValue.value.doubleValue();
      }
      return sum / mTimestampedValues.size();
  }
  ```

### Step 2: Fix Sentinel Normalization in `EditSensorFieldViewModel.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldViewModel.kt`
* **Action**:
  In `saveChanges()`, replace lines 541–569 to compute `newSourceDeviceName = if (currentState.selectedDeviceId > 0) currentState.selectedDeviceName else null` and `newSourceDeviceId = if (currentState.selectedDeviceId > 0) currentState.selectedDeviceId else null`, passing them to `FilterData` and `trackingViewsRepository.insertSensorFieldConfig` / `updateSensorFieldConfig`.

### Step 3: Implement Unit Tests in `FilterNullSafetyTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/banalservice/filters/FilterNullSafetyTest.kt`
* **Action**:
  Add test cases:
  1. `timedMovingAverageFilter_expiredSamples_shouldReturnNullNotNaN()` (`[TST-FIL-014.4]`):
     - Instantiate `TimedMovingAverageFilter("test", SensorType.POWER, 1)` with a 1-second window.
     - Add sample via `newValue(250)`.
     - Seed an expired timestamp (or invoke `newValue`, wait >1000ms or populate `mTimestampedValues` with an aged entry `System.currentTimeMillis() - 2000`).
     - Assert `filter.filteredValue == null` and assert that it is NOT `Double.NaN`.
  2. `timedMovingAverageFilter_freshSamples_shouldCalculateAccurateAverage()` (`[TST-FIL-014.5]`):
     - Add multiple samples within the window (`100`, `200`).
     - Assert `filter.filteredValue == 150.0`.

### Step 4: Implement Unit Tests in `EditSensorFieldViewModelTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldViewModelTest.kt`
* **Action**:
  Add unit tests using MockK slot captures:
  1. `testSaveChanges_newField_bestSensor_normalizesDeviceNameToNull()` (`[TST-FIL-014.1]`):
     - Create ViewModel with `sensorFieldId = -1L`.
     - Call `saveChanges()`.
     - Verify `banalServiceRepo.createFilter(capture(filterSlot))` captured `filterSlot.captured.deviceName == null`.
     - Verify `trackingViewsRepo.insertSensorFieldConfig(...)` received `newSourceDeviceId == null` and `newSourceDeviceName == null`.
  2. `testSaveChanges_editField_bestSensor_normalizesDeviceNameToNull()` (`[TST-FIL-014.2]`):
     - Create ViewModel with `sensorFieldId = 42L`.
     - Call `saveChanges()`.
     - Verify `banalServiceRepo.createFilter(...)` captured `deviceName == null`.
     - Verify `trackingViewsRepo.updateSensorFieldConfig(...)` received `newSourceDeviceId == null` and `newSourceDeviceName == null`.
  3. `testSaveChanges_specificDevice_preservesDeviceNameAndId()` (`[TST-FIL-014.3]`):
     - Create ViewModel, call `onDeviceChanged(123L, "Stages Power")`.
     - Call `saveChanges()`.
     - Verify `banalServiceRepo.createFilter(...)` captured `deviceName == "Stages Power"`.
     - Verify `trackingViewsRepo.insertSensorFieldConfig(...)` received `newSourceDeviceId == 123L` and `newSourceDeviceName == "Stages Power"`.

### Step 5: Execute Targeted Unit Tests
* **Commands**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.filters.FilterNullSafetyTest"
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldViewModelTest"
  ```

### Step 6: Verify 9-Language Localization Parity
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification Strategy**:
  1. **Stage 4 Construction Verification**: Targeted unit tests in `FilterNullSafetyTest` and `EditSensorFieldViewModelTest` executed via `./gradlew testDebugUnitTest --tests ...` (5–15 seconds execution time).
  2. **Stage 5 Verification**: Full clean-room test suite execution:
     ```bash
     ./gradlew testDebugUnitTest
     ```
     ensuring 0 regressions across all modules.
  3. **Lint & Build Verification**:
     ```bash
     ./gradlew assembleDebug
     ```
* **Rollback Strategy**:
  - All changes reside in branch `feature/ATT-4282`.
  - In the event of unexpected side effects, the branch can be discarded cleanly without touching `develop` or `sprint/2026-43.1`:
    ```bash
    git checkout sprint/2026-43.1
    git branch -D feature/ATT-4282
    ```
