# Problem Domain & Root Cause Analysis: ATT-7924

**Ticket**: `ATT-7924` (`[Feature] Backfill Polar Verity Sense stored HR on workout stop and Aftermath retry`, 2 SP)
**Target Release**: `V5.0.0`

## 1. Root Cause / Problem Domain
`ATT-7923` added `PolarVeritySenseOfflineController` (`FEATURE_POLAR_OFFLINE_RECORDING`) to arm optical 1-Hz HR recording into Verity Sense internal flash and fetch/remove `HrOfflineRecording`. However, `TrackerService.endWorkout()` currently invokes only `PolarH10OfflineController.stopAndBackfillWorkoutBlocking(...)`, clearing Verity's `isArmed` prematurely in `onWorkoutStopped()`, leaving missed BLE HR gaps unpatched in `WorkoutSamples.db`, never gating downstream exports on Verity backfill, and offering no Aftermath Blue Sensor Mode retry when Verity Sense is turned off before Stop.

## 2. Change Impact Analysis
- **Affected Requirements**: Extends `REQ-SET-108` (`ATT-7923`) and complements `REQ-SET-105` (`ATT-7918`) / `REQ-SET-106` (`ATT-7919`) by reserving **`REQ-SET-109`** in `docs/requirements/req_settings_profiles.md` and **`TST-SET-111`** in `docs/tests/tst_settings_profiles.md`. Reuses `WorkoutSummaries.db` v31 `polarSyncPending`/`polarExerciseId` (`verity:` marker) with zero DB schema bump.
- **Affected Architecture Elements**: `PolarVeritySenseOfflineController.kt` (add `shouldBackfill()`, `stopAndBackfillWorkoutBlocking()`, `syncPendingWorkout()`, 1-Hz HR sample alignment, HR extrema recomputation), `WorkoutSamplesDatabaseManager.java` (reuse `hasMissingHeartRateSamples()` / `patchMissingHeartRateSamples()`), `TrackerService.java` (`endWorkout()` stop-time backfill before `ExportManager.workoutFinished()`), `PolarH10RetrySyncBanner.kt`, `TrackOnMapScreen.kt`, `WorkoutDetailTabbedContent.kt`, `res/values*/strings_devices.xml` (9 locales, zero emojis), `docs/architecture.md` (§3.4).
- **Verification Impact & Test Surface**: Add unit tests in `PolarVeritySenseOfflineControllerTest.kt` (1-Hz alignment, gap-only patch, zero `WorkoutRr` / `HrvPostWorkoutRecomputer` calls, HR summary/zone refresh, post-commit single-entry deletion, zero-gap fast path, deferred retry) and `PolarH10RetrySyncBannerTest.kt` (Verity vs H10 copy & dispatch), plus `TranslationParityTest`, `SourceTextReadingAuditContractTest`, `AspiceSweAuditContractTest`, `:app:lintDebug`, scoped `detekt`, and visual screenshot proof on `emulator-5554`.
- **Risk Assessment & Invariants**:
  1. **Gap-only HR patch**: Patch strictly `HR IS NULL OR HR <= 0` rows in `WorkoutSamples.db`; preserve valid HR, GPS, speed, cadence, power; zero-gap fast path skips full PFTP download while stopping & removing matched entry.
  2. **Strict HRV Integrity**: NEVER write synthetic RR or optical `PPI` into `WorkoutRr.db` and NEVER invoke `HrvPostWorkoutRecomputer`.
  3. **Multi-Session Flash Safety & Export Gate**: Remove only the workout's matched `PolarOfflineRecordingEntry` strictly after SQLite commit; hold downstream exports until stop-time backfill finishes.

## 3. Scope Bounds
- **In Scope**: Stop-time Verity HR backfill, zero-gap fast path, HR summary/zone refresh, export gating, Aftermath Blue Sensor Mode retry banner & 9-locale copy, `REQ-SET-109`/`TST-SET-111`/`docs/architecture.md`.
- **Out of Scope**: `WorkoutRr.db` / `HrvPostWorkoutRecomputer` changes, PPI/ACC offline recording, DB schema version bumps.
