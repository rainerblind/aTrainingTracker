# Stage 1 Analysis: ATT-6699 - Design and wire dedicated ANT+ (bike_radar) and BLE (bt_bike_radar) device vector logos for DeviceType.BIKE_RADAR

**Ticket**: [ATT-6699](https://rainerblind.atlassian.net/browse/ATT-6699)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6699`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation

During Sprint 2026-46.1 review of `ATT-5083`, it was observed that although `DeviceType.BIKE_RADAR` was introduced into `DeviceType.java`, `DeviceManager`, and sensor acquisition pipelines, dedicated protocol-badged vector drawables were omitted from `app/src/main/res/drawable/`.
As a result:
- Discovered and paired ANT+ radar devices fall back to the generic `protocol.iconId` (`ant_logo`).
- Discovered and paired Bluetooth LE radar devices fall back to the generic `protocol.iconId` (`logo_protocol_bluetooth`).
This visually diverges from the rest of the unified sensor badge family (`bike_pwr`/`bt_bike_pwr`, `bike_spd`/`bt_bike_spd`, `bike_cad`/`bt_bike_cad`, `hr`/`bt_hr`, `run_spd`/`bt_run`), where every sensor type displays a distinct, protocol-badged icon with a centered discipline motif, protocol-specific background color (`#000000` for ANT+, `#0A3D91` for BLE), domain-specific mini-icon/wave, and bold uppercase bottom typography (`REQ-UI-375`, `REQ-UI-442`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification**:
  * **Hypothesis 1 (`UIHelper.java` missing mapping) — Verified**:
    Inspection of `UIHelper.java` lines 31–67 confirms `switch (protocol)` / `switch (deviceType)` cases exist for `HRM`, `BIKE_SPEED`, `BIKE_CADENCE`, `BIKE_SPEED_AND_CADENCE`, `BIKE_POWER`, `RUN_SPEED`, and `ENVIRONMENT`. `DeviceType.BIKE_RADAR` is absent under both `ANT_PLUS` and `BLUETOOTH_LE`, falling through to `protocol.getIconId()`.
  * **Hypothesis 2 (`DeviceData.kt` missing mapping) — Verified**:
    Inspection of `DeviceData.kt` lines 244–275 confirms `getIconId(deviceType, protocol)` lacks branches for `DeviceType.BIKE_RADAR` under both `Protocol.ANT_PLUS` and `Protocol.BLUETOOTH_LE`, falling back to `protocol.iconId`.
  * **Hypothesis 3 (Missing Vector Assets) — Verified**:
    `app/src/main/res/drawable/bike_radar.xml` and `app/src/main/res/drawable/bt_bike_radar.xml` do not exist in the codebase.
  * **Hypothesis 4 (Contract Test Coverage) — Verified**:
    `VectorAssetResolutionContractTest.kt` verifies the existing 16 migrated vector drawables for Rule 37 constraints ($\le 45$ `L` commands, analytical curves, `evenOdd` counter punch-outs, `#0A3D91` color token, full-bleed badge bounds). `bike_radar` and `bt_bike_radar` are not yet included in the test matrices.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Design `bike_radar.xml`: ANT+ vector drawable (`96x96`, `48dp x 48dp`, black `#000000` rounded badge $r=10.0$, centered cyclist motif, rear radar wave arcs behind the saddle/seatpost, and bold uppercase `RDR` label at $y \in [75.5, 90.5]$ with `evenOdd` fillType).
  * Design `bt_bike_radar.xml`: BLE vector drawable (`144x144`, `48dp x 48dp`, `#0A3D91` full-bleed badge $r=22.0$, centered cyclist motif with `translateX=18.18`, top-left radar wave mini-icon, rear radar wave emission arcs, and bold uppercase `RDR` label at $y \in [113.25, 135.75]$ with `evenOdd` fillType).
  * Wire `DeviceType.BIKE_RADAR` in `DeviceData.kt` (`getIconId`) and `UIHelper.java` (`getIconId`).
  * Extend `VectorAssetResolutionContractTest.kt` to enforce Rule 37 geometric constraints, color tokens, and resource resolution for both radar icons.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to sensor discovery engines or connection drivers.
  * No modification to radar threat overlay UI or audio/haptic chimes.
  * No modifications to raster asset pipelines or other sensor icons.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-375` (*Mathematical VectorDrawable Migration*), `REQ-UI-442` (*Full-Bleed Badge Geometry & Uppercase Labels for Bluetooth LE Sensor Drawables*).
* **Historical Origin & Commit Trace**:
  * `ATT-2859` (commit `15c74685`, Sprint 2026-42.7: VectorDrawable migration from masters).
  * `ATT-4956` (commit `be03a5bc14`, Sprint 2026-45.1: Full-bleed BTLE badge geometry).
  * `ATT-5201` (commit `43de6fd0`, Sprint 2026-45.5: Official Bluetooth color `#0A3D91`, centered cyclist, top-left canonical mini-icons).
* **Root Reason for Existing Formulation**:
  * Vector icons were mathematically designed with explicit analytical curves to eliminate jagged rendering, density-split APK bloat, and ICC color distortion.
  * ANT+ sensor badges use `#000000` with 3-letter abbreviations (`PWR`, `SPD`, `CAD`) on `96x96` grids; BLE sensor badges use `#0A3D91` with matching abbreviations on `144x144` grids with top-left domain mini-icons.
* **Preservation of Core Invariants**:
  * Rule 37 geometric constraints ($\le 45$ `L` commands per path, analytical `A`/`C`/`Q` curves, zero auto-trace artifacts) preserved.
  * Official Bluetooth color token `#0A3D91` strictly preserved.
  * 100% backward compatibility for all existing `DeviceType` icon resolutions in `UIHelper` and `DeviceData`.

---

## 5. Architectural Strategy & High-Level Solution

1. **`app/src/main/res/drawable/bike_radar.xml`**:
   - Background: Rounded rect `#000000` ($96 \times 96$, $r=10$).
   - Cyclist: Standard white silhouette matching `bike_pwr.xml`.
   - Radar Motif: Analytical arcs radiating backward from the cyclist's seatpost/tail ($x \approx 8..22, y \approx 28..48$).
   - Label: Mathematical `evenOdd` `RDR` vector typography ($y \in [75.5, 90.5]$).
2. **`app/src/main/res/drawable/bt_bike_radar.xml`**:
   - Background: Full-bleed rounded rect `#0A3D91` ($144 \times 144$, $r=22$).
   - Cyclist: Centered cyclist group with `translateX="18.18"`.
   - Mini-Icon: Canonical radar wave mini-icon in top-left ($x \approx 12, y \approx 10$).
   - Radar Motif: Analytical arcs radiating backward from the cyclist's seatpost.
   - Label: Mathematical `evenOdd` `RDR` vector typography ($y \in [113.25, 135.75]$).
3. **Wiring**:
   - `DeviceData.kt`: Map `DeviceType.BIKE_RADAR` to `R.drawable.bike_radar` for ANT+ and `R.drawable.bt_bike_radar` for BLE.
   - `UIHelper.java`: Map `DeviceType.BIKE_RADAR` to corresponding drawables.
4. **Contract Testing**:
   - Add `bike_radar` and `bt_bike_radar` to `VectorAssetResolutionContractTest.kt`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Pure UI vector asset addition; zero changes to BLE/ANT+ protocol drivers, database schemas, or tracking engine.
  2. Rule 37 compliance: $\le 45$ `L` commands per `<path>`, analytical curves only, zero polygon auto-tracing.
  3. All existing unit tests and vector contract tests continue to pass with 100% success.
* **Risk Rating**: **LOW**
  - Pure graphical vector asset addition with localized wiring in UI helper classes and deterministic contract test validation.
