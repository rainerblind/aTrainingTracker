# Stage 5: Walkthrough & Verification - ATT-8001: Swim Mode Backfill & Aftermath Adjustment

**Ticket**: [ATT-8001](https://rainerblind.atlassian.net/browse/ATT-8001)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0`  
**Requirement Mapping**: `REQ-SET-111` (`REQ-SET-111.1`..`REQ-SET-111.4`) [Verified]  
**Test Mapping**: `TST-SET-113` (`TST-SET-113.1`..`TST-SET-113.5`) [Verified]  
**Stage 4 Commit SHA**: `7983419f690e3b5a77b3669548bc6d746f86af73` (healed from Stage 6 Cold Carbon review)  
**Branch**: `feature/ATT-8001`  
**Author**: AI Agent 2 (Verifier / Subagent Flash)  
**Date**: 2026-11-16  

---

## 1. Executive Summary & Verification Overview
Stage 5 clean-room verification for ATT-8001 completed. Verified Polar Verity Sense Swim Mode backfill and Aftermath adjustment including Stage 6 Cold Carbon review healing:
1. **Auto Swim SportType Assignment**: `SportTypesRepository.ensureSwimSportTypeExists()` idempotently provisions the Swimming sport type during swim session backfill, assigning `BSportType.SWIMMING` and its `sportId` to the workout to avoid Walking heuristics.
2. **Pool-Length Distance & Lap Backfill**: Backfills 1-Hz cumulative `DISTANCE_m`, `SPEED_mps`, and `LAP_NR` samples via `WorkoutSamplesDatabaseManager.patchSwimKinematics()` alongside discrete lap persistence in `Laps.db` via `LapsDatabaseManager.saveLap()` (enforcing the `ATT-896` non-zero lap duration/distance guard).
3. **Summary & Export Gating**: Recomputes workout total distance, average speed, and HR zone times in `WorkoutSummaries.db` before releasing held automatic exports (`ExportManager`), keeping `WorkoutRr.db` untouched.
4. **Aftermath Quick Adjustment**: `SwimWorkoutAdjustCard` mounted in `WorkoutDetailTabbedContent.kt` allows switching pool length presets (25m, 50m, 25yd) and adjusting total lengths (+1 / -1), recalculating metrics in SQLite and invalidating export caches across all 9 UI locales without emojis.
5. **Stage 6 Cold Carbon Healing**:
   - Cleaned git merge conflict markers in `docs/requirements.md` and `docs/tests.md`.
   - Filtered `validTurns` in `PolarVeritySenseOfflineController.kt` before `lengthCount` computation to eliminate phantom 0s/1s laps when turns occur $\le$ `anchorMs`.
   - Added table existence guard in `WorkoutSamplesDatabaseManager.java:applySwimKinematicsIntervals` (`baseFileName == null || !existsTable(baseFileName) || intervals.isEmpty()`).
   - Fixed Compose state caching in `WorkoutDetailTabbedContent.kt` to observe `workoutData.laps` directly (fallback keyed on `id, totalDistance`, using `WorkoutRepository.getInstance(currentContext)`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-SET-111.1` | `TST-SET-113.1` | Unit (Robolectric / MockK) | PASSED | `Verified` |
| `REQ-SET-111.2` | `TST-SET-113.2`, `TST-SET-113.3` | Unit (SQLite / Manager) | PASSED | `Verified` |
| `REQ-SET-111.3` | `TST-SET-113.4` | Unit & On-Device UI | PASSED | `Verified` |
| `REQ-SET-111.4` | `TST-SET-113.5` | Localization Contract Test | PASSED | `Verified` |

---

## 3. Automated Test Evidence
Per Rule 7.5 (Zero Redundant Stage 5 Re-Runs), test evidence established on `7983419f`:
- **Targeted Unit Tests (PolarVeritySenseOfflineControllerTest)**: 22/22 passing (`~12s`), including `testApplySwimBackfillAndCommit_filtersUnanchoredTurnsSafely`.
- **Targeted Database Tests (WorkoutSamplesDatabaseManagerTest)**: 8/8 passing, including `testPatchSwimKinematics_nonExistentTable_returnsSafelyWithoutException`.
- **Localization Parity**: `TranslationParityTest` — 10/10 passing (9-locale parity across `strings_swimming.xml` with zero emojis).
- **On-Device UI Verification**: `SwimWorkoutAdjustCardAndroidTest` — 1/1 passing on `emulator-5554` (`ATT-8001_swim_adjust_card.png`).
- **Total Targeted Tests**: 40/40 passing cleanly.
- **Living Document Governance**: `python3 tools/verify_requirement_governance.py --sync-indexes` passed with 100% parity (Total: 1156 REQs, 1158 TSTs, 0 orphans, 0 conflict markers).

---

## 4. Visual Inspection Evidence (Rule 8.5)

| Visual Asset | Description | Inspection Result |
| :--- | :--- | :--- |
| `docs/engineering/walkthroughs/ATT-8001_swim_adjust_card.png` | SwimWorkoutAdjustCard in Workout Details with 25m/50m/25yd segmented pills and +/- length stepper controls on `emulator-5554` | VERIFIED (read_file) |

- **Orientation & Geometry**: Proper 16dp rounded card shape, balanced internal padding, clean alignment between labels and controls, zero clipping or overlap.
- **Color Mapping & M3 Contrast**: Card surface uses elevated white, segmented button uses primary container tint for active preset, high-contrast text, tonal icon buttons for stepper.
- **Typography & Wrapping**: Single-line German labels ("Schwimm-Einstellungen", "Bahnlänge", "Bahnen", "25 m", "50 m", "25 yd", "32") wrap cleanly without truncation.
- **Touch Ergonomics**: Segmented buttons and circular stepper buttons provide generous touch targets ($\ge 48\text{dp}$ height) with comfortable finger clearance.
