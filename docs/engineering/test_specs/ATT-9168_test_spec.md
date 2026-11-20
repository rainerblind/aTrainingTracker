# Stage 2: Requirement & Test Specification - ATT-9168: Fix Invisible Offscreen `WorkoutHeader` Intercepting Clicks in `MapDetailLayout`

**Ticket**: `ATT-9168` (`1 SP`, `Bug`, `severity-major`) | **Parent Epic**: `ATT-68` | **Target Release**: `V5.0.0`  
**Active Sprint**: `465` (`2026-11-20_01_Früh`) | **Branch**: `feature/ATT-9168` (base `49a2f16a`)  
**Requirement Mapping**: `REQ-REV-015` (`REQ-REV-015.1`–`.2`)  
**Test Spec ID**: `TST-REV-016` (`TST-REV-016.1`–`.2`) | **Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-20

---

## 1. Requirement Specification (`REQ-REV-015`)

### 1.1 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit
- **Implementation**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt` (`1,062 / 1,500` LoC)

### 1.2 Functional & Architectural Requirements
1. **On-Demand Composition & Hit-Test / Semantics Isolation (`REQ-REV-015.1`)**:
   - When `showHeader == false` and `!isSharing && !externalSnapshotTrigger`, `MapDetailLayout` **SHALL NOT** compose or place `header()` (`snapshotHeader`), preventing invisible pointer interception on `Sichtbare Diagramme`, `Tempo` multi-source layers icon, and top-viewport controls, and eliminating duplicate `WorkoutHeader` accessibility/TalkBack semantics.
   - When `showHeader == false` and `(isSharing || externalSnapshotTrigger)` is `true`, `MapDetailLayout` **SHALL** compose `header()` outside the `.zIndex(1f)` upper `Column` inside a `.zIndex(-1f)` off-viewport container (`placeable.place(...)` outside visible bounds) wrapped with `.clearAndSetSemantics {}` so it stays non-interactive and invisible to TalkBack even during snapshot capture.
2. **Frame-Synchronized `GraphicsLayer` Recording (`REQ-REV-015.2`)**:
   - When Share Snapshot is triggered (`isSharing` or `externalSnapshotTrigger`) with `showHeader == false`, `MapDetailLayout` **SHALL** synchronize snapshot capture with the Compose draw pass (`isHeaderLayerRecorded` / `withFrameNanos {}` before setting `shouldTakeSnapshot = true` on `ATrainingTrackerMap` and/or before `headerLayer.toImageBitmap()`) so `headerLayer.size.width > 0 && headerLayer.size.height > 0` and the exported Share Snapshot bitmap always contains the full `WorkoutHeader`.
   - Standalone `showHeader = true` layout, `REQ-REV-006.5` UI-thread rasterization, and `MapDetailLayout.kt <= 1,500` LoC **MUST** remain preserved (`Localization Parity: N/A - no new or modified UI strings`).

## 2. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
- *Original Requirement ID & Target*: Net-new `REQ-REV-015`, refining `REQ-REV-006` (`REQ-REV-006.3`–`.4`, `docs/requirements/req_aftermath_reviews.md:85-115`) and `REQ-PER-029` (`docs/requirements/req_aftermath_periods.md:867-896`); targets `MapDetailLayout.kt`.
- *Historical Origin & Commit Trace*: `4fb53a57` (`ATT-6322`: `showHeader = false` offscreen `headerLayer` recording), `d4e6cd52` (`ATT-6685`: UI-thread `GraphicsLayer` rasterization), `3652c31c` (`ATT-8080`: `Sichtbare Diagramme` & multi-source chart overlays).
- *Root Reason for Existing Formulation*: `ATT-6322` eliminated duplicate visible headers below the tab bar while keeping `headerLayer` permanently recorded for Share Snapshot, inadvertently placing invisible `header()` bounds at `(0, 0)` inside `.zIndex(1f)` over top-viewport controls.
- *Preservation of Core Invariants*: `showHeader = false` still suppresses duplicate visible headers; Share Snapshot (`headerLayer.toImageBitmap().asAndroidBitmap()` on UI thread + `combineWorkoutAndShare`) still captures the full `WorkoutHeader` bitmap; standalone `showHeader = true` visual parity and `MapDetailLayout.kt <= 1,500` LoC remain intact.

## 3. Acceptance Criteria
- **AC-1 (`REQ-REV-015.1` — Idle Click-Through & Semantics Isolation)**:
  - *Given* `MapDetailLayout(showHeader = false)` idle (`!isSharing && !externalSnapshotTrigger`) with `Sichtbare Diagramme` and `Tempo` layers buttons at the top of the viewport, *When* rendered and tapped, *Then* `snapshotHeader` is NOT composed, zero duplicate `WorkoutHeader` semantics nodes exist, and both `Sichtbare Diagramme` and `Tempo` layers clicks trigger reliably.
- **AC-2 (`REQ-REV-015.2` — On-Demand Frame-Synchronized Snapshot Capture)**:
  - *Given* Share Snapshot triggered (`isSharing` or `externalSnapshotTrigger`) with `showHeader = false`, *When* Compose draws `snapshotHeader` inside `.zIndex(-1f)` / off-viewport `.clearAndSetSemantics {}` and `executeSnapshotCaptureAndShare()` captures `headerLayer`, *Then* `headerLayer` is recorded before capture (`size > 0`), yielding a full `WorkoutHeader` bitmap with zero pointer/accessibility leaks.

## 4. Test Specification (`TST-REV-016`)
- **`TST-REV-016.1` (Idle Click-Through & Zero Duplicate Semantics — `MapDetailLayoutTest.kt`, `androidx.compose.ui.test.junit4.v2.createComposeRule`)**:
  - Render `MapDetailLayout(showHeader = false, showMap = false, showTelemetryCharts = true, ...)` with a clickable `snapshotHeader` and top-viewport `Sichtbare Diagramme` / `Tempo` layers controls; assert `snapshotHeader` does not exist (`assertDoesNotExist()`), and clicking both top-viewport controls triggers their callbacks (`0` deprecation warnings, `0` `src/main` reads).
- **`TST-REV-016.2` (On-Demand Frame-Synced `headerLayer` Capture & Contracts — `MapDetailLayoutTest.kt`, `MapDetailLayoutSnapshotContractTest.kt`, Contracts)**:
  - Trigger `externalSnapshotTrigger = true` (or `isSharing = true`) with `showHeader = false`, verify `headerLayer.record` executes with non-zero bounds before `shouldTakeSnapshot` / `toImageBitmap()`, and verify zero duplicate `WorkoutHeader` semantics nodes (`.clearAndSetSemantics {}`); run `MapDetailLayoutSnapshotContractTest`, `ArchitectureConformanceContractTest` (`MapDetailLayout.kt <= 1,500` LoC), and `SourceTextReadingAuditContractTest` (`0` new `src/main` reads; `Localization Parity: N/A - no new or modified UI strings`).

## 5. Traceability Matrix

| Test Case | Scope | Test Suite(s) | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-REV-016.1` | Idle `showHeader = false` non-composition, click-through on `Sichtbare Diagramme` & `Tempo` layers, zero duplicate semantics | `MapDetailLayoutTest` (`v2.createComposeRule`) | `REQ-REV-015.1` | Planned |
| `TST-REV-016.2` | On-demand `.zIndex(-1f)`/`.clearAndSetSemantics {}` frame-synced `headerLayer` recording & contracts | `MapDetailLayoutTest`, `MapDetailLayoutSnapshotContractTest`, `ArchitectureConformanceContractTest`, `SourceTextReadingAuditContractTest` | `REQ-REV-015.2` | Planned |
