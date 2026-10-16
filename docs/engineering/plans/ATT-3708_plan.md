# Stage 3 Architecture & Implementation Plan: ATT-3708

**Parent Ticket:** `ATT-3708` — *Scale live ZoneGaugeBar linearly with 10% boundary clipping, center active zone label beneath active segment, and use primary-blue needle*  
**Sprint:** `2026-42.6` (`sprint/2026-42.6`)  
**Requirements:** `REQ-UI-390` (amending `REQ-UI-376`)  
**Test Cases:** `TST-UI-353`

---

## 1. Architectural Decomposition (SWE.2)

| Layer | Component / File | Responsibility |
| :--- | :--- | :--- |
| **Domain / Pure Math** | `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/ZoneGaugeMath.kt` | Compute linear domain bounds $[X_{\min}, X_{\max}]$, proportional segment weights (`calculateSegmentWeights`), and strictly linear `calculateNeedleFraction` with $10\%$ boundary clipping ($w_{\text{cap}} = \frac{10.0}{95.0} S_{\text{inner}}$). |
| **Presentation State** | `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/ZoneGaugeState.kt` | Extend `ZoneGaugeState` with `val segmentWeights: List<Float>` (defaulting to equal weights `1f / segmentColors.size` when omitted). |
| **UI Component (Compose)** | `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/ZoneGaugeBar.kt` | Replace outer `Row` with `Column(modifier = modifier.fillMaxWidth())`, weight segments by `state.segmentWeights`, center `"Z${state.activeZoneIndex}"` beneath the active segment in `MaterialTheme.colorScheme.onSurface`, and render a `4.dp` needle in `MaterialTheme.colorScheme.primary` with a contrasting outline. |
| **Unit & Contract Tests** | `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/ZoneGaugeMathTest.kt`<br>`app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/SensorFieldZoneGaugeTest.kt` | Verify Coggan 7-zone proportional weights (`[10, 20, 15, 15, 15, 30, 10] / 115f`), 5-zone HR weights, strictly linear needle velocity, exact cutoff alignment, boundary clamping at $X_{\min}$/$X_{\max}$, under-segment badge layout, and Primary Blue needle rendering (`TST-UI-353`). |

---

## 2. Atomic Step Breakdown

### Step 1: Extend `ZoneGaugeState.kt` with `segmentWeights`
- Add property `val segmentWeights: List<Float> = if (segmentColors.isNotEmpty()) List(segmentColors.size) { 1f / segmentColors.size } else emptyList()` to `ZoneGaugeState` in `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/ZoneGaugeState.kt`.

### Step 2: Implement Linear Domain Bounds, Proportional Weights & Linear Needle Math in `ZoneGaugeMath.kt`
- Add constant `const val BOUNDARY_CAP_RATIO = 10.0 / 95.0`.
- Implement `calculateSegmentWeights(cutoffs: List<Int>, isPower: Boolean = false): List<Float>`:
  - Let `numZones = if (isPower) 7 else 5` and `requiredCutoffs = numZones - 1`.
  - If `cutoffs.size < requiredCutoffs`, return equal weights `List(numZones) { 1f / numZones }`.
  - Compute interior spans $w_i = \max(1.0, (C_i - C_{i-1}).\text{toDouble}())$ for $i \in \{1 \dots \text{requiredCutoffs} - 1\}$ ($0$-indexed cutoffs `cutoffs[i] - cutoffs[i - 1]`).
  - Compute total interior span $S_{\text{inner}} = \sum w_i$.
  - Compute clipped outer zone span $w_{\text{cap}} = \text{BOUNDARY\_CAP\_RATIO} \times S_{\text{inner}}$.
  - Set raw spans $[w_{\text{cap}}, w_2, \dots, w_{K-1}, w_{\text{cap}}]$ and normalize by $S_{\text{total}} = S_{\text{inner}} + 2.0 \times w_{\text{cap}}$ so $\sum \text{weight}_i = 1.0\text{f}$.
- Update `calculateNeedleFraction(value, cutoffs, hrMax, ftp, isPower)`:
  - Validate `value != null && value.isFinite() && cutoffs.size >= requiredCutoffs`.
  - Compute interior spans $w_i = \max(1.0, (cutoffs[i] - cutoffs[i - 1]).\text{toDouble}())$, $S_{\text{inner}} = \sum w_i$, and $w_{\text{cap}} = \text{BOUNDARY\_CAP\_RATIO} \times S_{\text{inner}}$.
  - Set $X_{\min} = cutoffs[0].\text{toDouble}() - w_{\text{cap}}$ and $X_{\max} = cutoffs[0].\text{toDouble}() + S_{\text{inner}} + w_{\text{cap}}$ (note: using monotonically guarded `cutoffs[0] + S_inner` guarantees $X_{\max} > X_{\min}$ and exact alignment with cumulative `segmentWeights` even if a user saves degenerate equal cutoffs).
  - Return `((value - xMin) / (xMax - xMin)).coerceIn(0.0, 1.0).toFloat()`.
