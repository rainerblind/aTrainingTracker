# Stage 1 Problem Domain & Root Cause Analysis: ATT-3708

**Parent Ticket:** `ATT-3708` — *Scale live ZoneGaugeBar linearly with 10% boundary clipping, center active zone label beneath active segment, and use primary-blue needle*  
**Sprint:** `2026-42.6` (`sprint/2026-42.6`)  
**Target Requirement:** `REQ-UI-390` (amending `REQ-UI-376`)  
**Target Test Case:** `TST-UI-353`

---

## 1. Forensic Root Cause Analysis (RCA)

Following on-device inspection of the live cockpit zone gauge (`ZoneGaugeBar.kt`, `ZoneGaugeMath.kt`, `ZoneGaugeState.kt`, introduced in `ATT-1293` / `REQ-UI-376`), three mathematical and visual defects were identified:

1. **Distorted Segment Proportions & Piecewise Non-Uniform Needle Velocity (`ZoneGaugeMath.kt` & `ZoneGaugeBar.kt:120`)**:
   - Currently, `ZoneGaugeBar.kt:120` assigns `Modifier.weight(1f)` to all $K$ segments ($1/7$ each for 7-zone Power, $1/5$ each for 5-zone Heart Rate), while `ZoneGaugeMath.calculateNeedleFraction` uses piecewise-linear interpolation within each zone and scales by `segmentWidth = 1.0 / numZones`.
   - In the standard 7-zone Coggan model (`[55%, 75%, 90%, 105%, 120%, 150%]` of FTP), interior spans are $w_2 = 20\%$, $w_3 = w_4 = w_5 = 15\%$, and $w_6 = 30\%$ of FTP (total interior span $S_{\text{inner}} = C_6 - C_1 = 95\%\text{ FTP}$). Because all 7 visual segments are forced to equal pixel width ($1/7 \approx 14.3\%$), the needle moves **twice as fast per Watt** in Z3/Z4/Z5 ($15\%\text{ FTP}$) as in Z6 ($30\%\text{ FTP}$), abruptly changing velocity at every zone boundary, and customized zone widths are not reflected visually.
2. **Misplaced Active Zone Label (`ZoneGaugeBar.kt:90, 174-187`)**:
   - Currently, `ZoneGaugeBar` wraps the bar and the `"Z${state.activeZoneIndex}"` badge in a horizontal `Row` (`ZoneGaugeBar.kt:90-188`), placing the badge to the right of Z7 (`ZoneGaugeBar.kt:174-187`).
   - This positions the active zone label next to Z7 even when riding in Z1 or Z2 on the far left, steals horizontal track width from the gauge bar, and colors the label text with `activeColor` (which makes Z7 `#000000` Black invisible in Dark/AMOLED mode and Z4 `#FFFF00` Yellow low-contrast in Light mode).
3. **Poor Needle Legibility & Hue Collision with Card Background (`ZoneGaugeBar.kt:139-168`)**:
   - Currently, the needle uses a `3.dp` `Color.White` fill (`ZoneGaugeBar.kt:163-168`).
   - In Light Mode, the sensor tile surface (`MaterialTheme.colorScheme.surface`) and the `1.dp` inter-zone dividers (`ZoneGaugeBar.kt:129`) are also white, causing the top/bottom needle overhang (`2.dp` above and below the track) to blend into the card background and resemble an inter-zone divider. Furthermore, white has weak contrast against bright zones like Z1 (`#7FFF00` Chartreuse) and Z4 (`#FFFF00` Yellow).
   - Because **none of the 7 zone colors is Blue**, rendering a `4.dp` needle filled with `MaterialTheme.colorScheme.primary` (vibrant Royal Blue `#1464F4` in Light Mode, bright Sky Blue `#A6C8FF` in Dark/AMOLED Mode) with a contrasting halo outline guarantees 100% hue separation against all 7 zone colors and immediate visibility against both light and dark/AMOLED card backgrounds.

---

