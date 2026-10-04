# Stage 1 Analysis: ATT-2079 - Inform Athlete on Process Kill Reasons (Battery Saver, LMK, Permission Revocation) via ApplicationExitInfo (Rework Cycle 2)

**Ticket**: [ATT-2079](https://rainerblind.atlassian.net/browse/ATT-2079)  
**Sub-task**: [ATT-2395](https://rainerblind.atlassian.net/browse/ATT-2395) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` (per Rule 19: Lösungsversion assigned upon completion)  
**Active Sprint**: `Sprint 2026-40.16`  
**Branch**: `feature/ATT-2079`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Statement & Motivation

During active sports tracking (cycling, running, hiking), the Android operating system may terminate the background tracking process (`TrackerService`) unexpectedly due to aggressive platform resource management. Common termination triggers include:
1. **Aggressive Battery Optimizations / Doze Mode**: OEM firmware and Android standard battery management killing background services holding partial wakelocks.
2. **Low Memory Killer (LMK)**: When the athlete opens resource-intensive foreground applications during their workout (e.g., high-resolution camera, 3D navigation, streaming music).
3. **Runtime Permission Revocation**: The operating system or user revoking location or notification permissions while tracking is active.

### Sprint 2026-40.15 Joint Review Findings (Rework Cycle 2 Drivers)
During live verification on the physical Pixel 10 test device in Sprint 2026-40.15 Review, the Cycle 1 implementation was rejected due to three critical usability and behavioral defects:

1. **Generic Dialog Title**:
   - The dialog title always displayed generic `unfinished_workout_title` (*"Training unterbrochen"* / *"Workout Interrupted"*).
   - The concrete termination reason was hidden in the message body, forcing the athlete to read long explanatory paragraphs to understand why tracking stopped.
   - **Requirement**: The concrete termination cause MUST be visible directly in the dialog title (e.g. *"Training unterbrochen: Akku-Optimierung"* or *"Training unterbrochen: Speicherengpass"*).

2. **Overly Sarcastic Tone**:
   - The escalation copy in Cycle 1 was criticized as patronizing and sarcastic (*"Wer nicht hören will..."*, *"Beratungsresistenter Athlet des Monats 🏆"*, *"doppelt bitter 🙈"*).
   - **Requirement**: Tone must be de-escalated to an empathetic, sportingly constructive, and objective athletic-partnership tone across all escalation stages and all 9 supported languages.

3. **Hyper-Aggressive Escalation Counter (Rapid Jump to Stage 3)**:
   - In practical testing, the counter jumped directly to Stage 3.
   - **Root Cause Identified**: `resolveKillReason(context)` in `ProcessExitReasonHelper.kt` invoked `incrementBatteryKillCount(context)` unconditionally on every execution. Because `StartOrResumeDialog.onCreateDialog()` calls `resolveKillReason(context)`, any configuration change (e.g. screen rotation, dark/light theme switch, split screen) immediately triggered multiple increments within seconds. Furthermore, `incrementBatteryKillCount` was called even if the athlete had already granted battery optimization exemption or if `ApplicationExitInfo` had not changed.
   - **Requirement**: Decouple evaluation from incrementation. Ensure idempotent, session-bound incrementation (incrementing at most once per distinct process exit event, and never on configuration changes). Reset the counter automatically when the athlete grants battery optimization exemption.

4. **Direct Intent Governance (Rule 21)**:
   - When the athlete taps the battery optimization button in the recovery dialog, the app must launch `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with package URI directly, avoiding unnecessary hops through generic settings.

---

## 2. Forensic Investigation & Root Cause Analysis

### Forensic Cause of Counter Race Condition
In `ProcessExitReasonHelper.kt`:
```kotlin
// Defective Cycle 1 implementation:
fun resolveKillReason(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): KillDiagnosis {
    ...
    if (detectedReason == KillReason.BATTERY_KILL) {
        escalationLevel = incrementBatteryKillCount(context) // <-- CALLED ON EVERY RESOLUTION!
        shouldShowBatteryButton = !isIgnoringBattery
    }
    ...
}
```
In Android's fragment lifecycle:
1. `MainActivityWithNavigation.checkUnfinishedWorkout()` creates `StartOrResumeDialog`.
2. `StartOrResumeDialog.onCreateDialog()` calls `resolveKillReason(context)`.
3. If the user rotates the device, Android destroys and recreates the DialogFragment, calling `onCreateDialog()` again.
4. If the user dismisses or background/foregrounds the app, `checkUnfinishedWorkout()` runs again.
5. In just 2 seconds of handling the device, the kill count increments 1 -> 2 -> 3, presenting the highest escalation stage immediately on the very first real incident!

### Idempotent Incrementation Solution
To fix this permanently:
1. Store `PREF_LAST_EVALUATED_EXIT_TIMESTAMP` in SharedPreferences.
2. When evaluating `ApplicationExitInfo` on API 30+, compare `exitInfo.timestamp` with `lastEvaluatedTimestamp`.
3. Only increment `PREF_BATTERY_KILL_COUNT` if `exitInfo.timestamp > lastEvaluatedTimestamp` (or on first detection of an unrecorded session termination).
4. Store `exitInfo.timestamp` as evaluated.
5. Subsequent calls to `resolveKillReason(context)` within the same app session or across screen rotations will read `getBatteryKillCount(context)` without re-incrementing.
6. When `isIgnoringBatteryOptimizations(context)` is `true`, reset `PREF_BATTERY_KILL_COUNT = 0`.

---

## 3. Dynamic Dialog Title Design

The dialog title in `StartOrResumeDialog.kt` will dynamically adapt to the diagnosed kill reason:

| Kill Reason | English Title (`values/strings.xml`) | German Title (`values-de/strings.xml`) |
| :--- | :--- | :--- |
| `BATTERY_KILL` | `Workout Interrupted: Battery Optimization` | `Training unterbrochen: Akku-Optimierung` |
| `LOW_MEMORY` | `Workout Interrupted: Low Memory` | `Training unterbrochen: Speicherengpass` |
| `PERMISSION_REVOKED` | `Workout Interrupted: Permission Revoked` | `Training unterbrochen: Berechtigung entzogen` |
| `GENERIC_UNFINISHED` | `Workout Interrupted` | `Training unterbrochen` |

This gives the athlete immediate clarity within the first 100 milliseconds of seeing the dialog.

---

## 4. De-Escalated Tone & Copy Architecture (All 9 Locales)

### Philosophy: Constructive Athletic Partnership
Instead of mocking the user, we position aTrainingTracker as a reliable training partner navigating the aggressive background limits of modern Android together with the athlete.

### Stage 1 (Initial Battery Interruption):
- **EN**: *"Android interrupted background tracking because battery optimization is currently enabled for this app. To ensure uninterrupted recording of your kilometers and elevation, please disable battery optimization for aTrainingTracker."*
- **DE**: *"Android hat die Hintergrundaufzeichnung unterbrochen, weil die Akku-Optimierung für diese App aktiv ist. Damit deine Kilometer und Höhenmeter zuverlässig erfasst werden, deaktiviere bitte die Akku-Optimierung für aTrainingTracker."*
- **Strava Add-on**:
  - **EN**: *"We know how important complete workout telemetry is for your Strava activities."*
  - **DE**: *"Wir wissen, wie wichtig lückenlose Trainingsaufzeichnungen für deine Strava-Aktivitäten sind."*

### Stage 2 (Repeated Battery Interruption):
- **EN**: *"Tracking was interrupted again by system battery management. Without exempting the app, Android will continue stopping background recordings during your workouts. Please take a moment to disable battery optimization."*
- **DE**: *"Die Aufzeichnung wurde erneut durch das System-Akkumanagement beendet. Ohne Ausnahme wird Android das Tracking bei längeren Einheiten immer wieder stoppen. Bitte nimm dir kurz Zeit, um die Akku-Optimierung zu deaktivieren."*

### Stage 3 (Persistent Interruption / High Escalation):
- **EN**: *"Repeated background interruptions detected. Modern Android versions strictly terminate tracking services that are not exempted from battery restrictions. Please tap the button below to exempt aTrainingTracker and protect your workouts."*
- **DE**: *"Wiederholte Unterbrechungen durch das Betriebssystem erkannt. Aktuelle Android-Versionen beenden Hintergrunddienste ohne Ausnahme zwingend, um Energie zu sparen. Bitte tippe unten auf den Button, um aTrainingTracker auszunehmen und deine Trainings zu schützen."*

### 9-Language Parity Coverage
All new and updated copy will be synchronized across:
- `values/` (English)
- `values-de/` (German)
- `values-es/` (Spanish)
- `values-fr/` (French)
- `values-it/` (Italian)
- `values-ja/` (Japanese)
- `values-nl/` (Dutch)
- `values-pl/` (Polish)
- `values-pt/` (Portuguese)

---

## 5. Direct Platform Intent Dispatching (Rule 21)

In `ProcessExitReasonHelper.openBatteryOptimizationSettings(context)`:
1. Primary dispatch:
   ```kotlin
   Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
       data = Uri.parse("package:${context.packageName}")
       addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
   }
   ```
2. Fallback on restricted OEM skins (`ActivityNotFoundException` / `SecurityException`):
   ```kotlin
   Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
   ```
3. Terminal fallback:
   ```kotlin
   Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
   ```

---

## 6. Chesterton's Fence & Invariant Preservation

* **Original Requirement**: `REQ-STB-003` (*Interrupted Workout Resumption & Unfinished Workout Recovery*).
* **Enhanced Requirement**: `REQ-STB-012` (*Forensic Process Kill Diagnosis, Progressive Escalation & Battery Optimization Guidance*).
* **Core Invariants Preserved**:
  - Resumption options (`chooseResume()`, `chooseStart()`) remain unchanged.
  - Recovery from `EXTRA_RESUME_INTERRUPTED_WORKOUT` continues to bypass the dialog cleanly.
  - No crash data is lost in SQLite.

---

## 7. Scope Bounding (ATT-1250)

### In-Scope
1. `ProcessExitReasonHelper.kt`: Idempotent session-bound counter incrementation, timestamp tracking, reset when exempted, direct Rule 21 intent dispatching.
2. `StartOrResumeDialog.kt`: Dynamic title binding based on `KillReason`, updated constructive copy display.
3. String resources: 9-language parity for dynamic titles and revised empathetic escalation copy.
4. Unit tests: Verification of idempotent counter handling, rotation resilience, dynamic title mapping, and intent fallbacks.

### Out-of-Scope
- Modifying `TrackerService` internal tracking logic or GPS recording loops.
- Altering SQLite workout schemas.

---

## 8. Verification Strategy

1. **Targeted Unit Tests (`ProcessExitReasonHelperTest`, `StartOrResumeDialogTest`)**:
   - Verify screen rotation simulation does not increment kill count.
   - Verify dynamic title resolution matches detected `KillReason`.
   - Verify reset to 0 when `isIgnoringBatteryOptimizations == true`.
   - Verify direct platform intent construction per Rule 21.
2. **Localization Audit**:
   - Ensure all title and copy keys exist and match formatting across 9 language XML files.
3. **Clean-Room Regression**:
   - Execute `./gradlew testDebugUnitTest` and assemble debug APK.
