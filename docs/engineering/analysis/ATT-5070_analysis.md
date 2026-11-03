# Stage 1 Analysis: ATT-5070 - [Bug] Power zone not shown

**Ticket**: [[ATT-5070]](https://rainerblind.atlassian.net/browse/ATT-5070)  
**Sub-task**: [[ATT-5097]](https://rainerblind.atlassian.net/browse/ATT-5097) (`[Analysis]`)  
**Parent Epic**: None  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Branch**: `feature/ATT-5070`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Problem Statement & Motivation
* **Current Behavior**: In the scrubbing tooltip card on *Karte & Diagramme* (`ElevationProfile.kt`), Heart Rate displays its zone suffix (e.g. `147 bpm • Z3`), but Coggan Power Zones 6 and 7 (e.g. `603 W • Z7`) omit the `• Z<n>` suffix, even though the Power chart header right below correctly shows `Leistung: 603 W • Z7`.
* **Expected Behavior**: The scrubbing tooltip card must display the zone suffix for all 7 Coggan cycling power zones (`Z1..Z7`) using their corresponding colors from `TelemetryZoneMath.ZONE_COLORS` (`TTColor.Zone1..Zone7`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA Call Site**:
  * In `ElevationProfile.kt` (line 1137), `ScrubbingTelemetryBadge` checks:
    ```kotlin
    if (powerZone != null && powerZone in 1..5)
    ```
    This restricts power zone suffix rendering to zones 1..5, suppressing `• Z6` and `• Z7`.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Verified hypothesis against codebase: `TelemetryZoneMath.determinePowerZone()` maps power to zones `1..7`, and `TelemetryZoneMath.ZONE_COLORS` contains 7 colors (`TTColor.Zone1..Zone7`).
  * `MapDetailLayout.kt` (line 449) and `TelemetryMetricUtils.kt` (line 218) format power zones without clamping to 5, which explains why the chart header displays `603 W • Z7` while the tooltip card omits it.
  * Audited all other zone callers across `ui/map/` and `ui/aftermath/`: `ElevationProfile.kt:1137` is the sole call site erroneously restricting power zones to `1..5`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Update `ElevationProfile.kt` power zone range check to `1..TelemetryZoneMath.ZONE_COLORS.size` (or `1..7`).
  * Update `ScrubbingBadgeZoneColorContractTest.kt` to assert support for all 7 Coggan power zones.
  * Update living documentation (`REQ-UI-242`, `TST-UI-201`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying Heart Rate 5-zone range checks (`1..5`).
  * Modifying zone threshold calculations, database schemas, or layout structure of `ScrubbingTelemetryBadge`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-242` (*Aftermath/Scrubbing: Render Training Zone Badge Suffix in Active Zone Color in Scrubbing Telemetry Badge*).
* **Historical Origin & Commit Trace**: Introduced in `ATT-2015` (Sprint 2026-40.12, commit `0064fba1`).
* **Root Reason for Existing Formulation**: When dynamic zone coloring was added to `ScrubbingTelemetryBadge`, the 5-zone range `in 1..5` was copy-pasted from Heart Rate to Power, inadvertently overlooking Coggan's 7-zone power scale.
* **Preservation of Core Invariants**: Retains neutral `onSurface` base text styling, dynamic zone coloring via `TelemetryZoneMath.ZONE_COLORS`, single-line layout, and 9-language localization parity. Expanding range to 1..7 safely restores visual parity with chart headers and zone distributions.

---

## 5. Architectural Strategy & High-Level Solution
* In `ElevationProfile.kt` (line 1137):
  Replace `if (powerZone != null && powerZone in 1..5)` with `if (powerZone != null && powerZone in 1..TelemetryZoneMath.ZONE_COLORS.size)`.
* In `ScrubbingBadgeZoneColorContractTest.kt`:
  Add contract assertions verifying that `ElevationProfile.kt` does not clamp power zones to `1..5` and permits all 7 Coggan zones.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero regressions in HR zone rendering (1..5); single-line text layout with no baseline jitter; contrast accessibility preserved.
* **Risk Rating**: **LOW**. Single-line conditional range expansion in a presentation composable; zero data layer or schema changes.
