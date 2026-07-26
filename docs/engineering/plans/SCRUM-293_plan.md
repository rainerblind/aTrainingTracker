# Implementation Plan - ATT-293: More Info in Backup & Restore

Enhance the informative depth of the Backup & Restore dashboard by refining labels and adding descriptive text for destructive and complex operations.

## 1. Requirements Mapping
- **Requirement**: `REQ-MIG-008` (Informative Dashboard Labels)
- **Test ID**: `TST-MIG-005` (Informative Labels Audit)

## 2. Impact Analysis
- **Core Component**: `BackupRestoreScreen.kt`.
- **UI/UX**: Refines component labels to better reflect internal logic (e.g., dual create-then-upload flow) and adds instructional context to restoration modes.
- **Risk**: Low. Purely informational UI changes with zero logical side effects.

## 3. Proposed Changes

### 3.1 String Resources (`strings.xml`)
- Update `dropbox_backup_label` (or similar) to "Create & Upload Backup to Dropbox".
- Add `full_restore_description`: "Wipes all local data and replaces it with the content of the backup. Forces an app restart."
- Add `incremental_import_description`: "Merges workouts from a backup into your current history. Workouts already present locally will be skipped."
- Provide German translations in `values-de/strings.xml`.

### 3.2 Dashboard Refinement (`BackupRestoreScreen.kt`)
- **Backup Card**: Update the Dropbox button text.
- **Restore Card**: Insert the `full_restore_description` below the header and above the buttons.
- **Import Card**: Insert the `incremental_import_description` below the header and above the button.

## 4. Verification Plan
- **Manual Audit (TST-MIG-005)**:
    1. Launch the app and navigate to Backup & Restore.
    2. Confirm the Dropbox button label.
    3. Confirm presence and accuracy of section descriptions.
    4. Switch to German and verify localization quality.
