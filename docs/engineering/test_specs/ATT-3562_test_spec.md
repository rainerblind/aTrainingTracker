# Stage 2 Test Specification: ATT-3562 - Replace Unguarded Enum.valueOf Calls on Cursors, Intents, and Preferences with Safe Deserialization

**Ticket**: [ATT-3562](https://rainerblind.atlassian.net/browse/ATT-3562)  
**Sub-task**: [ATT-3656](https://rainerblind.atlassian.net/browse/ATT-3656) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No crashes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Branch**: `feature/ATT-3562`  
**Requirement Mapping**: `REQ-ARC-001` (*Safe Enum Deserialization, Observability & Crash Immunity Across External Boundaries*)  
**Test Mapping**: `TST-ARC-001`  
**Author**: AI Agent 1 (Implementer)  
**Reviewer / Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-15  

---

## 1. Formal Requirement Specification (`REQ-ARC-001`)

### REQ-ARC-001: Safe Enum Deserialization, Observability & Crash Immunity Across External Boundaries

The system SHALL guarantee crash immunity against `NullPointerException` and `IllegalArgumentException` during enum deserialization from external untrusted sources (SQLite cursors, IPC Intents/Bundles, JSON payloads, and SharedPreferences), while enforcing ASPICE data correctness and telemetry observability (ATT-3562, parent epic ATT-235):

1. **Canonical Deserialization Engine (`REQ-ARC-001.1`)**:
   - The system SHALL provide a centralized utility `com.atrainingtracker.util.EnumUtils` offering:
     - `safeValueOf(enumClass: Class<T>, name: String?, defaultValue: T, tag: String = "EnumUtils"): T`
     - `safeValueOfOrNull(enumClass: Class<T>, name: String?, tag: String = "EnumUtils"): T?`
   - Kotlin callers SHALL be provided with inline reified extension functions:
     - `safeEnumValueOf(name: String?, default: T, tag: String = "EnumUtils"): T`
     - `safeEnumValueOfOrNull(name: String?, tag: String = "EnumUtils"): T?`
   - The utility SHALL accept `null`, empty string `""`, leading/trailing whitespace strings, and unrecognized string tokens without throwing any runtime exception.

2. **Criticality-Tiers Handling Strategy (`REQ-ARC-001.2`)**:
   - **Tier A (Critical Hardware & Lifecycle State)**: In `DeviceManager` (`Protocol`, `DeviceType`) and `TrackerService` (`StartType`), unparseable or null enum strings SHALL trigger an explicit high-severity error log (`Log.e`) and a graceful operation abort / early exit, preventing corrupted radio dispatch or undefined lifecycle states.
   - **Tier B (Database Multi-Record Queries)**: In database query cursor iteration (`WorkoutSummariesDatabaseManager`, `ExportStatusDatabaseManager`, `PebbleDatabaseManager`), rows containing unparseable or null enum strings SHALL trigger a diagnostic warning log (`Log.w`) and be skipped (`continue;`), preventing corruption of user statistics with synthetic ghost records.
   - **Tier C (UI Configuration & Preferences)**: In preference loaders, dialog arguments, and view configurations (`PeriodSummariesDatabaseManager`, `EquipmentDbHelper`, `TrainingApplication`, `TuningPreferencesDataStore`), invalid or missing enum strings SHALL trigger a diagnostic warning log (`Log.w`) and fallback safely to canonical defaults (`PeriodType.WEEK`, `BSportType.UNKNOWN`, `ViewSize.NORMAL`, `MyUnits.METRIC`).

3. **Observability & Telemetry Invariant (`REQ-ARC-001.3`)**:
   - All safe deserialization fallbacks and skipped rows SHALL emit structured diagnostic logs containing the caller `tag`, the target enum class name, and the invalid string token for telemetry traceability.

4. **Architectural Guard & Regression Prevention (`REQ-ARC-001.4`)**:
   - The codebase SHALL enforce safe deserialization via automated static audit tests (`SafeEnumDeserializationAuditTest.kt`) ensuring no direct unguarded `Enum.valueOf(cursor.getString(...))` or `Enum.valueOf(intent.getStringExtra(...))` calls exist in production code.

---

## 2. Test Specification (`TST-ARC-001`)

### TST-ARC-001: Safe Enum Deserialization, Observability & Architecture Guard Verification Suite

1. **Central Utility Unit Tests (`EnumUtilsTest.kt`) (`TST-ARC-001.1`)**:
   - `testValidConstant_returnsExactEnum`:
     - Given valid enum name string `"RUN"`, `safeValueOf(BSportType::class.java, "RUN", BSportType.UNKNOWN)` returns `BSportType.RUN`.
     - `safeValueOfOrNull(BSportType::class.java, "RUN")` returns `BSportType.RUN`.
   - `testWhitespaceTrimming_returnsExactEnum`:
     - Given `" RUN  "`, returns `BSportType.RUN`.
   - `testNullInput_returnsFallbackOrNull`:
     - Given `null`, `safeValueOf` returns `BSportType.UNKNOWN`.
     - Given `null`, `safeValueOfOrNull` returns `null`.
   - `testEmptyOrBlankInput_returnsFallbackOrNull`:
     - Given `""` or `"   "`, `safeValueOf` returns `BSportType.UNKNOWN`.
     - Given `""` or `"   "`, `safeValueOfOrNull` returns `null`.
   - `testUnrecognizedConstant_returnsFallbackOrNull`:
     - Given `"NON_EXISTENT_SPORT"`, `safeValueOf` returns `BSportType.UNKNOWN`.
     - Given `"NON_EXISTENT_SPORT"`, `safeValueOfOrNull` returns `null`.
   - `testKotlinReifiedExtensions`:
     - Verify `safeEnumValueOf<BSportType>("BIKE", BSportType.UNKNOWN)` returns `BSportType.BIKE`.
     - Verify `safeEnumValueOfOrNull<BSportType>("INVALID")` returns `null`.

2. **Tier A (Hardware & Lifecycle) Validation Tests (`TST-ARC-001.2`)**:
   - In `DeviceManager`:
     - Given an `Intent` with missing or corrupt `Protocol` or `DeviceType` extra,
     - When received,
     - The search handler aborts immediately and does not trigger BLE/ANT background scanning.
   - In `TrackerService`:
     - Given an `Intent` with missing or corrupt `StartType` extra,
     - When processed in `onStartCommand`,
     - Service falls back safely or aborts without crashing.

3. **Tier B (Database Multi-Record Queries) Deserialization Tests (`TST-ARC-001.3`)**:
   - In `WorkoutSummariesDatabaseManagerTest`:
     - Given a mocked SQLite cursor row with `NULL` or unknown `SensorType` (e.g. `"FUTURE_SENSOR"`),
     - When parsing extrema records,
     - The corrupted row is skipped, valid neighboring rows are parsed, and no NPE or IAE is thrown.

4. **Tier C (Preferences & Configuration) Deserialization Tests (`TST-ARC-001.4`)**:
   - In `PeriodSummariesDatabaseManagerTest`:
     - Given a cursor with invalid `PeriodType` string,
     - Falls back to `PeriodType.WEEK` with warning log.
   - In `EquipmentDbHelperTest`:
     - Given a cursor with invalid `BSportType`,
     - Falls back to `BSportType.UNKNOWN` or sensible sport default.

5. **Static Architectural Audit Guard (`SafeEnumDeserializationAuditTest.kt`) (`TST-ARC-001.5`)**:
   - Source scanner scans `app/src/main/java` and `app/src/main/kotlin`.
   - Searches for regex patterns of unguarded deserialization:
     - `\.valueOf\(\s*cursor\.getString`
     - `\.valueOf\(\s*intent\.getStringExtra`
     - `\.valueOf\(\s*getArguments\(\)\.getString`
     - `\.valueOf\(\s*sharedPreferences\.getString`
   - Asserts that matching occurrences count is exactly 0.

6. **Clean-Room Full Suite Regression Execution (`TST-ARC-001.6`)**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

---

## 3. Traceability Matrix

| Requirement Clause | Test Specification ID | Verification Method |
| :--- | :--- | :--- |
| `REQ-ARC-001.1` (Canonical Deserialization Engine) | `TST-ARC-001.1` | `EnumUtilsTest.kt` |
| `REQ-ARC-001.2` (Tier A Hardware & Lifecycle Validation) | `TST-ARC-001.2` | `EnumUtilsTest.kt`, `DeviceManagerTest.java` / targeted unit tests |
| `REQ-ARC-001.2` (Tier B Database Multi-Record Queries) | `TST-ARC-001.3` | `WorkoutSummariesDatabaseTest.kt` / unit tests |
| `REQ-ARC-001.2` (Tier C Preferences & Configuration) | `TST-ARC-001.4` | `PeriodSummariesDatabaseTest.kt` / unit tests |
| `REQ-ARC-001.3` (Observability & Telemetry Invariant) | `TST-ARC-001.1` | `EnumUtilsTest.kt` (Log capture verification) |
| `REQ-ARC-001.4` (Architectural Guard & Regression Prevention) | `TST-ARC-001.5` | `SafeEnumDeserializationAuditTest.kt` |
| Full System Regression | `TST-ARC-001.6` | `./gradlew testDebugUnitTest` |
