# Stage 2 Requirement & Test Specification: ATT-2075 - Modernize Permission Flow: Contextual Just-in-Time Prompts & Graceful Settings Return Handling (Rework Cycle)

**Ticket**: [ATT-2075](https://rainerblind.atlassian.net/browse/ATT-2075)  
**Sub-task**: [ATT-2378](https://rainerblind.atlassian.net/browse/ATT-2378) (`[Test-Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` (per ASPICE Rule 19: Lösungsversion added upon final acceptance)  
**Active Sprint**: `2026-40.15`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Formal Requirement: REQ-PRI-003 (Refined for Rework Cycle)

### REQ-PRI-003: Contextual Just-in-Time Permission Flow, Zero-Friction Cold Start, Background Location Escalation, Battery Optimization Exemption & Graceful Settings Return Handling

The system SHALL NOT present modal permission dialogs or battery optimization popups during cold start; it SHALL evaluate permissions contextually when the athlete expresses tracking intent (tapping Start or searching sensors), display educational Material 3 rationale sheets highlighting athletic benefits, enforce the Android 11+ two-step background location cascade, handle battery optimization exemption with fail-safe OEM fallback, and handle denied permissions or returns from settings gracefully with an amber warning badge on the Start button and actionable feedback without infinite loops (ATT-2075):

1. **Zero-Friction Cold Start (`MainActivityWithNavigation.kt`)**:
   - `MainActivityWithNavigation.onCreate()` SHALL NOT trigger blocking permission dialogs (`AlertDialog.show()`) or battery optimization requests upon cold startup, allowing athletes immediate, unhindered access to explore the cockpit, workout history, equipment, and routes.
2. **Progressive 3-Stage Just-in-Time Setup Flow (`ControlTrackingScreen.kt`)**:
   - When the athlete taps **Start Tracking** (`handleStartClick`):
     - **Stage A (Foreground Permissions)**: If foreground location (`ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`), Bluetooth (`BLUETOOTH_CONNECT` / `BLUETOOTH_SCAN` on API 31+), or notification (`POST_NOTIFICATIONS` on API 33+) permissions are missing, the system SHALL display `PermissionRationaleSheet(RationaleType.FOREGROUND)`. Tapping Continue SHALL launch the foreground permissions request.
     - **Stage B (Background Location Escalation - API 29+)**: On Android 10+ (API 29+), if foreground location is granted but `ACCESS_BACKGROUND_LOCATION` is missing, the system SHALL display `PermissionRationaleSheet(RationaleType.BACKGROUND_LOCATION)` explaining why "Allow all the time" ("Immer zulassen") is required for tracking in a jersey pocket or with locked screen. Tapping Continue SHALL request `ACCESS_BACKGROUND_LOCATION` (or direct to App Settings if permanently denied / required by API 30+).
     - **Stage C (Battery Optimization Exemption - API 23+)**: If `PowerManager.isIgnoringBatteryOptimizations(packageName)` returns `false`, the system SHALL display `PermissionRationaleSheet(RationaleType.BATTERY_OPTIMIZATION)` explaining that Android kills background services during long workouts unless set to "Unrestricted" ("Nicht eingeschränkt"). Tapping Continue SHALL launch `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
     - **Stage D (Active Recording Dispatch)**: Once required permissions and prompts are handled (or acknowledged/skipped by the athlete), the system SHALL invoke `onStart()` to commence active recording via `TrackerService`.
3. **Fail-Safe OEM Intent Cascading (`ControlTrackingScreen.kt`)**:
   - Attempting to launch `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` SHALL be wrapped in a fail-safe cascade: if `ActivityNotFoundException` or `SecurityException` is caught on customized OEM ROMs (MIUI, EMUI, OneUI), the system SHALL catch the exception and fall back to `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`, and if that fails, fall back to `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`, preventing app crashes.
4. **Reactive Lifecycle Synchronization & Warning Badge (`ControlTrackingScreen.kt`)**:
   - On `Lifecycle.Event.ON_RESUME`, the screen SHALL reactively re-evaluate foreground location, background location, and battery optimization states.
   - If location permissions are missing, `ControlTrackingButton` SHALL render an amber warning badge.
   - Tapping Start when permissions are permanently denied SHALL direct the athlete to Settings with clear feedback rather than leaving the button dead or triggering crash loops.
5. **Chesterton's Fence Archaeology & Invariant Preservation**:
   - Refines `REQ-PRI-003` and preserves `REQ-STB-008` (GPS crash immunity), `REQ-STB-002` (FGS resilience), and `REQ-LOC-001` (100% 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT).

#### Acceptance Criteria (Given-When-Then)
* **AC-1 (Zero Cold-Start Friction)**:
  - *Given* a fresh install or revoked permissions,
  - *When* `MainActivityWithNavigation` is launched,
  - *Then* zero modal permission or battery dialogs SHALL appear.
* **AC-2 (Foreground JIT Rationale)**:
  - *Given* missing foreground location,
  - *When* the athlete taps Start Tracking,
  - *Then* `PermissionRationaleSheet` with foreground rationale SHALL be displayed.
* **AC-3 (Background Location Escalation)**:
  - *Given* foreground location granted but background location missing on Android 10+,
  - *When* the athlete proceeds from foreground permissions or taps Start,
  - *Then* `PermissionRationaleSheet` with background location rationale ("Allow all the time" / "Immer zulassen") SHALL be displayed before tracking starts.
* **AC-4 (Battery Optimization Prompt & Fail-Safe Intent)**:
  - *Given* battery optimization active on device,
  - *When* location permissions are granted,
  - *Then* `PermissionRationaleSheet` with battery optimization rationale ("Unrestricted" / "Nicht eingeschränkt") SHALL be displayed, and launching the intent SHALL never throw an uncaught exception.
* **AC-5 (Graceful Settings Return Handling)**:
  - *Given* returning from Settings (`ON_RESUME`),
  - *Then* permission states and the Start button warning badge SHALL update reactively.

---

## 2. Test Specification: TST-PRI-002 (Refined for Rework Cycle)

### TST-PRI-002: Contextual Just-in-Time Permission Flow, Background Location Escalation & Battery Optimization Verification

1. **Cold-Start Freedom Test (`MainActivityStartupPermissionContractTest.kt`)**:
   - Verify `MainActivityWithNavigation.onCreate()` does not invoke blocking `AlertDialog.show()` for permissions or battery optimizations on cold start.
   - Verify GPS provider access remains shielded per `REQ-STB-008`.
2. **Progressive JIT State Machine & Escalation Tests (`ControlTrackingPermissionTest.kt`)**:
   - *Test 2.1*: Verify tapping Start when foreground location is missing triggers foreground rationale sheet.
   - *Test 2.2*: Verify granting foreground location on API 29+ transitions to background location rationale sheet when `ACCESS_BACKGROUND_LOCATION` is missing.
   - *Test 2.3*: Verify when location permissions are granted but battery optimization is active, system transitions to battery optimization rationale sheet.
   - *Test 2.4*: Verify `ControlTrackingButton` displays amber warning badge when location permissions are missing.
   - *Test 2.5*: Verify `ON_RESUME` re-evaluates all permission states reactively.
3. **Fail-Safe OEM Intent Contract Test (`ControlTrackingPermissionTest.kt` / `PermissionRationaleSheetContractTest.kt`)**:
   - Verify battery optimization intent launch handles `ActivityNotFoundException` and `SecurityException` with fallback to settings without crashing.
4. **9-Language Localization Audit**:
   - Verify background location and battery optimization titles and descriptions exist across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
5. **Clean-Room Full Suite Regression Execution**:
   - Run `./gradlew testDebugUnitTest` asserting 100% pass rate.

---

## 3. Traceability Matrix

| Requirement | Test ID | Target Component | Status |
| :--- | :--- | :--- | :--- |
| **REQ-PRI-003** | `TST-PRI-002` | `ControlTrackingScreen.kt`, `PermissionRationaleSheet.kt`, `ControlTrackingButton.kt` | Specified |
