# Engineering Analysis - ATT-1242

**Ticket**: `ATT-1242`: `[Bug] Remove progress notification while fetching Strava equipment.`  
**Parent / Component**: Strava Integration / Equipment Synchronization (`StravaEquipmentSynchronizeThread.java`, `StravaSettingsDialog.kt`, `EquipmentRepository.kt`, `res/values*/strings.xml`)  
**Sprint**: `2026-39.1`  
**Target Release**: `V4.9.38`  
**Stage**: `Stage 1: Analysis (SWE.1 / SYS.2)`

---

## 1. Problem Statement & User Motivation

When an athlete initiates Strava equipment synchronization (either manually via the "Update Strava Equipment" card in `StravaSettingsDialog` or automatically upon successful Strava OAuth2 connection), an intrusive modal Android dialog (`android.app.ProgressDialog`) appears on screen. This dialog displays sequential progress messages:
- `"getting equipment from Strava… please wait"` (`@string/getting_equipment_from_strava`)
- `"checking Strava shoes"`
- `"getting shoes from Strava\ngot shoe: <name>"` (`@string/got_shoe`)
- `"checking Strava bikes"`
- `"getting bikes from Strava\ngot bike: <name>"` (`@string/got_bike`)

### Architectural & UX Defects of Current Behavior:
1. **Intrusive & Blocking UX**: The modal `ProgressDialog` pops up over the Compose bottom sheet (`StravaSettingsDialog`), intercepts all user touch events, and blocks navigation or interaction while multi-step HTTP requests and database operations take place.
2. **Asymmetric Inconsistency with Routes and Segments**: 
   - Under `ATT-1219` (Segments) and `ATT-1230` (Routes), synchronizations execute non-blockingly entirely in the background. The user can continue interacting with the app or close the dialog freely.
   - In `ATT-1231`, reactive in-place status tracking was introduced for Equipment via `EquipmentRepository.isSyncing: StateFlow<Boolean>`, displaying `"now"` (`@string/lastUpdateOfEquipmentNow`) directly inside the action card. 
   - The modal `ProgressDialog` is now completely redundant, obtrusive, and contradictory to the modern reactive UI paradigm established across the application.
3. **Deprecated Android Framework Component**: `android.app.ProgressDialog` has been formally deprecated by Google since Android 8.0 (API level 26). Google's guidelines mandate non-blocking in-place progress indicators (e.g. progress bars, reactive text, or background workers) rather than modal dialogs that lock user interaction.
4. **Window Leaks & Lifecycle Fragility**: Because `ProgressDialog` requires an active window attachment, rotating the device, dismissing the bottom sheet, or navigating away while the background thread is running causes `WindowManager.BadTokenException` or `IllegalArgumentException: View not attached to window manager`.
5. **Activity Context Coupling**: Because `ProgressDialog` required a valid window token, `StravaSettingsDialog.kt` had to cast `context as? Activity` before starting the worker thread (`(context as? Activity)?.let { StravaEquipmentSynchronizeThread(it).start() }`). If the composable context was not an `Activity` (e.g. `ContextThemeWrapper`), the synchronization would silently fail to start.

---

## 2. Forensic Root Cause Analysis (RCA)

### 2.1 Symptom vs. Root Cause
* **Superficial Symptom**: A legacy modal dialog appears on screen when updating Strava equipment, displaying itemized progress messages.
* **True Root Cause**: `StravaEquipmentSynchronizeThread.java` is an early legacy worker thread dating back to Android Gingerbread/Ice Cream Sandwich era. It retains a direct reference to `ProgressDialog mProgressDialog` initialized in its constructor, called via `mMainHandler.post(() -> mProgressDialog.show())`, updated via `publishProgress(String)`, and dismissed via `mProgressDialog.dismiss()`.

