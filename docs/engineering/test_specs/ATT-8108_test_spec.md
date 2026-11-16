# Stage 2: Requirement & Test Specification - ATT-8108: Improve Supporter Tier Anchoring & Post-Onboarding Garage Nudges

**Ticket**: [ATT-8108](https://rainerblind.atlassian.net/browse/ATT-8108)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1`  
**Requirement Mapping**: `REQ-SET-115` (*Supporter Tier Self-Selection Anchoring, Garage-Match Badge & Post-Onboarding Engagement-Gated Garage Nudges*; amends `REQ-SET-088.3/.4`, extends `REQ-SET-086`, `REQ-UI-501`)  
**Test Spec ID**: `TST-SET-117`  
**Branch**: `feature/ATT-8108` | **Author**: AI Agent 2 (`subagent_flash`) | **Date**: 2026-11-16

---

## 1. Requirement Specification (REQ-SET-115)
* **Attributes**: Priority: `Medium` | Type: `Functional` | Verification Method: `Unit / Integration` | Target Status: `[Planned]`
* **REQ-SET-115.1 (Warm-Engagement Workout Gate & Bounded Seasonal Cooldown)**: `GarageNudgeEvaluator.shouldTriggerNudge(...)` and `GarageLeagueDetector.evaluateNudge()` SHALL require `finishedWorkoutCount >= 8` (`MIN_FINISHED_WORKOUTS_FOR_NUDGE`, queried via `WorkoutSummariesDatabaseManager.getFinishedWorkoutCount()`) before triggering any Gold or Diamond `GarageUpgradeNudgeDialog`. Day-0 pairing (`DeviceListViewModel`) or equipment creation (`EquipmentViewModel`) with `< 8` finished workouts MUST NOT consume nudge quota. Permanent single-shot flags (`hasShownGoldGarageNudge`, `hasShownDiamondGarageNudge`) SHALL migrate safely to bounded seasonal cooldown state in `SettingsDataStore` (`last*NudgeTimestampMs`, `*NudgeCount`, `last*NudgeWorkoutCount`; `MAX_NUDGES_PER_LEAGUE = 3`), allowing follow-ups only when **both** `>= 180 days` AND `>= 25` additional finished workouts have elapsed. `SupporterDebugSection` reset SHALL clear all legacy and cooldown keys, while `SensorQuotaReconciliationManager` (`MAX_FREE_PAIRED_DEVICES = 2`) remains untouched and truthful.
* **REQ-SET-115.2 (Post-Workout Nudge Evaluation & Jubilee Priority Arbitration)**: Upon `TrackerService.TRACKING_FINISHED_INTENT` (`MainActivityWithNavigation.mTrackingStoppedReceiver` / `ATrainingTrackerApp`), the app SHALL asynchronously evaluate `GarageLeagueDetector.evaluateNudge()` for athletes crossing `finishedWorkoutCount >= 8`, strictly yielding whenever `FirstJubileeCelebrationDialog` (`firstJubileeCelebrationState != null`, `REQ-REV-003`) or `SensorQuotaReconciliationManager` (`keepTwoSensorsEvent != null`, `REQ-SET-084.5`) is active.
* **REQ-SET-115.3 (Supporter Tier Self-Selection Subtitles, Garage-Match Badge & Detekt Modularity)**: `SupporterPlanCard` (`SupporterComponents.kt`, `SupporterScreen.kt`) SHALL render localized 1–2 line fairness/persona subtitles on all 5 unchanged `SupporterBasePlan.PLANS` (`bronze-monthly`, `bronze-yearly`, `silver-yearly` keeping `isRecommended = true` anchor below Silver, `gold-yearly`, `diamond-yearly`), show a subtle `"Matches your garage"` (`supporter_garage_match_badge`) badge on the `gold-yearly` / `diamond-yearly` card when `GarageNudgeEvaluator.evaluateLeague()` exceeds `effectiveTier`, and extract helper subcomposables to eliminate `Detekt` `LongMethod` (`app/detekt-baseline.xml:808`) while preserving `Rule 38` and `Rule 73`.
* **REQ-SET-115.4 (9-Locale Parity, Zero-Emoji Hygiene & Rule 8.5 Compose PNG)**: All 6 new `strings_monetization.xml` keys SHALL maintain 100% parity across all 9 locales (`EN, DE, ES, FR, IT, JA, NL, PL, PT`) with zero inline emojis (`ATT-5400`) and zero defensive buzzwords (`ATT-5659`). Stage 4 SHALL render `docs/engineering/walkthroughs/img/ATT-8108_supporter_cards.png` via Robolectric Compose and inspect it with `read_file` (`Rule 8.5`).

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-SET-088.3`, `REQ-SET-088.4` and extends `REQ-SET-086.4/.5`, `REQ-UI-501`; targets `GarageLeagueDetector.kt`, `SettingsDataStore.kt`, `MainActivityWithNavigation.kt`, `ATrainingTrackerApp.kt`, `SupporterScreen.kt`, `SupporterComponents.kt`, `SupporterDebugSection.kt`, `values*/strings_monetization.xml`.
* **Historical Origin & Commit Trace**: `de0e1d55` (`ATT-51`), `3b4e0a20` (`ATT-5299`), `30ab5df4` (`ATT-5468`), `be746cdd` (`ATT-5570`), `7d72425c` (`ATT-6286`).
* **Root Reason for Existing Formulation**: Permanent `hasShown*` flags avoided repeated prompts during consecutive edits; plain cards avoided paywall complexity.
* **Preservation of Core Invariants**: All 5 `SupporterBasePlan.PLANS` SKUs/prices (`REQ-SET-086.1`), `evaluateLeague()` thresholds & Strava boost (`REQ-SET-088.1`), tier monotonicity (`REQ-SET-088.2`), `SensorQuotaReconciliationManager` (`REQ-SET-084.5`), `Rule 38`, `Rule 73`, `ATT-5400`, `ATT-5659`, and `REQ-REV-003` Jubilee precedence remain 100% intact.

---

## 3. Acceptance Criteria (Given-When-Then)
* **Criterion 1 (No Premature Day-0 Nudge)**: *Given* a `FREE`/`BRONZE` athlete with `< 8` finished workouts, *When* they pair a power meter or add 3 bikes, *Then* `GarageUpgradeNudgeDialog` does NOT trigger and cooldown/count state is untouched (while `SensorQuotaReconciliationManager` quota remains truthful).
* **Criterion 2 (Post-Workout Nudge & Bounded Seasonal Cooldown)**: *Given* a `BRONZE` athlete with Gold garage reaching `>= 8` finished workouts (without Jubilee/quota dialog), *When* tracking finishes, *Then* `GarageUpgradeNudgeDialog` is presented; if dismissed, it remains silent until **both** `>= 180 days` AND `>= 25` additional workouts pass (max 3 total nudges per league).
* **Criterion 3 (Self-Selection Subtitles, Garage-Match Badge & 9-Locale Parity)**: *Given* `SupporterScreen` across all 9 locales, *When* the 5 cards render, *Then* each shows its zero-emoji persona subtitle without button wrapping (`Rule 38`), `Silver` retains `Empfehlung` below Silver, qualifying users see `"Matches your garage"` on Gold/Diamond, and `ATT-8108_supporter_cards.png` passes `Rule 8.5`.

---

## 4. Test Specification (TST-SET-117)
* **TST-SET-117.1 (Workout Gate & Quota Truthfulness)**: Assert `shouldTriggerNudge` / `evaluateNudge()` return `null` for `finishedWorkoutCount < 8` without consuming quota; `SensorQuotaReconciliationManagerTest` remains green.
* **TST-SET-117.2 (Bounded Cooldown, Legacy Migration & Debug Reset)**: Assert `>= 180d && >= 25` workout conjunction, `MAX_NUDGES_PER_LEAGUE = 3` cap, safe migration of `hasShown* = true`, and full reset via `resetGarageNudgeFlags()`.
* **TST-SET-117.3 (Post-Workout Evaluation & Jubilee Suppression)**: Assert post-workout evaluation triggers at `>= 8` workouts and yields when Jubilee or quota reconciliation is active.
* **TST-SET-117.4 (Card Anchoring, Garage Badge & Detekt Extraction)**: Assert `SupporterPlanCardHierarchyTest`, `SupporterCopyContractTest`, `GarageUpgradeNudgeDialogContractTest`, `Rule 38`/`Rule 73`, and zero `LongMethod` on `SupporterPlanCard`.
* **TST-SET-117.5 (9-Locale Parity & Rule 8.5 PNG)**: Run `TranslationParityTest` + Robolectric Compose screenshot `ATT-8108_supporter_cards.png` (`read_file` 4-point visual audit).

---

## 5. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-117.1` | Unit | `GarageLeagueDetectorTest.kt` | `REQ-SET-115.1` | Planned |
| `TST-SET-117.2` | Unit | `GarageLeagueDetectorTest.kt`, `SettingsDataStore.kt` | `REQ-SET-115.1` | Planned |
| `TST-SET-117.3` | Unit / Integration | `MainActivityWithNavigation.kt`, `ATrainingTrackerApp.kt` | `REQ-SET-115.2` | Planned |
| `TST-SET-117.4` | Unit / Contract | `SupporterPlanCardHierarchyTest.kt`, `SupporterComponents.kt` | `REQ-SET-115.3` | Planned |
| `TST-SET-117.5` | Localization / UI | `TranslationParityTest.kt`, `SupporterCardsScreenshotTest.kt` | `REQ-SET-115.4` | Planned |
