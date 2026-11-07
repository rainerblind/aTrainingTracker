# Stage 3: Implementation Plan - ATT-5570: Strava Pro Detection for Garage League & Dialog Copy

**Ticket**: [ATT-5570](https://rainerblind.atlassian.net/browse/ATT-5570)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (Good and consistent UI) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.9` | **Branch**: `feature/ATT-5570`  
**Requirement Mapping**: `REQ-SET-088` (`REQ-SET-088.1`, `REQ-SET-088.5`)  
**Test Spec Mapping**: `TST-SET-088` (`TST-SET-088.1`, `TST-SET-088.5`), `TST-LOC-001`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-07  

---

## 1. Architectural Strategy & Component Boundaries
* **Architecture (SWE.2)**: Modular integration across telemetry ingestion (`StravaHelper.kt`, `StravaEquipmentSynchronizeThread.java`), preference storage (`TrainingApplication.java`), business evaluation (`GarageLeagueDetector.kt`), and presentation (`GarageUpgradeNudgeDialog.kt`, localized `strings.xml`).
* **Invariants**: Rule 75 null-safe `cSharedPreferences != null` guards; complete data purge hygiene; tier monotonicity; once-in-a-lifetime non-spam persistence; Rule 73 stacked CTA buttons; zero inline Unicode emojis; 100% 9-language translation parity.

---

## 2. Atomic Implementation Steps

| Step | Action | Target Files | Verification |
| :--- | :--- | :--- | :--- |
| **Step 1** | Add `SP_STRAVA_IS_SUBSCRIBER`, null-safe `isStravaSubscriber()`, `setStravaSubscriber(boolean)`, and purge in `deleteStravaToken()`. | `TrainingApplication.java` | Rule 75 null-safety, unit tests |
| **Step 2** | Ingest `summit`/`premium` from athlete JSON payload during OAuth exchange and equipment sync; reset in local data purge. | `StravaHelper.kt`<br>`StravaEquipmentSynchronizeThread.java`<br>`StravaDataPurgeManager.kt` | `StravaHelperTest`<br>`StravaDataPurgeManagerTest` |
| **Step 3** | Add `isStravaSubscriber: Boolean = false` to `GarageStats`, populate in `queryGarageStats()`, and integrate Gold qualification & Diamond booster in `evaluateLeague()`. | `GarageLeagueDetector.kt` | `GarageLeagueDetectorTest` (`TST-SET-088.1`) |
| **Step 4** | Add `garage_detector_dialog_message_{gold\|diamond}_strava` across all 9 `strings.xml`; select `_strava` copy in dialog when subscriber is true. | `GarageUpgradeNudgeDialog.kt`<br>`res/values*/strings.xml` | `GarageUpgradeNudgeStringsAuditTest`<br>`TranslationParityTest` |
| **Step 5** | Expand automated unit and contract tests covering booster logic, ingestion/purge, zero emojis, and format specifiers; run governance verification. | `GarageLeagueDetectorTest.kt`<br>`GarageUpgradeNudgeStringsAuditTest.kt`<br>`StravaHelperTest.kt`<br>`StravaDataPurgeManagerTest.kt` | `./gradlew testDebugUnitTest`<br>`verify_requirement_governance.py` |

---

## 3. UI Consistency & Localization Constraints
* **Reference Component**: Extends existing `GarageUpgradeNudgeDialog.kt` (`AppModalBottomSheet`) adhering to Rule 73 stacked buttons (`Button` full-width CTA + `TextButton` full-width dismiss).
* **Localization Parity**: 2 new message keys added to all 9 locale files (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) strictly preserving `%1$d`..`%5$s` positional specifiers and 0 inline Unicode emojis.
