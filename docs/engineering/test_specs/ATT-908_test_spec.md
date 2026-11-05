# Stage 2: Requirement & Test Specification - ATT-908: Aftermath: Move lap based on the map

**Ticket**: [ATT-908](https://rainerblind.atlassian.net/browse/ATT-908)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-908`  
**Requirement Mapping**: `REQ-UI-450` (*Interactive Map Lap Boundary Moving in Workout Aftermath*)  
**Test Spec ID**: `TST-UI-450`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-05  

---

## 1. Requirement Specification (REQ-UI-450)

### 1.1 Problem Statement & Rationale
Athletes inspecting recorded workouts in Aftermath currently lack a mechanism to adjust the boundary between two adjacent recorded laps ($k$ and $k+1$) when a lap split occurred too early, too late, or during an untimely auto-lap. The system SHALL enable interactive lap boundary shifting along the GPS polyline with real-time preview and atomic multi-database range updates without altering overall session metrics.

### 1.2 Functional & Architectural Requirements
1. **Entry Points & Pre-Seeding (`REQ-UI-450.1`)**: Provide a "Move Boundary on Map" (*Rundengrenze auf Karte verschieben*) CTA in `LapEditBottomSheet` (for Lap $k < K$) and adjacent lap headers in `WorkoutLaps`, pre-seeding the boundary pin at the existing junction between Lap $k$ and Lap $k+1$.
2. **Interactive Boundary Move Sheet (`REQ-UI-450.2`, `MoveLapBoundaryOnMapSheet`)**: Hosted in `AppModalBottomSheet`, rendering the track polyline, start/junction/stop pins, precision slider, and live preview cards for Lap $k$ and Lap $k+1$ (duration, distance, average speed/pace via `LapSplitAndMoveCalculator`).
3. **Boundary Clamping & Zero-Phantom Guard (`REQ-UI-450.3`)**: Clamps $s_{\text{new}}$ strictly within $[startIndex(k) + 1, endIndex(k+1) - 1]$. Neither adjacent lap can collapse to duration $\le 0$ or distance $\le 0.0$ (`REQ-TRK-002`).
4. **Transactional Persistence (`REQ-UI-450.4`, `WorkoutRepository.moveLapBoundary`)**: Selectively updates `LAP_NR` for shifted samples ($[s_{\text{min}}, s_{\text{max}}]$) via `WorkoutSamplesDatabaseManager.updateLapNumberForSampleRange`, persists recalculated summaries for Lap $k$ and Lap $k+1$ via `replaceCroppedLaps`, invalidates export caches, and reloads `WorkoutData`.
5. **Universal Localization Parity (`REQ-UI-450.5`)**: All strings SHALL maintain 100% parity across all 9 locales with zero inline emojis.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-UI-450`, extending `REQ-UI-449`, `REQ-TRK-002`, and `REQ-UI-142`. Targets `WorkoutRepository.kt`, `WorkoutSamplesDatabaseManager.java`, `LapSplitAndMoveCalculator.kt`, and `MoveLapBoundaryOnMapSheet.kt`.
* **Historical Origin & Commit Trace**: `ATT-907` (map lap creation) and `ATT-896` (phantom lap prevention).
* **Root Reason for Existing Formulation**: Boundaries were immutable to prevent cross-database sample/summary desynchronization.
* **Preservation of Core Invariants**: No phantom laps; contiguous $1 \dots K$ sequence; immutable session summary, distance, time, and extrema.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Sheet Invocation & Pre-Seeding)**: *Given* a workout with $\ge 2$ laps, *When* tapping "Move Boundary on Map" for Lap $k$, *Then* `MoveLapBoundaryOnMapSheet` opens centered on the two-lap boundary with the movable pin at the junction.
* **Criterion 2 (Reactive Boundary Preview)**: *Given* `MoveLapBoundaryOnMapSheet` open, *When* moving the slider or pin, *Then* metrics for Lap $k$ and Lap $k+1$ update reactively in real time.
* **Criterion 3 (Strict Interior Clamping)**: *Given* an attempt to drag beyond adjacent boundaries, *Then* the position is clamped to $[startIndex(k) + 1, endIndex(k+1) - 1]$ preventing zero-duration laps.
* **Criterion 4 (Atomic Persistence)**: *Given* confirmed boundary shift, *When* applied, *Then* `LAP_NR` updates in `WorkoutSamples.db` for affected samples only, `Laps.db` replaces Lap $k$ and Lap $k+1$, other laps remain untouched, and the UI refreshes immediately.

---

## 2. Test Specification (TST-UI-450)

### Test Case 1: Boundary Clamping & Move Preview Math (`TST-UI-450.1`, `LapBoundaryMoveCalculatorTest.kt`)
* **Scope**: Unit test asserting `validateBoundaryMove` clamps to interior points, calculates accurate metrics for forward and backward shifts, and prevents phantom laps.

### Test Case 2: Multi-Database Selective Range Update (`TST-UI-450.2`, `WorkoutRepositoryBoundaryMoveTest.kt`)
* **Scope**: Integration test asserting `updateLapNumberForSampleRange` updates only the $[s_{\text{min}}, s_{\text{max}}]$ sample range, replaces only the 2 affected laps in `Laps.db`, and verifies rollback safety.

### Test Case 3: Interactive Boundary Move Sheet Contract (`TST-UI-450.3`, `MoveLapBoundaryOnMapSheetTest.kt`)
* **Scope**: Compose semantics test verifying slider range clamping, pre-seeding, live preview cards, and confirmation dispatch.

### Test Case 4: 9-Language Localization Parity (`TST-UI-450.4`)
| Locale | Action Label (`move_lap_boundary_action`) | Sheet Title (`move_lap_boundary_title`) | Confirm CTA (`move_lap_boundary_confirm`) |
| :--- | :--- | :--- | :--- |
| `values` (EN) | Move Lap Boundary | Move Lap Boundary | Move Boundary |
| `values-de` (DE) | Rundengrenze verschieben | Rundengrenze auf Karte verschieben | Grenze verschieben |
| `values-es` (ES) | Mover límite de vuelta | Mover límite de vuelta en el mapa | Mover límite |
| `values-fr` (FR) | Déplacer la limite du tour | Déplacer la limite du tour sur la carte | Déplacer la limite |
| `values-it` (IT) | Sposta limite giro | Sposta limite giro sulla mappa | Sposta limite |
| `values-ja` (JA) | ラップ境界を移動 | マップ上でラップ境界を移動 | 境界を移動 |
| `values-nl` (NL) | Rondegrens verplaatsen | Rondegrens verplaatsen op kaart | Grens verplaatsen |
| `values-pl` (PL) | Przesuń granicę okrążenia | Przesuń granicę okrążenia na mapie | Przesuń granicę |
| `values-pt` (PT) | Mover limite de volta | Mover limite de volta no mapa | Mover limite |

### Test Case 5: Clean-Room Regression Suite (`TST-UI-450.5`)
* **Scope**: Targeted test suite (`domain.laps.*`, `ui.aftermath.*`, `database.*`, `ThemeColorCentralizationAuditTest`, `build.*`, `TranslationParityTest`).

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-450.1` | Unit (Math) | `LapSplitAndMoveCalculator.kt` | `REQ-UI-450.2`, `REQ-UI-450.3` | Specified |
| `TST-UI-450.2` | Integration / DB | `WorkoutSamplesDatabaseManager`, `WorkoutRepository` | `REQ-UI-450.3`, `REQ-UI-450.4` | Specified |
| `TST-UI-450.3` | UI / Compose | `MoveLapBoundaryOnMapSheet.kt`, `LapEditBottomSheet.kt` | `REQ-UI-450.1`, `REQ-UI-450.2` | Specified |
| `TST-UI-450.4` | Localization | `res/values*/strings.xml` | `REQ-UI-450.5` | Specified |
| `TST-UI-450.5` | Regression | Full targeted unit test suite | `REQ-PRO-001`, `REQ-UI-450` | Specified |
