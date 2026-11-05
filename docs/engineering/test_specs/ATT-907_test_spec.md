# Stage 2: Requirement & Test Specification - ATT-907: Aftermath: Create lap based on the map

**Ticket**: [ATT-907](https://rainerblind.atlassian.net/browse/ATT-907)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.6` | **Branch**: `feature/ATT-907`  
**Requirement Mapping**: `REQ-UI-449` (*Interactive Map Lap Creation & Splitting in Workout Aftermath*)  
**Test Spec ID**: `TST-UI-449`  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-05  

---

## 1. Requirement Specification (REQ-UI-449)

### 1.1 Problem Statement & Rationale
Athletes inspecting recorded workouts in Aftermath (`TrackOnMapScreen` / `WorkoutDetails`) currently lack a mechanism to retroactively split an existing lap when a split was omitted during live activity. The system SHALL enable interactive lap splitting at any sample point along the GPS polyline with real-time preview and atomic multi-database persistence.

### 1.2 Functional & Architectural Requirements
1. **Entry Point & Scrub Pre-Seeding (`REQ-UI-449.1`)**: Provide a "Create / Split Lap on Map" (*Runde auf Karte teilen*) action in `WorkoutLaps` / `LapSplitVisualizerCard` and `MapDetailLayout` scrubbing overlay, pre-populating the split position with the current scrub point index when active.
2. **Interactive Bottom Sheet (`REQ-UI-449.2`, `CreateLapOnMapSheet`)**: Hosted in `AppModalBottomSheet` (ATT-5399), displaying full route polyline, lap pins, movable split pin (tap or slider), and reactive metric preview cards for Lap $k$ and Lap $k+1$ (duration, distance, average speed/pace via `LapEditorMath`). Split index $s$ SHALL be strictly constrained to $start_k < s \le end_k$ (no phantom laps per `REQ-TRK-002`).
3. **Transactional Persistence (`REQ-UI-449.3`, `WorkoutRepository.splitLapAtSample`)**: Increment `LAP_NR = LAP_NR + 1` for samples $\ge splitRowId$ via `WorkoutSamplesDatabaseManager.splitLapAtSampleRow`, persist recomputed laps in `Laps.db` via `LapsDatabaseManager.replaceCroppedLaps`, invalidate export caches, and reactively reload `WorkoutData`.
4. **ATT-908 Synergy (`REQ-UI-449.4`)**: Preview calculations and sheet components SHALL establish a shared foundation for adjacent lap boundary shifting (`ATT-908`).
5. **Localization Parity (`REQ-UI-449.5`)**: All strings SHALL maintain 100% parity across all 9 locales with zero inline emojis.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-UI-449`, extending `REQ-TRK-002`, `REQ-UI-368`, and `REQ-UI-204`. Targets `WorkoutRepository.kt`, `WorkoutSamplesDatabaseManager.java`, `LapsDatabaseManager.java`, and `CreateLapOnMapSheet.kt`.
* **Historical Origin & Commit Trace**: `ATT-896` (Sprint `2026-44.1`, phantom lap elimination) and `ATT-3274` (Sprint `2026-42.3`, sample pruning).
* **Root Reason for Existing Formulation**: Previously, laps were generated strictly during active tracking to avoid multi-table sample/summary desynchronization.
* **Preservation of Core Invariants**: No phantom laps (duration $> 0$ or distance $> 0.0$); sequential contiguous $1 \dots K$ sequence; immutable total session metrics.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Sheet Invocation & Pre-Seeding)**: *Given* an aftermath workout, *When* tapping "Create Lap on Map" (or scrubbing to a point and launching), *Then* `CreateLapOnMapSheet` opens with route polyline, boundary markers, and movable split pin at the scrub point.
* **Criterion 2 (Reactive Split Preview)**: *Given* `CreateLapOnMapSheet` open, *When* moving the split pin or slider, *Then* preview metrics for Lap $k$ and Lap $k+1$ update reactively in real time.
* **Criterion 3 (Atomic Persistence)**: *Given* confirmed split at sample $s$, *When* applied, *Then* `LAP_NR` increments in `WorkoutSamples.db`, `Laps.db` is updated via `replaceCroppedLaps`, and UI reflects the new lap split immediately.
* **Criterion 4 (Boundary Guard)**: *Given* split pin at boundary ($s \le start_k$ or $s > end_k$), *Then* confirmation is disabled.

---

## 2. Test Specification (TST-UI-449)

### Test Case 1: Domain Math & Boundary Validation (`TST-UI-449.1`, `LapEditorMathTest.kt`)
* **Scope**: Unit test validating `calculateSplitLapsPreview` accuracy and boundary rejection preventing phantom zero-duration laps.

### Test Case 2: Multi-Database Transactional Splitting (`TST-UI-449.2`, `WorkoutSamplesDatabaseManagerLapSplitTest.java` / `WorkoutRepositoryLapSplitTest.kt`)
* **Scope**: Unit/Robolectric test asserting atomic `LAP_NR` update, `Laps.db` replacement, rollback safety, and reactive emission.

### Test Case 3: Interactive Compose Sheet Contract (`TST-UI-449.3`, `CreateLapOnMapSheetTest.kt`)
* **Scope**: Compose semantics test verifying pin/slider interaction, scrub pre-seeding, and confirmation dispatch.

### Test Case 4: 9-Language Localization Parity (`TST-UI-449.4`)
| Locale | Action Label (`split_lap_action`) | Sheet Title (`create_lap_on_map_title`) | Confirm CTA (`split_lap_confirm`) |
| :--- | :--- | :--- | :--- |
| `values` (EN) | Split Lap on Map | Create Lap on Map | Split Lap |
| `values-de` (DE) | Runde auf Karte teilen | Runde auf Karte erstellen | Runde teilen |
| `values-es` (ES) | Dividir vuelta en mapa | Crear vuelta en el mapa | Dividir vuelta |
| `values-fr` (FR) | Diviser le tour sur la carte | Créer un tour sur la carte | Diviser le tour |
| `values-it` (IT) | Dividi giro sulla mappa | Crea giro sulla mappa | Dividi giro |
| `values-ja` (JA) | マップ上でラップを分割 | マップ上でラップを作成 | ラップを分割 |
| `values-nl` (NL) | Ronde splitsen op kaart | Ronde maken op kaart | Ronde splitsen |
| `values-pl` (PL) | Podziel okrążenie na mapie | Utwórz okrążenie na mapie | Podziel okrążenie |
| `values-pt` (PT) | Dividir volta no mapa | Criar volta no mapa | Dividir volta |

### Test Case 5: Clean-Room Regression Suite (`TST-UI-449.5`)
* **Scope**: Targeted unit tests (`ui.aftermath.*`, `database.*`, `ThemeColorCentralizationAuditTest`, `build.*`, `TranslationParityTest`).

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-449.1` | Unit (Math) | `LapEditorMath.kt` | `REQ-UI-449.2`, `REQ-UI-449.4` | Specified |
| `TST-UI-449.2` | Integration / DB | `WorkoutSamplesDatabaseManager`, `WorkoutRepository` | `REQ-UI-449.3` | Specified |
| `TST-UI-449.3` | UI / Compose | `CreateLapOnMapSheet.kt`, `MapDetailLayout.kt` | `REQ-UI-449.1`, `REQ-UI-449.2` | Specified |
| `TST-UI-449.4` | Localization | `res/values*/strings.xml` | `REQ-UI-449.5` | Specified |
| `TST-UI-449.5` | Regression | Full targeted unit test suite | `REQ-PRO-001`, `REQ-UI-449` | Specified |
