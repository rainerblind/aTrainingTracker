# Stage 3: Implementation Plan - ATT-1643: Dedicated Row and Refined Compact Sizing for Starts and Strecken Badges on KnownLocationCard

**Ticket**: [ATT-1643](https://atrainingtracker.atlassian.net/browse/ATT-1643)  
**Sub-task**: [ATT-1661](https://atrainingtracker.atlassian.net/browse/ATT-1661) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Requirement Mapping**: `REQ-UI-195`  
**Test Mapping**: `TST-UI-149`  
**Branch**: `feature/ATT-1643`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Problem Description & Background

In `KnownLocationCard`, the altitude metric, Starts-Badge, and Strecken-Badge currently share an adaptive `FlowRow`. When a location has many starts or routes, the Strecken-Badge wraps to a second line, resulting in inconsistent card heights across the list. Additionally, the badges use an explicit `defaultMinSize(minHeight = 48.dp)` container with heavy padding, making them visually disproportionate.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-195` (*Lieblingsorte: Dedicated Row and Compact Sizing for Starts and Strecken Badges on KnownLocationCard*)
* **Test Mapping**: `TST-UI-149` (*KnownLocationCard Dedicated Badges Row & Compact Sizing Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Accessibility**: Tap target accessibility remains preserved via Material 3 `Surface(onClick = ...)` semantics.
2. **Interactive Navigation**: `onShowWorkouts` and `onShowRoutes` click handlers remain intact.
3. **Card Semantics**: Card body click (`onEdit`) and universal long-press delete menu (`REQ-UI-061`) remain intact.
4. **Localization**: All 9 supported locales retain existing `@plurals/known_locations_starts` and `@plurals/known_locations_routes` strings.
5. **Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component: `KnownLocationCard` in `KnownLocationsScreen.kt`
- Extract the altitude metric into its own dedicated `Row` positioned directly beneath the location name header.
- Place the interactive badges (`Starts-Badge` and `Strecken-Badge`) inside a dedicated `FlowRow` directly beneath the altitude metric.
- Refine badge dimensions:
  - Shape: `RoundedCornerShape(8.dp)`.
  - Padding: `Modifier.padding(horizontal = 8.dp, vertical = 4.dp)`.
  - Typography: `MaterialTheme.typography.labelLarge` with `FontWeight.Bold`.
  - Leading icons: `14.dp`.
  - Trailing chevron: `12.dp`.
  - Omit `defaultMinSize(minHeight = 48.dp)` from badge containers.
  - Preserve test tags `location_starts_badge_${item.id}` and `location_routes_badge_${item.id}`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `KnownLocationCard` Layout and Styling
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt`
* **Changes**:
  - Separate altitude metric into dedicated `Row`.
  - Place badges into dedicated `FlowRow`.
  - Apply compact shape (`8.dp`), compact padding (`8.dp` / `4.dp`), `14.dp` icons, `12.dp` chevrons, and remove `defaultMinSize(minHeight = 48.dp)`.

### Step 2: Create Contract Unit Test
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationCardLayoutTest.kt`
* **Changes**: Assert dedicated row layout structure, absence of 48dp minHeight on badges, compact corner radius, and preservation of test tags.

### Step 3: Run Targeted Unit Tests
* **Command**:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*" --tests "com.atrainingtracker.trainingtracker.ui.GlobalDeleteContextMenuAuditTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests during construction, followed by clean-room full suite regression (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback Plan**: All changes isolated on `feature/ATT-1643` branch.
