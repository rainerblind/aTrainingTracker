# Stage 5 Verification & Walkthrough: ATT-6687

**Ticket**: [ATT-6687](https://rainerblind.atlassian.net/browse/ATT-6687)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Requirements**: `REQ-TRK-070.3`  
**Test Suite**: `TST-TRK-070.2`  

---

## 1. Summary of Verification
* Verified that `ActivityType.java` registers all 8 MMP rolling and peak power `SensorType` constants (`POWER_5S`, `POWER_1M`, `POWER_5M`, `POWER_20M`, `POWER_PEAK_5S`, `POWER_PEAK_1M`, `POWER_PEAK_5M`, `POWER_PEAK_20M`) in `ActivityType.BIKE_POWER`.
* Verified that `SensorFilterDefaults.kt` resolves them to `FilterType.INSTANTANEOUS`.
* Executed `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ActivityTypeTest"` with 100% pass rate.

---

## 2. Test Execution
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.ActivityTypeTest"
```
Result: BUILD SUCCESSFUL (100% pass).
