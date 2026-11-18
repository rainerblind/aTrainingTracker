# Stage 1 Analysis: ATT-8631 - SourceTextReadingAuditContractTest Failure on SensorTypeUnitsContractTest

**Ticket**: [ATT-8631](https://rainerblind.atlassian.net/browse/ATT-8631)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11 – Evidence Integrity, Effective Gates & Verification to CL2*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_03_Nacht` | **Branch**: `feature/ATT-8631`  
**Author**: AI Agent 1 (Implementer, `--as agent1`) | **Date**: 2026-11-18

---

## 1. Problem Statement & Motivation
`SourceTextReadingAuditContractTest.testSourceReadingAudit_noUnallowlistedSourceReaders` fails on `sprint/2026-11-17_03_Nacht` (`dace03f5`). Ticket `ATT-8631` cites two unallowlisted test files reading `app/src/main/`: `AdvancedTuningZoneDisplayTest.kt:310` (`CockpitTypographySection.kt`) and `SensorTypeUnitsContractTest.kt:50,138,140` (`SensorType.java`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * **Hypothesis 1 (`AdvancedTuningZoneDisplayTest.kt`) — Already Resolved Upstream**: `ATT-8498` (`81af2506`) originally added `AdvancedTuningZoneDisplayTest.kt` (`316` LOC), which read `CockpitTypographySection.kt`. A subsequent `ATT-8498` commit (`d7dec447`) deleted `AdvancedTuningZoneDisplayTest.kt` and `CockpitZoneDisplaySubsection.kt`, restored `CockpitTypographySection.kt` to `367` LOC (`< 400`, `REQ-UI-277.3`), and moved behavioral tests into `EditSensorFieldZoneDisplayTest.kt` and `TrackingTabConfigZoneDisplayTest.kt` (zero `src/main` reads).
  * **Hypothesis 2 (`SensorTypeUnitsContractTest.kt`) — Confirmed Live Defect**: Introduced in `ATT-8579` (`367a412f`, `REQ-TRK-083`). `findRepoRoot()` (`:50`) probes `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorType.java`, and `allSensorTypes_haveValidInternalUnitAndPrecedingJavadoc()` (`:138,140`) invokes `sensorTypeFile.readText()` for `parsePrecedingJavadocs()` (`:87-131`), triggering `SourceTextReadingAuditContractTest.scanTestSourceFiles()` (`:101-110`). Meanwhile `sensorUnitsDoc_matchesAllSensorTypesBidirectionally_andArchitectureLinksIt()` (`:162-222`) uses only `SensorType.values()` reflection and `docs/architecture/*.md`.
  * **Repo-Wide Scan (`app/src/test/java`)**: Scanning all `.kt`/`.java` tests with `SourceTextReadingAuditContractTest` rules yields `127` source readers (`22` `LEGITIMATE_GOVERNANCE_GUARDS` + `105` `app/source-reading-baseline.txt`, `0` stale): **`SensorTypeUnitsContractTest.kt` is the sole unallowlisted reader** (all other recent `src/main` references target `src/main/res/` XML DOM parsing).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Remove all `app/src/main/` file references and `readText()` from `SensorTypeUnitsContractTest.kt` while keeping JVM reflection (`SensorType.values()`, `getInternalUnit()`) and `docs/architecture/sensor_units.md` / `docs/architecture.md` parity checks.
  2. Migrate the static `SensorType.java` per-constant Javadoc check (`REQ-TRK-083.2`, `"Internal unit:"`) into `tools/verify_requirement_governance.py` with offline Python unit tests in `tools/test_verify_requirement_governance.py`.
  3. Synchronize `REQ-TRK-083` / `TST-TRK-083` and verify `SourceTextReadingAuditContractTest` passes `4/4`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Do NOT expand `LEGITIMATE_GOVERNANCE_GUARDS` (`22`) or `app/source-reading-baseline.txt` (`105`), alter `SensorType.java` runtime behavior/`ordinal()`/`name()`, or clean up the `105` legacy baseline tests (`1 SP` confirmed; no split).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-083` (`REQ-TRK-083.2`, `REQ-TRK-083.3`) & `TST-TRK-083` (`TST-TRK-083.1`) in `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md`; preserves `REQ-PRO-049` (`REQ-PRO-049.1`–`REQ-PRO-049.3`).
* **Historical Origin & Commit Trace**: `REQ-PRO-049` created in `ATT-6186` (`c91e62b6`), hardened in `ATT-6731` (`6ec52d7b`, `22` guards) and `ATT-7525` (`97133154`, `105` baseline); `REQ-TRK-083` / `SensorTypeUnitsContractTest.kt` added in `ATT-8579` (`367a412f`).
* **Root Reason for Existing Formulation**: `ATT-8579` verified `SensorType.java` Javadoc presence and `"Internal unit:"` strings in the same JUnit file as `SensorType.values()` reflection without accounting for `REQ-PRO-049.1`'s ban on unallowlisted `src/main` reads.
* **Preservation of Core Invariants**: Keeps `REQ-PRO-049.2` cap (`22` guards) and baseline (`105`) untouched; retains all `SensorType.values()` & `sensor_units.md` runtime/doc parity assertions in `SensorTypeUnitsContractTest.kt`; preserves continuous Javadoc verification in `tools/verify_requirement_governance.py`.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-TRK-083.2`, `REQ-TRK-083.3`, and `TST-TRK-083.1`; `REQ-PRO-049` remains unchanged.
* **Affected Architecture Elements**: `app/src/test/java/com/atrainingtracker/banalservice/sensor/SensorTypeUnitsContractTest.kt`, `tools/verify_requirement_governance.py`, `tools/test_verify_requirement_governance.py`, `docs/requirements/req_tracking_cockpit.md`, `docs/tests/tst_tracking_cockpit.md`.
* **Verification Impact & Test Surface**: `SourceTextReadingAuditContractTest` (`4/4`), `SensorTypeUnitsContractTest` (`2/2`), `python3 tools/verify_requirement_governance.py`, `python3 -m unittest tools/test_verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: **LOW** risk; zero `app/src/main/` production edits, zero SQLite/UI/strings edits, zero `LEGITIMATE_GOVERNANCE_GUARDS` (`22`) or baseline (`105`) modifications.

---

## 6. Architectural Strategy & High-Level Solution
1. **`SensorTypeUnitsContractTest.kt`**: Remove `SensorType.java` path probe (`:50`), `sensorTypeFile.readText()` (`:138,140`), and `parsePrecedingJavadocs()` (`:87-131`); keep `allSensorTypes_haveValidInternalUnit()` and `sensorUnitsDoc_matchesAllSensorTypesBidirectionally_andArchitectureLinksIt()` strictly on JVM reflection + `docs/architecture/`.
2. **`tools/verify_requirement_governance.py`**: Add `verify_sensor_type_javadocs()` invoked by `verify_governance()` to verify every `SensorType.java` enum constant has a preceding `/** ... */` Javadoc containing `"Internal unit:"` and its declared `internalUnit` (cross-checked with `docs/architecture/sensor_units.md`), plus isolated `tempfile` tests in `tools/test_verify_requirement_governance.py`.
3. **Living Docs & Verification**: Update `REQ-TRK-083` / `TST-TRK-083` (`ATT-8631`) with Chesterton's Fence audit and verify `SourceTextReadingAuditContractTest` passes `4/4`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. `SensorType.java` `ordinal()`, `name()`, `getInternalUnit()`, and runtime behavior remain untouched (`N/A - no UI strings`).
  2. `SourceTextReadingAuditContractTest` invariants (`22` guards, `105` baseline, `0` stale) remain strictly intact.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** (`1 SP` test & governance script refactoring; zero production source modifications).
