# Stage 1 Analysis: ATT-4667 - Add @JvmOverloads to WorkoutClustersViewModel constructor to fix NoSuchMethodException crash on Clusters screen

**Ticket**: [ATT-4667](https://rainerblind.atlassian.net/browse/ATT-4667)  
**Sub-task**: [ATT-4682](https://rainerblind.atlassian.net/browse/ATT-4682) (`[Analysis]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.7` | **Branch**: `feature/ATT-4667`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Statement & Motivation
Navigating to the Favorite Tracks (Workout Clusters) screen or Known Locations screen in `ATrainingTrackerApp.kt` / `WorkoutClustersScreen.kt` causes a fatal runtime crash:
`java.lang.NoSuchMethodException: com.atrainingtracker.trainingtracker.ui.clusters.WorkoutClustersViewModel.<init> [class android.app.Application]`.
The defect completely blocks users from viewing, selecting, and managing route clusters on device.

---

## 2. Root Cause Analysis (Forensic Investigation)
* **Bytecode Constructor Erasure**: In `ATT-4003` (commit `6073cf43`), `WorkoutClustersViewModel.kt` (L86–89) added `private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO` to offload sport equipment mapping (`REQ-DAT-033.3`), but omitted `@JvmOverloads constructor`.
* **Framework Reflection Failure**: In `ATrainingTrackerApp.kt` (L402, L445) and `WorkoutClustersScreen.kt` (L71), `WorkoutClustersViewModel` is instantiated via default `viewModel()` / `viewModel(activity)`. AndroidX's `ViewModelProvider.AndroidViewModelFactory` attempts reflection on `WorkoutClustersViewModel.class.getConstructor(Application.class)`. Because Kotlin only emitted `(Application, CoroutineDispatcher)` and `(Application, CoroutineDispatcher, Int, DefaultConstructorMarker)` in bytecode, reflection fails with `NoSuchMethodException`.
* **Codebase ViewModel Audit**: Audited all 18 `AndroidViewModel` subclasses across `app/src/main`. All others instantiated without custom factories (`DeviceListViewModel`, `EquipmentViewModel`, `KnownLocationsViewModel`, `MapFragmentWithTrackViewModel`, `RoutesViewModel`, `TrackOnMapAftermathViewModel`, `BackupRestoreViewModel`) already declare `@JvmOverloads constructor` or secondary 1-arg constructors (`ZonesSettingsViewModel`), or have strictly 1-arg constructors (`PeriodsViewModel`, `SportTypeViewModel`, `AthleteProfileViewModel`, `EditDeviceViewModel`, `WorkoutSummariesViewModel`). `WorkoutClustersViewModel` is the sole unannotated class with default parameters.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Add `@JvmOverloads constructor` to `WorkoutClustersViewModel` primary constructor.
  * Implement an automated reflection contract unit test (`AndroidViewModelReflectionContractTest.kt`) asserting that every `AndroidViewModel` subclass instantiated via default framework factory exposes an accessible 1-arg `(Application.class)` constructor.
  * Verify `docs/coding_standards.md` §3.5 codifies the mandatory `@JvmOverloads constructor` rule for reflective Android framework classes (consolidated from duplicate `ATT-4663`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No architectural changes or factory wrapping for ViewModels requiring runtime arguments (`CropWorkoutViewModel`, `EditWorkoutViewModel`, `EditSensorFieldViewModel`, `TrackingTabsViewModel`, `SegmentListViewModel`).
  * No modifications to `WorkoutClustersViewModel` clustering algorithms, StateFlows, or DB queries.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-ARC-004` (*Android Framework Reflection Constructor Compatibility & Lifecycle ViewModel Factory Hygiene*), extending `REQ-ARC-003` under Epic `ATT-431` (*Clean Code*), verified by `TST-ARC-004`. Targets `WorkoutClustersViewModel.kt`, `AndroidViewModelReflectionContractTest.kt`, and `docs/coding_standards.md`.
* **Historical Origin & Commit Trace**: Commit `6073cf43` (`ATT-4003`, Sprint `2026-43.6`), introducing `ioDispatcher` for non-blocking sport equipment mapping (`REQ-DAT-033.3`).
* **Root Reason for Existing Formulation**: `ioDispatcher` was introduced to satisfy `REQ-DAT-033.3` without realizing that Kotlin constructor default arguments do not generate single-arg Java bytecode overloads without `@JvmOverloads`.
* **Preservation of Core Invariants**: Retains `ioDispatcher: CoroutineDispatcher = Dispatchers.IO` for coroutine testing and background execution; preserves all existing cluster caching, route linking, and UI StateFlow contracts; 100% clean-room test pass rate.

---

## 5. Architectural Strategy & High-Level Solution
* **Constructor Annotation**: Add `@JvmOverloads constructor` to `WorkoutClustersViewModel`:
  ```kotlin
  class WorkoutClustersViewModel @JvmOverloads constructor(
      application: Application,
      private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
  ) : AndroidViewModel(application)
  ```
* **Governance Contract Test**: Author `AndroidViewModelReflectionContractTest.kt` in `app/src/test/.../build/`. It scans all `AndroidViewModel` subclasses instantiated via default `viewModel()` / `AndroidViewModelFactory` and asserts `clazz.getConstructor(Application::class.java)` succeeds and is public.
* **Standards Enforcement**: Maintain §3.5 in `docs/coding_standards.md` prohibiting reflective framework classes with default parameters lacking `@JvmOverloads constructor`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression in existing `WorkoutClustersViewModel` logic and tests (`WorkoutClustersViewModelTest`).
  2. Bytecode compatibility with `ViewModelProvider.AndroidViewModelFactory` across all Android API levels.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** — Adding `@JvmOverloads constructor` emits an additional 1-arg JVM bytecode constructor without altering runtime execution flow or coroutine scheduling.
