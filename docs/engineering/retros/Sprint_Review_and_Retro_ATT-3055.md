# Sprint Review & Retrospective: Sprint 2026-41.6

* **Ticket**: [ATT-3055](https://atrainingtracker.atlassian.net/browse/ATT-3055) (*Review & Retro - Sprint 2026-41.6*)
* **Sprint**: `2026-41.6`
* **Branch**: `sprint/2026-41.6` -> `develop`
* **Target Hardware**: Google Pixel 10 (Android 16 preview / SDK 37, Device ID `66020DLCR002FL`)
* **Date**: 2026-10-10

---

## 1. Executive Summary & Review Outcomes

Sprint **2026-41.6** delivered major advancements across navigation guidance, turn prompts, climb/segment visualization, map layering, modern Material 3 dialogs, and a critical system-level workout finalization recovery:

1. **System Defect Recovery & Forensic Bisection ([ATT-2972](https://atlassian.net/browse/ATT-2972))**:
   - Resolved a critical workout finalization failure where workouts remained marked `FINISHED = 0`, terminal completion broadcasts were dropped, `EditWorkoutScreen` failed to open, and false crash recovery dialogs appeared on restart.
   - Formalized the **`regression-bisect`** skill and **`tools/sprint_bisect.py`**, pinpointing the exact breaking commit across 220 commits in 8 evaluation steps.
2. **Material 3 Dialog & Banner Modernization**:
   - Modernized ANT missing adapter/dependency alerts ([ATT-2939](https://atlassian.net/browse/ATT-2939)).
   - Modernized workout crash recovery dialog ([ATT-2948](https://atlassian.net/browse/ATT-2948)).
   - Removed duplicate upper active route banner from Route Selector dialog ([ATT-2943](https://atlassian.net/browse/ATT-2943)).
3. **Map Layering & Navigation Ergonomics**:
   - Route dual-layer Z-index & transparent dashed segment gaps ([ATT-2864](https://atlassian.net/browse/ATT-2864)).
   - Map layers control menu on general map ([ATT-2931](https://atlassian.net/browse/ATT-2931)).
   - Speed-dependent zoom curve and forward lookahead padding ([ATT-2946](https://atlassian.net/browse/ATT-2946)).

In accordance with **Rule 16 (Install Before Review)**, the sprint build was compiled from `sprint/2026-41.6` and installed onto the physical Google Pixel 10 prior to evaluation. All sprint tickets were inspected strictly one-by-one in rank order (**Rules 9 & 14**).

---

### 1.1 Review Evaluation Decisions

| Ticket | Summary | Review Result | Target Version | Status & Follow-up Actions |
| :--- | :--- | :---: | :---: | :--- |
| **[ATT-2860](https://atlassian.net/browse/ATT-2860)** | Visual & UX styling polish for Climb & Segment detail bottom sheets | **Approved & Accepted** | `V4.9.39` | Segment styling approved. Follow-up **[ATT-3053](https://atlassian.net/browse/ATT-3053)** created to harmonize Climb popup layout with Segment/Route base layout. |
| **[ATT-2864](https://atlassian.net/browse/ATT-2864)** | Route dual-layer Z-index & transparent dashed segment gaps | **Approved & Accepted** | `V4.9.39` | Verified and approved. |
| **[ATT-2931](https://atlassian.net/browse/ATT-2931)** | Layers control menu to toggle routes, segments, markers, and track | **Approved & Accepted** | `V4.9.39` | Verified and approved. |
| **[ATT-2940](https://atlassian.net/browse/ATT-2940)** | Selected route polyline thickness & dashed overlay | **Rejected (n.i.O.)** | — | Bounced to `Analysis` (FixVersion cleared) to clarify multi-route selection criteria. |
| **[ATT-2969](https://atlassian.net/browse/ATT-2969)** | Synchronized TCX replay tool with PyQt6 GUI, BLE & mock GPS | **Rejected (n.i.O.)** | — | Bounced to `Zu erledigen` (FixVersion cleared) for hardware/BLE connection troubleshooting. |
| **[ATT-2938](https://atlassian.net/browse/ATT-2938)** | Prevent unsolicited ReturnNavigationHud activation | **Deferred / In Review** | `V4.9.39` | Stays in `Final Review (Human)` for later detailed testing. |
| **[ATT-2939](https://atlassian.net/browse/ATT-2939)** | Modernize ANT missing adapter / dependency alerts to M3 Compose | **Approved & Accepted** | `V4.9.39` | Verified and approved. |
| **[ATT-2941](https://atlassian.net/browse/ATT-2941)** | Align turn-by-turn navigation hints UI with updated design guidelines | **Deferred / In Review** | `V4.9.39` | Stays in `Final Review (Human)` for later detailed testing. |
| **[ATT-2942](https://atlassian.net/browse/ATT-2942)** | Restrict in-ride fork candidate matching to active selected routes | **Deferred / In Review** | `V4.9.39` | Stays in `Final Review (Human)` for later detailed testing. |
| **[ATT-2943](https://atlassian.net/browse/ATT-2943)** | Remove duplicate upper active route banner from Route Selector dialog | **Approved & Accepted** | `V4.9.39` | Verified and approved. |
| **[ATT-2944](https://atlassian.net/browse/ATT-2944)** | Isolate tracking map composable from sensor grid telemetry recomposition | **Deferred / In Review** | `V4.9.39` | Stays in `Final Review (Human)` for later detailed testing. |
| **[ATT-2945](https://atlassian.net/browse/ATT-2945)** | Suppress climb cockpit bottom sheet on non-climb tracking tabs | **Deferred / In Review** | `V4.9.39` | Stays in `Final Review (Human)` for later detailed testing. |
| **[ATT-2946](https://atlassian.net/browse/ATT-2946)** | Speed-dependent zoom curve and lookahead camera padding | **Approved & Accepted** | `V4.9.39` | Approved base. Follow-up **[ATT-3054](https://atlassian.net/browse/ATT-3054)** created to invert padding to top and add zoom direction hints in settings. |
| **[ATT-2947](https://atlassian.net/browse/ATT-2947)** | Align Live Climb cockpit sheet visual design with Live Segment popup | **Deferred / In Review** | `V4.9.39` | Stays in `Final Review (Human)` for later detailed testing. |
| **[ATT-2948](https://atlassian.net/browse/ATT-2948)** | Modernize StartOrResume workout recovery dialog with M3 Compose | **Deferred / In Review** | `V4.9.39` | Stays in `Final Review (Human)` for later detailed testing. |
| **[ATT-2962](https://atlassian.net/browse/ATT-2962)** | Modernize in-ride fork decision card with direction grouping and white surfaces | **Deferred / In Review** | `V4.9.39` | Stays in `Final Review (Human)` for testing with TCX tool. |
| **[ATT-2964](https://atlassian.net/browse/ATT-2964)** | Suppress in-ride fork decision alerts when all routes share same direction | **Deferred / In Review** | `V4.9.39` | Stays in `Final Review (Human)` for testing with TCX tool. |
| **[ATT-2967](https://atlassian.net/browse/ATT-2967)** | Athlete position marker on Live Segment elevation profile graph | **Deferred / In Review** | `V4.9.39` | Stays in `Final Review (Human)` for testing with TCX tool. |
| **[ATT-3044](https://atlassian.net/browse/ATT-3044)** | Today's TCX export and import did not work | **Completed & Closed** | — | Fixed and closed during earlier diagnostic session. |
| **[ATT-2972](https://atlassian.net/browse/ATT-2972)** | Workout finalization failure on stop | **Approved & Accepted** | `V4.9.39` | SecurityException resolved, dynamic FGS gating, transactional finalization verified on Pixel 10. |

---

## 2. Topic 1 Deep Dive: Forensic Archaeology of ATT-2972

### 2.1 The Incident & The Failure Cascade
During Sprint 2026-41.4, commit `53370e78` (ticket `ATT-2773`) addressed Bluetooth LE sensor discovery on Android 14+. To comply with Android 14 foreground service policies, the commit introduced:
```java
int foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
    foregroundServiceType |= ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH | ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
}
performStartForeground(TrainingApplication.TRACKING_NOTIFICATION_ID, notification, foregroundServiceType);
```
**Why the bug entered the code**:
1. *API Permission Subtlety*: In Android 14 (API 34), passing `FOREGROUND_SERVICE_TYPE_HEALTH` strictly requires that at least one runtime health permission (e.g. `ACTIVITY_RECOGNITION`) is granted before calling `startForeground()`. If not granted, `ActivityManagerService` throws a fatal `SecurityException`.
2. *Robolectric / Unit Test Blind Spot*: In headless unit test suites, Android system services do not enforce OS-level runtime permission checks on `startForeground()`. All 2,176 unit tests passed 100%.
3. *Swallowed Exception into Poisoned State*: `TrackerService.onStartCommand()` caught the `SecurityException` and set `mTrackingInterrupted = true`.
4. *Delayed Detonation*: While active tracking continued to function visibly (GPS updates, sensor display), when the athlete stopped the workout, `onDestroy()` encountered an ancient guard:
   ```java
   if (!mTrackingInterrupted) {
       endWorkout();
   }
   ```
   Because `mTrackingInterrupted` had been poisoned at startup, `endWorkout()` was silently skipped!

### 2.2 The Bisection Breakthrough
Rather than speculative debugging, the team formulated the **`regression-bisect`** methodology and authored `tools/sprint_bisect.py`. By bounding the working milestone (`Sprint 2026-41.1`, commit `d70612e8`) and the broken milestone (`Sprint 2026-41.4`, commit `813e94ca`), binary interval bisection evaluated just **8 test builds across 220 commits**, isolating commit `53370e78` in minutes.

---

## 3. Topic 2 Deep Dive: Developer Tooling & Real-World Host Testing (ATT-2969)

### 3.1 The Gap Between Mocks and Reality
For `ATT-2969` (TCX workout replay tool), all mock tests passed in Python and Gradle. However, real-world host execution failed:
* **Linux BLE Peripheral Requirements**: Emulating BLE GATT peripherals requires Linux BlueZ (`org.bluez.LEAdvertisement1`) and controller hardware supporting the Broadcaster/Peripheral role. Many PC/laptop Bluetooth chipsets only support Central mode, and D-Bus requires specific user group permissions or experimental daemon flags.
* **Android Mock Location Requirements**: OS-level mock location requires developer settings configuration on the device.

### 3.2 Countermeasure
Any developer tool interacting with host hardware must include an automated **Pre-Flight Diagnostic** on startup that checks adapter capabilities, D-Bus permissions, and device connectivity before attempting replay.

---

## 4. Topic 3 & 4 Deep Dives: UI Mathematics & Component Harmonization

### 4.1 Camera Geometry Math (ATT-2946 & ATT-3054)
In Google Maps camera geometry, `GoogleMap.setPadding(left, top, right, bottom)` shifts the projection center *away* from the padded edge. Specifying `bottomPadding` told Google Maps the bottom was obscured by UI, shifting the camera target **upwards** and pushing the rider into the upper edge. In addition, numerical zoom sliders lacked orientation cues.
* *Countermeasures*: Follow-up **[ATT-3054](https://atlassian.net/browse/ATT-3054)** filed to invert padding to `top` and add directional hints (`Höher = Näher`) across all 9 languages.

### 4.2 Shared Base Layout Architecture (ATT-2860 & ATT-3053)
Divergence between Climb and Segment popups confirmed that map detail sheets must share a single underlying layout component (`MapDetailLayout`) rather than maintaining independent composables.
* *Countermeasures*: Follow-up **[ATT-3053](https://atlassian.net/browse/ATT-3053)** filed to harmonize Climb popup layout with Segment/Route base layout.

---

## 5. Process Hardening & Governance Additions

The following governance rules are permanently established:

### Rule 29: Defensive Service Lifecycle Finalization & FGS Permission Gating
1. **Permission-Gated FGS Types**: Never request specialized Android 14+ foreground service types (`FOREGROUND_SERVICE_TYPE_HEALTH`, `CONNECTED_DEVICE`) without dynamically verifying held runtime permissions (`ContextCompat.checkSelfPermission(...)`).
2. **Unconditional Finalization**: Critical session teardown, database state closure (`FINISHED = 1`), and terminal broadcast intents must never be conditional on transient error flags (`mTrackingInterrupted`). If a session ID exists, database commits must be enclosed in an explicit transaction (`beginTransaction() ... endTransaction()`) and completion dispatches guaranteed in `finally` blocks.

### Rule 30: Developer Tool Host Pre-Flight Diagnostics
Any developer utility, simulator, or replay tool relying on host hardware interfaces (Bluetooth LE peripherals, D-Bus, serial ports, ADB) must execute a self-diagnostic pre-flight check at launch, verifying hardware capabilities and system permissions, and surface clear actionable guidance if prerequisites are missing.

### Rule 31: Explanatory Scale Direction in Settings Sliders
Whenever exposing numerical tuning sliders (such as map zoom levels, camera tilt, or sensitivity curves), the preference subtitle or label must explicitly state the physical effect of directionality (e.g. *"Höherer Wert = Näher herangezoomt"*).
