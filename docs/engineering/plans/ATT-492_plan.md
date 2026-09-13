# Implementation Plan: ATT-492 - Responsive Layout for Pre-Import Workout Cluster Tuning

## 1. Goal Description
Resolve the cluttered/overflowing layout issue in `PreImportTuningDialog`:
1. **Vertical Scrolling (`ImportBackupTabsScreen.kt`)**: Wrap `PreImportTuningDialog` text content in a `verticalScroll(rememberScrollState())` Column container so all controls remain accessible on smaller screens without clipping.
2. **Compact Spacing (`ClusterTuningScreen.kt`)**: Add an optional `isDialog: Boolean = false` parameter to `ClusterTuningContent(...)` to apply compact 12.dp vertical spacing and adjusted text styles when rendered inside a modal dialog.

---

## 2. Proposed Changes

### Component 1: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterTuningScreen.kt`
#### [MODIFY] [ClusterTuningScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterTuningScreen.kt)
- Add `isDialog: Boolean = false` parameter to `ClusterTuningContent(...)`.
- Dynamically select vertical arrangement spacing (`if (isDialog) 12.dp else 24.dp`).

### Component 2: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`
#### [MODIFY] [ImportBackupTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt)
- Update `PreImportTuningDialog`:
  - Add `verticalScroll(rememberScrollState())` to the dialog's content Column.
  - Pass `isDialog = true` to `ClusterTuningContent(...)`.

---

## 3. Verification Plan

### Automated Tests
- Execute `:app:testDebugUnitTest` to verify layout and component compilation.

### Manual Verification Steps (`TST-MIG-009`)
1. **Dialog Layout Verification**:
   - Navigate to 'Import & Backup' -> 'Import' and trigger a TCX import to launch `PreImportTuningDialog`.
   - Verify that the layout is clean, scrollable, and compact.
   - Verify that toggling "Show details" displays the individual sliders with smooth scrolling.
   - Verify that slider positions persist when confirming the dialog.
