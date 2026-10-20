# Stage 1 Analysis: ATT-4282 — [Bug] Fix runtime filter registration for 'Best Sensor' fields in EditSensorFieldViewModel and NaN division in TimedMovingAverageFilter

**Ticket**: [ATT-4282](https://rainerblind.atlassian.net/browse/ATT-4282)  
**Sub-task**: [ATT-4355](https://rainerblind.atlassian.net/browse/ATT-4355) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Branch**: `feature/ATT-4282`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

During on-device testing and the Sprint Review of `ATT-1296` on a physical Google Pixel 10 (see attached artifact `w_prime_cockpit.png`), two critical runtime bugs were discovered in the sensor field configuration and telemetry filtering pipeline:

1. **Stuck Sensor Tiles on Runtime 'Best Sensor' Creation or Editing**:  
   When an athlete configures a new telemetry tile (such as `W_PRIME_BAL`, `POWER`, or `SPEED_mps`) or modifies the sensor type of an existing tile in the live Cockpit while retaining the default "Best Sensor" source (`selectedDeviceId = -1`), the newly added or updated field remains permanently stuck displaying `--` (or empty gauge tracks) throughout the workout session. The tile only starts displaying live data after the application is completely killed and restarted.

2. **Division by Zero (`Double.NaN`) in Rolling Moving Average Filter**:  
   In `TimedMovingAverageFilter.java`, `getFilteredValue()` verifies whether samples exist *before* purging expired entries via `trimValues()`. If all buffered samples in the moving time window age out simultaneously between sensor evaluations (for instance, when an athlete stops pedaling or coasting occurs), `trimValues()` evacuates the collection. The method subsequently computes `sum / mTimestampedValues.size()`, evaluating `0.0 / 0 = Double.NaN`. In Java, `Double.NaN` is a non-null object that bypasses standard null checks in downstream mathematical calculations (such as `CyclingPowerMetricsDevice.calculateMetrics()`) and formatters, rendering `"NaN"` or corrupting dependent metrics.

**Motivation**:  
- Athletes frequently adjust or add telemetry tiles on the fly while preparing for or during workouts. Runtime configuration changes must reflect immediately without requiring an application restart.
- Telemetry filters must maintain strict mathematical integrity: when no valid samples exist in a moving window, filters must return `null` so that UI cards and formatting layers render clean dashes (`"--"`) instead of `NaN` artifacts or arithmetic crashes.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Root Cause 1: `EditSensorFieldViewModel` Leaking Localized UI Display Sentinel into `FilterData`

A forensic trace through `EditSensorFieldViewModel.kt`, `FilterManager.java`, `TrackingViewsDatabaseManager.java`, and `TrackingViewModel.kt` reveals the exact failure mechanism:

1. **Sentinel ID Paired with Localized Display Label**:  
   In `EditSensorFieldViewModel.setupDefaultState()` (lines 220–221, 231–232) and `onSensorTypeChanged()` (lines 298–299):
   ```kotlin
   selectedDeviceId = -1,
   selectedDeviceName = context.getString(R.string.bestSensor), // e.g. "Bester Sensor" (DE) or "Best sensor" (EN)
   ```
   The view model initializes `selectedDeviceId` to `-1` (the sentinel representing "Best Sensor"), but simultaneously assigns `selectedDeviceName` the localized UI string from resources.

2. **Unsanitized Construction of Backend Identifiers in `saveChanges()`**:  
   In `EditSensorFieldViewModel.saveChanges()` (lines 541–548):
   ```kotlin
   val newSourceDeviceName = currentState.selectedDeviceName
   val newSensorType = sensorType
   val newFilterType = getFinalFilterType()
   val newFilterConstant = getFinalFilterConstant()

   val filterData = FilterData(newSourceDeviceName, newSensorType, newFilterType, newFilterConstant)
   banalServiceRepository.createFilter(filterData)
   ```
   `saveChanges()` forwards `currentState.selectedDeviceName` directly into `FilterData` without checking whether `selectedDeviceId <= 0`. As a result, `FilterData.deviceName` is set to `"Bester Sensor"` (or `"Best sensor"`).

3. **Failed Filter Creation in `FilterManager.createFilter()`**:  
   In `FilterManager.java` (lines 108–122):
   ```java
   // when deviceName is null, we try to get the best sensor
   if (filterData.deviceName == null) {
       mySensor = mMySensorManager.getSensor(filterData.sensorType);
   } else {
       MyDevice myDevice = mDeviceManager.getMyDeviceByName(filterData.deviceName);
       if (myDevice != null) {
           mySensor = myDevice.getSensor(filterData.sensorType);
       }
   }

   if (mySensor == null) {  // sensor not (yet) available
       mRequiredButNotAvailableFilters.put(hashKey, filterData);
       return;
   }
   ```
   Because `filterData.deviceName` is `"Bester Sensor"` (non-null), `FilterManager` skips `mMySensorManager.getSensor(...)` (which resolves the best sensor via `ProxySensor`) and instead attempts to find a paired physical device named `"Bester Sensor"` via `mDeviceManager.getMyDeviceByName(...)`. Since no physical Bluetooth/ANT+ device has that name, `myDevice` is `null`, `mySensor` is `null`, and the filter is never registered in `myFilterMap`. Instead, it is stashed into `mRequiredButNotAvailableFilters` indefinitely.

4. **Hash Mismatch in `TrackingViewModel`**:  
   Simultaneously, `TrackingViewsDatabaseManager.insertSensorFieldAt()` (lines 191–193) evaluates:
   ```java
   if (sourceDeviceId != null && sourceDeviceId >= 0) {
       values.put(TrackingViewsDbHelper.SOURCE_DEVICE_ID, sourceDeviceId);
   }
   ```
   Since `sourceDeviceId = -1`, SQLite stores `SOURCE_DEVICE_ID = NULL`.  
   When `TrackingViewsRepository.fetchSensorFieldConfigs()` reads the row back (lines 299–304):
   ```kotlin
   val deviceName = if (sourceDeviceId > 0) devicesDbManager.getDeviceName(sourceDeviceId) else null
   ```
   It emits `SensorFieldConfig` with `sourceDeviceName = null`.  
   In `TrackingViewModel.kt` (lines 493 and 591), telemetry matching uses:
   ```kotlin
   val uniqueHash = Objects.hash(sensorType, filterType, filterConstant, sourceDeviceName)
   ```
   Because `config.sourceDeviceName` is `null`, `TrackingViewModel` looks for incoming data matching `Objects.hash(..., null)`. Even if `FilterManager` had created a filter for `"Bester Sensor"`, the hashes would not match.

5. **Why the Bug Disappeared on App Restart**:  
   When the app is restarted, `MainActivityWithNavigation.kt` initializes filters via:
   ```kotlin
   for (filterData in TrackingViewsDatabaseManager.getInstance(applicationContext).getAllFilterData(devicesDatabaseManager)) {
       banalService.createFilter(filterData)
   }
   ```
   In `TrackingViewsDatabaseManager.getAllFilterData()` (lines 633–635), `devicesDatabaseManager.getDeviceName(-1)` returns `null`. Thus, on cold start, `filterData.deviceName` is `null`, `FilterManager` successfully registers the filter using `mMySensorManager.getSensor()`, and the hash matches `TrackingViewModel`.

---

### 2.2 Root Cause 2: Division by Zero (`0.0 / 0 = Double.NaN`) in `TimedMovingAverageFilter.getFilteredValue()`

In `TimedMovingAverageFilter.java` (lines 70–82):
```java
@Override
public synchronized Number getFilteredValue() {
    if (mTimestampedValues.size() == 0) {
        return null;
    }

    trimValues();

    double sum = 0;
    for (TimestampedValue timestampedValue : mTimestampedValues) {
        sum += timestampedValue.value == null ? 0 : timestampedValue.value.doubleValue();
    }
    return sum / mTimestampedValues.size();
}
```

1. **Premature Emptiness Guard**:  
   The check `if (mTimestampedValues.size() == 0) return null;` occurs **before** calling `trimValues()`.
2. **Collection Evacuation During `trimValues()`**:  
   If `mTimestampedValues` contains entries that have aged beyond `threshold = System.currentTimeMillis() - 1000 * mSeconds`, `trimValues()` polls all of them:
   ```java
   while (mTimestampedValues.peek() != null && mTimestampedValues.peek().timestamp < threshold) {
       mTimestampedValues.poll();
   }
   ```
3. **Division by Zero**:  
   If all samples were older than the window, `mTimestampedValues.size()` becomes `0`. The loop does not execute, leaving `sum = 0.0`. The return statement then calculates:
   $$\frac{\text{sum}}{\text{mTimestampedValues.size}()} = \frac{0.0}{0} = \text{Double.NaN}$$
4. **Impact on Downstream Telemetry**:  
   In Java floating-point arithmetic, $0.0 / 0.0 = \text{Double.NaN}$.  
   `Double.valueOf(Double.NaN)` is returned.  
   When passed to `CyclingPowerMetricsDevice.calculateMetrics()` or `TrackingViewModel`, `powerFiltered != null` evaluates to `true` (since `NaN` is not `null`), bypassing fallback branches and corrupting dependent computations or rendering literal `"NaN"` on the screen.

### 2.3 Forensic Survey of Other Filter Classes in `com.atrainingtracker.banalservice.filters`

An exhaustive review of all other filter implementations confirms:
- **`NumberedMovingAverageFilter.java`**: Implements a fixed-size ring buffer array (`ArrayList<Number> mValues`). It does not trim by timestamp. In `getFilteredValue()`, line 84 explicitly checks: `return count == 0 ? null : sum / count;`. It is completely immune.
- **`AverageFilter.java`**: Checks `if (mNumberOfSamples == 0) return null; else return mAccumulatedValue / mNumberOfSamples;`. Immune.
- **`ExponentialSmoothingFilter.java`**: Implements single-pole recurrence $y_k = \alpha x_k + (1-\alpha)y_{k-1}$ without division. Immune.
- **`MaxValueFilter.java`**: Tracks scalar maximum; returns `mMaxValue`. Immune.
- **`InstantaneousFilter.java`**: Returns latest sample `mValue` directly. Immune.

**Conclusion**: `TimedMovingAverageFilter` is the only filter class in `banalservice.filters` subject to post-trim collection evacuation.

---

## 3. User Scope Grounding (ATT-1250)

### In-Scope Goals:
1. **Sentinel Normalization in `EditSensorFieldViewModel`**:
   - In `saveChanges()`, normalize `newSourceDeviceName` to `null` whenever `selectedDeviceId <= 0` before constructing `FilterData` and calling `banalServiceRepository.createFilter(filterData)`.
   - Pass the normalized `newSourceDeviceName = null` and `newSourceDeviceId = null` to `trackingViewsRepository.insertSensorFieldConfig()` and `updateSensorFieldConfig()`.
   - In `setupDefaultState()` and `onSensorTypeChanged()`, keep `selectedDeviceId = -1` and ensure `selectedDeviceName` state initialization is clean and consistent.
2. **Post-Trim Emptiness Guard in `TimedMovingAverageFilter`**:
   - In `TimedMovingAverageFilter.getFilteredValue()`, invoke `trimValues()` first, and then immediately check `if (mTimestampedValues.isEmpty()) return null;` before summing and dividing.
3. **Defensive Verification in `TrackingViewsRepository`**:
   - Verify that `insertSensorFieldConfig()` and `updateSensorFieldConfig()` safely forward `null` for `sourceDeviceId` and `sourceDeviceName` when sentinel values are passed.
4. **Unit Test Coverage**:
   - Unit tests in `EditSensorFieldViewModelTest`:
     - Assert that saving a new field with "Best Sensor" (`selectedDeviceId = -1`) passes `FilterData(deviceName = null, ...)` to `banalServiceRepository`.
     - Assert that updating an existing field to "Best Sensor" passes `FilterData(deviceName = null, ...)`.
     - Assert that selecting a specific paired device (`selectedDeviceId > 0`, e.g. `123L`) correctly passes its device name to `FilterData`.
   - Unit tests in `TimedMovingAverageFilterTest`:
     - Assert that when all samples in `mTimestampedValues` expire past the time window, `getFilteredValue()` returns `null` instead of `Double.NaN`.
     - Assert that adding fresh samples computes the expected average.

### Out-of-Scope Non-Goals (Scope Bounding):
- **No Database Schema Alterations**: `TrackingViews.db` and `Devices.db` schemas already store `NULL` for `SOURCE_DEVICE_ID` when `sourceDeviceId <= 0`. No migration or schema changes are needed.
- **No Modifications to Unaffected Filter Classes**: `NumberedMovingAverageFilter`, `AverageFilter`, `ExponentialSmoothingFilter`, and `MaxValueFilter` were forensically proven to be immune and must remain untouched.
- **No UI Visual Redesign**: The Compose dialog layout (`EditSensorFieldDialog.kt`) and `Spinner` behavior remain intact; this is strictly a backend identifier normalization and math bugfix.
- **No Refactoring of `FilterManager` Internal Maps**: `FilterManager`'s design where `deviceName = null` maps to the best sensor via `mMySensorManager.getSensor()` is correct and must be preserved.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

The proposed fix introduces `REQ-FIL-014` (*Backend Filter Key Normalization, Runtime 'Best Sensor' Field Registration & Post-Trim Rolling Filter NaN Guard*), which refines and amends `REQ-FIL-011` (*Null-Safe Sensor Filtering*) and `REQ-UI-167` (*Intuitive Smoothing Presets, Simplified Filter Configuration & Smart Athletic Defaults*).

* **Original Requirement ID & Target**:  
  Refines `REQ-FIL-011` (*Null-Safe Sensor Filtering*) and `REQ-UI-167` (*Intuitive Smoothing Presets, Simplified Filter Configuration & Smart Athletic Defaults*). Targets `EditSensorFieldViewModel.kt`, `TrackingViewsRepository.kt`, and `TimedMovingAverageFilter.java`.

* **Historical Origin & Commit Trace**:  
  - `REQ-FIL-011` was introduced in early baseline architecture and formalized in Sprint 2026-41.10 (`ATT-3222`).  
  - `REQ-UI-167` was introduced in Sprint 2026-40.4 (`ATT-1276`, commit `69e4f1a2`) when Compose-based sensor field editing was created.  
  - Discovered during on-device testing and Sprint Review 2026-42.10 (`ATT-1296`, commit `889ebd18` / `c8939a3f`) on Google Pixel 10 (attached `w_prime_cockpit.png`), where `W_PRIME_BAL` added at runtime remained stuck displaying `--` and `TimedMovingAverageFilter` evaluated `0.0 / 0 = Double.NaN`. Codified into Document Triad Rule 50 (`docs/coding_standards.md` §1.7).

* **Root Reason for Existing Formulation**:  
  - In `EditSensorFieldViewModel`, `selectedDeviceId` was assigned `-1` as a sentinel for "Best Sensor", while `selectedDeviceName` was populated with `context.getString(R.string.bestSensor)` to supply a display string for the dropdown spinner. When `saveChanges()` was subsequently wired to notify `BANALService.createFilter()`, the display string was forwarded without normalizing sentinel IDs to `null`.
  - In `TimedMovingAverageFilter`, `getFilteredValue()` placed the check `if (mTimestampedValues.size() == 0) return null;` before `trimValues()` under the assumption that `trimValues()` would only discard older samples while leaving at least one fresh sample. The author did not anticipate the boundary condition where all samples in the window age out simultaneously between evaluations.

* **Preservation of Core Invariants**:  
  - Preserves explicit device selection: When `selectedDeviceId > 0`, the specific device name continues to be passed to `FilterData` and persisted to SQLite.
  - Preserves "Best Sensor" resolution: When `selectedDeviceId <= 0`, `FilterData.deviceName = null` correctly triggers `MySensorManager.getSensor()` to fetch the active `ProxySensor`.
  - Preserves mathematical moving average contracts: Returning `null` when a time window has no samples ensures downstream code and canonical formatters render clean dashes (`"--"`) rather than `NaN`.
  - Maintains 100% clean-room test suite pass rate.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 `EditSensorFieldViewModel.kt` Normalization
In `EditSensorFieldViewModel.saveChanges()`:
```kotlin
// Normalize sentinel selectedDeviceId (<= 0) to null deviceName for backend FilterData and repository
val newSourceDeviceName = if (currentState.selectedDeviceId > 0) currentState.selectedDeviceName else null
val newSourceDeviceId = if (currentState.selectedDeviceId > 0) currentState.selectedDeviceId else null
val newSensorType = sensorType
val newFilterType = getFinalFilterType()
val newFilterConstant = getFinalFilterConstant()

// Register filter in BANALService with normalized deviceName (null for "Best Sensor")
val filterData = FilterData(newSourceDeviceName, newSensorType, newFilterType, newFilterConstant)
banalServiceRepository.createFilter(filterData)
```
When saving to `trackingViewsRepository.insertSensorFieldConfig()` or `updateSensorFieldConfig()`, pass `newSourceDeviceId` and `newSourceDeviceName`.

### 5.2 `TimedMovingAverageFilter.java` Post-Trim Empty Guard
In `TimedMovingAverageFilter.java`:
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
Calling `trimValues()` first ensures that only currently valid (non-expired) samples are considered. The subsequent `isEmpty()` check guarantees that division by zero is physically impossible, and `null` is cleanly returned when no samples remain.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. **Rule 50 Compliance (`docs/coding_standards.md` §1.7)**: Backend filter keys must never receive localized display strings (`"Best Sensor"` / `"Bester Sensor"`). Sentinel IDs (`<= 0`) must normalize to `null`.
  2. **Mathematical Filter Integrity**: Rolling filters must never return `Double.NaN`. An empty window must strictly return `null`.
  3. **Multi-Locale Parity**: Normalization must operate purely on numeric sentinel IDs (`selectedDeviceId <= 0`) so that behavior is 100% identical across all 9 application locales without string parsing or comparisons.
  4. **Thread Safety**: `TimedMovingAverageFilter` operations remain `synchronized` across `newValue()`, `reset()`, and `getFilteredValue()`.
  5. **Human Decision Gate**: Parent ticket ATT-4282 will advance to `Test Spec` upon Stage 1 approval and remain strictly guarded until human review in Ceremony 2.

* **Risk Rating**: **LOW**  
  *Justification*: The changes are isolated and targeted:
  - Normalizing `newSourceDeviceName` to `null` in `EditSensorFieldViewModel.saveChanges()` aligns runtime creation with cold-start persistence logic.
  - Inverting the order of `trimValues()` and `isEmpty()` in `TimedMovingAverageFilter` prevents an arithmetic division-by-zero edge case without altering the underlying moving average algorithm.
  - Zero database schema migrations, zero UI layout modifications, and zero impact on other filter types.
