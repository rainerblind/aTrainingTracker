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

During high-intensity cardiovascular efforts (such as VO2 max cycling intervals or threshold running hill repeats), an athlete's cognitive capacity and visual acuity degrade significantly due to physical fatigue, vibration, and rapid movement. Reading small numerical digits (e.g. `168 bpm` or `315 W`) requires excessive focal concentration and mental translation against memorized zone boundaries.

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

The visual zone gauge dynamically adapts its segmented track:
- When displaying Heart Rate: Renders exactly 5 contiguous zone segments.
- When displaying Cycling Power: Renders exactly 7 contiguous zone segments.

### 2.2 Mathematical Cursor / Needle Positioning & Open-Ended Top Zone
A visual zone gauge maps the instantaneous scalar reading $v$ into a normalized horizontal position $x \in [0.0, 1.0]$ across the gauge width:
1. **Segment Geometry & Equal Visual Width UX Justification**:
   - The bar is partitioned into $K$ equal-width segments, where $K = 5$ for HR and $K = 7$ for Power. Each zone $i \in \{1 \dots K\}$ occupies a horizontal slice $[x_{i-1}, x_i] = [\frac{i-1}{K}, \frac{i}{K}]$.
   - **UX Justification**: In cycling and running exercise physiology, numerical zone spans vary widely (e.g. Zone 1 might span 0–150W, Zone 5 spans 35W, and Zone 7 is open-ended). If segments were sized strictly proportional to raw Watt/bpm ranges, high-intensity target zones (Z4 Sweetspot, Z5 VO2max) would shrink to narrow, unreadable slivers on compact cockpit tiles, impairing interval pacing. Equal visual width per zone guarantees that each training intensity band offers uniform visual resolution and unambiguous needle target tracking at high exertion.
2. **Intra-Zone Interpolation**:
   - For closed interior zone $i \in \{1 \dots K-1\}$ with bounds $[L_i, U_i]$ (where $L_1 = 0$ for Power and $L_1 = 30$ for HR):
     $$\text{fraction}_i = \frac{v - L_i}{U_i - L_i} \in [0.0, 1.0]$$
     $$x_{\text{needle}} = \frac{(i - 1) + \text{fraction}_i}{K}$$
3. **Open-Ended Top Zone Dynamic Ceiling (Zone 5 for HR, Zone 7 for Power)**:
   - Rather than relying on an arbitrary static multiplier, the top zone defines a dynamic, physiologically grounded ceiling $U_{\text{top}}$:
     - **For HR Zone 5**: Lower bound $L_5 = Z_{4\text{max}}$. Nominal ceiling $U_5 = \max(H\!R_{\max}, Z_{4\text{max}} + 25\text{ bpm})$.
     - **For Power Zone 7**: Lower bound $L_7 = Z_{6\text{max}}$. Nominal ceiling is defined dynamically:
       $$U_7 = \min(2500\text{ W}, \max(Z_{6\text{max}} + 300\text{ W}, \text{FTP} \times 2.0))$$
       where $2500\text{ W}$ is the strict physical maximum ceiling established in `REQ-UI-373.1`.
     - Inside top zone ($v \ge L_{\text{top}}$):
       $$\text{fraction}_{\text{top}} = \min\left(1.0, \frac{v - L_{\text{top}}}{U_{\text{top}} - L_{\text{top}}}\right)$$
       $$x_{\text{needle}} = \frac{(K - 1) + \text{fraction}_{\text{top}}}{K}$$
     - If an extreme sprint exceeds $U_{\text{top}}$, the needle cleanly clamps to $x = 1.0$ without overflow.
4. **Telemetry Stale / Disconnected / Null State**:
   - When telemetry is absent, disconnected, unconfigured, or stale (`v == null`, `v.isNaN()`, `!v.isFinite()`, or stale packet TTL > 5s):
     - The needle is completely **hidden** (or rendered with 0.0 alpha), avoiding confusing ghost pointers.
     - The segmented zone track remains rendered with dimmed baseline opacity (alpha 0.35), clearly communicating available zone bands without active telemetry.

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
   - When both `showBackground` and `showGauge` are enabled simultaneously, the gauge maintains high contrast: the gauge container uses a subtle dark/light frame `outlineVariant` with solid inner zone fills, preventing background tint washout.
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

## 4. Architectural Risk Assessment & Non-Functional Constraints

1. **Rendering Performance & Frame Budget (60 FPS)**:
   - Power sensors (especially BLE power meters) can emit updates at 2Hz–4Hz.
   - In `TrackingViewModel.kt`, UI state updates are already decoupled: `SensorFieldState` updates only when formatted value or zone color/needle changes.
   - In Compose, `ZoneGaugeBar` consumes primitive `Float` needle progress (`needleFraction`), drawing directly via `Modifier.drawWithContent` or Canvas without triggering subtree recompositions or layout passes, comfortably satisfying the 16ms / 60 FPS frame budget.
2. **AMOLED Contrast & WCAG 2.1 AA Compliance**:
   - In dark and True-Black AMOLED modes (`#000000` background):
     - Zone 7 is `#000000` (Pure Black) and Zone 6 is `#4E342E` (Dark Brown).
     - To ensure non-text contrast ($\ge 3:1$ per WCAG 2.1 AA), the outer track and segment dividers are stroked with `MaterialTheme.colorScheme.outlineVariant` (`#2B3342` / `0xFF2B3342`), creating distinct visible boundaries.
     - The needle is rendered in pure white (`Color.White` `#FFFFFF`) with a subtle 1dp dark shadow/border, achieving $> 12:1$ contrast against any zone segment color.
3. **Layout Height Constraints in Compact Cockpit Tiles**:
   - In compact sensor tiles (`ViewSize.XSMALL`, `ViewSize.SMALL`), vertical space is constrained.
   - `ZoneGaugeBar` track height is set to `6.dp` with `2.dp` needle overhang, fitting seamlessly into the tile layout without displacing metric values or causing vertical clipping.
