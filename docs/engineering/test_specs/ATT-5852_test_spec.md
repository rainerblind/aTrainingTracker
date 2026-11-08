# Stage 2: Requirement & Test Specification - ATT-5852: Workout Cluster Trend Chart & Dashboard Polish

**Ticket**: [ATT-5852](https://rainerblind.atlassian.net/browse/ATT-5852)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*) | **Story Points**: 2 SP  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5852`  
**Requirement Mapping**: `REQ-UI-490` (`REQ-UI-490.1`–`REQ-UI-490.4`) | **Test Spec ID**: `TST-UI-490` (`TST-UI-490.1`–`TST-UI-490.6`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-08  

---

### Requirement Archaeology & Chesterton's Fence Audit
* `Original Requirement ID & Target`: Refines `REQ-SET-078` (*Historical Duration and Speed/Pace Trend Chart for Workout Clusters*), `REQ-UI-225`, and Rule 36 formatters (`DistanceFormatter`, `AltitudeFormatter`).
* `Historical Origin & Commit Trace`: Commit `feat(ATT-4067)` (Sprint 2026-42.10) and `feat(ATT-4287)`.
* `Root Reason for Existing Formulation`: Initial trend card placed both `yAxisBottomLabel` and `xAxisStartLabel` at `x = leftPadding`, without anticipating collision or time-range filtering on clusters with hundreds of workouts; formatters used `%.0f` without grouping specifier `,`.
* `Preservation of Core Invariants`: "Faster-is-higher" coordinate inversion, PR markers, nearest-point touch selection, $N \ge 2$ threshold, and 9-language localization parity without decorative emojis (`ATT-5400`) MUST remain strictly preserved.

---

## 1. Requirement Specification (`REQ-UI-490`)

### 1.1 Problem Statement & Rationale
In `WorkoutClusterDetailDashboard` (`WorkoutClusterHeatmapScreen.kt` & `WorkoutClusterTrendChartCard.kt`), high-volume clusters (e.g. `Bike2Work` with 444 workouts) exhibit several readability and layout defects: (1) average label `Ø ...` drawn without a background pill directly over dense trend lines and data markers; (2) bottom Y-axis boundary label colliding vertically with X-axis start date at `x = leftPadding`; (3) redundant card title `📈 Leistungsverlauf` repeating the selected tab button; (4) lack of time-range filtering for multi-year clusters; and (5) lack of localized thousands separators on cumulative statistics totals (`136730 m`, `4226 km`).

### 1.2 Functional & Architectural Requirements
1. **Canvas Text Separation & Left Gutter (`REQ-UI-490.1`)**: In `WorkoutClusterTrendChartCard.kt`, Y-axis boundary labels (`yAxisTopLabel` and `yAxisBottomLabel`) SHALL be positioned in a dedicated left gutter (`leftPadding = 48.dp.toPx()`). `xAxisStartLabel` and `xAxisEndLabel` SHALL be drawn exclusively along the bottom margin below the plot canvas.
2. **Average Reference Line Badge (`REQ-UI-490.2`)**: The average label `Ø ...` SHALL be rendered with a rounded background container badge (`surfaceColor` with subtle fill) drawn on top of the trend lines and points, guaranteeing text readability regardless of underlying data marker density.
3. **Header Deduplication & Time-Range Filter Chips (`REQ-UI-490.3`)**: The redundant text header `📈 Leistungsverlauf` inside `WorkoutClusterTrendChartCard` SHALL be replaced with a compact time-range filter chip group (`FilterChip` for `1J` / `3J` / `Alle`, localized across all 9 locales without emojis) docked to the left of the `[Dauer | Tempo]` selector row. Points SHALL filter relative to the latest workout timestamp (`latestTimeS - spanS`), falling back to all points if fewer than 2 workouts match.
4. **Cumulative Volume Localized Thousands Grouping (`REQ-UI-490.4`)**: Large integer meter/kilometer totals formatted via `DistanceFormatter.java` and `AltitudeFormatter.java` SHALL use locale-aware grouping formats (`%,.0f`) so cumulative totals render with localized grouping separators (e.g. `136.730 m`, `4.226 km` in German).

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
* **AC-1 (Average Badge Legibility)**: *Given* `WorkoutClusterTrendChartCard` with multiple workouts, *When* `Ø ...` is drawn, *Then* it renders with a rounded surface background badge, ensuring complete text legibility over dense trend lines and data markers.
* **AC-2 (Axis Label Separation)**: *Given* `WorkoutClusterTrendChartCard`, *When* rendered, *Then* bottom Y-axis label is in the left gutter and start date along the bottom margin without visual collision.
* **AC-3 (Time-Range Filter Toolbar)**: *Given* `WorkoutClusterDetailDashboard`, *When* viewing trend card, *Then* redundant `📈 Leistungsverlauf` is replaced by time-range filter chips (`1J | 3J | Alle`) docked left of `[Dauer | Tempo]`.
* **AC-4 (Thousands Grouping)**: *Given* the cumulative volume card in the Statistics tab, *When* displaying totals $\ge 1.000$, *Then* cumulative distance and elevation are formatted with locale-aware thousands grouping (e.g., `4.226 km` and `136.730 m` in German).
* **Invariants**: 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT; cluster similarity calculations; database schema backward compatibility; 100% test pass rate.

---

## 2. Test Specification (`TST-UI-490`)

### Test Case 1: Canvas Text Separation & Left Gutter Contract Tests (`TST-UI-490.1`)
* **Scope**: UI Layout Contract Test (`WorkoutClusterTrendCardPolishTest.kt`)
* **Action**: Assert `WorkoutClusterTrendChartCard.kt` positions Y-axis labels in dedicated left gutter (`leftPadding = 48.dp.toPx()`) and assert `yAxisBottomLabel` and `xAxisStartLabel` do not collide horizontally or vertically.

### Test Case 2: Average Badge Legibility Contract Tests (`TST-UI-490.2`)
* **Scope**: UI Layout Contract Test (`WorkoutClusterTrendCardPolishTest.kt`)
* **Action**: Assert `WorkoutClusterTrendChartCard.kt` draws a rounded surface background badge behind `formattedAverage`.

### Test Case 3: Time-Range Filtering & Header Deduplication Contract Tests (`TST-UI-490.3`)
* **Scope**: UI Layout & State Contract Test (`WorkoutClusterTrendCardPolishTest.kt`)
* **Action**: Assert redundant `📈 Leistungsverlauf` heading is removed and time-range filter chips (`1J | 3J | Alle`) filter points relative to latest workout timestamp with safe fallback when $< 2$ points match.

### Test Case 4: Thousands Grouping Formatter Unit Tests (`TST-UI-490.4`)
* **Scope**: Formatter Unit Test (`DistanceFormatterTest.java`, `AltitudeFormatterTest.java`)
* **Action**: Verify `DistanceFormatter.format_with_units(4_226_000)` formats as `"4.226 km"` in German locale and `"4,226 km"` in US locale; verify `AltitudeFormatter.format_with_units(136_730)` formats as `"136.730 m"` in German locale and `"136,730 m"` in US locale.

### Test Case 5: 9-Language Localization & Format Audit (`TST-UI-490.5`)
* **Scope**: Localization Parity Test (`TranslationParityTest.kt`)
* **Action**: Verify `cluster_trend_range_all`, `cluster_trend_range_1y`, and `cluster_trend_range_3y` exist across all 9 locales with zero inline emojis (`ATT-5400`).

### Test Case 6: Tier 1 Targeted Regression Suite (`TST-UI-490.6`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.banalservice.sensor.formater.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Action**: Verify 100% test pass rate across all modified packages.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-490.1` | UI Layout Contract | `WorkoutClusterTrendChartCard.kt` | `REQ-UI-490.1` | Planned |
| `TST-UI-490.2` | UI Layout Contract | `WorkoutClusterTrendChartCard.kt` | `REQ-UI-490.2` | Planned |
| `TST-UI-490.3` | UI & State Contract | `WorkoutClusterTrendChartCard.kt` | `REQ-UI-490.3` | Planned |
| `TST-UI-490.4` | Formatter Unit | `DistanceFormatter.java`, `AltitudeFormatter.java` | `REQ-UI-490.4` | Planned |
| `TST-UI-490.5` | Localization | `strings.xml` (all 9 locales), `TranslationParityTest` | `REQ-UI-490.3` | Planned |
| `TST-UI-490.6` | Regression | Modified Package Test Suites | `REQ-UI-490.1`..`REQ-UI-490.4` | Planned |