- Update `calculateZoneGaugeState` to pass `segmentWeights = calculateSegmentWeights(cutoffs, isPower)` into `ZoneGaugeState`.

### Step 3: Update `ZoneGaugeBar.kt` for Full-Width Weighted Track, Centered Under-Segment Badge & Primary Blue Needle
- Capture `val primaryColor = MaterialTheme.colorScheme.primary` and `val surfaceColor = MaterialTheme.colorScheme.surface` outside the `Canvas` block.
- Change the root layout of `ZoneGaugeBar` from `Row` to `Column(modifier = modifier.fillMaxWidth())`.
- Inside the top `BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(totalBarHeight).testTag("zone_gauge_bar_container"))`:
  - Weight each segment `Box` in the track `Row` with `Modifier.weight(state.segmentWeights.getOrElse(index) { 1f }.coerceAtLeast(0.001f))`.
  - In the needle `Canvas`, use `4.dp` needle width (`val needleWidthPx = 4.dp.value * (totalHeightPx / totalBarHeight.value)`), draw the outer halo stroke (`TTColor.ZoneGaugeTrack` / contrasting border), and draw the inner rounded rectangle fill with `color = primaryColor` (`MaterialTheme.colorScheme.primary`).
- Beneath the track `BoxWithConstraints`, when `showBadge && state.activeZoneIndex != null`:
  - Render `Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically)` iterating `state.segmentColors.indices`:
    - Each slot is a `Box(modifier = Modifier.weight(state.segmentWeights.getOrElse(index) { 1f }.coerceAtLeast(0.001f)), contentAlignment = Alignment.Center)`:
      - When `index == state.activeZoneIndex - 1`, render `Text(text = "Z${state.activeZoneIndex}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false, modifier = Modifier.testTag("zone_gauge_badge"))`.
    - Between slots (`if (index < state.segmentColors.lastIndex)`), include `Spacer(modifier = Modifier.width(1.dp))` matching the inter-zone divider width.

### Step 4: Update & Expand Unit and Contract Tests (`ZoneGaugeMathTest.kt`, `SensorFieldZoneGaugeTest.kt`)
- Update `ZoneGaugeMathTest.kt` to test the new proportional segment weights (`[10, 20, 15, 15, 15, 30, 10] / 115f` for Coggan Power), exact cumulative weight alignment at all cutoffs $C_1 \dots C_{K-1}$ for both 5-zone HR and 7-zone Power, constant linear needle velocity across interior zones, and $10\%$ boundary clipping at $X_{\min}$ (`0.0f`) and $X_{\max}$ (`1.0f`).
- Update `SensorFieldZoneGaugeTest.kt` to verify `ZoneGaugeState.segmentWeights` and add a source contract test verifying `ZoneGaugeBar.kt` uses `Column`, `state.segmentWeights`, `MaterialTheme.colorScheme.onSurface` for the centered under-segment badge, and `MaterialTheme.colorScheme.primary` (`primaryColor`) with `4.dp` width for the needle.
- Run targeted unit tests:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.ZoneGaugeMathTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldZoneGaugeTest"`

---

## 3. UI Consistency & Invariants (Rule 23)

- **Reference Components**: `ZoneDistributionStackedBar.kt` (proportional horizontal segment weighting via `Modifier.weight(...)` with `1.dp` `MaterialTheme.colorScheme.surface` dividers and `0.5.dp` `outlineVariant` border) and `SensorFieldView.kt` (cockpit tile hosting `ZoneGaugeBar`).
- **Theme Tokens**:
  - Needle fill: `MaterialTheme.colorScheme.primary` (Royal Blue `#1464F4` in Light Mode, Sky Blue `#A6C8FF` in Dark/AMOLED Mode — zero hue overlap with any of the 7 zone colors `TTColor.Zone1..Zone7`).
  - Needle halo outline: `TTColor.ZoneGaugeTrack` (`#1B1B1F`) stroke for contrast against bright zones (`Zone1` Chartreuse, `Zone4` Yellow) and Light Mode white backgrounds.
  - Active zone badge text: `MaterialTheme.colorScheme.onSurface` (guarantees > 12:1 contrast in both Light and Dark/AMOLED modes regardless of whether the active zone is Z4 Yellow or Z7 Black).
- **Non-Negotiable Invariants**:
  - DataStore `showGauge` persistence (`REQ-UI-376.1`) and stale dimmed state (`0.35f` track alpha, hidden needle) remain unchanged.
  - Zero inline `Color(0x...)` literals outside `Color.kt` (`REQ-UI-381`).
