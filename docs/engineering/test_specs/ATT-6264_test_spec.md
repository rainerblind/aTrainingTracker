# Stage 2: Requirement & Test Specification - ATT-6264: Prioritize Topographic Altitude (DEM) Above Barometer When Explicitly Paired

**Ticket**: [ATT-6264](https://rainerblind.atlassian.net/browse/ATT-6264) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-6264`  
**Requirement Mapping**: `REQ-TRK-024` (`REQ-TRK-024.1`, `REQ-TRK-024.5`) & `REQ-SEN-025` | **Test Spec ID**: `TST-TRK-016` (`TST-TRK-016.1`, `TST-TRK-016.6`) / `TST-SEN-025`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (`REQ-TRK-024` Amendment)

### 1.1 Functional Scope & Technical Invariants
* **Topographic Altitude Priority in `ALTITUDE_PRIORITY` (`REQ-TRK-024.1`)**: In `MySensorManager.java`, `ALTITUDE_PRIORITY` SHALL be reordered to:
  `[DeviceType.ALTITUDE_FROM_POSITION, DeviceType.ALTITUDE_FROM_PRESSURE, DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED, DeviceType.SPEED_AND_LOCATION_GPS]`.
  When an athlete explicitly pairs *Topografische Höhe (DEM)* (`AltitudeFromPositionDevice`), it SHALL take precedence over the internal smartphone barometer (`AltitudeFromPressureDevice`) for live cockpit altitude and telemetry recording as soon as valid elevation coordinates are resolved.
* **Canonical Priority Rank Helper Update (`REQ-TRK-024.5`)**: `MySensorManager.getPriorityRank(SensorType.ALTITUDE, ...)` SHALL return rank `0` for `ALTITUDE_FROM_POSITION`, rank `1` for `ALTITUDE_FROM_PRESSURE`, rank `2` for `SPEED_AND_LOCATION_GOOGLE_FUSED`, and rank `3` for `SPEED_AND_LOCATION_GPS`.
* **Preservation of Default Unpaired State & Barometer Fallback (`REQ-SEN-025.1`)**: `ALTITUDE_FROM_POSITION` SHALL remain `PAIRED = false` by default on fresh installations in `DeviceManager.java` and `DevicesDatabaseManager.java`. Unconfigured athletes and indoor sessions without GPS fixes SHALL seamlessly continue using `ALTITUDE_FROM_PRESSURE` as the default altitude source.
* **Localization Impact**: N/A (no string additions or modifications; existing localized names preserved).

### 1.2 Given-When-Then Acceptance Criteria
* **AC-1 (DEM Priority Over Barometer When Paired)**: *Given* both `AltitudeFromPositionDevice` (DEM) and `AltitudeFromPressureDevice` (barometer) are registered in `MySensorManager`, *When* `getBestSensor(SensorType.ALTITUDE)` is queried, *Then* it SHALL return `AltitudeFromPositionDevice`.
* **AC-2 (Seamless Fallback to Barometer)**: *Given* `AltitudeFromPressureDevice` is active and `AltitudeFromPositionDevice` is unpaired or unregisters (e.g. loss of GPS fix), *When* `getBestSensor(SensorType.ALTITUDE)` is queried, *Then* it SHALL return `AltitudeFromPressureDevice`.
* **AC-3 (Priority Rank Verification)**: *When* `MySensorManager.getPriorityRank(SensorType.ALTITUDE, deviceType)` is called, *Then* it SHALL return `0` for `ALTITUDE_FROM_POSITION` and `1` for `ALTITUDE_FROM_PRESSURE`.
* **AC-4 (Default Pairing Invariant)**: *Given* a fresh app installation, *When* `DevicesDatabaseManager.addLocationDevicesToDb()` executes, *Then* `ALTITUDE_FROM_POSITION` SHALL be inserted with `PAIRED = false` and `ALTITUDE_FROM_PRESSURE` with `PAIRED = true` (on devices with a pressure sensor).

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-024` targeting `MySensorManager.java`, extended by `ATT-4375` targeting `DeviceType.java`, `MySensorManager.java`, and `DevicesDatabaseManager.java`, amended by `ATT-5491`, `ATT-5492`, and amended by `ATT-6264`.
2. *Historical Origin & Commit Trace*: Commit `fc28443c` (`ATT-3961`, Sprint `2026-43.1`), refined in `ATT-4375` (Sprint `2026-43.2`), amended in `ATT-5491` (Sprint `2026-45.8`), `ATT-5492` (Sprint `2026-45.9`), and `ATT-6264` (Sprint `2026-46.1`).
3. *Root Reason for Existing Formulation*: In `ATT-5491`/`ATT-5492`, `ALTITUDE_FROM_PRESSURE` was placed at rank 0 ahead of `ALTITUDE_FROM_POSITION` because barometric altitude provides smooth 1-Hz relative elevation without requiring GPS fixes or network connectivity. However, hardcoding barometer above DEM meant explicitly pairing DEM had zero effect unless the user manually unpaired the barometer.
4. *Preservation of Core Invariants*: `ALTITUDE_FROM_POSITION` remains default unpaired (`PAIRED = false`) in `DeviceManager` and `DevicesDatabaseManager`. Fresh installs and unconfigured users retain `ALTITUDE_FROM_PRESSURE` as the active default altitude source. When DEM is paired, seamless fallback to barometer operates whenever DEM is not yet resolved. Dedicated wheel speed and power sensors still strictly supersede all location sensors. Existing user databases and pairings are 100% untouched.

---

## 2. Test Specification & Traceability Matrix (`TST-TRK-016`)

| Req ID | Test Case | Target Component | Verification Scope |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-024.1` | `TST-TRK-016.1` | `MySensorManagerPriorityTest` | `ALTITUDE_PRIORITY` array order: asserts `ALTITUDE_FROM_POSITION` (0) > `ALTITUDE_FROM_PRESSURE` (1) > `GOOGLE_FUSED` (2) > `GPS` (3). |
| `REQ-TRK-024.1` | `TST-SEN-025.1` | `MySensorManagerPriorityTest` | Dynamic source switching: registering DEM upgrades over barometer; unregistering DEM falls back to barometer. |
| `REQ-TRK-024.5` | `TST-TRK-016.6` | `MySensorManagerPriorityTest` | `getPriorityRank` helper: asserts rank 0 for `ALTITUDE_FROM_POSITION` and rank 1 for `ALTITUDE_FROM_PRESSURE`. |
| `REQ-SEN-025.1` | `TST-SEN-025.3` | `DevicesDatabaseManagerOrderingTest` | Default pairing: asserts `ALTITUDE_FROM_POSITION` is default unpaired (`PAIRED = false`). |
| `REQ-TRK-024` | `TST-TRK-016.9` | Targeted Test Suite | Targeted test execution: `com.atrainingtracker.banalservice.sensor.*` and `com.atrainingtracker.banalservice.devices.*`. |
