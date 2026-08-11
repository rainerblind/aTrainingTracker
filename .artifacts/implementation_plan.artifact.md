# Implementation Plan - ATT-507: Standardize TCX Cadence Export

Standardize the TCX export engine to strictly follow the Training Center XML (TCX) Version 2.0 schema by ensuring cadence and power values are exported as integers.

## User Review Required

> [!IMPORTANT]
> - **Schema Compliance**: The TCX specification strictly requires `<Cadence>` (0-254) and `<Watts>` (0-65535) to be integers. Currently, we are exporting them as doubles (e.g., `85.0`, `250.0`), which causes compatibility issues with strict analytical platforms.
> - **Consistent Formatting**: I am refactoring the export loop to cast these values to integers, which will result in clean output like `85` and `250`.

## Proposed Changes

### 1. Export Layer: TCX Standard Format
Fulfills REQ-EXP-001 (Refinement) | Test: TST-EXP-004

#### [MODIFY] [TCXFileWriter.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/exporter/writer/TCXFileWriter.java)
- **Refactor Cadence & Power Output**:
    - Change the `cadence` and `power` variable declarations to `int` within the export loop.
    - Round the double values from the database to the nearest integer using `Math.round()` before casting.
    - Ensure `<Cadence>`, `<Watts>`, and `<RunCadence>` all utilize these integer values.
    - Maintain `double` for fields that require it (e.g., `<Speed>`, `<AltitudeMeters>`, `<DistanceMeters>`).

## Verification Plan

### Manual Verification (TST-EXP-004)
1. Perform a TCX export of a workout containing Cadence and Power data.
2. Open the resulting `.tcx` file in a text editor.
3. **Verify** that the `<Cadence>`, `<Watts>`, and `<RunCadence>` tags contain whole numbers (e.g., `85`) instead of decimal values (e.g., `85.0`).
