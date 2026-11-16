# Stage 1 Analysis: ATT-8108 - Improve Supporter Tier Anchoring & Post-Onboarding Garage Nudges

**Ticket**: [ATT-8108](https://rainerblind.atlassian.net/browse/ATT-8108) | **Sub-task**: `[Analysis]` (Pending `submit-stage`)
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-47.1` | **Branch**: `feature/ATT-8108`
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Problem Statement & Motivation
Two behavioral timing and anchoring friction points suppress voluntary selection of `Silver` (`25 €`), `Gold` (`50 €`), and `Diamond` (`100 €`) in the "Pay what you want" Supporter model (`REQ-SET-086`, `REQ-SET-088`):
1. **Premature Day-0 Nudge & Single-Shot Burnout**: `GarageLeagueDetector.evaluateNudge()` fires immediately on sensor pairing / equipment creation with zero minimum workout gate, burning permanent single-shot `hasShownGoldGarageNudge` / `hasShownDiamondGarageNudge` flags before the athlete records their first workout.
2. **Top-Down Low-Price Anchoring**: `SupporterScreen.kt` renders `bronze-monthly` (`1 €`) and `bronze-yearly` (`10 €`) at the top without self-selection persona framing or garage-qualification hints, anchoring users on `10 €`.

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA (`GarageLeagueDetector.kt`, `SettingsDataStore.kt`)**:
  * `GarageNudgeEvaluator.shouldTriggerNudge()` (`GarageLeagueDetector.kt:105-135`) evaluates only league thresholds, `currentTier.rank`, and boolean `!hasShownGold` / `!hasShownDiamond` (`SettingsDataStore.kt:233-234, 653-678`). It ignores `WorkoutSummariesDatabaseManager.getFinishedWorkoutCount()` (`WorkoutSummariesDatabaseManager.java:596`) and lacks cooldown timestamps/counters.
  * `DeviceListViewModel.kt:112,194,223` and `EquipmentViewModel.kt:390,403` invoke `evaluateNudge()` + `markNudgeShown()` immediately post-save. Conversely, `MainActivityWithNavigation.kt:276-295` (`mTrackingStoppedReceiver` on `TRACKING_FINISHED_INTENT`) + `ATrainingTrackerApp.kt:878-916` only surface `FirstJubileeCelebrationDialog` (`REQ-REV-003`), so Day-0-paired users never get nudged after finishing workouts.
* **RCA (`SupporterScreen.kt`, `SupporterComponents.kt`)**:
  * `SupporterPlanCard` (`SupporterComponents.kt:182-314`) displays only tier title + price; zero persona subtitles (`values*/strings_monetization.xml`) and no garage-match badge.
* **Independent Peer Review & Verification** (audited against `sprint/2026-47.1` @ `5a263429`):
  * *No Play Billing Client*: Contrary to generic billing assumptions, `SupporterComponents.handlePlanSelection()` (`lines 316-341`) opens Play Store URL in release and mutates `SettingsDataStore` in debug; `SupporterBasePlan.PLANS` (`SupporterTier.kt:74-80`) holds the 5 static plans.
  * *Blind Spot — Post-Workout Dialog Host*: Neither `MainActivityWithNavigation` nor `ATrainingTrackerApp` nor `WorkoutSummariesTabbedScreen` currently hosts `GarageUpgradeNudgeDialog`; Stage 3/4 must add post-workout evaluation & host wiring (suppressing if `firstJubileeCelebrationState != null` or downgrade sheet active).
  * *Blind Spot — Detekt & Migration*: `detekt-baseline.xml:808` flags `LongMethod` on `SupporterPlanCard`; extract subtitle/badge subcomposables. Legacy `hasShown*GarageNudge = true` prefs must migrate safely without re-triggering before cooldown (`>= 180d` AND `>= 25` workouts).
  * *Stage 4 `Rule 8.5` Screenshot (`VISUAL/UI`)*: Visual changes to `SupporterPlanCard` (subtitles + garage-match badge) require a Stage 4 Compose PNG screenshot (`docs/engineering/walkthroughs/img/ATT-8108_supporter_cards.png`) verified via `read_file` against the 4-point visual checklist.

## 3. Scope Grounding & Non-Goals (`ATT-1250`, `3 SP`)
* **In-Scope Goals**:
  1. Warm-engagement gate (`MIN_FINISHED_WORKOUTS_FOR_NUDGE = 8`) + bounded seasonal re-nudge (`>= 180 days` AND `>= 25` additional finished workouts, max 2 reminders / 3 total per league) in `GarageLeagueDetector` & `SettingsDataStore` + `SupporterDebugSection` reset.
  2. Post-workout `TRACKING_FINISHED_INTENT` nudge check in `MainActivityWithNavigation` / `ATrainingTrackerApp`, strictly yielding to `FirstJubileeCelebrationDialog` (`REQ-REV-003`).
  3. 9-locale zero-emoji persona subtitles (`strings_monetization.xml`) + subtle `"Matches your garage"` badge on `SupporterPlanCard` when `evaluateLeague()` qualifies `GOLD`/`DIAMOND` above `effectiveTier`.
  4. Unit/contract tests (`GarageLeagueDetectorTest`, `SupporterPlanCardHierarchyTest`, `SupporterCopyContractTest`, `TranslationParityTest`) + `Rule 8.5` Compose PNG inspection.
* **Out-of-Scope Non-Goals**: Altering `SupporterBasePlan.PLANS` IDs/prices (`REQ-SET-086.1`), adding Play Billing SDK, modifying `evaluateLeague()` thresholds (`REQ-SET-088.1`), or changing `FirstJubileeCelebrationDialog`. Verified **3 SP**.

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-SET-088` (`REQ-SET-088.3`, `REQ-SET-088.4`) and extends `REQ-SET-086` / `REQ-UI-501` (`GarageLeagueDetector.kt`, `SettingsDataStore.kt`, `SupporterScreen.kt`, `SupporterComponents.kt`, `strings_monetization.xml`).
* **Historical Origin & Commit Trace**: `de0e1d55` (`ATT-51`), `3b4e0a20` (`ATT-5299`), `30ab5df4` (`ATT-5468`), `be746cdd` (`ATT-5570`), `7d72425c` (`ATT-6286`).
* **Root Reason for Existing Formulation**: Permanent `hasShown*` flags prevented repeated dialog spam during equipment/sensor edits; plain cards avoided paywall complexity.
* **Preservation of Core Invariants**: `REQ-SET-086.1` (5 base plan IDs/prices), `REQ-SET-088.1/.2/.7` (league thresholds, tier monotonicity, `Rule 73` stacked dialog buttons), `Rule 38` (`maxLines = 1, softWrap = false`), `ATT-5400` (zero emojis), `ATT-5659` (no defensive buzzwords), `REQ-REV-003/.004` (Jubilee priority).

## 5. Change-Request Impact Analysis (SUP.10 / `REQ-PRO-064`)
* **Affected Requirements**: `REQ-SET-088.3`, `REQ-SET-088.4` (`docs/requirements/req_settings_profiles.md`); `REQ-UI-501` / net-new `REQ-UI-5XX` (`docs/requirements/req_ui_design_system.md`).
* **Affected Architecture Elements**: `GarageLeagueDetector.kt`, `SettingsDataStore.kt`, `MainActivityWithNavigation.kt`, `ATrainingTrackerApp.kt`, `SupporterScreen.kt`, `SupporterComponents.kt`, `SupporterDebugSection.kt`, `values*/strings_monetization.xml`.
* **Verification Impact & Test Surface**: `TST-SET-088` (`GarageLeagueDetectorTest`), `TST-UI-501` (`SupporterPlanCardHierarchyTest`), `SupporterCopyContractTest`, `TranslationParityTest`, `GarageUpgradeNudgeDialogContractTest`, plus Stage 4 `Rule 8.5` Compose PNG screenshot.
* **Risk Assessment & Invariants**: Medium risk (`3 SP`); DataStore preference migration & `Detekt` `LongMethod` baseline avoided via helper extraction and backward-compatible default constructor params.

## 6. System Invariants & Risk Assessment
* **Core Invariants**: 5 base plan IDs/prices intact; `evaluateLeague()` thresholds & Strava Pro rules unchanged; `Rule 38` single-line buttons; `Rule 73` stacked dialog buttons; zero inline emojis across 9 locales (`ATT-5400`); Jubilee never collides with garage nudge.
* **Risk Rating**: **MEDIUM** (`3 SP`) — Spans monetization DataStore cooldown migration, post-workout dialog orchestration, 9-locale localization, and `SupporterPlanCard` visual hierarchy.
