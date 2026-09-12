# Stage 1 Analysis: Import / Export Lap Info (Name and Description) (ATT-892)

* **Ticket**: [ATT-892](https://rainerblind.atlassian.net/browse/ATT-892) (*[Feature] Import / Export Lap info (Name and Description)*)
* **Sub-task**: [ATT-910](https://rainerblind.atlassian.net/browse/ATT-910) (*[Analysis] Import / Export Lap info (Name and Description)*)
* **Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*[Epic] Laps*)
* **Target Release Version**: `V4.9.36`
* **Branch**: `feature/ATT-892`

---

## 1. Feature Description & Problem Domain

### 1.1 Background & Motivation
In **ATT-510** (`REQ-UI-141`) and **ATT-511** (`REQ-UI-142`), we established full lifecycle support for workout laps within the app:
1. `Laps.db` (`DB_VERSION = 2`) stores `name TEXT` and `description TEXT` alongside quantitative split metrics (`time_total_s`, `distance_total_m`, `speed_average_mps`, `time_start`).
2. Athletes can annotate individual laps using preset quick-tag chips (*Warm-up*, *Interval*, *Recovery*, *Hill Climb*, *Tempo*, *Sprint*, *Cool-down*) or custom text notes and multi-line descriptions via `LapEditBottomSheet.kt`.
3. Lap rows are displayed with names, descriptions, and performance badges (Rabbit 🐇 / Hedgehog 🦔) in `WorkoutSummary.kt` (`WorkoutLaps.kt`).

However, the file export and import subsystems currently lack support for lap names and descriptions:
* **TCX Export (`TCXFileWriter.java`)**: When generating Garmin TCX v2 XML files, the writer queries `Laps.db` for each lap but only reads and emits `<TotalTimeSeconds>` and `<DistanceMeters>`. The custom `name` and `description` are completely ignored, and no `<Notes>` or `<Extensions>` elements are written for laps or for the overall workout. Consequently, when an athlete exports a workout to TCX (or uploads it to Strava/Dropbox), all lap annotations are lost.
* **TCX Import (`LegacyImportEngine.kt`)**: When importing a TCX file (from single-file import or Dropbox legacy recovery), `LegacyImportEngine` parses `<Lap>` tags into `ParsedLap`. Currently, `ParsedLap` has no fields for `name` or `description`. Furthermore, when the parser encounters `<Notes>` inside a lap, it erroneously overwrites the global `workoutNotes` variable instead of associating the note with the active lap split. When writing laps into `LapsDatabaseManager.java`, it calls the 6-parameter `saveLap(...)` overload which leaves `name` and `description` as `null`.

**ATT-892** addresses this gap by introducing bidirectional serialization of lap names and descriptions for TCX export and import.

---

## 2. Technical Architecture & File Impact

### 2.1 TCX v2 Standard Schema Analysis (`TrainingCenterDatabasev2.xsd`)
According to the official Garmin Training Center Database XML Schema v2 (`TrainingCenterDatabasev2.xsd`), the structure of `ActivityLap_t` is:
```xml
<xs:complexType name="ActivityLap_t">
    <xs:sequence>
        <xs:element name="TotalTimeSeconds" type="xs:double"/>
        <xs:element name="DistanceMeters" type="xs:double"/>
        <xs:element name="MaximumSpeed" type="xs:double" minOccurs="0"/>
        <xs:element name="Calories" type="xs:unsignedShort"/>
        <xs:element name="AverageHeartRateBpm" type="HeartRateInBeatsPerMinute_t" minOccurs="0"/>
        <xs:element name="MaximumHeartRateBpm" type="HeartRateInBeatsPerMinute_t" minOccurs="0"/>
        <xs:element name="Intensity" type="Intensity_t"/>
        <xs:element name="Cadence" type="CadenceValue_t" minOccurs="0"/>
        <xs:element name="TriggerMethod" type="TriggerMethod_t"/>
        <xs:element name="Track" type="Track_t" minOccurs="0" maxOccurs="unbounded"/>
        <xs:element name="Notes" type="xs:string" minOccurs="0"/>
        <xs:element name="Extensions" type="Extensions_t" minOccurs="0"/>
    </xs:sequence>
    <xs:attribute name="StartTime" type="xs:dateTime" use="required"/>
</xs:complexType>
```

Key schema rules:
1. **Element Ordering**: `<Notes>` and `<Extensions>` MUST appear immediately *after* `</Track>` and before `</Lap>`. Placing them before `<Track>` would violate the schema sequence.
2. **Standard Element**: `<Notes>` is a standard string element supported by standard TCX readers (Garmin Connect, Strava, GoldenCheetah, TrainingPeaks).
3. **Activity-Level Notes**: Under `<Activity_t>`, an optional `<Notes>` element is also supported directly after all `<Lap>` elements and before `<Creator>`.

### 2.2 Dual Serialization Strategy: Universal Notes + Structured Extensions

To achieve both **100% standard interoperability** with third-party platforms and **100% lossless fidelity** on round-trip export/import within aTrainingTracker, we establish a dual serialization design:

#### 1. Universal Human-Readable `<Notes>`:
Written into standard `<Notes>` under `<Lap>`:
* If both `name` and `description` exist: `[Name] Description`
* If only `name` exists: `[Name]`
* If only `description` exists: `Description`

*Example*:
```xml
<Notes>[Warm-up] 10 mins easy spin in HR Zone 1</Notes>
```
Third-party platforms that display lap notes will cleanly render `[Warm-up] 10 mins easy spin in HR Zone 1`.

#### 2. Lossless Structured `<Extensions>`:
Written into `<Extensions>` under `<Lap>`:
```xml
<Extensions>
  <att:LapExtension xmlns:att="http://atrainingtracker.com/xmlschemas/TrainingCenterDatabaseExtensions/v1">
    <att:Name>Warm-up</att:Name>
    <att:Description>10 mins easy spin in HR Zone 1</att:Description>
  </att:LapExtension>
</Extensions>
```
When importing a TCX file generated by aTrainingTracker, the presence of `<att:Name>` and `<att:Description>` guarantees exact, lossless recovery without parsing heuristics.

#### 3. XML Character Escaping:
All XML output containing text fields (`name`, `description`, `notes`) must be strictly sanitized for XML special characters (`&amp;`, `&lt;`, `&gt;`, `&quot;`, `&apos;`).

---

## 3. Component Deep-Dive & Execution Flow

### 3.1 TCX Export (`TCXFileWriter.java`)
1. **Pre-Loading Laps (Query Optimization)**:
   - Replace the repeated per-lap query (`lapDb.query(...)`) inside the cursor loop by pre-fetching all laps for `workoutID` via `LapsDatabaseManager.getInstance(mContext).getLaps(workoutID)` into an in-memory `Map<Long, LapData>`.
   - This eliminates N+1 SQLite queries during export.
2. **Closing Laps with Notes and Extensions**:
   - Track `currentLapData` for the active lap split.
   - When a lap transitions (`prevLineLap != lap` and `lap != BANALService.INIT_LAP_NR`), write `</Track>`, followed by `writeLapNotesAndExtensions(...)`, then `</Lap>`.
   - At the tail after the sample cursor loop completes, close the final lap with `writeLapNotesAndExtensions(...)` before writing `</Lap>`.
3. **Workout Notes**:
   - Write activity-level `<Notes>` under `<Activity>` if `description` is non-empty.

### 3.2 TCX Import (`LegacyImportEngine.kt`)
1. **`ParsedLap` Data Model**:
   - Add `var name: String? = null` and `var description: String? = null`.
2. **XML Pull Parser Enhancements**:
   - Track `inLap: Boolean` (set `true` on `<Lap>` START_TAG, `false` on `</Lap>` END_TAG).
   - Track `currentLap: ParsedLap?` (reset to `null` on `</Lap>` END_TAG).
   - On `<Notes>`:
     - If `inLap && currentLap != null`:
       - Parse bracket format `^\[(.*?)\](?:\s*(.*))?$`:
         - Group 1 -> `name`
         - Group 2 -> `description` (if non-empty)
       - If no brackets:
         - If contains `\n`: line 1 (if `<= 40` chars) is `name`, remaining lines are `description`.
         - If single line: if `<= 40` chars -> `name`; if `> 40` chars -> `description`.
     - If `!inLap`:
       - Populate `workoutNotes = text` (whole workout description).
   - On `<att:Name>` / `<Name>` inside lap: directly set `currentLap.name = text`.
   - On `<att:Description>` / `<Description>` inside lap: directly set `currentLap.description = text`.
3. **Database Insertion**:
   - In `LegacyImportEngine.recalculateStats(...)`:
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
     This utilizes the existing 8-parameter `saveLap(...)` method in `LapsDatabaseManager.java`.

---

## 4. Requirements & Verification Traceability

| Artifact | Identifier | Description |
| :--- | :--- | :--- |
| **Requirement** | `REQ-DAT-012` | **TCX Lap Information (Name and Description) Bidirectional Export & Import.** |
| **Test Specification** | `TST-DAT-006` | **TCX Lap Serialization, Parsing, Round-Trip & Escaping Verification.** |
| **Jira Ticket** | `ATT-892` | [Feature] Import / Export Lap info (Name and Description) |
| **Sub-task** | `ATT-910` | [Analysis] Import / Export Lap info (Name and Description) |

---

## 5. System Invariants & Quality Standards

1. **Schema Compliance**: TCX files generated MUST remain 100% valid under `TrainingCenterDatabasev2.xsd`.
2. **Data Integrity**: Numerical lap metrics (`time_total_s`, `distance_total_m`, `speed_average_mps`) MUST remain completely unchanged during export and import.
3. **Backward Compatibility**: Pre-existing TCX files without lap notes or extensions MUST continue to import cleanly with null `name` and `description`.
4. **Third-Party Resilience**: Malformed or unescaped notes from external sources MUST NOT cause XML parse exceptions or import crashes.
5. **Human Gate Guard**: In accordance with ASPICE governance, transitions to `Erledigt` remain strictly reserved for the human user.