### 2.2 Forensic Code Path Inspection
In `app/src/main/java/com/atrainingtracker/trainingtracker/onlinecommunities/strava/StravaEquipmentSynchronizeThread.java`:
```java
// Lines 74-88:
private final Context mContext;
@Nullable
private final ProgressDialog mProgressDialog;
@Nullable
private final Handler mMainHandler;

public StravaEquipmentSynchronizeThread(Context context) {
    this(context, createSafeProgressDialog(context), createSafeHandler());
}

StravaEquipmentSynchronizeThread(Context context, @Nullable ProgressDialog progressDialog, @Nullable Handler handler) {
    mContext = context;
    mProgressDialog = progressDialog;
    mMainHandler = handler;
}
```
During execution (`run()`):
```java
// Lines 125-136:
if (mMainHandler != null) {
    mMainHandler.post(() -> {
        try {
            if (mProgressDialog != null) {
                mProgressDialog.setMessage(mContext.getString(R.string.getting_equipment_from_strava));
                mProgressDialog.show();
            }
        } catch (Exception e) {
            // Window might not be attached
        }
    });
}
```
During JSON parsing (`fillDbFromJsonObject()`):
```java
// Lines 231, 241, 279:
publishProgress("checking Strava shoes");
...
publishProgress(mContext.getString(R.string.got_shoe, name));
...
publishProgress(mContext.getString(R.string.got_bike, name));
```
And upon completion / failure:
```java
// Lines 144-150:
if (mProgressDialog != null && mProgressDialog.isShowing()) {
    try {
        mProgressDialog.dismiss();
    } catch (IllegalArgumentException e) {
        // View not attached to window manager
    }
}
```

### 2.3 Post-Completion Mechanics
Notice lines 152-164:
```java
TrainingApplication.setLastUpdateTimeOfStravaEquipment(result);

mContext.sendBroadcast(new Intent(SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED)
        .setPackage(mContext.getPackageName()));
```
Both `TrainingApplication.setLastUpdateTimeOfStravaEquipment` (which writes to `SharedPreferences` via thread-safe APIs) and `mContext.sendBroadcast` (which dispatches an Android system broadcast) are thread-safe and do **not** require execution on the Android UI Main Looper. The only reason `mMainHandler` was ever introduced was to safely invoke UI methods on `ProgressDialog`.

---

## 3. Call Sites Slated for Inspection & Modification

### 3.1 Primary Component Audit
| Component / File | Current Role | Proposed Modification | Side-Effect & Safety Analysis |
|---|---|---|---|
| `StravaEquipmentSynchronizeThread.java` | Background worker thread with legacy `ProgressDialog` UI handling. | 1. Remove `mProgressDialog` field, `createSafeProgressDialog` method, and `publishProgress` method.<br>2. Remove `mProgressDialog.show()` and `mProgressDialog.dismiss()`.<br>3. Remove all `publishProgress(...)` calls.<br>4. Simplify constructor to `public StravaEquipmentSynchronizeThread(@NonNull Context context)`.<br>5. Execute completion logic (`setLastUpdateTimeOfStravaEquipment` and broadcast) directly or cleanly via background execution. | Eliminates deprecated Android UI classes, window leak exceptions, and blocking modal dialogs. Thread execution remains isolated on worker thread. |
| `StravaSettingsDialog.kt` | Compose bottom sheet dialog for Strava settings. | Replace `(context as? Activity)?.let { StravaEquipmentSynchronizeThread(it).start() }` with `StravaEquipmentSynchronizeThread(context).start()`. | Removes fragile `Activity` cast requirement. Non-blocking thread launch works with any `Context`. |
| `app/src/main/res/values*/strings.xml` (9 locales) | String resources for UI localization. | Audit strings: `getting_equipment_from_strava`, `got_shoe`, `got_bike`.<br>Since these strings are exclusively referenced by the removed `ProgressDialog` / `publishProgress`, remove or mark obsolete across all 9 languages. | Eliminates dead resource debt across all 9 locales without breaking any other screens. |
| `EquipmentRepositorySyncTest.kt` | Unit tests for equipment sync lifecycle and concurrency. | Verify thread execution succeeds without any `ProgressDialog` instantiation or UI dependencies. | Ensures zero regression in JVM test environment. |
| `StravaEquipmentSyncTest.kt` | Unit tests for OAuth scope and JSON parsing. | Verify `fillDbFromJsonObject` parses gear correctly without attempting to post progress messages. | Confirms database operations and return values remain intact. |

---

## 4. Preserved Invariants & Architectural Integrity

### 4.1 System Invariants
1. **Background Synchronization Logic (Network & DB Invariant)**:
   - `getStravaEquipment()` MUST continue to fetch `STRAVA_URL_ATHLETE` (`GET https://www.strava.com/api/v3/athlete`) using `StravaHelper.getRefreshedAccessToken()`.
   - Parsing of `shoes` and `bikes` JSON arrays, frame type resolution (`getStravaFrameType`), and SQLite database insertion/updating in `EquipmentDbHelper` (`Equipment.db`) MUST remain 100% functionally identical.
