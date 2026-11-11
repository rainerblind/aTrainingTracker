# Stage 3: Implementation Plan - ATT-6683: GoogleFusedSpeedAndLocationDevice & Map Location Clients: Audit and deduplicate 7 concurrent FusedLocationProviderClient registrations

**Ticket**: [ATT-6683](https://rainerblind.atlassian.net/browse/ATT-6683)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-071` (`REQ-TRK-071.1`..`REQ-TRK-071.5`)  
**Test Mapping**: `TST-TRK-071` (`TST-TRK-071.1`..`TST-TRK-071.4`)  
**Branch**: `feature/ATT-6683`  
**Author**: AI Agent 1 (Implementer - Pro) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
During Sprint 2026-46.1 review of `ATT-6256` on Pixel 10, diagnostics showed 7 simultaneous active `LocationRequest` listeners from `SpeedAndLocationDevice_GoogleFused` due to missing registration status tracking across repetitive `checkOrReRegisterLocationUpdates()` calls and missing unregistration in `shutDown()`. This led to Google Play Services logging `location delivery to %s blocked - too fast` 6 times per second. This plan details the implementation of an idempotent registration guard and defensive teardown unregistration in `SpeedAndLocationDevice_GoogleFused.java` and companion contract unit tests.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-TRK-071` (*Google Fused Location Registration Idempotency & Teardown Lifecycle*)
  * `REQ-TRK-071.1`: Idempotent registration guard (`mUpdatesRegistered`).
  * `REQ-TRK-071.2`: Registration state setting upon successful update request.
  * `REQ-TRK-071.3`: Lifecycle teardown & clean unregistration via `removeLocationUpdates`.
  * `REQ-TRK-071.4`: Connection suspension and failure state resets.
  * `REQ-TRK-071.5`: Registration state observability (`isLocationUpdatesRegistered()`).
* **Test Mapping**: `TST-TRK-071` (*Google Fused Location Registration Idempotency, Deduplication & Teardown Verification Suite*)
  * `TST-TRK-071.1`: Deduplication across 7 concurrent / sequential re-registration triggers.
  * `TST-TRK-071.2`: Clean teardown unregistration and client disconnect.
  * `TST-TRK-071.3`: Connection suspension reset and teardown exception shielding.
  * `TST-TRK-071.4`: Tier 1 devices regression suite execution.

---

## 3. System Invariants & Preserved Behavior
1. **Architectural Grounding**: Retain `GoogleApiClient` and `LocationServices.FusedLocationApi` architecture locked by `SYSREQ-TRK-004` and `SystemContextContractTest.kt`.
2. **Fresh Install Permission Recovery**: Retain full compatibility with late runtime permission grant recovery (`ATT-3045` / `REQ-STB-004`).
3. **Registration Bound**: Enforce strictly $\le 1$ active location listener registration in Google Play Services at all times.
4. **Clean Teardown**: Ensure zero leaked location callbacks upon device shutdown or service destruction.
5. **No Map or GPS Regressions**: Preserve existing `SpeedAndLocationDevice_GPS` and Compose map behaviors untouched.

---

## 4. Proposed Architectural Changes
* **Fused Location Provider Client (`SpeedAndLocationDevice_GoogleFused.java`)**:
  - Add `private boolean mUpdatesRegistered = false`.
  - Expose `public synchronized boolean isLocationUpdatesRegistered() { return mUpdatesRegistered; }`.
  - In `onConnected()`: Guard with `if (mUpdatesRegistered) return;`, request updates, set `mUpdatesRegistered = true`.
  - In `checkOrReRegisterLocationUpdates()`: Return early if `mUpdatesRegistered`. On successful update registration, set `mUpdatesRegistered = true`.
  - In `onConnectionSuspended()` and `onConnectionFailed()`: Reset `mUpdatesRegistered = false`.
  - In `shutDown()`: If `mGoogleApiClient != null && mGoogleApiClient.isConnected()`, invoke `LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this)` within a `try-catch` block, set `mUpdatesRegistered = false`, and call `mGoogleApiClient.disconnect()`.
* **Contract Test Suite (`app/src/test/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDeviceGoogleFusedTest.kt`)**:
  - Create comprehensive MockK unit test suite validating deduplication, teardown unregistration, connection lifecycle reset, and exception shielding.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: Code Lock Verification (Rule 4)**
   - Verify Gate 3 sign-off via `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>`.
2. **Step 2: Production Code Hardening (`SpeedAndLocationDevice_GoogleFused.java`)**
   - Add `mUpdatesRegistered` flag, getter, registration guards, connection lifecycle resets, and teardown unregistration.
3. **Step 3: Unit Test Construction (`SpeedAndLocationDeviceGoogleFusedTest.kt`)**
   - Author unit test cases covering `TST-TRK-071.1`, `TST-TRK-071.2`, and `TST-TRK-071.3`.
4. **Step 4: Targeted Unit Test Execution**
   - Run targeted unit test suite under `/tmp/att_build_merge.lock` via `python3 tools/parallel_sprint_lock.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.trainingtracker.build.SystemContextContractTest"`.
* **Rollback**: Discard uncommitted changes on `feature/ATT-6683` via `git restore . && git clean -fd`.
