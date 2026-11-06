# Stage 1 Analysis: ATT-5477 - [Bug] ActivityThread.throwRemoteServiceException

**Ticket**: [ATT-5477](https://rainerblind.atlassian.net/browse/ATT-5477)  
**Sub-task**: [ATT-5499](https://rainerblind.atlassian.net/browse/ATT-5499) (`[Analysis]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5477`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-05  

---

## 1. Problem Statement & Motivation
In production release `V4.9.40 (271)`, Firebase Crashlytics recorded fatal `RemoteServiceException` crashes:
* `Fatal Exception: android.app.RemoteServiceException: Bad notification(tag=null, id=1) posted from package com.atrainingtracker: Couldn't create icon StatusBarIcon(icon=Icon(typ=RESOURCE pkg=com.atrainingtracker id=0x7f080138))`
* **Failure Mechanism**: `TrackerService.performStartForeground` posts notification ID 1 via `TrainingApplication.getNewNotificationBuilder().setSmallIcon(R.drawable.logo)`. Because `R.drawable.logo` is a multi-density raster PNG and `bundle.density.enableSplit` defaults to `true` in Android App Bundles, Google Play extracts density rasters into `split_config.<density>.apk`. When `com.android.systemui` resolves the status bar icon via `PackageManager.getResourcesForApplication()` using only `base.apk` (or during density switches/split desync), `Icon.loadDrawable()` fails and throws `BadForegroundServiceNotificationException`, crashing the app.
* Furthermore, Android notification guidelines mandate that status bar icons (`setSmallIcon`) must be monochrome vectors rather than colored multi-density rasters.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic RCA**:
  * Multi-density raster `R.drawable.logo` (96x84 PNG) passed across Binder IPC to `SystemUI`.
  * `app/build.gradle` omits `bundle { density { enableSplit = false } }`, letting AGP split density drawables out of `base.apk`.
  * Redundant copies placed in `drawable-nodpi/` in ATT-3529 failed to prevent SystemUI resolution failure on split APKs and created resource bloat.
* **Independent Peer Review & Verification**:
  * **Call Sites Audited**: Confirmed all 6 call sites across 5 notification managers use `R.drawable.logo`:
    1. `TrainingApplication.java:1511` (Foreground Tracking notification)
    2. `TrackerService.java:1118` (Tracking Interrupted notification)
    3. `WorkoutDeletionNotificationManager.kt:62, 84` (Deletion progress & resync notifications)
    4. `ExportNotificationManager.kt:111` (Workout export notification)
    5. `SensorBatteryNotificationManager.kt:102` (Low battery alert notification)
  * **Build Configuration**: Audited `app/build.gradle`; verified `bundle.density.enableSplit` is currently absent (defaults to `true`).
  * **Drawable Audit**: Audited `res/drawable-nodpi/`; verified redundant duplicate PNGs for `ant_logo`, `dropbox_logo_blue`, `export_*`, `logo`, `logo_512`, `logo_protocol_bluetooth`, and `stat_sys_battery_*` that already exist across `drawable-*dpi/` and have root fallbacks in `res/drawable/`.
  * Non-notification reference: `Protocol.java:27` (`ALL(R.drawable.logo)`) verified; `R.drawable.logo` will be preserved for backward compatibility.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Author dedicated monochrome VectorDrawable `app/src/main/res/drawable/ic_stat_notify_logo.xml` (24x24dp, white silhouette `#FFFFFF` with alpha transparency, guaranteed in `base.apk`).
  2. Migrate all 5 notification builders (6 call sites) to `R.drawable.ic_stat_notify_logo`.
  3. Configure `bundle { density { enableSplit = false } }` in `app/build.gradle`.
  4. Purge redundant `drawable-nodpi/` raster duplicates and update `DrawableDensityFallbackAuditTest.kt`.
* **Out-of-Scope Non-Goals**:
  * No modification to launcher icons (`ic_launcher*`), drawer branding (`logo_512`), or `Protocol.ALL`.
  * No refactoring of notification channel logic or `PendingIntent` handling.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-380` (*Defensive Compose & High-Density Asset Architecture*) and `REQ-UI-444.4` (*Master Asset Preservation & Notification Callers*). Introduces `REQ-UI-446` (*Status Bar Notification Vector Icon & Monolithic Density Packaging*).
* **Historical Origin & Commit Trace**: `ATT-3529` (`4adb2fcb`) introduced high-density raster coverage and populated `drawable-nodpi/` to mitigate split APK issues, while `ATT-1482` (`REQ-UI-444.4`) preserved `R.drawable.logo` for notification backwards compatibility.
* **Root Reason for Existing Formulation**: `ATT-3529` attempted to cover missing densities using raster copies in `nodpi`, but `SystemUI` Binder IPC requires assets present in `base.apk` without density stripping.
* **Preservation of Core Invariants & Test Audit**:
  * `DrawableDensityFallbackAuditTest.kt:88` currently asserts `targetDensities = listOf("drawable-xxhdpi", "drawable-xxxhdpi", "drawable-nodpi")`. Removing `nodpi` duplicates requires aligning `targetDensities` with `REQ-UI-380.4` (`drawable-xhdpi`, `drawable-xxhdpi`, `drawable-xxxhdpi`).
  * `ComposeSafePainterContractTest.kt` and `SafePainterResourceContractTest.kt` remain intact and 100% passing.

---

## 5. Architectural Strategy & High-Level Solution
* **Vector Notification Icon**: Construct `ic_stat_notify_logo.xml` based on authentic cyclist/stopwatch silhouette from `ic_launcher_monochrome.xml`, scaled to 24x24dp standard notification viewport.
* **Notification Wiring**: Replace `R.drawable.logo` with `R.drawable.ic_stat_notify_logo` across the 5 notification managers.
* **AGP Packaging**: Add `bundle { density { enableSplit = false } }` under `android { ... }` in `app/build.gradle`.
* **Resource Cleanup & Test Alignment**: Purge redundant files from `drawable-nodpi/` and update `DrawableDensityFallbackAuditTest.kt`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero `RemoteServiceException` / `BadForegroundServiceNotificationException` crashes on notification posting.
  2. Android notification design guidelines strictly enforced (monochrome silhouette with alpha channel).
  3. All existing unit tests pass (`./gradlew testDebugUnitTest`).
  4. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** (Defensive packaging, standard Android VectorDrawable notification pattern, zero database/concurrency risk).
