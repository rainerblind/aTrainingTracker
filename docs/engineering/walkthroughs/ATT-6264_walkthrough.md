# Stage 5 Walkthrough: ATT-6264 - Prioritize Topographic Altitude (DEM) Above Barometer When Explicitly Paired

## Overview
- **Ticket**: [ATT-6264](https://rainerblind.atlassian.net/browse/ATT-6264) (`[Medium] Prioritize Topographic Altitude (DEM / ALTITUDE_FROM_POSITION) above Barometric Altitude (ALTITUDE_FROM_PRESSURE) when explicitly paired while keeping Barometer as default`)
- **Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)
- **Target Release**: `V5.0.0`
- **Fulfills Requirements**: `REQ-TRK-024` (`REQ-TRK-024.1`, `REQ-TRK-024.5`), `REQ-SEN-025` (`REQ-SEN-025.1`)
- **Fulfills Tests**: `TST-TRK-016` (`TST-TRK-016.1`, `TST-TRK-016.6`, `TST-TRK-016.9`), `TST-SEN-025.1`
- **Implementation Subtask**: ATT-6373

---

## Changes Implemented

### 1. Priority Hierarchy Reordering (`MySensorManager.java`)
- **DEM Priority Over Barometer (`REQ-TRK-024.1`)**: Reordered `ALTITUDE_PRIORITY` to place `DeviceType.ALTITUDE_FROM_POSITION` (rank 0) ahead of `DeviceType.ALTITUDE_FROM_PRESSURE` (rank 1), followed by Google Fused (rank 2) and GPS (rank 3).
- **Canonical Rank Resolution (`REQ-TRK-024.5`)**: `MySensorManager.getPriorityRank(SensorType.ALTITUDE, ...)` resolves rank `0` for DEM and rank `1` for Barometer. Added comprehensive Javadoc documentation.

### 2. Preserved System Invariants
- **Default Out-of-the-Box State (`REQ-SEN-025.1`)**: `ALTITUDE_FROM_POSITION` remains unpaired (`PAIRED = false`) by default in `DeviceManager` and `DevicesDatabaseManager`. Fresh installs retain Barometer as primary sensor.
- **Dynamic Fallback & Filter Re-seeding**: Prior to initial GPS fix or indoors, DEM is not registered and `MySensorManager` automatically falls back to Barometer. When DEM registers, `VerticalSpeedAndSlopeDevice.checkAltitudeSourceTransition()` re-seeds altitude and VAM filters seamlessly.
- **Zero UI Alterations**: Preserves `DeviceType` enum order and UI layout per `docs/design_guidelines.md` §5.

### 3. Unit Test Alignment (`MySensorManagerPriorityTest.kt`)
- **Hierarchy Verification (`TST-TRK-016.1`)**: Added explicit array equality assertion for `ALTITUDE_PRIORITY` in `speedPriority_prioritizesGoogleFusedOverGps`.
- **Dynamic Switching (`TST-SEN-025.1`)**: Verified dynamic upgrading across GPS $\rightarrow$ Fused $\rightarrow$ Pressure $\rightarrow$ DEM, and cascading fallback upon unregistering DEM $\rightarrow$ Pressure $\rightarrow$ Fused $\rightarrow$ GPS.
- **Canonical Indices (`TST-TRK-016.6`)**: Verified rank 0 for DEM and rank 1 for Barometer across `getPriorityRank`.

---

## Verification & Test Results

### Tier 1 Targeted Unit Test Scope (`Rule 60`)
- **Scope**: Targeted unit tests covering modified sensor management and build contracts (`com.atrainingtracker.banalservice.sensor.*` and `com.atrainingtracker.trainingtracker.build.*`).
- **Command**: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.banalservice.sensor.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
- **Result**: `BUILD SUCCESSFUL` (100% PASS, 6/6 tests passed in `MySensorManagerPriorityTest`).

### Governance & Living Documentation
- **Governance Audit**: `python3 tools/verify_requirement_governance.py --sync-indexes && python3 tools/verify_requirement_governance.py`
- **Result**: `PASS: Master index tables successfully synchronized with 100% parity` (Total living tests: 937, 937 Verified; Total living requirements: 935, 929 Verified, 6 Planned).
- **Physical Device Deployment**: Skipped pursuant to `Rule 60` / `stage5-verification/SKILL.md` (pure backend sensor priority configuration and unit test suite; zero UI, navigation, or layout changes).
