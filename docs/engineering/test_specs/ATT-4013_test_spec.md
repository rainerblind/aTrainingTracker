# Stage 2: Requirement & Test Specification - ATT-4013: GPX/TCX exports can be malformed and Downloads copies use wrong MIME type

**Ticket**: [ATT-4013](https://rainerblind.atlassian.net/browse/ATT-4013)  
**Parent Epic**: None  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.10`  
**Requirement Mapping**: `REQ-EXP-022` (*GPX/TCX Export XML Well-Formedness, Garmin Schema Compliance, Downloads MIME Type & Privacy Protection*)  
**Test Spec ID**: `TST-EXP-019`  
**Branch**: `feature/ATT-4013`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Requirement Specification (REQ-EXP-022)

### 1.1 Problem Statement & Rationale
During code review and forensic analysis of the export subsystem, several structural, concurrency, and privacy flaws were identified:
1. `GPXFileWriter.java` manages track segment closing (`</trkseg>`) using an inequality on lap numbers (`prevLineLap != lap` with `lap != BANALService.INIT_LAP_NR`). If lap 0 is cropped or pruned, the first sample row has `lap > 0`, causing the exporter to emit an orphan closing `</trkseg>` tag before opening `<trkseg>`. Similarly, zero-sample workouts omit the while loop but unconditionally emit a closing `</trkseg>` in the tail.
2. `TCXFileWriter.java` violates Garmin's official `TrainingCenterDatabasev2.xsd` schema by placing `<Creator xsi:type="Device_t">` outside `<Activity>` at the root of `<TrainingCenterDatabase>`. In addition, required schema children for `Device_t` and `Application_t` were commented out.
3. Both `GPXFileWriter.java` and `TCXFileWriter.java` use static `SimpleDateFormat` instances across threads without synchronization or UTC timezone designation, risking data races and DST spring-forward timestamp distortions.
4. `BaseExporter.copyFileToDownloads` indiscriminately copies internal temporary files created for community uploads (e.g. `FileFormat.STRAVA` or `ExportType.COMMUNITY`) to the shared public `Downloads/` directory, hardcodes `application/gpx+xml` as the MIME type for all formats (including CSV, FIT, TCX, JSON), and lacks null-safety checks on MediaStore insertion.

### 1.2 Functional & Architectural Requirements
1. **GPX Track Segment State Machine**:
   - `GPXFileWriter.java` SHALL maintain an explicit boolean flag `isSegmentOpen = false;`.
   - When iterating sample points: if a new lap is detected (`prevLineLap != lap`), if `isSegmentOpen` is true, it SHALL emit `</trkseg>`. Then it SHALL emit `<trkseg>` and set `isSegmentOpen = true;`.
   - In the post-loop tail, it SHALL emit `</trkseg>` if and only if `isSegmentOpen` is true.
   - Zero-sample workouts SHALL NOT emit orphan closing tags and output well-formed XML.
2. **Garmin TCX v2 Schema Compliance**:
   - In `TCXFileWriter.java`, `<Creator xsi:type="Device_t">` SHALL be placed strictly inside `<Activity>`, immediately following `<Notes>` (if present) and preceding `<Extensions>`. `<Creator>` MUST NOT be emitted as a child of `<TrainingCenterDatabase>`.
   - `<Creator xsi:type="Device_t">` SHALL include valid schema children: `<Name>` (`aTrainingTracker`), `<UnitId>` (`0`), `<ProductID>` (`1`), and `<Version>` (`<VersionMajor>`, `<VersionMinor>`, `<BuildMajor>`, `<BuildMinor>`).
   - `<Author xsi:type="Application_t">` under `<TrainingCenterDatabase>` SHALL include valid schema children: `<Name>` (`aTrainingTracker`), `<Build>` with `<Version>`, `<LangID>` (`en`), and `<PartNumber>` (`000-00000-00`).
3. **Thread Safety & UTC Formatting**:
   - Static `SimpleDateFormat` instances SHALL be replaced with immutable `java.time.format.DateTimeFormatter` using `ZoneOffset.UTC`.
4. **Public Downloads Copying, MIME Types & Privacy**:
   - Explicit user file exports (`ExportType.FILE`: `CSV`, `TCX`, `GPX`, `FIT`, `GC`) SHALL continue to be copied to public `Downloads/` on Android 10+ (API 29+).
   - Internal temporary files created solely for community uploads (`FileFormat.STRAVA` or `ExportType.COMMUNITY`) SHALL NOT be copied to `Downloads/`.
   - `FileFormat.java` SHALL define `getMimeType()` providing standard MIME types (`application/gpx+xml`, `application/vnd.garmin.tcx+xml`, `application/vnd.ant.fit`, `text/csv`, `application/json`), and `copyFileToDownloads` SHALL use this MIME type.
   - `copyFileToDownloads` SHALL verify `targetUri != null` to prevent `NullPointerException` on insertion failure.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (GPX Cropped Lap Well-Formedness)**:
  * *Given* a workout whose initial lap 0 was cropped (samples start at lap 1),
  * *When* exported to GPX,
  * *Then* the output starts with `<trkseg>`, emits no preceding `</trkseg>`, and parses as well-formed XML.
* **Criterion 2 (GPX Zero-Sample Well-Formedness)**:
  * *Given* a workout with zero sample points,
  * *When* exported to GPX,
  * *Then* no orphan `</trkseg>` tag is emitted and the XML document parses cleanly.
* **Criterion 3 (TCX Garmin v2 Schema Compliance)**:
  * *Given* an exported TCX file,
  * *When* validated against Garmin's official `TrainingCenterDatabasev2.xsd`,
  * *Then* `<Creator>` is located inside `<Activity>` and both `<Creator>` and `<Author>` contain all mandatory children with zero schema validation errors.
* **Criterion 4 (Downloads Correct MIME Types)**:
  * *Given* explicit exports to CSV, FIT, TCX, GPX, or GC JSON,
  * *When* `copyFileToDownloads` executes,
  * *Then* MediaStore `MIME_TYPE` is set to the respective format MIME type.
* **Criterion 5 (Internal Upload Privacy Guard)**:
  * *Given* an internal upload export (`FileFormat.STRAVA` or `ExportType.COMMUNITY`),
  * *When* export completes,
  * *Then* no copy is written to the public `Downloads/` directory.
* **Criterion 6 (MediaStore Insert Null-Safety)**:
  * *Given* a MediaStore insert returning null `targetUri`,
  * *When* `copyFileToDownloads` executes,
  * *Then* it logs a warning and returns without throwing `NullPointerException`.

### 1.4 System Invariants
- Preservation of public `Downloads/` copy for all explicit `ExportType.FILE` exports (`CSV`, `TCX`, `GPX`, `FIT`, `GC`).
- Custom XML extensions (`att:ActivityExtension`) and metadata tags remain functional.
- 100% clean-room test suite pass rate across all unit tests.

---

## 2. Test Specification (TST-EXP-019)

### Test Case 1: GPX Segment State Machine & Well-Formedness (`TST-EXP-019.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/writer/GPXFileWriterTest.kt`
* **Preconditions**: SQLite database containing mock workout data with cropped lap 0 (starting at lap 1), zero samples, and multi-lap sequences.
* **Action**: Invoke `GPXFileWriter.writeWorkout()` and parse resulting GPX files via `DocumentBuilderFactory.newDocumentBuilder().parse()`.
* **Expected Result**: XML DOM parser successfully parses all outputs; no opening without closing or closing without opening tags.

### Test Case 2: Garmin TCX Schema Hierarchy & Validation (`TST-EXP-019.2`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/writer/TCXFileWriterTest.kt`
* **Preconditions**: Mock workout data exported via `TCXFileWriter.writeWorkout()`.
* **Action**: Parse TCX output, inspect DOM tree for `<Creator>` inside `<Activity>` (following `<Notes>`, preceding `<Extensions>`), and validate with Garmin `TrainingCenterDatabasev2.xsd`.
* **Expected Result**: Document passes DOM parsing and XML schema validation without errors.

### Test Case 3: DateTimeFormatter Thread Safety & UTC Zone (`TST-EXP-019.3`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/writer/DateTimeFormattingTest.kt`
* **Preconditions**: Multi-threaded execution formatting 1,000 dates concurrently.
* **Action**: Assert formatting deterministic output matching ISO-8601 UTC representation (`YYYY-MM-DD'T'HH:mm:ss'Z'`).
* **Expected Result**: Zero race conditions, consistent UTC output.

### Test Case 4: BaseExporter Downloads Copying & Privacy (`TST-EXP-019.4`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/exporter/BaseExporterDownloadsTest.kt`
* **Preconditions**: Mock `ExportInfo` instances for `FileFormat.GPX`, `FileFormat.TCX`, `FileFormat.FIT`, `FileFormat.CSV`, `FileFormat.GC`, and `FileFormat.STRAVA` with `ExportType.COMMUNITY` and `ExportType.FILE`.
* **Action**: Check `FileFormat.getMimeType()` outputs and simulate `copyFileToDownloads`.
* **Expected Result**: Proper MIME types passed to MediaStore; internal community uploads bypass `copyFileToDownloads`; null `targetUri` handled without NPE.

### Test Case 5: Clean-Room Full Suite Regression (`TST-EXP-019.5`)
* **Scope**: Automated Full Suite Regression
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% test pass rate with zero regressions across entire repository.
