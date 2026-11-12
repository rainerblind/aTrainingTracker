# Stage 2 Requirement & Test Specification: ATT-6687

**Ticket**: [ATT-6687](https://rainerblind.atlassian.net/browse/ATT-6687)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Requirements**: `REQ-TRK-070.3`  
**Test Suite**: `TST-TRK-070.2`  

---

## 1. Living Requirements Traceability
* **`REQ-TRK-070.3`**: `ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, context)` SHALL return an array containing all 8 power duration `SensorType` constants:
  - Rolling: `POWER_5S`, `POWER_1M`, `POWER_5M`, `POWER_20M`
  - Peak: `POWER_PEAK_5S`, `POWER_PEAK_1M`, `POWER_PEAK_5M`, `POWER_PEAK_20M`

---

## 2. Test Specification (`TST-TRK-070.2`)
* **Test Class**: `com.atrainingtracker.banalservice.ActivityTypeTest`
* **Test Method**: `testGetSensorTypeArray_bikePowerContainsAll8PowerDurationSensors`
* **Given-When-Then**:
  * **Given**: `ActivityType.BIKE_POWER`
  * **When**: Querying `ActivityType.getSensorTypeArray(ActivityType.BIKE_POWER, null)`
  * **Then**: Every one of the 8 MMP rolling and peak power duration `SensorType` constants is present.
