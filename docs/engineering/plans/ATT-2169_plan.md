# Stage 3: Implementation Plan - ATT-2169: [Medium] Verify and Modernize GoldenCheetah JSON File Export Format and Data Integrity

**Ticket**: [ATT-2169](https://rainerblind.atlassian.net/browse/ATT-2169)  
**Sub-task**: [ATT-4103](https://rainerblind.atlassian.net/browse/ATT-4103) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-281](https://rainerblind.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-DAT-027` (*GoldenCheetah JSON File Export Format and Data Integrity Verification*)  
**Test Mapping**: `TST-DAT-022`  
**Branch**: `feature/ATT-2169`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Description & Background
`GCFileWriter.java` exports workout sessions into GoldenCheetah-compatible JSON (`.json`). The legacy implementation suffered from:
1. `String.format(Locale.getDefault(), "%.3f", ...)` formatting metric doubles into string types, producing decimal commas in European/German locales (`"12,345"`), which breaks GoldenCheetah's Bison parser (`JsonRideFile.y`).
2. Duplicate `SensorType.LATITUDE.name()` validity check while omitting `LONGITUDE` validity checking.
3. Longitude extraction via `cursor.getFloat(...)` causing 32-bit floating-point precision truncation.
4. Total absence of the `"INTERVALS"` JSON block mapping workout laps to GoldenCheetah interval structures.
5. Incomplete `"TAGS"` metadata (omitting workout title and notes).
6. Non-thread-safe static `SimpleDateFormat` date formatters without UTC timezone binding.
7. Unprotected file writer and database cursor resources without `try-with-resources`.
8. Absence of automated unit test coverage (`GCFileWriterTest.kt`).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-DAT-027` (*GoldenCheetah JSON File Export Format and Data Integrity Verification*)
  * Sec 1.2.1: Numeric telemetry primitives (locale-independent, zero decimal commas).
  * Sec 1.2.2: GPS sensor validation (`LATITUDE` and `LONGITUDE`) and 64-bit double precision.
  * Sec 1.2.3: Workout laps export to GoldenCheetah `"INTERVALS"` block.
  * Sec 1.2.4: Metadata tags (`Sport`, `Workout Code`, `Workout Title`, `Notes`, `Device`, `Data`).
  * Sec 1.2.5: Thread-safe, UTC-anchored start time formatting.
  * Sec 1.2.6: Resource cleanup via `try-with-resources`.
* **Test Mapping**: `TST-DAT-022` (*GoldenCheetah JSON File Export Format and Data Integrity Verification Suite*)
  * `TST-DAT-022.1` to `TST-DAT-022.6`: `GCFileWriterTest.kt` unit test cases.
  * `TST-DAT-022.7`: `TranslationParityTest`.
  * `TST-DAT-022.8`: Full clean-room regression suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior
1. **File Extension & Exporter Contract**: Output file format remains `.json` via `FileFormat.GOLDEN_CHEETAH` and `BaseFileWriter` pipeline.
2. **Zero Regressions Across Exporters**: FIT, TCX, GPX, and Strava export engines remain unaffected.
3. **Pure Numeric Literals in SAMPLES**: Every metric in `SAMPLES` is stored as `double` or `int` in `JSONObject`, producing unquoted numbers with `.` decimal separators.
4. **Subtask Direct Completion & Synchronous Parent Stage Progression (Rule 3 / Rule 45)**: Sub-task completes directly to `Erledigt` via `freigabe` once Gate audit passes.
5. **Parent Human Gate Invariance**: Terminal transition on `ATT-2169` is `Final Review (Human)`.
6. **One Commit per Ticket (Rule 44)**: Intermediate stage commits are avoided; all changes will be committed as a single atomic commit at the conclusion of Stage 5.

---

## 4. Proposed Architectural Changes

### Component 1: `GCFileWriter.java` (`app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/GCFileWriter.java`)
* **Thread-Safe Date Formatting**:
  Replace static mutable `SimpleDateFormat` with thread-safe method-local formatters configured with `TimeZone.getTimeZone("UTC")` and `Locale.US`.
* **Resource Safety**:
  Wrap `BufferedWriter` and `Cursor` inside `try-with-resources`:
  ```java
  try (BufferedWriter bufferedWriter = getBufferedWriter(exportInfo.getShortPath());
       Cursor cursor = db.query(...)) { ... }
  ```
* **Pure Numeric JSON Metrics Serialization**:
  Introduce clean rounding utilities:
  ```java
  private static double round3(double value) {
      return Math.round(value * 1000.0) / 1000.0;
  }
  private static double round1(double value) {
      return Math.round(value * 10.0) / 10.0;
  }
  ```
  Serialize doubles directly:
  - `KM`: `round3(cursor.getDouble(...) / 1000.0)`
  - `KPH`: `round3(cursor.getDouble(...) * 3.6)`
  - `WATTS`, `LRBALANCE`, `LTE`, `RTE`, `LPS`, `RPS`, `CAD`, `ALT`: `round1(...)`
  - `NM`: `round3(...)`
  - `SECS`, `HR`: `cursor.getInt(...)`
* **Complete GPS Validation & Double Precision**:
  ```java
  if (!indoorTrainerSession && haveGeo 
          && dataValid(cursor, SensorType.LATITUDE.name()) 
          && dataValid(cursor, SensorType.LONGITUDE.name())) {
      latitudeOld = latitude;
      longitudeOld = longitude;
      latitude = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.LATITUDE.name()));
      longitude = cursor.getDouble(cursor.getColumnIndexOrThrow(SensorType.LONGITUDE.name()));
      if (WRITE_ONLY_ON_NEW_GEO_DATA && latitude == latitudeOld && longitude == longitudeOld) {
          // skip redundant point
      } else {
          sample.put(LAT, latitude);
          sample.put(LON, longitude);
      }
  }
  ```
* **Intervals Block Generation**:
  Query `LapsDatabaseManager.getInstance(mContext).getLaps(workoutID)`. If non-empty, serialize `"INTERVALS"` array with `NAME`, `START`, and `STOP` seconds.
* **Tags Metadata Alignment**:
  Populate `"Sport"`, `"Workout Code"` (trimmed), `"Workout Title"` (if non-empty), `"Notes"` (if non-empty), `"Device"`, and `"Data"`.

### Component 2: `GCFileWriterTest.kt` (`app/src/test/java/com/atrainingtracker/trainingtracker/exporter/writer/GCFileWriterTest.kt`)
* Author comprehensive test suite leveraging MockK and Robolectric/pure JUnit:
  - Valid JSON syntax check with `org.json.JSONObject`.
  - Type checking asserting that `sample.get("KM")` is `java.lang.Number`.
  - Forcing `Locale.GERMANY` to prove zero commas in output.
  - Asserting `LAT` and `LON` retain double precision without float truncation.
  - Asserting `"INTERVALS"` array serialization and lap timing.
  - Asserting `"TAGS"` mapping.
  - Asserting cursor/writer closure on exception.

### UI Consistency (Rule 23)
* **No UI changes.** (Backend export format modernization).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Check Gate 3 Prerequisite (Rule 4)
* Verify `python3 tools/jira_util.py check-gate ATT-4103` exits with 0 before editing production code.

### Step 2: Refactor `GCFileWriter.java`
* Target: `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/GCFileWriter.java`
* Implement thread-safe UTC date conversion.
* Implement `try-with-resources` resource safety.
* Implement numeric metric serialization eliminating `String.format(Locale.getDefault(), ...)`.
* Fix GPS validation (`SensorType.LONGITUDE.name()`) and read longitude via `cursor.getDouble(...)`.
* Add `"INTERVALS"` block from `LapsDatabaseManager`.
* Add `"Workout Title"` and `"Notes"` to `"TAGS"`.

### Step 3: Implement Unit Test Suite `GCFileWriterTest.kt`
* Target: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/writer/GCFileWriterTest.kt`
* Author `GCFileWriterTest` with tests for numeric types, German locale, GPS precision, intervals, and tags.

### Step 4: Execute Targeted Unit Tests
* Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.exporter.writer.GCFileWriterTest"`
* Confirm all tests pass cleanly.

---

## 6. Verification & Rollback Plan
* **Verification**:
  - Run targeted test suite `GCFileWriterTest`.
  - In Stage 5, execute full clean-room regression `./gradlew testDebugUnitTest`.
* **Rollback Plan**:
  - If issues arise, changes remain strictly isolated to `feature/ATT-2169`. `git checkout sprint/2026-42.10` ensures zero pollution of the sprint integration baseline.
