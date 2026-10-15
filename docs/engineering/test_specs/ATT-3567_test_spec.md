# Stage 2: Requirement & Test Specification - ATT-3567: Remove Unnecessary FOREGROUND_SERVICE_CONNECTED_DEVICE Permission from Manifest and TrackerService

**Ticket**: [ATT-3567](https://atrainingtracker.atlassian.net/browse/ATT-3567)  
**Parent Epic**: [ATT-431](https://atrainingtracker.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Active Sprint**: `2026-42.5` (Sprint ID `372`)  
**Target Release**: `V4.9.40`  
**Requirement Mapping**: `REQ-TRK-022` (*Streamlined Dual-Type (Location and Health) Foreground Service Architecture & Removal of Redundant Connected Device Permission*)  
**Test Spec ID**: `TST-TRK-014` (*Streamlined Dual-Type (Location and Health) Foreground Service Verification Suite*)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Traceability Matrix

| Requirement ID | Test Case ID | Test Target | Verification Description | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-022.1` | `TST-TRK-014.1` | `MergedManifestAuditTest` / Manifest Inspection | Verify `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />` is absent from merged manifest | Draft |
| `REQ-TRK-022.2` | `TST-TRK-014.2` | `MergedManifestAuditTest` / Manifest Inspection | Verify `TrackerService` and `BANALService` specify `android:foregroundServiceType="location\|health"` without `connectedDevice` | Draft |
| `REQ-TRK-022.3` | `TST-TRK-014.3` | `TrackerServiceFGSTypeTest.kt` | Verify `determineForegroundServiceType()` returns `LOCATION` (health not granted) or `LOCATION \| HEALTH` (health granted), and NEVER returns `CONNECTED_DEVICE` | Draft |
| `REQ-TRK-022.4` | `TST-TRK-014.4` | `TrackerServiceFinalizationTest.kt` | Verify `determineForegroundServiceType()` omits health type without permission and expects strictly `FOREGROUND_SERVICE_TYPE_LOCATION` | Draft |
| `REQ-TRK-022` | `TST-TRK-014.5` | `./gradlew testDebugUnitTest` | Full clean-room test suite execution with 100% pass rate and zero regressions | Draft |

---

## 2. Requirement Specification (`REQ-TRK-022`)

### 2.1 Formal Requirement Definition
The system SHALL streamline its Foreground Service architecture by eliminating `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE` from the application manifest, removing the `connectedDevice` service attribute from `TrackerService` and `BANALService`, and simplifying `TrackerService.determineForegroundServiceType()` to derive strictly `FOREGROUND_SERVICE_TYPE_LOCATION` and permission-gated `FOREGROUND_SERVICE_TYPE_HEALTH`:

1. **Manifest Permission Cleanup (`REQ-TRK-022.1`)**:
   - The application manifest (`AndroidManifest.xml`) SHALL NOT declare `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />`.
   - `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, and `FOREGROUND_SERVICE_HEALTH` SHALL remain fully declared and intact.

2. **Service Declaration Attribute Cleanup (`REQ-TRK-022.2`)**:
   - In `AndroidManifest.xml`, the `<service>` declarations for `TrackerService` and `BANALService` SHALL specify `android:foregroundServiceType="location|health"`.
   - The string `connectedDevice` SHALL be completely purged from all service definitions.

3. **Runtime FGS Bitmask Simplification (`REQ-TRK-022.3`)**:
   - In `TrackerService.java`, `determineForegroundServiceType()` SHALL start with `ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION`.
   - On Android 14+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE`), if `hasHealthPermission()` returns `true`, the bitmask SHALL add `ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH`.
   - `ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` SHALL NEVER be included in the returned bitmask.
   - The unused `hasBluetoothPermission()` helper in `TrackerService.java` SHALL be removed.

4. **Preservation of System Invariants (`REQ-TRK-022.4`)**:
   - Uninterrupted workout session recording on Android 14+ (API 34+).
   - Zero `SecurityException` during `Service.startForeground()` promotion.
   - Active Bluetooth LE GATT sensor telemetry collection and background CPU wake lock upkeep.

---

## 3. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement IDs & Targets**:
   - Amends `REQ-CON-019` (*Robust Bluetooth LE Sensor Discovery & Direct LE Transport Connection Architecture*, ATT-2773) and `REQ-TRK-015` (*Permission-Gated Foreground Service Types in TrackerService*, ATT-2972).
   - Targets `app/src/main/AndroidManifest.xml`, `TrackerService.java`, and unit tests `TrackerServiceFGSTypeTest.kt` and `TrackerServiceFinalizationTest.kt`. Formally tracked as `REQ-TRK-022`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-41.4 (`ATT-2773`, commit `67451a9`): added `FOREGROUND_SERVICE_CONNECTED_DEVICE` and `connectedDevice` attribute.
   - Sprint 2026-41.7 (`ATT-2972`, commit `4466c40`): gated FGS types in `TrackerService.determineForegroundServiceType()`.
3. **Root Reason for Existing Formulation**:
   - The initial Android 14 compliance migration defensively adopted all available peripheral types. Subsequent operational testing and Google Play Console policy enforcement confirm that `FOREGROUND_SERVICE_LOCATION` and `FOREGROUND_SERVICE_HEALTH` completely satisfy Android 14 background execution requirements for athletic workouts while eliminating unnecessary Google Play Console policy questionnaires regarding external hardware file transfers.
4. **Preservation of Core Invariants**:
   - Process foreground priority, GPS track recording, health sensor data ingestion, and 100% clean-room test pass rate remain strictly preserved.

---

## 4. Detailed Test Specification (`TST-TRK-014`)

### TST-TRK-014.1: Manifest Permission Audit
- **Target**: `app/src/main/AndroidManifest.xml` and merged manifest.
- **Verification**: Assert that `FOREGROUND_SERVICE_CONNECTED_DEVICE` does not exist in any `<uses-permission>` tag.
- **Assertion**: String match fails for `FOREGROUND_SERVICE_CONNECTED_DEVICE`.

### TST-TRK-014.2: Manifest Service Attribute Audit
- **Target**: `app/src/main/AndroidManifest.xml` and merged manifest.
- **Verification**: Assert that `TrackerService` and `BANALService` have `android:foregroundServiceType="location|health"`.
- **Assertion**: String match fails for `connectedDevice`.

### TST-TRK-014.3: TrackerService FGS Type Derivation
- **Class**: `TrackerServiceFGSTypeTest.kt`
- **Method**:
  - `testDetermineForegroundServiceType_WithoutHealth_ReturnsLocationOnly()`
  - `testDetermineForegroundServiceType_WithHealth_ReturnsLocationAndHealth()`
- **Given**: `TrackerService` on Android 14+ (API 34+).
- **When**: Evaluating `determineForegroundServiceType()`.
- **Then**: Returns `LOCATION` when health is false, and `LOCATION | HEALTH` when health is true. Bitwise AND with `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` is strictly `0`.

### TST-TRK-014.4: TrackerService Finalization FGS Type Check
- **Class**: `TrackerServiceFinalizationTest.kt`
- **Method**: `testDetermineForegroundServiceType_WithoutHealthPermission_OmitsHealthType()`
- **Given**: `TrackerService` on Android 14+ with health permission false.
- **When**: Evaluating `determineForegroundServiceType()`.
- **Then**: Returns strictly `ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION`.

### TST-TRK-014.5: Clean-Room Full Suite Regression
- **Command**: `./gradlew testDebugUnitTest`
- **Assertion**: 100% pass rate across entire unit test suite.
