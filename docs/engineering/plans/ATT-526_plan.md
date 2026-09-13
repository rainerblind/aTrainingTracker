# Implementation Plan - ATT-526: Navigation Drawer Compose Migration & Item Density (ATT-243)

## 1. Executive Summary & Problem Context
Ticket **ATT-526** replaces the legacy XML-based `NavigationView` with a declarative Jetpack Compose implementation inside `MainActivityWithNavigation`, while simultaneously delivering on **ATT-243** (high-density drawer item layout) and permanently eliminating the reflection-based `InflateException` crashes revealed during incident **ATT-516**.

### Root Cause & Forensic Findings (from ATT-654 - Erledigt)
1. **Fragile XML `NavigationView` Inflation (ATT-516)**:
   - In production, `MainActivityWithNavigation.onCreate()` encountered fatal `Resources$NotFoundException` during `NavigationView.inflateMenu()`.
   - Google's Material Design `NavigationView` relies on reflection-based XML inflation of `@menu/main_navigation_drawer`. Any resource lookup mismatch or OEM drawable anomaly crashes the application on launch.
2. **Layout Rigidity Impeding Density (ATT-243)**:
   - `NavigationView` hardcodes item height in private Material dimensions (56dp). With 21 navigation items grouped across 5 categories, the legacy drawer required >1200dp total vertical height, forcing extensive scrolling on mobile screens.
   - Declarative Jetpack Compose enables fine-grained control over item height (~40-42dp), vertical padding, and typography, allowing the entire core navigation structure to fit cleanly within standard viewport heights.
3. **Imperative Menu State Synchronization**:
   - Tracking state ("Start Tracking", "Tracking", "Pause") was previously mutated imperatively via `BroadcastReceivers` calling `mNavigationView.getMenu().findItem(R.id.nav_tracking).setTitle(...)`. Moving to reactive state (`MutableState<Int>`) provides clean, predictable unidirectional data flow.

---

## 2. Requirement & Test Traceability

| Requirement ID | Description | Implementation File(s) | Verification ID | Status |
|:---|:---|:---|:---|:---|
| **REQ-UI-123** | Declarative Compose Navigation Drawer & High-Density Presentation | `main_activity_with_navigation.xml`, `AppNavigationDrawer.kt`, `MainActivityWithNavigation.java` | `TST-NAV-007` | Verified |
| **REQ-SET-050** | Navigation Drawer Hub & Item Structure | `AppNavigationDrawer.kt`, `MainActivityWithNavigation.java` | `TST-NAV-001` | Verified |
| **REQ-UI-112** | Navigation Drawer Terminology (Equipment, Synchronization, Export) | `AppNavigationDrawer.kt`, `strings.xml` | `TST-NAV-002` | Verified |
| **REQ-UI-115** | Concise Settings Terminology | `AppNavigationDrawer.kt`, `strings.xml` | `TST-UI-075` | Verified |

---

## 3. Architecture & Technical Design

### A. Hybrid Compose Integration Surface (`main_activity_with_navigation.xml`)
- Retain the parent `DrawerLayout` (`@+id/drawer_layout`) to preserve established gesture handling, drawer toggling (`mDrawerToggle`), and edge swiping.
- Replace `<com.google.android.material.navigation.NavigationView>` with `<androidx.compose.ui.platform.ComposeView>`:
  - `android:id="@+id/compose_nav_view"`
  - `android:layout_width="300dp"`
  - `android:layout_height="match_parent"`
  - `android:layout_gravity="start"`
  - `android:fitsSystemWindows="true"`
- Fragment container hosting (`@+id/content`) remains untouched.

### B. Declarative Navigation Drawer (`AppNavigationDrawer.kt`)
- **Header**: Compose representation of `headerview.xml` featuring app icon, application title ("TrainingTracker"), and Material 3 theme alignment.
- **Section Hubs & Items**:
  - **Training**: Start/Pause/Tracking (`nav_tracking`), Workouts (`nav_workouts`), Routes (`nav_routes`), Workout Clusters (`nav_workout_clusters`), History & Periods (`nav_history_periods`).
  - **Spatial**: Maps (`nav_maps`), Known Locations (`nav_known_locations`).
  - **Equipment**: Sport Types (`nav_sport_types`), Training Zones (`nav_zones`), Sensors (`nav_sensors`), Bikes/Shoes Equipment (`nav_bikes_shoes_equipment`).
  - **Synchronization**: Strava (`nav_strava`), Dropbox (`nav_dropbox`), Export (`nav_export`).
  - **System**: Audio Cues (`nav_sound`), Preferences (`nav_preferences`), Units (`nav_units`), Search (`nav_search`), Display (`nav_display`), Tracking Tabs (`nav_tracking_tabs`).
- **High-Density Presentation (ATT-243)**:
  - Item height configured at 40dp (reduced from Material 56dp).
  - Compact vertical spacing (2dp vertical padding per item, 8dp category section headers).
  - Maintains accessible touch targets and ripple feedback.
- **State & Interop API**:
  - `selectedItemId: Int`
  - `startTrackingTitleRes: Int` (observed reactively)
  - `onItemSelected: (Int) -> Unit`
  - Java-friendly interop helper `AppNavigationDrawerKt.setComposeNavigationDrawer(...)` ensuring `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed` is configured.

### C. Activity Logic Refactoring (`MainActivityWithNavigation.java`)
- Remove `NavigationView.OnNavigationItemSelectedListener` implementation.
- Remove `mNavigationView` and `mPreviousMenuItem` fields.
- Introduce reactive state holding `startTrackingTitleRes` (`androidx.compose.runtime.MutableState<Integer>`).
- Introduce direct routing method `void navigateToDrawerItem(int itemId)` replacing legacy `onNavigationItemSelected(MenuItem)`.
- Update lifecycle receivers (`mStartTrackingReceiver`, `mPauseTrackingReceiver`, `mStopTrackingReceiver`) to update the Compose state instead of querying/mutating `Menu`.
- Update programmatic navigation calls (`handleIntent`, `onBackPressed`, `mTrackingStoppedReceiver`) to invoke `navigateToDrawerItem(id)`.

---

## 4. Safety Invariants & Impact Analysis

1. **Crash Prevention (ATT-516 Invariant)**: Zero XML menu inflation during `onCreate`. Eliminates reflection-based crashes completely.
2. **Navigation Invariants**: All 21 navigation destinations and modal triggers (Tracking Tabs dialog, Display dialog, Units dialog, Export dialog) route to the exact same handlers and Fragment transactions in `R.id.content`.
3. **Lifecycle & Memory Safety**: ComposeView uses `DisposeOnViewTreeLifecycleDestroyed` to guarantee complete cleanup when the activity view tree is destroyed.
4. **Orientation & Configuration Changes**: Selected item state and tracking status state survive or re-bind accurately through activity recreation.

---

## 5. Verification Plan

### Automated Unit Tests
- `NavigationDrawerStateTest.kt`: Unit test verifying state mapping for Start/Pause/Tracking status titles.
- Full regression suite execution: `./gradlew testDebugUnitTest` ensuring zero regressions across all modules.

### Manual Verification
1. Launch `MainActivityWithNavigation` and verify the drawer opens cleanly without crash (mitigating ATT-516).
2. Verify visual density: all 21 items across 5 categories are displayed with compact ~40dp rows without requiring extreme scrolling (ATT-243).
3. Tap each navigation item to verify routing to fragments or modal dialogs.
4. Start a live workout session and verify the top drawer item dynamically updates between "Start Tracking", "Tracking", and "Pause".
