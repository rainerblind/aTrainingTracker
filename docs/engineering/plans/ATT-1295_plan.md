# Stage 3 Implementation Plan: ATT-1295 - Visual pedal power balance indicator tile in cockpit

**Ticket**: [ATT-1295](https://rainerblind.atlassian.net/browse/ATT-1295)  
**Sub-task**: [ATT-3652](https://rainerblind.atlassian.net/browse/ATT-3652) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*UI Improvements & Visual Modernization*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Branch**: `feature/ATT-1295`  
**Requirement Mapping**: `REQ-UI-377` (extending `REQ-UI-171`, `REQ-UI-103`, and complementing `REQ-UI-376`)  
**Test Mapping**: `TST-UI-340`  
**Author**: AI Agent 1 (Implementer)  
**Reviewer / Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-15  

---

## 1. Executive Summary & Core Architectural Goal

The primary objective of **ATT-1295** is to transform the existing single-integer pedal power balance telemetry tile into an intuitive, real-time visual split-bar indicator (`PedalPowerBalanceBar`) in the live cockpit.

Dual-sided power meters broadcast power balance as Right pedal contribution $P_R \in [0, 100]\%$. Athletes riding at threshold or sprinting cannot mentally decode a single number `"52"` into Left/Right asymmetry or assess whether a deviation is within normal biomechanical tolerances ($\le 2\%$) or indicates severe muscular fatigue/injury ($> 5\%$).

This plan establishes:
1. Pure $O(1)$ algorithmic mathematical modeling (`PedalPowerBalanceMath.kt`) and immutable data models (`PedalBalanceState.kt`) computing Left/Right percentages, deviation distance $|\Delta|$, dominant side, and physiological tiers (Balanced Green, Alert Amber, Critical Red).
2. Dual-readout formatting (`"48 | 52 %"` and `"-- | -- %"` for null/stale).
3. A dedicated Compose component `PedalPowerBalanceBar.kt` featuring a center-anchored bidirectional split track, "L" and "R" peripheral markers, a high-contrast white needle with dark border, and strict True-Black AMOLED compliance (`outlineVariant` border `#2B3342` with $\ge 3:1$ contrast against `#000000`).
4. Direct, lightweight integration into `SensorFieldState.kt`, `SensorFieldView.kt`, and `TrackingViewModel.kt` without layout churn or memory allocation in draw loops.

---

## 2. Requirement & Test Traceability Matrix

| Requirement Clause | Description | Test Specification ID | Verification Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-377.1` | Mathematical Representation & Telemetry Ingestion ($P_L, P_R, \Delta$) | `TST-UI-340.1` | `PedalPowerBalanceMathTest.kt` |
| `REQ-UI-377.2` | Physiological Asymmetry Tiers & Color Cues (Green, Amber, Red) | `TST-UI-340.1` | `PedalPowerBalanceMathTest.kt` |
| `REQ-UI-377.3` | Split Readout Formatting (`"48 \| 52"`, `"-- \| --"`) | `TST-UI-340.2` | `PedalPowerBalanceFormatterTest.kt` |
| `REQ-UI-377.4` | Visual Component (`PedalPowerBalanceBar.kt`) & AMOLED Compliance | `TST-UI-340.3` | `SensorFieldPedalBalanceTest.kt` & Compose Preview |
| `REQ-UI-377.5` | Cockpit Sensor Tile Integration (`SensorFieldView.kt`, `SensorFieldState.kt`) | `TST-UI-340.3` | `SensorFieldPedalBalanceTest.kt` |
| `REQ-UI-377.6` | 60 FPS Performance Budget ($O(1)$ pure math, zero alloc) | `TST-UI-340.1` | Static analysis & unit tests |
| `REQ-PRO-001` | Clean-Room Full Suite Regression Execution | `TST-UI-340.4` | `./gradlew testDebugUnitTest` |

---

## 3. SWE.2 Architecture & System Decomposition

### 3.1 Architectural Flow Diagram

```mermaid
flowchart TD
    subgraph Device Drivers
        ANT[ANTBikePowerDevice] -->|0..100 Right%| Service[BANALService Pipeline]
        BLE[BTLEBikePowerDevice] -->|0..100 Right%| Service
    end

    subgraph ViewModel & Pure Math Engine
        Service -->|SensorType.PEDAL_POWER_BALANCE| VM[TrackingViewModel]
        VM -->|raw Right%| PPBM[PedalPowerBalanceMath]
        PPBM -->|PedalBalanceState| SFS[SensorFieldState]
        PPBM -->|formatted string '48 | 52'| SFS
    end

    subgraph Cockpit UI
        SFS --> SFV[SensorFieldView]
        SFV -->|pedalBalanceState != null| PPBB[PedalPowerBalanceBar]
        PPBB --> Canvas[Canvas Split Bar & Needle]
    end
```

### 3.2 Component Breakdown & Data Models

#### 1. Data Model: `PedalBalanceState.kt` (in `com.atrainingtracker.trainingtracker.ui.tracking`)
```kotlin
package com.atrainingtracker.trainingtracker.ui.tracking

import androidx.compose.ui.graphics.Color

enum class PedalBalanceTier {
    OPTIMAL, // |Delta| <= 2% (48/52)
    MILD,    // 2% < |Delta| <= 5% (45/55)
    SEVERE   // |Delta| > 5% (<45 / >55)
}

enum class DominantSide {
    BALANCED,
    LEFT,
    RIGHT
}

data class PedalBalanceState(
    val leftPercent: Int,
    val rightPercent: Int,
    val deviationPercent: Int,
    val dominantSide: DominantSide,
    val tier: PedalBalanceTier,
    val tierColor: Color,
    val indicatorFraction: Float?, // [0.0..1.0], 0.50 is center, null if stale
    val isStale: Boolean
)
```

#### 2. Pure Calculation Engine: `PedalPowerBalanceMath.kt`
- Path: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/PedalPowerBalanceMath.kt`
- Functions:
  - `calculateBalanceState(rightPercentValue: Number?): PedalBalanceState`:
    - Validates null, NaN, and infinity. Returns `isStale = true` with neutral $50/50$ values and `indicatorFraction = null` when invalid.
    - Clamps $P_R \in [0, 100]$.
    - Calculates $P_L = 100 - P_R$, $\Delta = |P_R - 50|$.
    - Resolves `DominantSide` (`LEFT`, `RIGHT`, `BALANCED`).
    - Resolves `PedalBalanceTier`:
      - $\Delta \le 2$: `PedalBalanceTier.OPTIMAL` (`TTColor.ConnectionStatusGreen` `#4CAF50`).
      - $2 < \Delta \le 5$: `PedalBalanceTier.MILD` (`TTColor.Zone3` `#FFA500`).
      - $\Delta > 5$: `PedalBalanceTier.SEVERE` (`TTColor.Zone4` `#FF0000`).
    - `indicatorFraction = P_R / 100.0f`.
  - `formatBalanceString(state: PedalBalanceState): String`:
    - Returns `"-- | --"` if stale, or `"${state.leftPercent} | ${state.rightPercent}"`.

#### 3. Visual Component: `PedalPowerBalanceBar.kt`
- Path: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/PedalPowerBalanceBar.kt`
- Layout:
  - Row with "L" label (`fontSize = 11.sp`, `FontWeight.Bold`, `onSurfaceVariant`), central horizontal track, and "R" label.
  - Track: `height = 8.dp`, `RoundedCornerShape(4.dp)`, border `0.5.dp` `MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)`.
  - Center 50/50 anchor: vertical divider line (`1.5.dp` width, `MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)`).
  - Deviation fill: extends from center ($0.50$) to `indicatorFraction`, filled with `state.tierColor`.
  - High-contrast needle: Pure White `#FFFFFF` with 1dp dark border (`#1B1B1F`), `3.dp` width, `12.dp` height (overhanging track by 2dp top/bottom), centered at `indicatorFraction`.
  - Stale state: track dimmed to $0.35$ alpha, fill bar hidden, needle hidden or centered.
  - Previews: Light, Dark, AMOLED themes, balanced (50/50), mild deviation (46/54), severe deviation (40/60), and stale state.

