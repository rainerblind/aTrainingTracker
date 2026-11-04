# Stage 2: Requirement & Test Specification - ATT-51: Monetize

**Ticket**: [ATT-51](https://rainerblind.atlassian.net/browse/ATT-51)  
**Sub-task**: [ATT-5331](https://rainerblind.atlassian.net/browse/ATT-5331) (`[Req & Test Spec]`)  
**Parent Epic**: None  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Requirement Mapping**: `REQ-SET-083`..`REQ-SET-087`, `REQ-TRK-024.6`  
**Test Spec ID**: `TST-SET-083`..`TST-SET-087`, `TST-LOC-001`  
**Branch**: `feature/ATT-51`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Requirement Specification (REQ-SET-083..087, REQ-TRK-024.6)

### 1.1 Problem Statement & Rationale
Introduce a transparent "Pay what you want" monetization model funding test hardware acquisition and continuous development. Enforce uniform `MAX_FREE_PAIRED_DEVICES = 2` across all protocols (`SMARTPHONE`, `BLE`, `ANT_PLUS`), grant permanent lifetime Pioneer status (`★ PIONEER ★`) for workouts before `2026-11-30 23:59:59`, provide a 1-tap Swap-or-Upgrade dialog on pairing a 3rd device, and offer 5 Google Play subscription tiers with hardware voting.

### 1.2 Functional & Architectural Requirements
1. **Fresh Install Baseline & Insertion Order (`REQ-TRK-024.6`, `REQ-SET-083.2`)**: In `DevicesDatabaseManager.addLocationDevicesToDb()`, insert `GPS` (paired=true), `GOOGLE_FUSED` (paired=false), and `NETWORK` (paired=false), guaranteeing autoincrement `_id` priority order while establishing the `2/2` Free baseline (`GPS` + `ALTITUDE_FROM_PRESSURE`).
2. **Uniform 2-Device Free Quota (`REQ-SET-083.1`)**: `DeviceListViewModel` SHALL limit paired devices across all protocols to `MAX_FREE_PAIRED_DEVICES = 2` in Free tier (`!isProActive()`).
3. **One-Tap Swap-or-Upgrade Dialog (`REQ-SET-084`)**: Intercept 3rd device pairing in Free tier with `SwapOrUpgradeDialog`, preselecting `Barometer` for outdoor / `GPS` for indoor, executing atomic swaps, and linking to `SupporterScreen`.
4. **Lifetime Pioneer Status (`REQ-SET-085`)**: `WorkoutSummariesDatabaseManager.hasWorkoutBefore('2026-11-30 23:59:59')` queries for pre-cutoff workouts; `SettingsDataStore` persists permanent `isPioneerEarlyAdopter = true`.
5. **5-Tier Google Play Subscriptions & Hardware Voting (`REQ-SET-086`)**: Integrate `att_supporter_sub` across 5 base plans (`bronze-monthly`, `bronze-yearly`, `silver-yearly` [Recommended], `gold-yearly`, `diamond-yearly`), hardware voting selector (`setObfuscatedProfileId("vote:<hw>")` + Firebase event), and drawer badge.
6. **Debug Override & ProFeatureManager (`REQ-SET-087`)**: `ProFeatureManager` resolves tier hierarchy (`BuildConfig.DEBUG` Override > Pioneer > Subscription). Expose tier selector in `AdvancedTuningScreen` strictly gated by `if (BuildConfig.DEBUG)`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-024.6` (`DevicesDatabaseManager.java`).
* **Historical Origin & Commit Trace**: Commit `fc28443c` (`ATT-3961` / `ATT-4375`, Sprint `2026-43.2`).
* **Root Reason for Existing Formulation**: Ensured insertion order `GPS -> FUSED -> NETWORK` aligned autoincrement `_id` with priority ranking. `FUSED` was set to `paired=true` purely by legacy default.
* **Preservation of Core Invariants**: Changing `GOOGLE_FUSED` to `paired=false` preserves autoincrement `_id` sequence (1=GPS, 2=FUSED, 3=NETWORK) and priority rank while freeing the 2nd slot for `ALTITUDE_FROM_PRESSURE`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (2/2 Free Baseline)**: *Given* fresh install with barometer, *When* initialized, *Then* exactly 2 devices are paired (`GPS` + `Altitude from pressure`).
* **AC-2 (Swap Interception)**: *Given* Free tier with 2 paired devices, *When* pairing a 3rd device, *Then* `SwapOrUpgradeDialog` appears and 1-tap swap maintains total paired $\le 2$.
* **AC-3 (Pioneer Recognition)**: *Given* a workout before `2026-11-30 23:59:59`, *When* evaluated, *Then* `isPioneerEarlyAdopter` is permanently true with unlimited devices.

---

## 2. Test Specification (TST-SET-083..087, TST-LOC-001)

### Test Case 1: `testFreeQuotaAndFreshInstallDefaults` (`TST-SET-083`)
* **Scope**: Unit & Contract Test (`DevicesDatabaseManagerOrderingTest.kt`, `DeviceListViewModelTest.kt`)
* **Expected Result**: `GOOGLE_FUSED` starts unpaired (`0`); total paired count $\le 2$ enforced in Free tier.

### Test Case 2: `testSwapOrUpgradeDialogFlow` (`TST-SET-084`)
* **Scope**: UI & ViewModel Test (`DeviceListViewModelTest.kt`, `SwapOrUpgradeDialogTest.kt`)
* **Expected Result**: 3rd device pairing triggers dialog; 1-tap swap unpairs recommended device and pairs new device atomically.

### Test Case 3: `testPioneerEarlyAdopterCutoff` (`TST-SET-085`)
* **Scope**: Database & Repository Test (`WorkoutSummariesDatabaseManagerTest.kt`, `ProFeatureManagerTest.kt`)
* **Expected Result**: `TIME_START <= PIONEER_CUTOFF_TIMESTAMP` unlocks permanent Pioneer status and Pro features.

### Test Case 4: `testSupporterTiersAndDebugOverride` (`TST-SET-086`, `TST-SET-087`)
* **Scope**: Unit & Integration Test (`ProFeatureManagerTest.kt`, `BillingClientLifecycleTest.kt`)
* **Expected Result**: Priority order (Override > Pioneer > Subscription) resolves correctly; override inert in release builds.

### Test Case 5: 9-Language Localization Parity (`TST-LOC-001`)
* **Scope**: Localization Parity (`TranslationParityTest.kt`)
* **Expected Result**: 100% parity across all 9 locales for all monetization strings.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-083` | Unit/Contract | `DevicesDatabaseManagerOrderingTest.kt` | `REQ-SET-083`, `REQ-TRK-024.6` | Specified |
| `TST-SET-084` | ViewModel/UI | `DeviceListViewModelTest.kt` | `REQ-SET-084` | Specified |
| `TST-SET-085` | Database/Repo | `WorkoutSummariesDatabaseManagerTest.kt` | `REQ-SET-085` | Specified |
| `TST-SET-086` | Domain/UI | `SupporterScreenTest.kt` | `REQ-SET-086` | Specified |
| `TST-SET-087` | Domain/Settings| `ProFeatureManagerTest.kt` | `REQ-SET-087` | Specified |
| `TST-LOC-001` | Localization | `TranslationParityTest.kt` | `REQ-UI-106`, `REQ-SET-083..087` | Specified |
