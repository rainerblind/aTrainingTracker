# Stage 2: Requirement & Test Specification - ATT-8823: EditDeviceDialog Private @Preview Audit

**Ticket**: [ATT-8823](https://rainerblind.atlassian.net/browse/ATT-8823)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-19_02_Spät` | **Branch**: `feature/ATT-8823`  
**Requirement Mapping**: `REQ-ARC-003` (`REQ-ARC-003.5` *Compose UI Preview Visibility Scoping*, `[Verified]`)  
**Test Spec ID**: `TST-ARC-003` (`TST-ARC-003.5` *Top-Level Compose Preview Private Visibility Audit*, `[Verified]`)  
**Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-19  

---

## 1. Requirement Specification (`REQ-ARC-003.5` — Existing, Unmodified)

### 1.1 Problem Statement & Rationale
No net-new `REQ-*` or `TST-*` identifier is required (`SYS.2`/`SYS.3` impact: None). Existing `REQ-ARC-003.5` (`docs/requirements/req_process_stability_part1.md:2295-2296`) mandates that all Compose `@Preview` functions under `app/src/main/java` have `private` visibility. `ATT-8823` remediates the 4 `internal fun RadarLightControlSectionOffline*Preview` declarations in `EditDeviceDialog.kt` (`1437`, `1454`, `1471`, `1488`).

### 1.2 Attributes (`REQ-ARC-003` / `REQ-ARC-003.5`)
- **Priority**: High | **Type**: Non-Functional | **Verification Method**: Review (automated via `DeadCodeEliminationContractTest`)
- **System Requirement(s)**: Derived (SW-only) — internal Compose visibility hygiene (`REQ-PRO-076`).

### 1.3 Functional & Architectural Requirements
1. In `EditDeviceDialog.kt`, `RadarLightControlSectionOfflineMultiModeLightPreview` (`1437`), `RadarLightControlSectionOfflineMultiModeDarkPreview` (`1454`), `RadarLightControlSectionOfflineStvzoLightPreview` (`1471`), and `RadarLightControlSectionOfflineStvzoDarkPreview` (`1488`) **SHALL** be declared `private fun` instead of `internal fun`.
2. `EditDeviceDialog.kt` **MUST** remain at `1,843 <= 2,004` LoC (`0` net LoC delta) per `ArchitectureConformanceContractTest.MAX_MONOLITH_FILE_LOC_BASELINE`.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Preview Private Visibility)**:
  * *Given* `EditDeviceDialog.kt` with the 4 `RadarLightControlSectionOffline*Preview` functions changed from `internal fun` to `private fun` at lines `1437`, `1454`, `1471`, `1488`,
  * *When* `DeadCodeEliminationContractTest` (`testComposePreviewsVisibilityAudit` + 4 sibling contract tests) executes,
  * *Then* all `5/5` tests in `DeadCodeEliminationContractTest` pass with `0` violations across `app/src/main/java`.
* **Criterion 2 (Architecture & Source-Text Audit Compliance + Zero Runtime Change)**:
  * *Given* the `0` net LoC delta (`1,843 <= 2,004`) and zero `app/src/test/` source-text reading additions,
  * *When* `ArchitectureConformanceContractTest`, `SourceTextReadingAuditContractTest`, and `EditDeviceDialogRadarLightTest` execute,
  * *Then* all suites pass `100%` with zero runtime/UI behavioral change to `RadarLightControlSectionContent`.

### 1.5 System Invariants
1. Zero runtime UI or `RadarLightControlSectionContent` layout/behavior modification.
2. Zero new strings or localization impact (`TranslationParityTest` N/A — `values*/strings*.xml` untouched).

---

## 2. Test Specification (`TST-ARC-003.5`)

### Test Case 1: Compose Preview Private Visibility Contract (`TST-ARC-003.5`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/DeadCodeEliminationContractTest.kt`
* **Action**: Run `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.build.DeadCodeEliminationContractTest"`
* **Expected Result**: `5/5` tests pass (`testComposePreviewsVisibilityAudit` reports empty `nonPrivatePreviews`).

### Test Case 2: Architecture LoC Ceiling & Source-Text Audit (`TST-PRO-050`, `TST-PRO-129`, `REQ-PRO-133`)
* **Target Files**: `ArchitectureConformanceContractTest.kt`, `SourceTextReadingAuditContractTest.kt`
* **Action**: Run `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"`
* **Expected Result**: `100%` pass; `EditDeviceDialog.kt` LoC verified `1,843 <= 2,004`.

### Test Case 3: EditDeviceDialog Radar Light Regression Suite
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceDialogRadarLightTest.kt`
* **Action**: Run `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceDialogRadarLightTest"`
* **Expected Result**: `100%` pass, confirming zero runtime/UI behavioral regression.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Suite Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-ARC-003.5` | Build Contract | `DeadCodeEliminationContractTest` (`5/5`) | `REQ-ARC-003.5` | Specified |
| `TST-PRO-129.2` / `REQ-PRO-133` | Build Contract | `ArchitectureConformanceContractTest`, `SourceTextReadingAuditContractTest` | `REQ-PRO-129.2`, `REQ-PRO-133` | Specified |
| Regression | Compose UI Unit | `EditDeviceDialogRadarLightTest` | `REQ-CON-047`, `REQ-ARC-003.5` | Specified |
