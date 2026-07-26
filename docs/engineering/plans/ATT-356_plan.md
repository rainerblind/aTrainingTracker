# Implementation Plan - ATT-356: Dropbox Wait Feedback

Improve user transparency during TCX imports by providing explicit status feedback when the system is waiting for network responses from Dropbox.

## User Review Required

> [!IMPORTANT]
> - **Visual Feedback**: During a bulk TCX recovery, you will now see specific status messages in the progress card:
>   - *"Scanning [folder] on Dropbox..."* (instead of a generic scanning message)
>   - *"Downloading [file] from Dropbox..."* (newly added to indicate the start of a potentially slow download)
> - **Localization**: These messages will be fully translated into all 9 supported languages.

## Proposed Changes

### 1. Localization Layer: Descriptive Statuses
Fulfills REQ-MIG-019 | Test: TST-MIG-015

#### [MODIFY] `strings.xml` (and all translations)
- Add new string resources:
    - `legacy_import__scanning_dropbox`: "Scanning %1$s on Dropbox..."
    - `legacy_import__downloading_dropbox`: "Downloading %1$s from Dropbox..."

### 2. Migration Engine: Status Signaling
Fulfills REQ-MIG-019 | Test: TST-MIG-015

#### [MODIFY] [LegacyImportEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt)
- Update **`bulkRecoverFromDropbox`**:
    - Use `R.string.legacy_import__scanning_dropbox` before the `listFolder` call.
    - Insert a `listener?.onStatus` call using `R.string.legacy_import__downloading_dropbox` immediately before `dbxClient.files().download(...)`.
    - This ensures that the UI reflects the "waiting" state for both discovery and retrieval.

## Verification Plan

### Manual Verification (TST-MIG-015)
1. Navigate to 'Import & Backup' screen.
2. Trigger 'Scan TCX' from Dropbox.
3. **Verify** that the status message in the progress card shows "Scanning..." when searching folders.
4. **Verify** that it shows "Downloading [filename] from Dropbox..." before each file is processed.
5. Check that translations appear correctly when the system language is changed.
