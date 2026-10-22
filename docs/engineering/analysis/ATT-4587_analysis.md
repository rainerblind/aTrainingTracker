# Stage 1 Analysis: ATT-4587 - [Verbesserung] Insert a newline before '(Dropbox & Google Drive)' on the unified manual cloud backup button

**Ticket**: [ATT-4587](https://rainerblind.atlassian.net/browse/ATT-4587)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4587`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`) | **Date**: 2026-10-21  

---

## 1. Problem Statement & Motivation

When both Dropbox and Google Drive cloud backup providers are connected, the manual cloud upload button on the **Backup** tab (`BackupTabContent.kt`) displays `R.string.create_and_upload_to_both`:
1. **Uncontrolled Line Wrapping**: The string currently formats as a continuous line (35–45 characters, e.g. *"Create & Upload (Dropbox & Google Drive)"* / *"Erstellen & Upload (Dropbox & Google Drive)"*). On compact displays or larger font scales, text wraps awkwardly mid-word or unevenly across lines.
2. **Button Text Alignment**: The `Text` composable for the cloud backup button in `BackupTabContent.kt` lacks explicit `textAlign = TextAlign.Center`, causing multi-line text to align to the start/left rather than centering harmoniously inside the filled button.

---

## 2. Root Cause Analysis (Forensic Investigation)

* `ATT-4518` introduced `create_and_upload_to_both` as a single linear phrase across all 9 localized `strings.xml` files without explicit line breaks.
* `BackupTabContent.kt` (lines 146) renders `Text(stringResource(cloudButtonTextRes))` without `textAlign = TextAlign.Center`, relying on Material 3's default start alignment for multi-line text in button content slots.

---

## 3. Call Site & Blast Radius Audit

| Component | Target File | Nature of Change & Impact |
| :--- | :--- | :--- |
| **Compose UI** | `BackupTabContent.kt` | Set `textAlign = TextAlign.Center` on the cloud backup button `Text` composable. |
| **Localization** | `res/values*/strings.xml` (9 locales) | Insert explicit newline (`\n` / `&#10;`) before `(Dropbox & Google Drive)` in `create_and_upload_to_both`. |
| **Unit Tests** | `BackupCloudProviderUnificationTest.kt` | Verify centered text styling contract and newline inclusion in multi-provider button text. |
| **Parity Tests** | `TranslationParityTest.kt` | Ensure all 9 locales maintain 100% translation parity and newline uniformity. |

---

## 4. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Insert an explicit newline (`\n` or `&#10;`) before `(Dropbox & Google Drive)` in `create_and_upload_to_both` across all 9 locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).
  2. Set `textAlign = TextAlign.Center` on the manual cloud backup button `Text` in `BackupTabContent.kt`.
  3. Verify translation parity with `TranslationParityTest`.
* **Out-of-Scope Non-Goals**:
  1. No modifications to single-provider button strings (`create_and_upload_to_dropbox`, `create_and_upload_to_google_drive`, `create_and_upload_to_cloud`).
  2. No changes to `BackupRestoreViewModel.uploadToCloud` mutex or persistence mechanics.

---

## 5. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: Amends `REQ-MIG-036.11` (*Dynamic Manual Cloud Backup Button Label & Provider Disconnect Prompt*).
* **Historical Origin & Commit Trace**: Introduced in `ATT-4518` (`c87f481e`, Sprint 2026-43.5).
* **Root Reason for Existing Formulation**: `ATT-4518` designed `create_and_upload_to_both` as a standard string resource without anticipating narrow-screen line-wrap aesthetics.
* **Preservation of Core Invariants**: Linguistic translation parity across all 9 locales, single-tap cloud upload dispatch, and 100% clean-room test pass rate are strictly preserved.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. 100% translation parity across all 9 supported locales.
  2. Button click semantics, provider dispatch, and mutex locking MUST NOT be altered.
  3. Clean-room unit test suite pass rate MUST remain 100%.
* **Risk Rating**: **LOW** — Strictly limited to string newline formatting and Compose text alignment token assignment.
