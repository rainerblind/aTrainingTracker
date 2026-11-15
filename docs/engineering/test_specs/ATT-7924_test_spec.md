# Requirement & Test Specification: ATT-7924

**Ticket**: `ATT-7924` (`[Feature] Backfill Polar Verity Sense stored HR on workout stop and Aftermath retry`, 2 SP)
**Target Release**: `V5.0.0`

## 1. Requirements Summary (`REQ-SET-109` [Planned])
- **Priority**: `High` | **Type**: `Functional` | **Verification Method**: `Unit / Integration`
- **REQ-SET-109.1 (Stop-Time Backfill, Export Gate & Zero-Gap Fast Path)**: On Android 13+, `TrackerService.endWorkout()` SHALL invoke `PolarVeritySenseOfflineController.stopAndBackfillWorkoutBlocking(...)` BEFORE `ExportManager.workoutFinished()`, holding downstream exports until backfill or pending-sync fallback completes. `shouldBackfill()` checks ONLY `WorkoutSamplesDatabaseManager.hasMissingHeartRateSamples()`. Zero missing HR skips `getOfflineRecord` while stopping HR and deleting the matched entry. Missing HR patches ONLY `HR IS NULL OR HR <= 0` rows via `HrOfflineRecording.startTime` + 1-Hz samples.
- **REQ-SET-109.2 (Strict HRV Integrity & HR Summary Recomputation)**: Controller MUST NEVER write `WorkoutRr.db` / optical PPI or invoke `HrvPostWorkoutRecomputer`. Post-patch, recompute `SensorType.HR` (`MIN`, `MAX`, `AVG`) into `WorkoutSummaries.db` (`TABLE_EXTREMA_VALUES`) and call `WorkoutRepository.refreshWorkout()`, updating HR zones via `ZoneDistributionCalculator`.
- **REQ-SET-109.3 (Multi-Session Flash Safety & Aftermath Retry)**: Delete ONLY the matched `PolarOfflineRecordingEntry` strictly AFTER SQLite commit. Unreachable sensor persists `setPolarSyncPending` with `verity:` `polarExerciseId` tag (zero DB bump). `PolarH10RetrySyncBanner` renders localized Blue Sensor Mode wake copy (zero emojis) and dispatches *Sync Now* to `PolarVeritySenseOfflineController.syncPendingWorkout()`, which refreshes summaries and triggers export invalidation + re-export.

## 2. Acceptance Criteria & Invariants
- **Given-When-Then**: (1) Locker-room stop backfill populates `HR` gaps + summary/zones before exports with `WorkoutRr.db` untouched; (2) Multi-session flash deletes only the workout's matched entry post-commit; (3) Zero-gap fast path skips full PFTP download; (4) Powered-off sensor at stop triggers `polarSyncPending` (`verity:`) and Aftermath Blue Sensor Mode *Sync Now* backfills and clears banner.
- **System Invariants**: Zero `WorkoutRr.db`/`HrvPostWorkoutRecomputer` writes; zero `WorkoutSummaries.db` version bump; gap-only `HR` patching; post-commit single-entry deletion; zero emojis in strings.

## 3. 9-Locale Localization Parity Plan (`strings_devices.xml`)
New keys across `en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt` (zero emojis, literal strings):
`notification_polar_verity_syncing_title`, `notification_polar_verity_syncing_desc`, `polar_verity_retry_sync_title`, `polar_verity_retry_sync_desc` ("...Turn on your Polar Verity Sense in Blue Sensor Mode and tap Sync Now."), `polar_verity_retry_syncing`, `polar_verity_retry_sync_failed`.

## 4. Test Specification & Traceability (`TST-SET-111` [Planned])

| Test ID | Target Component | Procedure & Expected Verification | Traces To |
| :--- | :--- | :--- | :--- |
| `TST-SET-111.1` | `PolarVeritySenseOfflineControllerTest`, `TrackerService` | Verify `onWorkoutStopped()` keeps armed state, `stopAndBackfillWorkout()` aligns 1-Hz `HrOfflineRecording`, patches only missing `HR`, preserves live GPS/HR/power, runs pre-export. | `REQ-SET-109.1` |
| `TST-SET-111.2` | `PolarVeritySenseOfflineControllerTest` | Verify `hasMissingHeartRateSamples()==false` skips `getOfflineRecord()`, stops active `HR`, and deletes matched flash entry. | `REQ-SET-109.1` |
| `TST-SET-111.3` | `PolarVeritySenseOfflineControllerTest` | Verify `WorkoutRrRepository` receives zero inserts and `HrvPostWorkoutRecomputer` is never invoked. | `REQ-SET-109.2` |
| `TST-SET-111.4` | `PolarVeritySenseOfflineControllerTest` | Verify `HR` `MIN`/`MAX`/`AVG` extrema recompute, `removeOfflineRecord()` runs post-commit on ONLY matched entry, and rollback/exception skips deletion. | `REQ-SET-109.2`, `REQ-SET-109.3` |
| `TST-SET-111.5` | `PolarH10RetrySyncBannerTest`, `TranslationParityTest`, Screenshot | Verify `verity:` pending banner shows Blue Sensor Mode copy in 9 locales (zero emojis), dispatches `syncPendingWorkout()`, passes screenshot check on `emulator-5554`. | `REQ-SET-109.3` |
