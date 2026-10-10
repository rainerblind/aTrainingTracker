# Stage 3: Implementation Plan - ATT-3104: Remove remaining grey container surfaces from in-ride fork decision card

**Ticket**: [ATT-3104](https://atrainingtracker.atlassian.net/browse/ATT-3104)  
**Sub-task**: [ATT-3140](https://atrainingtracker.atlassian.net/browse/ATT-3140) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.8`  
**Requirement Mapping**: `REQ-UI-337` (*Elimination of Lingering Grey Container Surfaces, Zero Tonal Elevation and Transparent Surface Harmonization on In-Ride Fork Decision Card*)  
**Test Mapping**: `TST-UI-297` (*In-Ride Fork Decision Card Zero Tonal Elevation & Clean Surface Verification*)  
**Branch**: `feature/ATT-3104`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Description & Background

During Sprint 2026-41.7 Joint Review of ATT-2962, the athlete observed that grey container surfaces still remain on the in-ride fork decision HUD card (`ForkDecisionCard.kt`).

Forensic analysis in Stage 1 revealed that while ATT-2962 transitioned the outer container from `surfaceVariant` to `surface`, visible grey/tinted container rectangles persist due to Material 3 elevation tinting mechanics:
1. `ForkDirectionGroup` explicitly specified `tonalElevation = 1.dp`, which triggers M3 `surfaceColorAtElevation(1.dp)` to inject a tint overlay on top of the surface, degrading pure white into tinted grey and pitch black into washed-out slate.
2. The outer `Card` specified `elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)`. In Compose Material 3, `CardElevation` couples shadow elevation to tonal elevation, applying a 6dp (~11%) tonal elevation tint across the entire outer card.
3. `ForkDirectionGroup` used `color = MaterialTheme.colorScheme.surface` with full opacity (alpha = 1.0f) rather than matching the card's `overlayAlpha`, resulting in an opaque rectangular patch artifact inside translucent card margins.

This plan details the precise atomic changes and contract tests required to eliminate all tonal elevation tinting (`tonalElevation = 0.dp`), decouple shadow elevation using `Surface`, and unify surface opacity with `overlayAlpha`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-337` (*Elimination of Lingering Grey Container Surfaces, Zero Tonal Elevation and Transparent Surface Harmonization on In-Ride Fork Decision Card*)
* **Test Mapping**: `TST-UI-297` (*In-Ride Fork Decision Card Zero Tonal Elevation & Clean Surface Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Tonal Elevation Tint Invariant**: Both the outer card container and nested directional group containers MUST enforce `tonalElevation = 0.dp` to prevent Material 3 from applying any tint or elevation overlay over pure `#FFFFFF`, `#16161A`, or `#000000` surfaces.
2. **Zero `surfaceVariant` Invariant**: No container or row background in `ForkDecisionCard.kt` shall use `surfaceVariant`.
3. **Pure Surface Palette Compliance**:
   - Light Mode: Clean pure white surfaces (`LightSurface = #FFFFFF`).
   - Dark Mode: Deep dark surfaces (`DarkSurface = #16161A`).
   - AMOLED Mode: Pure AMOLED black surfaces (`AmoledSurface = #000000`).
4. **Uniform Opacity Invariant**: Inner direction groups must apply `overlayAlpha` in parity with the outer container (`color = MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha)`), eliminating opaque visual patches.
5. **Preservation of Functional Callbacks**: `onRouteSelected(branch.routeId)` and `onDismiss()` remain fully functional.
6. **Preservation of 9-Language Localization**: All string resources remain 100% intact across EN, DE, ES, FR, IT, JA, NL, PL, PT.
7. **Sub-Task Self-Sufficiency**: Sub-tasks transition autonomously to `Erledigt` upon passing automated Gate audits (`freigabe`).
8. **Parent Human Gate Invariance**: Final completion of parent ticket `ATT-3104` is reserved exclusively for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `ForkDecisionCard.kt` Outer Container Refactoring
* Migrate outer container from `Card` to `Surface`:
  ```kotlin
  Surface(
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
      shadowElevation = 6.dp,
      tonalElevation = 0.dp,
      modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 6.dp)
  ) {
      Column(
          modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp)
      ) {
          ...
      }
  }
  ```
* **Rationale**: Compose M3 `Card` forces `tonalElevation` to match `defaultElevation` (6.dp). Using `Surface` explicitly decouples `shadowElevation = 6.dp` (preserving outer drop shadow for depth) from `tonalElevation = 0.dp` (eliminating M3 elevation color tinting).

### Component 2: `ForkDecisionCard.kt` Direction Group Refactoring
* In `ForkDirectionGroup`:
  - Pass `overlayAlpha: Float = overlayAlpha` from `ForkDecisionCard`.
  - Update `Surface` invocation:
    ```kotlin
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    )
    ```
* **Rationale**: `tonalElevation = 0.dp` removes the 1.dp M3 elevation overlay tint. `color = MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha)` guarantees opacity consistency with the outer container.

### Component 3: Contract Tests in `ForkDecisionCardTest.kt`
* In `ForkDecisionCardTest.kt`:
  - Verify that `ForkDecisionCard.kt` specifies `tonalElevation = 0.dp` for the outer container and `tonalElevation = 0.dp` for `ForkDirectionGroup`.
  - Verify that `ForkDecisionCard.kt` does not use `surfaceVariant` for any container backgrounds.
  - Verify that `ForkDirectionGroup` accepts `overlayAlpha`.

### UI Consistency (Rule 23)
* **Reference screen / component**: Existing `ForkDecisionCard.kt`, `TurnPromptBanner.kt`.
* **Reused components**: `Surface`, `RoundedCornerShape`, `BorderStroke`, `VerticalDivider`, `HorizontalDivider`.
* **Theme tokens**: `MaterialTheme.colorScheme.surface`, `MaterialTheme.colorScheme.outlineVariant`, `TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_TRANSPARENCY`.
* **New one-off styles & justification**: None. Reusing existing theme tokens and Material 3 components exclusively.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Gate Verification
* Run `python3 tools/jira_util.py check-gate ATT-3140` to confirm Gate 3 passage before code modification.

### Step 2: Implement Production Code Change
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/ForkDecisionCard.kt`
* Change 1: Replace outer `Card` with `Surface` featuring `shadowElevation = 6.dp` and `tonalElevation = 0.dp`.
* Change 2: In `ForkDirectionGroup`, add `overlayAlpha: Float = com.atrainingtracker.trainingtracker.settings.TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_TRANSPARENCY`, set `color = MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha)`, and set `tonalElevation = 0.dp`.
* Change 3: Forward `overlayAlpha = overlayAlpha` when calling `ForkDirectionGroup` from `ForkDecisionCard`.

### Step 3: Implement & Update Contract Tests
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/ForkDecisionCardTest.kt`
* Add contract test verifying strict `tonalElevation = 0.dp`, zero `surfaceVariant`, and `overlayAlpha` forwarding.

### Step 4: Targeted Unit Test Execution
* Execute:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.ForkDecisionCardTest"`
* Confirm 100% pass rate.

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit tests in Stage 4, followed by complete clean-room test suite `./gradlew testDebugUnitTest` in Stage 5.
* **Rollback Plan**: In the event of an unresolvable defect, `git checkout feature/ATT-3104` and `git reset --hard HEAD~1` restores the previous commit cleanly without impacting `sprint/2026-41.8` or `develop`.
