# Stage 1 Analysis: ATT-4013 - GPX/TCX exports can be malformed and Downloads copies use wrong MIME type

**Ticket**: [ATT-4013](https://rainerblind.atlassian.net/browse/ATT-4013)  
**Sub-task**: [ATT-4241](https://rainerblind.atlassian.net/browse/ATT-4241) (`[Analysis]`)  
**Parent Epic**: None  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.10`  
**Branch**: `feature/ATT-4013`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Problem Statement & Motivation

During the October 2026 architectural code audit (commit `0234bfc0`), several defects and privacy/data-integrity gaps were identified in the export subsystems:
1. **Malformed GPX Output on Cropped or Empty Workouts**:
   - In `GPXFileWriter.java`, segment closing (`</trkseg>`) was conditionally triggered by `lap != BANALService.INIT_LAP_NR`. If a workout's initial lap 0 was trimmed/cropped via lap management or workout editing, the first processed row has `lap > 0`, causing the exporter to output a closing `</trkseg>` tag before any opening `<trkseg>`.
   - On zero-sample workouts (e.g. empty or aborted recordings), the loop never executes, but the tail unconditionally emits `</trkseg>`, generating malformed XML that third-party platforms reject.
2. **Thread Safety & Timezone Offsets in Date Formatting**:
   - `GPXFileWriter.java` and `TCXFileWriter.java` employ static `SimpleDateFormat` instances (`msdfFromDb`, `msdfToXML`) across threads without synchronization.
   - These formatters omit explicit UTC time zones, parsing database timestamps using JVM default device time. During Daylight Saving Time (DST) spring-forward transitions, local parsing shifts timestamps non-monotonically.
3. **MIME Type Hardcoding & Indiscriminate Public Downloads Leakage**:
   - `BaseExporter.copyFileToDownloads` copies all exported files to shared public `Downloads/` with a hardcoded MIME type `application/gpx+xml`, incorrectly tagging FIT, TCX, CSV, and GC JSON files as GPX.
   - Internal temporary files created strictly for online community uploads (such as `FileFormat.STRAVA` TCX files) are indiscriminately duplicated into public `Downloads/`, leaking athlete track data.
   - `copyFileToDownloads` lacks null-checks on `resolver.insert()`, resulting in `NullPointerException` on constrained storage.
4. **TCX Garmin v2 Schema Non-Compliance**:
   - In `TCXFileWriter.java`, `<Creator xsi:type="Device_t">` is emitted outside `<Activity>` directly beneath `<TrainingCenterDatabase>`. In Garmin's official `TrainingCenterDatabasev2.xsd`, `<Creator>` is valid only as a child of `<Activity>` (preceding `<Extensions>`).
   - Required schema children for `Device_t` and `Application_t` (`UnitId`, `ProductID`, `Version`, `Build`, `LangID`, `PartNumber`) were commented out, causing strict schema validators to reject the file.

---

## 2. Root Cause Analysis (Forensic Investigation)

### Component 1: `GPXFileWriter.java` Track Segment State Machine
Lines 104-116 and 180-183:
```java
long prevLineLap = BANALService.INIT_LAP_NR - 1;
while (cursor != null && cursor.moveToNext()) {
    int lap = cursor.getInt(cursor.getColumnIndexOrThrow(SensorType.LAP_NR.name()));
    if (prevLineLap != lap) { // new lap
        if (lap != BANALService.INIT_LAP_NR) { // finish previous lap
            bufferedWriter.write("  </trkseg>\n");
        }
        ...
        bufferedWriter.write("  <trkseg>\n");
    }
    prevLineLap = lap;
    ...
}
bufferedWriter.write("  </trkseg>\n");
```
* **Failure Mechanism**: The logic assumes the sequence of laps always begins at `INIT_LAP_NR` (0). When lap 0 is cropped, `prevLineLap` (-1) != `lap` (e.g. 1), and since `lap != 0`, it writes `</trkseg>` immediately. At the loop conclusion, `</trkseg>` is written regardless of whether a segment was currently open.
* **Root Cause**: Segment state was tracked implicitly via lap number inequality rather than an explicit boolean flag (`isSegmentOpen`).

### Component 2: Static `SimpleDateFormat` Concurrency & Timezones
* `GPXFileWriter.java:50-51` & `TCXFileWriter.java:56-57`:
  `protected static final SimpleDateFormat msdfFromDb = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);`
* **Failure Mechanism**: `SimpleDateFormat` stores intermediate state in internal calendars; concurrent calls from background WorkManager exports corrupt date strings. Furthermore, without `setTimeZone(TimeZone.getTimeZone("UTC"))`, times are interpreted in device local timezone.
* **Remedy**: Use immutable, thread-safe `java.time.format.DateTimeFormatter` with `ZoneOffset.UTC`.

### Component 3: `BaseExporter.java` and `BaseFileWriter.java` Downloads Copying
* `BaseFileWriter.java:70-72`:
  ```java
  @Override
  protected void onFinished(@NonNull ExportInfo exportInfo) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          copyFileToDownloads(exportInfo);
      }
  }
  ```
* `BaseExporter.java:189-195`:
  ```java
  ContentValues contentValues = new ContentValues();
  contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileToCopy.getName());
  contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "application/gpx+xml");
  contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
  Uri targetUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues);
  ```
* **Failure Mechanism**:
  1. `exportInfo.getFileFormat() == FileFormat.STRAVA` or `exportInfo.getExportType() == ExportType.COMMUNITY` is copied to public `Downloads/`.
  2. MIME type is hardcoded to `application/gpx+xml`.
  3. If `targetUri == null`, `resolver.openOutputStream(targetUri)` throws `NullPointerException`.

### Component 4: `TCXFileWriter.java` Garmin TCX Schema Hierarchy
* Lines 324-355:
  ```xml
      </Activity>
    </Activities>
    <Creator xsi:type="Device_t">
      <Name>...</Name>
    </Creator>
    <Author xsi:type="Application_t">
      <Name>...</Name>
    </Author>
  </TrainingCenterDatabase>
  ```
* **Schema Contract (`TrainingCenterDatabasev2.xsd`)**:
  - `Activity_t`: sequence is `<Id>`, `<Lap>` (1..*), `<Notes>` (0..1), `<Training>` (0..1), `<Creator>` (0..1), `<Extensions>` (0..1).
  - `TrainingCenterDatabase_t`: sequence is `<Folders>`, `<Activities>`, `<Workouts>`, `<Courses>`, `<Author>`, `<Extensions>`.
* **Failure Mechanism**: `<Creator>` is placed outside `<Activity>` under `<TrainingCenterDatabase>`. When present, `<Creator xsi:type="Device_t">` requires `<Name>`, `<UnitId>`, `<ProductID>`, and `<Version>`. `<Author xsi:type="Application_t">` requires `<Name>`, `<Build>`, `<LangID>`, and `<PartNumber>`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Refactor `GPXFileWriter` to use an explicit boolean `isSegmentOpen` flag, ensuring no orphan `</trkseg>` tags are generated for cropped or empty workouts.
  2. Modernize date formatting in `GPXFileWriter` and `TCXFileWriter` to thread-safe `java.time` with explicit UTC offset.
  3. Update `BaseExporter.copyFileToDownloads` to:
     - Assign correct per-format MIME types via `FileFormat.getMimeType()`.
     - Skip copying internal community files (`FileFormat.STRAVA` / `ExportType.COMMUNITY`).
     - Check `targetUri == null` and handle MediaStore insertion failures gracefully.
  4. Correct TCX schema hierarchy in `TCXFileWriter`:
     - Relocate `<Creator>` inside `<Activity>` before `<Extensions>`.
     - Supply valid schema sub-elements for `Device_t` and `Application_t`.
  5. Author unit test verification covering XML well-formedness, XSD schema compliance, MIME types, and privacy invariants.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Do NOT remove or make public Downloads copies opt-in for explicit `FILE` exports (`ExportType.FILE`) — users rely on exported files appearing in `Downloads/`.
  - Do NOT modify cloud uploader logic (`StravaUploader`, `DropboxClient`, `GoogleDriveClient`).
  - Do NOT alter database schemas.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-EXP-022` (*GPX/TCX Export XML Well-Formedness, Garmin Schema Compliance, Downloads MIME Type & Privacy Protection*), amending `REQ-DAT-013` (TCX Workout Name/Description) and refining `BaseExporter`.
