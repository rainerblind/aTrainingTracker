# Stage 3 Implementation Plan: ATT-1530 Card Header Actions & 3-Dots Removal

**Ticket**: [ATT-1530](https://atrainingtracker.atlassian.net/browse/ATT-1530)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.3`  
**Subtask**: `ATT-1533`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Overview & Architectural Design (`SWE.2`)

The goal of ATT-1530 is to simplify the visual hierarchy of `KnownLocationCard` in `KnownLocationsScreen.kt` by ensuring the legacy 3-dots overflow button (`Icons.Default.MoreVert`) is excised, the title text spans full width, and standard interactions (`onClick` -> edit, `onLongClick` -> delete) are maintained cleanly.

### Affected Components:
1. **`KnownLocationsScreen.kt`**:
   - `KnownLocationCard`: Enclosed in `MappableListItem` with `onClick = onEdit` and `onLongClick = { showContextMenu = true }`.
   - Header Row: Composes `Text(text = item.name, modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)`.
   - Zero presence of `IconButton(MoreVert)`.
2. **`KnownLocationsScreenTest.kt`**:
   - Compose UI tests verifying header layout, absence of overflow button, and click/long-click interactions.

---

## 2. Invariants & Guardrails

- **Zero-Friction Editing**: Single-tap on card MUST continue to invoke `onEdit` (`EditKnownLocationDialog`).
- **Universal Delete Contract (`REQ-UI-061`)**: Long-press MUST anchor the delete-only context menu at `TopStart`.
- **Full Text Visibility**: Card title MUST occupy `Modifier.fillMaxWidth()` with `TextOverflow.Ellipsis`.
- **Zero Foreign Disruption**: SQLite schema, DAO queries, repository coroutine serialization (`KnownLocationsDB-Thread`), and 9-language strings MUST NOT be regressed.

---

## 3. Step-by-Step Execution Sequence (Stage 4 Construction)

### Step 1: Static Architectural Verification
* Inspect `KnownLocationsScreen.kt` (`KnownLocationCard` composable) to verify that `Icons.Default.MoreVert` and redundant overflow button calls are absent.

### Step 2: Unit & UI Test Execution
* Execute targeted tests in `KnownLocationsScreenTest.kt` validating card interactions and layout structure.

### Step 3: Clean-Room Regression Verification
* Execute full `./gradlew testDebugUnitTest` suite to guarantee 100% pass rate.
