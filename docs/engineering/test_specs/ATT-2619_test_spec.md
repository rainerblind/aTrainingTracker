# Stage 2: Requirement & Test Specification - ATT-2619: [Bug] ResearchButtonKt.ResearchButton

**Ticket**: [ATT-2619](https://atrainingtracker.atlassian.net/browse/ATT-2619)  
**Sub-task**: [ATT-2641](https://atrainingtracker.atlassian.net/browse/ATT-2641) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-1157](https://atrainingtracker.atlassian.net/browse/ATT-1157) (*Android Modernization & Production Stability*)  
**Target Release**: `V4.9.38.3`  
**Active Sprint**: Hotfix Release  
**Requirement Mapping**: `REQ-UI-254` (*ResearchButton Jetpack Compose Vector Resilience & Universal Resource Fallback*)  
**Test Spec ID**: `TST-UI-213`  
**Branch**: `hotfix/V4.9.38.3__266`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-07  

---

## 1. Requirement Specification (REQ-UI-254)

### 1.1 Problem Statement & Rationale
In production release `4.9.38.2 (265)`, Crashlytics captured a fatal `Resources$NotFoundException: Resource ID #0x7f08017c` when composing `ResearchButton` on `ControlTrackingScreen`. The root cause was that `ResearchButton.kt` loaded `R.drawable.research_icon` through `painterResource()`, while `research_icon.png` existed solely in density-specific folders without a universal vector or default fallback in `res/drawable/`. On devices running split APK configurations or non-standard display densities, resource resolution failed at runtime, crashing the application upon screen launch.

### 1.2 Functional & Architectural Requirements
The system SHALL guarantee runtime immunity against `Resources$NotFoundException` when rendering `ResearchButton` and ensure universal fallback resolution for research icon assets across all device configurations and screen densities:
1. *Jetpack Compose Vector Modernization (`ResearchButton.kt`)*:
   - `ResearchButton.kt` SHALL render its icon using `Icons.Default.Refresh` (`ImageVector`) directly within Compose code, eliminating runtime Android resource lookups via `Resources.getValue()` / `painterResource()` and preventing `Resources$NotFoundException` crashes on non-standard device densities or split-APK configurations.
   - `Icon` SHALL supply `contentDescription = stringResource(id = R.string.research)` to provide accessible screen reader labels.
2. *State & Visual Parity*:
   - `ResearchButton` SHALL preserve 48dp dimension (`Modifier.size(48.dp)`).
   - The icon tint SHALL evaluate to `MaterialTheme.colorScheme.primary` when `isEnabled == true`, and `Color.Gray.copy(alpha = 0.4f)` when `isEnabled == false`.
   - Click interactions SHALL remain bound to `Modifier.clickable(enabled = isEnabled, onClick = onClick)`.
   - Text label SHALL display `stringResource(id = R.string.research)` with `MaterialTheme.typography.labelMedium`.
3. *Universal Resource Fallback (`res/drawable/research_icon.xml`)*:
   - The system SHALL provide a vector drawable `app/src/main/res/drawable/research_icon.xml` in the root `drawable/` directory.
   - This ensures that non-Compose Android system callers (e.g. `TrainingApplication.java` notification action builders referencing `R.drawable.research_icon`) resolve the drawable safely without throwing `Resources$NotFoundException` across all device form factors and screen densities.
4. *9-Language Localization Parity*:
   - String resource `research` SHALL be verified with 100% parity across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1**:
  * *Given* an athlete on the tracking control screen (`ControlTrackingScreen`),
  * *When* `ResearchButton` composable is composed under any display density or split APK configuration,
  * *Then* `ResearchButton` SHALL render `Icons.Default.Refresh` without throwing `Resources$NotFoundException`.
* **Criterion 2**:
  * *Given* `ResearchButton` with `isEnabled == true`,
  * *When* rendered,
  * *Then* the icon tint SHALL evaluate to `MaterialTheme.colorScheme.primary` and the button SHALL respond to tap gestures.
* **Criterion 3**:
  * *Given* `ResearchButton` with `isEnabled == false`,
  * *When* rendered,
  * *Then* the icon tint SHALL evaluate to `Color.Gray.copy(alpha = 0.4f)` and tap gestures SHALL be disabled.
* **Criterion 4**:
  * *Given* any Android caller resolving `R.drawable.research_icon` via `context.getDrawable(R.drawable.research_icon)`,
  * *Then* the resource SHALL resolve to a non-null Drawable without throwing `Resources$NotFoundException`.

### 1.4 System Invariants
1. Zero regression in existing unit and UI tests.
2. Tracking control layout, sensor pairing triggers, and 9-language localization parity MUST NOT be broken.
3. Parent ticket Human Decision Gate remains strictly enforced.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-254`), targeting `ResearchButton.kt` and `res/drawable/research_icon.xml`.
* **Historical Origin & Commit Trace**: Hotfix release `V4.9.38.3` addressing production crash `ATT-2619` (`Issue 355c60b8489f67ef630dc85e12708945`).
* **Root Reason for Existing Formulation**: `ResearchButton.kt` previously loaded `R.drawable.research_icon` through `painterResource()`. Because `research_icon.png` was only present in density-specific folders without a default `res/drawable/` fallback, runtime resolution threw `Resources$NotFoundException` on certain device density configurations.
* **Preservation of Core Invariants**: 48dp sizing, primary/disabled tinting, click handling, and 9-language translation parity are 100% preserved.

---

## 2. Test Specification (TST-UI-213)

### Test Case 1: `testResearchButton_rendersWithoutResourceNotFoundException` (`TST-UI-213.1`)
* **Scope**: Unit / Compose Rendering Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ResearchButtonTest.kt`
* **Preconditions**: Robolectric Compose test environment initialized.
* **Action**:
  - Render `ResearchButton(isEnabled = true, onClick = {})`.
  - Render `ResearchButton(isEnabled = false, onClick = {})`.
* **Expected Result**:
  - Zero `Resources$NotFoundException` thrown.
  - Component renders successfully with appropriate text label and content description.

### Test Case 2: `testResearchIconDrawable_resolvesFromResources` (`TST-UI-213.2`)
* **Scope**: Unit Test / Resource Fallback
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/ResearchButtonTest.kt`
* **Preconditions**: Android application Context available.
* **Action**: Invoke `ContextCompat.getDrawable(context, R.drawable.research_icon)`.
* **Expected Result**: Returns a non-null `Drawable` without throwing `Resources.NotFoundException`.

### Test Case 3: 9-Language Localization & Specifier Audit (`TST-UI-213.3`)
* **Scope**: Localization Parity Test
* **Goal**: Verify string `research` exists and is non-empty across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing entries.

### Test Case 4: Clean-Room Regression Suite (`TST-UI-213.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across all modules with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-213.1` | Unit (Compose) | `ResearchButton` | `REQ-UI-254` | Specified |
| `TST-UI-213.2` | Unit (Resources) | `R.drawable.research_icon` | `REQ-UI-254` | Specified |
| `TST-UI-213.3` | Localization | `research` (all 9 locales) | `REQ-UI-254`, `REQ-UI-106` | Specified |
| `TST-UI-213.4` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