* **Historical Origin & Commit Trace**: Independent whole-app code review (2026-10-18, commit `0234bfc0`, ticket `ATT-4013`).
* **Root Reason for Existing Formulation**: Early Android export implementations used rudimentary string concatenation for XML tags and a single MIME type for simplicity. TCX creator and author blocks were commented out during rapid prototyping.
* **Preservation of Core Invariants**: Existing file export paths, workout naming extensions (`att:ActivityExtension`), and user access in `Downloads/` for explicit file exports remain 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution

1. **`FileFormat.java`**:
   Add `public String getMimeType()`:
   - `GPX` -> `"application/gpx+xml"`
   - `TCX`, `STRAVA` -> `"application/vnd.garmin.tcx+xml"`
   - `FIT` -> `"application/vnd.ant.fit"`
   - `CSV` -> `"text/csv"`
   - `GC` -> `"application/json"`
2. **`BaseExporter.java` & `BaseFileWriter.java`**:
   - In `BaseFileWriter.onFinished`: Only invoke `copyFileToDownloads` if `exportInfo.getFileFormat() != FileFormat.STRAVA && exportInfo.getExportType() != ExportType.COMMUNITY`.
   - In `copyFileToDownloads`: Use `exportInfo.getFileFormat().getMimeType()`. Guard `targetUri == null` and handle streams safely.
3. **`GPXFileWriter.java`**:
   - Use `boolean isSegmentOpen = false;`.
   - When new lap is detected: if `isSegmentOpen`, write `</trkseg>`; write `<trkseg>`; `isSegmentOpen = true;`.
   - In tail: if `isSegmentOpen`, write `</trkseg>`.
   - Replace static `SimpleDateFormat` with thread-safe `java.time` formatter in UTC.
4. **`TCXFileWriter.java`**:
   - Place `<Creator xsi:type="Device_t">` inside `<Activity>` before `<Extensions>`.
   - Populate valid `Device_t` elements (`UnitId`, `ProductID`, `Version`) and valid `Author` `Application_t` elements (`Build`, `LangID`, `PartNumber`).
   - Replace static `SimpleDateFormat` with thread-safe `java.time` formatter in UTC.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero XML/TCX regressions in existing test suites (`TCXFileWriterWorkoutNameTest`, `TCXFileWriterLapTest`, `FitFileWriterTest`, `GCFileWriterTest`).
  2. Public `Downloads/` copy preserved for all user-initiated `FILE` exports.
  3. Clean-room 100% pass rate (`./gradlew testDebugUnitTest`).
* **Risk Rating**: **LOW**
  - Isolated file writer and copy-to-downloads logic. No database migrations or lifecycle dependencies.
