# Stage 3: Implementation Plan - ATT-1955: In-Ride Fork-in-the-Road Route Selection & Decision Alerts

**Ticket**: [ATT-1955](https://rainerblind.atlassian.net/browse/ATT-1955)  
**Sub-task**: [ATT-2433](https://rainerblind.atlassian.net/browse/ATT-2433) (`[Impl-Plan] In-Ride Fork-in-the-Road Route Selection & Decision Alerts`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.16`  
**Requirement Mapping**: `REQ-MAP-031` (*In-Ride Fork-in-the-Road Route Selection & Decision Alerts*)  
**Test Spec ID**: `TST-MAP-033` (*In-Ride Fork-in-the-Road Route Selection & Decision Alerts Verification*)  
**Branch**: `feature/ATT-1955`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Architectural Overview & Boundaries (SWE.2)

```
┌────────────────────────────────────────────────────────┐
│                   UI Layer (Compose)                   │
│   SensorGridScreen.kt  <──> ForkDecisionCard.kt        │
│   (Directional arrows, route names, distances, climb)  │
└───────────────────────────▲────────────────────────────┘
                            │
┌───────────────────────────┴────────────────────────────┐
│              Repository & State Management             │
│   ForkNavigationRepository.kt                          │
│   - Observes workout location updates                  │
│   - Coordinates auto-binding & manual branch selection │
│   - Interacts with RoutesRepository                    │
└───────────────────────────▲────────────────────────────┘
                            │
┌───────────────────────────┴────────────────────────────┐
│                  Domain & Algorithmic                  │
│   ForkRouteMatcher.kt (matches outbound corridor)      │
│   RouteDivergenceDetector.kt (detects split & bearings)│
│   - Pure spherical geodesic math (Haversine)           │
│   - Zero android.location.* framework dependencies     │
└────────────────────────────────────────────────────────┘
```

---

## 2. Atomic Step-by-Step Sequencing

### Step 1: Localization Parity (9 Locales)
* Add 7 localized string tokens across all 9 `strings.xml` resource files:
  - `values/strings.xml` (EN)
  - `values-de/strings.xml` (DE)
  - `values-es/strings.xml` (ES)
  - `values-fr/strings.xml` (FR)
  - `values-it/strings.xml` (IT)
  - `values-ja/strings.xml` (JA)
  - `values-nl/strings.xml` (NL)
  - `values-pl/strings.xml` (PL)
  - `values-pt/strings.xml` (PT)
* Tokens:
  - `fork_alert_title`: "Fork Ahead" / "Gabelung voraus" / "Bifurcación más adelante" / "Bifurcation à venir" / "Biforcazione avanti" / "分岐接近" / "Splitsing vooruit" / "Rozwidlenie przed Tobą" / "Bifurcação à frente"
  - `fork_approaching_in_m`: "in %1$d m"
  - `fork_direction_left`: "Left" / "Links" / "Izquierda" / "Gauche" / "Sinistra" / "左" / "Links" / "Lewo" / "Esquerda"
  - `fork_direction_straight`: "Straight" / "Geradeaus" / "Recto" / "Tout droit" / "Dritto" / "直進" / "Rechtdoor" / "Prosto" / "Em frente"
  - `fork_direction_right`: "Right" / "Rechts" / "Derecha" / "Droite" / "Destra" / "右" / "Rechts" / "Prawo" / "Direita"
  - `fork_dismiss`: "Dismiss" / "Schließen" / "Descartar" / "Fermer" / "Ignora" / "閉じる" / "Sluiten" / "Odrzuć" / "Dispensar"
  - `fork_select_hint`: "Tap to select route" / "Tippen zum Auswählen" / "Toca para seleccionar" / "Appuyez pour choisir" / "Tocca per selezionare" / "タップして選択" / "Tik om te kiezen" / "Dotknij, aby wybrać" / "Toque para selecionar"
* Target Test: `TranslationParityTest`.

### Step 2: Pure Domain Algorithmic Engines
1. **Data Models (`ForkModels.kt`)**:
   - `ForkDirection`: `LEFT`, `STRAIGHT`, `RIGHT`
   - `ForkBranchOption`: `routeId: Long`, `routeName: String`, `totalDistanceMeters: Double`, `totalElevationMeters: Double`, `direction: ForkDirection`, `bearingDiffDegrees: Double`
   - `ForkDecisionState`: `divergenceCoordinate: LatLng`, `distanceToForkMeters: Double`, `branches: List<ForkBranchOption>`
2. **Corridor Matcher (`ForkRouteMatcher.kt`)**:
   - `findCandidateRoutes(allRoutes: List<RouteWithPath>, currentPos: LatLng, recentPoints: List<LatLng>?): List<RouteWithPath>`
   - Evaluates shared prefix $\ge 300\text{ m}$ within $\le 50\text{ m}$ spatial corridor.
3. **Divergence Detector (`RouteDivergenceDetector.kt`)**:
   - `detectDivergence(candidateRoutes: List<RouteWithPath>, currentPos: LatLng): ForkDecisionState?`
   - Identifies vertex where candidate polylines separate by $> 40\text{ m}$.
   - Computes $D_{\text{fork}}$ from current position to divergence vertex.
   - Computes approach bearing vs branch departure bearing:
     - $\Delta \theta < -20^\circ \implies \text{LEFT}$
     - $-20^\circ \le \Delta \theta \le 20^\circ \implies \text{STRAIGHT}$
     - $\Delta \theta > 20^\circ \implies \text{RIGHT}$
   - Returns non-null `ForkDecisionState` if $0 < D_{\text{fork}} \le 300\text{ m}$.
* Target Tests: `ForkRouteMatcherTest.kt`, `RouteDivergenceDetectorTest.kt`.

### Step 3: Repository & Autonomous Snapping Logic (`ForkNavigationRepository.kt`)
* State holder & controller:
  - Exposes `forkDecisionState: StateFlow<ForkDecisionState?>`.
  - Method `onLocationChanged(currentPos: LatLng, recentPoints: List<LatLng>? = null)`.
  - When actively navigating a locked route, does not trigger fork alerts.
  - When rider reaches $> 50\text{ m}$ past the fork:
    - Calculates cross-track error to each branch.
    - If cross-track $< 25\text{ m}$ on chosen branch and $> 50\text{ m}$ on others, auto-binds route via `RoutesRepository.selectRouteForNavigation(routeId)` and clears state.
    - If cross-track $> 50\text{ m}$ from all branches, dismisses state.
  - Method `selectRouteManually(routeId: Long)`: Binds route and clears prompt.
  - Method `dismissPrompt()`: Clears active fork state.
* Target Test: `ForkNavigationRepositoryTest.kt`.

### Step 4: Cockpit HUD Decision Card (`ForkDecisionCard.kt`)
* Compose UI component in `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/ForkDecisionCard.kt`:
  - Material 3 `ElevatedCard` with rounded corners, subtle surface tint.
  - Header: Alert icon, localized title with countdown distance (e.g. "Gabelung voraus (in 250 m)"), dismiss 'X' button.
  - Body: Row/Column of branch choices, each showing:
    - Relative direction icon (Left arrow, Straight arrow, Right arrow)
    - Route name
    - Distance (km) and Elevation gain (+m)
    - 1-tap selection invoking `onRouteSelected(branch.routeId)`.
* Wire into `SensorGridScreen.kt` above bottom sheet anchors.
* Target Test: `ForkDecisionCardTest.kt`.

### Step 5: Verification & Clean-Room Regression
* Run targeted unit and contract tests:
  - `TranslationParityTest`
  - `ForkRouteMatcherTest`
  - `RouteDivergenceDetectorTest`
  - `ForkNavigationRepositoryTest`
  - `ForkDecisionCardTest`
* Run full suite regression: `./gradlew testDebugUnitTest`.

---

## 3. Invariants & Guardrails
- **Zero Database Regressions**: `Routes.db` SQLite schema (v10) and DAO queries must remain completely untouched.
- **Offline Sovereignty & JVM Testability**: Pure geodesic math without `android.location.Location` framework dependencies.
- **Navigation Continuity**: Manual route selection and existing turn cues (`REQ-MAP-028`) and climb guidance (`REQ-MAP-027`) must activate immediately upon route binding.
- **Full Suite Pass Rate**: All existing 1,770+ unit tests must pass without failure.
