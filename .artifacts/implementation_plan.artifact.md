# Implementation Plan - ATT-236: Mandatory Code Documentation

Establish a new professional standard for internal code documentation by updating the project protocol and adding comprehensive KDoc/JavaDoc comments to core system components, starting with the WorkoutRepository.

## User Review Required

> [!IMPORTANT]
> - **Mandatory Documentation Standard**: I am updating the `project_protocol.md` to require KDoc (for Kotlin) and JavaDoc (for Java) headers for all classes and methods. This ensures that the "Why" and "How" of the code are always documented alongside the implementation.
> - **Core Logic Documentation**: I am starting with `WorkoutRepository.kt`, providing detailed descriptions for its 60+ methods to ensure this critical data hub is easy to maintain.
> - **Future Enforcement**: This new rule will apply to all future code changes made by AI agents or human developers.

## Proposed Changes

### 1. Process Layer: Protocol Update
Fulfills REQ-PRO-011 | Test: TST-STR-015

#### [MODIFY] [project_protocol.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/project_protocol.md)
- Add a new section **"Internal Documentation Standards"** under Mandatory Development Workflow.
- Mandate class-level headers: Purpose and architectural role.
- Mandate method-level headers: Functional description and implementation logic.

### 2. Documentation Layer: Core Repository Enrichment
Fulfills REQ-PRO-011 | Test: TST-STR-015

#### [MODIFY] [WorkoutRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt)
- Add class-level KDoc.
- Add KDoc headers to all public and internal methods, including:
    - `loadAllWorkouts()`: Describing the progressive loading strategy and StateFlow emission.
    - `getWorkoutTrackPoints()`: Explaining the dynamic track selection and decoding logic.
    - `saveWorkout()`: Detailing the database update and memory synchronization process.
    - `getWorkoutMarkers()`: Documenting the extrema visualization rules.

### 3. Requirements & Verification
#### [MODIFY] [requirements.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md)
- Add **REQ-PRO-011**: Mandatory Code Documentation.

#### [MODIFY] [tests.md](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md)
- Add **TST-STR-015**: Documentation Audit.

## Verification Plan

### Manual Verification (TST-STR-015)
- **Static Audit**: Inspect `WorkoutRepository.kt` to ensure every method has a descriptive header.
- **Protocol Compliance**: Verify that the new rule is clearly stated in `project_protocol.md`.
