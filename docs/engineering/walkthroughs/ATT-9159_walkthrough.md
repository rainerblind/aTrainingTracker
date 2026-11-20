# Walkthrough — ATT-9159: Persist Actual UTC Lap Start Timestamp in `TrackerService.saveLap`

- **Jira Ticket**: `ATT-9159` (`1 SP`, Bug, Epic `ATT-826: Laps`)
- **Living Specs**: `REQ-TRK-090` (`REQ-TRK-090.1`..`REQ-TRK-090.2`) & `TST-TRK-090` (`TST-TRK-090.1`..`TST-TRK-090.2`) — `[Verified]`
- **Code Commit (Gate 4 Certified)**: `45c3fd9c` on `feature/ATT-9159` (`Rule 7.5` doc-only Stage 5: `git diff 45c3fd9c..HEAD -- app/` is empty)
- **Localization**: `Localization Parity: N/A - no UI strings`

## 1. Root Cause & Implemented Solution
1. **Pre-Mutation Anchor Capture & Pause Neutrality (`MultisportLapCoordinator.kt`, `569` LoC, `REQ-TRK-090.1`)**:
   - Previously, `onLapSummaryIntent()` and `saveFinalLap()` only passed `timeStart` when `activeLegStartTimeUtc != null` (multisport transitions), falling through to `saveUnnamedLap` -> `TrackerService.saveLap` -> 5-arg `LapsDatabaseManager.saveLap(..., timeStart = null, ...)`, which caused SQLite `DATETIME DEFAULT CURRENT_TIMESTAMP` to record lap **end** time instead of lap **start** time.
   - `onLapSummaryIntent()` now captures `previousLapStartMs = currentLapStartAnchor.timestampMs` **before** `if (!isPause)` overwrites `currentLapStartAnchor`, setting `pendingUnnamedLapStartUtc` strictly after the `lapTime <= 0 && lapDistance <= 0.0` phantom guard while preserving `saveUnnamedLap` dispatch and clearing stale pending state in `resetForSession`, `planAndCommitAnchorTransition`, `saveFinalLap`, named-lap branches, and phantom early returns.
   - Added `@Synchronized fun consumeLapStartTimeUtc(lapTimeS: Int): String?` formatting UTC `"yyyy-MM-dd HH:mm:ss"` via `formatUtcTimestamp()` (preserving pause neutrality, in-process resume anchor, and `applyResumedState` post-process-death anchor at `TrackerService.java:1906`).
2. **`TrackerService.saveLap` & `LapsDatabaseManager.saveLap` UTC Fallback (`REQ-TRK-090.2`)**:
   - `TrackerService.java` (`3,539 / 3,559` LoC, `+1` LoC): 5-arg `saveLap` resolves `String timeStart = mMultisportLapCoordinator.consumeLapStartTimeUtc(lapTime);` and forwards to 8-arg `WorkoutRepository.saveLap(..., timeStart, ...)`, preserving 4-/5-arg signatures.
   - `LapsDatabaseManager.java` (`626` LoC): 8-arg `saveLap` computes UTC `now - lapTime * 1000L` via `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US).withZone(ZoneOffset.UTC)` whenever `timeStart == null && lapTime > 0` (zero `database -> tracker` dependency, preserving 5-/6-/8-arg signatures and `ATT-896` phantom rejection).
   - Detailed design `docs/architecture/detailed_design/tracker_service.md` synced (`1.0.3`, `3,539 LOC`); `0` edits to `WorkoutRepository.kt` (`4,931 / 5,117`), `DeviceManager.java`, or `DevicesDatabaseManager.java`.

## 2. Verified Test Evidence (`103/103 PASS` across 10 Targeted & Contract Suites @ Gate 4 `45c3fd9c`)
*Scope: Tier 1 targeted unit, exporter integration, and contract suites executed under `tools/parallel_sprint_lock.py` (`--rerun`) at Gate 4 (`ATT-9294`) with `0` new compiler warnings (`app/` unchanged since `45c3fd9c`).*

| Test Suite | Result | Key `TST-TRK-090` Verbatim Methods / Checks |
| :--- | :---: | :--- |
| `MultisportLapCoordinatorTest` | 7/7 PASS | `singleSportMultiLap_persistsPreMutationUtcTimeStart_withPauseNeutrality_underNonUtcTimezone_tstTrk090_1`, `emptyManualSplit_doesNotLeakPendingStartTimestamp_tstTrk090_1` |
| `LapsDatabaseManagerTest` | 11/11 PASS | `testSaveLap_nullTimeStartWithPositiveLapTime_fallsBackToUtcNowMinusLapTime_tstTrk090_2` (`Europe/Berlin` w/ `try/finally` TZ restore) |
| `TrackerServiceLapTest` | 7/7 PASS | `testTrackerServiceSaveLap_forwardsUtcStartTimestampFromCoordinator_tstTrk090_2` |
| `TrackerServiceFinalizationTest` | 29/29 PASS | Final-lap & snapshot finalization regression |
| `TrackerServiceResumeTest` | 18/18 PASS | Resume & anchor preservation regression |
| `TrackerServiceStateMachineCharacterisationTest` | 8/8 PASS | 4-/5-arg `saveLap` broadcast & state machine regression |
| `FileExportersIntegrationTest` | 3/3 PASS | FIT `LapMesg.startTime` UTC round-trip integration |
| `ArchitectureConformanceContractTest` | 11/11 PASS | `TrackerService.java = 3,539 / 3,559` LoC; `WorkoutRepository.kt = 4,931 / 5,117` |
| `DetailedDesignParityContractTest` | 3/3 PASS | `tracker_service.md` `1.0.3` parity |
| `SourceTextReadingAuditContractTest` | 6/6 PASS | `0` `src/main` file reads in tests |

- **Governance**: `verify_requirement_governance.py --sync-indexes`, `--base-ref refs/heads/sprint/2026-11-20_01_Früh`, and `--check-verified-evidence --test-results-dir /tmp/empty_check_ATT-9159_s5` all pass with **EXIT 0**.
