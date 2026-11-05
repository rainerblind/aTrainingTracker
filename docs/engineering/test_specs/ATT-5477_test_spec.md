# Stage 2: Requirement & Test Specification - ATT-5477: RemoteServiceException Notification Crash

**Ticket**: [ATT-5477](https://rainerblind.atlassian.net/browse/ATT-5477)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5477`  
**Requirement Mapping**: `REQ-UI-452` | **Test Spec ID**: `TST-UI-452`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-05  

---

## 1. Requirement Specification (`REQ-UI-452`)

### 1.1 Problem Statement & Rationale
In production release `V4.9.40 (271)`, `RemoteServiceException` crashed when `SystemUI` failed to load `R.drawable.logo` (a multi-density raster PNG) across Binder IPC. Because `bundle.density.enableSplit` defaults to `true`, Play Store splits drawables into config APKs that SystemUI cannot reliably resolve from `base.apk`. Furthermore, Android status bar icons mandate monochrome vectors.

### 1.2 Functional & Architectural Requirements
1. **Monochrome Vector Icon (`REQ-UI-452.1`)**: Provide a single density-independent 24x24dp VectorDrawable at `res/drawable/ic_stat_notify_logo.xml` (white silhouette `#FFFFFF` with alpha transparency) without density/nodpi siblings.
2. **Notification Builder Migration (`REQ-UI-452.2`)**: Migrate all 6 `.setSmallIcon(...)` call sites across 5 notification managers (`TrainingApplication`, `TrackerService`, `WorkoutDeletionNotificationManager`, `ExportNotificationManager`, `SensorBatteryNotificationManager`) from `R.drawable.logo` to `R.drawable.ic_stat_notify_logo`.
3. **Monolithic Density Packaging (`REQ-UI-452.3`)**: Configure `bundle { density { enableSplit = false } }` in `app/build.gradle` so all drawable assets remain embedded inside `base.apk`.
4. **Nodpi Cleanup & Contract Alignment (`REQ-UI-452.4`)**: Purge redundant raster duplicates from `res/drawable-nodpi/` and update `DrawableDensityFallbackAuditTest.kt` to check canonical high-density buckets (`xhdpi`, `xxhdpi`, `xxxhdpi`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (Vector SmallIcon)**: *Given* any notification posted by the 5 managers, *When* displayed by `SystemUI`, *Then* `ic_stat_notify_logo` loads safely from `base.apk` without `RemoteServiceException`.
* **AC-2 (Bundle Configuration)**: *Given* `app/build.gradle`, *When* bundling artifacts, *Then* `bundle.density.enableSplit` is `false`.
* **AC-3 (Asset Parity & Nodpi Cleanup)**: *Given* `DrawableDensityFallbackAuditTest.kt`, *When* executed, *Then* fallback coverage and high-density coverage pass cleanly without `nodpi` duplicates.

### 1.4 System Invariants & Chesterton's Fence Audit
* **System Invariants**: Zero `RemoteServiceException` crashes; notification priorities, channels, and pending intents preserved; legacy `R.drawable.logo` preserved for non-notification usages (`Protocol.ALL`).
* **Archaeology**: Amends `REQ-UI-380` (`ATT-3529`, commit `4adb2fcb`) and `REQ-UI-444.4` (`ATT-1482`). `ATT-3529` populated `nodpi` to fix missing density crashes but did not prevent AGP bundle density splitting. Monolithic density packaging and vector assets safely supersede `nodpi` raster duplication.

---

## 2. Test Specification (`TST-UI-452`)

### Test Case 1: Vector Notification Asset & Silhouette (`TST-UI-452.1`)
* **Scope**: Contract test in `NotificationIconContractTest.kt`.
* **Procedure**: Assert `ic_stat_notify_logo.xml` exists in `res/drawable/`, has `viewportWidth=24`, `viewportHeight=24`, and has zero `drawable-*dpi` or `nodpi` siblings.

### Test Case 2: Notification Builder SmallIcon Migration (`TST-UI-452.2`)
* **Scope**: Source contract audit in `NotificationIconContractTest.kt`.
* **Procedure**: Assert 0 occurrences of `.setSmallIcon(R.drawable.logo)` and 6 occurrences of `.setSmallIcon(R.drawable.ic_stat_notify_logo)` across the 5 notification managers.

### Test Case 3: App Bundle Density Split Deactivation (`TST-UI-452.3`)
* **Scope**: Build configuration audit in `NotificationIconContractTest.kt`.
* **Procedure**: Assert `app/build.gradle` defines `bundle.density.enableSplit = false`.

### Test Case 4: Core High-Density Coverage & Fallback Audit (`TST-UI-452.4`)
* **Scope**: Resource regression in `DrawableDensityFallbackAuditTest.kt`.
* **Procedure**: Verify `testAllDensityDrawablesHaveRootFallback()` and `testCoreDrawablesHaveFullHighDensityCoverage()` pass across `xhdpi`, `xxhdpi`, and `xxxhdpi`.

### Test Case 5: Clean-Room Governance & Regression (`TST-UI-452.5`)
* **Command**: `python3 tools/verify_requirement_governance.py && ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.resources.*" --tests "com.atrainingtracker.trainingtracker.notifications.*"`
* **Expected Result**: Exit code 0, 100% test pass rate.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-452.1` | Dedicated 24x24dp monochrome VectorDrawable | `TST-UI-452.1` | Automated Unit Contract Test |
| `REQ-UI-452.2` | Universal 6 call site smallIcon migration | `TST-UI-452.2` | Automated Unit Contract Test |
| `REQ-UI-452.3` | `bundle.density.enableSplit = false` in build | `TST-UI-452.3` | Automated Unit Contract Test |
| `REQ-UI-452.4` | Nodpi cleanup & high-density coverage | `TST-UI-452.4` | Automated Unit Contract Test |
| `REQ-UI-452` | System Invariants & Zero Crash Guarantee | `TST-UI-452.5` | Targeted Regression Suite |
