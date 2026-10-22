# Stage 2: Requirement & Test Specification - ATT-4588: [Bug] Linking to Dropbox fails to complete OAuth / persist credentials

**Ticket**: [ATT-4588](https://rainerblind.atlassian.net/browse/ATT-4588) | **Sub-task**: Pending Submission (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4588`  
**Requirement Mapping**: `REQ-UI-152` (*Dropbox Settings Modal Bottom Sheet & Navigation Integration*)  
**Test Spec ID**: `TST-UI-105`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`) | **Date**: 2026-10-21  

---

## 1. Requirement Specification (REQ-UI-152 Clauses 1, 3, & 5)

### 1.1 Problem Statement & Rationale
Linking to Dropbox fails due to 4 compounding defects:
1. `(context as? Activity)` cast in `DropboxSettingsDialog.kt` evaluates to `null` when wrapped in `ContextThemeWrapper`, silently aborting `Auth.startOAuth2PKCE`.
2. `Auth.getDbxCredential()` is consumed concurrently in both `DropboxSettingsDialogFragment.onResume()` and `DropboxSettingsDialog` composable, causing a static token race and failure to update Compose state.
3. Hardcoded redirect URI scheme triggers `checkAppBeforeAuth()` alerts if release and debug packages are co-installed.
4. `ImportBackupTabsScreen.kt` keys `isDropboxConnected` strictly on `remember(uiState)`, leaving manual backup controls stale upon returning from settings.

### 1.2 Functional & Architectural Requirements
* **Context Unwrapping (`REQ-UI-152.1`)**: `DropboxSettingsDialog` SHALL recursively traverse `ContextWrapper.baseContext` via `Context.findActivity(): Activity?` prior to invoking `Auth.startOAuth2PKCE`.
* **Single-Authority Credential Capture (`REQ-UI-152.3`)**: Credential capture via `Auth.getDbxCredential()` SHALL be centralized exclusively in `DropboxSettingsDialog`'s `ON_RESUME` observer, immediately updating `isConnected` state; redundant consumption in `DropboxSettingsDialogFragment.onResume()` SHALL be removed.
* **Reactive Connection State & Redirect Isolation (`REQ-UI-152.5`)**: `ImportBackupTabsScreen` SHALL reactively refresh `isDropboxConnected` and `isGoogleDriveConnected` on `Lifecycle.Event.ON_RESUME`. Manifest redirect schemes SHALL isolate build variants.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* `DropboxSettingsDialog` hosted within a `ContextWrapper`, *When* "Mit Dropbox verknüpfen" is tapped, *Then* `Context.findActivity()` unwraps the host `Activity` and launches OAuth PKCE.
* *Given* browser OAuth authorization completes, *When* returning to `DropboxSettingsDialog`, *Then* `Auth.getDbxCredential()` is captured once by `DropboxSettingsDialog` and `isConnected` updates immediately.
* *Given* `ImportBackupTabsScreen`, *When* returning from settings after modifying Dropbox connection, *Then* `isDropboxConnected` refreshes on `ON_RESUME` without screen recreation.

### 1.4 System Invariants
1. OAuth PKCE exchange invariants and `auth_credentials_prefs` storage MUST remain intact.
2. `ModalBottomSheetDialogsIntegrityTest` structural contracts for `DropboxSettingsDialog` and `DropboxSettingsDialogFragment` MUST NOT be broken.
3. 100% clean-room test pass rate with zero regressions.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

* `Original Requirement ID & Target`: Refines `REQ-UI-152` (targets `DropboxSettingsDialog.kt`, `DropboxSettingsDialogFragment.kt`, `ImportBackupTabsScreen.kt`, `AndroidManifest.xml`).
* `Historical Origin & Commit Trace`: Commit `85c3a5bf` (`ATT-1051`, Sprint 2026-42.1), `203baa27` (`ATT-4023`, Sprint 2026-42.10), and Sprint 2026-43.6 (`ATT-4588`).
* `Root Reason for Existing Formulation`: `ATT-1051` converted Dropbox settings to Compose within a DialogFragment, but introduced an unsafe `(context as? Activity)` cast and duplicated `onResume` credential checks without noticing the `ContextThemeWrapper` or static token consumption quirks.
* `Preservation of Core Invariants`: Authentic Dropbox branding, PKCE security flow, credential storage in `auth_credentials_prefs` (`REQ-DAT-032`), modal sheet dismiss mechanics, and 100% test pass rate are strictly preserved.

---

## 3. Test Specification (TST-UI-105)

* **`TST-UI-105.1`**: Recursive `Context.findActivity()` unwrapping tests resolving `Activity` through multiple layers of `ContextWrapper` / `ContextThemeWrapper` (`DropboxSettingsDialogTest.kt`).
* **`TST-UI-105.2`**: Single-authority credential capture and reactive `isConnected` update test on `ON_RESUME` (`DropboxSettingsDialogTest.kt`).
* **`TST-UI-105.3`**: Fragment lifecycle deduplication verification (`DropboxSettingsDialogFragmentTest.kt`).
* **`TST-UI-105.4`**: `ImportBackupTabsScreen` reactive connection state refresh verification on `ON_RESUME` (`ImportBackupTabsScreenTest.kt`).
* **`TST-UI-105.5`**: Clean-room regression suite (`./gradlew testDebugUnitTest`) and structural integrity verification (`ModalBottomSheetDialogsIntegrityTest.kt`).

---

## 4. Traceability Matrix

| Test Case | Scope | Method Under Test / Subject | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-105.1]` | Unit | `Context.findActivity()` recursive unwrap | `REQ-UI-152.1` | Specified (ATT-4588) |
| `[TST-UI-105.2]` | Unit | `DropboxSettingsDialog` credential capture & `isConnected` | `REQ-UI-152.3` | Specified (ATT-4588) |
| `[TST-UI-105.3]` | Unit | `DropboxSettingsDialogFragment.onResume()` deduplication | `REQ-UI-152.3` | Specified (ATT-4588) |
| `[TST-UI-105.4]` | Unit | `ImportBackupTabsScreen` `ON_RESUME` refresh | `REQ-UI-152.5` | Specified (ATT-4588) |
| `[TST-UI-105.5]` | Regression | Reflection integrity & full suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Specified (ATT-4588) |
