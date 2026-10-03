# Stage 2: Requirement & Test Specification - ATT-2075: Modernize Permission Flow: Contextual Just-in-Time Prompts & Graceful Settings Return Handling

**Ticket**: [ATT-2075](https://rainerblind.atlassian.net/browse/ATT-2075)  
**Sub-task**: [ATT-2211](https://rainerblind.atlassian.net/browse/ATT-2211) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.14`  
**Branch**: `feature/ATT-2075`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Requirement Specification (`REQ-PRI-003`)

| Field | Specification |
| :--- | :--- |
| **Requirement ID** | `REQ-PRI-003` |
| **Title** | **Contextual Just-in-Time Permission Flow, Zero-Friction Cold Start & Graceful Settings Return Handling.** |
| **Category** | Functional / Privacy / User Experience |
| **Scope** | `MainActivityWithNavigation.kt`, `ControlTrackingScreen.kt`, `ControlTrackingButton.kt`, `ControlTrackingViewModel.kt`, `PermissionRationaleSheet.kt` |
| **Status** | Specified |

### Clause Breakdown & Architectural Constraints

1. **Zero-Friction Cold Start (`MainActivityWithNavigation.kt`)**:
   - `MainActivityWithNavigation.onCreate()` SHALL NOT present modal `AlertDialog` popups for location permissions, background location, or battery optimizations during application cold start.
   - The athlete SHALL be granted immediate, unobstructed access to the Cockpit and all peripheral screens (Workouts, Equipment, Routes, Settings).
2. **Contextual Just-in-Time Request Trigger (Moment of Intent)**:
   - The permission evaluation SHALL be triggered when the athlete expresses explicit tracking intent: tapping "Start Tracking" (`ControlTrackingButton`) or initiating sensor search in `ControlTrackingScreen`.
   - `ControlTrackingButton` SHALL remain clickable when permissions are missing, rather than being disabled without explanation.
3. **Athletic Educational Rationale (`PermissionRationaleSheet.kt`)**:
   - When permissions are required, the system SHALL display a modern Material 3 `ModalBottomSheet` explaining the sport-specific utility:
     - **Location & Background Location**: Logging continuous GPS trackpoints, pace, and elevation while the phone is stowed in a cycling jersey pocket or running belt with screen locked.
     - **Bluetooth (Nearby Devices)**: Connecting to heart rate monitors, bike speed/cadence sensors, power meters, and smart trainers.
     - **Notifications**: Posting real-time workout stats and pause/resume controls on the lock screen.
   - The sheet SHALL offer explicit actions: "Continue" (launching system permission request) and "Not Now" (dismissing cleanly).
4. **Graceful Settings Return & Bad Choice Feedback**:
   - When the athlete returns from Android Settings or if permissions were permanently denied, the app SHALL NOT enter continuous popup loops.
   - An accessible visual indicator (amber warning badge on the Start button) SHALL signal that location permissions are missing.
   - Tapping the Start button in this state SHALL provide transparent feedback explaining the consequence (training cannot record GPS trackpoints without location permission) and offer a direct shortcut to open App Settings.
5. **9-Language Localization Parity (`REQ-LOC-001`)**:
   - All newly introduced user-facing strings SHALL be translated with 100% parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
6. **Preserved Invariants**:
   - Location provider safety (`REQ-STB-008`): Invocations of `LocationManager` must remain permission-gated and shielded against `SecurityException`.
   - FGS lifecycle integrity (`REQ-STB-002`): Background location requirement on Android 14+ preserved.
   - Clean-room test suite 100% pass rate.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Refines and modernizes `REQ-PRI-001` (*Permission Transparency*), while strictly preserving `REQ-STB-008` (*Resilient Permission-Gated Location Provider Access & Startup Crash Immunity*) under Epic `ATT-355` (*Good and consistent UI*).
2. **Historical Origin & Commit Trace**:
   - Commit `ac493d2bdf` (June 2026), initial ASPICE baseline establishment.
3. **Root Reason for Existing Formulation**:
   - `REQ-PRI-001` called for transparent information before requesting permissions. It was originally implemented as synchronous `AlertDialog.Builder` popup chains in `MainActivityWithNavigation.onCreate()`. This created a "popup wall" that frustrated users who merely wanted to inspect their gear, view past activities, or check app settings.
4. **Preservation of Core Invariants**:
   - **Startup Crash Immunity (`REQ-STB-008`)**: All `LocationManager` and GPS queries remain shielded against `SecurityException` when permissions are missing.
   - **Foreground Service Android 14+ Compliance (`REQ-STB-002`)**: `TrackerService` requirements for `ACCESS_BACKGROUND_LOCATION` during lock-screen tracking remain strictly maintained.
   - **9-Language Parity (`REQ-LOC-001`)**: Complete multi-language coverage across all 9 supported locales.

---

## 3. Acceptance Criteria (Given-When-Then)

### AC-1: Zero-Friction Cold Start
- **Given** a fresh application install where runtime permissions (location, notifications, bluetooth) have not yet been granted,
- **When** the athlete opens the app (`MainActivityWithNavigation.onCreate()`),
- **Then** the application launches directly into the Cockpit without presenting modal permission dialogs or battery optimization popups, allowing free navigation across all tabs.

### AC-2: Contextual Just-in-Time Request Trigger (Moment of Intent)
- **Given** an athlete in the Cockpit with ungranted location permissions,
- **When** the athlete taps the "Start Tracking" button,
- **Then** the app displays the Material 3 `PermissionRationaleSheet` explaining the athletic necessity of location tracking (route recording in jersey pocket) and providing "Continue" and "Not Now" options.

### AC-3: Permission Acquisition & Tracking Launch
- **Given** the `PermissionRationaleSheet` presented,
- **When** the athlete taps "Continue" and grants the requested permissions in the system dialog,
- **Then** the sheet dismisses and tracking starts immediately or the Start button reflects ready state.

### AC-4: Graceful Settings Return & Bad Choice Feedback
- **Given** an athlete who denied permissions or returned from Android App Settings without granting permissions,
- **When** the application resumes (`onResume`),
- **Then** no automatic modal popups appear, an amber warning badge is displayed on the Start button, and tapping Start presents a clear explanatory dialog with a direct button to open App Settings.

---

## 4. Test Specification (`TST-PRI-002`)

### 1. Cold-Start Freedom Test (`MainActivityStartupPermissionContractTest.kt`)
- Verify `MainActivityWithNavigation.onCreate()` does not invoke blocking `AlertDialog.show()` for permissions or battery optimizations.
- Verify GPS provider access remains shielded per `REQ-STB-008`.

### 2. ViewModel Permission State & JIT Request Test (`ControlTrackingPermissionTest.kt`)
- Verify `ControlTrackingViewModel` evaluates permission status reactively.
- Verify tapping Start when permissions are missing triggers rationale sheet display instead of silent failure or crash.
- Verify tapping Start when permissions are granted dispatches `onStartTracking()`.

### 3. UI Rationale & Badge Contract Test (`PermissionRationaleSheetContractTest.kt`)
- Verify `PermissionRationaleSheet` renders title, athletic explanations for location and bluetooth, and action buttons.
- Verify `ControlTrackingButton` renders warning badge when location permissions are missing.

### 4. 9-Language Localization Audit
- Verify all new permission rationale strings, badge labels, and settings guidance exist across all 9 locales:
  - `values/strings.xml` (EN)
  - `values-de/strings.xml` (DE)
  - `values-es/strings.xml` (ES)
  - `values-fr/strings.xml` (FR)
  - `values-it/strings.xml` (IT)
  - `values-ja/strings.xml` (JA)
  - `values-nl/strings.xml` (NL)
  - `values-pl/strings.xml` (PL)
  - `values-pt/strings.xml` (PT)

### 5. Full Clean-Room Regression Suite
- Run `./gradlew testDebugUnitTest` verifying 100% pass rate.
