# Stage 1: Problem Domain & Root Cause Analysis - ATT-1955: In-Ride Fork-in-the-Road Route Selection & Decision Alerts

**Ticket**: [ATT-1955](https://rainerblind.atlassian.net/browse/ATT-1955)  
**Sub-task**: [ATT-2431](https://rainerblind.atlassian.net/browse/ATT-2431) (`[Analysis] In-Ride Fork-in-the-Road Route Selection & Decision Alerts`)  
**Parent Epic**: [ATT-66](https://rainerblind.atlassian.net/browse/ATT-66) (*[Epic] Improve Routes*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-40.16`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Problem Statement & User Value

Endurance athletes frequently depart on training rides or long runs along familiar departure corridors (e.g. initial 2–5 km exiting their hometown) without having chosen a specific route beforehand. As the athlete progresses along the shared corridor, they approach critical forks in the road where saved routes diverge (e.g. Left fork $\to$ moderate 38 km rolling loop; Right fork $\to$ challenging 65 km hilly loop).

Currently:
1. **Decision Friction & Interruption**: The athlete must either memorize the branches in advance or interrupt their workout, stop at the side of the road, remove gloves, and manually search their saved routes list on their smartphone.
2. **Missing In-Flight Context**: Athletes cannot make spontaneous, informed routing decisions based on their current pacing, energy reserves, wind conditions, or remaining daylight because comparative route metrics (distance, elevation gain, hill profiles) are not available when approaching the junction.
3. **Delayed Navigation Activation**: If the athlete rides past the fork without selecting a route, active navigation capabilities—such as turn-by-turn prompts (`REQ-MAP-028`), live climb profiles (`REQ-MAP-027`), and ETA predictions (`REQ-MAP-029`)—remain completely inactive unless manually enabled later.

---

## 2. Technical Gap Analysis & Architectural State

| Subsystem | Existing Implementation | Architectural Gap for ATT-1955 |
| :--- | :--- | :--- |
| **Route Corpus Access** | `RoutesRepository.allRoutes` exposes saved routes with polylines (`RouteWithPath`). | No engine actively filters and maintains a live "candidate set" of routes matching the rider's current outbound path. |
| **Corridor Projection** | `ReturnCorridorSnapper.kt` projects coordinates orthogonally onto polylines. | No multi-polyline divergence engine detects where candidate routes split into distinct directional branches. |
| **Proximity Monitoring** | `TurnByTurnNavigationEngine.kt` monitors distance to turn cues on an *actively navigated* route. | No proximity detector alerts the athlete when approaching a road fork when *unrouted* or navigating a multi-route corridor. |
| **Cockpit Presentation** | `TurnPromptBanner.kt` and `ReturnNavigationHud.kt` render single-purpose banners in `SensorGridScreen.kt`. | No comparative decision card allows athletes to compare 2+ branching route choices (with relative arrows, distances, and elevations) and select with 1 tap. |
| **Autonomous Binding** | Manual selection via `RouteSelectorSheet.kt` or `RoutesListScreen.kt`. | No automated route snapper detects that the athlete turned $> 50\text{ m}$ down a specific fork and automatically binds that route to active navigation. |

---

## 3. Scope Bounding & Chesterton's Fence Archaeology

### 3.1 In-Scope Deliverables
1. **Dynamic Outbound Corridor Matching (`ForkRouteMatcher.kt`)**: Evaluates unrouted or corridor-navigated movement against stored routes in `RoutesRepository.allRoutes` to track active candidates within spatial tolerance ($\le 50\text{ m}$).
2. **Divergence Vertex Detection (`RouteDivergenceDetector.kt`)**: Analyzes candidate polylines ahead to detect upcoming divergence coordinates where paths split ($> 40\text{ m}$ separation). Computes distance to fork ($D_{\text{fork}}$).
3. **Cockpit Decision HUD Card (`ForkDecisionCard.kt` in `SensorGridScreen.kt`)**: Displays branching choices (left/right/straight arrows, route names, distances, elevation gains) when within $300\text{ m}$ of the fork.
4. **Autonomous Route Binding (`ForkNavigationRepository.kt`)**: Automatically binds the chosen route when the athlete rides $> 50\text{ m}$ into a branch (cross-track $< 25\text{ m}$ on chosen branch vs $> 50\text{ m}$ on alternative), immediately activating full turn-by-turn and climb guidance.
5. **1-Tap Manual Selection & Dismissal**: Athletes can tap any branch card directly to lock in the route immediately, or dismiss the prompt.
6. **100% 9-Language Localization Parity**: Full parity across EN, DE, ES, FR, IT, JA, NL, PL, PT for all decision labels and hints.

### 3.2 Out-of-Scope (Guarded Against Scope Creep)
- External online routing APIs (GraphHopper, OpenStreetMap, Google Maps): All route matching operates 100% locally and offline from the athlete's saved route database.
- Complex graph topology construction for roads outside the saved route corpus: Analysis is strictly confined to stored routes in `RoutesRepository`.

### 3.3 Chesterton's Fence Audit (`REQ-PRO-022`)
- **Requirement Target**: Net-new requirement `REQ-MAP-031` (*In-Ride Fork-in-the-Road Route Selection & Decision Alerts*).
- **Existing Requirements**: Zero existing requirements modified. All existing navigation (`REQ-MAP-028`), climbs (`REQ-MAP-027`), and return navigation (`REQ-MAP-029`) behavior remains 100% preserved.

---

## 4. Acceptance Criteria Formulation (Given-When-Then)

- **Criterion 1 (Fork Proximity Alert)**:
  - *Given* an active workout tracking along a corridor shared by 2 or more stored routes,
  - *When* the athlete approaches within $300\text{ m}$ of the divergence point ($D_{\text{fork}} \le 300\text{ m}$),
  - *Then* the Cockpit SHALL display an ambient decision card presenting each branch's relative direction, route name, total distance, and elevation gain.
- **Criterion 2 (Autonomous Route Binding)**:
  - *Given* an active fork decision prompt displayed in the Cockpit,
  * *When* the rider turns onto one of the diverging branches and travels $\ge 50\text{ m}$ along it,
  * *Then* the system SHALL automatically bind the chosen route to `RoutesRepository.activeNavigatedRouteId`, dismiss the decision card, and initiate active turn and climb guidance.
- **Criterion 3 (1-Tap Manual Selection)**:
  - *Given* an active fork decision card in the Cockpit,
  - *When* the athlete taps one of the branch options,
  - *Then* the selected route SHALL immediately be bound as the active navigated route.
- **Criterion 4 (Clean Dismissal & Deviation)**:
  - *Given* an active fork prompt,
  - *When* the athlete taps dismiss or deviates from all candidate branches ($> 50\text{ m}$ from all routes),
  - *Then* the prompt SHALL be dismissed without binding a route.
