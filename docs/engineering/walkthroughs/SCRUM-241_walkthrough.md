# Walkthrough - Fix Production Crash in AverageFilter (ATT-241)

## Fulfilling REQ-FIL-011: Null-Safe Sensor Filtering

A production crash was reported where `AverageFilter` would throw a `NullPointerException` when initialized with a `null` value from a sensor. This occurred because `MySensor.addSensorListener` immediately propagates its current value (null by default) to new listeners.

### Implemented Changes

#### Core Filters
- **`AverageFilter.java`**: Added null check in `newValue()`.
- **`ExponentialSmoothingFilter.java`**: Added null check in `newValue()`.
- **`NumberedMovingAverageFilter.java`**: Added null checks in `newValue()` and `getFilteredValue()`.
- **`TimedMovingAverageFilter.java`**: Added null check in `newValue()`.

#### Quality Assurance
- **`FilterNullSafetyTest.kt`**: Created a comprehensive unit test suite that verifies all affected filters against null inputs using `MockK` for static app state mocking.

### Verification Evidence (TST-FIL-001)
- **SWE.4 Unit Verification**: Executed `./gradlew :app:testDebugUnitTest --tests com.atrainingtracker.banalservice.filters.FilterNullSafetyTest`.
- **Result**: `BUILD SUCCESSFUL`. All 4 test cases (Average, Exponential, Numbered MA, Timed MA) passed, confirming null safety.

## Final Status: Verified
Requirement REQ-FIL-011 is fully satisfied. The production crash is resolved.
