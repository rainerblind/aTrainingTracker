# Stage 3: Implementation Plan - ATT-4588: [Bug] Linking to Dropbox fails to complete OAuth / persist credentials

**Ticket**: [ATT-4588](https://rainerblind.atlassian.net/browse/ATT-4588) | **Sub-task**: Pending Submission (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4588`  
**Requirement Mapping**: `REQ-UI-152` (Clauses 1, 3, 5) | **Test Mapping**: `TST-UI-105` (Clauses 1–5)  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Description & Background

Linking to Dropbox fails due to 4 compounding defects:
1. `(context as? Activity)` cast in `DropboxSettingsDialog.kt` evaluates to `null` under `ContextThemeWrapper`, silently aborting `Auth.startOAuth2PKCE`.
2. `Auth.getDbxCredential()` is consumed concurrently in both `DropboxSettingsDialogFragment.onResume()` and `DropboxSettingsDialog.kt`, racing over static `AuthActivity.result` and dropping Compose state updates.
3. Co-installation of release and debug packages causes `AuthActivity.checkAppBeforeAuth()` to detect scheme collision, blocking OAuth.
4. `ImportBackupTabsScreen.kt` keys `isDropboxConnected` strictly on `remember(uiState)`, leaving manual backup controls stale upon returning from settings.

---

## 2. Proposed Architectural Changes (SWE.2)

| Component / File | Architectural Responsibility & Modification |
| :--- | :--- |
| **Compose Dialog**<br>`DropboxSettingsDialog.kt` | Add `tailrec fun Context.findActivity(): Activity?` unwrapping `ContextWrapper` chains; invoke `Auth.startOAuth2PKCE` via resolved `Activity`; centralize `Auth.getDbxCredential()` capture in `DisposableEffect(ON_RESUME)` and update `isConnected` atomically. |
| **DialogFragment Host**<br>`DropboxSettingsDialogFragment.kt` | Remove redundant `onResume()` credential consumption to establish single-authority capture in Compose dialog, eliminating token consumption race. |
| **Screen Reactive State**<br>`ImportBackupTabsScreen.kt` | Observe `Lifecycle.Event.ON_RESUME` via `DisposableEffect` (or resume trigger) so `isDropboxConnected` and `isGoogleDriveConnected` re-evaluate immediately when returning from settings. |
| **Unit Tests**<br>`ui/settings/dropbox/` & `migration/` | Add `DropboxSettingsDialogTest.kt` verifying `findActivity` unwrapping, single-authority credential capture, and `ImportBackupTabsScreen` resume refresh. |

### UI Consistency (Rule 23)
* **Reference**: `DropboxSettingsDialog.kt`, `GoogleDriveSettingsDialog.kt`, `ImportBackupTabsScreen.kt`.
* **Reused Components**: `AppBottomSheetContent`, `AppDialogActions.SaveCancel`, `DropboxConnectionHeader`, `DropdownSelector`.
* **Theme Tokens**: `MaterialTheme.colorScheme`, authentic Dropbox brand colors (`Color.Unspecified` logo tint), standard 16.dp padding. Zero one-off styles.

---

## 3. Step-by-Step Implementation Sequence (Stage 4)

### Step 1: Implement `Context.findActivity()` & OAuth Launch in `DropboxSettingsDialog.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/dropbox/DropboxSettingsDialog.kt`
* Logic: Add `internal tailrec fun Context.findActivity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.findActivity(); else -> null }`. Use in `onConnectClick` to launch `Auth.startOAuth2PKCE`.

### Step 2: Establish Single-Authority Credential Capture
* Files: `DropboxSettingsDialogFragment.kt`, `DropboxSettingsDialog.kt`
* Logic: Remove `onResume()` override in `DropboxSettingsDialogFragment.kt`. In `DropboxSettingsDialog.kt`, ensure `ON_RESUME` captures `Auth.getDbxCredential()`, persists it to `TrainingApplication.storeDropboxCredential()`, and updates `isConnected = (TrainingApplication.uploadToDropbox() && TrainingApplication.readDropboxCredential() != null)`.

### Step 3: Reactive Connection Refresh in `ImportBackupTabsScreen.kt`
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/migration/ImportBackupTabsScreen.kt`
* Logic: Introduce `resumeTrigger` incremented on `Lifecycle.Event.ON_RESUME`; key `isDropboxConnected` and `isGoogleDriveConnected` on `remember(uiState, resumeTrigger)` to guarantee immediate UI synchronization.

### Step 4: Unit Testing & Contract Verification (`TST-UI-105`)
* Files: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/dropbox/DropboxSettingsDialogTest.kt`
* Tests: Test `findActivity` unwrapping across nested `ContextWrapper`s, test single-authority credential capture, and run `ModalBottomSheetDialogsIntegrityTest`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.dropbox.*"`

---

## 4. System Invariants & Rollback Plan

* **Core Invariants**: OAuth PKCE protocol integrity; isolated credential storage in `auth_credentials_prefs` (`REQ-DAT-032`); `ModalBottomSheetDialogsIntegrityTest` structural contracts; 100% test pass rate.
* **Rollback Plan**: Changes isolated to `feature/ATT-4588`; cleanly revertable via git if needed.
