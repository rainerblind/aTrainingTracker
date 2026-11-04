# Stage 3: Implementation Plan - ATT-2308: Milestone Workout Auto-Naming and First Jubilee Celebration Review Prompt

**Ticket**: [ATT-2308](https://rainerblind.atlassian.net/browse/ATT-2308)  
**Sub-task**: [ATT-5337](https://rainerblind.atlassian.net/browse/ATT-5337) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Requirement Mapping**: `REQ-REV-001`..`REQ-REV-005`  
**Test Mapping**: `TST-REV-001`..`TST-REV-006`, `TST-LOC-001`  
**Branch**: `feature/ATT-2308`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Problem Description & Background
* Personal training milestones (100, 250, 500, 1000...) represent major emotional achievements for endurance athletes.
* Workouts reaching milestone counts currently receive generic auto-names without milestone recognition.
* Milestone completion provides the ideal peak-end moment to celebrate, solicit Google Play reviews, offer direct email feedback to Rainer, and invite free-tier athletes to support ongoing development via Pro.

---

## 2. Traceability & Requirements Mapping
* **REQ-REV-001** -> `TST-REV-001`: Sport-specific finished workout counting in SQLite and milestone threshold evaluation.
* **REQ-REV-002** -> `TST-REV-002`, `TST-REV-005`: Dynamic title augmentation `[Count]. [SportName]: [Name]` and export propagation.
* **REQ-REV-003** -> `TST-REV-003`, `TST-REV-005`: First-Jubilee Celebration Dialog with In-App Review, direct email, and Free-tier Pro appeal.
* **REQ-REV-004** -> `TST-REV-004`: Single-prompt invariant in `SettingsDataStore` (`has_shown_first_jubilee_prompt`).
* **REQ-REV-005** -> `TST-REV-006`: `BuildConfig.DEBUG` preview trigger button in `SupporterDebugSection`.

---

## 3. System Invariants & Preserved Behavior
1. *AC-4 Single-Prompt Invariant*: Dialog is shown strictly once in app lifetime. Subsequent jubilees augment title without dialog re-prompting.
2. *Export Title Symmetry*: Augmented title in `WorkoutSummaries.WORKOUT_NAME` propagates to TCX, FIT, and Strava without altering export codecs.
3. *Thread Safety*: SQLite counting and naming logic execute on `Dispatchers.IO`. Zero UI jank.
4. *ASPICE Code Lock*: Production code edits strictly locked until Gate 3 passes.

---

## 4. Proposed Architectural Changes
* **Database Layer (`WorkoutSummariesDatabaseManager.java`)**: Add `getFinishedWorkoutCountForSport(long sportId): int`.
* **Preferences Layer (`SettingsDataStore.kt`)**: Add `hasShownFirstJubileePrompt` Flow/setter with `booleanPreferencesKey("has_shown_first_jubilee_prompt")`.
* **Domain Layer (`MilestoneAutoNamingManager.kt`)**: Detect thresholds (`count == 100 || count == 250 || (count >= 500 && count % 500 == 0)`), format milestone titles with localized strings (`milestone_workout_name_format`), and hook into `TrackerService.finalizeLiveSession`.
* **UI Layer (`FirstJubileeCelebrationDialog.kt`, `ATrainingTrackerApp.kt`)**: Material 3 dialog with Google Play In-App Review (Play Store URI fallback), direct email feedback (`Intent.ACTION_SENDTO` `mailto:rainer.blind@gmail.com`), and Free-tier Supporter note (`NavRoutes.SUPPORTER`) gated by `!ProFeatureManager.isProActive.value`.
* **Debug Layer (`SupporterDebugSection.kt`)**: 1-tap "Preview Jubilee Celebration" button gated by `BuildConfig.DEBUG`.

### UI Consistency (Rule 23)
* **Reference screen / component**: `SwapOrUpgradeDialog.kt` and `SupporterComponents.kt`.
* **Reused components**: Material 3 `AlertDialog`, `Button`, `OutlinedButton`, `TextButton`, `Text`, `Icon`.
* **Theme tokens**: `MaterialTheme.shapes.extraLarge`, padding 16.dp / 8.dp, `colorScheme.primary`, `surface`, `onSurfaceVariant`.
* **New one-off styles & justification**: None; strictly standard Material 3 tokens.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Strings & Localization (9 Locales)**: Add milestone title templates and dialog strings to `res/values*/strings.xml`.
2. **Database & DataStore Extension**: Add `getFinishedWorkoutCountForSport` and `SettingsDataStore.hasShownFirstJubileePrompt`.
3. **Domain Milestone Manager**: Create `MilestoneAutoNamingManager.kt` and integrate with `TrackerService.finalizeLiveSession`.
4. **Celebration Dialog & Host Wiring**: Implement `FirstJubileeCelebrationDialog.kt`, wire into `MainActivityWithNavigation.kt` on `TRACKING_FINISHED_INTENT`.
5. **Debug Preview Trigger**: Add 1-tap preview trigger to `SupporterDebugSection.kt`.
6. **Targeted Unit Tests**: Run `./gradlew testDebugUnitTest --tests "*Milestone*" --tests "*Jubilee*" --tests "*TranslationParityTest*"`.

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit suite (`TST-REV-001`..`006`) followed by full test suite regression in Stage 5.
* **Rollback**: Changes isolated on branch `feature/ATT-2308`; atomic git revert if necessary.
