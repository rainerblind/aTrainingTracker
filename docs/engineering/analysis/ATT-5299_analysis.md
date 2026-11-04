# Stage 1 Analysis: ATT-5299 - Supporter N+1 Garage Detector: Humorous Gold/Diamond Upgrade Nudge

**Ticket**: [ATT-5299](https://rainerblind.atlassian.net/browse/ATT-5299)  
**Sub-task**: [ATT-5340](https://rainerblind.atlassian.net/browse/ATT-5340) (`[Analysis]`)  
**Parent Epic**: None (*Monetization & Supporter Experience*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.4`  
**Branch**: `feature/ATT-5299`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-04  

---

## 1. Problem Statement & Motivation
* As athletes expand their gear setup (e.g., $\ge 3$ bikes, $\ge 4$ running shoes, $\ge 5$ sensors, or multiple power meters), their equipment profile reaches enthusiast ("Gold") or world-class ("Diamond") scale.
* Currently, users who unlocked Bronze, Silver, or hold Pioneer status can accumulate extensive hardware fleets without ever being nudged to upgrade to Gold (50 €/yr) or Diamond (100 €/yr).
* The ideal psychological moment to convert high-end athletes is immediately upon saving a new bike/shoe in equipment management or pairing an additional sensor—presenting a humorous, self-ironic cyclist/runner dialog celebrating their "N+1 Garage" with dynamic item counts and a 1-tap upgrade path.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Current State & Gap Analysis**:
  * `EquipmentDbHelper.addEquipment()` inserts bikes (`frameType in 1..4`, `BSportType.BIKE`) and shoes (`frameType == 0`, `BSportType.RUN`). `EquipmentViewModel` invokes this but lacks post-insertion garage tier evaluation.
  * `DeviceListViewModel.onPairedChanged()` / `executeSwapAndPair()` enforce Free-tier pairing quota (2 devices) via `ProFeatureManager.isDevicePairingAllowed()`, but do not evaluate total registered sensors or power meter density.
  * `ProFeatureManager` and `SettingsDataStore` manage supporter tiers, but lack league qualification logic and one-time display state tracking.
* **Independent Peer Review & Verification (ATT-3534)**:
  * *Threshold Hierarchy*: Diamond League ($\ge 4$ bikes OR $\ge 8$ sensors OR $\ge 2$ power meters) targets `SupporterTier.DIAMOND` (`diamond-yearly`, 100 €/yr) when effective tier < `DIAMOND`. Gold League ($\ge 3$ bikes OR $\ge 4$ shoes OR $\ge 5$ sensors OR $\ge 1$ power meter) targets `SupporterTier.GOLD` (`gold-yearly`, 50 €/yr) when effective tier < `GOLD`.
  * *Power Meter Type Audit*: Verified that `DeviceType.BIKE_POWER` is the authoritative power meter type (the ticket hypothesis note `ROW_POWER` was a typo/hypothetical; `BIKE_POWER` represents power meters in SQLite).
  * *Precedence & Side-Effect Audit (Rule 68)*: Diamond evaluation takes precedence over Gold. Detection runs asynchronously on `Dispatchers.Default` post-save, maintaining zero UI latency and zero interference with sensor reconnect or SQLite transactions.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Event-driven triggers: after adding equipment in `EquipmentViewModel` and after pairing in `DeviceListViewModel`.
  * Domain evaluator `GarageLeagueDetector`: pure/testable evaluation of bikes, shoes, remote sensors, and power meters against tier thresholds and persistent flags.
  * Non-spam persistence in `SettingsDataStore`: `hasShownGoldGarageNudge` and `hasShownDiamondGarageNudge` (nudges at most once per league level).
  * Material 3 `GarageDetectorDialog`: humorous congratulatory copy with dynamic athlete counts, 1-tap button opening `SupporterScreen` (with pre-selected tier), and "Später / Weiter zum Training" dismiss button.
  * `SupporterScreen` optional pre-selected plan support (auto-highlighting Gold or Diamond plan).
  * `SupporterDebugSection` 1-tap preview triggers (Gold/Diamond) and reset button for Sprint Review testing.
  * 9-language localization parity (EN, DE, ES, FR, IT, JA, NL, PL, PT).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No blocking of equipment addition or sensor pairing—the dialog is purely advisory and dismissible.
  * No recurring prompts for the same league level once shown or dismissed.
  * No alterations to Google Play billing purchase verification or subscription polling lifecycles.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement (`REQ-SET-088`), extending `REQ-SET-086` (*Five-Tier Google Play Supporter Subscriptions*) and `REQ-SET-087` (*Debug Supporter Tier Override & Pro Feature Manager Architecture*). No existing requirements modified.

---

## 5. Architectural Strategy & High-Level Solution
* **Domain Evaluator**: Implement `GarageLeagueDetector` to query `EquipmentDbHelper` and `DevicesDatabaseManager` asynchronously and compare against `ProFeatureManager.effectiveTier` and `SettingsDataStore` nudge flags.
* **UI Dialog & Navigation**: Implement `GarageDetectorDialog` composable. When triggered, emit an event in `EquipmentViewModel` / `DeviceListViewModel` to render dialog. On upgrade tap, navigate to `NavRoutes.SUPPORTER` passing pre-selected plan (`gold-yearly` or `diamond-yearly`), while immediately persisting the shown flag.
* **Debug Controls**: Add 1-tap preview buttons and reset action in `SupporterDebugSection` (`BuildConfig.DEBUG`).

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. *Single-Nudge Invariant*: Each garage league level nudges at most once across the app lifetime.
  2. *Tier Monotonicity*: Never nudge if current effective `SupporterTier` is $\ge$ the target league tier.
  3. *Zero Workflow Interruption*: Saving equipment or pairing sensors completes atomically prior to dialog trigger.
  4. *Thread Safety*: All counts and DataStore operations executed off the main thread.
* **Risk Rating**: **LOW** (Modular domain detector and UI dialog; decoupled from sensor telemetry; guarded by DataStore flags and unit tests).
