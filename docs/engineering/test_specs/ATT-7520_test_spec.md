# Stage 2: Requirement & Test Specification - ATT-7520: 3-Zone HRV Gauge Bar Architecture

**Ticket**: [ATT-7520](https://rainerblind.atlassian.net/browse/ATT-7520)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.5`  
**Requirement Mapping**: `REQ-TRK-061.3` (*Semantic Aerobic Threshold Zone Indicators*)  
**Test Spec ID**: `TST-TRK-061.3` (`TST-TRK-061.3a`–`TST-TRK-061.3e`) | **Branch**: `feature/ATT-7520`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-13  

---

## 1. Requirement Specification (REQ-TRK-061.3)

### 1.1 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit

### 1.2 Functional & Architectural Requirements
1. `ZoneGaugeState` SHALL provide optional parameter `val zoneLabels: List<String>? = null` (default `null`). When `zoneLabels != null`, `ZoneGaugeBar` SHALL render the active zone badge using `zoneLabels.getOrNull(index) ?: "Z${state.activeZoneIndex}"`, decoupling badge text from 1-based index (`REQ-TRK-061.3a`, `REQ-UI-376`).
2. `HrvZoneIndicator.calculateZoneGaugeState(alpha1, t1, t2)` SHALL calculate and return a 3-segment `ZoneGaugeState` representing the 3 physiological zones of the HRV DFA $\alpha_1$ model:
   - `segmentColors = listOf(TTColor.Zone2, TTColor.Zone3, TTColor.Zone4)` (`REQ-TRK-061.3b`).
   - `zoneLabels = listOf("Z2", "Z3", "Z4")` (`REQ-TRK-061.3c`).
3. `segmentWeights` in `HrvZoneIndicator.calculateZoneGaugeState` SHALL be dynamically computed proportionally to the spans of the 3 physiological zones: $S_1 = 1.20f - t1$, $S_2 = t1 - t2$, $S_3 = t2 - 0.20f$, normalized to sum to $1.0f$ (`REQ-TRK-061.3d`).
4. `needleFraction` SHALL map $\alpha_1 \in [0.20, 1.20]$ monotonically across the 3 segments (Zone 2 left, Zone 3 middle, Zone 4 right) with needle clamped within $[0.05f, 0.95f]$, and `activeZoneIndex` SHALL resolve to 1 ($> t1$, Z2), 2 ($t2..t1$, Z3), or 3 ($< t2$, Z4) (`REQ-TRK-061.3e`).
5. When $\alpha_1$ is null, NaN, infinite, or negative, `calculateZoneGaugeState` SHALL return `null` (`REQ-TRK-061.3f`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC1 (Aerobic Base Zone 2)**: Given $\alpha_1 = 0.85$ ($T_1 = 0.75, T_2 = 0.50$), When `calculateZoneGaugeState` is called, Then `segmentColors.size == 3`, `activeZoneIndex == 1`, `needleFraction` is in Segment 0, and badge label resolves to `"Z2"`.
* **AC2 (Threshold Transition Zone 3)**: Given $\alpha_1 = 0.65$, When `calculateZoneGaugeState` is called, Then `activeZoneIndex == 2`, `needleFraction` is in Segment 1, and badge label resolves to `"Z3"`.
* **AC3 (Anaerobic Zone 4)**: Given $\alpha_1 = 0.40$, When `calculateZoneGaugeState` is called, Then `activeZoneIndex == 3`, `needleFraction` is in Segment 2, and badge label resolves to `"Z4"`.
* **AC4 (Invalid / Null Input)**: Given null, negative, or NaN $\alpha_1$, When `calculateZoneGaugeState` is called, Then it returns `null`.
* **AC5 (HR & Power Gauge Parity)**: Given HR (5-zone) and Power (7-zone) gauges with `zoneLabels == null`, When `ZoneGaugeBar` renders, Then existing `"Z1"`..`"Z5"` and `"Z1"`..`"Z7"` badges remain 100% backward-compatible.

### 1.4 System Invariants
- 5-zone HR and 7-zone Power gauge calculations and badge rendering 100% preserved.
- AMOLED contrast and Rule 71 theme tokens strictly preserved.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Amends `REQ-TRK-061.3` and `REQ-UI-376` (`HrvZoneIndicator.kt`, `ZoneGaugeState.kt`, `ZoneGaugeBar.kt`).
* **Historical Origin & Commit Trace**: Introduced in `ATT-3542` (commit `14292b35206`), refined in `ATT-7317`.
* **Root Reason for Existing Formulation**: `ZoneGaugeBar` originally catered only to 5-zone HR and 7-zone Power; `HrvZoneIndicator` temporarily mapped HRV onto the 5-zone HR color palette as an MVP workaround, leaving dummy segments Z1 and Z5 visible.
* **Preservation of Core Invariants**: 5-zone HR and 7-zone Power models remain 100% untouched; `ZoneGaugeState` defaults `zoneLabels` to null preserving `"Z${activeZoneIndex}"` fallback; clean-room test pass rate strictly preserved.

---

## 3. Test Specification (TST-TRK-061.3)

### Test Case 1: 3-Zone HRV Segment Colors & Labels Contract (`TST-TRK-061.3a`)
* **Scope**: Unit (`TrackingHrvZoneIndicatorTest.kt`)
* **Verification**: Verify `calculateZoneGaugeState` returns `segmentColors = listOf(TTColor.Zone2, TTColor.Zone3, TTColor.Zone4)` and `zoneLabels = listOf("Z2", "Z3", "Z4")`.

### Test Case 2: Proportional Segment Weights & Needle Math (`TST-TRK-061.3b`)
* **Scope**: Unit (`TrackingHrvZoneIndicatorTest.kt`)
* **Verification**: Verify `segmentWeights` dynamically reflect spans $(1.20 - T_1)$, $(T_1 - T_2)$, and $(T_2 - 0.20)$ summing to $1.0f$; verify continuous monotonic needle interpolation across all zones.

### Test Case 3: Active Zone Mapping & Edge Clamping (`TST-TRK-061.3c`)
* **Scope**: Unit (`TrackingHrvZoneIndicatorTest.kt`)
* **Verification**: Verify activeZoneIndex 1 for $\alpha_1 > T_1$, 2 for $T_2 \le \alpha_1 \le T_1$, 3 for $\alpha_1 < T_2$; verify null for negative, NaN, or null $\alpha_1$.

### Test Case 4: ZoneGaugeBar Custom Labels & Backward Compatibility (`TST-TRK-061.3d`)
* **Scope**: Unit (`SensorFieldZoneGaugeTest.kt`)
* **Verification**: Verify `ZoneGaugeBar` renders custom labels when `zoneLabels != null` and falls back to `"Z$activeZoneIndex"` when `zoneLabels == null`.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-TRK-061.3e`)
* **Scope**: Integration / Unit
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"`

---

## 4. Traceability Matrix

| Test Case | Scope | Target File | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-061.3a` | Unit | `TrackingHrvZoneIndicatorTest.kt` | `REQ-TRK-061.3b`, `REQ-TRK-061.3c` | Specified |
| `TST-TRK-061.3b` | Unit | `TrackingHrvZoneIndicatorTest.kt` | `REQ-TRK-061.3d` | Specified |
| `TST-TRK-061.3c` | Unit | `TrackingHrvZoneIndicatorTest.kt` | `REQ-TRK-061.3e`, `REQ-TRK-061.3f` | Specified |
| `TST-TRK-061.3d` | Unit | `SensorFieldZoneGaugeTest.kt` | `REQ-TRK-061.3a` | Specified |
| `TST-TRK-061.3e` | Tier 1 Regr | Targeted test suites | `REQ-PRO-001` | Specified |
