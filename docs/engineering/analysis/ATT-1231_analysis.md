# Engineering Analysis - ATT-1231

**Ticket**: `ATT-1231`: `[Verbesserung] Within the Strava popup, display 'now' while Equipment is updating`  
**Parent / Component**: Strava Integration / Equipment Synchronization (`StravaSettingsDialog.kt`, `EquipmentRepository.kt`, `StravaEquipmentSynchronizeThread.java`)  
**Sprint**: `2026-39.1`  
**Target Release**: `V4.9.38`  
**Stage**: `Stage 1: Analysis (SWE.1 / SYS.2)`

---

## 1. Problem Statement & User Motivation

Within the Strava settings bottom sheet dialog (`StravaSettingsDialog`), an action card allows the athlete to trigger manual synchronization of Strava Equipment (`@string/updateStravaEquipment`). The card displays the timestamp of the last successful synchronization (`TrainingApplication.getLastUpdateTimeOfStravaEquipment()`).

Currently, when manual equipment synchronization is triggered:
1. `(context as? Activity)?.let { StravaEquipmentSynchronizeThread(it).start() }` spawns a background `Thread` which performs multiple HTTP requests (`GET /api/v3/athlete` and individual `GET /api/v3/gear/{id}` calls for each bicycle).
2. During this multi-second process, the UI card continues to display the previous update time (or "never" / "noch nie aktualisiert").
3. The athlete receives no immediate in-card indication that synchronization is actively occurring.
4. If the athlete taps the card repeatedly, multiple redundant background synchronization threads are spawned concurrently.
5. Furthermore, transient state must remain decoupled from persistent storage: temporal indicators such as `"now"` / `"jetzt"` must **never** be written to `SharedPreferences` (`SP_LAST_UPDATE_TIME_OF_STRAVA_EQUIPMENT`), as an unhandled crash or process kill would permanently corrupt the stored timestamp with obsolete relative text.

---

## 2. Technical Investigation & Call Site Audit

### 2.1 Existing Components
1. **`StravaEquipmentSynchronizeThread.java`**:
   * Extends Java `Thread`.
   * Triggered in `StravaSettingsDialog.kt` at line 134 (on OAuth success) and line 237 (card click).
   * Fetches Strava athlete data and gear, updating `EquipmentDbHelper`.
   * Completes by calling `TrainingApplication.setLastUpdateTimeOfStravaEquipment(result)` and broadcasting `SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED`.
   * Lacks lifecycle management via `try ... finally` for safe state reset.
2. **`EquipmentRepository.kt`**:
   * Singleton repository (`getInstance(application)`).
   * Natural architectural location for reactive `isSyncing: StateFlow<Boolean>`.
   * Can expose `@JvmStatic val isSyncing: StateFlow<Boolean>` and `@JvmStatic fun setSyncing(Boolean)` so Java `StravaEquipmentSynchronizeThread` can effortlessly signal synchronization start and completion without complex coroutine interop.
3. **`StravaSettingsDialog.kt`**:
   * Manages the Strava settings bottom sheet in Jetpack Compose.
   * Already collects `isSegmentsSyncing` from `SegmentsRepository` (ATT-1219) and `isRoutesSyncing` from `RoutesRepository` (ATT-1230).
   * Needs to observe `isEquipmentSyncing` from `EquipmentRepository` and dynamically render `@string/lastUpdateOfEquipmentNow` while `isEquipmentSyncing == true`.
   * Needs to debounce click events (`if (!isEquipmentSyncing)`).
4. **Localization Resources (`strings.xml`)**:
   * Base `lastUpdateOfEquipmentNever` exists across all 9 supported languages.
   * `lastUpdateOfEquipmentNow` must be added directly adjacent to `lastUpdateOfEquipmentNever` across all 9 locales:
     - `values/strings.xml`: `now`
     - `values-de/strings.xml`: `jetzt`
     - `values-es/strings.xml`: `ahora`
     - `values-fr/strings.xml`: `maintenant`
     - `values-it/strings.xml`: `adesso`
     - `values-ja/strings.xml`: `今`
     - `values-nl/strings.xml`: `nu`
     - `values-pl/strings.xml`: `teraz`
     - `values-pt/strings.xml`: `agora`

