# Stage 3: Implementation Plan - ATT-6712: Health Connect HRV Opt-In Toggle, Pagination, Localization & In-Place Sync

**Ticket**: [ATT-6712](https://rainerblind.atlassian.net/browse/ATT-6712)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PER-031` (`REQ-PER-031.1`, `REQ-PER-031.2`, `REQ-PER-031.4`), `REQ-PER-033` (`REQ-PER-033.1`), `REQ-SET-103` (`REQ-SET-103.1`)  
**Test Mapping**: `TST-PER-039` (`TST-PER-039.1`, `TST-PER-039.2`, `TST-PER-039.3`), `TST-PER-041` (`TST-PER-041.5`), `TST-SET-103` (`TST-SET-103.1`), `TST-PER-039.5`  
**Branch**: `feature/ATT-6712` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
Sprint `2026-46.1` follow-up of `ATT-4981` identified three integration defects: `HealthConnectHrvImporter` is orphaned without callers or an opt-in toggle in `HealthConnectSettingsDialog`; `hasHrvReadPermissions()` requires both HRV and RHR permissions, blocking import when users grant only HRV; `readRecords` lacks `pageToken` pagination, truncating large multi-week datasets; deduplication skips existing dates unconditionally, locking out later full-night samples after mid-sleep syncs and storing sentinel `restingHrBpm = 0`; and `HrvReadinessTrendCard` displays unlocalized `"OVERNIGHT_WEARABLE"` without a source provenance badge.

---

## 2. Traceability & Requirements Mapping
* `REQ-PER-031.1` (HRV read permission optionality for RHR) $\rightarrow$ `TST-PER-039.1` (`HealthConnectManagerTest`)
* `REQ-PER-031.2` (`pageToken` pagination loop for multi-page HRV & RHR reads) $\rightarrow$ `TST-PER-039.2` (`HealthConnectHrvImporterTest`)
* `REQ-PER-031.4` (In-place nocturnal row update & `updateEntry()`) $\rightarrow$ `TST-PER-039.3` (`HrvReadinessDatabaseManagerTest`, `HealthConnectHrvImporterTest`)
* `REQ-SET-103.1` (Settings toggle, 60d backfill & 14d refresh triggers) $\rightarrow$ `TST-SET-103.1` (`HealthConnectSettingsDialogTest`, `PeriodsViewModelTest`)
* `REQ-PER-033.1` (9-locale posture string, provenance badge & posture bounds) $\rightarrow$ `TST-PER-041.5` (`HrvReadinessTrendCardTest`, `TranslationParityTest`)
* `REQ-PRO-001` (Tier 1 clean-room regression suite) $\rightarrow$ `TST-PER-039.5` (Parallel lock Gradle verification)

---

## 3. System Invariants & Preserved Behavior
1. **Cohort & Posture Segregation**: Waking baselines (`SUPINE`, `SEATED`, `STANDING`) and nocturnal baselines (`OVERNIGHT_WEARABLE`) remain strictly isolated. Manual check in `MorningHrvCheckScreen` remains restricted to waking postures only.
2. **Schema & Rollback Safety**: Zero changes to `HrvReadiness.db` SQLite schema (v1). Unpinned row updates modify existing columns in-place without altering table layout.
3. **Idempotence & Pin Invariant**: User-pinned entries (`isPrimaryForDay == true` from manual pin or custom notes) are never overwritten by automated syncs.

---

## 4. Proposed Architectural Changes

### UI Consistency (`design_guidelines.md` §3.1, §5)
* **Reference Component**: `HealthConnectSettingsDialog.kt`, `HrvReadinessTrendCard.kt`.
* **Reused Components**: `Switch`, `Surface`, `FilterChip`, `AppBottomSheetContent`.
* **Theme Tokens**: `MaterialTheme.colorScheme.secondaryContainer`, `onSecondaryContainer`, `TTColor.ReadinessOptimal`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Permission Relaxation & PageToken Pagination (`HealthConnectManager.kt`, `HealthConnectHrvImporter.kt`)
* Files: `HealthConnectManager.kt`, `HealthConnectHrvImporter.kt`.
* Action: Update `hasHrvReadPermissions()` to check `HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class) in granted`, treating `RestingHeartRateRecord` as optional. Implement `pageToken` pagination loops on `client.readRecords` for both HRV and RHR queries until `pageToken == null`.

### Step 2: In-Place Nocturnal Row Updates & Database CRUD (`HrvReadinessDatabaseManager.kt`, `HrvReadinessRepository.kt`, `HealthConnectHrvImporter.kt`)
* Files: `HrvReadinessDatabaseManager.kt`, `HrvReadinessRepository.kt`, `HealthConnectHrvImporter.kt`.
* Action: Add `updateEntry(entry: HrvReadinessEntry): Boolean` in `HrvReadinessDatabaseManager` and `HrvReadinessRepository`. In `HealthConnectHrvImporter`, update existing unpinned `OVERNIGHT_WEARABLE` row in-place if newer nocturnal samples arrived (`maxTs > existing.timestampMs` or `existing.restingHrBpm == 0 && restingHrBpm > 0`), preserving user-pinned records.

### Step 3: 9-Locale Posture Localization & Provenance Badge (`strings_hrv.xml`, `HrvReadinessTrendCard.kt`, `HrvReadinessTrendMath.kt`)
* Files: `app/src/main/res/values*/strings_hrv.xml`, `HrvReadinessTrendCard.kt`, `HrvReadinessTrendMath.kt`.
* Action: Add `morning_hrv_posture_overnight_wearable` across all 9 locales. Localize `OVERNIGHT_WEARABLE` in `HrvReadinessTrendCard.kt`. Add `notes` to `HrvTrendPoint` and render source provenance badge (`"Health Connect (Oura / Fitbit / ...)"`) from notes. Keep manual check restricted to waking postures.

### Step 4: Opt-In Settings Switch & 60-Day Backfill (`HealthConnectSettingsDialog.kt`, `strings_health_connect.xml`)
* Files: `HealthConnectSettingsDialog.kt`, `app/src/main/res/values*/strings_health_connect.xml`.
* Action: Add "Import Resting HRV from Health Connect" switch with `HRV_READ_PERMISSIONS` permission request. When toggled ON, trigger initial 60-day backfill (`importRecentHrv(context, 60)`). Persist state via `TrainingApplication.setHealthConnectHrvImportEnabled()`.

### Step 5: Background 14-Day Refresh Wiring (`PeriodsViewModel.kt`, `MorningHrvCheckViewModel.kt`)
* Files: `PeriodsViewModel.kt`, `MorningHrvCheckViewModel.kt`.
* Action: In `PeriodsViewModel.refreshHrvTrend()`, invoke `HealthConnectHrvImporter.importRecentHrv(context, 14)` when enabled prior to computing trend series. In `MorningHrvCheckViewModel.init`, launch 14-day refresh on `Dispatchers.IO` when enabled.

### Step 6: Targeted Unit & Regression Test Verification
* Files: `HealthConnectHrvImporterTest.kt`, `HrvReadinessDatabaseManagerTest.kt`, `HrvReadinessRepositoryTest.kt`, `HealthConnectSettingsDialogTest.kt`, `TranslationParityTest.kt`.
* Command: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.cloud.healthconnect.*" --tests "com.atrainingtracker.trainingtracker.database.hrv.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.healthconnect.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.localization.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: 100% pass on targeted unit tests, translation parity across 9 locales, and clean-room parallel sprint lock check.
* **Rollback**: Isolated branch `feature/ATT-6712` ensures zero impact on `sprint/2026-46.2` until Stage 5 squash merge.
