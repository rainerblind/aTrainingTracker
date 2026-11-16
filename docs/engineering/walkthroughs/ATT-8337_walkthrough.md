# Stage 5: Walkthrough & Verification — ATT-8337: Post-Workout ERTS Smoother GNSS Fix Gating & Origin Anchoring

**Ticket**: [ATT-8337](https://rainerblind.atlassian.net/browse/ATT-8337)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-47.2` | **Branch**: `feature/ATT-8337`  
**Requirement Mapping**: `REQ-FIL-019` (`REQ-FIL-019.1`..`REQ-FIL-019.4`)  
**Test Mapping**: `TST-FIL-019` (`TST-FIL-019.1`..`TST-FIL-019.5`)  
**Author**: AI Agent 1 (Implementer, Lane 1 Flash) | **Date**: 2026-11-16  

---

## 1. Executive Summary & Verification Overview
On workouts recorded before GNSS satellite lock is acquired, leading samples have `LATITUDE = NULL, LONGITUDE = NULL`. `ErtsPostWorkoutRecomputer.kt` coerced `null` to `0.0`, causing `ExtendedRtsOdometrySmoother.kt` to anchor its reference origin at Null Island `(0.0, 0.0)` in the Gulf of Guinea and update the forward EKF with `(0.0, 0.0)`. When the first genuine fix arrived (~5,500 km away), a 477 km/s velocity shock corrupted SQLite samples, summaries, exports, period summaries, and route clusters.
* **Fix**:
  1. `RawOdometrySample` updated to declare `latitude: Double? = null` and `longitude: Double? = null`.
  2. `hasValidGnssFix()` validates non-null, finite coordinates within geographic bounds, strictly rejecting `(0.0, 0.0)`.
  3. `ExtendedRtsOdometrySmoother.smoothSamples()` anchors `refLat`/`refLon` strictly to the first valid GNSS fix. Forward `ekf.updateGnssPosition()` is gated strictly on valid fixes; pre-fix/gap samples execute prediction and wheel/yaw speed updates only.
  4. `ErtsPostWorkoutRecomputer.kt` preserves SQLite `NULL` on no-fix rows via `stmt.bindNull()`, excludes no-fix rows and `(0.0, 0.0)` from `mapPolyline` and start/end extrema, and executes `isSmoothedOutputSane()` to abort transaction and preserve live tracking data on runaway divergence.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Living Doc Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-FIL-019.1` (*Nullable Coordinates*) | `TST-FIL-019.1`, `TST-FIL-019.3` | Unit Test | **PASSED** | `[Verified]` |
| `REQ-FIL-019.2` (*First Fix Origin & Gating*) | `TST-FIL-019.1`, `TST-FIL-019.2` | Unit Test | **PASSED** | `[Verified]` |
| `REQ-FIL-019.3` (*No-Fix Row DB Integrity*) | `TST-FIL-019.3` | Unit Test | **PASSED** | `[Verified]` |
| `REQ-FIL-019.4` (*Output Sanity Guard*) | `TST-FIL-019.4` | Unit Test | **PASSED** | `[Verified]` |
| `REQ-FIL-017` / Invariants | `TST-FIL-019.5` | Targeted Regression & Governance | **PASSED** | `[Verified]` |

---

## 3. Automated Test Evidence

### Targeted Unit & Regression Test Scope
* **`ExtendedRtsOdometrySmootherTest.kt`**:
  * `testLeadingNullGnssFixes_anchorsToFirstValidFixWithoutNullIslandShock` (`// TST-FIL-019.1`): PASSED.
  * `testMidWorkoutGnssGap_bridgesSmoothlyWithoutNullIslandPull` (`// TST-FIL-019.2`): PASSED.
  * All existing simulation & 14,400-sample benchmark tests: PASSED.
* **`ErtsPostWorkoutRecomputerTest.kt`**:
  * `testLeadingNullCoordinatesPreservedInDbAndExcludedFromPolyline` (`// TST-FIL-019.3`): PASSED.
  * `testOutputSanityGuardAbortsCorruptedOutputAndPreservesLiveData` (`// TST-FIL-019.4`): PASSED.
  * `testStrictColumnScopeInvariantAndRecomputation`: PASSED.
* **`WorkoutSamplesDatabaseManagerTest`**: 100% PASSED.

---

## 4. Verification & Clean-Room Governance Audit
* `python3 tools/verify_requirement_governance.py`: **PASS** (100% living doc parity, 0 ungrounded requirements, 0 code orphans).
* `python3 tools/verify_requirement_governance.py --check-code-orphans`: **PASS** (0 code-to-shard orphans).
* `python3 tools/verify_requirement_governance.py --check-req-description`: **PASS** (100% description coverage).
* `python3 tools/verify_requirement_governance.py --verify-sys-trace --check-reverse-sys-trace --report-sys-coverage`: **PASS** (100% bidirectional SYSREQ-TRK-003 / SYSREQ-TRK-004 traceability).
* `python3 tools/verify_requirement_governance.py --check-verified-evidence`: **PASS** (100% test evidence verified).
* `ANT-Android-SDKs` submodule: clean & untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.