### 2.2 Call Sites Slated for Inspection & Modification
| File | Role / Responsibility | Planned Modification |
|---|---|---|
| `EquipmentRepository.kt` | Repository layer for equipment | Expose reactive `isSyncing: StateFlow<Boolean>` backed by `_isSyncing: MutableStateFlow<Boolean>` with `@JvmStatic` setters for thread interop. |
| `StravaEquipmentSynchronizeThread.java` | Background worker thread | Set `EquipmentRepository.setSyncing(true)` at start, wrap run block in `try ... finally { EquipmentRepository.setSyncing(false); }`. |
| `StravaSettingsDialog.kt` | Presentation layer (Compose) | Collect `isEquipmentSyncing by equipmentRepo.isSyncing.collectAsState()`, debounce `onClick`, and conditionally render `lastUpdateOfEquipmentNow`. |
| `app/src/main/res/values*/strings.xml` (9 locales) | UI Localization | Add `lastUpdateOfEquipmentNow` resource across all 9 languages. |
| `EquipmentRepositorySyncTest.kt` | Unit Verification | Test `isSyncing` state transitions, exception safety, persistence decoupling, and 9-language translation parity. |

---

## 3. Requirement Mapping & Cross-Check

Cross-referencing `docs/requirements.md` for mapped requirements on target files:
* **`REQ-EXT-006`**: *Strava Profile Scope & Equipment Synchronization Integrity* (`EquipmentDbHelper.java`, `StravaEquipmentSynchronizeThread.java`, `EquipmentFragment.kt`).  
  -> *Impact*: Zero regression. Gear extraction logic, scope verification, and `SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED` broadcasts are 100% preserved.
* **`REQ-EXT-009`**: *Strava Data Deletion on Token Invalidation, Deauthorization & Reauthorization Integrity* (`StravaSettingsDialog.kt`, `EquipmentDbHelper.java`, etc.).  
  -> *Impact*: Zero regression. Deauthorization, confirmation dialogs, and gear re-linking remain unchanged.
* **`REQ-UI-153`**: *Strava Settings Modal Bottom Sheet & Navigation Integration* (`StravaSettingsDialog.kt`).  
  -> *Impact*: Zero regression. Action card styling, selective upload switches, and bottom sheet structure are preserved.
* **`REQ-EXP-014` / `REQ-EXP-015`**: *Reactive Strava Segment/Route Synchronization Progress Indicator & Transient State Decoupling*.  
  -> *Impact*: Symmetrical pattern alignment. We will introduce **`REQ-EXP-016`** to formalize this standard for equipment.

---

## 4. System Invariants & Preserved Behavior

1. **Decoupled Persistence (Storage Invariant)**:
   * Transient string `"now"` MUST NEVER be written to `SharedPreferences` (`SP_LAST_UPDATE_TIME_OF_STRAVA_EQUIPMENT`).
   * `SharedPreferences` persists strictly formatted completion timestamps (`DateFormat.getDateTimeInstance().format(Date())`) or remains `"never"`.
2. **Exception Safety & Concurrency**:
   * `isSyncing` MUST be guaranteed to reset to `false` via `finally` even if network timeouts, JSON parsing errors, or SQLite exceptions occur.
3. **Debounce Guard**:
   * Tapping the "Update Strava Equipment" card while `isSyncing == true` must be ignored to prevent redundant thread launches.
4. **Broadcast Compatibility**:
   * `mContext.sendBroadcast(new Intent(SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED))` must continue to fire to notify components like `EquipmentFragment`.

---

## 5. Risk Assessment & Recommendation

* **Risk Level**: **LOW**.  
* **Justification**: Follows the identical, vetted reactive pattern successfully implemented in `ATT-1219` (Segments) and `ATT-1230` (Routes). No database schema changes, zero external API changes, isolated to presentation and thread lifecycle tracking.
* **Recommendation**: **RECOMMEND PASS**. Proceed to Stage 2: Test Specification (`[Test-Spec]`).
