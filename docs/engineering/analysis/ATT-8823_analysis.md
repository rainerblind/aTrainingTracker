# Stage 1 Analysis: ATT-8823 - EditDeviceDialog Private @Preview Visibility Audit

**Ticket**: [ATT-8823](https://rainerblind.atlassian.net/browse/ATT-8823)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-19_02_Spät` | **Branch**: `feature/ATT-8823`  
**Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-19  

---

## 1. Problem Statement & Motivation

Tier-2 CI close gate detected `DeadCodeEliminationContractTest.testComposePreviewsVisibilityAudit` failing (`4/5` pass, `1/5` fail) on four `internal fun` Compose `@Preview` functions in `EditDeviceDialog.kt` introduced by `ATT-8450` (commit `3376bb3f`). Contract `REQ-ARC-003.5` / `TST-ARC-003.5` requires all `@Preview` composables under `app/src/main/java` to have `private` visibility.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

Reproduced failure on `feature/ATT-8823` (HEAD `73ff0cdd`) via `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.DeadCodeEliminationContractTest"`:
- `testComposePreviewsVisibilityAudit` (`DeadCodeEliminationContractTest.kt:215-241`) scans all `.kt` files in `app/src/main/java` with regex `(@Preview[\s\S]*?)(?:(private|internal|public)\s+)?fun\s+([A-Za-z0-9_]+)` and fails if `visibility != "private"`.
- **Independent Peer Review & Verification**:
  - **Line-number discrepancy**: Jira description cites `EditDeviceDialog.kt` lines `1598-1649` (which currently hold `isPolarVeritySenseDevice` / `PolarOfflineDeviceSection`). On live `sprint/2026-11-19_02_Spät`, the 4 offending `@Preview` functions reside at **lines 1435–1501** (`internal fun` declarations at **1437, 1454, 1471, 1488**; in `3376bb3f` they were at lines 1406–1474):
    1. `RadarLightControlSectionOfflineMultiModeLightPreview` (`EditDeviceDialog.kt:1437`)
    2. `RadarLightControlSectionOfflineMultiModeDarkPreview` (`EditDeviceDialog.kt:1454`)
    3. `RadarLightControlSectionOfflineStvzoLightPreview` (`EditDeviceDialog.kt:1471`)
    4. `RadarLightControlSectionOfflineStvzoDarkPreview` (`EditDeviceDialog.kt:1488`)
  - **Codebase-wide sweep**: Scanning all `.kt` files in `app/src/main/java` confirms these **4** are the *only* non-`private` `@Preview` functions repo-wide. Other `@Preview`s in `EditDeviceDialog.kt` (`WheelPositionEquipmentLightPreview` at line 1771, `WheelPositionEquipmentDarkPreview` at line 1783) are already `private`.
  - **Callers & LoC**: `grep` across `app/` and `docs/` confirms **0 callers** of `RadarLightControlSectionOffline*Preview`. `EditDeviceDialog.kt` is `1,843` LoC (well below `ArchitectureConformanceContractTest.MAX_MONOLITH_FILE_LOC_BASELINE` ceiling `2,004`).

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Change `internal fun` to `private fun` on the 4 `RadarLightControlSectionOffline*Preview` functions in `EditDeviceDialog.kt`.
  * Verify `DeadCodeEliminationContractTest` (`5/5` pass) and `EditDeviceDialogRadarLightTest` pass.
* **Out-of-Scope Non-Goals**:
  * Splitting `EditDeviceDialog.kt` below `1,500` LoC, modifying preview UI/parameters, or modifying `REQ-ARC-003`/`TST-ARC-003` semantics.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Existing Requirement (Unmodified)**: `REQ-ARC-003` (`REQ-ARC-003.5` *Compose UI Preview Visibility Scoping*, `docs/requirements/req_process_stability_part1.md:2295-2296`) and `TST-ARC-003` (`TST-ARC-003.5`, `docs/tests/tst_process_stability_part1.md:1609-1610`), introduced under `ATT-4031`.
* **No Net-New ID / No Formulation Change**: `REQ-ARC-003.5` already mandates `private` visibility for all `@Preview` composables to prevent preview functions from leaking into internal/public module bytecode.

---

## 5. Change-Request Impact Analysis (SUP.10)

* **Affected Requirements**: `REQ-ARC-003` (`REQ-ARC-003.5`) — semantics unchanged (`[Verified]`); `0` net-new or modified `REQ-*`/`TST-*` IDs. `SYS.2`/`SYS.3` impact: **None** (no hardware sensors, BLE/ANT+ profiles, or external APIs altered).
* **Affected Architecture Elements**: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceDialog.kt` (visibility keyword change only on lines 1437, 1454, 1471, 1488; LoC remains `1,843 <= 2,004`).
* **Verification Impact & Test Surface**: `TST-ARC-003` (`TST-ARC-003.5`) via `DeadCodeEliminationContractTest`, plus `EditDeviceDialogRadarLightTest` and `ArchitectureConformanceContractTest`. Zero string resource edits (`TranslationParityTest` N/A).
* **Risk Assessment & Invariants**: **LOW Risk** (`1 SP`); zero runtime behavior, Compose layout, or caller impact.

---

## 6. Architectural Strategy & High-Level Solution

Replace `internal fun` with `private fun` on lines `1437`, `1454`, `1471`, and `1488` of `EditDeviceDialog.kt`, preserving all `@Preview`, `@Composable`, and body implementations intact.

---

## 7. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero runtime UI or `RadarLightControlSectionContent` behavior change.
  2. `EditDeviceDialog.kt` LoC (`1,843`) remains under `ArchitectureConformanceContractTest` baseline (`2,004`).
  3. Parent ticket Human Decision Gate remains strictly enforced (`Final Review (Human)`).
* **Risk Rating**: **LOW** — Mechanical visibility narrowing (`internal` -> `private`) on 4 uncalled `@Preview` functions.
