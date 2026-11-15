# Stage 3: Implementation Plan - ATT-7903: Leaf Sensor Formatters Kotlin Migration

**Ticket**: [ATT-7903](https://rainerblind.atlassian.net/browse/ATT-7903)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.7`  
**Requirement Mapping**: `REQ-ARC-007` (*Leaf Sensor Formatters Kotlin Migration & Interoperability Parity*)  
**Test Mapping**: `TST-ARC-007`  
**Risk Classification**: `STANDARD` (non-UI pure leaf formatter migration)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Problem Description & Background
Under ADR-001 (`REQ-ARC-005`, `ATT-7566`) and the PO decision from retro `ATT-7749`, all 15 pure leaf formatter classes in `com.atrainingtracker.banalservice.sensor.formater` (775 LOC) will migrate from Java to idiomatic Kotlin (`.kt`). This eliminates platform type (`T!`) nullability hazards and allocation overhead while ensuring 100% output parity, defensive handling of null/NaN/Infinity, and seamless Java caller interoperability.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-ARC-007` (`REQ-ARC-007.1`–`REQ-ARC-007.4`)
* **Test Mapping**: `TST-ARC-007` (`TST-ARC-007.1`–`TST-ARC-007.5`)

---

## 3. System Invariants & Preserved Behavior
1. **100% Output & Locale Parity**: Formatter outputs for valid inputs match legacy snapshots bitwise, including rounding, unit suffixes, and time/pace string conventions (`mm:ss`, `h:mm:ss`).
2. **Defensive Edge-Case Guard**: Null, NaN, and Infinite inputs consistently return authoritative fallback representations (`"--"`, `"--.--"`, `"0"`), with zero uncaught exceptions.
3. **Java Interop Parity**: Auxiliary methods (`format_with_units`, `format_3`) preserve exact signatures for Java callers (`SensorType`, UI presenters). SAM conversion guaranteed via `fun interface MyFormatter<T>`.
4. **Contract Test Invariance**: Total Java files drop from 138 to 123, safely satisfying `JavaCoreMigrationStrategyContractTest` (`<= 149`).
5. **Submodule Invariance**: `ANT-Android-SDKs` submodule gitlink (`160000`) untouched.

---

## 4. Proposed Architectural Changes
* **Subsystem `com.atrainingtracker.banalservice.sensor.formater`**:
  * `MyFormatter.kt`: `fun interface MyFormatter<T> { fun format(value: T?): String }`.
  * Simple formatters: `DefaultStringFormatter.kt`, `DefaultNumberFormatter.kt`, `IntegerFormatter.kt`, `SignedIntegerFormatter.kt`, `CadenceFormatter.kt`.
  * Domain formatters: `DfaA1Formatter.kt`, `IntensityFactorFormatter.kt`, `PowerToWeightFormatter.kt`, `BatteryRemainingTimeFormatter.kt`.
  * Metric & unit formatters: `TimeFormatter.kt`, `PaceFormatter.kt`, `SpeedFormatter.kt`, `AltitudeFormatter.kt`, `DistanceFormatter.kt`.
  * Delete all 15 legacy `.java` files upon `.kt` replacement.
* **SWE.2 & Architecture Governance**:
  * Verify `docs/architecture.md` and keep all architectural invariants synchronized.
* **UI Consistency**: No UI changes (pure leaf formatting algorithms).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Base SAM Interface & Simple Formatters Migration
* Files: `MyFormatter.kt`, `DefaultStringFormatter.kt`, `DefaultNumberFormatter.kt`, `IntegerFormatter.kt`, `SignedIntegerFormatter.kt`, `CadenceFormatter.kt`.
* Changes: Migrate to Kotlin SAM interface and utility classes with non-null string returns. Delete corresponding `.java` files.

### Step 2: Domain-Specific Formatters Migration
* Files: `DfaA1Formatter.kt`, `IntensityFactorFormatter.kt`, `PowerToWeightFormatter.kt`, `BatteryRemainingTimeFormatter.kt`.
* Changes: Migrate to Kotlin; ensure defensive null/NaN/Infinity handling (`"--"`, `"--.--"`), battery state checks (-1/-2), and `format_with_units`. Delete corresponding `.java` files.

### Step 3: Metric, Time & Distance Formatters Migration
* Files: `TimeFormatter.kt`, `PaceFormatter.kt`, `SpeedFormatter.kt`, `AltitudeFormatter.kt`, `DistanceFormatter.kt`.
* Changes: Migrate to Kotlin; ensure time/pace boundary checks (`BANALService.MAX_PACE`), `format_with_units`, and `format_3` overloads for Java callers. Delete corresponding `.java` files.

### Step 4: Java Caller Interoperability & SWE.2 Check
* Files: `SensorType.java`, `docs/architecture.md`.
* Changes: Verify seamless Java compilation with zero caller modifications; verify zero broken imports or SAM mismatches.

### Step 5: Verification & Governance Audit
* Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.sensor.formater.*"`
* Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.architecture.JavaCoreMigrationStrategyContractTest"`
* Run: `python3 tools/verify_requirement_governance.py`
* Run: `./gradlew :app:koverVerifyDebug` and `./gradlew :app:detekt`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests (`TST-ARC-007.1`–`TST-ARC-007.5`), Kover coverage floor ($\ge 70\%$ Line / $\ge 50\%$ Branch), Detekt 0 issues, and requirement governance check.
* **Rollback**: Branch isolation allows immediate full revert via `git checkout -- .` before merge.
