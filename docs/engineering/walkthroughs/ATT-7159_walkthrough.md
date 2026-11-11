# Stage 5: Walkthrough & Verification - ATT-7159: Reject Altitude Spikes, Validate Workout Climbs via DEM & Clean Climbs.db

**Ticket**: [ATT-7159](https://rainerblind.atlassian.net/browse/ATT-7159)  
**Sub-task**: `[Test]`  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3`  
**Requirement Mapping**: `REQ-CLM-009` (*Climb Detection Spike Guards, DEM Cross-Validation & Corrupted Climbs Self-Healing*)  
**Test Mapping**: `TST-CLM-009` (*Climb Detection Spike Guards, DEM Ground-Truth Cross-Validation & Self-Healing Verification Suite*)  
**Branch**: `bugfix/ATT-7159` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Executive Summary & Verification Overview
Resolved corrupted climbs in `Climbs.db` caused by GPS cold-start altitude jumps, extreme gradients, and cold-air barometer thermal drift. Added physical plausibility step jump and gradient ceiling checks to `ClimbDetector`, cross-validated workout climbs against DEM ground truth (`ElevationCacheDatabaseManager`) in `WorkoutRepository`, and implemented a one-time idempotent cleanup migration (`PREF_CLIMBS_WORKOUT_CLEANUP_V2`) purging corrupted entries and refreshing `LiveClimbsRepository`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CLM-009.1` | `TST-CLM-009.1` | Unit Test (`testDetectClimbs_rejectsAltitudeStepSpikesAndExtremeGradients`) | **PASSED** | `Verified` |
| `REQ-CLM-009.2` | `TST-CLM-009.2` | Unit Test (`testWorkoutClimbIngestion_rejectsBarometerThermalDriftViaDemGroundTruth`) | **PASSED** | `Verified` |
| `REQ-CLM-009.3` | `TST-CLM-009.3` | Unit Test (`testCleanupCorruptedWorkoutClimbs_purgesCorruptedEntriesAndSetsFlag`) | **PASSED** | `Verified` |
| `REQ-CLM-009` | `TST-CLM-009.4` | Tier 1 Targeted Climbs & Aftermath Suite (`testDebugUnitTest`) | **PASSED** | `Verified` |
| `REQ-PRO-022` | `TST-PRO-015` | Living Docs Governance (`verify_requirement_governance.py`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope (`climbs.*`, `ui.aftermath.*`)
```text
BUILD SUCCESSFUL
36 actionable tests completed, 0 failed.
```

### Living Requirements Governance Verification
```text
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: Zero unmounted Composable islands detected in git diff.
PASS: Repository encapsulation verified (zero leaky DatabaseManager/Cursor members).
PASS: Zero architecture or system specification modifications detected.
```

---

## 4. Hardware / Physical Verification (Pixel 10)
Pure domain repository and detection logic fix in `ClimbDetector.kt` and `WorkoutRepository.kt`; zero UI composable or layout alterations; verified via automated unit and regression test suite.

---

## 5. Invariant & Governance Verification
1. **Physical Plausibility Enforced**: Vertical jumps $> 25\text{m}$ in $\le 50\text{m}$, average grade $> 25\%$, and calibration snap drops $> 35\text{m}$ within $< 50\text{m}$ strictly rejected.
2. **DEM Ground-Truth Validation**: Barometric drift exceeding DEM threshold or tracks with DEM gain $< 15\text{m}$ or grade $< 2.2\%$ rejected.
3. **Self-Healing Cleanup**: Existing corrupted climbs purged idempotently via `PREF_CLIMBS_WORKOUT_CLEANUP_V2`.
4. **Living Docs Updated**: `REQ-CLM-009` and `TST-CLM-009` synchronized as `Verified`.
