# Implementation Plan - ATT-236 Extension: Global Architectural Documentation

Establish a comprehensive documentation layer across all core functional areas of the application by applying the new KDoc/JavaDoc standards to all modules analyzed or modified during recent development cycles.

## User Review Required

> [!IMPORTANT]
> - **Total Knowledge Transfer**: I am providing detailed internal documentation for 15+ critical source files, covering Mapping, Analytical Rollups, Device Integration, and Core Tracking.
> - **Standardized "How-To"**: Each method header will not just say *what* it does, but briefly explain *how* it handles threading, database I/O, or mathematical transformations.
> - **Future-Proofing**: This completes the documentation of the "active" codebase, ensuring that the entire system architecture is transparent and maintainable.

## Proposed Changes

### 1. Spatial & Mapping Layer
Fulfills REQ-PRO-011 | Test: TST-STR-015

#### [MODIFY] [ATrainingTrackerMap.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/ATrainingTrackerMap.kt)
- Document the entry point for the modular Map DSL.
- Explain the coordination between standard overlays (User Location, Scrubber) and DSL content.

#### [MODIFY] [MapContentScope.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapContentScope.kt)
- Document the tiered blending engine and zoom-adaptive multiplier schedule.
- Explain how background data (heatmaps, markers) is reactively rendered without blocking the UI.

#### [MODIFY] [MapBehaviors.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt)
- Document the high-priority camera snap logic and reactive bounds fitting.

---

### 2. Analytical & Period Layers
Fulfills REQ-PRO-011 | Test: TST-STR-015

#### [MODIFY] [PeriodsViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsViewModel.kt)
- Document the selection-driven loading algorithm and the `PeriodMapState` management.

#### [MODIFY] [PeriodsRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodsRepository.kt)
- Document the hierarchical database rollups and reactive enrichment pass.
- Explain the precision temporal filtering for Day/Week/Month/Year levels.

#### [MODIFY] [PeriodSummaryCard.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummaryCard.kt)
- Document the tiered progressive loading for mini-maps (Instant Anchors vs. Background Full History).

---

### 3. Workout Clusters & Identity
Fulfills REQ-PRO-011 | Test: TST-STR-015

#### [MODIFY] [WorkoutClustersViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt)
- Document the cluster selection job and background map processing.

#### [MODIFY] [WorkoutClusterEngine.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutClusterEngine.kt)
- Document the spatial fingerprint matching algorithm (Similarity scoring) and the learning feedback loop.

---

### 4. Core Infrastructure & Data
Fulfills REQ-PRO-011 | Test: TST-STR-015

#### [MODIFY] [TrackerService.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java)
- Document the 1Hz sampling loop, WakeLock management, and session finalization logic.

#### [MODIFY] [EncodingUtils.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/utils/EncodingUtils.kt)
- Document the delta-encoding algorithm for polyline and numeric stream compression.

#### [MODIFY] [ShareUtils.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/helpers/ShareUtils.kt)
- Document the asynchronous bitmap composition and SAF-based sharing workflow.

## Verification Plan

### Manual Verification (TST-STR-015)
- **Static Audit**: Perform a full audit of all modified files to ensure 100% header coverage for classes and public/protected methods.
- **Traceability Verification**: Re-run the requirement-to-file mapping check to ensure the implementation files are accurately documented in `requirements.md`.
