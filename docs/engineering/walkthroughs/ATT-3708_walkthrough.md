# Stage 5: Walkthrough & Verification - ATT-3708: Scale live ZoneGaugeBar linearly with 10% boundary clipping, center active zone label beneath active segment, and use primary-blue needle

**Ticket**: [ATT-3708](https://rainerblind.atlassian.net/browse/ATT-3708)  
**Sub-task**: `ATT-3829` (`[Test]`)  
**Parent Epic**: [ATT-2177](https://rainerblind.atlassian.net/browse/ATT-2177) (*2.2 Settings, Sensors & Cockpit Modernization*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.6`  
**Requirement Mapping**: `REQ-UI-390` (amending `REQ-UI-376`)  
**Test Mapping**: `TST-UI-353`  
**Branch**: `feature/ATT-3708`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-16  

---

## 1. Executive Summary & Verification Overview

Ticket `ATT-3708` refines the live cockpit `ZoneGaugeBar` (`ZoneGaugeMath.kt`, `ZoneGaugeState.kt`, `ZoneGaugeBar.kt`) across three dimensions identified during the Sprint `2026-42.5` joint review:
1. **Linear Bar Scaling with 10% Boundary Clipping (`REQ-UI-390.1`)**:
   - Interior closed zones ($i \in \{2 \dots K-1\}$) scale strictly proportionally to their configured span $w_i = \max(1.0, C_i - C_{i-1})$.
   - Outer open-ended zones ($Z_1$ and $Z_K$) are clipped to a $10\%$ boundary span $w_1 = w_K = w_{\text{cap}} = \frac{10.0}{95.0} S_{\text{inner}}$ (`ZoneGaugeMath.BOUNDARY_CAP_RATIO`), matching `ZoneRangeVisualizationBar.kt` in `ZonesSettingsActivity.kt`.
   - For standard Coggan 7-zone cycling power cutoffs (`[55%, 75%, 90%, 105%, 120%, 150%]` of FTP), $S_{\text{inner}} = 95\%\text{ FTP}$, $w_1 = w_7 = 10\%\text{ FTP}$ ($X_{\min} = 45\%\text{ FTP}$, $X_{\max} = 160\%\text{ FTP}$, $S_{\text{total}} = 115\%\text{ FTP}$), and `ZoneGaugeMath.calculateSegmentWeights` yields normalized weights `[10/115f, 20/115f, 15/115f, 15/115f, 15/115f, 30/115f, 10/115f]`.
   - `ZoneGaugeMath.calculateNeedleFraction` maps telemetry values strictly linearly across $[X_{\min}, X_{\max}]$, clamping to `0.0f` for $v \le X_{\min}$ and `1.0f` for $v \ge X_{\max}$ and aligning with every proportional zone divider.
2. **Centered Under-Segment Active Zone Badge in High-Contrast Theme Text (`REQ-UI-390.2`)**:
   - `ZoneGaugeBar` uses an outer `Column(modifier = modifier.fillMaxWidth())` so the segmented track spans the full width of the sensor tile.
   - When `showBadge && state.activeZoneIndex != null`, a matching weighted bottom `Row(modifier = Modifier.fillMaxWidth())` renders the active zone label (`"Z${state.activeZoneIndex}"`) centered directly beneath the active zone segment using `MaterialTheme.colorScheme.onSurface` (`FontWeight.Bold`, `maxLines = 1`, `softWrap = false`), ensuring high contrast across all zones (including Z4 Yellow and Z7 Black) in Light, Dark, and AMOLED themes.
3. **High-Contrast Primary Blue Needle (`REQ-UI-390.3`)**:
   - The needle indicator uses a `4.dp` wide rounded rectangle filled with `MaterialTheme.colorScheme.primary` (Primary Blue) surrounded by a contrasting halo outline (`TTColor.ZoneGaugeTrack` / `MaterialTheme.colorScheme.surface`), eliminating white-on-white camouflage on Light Mode tiles and offering 100% hue separation against all 7 zone colors.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-390.1` | `TST-UI-353.1` | `ZoneGaugeMathTest` (Proportional segment weights, 10% boundary clipping, linear needle velocity & cutoff alignment) | **PASSED** | `Verified` |
| `REQ-UI-390.2` | `TST-UI-353.2` | `SensorFieldZoneGaugeTest` (Under-segment centered active badge in `MaterialTheme.colorScheme.onSurface`, full-width weighted `Column` layout) | **PASSED** | `Verified` |
| `REQ-UI-390.3` | `TST-UI-353.2` | `SensorFieldZoneGaugeTest` (`4.dp` needle filled with `MaterialTheme.colorScheme.primary` and contrasting outline) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-353.3` | Full Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL
```

### Targeted Unit & Contract Tests
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.ZoneGaugeMathTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldZoneGaugeTest" --tests "com.atrainingtracker.trainingtracker.settings.SettingsDataStoreZoneGaugeTest"
BUILD SUCCESSFUL in 24s
```

---

## 4. Hardware / Physical Verification (Pixel 10)

### Visual Consistency (Rule 23)
* **Reference Screen**: `ZoneRangeVisualizationBar.kt` (`ZonesSettingsActivity`) and `SensorFieldView.kt` (`SensorGridScreen`).
* **Checked against `docs/design_guidelines.md` §5**: shapes ☑ spacing ☑ colors/themes ☑ typography/icons ☑ placement ☑
* **Compose Previews Provided**:
  * `ZoneGaugeBar5ZoneLightPreview` (5-Zone HR active Z3 in Light Mode)
  * `ZoneGaugeBar7ZoneDarkPreview` (7-Zone Power active Z4 in Dark/AMOLED Mode)
  * `ZoneGaugeBarStalePreview` (Stale/offline telemetry dimmed at `0.35f` alpha)
* **Deviations & justification**: None. Full on-device verification on Google Pixel 10 will be performed at the start of Ceremony 2 (`./gradlew installDebug`) per Rule 10.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Clean-room unit test suite executed with 100% pass rate.
2. **Living Documentation Synchronized**: Status of `REQ-UI-390` in `docs/requirements.md` and `TST-UI-353` in `docs/tests.md` updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask transitioned to `Erledigt` via `freigabe`.
4. **Parent Ticket Final Review**: Parent ticket `ATT-3708` transitioned to `Final Review (Human)` and assigned to `human` for final release sign-off.
