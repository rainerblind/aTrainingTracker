# Implementation Plan: Fix Dropbox Connection (ATT-244)

## 1. Requirement Traceability

| ID | Description | Component | Test ID |
|:---|:---|:---|:---|
| **REQ-EXP-002** | Ensure Dropbox connection state is correctly persisted and reflected in UI after OAuth flow. | `CloudUploadFragment.kt` | `TST-REG-008` |

## 2. Root Cause Analysis (RCA)
- The system correctly initiates the Dropbox OAuth flow but fails to update the global `uploadToDropbox` preference when the user returns to the app.
- The `mAwaitDropboxResult` flag is transient and lost if the application process is killed by the OS while the browser is in the foreground (standard Android behavior).
- Successful credential retrieval does not trigger a UI refresh of the connection status header.

## 3. Proposed Changes

### `CloudUploadFragment.kt`
1.  **State Persistence**: Implement `onSaveInstanceState` to save the `mAwaitDropboxResult` flag.
2.  **State Restoration**: Update `onCreate` (or check in `onResume`) to restore the flag from `savedInstanceState`.
3.  **Logic Fix**: In `onResume`, after calling `Auth.getDbxCredential()` and storing it:
    *   Explicitly call `TrainingApplication.setUploadToDropbox(true)` if the credential is non-null.
    *   Call `updateHeaderContent()` to refresh the Compose-based status header.
4.  **Cleanup**: Ensure `mAwaitDropboxResult` is reset only after the result is processed.

## 4. Impact Analysis
- **System**: Uses standard Dropbox SDK authentication patterns. No impact on battery or background execution rules.
- **Interfaces**: Modifies `SharedPreferences` values already observed by the fragment.
- **Data Integrity**: Dropbox credentials are stored using the existing `TrainingApplication` helper which persists them in private `SharedPreferences`.

## 5. Verification Plan (TST-REG-008)
1.  Open Navigation Drawer -> Synchronization -> Dropbox.
2.  Tap "Connect to Dropbox".
3.  Complete authorization in the browser.
4.  Return to the app.
5.  **Expected Result**: The header immediately shows "Connected to Dropbox" (Green).
6.  Navigate to Workouts and back to Dropbox.
7.  **Expected Result**: The "Connected" state is maintained.
