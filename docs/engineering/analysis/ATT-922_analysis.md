# Stage 1 Analysis: TCX Export / Import Must Respect Workout Name (ATT-922)

* **Ticket**: [ATT-922](https://rainerblind.atlassian.net/browse/ATT-922) (*[Bug] TCX Export / Import must respect the name of a workout*)
* **Sub-task**: [ATT-923](https://rainerblind.atlassian.net/browse/ATT-923) (*[Analysis] TCX Export / Import must respect the name of a workout*)
* **Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*[Epic] Laps*)
* **Target Release Version**: `V4.9.36`
* **Branch**: `bugfix/ATT-922`

---

## 1. Problem Statement & User Bug Reproduction

### 1.1 Defect Reproduction Scenario
1. An athlete has a workout in aTrainingTracker with a custom or assigned name (e.g. `"Morning Interval Run"`, stored in `WorkoutSummaries.WORKOUT_NAME` / `exportName`). The workout may optionally have a description in `WorkoutSummaries.DESCRIPTION` (e.g. `"Zone 2 progressive tempo with hill strides"`).
2. The athlete exports the workout to a Garmin TCX file (`.tcx`).
3. The athlete deletes the workout from the device.
4. The athlete imports the exported TCX file back into aTrainingTracker via `LegacyImportEngine.importFromTcx`.
5. **Observed Failure**: The imported workout's name is completely lost and replaced by the raw timestamp base file name (e.g. `2026-09-12_10-00-00`) or overwritten by a generic cluster name. The athlete's custom workout title is destroyed.

---

## 2. Forensic Root Cause Analysis (RCA)

Distinguishing superficial symptoms from the underlying architectural root causes across export and import pipelines:

### 2.1 TCX Export Root Cause (`BaseFileWriter.java` & `TCXFileWriter.java`)
1. **Omission in Header Query (`BaseFileWriter.java`)**:
   In `BaseFileWriter.getHeaderData(ExportInfo exportInfo)`, the cursor query over `WorkoutSummaries.TABLE` reads `TIME_START`, `TIME_TOTAL_s`, `GC_DATA`, `GOAL`, `METHOD`, `DISTANCE_TOTAL_m`, and `DESCRIPTION`.
   * **Root Cause**: The column `WorkoutSummaries.WORKOUT_NAME` (`"exportName"`) is **never queried or stored** in `BaseFileWriter`. The field `workoutName` does not even exist in `BaseFileWriter`.
2. **Omission in TCX Activity Serialization (`TCXFileWriter.java`)**:
   In `TCXFileWriter.java`:
   ```java
   if (description != null && !description.trim().isEmpty()) {
       bufferedWriter.write("      <Notes>" + escapeXml(description.trim()) + "</Notes>\n");
   }
   ```
   * **Root Cause**: `TCXFileWriter` only writes `description` into `<Activity><Notes>`. The workout's name is completely ignored and omitted from the generated TCX XML. If a workout has a name but no description, no `<Notes>` tag is written at all.

### 2.2 TCX Import Root Cause (`LegacyImportEngine.kt`)
1. **Initial Record Creation Hardcoded to `baseFileName`**:
   In `LegacyImportEngine.kt` (lines 552–562):
   ```kotlin
   val summaryValues = ContentValues().apply {
       put(WorkoutSummaries.FILE_BASE_NAME, baseFileName)
       put(WorkoutSummaries.WORKOUT_NAME, baseFileName) // <-- Hardcoded to raw file base name
       ...
   }
   ```
2. **Context Isolation & Extraction Gap for Activity Notes**:
   When `<Notes>` is encountered outside a `<Lap>`, `LegacyImportEngine` assigns the raw string to `workoutNotes`.
   In `recalculateStats()`, `workoutNotes` is written strictly to `WorkoutSummaries.DESCRIPTION`:
   ```kotlin
   if (!workoutNotes.isNullOrBlank()) {
       values.put(WorkoutSummaries.DESCRIPTION, workoutNotes.trim())
   }
   ```
   * **Root Cause**: `values` in `recalculateStats()` **never sets or updates `WorkoutSummaries.WORKOUT_NAME`**. It remains the raw `baseFileName`.
3. **Clustering Collision (`WorkoutClusterEngine.kt`)**:
   In `WorkoutClusterEngine.assignClusterToWorkout(...)` (line 627):
   ```kotlin
   if (forceIdentity || currentName.isNullOrEmpty() || currentName == fileBaseName) {
       put(WorkoutSummaries.WORKOUT_NAME, formatClusterWorkoutName(...))
   }
   ```
   Because `currentName` was left as `fileBaseName` by `LegacyImportEngine`, `WorkoutClusterEngine` concludes that the workout is unnamed and overwrites it with the cluster name.

---

## 3. TCX v2 Schema Analysis & Serialization Strategy

### 3.1 Garmin TCX v2 Schema (`TrainingCenterDatabasev2.xsd`)
In the official Garmin TCX v2 schema, the sequence of child elements under `Activity_t` is:
```xml
<xsd:complexType name="Activity_t">
    <xsd:sequence>
        <xsd:element name="Id" type="xsd:dateTime"/>
        <xsd:element name="Lap" type="ActivityLap_t" maxOccurs="unbounded"/>
        <xsd:element name="Notes" type="xsd:string" minOccurs="0"/>
        <xsd:element name="Training" type="Training_t" minOccurs="0"/>
        <xsd:element name="Creator" type="AbstractSource_t" minOccurs="0"/>
        <xsd:element name="Extensions" type="Extensions_t" minOccurs="0"/>
    </xsd:sequence>
    <xsd:attribute name="Sport" type="Sport_t" use="required"/>
</xsd:complexType>
```
Key architectural constraints:
1. `Activity_t` does NOT have a native `<Name>` element in the standard Garmin TCX schema.
2. Standard 3rd-party consumers (Garmin Connect, Strava, GoldenCheetah, TrainingPeaks) display `<Activity><Notes>` as the activity title or notes.
3. `<Extensions>` is an authorized, optional element at the end of `Activity_t`.

### 3.2 Dual Serialization Strategy: Universal Notes + Lossless Structured Extensions

To ensure both universal third-party compatibility and 100% round-trip fidelity in aTrainingTracker, we apply the same dual strategy established for laps in `ATT-892`:

#### 1. Universal Human-Readable `<Notes>`:
Written under `<Activity>` immediately after the final `</Lap>`:
* If both `workoutName` (distinct from `baseFileName`) and `description` exist:
  `<Notes>[Workout Name] Workout Description</Notes>`
* If only `workoutName` exists (distinct from `baseFileName`):
  `<Notes>[Workout Name]</Notes>`
* If only `description` exists:
  `<Notes>Workout Description</Notes>`

#### 2. Lossless Structured `<Extensions>`:
Written under `<Activity><Extensions>`:
```xml
<Extensions>
  <att:ActivityExtension xmlns:att="http://atrainingtracker.com/xmlschemas/TrainingCenterDatabaseExtensions/v1">
    <att:Name>Workout Name</att:Name>
    <att:Description>Workout Description</att:Description>
  </att:ActivityExtension>
</Extensions>
```
* If only `workoutName` exists: `<att:Name>Workout Name</att:Name>`
* If only `description` exists: `<att:Description>Workout Description</att:Description>`

#### 3. XML Entity Escaping:
All values serialized into XML tags must be escaped via `escapeXml(String)` (`&`, `<`, `>`, `"`, `'`).

---

## 4. Deserialization & Parsing Strategy (`LegacyImportEngine.kt`)

When importing a TCX file, `LegacyImportEngine` must extract `workoutName` and `workoutDescription`:

1. **Structured Extension Tags**:
   When outside of `<Lap>`, encounter of `<att:Name>` or `<Name>` populates `workoutName`. Encounter of `<att:Description>` or `<Description>` populates `workoutDescription`.
2. **Third-Party `<Training><Plan><Name>`**:
   If a TCX file contains `<Training><Plan><Name>Plan Name</Name></Plan></Training>`, extract it as `workoutName` if not already set.
3. **Bracket Format in Activity `<Notes>`**:
   When `<Notes>` is parsed outside `<Lap>`:
   * Regex matching `^\[(.*?)\](?:\s*(.*))?$`:
     - Group 1 $\rightarrow$ `workoutName`
     - Group 2 $\rightarrow$ `workoutDescription`
   * Non-bracketed text:
     - Preserves existing `ATT-617` behavior: assigned as `workoutDescription`. If single line and $\le 40$ chars and no other name was specified, treated as `workoutName`.
4. **Database Persistence**:
   * When inserting initial summary or running `recalculateStats()`:
     - If `workoutName` is non-empty, persist to `WorkoutSummaries.WORKOUT_NAME`.
     - If `workoutDescription` is non-empty, persist to `WorkoutSummaries.DESCRIPTION`.
   * Because `WorkoutSummaries.WORKOUT_NAME` is populated with the explicit workout name (different from `baseFileName`), `WorkoutClusterEngine.assignClusterToWorkout(...)` will preserve it without overwriting.

---

## 5. Call Site Audit & Impact Analysis

### 5.1 Affected Classes & Callers
* `BaseFileWriter.java`: Add `protected String workoutName;` and query `WorkoutSummaries.WORKOUT_NAME` in `getHeaderData()`.
* `TCXFileWriter.java`: Write `<Notes>` with bracket formatting and `<Extensions><att:ActivityExtension>` before `</Activity>`.
* `LegacyImportEngine.kt`: Track `workoutName` and `workoutDescription` across parser states, parse bracket format and structured tags, and persist to `WorkoutSummaries.WORKOUT_NAME`.

### 5.2 Mapped Requirements & Invariant Protection
* **REQ-DAT-012**: TCX Lap Information (Name & Description) Bidirectional Export & Import.
  * Invariant: Lap serialization, schema ordering, and lap-level notes/extensions must remain completely untouched.
* **REQ-SET-007 / REQ-SET-008**: Workout Clustering.
  * Invariant: If a workout has a custom name, clustering must not overwrite it. If a workout has no custom name, clustering behaves normally.
* **Database Invariants**: `WorkoutSummaries.db` schema (`DB_VERSION`) requires no changes because `WORKOUT_NAME` (`"exportName"`) and `DESCRIPTION` already exist.

---

## 6. Risk Assessment & Recommendations

* **Risk Level**: **LOW**
* **Technical Justification**: Clean, localized changes in export serialization and import deserialization adhering strictly to the existing schema and patterns established in `ATT-892`.
* **Recommendation**: **RECOMMEND PASS** to advance to Stage 2 (Test Specification & Requirement Synchronization).
