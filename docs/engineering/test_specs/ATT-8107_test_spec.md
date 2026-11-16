# Stage 2: Requirement & Test Specification - ATT-8107: Remove Superfluous Top Divider in SensorSourceDialog

**Ticket**: [ATT-8107](https://rainerblind.atlassian.net/browse/ATT-8107)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Schicht 2026-11-16_02_Spät` | **Branch**: `feature/ATT-8107`  
**Requirement Mapping**: `REQ-UI-2781` (*Conditional Section Dividers in SensorSourceDialog*)  
**Test Spec ID**: `TST-UI-2781`  
**Author**: AI Agent 1 (Implementer, Lane 1 Flash) | **Date**: 2026-11-16  

---

## 1. Requirement Specification (REQ-UI-2781)

### 1.1 Problem Statement & Rationale
When opening `SensorSourceDialog` in the tracking cockpit for a sensor category with no active connected devices (`sourceDevice == null` and `activeBackups.isEmpty()`), an orphaned `HorizontalDivider` is unconditionally rendered at the top of the dialog body directly beneath the title and above the "Nicht verbunden" section header. Furthermore, when `ALTITUDE` calibration is active without connected devices, a trailing divider and leading divider render consecutively. Dividers MUST strictly separate adjacent rendered sections without leading, trailing, or stacked dividers.

### 1.2 Attributes
- **Priority**: Medium
- **Type**: Functional
- **Verification Method**: Unit
- **System Requirement(s)**: `SYSREQ-TRK-001`
- **Implementation**: `SensorSourceDialog.kt`

### 1.3 Functional & Architectural Requirements
1. **Preceding-Section Divider Guarding (`REQ-UI-2781.1`)**: In `SensorSourceDialog.kt`, section rendering SHALL maintain a sequential preceding-section guard (`hasPrecedingSection`). A `HorizontalDivider` SHALL ONLY be rendered prior to a section (Source Device, Active Backups, Not Connected, or Internal Fallback) if a preceding section was actually rendered.
2. **Trailing and Stacked Divider Elimination (`REQ-UI-2781.2`)**: The Altitude Calibration section SHALL NOT emit an unconditional trailing `HorizontalDivider`. Under no combination of active, backup, disconnected, or calibration elements SHALL two `HorizontalDivider` composables render consecutively or as a dangling trailing element.

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* `SensorSourceDialog` opened with `sourceDevice == null` and `activeBackups.isEmpty()`,
  * *When* the dialog content renders,
  * *Then* zero `HorizontalDivider` composables SHALL precede the "Nicht verbunden" (`R.string.source_not_connected`) header.
* *Given* `SensorSourceDialog` opened for `SensorType.ALTITUDE` with calibration active and `sourceDevice == null`,
  * *When* the calibration card and `notConnected` devices render,
  * *Then* exactly one `HorizontalDivider` SHALL separate the calibration card from `notConnected`, with zero stacked dividers.
* *Given* `SensorSourceDialog` opened with an active `sourceDevice` and `activeBackups.isNotEmpty()`,
  * *When* the dialog content renders,
  * *Then* exactly one `HorizontalDivider` SHALL separate `sourceDevice` from `activeBackups`.

### 1.5 System Invariants
Multi-tier telemetry hierarchy, semantic header color tokens (`REQ-UI-051`), canonical priority sorting (`REQ-UI-268.5`), and dialog dismiss actions MUST NOT be altered.

---

## 2. Test Specification (TST-UI-2781)

### Test Case 1: Disconnected Sensor Top-Divider Absence & Permutations (`TST-UI-2781.1`)
* **Scope**: Contract & Unit Test (`SensorSourceDialogContractTest.kt`).
* **Assertions**: For disconnected sensor state (`sourceDevice == null`, `activeBackups.isEmpty()`), no divider precedes `notConnected`. For connected state (`sourceDevice != null`), exactly one divider precedes `activeBackups` or `notConnected`.

### Test Case 2: Altitude Calibration & Stacked Divider Elimination (`TST-UI-2781.2`)
* **Scope**: Contract & Unit Test (`SensorSourceDialogContractTest.kt`).
* **Assertions**: When altitude calibration is present, exactly one divider separates it from subsequent content, and zero trailing dividers exist if no subsequent content is present.

### Test Case 3: Tier-1 Targeted Regression & Governance Verification (`TST-UI-2781.3`)
* **Scope**: Regression & Architecture Test.
* **Command**: `./gradlew testDebugUnitTest --tests "*SensorSourceDialog*" --tests "*SensorStatus*"` + `python3 tools/verify_requirement_governance.py`.
* **Assertions**: 100% test pass rate; zero requirement governance violations.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-2781.1` | Preceding-section divider guarding (no leading divider) | `TST-UI-2781.1` | Contract / Unit Test |
| `REQ-UI-2781.2` | Trailing and stacked divider elimination | `TST-UI-2781.2` | Contract / Unit Test |
| `REQ-UI-049`, `REQ-UI-051` | Living doc & regression preservation | `TST-UI-2781.3` | Regression & Governance |
