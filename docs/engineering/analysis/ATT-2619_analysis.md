# Stage 1 Analysis: ATT-2619 - [Bug] ResearchButtonKt.ResearchButton

**Ticket**: [ATT-2619](https://atrainingtracker.atlassian.net/browse/ATT-2619)  
**Sub-task**: [ATT-2640](https://atrainingtracker.atlassian.net/browse/ATT-2640) (`[Analysis]`)  
**Parent Epic**: [ATT-1157](https://atrainingtracker.atlassian.net/browse/ATT-1157) (*Android Modernization & Production Stability*)  
**Target Release**: `V4.9.38.3`  
**Active Sprint**: Hotfix Release  
**Branch**: `hotfix/V4.9.38.3__266`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-07  

---

## 1. Problem Statement & Motivation

During production execution of app release `4.9.38.2 (265)`, a fatal crash was reported in Crashlytics (`Issue 355c60b8489f67ef630dc85e12708945`):

```text
Fatal Exception: android.content.res.Resources$NotFoundException: Resource ID #0x7f08017c
       at android.content.res.ResourcesImpl.getValue(ResourcesImpl.java:225)
       at android.content.res.Resources.getValue(Resources.java:1428)
       at androidx.compose.ui.res.ResourceIdCache.resolveResourcePath(Resources.android.kt:38)
       at androidx.compose.ui.res.PainterResources_androidKt.painterResource(PainterResources.android.kt:62)
       at com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ResearchButtonKt.ResearchButton(ResearchButton.kt:48)
       at com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingScreenKt.ControlTrackingScreen(ControlTrackingScreen.kt:110)
```

The crash occurred immediately upon launching the tracking screen (`TrackingTabsScreen` -> `ControlTrackingScreen` -> `ResearchButton`), causing an unhandled fatal application crash for the athlete.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Failure Mechanism & Call Chain
1. In `ControlTrackingScreen.kt:110`, the `ResearchButton` composable is anchored at the top-start of the tracking control page:
   ```kotlin
   Box(modifier = Modifier.align(Alignment.TopStart)) {
       ResearchButton(
           isEnabled = searchingFor == null,
           onClick = onSearch
       )
   }
   ```
2. In `ResearchButton.kt:48`, the icon is rendered using:
   ```kotlin
   Icon(
       painter = painterResource(id = R.drawable.research_icon),
       contentDescription = null,
       modifier = Modifier.size(48.dp),
       tint = if (isEnabled) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
   )
   ```
3. The underlying asset `research_icon.png` is stored exclusively in screen-density folders:
   - `app/src/main/res/drawable-mdpi/research_icon.png`
   - `app/src/main/res/drawable-hdpi/research_icon.png`
   - `app/src/main/res/drawable-xhdpi/research_icon.png`
   - `app/src/main/res/drawable-xxhdpi/research_icon.png`
   - `app/src/main/res/drawable-xxxhdpi/research_icon.png`
4. Noticeably, there is **no** fallback `app/src/main/res/drawable/research_icon.xml` (or `research_icon.png`) in the root `drawable/` directory.
5. In production Google Play deployments utilizing Android App Bundles (AAB), APK splits are dynamically generated based on device density, ABI, and language. When an athlete runs on a device with non-standard display densities, split mismatch, or when AAPT2 / resource resolution fails during runtime configuration changes, `ResourcesImpl.getValue()` throws `Resources$NotFoundException: Resource ID #0x7f08017c`.
6. Furthermore, `painterResource(id)` relies on runtime reflection and Android `TypedValue` resolution within `ResourceIdCache`. If the resource lookup fails, Compose crashes immediately with an unhandled runtime exception on the main thread.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Migrate `ResearchButton.kt` to use Compose vector `ImageVector` (`Icons.Default.Refresh`) directly, completely eliminating runtime `Resources$NotFoundException` risks for the Compose UI.
  2. Provide a vector drawable `app/src/main/res/drawable/research_icon.xml` in the root `drawable/` folder, ensuring backward compatibility for legacy non-Compose Android components (e.g., `TrainingApplication.java` notification action builders).
  3. Ensure active and disabled visual states (`isEnabled == true` vs `isEnabled == false`), sizing (48dp), click handlers, and localization (`R.string.research`) remain 100% intact.
  4. Write comprehensive Compose unit tests verifying `ResearchButton` renders without resource failure under both enabled and disabled states.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Do NOT alter device search logic or sensor pairing state machines in `ControlTrackingViewModel` / `BANALService`.
  2. Do NOT alter notification action click behavior in `TrainingApplication.java`.
  3. Do NOT modify unrelated control buttons (Start, Pause, Stop, Map, Settings).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

### Requirement Archaeology & Chesterton's Fence Audit

Net-new requirement only (`REQ-UI-254`). No existing requirements modified.

* **Original Requirement ID & Target**: Net-new `REQ-UI-254` (*ResearchButton Vector Resilience & Resource Fallback*), targeting `ResearchButton.kt` and `res/drawable/research_icon.xml`.
* **Historical Origin & Commit Trace**: N/A (new requirement created to eliminate production crash ATT-2619).
* **Root Reason for Existing Formulation**: Previously, `ResearchButton.kt` used legacy raster asset `R.drawable.research_icon` with `painterResource()`. In Jetpack Compose, vector assets (`ImageVector`) eliminate runtime Android resource lookup failures completely.
* **Preservation of Core Invariants**: Sizing (48dp), primary color active tint, gray disabled tint, click dispatching, and string resource `@string/research` remain 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Jetpack Compose Modernization
In `ResearchButton.kt`:
Replace:
```kotlin
Icon(
    painter = painterResource(id = R.drawable.research_icon),
    contentDescription = null,
    modifier = Modifier.size(48.dp),
    tint = if (isEnabled) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
)
```
With:
```kotlin
Icon(
    imageVector = Icons.Default.Refresh,
    contentDescription = stringResource(id = R.string.research),
    modifier = Modifier.size(48.dp),
    tint = if (isEnabled) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f)
)
```
Using `imageVector = Icons.Default.Refresh`:
- Generates zero runtime resource lookups through `Resources.getValue()`.
- Renders sharply at all screen densities and scale factors.
- Provides standard accessibility content description via `stringResource(id = R.string.research)`.

### 5.2 Root Drawable Vector Fallback
Create `app/src/main/res/drawable/research_icon.xml` vector drawable representing the refresh icon. This ensures that any legacy non-Compose Android component referencing `R.drawable.research_icon` (such as `TrainingApplication.java:1166`) has an authoritative vector fallback in `res/drawable/` regardless of device screen density.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing unit tests (`testDebugUnitTest`).
  2. Visual hierarchy: 48dp size, `primary` theme color when enabled, 40% gray when disabled.
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW**
  - Modifying `ResearchButton.kt` to use `Icons.Default.Refresh` is purely declarative and eliminates the crash vector without touching business or sensor logic.
