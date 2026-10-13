# Stage 5 Walkthrough: ATT-2865 - Preserve authentic sport type icon colors in route selector sheet

**Ticket**: [ATT-2865](https://rainerblind.atlassian.net/browse/ATT-2865)  
**Sub-task**: [ATT-2919](https://rainerblind.atlassian.net/browse/ATT-2919) (`[Test]`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.5`  
**Requirement Mapping**: `REQ-UI-320` (*Authentic Multi-Color Sport Type Icon Rendering in Route Selector Sheet*)  
**Test Spec ID**: `TST-UI-280`  
**Branch**: `improvement/ATT-2865`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Executive Summary & Verification Outcome

During sprint review and on-device inspection of `RouteSelectorSheet.kt` (`RouteCard`) introduced in Sprint 2026-41.4 (`ATT-2668`), sport type vector icons (`bsport_bike`, `bsport_run`, `bsport_other`) were rendered with a monochrome theme tint:
`tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant`.

Because `BSportType` icons are designed as rich multi-color vector assets (featuring distinct frame, wheels, body, and accent colors), applying a monochrome tint flattened the vector graphic into a single solid color silhouette. Following the precedent established in `SportTypeSelectorKt.SportItem` (`ATT-2856`), `ATT-2865` eliminates monochrome tinting by specifying `tint = Color.Unspecified`.

Active route selection continues to be unmistakably communicated via:
1. `secondaryContainer` card background color (vs `surfaceVariant` for inactive routes).
2. `primary` border outline (vs `outlineVariant` for inactive routes).
3. Dedicated `"ACTIVE"` badge chip with `primary` background and `onPrimary` text (`labelSmall`).

### Clean-Room Verification
* Contract and targeted unit tests in `RouteSelectorSheetTest.kt` passed with 100% success.
* Full clean-room test suite (`./gradlew testDebugUnitTest`) passed with 100% success (0 regressions).

---

## 2. Requirement & Test Traceability Matrix

| Requirement | Test Spec | Scope | Test Target | Result | Status |
| :--- | :--- | :--- | :--- | :---: | :---: |
| `REQ-UI-320` | `TST-UI-280.1` | Contract / Unit | `RouteSelectorSheetTest` (`testRouteCard_displaysSportIconWithAppropriateTokens`) | **PASSED** | `Verified` |
| `REQ-UI-320` | `TST-UI-280.2` | Contract / Unit | `RouteSelectorSheetTest` (`testRouteCard_preservesActiveRouteChipAndCardColors`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-280.3` | Regression | Full test suite (`./gradlew testDebugUnitTest`) | **PASSED** | `Verified` |

---

## 3. Test Execution Results

```text
RouteSelectorSheetTest > testRouteCard_displaysSportIconWithAppropriateTokens PASSED
RouteSelectorSheetTest > testRouteCard_preservesActiveRouteChipAndCardColors PASSED
RouteSelectorSheetTest > testRouteSelectorModalBottomSheet_rendersCorrectly PASSED
RouteSelectorSheetTest > testRouteSelectorModalBottomSheet_emptyState PASSED

BUILD SUCCESSFUL
32 actionable tasks: 1 executed, 31 up-to-date
```

---

## 4. Modified Files

* [RouteSelectorSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheet.kt): Updated `Icon` tint to `Color.Unspecified` in `RouteCard` to preserve multi-color vector asset rendering.
* [RouteSelectorSheetTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheetTest.kt): Updated contract tests asserting `tint = Color.Unspecified` and verifying active route container and chip styling.
* [docs/requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md): Added `REQ-UI-320` in status `Verified`.
* [docs/tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md): Added `TST-UI-280` in status `Verified`.
