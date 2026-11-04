# Stage 3: Implementation Plan - ATT-51: Monetize

**Ticket**: [ATT-51](https://rainerblind.atlassian.net/browse/ATT-51)  
**Sub-task**: [ATT-5332](https://rainerblind.atlassian.net/browse/ATT-5332) (`[Impl-Plan]`)  
**Parent Epic**: None  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.4` | **Branch**: `feature/ATT-51`  
**Requirement Mapping**: `REQ-SET-083`..`REQ-SET-087`, `REQ-TRK-024.6`  
**Test Mapping**: `TST-SET-083`..`TST-SET-087`, `TST-LOC-001`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-04  

---

## 1. Problem Description & Background
Monetize aTrainingTracker via a fair, transparent "Pay what you want" model funding sensor testing hardware. Enforce `MAX_FREE_PAIRED_DEVICES = 2` across all protocols (`SMARTPHONE`, `BLE`, `ANT_PLUS`), default fresh install to `GPS` + `Barometer` (unpairing `GOOGLE_FUSED`), grandfather pre-cutoff (`<= 2026-11-30 23:59:59`) users with lifetime Pioneer status, show a 1-tap Swap-or-Upgrade dialog on 3rd device pairing, and provide 5 Google Play subscription tiers with hardware voting and a `BuildConfig.DEBUG` tier override.

---

## 2. Proposed Architectural Changes (SWE.2)

### Component 1: Domain & Preferences Layer (`ProFeatureManager`, `SettingsDataStore`)
* Enums: `SupporterTier` (`FREE`, `PIONEER`, `BRONZE`, `SILVER`, `GOLD`, `DIAMOND`), `SupporterTierOverride` (`AUTO`, `FORCE_FREE`, `FORCE_PIONEER`, `FORCE_BRONZE`, `FORCE_SILVER`, `FORCE_GOLD`, `FORCE_DIAMOND`), `HardwareVoteOption`, `SupporterBasePlan` (`bronze-monthly`, `bronze-yearly`, `silver-yearly`, `gold-yearly`, `diamond-yearly` on `att_supporter_sub`).
* In `SettingsDataStore.kt`: Persist `isPioneerEarlyAdopter`, `supporterTier`, `supporterTierOverride`, `hardwareVote`.
* In `ProFeatureManager`: Resolve tier hierarchy (`Override` > `Pioneer` > `BillingSubscription`), exposing `isProActive()` and quota.

### Component 2: Database Layer (`DevicesDatabaseManager`, `WorkoutSummariesDatabaseManager`)
* In `DevicesDatabaseManager.java`: `addLocationDevicesToDb` sets `SPEED_AND_LOCATION_GOOGLE_FUSED` to `PAIRED = false` (`REQ-TRK-024.6`). Expose `getPairedDevicesCount()`, `getPairedDevices()`.
* In `WorkoutSummariesDatabaseManager.java`: Add `hasWorkoutBefore(String cutoffTimestamp)` / `hasPioneerEarlyAdopterWorkout()` (`TIME_START <= '2026-11-30 23:59:59'`).

### Component 3: UI & Presentation Layer (Swap Dialog, Supporter Screen, Drawer, Tuning)
* In `DeviceListViewModel.kt` & `DeviceListScreen.kt`: Intercept pairing toggle when `totalPaired >= 2 && !isProActive()`, emitting `SwapOrUpgradeDialogEvent`.
* `SwapOrUpgradeDialog.kt`: Compose dialog with 1-tap unpair recommendations (outdoor `Barometer`, indoor `GPS`) or Pro upgrade navigation.
* `SupporterScreen.kt` & `BillingViewModel`: 2x3 pricing tier cards, hardware vote selector, Google Play Billing purchase flow.
* `AppNavigationDrawer.kt`: Display `★ PIONEER ★` / Supporter badge in header and menu item.
* `AdvancedTuningScreen.kt` / `AdvancedTuningDialog.kt`: Supporter Tier Override dropdown gated by `if (BuildConfig.DEBUG)`.

### UI Consistency (Rule 23)
* **Reference screen**: `AthleteProfileScreen.kt` / `AdvancedTuningScreen.kt`.
* **Reused components**: `AppDialogActions`, `SingleChoiceSegmentedButtonRow`, `FilterChip`, `Card`, `MaterialTheme.colorScheme`.
* **Theme tokens**: Shapes `RoundedCornerShape(12.dp)`, spacing `8.dp`/`16.dp`, colors `surfaceVariant`, `primary`, `onSurface`.
* **New one-off styles**: None.

---

## 3. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Database & Model Foundation (`REQ-TRK-024.6`, `REQ-SET-083`, `REQ-SET-085`)
* Files: `DevicesDatabaseManager.java`, `WorkoutSummariesDatabaseManager.java`, `DevicesDatabaseManagerOrderingTest.kt`.
* Changes: Default `GOOGLE_FUSED` to `PAIRED = false`; align test assertion `devices[1].third == 0`; add `hasWorkoutBefore()`.

### Step 2: Settings, Domain Models & Pro Feature Manager (`REQ-SET-085`, `REQ-SET-086`, `REQ-SET-087`)
* Files: `SupporterTier.kt`, `SettingsDataStore.kt`, `ProFeatureManager.kt`, `ProFeatureManagerTest.kt`.
* Changes: Implement supporter tier enums, Datastore keys, resolution logic, and unit tests.

### Step 3: Quota Enforcement & 1-Tap Swap Dialog (`REQ-SET-083`, `REQ-SET-084`)
* Files: `DeviceListViewModel.kt`, `DeviceListScreen.kt`, `SwapOrUpgradeDialog.kt`, `DeviceListViewModelTest.kt`.
* Changes: Intercept pairing on quota exceed; implement atomic unpair+pair swap and upgrade trigger.

### Step 4: Supporter Screen, Navigation & Debug Override (`REQ-SET-086`, `REQ-SET-087`, `REQ-LOC-001`)
* Files: `SupporterScreen.kt`, `NavRoutes.kt`, `AppNavigationDrawer.kt`, `AdvancedTuningScreen.kt`, 9 `strings.xml`.
* Changes: Implement supporter pricing UI, drawer badge, debug override dropdown, and 9-locale parity.

### Step 5: Verification & Governance
* Commands:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.database.*" --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.banalservice.ui.devices.devicelist.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.i18n.TranslationParityTest"`
  `python3 tools/verify_requirement_governance.py`

---

## 4. System Invariants & Rollback Plan
* **Core Invariants**: 100% free workout recording, map display, telemetry, and export; irrevocable Pioneer status; thread-safe DB dispatchers.
* **Rollback Plan**: Revert branch `feature/ATT-51` cleanly; database schema remains backward compatible.
