# Stage 3: Implementation Plan - ATT-4013: GPX/TCX exports can be malformed and Downloads copies use wrong MIME type

**Ticket**: [ATT-4013](https://rainerblind.atlassian.net/browse/ATT-4013)  
**Parent Epic**: None  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-EXP-022` (*GPX/TCX Export XML Well-Formedness, Garmin Schema Compliance, Downloads MIME Type & Privacy Protection*)  
**Test Mapping**: `TST-EXP-019` (*GPX/TCX Export XML Well-Formedness, Garmin Schema Compliance, Downloads MIME Type & Privacy Verification*)  
**Branch**: `feature/ATT-4013`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Description & Background

In the current file export subsystems:
1. `GPXFileWriter.java` track segment closure (`</trkseg>`) depends on `lap != BANALService.INIT_LAP_NR`. If a workout's initial lap 0 has been cropped, the first sample row has `lap > 0`, triggering an orphan closing `</trkseg>` tag prior to any `<trkseg>`. For zero-sample workouts, the while loop is bypassed, but the tail unconditionally emits an orphan `</trkseg>`.
2. `TCXFileWriter.java` emits `<Creator xsi:type="Device_t">` outside `<Activity>` at the document root under `<TrainingCenterDatabase>`. In Garmin's official `TrainingCenterDatabasev2.xsd` schema, `<Creator>` is valid only as an optional child of `<Activity>` (following `<Notes>` and preceding `<Extensions>`). Furthermore, mandatory schema children for `Device_t` and `Application_t` were commented out.
3. Both `GPXFileWriter.java` and `TCXFileWriter.java` use static `SimpleDateFormat` instances (`msdfFromDb`, `msdfToXML`) across threads without synchronization or UTC timezone designation, risking data races during parallel exports and local DST timestamp shifts.
4. `BaseExporter.copyFileToDownloads` copies all files using hardcoded `application/gpx+xml`, incorrectly assigning GPX MIME types to CSV, FIT, TCX, and JSON files.
5. `BaseFileWriter.onFinished` indiscriminately invokes `copyFileToDownloads` for internal temporary files created solely for community uploads (e.g. `FileFormat.STRAVA` or `ExportType.COMMUNITY`), leaking athlete tracks to public shared storage.
6. `copyFileToDownloads` lacks null checks on `resolver.insert()`, resulting in `NullPointerException` if MediaStore insertion fails.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-EXP-022` (*GPX/TCX Export XML Well-Formedness, Garmin Schema Compliance, Downloads MIME Type & Privacy Protection*)
* **Test Mapping**: `TST-EXP-019` (*GPX/TCX Export XML Well-Formedness, Garmin Schema Compliance, Downloads MIME Type & Privacy Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Keep Public Downloads Copy for Explicit File Exports**: Users rely on exported workout files (`CSV`, `TCX`, `GPX`, `FIT`, `GC`) appearing in `Downloads/`. Do NOT disable or hide `copyFileToDownloads` behind an off-by-default setting for standard file exports.
2. **Custom XML Extension Preservation**: Custom extension schemas (`att:ActivityExtension`, `att:LapExtension`) and naming conventions must remain 100% intact.
3. **Thread Safety & UTC Formatting**: Timestamps in export files must represent pure UTC ISO-8601 instants.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing their respective Gate review.
5. **Parent Human Gate Invariance**: Final completion of `ATT-4013` remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `FileFormat.java` Per-Format MIME Types
- Add `public String getMimeType()`:
  - `GPX`: `"application/gpx+xml"`
  - `TCX`, `STRAVA`: `"application/vnd.garmin.tcx+xml"`
  - `FIT`: `"application/vnd.ant.fit"`
  - `CSV`: `"text/csv"`
  - `GC`: `"application/json"`

### Component 2: `BaseFileWriter.java` & `BaseExporter.java` Privacy & MediaStore Safety
- In `BaseFileWriter.onFinished(@NonNull ExportInfo exportInfo)`:
  - Guard: only copy to downloads if `exportInfo.getFileFormat() != FileFormat.STRAVA && exportInfo.getExportType() != ExportType.COMMUNITY`.
  - On API 29+ (`Build.VERSION_CODES.Q`), invoke `copyFileToDownloads(exportInfo)`.
- In `BaseExporter.copyFileToDownloads(@NonNull ExportInfo exportInfo)`:
  - Query MIME type dynamically: `contentValues.put(MediaStore.MediaColumns.MIME_TYPE, exportInfo.getFileFormat().getMimeType());`.
  - Verify `Uri targetUri = resolver.insert(...)`:
    ```java
    if (targetUri == null) {
        Log.w(TAG, "MediaStore insert returned null for " + fileToCopy.getName());
        return;
    }
    ```

### Component 3: `GPXFileWriter.java` Segment State Machine & Thread Safety
- **Segment State Machine**:
  - Introduce `boolean isSegmentOpen = false;` in `doExport()`.
  - Inside the while loop when `prevLineLap != lap`:
    - If `isSegmentOpen`: `bufferedWriter.write("  </trkseg>\n");`
    - Write lap segment header: `bufferedWriter.write("  <trkseg>\n");`
    - Set `isSegmentOpen = true;`
    - Update `prevLineLap = lap;`
  - In post-loop tail:
    - If `isSegmentOpen`: `bufferedWriter.write("  </trkseg>\n");`
- **Thread Safety & UTC Formatting**:
  - Replace static `SimpleDateFormat` with thread-safe `DateTimeFormatter`:
    ```java
    private static final DateTimeFormatter DB_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter XML_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
    ```
  - Implement `dbTime2XMLTime(String dbTime)` using `LocalDateTime.parse(dbTime, DB_TIME_FORMATTER).atOffset(ZoneOffset.UTC).format(XML_TIME_FORMATTER)`.

### Component 4: `TCXFileWriter.java` Garmin Schema Compliance & Thread Safety
- **Thread Safety & UTC Formatting**:
  - Replace static `SimpleDateFormat` with `DateTimeFormatter` matching `GPXFileWriter`.
- **Garmin TCX Schema Hierarchy**:
  - Remove `<Creator xsi:type="Device_t">` from root `<TrainingCenterDatabase>`.
  - Place `<Creator xsi:type="Device_t">` inside `<Activity>`, after `<Notes>` and before `<Extensions>`.
  - Populate valid schema elements for `<Creator xsi:type="Device_t">`:
    ```xml
    <Creator xsi:type="Device_t">
      <Name>[MANUFACTURER] [MODEL]</Name>
      <UnitId>0</UnitId>
      <ProductID>1</ProductID>
      <Version>
        <VersionMajor>[MAJOR]</VersionMajor>
        <VersionMinor>[MINOR]</VersionMinor>
        <BuildMajor>[BUILD]</BuildMajor>
        <BuildMinor>0</BuildMinor>
      </Version>
    </Creator>
    ```
  - Populate valid schema elements for `<Author xsi:type="Application_t">`:
    ```xml
    <Author xsi:type="Application_t">
      <Name>[APP_NAME]</Name>
      <Build>
        <Version>
          <VersionMajor>[MAJOR]</VersionMajor>
          <VersionMinor>[MINOR]</VersionMinor>
          <BuildMajor>[BUILD]</BuildMajor>
          <BuildMinor>0</BuildMinor>
        </Version>
        <Type>Release</Type>
      </Build>
      <LangID>en</LangID>
      <PartNumber>000-00000-00</PartNumber>
    </Author>
    ```

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Add `getMimeType()` to `FileFormat.java`
* Add standard MIME types to `FileFormat.java`.
* Author unit tests in `BaseExporterDownloadsTest.kt` verifying MIME types.

### Step 2: Update `BaseFileWriter.java` and `BaseExporter.java`
* In `BaseFileWriter.onFinished`: skip community uploads (`FileFormat.STRAVA` or `ExportType.COMMUNITY`).
* In `BaseExporter.copyFileToDownloads`: use `exportInfo.getFileFormat().getMimeType()`, guard `targetUri == null`.
* Unit test Downloads copying behavior and privacy skipping in `BaseExporterDownloadsTest.kt`.

### Step 3: Refactor `GPXFileWriter.java` Segment State Machine and Date Formatting
* Use `isSegmentOpen` boolean flag.
* Refactor date formatting to `java.time.format.DateTimeFormatter` with `ZoneOffset.UTC`.
* Author unit tests in `GPXFileWriterTest.kt` covering:
  - Cropped lap 0 (samples starting at lap 1) -> well-formed XML, no orphan closing tag.
  - Zero-sample workout -> well-formed XML, no orphan tags.
  - Multi-lap sequence -> matching open/close tags.

### Step 4: Refactor `TCXFileWriter.java` Schema Placement, Children & Date Formatting
* Move `<Creator>` inside `<Activity>` before `<Extensions>`.
* Emit valid schema children for `Device_t` and `Application_t`.
* Refactor date formatting to `java.time.format.DateTimeFormatter` with `ZoneOffset.UTC`.
* Author/update unit tests in `TCXFileWriterTest.kt` and `TCXFileWriterLapTest.kt` verifying XML well-formedness and schema compliance.

### Step 5: Execute Clean-Room Full Suite Regression
* Run `./gradlew testDebugUnitTest` verifying 100% test pass rate across all modules.

---

## 6. Verification & Sign-off Criteria
* All acceptance criteria in `ATT-4013_test_spec.md` satisfied.
* Clean Gate 3 audit passing `tools/review_agent.py audit <SUBTASK_KEY>`.
