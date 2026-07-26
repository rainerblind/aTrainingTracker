# Implementation Plan - ATT-312: Reorder Backup & Restore to Tabbed layout

Reorganize the "Backup & Restore" screen into a standard tabbed layout with three tabs: **Import**, **Backup**, and **Restore**. Rename the screen to "Import & Backup".

## User Review Required

> [!IMPORTANT]
> The screen will be renamed to **Import & Backup** in the navigation drawer and headers. 
> The layout will change from a single scrollable list to a `HorizontalPager` with three tabs.

## Proposed Changes

### Localization & Branding

#### [MODIFY] [strings.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values/strings.xml) (and all translations)
- Add/Rename `backup_restore` to `import_backup`: "Import \u0026 Backup" (EN), "Import \u0026 Backup" (DE).
- Add `tab_import`: "Import".
- Add `tab_backup`: "Backup".
- Add `tab_restore`: "Restore" (EN), "Wiederherstellen" (DE).

### UI Components

#### [NEW] [ImportBackupTabsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt)
- Create a new file for the tabbed layout using `HorizontalPager` and `PrimaryScrollableTabRow`.
- Port content from `BackupRestoreScreen.kt` into three distinct tab views:
    - **Import**: `Import Workouts` (incremental merge) and `Legacy Recovery` (TCX).
    - **Backup**: `Create Backup` (share), `Upload to Dropbox` (manual), and `Automated Backups` configuration.
    - **Restore**: `Local Restore` and `Dropbox Restore`.

#### [DELETE] [BackupRestoreScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreScreen.kt)
- Replace with the new tabs screen.

#### [MODIFY] [BackupRestoreFragment.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/BackupRestoreFragment.kt)
- Update to use `ImportBackupTabsScreen`.

### Navigation

#### [MODIFY] [main_navigation_drawer.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/menu/main_navigation_drawer.xml)
- Update title for `drawer_backup_restore` to `@string/import_backup`.

## Verification Plan

### Automated Tests
- None.

### Manual Verification
- **Navigation**: Open the drawer and verify the "Import & Backup" label.
- **Layout**: Verify that the screen opens with the "Import" tab by default.
- **Swiping**: Verify smooth swiping between Import, Backup, and Restore tabs.
- **Functionality**:
    - Verify "Backup" tab contains the automated backup switch and manual backup buttons.
    - Verify "Import" tab contains incremental merge and legacy TCX recovery.
    - Verify "Restore" tab contains destructive recovery buttons.
