# Stage 1 Analysis: ATT-2169 - [Medium] Verify and Modernize GoldenCheetah JSON File Export Format and Data Integrity

**Ticket**: [ATT-2169](https://rainerblind.atlassian.net/browse/ATT-2169)  
**Sub-task**: [ATT-4101](https://rainerblind.atlassian.net/browse/ATT-4101) (`[Analysis]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Branch**: `feature/ATT-2169`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Statement & Motivation
aTrainingTracker provides native export of recorded training sessions to GoldenCheetah's JSON format (`GCFileWriter.java`, `.json`). GoldenCheetah (v3.6/v3.7) consumes this format for in-depth cycling performance modeling (power curves, W' balance, aerobic decoupling, and interval analysis).
However, the legacy implementation in `GCFileWriter.java` was written as an early prototype and suffers from critical specification divergences, serialization defects, and missing features:
1. **Locale-Dependent Formatting & JSON Type Violation**: Telemetry metrics (`KM`, `KPH`, `WATTS`, `CAD`, `NM`, `ALT`, power balance/smoothness/torque effectiveness metrics) are formatted using `String.format(Locale.getDefault(), "%.3f", ...)` and injected into `JSONObject` as JSON Strings. In European and German locales (`Locale.GERMANY`, `Locale.FRANCE`), this formats floating-point values with decimal commas (e.g. `"12,345"`). Furthermore, GoldenCheetah's Bison grammar (`JsonRideFile.y`) specifically specifies numeric tokens (`JS_INTEGER` / `JS_FLOAT`) for series elements (`KM ':' number`, `WATTS ':' number`, etc.), rejecting string-typed numbers and failing to parse comma-formatted decimals.
2. **Sensor Validation Bug**: In line 188 of `GCFileWriter.java`, GPS telemetry validation checks `SensorType.LATITUDE.name()` twice:
   ```java
   if (!indoorTrainerSession && haveGeo && dataValid(cursor, SensorType.LATITUDE.name()) && dataValid(cursor, SensorType.LATITUDE.name()))
   ```
   The second check was intended to validate `SensorType.LONGITUDE.name()`.
3. **Longitude Precision Degradation**: In line 192, longitude is read via `cursor.getFloat(...)` instead of `cursor.getDouble(...)`, truncating IEEE 754 precision from 53 bits (double, ~15 decimal digits) to 24 bits (float, ~7 decimal digits), introducing positioning jitter and spatial distortion of several meters in GPS coordinates.
4. **Missing Laps/Intervals Export**: GoldenCheetah defines an `"INTERVALS"` array under `"RIDE"` to delineate interval and lap boundaries (`NAME`, `START`, `STOP`, `COLOR`, `PTEST`). Currently, `GCFileWriter.java` completely omits laps recorded during workouts, preventing athletes from seeing lap breakdowns in GoldenCheetah's Intervals sidebar.
5. **Incomplete Metadata Tags**: While `DATA`, `SPORT`, and `WORKOUT_CODE` are included in `"TAGS"`, workout name (`workoutName`), description/notes (`description`), and device identifier are not cleanly populated, leaving activities untitled and without athlete notes in GoldenCheetah.
6. **Thread-Safety & TimeZone Concurrency Defects**: Date formatting uses static `SimpleDateFormat` instances without explicit UTC time zones, creating race conditions under concurrent exports and potentially shifting timestamps by device timezone offsets.
7. **Resource Leaks**: `BufferedWriter` and `Cursor` are not managed via `try-with-resources`, leaking open file descriptors and SQLite cursor locks if an exception occurs during serialization.
8. **Missing Automated Test Suite**: There is currently no `GCFileWriterTest` verifying valid JSON structure, numeric primitives, locale independence, GPS coordinate fidelity, interval extraction, or tag metadata.

---

## 2. Root Cause Analysis (Forensic Investigation)
Forensic examination of `GCFileWriter.java`, GoldenCheetah source code (`JsonRideFile.y`, `RideMetadata.h`), and aTrainingTracker database structures (`LapsDatabaseManager.java`, `WorkoutSamplesDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`) reveals:
1. **GoldenCheetah JSON Grammar Constraints (`JsonRideFile.y`)**:
   - `SAMPLES` array items contain key-value pairs where metric keys map strictly to `number` tokens:
     ```y
     series: SECS ':' number
           | KM ':' number
           | WATTS ':' number
           | NM ':' number
           | CAD ':' number
           | KPH ':' number
           | HR ':' number
           | ALTITUDE ':' number
           | LAT ':' number
           | LON ':' number
           ...
     ```
     `org.json.JSONObject.put(String, double)` and `put(String, int)` write raw numbers (e.g. `{"KM":12.345}`). The legacy code erroneously stored formatted strings: `sample.put(KM, String.format(Locale.getDefault(), "%.3f", ...))` producing `{"KM":"12,345"}`.
   - Altitude is written by GoldenCheetah as `"ALT": <number>`, which is mapped to internal altitude by GoldenCheetah's parser.
2. **GPS Validation Typo**:
   Line 188 explicitly checks `SensorType.LATITUDE.name()` twice:
   ```java
   dataValid(cursor, SensorType.LATITUDE.name()) && dataValid(cursor, SensorType.LATITUDE.name())
   ```
   If a sample row contains invalid/null longitude while latitude is non-null, `cursor.getColumnIndexOrThrow(SensorType.LONGITUDE.name())` or subsequent processing handles unvalidated data. Longitude must be validated using `dataValid(cursor, SensorType.LONGITUDE.name())`.
3. **Float vs Double Coordinate Extraction**:
   Line 191 uses `cursor.getDouble(...)` for latitude, but line 192 uses `cursor.getFloat(...)` for longitude. Both columns in `WorkoutSamples` store `REAL` (IEEE 754 64-bit double precision). Reading via `getFloat` causes precision loss.
4. **Interval Extraction Mechanics**:
   GoldenCheetah requires:
   ```json
   "INTERVALS": [
     { "NAME": "Lap 1", "START": 0, "STOP": 300 }
   ]
   ```
   `LapsDatabaseManager.getInstance(mContext).getLaps(workoutID)` provides the recorded laps with `lapNr`, `timeTotalS`, `name`, etc.
   If samples contain `SensorType.LAP_NR`, exact `START` and `STOP` seconds can be extracted from sample timestamps (`TIME_TOTAL`), or derived from cumulative lap duration.
5. **Thread Safety & TimeZone Handling**:
   `SimpleDateFormat` instances are mutable and not thread-safe. Using method-local formatters with `TimeZone.getTimeZone("UTC")` or thread-safe date formatting guarantees deterministic output independent of device locale or local daylight saving shifts.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Fix `GCFileWriter.java` to serialize numeric primitives (`double`/`int`) with strict `.` decimal separators, eliminating locale dependency.
  * Correct line 188 typo so `SensorType.LONGITUDE.name()` is validated alongside `LATITUDE`.
  * Read longitude with `cursor.getDouble(...)` preserving full coordinate precision.
  * Add `"INTERVALS"` array export mapping workout laps from `LapsDatabaseManager` / samples to GoldenCheetah interval entries (`NAME`, `START`, `STOP`).
  * Enhance `"TAGS"` with `Workout Title` (or `Route` / `Title`), `Notes`, and sanitized `Workout Code`.
  * Protect file writing and cursor access with `try-with-resources`.
  * Ensure thread-safe, UTC-anchored date formatting for `STARTTIME`.
  * Author comprehensive unit test suite `GCFileWriterTest.kt` verifying valid JSON syntax, numeric data types, German locale independence, GPS fidelity, intervals, and tag metadata.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying FIT, TCX, or GPX writers (governed by separate tickets).
  * Changing GoldenCheetah binary import or cloud upload mechanisms.
  * Altering database schemas (`WorkoutSummaries`, `WorkoutSamples`, `Laps`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-DAT-027` (*GoldenCheetah JSON File Export Format and Data Integrity Verification*). No existing requirements in `docs/requirements.md` are altered.
* **Historical Origin & Commit Trace**: `GCFileWriter.java` was created in early commits as a basic file writer (`FileFormat.GOLDEN_CHEETAH`).
* **Root Reason for Existing Formulation**: Early prototype relied on `String.format(Locale.getDefault(), ...)` and basic tag structures.
* **Preservation of Core Invariants**: The output file extension remains `.json`. Exporter contract with `BaseFileWriter`, `ExportManager`, and `ExportInfo` is strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. **Refactor `GCFileWriter.java`**:
   - Wrap `BufferedWriter` and `Cursor` in `try-with-resources`.
   - Thread-safe date conversion: Use method-local `SimpleDateFormat` with `TimeZone.getTimeZone("UTC")` and `Locale.US`.
   - Numeric Sample Serialization:
     - For double values (`KM`, `KPH`, `WATTS`, `CAD`, `NM`, `ALT`, `LRBALANCE`, `LTE`, `RTE`, `LPS`, `RPS`), round cleanly and store as numeric primitives (`sample.put(...)` with `double` or `int`, e.g. `Math.round(val * 1000.0) / 1000.0`).
     - For integer values (`SECS`, `HR`), store as `int`.
     - For GPS coordinates (`LAT`, `LON`), store raw double precision from `cursor.getDouble(...)`.
   - Fix validation check: `dataValid(cursor, SensorType.LATITUDE.name()) && dataValid(cursor, SensorType.LONGITUDE.name())`.
   - Populate `INTERVALS`:
     - Query `LapsDatabaseManager.getInstance(mContext).getLaps(workoutID)`.
     - If laps exist, construct `"INTERVALS"` JSON array with `NAME`, `START`, `STOP` (and optional `COLOR`: `"#000000"`).
   - Populate `TAGS`:
     - `Sport`: mapped via `SportTypeDatabaseManager.getInstance(mContext).getGcName(sportTypeId)`.
     - `Workout Code`: `(goal + " " + method).trim()`.
     - `Workout Title`: `workoutName` (if non-empty).
     - `Notes`: `description` (if non-empty).
     - `Device`: `TrainingApplication.getAppName()`.
     - `Data`: `data`.
2. **Author `GCFileWriterTest.kt`**:
   - Setup MockK test harness mirroring `FitFileWriterTest.kt`.
   - Validate JSON parsing with `org.json.JSONObject`.
   - Assert all metrics are numeric types (`Double`/`Int`), not `String`.
   - Force `Locale.GERMANY` and verify no commas appear in numbers.
   - Verify GPS coordinates, intervals, and tags.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regressions in existing export formats (FIT, TCX, GPX, Strava, Google Drive).
  2. Full suite clean-room unit tests (`./gradlew testDebugUnitTest`) pass at 100%.
  3. Parent ticket Human Decision Gate remains strictly intact (terminal state `Final Review (Human)`).
* **Risk Rating**: **LOW**
  - Changes are strictly isolated to `GCFileWriter.java` and its new unit test suite `GCFileWriterTest.kt`. No database schema or core tracking pipeline modifications.
