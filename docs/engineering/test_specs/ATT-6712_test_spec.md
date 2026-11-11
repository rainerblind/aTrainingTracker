# Stage 2: Requirement & Test Specification - ATT-6712: Health Connect HRV Opt-In Toggle, Pagination, Localization & In-Place Sync

**Ticket**: [ATT-6712](https://rainerblind.atlassian.net/browse/ATT-6712)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PER-031` (`REQ-PER-031.1`, `REQ-PER-031.2`, `REQ-PER-031.4`), `REQ-PER-033` (`REQ-PER-033.1`), `REQ-SET-103` (`REQ-SET-103.1`)  
**Test Spec ID**: `TST-PER-039` (`TST-PER-039.1`, `TST-PER-039.2`, `TST-PER-039.3`), `TST-PER-041` (`TST-PER-041.5`), `TST-SET-103` (`TST-SET-103.1`)  
**Branch**: `feature/ATT-6712` | **Author**: AI Agent 1 (Specifier, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-PER-031, REQ-PER-033, REQ-SET-103)

### 1.1 Problem Statement & Rationale
`ATT-4981` established backend ingestion and log-normal math for nocturnal Health Connect HRV records, but left critical integration gaps: the backend is orphaned without an opt-in toggle or permission flow in `HealthConnectSettingsDialog`; `hasHrvReadPermissions()` requires both HRV and RHR permissions, blocking import when users grant only HRV; `readRecords` lacks `pageToken` pagination, truncating large multi-week datasets; deduplication skips existing dates unconditionally, locking out later full-night samples after mid-sleep syncs and storing sentinel `restingHrBpm = 0`; and `HrvReadinessTrendCard` displays unlocalized `"OVERNIGHT_WEARABLE"` without a source provenance badge.

### 1.2 Functional & Architectural Requirements
1. **Permission Relaxation & User Opt-In Gating (`REQ-PER-031.1`, `REQ-SET-103.1`)**:
   - `HealthConnectSettingsDialog` SHALL provide an *"Import Resting HRV from Health Connect"* switch wired to `TrainingApplication.isHealthConnectHrvImportEnabled()` and request `HRV_READ_PERMISSIONS` on enable.
   - `HealthConnectManager.hasHrvReadPermissions()` SHALL gate strictly on `HeartRateVariabilityRmssdRecord`, treating `RestingHeartRateRecord` as optional.
2. **PageToken Pagination & Multi-Epoch Aggregation (`REQ-PER-031.2`)**:
   - `HealthConnectHrvImporter` SHALL paginate `readRecords` using `pageToken` loops until `pageToken == null` for both HRV and RHR queries.
   - Multi-epoch nocturnal rMSSD SHALL be log-normally aggregated into geometric mean `rmssd_ms` and $\text{ln\_rmssd}$.
3. **Idempotent Ingestion & In-Place Nocturnal Updates (`REQ-PER-031.4`)**:
   - When importing for `dateIso` where an unpinned `OVERNIGHT_WEARABLE` row already exists, `HealthConnectHrvImporter` SHALL update the entry in-place via `updateEntry()` if newer nocturnal samples arrived (`maxTs > existing.timestampMs` or `existing.restingHrBpm == 0 && rhr > 0`). User-pinned entries SHALL NOT be overwritten.
4. **Posture Localization & Provenance Badge (`REQ-PER-033.1`)**:
   - `HrvPosture.OVERNIGHT_WEARABLE` SHALL be localized across all 9 supported locales in `strings_hrv.xml` (`morning_hrv_posture_overnight_wearable`).
   - `HrvReadinessTrendCard` SHALL render this localized label and a source provenance badge parsed from `notes` (e.g. *"Health Connect (Oura / Fitbit / ...)"*).
   - Manual posture selection in `MorningHrvCheckScreen` SHALL remain strictly restricted to waking postures (`SUPINE`, `SEATED`, `STANDING`).
5. **Background Refresh Lifecycle (`REQ-SET-103.1`)**:
   - Enabling the toggle in `HealthConnectSettingsDialog` SHALL trigger an initial 60-day backfill (`importRecentHrv(context, 60)`).
   - `PeriodsViewModel.refreshHrvTrend()` and `MorningHrvCheckViewModel.init` SHALL trigger a 14-day refresh when enabled.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (Opt-in & Permission Relaxation)**: *Given* an athlete in `HealthConnectSettingsDialog`, *When* toggling "Import Resting HRV", *Then* `HRV_READ_PERMISSIONS` is requested, and granting HRV without RHR permission allows successful import.
* **AC 2 (PageToken Pagination & In-Place Updates)**: *Given* multi-page records (>1,000) or an earlier mid-sleep sync, *When* imported, *Then* all pages are fetched via `pageToken` and unpinned rows for the same `dateIso` update in-place with latest nocturnal samples and non-zero RHR.
* **AC 3 (Localization, Provenance & Posture Boundary)**: *Given* `OVERNIGHT_WEARABLE` records, *When* displayed in `HrvReadinessTrendCard`, *Then* the label is localized in all 9 languages with a provenance badge, while `MorningHrvCheckScreen` retains waking postures only.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-PER-031` (`REQ-PER-031.1`, `031.2`, `031.4`), `REQ-PER-033` (`REQ-PER-033.1`), and `REQ-SET-103` (`REQ-SET-103.1`), targeting `HealthConnectHrvImporter.kt`, `HealthConnectManager.kt`, `HealthConnectSettingsDialog.kt`, `HrvReadinessTrendCard.kt`, `HrvReadinessDatabaseManager.kt`, and `strings_hrv.xml`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-4981` (commit `04c05e52`), refined in Sprint `2026-46.2` (`ATT-6712`).
* **Root Reason for Existing Formulation**: Ingestion math was delivered first in ATT-4981 while UI settings toggles were deferred; RHR was coupled to HRV read permissions out of convenience; pagination was omitted for initial small test fixtures.
* **Preservation of Core Invariants**: Waking spot-check baselines (`SUPINE`, `SEATED`, `STANDING`) and nocturnal baselines (`OVERNIGHT_WEARABLE`) remain strictly isolated; manual check remains restricted to waking postures; zero SQLite schema migrations to `HrvReadiness.db` (v1); 100% clean-room test pass rate.

---

## 3. Test Specification (TST-PER-039, TST-PER-041, TST-SET-103)

### Test Case 1: Permission Relaxation & PageToken Pagination (`TST-PER-039.1`, `TST-PER-039.2`)
* **Scope**: `HealthConnectHrvImporterTest.kt`. Verify that granting `READ_HEART_RATE_VARIABILITY` without `READ_RESTING_HEART_RATE` allows import to succeed, and verify `pageToken` pagination loop consumes multi-page records (>1,000).

### Test Case 2: In-Place Nocturnal Updates & Database CRUD (`TST-PER-039.3`)
* **Scope**: `HealthConnectHrvImporterTest.kt` / `HrvReadinessRepositoryTest.kt`. Verify `updateEntry()` updates unpinned `OVERNIGHT_WEARABLE` entries in-place when newer timestamps or non-zero RHR arrive, while user-pinned entries are never overwritten.

### Test Case 3: Settings Opt-In Toggle & Refresh Triggers (`TST-SET-103.1`)
* **Scope**: `HealthConnectSettingsDialogTest.kt` / `PeriodsViewModelTest.kt` / `MorningHrvCheckViewModelTest.kt`. Verify toggle switch state, permission request launcher, 60-day initial backfill trigger on enable, and 14-day refresh on ViewModel load.

### Test Case 4: Localization Parity, Provenance Badge & Posture Boundaries (`TST-PER-041.5`)
* **Scope**: `HrvReadinessTrendCardTest.kt` / `TranslationParityTest.kt`. Verify `morning_hrv_posture_overnight_wearable` across all 9 locales, source provenance badge parsing from `notes`, and posture selection boundary in `MorningHrvCheckScreen`.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-PER-039.5`)
* **Scope**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.cloud.healthconnect.*" --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.healthconnect.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.localization.*"`.

---

## 4. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-PER-039.1]` | Unit / Auth | RHR permission optionality & HRV gating | `REQ-PER-031.1` | Specified |
| `[TST-PER-039.2]` | Unit | `pageToken` pagination loop (>1,000 records) | `REQ-PER-031.2` | Specified |
| `[TST-PER-039.3]` | Unit / DB | In-place nocturnal updates & `updateEntry()` | `REQ-PER-031.4` | Specified |
| `[TST-SET-103.1]` | UI / Flow | Settings switch, 60d backfill & 14d refresh | `REQ-SET-103.1` | Specified |
| `[TST-PER-041.5]` | UI / Loc | 9-locale parity, provenance badge & posture bounds | `REQ-PER-033.1` | Specified |
| `[TST-PER-039.5]` | Regression | Tier 1 clean-room regression across target packages | `REQ-PRO-001` | Specified |