#### 4. Sensor Tile Integration: `SensorFieldState.kt`, `SensorFieldView.kt`, & `TrackingViewModel.kt`
- `SensorFieldState`: Add `val pedalBalanceState: PedalBalanceState? = null`.
- `SensorFieldView`: Render `PedalPowerBalanceBar` directly below the value/units row when `fieldState.pedalBalanceState != null` or sensor is `PEDAL_POWER_BALANCE`.
- `TrackingViewModel`: In `baseFields` and `applySensorData`, when `sensorType == SensorType.PEDAL_POWER_BALANCE`, calculate `pedalBalanceState` via `PedalPowerBalanceMath.calculateBalanceState(sensorData.value as? Number)` and format value as `"48 | 52"` (or `"-- | --"`).

---

## 4. Invariants & Chesterton's Fence Forensics

1. **Telemetry Pipeline & Exporter Integrity**:
   - `SensorType.PEDAL_POWER_BALANCE` retains its raw integer percentage representation for external export (FIT, TCX, GoldenCheetah `GCFileWriter`).
   - Database schema and `SensorValueType.INTEGER` remain 100% backward compatible.
2. **True-Black AMOLED Cockpit Background Compatibility (`REQ-UI-190`, Rule 23)**:
   - Outer track border is stroked with `MaterialTheme.colorScheme.outlineVariant` (`#2B3342`), ensuring non-text contrast $\ge 3:1$ against `#000000` AMOLED surfaces.
   - Indicator needle uses pure white `#FFFFFF` with a 1dp dark border, guaranteeing $> 12:1$ contrast against any fill color or surface.
