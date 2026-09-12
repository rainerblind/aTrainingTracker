# Stage 3 Implementation Plan: TCX Export / Import Workout Name & Description

**Ticket**: [ATT-922](https://rainerblind.atlassian.net/browse/ATT-922) / Sub-task: [ATT-926](https://rainerblind.atlassian.net/browse/ATT-926)  
**Author**: Agent 1 (Pair Programming Assistant)  
**Date**: 2026-09-12  
**Target Version**: V4.9.36  
**Git Branch**: `bugfix/ATT-922`  
**Requirement**: [REQ-DAT-013](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md)  
**Test Specification**: [TST-DAT-007](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md)  

---

## 1. Executive Summary & Root Cause Analysis

### 1.1 Problem Statement
When exporting a workout to Garmin TCX format, deleting it, and re-importing the TCX file, the user-assigned custom workout name (`WorkoutSummaries.WORKOUT_NAME`) was completely lost. The workout either reverted to `fileBaseName` (e.g. `2026-09-12 10:00:00`) or was overwritten by a route cluster name (e.g. `Isar River Loop #3`).

### 1.2 Root Cause Analysis (RCA)
1. **Export Pipeline (`BaseFileWriter.java` & `TCXFileWriter.java`)**:
   - `BaseFileWriter.getHeaderData()` never queried `WorkoutSummaries.WORKOUT_NAME` (`"exportName"`). The field `workoutName` did not exist on `BaseFileWriter`.
   - `TCXFileWriter.java` wrote only `description` into `<Activity><Notes>`. The custom workout name was completely omitted from the generated TCX XML.
2. **Import Pipeline (`LegacyImportEngine.kt`)**:
   - On initial insert (line 554), `WORKOUT_NAME` was hardcoded to `baseFileName`.
   - `workoutNotes` was populated from activity `<Notes>`, but only persisted to `WorkoutSummaries.DESCRIPTION` during `recalculateStats()`. Activity-level `<Name>` or structured extension tags were ignored outside `<Lap>`.
3. **Clustering Collision (`WorkoutClusterEngine.kt`)**:
   - In `WorkoutClusterEngine.assignClusterToWorkout()`, line 627 checks:
     `if (forceIdentity || currentName.isNullOrEmpty() || currentName == fileBaseName)`.
   - Because `WORKOUT_NAME` remained `baseFileName`, the clustering engine assumed the imported workout was unnamed and silently overwrote it with the matched route cluster name.

---

## 2. Invariants & Safety Guardrails

In accordance with `REQ-PRO-013` and `REQ-DAT-013`, the following system invariants MUST NOT be altered:
1. **Lap-Level Data Integrity (`REQ-DAT-012`)**:
   - Lap notes, lap names, lap descriptions, and structured `<att:LapExtension>` parsing must remain completely isolated from activity-level notes and names.
2. **Quantitative Sensor Extrema & Streams (`REQ-DAT-001`)**:
   - Raw trackpoints, sensor streams (HR, Cadence, Power, Speed, Altitude), and extrema calculations must remain untouched.
3. **Cluster Engine Learning (`REQ-ROU-001`)**:
   - Automatic route clustering geometry matching (start, end, apex, distance) and cluster hit counting must remain unaltered.
4. **Default Unnamed Workouts**:
   - If a workout has no custom name (i.e. name is null, empty, or equals `fileBaseName`), default clustering behavior must continue to apply cluster names as intended.

---

## 3. Technical Implementation Details

### 3.1 Data Retrieval in `BaseFileWriter.java`
- **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/BaseFileWriter.java`
- **Changes**:
  1. Add protected field:
     ```java
     String startTime, totalTime, data, goal, method, totalDistance, description, workoutName;
     ```
  2. In `getHeaderData(@NonNull ExportInfo exportInfo)`:
     ```java
     workoutName = myGet(cursor, WorkoutSummaries.WORKOUT_NAME, "");
     ```

### 3.2 TCX Export Serialization in `TCXFileWriter.java`
- **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/TCXFileWriter.java`
- **Changes**:
  1. Immediately following all `<Lap>` elements (after line 290):
     ```java
     String name = (workoutName != null && !workoutName.trim().isEmpty() && !workoutName.equals(exportInfo.getFileBaseName()))
             ? workoutName.trim() : null;
     String desc = (description != null && !description.trim().isEmpty())
             ? description.trim() : null;

     if (name != null || desc != null) {
         String noteContent;
         if (name != null && desc != null) {
             noteContent = "[" + name + "] " + desc;
         } else if (name != null) {
             noteContent = "[" + name + "]";
         } else {
             noteContent = desc;
         }
         bufferedWriter.write("      <Notes>" + escapeXml(noteContent) + "</Notes>\n");

         bufferedWriter.write("      <Extensions>\n");
         bufferedWriter.write("        <att:ActivityExtension xmlns:att=\"http://atrainingtracker.com/xmlschemas/TrainingCenterDatabaseExtensions/v1\">\n");
         if (name != null) {
             bufferedWriter.write("          <att:Name>" + escapeXml(name) + "</att:Name>\n");
         }
         if (desc != null) {
             bufferedWriter.write("          <att:Description>" + escapeXml(desc) + "</att:Description>\n");
         }
         bufferedWriter.write("        </att:ActivityExtension>\n");
         bufferedWriter.write("      </Extensions>\n");
     }
     ```
  2. **Schema Compliance**:
     Per `TrainingCenterDatabasev2.xsd` (`Activity_t`):
     - Sequence: `Id` -> `Lap`+ -> `Notes`? -> `Training`? -> `Creator`? -> `Extensions`?
     - Placing `<Notes>` right after `</Lap>` and `<Extensions>` at the end of `<Activity>` strictly satisfies the schema.

### 3.3 TCX Import Parsing & Persistence in `LegacyImportEngine.kt`
- **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`
- **Changes**:
  1. Declare parser state:
     ```kotlin
     var workoutName: String? = null
     var workoutNotes: String? = null
     ```
  2. In `XmlPullParser.START_TAG`:
     - When `name == "Notes"` and `!inLap && !inTrackpoint`:
       ```kotlin
       val text = parser.nextText()
       if (!text.isNullOrBlank()) {
           val trimmed = text.trim()
           val bracketMatch = Regex("""^\[(.*?)\](?:\s*(.*))?$""", RegexOption.DOT_MATCHES_ALL).find(trimmed)
           if (bracketMatch != null) {
               val extractedName = bracketMatch.groupValues[1].trim()
               val extractedDesc = bracketMatch.groupValues.getOrNull(2)?.trim()
               if (workoutName.isNullOrBlank() && extractedName.isNotEmpty()) {
                   workoutName = extractedName
               }
               if (workoutNotes.isNullOrBlank() && !extractedDesc.isNullOrEmpty()) {
                   workoutNotes = extractedDesc
               }
           } else {
               val lines = trimmed.lines()
               if (lines.size > 1) {
                   val firstLine = lines.first().trim()
                   if (workoutName.isNullOrBlank() && firstLine.length <= 60) {
                       workoutName = firstLine
                       if (workoutNotes.isNullOrBlank()) {
                           val remaining = lines.drop(1).joinToString("\n").trim()
                           if (remaining.isNotEmpty()) {
                               workoutNotes = remaining
                           }
                       }
                   } else if (workoutNotes.isNullOrBlank()) {
                       workoutNotes = trimmed
                   }
               } else {
                   if (workoutNotes.isNullOrBlank()) {
                       workoutNotes = trimmed
                   }
               }
           }
       }
       ```
     - When `name == "Name"` and `!inLap && !inTrackpoint`:
       ```kotlin
       val text = parser.nextText()
       if (!text.isNullOrBlank()) {
           workoutName = text.trim()
       }
       ```
       (Captures `<att:Name>` under `<att:ActivityExtension>` and `<Training><Plan><Name>`).
     - When `name == "Description"` and `!inLap && !inTrackpoint`:
       ```kotlin
       val text = parser.nextText()
       if (!text.isNullOrBlank()) {
           workoutNotes = text.trim()
       }
       ```
       (Captures `<att:Description>` under `<att:ActivityExtension>`).
  3. On initial record creation (line 554):
     ```kotlin
     put(WorkoutSummaries.WORKOUT_NAME, if (!workoutName.isNullOrBlank()) workoutName!!.trim() else baseFileName)
     ```
  4. In `recalculateStats`:
     - Add `workoutName: String? = null` parameter.
     - Persist `workoutName` into `WorkoutSummaries`:
       ```kotlin
       if (!workoutName.isNullOrBlank()) {
           values.put(WorkoutSummaries.WORKOUT_NAME, workoutName.trim())
       }
       ```

### 3.4 Cluster Overwrite Immunity Verification
- In `WorkoutClusterEngine.assignClusterToWorkout()`:
  Line 627 checks: `if (forceIdentity || currentName.isNullOrEmpty() || currentName == fileBaseName)`.
  Because `WORKOUT_NAME` has been restored to the custom name (`currentName != fileBaseName`), `assignClusterToWorkout(context, workoutId, matchingCluster.id, false)` evaluates to false. The custom workout name is protected from being overwritten.

---

## 4. Verification & Testing Plan (`TST-DAT-007`)

### 4.1 Unit Tests for TCX Export (`TCXFileWriterWorkoutNameTest.kt`)
- `testBaseFileWriterQueriesWorkoutName`: Verify `BaseFileWriter.getHeaderData()` populates `workoutName` from `WorkoutSummaries.WORKOUT_NAME`.
- `testTcxExportSerializesWorkoutNameAndDescriptionInNotes`: Verify `<Notes>[Custom Name] Custom Description</Notes>` is serialized under `<Activity>`.
- `testTcxExportSerializesStructuredActivityExtension`: Verify `<Extensions><att:ActivityExtension><att:Name>...</att:Name><att:Description>...</att:Description></att:ActivityExtension></Extensions>` is generated.
- `testTcxExportNameOnlyAndDescriptionOnly`: Verify correct formatting when only name or only description is present.
- `testTcxExportXmlCharacterEscaping`: Verify special XML characters (`&`, `<`, `>`, `"`, `'`) are properly escaped.
- `testTcxExportOmitsDefaultFileBaseName`: Verify that if `workoutName == fileBaseName`, it is not treated as a custom name.

### 4.2 Unit Tests for TCX Import (`LegacyImportEngineWorkoutNameTest.kt`)
- `testImportExtractsStructuredActivityExtension`: Verify `<att:ActivityExtension>` sets both `workoutName` and `description`.
- `testImportExtractsBracketNotationFromNotes`: Verify `[Morning Ride] Easy spin` extracts name and description.
- `testImportExtractsGarminTrainingPlanName`: Verify `<Training><Plan><Name>Speed Session</Name></Training>` sets `workoutName`.
- `testImportUnescapesXmlEntities`: Verify XML entities are unescaped back to raw characters.
- `testImportFallbackToFileName`: Verify that files with no workout name default to `fileBaseName`.

### 4.3 Round-Trip & Cluster Immunity Integration Test
- Export a workout with custom name `"Tempo Run & Intervals"`.
- Import the exported TCX via `LegacyImportEngine.importFromTcx`.
- Verify `WorkoutSummaries.WORKOUT_NAME` equals `"Tempo Run & Intervals"`.
- Invoke `WorkoutClusterEngine.assignClusterToWorkout(..., forceIdentity = false)`.
- Verify `WorkoutSummaries.WORKOUT_NAME` remains `"Tempo Run & Intervals"` and is not replaced by the cluster name.

### 4.4 Clean-Room Regression Verification
- Run `./gradlew testDebugUnitTest` to confirm zero regressions across all modules.

---

## 5. File Modification Manifest

| Component | File Path | Type | Scope |
|:---|:---|:---|:---|
| Exporter | `app/src/main/java/.../exporter/writer/BaseFileWriter.java` | Modify | Add `workoutName` member and query in `getHeaderData()` |
| Exporter | `app/src/main/java/.../exporter/writer/TCXFileWriter.java` | Modify | Write activity `<Notes>` and `<Extensions><att:ActivityExtension>` |
| Importer | `app/src/main/java/.../migration/LegacyImportEngine.kt` | Modify | Parse activity notes/extensions/name, persist `WORKOUT_NAME` |
| Test | `app/src/test/java/.../exporter/writer/TCXFileWriterWorkoutNameTest.kt` | New | Comprehensive export serialization unit tests |
| Test | `app/src/test/java/.../migration/LegacyImportEngineWorkoutNameTest.kt` | New | Comprehensive import deserialization & cluster immunity unit tests |
