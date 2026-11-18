# Living Requirements: Persistence, Migration & Cloud Export (Part 2 of 2)

**Document ID**: `DOC-REQ-MIG-P2`  
**Version**: 1.0.1  
**Status**: Reviewed  
**Approver**: Pending PO Sign-Off  

**Domain**: `data_migration_export_part2`  
**Prefixes**: `DAT`, `EXP`, `EXT`, `MIG`  
**Scope**: Database schema, backup/restore, migration pipelines, Strava/cloud upload, FIT/GPX export.  
**Total Requirements**: 2

---

### REQ-EXP-043: Standard Garmin FIT Pedal Power Balance, Torque Effectiveness & Pedal Smoothness Serialization and Lossless Round-Trip [Verified]
- **Tests**: `TST-EXP-043`
- **System Requirement(s)**: `SYSREQ-DEX-003`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **Implementation**: `FitFileWriter.java`, `FitWorkoutParser.kt`, `WorkoutExportHeader.kt`, `WorkoutRepository.kt`
- **Rationale**: Cyclists using dual-sided ANT+ or Bluetooth LE power meters expect recorded Left/Right pedal power balance, torque effectiveness (TE), and pedal smoothness (PS) metrics exported into standard Garmin `.fit` files for analysis on Intervals.icu, Strava, TrainingPeaks, GoldenCheetah, and WKO5, as well as preserved across `.fit` backup/restore cycles without data loss or bilateral inversion.
- **Description**:
  1. *Sample Streaming Extension (`WorkoutExportHeader.kt` & `WorkoutRepository.kt`)*: `WorkoutSampleRecord` SHALL include nullable fields `pedalPowerBalance: Double?`, `torqueEffectivenessL: Double?`, `torqueEffectivenessR: Double?`, `pedalSmoothnessL: Double?`, `pedalSmoothnessR: Double?`, and `pedalSmoothnessCombined: Double?`. `WorkoutRepository.streamSamples(...)` SHALL project `SensorType.PEDAL_POWER_BALANCE`, `TORQUE_EFFECTIVENESS_L`, `TORQUE_EFFECTIVENESS_R`, `PEDAL_SMOOTHNESS_L`, `PEDAL_SMOOTHNESS_R`, and `PEDAL_SMOOTHNESS` while preserving $O(1)$ memory streaming (`REQ-DAT-042.2`) and zero SQLite imports in `FitFileWriter.java` (`REQ-DAT-042.5`).
  2. *Standard FIT Trackpoint Serialization (`FitFileWriter.java`)*: For trackpoint records containing pedal dynamics:
     - `pedalPowerBalance` (normalized Right % `0..100` per `REQ-CON-022`) SHALL be encoded via `record.setLeftRightBalance((short) ((Math.round(clampedRightPct) & LeftRightBalance.MASK) | LeftRightBalance.RIGHT))` where `LeftRightBalance.RIGHT = 0x80` and `LeftRightBalance.MASK = 0x7F`.
     - `torqueEffectivenessL` and `torqueEffectivenessR` (`0.0..100.0%`) SHALL be serialized via `record.setLeftTorqueEffectiveness(Float)` and `record.setRightTorqueEffectiveness(Float)`.
     - `pedalSmoothnessL`, `pedalSmoothnessR`, and combined `pedalSmoothnessCombined` (`0.0..100.0%`) SHALL be serialized via `record.setLeftPedalSmoothness(Float)`, `record.setRightPedalSmoothness(Float)`, and `record.setCombinedPedalSmoothness(Float)`.
  3. *Lap & Session Aggregate Serialization (`FitFileWriter.java`)*: During sample streaming, the exporter SHALL accumulate online sums and counts in $O(1)$ memory. When valid samples exist:
     - `LapMesg` and `SessionMesg` SHALL encode average balance via `setLeftRightBalance(Integer)` using `LeftRightBalance100` (`(Math.round(avgRightPct * 100.0) & LeftRightBalance100.MASK) | LeftRightBalance100.RIGHT`, `LeftRightBalance100.RIGHT = 0x8000`, `LeftRightBalance100.MASK = 0x3FFF`, `0.01%` resolution).
     - `LapMesg` and `SessionMesg` SHALL encode averages via `setAvgLeftTorqueEffectiveness`, `setAvgRightTorqueEffectiveness`, `setAvgLeftPedalSmoothness`, `setAvgRightPedalSmoothness`, and `setAvgCombinedPedalSmoothness`. Workouts lacking these sensors SHALL leave corresponding fields unset (`null`).
  4. *Lossless FIT Ingestion (`FitWorkoutParser.kt`)*: The parser SHALL decode `RecordMesg.getLeftRightBalance()` (extracting Right % when `LeftRightBalance.RIGHT` is set and populating `SensorType.PEDAL_POWER_BALANCE`), `getLeftTorqueEffectiveness()`, `getRightTorqueEffectiveness()`, `getLeftPedalSmoothness()`, `getRightPedalSmoothness()`, and `getCombinedPedalSmoothness()` into `bufferedSamples` and register the corresponding `SensorType` entries in `foundSensors` (`REQ-MIG-053`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* a recorded cycling workout containing `PEDAL_POWER_BALANCE = 52.0` (48% L / 52% R), `TORQUE_EFFECTIVENESS_L = 78.5`, `TORQUE_EFFECTIVENESS_R = 81.0`, `PEDAL_SMOOTHNESS_L = 24.0`, and `PEDAL_SMOOTHNESS_R = 25.5`,
• *When* exported to `.fit` via `FitFileWriter` and decoded using Garmin FIT SDK `Decode`,
• *Then* `RecordMesg.getLeftRightBalance()` equals `(52 | LeftRightBalance.RIGHT)`, `getLeftTorqueEffectiveness()` / `getRightTorqueEffectiveness()` equal `78.5f` / `81.0f`, and `getLeftPedalSmoothness()` / `getRightPedalSmoothness()` equal `24.0f` / `25.5f`.
• *Given* a multi-lap workout with recorded L/R balance, TE, and PS samples,
• *When* exported to `.fit`,
• *Then* `LapMesg` and `SessionMesg` populate `left_right_balance` using `LeftRightBalance100` (`0.01%` resolution with `LeftRightBalance100.RIGHT = 0x8000`) and populate average TE and PS fields, while workouts without these sensors omit them cleanly (`null`).
• *Given* a workout with L/R balance, TE, and PS exported to `.fit` by `FitFileWriter`,
• *When* re-imported through `FitWorkoutParser`,
• *Then* `SensorType.PEDAL_POWER_BALANCE`, `TORQUE_EFFECTIVENESS_L/R`, and `PEDAL_SMOOTHNESS_L/R` (or combined `PEDAL_SMOOTHNESS`) are restored into `WorkoutSamples` and `foundSensors` without L/R inversion.

**Invariants**: $O(1)$ heap memory overhead during FIT export stream (`REQ-DAT-042.2`); zero SQLite imports or direct database calls in `FitFileWriter.java` (`REQ-DAT-042.5`); Right-pedal 0..100% normalization preserved (`REQ-CON-022.1`); clean omission for absent sensors without artificial 0% artifacts.

---

### REQ-EXP-044: Polar Raw Sensor Telemetry Sidecar Export & Aftermath Diagnostic ZIP Bundle Sharing (`ATT-8020`) [Verified]
- **Tests**: `TST-EXP-044`
- **System Requirement(s)**: `SYSREQ-DEX-001`, `SYSREQ-DEX-004`, `SYSREQ-DEX-007`
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit / Integration
- **Implementation**: `FileFormat.java`, `ExportType.java`, `ExportManager.java`, `TrainingApplication.java`, `RawSensorFileExporter.kt`, `DropboxUploader.java`, `GoogleDriveUploader.kt`, `ExportSettingsDialog.kt`, `SensorDiagnosticBundlePackager.kt`, `SensorDiagnosticShareSheet.kt`, `WorkoutHeader.kt`, `WorkoutDetailTabbedContent.kt`, `docs/architecture.md`
- **Rationale**: Persisting raw sensor telemetry in external `.polar_raw.csv.gz` files (`ATT-8019`) enables algorithm R&D and diagnostic replay; exporting sidecars through `ExportManager` to local public storage, Dropbox, and Google Drive grants data sovereignty, while a 1-tap Aftermath diagnostic bundle share (`.zip`) empowers athletes to report sensor metric discrepancies (e.g., swim turn miscounting, stroke misclassification, running cadence glitches) directly to support with complete raw and computed context and user privacy controls.
- **Description**:
  1. *Multi-Destination Raw Sidecar Export (`REQ-EXP-044.1`)*:
     - `FileFormat.java` SHALL define `RAW_SENSOR("raw_sensor", ".polar_raw.csv.gz", R.string.file_format_raw_sensor)` with MIME type `"application/gzip"`.
     - `ExportManager` SHALL support exporting `RAW_SENSOR` across `ExportType.FILE` (via `RawSensorFileExporter` copying to MediaStore/Public Downloads), `ExportType.DROPBOX`, and `ExportType.GOOGLE_DRIVE`.
     - `TrainingApplication` and `ExportSettingsDialog` SHALL provide an opt-in toggle (`export_raw_sensor`) defaulting to `false`.
     - For workouts lacking a `.polar_raw.csv.gz` sidecar file, `ExportManager`, `DropboxUploader`, and `GoogleDriveUploader` SHALL complete with a graceful skip (status success / skipped) and MUST NOT fail standard FIT/TCX/GPX/CSV exports or crash the export pipeline.
  2. *1-Tap Aftermath Sensor Diagnostic ZIP Packaging (`REQ-EXP-044.2`)*:
     - When a workout has an available `.polar_raw.csv.gz` sidecar (`PolarRawTelemetryPruner.hasSidecar`), Workout Details (*Aftermath*) SHALL present a "Report Raw Sensor Data" action in the header context menu and diagnostic banner.
     - `SensorDiagnosticBundlePackager` SHALL assemble a compressed `.zip` archive inside `cache/diagnostics/` containing:
       (a) the raw `.polar_raw.csv.gz` sidecar stream (`ATT-8019`),
       (b) `workout_summary.json` containing sport, duration, distance, calories, extrema, app version, Android OS version, and sensor model/firmware,
       (c) `laps.json` or `laps.csv` containing computed lap and pool length intervals,
       (d) `samples_1hz.csv` containing 1-Hz computed sensor series from `WorkoutSamplesDatabaseManager`, and
       (e) `diagnostic_metadata.json` including an optional user-entered symptom description note.
  3. *Privacy-Preserving GPS Stripping Toggle (`REQ-EXP-044.3`)*:
     - The diagnostic share sheet SHALL provide an explicit "Include GPS Location" toggle (default `false`).
     - When unchecked, `SensorDiagnosticBundlePackager` SHALL strictly omit latitude and longitude coordinates from `samples_1hz.csv` and `summary.json` prior to zip creation, safeguarding user location privacy.
  4. *Secure FileProvider Sharing & 9-Locale Localization (`REQ-EXP-044.4`)*:
     - `file_paths.xml` SHALL declare `<cache-path name="diagnostics" path="diagnostics/" />`.
     - The diagnostic sheet SHALL dispatch `Intent.ACTION_SEND` (or `mailto:rainer.blind@gmail.com`) granting `FLAG_GRANT_READ_URI_PERMISSION` for the generated `.zip` URI via `FileProvider`.
     - All string resources across all 9 supported locales (`EN`, `DE`, `ES`, `FR`, `IT`, `JA`, `NL`, `PL`, `PT`) SHALL be defined in `strings_export.xml` or `strings_devices.xml` with zero emojis (`Rule 38`, `ATT-5400`).

**Acceptance Criteria (Given-When-Then)**:
• *Given* raw sensor export is enabled in Export Settings and a finished workout has a `.polar_raw.csv.gz` sidecar,
• *When* `ExportManager` executes for Local Folder, Dropbox, or Google Drive,
• *Then* the sidecar is exported to public downloads and cloud storage without altering standard format exports.
• *Given* a workout recorded without a raw sensor sidecar,
• *When* `ExportManager` executes with raw sensor export enabled,
• *Then* raw sensor export gracefully skips without error, and all standard formats export successfully.
• *Given* a workout with an existing `.polar_raw.csv.gz` sidecar viewed in Workout Details,
• *When* the user taps "Report Raw Sensor Data", enters a description note, unchecks "Include GPS Location", and taps "Share Diagnostic Bundle",
• *Then* a valid `.zip` is generated containing the raw sidecar, computed laps, and anonymized 1-Hz series without coordinates, and an `ACTION_SEND` share intent is launched via `FileProvider`.

**Invariants**: Standard exports (`.fit`, `.tcx`, `.gpx`, `.csv`) completely unaffected by sidecar availability; zero BLE or UI thread blocking during diagnostic zip creation; GPS stripping strictly enforced when opted out; `ANT-Android-SDKs` remains untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-18 | AI Agent (`ATT-8206`) | `ATT-8206` | Split oversized shard into compliant sub-shard (<= 4,000 lines, `REQ-PRO-093.4`, `REQ-PRO-127.3`). |
| `1.0.1` | 2026-11-18 | AI Agent (`ATT-8201`) | `ATT-8201` | Linked refined `REQ-*` entries in-place to net-new `SYSREQ-TRK-007..013` and `SYSREQ-DEX-007..008`. |
