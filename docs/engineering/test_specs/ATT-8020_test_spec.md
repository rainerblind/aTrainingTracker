# Stage 2: Requirement & Test Specification - ATT-8020: Export Raw Polar Sidecars & 1-Tap Aftermath Diagnostic Bundle Share

**Ticket**: [ATT-8020](https://rainerblind.atlassian.net/browse/ATT-8020) (`2 SP`) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) | **Sprint**: `2026-47.2`  
**Requirement Mapping**: `REQ-EXP-044` | **Test Spec ID**: `TST-EXP-044` | **Target Release**: `V5.0.0`  
**Author**: `agent1` (`subagent_flash`, Role Rotation `Rule 7.1`)  

---

## 1. Requirement Specification (`REQ-EXP-044`)
- **Attributes**: Priority `Medium` | Type `Functional` | Method `Unit / Integration` | Status `Planned`
- **System Requirements**: `SYSREQ-DEX-001` (Multi-Format Telemetry Export), `SYSREQ-DEX-004` (Cloud Storage Sync)
- **REQ-EXP-044.1 (Multi-Destination Raw Sidecar Export & Graceful Skip)**: `FileFormat.java` SHALL define `RAW_SENSOR("raw_sensor", ".polar_raw.csv.gz", R.string.file_format_raw_sensor)` with MIME `"application/gzip"`. `ExportManager` SHALL support exporting `RAW_SENSOR` across `ExportType.FILE` (via `RawSensorFileExporter` copying to MediaStore/Downloads), `ExportType.DROPBOX`, and `ExportType.GOOGLE_DRIVE` governed by opt-in setting `export_raw_sensor` (default `false`). When a workout has no sidecar, export SHALL complete with a graceful skip and MUST NOT fail standard exports.
- **REQ-EXP-044.2 (1-Tap Aftermath Diagnostic ZIP Bundle Packaging)**: When a workout has an available `.polar_raw.csv.gz` sidecar, Workout Details (*Aftermath*) SHALL expose a "Report Raw Sensor Data" action in the header context menu and diagnostic banner. `SensorDiagnosticBundlePackager` SHALL assemble a `.zip` in `cache/diagnostics/` containing the raw sidecar, `workout_summary.json`, `laps.json`, `samples_1hz.csv`, and `diagnostic_metadata.json` with user notes.
- **REQ-EXP-044.3 (Privacy-Preserving GPS Stripping Toggle)**: The diagnostic share sheet SHALL provide an "Include GPS Location" toggle (default `false`). When unchecked, latitude and longitude coordinates SHALL be strictly omitted from `samples_1hz.csv` and `summary.json`.
- **REQ-EXP-044.4 (Secure FileProvider Sharing & 9-Locale Localization)**: `file_paths.xml` SHALL declare `<cache-path name="diagnostics" path="diagnostics/" />`. The diagnostic sheet SHALL dispatch `Intent.ACTION_SEND` (support email to `rainer.blind@gmail.com`) granting `FLAG_GRANT_READ_URI_PERMISSION` via `FileProvider`. All user-facing strings across all 9 locales (`EN, DE, ES, FR, IT, JA, NL, PL, PT`) SHALL be defined in `strings_export.xml` or `strings_devices.xml` without emojis (`Rule 38`, `ATT-5400`).

### Acceptance Criteria (Given-When-Then) & System Invariants
- *Given* raw sensor export is enabled and a workout has a `.polar_raw.csv.gz` sidecar, *When* `ExportManager` runs for Local Folder, Dropbox, or Google Drive, *Then* the sidecar is exported alongside configured workout files.
- *Given* a workout recorded without a raw sensor sidecar, *When* `ExportManager` runs, *Then* raw sensor export gracefully skips without error, and all standard formats export successfully.
- *Given* a workout with a raw sidecar viewed in Workout Details, *When* the user taps "Report Raw Sensor Data", enters notes, unchecks "Include GPS Location", and taps "Share Diagnostic Bundle", *Then* a valid `.zip` is generated containing the raw sidecar, computed laps, and anonymized 1-Hz series without coordinates, and an `ACTION_SEND` share intent is launched via `FileProvider`.
- **Invariants**: Standard format exports (`.fit`, `.tcx`, `.gpx`, `.csv`) completely unaffected by sidecar presence; zero BLE or UI thread blocking during zip packaging; GPS stripping strictly enforced when opted out; `ANT-Android-SDKs` remains untouched at `160000 commit 0e8cea714a231fb9565170d4ed01eafa868f9798`.

---

## 2. Test Specification (`TST-EXP-044`)
1. `TST-EXP-044.1` (`RawSensorFileExporterTest.kt`): Validates copying `.polar_raw.csv.gz` to public downloads via `copyFileToDownloads()`, and graceful skip when sidecar is absent on disk.
2. `TST-EXP-044.2` (`ExportManagerRawSensorTest.kt`, `DropboxUploaderRawSensorTest.kt`, `GoogleDriveUploaderRawSensorTest.kt`): Validates `ExportManager`, `DropboxUploader`, and `GoogleDriveUploader` export workflows for `FileFormat.RAW_SENSOR`.
3. `TST-EXP-044.3` (`SensorDiagnosticBundlePackagerTest.kt`): Validates ZIP archive creation, structure, raw sidecar inclusion, summary JSON, laps JSON, and metadata parsing.
4. `TST-EXP-044.4` (`SensorDiagnosticBundlePrivacyTest.kt`): Validates strict omission of latitude and longitude in `samples_1hz.csv` and `summary.json` when `includeGps = false`, and inclusion when `true`.
5. `TST-EXP-044.5` (`TranslationParityTest.kt`, `SensorDiagnosticShareSheetScreenshotTest.kt`): Validates 9-language localization parity without emojis and generates `docs/engineering/walkthroughs/img/ATT-8020_diagnostic_share_sheet.png` for 4-point visual inspection (`Rule 8.5`).

---

## 3. Traceability Matrix

| Requirement | Description | Test Spec ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-EXP-044.1` | Multi-destination raw sidecar export & graceful skip | `TST-EXP-044.1`, `TST-EXP-044.2` | Unit / Integration |
| `REQ-EXP-044.2` | 1-Tap Aftermath diagnostic ZIP packaging | `TST-EXP-044.3` | Unit |
| `REQ-EXP-044.3` | Privacy-preserving GPS stripping toggle | `TST-EXP-044.4` | Unit |
| `REQ-EXP-044.4` | Secure FileProvider sharing & 9-locale strings | `TST-EXP-044.5` | Unit / Screenshot |
