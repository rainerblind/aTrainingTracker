# Stage 3: Implementation Plan - ATT-8108: Supporter Tier Anchoring & Post-Onboarding Garage Nudges

**Ticket**: [ATT-8108](https://rainerblind.atlassian.net/browse/ATT-8108) | **Sub-task**: `[Impl-Plan]` (Pending `submit-stage`)
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1` | **Branch**: `feature/ATT-8108`
**Requirement / Test Mapping**: `REQ-SET-115` (`REQ-SET-115.1`..`.4`) / `TST-SET-117` (`TST-SET-117.1`..`.5`)
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-16

---

## 1. Problem Description & Background
`GarageLeagueDetector` currently triggers on Day-0 sensor pairing or equipment creation (`< 8` workouts) and burns permanent single-shot `hasShown*` flags, while `SupporterScreen` presents 5 plans without persona self-selection subtitles or garage-match cues. Stage 4 will gate nudges on post-onboarding engagement, add bounded seasonal cooldowns, wire post-workout evaluation, and add 9-locale plan subtitles + garage-match badges.

## 2. Traceability & Requirements Mapping
* `REQ-SET-115.1` -> `TST-SET-117.1`, `TST-SET-117.2`: `GarageLeagueDetector.kt`, `SettingsDataStore.kt`, `SupporterDebugSection.kt`
* `REQ-SET-115.2` -> `TST-SET-117.3`: `MainActivityWithNavigation.kt`, `ATrainingTrackerApp.kt`
* `REQ-SET-115.3` -> `TST-SET-117.4`: `SupporterScreen.kt`, `SupporterComponents.kt`, `app/detekt-baseline.xml`
* `REQ-SET-115.4` -> `TST-SET-117.5`: `values*/strings_monetization.xml`, `SupporterCardsScreenshotTest.kt`, `TranslationParityTest.kt`

## 3. UI Consistency & Design Guidelines (`docs/design_guidelines.md`)
* **Component & Token Compliance**: Reuse `MaterialTheme.colorScheme` (`primary`, `secondaryContainer`, `onSurfaceVariant`), `MaterialTheme.typography`, `RoundedCornerShape(8.dp / 12.dp)`, and `44.dp` tier emblems (`REQ-UI-445.4`). Zero raw `FilterChip`, `Switch`, nested `ElevatedCard`, hardcoded colors, inline emojis (`ATT-5400`), or defensive buzzwords (`ATT-5659`).
* **Button & Dialog Ergonomics**: Preserve `Rule 38` (`maxLines = 1, softWrap = false`, `PaddingValues(horizontal = 16.dp, vertical = 8.dp)`) on `SupporterPlanCard` buttons and `Rule 73` stacked full-width buttons on `GarageUpgradeNudgeDialog` (`AppModalBottomSheet`).

## 4. System Invariants & Preserved Behavior
1. **Unchanged SKUs & Prices**: `SupporterBasePlan.PLANS` (`bronze-monthly`..`diamond-yearly`) and `evaluateLeague()` hardware/Strava thresholds (`REQ-SET-086.1`, `REQ-SET-088.1`) remain 100% intact.
2. **Quota & Jubilee Priority**: `SensorQuotaReconciliationManager` (`MAX_FREE_PAIRED_DEVICES = 2`) stays truthful; `FirstJubileeCelebrationDialog` (`REQ-REV-003`) and `keepTwoSensorsDialogEvent` (`REQ-SET-084.5`) strictly preempt garage nudges.

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 — Cooldown & Legacy Migration State (`SettingsDataStore.kt`, `SupporterDebugSection.kt`)**: Add `KEY_*_GARAGE_NUDGE_COUNT`, `KEY_LAST_*_GARAGE_NUDGE_TIMESTAMP_MS`, `KEY_LAST_*_GARAGE_NUDGE_WORKOUT_COUNT` (`*` = `GOLD`/`DIAMOND`), `LeagueNudgeState` flows, `recordGarageNudgeShown(...)`, and update `resetGarageNudgeFlags()` (already invoked by `SupporterDebugSection.kt:230`) to clear both legacy booleans and cooldown/count keys.
2. **Step 2 — Engagement Gate & Seasonal Cooldown (`GarageLeagueDetector.kt`)**: Inject default `workoutSummariesDbManager` and `timeProvider` in `GarageLeagueDetector`. Add `MIN_FINISHED_WORKOUTS_FOR_NUDGE = 8`, `RE_NUDGE_COOLDOWN_MS = 180d`, `RE_NUDGE_MIN_WORKOUT_DELTA = 25`, `MAX_NUDGES_PER_LEAGUE = 3` in `GarageNudgeEvaluator.shouldTriggerNudge(...)`. Return `null` if `finishedWorkoutCount < 8` (never consuming quota). Safely migrate `legacyHasShown == true && nudgeCount == 0` by lazily seeding `(count = 1, timestamp = nowMs, workouts = finishedWorkoutCount)` and returning `null` until `>= 180d && >= 25` additional workouts elapse.
3. **Step 3 — Post-Workout Trigger & App Host (`MainActivityWithNavigation.kt`, `ATrainingTrackerApp.kt`)**: Add `garageNudgeState` on `MainActivityWithNavigation`. On `TRACKING_FINISHED_INTENT`, if `!isFirstJubilee && firstJubileeCelebrationState == null && keepTwoSensorsDialogEvent.value == null`, evaluate `GarageLeagueDetector.evaluateNudge()` off-main-thread; if non-null and still unpreempted, mark shown and emit `garageNudgeState`. Render `GarageUpgradeNudgeDialog` in `ATrainingTrackerApp.kt` (gated on `firstJubileeCelebrationState == null && keepTwoSensorsEvent == null`), routing `onUpgrade` via `navController.navigate(NavRoutes.supporter(planId, pushed = true))`.
4. **Step 4 — 9-Locale Monetization Copy (`app/src/main/res/values*/strings_monetization.xml`)**: Add 6 zero-emoji, zero-buzzword keys (`supporter_plan_subtitle_bronze_monthly`, `_bronze_yearly`, `_silver_yearly`, `_gold_yearly`, `_diamond_yearly`, `supporter_garage_match_badge`) across all 9 existing locale files (`values`, `-de`, `-es`, `-fr`, `-it`, `-ja`, `-nl`, `-pl`, `-pt`; note `values-sv` does not exist in repo).
5. **Step 5 — Plan Card Anchoring & Detekt Modularity (`SupporterComponents.kt`, `SupporterScreen.kt`, `app/detekt-baseline.xml`)**: Add `subtitleRes()` on `SupporterBasePlan` and `isGarageMatch: Boolean = false` on `SupporterPlanCard`; extract `SupporterPlanHeaderBadges`, `SupporterPlanTitleBlock`, `SupporterPlanActionButton` to eliminate `LongMethod` (remove baseline entry at `app/detekt-baseline.xml:808`). Wire `SupporterScreen` to resolve `qualifiedLeague` on `Dispatchers.IO` and flag matching `GOLD`/`DIAMOND` cards when `effectiveTier.rank < qualifiedTier.rank`.
6. **Step 6 — Unit, Contract, Detekt & `Rule 8.5` Screenshot Verification**:
   - Add `// TST-SET-117.1`..`.3` tests in `GarageLeagueDetectorTest.kt`; `// TST-SET-117.4`..`.5` assertions in `SupporterPlanCardHierarchyTest.kt`, `SupporterCopyContractTest.kt`, and `SupporterCardsScreenshotTest.kt` (rendering Light + Dark cards to `docs/engineering/walkthroughs/img/ATT-8108_supporter_cards.png`, inspected via `read_file`).
   - Run `./gradlew :app:testDebugUnitTest --tests "*GarageLeagueDetectorTest" --tests "*SupporterPlanCardHierarchyTest" --tests "*SupporterCopyContractTest" --tests "*GarageUpgradeNudge*" --tests "*SensorQuotaReconciliationManagerTest" --tests "*TranslationParityTest" --tests "*SupporterCardsScreenshotTest"` and `./gradlew :app:detekt`.

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit/contract/parity/screenshot tests + `:app:detekt` + `Rule 8.5` PNG inspection + `verify_requirement_governance.py`.
* **Rollback**: Isolated on detached `HEAD` / `feature/ATT-8108` prior to Stage 5 squash-merge.
