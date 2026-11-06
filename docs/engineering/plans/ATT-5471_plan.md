# Stage 3: Implementation Plan - ATT-5471: StravaCsvParser: Language-Independent Fixed Column Indices & Pattern Detection

**Ticket**: [ATT-5471](https://rainerblind.atlassian.net/browse/ATT-5471)  
**Sub-task**: `[Impl-Plan]` (To be created via `submit-stage`)  
**Parent Epic**: None (*Standalone*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5471`  
**Requirement Mapping**: `REQ-MIG-048` (`REQ-MIG-048.2`)  
**Test Mapping**: `TST-MIG-046` (`TST-MIG-046.1`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
`StravaCsvParser.kt` resolves columns in `activities.csv`, `bikes.csv`, and `shoes.csv` using hardcoded German/English header strings. Non-DE/EN Strava web exports (e.g. French, Spanish, Japanese) fail header matching (`idIdx = -1`), silently skipping workout metadata enrichment and upload ID registration. Furthermore, `StravaActivityRow` parses 9 unused summary telemetry columns already provided by `.fit`/`.tcx`/`.gpx` stream files.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-MIG-048.2` (*Language-Independent & Multi-Line RFC 4180 CSV Metadata Parser*)
* **Test Mapping**: `TST-MIG-046.1` (*Language-Independent Strava CSV Parsing & Metric Pruning Contract*)

---

## 3. System Invariants & Preserved Behavior
1. **RFC 4180 Compliance**: Multi-line quoted values, escaped quotes (`""`), and CRLF/LF newlines parse identically.
2. **Backward Compatibility**: Existing German and English export archives and synthetic test CSVs pass without regression.
3. **Data Protection & Thread Safety**: Archive streaming remains non-blocking on `Dispatchers.IO` with zero disk bloat.
4. **Parent Human Gate**: Terminal transition to `Erledigt` is reserved strictly for human acceptance.

---

## 4. Proposed Architectural Changes

### Component 1: `StravaCsvParser.kt` & `StravaActivityRow`
* Implement 3-tier hybrid resolution in `parseActivities()`:
  1. Priority 1: Check known header aliases via `findFirstHeader()`.
  2. Priority 2: Auto-detect stream filename column index via regex `(?i)^activities/.*` over data rows.
  3. Priority 3: Fall back to Strava canonical fixed column indices (`id=0`, `date=1`, `name=2`, `type=3`, `desc=4`, `commute=9`, `note=10`, `gear=11`, `filename=12`, `media=102`).
* In `parseBikes()` & `parseShoes()`, fall back to fixed indices (Col 0: Name, Col 1: Brand, Col 2: Model) when header aliases are absent.
* Prune 9 dead summary metrics from `StravaActivityRow` (`elapsedTime`, `movingTime`, `distance`, `elevationGain`, `calories`, `avgHr`, `maxHr`, `avgWatts`, `maxWatts`).
* Expand `mapSportType()` for multilingual Strava types (French, Spanish, Italian, Dutch, Polish) and `parseBoolean()` for international truthy values.

### Component 2: `StravaCsvParserTest.kt`
* Add multi-locale contract tests (French, Spanish, Japanese) for `activities.csv`, `bikes.csv`, and `shoes.csv`.
* Verify regex pattern auto-detection when filename column is shifted.
* Update existing tests to reflect pruned `StravaActivityRow` fields.

### UI Consistency (Rule 23)
* **No UI changes.** Localized metadata parsing engine change only.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Prune Unused Metric Fields in `StravaActivityRow` (`REQ-MIG-048.2`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/StravaCsvParser.kt`
* Remove 9 unused `Double?` telemetry properties from `StravaActivityRow` and their parsing logic in `parseActivities()`.

### Step 2: Implement Hybrid Column Resolution & Multi-Language Support (`REQ-MIG-048.2`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/StravaCsvParser.kt`
* Add filename pattern auto-detection and fixed canonical column index fallbacks for activities, bikes, shoes, and media captions.
* Add multilingual aliases to `mapSportType()` and `parseBoolean()`.

### Step 3: Expand Unit & Contract Tests (`TST-MIG-046.1`)
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/migration/StravaCsvParserTest.kt`
* Add test cases verifying French, Spanish, Japanese localized headers, fixed index fallbacks, and regex filename detection.
* Update existing tests to remove references to pruned metric fields.

### Step 4: Tier 1 Targeted Verification
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted migration package tests, build hygiene tests, and living doc parity verification.
* **Rollback**: Isolated branch `feature/ATT-5471` can be discarded with `git checkout sprint/2026-45.8` without side effects.
