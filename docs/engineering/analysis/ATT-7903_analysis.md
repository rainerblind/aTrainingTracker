# Stage 1 Analysis: ATT-7903 Java Core Migration: Leaf Sensor Formatters

- **Ticket**: ATT-7903
- **Target Release**: V5.0.0
- **Risk Classification**: Standard (Tier 1 Pure Leaf Migration)

## 1. Problem Domain & Forensic Investigation
Package `com.atrainingtracker.banalservice.sensor.formater` contains 15 legacy Java classes (775 LOC).
Forensic inspection reveals ticket description discrepancies:
- Folder path is `sensor/formater` (single 't'); files use `*Formatter` suffix (e.g. `AltitudeFormatter.java`), not `Formater*` prefix.
- Separate classes like `FormaterCalories`, `FormaterHeartRate`, `FormaterPower` do not exist; `SensorType.java` maps them directly to `IntegerFormatter` or `DefaultNumberFormatter`.
- Real classes: `MyFormatter` (interface), `DefaultStringFormatter`, `DefaultNumberFormatter`, `IntegerFormatter`, `SignedIntegerFormatter`, `CadenceFormatter`, `DfaA1Formatter`, `IntensityFactorFormatter`, `PowerToWeightFormatter`, `BatteryRemainingTimeFormatter`, `TimeFormatter`, `PaceFormatter`, `SpeedFormatter`, `AltitudeFormatter`, `DistanceFormatter`.
- Formatters are leaf stateless/utility components implementing `MyFormatter<T>`, called by `SensorType`, UI view models, and overlay formatters.

## 2. Invariants & Migration Requirements
- **Interface Parity**: `MyFormatter<T>` must be functional interface `fun interface MyFormatter<T> { fun format(value: T?): String }`.
- **Null & Boundary Safety**: Handle null, `Double.NaN`, `Double.POSITIVE_INFINITY`, and out-of-range values defensively, returning fallback representations (`"--"`, `"--.--"`).
- **Secondary Methods**: Maintain auxiliary methods (`format_with_units`, `format_3`) called by Java/Kotlin clients with exact signatures.
- **Contract Tests**: `JavaCoreMigrationStrategyContractTest` already enforces `<= 149` Java files; migrating 15 classes reduces count from 138 to 123.
- **Submodule Integrity**: Never alter `ANT-Android-SDKs` submodule gitlink (`160000`).

## 3. Change-Request Impact Analysis
- **Affected Requirements**: `REQ-ARC-005` (Java Core Services Migration Baseline) and net-new `REQ-ARC-007` (Leaf Sensor Formatters Kotlin Migration). Zero breaking functional impact; all 14 concrete formatters maintain 100% string output parity, locale formatting, and fallback semantics (`"--"`, `"--.--"`).
- **Affected Architecture Elements**: `com.atrainingtracker.banalservice.sensor.formater` (15 classes: `MyFormatter` interface and 14 concrete implementations), `SensorType.java` (references `MyFormatter`), UI ViewModels, and `JavaCoreMigrationStrategyContractTest.kt` (asserting `<= 149` Java files).
- **Verification Impact & Test Surface**: Unit tests in `com.atrainingtracker.banalservice.sensor.formater.*`, `JavaCoreMigrationStrategyContractTest`, and UI formatter consumers. Subsystem verification target: line coverage >= 70%, branch coverage >= 50%.
- **Risk Assessment & Invariants**: Standard / Low risk (Tier 1 Pure Leaf Migration). Invariants include pure functional stateless execution, null/NaN/Infinity safety, exact auxiliary method signatures (`format_with_units`, `format_3`), and preservation of `ANT-Android-SDKs` submodule gitlink (`160000`).

## 4. Independent Peer Review & Verification
- **Build & Unit Tests**: Verified clean baseline `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.sensor.formater.*"`.
- **Contract Tests**: Verified `JavaCoreMigrationStrategyContractTest.kt` passes with `<= 149` invariant.
- **Readiness**: Codebase is fully prepared for Stage 2 Requirement and Test Specification.
