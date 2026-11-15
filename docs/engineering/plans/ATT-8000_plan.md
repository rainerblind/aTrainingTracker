# Stage 3: Implementation Plan - ATT-8000: Polar Verity Sense Swim Mode

**Ticket**: [ATT-8000](https://rainerblind.atlassian.net/browse/ATT-8000)  
**Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`  
**Requirement Mapping**: `REQ-SET-110` | **Test Mapping**: `TST-SET-112`  
**Branch**: `feature/ATT-8000` | **Author**: AI Agent 1 (Implementer)  
**Stage 4 Execution Classification**: `VISUAL/UI` (`subagent_pro` Doing + 4-point visual inspection per Rules 7.1 & 8.5; `subagent_flash` Gate 4)  

---

## 1. SWE.2 Architecture, Threading Boundaries & Invariants
- **GATT & SDK Feature Set**: Add `FEATURE_POLAR_TRAINING_DATA` to `PolarOfflineGattCoordinator` and required offline features.
- **FTU Provisioning Lifecycle**: In `PolarVeritySenseOfflineController`, check `api.isFtuDone(polarId)`. If false, execute `api.doFirstTimeUse(polarId, config)` with athlete default parameters to unlock standalone button modes without a Polar account.
- **Swim Session Reader & Turn Extraction**: Scan `api.getTrainingSessionReferences()`, fetch `PolarTrainingSession`, identify Pool Swimming via `sport.value == 103` (`PbSportIdentifier`), extract 1-Hz HR from `heartRateSamples`, and detect raw wall turn timestamps from `distanceSamples` decoupled from internal pool length.
- **SYSTEM_BUSY Resilience**: On `SYSTEM_BUSY` during stop backfill or retry sync, save `setPolarSyncPending` with `verity:swim_busy:<targetId>`. `PolarH10RetrySyncBanner` renders localized button-exit instructions across 9 locales.
- **Strict Invariants**: Zero `WorkoutRr.db` writes; zero `WorkoutSummaries.db` schema bumps; gap-only `HR` patching; post-commit `deleteTrainingSession` for only the matched session; zero emojis in strings.

---

## 2. UI Consistency (`docs/design_guidelines.md`)
- **Reference Component**: `PolarH10RetrySyncBanner.kt` in `TrackOnMapScreen.kt` and `WorkoutDetailTabbedContent.kt`.
- **Reused Components**: Material 3 `Card` (`secondaryContainer`), `Sync` icon, filled primary `Button` (*Sync Now*), and `OutlinedButton` (*Dismiss*).
- **Theme Tokens & Layout**: `RoundedCornerShape(16.dp)`, `16.dp` padding, single-line German buttons (`maxLines = 1`, `TextOverflow.Ellipsis`).
- **Mode Dispatching**: Add `isVeritySwimBusy` check to select `polar_verity_swim_busy_title` / `polar_verity_swim_busy_desc` (*"Please press the button on your Polar Verity Sense to exit Swim Mode (return to blue LED) and tap Sync Now."*).

---

## 3. Step-by-Step Implementation Sequence (Stage 4 Construction)

| Step | Target Files | Change Summary | Traces To |
| :--- | :--- | :--- | :--- |
| 1 | `res/values*/strings_devices.xml` (9 locales) | Add `polar_verity_swim_busy_title`, `polar_verity_swim_busy_desc`, `polar_verity_swim_syncing` across `en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt` (zero emojis, literal strings). | `REQ-SET-110.3`, `REQ-UI-106` |
| 2 | `PolarOfflineGattCoordinator.kt` | Include `FEATURE_POLAR_TRAINING_DATA` in `REQUIRED_OFFLINE_FEATURES` and feature wait set. | `REQ-SET-110.1` |
| 3 | `PolarVeritySenseOfflineController.kt` | Add `VERITY_SWIM_BUSY_PREFIX = "verity:swim_busy:"`, `isVeritySwimBusy()`, `checkAndPerformFtu()`, `fetchSwimTrainingSession()`, `parseSwimTrainingSession()` (`PbSportIdentifier == 103`, 1-Hz HR and turn extraction), and post-commit `deleteTrainingSession()`. Handle `SYSTEM_BUSY` in `stopAndBackfillWorkout` and `syncPendingWorkout` by saving `verity:swim_busy:` tag. | `REQ-SET-110.1`, `REQ-SET-110.2`, `REQ-SET-110.3` |
| 4 | `PolarH10RetrySyncBanner.kt` | Wire `isVeritySwimBusy(polarExerciseId)` to surface swim-mode title/desc in `PolarPendingRetrySyncSection` and `PolarH10RetrySyncBanner`. Add `@Preview` for Swim Mode busy guidance. | `REQ-SET-110.3` |
| 5 | `PolarVeritySenseOfflineControllerTest.kt`, `PolarRetrySyncBannerAndroidTest.kt` | Author unit and UI tests covering `TST-SET-112.1`..`TST-SET-112.5` (`PbSportIdentifier == 103` parsing, FTU execution, `SYSTEM_BUSY` tagging, post-commit deletion, 9-locale parity). | `TST-SET-112` |

---

## 4. Tier 1 Test Command & Verification Plan
```bash
./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.PolarVeritySenseOfflineControllerTest" --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"
```
* **Visual Inspection**: Capture `ATT-8000_verity_swim_busy_banner.png` on `emulator-5554` via `PolarRetrySyncBannerAndroidTest`, verify via `read_file` with 4-point visual checklist before `submit-stage 4`.
