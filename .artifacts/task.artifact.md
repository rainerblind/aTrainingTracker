# Task List - ATT-462: Restore Period Marker Filtering

- `[x]` **Repository & Data Layer**
    - `[x]` Update `PeriodMapState` to use `PeriodPeakMarker` in `PeriodsViewModel.kt`
    - `[x]` Add Altitude marker generation to `enrich` in `PeriodsRepository.kt`
- `[x]` **Logic Layer (ViewModel)**
    - `[x]` Update `showPeriodMap` to generate typed `PeriodPeakMarker` for members
    - `[x]` Implement Maximum Altitude detection for member markers
- `[x]` **UI & Filtering Layer**
    - `[x]` Update `PeriodMapScreen.kt` to filter member markers reactively
    - `[x]` Update `InteractivePeriodMap.kt` to handle standard markers
- `[ ]` **Verification & Documentation**
    - `[ ]` Verify marker filtering (Start/End/Altitude/Distance)
    - `[ ]` Verify sport-type exclusion for markers
    - `[ ]` Create walkthrough artifact
    - `[ ]` Update Jira ticket status
