# Stage 2: Requirement & Test Specification - ATT-6683: GoogleFusedSpeedAndLocationDevice & Map Location Clients: Audit and deduplicate 7 concurrent FusedLocationProviderClient registrations

**Ticket**: [ATT-6683](https://rainerblind.atlassian.net/browse/ATT-6683)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-071` (*Google Fused Location Registration Idempotency & Teardown Lifecycle*)  
**Test Spec ID**: `TST-TRK-071` (*Google Fused Location Registration Idempotency, Deduplication & Teardown Verification Suite*)  
**Branch**: `feature/ATT-6683`  
**Author**: AI Agent 1 (Flash Implementer)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-TRK-071)

### 1.1 Problem Statement & Rationale
During Sprint 2026-46.1 review of `ATT-6256`, diagnostics on Pixel 10 revealed 7 simultaneous active `LocationRequest` registrations in Google Play Services from `com.atrainingtracker.debug`, flooding logcat 6 times per second (`location delivery to %s blocked - too fast`). `SpeedAndLocationDevice_GoogleFused` lacked an `mUpdatesRegistered` guard and clean lifecycle unregistration in `shutDown()`. Establishing an idempotent registration guard and defensive teardown unregistration ensures strictly $\le 1$ active registration.

### 1.2 Functional & Architectural Requirements
* **REQ-TRK-071.1** (*Idempotent Registration Guard*): `SpeedAndLocationDevice_GoogleFused` SHALL maintain an internal boolean flag `mUpdatesRegistered = false`. In `onConnected()` and `checkOrReRegisterLocationUpdates()`, if `mUpdatesRegistered == true`, the device SHALL return early without requesting redundant location updates.
* **REQ-TRK-071.2** (*Registration State Setting*): Upon successfully invoking `LocationServices.FusedLocationApi.requestLocationUpdates(...)`, `SpeedAndLocationDevice_GoogleFused` SHALL set `mUpdatesRegistered = true`.
* **REQ-TRK-071.3** (*Lifecycle Teardown & Clean Unregistration*): In `shutDown()`, if `mGoogleApiClient != null && mGoogleApiClient.isConnected()`, `SpeedAndLocationDevice_GoogleFused` SHALL invoke `LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this)` within a defensive try-catch block, set `mUpdatesRegistered = false`, and call `mGoogleApiClient.disconnect()`.
* **REQ-TRK-071.4** (*Connection State Reset*): Upon `onConnectionSuspended()` or `onConnectionFailed()`, `SpeedAndLocationDevice_GoogleFused` SHALL reset `mUpdatesRegistered = false`.
* **REQ-TRK-071.5** (*Registration State Observability*): `SpeedAndLocationDevice_GoogleFused` SHALL expose `public synchronized boolean isLocationUpdatesRegistered()` for contract testing and parity with `SpeedAndLocationDevice_GPS`.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Idempotent Registration & Deduplication)**:
  * *Given* `SpeedAndLocationDevice_GoogleFused` connected with updates registered (`isLocationUpdatesRegistered() == true`),
  * *When* `checkOrReRegisterLocationUpdates()` is invoked repeatedly (e.g. 7 times across tracking setup),
  * *Then* `FusedLocationApi.requestLocationUpdates(...)` SHALL NOT be called again, maintaining exactly 1 active registration.
* **Criterion 2 (Clean Lifecycle Teardown)**:
  * *Given* `SpeedAndLocationDevice_GoogleFused` with active updates,
  * *When* `shutDown()` is invoked,
  * *Then* `FusedLocationApi.removeLocationUpdates(...)` SHALL be invoked, `isLocationUpdatesRegistered()` SHALL return `false`, and `mGoogleApiClient.disconnect()` SHALL execute cleanly without uncaught exceptions.
* **Criterion 3 (Connection Interruption Reset)**:
  * *Given* an active registered client,
  * *When* `onConnectionSuspended()` or `onConnectionFailed()` is received,
  * *Then* `isLocationUpdatesRegistered()` SHALL reset to `false`.

### 1.4 System Invariants & Chesterton's Fence Audit
* **Architectural Invariant**: Retains `GoogleApiClient` / `FusedLocationApi` architecture locked by `SYSREQ-TRK-004` and `SystemContextContractTest.kt`.
* **Chesterton's Fence Audit**: Refining `REQ-STB-004` (commit `53ba5fa8`, `ATT-3045`). Runtime permission recovery is fully preserved while eliminating redundant parallel registrations and teardown leaks.

---

## 2. Test Specification (TST-TRK-071)

### Test Case 1: Deduplication & Idempotency Test (`TST-TRK-071.1`)
* **Scope**: Unit Test (`SpeedAndLocationDeviceGoogleFusedTest.kt`)
* **Preconditions**: Mocked `GoogleApiClient` (connected) and `FusedLocationProviderApi`.
* **Action**: Connect device (`onConnected`), then call `checkOrReRegisterLocationUpdates()` 7 times.
* **Expected Result**: `requestLocationUpdates` called exactly once; `isLocationUpdatesRegistered()` returns `true`.

### Test Case 2: Clean Teardown & Unregistration Test (`TST-TRK-071.2`)
* **Scope**: Unit Test (`SpeedAndLocationDeviceGoogleFusedTest.kt`)
* **Action**: Call `shutDown()` on active device.
* **Expected Result**: `removeLocationUpdates` invoked once; `isLocationUpdatesRegistered()` returns `false`; client disconnected.

### Test Case 3: Connection State Resets & Exception Shielding (`TST-TRK-071.3`)
* **Scope**: Unit Test (`SpeedAndLocationDeviceGoogleFusedTest.kt`)
* **Action**: Verify `onConnectionSuspended()` resets flag to `false`; verify `shutDown()` swallows API exceptions safely without throwing.

### Test Case 4: Tier 1 Targeted Test Suite Execution (`TST-TRK-071.4`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.trainingtracker.build.SystemContextContractTest"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-071.1` | Unit | `SpeedAndLocationDevice_GoogleFused.checkOrReRegisterLocationUpdates` | `REQ-TRK-071.1`, `REQ-TRK-071.2`, `REQ-TRK-071.5` | Specified |
| `TST-TRK-071.2` | Unit | `SpeedAndLocationDevice_GoogleFused.shutDown` | `REQ-TRK-071.3` | Specified |
| `TST-TRK-071.3` | Unit | `SpeedAndLocationDevice_GoogleFused.onConnectionSuspended` | `REQ-TRK-071.3`, `REQ-TRK-071.4` | Specified |
| `TST-TRK-071.4` | Regression | Tier 1 Devices Suite | `REQ-PRO-001`, `REQ-STB-004` | Specified |
