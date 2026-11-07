# Stage 1: Problem Domain & Root Cause Analysis - ATT-5570

**Ticket**: [ATT-5570](https://rainerblind.atlassian.net/browse/ATT-5570)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (Good and consistent UI) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5570`  
**Requirement Mapping**: `REQ-SET-088` (`REQ-SET-088.1`, `REQ-SET-088.5`)  
**Test Mapping**: `TST-SET-088` (`TST-SET-088.1`, `TST-SET-088.7`), `TST-LOC-001`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Problem Domain & Background
Athletes with an active Strava Pro subscription (`summit == true` or `premium == true` in Strava API v3 athlete payload) already pay ~60–75 € / year for fitness software and represent the highest-propensity cohort for aTrainingTracker's Gold (50 €/yr) and Diamond (100 €/yr) Supporter tiers. While the Strava API delivers these fields without extra OAuth scopes during token exchange and equipment polling, aTrainingTracker currently ignores them. Furthermore, `GarageLeagueDetector` evaluates only physical hardware counts, and `GarageUpgradeNudgeDialog` lacks tailored copy mentioning the Strava Pro status.

---

## 2. Chesterton's Fence & Invariant Archaeology

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Amends `REQ-SET-088` (`REQ-SET-088.1`, `REQ-SET-088.5`), targeting `TrainingApplication.java`, `StravaHelper.kt`, `StravaEquipmentSynchronizeThread.java`, `StravaDataPurgeManager.kt`, `GarageLeagueDetector.kt`, `GarageUpgradeNudgeDialog.kt`, and `strings.xml` across all 9 locales.
* `Historical Origin & Commit Trace`: Sprint 2026-45.1 (`ATT-5299`, initial detector), Sprint 2026-45.3 (`ATT-5400`, zero-emoji copy), Sprint 2026-45.6 (`ATT-5468`, Rule 73 stacked buttons), Sprint 2026-45.9 (`ATT-5570`).
* `Root Reason for Existing Formulation`: In ATT-5299, qualification relied solely on local hardware counts because Strava account subscription telemetry had not yet been ingested into application preferences.
* `Preservation of Core Invariants`: Rule 75 SharedPreferences null-safety (`cSharedPreferences != null`); complete data purge hygiene (`executeLocalDataPurge` resets subscriber state); tier monotonicity (`< SupporterTier.DIAMOND`, `< SupporterTier.GOLD`); once-in-a-lifetime non-spam persistence (`hasShownGoldGarageNudge`, `hasShownDiamondGarageNudge`); Rule 73 stacked action buttons; zero-emoji localization parity across all 9 languages.

1. **Rule 75 SharedPreferences Null-Safety**: `TrainingApplication.java` encapsulates static preference access. To prevent NullPointerExceptions in headless unit tests or early process initialization, all getter/setter accesses MUST guard `cSharedPreferences != null`.
2. **Data Purge & Privacy Hygiene**: `StravaDataPurgeManager.executeLocalDataPurge` and `TrainingApplication.deleteStravaToken` must unconditionally clear or reset `isStravaSubscriber` to `false` on account disconnection or data purge.
3. **Monotonicity & Non-Spam Gating (`REQ-SET-088.2`, `REQ-SET-088.4`)**: Strava subscriber status augments qualification without compromising once-in-a-lifetime nudge persistence (`hasShownGoldGarageNudge`, `hasShownDiamondGarageNudge`) or current tier rank suppression.
4. **Rule 73 Stacked CTAs & Zero-Emoji Localization (`REQ-SET-088.5`, `REQ-SET-088.7`)**: Dialog copy must preserve strict positional format specifiers (`%1$d`..`%5$s`), avoid inline Unicode emojis, and maintain full-width stacked action buttons.

---

## 3. Scope Bounding & Technical Architecture
1. **Preference Persistence (`TrainingApplication.java`)**:
   - Add `SP_STRAVA_IS_SUBSCRIBER = "SP_STRAVA_IS_SUBSCRIBER"`.
   - Expose `isStravaSubscriber(): Boolean` and `setStravaSubscriber(Boolean)` with `cSharedPreferences != null` guards.
   - Include `remove(SP_STRAVA_IS_SUBSCRIBER)` in `deleteStravaToken()`.
2. **Ingestion & Purge (`StravaHelper.kt`, `StravaEquipmentSynchronizeThread.java`, `StravaDataPurgeManager.kt`)**:
   - `StravaHelper.storeJSONData`: Read `athlete.optBoolean("summit") || athlete.optBoolean("premium")` and persist.
   - `StravaEquipmentSynchronizeThread.fillDbFromJsonObject`: Read `jsonObject.optBoolean("summit") || jsonObject.optBoolean("premium")` and persist.
   - `StravaDataPurgeManager.executeLocalDataPurge`: Ensure subscriber state is reset to `false`.
3. **Garage League Booster (`GarageLeagueDetector.kt`)**:
   - Add `val isStravaSubscriber: Boolean = false` to `GarageStats`.
   - `queryGarageStats()` populates `isStravaSubscriber` via `TrainingApplication.isStravaSubscriber()`.
   - In `GarageNudgeEvaluator.evaluateLeague`:
     - **DIAMOND**: `hardwareDiamond || (hardwareGold && isStravaSubscriber)` (Gold hardware + Strava Pro boosts to Diamond).
     - **GOLD**: `hardwareGold || isStravaSubscriber` (Strava Pro alone qualifies for Gold).
4. **Dynamic Dialog Copy & Localization (`GarageUpgradeNudgeDialog.kt`, `strings.xml`)**:
   - If `stats.isStravaSubscriber`, use `garage_detector_dialog_message_{gold|diamond}_strava`.
   - Implement localized strings across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 5 positional specifiers and 0 emojis.
5. **Targeted Verification**:
   - Expand `GarageLeagueDetectorTest`, `GarageUpgradeNudgeStringsAuditTest`, `StravaHelperTest`, and `StravaDataPurgeManagerTest`.
   - Run `TranslationParityTest` and `verify_requirement_governance.py`.
