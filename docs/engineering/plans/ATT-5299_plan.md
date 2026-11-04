# Stage 3: Implementation Plan - ATT-5299: Supporter N+1 Garage Detector

**Ticket**: [ATT-5299](https://rainerblind.atlassian.net/browse/ATT-5299)  
**Sub-task**: [ATT-5342](https://rainerblind.atlassian.net/browse/ATT-5342) (`[Impl-Plan]`)  
**Parent Epic**: None (*Monetization & Supporter Experience*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Requirement Mapping**: `REQ-SET-088.1`..`REQ-SET-088.6`  
**Test Mapping**: `TST-SET-088.1`..`TST-SET-088.4`, `TST-LOC-001`  
**Branch**: `feature/ATT-5299`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Problem Description & Background
* Athletes who build large garages ($\ge 3$ bikes, $\ge 4$ shoes, $\ge 5$ sensors, or multiple power meters) often remain on Free, Pioneer, Bronze, or Silver tiers without exposure to Gold (50 €/yr) or Diamond (100 €/yr) tiers.
* The moment of adding a new bike/shoe or pairing an additional sensor offers the ideal event-driven trigger for a humorous, self-ironic "N+1 Garage" upgrade dialog celebrating the athlete's hardware collection with 1-tap pre-selected upgrade navigation.

---

## 2. Traceability & Requirements Mapping
* **REQ-SET-088.1** -> `TST-SET-088.1`: Diamond/Gold qualification thresholds; Diamond takes precedence.
* **REQ-SET-088.2** -> `TST-SET-088.2`: Strict tier monotonicity gating (< `GOLD` for Gold, < `DIAMOND` for Diamond).
* **REQ-SET-088.3** -> `TST-SET-088.1`: Event-driven post-save evaluation after equipment save or sensor pairing.
* **REQ-SET-088.4** -> `TST-SET-088.2`: Single-nudge persistence in `SettingsDataStore` (`hasShownGold/DiamondGarageNudge`).
* **REQ-SET-088.5** -> `TST-SET-088.3`, `TST-LOC-001`: Humorous `GarageDetectorDialog` with dynamic counts and pre-selected plan.
* **REQ-SET-088.6** -> `TST-SET-088.4`: `BuildConfig.DEBUG` 1-tap preview triggers and reset button in `SupporterDebugSection`.

---

## 3. System Invariants & Preserved Behavior
1. *Single-Nudge Invariant*: Each league level (`GOLD`, `DIAMOND`) nudges at most once in app lifetime.
2. *Tier Monotonicity*: Never nudge if current effective `SupporterTier` is $\ge$ the target league tier.
3. *Zero Workflow Interruption*: Equipment and sensor persistence commit atomically prior to asynchronous evaluation.
4. *Thread Safety*: SQLite reads, tier comparison, and DataStore writes execute on `Dispatchers.IO`.

---

## 4. Proposed Architectural Changes
* **Preferences Layer (`SettingsDataStore.kt`)**: Add `hasShownGoldGarageNudge` and `hasShownDiamondGarageNudge` Flow/setters and `resetGarageNudgeFlags()`.
* **Domain Layer (`GarageLeagueDetector.kt`)**: Pure evaluation logic (`evaluateLeague`, `shouldTriggerNudge`, and `queryGarageStats`) aggregating counts from `EquipmentDbHelper` and `DevicesDatabaseManager`.
* **UI Dialog (`GarageDetectorDialog.kt`)**: Material 3 dialog with dynamic item counts, upgrade button navigating to `SupporterScreen` (`gold-yearly` or `diamond-yearly`), and dismiss button.
* **Screen & ViewModel Wiring**: Expose `garageNudgeEvent` StateFlow in `EquipmentViewModel` and `DeviceListViewModel`.
* **Debug Controls (`SupporterDebugSection.kt`)**: 1-tap Gold/Diamond preview and flag reset in `BuildConfig.DEBUG`.

### UI Consistency (Rule 23)
* **Reference screen / component**: `FirstJubileeCelebrationDialog.kt`, `SwapOrUpgradeDialog.kt`, `SupporterComponents.kt`.
* **Reused components**: Material 3 `AlertDialog`, `Button`, `OutlinedButton`, `Text`, `Icon`, `Card`.
* **Theme tokens**: `MaterialTheme.shapes.extraLarge`, padding `16.dp` / `12.dp`, `colorScheme.primary`, `surface`, `onSurfaceVariant`.
* **New one-off styles & justification**: None; strictly standard Material 3 tokens.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Strings & Localization (9 Locales)**: Add humorous copy and debug strings to `res/values*/strings.xml`.
2. **DataStore Persistence**: Add nudge boolean flags and reset helper in `SettingsDataStore.kt`.
3. **Domain Evaluator & Unit Tests**: Implement `GarageLeagueDetector.kt` and `GarageLeagueDetectorTest.kt`.
4. **UI Composable Dialog**: Implement `GarageDetectorDialog.kt` and wire optional pre-selection in `SupporterScreen.kt`.
5. **ViewModel Integration**: Wire post-save triggers in `EquipmentViewModel.kt` and `DeviceListViewModel.kt`.
6. **Debug Controls**: Add 1-tap Gold/Diamond previews and reset button in `SupporterDebugSection.kt`.
7. **Targeted Tests & Verification**: Run `./gradlew testDebugUnitTest --tests "*GarageLeagueDetector*" --tests "*TranslationParityTest*"` and `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Execute targeted unit suite (`TST-SET-088.1`..`4`) followed by full test suite regression in Stage 5.
* **Rollback**: Branch isolation on `feature/ATT-5299`; full revert possible without touching `develop`.
