# Stage 1 Analysis: ATT-7757 - Trim whitespace on RouteCluster names and self-heal existing entries

**Ticket**: [ATT-7757](https://rainerblind.atlassian.net/browse/ATT-7757)  
**Sub-task**: Pending creation via `submit-stage 1` (`[Analysis]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Lieblingsstrecken / Route Clusters & Auto-Naming*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.7` | **Branch**: `feature/ATT-7757`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Problem Statement & Motivation
Cluster names saved with leading/trailing whitespace (e.g., `"Kurze Runde "` appended by soft-keyboard autocomplete) cause double spaces in auto-named workouts (`"Kurze Runde  #2"` via `formatClusterWorkoutName`), duplicate-looking clusters bypassing `isNameTaken` / `findUniqueClusterName`, and asymmetric chip padding. Legacy untrimmed rows already persisted in `RouteClusters.db` remain broken unless normalized on startup/refresh.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * Verified `EditWorkoutViewModel.createNewCluster` (`EditWorkoutViewModel.kt:516`) trims `customName`, whereas:
    1. `WorkoutClustersViewModel.updateClusterIdentity` (`WorkoutClustersViewModel.kt:560`) and `EditWorkoutClusterIdentityDialog` (`WorkoutClusterHeatmapScreen.kt:1197`) pass raw untrimmed `newName`.
    2. `WorkoutClustersViewModel.addManualCluster` (`:683`), `executeClusterSplit` (`:811`), `WorkoutClusterEngine.manuallyCreateCluster` (`WorkoutClusterEngine.kt:1548`), `syncRouteNameChange` (`:615`), `findUniqueClusterName` (`:794`), and `formatClusterWorkoutName` (`:103`) omit `.trim()` and/or blank-rejection.
    3. `WorkoutClusterDatabaseManager.insertCluster` (`:473`), `updateCluster` (`:478`), and `isNameTaken` (`:569`) persist/query raw untrimmed names (`getClusterByName` at `:510` already trims).
    4. `WorkoutClusterRepository.updateCluster` (`:424`), `updateClusterSync` (`:679`), and `RoutesRepository.syncClusterNameChange` (`:324`) lack `.trim()` / blank guards, and `loadAllClustersLightweight` (`:161`) / `refreshClusters` (`:235`) heal `hitCount`/apexes but not untrimmed names.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Normalize `.trim()` and reject blank-only names across all `WorkoutCluster` write, lookup, and formatting paths.
  * Self-heal existing untrimmed `RouteClusters.name` entries in `WorkoutClusterRepository` (`loadAllClustersLightweight` / `refreshClusters`) with collision resolution (`findUniqueClusterName` logic) and propagate healed names to `WorkoutData.clusterName` and linked `Routes`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No SQLite schema `DB_VERSION` bump or destructive migration; no internal-whitespace collapsing; no changes to `req_settings_profiles.md` or `tst_settings_profiles.md`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* Net-new requirement only (`REQ-PER-041` in `docs/requirements/req_aftermath_periods.md`). No existing requirements modified (`REQ-PER-040` and `REQ-SET-089` invariants remain intact).

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-PER-041` (`docs/requirements/req_aftermath_periods.md`, `Target Release: V5.0.0`); preserves `REQ-PER-040` (`ATT-7758`).
* **Affected Architecture Elements**: `WorkoutClusterDatabaseManager.kt`, `WorkoutClusterEngine.kt`, `WorkoutClusterRepository.kt`, `WorkoutClustersViewModel.kt`, `EditWorkoutViewModel.kt`, `WorkoutClusterHeatmapScreen.kt`, `RoutesRepository.kt`.
* **Verification Impact & Test Surface**: `TST-PER-049` (`WorkoutClusterDatabaseManager` / `WorkoutClusterEngine` / `WorkoutClusterRepository` / `WorkoutClustersViewModel` / `EditWorkoutViewModel` unit tests) + targeted package tests + `:app:lintDebug` + `detekt`.
* **Risk Assessment & Invariants**: `LOW` risk; preserves `WorkoutData.workoutName` immutability, non-recursive `Route <-> Cluster` sync (`ATT-7758`), and plain JVM isolation (`coding_standards.md` §5.5).

---

## 6. Architectural Strategy & High-Level Solution
1. Expose a shared `internal` pure helper `WorkoutClusterEngine.findUniqueClusterName(...)` (trimming `baseName` and comparing trimmed existing names) and sanitize `formatClusterWorkoutName` (`clusterName.trim()`), `syncRouteNameChange`, `splitCluster`, `manuallyCreateCluster`, and `createNewClusterFromWorkout`.
2. Normalize `.trim()` + blank guards at DB/Repo/VM/UI entry points (`WorkoutClusterDatabaseManager`, `WorkoutClusterRepository`, `RoutesRepository.syncClusterNameChange`, `WorkoutClustersViewModel`, `EditWorkoutViewModel`, `EditWorkoutClusterIdentityDialog`).
3. Add a single shared `healUntrimmedClusterNames` pass in `WorkoutClusterRepository` invoked by both `loadAllClustersLightweight()` and `refreshClusters()` (before fast-path return) to trim untrimmed names in-place with collision resolution and propagate healed names via `notifyRoutesRepository`, `notifyWorkoutRepository { it.onClusterRenamed(...) }`, and `_clusterMutationEvents`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression in existing clustering, auto-naming, and `ATT-7758` in-memory sync tests.
  2. `ANT-Android-SDKs` submodule untouched (`160000`); no edits to `req_settings_profiles.md` / `tst_settings_profiles.md`.
  3. Parent ticket transitions stop at `Final Review (Human)`.
* **Risk Rating**: **LOW** — additive normalization and idempotent startup/refresh self-healing with zero schema or API-breaking changes.
