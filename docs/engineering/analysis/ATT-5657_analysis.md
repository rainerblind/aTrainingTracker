# Stage 1 Analysis: ATT-5657 - SwapOrUpgradeDialog & SensorQuotaReconciliationManager: Never recommend Google Fused (or the sole Location sensor) for unpairing

**Ticket**: [ATT-5657](https://rainerblind.atlassian.net/browse/ATT-5657)  
**Parent Epic**: Standalone  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.9`  
**Branch**: `feature/ATT-5657`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
On Free tier (`!isProActive()`, limit 2 paired devices), pairing a 3rd device triggers `SwapOrUpgradeDialog`. When `Google Fused` and an external sensor (e.g. `my cad`) are currently paired, the dialog proposes `Google Fused` with the `Empfehlung` badge and preselects it for unpairing. Unpairing the sole location sensor degrades tracking by stripping the app of its primary speed/location provider.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: In `SensorQuotaReconciliationManager.kt` (`determineRecommendedUnpairDeviceId`, lines 214–223), after checking for `ALTITUDE_FROM_PRESSURE` and `hasFused && gps != null`, the algorithm falls back to `pairedDevices.firstOrNull()?.id ?: -1L`. Because internal smartphone sensors are seeded first in `DevicesDatabaseManager` (index 0), `Google Fused` is always `firstOrNull()`.
* **Independent Peer Review & Verification**:
  * Confirmed: `SensorQuotaReconciliationManagerTest.kt` line 357 explicitly asserted the buggy fallback (`assertEquals(2L, manager.determineRecommendedUnpairDeviceId(listOf(pairedFused, pairedHrm)))`).
  * In addition, `SwapOrUpgradeDialog.kt` line 58 also contained an unsafe fallback `currentlyPairedDevices.firstOrNull()?.id ?: -1L` if `recommendedUnpairDeviceId` were invalid or -1L.
  * Chesterton's fence check confirmed `ATT-5492` removed `SPEED_AND_LOCATION_NETWORK`, leaving only `SPEED_AND_LOCATION_GOOGLE_FUSED` and `SPEED_AND_LOCATION_GPS` as location types.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Ensure `determineRecommendedUnpairDeviceId` never recommends `SPEED_AND_LOCATION_GOOGLE_FUSED` (nor the sole location sensor `SPEED_AND_LOCATION_GPS`) when any non-location sensor is paired.
  * If a location sensor is paired alongside non-location sensor(s), prioritize recommending the non-location sensor (disconnected first, oldest `lastSeen` second).
  * Harden `SwapOrUpgradeDialog.kt` fallback selection so it never defaults to a sole location sensor.
* **Out-of-Scope Non-Goals**:
  * No changes to `determineDefaultKeepDeviceIds` (already correctly prioritizes Google Fused in Slot 1).
  * No modification to Free tier quota limit ($N=2$, `MAX_FREE_PAIRED_DEVICES`).
  * No changes to database schema or `DevicesDatabaseManager`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-SET-084` (`REQ-SET-084.2`, Smart Recommendation Preselection) in `docs/requirements/req_settings_profiles.md`.
* **Historical Origin & Commit Trace**: `ATT-51` (initial swap dialog), `ATT-5490` (`3db0fc22`, moved to manager; added `hasFused && gps != null`), `ATT-5492` (`fb0068af`, excised network location), `ATT-5661` (appreciative copy).
* **Root Reason for Existing Formulation**: `pairedDevices.firstOrNull()` was assumed to be an innocuous fallback, overlooking that index 0 in `Devices.db` is always the internal location sensor.
* **Preservation of Core Invariants**: `ALTITUDE_FROM_PRESSURE` is still recommended first if paired; `GPS` is recommended if both `Google Fused` and `GPS` are paired; total paired sensors $\le 2$ invariant preserved.

---

## 5. Architectural Strategy & High-Level Solution
* In `SensorQuotaReconciliationManager.determineRecommendedUnpairDeviceId`:
  1. Recommend `ALTITUDE_FROM_PRESSURE` if present.
  2. If both `SPEED_AND_LOCATION_GOOGLE_FUSED` and `SPEED_AND_LOCATION_GPS` are present, recommend `SPEED_AND_LOCATION_GPS`.
  3. Filter for non-location sensors (`it.deviceType != GOOGLE_FUSED && it.deviceType != GPS`). If present, recommend the non-location sensor (unconnected first, then oldest `lastSeen`).
  4. Only fall back to `pairedDevices.firstOrNull()` if only location sensors exist.
* In `SwapOrUpgradeDialog.kt`: Harden the fallback selector to filter for non-location sensors before defaulting to `firstOrNull()`.
* Update tests in `SensorQuotaReconciliationManagerTest.kt` and `DeviceListViewModelQuotaTest.kt` to verify that `Google Fused` and sole `GPS` are never recommended when paired with remote sensors.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Base-tier pairing limit ($\le 2$ sensors) is strictly preserved.
  2. Unselected sensors remain intact with names and calibrations in `Devices.db`.
  3. Zero regressions in device list pairing or downgrade reconciliation.
* **Risk Rating**: **LOW** — Self-contained priority logic in `determineRecommendedUnpairDeviceId` and Compose fallback; pure unit-testable domain logic with no database migrations or service alterations.
