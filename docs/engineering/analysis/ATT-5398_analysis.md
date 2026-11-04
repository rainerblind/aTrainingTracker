# Stage 1 Analysis: ATT-5398 - Subscription Downgrade Sensor Quota Reconciliation: Keep-2-Sensors Selection Dialog when Paired Sensors > 2 in Free Tier

**Ticket**: [ATT-5398](https://rainerblind.atlassian.net/browse/ATT-5398)  
**Sub-task**: [ATT-5419](https://rainerblind.atlassian.net/browse/ATT-5419) (`[Analysis]`)  
**Parent Epic**: None (*Standalone*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-5398`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
The Free-tier pairing quota (`MAX_FREE_PAIRED_DEVICES = 2`, `REQ-SET-083`) is currently enforced only when pairing new devices in `DeviceListViewModel`. When a Supporter subscription expires or drops to Free (`SupporterTier.FREE`, `!isProActive()`), athletes who previously paired 3–8 sensors retain `PAIRED = 1` in `Devices.db`. This leaves active multi-sensor pairing unreconciled. Attempting to pair an additional sensor via `SwapOrUpgradeDialog` only unpairs 1 device, still leaving $> 2$ paired sensors. A friendly Material 3 bottom sheet (`AppModalBottomSheet`) is required to guide the athlete to select 2 active sensors, non-destructively unpairing excess devices while preserving all sensor metadata.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Downgrade Gap RCA**: `ProFeatureManager.effectiveTier` and `isProActive` reactively transition to Free, but no lifecycle/state observer detects `isProActive == false && getPairedDevicesCount() > 2` to trigger quota reconciliation outside active tracking.
* **Multi-Sensor Swap RCA**: `executeSwapAndPair()` in `DeviceDataRepository` assumes `pairedCount == 2` and performs a 1:1 unpair/pair swap; if `pairedCount > 2`, swapping 1 sensor leaves $N - 1 + 1 = N > 2$ devices paired.
* **Independent Peer Review & Verification**:
  * Audited `DeviceListViewModel.kt`: `onPairedChanged()` only gates pairing via `isDevicePairingAllowed()`. `executeSwapAndPair()` lacks multi-sensor pruning.
  * Audited `DevicesDatabaseManager.java` & `DeviceDataRepository.kt`: Setting `PAIRED = 0` via `updateDevice()` only mutates the `paired` column and sends `BANALService.PAIRING_CHANGED`. Custom names, calibration factors, wheel circumferences, and equipment links remain 100% intact.
  * Audited `ATrainingTrackerApp.kt` & `MainActivityWithNavigation.kt`: Reconciliation MUST check `!TrainingApplication.isTracking()` so active workouts are never disrupted.
  * Audited `docs/design_guidelines.md` (Sections 5.3 & 5.8) & `ATT-5399`: Selection prompts MUST be implemented as `AppModalBottomSheet` rather than centered `AlertDialog`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Keep-2-Sensors reconciliation popup (`KeepTwoSensorsBottomSheet`) built as `AppModalBottomSheet` (REQ-UI-149, Section 5.3 & 5.8).
  * Downgrade detection trigger: `!isProActive() && pairedDevicesCount > 2 && !TrainingApplication.isTracking()`.
  * Pre-select 2 sensible defaults (`SPEED_AND_LOCATION_GPS` + `ALTITUDE_FROM_PRESSURE`, or 2 most recent/first paired).
  * Athlete interaction: checkbox selection (max 2 active) or 1-tap confirmation; 1-tap upsell action to `SupporterScreen`.
  * Non-destructive unpairing: setting `PAIRED = 0` on unselected devices in SQLite. If dismissed without selection, apply defaults to prevent quota bypass.
  * Harden `SwapOrUpgradeDialog` & `executeSwapAndPair()` so resulting paired sensor count in Free mode is strictly $\le 2$.
* **Out-of-Scope Non-Goals**:
  * Modifying Google Play Billing SKUs or subscription pricing (`REQ-SET-086`).
  * Deleting devices or wiping calibration/equipment data from `Devices.db`.
  * Triggering reconciliation during active workout recording (`TrainingApplication.isTracking()`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-083` (2-Device Free Quota) and `REQ-SET-084` (Swap-or-Upgrade Dialog). Net-new requirement: `REQ-SET-089` (Subscription Downgrade Sensor Quota Reconciliation).
* **Historical Origin & Commit Trace**: Commit `de0e1d55` (`ATT-51`, Sprint `2026-45.4`).
* **Root Reason for Existing Formulation**: Initial monetization MVP addressed pairing-time quota enforcement, assuming initial 2/2 baseline, without post-expiration downgrade reconciliation.
* **Preservation of Core Invariants**: Retains the 2-sensor Free limit (`REQ-SET-083`), prevents workout disruption, preserves all sensor calibration and equipment links, and follows Material 3 bottom sheet ergonomics.

---

## 5. Architectural Strategy & High-Level Solution
* **Detection & State Flow**: Introduce reactive state in `DeviceListViewModel` (and/or `ATrainingTrackerApp`) evaluating `!isProActive && pairedDevices.size > 2 && !isTracking`.
* **Reconciliation Bottom Sheet**: Author `KeepTwoSensorsBottomSheet.kt` using `AppModalBottomSheet` with `Icons.Default.Devices`, allowing max 2 checked items, confirm button, and Supporter upsell button. Dismissal applies the 2 pre-selected defaults.
* **Repository Batch Prune**: Add `reconcilePairedDevices(keepDeviceIds: Set<Long>)` in `DeviceDataRepository` setting `PAIRED = 0` on unselected devices and broadcasting changes.
* **Swap Hardening**: Update `executeSwapAndPair()` to unpair all excess devices when in Free tier, ensuring strictly $\le 2$ paired sensors.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Data Preservation: Unpaired sensors are NEVER deleted; calibration and equipment links remain intact.
  2. Recording Safety: Zero prompt or unpair actions while `TrainingApplication.isTracking()`.
  3. Quota Integrity: Quota cannot be bypassed by dismissing; swaps strictly enforce $\le 2$ paired sensors.
  4. M3 Sheet Standard: Authored directly as `AppModalBottomSheet` (ATT-5399).
* **Risk Rating**: **LOW** (Declarative Compose bottom sheet and non-destructive SQLite pairing updates; zero risk to tracking engine).
