# Stage 3: Implementation Plan - ATT-5070: [Bug] Power zone not shown

**Ticket**: [[ATT-5070]](https://rainerblind.atlassian.net/browse/ATT-5070)  
**Sub-task**: [[ATT-5099]](https://rainerblind.atlassian.net/browse/ATT-5099) (`[Impl-Plan]`)  
**Parent Epic**: None  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-UI-242` (`REQ-UI-242.1`..`REQ-UI-242.3`)  
**Test Mapping**: `TST-UI-201` (`TST-UI-201.1`, `TST-UI-201.2`)  
**Branch**: `feature/ATT-5070`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Problem Description & Background
In the scrubbing tooltip card on *Karte & Diagramme* (`ElevationProfile.kt`), power values in Coggan Zone 6 or 7 omit the zone suffix (`• Z7`), even though the Power chart header right below correctly shows `Leistung: 603 W • Z7`. The tooltip card erroneously clamped power zones to `1..5`, suppressing zones 6 and 7.

---

## 2. Traceability & Requirements Mapping
| Requirement | Summary | Target File / Composable | Test Mapping |
| :--- | :--- | :--- | :--- |
| `REQ-UI-242.1` | Full 7-zone Coggan power rendering | `ElevationProfile.kt` (`ScrubbingTelemetryBadge`) | `TST-UI-201.1` |
| `REQ-UI-242.2` | Neutral onSurface base power styling & fallback | `ElevationProfile.kt` (`ScrubbingTelemetryBadge`) | `TST-UI-201.1` |
| `REQ-UI-242.3` | HR 5-zone range invariant (`1..5`) | `ElevationProfile.kt` (`ScrubbingTelemetryBadge`) | `TST-UI-201.1` |
| `REQ-PRO-001` | Clean-room unit regression test suite | Full test suite | `TST-UI-201.2` |

---

## 3. System Invariants & Preserved Behavior
1. **Heart Rate 5-Zone Invariant**: HR zone checks remain strictly bounded to `1..5` (`TTColor.Zone1..Zone5`).
2. **Text Baseline & Contrast Parity**: Single-line layout in `buildAnnotatedString` with neutral `onSurface` base text and `FontWeight.Bold` zone suffixes.
3. **Array Bounds Safety**: `powerZone in 1..TelemetryZoneMath.ZONE_COLORS.size` guarantees safe indexing into `ZONE_COLORS[powerZone - 1]`.
4. **No Schema or Data Mutations**: Pure presentation composable refinement; zero database alterations.

---

## 4. Proposed Architectural Changes
* **Presentation Layer (`ElevationProfile.kt`)**:
  * Line 1137: update `if (powerZone != null && powerZone in 1..5)` to `if (powerZone != null && powerZone in 1..TelemetryZoneMath.ZONE_COLORS.size)`.
* **UI Consistency (Rule 23)**:
  * **Reference screen**: `ElevationProfile.kt` (`ScrubbingTelemetryBadge`) on *Karte & Diagramme*.
  * **Reused components**: `TelemetryZoneMath.ZONE_COLORS` (`TTColor.Zone1..Zone7`).
  * **Theme tokens**: `MaterialTheme.colorScheme.onSurface`, `FontWeight.Bold`, `FontWeight.SemiBold`.
  * **New one-off styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update Power Zone Range Check in `ElevationProfile.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfile.kt`
* **Change**: Expand `powerZone in 1..5` to `powerZone in 1..TelemetryZoneMath.ZONE_COLORS.size`.

### Step 2: Contract Test Verification in `ScrubbingBadgeZoneColorContractTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ScrubbingBadgeZoneColorContractTest.kt`
* **Change**: Add test asserting `ElevationProfile.kt` does not clamp power zones to `1..5` and contains `powerZone in 1..TelemetryZoneMath.ZONE_COLORS.size`.

### Step 3: Targeted Verification & Governance Check
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.map.ScrubbingBadgeZoneColorContractTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted tests during construction; Tier 1 suite in Stage 5.
* **Rollback**: `git checkout .` on branch `feature/ATT-5070`.
