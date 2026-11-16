# Stage 1 Analysis: ATT-8001 - Polar Verity Sense Swim Mode Backfill & Aftermath Adjustment

**Ticket**: [ATT-8001](https://rainerblind.atlassian.net/browse/ATT-8001)  
**Sub-task**: [Analysis] (`ATT-8001`)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.8`  
**Branch**: `feature/ATT-8001`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-16  

---

## 1. Problem Statement & Motivation
ATT-8000 successfully extracts 1-Hz HR and gyroscope wall-turn timestamps from Polar Verity Sense White Swimming Mode sessions.
However, current backfill only patches missing HR samples. It leaves the workout classified as default/walking heuristics,
omits pool-length distance and lap records, and lacks an Aftermath adjustment UI for swimmers to correct missed/phantom turns or pool length.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Codebase Analysis**:
  * `PolarVeritySenseOfflineController.applySwimBackfillAndCommit()` only invokes `patchMissingHeartRateSamples()`.
  * `SportTypesRepository.ensureSwimSportTypeExists()` (ATT-7999) is ready but uncalled during backfill, causing default `WALK`/`OTHER` sport selection.
  * `LapsDatabaseManager.saveLap()` enforces ATT-896 non-zero lap guards but receives no swim lengths from the controller.
  * `WorkoutSamplesDatabaseManager` currently lacks swim distance/speed/lap-nr sample backfill for offline sessions.
* **Independent Peer Review & Verification**:
  * Verified ATT-8000 `VeritySwimSession` already delivers `turnTimestampsMs` decoupled from sensor pool length.
  * Verified `TuningPreferencesDataStore.defaultPoolLengthMeters` provides user pool length (`25m`/`50m`/`22.86m`/custom).
  * Verified Aftermath tabbed layout in `WorkoutDetailTabbedContent.kt` can host a dedicated swim adjustment card without schema breaking.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Auto-assign Swimming sport type via `ensureSwimSportTypeExists()` during swim session backfill.
  * Calculate distance from app-side pool length $\times$ turn count and persist cumulative distance, speed, and lapNr into `WorkoutSamples.db`.
  * Persist discrete length laps in `LapsDatabaseManager` and update totals in `WorkoutSummariesDatabaseManager`.
  * Add Aftermath UI card allowing pool length switching (`25m`, `50m`, `25yd`, custom) and `+1`/`-1` length adjustment.
  * 9-language localization parity without emojis.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No optical HRV / `WorkoutRr.db` backfill (strictly excluded per PPG physics).
  * No real-time live BLE swimming metrics (BLE does not propagate through water).
  * No stroke style classification or drill logging.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Net-new requirement only (`REQ-SET-111`)**: Builds upon `REQ-SET-107` (Swim template) and `REQ-SET-110` (Swim session reader).
* No existing requirements modified. All existing invariants (ATT-896 lap guards, zero HRV for optical sensors) strictly preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-SET-111` (`REQ-SET-111.1`..`REQ-SET-111.4`). Mapped test: `TST-SET-113`.
* **Affected Architecture Elements**: `PolarVeritySenseOfflineController.kt`, `WorkoutSamplesDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`, `LapsDatabaseManager.java`, `SportTypesRepository.kt`, `WorkoutDetailTabbedContent.kt`, `strings_swimming.xml`.
* **Verification Impact & Test Surface**: Unit tests in `PolarVeritySenseOfflineControllerTest`, `SportTypeSwimTemplateTest`, `TranslationParityTest`, and Compose test for Aftermath swim adjustment card.
* **Risk Assessment & Invariants**:
  * Zero `WorkoutRr.db` writes; zero unhandled schema downgrades; hold auto-exports until commit; zero emojis.

---

## 6. Architectural Strategy & High-Level Solution
1. **Backfill Extension**: `applySwimBackfillAndCommit()` queries `TuningPreferencesDataStore.defaultPoolLengthMeters`, creates/assigns Swim sport type, backfills `DISTANCE_m`, `SPEED_mps`, and `LAP_NR` into `WorkoutSamples.db`, and calls `LapsDatabaseManager.saveLap()`.
2. **Aftermath Quick Adjustment**: `SwimWorkoutAdjustCard.kt` in Workout Details allows changing pool length and adjusting turns (`+1`/`-1`), recalculating laps, samples, and summary stats in SQLite and triggering re-export.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero `WorkoutRr.db` writes (PPG optical noise protection).
  2. Automatic exports held until SQLite backfill finishes.
  3. Idempotent `ensureSwimSportTypeExists()` (no duplicate rows in `SportType.db`).
  4. Zero emojis across all 9 localization XML files.
* **Risk Rating**: **MEDIUM** (Cross-database backfill + Compose Aftermath card, fully bounded by existing SQLite managers).
