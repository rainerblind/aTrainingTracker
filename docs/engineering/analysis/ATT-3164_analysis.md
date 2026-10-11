# Stage 1 Analysis: ATT-3164 - Prevent Resources$NotFoundException crash when loading logo in AppNavigationDrawer header

**Ticket**: [ATT-3164](https://atrainingtracker.atlassian.net/browse/ATT-3164)  
**Sub-task**: [ATT-3182](https://atrainingtracker.atlassian.net/browse/ATT-3182) (`[Analysis]`)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashs*)  
**Target Release**: `V4.9.38.5` (Hotfix)  
**Active Sprint**: `2026-41.9`  
**Branch**: `feature/ATT-3164` (integrating to `sprint/2026-41.9` and `hotfix/V4.9.38.5__269`)  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Statement & Motivation

During production execution of release `v4.9.38.4` (build 267), Firebase Crashlytics recorded a fatal crash (`Issue def5d78453112a2d2c7612693089b726`, session `6ACAEDF4025D00010EAAC6059D7655A3_DNE_0_v2`) triggered immediately upon opening the navigation drawer:

```text
Fatal Exception: android.content.res.Resources$NotFoundException: Resource ID #0x7f080128
       at android.content.res.ResourcesImpl.getValue(ResourcesImpl.java:225)
       at android.content.res.Resources.getValue(Resources.java:1428)
       at androidx.compose.ui.res.ResourceIdCache.resolveResourcePath(Resources.android.kt:38)
       at androidx.compose.ui.res.PainterResources_androidKt.painterResource(PainterResources.android.kt:62)
       at com.atrainingtracker.trainingtracker.ui.navigation.AppNavigationDrawerKt.DrawerHeader(AppNavigationDrawer.kt:305)
       at com.atrainingtracker.trainingtracker.ui.navigation.AppNavigationDrawerKt.AppNavigationDrawer(AppNavigationDrawer.kt:230)
       at com.atrainingtracker.trainingtracker.activities.MainActivityWithNavigation.onCreate(MainActivityWithNavigation.kt:377)
```

Inspection of `R.txt` confirms that `0x7f080128` maps directly to `int drawable logo_512 0x7f080128`.

Because `DrawerHeader` is composed during `MainActivityWithNavigation.onCreate()`, failing to resolve this drawable crashes the entire application process before the athlete can access any tracking features.

---

## 2. Forensic Root Cause Analysis (RCA)

### 2.1 Unhardened Direct `painterResource` Call Site
In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt` (lines 304–309):
```kotlin
Image(
    painter = painterResource(id = R.drawable.logo_512),
    contentDescription = null,
    modifier = Modifier.size(46.dp),
    contentScale = ContentScale.Fit
)
```
`androidx.compose.ui.res.painterResource` delegates directly to `Resources.getValue(id, value, true)`. If the Android `AssetManager` cannot locate the compiled asset (due to density split packaging, custom DPI device scaling, or asset table desynchronization), it throws `Resources$NotFoundException`. Standard `painterResource` performs zero exception handling, propagating the crash directly up the Compose hierarchy to take down the activity.

### 2.2 Incomplete High-Risk Call Site Hardening under REQ-UI-312
In Sprint 2026-41.5, ticket [ATT-2858](https://atrainingtracker.atlassian.net/browse/ATT-2858) established `REQ-UI-312` (*Universal Root Fallback Drawables and Guarded Compose Asset Resolution Architecture*). It introduced the defensive loader `safePainterResource(id: Int, fallback: ImageVector? = null): Painter` in `SafePainterResource.kt`:
```kotlin
@Composable
fun safePainterResource(
    @DrawableRes id: Int,
    fallback: ImageVector? = null
): Painter {
    val context = LocalContext.current
    val drawable = remember(id, context) {
        if (id <= 0) null
        else {
            try {
                ContextCompat.getDrawable(context, id)
            } catch (_: Resources.NotFoundException) {
                null
            } catch (_: Exception) {
                null
            }
        }
    }
    return when {
        drawable != null -> rememberDrawablePainter(drawable = drawable)
        fallback != null -> rememberVectorPainter(image = fallback)
        else -> remember { ColorPainter(Color.Transparent) }
    }
}
```
While `ATT-2858` hardened `SportTypeSelector.kt` and `RemoteDevices.kt`, it omitted `AppNavigationDrawer.kt`. `AppNavigationDrawer.kt:305` remained an unguarded direct `painterResource` call site.

### 2.3 Density Bucket Fragmentation
`logo_512.png` existed only in `app/src/main/res/drawable/` and `app/src/main/res/drawable-nodpi/`. It was absent from explicit density buckets (`drawable-mdpi`, `drawable-hdpi`, `drawable-xhdpi`, `drawable-xxhdpi`, `drawable-xxxhdpi`). When Google Play creates density-split APKs from an AAB, devices that resolve assets strictly from their primary density split can encounter resolution failures if the base split asset table fails to locate the bitmap.

---

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`) & Invariant Preservation

### 3.1 Existing Requirement: `REQ-UI-312`
1. **Original Requirement ID & Target**: `REQ-UI-312` (*Universal Root Fallback Drawables and Guarded Compose Asset Resolution Architecture*).
2. **Historical Origin & Commit Trace**: Established in Sprint 2026-41.5 (`ATT-2858`, commit `6047bff3`) under Epic `ATT-235` (*No crashs*).
3. **Root Reason for Existing Formulation**: Solved production crash `ATT-2856` where density-split APKs threw `Resources$NotFoundException` when loading sport type and peripheral icons.
4. **Preservation of Core Invariants**:
   - `AppNavigationDrawer.kt` directly falls under Clause 3 of `REQ-UI-312` (*High-Risk Call Site Hardening*).
   - Upgrading `AppNavigationDrawer.kt:305` to `safePainterResource` with an appropriate fallback icon (`Icons.Default.DirectionsRun` or `Icons.Default.FitnessCenter`) completely adheres to and strengthens `REQ-UI-312` without altering existing requirement invariants.
   - Populating `logo_512.png` across density buckets ensures optimal native density scaling while maintaining the universal fallback in `res/drawable/`.

---

## 4. User Scope Grounding & Non-Goals

### In-Scope:
1. Replace `painterResource(id = R.drawable.logo_512)` in `AppNavigationDrawer.kt` with `safePainterResource(id = R.drawable.logo_512, fallback = Icons.Default.DirectionsRun)`.
2. Populate `logo_512.png` across standard density folders (`drawable-mdpi`, `drawable-hdpi`, `drawable-xhdpi`, `drawable-xxhdpi`, `drawable-xxxhdpi`) to ensure split-APK density parity.
3. Author an architectural contract test asserting that `AppNavigationDrawer.kt` uses `safePainterResource` and never calls raw `painterResource` for the drawer header logo.
4. Integrate fix into `sprint/2026-41.9` and `hotfix/V4.9.38.5__269` per Rules 26 & 27.

### Non-Goals (Out of Scope):
- Redesigning the `AppNavigationDrawer` header layout, dimensions, or typography.
- Refactoring unrelated drawer menu items or navigation graph routes.

---

## 5. Target Architecture & Preventive Invariants

1. **Defensive Compose Rendering**:
   `DrawerHeader` in `AppNavigationDrawer.kt` MUST NOT invoke raw `painterResource`. It MUST invoke `safePainterResource(id = R.drawable.logo_512, fallback = Icons.Default.DirectionsRun)` so that even in an catastrophic asset failure or split corruption, the app boots cleanly and displays a placeholder vector without crashing.
2. **Universal Asset Availability**:
   `logo_512.png` SHALL reside in `res/drawable/`, `res/drawable-nodpi/`, and all density buckets (`mdpi`, `hdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`).
3. **Automated Architectural Guard**:
   An automated unit test (`AppNavigationDrawerAssetResilienceTest`) SHALL assert that `AppNavigationDrawer.kt` uses `safePainterResource` and that `R.drawable.logo_512` is resolvable across all configurations.

---

## 6. Traceability Matrix & Verification Strategy

| Requirement | Implementation Artifact | Verification Test Case | Status |
| :--- | :--- | :--- | :---: |
| **REQ-UI-312** (Clause 3: Drawer Hardening) | `AppNavigationDrawer.kt` | `TST-UI-272.4` / `AppNavigationDrawerAssetResilienceTest` | Planned |
| **REQ-UI-312** (Clause 1: Density Parity) | `res/drawable-*/logo_512.png` | `DrawableDensityFallbackAuditTest` | Planned |
