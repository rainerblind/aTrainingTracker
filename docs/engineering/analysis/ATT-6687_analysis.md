# Stage 1 Analysis: ATT-6687 - [Bug] ActivityType & EditSensorFieldDialog: Register the 8 MMP rolling and peak power SensorTypes (POWER_5S..POWER_PEAK_20M) in ActivityType.BIKE_POWER

**Ticket**: [ATT-6687](https://rainerblind.atlassian.net/browse/ATT-6687)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `Sprint 2026-46.4`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Problem Statement & Motivation
In `ATT-1298`, 8 Mean Maximal Power (MMP) rolling and session-peak `SensorType` enums were implemented in `SensorType.java`, `SlidingWindowPowerAccumulator.kt`, `CyclingPowerMetricsDevice.java`, and localized in `strings_cockpit.xml`:
* Rolling Mean: `POWER_5S`, `POWER_1M`, `POWER_5M`, `POWER_20M`
* Session Peak: `POWER_PEAK_5S`, `POWER_PEAK_1M`, `POWER_PEAK_5M`, `POWER_PEAK_20M`

However, `EditSensorFieldViewModel.kt` populates the Cockpit field picker via `ActivityType.getSensorTypeArray(activityType, context)`. Prior to consolidation in `ATT-6726`, `ActivityType.java` (`case BIKE_POWER:`) omitted these 8 enums.

---

## 2. Root Cause Analysis & Architectural Audit
* **Root Cause**: `ActivityType.java` contained a hardcoded array of `SensorType` constants for `BIKE_POWER` that was not updated alongside the MMP engine introduction.
* **Resolution in ATT-6726**: `ATT-6726` consolidated and resolved this gap by introducing `SensorSubCategory` (`POWER_ROLLING`, `POWER_PEAK`), wiring all 8 MMP power duration metrics into `ActivityType.BIKE_POWER`, and ensuring `SensorFilterDefaults.kt` routes them to `FilterType.INSTANTANEOUS`.
* **Verification**: `ActivityTypeTest.kt` verifies that all 8 metrics are present in `ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, null)`.

---

## 3. User Scope Grounding
* **In-Scope Goals**:
  * Verify registration of all 8 MMP power duration metrics in `ActivityType.BIKE_POWER`.
  * Verify `SensorFilterDefaults.kt` mappings.
  * Verify contract test `testGetSensorTypeArray_bikePowerContainsAll8PowerDurationSensors` passes 100%.
* **Out-of-Scope Non-Goals**:
  * No further modifications to `ActivityType.java` needed as code is already in place from `ATT-6726`.