2. **Completion Broadcast (Component Integration Invariant)**:
   - Upon completion of synchronization, the thread MUST broadcast `SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED`:
     ```java
     mContext.sendBroadcast(new Intent(SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED)
             .setPackage(mContext.getPackageName()));
     ```
   - This ensures `EquipmentFragment`'s `BroadcastReceiver` continues to reload the updated equipment list without regression.
3. **Timestamp Persistence & Decoupling Invariant (REQ-EXP-016)**:
   - `TrainingApplication.setLastUpdateTimeOfStravaEquipment(result)` MUST be invoked upon completion with the formatted date-time string.
   - Transient text (such as `"now"`) MUST NEVER be persisted to `SharedPreferences`.
4. **Two-Tier Concurrency Debounce & Lifecycle Guarantee (REQ-EXP-016)**:
   - `EquipmentRepository.isSyncing` MUST transition to `true` upon thread start and MUST reliably reset to `false` in `finally`.
   - The thread-level debounce guard (`if (EquipmentRepository.isSyncing().getValue()) return;`) MUST remain strictly enforced.
5. **Reactive Composable Rendering**:
   - `StravaSettingsDialog` MUST continue to observe `EquipmentRepository.isSyncing` and display `@string/lastUpdateOfEquipmentNow` while synchronization is active, updating to the latest timestamp upon completion.

---

## 5. Requirement Archaeology & Chesterton's Fence Audit

### 5.1 Investigation of Existing Requirements
A thorough search across `docs/requirements.md` was conducted for all requirements touching Strava equipment synchronization:
1. **`REQ-EXT-006`** (*Strava Profile Scope & Equipment Synchronization Integrity*):
   - Mandates `profile:read_all` OAuth scope, token validation, parsing of `bikes` and `shoes`, and broadcasting `SYNCHRONIZE_EQUIPMENT_STRAVA_FINISHED`.
   - *Result*: Does NOT mandate any `ProgressDialog` or modal UI notification.
2. **`REQ-EXP-016`** (*Reactive Strava Equipment Synchronization Progress Indicator & Transient State Decoupling*):
   - Mandates `EquipmentRepository.isSyncing: StateFlow<Boolean>`, `finally` reset, two-tier debounce, SharedPreferences decoupling, Compose rendering of `lastUpdateOfEquipmentNow`, and 9-language parity.
   - *Result*: Does NOT mandate any `ProgressDialog`. In fact, `REQ-EXP-016` was specifically created to provide non-blocking in-place progress tracking, rendering the legacy `ProgressDialog` completely obsolete.
3. **`REQ-UI-153`** (*Strava Settings Modal Bottom Sheet & Navigation Integration*):
   - Mandates: *"Tapping triggers background synchronization threads/coroutines."*
   - *Result*: Specifically characterizes synchronization as a background execution.

### 5.2 Architectural Conclusion
The modal `ProgressDialog` was a pre-ASPICE legacy implementation artifact that was never mandated by any formal requirement. Its presence directly conflicts with modern non-blocking background synchronization guidelines. Removing it:
- Aligns Equipment synchronization with Segments (`REQ-EXT-007` / `ATT-1219`) and Routes (`REQ-EXT-008` / `ATT-1230`).
- Fulfills the reactive in-place design established in `REQ-EXP-016`.
- Eliminates deprecated Android framework APIs and window leak vulnerabilities.

In Stage 2, we will introduce **`REQ-EXP-017`** (or update `REQ-EXP-016`) to explicitly formalize that Strava equipment synchronization SHALL execute strictly non-blockingly in the background without modal dialogs or notifications, preserving reactive in-place state tracking.

---

## 6. Verification Strategy

1. **JVM Unit Tests (`EquipmentRepositorySyncTest.kt`)**:
   - Verify `StravaEquipmentSynchronizeThread` executes cleanly without attempting to access or show `ProgressDialog`.
   - Verify `isSyncing` lifecycle transitions (`false` -> `true` -> `false`).
   - Verify completion broadcast and timestamp updates.
   - Verify two-tier debounce guard suppresses concurrent executions.
2. **JVM Unit Tests (`StravaEquipmentSyncTest.kt`)**:
   - Verify `fillDbFromJsonObject` correctly processes JSON payloads without attempting to post progress messages.
3. **Clean-Room Regression Suite**:
   - Run full project test suite (`./gradlew testDebugUnitTest`) to ensure zero regressions across all modules.
