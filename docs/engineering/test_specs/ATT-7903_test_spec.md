# Stage 2: Requirement & Test Specification - ATT-7903: Leaf Sensor Formatters Kotlin Migration

**Ticket**: [ATT-7903](https://rainerblind.atlassian.net/browse/ATT-7903)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0`  
**Requirement Mapping**: `REQ-ARC-007` (`REQ-ARC-007.1`..`REQ-ARC-007.4`)  
**Test Spec ID**: `TST-ARC-007` (`TST-ARC-007.1`..`TST-ARC-007.5`)  
**Branch**: `feature/ATT-7903` (Lane 3)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-15  

---

## 1. Requirement Specification (`REQ-ARC-007`)

### 1.1 Problem Statement & Rationale
15 pure leaf formatters in `com.atrainingtracker.banalservice.sensor.formater` (775 LOC) implement `MyFormatter<T>`. Migrating them to idiomatic Kotlin eliminates platform type (`T!`) nullability hazards and allocation overhead while preserving 100% string parity, null/NaN/Infinity safety, and Java caller compatibility (`format_with_units`, `format_3`).

### 1.2 Attributes
- **Priority**: Medium
- **Type**: Non-Functional
- **Verification Method**: Unit / Review

### 1.3 Functional & Architectural Requirements
1. **1:1 Kotlin Migration**: Migrate all 15 Java files in `sensor/formater/` (`MyFormatter` SAM interface + 14 concrete formatters) to `.kt`; delete legacy `.java` files (`REQ-ARC-007.1`).
2. **Defensive Edge-Case Formatting**: Null, NaN, Infinity, negative, and out-of-range sensor values return deterministic fallback tokens (`"--"`, `"--.--"`, `"0"`) without throwing exceptions (`REQ-ARC-007.2`).
3. **Java Interop Parity**: Preserve exact signatures for `format(value: T?): String`, `format_with_units(value: Number?): String`, and `format_3(value: Number?): String` with `@JvmOverloads`/`@JvmStatic` as needed for Java callers (`SensorType`, UI) (`REQ-ARC-007.3`).
4. **Contract & Coverage Bounds**: Relax total repository Java files from 138 to 123 (verifying `<= 149` bound in `JavaCoreMigrationStrategyContractTest.kt`); maintain subsystem Kover coverage $\ge 70\%$ Line / $\ge 50\%$ Branch (`REQ-ARC-007.4`).

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1**: *Given* 15 formatter classes, *When* converted to Kotlin, *Then* 0 `.java` files remain in `sensor/formater/` and all 15 `.kt` classes compile with non-null `String` returns.
* **Criterion 2**: *Given* null, NaN, or Infinite values, *When* formatted, *Then* deterministic fallback strings (`"--"`, `"--.--"`) are returned and zero exceptions occur.
* **Criterion 3**: *Given* `JavaCoreMigrationStrategyContractTest`, *When* executed, *Then* `<= 149` Java file bound passes.

### 1.5 System Invariants
Stateless pure functional execution; identical rounding/locale rules; zero UI bytecode change; zero breaking changes for `SensorType.java`.

---

## 2. Test Specification (`TST-ARC-007`)

### Test Case 1: Formatter String & Behavioral Parity Suite (`TST-ARC-007.1`)
* **Scope**: Unit Test (`app/src/test/java/com/atrainingtracker/banalservice/sensor/formater/`)
* **Goal**: Validate 100% output parity across all 14 formatters against legacy snapshots.

### Test Case 2: Null, NaN, Infinity & Boundary Robustness Suite (`TST-ARC-007.2`)
* **Scope**: Unit Test
* **Goal**: Assert null, NaN, $\pm\infty$, and zero inputs yield fallback tokens without exception.

### Test Case 3: Java Caller Interoperability & Secondary Overloads (`TST-ARC-007.3`)
* **Scope**: Integration & Compilation Check (`SensorType.java`, UI presenters)
* **Goal**: Ensure `format_with_units` and `format_3` resolve without modification.

### Test Case 4: Contract & Governance Suite (`TST-ARC-007.4`, `TST-ARC-007.5`)
* **Scope**: Contract Tests & Verification Tools
* **Goal**: `JavaCoreMigrationStrategyContractTest` (`<= 149`), Kover $\ge 70\%$/$50\%$, and `verify_requirement_governance.py` pass cleanly.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Method | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-ARC-007.1` | Unit | `AltitudeFormatter`, `SpeedFormatter`, `PaceFormatter`, etc. | `REQ-ARC-007.1` | Specified |
| `TST-ARC-007.2` | Unit | All 14 concrete formatters (`format`, fallback checks) | `REQ-ARC-007.2` | Specified |
| `TST-ARC-007.3` | Interop | `format_with_units`, `format_3` in `SensorType.java` | `REQ-ARC-007.3` | Specified |
| `TST-ARC-007.4` | Contract | `JavaCoreMigrationStrategyContractTest.kt` (`<= 149`) | `REQ-ARC-007.4` | Specified |
| `TST-ARC-007.5` | Coverage | Kover verification on `sensor.formater` ($\ge 70\%$/$50\%$) | `REQ-ARC-007.4` | Specified |
