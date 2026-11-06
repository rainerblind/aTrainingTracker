# Stage 1 Analysis: ATT-5491 - Location sensors: Prioritize Google Fused over GPS in MySensorManager and pair Google Fused by default on fresh install

**Ticket**: [ATT-5491](https://rainerblind.atlassian.net/browse/ATT-5491)  
**Parent Epic**: None | **Story Points**: 1 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.8` | **Branch**: `feature/ATT-5491`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-06  

---

## 1. Problem Statement & Motivation
In outdoor and urban tracking, Google Play Services Fused Location Provider (`SPEED_AND_LOCATION_GOOGLE_FUSED`) provides faster initial fixes and more stable position/speed vectors than raw GPS (`SPEED_AND_LOCATION_GPS`). However:
1. `MySensorManager.java` (L49–79) lists `SPEED_AND_LOCATION_GPS` *before* `SPEED_AND_LOCATION_GOOGLE_FUSED` in `SPEED_PRIORITY`, `ALTITUDE_PRIORITY`, and `LOCATION_PRIORITY`.
2. `DevicesDatabaseManager.java` (`addLocationDevicesToDb`, L1344–1360) pairs `SPEED_AND_LOCATION_GPS` by default (`PAIRED = true`) and leaves `SPEED_AND_LOCATION_GOOGLE_FUSED` unpaired (`PAIRED = false`) on fresh installs.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**: `SPEED_AND_LOCATION_GPS` is the historical default inherited from early Android versions before Google Fused Location was integrated. In `ATT-3961` and `ATT-4375`, Google Fused was re-enabled but placed behind GPS across all priority lists, and fresh DB initialization retained GPS as the paired location sensor.
* **Independent Peer Review & Verification**:
  * Confirmed `MySensorManager.java`: `SPEED_PRIORITY` (L55–56), `ALTITUDE_PRIORITY` (L70–71), and `LOCATION_PRIORITY` (L76–77) place `GPS` immediately ahead of `GOOGLE_FUSED`.
  * Confirmed `DevicesDatabaseManager.java` (L1344–1360): hardcodes `PAIRED = true` for GPS and `PAIRED = false` for Google Fused.
  * Verified Play Services check pattern: `DeviceManager.java` (L376, L455) checks `GooglePlayServicesUtil.isGooglePlayServicesAvailable(mContext) == ConnectionResult.SUCCESS`. This exact pattern can be cleanly reused in `addLocationDevicesToDb`.
  * Audited DB upgrade safety: `addLocationDevicesToDb` is only invoked in `onCreate` and `onUpgrade(oldVersion <= 6)`. Since current DB version is 9, existing user pairing states in SQLite will not be overwritten.

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit (Rule 68)
* **In-Scope Goals**:
  * Prioritize `SPEED_AND_LOCATION_GOOGLE_FUSED` ahead of `SPEED_AND_LOCATION_GPS` in `SPEED_PRIORITY`, `ALTITUDE_PRIORITY`, and `LOCATION_PRIORITY` (`MySensorManager.java`).
  * In `DevicesDatabaseManager.DevicesDbHelper.addLocationDevicesToDb`, default `SPEED_AND_LOCATION_GOOGLE_FUSED` to `PAIRED = true` and `SPEED_AND_LOCATION_GPS` to `PAIRED = false` when Google Play Services is available; fall back to GPS paired if unavailable.
  * Update living requirements (`REQ-TRK-024`) and unit tests (`MySensorManagerPriorityTest`, `DevicesDatabaseManagerOrderingTest`, `SensorSourceDialogPriorityTest`).
* **Out-of-Scope Non-Goals**:
  * Reordering `DeviceType.java` enum definitions or altering `DeviceType.ordinal()`.
  * Overwriting pairing states for existing users (no DB schema bump or migration script).
  * Altering hardware sensor priority (`RUN_SPEED`, `BIKE_SPEED`, `BIKE_POWER`, `ALTITUDE_FROM_PRESSURE` remain top-priority).
* **Complexity Rating**: **1 SP (Trivial)** (Priority array reordering, conditioned boolean flag, test suite update).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-TRK-024` (`REQ-TRK-024.1`, `REQ-TRK-024.6`) targeting `MySensorManager.java` and `DevicesDatabaseManager.java`.
* **Historical Origin & Commit Trace**: `ATT-3961` (`fc28443c`, Sprint `2026-43.1`) and `ATT-4375` (Sprint `2026-43.2`).
* **Root Reason for Existing Formulation**: `GPS` was the original native location source; `GOOGLE_FUSED` was placed as a secondary fallback to avoid disrupting existing installs without checking Play Services availability.
* **Preservation of Core Invariants**:
  1. Dedicated wheel speed / power sensors still supersede location sensors (`BIKE_SPEED > GOOGLE_FUSED > GPS > NETWORK`).
  2. Barometer altitude still supersedes location altitude (`PRESSURE > POSITION > GOOGLE_FUSED > GPS > NETWORK`).
  3. De-Googled devices without Play Services automatically fall back to GPS pairing out-of-the-box.

---

## 5. Architectural Strategy & High-Level Solution
* **MySensorManager.java**: Swap relative order of `SPEED_AND_LOCATION_GOOGLE_FUSED` and `SPEED_AND_LOCATION_GPS` in `SPEED_PRIORITY`, `ALTITUDE_PRIORITY`, and `LOCATION_PRIORITY`. `getPriorityRank` automatically returns the updated canonical ranks.
* **DevicesDatabaseManager.java**: In `addLocationDevicesToDb`, evaluate `GooglePlayServicesUtil.isGooglePlayServicesAvailable(mContext) == ConnectionResult.SUCCESS` to conditionally set `PAIRED` flag.
* **Downstream Alignment**: Update tests in `MySensorManagerPriorityTest.kt`, `DevicesDatabaseManagerOrderingTest.kt`, and `SensorSourceDialogPriorityTest.kt`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero regression in dedicated speed/altitude sensor precedence; fallback to raw GPS on de-Googled environments; existing user DB pairings untouched.
* **Risk Rating**: **LOW** (Declarative priority reordering and conditioned fresh-install flag; verified fallback for de-Googled devices; zero schema changes).
