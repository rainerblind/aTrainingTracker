# Stage 2: Requirement & Test Specification - ATT-4957: Import Strava bulk export ZIP archives

**Ticket**: [ATT-4957](https://rainerblind.atlassian.net/browse/ATT-4957)  
**Parent Epic**: None (*Standalone*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.6`  
**Requirement Mapping**: `REQ-MIG-048` (*Strava Bulk Export Archive Ingestion Pipeline*)  
**Test Mapping**: `TST-MIG-046` (`TST-MIG-046.1`..`TST-MIG-046.7`)  
**Branch**: `feature/ATT-4957`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-11-04  

---

## 1. Overview & Verification Strategy
Strava account archives (`export_<id>.zip`) contain an authoritative metadata summary (`activities.csv`), user equipment tables (`bikes.csv`, `shoes.csv`), and compressed activity streams (`activities/*.fit.gz`, `*.tcx.gz`, `*.gpx.gz`). To ingest these archives without multi-gigabyte disk extraction or XML crashes, `ATT-4957` implements a dedicated streaming ingestion pipeline (`StravaZipImportEngine`). The verification strategy validates RFC 4180 bilingual CSV parsing (German/English column aliases, multiline quoted descriptions), stream sanitization for TCX leading whitespace before `<?xml`, on-the-fly `GZIPInputStream` decompression, `EquipmentDbHelper` gear mapping, Strava `Activity ID` registration in `StravaUploadDbHelper` with `UPLOAD_TO_STRAVA = 0`, and 9-locale UI localization.

---

## 2. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Component | Scope & Target |
| :--- | :--- | :--- | :--- |
| `REQ-MIG-048.2` | `TST-MIG-046.1` | `StravaActivitiesCsvParserTest` | Bilingual headers (DE/EN), multiline quoted strings, escaped quotes (`""`). |
| `REQ-MIG-048.3` | `TST-MIG-046.2` | `TcxLeadingWhitespaceSanitizationTest` | Strips leading whitespace before `<?xml`, eliminating `XmlPullParserException`. |
| `REQ-MIG-048.1` / `REQ-MIG-040` | `TST-MIG-046.3` | `StravaZipImportEngineTest` | In-memory GZIP decompression, Zip-Slip rejection (`CWE-22`), zero disk staging. |
| `REQ-MIG-048.4` / `REQ-MIG-031` | `TST-MIG-046.4` | `StravaZipImportEngineTest` | Enriches title, notes, commute, equipment; safely skips duplicates. |
| `REQ-MIG-048.4` | `TST-MIG-046.5` | `StravaUploadDbRegistrationTest` | Records Strava `Activity ID` as `Uploaded`; sets `UPLOAD_TO_STRAVA = 0`. |
| `REQ-UI-106` | `TST-MIG-046.6` | `TranslationParityTest` | 9-locale parity for archive picker card strings (zero missing keys or token mismatches). |
| `REQ-PRO-001` | `TST-MIG-046.7` | Targeted Suite | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.*"` 100% pass. |

---

## 3. Concrete Test Scenarios (Given-When-Then)

### TST-MIG-046.1: Bilingual & Multi-Line RFC 4180 CSV Parsing
* **Given** an `activities.csv` fixture with German headers (`Aktivitäts-ID`, `Dateiname`, `Name der Aktivität`) and multiline descriptions with commas and escaped quotes (`""`),
* **When** parsed by `StravaActivitiesCsvParser`,
* **Then** all rows are correctly indexed by relative filename, preserving complete descriptions and typed fields.

### TST-MIG-046.2: TCX Leading Whitespace Sanitization
* **Given** a decompressed TCX stream beginning with 10 leading ASCII spaces before `<?xml`,
* **When** processed by `stripBomAndLeadingWhitespace` prior to `TcxWorkoutParser`,
* **Then** leading whitespace is skipped cleanly, and XML parsing succeeds without `XmlPullParserException`.

### TST-MIG-046.3: Streaming Decompression & Zip-Slip Protection
* **Given** a ZIP archive with `.fit.gz`, `.tcx.gz`, `.gpx.gz`, and a malicious entry (`../../evil.fit`),
* **When** ingested by `StravaZipImportEngine`,
* **Then** valid streams decompress on-the-fly without staging files to disk, and the traversal entry throws `SecurityException`.

### TST-MIG-046.4: Metadata Enrichment, Equipment Sync & Deduplication
* **Given** an export row with custom title, private notes, commute flag, and gear name matching `bikes.csv`,
* **When** `StravaZipImportEngine` imports the workout,
* **Then** `WorkoutSummaries` stores enriched title, notes, and resolved `EQUIPMENT_ID`, and re-importing skips duplicate safely.

### TST-MIG-046.5: Strava ID Registration & Cloud Upload Suppression
* **Given** an imported Strava activity with Strava ID `20811350233`,
* **When** persistence completes,
* **Then** `StravaUploadDbHelper` registers the ID with status `Uploaded` and `UPLOAD_TO_STRAVA = 0`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-MIG-048` under Standalone Ticket `ATT-4957` (Sprint `2026-45.6`), extending `REQ-MIG-030` (*Legacy GPX*), `REQ-MIG-031` (*Deduplication*), `REQ-MIG-037` (*Streaming XML Parsing*), `REQ-MIG-038` (*ParsedWorkoutData*), and `REQ-MIG-040` (*Secure Zip Extraction*). Targets `StravaZipImportEngine.kt`, `LegacyImportEngine.kt`, `ImportBackupTabsScreen.kt`, `TrackpointModels.kt`, `EquipmentDbHelper.java`, and `StravaUploadDbHelper.java`.
* **Historical Origin & Commit Trace**: `ATT-314` (commit `4d5b2c1` - Deduplication), `ATT-3982` (commit `b875938` - `REQ-MIG-040` Secure Zip Extraction), `ATT-4008` (commit `69e6b41` - TCX/GPX stream parsers).
* **Root Reason for Existing Formulation**: Prior import architecture supported only individual standalone files and directory scanning of uncompressed files. It lacked archive entry indexing, bilingual CSV metadata mapping, in-memory GZIP decompression, and TCX leading-whitespace tolerance.
* **Preservation of Core Invariants**: Mutex-guarded deduplication, transactional compensatory rollback on failure, Zip-Slip path sanitization, and standalone FIT/TCX/GPX import paths remain strictly invariant.

---

## 5. Invariants & Governance
* **Target Release Parity**: `V5.0.0` verified as active/unreleased in Jira (`tools/jira_util.py versions`).
* **Zero Disk Bloat**: All archive and stream reading executes via streaming without staging raw files to internal storage.
* **Localization Parity**: 9-locale parity verified across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
