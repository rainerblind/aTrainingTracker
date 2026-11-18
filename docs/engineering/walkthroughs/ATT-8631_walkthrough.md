# Stage 5: Walkthrough & Verification - ATT-8631: SourceTextReadingAuditContractTest Remediation

**Ticket**: [ATT-8631](https://rainerblind.atlassian.net/browse/ATT-8631)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11 – Evidence Integrity, Effective Gates & Verification to CL2*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-17_03_Nacht` | **Branch**: `feature/ATT-8631`  
**Requirement Mapping**: `REQ-PRO-126` (`REQ-PRO-126.1`–`.4`) | **Test Mapping**: `TST-PRO-126` (`TST-PRO-126.1`–`.4`)  
**Localization parity**: `N/A - no UI strings` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-18

---

## 1. Executive Summary & Verification Overview
Resolved `SourceTextReadingAuditContractTest.testSourceReadingAudit_noUnallowlistedSourceReaders` (`REQ-PRO-049.1`) without expanding `LEGITIMATE_GOVERNANCE_GUARDS` (`22`) or `app/source-reading-baseline.txt` (`105`):
1. Confirmed `AdvancedTuningZoneDisplayTest.kt` was already deleted upstream in `ATT-8498` (`d7dec447`), with behavioral tests (`EditSensorFieldZoneDisplayTest` `6/6`, `TrackingTabConfigZoneDisplayTest` `3/3`) passing cleanly.
2. Refactored `SensorTypeUnitsContractTest.kt` (`REQ-PRO-126.1`) to remove `SensorType.java` path probing, `readText()`, and `parsePrecedingJavadocs()`, keeping `allSensorTypes_haveValidInternalUnit()` (`SensorType.values()` JVM reflection) and `sensorUnitsDoc_matchesAllSensorTypesBidirectionally_andArchitectureLinksIt()` (`2/2` pass).
3. Added `_parse_sensor_type_constants()`, `_parse_sensor_units_doc_table()`, `verify_sensor_type_javadocs()`, and `--check-sensor-javadocs` to `tools/verify_requirement_governance.py` (`REQ-PRO-126.2`, verifying all `67/67` `SensorType.java` constants and `docs/architecture/sensor_units.md`), plus `TestSensorTypeJavadocVerification` (`4/4` tests) in `tools/test_verify_requirement_governance.py` (`REQ-PRO-126.4`).

---

## 2. Requirement & Test Verification Matrix
| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-126.1`, `REQ-PRO-126.3` | `TST-PRO-126.1` | `SourceTextReadingAuditContractTest` (`--rerun-tasks`) | **PASSED (`4/4`)** | `Verified` |
| `REQ-PRO-126.1`, `REQ-TRK-083.1` | `TST-PRO-126.2` | `SensorTypeUnitsContractTest` (`--rerun-tasks`) | **PASSED (`2/2`)** | `Verified` |
| `REQ-PRO-126.2`, `REQ-PRO-126.4` | `TST-PRO-126.3` | `python3 -m unittest tools.test_verify_requirement_governance` | **PASSED (`97/97`)** | `Verified` |
| `REQ-PRO-126.2`, `REQ-PRO-126.4` | `TST-PRO-126.4` | `tools/verify_requirement_governance.py` (`--check-sensor-javadocs` + full) | **PASSED (`exit 0`, `67/67`)** | `Verified` |

---

## 3. Automated Test Evidence
* **Zero Code Drift (`ATT-8030`)**: Stage 4 code SHA `656547dd` verified unmodified in Stage 5 (only walkthrough and living-doc status promotions added).
* **Tier-1 Targeted Gradle Suite** (`./gradlew :app:testDebugUnitTest --rerun-tasks --max-workers=2` under `parallel_sprint_lock.py`, `BUILD SUCCESSFUL in 5m 47s`, `21/21` passed, `0` failed, `0` skipped):
  * `SourceTextReadingAuditContractTest`: `4/4` PASS (`22` guards, `105` baseline, `0` unallowlisted, `0` stale)
  * `SensorTypeUnitsContractTest`: `2/2` PASS
  * `EditSensorFieldZoneDisplayTest`: `6/6` PASS
  * `TrackingTabConfigZoneDisplayTest`: `3/3` PASS
  * `DetailedDesignParityContractTest`: `3/3` PASS
  * `InterfaceSignatureDriftContractTest`: `3/3` PASS
* **Offline Python & Governance Gates**:
  * `python3 -m unittest tools.test_verify_requirement_governance`: `97/97` OK (`67.231s`, incl. `TestSensorTypeJavadocVerification` `4/4`)
  * `python3 tools/verify_requirement_governance.py --check-doc-headers --check-commit-hygiene --check-conflict-markers --check-sensor-javadocs`: `exit 0` (`67/67` `SensorType` constants verified)
  * `python3 tools/verify_requirement_governance.py`: `exit 0`

---

## 4. Hardware / Physical Verification
Pure test/governance tooling change (`app/src/main/` untouched); zero APK runtime bytecode changes; `Localization parity: N/A - no UI strings`; verified via Tier-1 Gradle contract suite (`21/21`) and Python governance suite (`97/97`).

---

## 5. Invariant & Governance Verification
1. **Zero Production Code Changes**: `SensorType.java` `ordinal()`, `name()`, `getInternalUnit()`, and runtime behavior untouched.
2. **Audit Guard & Baseline Integrity**: `LEGITIMATE_GOVERNANCE_GUARDS == 22` and `app/source-reading-baseline.txt == 105` preserved without inflation.
3. **Living Documentation Synchronized**: `REQ-PRO-126` & `TST-PRO-126` promoted to `[Verified]` in `docs/requirements/req_process_stability.md` & `docs/tests/tst_process_stability.md` (`v1.0.15`), master indexes synced (`--sync-indexes`), `feature/ATT-8631` preserved intact for Stage 6 Cold Review before atomic sprint merge, and parent `ATT-8631` ends at `Final Review (Human)`.
