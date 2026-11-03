# Stage 2: Requirement & Test Specification - ATT-5070: [Bug] Power zone not shown

**Ticket**: [[ATT-5070]](https://rainerblind.atlassian.net/browse/ATT-5070)  
**Sub-task**: [[ATT-5098]](https://rainerblind.atlassian.net/browse/ATT-5098) (`[Req & Test Spec]`)  
**Parent Epic**: None  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-UI-242` (*Training Zone Badge Suffix Rendering*)  
**Test Spec ID**: `TST-UI-201` (`TST-UI-201.1`)  
**Branch**: `feature/ATT-5070`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Requirement Specification (REQ-UI-242)

### 1.1 Problem Statement & Rationale
In the scrubbing tooltip card on *Karte & Diagramme* (`ElevationProfile.kt`), power values in Coggan Zone 6 or 7 (e.g. `603 W`) omit the zone suffix (`• Z7`), even though the Power chart header right below correctly shows `Leistung: 603 W • Z7`. The tooltip card erroneously clamped power zones to `1..5`, suppressing zones 6 and 7. The system must render zone suffixes across all 7 Coggan power zones (`Z1..Z7`) using `TelemetryZoneMath.ZONE_COLORS` (`TTColor.Zone1..Zone7`).

### 1.2 Functional & Architectural Requirements
1. **Full 7-Zone Power Rendering (`REQ-UI-242.1`)**:
   - In `ScrubbingTelemetryBadge` (`ElevationProfile.kt`), when `hasPower` is true and `powerZone` is resolved:
   - If `powerZone != null && powerZone in 1..TelemetryZoneMath.ZONE_COLORS.size` (1..7), the composable SHALL append ` • Z$powerZone` in `buildAnnotatedString` using `TelemetryZoneMath.ZONE_COLORS[powerZone - 1]` with `FontWeight.Bold`.
2. **Neutral Base Value & Fallback (`REQ-UI-242.2`)**:
   - The base numeric power value `${point.power} W` SHALL remain styled in `MaterialTheme.colorScheme.onSurface`.
   - If `powerZone` is null or outside `1..7`, only the base numeric value SHALL be rendered without trailing delimiters or miscolored text.
3. **Heart Rate 5-Zone Invariant (`REQ-UI-242.3`)**:
   - Heart Rate zone rendering SHALL remain strictly bounded to `1..5`.

### 1.3 Acceptance Criteria (Given-When-Then) & System Invariants
* **Criterion 1 (Z6/Z7 Suffix Rendering)**: *Given* an active workout scrubbed at point with power in Zone 6 or 7, *When* `ScrubbingTelemetryBadge` is composed, *Then* `• Z6` or `• Z7` is appended in bold with `TTColor.Zone6` or `TTColor.Zone7`.
* **Criterion 2 (Z1-Z5 Parity)**: *Given* power in Zones 1..5, *When* scrubbed, *Then* existing `• Z1`..`• Z5` coloring is unchanged.
* **Criterion 3 (Unconfigured Threshold Fallback)**: *Given* null power thresholds or power <= 0, *When* scrubbed, *Then* only `${point.power} W` is rendered in neutral `onSurface`.
* **Invariants**: Single-line badge rendering without baseline jitter, zero contrast regressions, zero schema changes.

---

## 2. Test Specification (TST-UI-201)

### Test Case 1: `testScrubbingTelemetryBadge_supportsAll7CogganPowerZones` (`[TST-UI-201.1]`)
* **Scope**: Contract / Unit Test (`ScrubbingBadgeZoneColorContractTest.kt`)
* **Preconditions**: `ElevationProfile.kt` source loaded.
* **Action**: Assert `content` does NOT contain `powerZone in 1..5`; assert `content` contains `powerZone in 1..TelemetryZoneMath.ZONE_COLORS.size` or `powerZone in 1..7`.
* **Expected Result**: Contract test passes asserting unconstrained 7-zone power support.

### Test Case 2: Clean-Room Regression Suite (`[TST-UI-201.2]`)
* **Command**: `./gradlew testDebugUnitTest` (full pass with zero regressions).

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-201.1]` | Contract | `ElevationProfile.kt` (`ScrubbingTelemetryBadge`) | `REQ-UI-242` | Specified |
| `[TST-UI-201.2]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-242` (*Training Zone Badge Suffix Rendering*). Targets `ElevationProfile.kt` (`ScrubbingTelemetryBadge`).
* **Historical Origin & Commit Trace**: Introduced in `ATT-2015` (Sprint 2026-40.12, commit `0064fba1`).
* **Root Reason for Existing Formulation**: When dynamic zone coloring was added to `ScrubbingTelemetryBadge`, the 5-zone range `in 1..5` was copied from Heart Rate to Power, inadvertently overlooking Coggan's 7-zone power scale.
* **Preservation of Core Invariants**: Retains neutral `onSurface` base text styling, dynamic zone coloring via `TelemetryZoneMath.ZONE_COLORS`, single-line layout, and 9-language localization parity. Expanding range to 1..7 restores visual parity with chart headers and zone distributions.
