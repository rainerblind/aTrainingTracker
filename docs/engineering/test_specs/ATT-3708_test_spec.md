# Stage 2 Requirement & Test Specification: ATT-3708

**Parent Ticket:** `ATT-3708` — *Scale live ZoneGaugeBar linearly with 10% boundary clipping, center active zone label beneath active segment, and use primary-blue needle*  
**Sprint:** `2026-42.6` (`sprint/2026-42.6`)  
**Requirement ID:** `REQ-UI-390` (amending `REQ-UI-376`)  
**Test Case ID:** `TST-UI-353`

---

## 1. Formal Requirement Specification (`REQ-UI-390`)

| ID | Title | Description | Summary | Code Entities | Tests | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **REQ-UI-390** | **Proportional Zone Segment Weights, 10% Boundary Clipping, Under-Segment Centered Active Badge & Primary-Blue Needle in Live ZoneGaugeBar.** | The system SHALL scale the live cockpit `ZoneGaugeBar` linearly across $[X_{\min}, X_{\max}]$ by sizing interior zone segments proportionally to their configured span $w_i = C_i - C_{i-1}$ and clipping the outer open-ended zones ($Z_1$ and $Z_K$) to a $10\%$ boundary span ($w_{\text{cap}} = \frac{10.0}{95.0} S_{\text{inner}}$), SHALL center the active zone badge (`"Z1"`..`"Z7"`) directly beneath the active zone segment using high-contrast theme text (`MaterialTheme.colorScheme.onSurface`), and SHALL render the needle indicator in theme Primary Blue (`MaterialTheme.colorScheme.primary`) with a contrasting halo outline (ATT-3708, amending `REQ-UI-376`):<br>1. *Unified Linear Domain $[X_{\min}, X_{\max}]$ & Proportional Segment Weights (`REQ-UI-390.1`)*:<br>• Given $K$ zones ($K = 7$ for Cycling Power, $K = 5$ for Heart Rate) and $K-1$ upper cutoffs $[C_1, \dots, C_{K-1}]$:<br>  - Interior closed zones $i \in \{2 \dots K-1\}$ SHALL have span $w_i = \max(1.0, (C_i - C_{i-1}).\text{toDouble}())$, with total interior span $S_{\text{inner}} = \sum_{i=2}^{K-1} w_i$.<br>  - Outer open-ended zones $Z_1$ and $Z_K$ SHALL be clipped to $w_1 = w_K = w_{\text{cap}} = \frac{10.0}{95.0} \times S_{\text{inner}}$ (so that for standard Coggan Power cutoffs $[55, 75, 90, 105, 120, 150]\%$ of FTP where $S_{\text{inner}} = 95\%\text{ FTP}$, $w_1 = w_7 = 10\%\text{ FTP}$ and total linear span $S_{\text{total}} = 115\%\text{ FTP}$).<br>  - The linear domain bounds SHALL be $X_{\min} = C_1 - w_{\text{cap}}$ and $X_{\max} = C_{K-1} + w_{\text{cap}}$, with normalized segment weights $\text{weight}_i = (w_i / S_{\text{total}}).\text{toFloat}()$ exposed via `ZoneGaugeMath.calculateSegmentWeights(cutoffs, isPower)` and stored in `ZoneGaugeState.segmentWeights`.<br>  - `ZoneGaugeMath.calculateNeedleFraction` SHALL compute strictly linear needle position across $[X_{\min}, X_{\max}]$: $\text{needleFraction} = ((v - X_{\min}) / (X_{\max} - X_{\min})).\text{coerceIn}(0.0, 1.0).\text{toFloat}()$, aligning with exact cumulative segment weights at every cutoff $C_i$ and clamping to $0.0\text{f}$ for $v \le X_{\min}$ and $1.0\text{f}$ for $v \ge X_{\max}$.<br>2. *Full-Width Weighted Track & Centered Under-Segment Active Zone Label (`REQ-UI-390.2`)*:<br>• `ZoneGaugeBar.kt` SHALL use an outer vertical `Column(modifier = modifier.fillMaxWidth())` so the horizontal segmented track always spans the full container width.<br>• Each zone segment `Box` in the track `Row` SHALL apply `Modifier.weight(state.segmentWeights.getOrElse(index) { 1f })`.<br>• When `showBadge && state.activeZoneIndex != null`, `ZoneGaugeBar` SHALL render a matching bottom `Row(modifier = Modifier.fillMaxWidth())` with $K$ slots using the identical `Modifier.weight(state.segmentWeights.getOrElse(index) { 1f })`, centering `Text(text = "Z${state.activeZoneIndex}", color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false, modifier = Modifier.testTag("zone_gauge_badge"))` directly beneath the active zone segment.<br>3. *High-Contrast Primary Blue Needle Indicator (`REQ-UI-390.3`)*:<br>• The needle indicator in `ZoneGaugeBar.kt` SHALL use a `4.dp` wide rounded rectangle filled with `MaterialTheme.colorScheme.primary` (instead of `Color.White`) surrounded by a contrasting halo outline (`TTColor.ZoneGaugeTrack` / `MaterialTheme.colorScheme.surface`), ensuring 100% hue separation against all 7 zone colors and immediate legibility on both Light and Dark/AMOLED tile backgrounds. | Proportional zone segment weights, 10% boundary clipping, under-segment centered active badge, and Primary Blue needle in live `ZoneGaugeBar`. | `ZoneGaugeMath.kt`, `ZoneGaugeState.kt`, `ZoneGaugeBar.kt` | `TST-UI-353` | Specified |

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Net-new requirement `REQ-UI-390`, amending `REQ-UI-376.2`, `REQ-UI-376.3`, and `REQ-UI-376.5` (`ATT-1293`). Targets `ZoneGaugeMath.kt`, `ZoneGaugeState.kt`, `ZoneGaugeBar.kt`, `ZoneGaugeMathTest.kt`, and `SensorFieldZoneGaugeTest.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint `2026-42.5` (`ATT-1293`, commits `5416c8b2`, `20e7e00d`, `9873a200`).
3. **Root Reason for Existing Formulation**:
   - `REQ-UI-376` originally used equal $1/K$ segment widths and piecewise-linear interpolation to avoid open-ended Z1/Z7 compression, placed the `"Z3"` badge to the right of the bar in `activeColor`, and used a `Color.White` needle fill. On-device review in Sprint `2026-42.5` revealed non-uniform needle velocity across zones, unreadable Z7 Black / Z4 Yellow badge colors, and needle camouflage on white Light Mode tiles.
4. **Preservation of Core Invariants**:
   - DataStore `showGauge` persistence (`REQ-UI-376.1`), stale/null dimmed state (`0.35f` alpha and hidden needle, `REQ-UI-376.4`), AMOLED `outlineVariant` track border (`REQ-UI-376.5`), $O(1)$ math performance (`REQ-UI-376.6`), and 9-locale parity (`REQ-UI-376.7`) remain 100% preserved.

---

## 3. Test Specification (`TST-UI-353`)

### 3.1 Mathematical Unit Tests (`ZoneGaugeMathTest.kt`)
1. `testCogganPower7Zones_proportionalSegmentWeights_match10_20_15_15_15_30_10`:
   - Given Coggan cutoffs `[110, 150, 180, 210, 240, 300]` (for $\text{FTP} = 200\text{ W}$, i.e., `[55%, 75%, 90%, 105%, 120%, 150%]`), verify `ZoneGaugeMath.calculateSegmentWeights(cutoffs, isPower = true)` returns `[10/115f, 20/115f, 15/115f, 15/115f, 15/115f, 30/115f, 10/115f]` within `1e-4f` and sums to `1.0f`.
2. `testStrictlyLinearNeedleVelocity_andExactCutoffCumulativeWeightAlignment`:
   - Verify that across both 5-zone HR (`[120, 140, 160, 180]`) and 7-zone Power (`[110, 150, 180, 210, 240, 300]`), `calculateNeedleFraction(C_i, cutoffs)` equals the exact cumulative sum of `segmentWeights.take(i).sum()` within `1e-4f`.
   - Verify constant linear derivative $\Delta \text{needleFraction} / \Delta \text{value}$ across all interior zones (e.g., +10W in Z4 produces the exact same $\Delta \text{needleFraction}$ as +10W in Z6).
3. `testBoundaryClipping_clampsCleanlyAtXMinAndXMax`:
   - Verify `calculateNeedleFraction(X_min, cutoffs)` and values $< X_{\min}$ return `0.0f`.
   - Verify `calculateNeedleFraction(X_max, cutoffs)` and values $> X_{\max}$ return `1.0f`.

### 3.2 Source & State Contract Tests (`SensorFieldZoneGaugeTest.kt`)
1. `testZoneGaugeBar_usesProportionalWeightsUnderSegmentBadgeAndPrimaryBlueNeedle`:
   - Inspect `ZoneGaugeBar.kt` to verify:
     - Outer container is `Column(` instead of horizontal `Row(` with right-side badge.
     - Track segments and under-segment badge row both apply `.weight(state.segmentWeights`.
     - Badge `Text` uses `color = MaterialTheme.colorScheme.onSurface`, `maxLines = 1`, `softWrap = false`, and `testTag("zone_gauge_badge")` inside a centered `Box`.
     - Needle draws using `MaterialTheme.colorScheme.primary` (captured before `Canvas`) and `4.dp` width instead of `Color.White`.

---

## 4. Traceability Matrix

| Requirement ID | Test Case ID | Test Class / Method | Verification Type |
| :--- | :--- | :--- | :--- |
| `REQ-UI-390.1` | `TST-UI-353` | `ZoneGaugeMathTest` | Unit Test (JVM) |
| `REQ-UI-390.2` | `TST-UI-353` | `SensorFieldZoneGaugeTest` | Source & State Contract Test |
| `REQ-UI-390.3` | `TST-UI-353` | `SensorFieldZoneGaugeTest` | Source & State Contract Test |
