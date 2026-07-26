# Walkthrough - ATT-285: Full Restore via Dropbox Confirmation

Implemented a safety confirmation dialog for Dropbox-based full restores to prevent accidental data loss.

## 1. Fulfilled Requirements
- **REQ-MIG-007 (Full Restore Confirmation)**: The system now requires explicit user consent before wiping the local state during a Dropbox restore.

## 2. Verification Results
- **Test ID**: `TST-MIG-004`
- **Scope**: SWE.6 Manual/Integration Verification
- **Evidence**:
    1. Tapping "Dropbox: Restore Backup" now opens a modal dialog.
    2. The dialog contains the standard danger warning (Title: "DANGER: Full Restore").
    3. Tapping "Cancel" dismisses the dialog with no side effects.
    4. Tapping "Restore" correctly triggers the `restoreFromDropbox` logic in the ViewModel, providing full status feedback as implemented in previous tickets.

## 3. Visual Changes
- The Dropbox restore flow now matches the safety level of the local restore flow, using identical warning strings and error-themed buttons for high-risk actions.
