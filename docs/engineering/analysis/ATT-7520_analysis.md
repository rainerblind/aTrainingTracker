# Stage 1 Analysis: ATT-7520 - HrvZoneIndicator & ZoneGaugeBar: Render 3 HRV Zones in SensorFieldView Gauge Bar

**Ticket**: [ATT-7520](https://rainerblind.atlassian.net/browse/ATT-7520)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.5`  
**Branch**: `feature/ATT-7520` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Statement & Forensic Root Cause Analysis
* **Observed Defect**: On-device review of the *Trainings Zonen -> HRV (DFA α1)* preview and live tracking cockpit (`SensorFieldView`) reveals that the horizontal zone gauge bar renders the 5 HR segments (Light Green Z1, Dark Green Z2, Orange Z3, Red Z4, Purple Z5) instead of the 3 physiological zones of the HRV DFA α1 model (`hrv_zones_bottom.png`).
* **Root Cause**: In `HrvZoneIndicator.kt` (lines 95–99):
  `calculateZoneGaugeState()` hardcodes `segmentColors = ZoneGaugeMath.HR_SEGMENT_COLORS` (5 segments) and offsets `activeZoneIndex` into 2, 3, 4, leaving dummy unused segments Z1 and Z5 visible on the bar. Furthermore, `ZoneGaugeBar.kt` hardcodes badge formatting to `"Z${state.activeZoneIndex}"`, preventing arbitrary or subset zone numbering.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Amends `REQ-TRK-061.3` (Live HRV threshold zone indicators in cockpit & preview) and `REQ-UI-376` (`ZoneGaugeBar`).
* **Historical Origin & Commit Trace**: Introduced in `ATT-3542` and `ATT-7317`.
* **Root Reason for Existing Formulation**: `ZoneGaugeBar` originally supported only 5-zone HR and 7-zone Power. Rather than generalizing `ZoneGaugeState` for 3-segment models, `HrvZoneIndicator` temporarily mapped HRV onto the 5-zone HR color palette as an MVP workaround.
* **Preservation of Core Invariants**: 5-zone HR and 7-zone Power models remain 100% untouched. True-black contrast and Rule 71 theme tokens (`TTColor.Zone2`, `TTColor.Zone3`, `TTColor.Zone4`) are strictly preserved.

---

## 3. Scope Bounding & Proposed Architecture (ATT-1250)
* **In-Scope Goals (2 SP - Minor)**:
  1. Add optional `val zoneLabels: List<String>? = null` to `ZoneGaugeState` (default `null` preserving `"Z${activeZoneIndex}"` for HR/Power).
  2. In `ZoneGaugeBar.kt`, resolve under-segment active badge text via `state.zoneLabels?.getOrNull(index) ?: "Z${state.activeZoneIndex}"`.
  3. In `HrvZoneIndicator.kt`:
     - Define `HRV_SEGMENT_COLORS = listOf(TTColor.Zone2, TTColor.Zone3, TTColor.Zone4)` and `HRV_ZONE_LABELS = listOf("Z2", "Z3", "Z4")`.
     - Calculate dynamic proportional `segmentWeights` based on threshold spans: $S_1 = 1.20 - T_1$, $S_2 = T_1 - T_2$, $S_3 = T_2 - 0.20$.
     - Map continuous $\alpha_1 \in [0.20, 1.20]$ to strictly monotonic linear `needleFraction` across the 3 segments (Green left, Orange middle, Red right).
     - Set `activeZoneIndex` to 1 ($> T_1$, Z2), 2 ($T_2..T_1$, Z3), 3 ($< T_2$, Z4).
  4. Update `TrackingHrvZoneIndicatorTest.kt` to assert 3-segment structure, proportional weights, and correct badge labels.
* **Out-of-Scope Non-Goals**:
  * No changes to HR (5-zone) or Power (7-zone) gauge calculations in `ZoneGaugeMath.kt`.
  * No modifications to DataStore schema or threshold defaults.

---

## 4. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-TRK-061.3`, `REQ-UI-376`.
* **Affected Components**: `HrvZoneIndicator.kt`, `ZoneGaugeState.kt`, `ZoneGaugeBar.kt`.
* **Verification Impact**: `TrackingHrvZoneIndicatorTest.kt`, `SensorFieldZoneGaugeTest.kt`, `ThemeColorCentralizationAuditTest`.
* **Risk Assessment**: Low risk. Isolated UI state model enhancement with complete backward compatibility.

---

## 5. Living Documentation Plan
* Refine `REQ-TRK-061.3` in `docs/requirements/req_tracking_cockpit.md` and `TST-TRK-061.3` in `docs/tests/tst_tracking_cockpit.md` to specify the 3-segment HRV gauge bar architecture.
