# Stage 2: Requirement & Test Specification - ATT-4587: [Verbesserung] Insert a newline before '(Dropbox & Google Drive)' on the unified manual cloud backup button

**Ticket**: [ATT-4587](https://rainerblind.atlassian.net/browse/ATT-4587) | **Sub-task**: Pending Submission (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4587`  
**Requirement Mapping**: `REQ-MIG-036` (Clause 11) | **Test Spec ID**: `TST-MIG-036`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Requirement Specification (REQ-MIG-036 Clause 11)

### 1.1 Problem Statement & Rationale
When both Dropbox and Google Drive are connected, the manual cloud backup button displays `R.string.create_and_upload_to_both`. Without explicit line breaking, the linear string (35–45 characters) wraps awkwardly mid-word on compact displays or large font scales. Additionally, the `Text` composable lacks `textAlign = TextAlign.Center`, causing multi-line text to align to the start edge rather than centering harmoniously inside the filled button.

### 1.2 Functional & Architectural Requirements
* **Explicit Newline in Multi-Provider Label (`REQ-MIG-036.11`)**: `create_and_upload_to_both` SHALL contain an explicit literal newline `\n` preceding `(Dropbox & Google Drive)` across all 9 supported locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`). XML entity `&#10;` SHALL NOT be used to prevent AAPT2 whitespace collapse.
* **Centered Button Text Alignment (`REQ-MIG-036.11`)**: In `BackupTabContent.kt`, the cloud backup button's `Text` composable SHALL enforce `textAlign = TextAlign.Center`, ensuring multi-line labels render centered within the button container.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* both Dropbox and Google Drive are connected, *When* `BackupTabContent` renders, *Then* the cloud backup button displays `create_and_upload_to_both` formatted across two lines with centered alignment (`textAlign = TextAlign.Center`).
* *Given* any of the 9 supported locales, *When* `create_and_upload_to_both` is resolved, *Then* it contains exactly one newline `\n` immediately before `(Dropbox & Google Drive)`.

### 1.4 System Invariants
1. Single-provider button labels (`create_and_upload_to_dropbox`, `create_and_upload_to_google_drive`, `create_and_upload_to_cloud`) MUST NOT be altered.
2. Button click semantics, `uploadToCloud` mutex acquisition, and upload dispatch MUST remain untouched.
3. 100% clean-room test pass rate and 100% translation parity across all 9 locales.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

* `Original Requirement ID & Target`: Refines `REQ-MIG-036.11` (targets `BackupTabContent.kt`, `res/values*/strings.xml`).
* `Historical Origin & Commit Trace`: Introduced in `ATT-4518` (commit `01b13096`, Sprint 2026-43.5), refined in `ATT-4587` (Sprint 2026-43.6).
* `Root Reason for Existing Formulation`: Single-line text lacked explicit line breaking, leading to mid-word wrapping on narrow devices, and button text composable lacked explicit `textAlign = TextAlign.Center`.
* `Preservation of Core Invariants`: 9-language parity, click/upload dispatch semantics, BackupManager concurrency lock, and 100% test pass rate strictly preserved.

---

## 3. Test Specification (TST-MIG-036)

* **`TST-MIG-036.7`**: Cloud backup button centered text styling verification in `BackupTabContentTest.kt` (`textAlign == TextAlign.Center`).
* **`TST-MIG-036.8`**: 9-Language Localization parity & newline verification in `TranslationParityTest.kt` (`create_and_upload_to_both` contains `\n` in all 9 locales).
* **`TST-MIG-036.9`**: Clean-room regression suite execution (`./gradlew testDebugUnitTest`).

---

## 4. Traceability Matrix

| Test Case | Scope | Method Under Test / Subject | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-MIG-036.7]` | UI / Unit | `BackupTabContent` button `textAlign = TextAlign.Center` | `REQ-MIG-036.11` | Specified (ATT-4587) |
| `[TST-MIG-036.8]` | Localization | 9-locale `\n` presence in `create_and_upload_to_both` | `REQ-MIG-036.11` | Specified (ATT-4587) |
| `[TST-MIG-036.9]` | Regression | Full test suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Specified (ATT-4587) |
