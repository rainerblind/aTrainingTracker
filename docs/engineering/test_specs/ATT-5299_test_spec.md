# Stage 2: Requirement & Test Specification - ATT-5299: Supporter N+1 Garage Detector

**Ticket**: [ATT-5299](https://rainerblind.atlassian.net/browse/ATT-5299)  
**Sub-task**: [ATT-5341](https://rainerblind.atlassian.net/browse/ATT-5341) (`[Req & Test Spec]`)  
**Parent Epic**: None (*Monetization & Supporter Experience*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Requirement Mapping**: `REQ-SET-088` (*Supporter N+1 Garage Detector & Upgrade Prompts*)  
**Test Spec ID**: `TST-SET-088`  
**Branch**: `feature/ATT-5299`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Requirement Specification (REQ-SET-088)

### 1.1 Problem Statement & Rationale
Athletes adding multiple bikes, shoes, and sensors reach Gold or Diamond scale gear setups without being prompted for higher supporter tiers. The N+1 Garage Detector identifies these milestones upon item creation/pairing and presents a good-humored, self-ironic upgrade prompt with the matching plan pre-selected.

### 1.2 Functional & Architectural Requirements
1. **League Qualification (`REQ-SET-088.1`)**: Classify into *Diamond League* ($\ge 4$ bikes OR $\ge 8$ remote sensors OR $\ge 2$ `BIKE_POWER` sensors) and *Gold League* ($\ge 3$ bikes OR $\ge 4$ shoes OR $\ge 5$ remote sensors OR $\ge 1$ `BIKE_POWER`). Diamond takes precedence.
2. **Tier Monotonicity (`REQ-SET-088.2`)**: Nudge only if current effective `SupporterTier` is below the qualified league (`< GOLD` for Gold; `< DIAMOND` for Diamond).
3. **Event-Driven Post-Save Triggers (`REQ-SET-088.3`)**: Trigger asynchronously after equipment save (`EquipmentViewModel`) or sensor pairing (`DeviceListViewModel`).
4. **Non-Spam Persistence (`REQ-SET-088.4`)**: Persist `hasShownGoldGarageNudge` and `hasShownDiamondGarageNudge` in `SettingsDataStore`; prompt at most once per league.
5. **Humorous Dialog & Pre-Selected Navigation (`REQ-SET-088.5`)**: Render dynamic counts in `GarageDetectorDialog` with 1-tap navigation to `SupporterScreen` (`gold-yearly` or `diamond-yearly` pre-selected) and "Später" dismiss.
6. **Diagnostic Debug Preview (`REQ-SET-088.6`)**: Expose 1-tap preview and flag reset in `SupporterDebugSection` (`BuildConfig.DEBUG`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Gold Trigger)**: Given an athlete on Free/Silver with 2 bikes, when saving a 3rd bike, then Gold League is detected and `GarageDetectorDialog` offers the Gold upgrade path.
* **Criterion 2 (Diamond Trigger)**: Given an athlete on Gold with 7 sensors, when pairing an 8th sensor, then Diamond League is detected and `GarageDetectorDialog` offers Diamond Sponsor.
* **Criterion 3 (Non-Spam Suppression)**: Given `hasShownGoldGarageNudge == true`, when adding another bike, then the Gold prompt is suppressed.

### 1.4 System Invariants
* Database operations (equipment save, sensor pairing) must commit before dialog triggers.
* Tier monotonicity is strictly preserved; debug controls are compiled out in release builds.

---

## 2. Test Specification (TST-SET-088)

### Test Case 1: Pure League Evaluation (`TST-SET-088.1`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/monetization/GarageLeagueDetectorTest.kt`
* **Procedure**: Test `evaluateLeague()` against boundary combinations of bikes, shoes, remote sensors, and power meters.
* **Expected Result**: Diamond takes precedence; Gold fires correctly; sub-threshold returns `NONE`.

### Test Case 2: Monotonicity & Non-Spam Gating (`TST-SET-088.2`)
* **Procedure**: Verify prompt eligibility given combinations of current `SupporterTier` (`FREE`..`DIAMOND`) and DataStore nudge flags.
* **Expected Result**: Suppressed if current tier $\ge$ league tier or if flag is already set.

### Test Case 3: 9-Language Localization Parity (`TST-SET-088.3` / `TST-LOC-001`)
* **Target**: `TranslationParityTest.kt` across `res/values*/strings.xml`.
* **Expected Result**: 100% parity across 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with matching `%1$d` positional specifiers.

### Test Case 4: Targeted Suite & Governance Verification (`TST-SET-088.4`)
* **Commands**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.monetization.*"` & `python3 tools/verify_requirement_governance.py`.
* **Expected Result**: All tests PASS and governance audit passes with 0 errors.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-088.1` | Unit | `GarageLeagueDetector.evaluateLeague` | `REQ-SET-088.1` | Specified |
| `TST-SET-088.2` | Unit / Repository | `GarageLeagueDetector.shouldShowNudge` | `REQ-SET-088.2`, `REQ-SET-088.4` | Specified |
| `TST-SET-088.3` | Localization | `TranslationParityTest` | `REQ-SET-088.5`, `TST-LOC-001` | Specified |
| `TST-SET-088.4` | Governance | `verify_requirement_governance.py` | `REQ-SET-088.1`–`.6` | Specified |
