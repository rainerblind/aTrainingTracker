# Stage 1 Analysis: ATT-51 - [Feature] Monetize

**Ticket**: [ATT-51](https://rainerblind.atlassian.net/browse/ATT-51)  
**Sub-task**: [ATT-5330](https://rainerblind.atlassian.net/browse/ATT-5330) (`[Analysis]`)  
**Parent Epic**: None  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Branch**: `feature/ATT-51`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
* Introduce a fair, transparent "Pay what you want" monetization model to fund continuous development and test hardware acquisition for future sensor integrations (Radar, Running Power, WearOS).
* Consolidates ATT-4858: Replace ambiguous external-only device counts with a uniform `MAX_FREE_PAIRED_DEVICES = 2` across all protocols (`SMARTPHONE`, `BLUETOOTH_LE`, `ANT_PLUS`), with a 1-tap Swap-or-Upgrade dialog when pairing a 3rd device.
* Grandfather all existing loyal athletes into lifetime Pioneer status (`★ PIONEER ★` with unlimited devices) if at least one workout was recorded before `2026-11-30 23:59:59` (`PIONEER_CUTOFF_TIMESTAMP`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Audit of Fresh Install Defaults (`DevicesDatabaseManager.java`)**:
  * In `addLocationDevicesToDb()`, `SPEED_AND_LOCATION_GOOGLE_FUSED` currently sets `PAIRED = true`. Combined with `GPS` (`PAIRED = true`) and `ALTITUDE_FROM_PRESSURE` (`PAIRED = true` on phones with a barometer), fresh installs start with 3 paired devices, instantly exceeding the Free limit. Setting `GOOGLE_FUSED` to `PAIRED = false` achieves the intended `2/2` baseline (`GPS` + `Barometer`).
  * `DevicesDatabaseManagerOrderingTest.kt` line 209 explicitly asserts `assertEquals(1, devices[1].third)`. Changing `GOOGLE_FUSED` to `PAIRED = false` requires aligning this assertion to `0`.
* **Independent Peer Review & Verification**:
  * Audited `DeviceListViewModel.onPairedChanged()`: Currently toggles pairing directly in `DeviceDataRepository.updateDevice()` without quota checks. Pairing a 3rd device in Free tier must intercept and show the Swap-or-Upgrade dialog.
  * Audited `WorkoutSummariesDatabaseManager.java`: `TIME_START` is stored as `DATETIME DEFAULT CURRENT_TIMESTAMP`. A query for `TIME_START < PIONEER_CUTOFF_TIMESTAMP` provides an efficient, reliable lifetime check.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Set `SPEED_AND_LOCATION_GOOGLE_FUSED` default `PAIRED = false` in `DevicesDatabaseManager`; update `DevicesDatabaseManagerOrderingTest`.
  * Implement `ProFeatureManager` + `SettingsDataStore`: Lifetime Pioneer check (`TIME_START < '2026-11-30 23:59:59'`), active tier resolution, and `BuildConfig.DEBUG` tier override in `AdvancedTuningScreen`.
  * Enforce `MAX_FREE_PAIRED_DEVICES = 2` in `DeviceListViewModel`: show Swap-or-Upgrade Dialog (1-tap swap recommending `Barometer` outdoor / `GPS` indoor, or upgrade).
  * Supporter Screen with 5 base plans (`bronze-monthly`, `bronze-yearly`, `silver-yearly` [Recommended], `gold-yearly`, `diamond-yearly`), hardware voting, Google Play Billing lifecycle, and Drawer/Settings access.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Custom backend payment servers (serverless via Google Play Billing + Firebase Analytics vote events).
  * Restricting core workout recording, cockpit, telemetry, routes, offline maps, or data export (100% free forever).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-024.6` (`DevicesDatabaseManager.java`).
* **Historical Origin & Commit Trace**: Commit `fc28443c` (`ATT-3961` / `ATT-4375`, Sprint `2026-43.2`).
* **Root Reason for Existing Formulation**: Ensured insertion order `GPS -> FUSED -> NETWORK` aligned autoincrement `_id` with priority ranking. `FUSED` was set to `paired=true` purely by legacy default.
* **Preservation of Core Invariants**: Changing `GOOGLE_FUSED` to `paired=false` preserves autoincrement `_id` sequence (1=GPS, 2=FUSED, 3=NETWORK) and priority rank while freeing the 2nd slot for `ALTITUDE_FROM_PRESSURE`.
* **Net-New Requirements**: `REQ-MON-001` (2-Device Free Quota), `REQ-MON-002` (Swap-or-Upgrade Dialog), `REQ-MON-003` (Pioneer Cutoff), `REQ-MON-004` (Supporter Tiers & Hardware Voting), `REQ-MON-005` (Debug Override).

---

## 5. Architectural Strategy & High-Level Solution
* **Data Layer**:
  * Add `hasWorkoutBefore(cutoffTimestamp)` in `WorkoutSummariesDatabaseManager`.
  * Persist `isPioneerEarlyAdopter`, `supporterTier`, `supporterTierOverride`, and `hardwareVote` in `SettingsDataStore`.
* **Domain Layer**:
  * `ProFeatureManager`: Computes effective tier (`Override` > `Pioneer` > `BillingSubscription`), exposes `isProActive()` and quota check.
* **UI Layer**:
  * `DeviceListViewModel`: Intercept pairing toggle when `totalPaired >= 2` and `!isProActive()`, triggering `SwapOrUpgradeDialog`.
  * `SwapOrUpgradeDialog`: One-tap swap list (preselecting outdoor/indoor recommendation) + direct link to Supporter screen.
  * `SupporterScreen` (`NavRoutes.SUPPORTER`): 5 base-plan cards, hardware vote chips, Play Billing purchase flow.
  * `AppNavigationDrawer`: Pioneer/Supporter badge in `DrawerHeader` and dedicated drawer destination.
  * `AdvancedTuningScreen`: Supporter tier override selector gated by `BuildConfig.DEBUG`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. All tracking, navigation, sensor processing, and local file exports remain 100% free and fully functional.
  2. Existing user database pairing states remain intact across app updates.
  3. Pioneer status is permanent and irrevocable once unlocked.
* **Risk Rating**: **MEDIUM** — Touches device pairing flow across protocols and adds Google Play Billing, mitigated by architecture isolation in `ProFeatureManager` and `BuildConfig.DEBUG` tier override for deterministic testing.
