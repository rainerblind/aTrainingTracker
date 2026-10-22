# Stage 1 Analysis: ATT-4588 - [Bug] Linking to Dropbox fails to complete OAuth / persist credentials

**Ticket**: [ATT-4588](https://rainerblind.atlassian.net/browse/ATT-4588)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4588`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Domain & Root Cause Analysis across 4 Candidates

Forensic investigation confirms 4 compounding defects breaking Dropbox OAuth linking and credential synchronization:
1. **Candidate 1 (Context Unwrap Failure in `DropboxSettingsDialog.kt`)**: `onConnectClick` calls `(context as? Activity)?.let { ... }`. When hosted inside `DropboxSettingsDialogFragment` or themed Compose trees, `LocalContext.current` is a `ContextThemeWrapper` / `ContextWrapper`, so the cast evaluates to `null`. `Auth.startOAuth2PKCE` is silently never executed.
2. **Candidate 2 (Double Consumption of `Auth.getDbxCredential()`)**: Both `DropboxSettingsDialogFragment.onResume()` and `DropboxSettingsDialog`'s `DisposableEffect(ON_RESUME)` call `Auth.getDbxCredential()`. The dual invocation creates an uncoordinated race across static `AuthActivity.result`, while the fragment consumer fails to update Compose `isConnected` state.
3. **Candidate 3 (Redirect URI Scheme Collision in `AndroidManifest.xml`)**: `AuthActivity` hardcodes `<data android:scheme="db-iknmdmr31sf64r0" />`. When both release (`com.atrainingtracker`) and debug (`com.atrainingtracker.debug`) packages are co-installed, `AuthActivity.checkAppBeforeAuth()` detects `activities.size > 1`, triggers an impersonation security alert dialog, and blocks OAuth launch entirely.
4. **Candidate 4 (Stale Connection State on `ImportBackupTabsScreen.kt`)**: `isDropboxConnected` is defined via `remember(uiState)`. Connecting or disconnecting Dropbox in settings does not mutate `BackupRestoreViewModel.uiState`, leaving the manual cloud backup button and summary stale until the screen is fully recreated.

---

## 2. Call Site & Blast Radius Audit

| Component | Target File | Nature of Change & Impact |
| :--- | :--- | :--- |
| **Compose Dialog** | `DropboxSettingsDialog.kt` | Add recursive `Context.findActivity(): Activity?` unwrapping; bind `onConnectClick` reliably; ensure `isConnected` reflects stored credentials. |
| **Dialog Host** | `DropboxSettingsDialogFragment.kt` | Remove redundant `onResume()` credential consumption; consolidate credential capture in `DropboxSettingsDialog` to prevent race conditions. |
| **Screen Reactive State** | `ImportBackupTabsScreen.kt` | Refresh `isDropboxConnected` and `isGoogleDriveConnected` on `Lifecycle.Event.ON_RESUME` (or preference listener) rather than solely `remember(uiState)`. |
| **Manifest / Configuration** | `AndroidManifest.xml` / `build.gradle` | Document / parameterize debug vs release URI scheme collision and dual-install isolation. |
| **Unit Tests** | `ui/settings/dropbox/` & `migration/` | Unit tests for `Context.findActivity()`, idempotent credential capture, and reactive connection state reflection. |

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Unify context unwrapping via `Context.findActivity(): Activity?` in `DropboxSettingsDialog.kt` so OAuth initiates reliably from any host.
  2. Centralize `Auth.getDbxCredential()` capture to eliminate dual-consumer race conditions and ensure immediate Compose state update.
  3. Isolate/document OAuth redirect URI collision across debug and release builds.
  4. Synchronize `isDropboxConnected` on `ImportBackupTabsScreen` on lifecycle resumption so manual backup button updates immediately.
* **Out-of-Scope Non-Goals**:
  1. No changes to `BackupWorker` background upload logic or WorkManager constraints (stabilized in ATT-3472 / ATT-4458).
  2. No changes to Google Drive OAuth flow (`GoogleDriveSettingsDialog.kt`).
  3. No redesign of Dropbox settings UI layout or brand assets.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Amends `REQ-UI-152` (*Dropbox Settings Modal Bottom Sheet & Navigation Integration*) and affects `ImportBackupTabsScreen.kt`.
* **Historical Origin & Commit Trace**: Commit `85c3a5bf` (`ATT-1051`, Sprint 2026-42.1) and `203baa27` (`ATT-4023`, Sprint 2026-42.10).
* **Root Reason for Existing Formulation**: `ATT-1051` converted Dropbox settings into a Compose dialog hosted in a DialogFragment, but introduced an unsafe `(context as? Activity)` cast and duplicated `onResume` credential checks without noticing the `ContextThemeWrapper` or static token consumption quirks.
* **Preservation of Core Invariants**: Authentic Dropbox branding, PKCE security flow, credential storage in `auth_credentials_prefs` (`REQ-DAT-032`), modal sheet dismiss mechanics, and 100% test pass rate are strictly preserved.

---

## 5. System Invariants & Risk Assessment

* **Core Invariants**:
  1. OAuth PKCE token exchange security invariants MUST be preserved.
  2. Credentials MUST remain isolated in `auth_credentials_prefs` via `TrainingApplication.storeDropboxCredential()`.
  3. `ModalBottomSheetDialogsIntegrityTest` structural contracts for `DropboxSettingsDialog` and `DropboxSettingsDialogFragment` MUST NOT be broken.
  4. 100% clean-room test suite pass rate with zero regressions.
* **Risk Rating**: **LOW** — Well-bounded defect repairs across context unwrapping, lifecycle observer deduplication, and Compose state refreshes without touching schema or core tracking engines.
