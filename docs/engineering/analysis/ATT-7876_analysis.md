# Stage 1 Analysis: ATT-7876 - Post-Crash Barometric Calibration Guard & Case B Average Consistency

**Ticket**: [ATT-7876](https://rainerblind.atlassian.net/browse/ATT-7876)  
**Sub-task**: Stage 1 (`[Analysis]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.7`  
**Branch**: `feature/ATT-7876`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-15

---

## 1. Problem Statement & Motivation

Forensic inspection of workout `_id = 2176` (`2026-11-14_084021`) and attached `elevation_profile_screenshot.png` (ID `11288`, showing pre-crash ~1.0 km segment shifted up to ~510 m before dropping back to ~435–448 m) revealed three compounding defects when `ALTITUDE_CORRECTION_INTENT` fires:
1. Pre-crash samples (rows `1..641`, calibrated at 438–448 m) were shifted up by `+62.37 m` to 500–510 m after `RESUME_SERVICE_RECREATION` (`row 642`) because `AltitudeFromPressureDevice.mLastCalibrationTimeMs == -1L` triggered an unbounded Case A shift across the entire table.
2. Non-barometric columns (`ALTITUDE (Topografische Höhe (DEM))`, `ALTITUDE (GPS)`, `ALTITUDE (Google Fused)`) and DEM-sourced canonical `ALTITUDE` rows were shifted by the barometric delta offset.
3. `LiveWorkoutSession` Case B (`t0Ms > 0`) summed 20-second `sampledAltitudes` into 1-Hz `altStats.sum` without adjusting `altStats.count`, dividing `AVG ALTITUDE` by ~15–20x.

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification** (verified live on `sprint/2026-46.7`):
  * **Column Over-Matching** (`WorkoutSamplesDatabaseManager.java:758-784`): `col.startsWith("ALTITUDE (") && col.endsWith(")")` matches DEM, GPS, Google Fused, and ATT Fused columns indiscriminately. Canonical `ALTITUDE` is shifted unconditionally even when a higher-priority source (`MySensorManager.ALTITUDE_PRIORITY` places `ALTITUDE_AND_SLOPE_ATT_FUSED` and `ALTITUDE_FROM_POSITION` above `ALTITUDE_FROM_PRESSURE`) supplied the row.
  * **Unbounded Case A** (`WorkoutSamplesDatabaseManager.java:778-783`): When `t0Ms <= 0`, SQL updates `WHERE quotedCol IS NOT NULL` with zero lower bound (`TIME >= sessionEpochStartTimeMs`) and zero upper bound (`TIME <= t1Ms`). Furthermore, `LiveWorkoutSession.rehydrateStreams()` (`lines 150-157`) leaves `sampledAltitudeTimestamps` unaligned with rehydrated `sampledAltitudes` (`0` vs `N_pre` entries), and `WorkoutSummariesDatabaseManager.shiftAltitudeData` (`lines 1458-1492`) also uniformly shifts pre-crash `ALTITUDE_STREAM` and extrema.
  * **Dimensional Mismatch in `LiveWorkoutSession` Case B** (`LiveWorkoutSession.java:383-400`): Recomputes `altStats.sum` over `sampledAltitudes` (every `ENCODING_STEP_SIZE = 20` ticks) while keeping `altStats.count` at 1-Hz sample count.

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**: Restrict SQL retroactive barometric shifts to barometric pressure columns + barometric-sourced canonical `ALTITUDE` rows; bound Case A shifts to `[sessionEpochStartTimeMs, t1Ms]` preserved cleanly on `RESUME_SERVICE_RECREATION`; keep `LiveWorkoutSession` Case B `altStats.sum`/`count` dimensionally consistent; keep `TrackerService.java` edits surgical (concurrent `ATT-7924` work).
* **Out-of-Scope Non-Goals**: Re-engineering `BarometricAltitudeFilter`, ERTS smoothing (`REQ-FIL-017`), or changing `WorkoutSamples` DB schema (`DB_VERSION`).

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new `REQ-FIL-018` extending `REQ-CON-017` (500 m sanity guard), `REQ-TRK-047.2` (Case A/Case B drift correction), and `REQ-TRK-057.3` (sourced-column sync across `WorkoutSamplesDatabaseManager.java`, `LiveWorkoutSession.java`, `TrackerService.java`).
* **Historical Origin & Commit Trace**: `ATT-4845` (`REQ-TRK-047`) introduced piecewise-linear drift correction; `ATT-6265` (`e079f7a9`, `REQ-TRK-057.3`) added `"ALTITUDE (*)"` wildcard discovery to keep `"ALTITUDE (Höhe aus Luftdruck)"` in sync with canonical `ALTITUDE`.
* **Root Reason for Existing Formulation**: Wildcard `ALTITUDE (*)` assumed barometer was the sole named altitude source before `ATT-4859` (DEM), `ATT-6279` (ATT Fused), and GPS/Fused elevation columns coexisted in `WorkoutSamples.db`; crash recovery epoch boundaries and 1-Hz vs 20-s stat cadence were unmodeled.
* **Preservation of Core Invariants**: $|\Delta h| \le 500\text{ m}$ (`REQ-CON-017`), Case B linear interpolation over `[t0, t1]` (`REQ-TRK-047`), barometric column sync (`REQ-TRK-057.3`), passive `onOpen()`, single-thread `mDbExecutor` dispatch, and `ANT-Android-SDKs` gitlink (`0e8cea714a`) stay untouched.

## 5. Change-Request Impact Analysis (SUP.10)

* **Affected Requirements**: Net-new `REQ-FIL-018` (`docs/requirements/req_tracking_cockpit.md`), preserving `REQ-CON-017`, `REQ-TRK-047`, `REQ-TRK-057`.
* **Affected Architecture Elements**: `WorkoutSamplesDatabaseManager`, `WorkoutSummariesDatabaseManager`, `WorkoutRepository`, `LiveWorkoutSession`, `TrackerService` (surgical epoch tracking).
* **Verification Impact & Test Surface**: `TST-FIL-018` covering `WorkoutSamplesDatabaseManagerTest`, `LiveWorkoutSessionAltitudeDriftTest`, `TrackerServiceCrashRecoveryCharacterisationTest`, and `AspiceSweAuditContractTest` / `SourceTextReadingAuditContractTest`.
* **Risk Assessment & Invariants**: Medium risk (SQLite time timezone/rounding on `CURRENT_TIMESTAMP` UTC seconds vs epoch ms, existing tests passing `t1Ms = 1000L` placeholders). Mitigated via integer-second floor on `sessionEpochStartTimeMs`, explicit barometric column allow-listing across all 9 locales + `DevicesDatabaseManager`, and strict unit verification.

## 6. Architectural Strategy & High-Level Solution

1. Resolve barometric columns in `WorkoutSamplesDatabaseManager` via `DevicesDatabaseManager` (`DeviceType.ALTITUDE_FROM_PRESSURE`) plus current/9-locale/legacy pressure names; exclude DEM, GPS, Google Fused, ATT Fused. Shift canonical `ALTITUDE` first, strictly `WHERE baroCol IS NOT NULL AND ABS(ALTITUDE - baroCol) < 0.01`, before shifting `baroCol`.
2. Pass `sessionEpochStartTimeMs` from `TrackerService` (`START_NORMAL`, `RESUME_SERVICE_RECREATION`, process restart) through `WorkoutRepository` and `LiveWorkoutSession`. Bound Case A SQL to `TIME >= floor(sessionEpochStartTimeMs/1000)` (if `> 0`) and `TIME <= t1Ms/1000.0` (if `> 0`); protect rehydrated pre-crash points/extrema in `LiveWorkoutSession` and `WorkoutSummariesDatabaseManager`.
3. In `LiveWorkoutSession` Case B, add `meanCorrection * altStats.count` to `altStats.sum` (leaving 1-Hz `altStats.count` intact) and align `sampledAltitudeTimestamps` with `0L` sentinels on `rehydrateStreams`.

## 7. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero mutation of `ALTITUDE (GPS)`, `ALTITUDE (Google Fused)`, `ALTITUDE (Topografische Höhe (DEM))`, `ALTITUDE (ATT Fused ...)`.
  2. Single-transaction atomic SQLite updates on `mDbExecutor`; 0 `onOpen()` mutations.
  3. Parent ticket Human Decision Gate preserved (`Final Review (Human)`).
* **Risk Rating**: **MEDIUM** — touches SQLite retroactive calibration SQL and resume lifecycle telemetry; mitigated by targeted Robolectric unit tests across single-session, DEM-priority, and post-crash scenarios.
