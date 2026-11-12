# Stage 2: Requirement & Test Specification - ATT-7318: [Bug] bt_bike_radar.xml & bike_radar.xml: Fix overlapping radar emission arcs with top-left mini-icon, rear wheel, and viewport edge

**Ticket**: [ATT-7318](https://rainerblind.atlassian.net/browse/ATT-7318)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.4`  
**Requirement Mapping**: `REQ-UI-514` (*Dedicated ANT+ and Bluetooth LE Vector Sensor Badges for DeviceType.BIKE_RADAR*)  
**Test Spec ID**: `TST-UI-514`  
**Branch**: `subagent-Lane-3-Agent---ATT-7318-self-dbceeb46`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-UI-514)

### 1.1 Problem Statement & Rationale
`bt_bike_radar.xml` previously suffered from colliding redundant radar emission arcs cutting through top-left `mini_icon_radar` and clipping off-screen, while `bike_radar.xml` had lower arc ends slicing into the rear wheel. The system SHALL eliminate all geometric overlaps and boundary violations while preserving the canonical sensor family visual language.

### 1.2 Functional & Architectural Requirements
1. **`bt_bike_radar.xml` Canonical Family Alignment (`REQ-UI-514.2`)**:
   - `cyclist` group (`translateX="18.18"`) SHALL contain exactly the 18 canonical unadorned cyclist paths matching `bt_bike_pwr.xml`, `bt_bike_spd.xml`, and `bt_bike_cad.xml` with zero device overlays.
   - `mini_icon_radar` group in the top-left quadrant SHALL maintain clean visual separation ($\ge 7.0$ px) from the cyclist silhouette and stay strictly within viewport bounds.
2. **`bike_radar.xml` Arc Origin & Clearance (`REQ-UI-514.1`)**:
   - Radar emission arcs SHALL radiate from origin $(36.0, 26.0)$ with concentric radii $[8.0, 14.0, 20.0]$ spanning from $+50^\circ$ up (angle $230^\circ$) to $-25^\circ$ down (angle $155^\circ$).
   - All arc points MUST maintain $\ge 3.0$ units physical clearance from the rear wheel ($y_{\text{wheel}} \ge 39.0$), cyclist saddle/back, and card border.
   - Arc endpoints MUST stay cleanly above the rear wheel ($y \le 35.0$).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-514` (*Dedicated ANT+ and Bluetooth LE Vector Sensor Badges for DeviceType.BIKE_RADAR*, Sprint 2026-46.3, `ATT-6699`).
* **Historical Origin & Commit Trace**: Introduced in `ATT-6699` to provide dedicated badges for `DeviceType.BIKE_RADAR`.
* **Root Reason for Existing Formulation**: `REQ-UI-514.2` originally drafted both top-left mini-icon and saddle arcs simultaneously, while `REQ-UI-514.1` used symmetric vertical chords that plunged into the rear wheel.
* **Preservation of Core Invariants**: Bluetooth `#0A3D91` badge, ANT+ `#000000` badge, full-bleed $0..144$ ($r=22$), uppercase bold `RDR` typography with `evenOdd`, Rule 37 compliance ($\le 45$ `L` commands, analytical curves), and `UIHelper`/`DeviceData` resolution contracts are strictly preserved.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (bt_bike_radar.xml)**:
  * *Given* `app/src/main/res/drawable/bt_bike_radar.xml`,
  * *When* parsed and inspected by `VectorAssetResolutionContractTest`,
  * *Then* the `cyclist` group contains exactly 18 canonical cyclist paths with zero colliding saddle arcs, and `mini_icon_radar` maintains $\ge 7.0$ px clearance from the cyclist silhouette.
* **Criterion 2 (bike_radar.xml)**:
  * *Given* `app/src/main/res/drawable/bike_radar.xml`,
  * *When* parsed and inspected by `VectorAssetResolutionContractTest`,
  * *Then* radar emission arcs maintain $\ge 3.0$ units clearance from the rear wheel, cyclist back, and card border, with all arc endpoints at $y \le 35.0$.

### 1.5 System Invariants
1. Zero regression in existing 8 unit tests in `VectorAssetResolutionContractTest.kt`.
2. Rule 37 geometric standards ($\le 45$ `L` commands, analytical curve commands).
3. Pure UI asset and test modification; zero changes to sensor engines, database schemas, or Bluetooth/ANT+ acquisition.

---

## 2. Test Specification (TST-UI-514)

### Test Case 1: `testRadarVectorAssetsGeometricBoundingBoxAndCollisionFreeContract` (`TST-UI-514.5`)
* **Scope**: Unit Test / Geometric Contract
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/resources/VectorAssetResolutionContractTest.kt`
* **Preconditions**: `bt_bike_radar.xml` and `bike_radar.xml` exist in `res/drawable/`.
* **Action**: Parse XML DOMs, extract path commands, and evaluate geometric bounds:
  - For `bt_bike_radar.xml`: Assert cyclist group contains exactly 18 paths. Assert `mini_icon_radar` bounding box ($x \in [12.8, 43.6], y \in [17.2, 36.0]$) has zero overlap with cyclist geometry ($x \ge 50.0$ in that Y band).
  - For `bike_radar.xml`: Sample points along all 3 emission arcs and assert distance to rear wheel center $(24.5, 51.5)$ is $\ge 12.5 + 1.4 + 3.0$ ($= 16.9$), distance to back line is $\ge 3.0$, distance to card border is $\ge 3.0$, and arc endpoints satisfy $y \le 35.0$.
* **Expected Result**: All geometric clearance and non-overlap assertions pass.

### Test Case 2: Tier 1 Targeted Regression Suite (`TST-UI-514.4`)
* **Command**: `python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.resources.VectorAssetResolutionContractTest"`
* **Expected Result**: 100% pass rate across all 9 tests in `VectorAssetResolutionContractTest`.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-514.1` | Unit | `testAll16MigratedVectorXmlsAreWellFormedAndUseTrueGeometricPrimitives` | `REQ-UI-514.1`, `REQ-UI-514.4` | Specified |
| `TST-UI-514.2` | Unit | `testMultiColorAndProtocolBadgeVisualFidelityContracts` | `REQ-UI-514.1`, `REQ-UI-514.2` | Specified |
| `TST-UI-514.3` | Unit | `testSportAndSensorDrawableMappingsResolveValidResourceIds` | `REQ-UI-514.3` | Specified |
| `TST-UI-514.4` | Regression | `VectorAssetResolutionContractTest` | `REQ-PRO-001` | Specified |
| `TST-UI-514.5` | Contract | `testRadarVectorAssetsGeometricBoundingBoxAndCollisionFreeContract` | `REQ-UI-514.1`, `REQ-UI-514.2` | Specified |
