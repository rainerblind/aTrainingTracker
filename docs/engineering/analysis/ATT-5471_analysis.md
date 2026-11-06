# Stage 1 Analysis: ATT-5471 - StravaCsvParser: Language-Independent Fixed Column Indices & Pattern Detection

**Ticket**: [ATT-5471](https://rainerblind.atlassian.net/browse/ATT-5471)  
**Sub-task**: `[Analysis]` (To be created via `submit-stage`)  
**Parent Epic**: None (*Standalone*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5471`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
In `StravaCsvParser.kt`, CSV columns for `activities.csv`, `bikes.csv`, and `shoes.csv` are resolved via hardcoded German and English header aliases (`findFirstHeader(headers, "aktivitäts-id", "activity id", ...)`). Because Strava localizes CSV export headers into the athlete's web UI language (e.g. French, Spanish, Italian, Dutch, Polish, Portuguese, Japanese), exports generated in non-DE/EN locales fail header matching (`idIdx = -1`), silently skipping all workout metadata enrichment and Strava upload ID registration. Furthermore, `StravaActivityRow` parses 9 unused telemetry summary metric columns (`elapsedTime`, `avgWatts`, `maxWatts`, `elevationGain`, `calories`, `avgHr`, `maxHr`, `movingTime`, `distance`) that are already provided by `.fit`/`.tcx`/`.gpx` stream files.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Fragile Header Matching**: `findFirstHeader()` relies on exact lowercase string matches for German and English aliases. Non-matching locales produce index `-1`, causing `StravaActivityRow` construction to abort on empty `activityId` (`if (rawId.isNullOrBlank()) continue`).
* **Unnecessary Metric Parsing Overhead**: `StravaBulkExportImporter.kt` ignores summary metrics in `StravaActivityRow` because telemetry streams (`FitWorkoutParser`, `TcxWorkoutParser`, `GpxWorkoutParser`) provide complete time series, leaving 9 `Double` fields as dead allocations.
* **Independent Peer Review & Verification**:
  * Audited real Strava export `/home/rainer/Downloads/export_387065/activities.csv` (104 columns, 3,287 rows). Verified canonical column indices: Col 0 (`Activity ID`), Col 1 (`Date`), Col 2 (`Name`), Col 3 (`Type`), Col 4 (`Description`), Col 9 (`Commute`), Col 10 (`Private Note`), Col 11 (`Gear`), Col 12 (`Filename` with 3,214 rows matching `activities/*`), Col 102 (`Media`).
  * Audited `bikes.csv` and `shoes.csv`: Col 0 is `Name`, Col 1 is `Brand`, Col 2 is `Model`.
  * Audited synthetic test fixtures: `testImportArchive_duplicateSkipped` uses non-canonical 4-column CSV (`Activity ID,Filename,...`). Header aliases must take priority when present, with fixed indices and pattern auto-detection as universal fallbacks.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Implement language-independent resolution in `StravaCsvParser.kt`: alias matching $\rightarrow$ content pattern detection (`activities/.*` for filename) $\rightarrow$ canonical fixed column index fallback.
  * Update `parseBikes()` and `parseShoes()` with fixed index fallbacks (Col 0: Name, Col 1: Brand, Col 2: Model).
  * Drop 9 unused metric columns from `StravaActivityRow` and `StravaCsvParser.kt`.
  * Expand `StravaCsvParserTest.kt` with multi-language export tests (French, Spanish, Japanese).
* **Out-of-Scope Non-Goals**:
  * Modifying `FitWorkoutParser`, `TcxWorkoutParser`, or `GpxWorkoutParser`.
  * Altering `EquipmentDbHelper` or `StravaUploadDbHelper` schemas or transaction lifecycles.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-MIG-048.2` (*Bilingual & Multi-Line RFC 4180 CSV Metadata Parser*). Targets `StravaCsvParser.kt` and `StravaCsvParserTest.kt`.
* **Historical Origin & Commit Trace**: Added in Sprint 2026-45.6 under `ATT-4957` (commit `cd915bb0`).
* **Root Reason for Existing Formulation**: Scoped initially to German and English exports available during ATT-4957 testing; header aliases were hardcoded for those two languages.
* **Preservation of Core Invariants**: RFC 4180 compliance (escaped `""`, multi-line quotes), Zip-Slip path sanitization, duplicate skipping, and 100% backward compatibility for existing German/English archives and unit tests are strictly preserved.

---

## 5. Architectural Strategy & High-Level Solution
* **Hybrid Column Resolution**:
  1. Priority 1: Match existing header aliases (`findFirstHeader`).
  2. Priority 2: For filename, auto-detect column index matching regex `(?i)^activities/.*` across the first non-empty data rows.
  3. Priority 3: Fall back to Strava canonical fixed indices (`id=0`, `date=1`, `name=2`, `type=3`, `desc=4`, `commute=9`, `note=10`, `gear=11`, `filename=12`).
* **Model Pruning**: Remove `elapsedTime`, `movingTime`, `distance`, `elevationGain`, `calories`, `avgHr`, `maxHr`, `avgWatts`, and `maxWatts` from `StravaActivityRow`, keeping only metadata required by `StravaBulkExportImporter`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. RFC 4180 compliance preserved (escaped quotes, multi-line quoted fields).
  2. Zero regression on existing German and English export ingestion tests.
  3. Thread safety on `Dispatchers.IO` and zero disk staging during archive streaming.
* **Risk Rating**: **LOW** (Localized parser refactor confined to `StravaCsvParser.kt` and its unit tests; no database migrations, Android UI, or service dependencies).
