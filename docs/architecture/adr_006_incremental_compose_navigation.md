# ADR-006: Incremental Single-Activity Compose Navigation (`MainActivityWithNavigation`)

- **Document ID**: `DOC-ARC-ADR006`
- **Version**: 1.0.0
- **Status**: Approved
- **Approver**: Rainer Blind (PO)
- **Deciders**: Architecture Board, Product Owner (Pending Sign-Off)
- **Date**: 2026-11-17
- **Target Release**: `5.1.0` (Active Sprint: `2026-11-17_01_Früh`)
- **Parent Epic**: [ATT-8138](https://issue-tracker/ATT-8138) (*ASPICE 4.0 Audit Remediation 2026-11*)
- **Ticket**: [ATT-8161](https://issue-tracker/ATT-8161)
- **Requirement Mapping**: `REQ-PRO-116` (`REQ-PRO-116.1`, `REQ-PRO-116.2`), `REQ-PRO-044`
- **Test Mapping**: `TST-PRO-116` (`TST-PRO-116.1`, `TST-PRO-116.2`), `UiPresentationIntegrationTest`

---

## 1. Context & Problem Statement

Originally built around classic Android `AppCompatActivity` screens, XML layouts, `Fragment`s, and `PreferenceFragmentCompat`, `aTrainingTracker` adopted Jetpack Compose Material 3 (`ATrainingTrackerTheme`) and MVI ViewModels (`IF-SWE-01`). All user workflows (live tracking cockpit, workout aftermath, route clusters, segments, equipment, devices, expert tuning) must navigate seamlessly from a unified drawer shell while remaining continuously shippable.

## 2. Decision Drivers & Evaluated Alternatives

Key selection criteria: (1) continuous sprint shippability without multi-month UI freezes, (2) unidirectional MVI state isolation (`StateFlow` + `collectAsStateWithLifecycle`), (3) deep link / notification navigation consistency, and (4) elimination of Fragment transaction state-loss bugs.

### Alternative 1: Remain on Multi-Activity / XML `FragmentManager` Architecture
- **Description**: Keep separate `Activity` subclasses and XML `Fragment` transactions, embedding isolated `ComposeView` islands per screen.
- **Evaluation**: **Rejected**. Multi-activity navigation duplicates drawer/toolbar scaffolding, complicates shared tracking state observation, and perpetuates `FragmentTransaction` `IllegalStateException` after `onSaveInstanceState`.

### Alternative 2: Big-Bang Rewrite of Every Legacy Activity/Fragment Before Shipping
- **Description**: Halt feature delivery until every remaining XML/Fragment screen (`ConfigViewsActivityClassic`, `ConfigViewsFragment`, `TrackOnMapHelper`) is deleted and rewritten.
- **Evaluation**: **Rejected**. High schedule risk and unnecessary churn on stable secondary utilities while core tracking, aftermath, device, and settings screens already benefit from Compose.

### Alternative 3: Single-Activity Compose `NavHost` (`MainActivityWithNavigation` -> `ATrainingTrackerApp`) with Incremental Migration (Selected)
- **Description**: Host `ATrainingTrackerApp` inside `MainActivityWithNavigation` using `androidx.navigation:navigation-compose` (`NavHost`) and `AppNavigationDrawer`, routing primary destinations to declarative Compose screens backed by `AndroidViewModel` `StateFlow`s (`TrackingViewModel`, `ControlTrackingViewModel`, `TrackOnMapAftermathViewModel`), while coexisting cleanly with remaining legacy Java UI helpers (`ConfigViewsActivityClassic`, `ConfigViewsFragment`, `TrackOnMapHelper`) until replaced atomically.
- **Evaluation**: **Selected**. Delivers unified Material 3 navigation, clean MVI boundaries (`IF-SWE-01`), and zero-downtime incremental modernization.

## 3. Decision Outcome

`MainActivityWithNavigation` + `ATrainingTrackerApp` (`NavHost`) is the canonical presentation shell (`docs/architecture.md` §1.1); all new UI screens MUST be declarative Jetpack Compose destinations wired into `NavHost` without legacy XML `Fragment`s.

## 4. Architectural Consequences & Trade-offs

### Positive Consequences
- **Unified MVI State Flow**: Single-activity `NavHost` eliminates Fragment back-stack desynchronization and keeps tracking/map state reactive.
- **Incremental Velocity**: Legacy XML/Fragment components (`ConfigViewsActivityClassic`, `ConfigViewsFragment`, `TrackOnMapHelper`) can be retired screen-by-screen without blocking releases.

### Negative / Mitigated Consequences
- **Dual UI Boundary Maintenance**: `MainActivityWithNavigation` temporarily bridges Compose `NavHostController` with legacy preference/activity hooks; mitigated by `UiPresentationIntegrationTest` and `ArchitectureConformanceContractTest`.

---

## Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-17 | AI Agent (`ATT-8161`, `ATT-8164`) | `ATT-8161`, `ATT-8164` | Retrospective ADR baseline with standardized `DOC-ARC-ADR006` control header. |
