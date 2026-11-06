# Stage 5 Walkthrough: ATT-5471 — StravaCsvParser: Language-Independent Fixed Column Indices & Pattern Detection

**Ticket**: [ATT-5471](https://rainerblind.atlassian.net/browse/ATT-5471)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5471`  
**Requirement Mapping**: `REQ-MIG-048` (`REQ-MIG-048.2`)  
**Test Mapping**: `TST-MIG-046` (`TST-MIG-046.1`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-06  

---

## 1. Executive Summary
Resolved an issue where `StravaCsvParser.kt` matched CSV columns in `activities.csv`, `bikes.csv`, and `shoes.csv` strictly via hardcoded German and English header strings (`findFirstHeader(...)`). Because Strava localizes CSV export headers into the athlete's web UI language (e.g. French, Spanish, Italian, Japanese, Dutch, Polish, Portuguese), exports created in non-DE/EN locales failed header resolution (`idIdx = -1`), silently omitting workout metadata enrichment and Strava upload ID registration. Additionally, `StravaActivityRow` parsed 9 unused summary telemetry columns already provided by `.fit`/`.tcx`/`.gpx` stream files.

A 3-tier hybrid resolution strategy was implemented: (1) Match multi-language header aliases; (2) Auto-detect stream filename and media column indices via regex pattern scanning (`^activities/.*`, `media/.*`); (3) Fall back to Strava canonical fixed column indices (`id=0`, `date=1`, `name=2`, `type=3`, `desc=4`, `commute=9`, `note=10`, `gear=11`, `filename=12`, `media=102`). Fixed column index fallbacks (Col 0: Name, Col 1: Brand, Col 2: Model) were added to `parseBikes()` and `parseShoes()`. The 9 dead summary metric properties were pruned from `StravaActivityRow`, and international sport types and truthy boolean strings were added to `mapSportType()` and `parseBoolean()`.

---

## 2. Key Architectural Changes
| File / Component | Summary of Changes |
| :--- | :--- |
| `StravaCsvParser.kt` | Implemented 3-tier hybrid column resolution (aliases -> regex pattern auto-detection -> canonical fixed indices), added gear item fallbacks (Col 0: Name, Col 1: Brand, Col 2: Model), pruned 9 dead summary metrics from `StravaActivityRow`, and expanded `mapSportType()` / `parseBoolean()` for international locales (`REQ-MIG-048.2`). |
| `StravaCsvParserTest.kt` | Added multi-locale contract tests for French, Spanish, and Japanese export layouts, shifted filename pattern auto-detection, and fixed column gear fallbacks (`TST-MIG-046.1`). |
| Living Documentation | Updated `docs/requirements/req_data_migration_export.md` (`REQ-MIG-048.2`) and `docs/tests/tst_data_migration_export.md` (`TST-MIG-046.1`) with Chesterton's Fence archaeology audit. |

---

## 3. Verification & Governance
* **Tier 1 Targeted Unit Tests**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.build.*"` -> `BUILD SUCCESSFUL` (100% pass rate across targeted migration tests).
* **AAPT2 Resource Compilation**: `./gradlew processDebugResources` -> `BUILD SUCCESSFUL`.
* **Requirement Governance**: `python3 tools/verify_requirement_governance.py` -> `PASS` (100% master table parity across all 9 shards, Chesterton's Fence archaeology audit verified).
* **Physical Device Deployment**: Pure backend CSV parser refactor (no UI layout, composable, or resource changes); verified via automated unit and contract test suites.

---

## 4. Requirement & Test Traceability
| Requirement ID | Test ID | Status | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-MIG-048.2` | `TST-MIG-046.1` | `Verified` | Unit Tests: `StravaCsvParserTest.testParseActivities_frenchSpanishJapanese_fixedIndicesAndPattern` |
| `REQ-MIG-048.2` | `TST-MIG-046.1` | `Verified` | Unit Tests: `StravaCsvParserTest.testParseActivities_shiftedFilenameColumn_patternDetection` |
| `REQ-MIG-048.2` | `TST-MIG-046.1` | `Verified` | Unit Tests: `StravaCsvParserTest.testParseBikesAndShoes_fixedIndicesFallback` |
| `REQ-MIG-048.2` | `TST-MIG-046.1` | `Verified` | Unit Tests: `StravaCsvParserTest.testMapSportType` (international types: FR, ES, IT, NL, PL, JA) |
| `REQ-MIG-048` | `TST-MIG-046` | `Verified` | Full Tier 1 targeted suite (`migration.*`, `build.*`) & governance verification (100% PASS) |