## 2. Chesterton's Fence Requirement Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   - Amends `REQ-UI-376` (*Real-Time Visual Training Zone Gauge Cockpit Integration*, `ATT-1293`). Targets `ZoneGaugeMath.kt`, `ZoneGaugeState.kt`, `ZoneGaugeBar.kt`, `ZoneGaugeMathTest.kt`, and `SensorFieldZoneGaugeTest.kt`. Formally tracked as `REQ-UI-390`.
2. **Historical Origin & Commit Trace**:
   - Sprint `2026-42.5` (`ATT-1293`, commits `5416c8b2`, `20e7e00d`, `9873a200`): Introduced `ZoneGaugeMath`, `ZoneGaugeState`, and `ZoneGaugeBar`.
3. **Root Reason for Existing Formulation**:
   - `REQ-UI-376.2` and `REQ-UI-376.3` initially used equal $1/K$ segment widths and piecewise-linear interpolation to prevent open-ended Z1 (`[0, Z1_max]`) and Z7 (`[Z6_max, 2500W]`) from dominating the bar width and squishing interior zones Z2–Z6 into tiny slivers.
   - However, during Sprint `2026-42.5` Joint Review (`ATT-3708`), on-device testing showed that clipping ONLY the two outer open-ended zones (Z1 and $Z_K$) to a compact `10%` boundary span on each side while sizing all interior closed zones ($Z_2 \dots Z_{K-1}$) proportionally to their true span $w_i = C_i - C_{i-1}$ solves the open-ended compression problem **and** makes the entire gauge strictly linear ($dx/d\text{Watt} = \text{const}$) across $[X_{\min}, X_{\max}]$.
4. **Preservation of Core Invariants**:
   - `ZoneDisplayOptions.showGauge` DataStore persistence (`REQ-UI-376.1`), stale/null telemetry dimming (`0.35f` track alpha and hidden needle, `REQ-UI-376.4`), AMOLED `outlineVariant` track border (`REQ-UI-376.5`), $O(1)$ math performance (`REQ-UI-376.6`), and 9-language localization (`REQ-UI-376.7`) remain 100% preserved.

---

## 3. Mathematical Formulation & Technical Scope

### 3.1 Unified Linear Domain $[X_{\min}, X_{\max}]$ & Proportional Segment Weights (`ZoneGaugeMath.kt`)
Given $K$ zones ($K = 7$ for Power, $K = 5$ for Heart Rate) and $K - 1$ upper cutoffs $[C_1, C_2, \dots, C_{K-1}]$:
- Interior closed zones $i \in \{2 \dots K-1\}$ have exact span:
  $$w_i = \max(1.0, (C_i - C_{i-1}).\text{toDouble}())$$
- Total interior span across closed zones $Z_2 \dots Z_{K-1}$:
  $$S_{\text{inner}} = \sum_{i=2}^{K-1} w_i = \max((K - 2).\text{toDouble}(), (C_{K-1} - C_1).\text{toDouble}())$$
- **10% Boundary Clipping for Outer Zones ($Z_1$ and $Z_K$)**:
  - In standard 7-zone Coggan Power (`[55%, 75%, 90%, 105%, 120%, 150%]` of FTP), $S_{\text{inner}} = 150\% - 55\% = 95\%\text{ FTP}$, and a $10\%\text{ FTP}$ cap on $Z_1$ and $Z_7$ equals:
    $$w_{\text{cap}} = \frac{10.0}{95.0} \times S_{\text{inner}}$$
  - Using $w_1 = w_K = w_{\text{cap}} = \frac{10.0}{95.0} \times S_{\text{inner}}$ generalizes identically to any FTP, custom power cutoffs, and 5-zone Heart Rate cutoffs without requiring a separate FTP/HRmax parameter:
    - $X_{\min} = C_1 - w_{\text{cap}}$
    - $X_{\max} = C_{K-1} + w_{\text{cap}}$
    - $S_{\text{total}} = X_{\max} - X_{\min} = S_{\text{inner}} + 2 w_{\text{cap}} = \frac{115.0}{95.0} S_{\text{inner}}$
  - Each zone $i \in \{1 \dots K\}$ receives normalized visual weight:
    $$\text{weight}_i = \frac{w_i}{S_{\text{total}}} \in (0.0, 1.0), \quad \sum_{i=1}^K \text{weight}_i = 1.0$$
  - For standard Coggan Power ($[55, 75, 90, 105, 120, 150]\%$ of FTP):
    - Raw spans: $[10, 20, 15, 15, 15, 30, 10]\%$ of FTP ($S_{\text{total}} = 115\%\text{ FTP}$)
    - Normalized weights: $[10/115, 20/115, 15/115, 15/115, 15/115, 30/115, 10/115] \approx [0.0870, 0.1739, 0.1304, 0.1304, 0.1304, 0.2609, 0.0870]$.
