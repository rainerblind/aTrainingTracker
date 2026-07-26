# Implementation Plan - ATT-285: Full Restore via Dropbox Confirmation

Implement a mandatory safety confirmation dialog before initiating a destructive Full Restore from Dropbox.

## 1. Requirements Mapping
- **Requirement**: `REQ-MIG-007` (Full Restore Confirmation)
- **Test ID**: `TST-MIG-004` (Full Restore Confirmation - Dropbox)

## 2. Impact Analysis
- **Core Component**: `BackupRestoreScreen.kt`.
- **UI/UX**: Introduces a modal `AlertDialog` that intercepts the direct Dropbox restore trigger. This adds a critical safety layer for data sovereignty.
- **Background Logic**: No changes to `BackupRestoreViewModel` or `DropboxBackupManager` are required, as the existing trigger remains valid but is now guarded by UI consent.
- **Risk**: Low. The change only affects the presentation layer and uses established Material 3 patterns.

## 3. Proposed Changes

### 3.1 UI State Management (`BackupRestoreScreen.kt`)
- Add a new `remember` state: `showDropboxRestoreConfirm`.

### 3.2 Guarded Trigger (`BackupRestoreScreen.kt`)
- Update the "Dropbox: Restore Backup" button to set `showDropboxRestoreConfirm = true` instead of calling the ViewModel directly.

### 3.3 Confirmation Dialog (`BackupRestoreScreen.kt`)
- Implement an `AlertDialog` that:
    - Uses `R.string.restore_warning_title` for the title.
    - Uses `R.string.restore_warning_message` for the text.
    - Features a "Restore" button (with `MaterialTheme.colorScheme.error` color) that calls `viewModel.restoreFromDropbox(context)`.
    - Features a "Cancel" button that dismisses the dialog.

## 4. Verification Plan
- **Integration Verification (TST-MIG-004)**:
    1. Navigate to the Backup & Restore dashboard.
    2. Tap the "Dropbox: Restore Backup" button.
    3. **Verify**: A confirmation dialog appears.
    4. Tap "Cancel".
    5. **Verify**: No restore process starts.
    6. Tap the button again and tap "Restore".
    7. **Verify**: The restore process begins (status reporting "Downloading from Dropbox...").
