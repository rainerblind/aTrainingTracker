# Walkthrough: Verify and Modernize GoldenCheetah JSON File Export Format and Data Integrity (ATT-2169)

* **Parent Ticket**: [ATT-2169](https://rainerblind.atlassian.net/browse/ATT-2169) (*[Medium] Verify and Modernize GoldenCheetah JSON File Export Format and Data Integrity*)
* **Subtasks**:
  - [ATT-4101](https://rainerblind.atlassian.net/browse/ATT-4101) (*[Analysis] [Medium] Verify and Modernize GoldenCheetah JSON File Export Format and Data Integrity* - Erledigt)
  - [ATT-4102](https://rainerblind.atlassian.net/browse/ATT-4102) (*[Test-Spec] [Medium] Verify and Modernize GoldenCheetah JSON File Export Format and Data Integrity* - Erledigt)
  - [ATT-4103](https://rainerblind.atlassian.net/browse/ATT-4103) (*[Impl-Plan] [Medium] Verify and Modernize GoldenCheetah JSON File Export Format and Data Integrity* - Erledigt)
  - [ATT-4104](https://rainerblind.atlassian.net/browse/ATT-4104) (*[Implementation] [Medium] Verify and Modernize GoldenCheetah JSON File Export Format and Data Integrity* - Erledigt)
  - [ATT-4105](https://rainerblind.atlassian.net/browse/ATT-4105) (*[Test] [Medium] Verify and Modernize GoldenCheetah JSON File Export Format and Data Integrity* - In Review)
* **Requirement**: `REQ-DAT-027` (Verified)
* **Test Specification**: `TST-DAT-022` (Verified)
* **Target Version**: `V4.9.40`
* **Branch**: `feature/ATT-2169`

---

## 1. Executive Summary

GoldenCheetah is an open-source performance analysis desktop application utilizing a JSON ride file format (`JsonRideFile.y`). In previous iterations of `GCFileWriter.java`, telemetry metrics inside the `SAMPLES` array were formatted as strings using `String.format(Locale.getDefault(), "%.3f", ...)`. In German and other European locales where commas represent the decimal separator, this emitted strings with decimal commas (e.g. `"12,345"`), violating GoldenCheetah's parser expectation for raw numeric tokens (`JS_FLOAT` / `JS_INTEGER`) and breaking import parsing. Furthermore, GPS coordinates suffered from a copy-paste bug (validating `SensorType.LATITUDE.name()` twice instead of `LONGITUDE`) and longitude was retrieved via single-precision `cursor.getFloat()`, losing precision compared to 64-bit SQLite storage. Finally, lap intervals and descriptive ride tags were omitted or incomplete.

We resolved these defects and modernized `GCFileWriter.java` to achieve full compliance with GoldenCheetah's specification while ensuring robust resource management.

Key capabilities delivered:
1. **Locale-Independent Numeric JSON Primitives (`GCFileWriter.java`)**:
   - Emits raw JSON numbers directly into each sample `JSONObject` (`KM`, `KPH`, `WATTS`, `CAD`, `NM`, `ALT`, `LAT`, `LON`, `LRBALANCE`, `LTE`, `RTE`, `LPS`, `RPS`), eliminating string encapsulation and localized decimal commas regardless of system locale.
   - Formats `SECS` and `HR` as integers.
2. **GPS Coordinate Precision & Validation (`GCFileWriter.java`)**:
   - Validates both `SensorType.LATITUDE.name()` and `SensorType.LONGITUDE.name()` via `dataValid(...)`.
   - Reads longitude with `cursor.getDouble(...)`, matching latitude and preserving full 64-bit IEEE 754 precision.
3. **Workout Lap Intervals Export (`GCFileWriter.java`)**:
   - Serializes GoldenCheetah `"INTERVALS"` array from `LapsDatabaseManager.getInstance(mContext).getLaps(workoutID)`.
   - Serializes `NAME`, `START` (elapsed seconds), `STOP` (elapsed seconds), and `COLOR`.
4. **Metadata & Tags Alignment (`GCFileWriter.java`)**:
   - Populates `"TAGS"` with `Sport` (via `SportTypeDatabaseManager.getGcName`), `Workout Code`, `Workout Title`, `Notes`, `Device`, and `Data`.
5. **Thread-Safe UTC Start Time & Resource Safety (`GCFileWriter.java`)**:
   - Converts workout start time using thread-safe, UTC-anchored date formatting.
   - Protects `BufferedWriter` and `Cursor` within `try-with-resources` blocks to prevent leakages on normal or exceptional paths.

---

## 2. Changes Summary

| Area | Component | Change | Description |
| :--- | :--- | :--- | :--- |
| **Export Core** | `GCFileWriter.java` | [MODIFY] | Modernized JSON serialization to emit raw numeric types, added `INTERVALS` block, corrected GPS validation/double precision, enriched `TAGS`, and added `try-with-resources`. |
| **Export Tests** | `GCFileWriterTest.kt` | [NEW] | Comprehensive unit test suite (5 tests) verifying valid JSON numeric types, German locale independence, GPS double precision, lap intervals, tags metadata, and exception resource safety. |
| **Living Requirements** | `docs/requirements.md` | [MODIFY] | Documented `REQ-DAT-027` with Given-When-Then criteria and marked as `Verified`. |
| **Living Test Specs** | `docs/tests.md` | [MODIFY] | Documented `TST-DAT-022` test suite specification and marked as `Verified`. |

---

## 3. Verification & Test Results

### 3.1 Automated Tests
* **Targeted Unit Tests (`GCFileWriterTest.kt`)**:
  - `testGCFileWriter_validNumericJson_and_localeIndependence`: PASSED (asserts JSON numbers and zero decimal commas under `Locale.GERMANY`).
  - `testGCFileWriter_gpsPrecisionAndCorrection`: PASSED (asserts longitude verification and 7-decimal double precision).
  - `testGCFileWriter_intervalsExport`: PASSED (asserts `INTERVALS` block structure and elapsed second boundaries).
  - `testGCFileWriter_tagsMetadataAlignment`: PASSED (asserts complete metadata tags mapping).
  - `testGCFileWriter_resourceSafetyOnException`: PASSED (asserts clean resource release on database failure).
* **Full Clean-Room Test Suite**:
  - Command: `./gradlew testDebugUnitTest`
  - Result: **3155/3155 tests passing (100% GREEN, 0 failures, 0 regressions)**.

### 3.2 Requirement Governance Verification
* Script: `python3 tools/verify_requirement_governance.py`
* Result: Clean execution, 100% compliance.
