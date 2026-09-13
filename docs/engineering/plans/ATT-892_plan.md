# Stage 3 Implementation Plan: Import / Export Lap info (Name and Description) (ATT-892)

* **Ticket**: [ATT-892](https://rainerblind.atlassian.net/browse/ATT-892) (*[Feature] Import / Export Lap info (Name and Description)*)
* **Sub-task**: [ATT-916](https://rainerblind.atlassian.net/browse/ATT-916) (*[Impl-Plan] Import / Export Lap info (Name and Description)*)
* **Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*[Epic] Laps*)
* **Requirement**: `REQ-DAT-012`
* **Test Specification**: `TST-DAT-006`
* **Target Version**: `V4.9.36`
* **Branch**: `feature/ATT-892`

---

## 1. Overview & Objectives

This implementation plan defines the architectural modifications, serialization strategy, parsing heuristics, and verification procedures required to fulfill `REQ-DAT-012` and `TST-DAT-006`:
1. **Bidirectional TCX Serialization**: Fully preserve lap `name` and `description` across Garmin TCX v2 export and import cycles.
2. **Schema Placement Compliance**: Guarantee strict sequence adherence under `TrainingCenterDatabasev2.xsd` (`ActivityLap_t`) by placing `<Notes>` and `<Extensions>` immediately *after* the closing `</Track>` tag and before `</Lap>`.
3. **Universal Third-Party Interoperability**: Serialize human-readable lap notes formatted as `[Name] Description` (or `[Name]` / `Description`) under standard `<Notes>`.
4. **Lossless Structured Extensions**: Serialize structured XML under `<Extensions><att:LapExtension xmlns:att="http://atrainingtracker.com/xmlschemas/TrainingCenterDatabaseExtensions/v1"><att:Name>...</att:Name><att:Description>...</att:Description></att:LapExtension></Extensions>` for 100% round-trip fidelity between aTrainingTracker installations.
5. **Activity-Level Notes**: Serialize overall workout description (`WorkoutSummaries.DESCRIPTION`) under `<Activity><Notes>...</Notes>` immediately following all `<Lap>` elements and before `<Creator>`.
6. **Robust XML Sanitization**: Strict XML escaping (`&amp;`, `&lt;`, `&gt;`, `&quot;`, `&apos;`) on all user-supplied text during export and unescaping during import.
7. **Query Optimization**: Pre-fetch all lap records for the exported workout in a single batch query (`LapsDatabaseManager.getLaps(workoutID)`) to eliminate N+1 SQLite queries during trackpoint iteration.
8. **Parser Isolation**: Ensure `LegacyImportEngine.kt` strictly isolates lap `<Notes>` from activity `<Notes>` using parser state tracking (`inLap`), and persists annotations via `LapsDatabaseManager.saveLap(...)`.

---

## 2. Component Breakdown & Detailed Modifications

### 2.1 TCX Export Serialization: `TCXFileWriter.java` [MODIFY]
* **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/TCXFileWriter.java`
* **Requirement**: `REQ-DAT-012` (Section 1)
* **Test**: `TST-DAT-006` (Section 1)
* **Modifications**:
  1. **XML Escaping Helper**:
     ```java
     protected static String escapeXml(String text) {
         if (text == null) return "";
         return text.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
     }
     ```
  2. **Batch Pre-Loading of Laps**:
     - Before the sample loop, pre-load all laps for `workoutID`:
       ```java
       List<LapData> lapsList = LapsDatabaseManager.getInstance(mContext).getLaps(workoutID);
       Map<Long, LapData> lapDataMap = new HashMap<>();
       for (LapData lapData : lapsList) {
           lapDataMap.put(lapData.getLapNr(), lapData);
       }
       ```
     - In the sample cursor loop, when `prevLineLap != lap`, look up `LapData` from `lapDataMap` instead of querying SQLite per lap.
  3. **Lap Closing & Annotation Serialization**:
     - Create helper `writeLapNotesAndExtensions(BufferedWriter writer, long lapNr, Map<Long, LapData> lapMap)`:
       - Retrieve `LapData` for `lapNr`.
       - If non-null and either `name` or `description` is non-empty:
         - Format human-readable note: `[Name] Description`, `[Name]`, or `Description`.
         - Write `        <Notes>` + `escapeXml(noteContent)` + `</Notes>\n`.
         - Write structured extension:
           ```xml
                   <Extensions>
                     <att:LapExtension xmlns:att="http://atrainingtracker.com/xmlschemas/TrainingCenterDatabaseExtensions/v1">
                       <att:Name>...</att:Name>
                       <att:Description>...</att:Description>
                     </att:LapExtension>
                   </Extensions>
           ```
     - Call `writeLapNotesAndExtensions` immediately after `</Track>` and before `</Lap>`:
       - In the sample loop when finishing previous lap (`prevLineLap != BANALService.INIT_LAP_NR - 1`).
       - In the tail block after the sample loop finishes.
  4. **Activity-Level Notes Serialization**:
     - In the tail block, after closing the final `</Lap>`, check `description`:
       ```java
       if (description != null && !description.trim().isEmpty()) {
           bufferedWriter.write("      <Notes>" + escapeXml(description.trim()) + "</Notes>\n");
       }
       ```
       immediately before `    </Activity>\n`.

---

### 2.2 TCX Import Deserialization: `LegacyImportEngine.kt` [MODIFY]
* **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`
* **Requirement**: `REQ-DAT-012` (Section 2)
* **Test**: `TST-DAT-006` (Section 2)
* **Modifications**:
  1. **Extend `ParsedLap` Data Model**:
     ```kotlin
     data class ParsedLap(
         val lapNr: Long,
         var startTime: String? = null,
         var totalTimeSeconds: Double = 0.0,
         var distanceMeters: Double = 0.0,
         var maxSpeed: Double? = null,
         var calories: Int? = null,
         var avgHeartRate: Int? = null,
         var maxHeartRate: Int? = null,
         var name: String? = null,
         var description: String? = null
     )
     ```
  2. **Parser Context State & Tag Handling**:
     - Maintain `var inLap: Boolean = false`.
     - In `XmlPullParser.START_TAG`:
       - `"Lap"`:
         - Set `inLap = true`.
         - Create `ParsedLap(lapNr = parsedLaps.size.toLong(), startTime = formattedStartTime)` and assign `currentLap = lap`.
       - `"Notes"`:
         - If `!inTrackpoint`:
           - Read `val text = parser.nextText()`.
           - If `inLap && currentLap != null`:
             - Parse lap note using bracket matching:
               - Match `Regex("""^\[(.*?)\](?:\s*(.*))?$""", RegexOption.DOT_MATCHES_ALL)`.
               - If matched: group 1 -> `currentLap.name`; group 2 (if present) -> `currentLap.description`.
               - Else if multiline: line 1 (if `<= 40` chars) -> `currentLap.name`; remaining lines -> `currentLap.description`.
               - Else if single line: if `<= 40` chars -> `currentLap.name`; else -> `currentLap.description`.
           - Else if `!inLap`:
             - `workoutNotes = text` (populates overall workout summary description).
       - `"Name"` or `"att:Name"`:
         - If `inLap && currentLap != null && !inTrackpoint`:
           - `currentLap.name = parser.nextText().trim()` (structured tags take precedence).
       - `"Description"` or `"att:Description"`:
         - If `inLap && currentLap != null && !inTrackpoint`:
           - `currentLap.description = parser.nextText().trim()`.
     - In `XmlPullParser.END_TAG`:
       - `"Lap"`:
         - `inLap = false`.
         - `currentLap = null`.
  3. **Database Insertion**:
     - In `recalculateStats(...)`:
       ```kotlin
       lapsDb.saveLap(
           workoutId,
           lap.lapNr,
           lap.startTime ?: firstTime,
           lapDuration,
           lapDistance,
           lapAvgSpeed,
           lap.name,
           lap.description
       )
       ```
       Invokes existing 8-parameter `saveLap(...)` overload in `LapsDatabaseManager.java`.

---

### 2.3 Automated Test Suite [NEW]
* **`TCXFileWriterLapTest.kt`**:
  - Validates `TCXFileWriter.doExport(...)` output against mock database fixtures.
  - Asserts XML schema ordering (`<Notes>` and `<Extensions>` strictly between `</Track>` and `</Lap>`).
  - Asserts human-readable bracket format and structured `att:LapExtension`.
  - Asserts activity `<Notes>` position and text.
  - Asserts special character escaping (`&amp;`, `&lt;`, `&gt;`, `&quot;`, `&apos;`).
* **`LegacyImportEngineLapTest.kt`**:
  - Validates `LegacyImportEngine.importFromTcx(...)` against sample TCX documents.
  - Tests bracket notation `[Warm-up] 10m` -> `name="Warm-up"`, `description="10m"`.
  - Tests structured extension tags `<att:Name>` / `<att:Description>`.
  - Tests multiline and single-line heuristics.
  - Tests XML unescaping.
  - Tests legacy TCX files with no lap notes (null fields, zero crash).
  - Tests overall activity notes isolation.
* **End-to-End Round-Trip Test**:
  - Creates workout with multi-lap annotations and summary description, exports to TCX, imports into clean database, and asserts 100% equivalence in `Laps.db` and `WorkoutSummaries.db`.

---

## 3. Traceability Matrix & Verification Plan

| Requirement ID | Component / File | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-DAT-012` (1) | `TCXFileWriter.java` | `TST-DAT-006` (1) | Unit test: Schema sequence, `<Notes>`, `<Extensions>`, XML escaping, query batching |
| `REQ-DAT-012` (2) | `LegacyImportEngine.kt` | `TST-DAT-006` (2) | Unit test: Bracket parsing, structured tags, heuristic parsing, database insertion |
| `REQ-DAT-012` (Acceptance) | `TCXFileWriter.java`, `LegacyImportEngine.kt` | `TST-DAT-006` (3) | Integration test: Full export/import round-trip equivalence in SQLite |
| System Invariants | All Modules | `TST-DAT-006` (4) | Clean-room `./gradlew testDebugUnitTest` suite (0 regressions) |

---

## 4. Invariant Protection Checklist

- [x] **Schema Sequence Compliance**: `<Notes>` and `<Extensions>` appear strictly *after* `</Track>` and before `</Lap>` per `TrainingCenterDatabasev2.xsd` (`ActivityLap_t`).
- [x] **Metric Immutability**: Split duration, distance, speed, and trackpoint coordinates are strictly unaffected.
- [x] **Third-Party Reader Resilience**: Standard `<Notes>` remains legible on Garmin Connect, Strava, and GoldenCheetah.
- [x] **Database Batching**: Pre-fetching laps via `LapsDatabaseManager.getLaps(workoutID)` prevents N+1 SQLite queries during sample iteration.
- [x] **Legacy Backward Compatibility**: Files lacking lap annotations import without error, setting `name = null` and `description = null`.
