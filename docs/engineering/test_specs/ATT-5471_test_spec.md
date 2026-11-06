# Stage 2: Requirement & Test Specification - ATT-5471: StravaCsvParser: Language-Independent Fixed Column Indices & Pattern Detection

**Ticket**: [ATT-5471](https://rainerblind.atlassian.net/browse/ATT-5471)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: None (*Standalone*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5471`  
**Requirement Mapping**: `REQ-MIG-048` (`REQ-MIG-048.2`) | **Test Spec ID**: `TST-MIG-046` (`TST-MIG-046.1`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Requirement Specification (`REQ-MIG-048.2`)

### 1.1 Problem Statement & Rationale
Strava localizes CSV export headers in `activities.csv`, `bikes.csv`, and `shoes.csv` into the athlete's web UI language (e.g. French, Spanish, Italian, Japanese). `StravaCsvParser.kt` previously looked up columns strictly via hardcoded German and English aliases, causing non-DE/EN archives to fail header resolution (`idIdx = -1`) and silently discard activity metadata enrichment. Additionally, `StravaActivityRow` parsed 9 unused summary telemetry columns already provided by `.fit`/`.tcx`/`.gpx` stream files.

### 1.2 Functional & Technical Invariants
1. **Hybrid Column Resolution (`REQ-MIG-048.2`)**:
   - `StravaCsvParser.parseActivities` SHALL resolve columns using a 3-tier hybrid strategy: (1) Match existing German/English aliases when present; (2) Auto-detect stream filename column index via content regex `(?i)^activities/.*` across initial data rows; (3) Fall back to canonical Strava fixed column indices (`id=0`, `date=1`, `name=2`, `type=3`, `desc=4`, `commute=9`, `note=10`, `gear=11`, `filename=12`, `media=102`).
   - `parseBikes` and `parseShoes` SHALL match aliases first and fall back to fixed indices (`name=0`, `brand=1`, `model=2`).
2. **Model Pruning (`REQ-MIG-048.2`)**:
   - Remove unused summary metric columns (`elapsedTime`, `movingTime`, `distance`, `elevationGain`, `calories`, `avgHr`, `maxHr`, `avgWatts`, `maxWatts`) from `StravaActivityRow` and `StravaCsvParser.kt`.
3. **Core Parser Invariants**:
   - RFC 4180 parsing compliance (multi-line quoted fields, escaped `""`), stream filename path sanitization, and 100% backward compatibility with German/English archives MUST remain intact.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Non-DE/EN Archive Parsing)**: *Given* an `activities.csv` from a French, Spanish, or Japanese Strava account export, *When* `StravaCsvParser.parseActivities` executes, *Then* activity ID, name, date, type, commute, notes, gear, and stream filename are resolved correctly.
* **AC-2 (Gear Fixed Column Fallback)**: *Given* a `bikes.csv` or `shoes.csv` without English/German headers, *When* `parseBikes` or `parseShoes` executes, *Then* gear name, brand, and model are parsed from columns 0, 1, and 2.
* **AC-3 (Backward Compatibility & RFC 4180)**: *Given* existing German or English CSV exports or multi-line quoted fields, *When* parsed, *Then* all existing test assertions pass without regression.

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: `REQ-MIG-048.2`, targeting `StravaCsvParser.kt` and `StravaCsvParserTest.kt`.
* `Historical Origin & Commit Trace`: Sprint 2026-45.6 (`ATT-4957`, commit `cd915bb0`) introduced `REQ-MIG-048.2` with German and English header aliases.
* `Root Reason for Existing Formulation`: Scoped initially to German and English test exports available during ATT-4957; Strava's localized header variants in other UI languages were not yet handled.
* `Preservation of Core Invariants`: RFC 4180 parsing, Zip-Slip path sanitization, duplicate skipping, and 100% backward compatibility for German/English fixtures remain strictly preserved.

---

## 2. Test Specification (`TST-MIG-046.1`)

### Test Case 1: Multi-Locale `activities.csv` Ingestion Contract (`TST-MIG-046.1`)
* **Scope**: Unit test in `StravaCsvParserTest.kt`.
* **Procedure**: Provide CSV fixtures with French, Spanish, and Japanese localized headers having canonical Strava column layouts. Verify `StravaActivityRow` extracts `activityId`, `activityName`, `gearName`, and `filename` matching regex `^activities/.*`.

### Test Case 2: Multi-Locale Gear Resolution Contract (`TST-MIG-046.1`)
* **Scope**: Unit test in `StravaCsvParserTest.kt`.
* **Procedure**: Provide `bikes.csv` and `shoes.csv` with non-English/German headers. Verify gear items are parsed via fixed indices 0 (name), 1 (brand), 2 (model).

### Test Case 3: Targeted Migration Suite Regression
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% test pass rate across all targeted tests.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-MIG-048.2` | Multi-locale hybrid column resolution (aliases, regex, fixed indices) | `TST-MIG-046.1` | `StravaCsvParserTest` (FR, ES, JA fixtures) |
| `REQ-MIG-048.2` | Fixed index gear fallback for `bikes.csv` & `shoes.csv` | `TST-MIG-046.1` | `StravaCsvParserTest.testParseBikesAndShoes_fixedIndices` |
| `REQ-MIG-048.2` | Pruning unused summary metric columns from `StravaActivityRow` | `TST-MIG-046.1` | `StravaCsvParserTest` & `StravaBulkExportImporterTest` |
| `REQ-MIG-048` | Strava migration pipeline stability and backward compatibility | `TST-MIG-046` | Targeted Unit Test Suite & Governance Parity |
