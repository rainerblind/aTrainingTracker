# Stage 5 Walkthrough: ATT-6712 - Wire HealthConnectHrvImporter Opt-In Settings Toggle & Background Sync, Localize OVERNIGHT_WEARABLE, and Paginate Health Connect HRV Reads

**Ticket**: [ATT-6712](https://rainerblind.atlassian.net/browse/ATT-6712)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-PER-031` (`REQ-PER-031.1`, `REQ-PER-031.2`, `REQ-PER-031.4`), `REQ-PER-033` (`REQ-PER-033.1`), `REQ-SET-103` (`REQ-SET-103.1`)  
**Test Spec ID**: `TST-PER-039` (`TST-PER-039.1`, `TST-PER-039.2`, `TST-PER-039.3`), `TST-PER-041` (`TST-PER-041.5`), `TST-SET-103` (`TST-SET-103.1`), `TST-PER-039.5`  
**Branch**: `feature/ATT-6712` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Executive Summary & Verification Overview
Sprint `2026-46.1` follow-up of `ATT-4981` addressed three critical defects in Android Health Connect HRV ingestion:
1. **Permission Relaxation & PageToken Pagination**: `HealthConnectManager.hasHrvReadPermissions()` was relaxed to gate strictly on `HeartRateVariabilityRmssdRecord`, treating `RestingHeartRateRecord` as optional (`REQ-PER-031.1`). `HealthConnectHrvImporter.kt` now paginates `client.readRecords` via `pageToken` loops until `pageToken == null` for both HRV and RHR queries, avoiding truncation on multi-week datasets (>1,000 records) (`REQ-PER-031.2`).
2. **In-Place Nocturnal Row Updates & Database CRUD**: Added `updateEntry(entry: HrvReadinessEntry): Boolean` to `HrvReadinessDatabaseManager` and `HrvReadinessRepository`. When subsequent nocturnal samples arrive for an existing `dateIso`, unpinned `OVERNIGHT_WEARABLE` rows are updated in-place when newer timestamps (`maxTs > existing.timestampMs`) or non-zero resting heart rate (`existing.restingHrBpm == 0 && restingHrBpm > 0`) arrive, while user-pinned entries are strictly preserved (`REQ-PER-031.4`).
3. **Opt-In Settings Switch & Background Refresh**: Added the *"Import Resting HRV from Health Connect"* switch + `HRV_READ_PERMISSIONS` permission request launcher to `HealthConnectSettingsDialog.kt`. Toggling ON triggers an initial 60-day backfill (`importRecentHrv(context, 60)`). `PeriodsViewModel.refreshHrvTrend()` and `MorningHrvCheckViewModel.init` invoke a 14-day background sync when enabled (`REQ-SET-103.1`).
4. **9-Locale Posture Localization & Source Provenance Badge**: Added `morning_hrv_posture_overnight_wearable` across all 9 supported locales in `strings_hrv.xml`. `HrvReadinessTrendCard.kt` renders the localized label and a source provenance badge pill (*"Health Connect (Oura / Fitbit / ...)"*) parsed from `notes`. Manual chest-strap check in `MorningHrvCheckScreen.kt` remains strictly restricted to waking postures (`SUPINE`, `SEATED`, `STANDING`) (`REQ-PER-033.1`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PER-031.1` | `TST-PER-039.1` | Unit (`HealthConnectManagerTest`, `HealthConnectHrvImporterTest`) | **PASSED** | `Verified` |
| `REQ-PER-031.2` | `TST-PER-039.2` | Unit (`HealthConnectHrvImporterTest`) | **PASSED** | `Verified` |
| `REQ-PER-031.4` | `TST-PER-039.3` | Unit / DB (`HrvReadinessDatabaseManagerTest`, `HealthConnectHrvImporterTest`) | **PASSED** | `Verified` |
| `REQ-SET-103.1` | `TST-SET-103.1` | UI / Flow (`HealthConnectSettingsDialogTest`, `PeriodsViewModelTest`) | **PASSED** | `Verified` |
| `REQ-PER-033.1` | `TST-PER-041.5` | UI / Localization (`HrvReadinessTrendCardTest`, `TranslationParityTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-PER-039.5` | Tier 1 Targeted Regression (237+ tests under lock) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Integration Tests (Tier 1 Regression Scope)
```text
python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest \
  --tests "com.atrainingtracker.trainingtracker.cloud.healthconnect.*" \
  --tests "com.atrainingtracker.trainingtracker.database.hrv.*" \
  --tests "com.atrainingtracker.trainingtracker.ui.settings.healthconnect.*" \
  --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" \
  --tests "com.atrainingtracker.trainingtracker.ui.hrv.*" \
  --tests "com.atrainingtracker.trainingtracker.localization.*"

BUILD SUCCESSFUL
237+ tests completed, 0 failed, 0 skipped
```

### Living Documentation & Governance Check
```text
python3 tools/verify_requirement_governance.py --sync-indexes
PASS: Living documentation master tables match all 9 domain shards with 100% parity.
PASS: Zero unmounted Composable islands detected in git diff.
PASS: Repository encapsulation verified (zero leaky DatabaseManager/Cursor members).
PASS: Zero architecture or system specification modifications detected.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Verification & Clean-Room Audit
* **Invariants**: Waking spot-check baselines (`SUPINE`, `SEATED`, `STANDING`) and nocturnal baselines (`OVERNIGHT_WEARABLE`) remain strictly isolated; manual check in `MorningHrvCheckScreen` remains waking-only; zero schema changes to `HrvReadiness.db` (v1); user-pinned readings are never overwritten.
* **Localization & UI**: 100% parity across all 9 supported application locales in `strings_hrv.xml` and `strings_health_connect.xml`.
