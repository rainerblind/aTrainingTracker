# Stage 1 Analysis: ATT-8325 - Executable Verification for Data Migration & Export (25 TSTs)

**Ticket**: [ATT-8325](https://rainerblind.atlassian.net/browse/ATT-8325)  
**Sub-task**: Pending `submit-stage 1` (`[Analysis]`)  
**Parent Epic**: [ATT-8138](https://rainerblind.atlassian.net/browse/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-19_02_Spät` | **Branch**: `feature/ATT-8325`  
**Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-19

---

## 1. Problem Statement & Motivation
Under ASPICE 4.0 Finding `M1`, `ATT-8145` triaged unbacked `[Verified]` test specs in `docs/tests/tst_data_migration_export.md` and downgraded `25` legacy `TST-*` specs (and unbacked single-TST parent `REQ-*` headings) to `[Implemented]` (`docs/engineering/test_evidence/ATT-8145_unbacked_triage.csv`). `ATT-8325` (`3 SP`) closes this gap by establishing genuine executable JVM/Robolectric/SQLite/Compose test evidence (and KDoc `TST-*` traceability tags for already-tested behaviors) across all `25` specs without altering `app/src/main/` production behavior.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Origin**: Pre-`ATT-8143` sprints marked legacy `TST-*` and `REQ-*` specs `[Verified]` without enforcing `TST-*` tags in `app/src/test/` or passing JUnit XML correlation (`REQ-PRO-099`).
* **Independent Peer Review & Verification**:
  * Verified all `25` `TST-*` specs in `docs/tests/tst_data_migration_export.md` are currently `[Implemented]`.
  * **Zero Attestation Needed**: All `25/25` specs are deterministically verifiable via host JVM / Robolectric SQLite / Compose UI / MockK / XML DOM tests (`0` hardware/OS external-service attestations required).
  * **`SourceTextReadingAuditContractTest` Compliance**: New/updated tests MUST NOT read `app/src/main/` files (`REQ-PRO-049`); all verifications must exercise production classes, SQLite helpers, Composables, and `strings.xml` directly.
  * **Existing Coverage vs. Net-New Tests (`6` Existing + Tag, `19` Net-New Executable)**:
    * **Existing + Tag (`6`)**: `TST-DAT-001` (`KnownLocationsDatabaseManagerTest.kt`), `TST-DAT-021` (`TrackerServiceResumeTest.kt`), `TST-DAT-032` (`*RealSqliteMigrationTest.kt` + `DatabaseMigrationIndexTest.kt` + schema parity assertion), `TST-MIG-002` (`LegacyImportEngineDeduplicationTest.kt` + `ImportEngine` merge test), `TST-MIG-003` (`BackupWorkerTest.kt` + `BackupWorkerDropboxGatingTest.kt` + `schedule()` Robolectric assertion), `TST-MIG-019` (`LegacyImportEngineWorkoutNameTest.kt` + `2026-08-01_10-00-00.tcx` assertion).
    * **Net-New Executable (`19`)**: `TST-EXP-004` (`TCXFileWriter.kt` integer `<Cadence>`/`<Watts>`/`<RunCadence>`), `TST-MIG-001` (`BackupManager`/`MigrationEngine`/tracking lockdown), `TST-MIG-004` (`ImportBackupTabsScreen` Dropbox restore `AlertDialog`), `TST-MIG-005` (`BackupTabContentLayout`/`RestoreTabContent` labels & descriptions), `TST-MIG-006` & `TST-MIG-007` (`prefs_dropbox.xml`/ViewModel/Worker defaults `1` day & `true`), `TST-MIG-008` (`ImportBackupTabsScreen` 3-tab `HorizontalPager`), `TST-MIG-010..018` (`LegacyImportEngine` idempotency, pre-download skip, recursive pagination, `Semaphore(3)` backpressure, sparse TCX, wait status, positive Import UI, concurrency, summary), `TST-MIG-025` (`uploadToStrava` flag + `PreImportTuningBottomSheet` toggle), `TST-EXT-001` (`StravaHelper.requestAccessToken` `CustomTabsIntent`), `TST-EXT-002` (`StravaAuthViewModel`/`StravaOAuthCallbackActivity` loading/error states).

### Complete 25-TST Inventory & Multi-TST Parent REQ Guard
| TST ID | Category | Target Test File(s) | Linked REQ(s) & Current Status | Multi-TST Parent REQ Guard |
|---|---|---|---|---|
| `TST-EXP-004` | New Executable | `TCXFileWriterSchemaComplianceTest.kt` | `REQ-EXP-001` `[Implemented]` | **BLOCKED**: Sibling `TST-REG-003` `[Implemented]` (`tst_process_stability.md`) |
| `TST-MIG-001` | New Executable | `BackupMigrationCycleTest.kt` | `REQ-MIG-001`,`002`,`006` `[Impl]`; `004` `[Ver]` | `REQ-MIG-004` has sibling `TST-MIG-037` `[Verified]` |
| `TST-MIG-002` | Existing + Tag | `LegacyImportEngineDeduplicationTest.kt` + `ImportEngine` | `REQ-MIG-005` `[Implemented]` | Single-TST REQ (promotable after PASS) |
| `TST-MIG-003` | Existing + Tag | `BackupWorkerTest.kt` + `BackupWorkerDropboxGatingTest.kt` | `REQ-MIG-003` `[Implemented]` | Single-TST REQ (promotable after PASS) |
| `TST-MIG-004` | New Executable | `ImportBackupTabsUiContractTest.kt` | `REQ-MIG-007` `[Implemented]` | Single-TST REQ (promotable after PASS) |
| `TST-MIG-005` | New Executable | `ImportBackupTabsUiContractTest.kt` | `REQ-MIG-008` `[Implemented]` | Single-TST REQ (promotable after PASS) |
| `TST-MIG-006` | New Executable | `DropboxBackupDefaultsTest.kt` | `REQ-MIG-009` `[Implemented]` | Single-TST REQ (promotable after PASS) |
| `TST-MIG-007` | New Executable | `DropboxBackupDefaultsTest.kt` | `REQ-MIG-010` `[Implemented]` | Single-TST REQ (promotable after PASS) |
| `TST-MIG-008` | New Executable | `ImportBackupTabsUiContractTest.kt` | `REQ-MIG-011` `[Implemented]` | Single-TST REQ (promotable after PASS) |
| `TST-MIG-010` | New Executable | `LegacyImportEngineCloudRecoveryTest.kt` | `REQ-MIG-013` `[Implemented]` | Single-TST REQ (promotable after PASS) |
| `TST-MIG-011` | New Executable | `LegacyImportEngineCloudRecoveryTest.kt` | `REQ-MIG-015` `[Implemented]` | Single-TST REQ (promotable after PASS) |
| `TST-MIG-012` | New Executable | `LegacyImportEngineCloudRecoveryTest.kt` | `REQ-MIG-016` `[Implemented]` | **Shared**: Also linked by `TST-MIG-018` (promote REQ only when both pass) |
| `TST-MIG-013` | New Executable | `BackupRestoreInteractionQueueTest.kt` | `REQ-MIG-017` `[Implemented]` | **Shared**: Also linked by `TST-MIG-017` (promote REQ only when both pass) |
| `TST-MIG-014` | New Executable | `LegacyImportEngineSparseDataTest.kt` | `REQ-MIG-018` `[Implemented]` | Single-TST REQ (promotable after PASS) |
| `TST-MIG-015` | New Executable | `LegacyImportEngineCloudRecoveryTest.kt` | `REQ-MIG-019` `[Implemented]` | Single-TST REQ (promotable after PASS) |
| `TST-MIG-016` | New Executable | `ImportBackupTabsUiContractTest.kt` | `REQ-MIG-020` `[Implemented]` | Single-TST REQ (promotable after PASS) |
| `TST-MIG-017` | New Executable | `BackupRestoreInteractionQueueTest.kt` | `REQ-MIG-014`,`REQ-MIG-017` `[Implemented]` | **BLOCKED on `REQ-MIG-014`**: Sibling `TST-PRO-004` `[Implemented]` |
| `TST-MIG-018` | New Executable | `LegacyImportEngineCloudRecoveryTest.kt` | `REQ-MIG-016`,`REQ-MIG-021` `[Implemented]` | `REQ-MIG-016` shared with `TST-MIG-012`; `REQ-MIG-021` single-TST |
| `TST-MIG-019` | Existing + Tag | `LegacyImportEngineWorkoutNameTest.kt` | `REQ-MIG-022` `[Implemented]` | Single-TST REQ (promotable after PASS) |
| `TST-MIG-025` | New Executable | `TcxImportStravaToggleTest.kt` | `REQ-MIG-028` `[Implemented]` | Single-TST REQ (promotable after PASS) |
| `TST-DAT-001` | Existing + Tag | `KnownLocationsDatabaseManagerTest.kt` | `REQ-DAT-007` `[Verified]` | Siblings `TST-DAT-002`,`TST-DAT-009` `[Verified]` |
| `TST-EXT-001` | New Executable | `StravaAuthCustomTabsFlowTest.kt` | `REQ-EXT-004` `[Implemented]` | Single-TST REQ (promotable after PASS) |
| `TST-EXT-002` | New Executable | `StravaAuthCustomTabsFlowTest.kt` | `REQ-EXT-005` `[Implemented]` | Single-TST REQ (promotable after PASS) |
| `TST-DAT-021` | Existing + Tag | `TrackerServiceResumeTest.kt` | `REQ-DAT-026` `[Implemented]` | Single-TST REQ (promotable after PASS) |
| `TST-DAT-032` | Existing + Tag | `*RealSqliteMigrationTest.kt` + `SqliteSchemaParityContractTest.kt` | `REQ-DAT-036` `[Implemented]` | Single-TST REQ (promotable after PASS) |

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**: Add KDoc `TST-*` tags and missing assertions to the `6` already-covered test suites; implement `19` genuine executable unit/Robolectric/SQLite/Compose tests for the remaining specs; run `./gradlew testDebugUnitTest` + `verify_requirement_governance.py --check-verified-evidence`; promote eligible `TST-*` and guarded single/all-verified `REQ-*` headings in Stage 5.
* **Out-of-Scope Non-Goals**: Any `app/src/main/` production code edits (`0` edits required); promoting `REQ-EXP-001` or `REQ-MIG-014` while sibling `TST-REG-003` / `TST-PRO-004` remain `[Implemented]`; touching `tools/` user-owned files or `ANT-Android-SDKs`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* No existing requirements modified (`Net-new verification tests only`).
* **Spec Evolution Notes**:
  * `TST-MIG-013` description (`10` queued items) predates `REQ-MIG-017` (`Semaphore(3)`, `BackupRestoreViewModel.kt:95`); tests will verify the normative `REQ-MIG-017` bound (`<= 3`).
  * `REQ-MIG-008` / `TST-MIG-005` dashboard labels evolved under `REQ-MIG-036` (`backup_now`, provider-aware cloud upload button, descriptive Restore/Import cards); tests will verify current active UI strings without contradicting `RestoreCardContractTest`.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-EXP-001` (held `[Implemented]` by multi-TST guard), `REQ-MIG-014` (held `[Implemented]` by multi-TST guard), `REQ-MIG-001..003,005..011,013,015..022,028`, `REQ-EXT-004..005`, `REQ-DAT-026,036` (eligible for `[Verified]` promotion upon passing JUnit XML evidence), plus `REQ-DAT-007`, `REQ-MIG-004` (already `[Verified]`). `SYS.2`/`SYS.3` impact: **None** (`0` `app/src/main/` or external interface changes; existing `SYSREQ-DEX-001..005` links unchanged).
* **Affected Architecture Elements**: `app/src/test/` unit/Robolectric/Compose test suites only; `docs/tests/tst_data_migration_export.md`, `docs/requirements/req_data_migration_export_part1.md`, master indexes, and `tools/verify_requirement_governance.py` (`LEGACY_UNBACKED_VERIFIED_TSTS` unaffected since these 25 were already downgraded in `ATT-8145`).
* **Verification Impact & Test Surface**: Targeted unit test runs (`./gradlew testDebugUnitTest --tests ...`), full `./gradlew testDebugUnitTest`, `SourceTextReadingAuditContractTest`, and `python3 tools/verify_requirement_governance.py --check-verified-evidence`.
* **Risk Assessment & Invariants**: **LOW** (`0` production code changes; all tests deterministic on JVM/Robolectric without network or `src/main` file reads).

---

## 6. Architectural Strategy & High-Level Solution
* Tag existing suites (`KnownLocationsDatabaseManagerTest`, `TrackerServiceResumeTest`, `*RealSqliteMigrationTest`, `LegacyImportEngineDeduplicationTest`, `BackupWorkerTest`, `LegacyImportEngineWorkoutNameTest`) with canonical `TST-*` KDoc tags and close minor assertion gaps (`ImportEngine` merge dedup, `BackupWorker.schedule`, `onCreate`/`onUpgrade` schema parity).
* Group the `19` net-new executable tests cleanly by subsystem (`TCXFileWriterSchemaComplianceTest`, `BackupMigrationCycleTest`, `DropboxBackupDefaultsTest`, `ImportBackupTabsUiContractTest`, `LegacyImportEngineCloudRecoveryTest`, `BackupRestoreInteractionQueueTest`, `LegacyImportEngineSparseDataTest`, `TcxImportStravaToggleTest`, `StravaAuthCustomTabsFlowTest`) using Robolectric `ApplicationProvider`, in-memory/temp-dir SQLite, Compose `createComposeRule()`, and MockK.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: `0` `app/src/main/` edits; `0` `src/main` source-text reading in tests; multi-TST parent REQ guard strictly enforced (`REQ-EXP-001`, `REQ-MIG-014`); user-owned `tools/` files untouched; Human Decision Gate enforced.
* **Risk Rating**: **LOW** — Test-only remediation with zero runtime blast radius.
