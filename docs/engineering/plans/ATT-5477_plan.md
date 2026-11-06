# Stage 3: Implementation Plan - ATT-5477: RemoteServiceException Notification Crash

**Ticket**: [ATT-5477](https://rainerblind.atlassian.net/browse/ATT-5477)  
**Sub-task**: [ATT-5501](https://rainerblind.atlassian.net/browse/ATT-5501) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5477`  
**Requirement Mapping**: `REQ-UI-452` | **Test Mapping**: `TST-UI-452`  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-05  

---

## 1. Problem Description & Background
In `V4.9.40 (271)`, `RemoteServiceException` crashed when `SystemUI` failed to resolve multi-density raster `R.drawable.logo` across Binder IPC. App Bundle density slicing stripped rasters from `base.apk`. Furthermore, Android status bar icons mandate monochrome vectors.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-452` (*Dedicated Status Bar Vector Icon, Notification Builder Migration & Monolithic Density Packaging*)
* **Test Mapping**: `TST-UI-452.1`..`TST-UI-452.5` (`NotificationIconContractTest`, `DrawableDensityFallbackAuditTest`)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Notification Crashes**: No `RemoteServiceException` or `BadForegroundServiceNotificationException`.
2. **Channel & Intent Invariance**: Notification IDs, channels, priorities, and `PendingIntent`s remain identical.
3. **Legacy Symbol Parity**: `R.drawable.logo` preserved for non-notification usages (`Protocol.ALL`).
4. **Android Design Standards**: Monochrome silhouette with alpha transparency on transparent background.

---

## 4. Proposed Architectural Changes

### Component 1: Monochrome Vector Notification Icon (`res/drawable/ic_stat_notify_logo.xml`)
24x24dp VectorDrawable silhouette (`#FFFFFF`), density-independent, embedded in `base.apk`.

### Component 2: Notification Managers (6 Call Sites Across 5 Classes)
Migrate `TrainingApplication.java`, `TrackerService.java`, `WorkoutDeletionNotificationManager.kt`, `ExportNotificationManager.kt`, and `SensorBatteryNotificationManager.kt` to `R.drawable.ic_stat_notify_logo`.

### Component 3: App Bundle Packaging (`app/build.gradle`)
Configure `bundle { density { enableSplit = false } }` to eliminate Split-APK drawable omissions.

### Component 4: Architecture Governance & Cleanup (`docs/architecture.md`)
Purge redundant `drawable-nodpi/` duplicates; document packaging invariants in SWE.2 Section 4.4.

### UI Consistency (Rule 23)
* **Reference Screen / Component**: Android System Notification Shade & Status Bar.
* **Reused Components / Tokens**: Monochrome white silhouette `#FFFFFF` matching Android 13+ status bar guidelines.
* **New One-Off Styles**: None (canonical vector notification asset).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Create Vector Notification Asset (`REQ-UI-452.1` / `TST-UI-452.1`)
* File: `app/src/main/res/drawable/ic_stat_notify_logo.xml`
* Action: Author 24x24dp monochrome vector silhouette without density siblings.

### Step 2: Migrate Notification Builder Call Sites (`REQ-UI-452.2` / `TST-UI-452.2`)
* Files: `TrainingApplication.java:1511`, `TrackerService.java:1118`, `WorkoutDeletionNotificationManager.kt:62,84`, `ExportNotificationManager.kt:111`, `SensorBatteryNotificationManager.kt:102`
* Action: Replace `.setSmallIcon(R.drawable.logo)` with `.setSmallIcon(R.drawable.ic_stat_notify_logo)`.

### Step 3: Deactivate Density Splitting in App Bundle (`REQ-UI-452.3` / `TST-UI-452.3`)
* File: `app/build.gradle`
* Action: Add `bundle { density { enableSplit = false } }` inside `android { ... }`.

### Step 4: Redundant Nodpi Cleanup & Test Alignment (`REQ-UI-452.4` / `TST-UI-452.4`)
* Files: `app/src/main/res/drawable-nodpi/`, `app/src/test/.../DrawableDensityFallbackAuditTest.kt`, `NotificationIconContractTest.kt`
* Action: Remove duplicate rasters from `drawable-nodpi/`; align `targetDensities` in audit test; add `NotificationIconContractTest.kt`.

### Step 5: SWE.2 Architecture Documentation Update (`REQ-ARC-001`)
* File: `docs/architecture.md`
* Action: Add Section 4.4 documenting status bar vector icon & monolithic density packaging invariant.

---

## 6. Verification & Rollback Plan
* **Verification Command**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.resources.*" --tests "com.atrainingtracker.trainingtracker.notifications.*" && python3 tools/verify_requirement_governance.py`
* **Rollback**: Atomic Git branch revert (`git checkout feature/ATT-5477`).
