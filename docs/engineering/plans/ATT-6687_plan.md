# Stage 3 Implementation Plan: ATT-6687

**Ticket**: [ATT-6687](https://rainerblind.atlassian.net/browse/ATT-6687)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Requirements**: `REQ-TRK-070.3`  
**Test Suite**: `TST-TRK-070.2`  

---

## 1. Architectural Strategy
* `ATT-6726` already integrated all 8 MMP power duration constants into `ActivityType.BIKE_POWER` and introduced `ActivityTypeTest.kt`.
* Step 1: Confirm presence in `ActivityType.java`.
* Step 2: Confirm default filter configuration in `SensorFilterDefaults.kt`.
* Step 3: Run unit tests `com.atrainingtracker.banalservice.ActivityTypeTest`.