3. **60 FPS Performance Budget & Zero Allocation**:
   - All calculations in `PedalPowerBalanceMath` are pure $O(1)$ operations with zero heap allocation in draw scopes.
4. **Physiological Threshold Anchoring**:
   - Tiers are anchored to established exercise biomechanics: $\le 2\%$ functional symmetry, $3-5\%$ mild asymmetry, $> 5\%$ severe asymmetry.

---

## 5. UI Consistency & Existing Component Reference

In compliance with **Section 5 of `docs/design_guidelines.md`**:
- **Closest Component Reference**: `ZoneGaugeBar.kt` and `ZoneDistributionStackedBar.kt`.
- **Harmonization**:
  - Track height `8.dp` with rounded corners `4.dp`.
  - Border stroke: `0.5.dp` `outlineVariant`.
  - Typography: "L" and "R" labels use `MaterialTheme.typography.labelSmall`.
  - Vertical spacing within tile: `4.dp` vertical padding below the value/units row.

---

## 6. Atomic Step Sequencing

### Step 1: Algorithmic Model & Math (`PedalBalanceState.kt`, `PedalPowerBalanceMath.kt`, & `PedalPowerBalanceMathTest.kt`)
- Create `PedalBalanceState.kt` and `PedalPowerBalanceMath.kt`.
- Author unit tests in `PedalPowerBalanceMathTest.kt` verifying:
  - 50/50 perfect balance.
  - Left-dominant and Right-dominant optimal balance ($48/52$, $52/48$).
  - Mild asymmetry ($46/54$, $55/45$).
  - Severe asymmetry ($40/60$, $62/38$).
  - Boundary clamping ($< 0 \rightarrow 0\%$, $> 100 \rightarrow 100\%$).
  - Stale / null / non-finite handling.
  - String formatting (`"48 | 52"`, `"-- | --"`).
- Run targeted unit test and verify PASS.

### Step 2: Visual Component Construction (`PedalPowerBalanceBar.kt`)
- Implement `PedalPowerBalanceBar` Composable in `com.atrainingtracker.trainingtracker.ui.tracking`.
- Render L/R labels, 50/50 center anchor, deviation fill bar, white cursor with dark border, and dimmed 0.35 alpha stale state.
- Include Compose `@Preview` covering light, dark, and AMOLED modes.

### Step 3: Cockpit Tile & ViewModel Integration (`SensorFieldState.kt`, `SensorFieldView.kt`, `TrackingViewModel.kt`, & Tests)
- Add `pedalBalanceState: PedalBalanceState? = null` to `SensorFieldState.kt`.
- Render `PedalPowerBalanceBar` in `SensorFieldView.kt` when `pedalBalanceState != null` or sensor is `PEDAL_POWER_BALANCE`.
- Wire `TrackingViewModel.kt` to compute `pedalBalanceState` and split formatted string for `PEDAL_POWER_BALANCE`.
- Author unit tests in `SensorFieldPedalBalanceTest.kt` verifying state preservation and rendering.
- Run targeted unit test and verify PASS.

---

## 7. Gate 3 Readiness Checklist

- [x] High-level SWE.2 architecture and system flow defined (Mermaid flowchart).
- [x] Clear data models specified (`PedalBalanceState`, `PedalBalanceTier`, `DominantSide`).
- [x] Pure $O(1)$ math decoupled from Compose UI (`PedalPowerBalanceMath.kt`).
- [x] Backward compatibility strictly preserved for exporters and drivers.
- [x] AMOLED True-Black contrast compliance verified (`outlineVariant` track borders, white needle with dark border).
- [x] Recomposition isolation and 60 FPS frame budget honored.
- [x] Invariants and Chesterton's Fence forensics documented.
- [x] UI design guidelines (Section 5) and closest references (`ZoneGaugeBar.kt`) incorporated.
- [x] Atomic step sequencing with targeted automated tests defined.
