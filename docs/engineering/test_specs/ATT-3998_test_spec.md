# Stage 2: Requirement & Test Specification - ATT-3998: [Bug] [High] Bluetooth and notification permissions only requested when location is missing

**Ticket**: [[ATT-3998]](https://rainerblind.atlassian.net/browse/ATT-3998)  
**Sub-task**: [[ATT-4366]](https://rainerblind.atlassian.net/browse/ATT-4366) (`[Req & Test Spec]`)  
**Parent Epic**: None (*Product Backlog / Bugfix*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Requirement Mapping**: `REQ-PRI-006` (*Decoupled Multi-Permission Cascade, Contextual Bluetooth & Notification Gating, Sensor Discovery Permission Handling, and Legacy Code Retirement*)  
**Test Spec ID**: `TST-PRI-005`  
**Branch**: `feature/ATT-3998`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification ([REQ-PRI-006])

### 1.1 Problem Statement & Rationale
In **aTrainingTracker**, tracking an active athletic session and communicating with wireless sensors requires distinct Android runtime permissions depending on the operating system version:
1. **Precise Location** (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`): Required across all Android versions for recording GPS trackpoints, route navigation, and speed/distance telemetry.
2. **Post Notifications** (`POST_NOTIFICATIONS`): Required on Android 13+ (API 33+) for foreground service notifications (`TrackerService`), lock-screen split timers, and pause/resume buttons.
3. **Bluetooth Nearby Devices** (`BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` on API 31+; `ACCESS_FINE_LOCATION`, `BLUETOOTH_ADMIN` on API < 31): Required for discovering nearby BLE peripherals (`BTSearchForNewDevicesEngine`) and maintaining GATT connections to paired heart rate monitors, power meters, speed, and cadence sensors (`MyBTLEDevice`).

In the current implementation:
- In [`ControlTrackingScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingScreen.kt#L250-L269), all foreground permissions (`ACCESS_FINE_LOCATION`, `POST_NOTIFICATIONS`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`) were bundled into a single step (`RationaleStep.FOREGROUND`) whose trigger was strictly gated on `if (!checkHasLocation())`.
- If an athlete upgrades the application or previously granted location in system settings, `checkHasLocation()` returns `true`. Consequently, `handleStartClick` skips `RationaleStep.FOREGROUND` entirely and proceeds to `proceedAfterPermissions()`.
- `proceedAfterPermissions()` evaluates only background location (`ACCESS_BACKGROUND_LOCATION`) and battery optimization exemptions. It never checks or prompts for missing `POST_NOTIFICATIONS` (API 33+) or `BLUETOOTH_CONNECT` (API 31+).
- In sensor discovery (`DevicesTabbedScreen.kt` and `ResearchButton` in `ControlTrackingScreen.kt`), sensor search is initiated without checking runtime Bluetooth permissions. On Android 12+, `BTSearchForNewDevicesEngine.startAsyncSearch()` logs `"BLUETOOTH_SCAN permission not granted"` and silently aborts, leaving the UI showing an active spinner while scanning never runs.
- In [`MainActivityWithNavigation.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/activities/MainActivityWithNavigation.kt#L509-L623), dead legacy permission methods (`getPermissions()`, `getPermissions(popup: Boolean)`, `onRequestPermissionsResult`, `showBackgroundLocationDialog()`) remain in the codebase with inverted rationale logic, maintained solely by an obsolete reflection assertion in `MainActivityStartupPermissionContractTest.kt`.

### 1.2 Functional & Architectural Requirements
The system SHALL fulfill the following architectural and behavioral requirements (`REQ-PRI-006`):

1. **Decoupled Permission Check Predicates (`REQ-PRI-006.1`)**:
   - `ControlTrackingScreen.kt` SHALL define granular, decoupled permission check lambdas:
     - `checkHasLocation`: returns `true` if `ACCESS_FINE_LOCATION` is granted.
     - `checkHasNotification`: returns `true` on API < 33, or if `POST_NOTIFICATIONS` is granted on API 33+.
     - `checkHasBluetoothConnect`: returns `true` on API < 31, or if `BLUETOOTH_CONNECT` is granted on API 31+.
     - `checkHasBluetoothScan`: returns `true` on API < 31, or if `BLUETOOTH_SCAN` is granted on API 31+.
   - Missing notifications or Bluetooth permissions SHALL NOT be shadowed or bypassed by location permission being already granted.

2. **Decoupled Multi-Step Cascade for Workout Start (`REQ-PRI-006.2`)**:
   - When the athlete taps Start (`handleStartClick`):
     - If `!checkHasLocation()`: the system SHALL present `RationaleStep.FOREGROUND` (requesting Location, Notifications on API 33+, and Bluetooth on API 31+).
     - If `checkHasLocation()` is `true`: the system SHALL evaluate `proceedAfterPermissions()`:
       1. If `!checkHasNotification()` (Android 13+) and notification permission has not yet been requested or dismissed in the current session: the system SHALL present `RationaleStep.NOTIFICATION` (or request `POST_NOTIFICATIONS`).
       2. If paired Bluetooth sensors are registered (`hasPairedBluetoothSensors == true`) and `!checkHasBluetoothConnect()` (Android 12+): the system SHALL present `RationaleStep.BLUETOOTH` (or request `BLUETOOTH_CONNECT`).
       3. If the athlete declines or dismisses notification or Bluetooth prompts ("Not Now"), the system SHALL gracefully advance to the next step without blocking workout tracking.
       4. If `!checkHasBackgroundLocation()` (Android 11+): the system SHALL present `RationaleStep.BACKGROUND_LOCATION`.
       5. If `!checkIsIgnoringBatteryOptimizations()`: the system SHALL present `RationaleStep.BATTERY_OPTIMIZATION`.
       6. When all required/un-dismissed steps are satisfied: the system SHALL execute `onStart()`.

3. **Just-in-Time Bluetooth Permissions for Sensor Discovery (`REQ-PRI-006.3`)**:
   - In `ControlTrackingScreen.kt` (`ResearchButton` / `onSearch`):
     - When tapped, the system SHALL check whether Bluetooth permissions are granted (`BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` on API 31+; `ACCESS_FINE_LOCATION` and `BLUETOOTH_ADMIN` on API < 31).
     - If missing, the system SHALL launch the permission request launcher rather than dispatching `REQUEST_START_SEARCH_FOR_PAIRED_DEVICES` blindly.
   - In `DevicesTabbedScreen.kt`:
     - Before executing `tabViewModel.startSearching()` or initiating a pairing scan for `Protocol.BLUETOOTH_LE`, the system SHALL verify Bluetooth scan permission.
     - If missing, the system SHALL request permission contextually.
     - If denied, the UI SHALL display a visible indicator / banner (`bluetooth_permission_scan_required`) with an action button (`grant_permission`) rather than running a silent dead search.

4. **Start Button Warning Badge Synchronization (`REQ-PRI-006.4`)**:
   - The warning indicator on `ControlTrackingButton` SHALL evaluate to `true` whenever:
     - Precise Location is missing (`!hasLocationPermission`), OR
     - Battery optimization exemption is missing (`!isIgnoringBatteryOptimizations`), OR
     - Paired Bluetooth sensors exist but Bluetooth connect permission is missing (`hasPairedBluetoothSensors && !hasBluetoothPermission`).

5. **Legacy Permission Dead Code Retirement (`REQ-PRI-006.5`)**:
   - The obsolete legacy methods in `MainActivityWithNavigation.kt` (`private fun getPermissions(): List<String>`, `fun getPermissions(popup: Boolean)`, `override fun onRequestPermissionsResult(...)`, and `private fun showBackgroundLocationDialog()`) SHALL be completely removed.
   - `MainActivityStartupPermissionContractTest.kt` SHALL retire the reflection assertion on `getPermissions(Boolean)` while preserving all assertions for cold-start popup freedom and location safety.

6. **Full 9-Language Localization Parity (`REQ-PRI-006.6`)**:
   - All newly introduced user-facing string resources (`bluetooth_permission_scan_required`, `grant_permission`, `permission_rationale_notification_subtitle`, `permission_rationale_bluetooth_subtitle`) SHALL be localized across all 9 supported locales:
     `values/` (EN), `values-de/` (DE), `values-es/` (ES), `values-fr/` (FR), `values-it/` (IT), `values-ja/` (JA), `values-nl/` (NL), `values-pl/` (PL), `values-pt/` (PT).

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Location Already Granted - Notification Prompt on Android 13+)**:
  * *Given* an athlete on Android 13+ (API 33+) where `ACCESS_FINE_LOCATION` is already granted, but `POST_NOTIFICATIONS` is not granted,
  * *When* the athlete taps the Start tracking button on `ControlTrackingScreen`,
  * *Then* the system SHALL evaluate `checkHasNotification() == false` and present notification rationale or request prompt before starting the workout.

* **Criterion 2 (Location Already Granted - Bluetooth Connect Prompt on Android 12+)**:
  * *Given* an athlete on Android 12+ (API 31+) where `ACCESS_FINE_LOCATION` is already granted, paired BLE sensors exist in `ActiveDevices.db`, and `BLUETOOTH_CONNECT` is not granted,
  * *When* the athlete taps the Start tracking button on `ControlTrackingScreen`,
  * *Then* the system SHALL evaluate `checkHasBluetoothConnect() == false` and present Bluetooth rationale or request prompt before starting the workout.

* **Criterion 3 (Non-Blocking Optional Permissions Dismissal)**:
  * *Given* an athlete presented with notification or Bluetooth permission rationale,
  * *When* the athlete taps "Not Now" / dismisses the rationale sheet,
  * *Then* the system SHALL advance to the next cascade step (background location / battery optimization / tracking start) without blocking tracking or looping indefinitely.

* **Criterion 4 (Missing Fine Location Triggers Foreground Rationale)**:
  * *Given* an athlete where `ACCESS_FINE_LOCATION` is missing (e.g. fresh install or coarse-only),
  * *When* the athlete taps Start,
  * *Then* the system SHALL present `RationaleStep.FOREGROUND` with full onboarding permission bundle.

* **Criterion 5 (Just-in-Time Bluetooth Scan Gating in ControlTrackingScreen)**:
  * *Given* `ControlTrackingScreen` with `ResearchButton` ("Suchen"),
  * *When* the athlete taps "Suchen" on Android 12+ while `BLUETOOTH_SCAN` is not granted,
  * *Then* the system SHALL trigger the Bluetooth permission request launcher and withhold the search broadcast until granted.

* **Criterion 6 (Just-in-Time Bluetooth Scan Gating in DevicesTabbedScreen)**:
  * *Given* `DevicesTabbedScreen` opened on Android 12+ with `BLUETOOTH_SCAN` not granted,
  * *When* displaying the sensor tabs or initiating a BLE pairing scan,
  * *Then* the system SHALL request permission and, if denied, display `bluetooth_permission_scan_required` with action to grant rather than silently failing.

* **Criterion 7 (Dead Code Removal & Contract Safety)**:
  * *Given* `MainActivityWithNavigation`,
  * *When* inspected,
  * *Then* `getPermissions(popup: Boolean)` and `showBackgroundLocationDialog()` do not exist, and `MainActivityStartupPermissionContractTest` passes cleanly without reflection check.

### 1.4 System Invariants
1. **Zero Cold-Start Popups (`REQ-PRI-003`)**: Application startup (`MainActivityWithNavigation.onCreate()`, `onResume()`) SHALL NOT present modal permission dialogs or battery optimization popups.
2. **Mandatory Precise Location (`REQ-PRI-004`)**: Workout tracking strictly requires fine location; coarse-only location cannot start GPS recording.
3. **Android 11+ Two-Step Background Location Cascade**: Foreground location is always requested and granted prior to escalating to background location (`ACCESS_BACKGROUND_LOCATION`).
4. **Fail-Safe OEM Intent Handling (`REQ-PRI-005`)**: Battery optimization exemption intent handling catches `ActivityNotFoundException` and `SecurityException` with graceful fallback to settings.
5. **Android 8–11 BLE Compatibility (`REQ-CON-021`, ATT-3997)**: `BLUETOOTH_ADMIN` manifest declaration and defensive exception handling remain strictly preserved.
6. **Clean-Room Full Test Suite Pass Rate**: 100% pass rate across the full test suite (`./gradlew testDebugUnitTest`).

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Refines and supersedes `REQ-PRI-003` (*Contextual Just-in-Time Permission Flow, Zero-Friction Cold Start, Background Location Escalation, Battery Optimization Exemption & Graceful Settings Return Handling*) and test specification `TST-PRI-002`. Targets `ControlTrackingScreen.kt`, `ControlTrackingButton.kt`, `DevicesTabbedScreen.kt`, `MainActivityWithNavigation.kt`, and `BTSearchForNewDevicesEngine.java`.
* `Historical Origin & Commit Trace`:
  - `REQ-PRI-003` was introduced in Sprint 2026-40.16 under ticket `ATT-2075` (commits `009c8f49`, `4a12158e`, `6b53f065`) to replace eager startup dialogs with contextual just-in-time permission sheets.
  - Refined in Sprint 2026-41.2 under `ATT-2621` (commit `057d5583`, `REQ-PRI-005`) to decouple battery optimization warnings.
  - Discovered in whole-app architectural code review of `feature/ATT-3921` (`0234bfc0`), filed under ticket `ATT-3998`.
* `Root Reason for Existing Formulation`:
  - In `ATT-2075`, the primary goal was eliminating cold-start modal dialogs on `MainActivity` startup.
  - The author created `RationaleStep.FOREGROUND` as a single bundled step containing all initial runtime permissions (`ACCESS_FINE_LOCATION`, `POST_NOTIFICATIONS`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`).
  - The author gated this step on `if (!checkHasLocation())` under the assumption that fresh installations would always lack location.
  - Upgraded installations where location was already granted were not accounted for, resulting in notification and Bluetooth permission requests being completely bypassed.
  - Sensor discovery in `DevicesTabbedScreen.kt` was built without Compose-level permission integration, relying on `BTSearchForNewDevicesEngine` to silently log failures.
  - `MainActivityWithNavigation.getPermissions(popup: Boolean)` was kept to prevent breaking tests during the initial Jetpack Compose transition.
* `Preservation of Core Invariants`:
  - **Zero Cold-Start Popups**: The core invariant of `REQ-PRI-003` (no modal dialogs or popups during application startup) remains strictly guarded.
  - **Two-Step Location Cascade**: Foreground location is granted before requesting background location (`ACCESS_BACKGROUND_LOCATION`) on Android 11+ per Google Play policy.
  - **Fail-Safe Battery Optimization**: Battery optimization exemption intent handling (`REQ-PRI-005`) with OEM fallback is preserved.
  - **Mandatory Precise Location**: Tracking still strictly requires fine location (`REQ-PRI-004`).
  - **Android 8–11 BLE Compatibility**: `BLUETOOTH_ADMIN` manifest declaration and defensive exception handling (`REQ-CON-021`, ATT-3997) remain untouched.

---

## 2. Test Specification ([TST-PRI-005])

### Test Case 1: Decoupled Multi-Permission Cascade Unit Tests (`TST-PRI-005.1`)
* **Scope**: Automated Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ControlTrackingPermissionTest.kt`
* **Preconditions**: `ControlTrackingViewModel` and permission state helpers initialized.
* **Actions & Expected Results**:
  1. *Location Missing*: `checkHasLocation() == false` -> triggers `RationaleStep.FOREGROUND`.
  2. *Location Granted, Notification Missing (API 33+)*: `checkHasLocation() == true`, `checkHasNotification() == false` -> triggers `RationaleStep.NOTIFICATION` (or requests `POST_NOTIFICATIONS`).
  3. *Location Granted, Paired BLE Sensors, Bluetooth Connect Missing (API 31+)*: `checkHasLocation() == true`, `hasPairedBluetoothSensors == true`, `checkHasBluetoothConnect() == false` -> triggers `RationaleStep.BLUETOOTH` (or requests `BLUETOOTH_CONNECT`).
  4. *Optional Dismissal*: Dismissing notification or Bluetooth rationale advances to background location / battery optimization / tracking start without infinite loops.
  5. *All Granted*: All permissions and battery exemptions granted -> invokes `onStart()`.

### Test Case 2: Sensor Discovery Just-in-Time Permission Gating Tests (`TST-PRI-005.2`)
* **Scope**: Automated Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/devicetabs/DevicesTabbedPermissionContractTest.kt`
* **Preconditions**: Compose test environment or mocked Activity result launcher.
* **Actions & Expected Results**:
  1. On Android 12+, calling sensor search when `BLUETOOTH_SCAN` is missing triggers the permission request contract.
  2. If permission is denied, verify visible feedback state is emitted (`bluetooth_permission_scan_required`) and search broadcast is withheld.
  3. On Android 12+, calling sensor search when `BLUETOOTH_SCAN` is granted dispatches search immediately.

### Test Case 3: Legacy Permission Method Retirement & Contract Safety Tests (`TST-PRI-005.3`)
* **Scope**: Automated Unit / Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/activities/MainActivityStartupPermissionContractTest.kt`
* **Preconditions**: `MainActivityWithNavigation` class loaded.
* **Actions & Expected Results**:
  1. Verify `MainActivityWithNavigation` does not declare `getPermissions` or `showBackgroundLocationDialog` methods.
  2. Verify `onCreate` does not invoke blocking `AlertDialog` on cold start (`REQ-PRI-003`).
  3. Verify GPS provider access remains shielded per `REQ-STB-008`.

### Test Case 4: 9-Language Localization & Format Specifier Audit (`TST-PRI-005.4`)
* **Scope**: Automated Unit / Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/PermissionLocalizationParityTest.kt`
* **Target Locales**:
  - `values/` (EN), `values-de/` (DE), `values-es/` (ES), `values-fr/` (FR), `values-it/` (IT), `values-ja/` (JA), `values-nl/` (NL), `values-pl/` (PL), `values-pt/` (PT)
* **Required String Keys**:
  - `bluetooth_permission_scan_required`
  - `grant_permission`
  - `permission_rationale_notification_subtitle`
  - `permission_rationale_bluetooth_subtitle`
* **Expected Result**: 100% translation parity across all 9 locales, zero missing entries, zero format specifier mismatches, valid literal `\n` linebreaks.

### Test Case 5: Clean-Room Full Regression Suite (`TST-PRI-005.5`)
* **Scope**: Clean-Room Regression Suite
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% pass rate across all project unit and contract tests with zero regressions.

---

## 3. Localization Parity Specification (9 Locales)

| Locale | Key | Localized Value |
| :--- | :--- | :--- |
| **EN** | `bluetooth_permission_scan_required` | `Bluetooth permission is required to scan for nearby sensors.` |
| | `grant_permission` | `Grant` |
| | `permission_rationale_notification_subtitle` | `Notifications keep workout duration, distance, split alerts, and quick controls accessible while tracking in the background.` |
| | `permission_rationale_bluetooth_subtitle` | `Nearby Devices permission connects aTrainingTracker to your paired heart rate, power, speed, and cadence sensors.` |
| **DE** | `bluetooth_permission_scan_required` | `Bluetooth-Berechtigung ist erforderlich, um nach Sensoren in der Nähe zu suchen.` |
| | `grant_permission` | `Erteilen` |
| | `permission_rationale_notification_subtitle` | `Benachrichtigungen halten Trainingsdauer, Distanz, Rundenalarme und Schnellsteuerungen während der Hintergrundaufzeichnung zugänglich.` |
| | `permission_rationale_bluetooth_subtitle` | `Die Berechtigung für Geräte in der Nähe verbindet aTrainingTracker mit Ihren gekoppelten Herzfrequenz-, Leistungs-, Geschwindigkeits- und Trittfrequenzsensoren.` |
| **ES** | `bluetooth_permission_scan_required` | `Se requiere permiso de Bluetooth para buscar sensores cercanos.` |
| | `grant_permission` | `Conceder` |
| | `permission_rationale_notification_subtitle` | `Las notificaciones mantienen accesibles la duración, la distancia, las alertas de vueltas y los controles rápidos durante el seguimiento en segundo plano.` |
| | `permission_rationale_bluetooth_subtitle` | `El permiso de Dispositivos cercanos conecta aTrainingTracker a sus sensores vinculados de frecuencia cardíaca, potencia, velocidad y cadencia.` |
| **FR** | `bluetooth_permission_scan_required` | `L'autorisation Bluetooth est requise pour rechercher des capteurs à proximité.` |
| | `grant_permission` | `Accorder` |
| | `permission_rationale_notification_subtitle` | `Les notifications gardent la durée de l'entraînement, la distance, les alertes de fractionné et les commandes rapides accessibles pendant l'enregistrement en arrière-plan.` |
| | `permission_rationale_bluetooth_subtitle` | `L'autorisation Appareils à proximité connecte aTrainingTracker à vos capteurs couplés de fréquence cardiaque, puissance, vitesse et cadence.` |
| **IT** | `bluetooth_permission_scan_required` | `L'autorizzazione Bluetooth è richiesta per cercare sensori nelle vicinanze.` |
| | `grant_permission` | `Concedi` |
| | `permission_rationale_notification_subtitle` | `Le notifiche mantengono accessibili la durata dell'allenamento, la distanza, gli avvisi dei giri e i controlli rapidi durante la registrazione in background.` |
| | `permission_rationale_bluetooth_subtitle` | `L'autorizzazione Dispositivi nelle vicinanze connette aTrainingTracker ai sensori associati di frequenza cardiaca, potenza, velocità e cadenza.` |
| **JA** | `bluetooth_permission_scan_required` | `近くのセンサーを検索するにはBluetoothの権限が必要です。` |
| | `grant_permission` | `許可` |
| | `permission_rationale_notification_subtitle` | `通知により、バックグラウンドでの記録中もワークアウト時間、距離、ラップアラート、クイック操作が利用可能になります。` |
| | `permission_rationale_bluetooth_subtitle` | `付近のデバイスの権限により、aTrainingTrackerをペアリング済みの心拍数、パワー、速度、ケイデンスセンサーに接続します。` |
| **NL** | `bluetooth_permission_scan_required` | `Bluetooth-toestemming is vereist om naar sensoren in de buurt te zoeken.` |
| | `grant_permission` | `Toestaan` |
| | `permission_rationale_notification_subtitle` | `Meldingen houden trainingsduur, afstand, tussentijd-meldingen en snelle bedieningselementen toegankelijk tijdens registratie op de achtergrond.` |
| | `permission_rationale_bluetooth_subtitle` | `Toestemming voor apparaten in de buurt verbindt aTrainingTracker met uw gekoppelde hartslag-, vermogens-, snelheids- en cadanssensoren.` |
| **PL** | `bluetooth_permission_scan_required` | `Uprawnienie Bluetooth jest wymagane do wyszukiwania pobliskich czujników.` |
| | `grant_permission` | `Przyznaj` |
| | `permission_rationale_notification_subtitle` | `Powiadomienia zapewniają dostęp do czasu treningu, dystansu, alertów okrążeń i szybkich elementów sterujących podczas rejestrowania w tle.` |
| | `permission_rationale_bluetooth_subtitle` | `Uprawnienie Urządzenia w pobliżu łączy aTrainingTracker ze sparowanymi czujnikami tętna, mocy, prędkości i kadencji.` |
| **PT** | `bluetooth_permission_scan_required` | `A permissão de Bluetooth é necessária para buscar sensores próximos.` |
| | `grant_permission` | `Conceder` |
| | `permission_rationale_notification_subtitle` | `As notificações mantêm acessíveis a duração do treino, a distância, os alertas de voltas e os controles rápidos durante a gravação em segundo plano.` |
| | `permission_rationale_bluetooth_subtitle` | `A permissão de Dispositivos próximos conecta o aTrainingTracker aos seus sensores emparelhados de frequência cardíaca, potência, velocidade e cadência.` |

---

## 4. Traceability Matrix

| Test Case | Scope | Target / Component Under Test | Requirement Mapping | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PRI-005.1]` | Unit | `ControlTrackingScreen.kt` cascade logic | `[REQ-PRI-006.1]`, `[REQ-PRI-006.2]` | Specified |
| `[TST-PRI-005.2]` | Unit | `ControlTrackingScreen.kt`, `DevicesTabbedScreen.kt` sensor discovery | `[REQ-PRI-006.3]` | Specified |
| `[TST-PRI-005.3]` | Contract | `MainActivityWithNavigation.kt` legacy code elimination | `[REQ-PRI-006.5]` | Specified |
| `[TST-PRI-005.4]` | Parity | `values*/strings.xml` (9 locales) | `[REQ-PRI-006.6]`, `REQ-UI-106` | Specified |
| `[TST-PRI-005.5]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
