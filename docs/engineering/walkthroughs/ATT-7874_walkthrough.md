# Stage 5 Walkthrough: ATT-7874 - Fix OutOfMemoryError from leaked TuningPreferencesDataStore scopes and offscreen Cockpit maps

**Ticket**: [ATT-7874](https://rainerblind.atlassian.net/browse/ATT-7874)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*No Crashes & App Stability (Crash-Free Resilience)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.8`  
**Requirement Mapping**: `REQ-TRK-079` (`REQ-TRK-079.1`..`REQ-TRK-079.3`) [Verified]  
**Test Spec Mapping**: `TST-TRK-079` (`TST-TRK-079.1`..`TST-TRK-079.5`) [Verified]  
**Author**: AI Agent 2 (`subagent_flash`) | **Date**: 2026-11-16  

---

## 1. Executive Summary & Verification Walkthrough
This ticket eliminates fatal 512 MB Java heap `OutOfMemoryError` and native JNI `SIGABRT` crashes during multi-hour background tracking:
1. **Application-Scoped DataStore Lifecycle (`REQ-TRK-079.1`)**:
   - Scoped `TuningPreferencesDataStore` constructor to `private` (with `createForTesting`), strictly normalized context to `applicationContext`, and bound startup purge maintenance to `appScope`.
   - Migrated all 19 direct constructor call sites across UI composables and ViewModels to `TuningPreferencesDataStore.getInstance(context)`.
2. **Bounded Viewport & Inactive Tab Map Disposal (`REQ-TRK-079.2`)**:
   - Bounded `HorizontalPager.beyondViewportPageCount = 0` in `TrackingTabsScreen`, eliminating offscreen Cockpit tab pre-composition.
   - Gated `TrackingMapContainer` on `isTabActive == true` to dispose underlying `GoogleMap` composables and native map renderer buffers on inactive tabs.
3. **Lifecycle-Gated Map Telemetry (`REQ-TRK-079.3`)**:
   - Wired `TrackingTabGridContent` via `repeatOnLifecycle(STARTED)` to toggle `viewModel.setUiVisible(visible)`.
   - `TrackingViewModel.observeMapState()` suppresses `TrackingMapState` emissions when `uiVisible == false`, preventing 1-Hz Polyline allocations during screen-off while `TrackerService` 1-Hz background recording continues uninterrupted.

---

## 2. Verification Evidence & Test Execution

### Targeted Tier 1 Unit Tests (Stage 4 Commit SHA: `416d3dc0`)
* **Test Command**:
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*"
  ```
* **Execution Results**: **682 / 682 unit tests PASSED** in 28.5s (exit code 0).
  - `TuningPreferencesDataStoreLifecycleTest`: Verified singleton identity across repeated calls, application context normalization, and lack of Activity retention.
  - `TrackingViewModelMapStateTest`: Verified map telemetry emissions pause when `isUiVisible` is false and resume when true.
  - `TrackingTabsScreenLayoutTest`: Verified bounded page pre-composition and layout integrity.

### Living Documentation Governance & Traceability
* **Command**: `python3 tools/verify_requirement_governance.py`
* **Result**: **11 / 11 checks PASSED** (exit code 0), 100% master table parity (`REQ-TRK-079` & `TST-TRK-079` marked `[Verified]`), 0 dangling references.

---

## 3. Requirement Verification & Acceptance Matrix

| Requirement | Test Spec | Verification Vector | Result | Living Doc Status |
| :--- | :--- | :--- | :---: | :---: |
| `REQ-TRK-079.1` | `TST-TRK-079.1` | Unit test (`testTuningPreferencesDataStore_singletonAndApplicationContextNormalization`) | **PASSED** | `[Verified]` |
| `REQ-TRK-079.2` | `TST-TRK-079.2` | Unit test (`testHorizontalPager_boundedViewportAndInactiveTabMapDisposal`) | **PASSED** | `[Verified]` |
| `REQ-TRK-079.3` | `TST-TRK-079.3` | Unit test (`testTrackingViewModel_observeMapState_gatedOnStartedLifecycle`) | **PASSED** | `[Verified]` |
| `REQ-TRK-079.3` | `TST-TRK-079.4` | Integration test (`testTrackerService_backgroundPersistenceNeutrality`) | **PASSED** | `[Verified]` |
| `REQ-PRO-001` | `TST-TRK-079.5` | Tier 1 unit test regression suite (682/682 tests) | **PASSED** | `[Verified]` |
