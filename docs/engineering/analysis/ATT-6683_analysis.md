# Stage 1 Analysis: ATT-6683 - GoogleFusedSpeedAndLocationDevice & Map Location Clients: Audit and deduplicate 7 concurrent FusedLocationProviderClient registrations

**Ticket**: [ATT-6683](https://rainerblind.atlassian.net/browse/ATT-6683)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.2`  
**Branch**: `feature/ATT-6683`  
**Author**: AI Agent 1 (Implementer - Pro)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
During live diagnostic inspection of `dumpsys activity service com.google.android.gms/com.google.android.location.internal.GoogleLocationManagerService` on Pixel 10 (Sprint 2026-46.1 review of `ATT-6256`), `com.atrainingtracker.debug` registered 7 simultaneous active `LocationRequest` listeners (`interval=1000ms, fastest=500ms, PRIORITY_HIGH_ACCURACY`).
Consequently, Google Play Services' `FusedLocation` logged 6 times every second:
`I FusedLocation: (REDACTED) location delivery to %s blocked - too fast`

The goal is to audit all location registration call sites across `SpeedAndLocationDevice_GoogleFused`, `DeviceManager`, `BANALService`, and map components, prevent duplicate listener registrations via an explicit `mUpdatesRegistered` guard, and ensure `removeLocationUpdates` is reliably called on lifecycle teardown.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * **Call-Site Audit**:
    * `SpeedAndLocationDevice_GoogleFused.java`: Lacks an `mUpdatesRegistered` guard (unlike `SpeedAndLocationDevice_GPS`, which enforces `if (mUpdatesRegistered) return;`).
    * `checkOrReRegisterLocationUpdates()`: Called repeatedly by `BANALService.checkOrInitializeLocationDevices()` across the UI/service lifecycle (`MainActivityWithNavigation.onResume`, `ControlTrackingScreen` composition & start button, `TrainingApplication.startTracking`, `TrackerService.onServiceConnected`, `TrackerService.startTracking`). In typical tracking entry flows, this method is invoked 6–7 times. Because each call unconditionally executed `LocationServices.FusedLocationApi.requestLocationUpdates(...)` when connected, Google Play Services created redundant concurrent listeners on the binder interface.
    * `shutDown()` in `SpeedAndLocationDevice_GoogleFused.java`: Only called `mGoogleApiClient.disconnect()`. It never invoked `LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this)`, leaking active registrations in Google Play Services until client process termination.
    * Map components audit: `ManualClusterScreen.kt` is the only screen setting `isMyLocationEnabled = currentLocation != null` (for cluster apex editing). `ATrainingTrackerMap.kt` and other maps use custom Compose marker overlays fed by `currentLocationFlow` (`isMyLocationEnabled = false`), avoiding duplicate map location clients.

---

## 3. Scope Grounding & Non-Goals (ATT-1250)
* **In-Scope Goals**:
  * Guard `SpeedAndLocationDevice_GoogleFused` with `mUpdatesRegistered` state flag across `onConnected()`, `checkOrReRegisterLocationUpdates()`, and `shutDown()`.
  * Expose `public synchronized boolean isLocationUpdatesRegistered()` for testability and parity with `SpeedAndLocationDevice_GPS`.
  * Reliably invoke `LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this)` within `shutDown()`, handling disconnect and exceptions safely.
  * Reset `mUpdatesRegistered = false` on `onConnectionSuspended()`, `onConnectionFailed()`, and `shutDown()`.
  * Add unit tests in `SpeedAndLocationDeviceGoogleFusedTest.kt` verifying deduplication, idempotent re-registration, clean teardown, and exception shielding.
* **Out-of-Scope Non-Goals**:
  * Migration to `FusedLocationProviderClient` (strictly locked by `SystemContextContractTest.kt` and `SYSREQ-TRK-004` to maintain `GoogleApiClient` / `LocationServices.FusedLocationApi` architecture).
  * Modifying `SpeedAndLocationDevice_GPS` or Compose map UI behavior.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-STB-004` (*Fresh install location provider attachment*, `ATT-3045`). Refined by net-new **`REQ-LOC-001`** (*Google Fused Location Registration Idempotency & Teardown Lifecycle*).
* **Historical Origin & Commit Trace**: Commit `53ba5fa8` (`ATT-3045`).
* **Root Reason for Existing Formulation**: `ATT-3045` introduced `checkOrReRegisterLocationUpdates()` to re-attempt location attachment after late runtime permission grants. While `SpeedAndLocationDevice_GPS` included an `mUpdatesRegistered` guard and teardown unregistration, these were omitted from `SpeedAndLocationDevice_GoogleFused`.
* **Preservation of Core Invariants**: Retains full compatibility with permission recovery on fresh install while ensuring strictly at most one location listener is active at any time.

---

## 5. Architectural Strategy & High-Level Solution
* Introduce `private boolean mUpdatesRegistered = false` in `SpeedAndLocationDevice_GoogleFused`.
* In `onConnected()`: Check `if (mUpdatesRegistered) return;`, request updates, and set `mUpdatesRegistered = true`.
* In `checkOrReRegisterLocationUpdates()`: Return early if `mUpdatesRegistered`.
* In `shutDown()`: If `mGoogleApiClient != null && mGoogleApiClient.isConnected()`, call `LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this)` wrapped in `try-catch`, call `disconnect()`, and set `mUpdatesRegistered = false`.
* Reset `mUpdatesRegistered = false` in `onConnectionSuspended()` and `onConnectionFailed()`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Exactly 1 active `LocationRequest` registration in Google Play Services.
  2. Zero logcat warnings (`location delivery to %s blocked - too fast`).
  3. Clean unregistration upon device teardown / service destruction.
* **Risk Rating**: **LOW** (Defensive state guard and clean lifecycle unregistration; zero breaking API changes).