- **Strictly Linear Needle Fraction**:
  $$\text{needleFraction} = \text{coerceIn}\left(\frac{\text{value} - X_{\min}}{X_{\max} - X_{\min}}, 0.0, 1.0\right).\text{toFloat}()$$
  - At every cutoff $C_i$ ($i \in \{1 \dots K-1\}$), $\frac{C_i - X_{\min}}{X_{\max} - X_{\min}} = \sum_{j=1}^i \text{weight}_j$, so the needle aligns with 100% mathematical precision to every inter-zone divider on the proportionally weighted bar.
  - For $\text{value} \le X_{\min}$, $\text{needleFraction} = 0.0\text{f}$; for $\text{value} \ge X_{\max}$, $\text{needleFraction} = 1.0\text{f}$.

### 3.2 `ZoneGaugeState` & `ZoneGaugeBar` Layout and Primary Blue Needle
- ** In `ZoneGaugeState.kt`**:
  - Add `val segmentWeights: List<Float> = List(segmentColors.size) { if (segmentColors.isNotEmpty()) 1f / segmentColors.size else 1f }` with a safe equal-weight default so existing callers constructing `ZoneGaugeState` directly remain binary/source compatible, while `ZoneGaugeMath.calculateZoneGaugeState` populates the exact proportional `segmentWeights` via `ZoneGaugeMath.calculateSegmentWeights(cutoffs, isPower)`.
- **In `ZoneGaugeBar.kt`**:
  - Replace the outer `Row` with `Column(modifier = modifier.fillMaxWidth())`.
  - **Top Row (Full-Width Track + Primary Blue Needle)**:
    - Each segment `Box` uses `Modifier.weight(state.segmentWeights.getOrElse(index) { 1f })`.
    - Needle uses `4.dp` width filled with `MaterialTheme.colorScheme.primary` and a `1.dp` contrasting halo outline (`TTColor.ZoneGaugeTrack` / `MaterialTheme.colorScheme.surface` contrast) so it is unmistakably legible against all 7 zone colors and both Light and Dark/AMOLED backgrounds.
  - **Bottom Row (Centered Active Zone Label Beneath Active Segment)**:
    - When `showBadge && state.activeZoneIndex != null`, render a full-width `Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp))` with $K$ slots using the exact same `Modifier.weight(state.segmentWeights.getOrElse(index) { 1f })` (and `1.dp` spacers matching the track dividers).
    - Inside the active slot (`index == state.activeZoneIndex - 1`), render `Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center)` with `Text(text = "Z${state.activeZoneIndex}", color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false, modifier = Modifier.testTag("zone_gauge_badge"))`.

---

## 4. Scope Boundaries

- **In Scope**:
  - `ZoneGaugeMath.kt`, `ZoneGaugeState.kt`, `ZoneGaugeBar.kt`, `ZonesSettingsActivity.kt` (ensuring `previewValue` for Z1 uses a representative value in Z1 rather than `z1Max` boundary if appropriate, or preserving `z1Max`), `ZoneGaugeMathTest.kt`, and `SensorFieldZoneGaugeTest.kt`.
- **Out of Scope**:
  - Changing Zone 6 color hex value (tracked separately in `ATT-3696`).
  - Modifying `PedalPowerBalanceBar.kt`.
