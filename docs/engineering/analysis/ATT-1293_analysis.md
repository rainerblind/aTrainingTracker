# Stage 1 Analysis: ATT-1293 - Visual zone gauge tile across training zones in cockpit

**Ticket**: [ATT-1293](https://rainerblind.atlassian.net/browse/ATT-1293)  
**Sub-task**: [ATT-3645](https://rainerblind.atlassian.net/browse/ATT-3645) (`[Analysis]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*UI Improvements & Visual Modernization*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Branch**: `feature/ATT-1293`  
**Requirement Mapping**: `REQ-UI-376` (extending `REQ-UI-172`, `REQ-UI-370`, `REQ-UI-372`, `REQ-UI-373`, and `REQ-UI-374`)  
**Test Mapping**: `TST-UI-339`  
**Author**: AI Agent 1 (Implementer)  
**Reviewer / Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-15  

---

## 1. Problem Statement & Motivation

During high-intensity cardiovascular efforts (such as VO2 max cycling intervals or threshold running hill repeats), an athlete's cognitive capacity and visual acuity degrade significantly due to physical fatigue and rapid movement. Reading small numerical digits (e.g. `168 bpm` or `315 W`) requires excessive focal concentration and mental translation against memorized zone boundaries.

To provide instantaneous peripheral comprehension, modern cycling computers and head units (Garmin Edge, Wahoo ELEMNT, Hammerhead Karoo) feature **visual zone gauge bars**. With a single glance, athletes can immediately determine:
1. Which physiological zone they are currently training in (via established semantic zone colors).
2. Their relative position within that zone (e.g. at the bottom, middle, or top of Zone 4 threshold).
3. The exact numerical telemetry value and active zone label (`Z1`..`Z5` for Heart Rate, `Z1`..`Z7` for Power).

Currently, `aTrainingTracker` supports:
- Discrete background tinting (12% alpha) and edge strips (6dp) via `ZoneDisplayOptions` (REQ-UI-172).
- Decoupled 5-zone HR scheme and 7-zone Coggan cycling power model (REQ-UI-370, ATT-3184).
- Automated boundary calculations from FTP and HR baselines (REQ-UI-373, ATT-3188).
- Horizontal stacked distribution bar in post-workout cards (REQ-UI-372, ATT-3390).

However, `SensorFieldView.kt` in the live tracking cockpit lacks an integrated, real-time **horizontal visual zone gauge** that dynamically renders the physiological spectrum and exact position needle.

---

## 2. Core Requirements & Component Analysis

### 2.1 Model Decoupling & Dynamic Zone Count
In Sprint 2026-42.4, ATT-3184 migrated cycling power to the Hunter Allen & Dr. Andrew Coggan 7-zone model while strictly preserving the 5-zone scheme for running/cycling heart rate:
- **Heart Rate (`HR_RUN`, `HR_BIKE`)**: Exactly 5 zones ($Z_1 \dots Z_5$), partitioned by 4 upper boundaries ($Z_{1\text{max}}, Z_{2\text{max}}, Z_{3\text{max}}, Z_{4\text{max}}$). Colors: `TTColor.Zone1` (`#7FFF00`), `TTColor.Zone2` (`#008000`), `TTColor.Zone3` (`#FFA500`), `TTColor.Zone4` (`#FF0000`), `TTColor.Zone5` (`#9400D3`).
- **Cycling Power (`PWR_BIKE`)**: Exactly 7 zones ($Z_1 \dots Z_7$), partitioned by 6 upper boundaries ($Z_{1\text{max}} \dots Z_{6\text{max}}$). Colors: `TTColor.Zone1..Zone5` plus `TTColor.Zone6` (`#4E342E` Dark Chestnut Brown) and `TTColor.Zone7` (`#000000` Pure Black).

The visual zone gauge must dynamically adapt its segmented track:
- When displaying Heart Rate: Renders exactly 5 contiguous zone segments.
- When displaying Cycling Power: Renders exactly 7 contiguous zone segments.

### 2.2 Mathematical Cursor / Needle Positioning
A visual zone gauge maps the instantaneous scalar reading $v$ into a normalized horizontal position $x \in [0.0, 1.0]$ across the gauge width:
1. **Segment Geometry**:
   - The bar is partitioned into $K$ equal-width segments, where $K = 5$ for HR and $K = 7$ for Power. Each zone $i \in \{1 \dots K\}$ occupies a horizontal slice $[x_{i-1}, x_i] = [\frac{i-1}{K}, \frac{i}{K}]$.
   - Alternatively, equal visual width per zone is optimal for peripheral recognition because physiological zones vary widely in numerical span (e.g. Zone 1 is large, Zone 5/6 narrow, Zone 7 unbounded). Equal width per zone ensures that each training zone has equal visual resolution and clear readability on screen.
2. **Intra-Zone Interpolation**:
   - For zone $i$ with bounds $[L_i, U_i]$:
     $$\text{fraction}_i = \frac{v - L_i}{U_i - L_i} \in [0.0, 1.0]$$
     $$x_{\text{needle}} = \frac{(i - 1) + \text{fraction}_i}{K}$$
   - **Boundary Conditions & Invariants**:
     - Below Zone 1 ($v \le L_1$): Clamped to $x = 0.0$ (needle rests at left edge of Z1).
     - Open-ended top zone (Zone 5 for HR, Zone 7 for Power):
       - For HR Zone 5, $L_5 = Z_{4\text{max}}$, nominal ceiling $U_5 = \max(H\!R_{\max}, Z_{4\text{max}} + 30)$.
       - For Power Zone 7, $L_7 = Z_{6\text{max}}$, nominal ceiling $U_7 = \min(2500, \max(Z_{6\text{max}} \times 1.5, Z_{6\text{max}} + 200))$.
       - Beyond nominal ceiling ($v \ge U_K$): Clamped to $x = 1.0$ (needle rests at right edge).
     - Undefined or unconfigured metrics ($v = \text{null}$, unconfigured FTP/zones): Needle is omitted or hidden; gauge displays neutral segmented track.

### 2.3 User Preferences & Presentation Options
In `ZoneDisplayOptions.kt` and `ZonesSettingsActivity.kt`, athletes configure how zones are highlighted:
- `showBackground: Boolean` (12% alpha tint)
- `showLeftBar: Boolean` (6dp left strip)
- `showRightBar: Boolean` (6dp right strip)
- `showTextColor: Boolean` (metric text colored)
- **New Option**: `showGauge: Boolean = false` (default false for backward compatibility, enabled per zone profile).
When `showGauge == true` and the field is `HR` or `POWER`:
- `SensorFieldView` renders a sleek, high-visibility horizontal segmented progress bar (`ZoneGaugeBar`) directly below the value/units row or integrated into the card footer.
- The needle / cursor is rendered with a high-contrast inverted marker (pure white with dark drop-shadow in dark/AMOLED theme, dark slate in light theme) with a crisp vertical indicator line or triangle pointer.
- Next to or above the gauge, a compact zone badge (`Z1`..`Z7`) rendered in the active zone color provides instant symbolic affirmation.

---

## 3. Scope & Chesterton's Fence Archaeology

### 3.1 Requirement Archaeology (`REQ-PRO-022`)
1. **Original Requirement ID & Target**:
   - Extends `REQ-UI-172` (*Configurable Zone Visual Presentation: Background, Left Bar, Right Bar, Text Color*).
   - Complements `REQ-UI-370` (*7-Zone Coggan Cycling Power Model*), `REQ-UI-372` (*Horizontal Stacked Bar*), `REQ-UI-373` (*Automated Zone Boundaries*), and `REQ-UI-374` (*Contextual Zone Settings Navigation*).
   - Targets `ZoneDisplayOptions.kt`, `SensorFieldState.kt`, `SensorFieldView.kt`, `ZonesSettingsActivity.kt`, `SettingsDataStore.kt`, and `TrackingViewModel.kt`.
2. **Historical Origin & Commit Trace**:
   - `REQ-UI-172` introduced in Sprint 2026-40.5 (`ATT-1390`, commit `e3966ebf`).
   - `ZoneDisplayOptions` persisted in DataStore with 4 boolean flags: `showBackground`, `showLeftBar`, `showRightBar`, `showTextColor`.
3. **Root Reason for Existing Formulation**:
   - `REQ-UI-172` provided binary full-card tinting and edge borders. Athletes noted that while background tinting indicates *which* zone is active, it does not reveal *where* within the zone the athlete is riding (e.g. sweet-spot vs redlining).
4. **Preservation of Core Invariants**:
   - All 4 existing `ZoneDisplayOptions` flags (`showBackground`, `showLeftBar`, `showRightBar`, `showTextColor`) MUST continue to work with 100% backward compatibility.
   - True-Black AMOLED Cockpit background compatibility (REQ-UI-190, REQ-UI-220) MUST be strictly preserved.
   - Battery Saver / Low CPU overhead: The gauge calculation must be pure lightweight math ($O(1)$) without heavy allocations or layout churn.

### 3.2 In-Scope vs. Out-of-Scope (ATT-1250)
- **In-Scope**:
  - Add `showGauge: Boolean = false` to `ZoneDisplayOptions` with DataStore persistence.
  - Implement `ZoneGaugeMath.kt` with pure algorithmic unit tests for intra-zone fraction, needle position clamping, 5-zone HR, and 7-zone Power.
  - Implement `ZoneGaugeBar.kt` composable component rendering horizontal segmented track, zone colors, boundary dividers, active zone badge, and high-visibility cursor needle.
  - Integrate `ZoneGaugeBar` into `SensorFieldView.kt` when `showGauge == true` for `HR` and `POWER`.
  - Add toggle for `showGauge` in `ZonesSettingsActivity.kt` under "Zonendarstellung / Zone Visualization" with live preview.
  - Add localized strings across all 9 supported languages.
- **Out-of-Scope**:
  - Circular / arc speedometer gauges (unsuited for dense rectangular cockpit grid tiles; horizontal segmented bar fits rectangular sensor tiles cleanly across all sizes).
  - Modifying non-zone sensors (Speed, Cadence, Distance, Ascent do not have physiological zones).

---

## 4. Architectural Risk Assessment
- **Layout Overflow in Compact Tiles**: In small tiles (`ViewSize.XSMALL`, `ViewSize.SMALL`), vertical height is limited. The gauge height should be compact (4dp to 6dp track height, 2dp needle) and adapt cleanly without clipping label or value text.
- **AMOLED Contrast for Dark Zones**: Zone 7 is `#000000` (Pure Black) and Zone 6 is `#4E342E` (Dark Brown). On True-Black AMOLED backgrounds, an outline border (`outlineVariant`) around the gauge track and segment dividers is required to keep Zone 6 and 7 distinct and visible.
