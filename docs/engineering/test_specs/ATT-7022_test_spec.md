# Stage 2: Requirement & Test Specification - ATT-7022: SensorStatus & SportingJourneySheet: Wire Supporter Emblem to 'My Sporting Journey' Trophy Vault

**Ticket**: [ATT-7022](https://rainerblind.atlassian.net/browse/ATT-7022)  
**Parent Epic**: [ATT-67](https://rainerblind.atlassian.net/browse/ATT-67) (*Zeiträume / Periods & Stats*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.3` | **Branch**: `feature/ATT-7022`  
**Requirement Mapping**: `REQ-SPT-001`–`REQ-SPT-004` | **Test Spec ID**: `TST-SPT-001`–`TST-SPT-004`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-12  

---

## 1. Requirement Specification (REQ-SPT-001..004)

### 1.1 Functional & Architectural Requirements
* **REQ-SPT-001** (*Cockpit Emblem & Entry Points*): In `TrackingTabsScreen.kt` (compact landscape and portrait modes), `SensorStatus` SHALL wire `onSupporterClick` to launch `SportingJourneySheet` whenever the emblem is visible. `SupporterScreen.kt` and `FirstJubileeCelebrationDialog.kt` SHALL provide secondary entry points into the vault.
* **REQ-SPT-002** (*Multi-Horizon & Sport Filtering*): `SportingJourneyRepository` SHALL query `WorkoutSummaries` (`finished = 1`) and `ExtremumValues` on `Dispatchers.IO` with zero database migrations. The engine SHALL support Time Horizons (`All-Time` vs individual calendar years) and Sport Filters (`All Sports`, `Cycling`, `Running`), reactively recalculating hero totals, Earth Equator %, Mount Everest multipliers, and route cluster counts.
* **REQ-SPT-003** (*Hidden Gems & Achievement Engine*): The discovery engine SHALL calculate biological and mechanical metrics: total pedal revolutions ($\sum \text{CADENCE}_{\text{avg}} \times t_{\text{active, min}}$), running steps, training heartbeats, mechanical work ($\text{kWh}$ / e-bike range), circadian DNA, and deterministic achievement trophies (`Century Rider`, `Everest Club`, `Dawn Patrol`, `Night Owl`, `Million Cranks`, `60 kWh Human Power Plant`, `Local Legend`, `ANT+ & BLE Pioneer`, `Half-Way Around the World`), supporting click-through to `WorkoutDetail`.
* **REQ-SPT-004** (*Shareable Athlete Passport & 9-Locale Parity*): The sheet SHALL provide a `Share Card` action formatting an Athlete Passport stamped with active emblem via Android `Intent.ACTION_SEND`. All UI strings SHALL reside in `strings_sporting_journey.xml` with 100% parity across all 9 supported locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).

### 1.2 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Cockpit Trigger)**: *Given* an athlete with Pioneer or Paid Supporter tier in `TrackingTabsScreen`, *When* tapping the supporter emblem on `SensorStatus`, *Then* `SportingJourneySheet` opens displaying their retrospective.
* **Criterion 2 (Filtering)**: *Given* `SportingJourneySheet` is open, *When* toggling time horizons (`All-Time` vs specific year) or sports (`All` vs `Bike` vs `Run`), *Then* all totals, planetary scales, Hidden Gems, and achievements update reactively on `Dispatchers.IO`.
* **Criterion 3 (Drill-Down & Achievements)**: *Given* unlocked milestone rows or achievement trophies, *When* tapped, *Then* the unlocking workout story is presented with navigation to workout details.
* **Criterion 4 (Share Card & Localization)**: *Given* `SportingJourneySheet`, *When* tapping `Share Card`, *Then* formatted summary passport is shared via `Intent.ACTION_SEND` with 100% 9-language translation parity.

---

## 2. Test Specification (TST-SPT-001..004)

| Test Spec ID | Requirement Mapping | Test Scope & Procedure | Target Component | Expected Result |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SPT-001` | `REQ-SPT-001` | Verify `onSupporterClick` callback wiring in `TrackingTabsScreen` launches sheet | `TrackingTabsScreen.kt` | Callback invoked, sheet state transitions to visible |
| `TST-SPT-002` | `REQ-SPT-002` | Unit tests for `SportingJourneyRepository` horizon/sport filtering & planetary math | `SportingJourneyRepository.kt` | Equator % and Everest multiplier computed accurately |
| `TST-SPT-003` | `REQ-SPT-003` | Unit tests for Hidden Gems (cranks, heartbeats, kWh) & badge unlocking logic | `SportingJourneyCalculator.kt` | Correct values aggregated, trophies unlock deterministically |
| `TST-SPT-004` | `REQ-SPT-004` | Contract test for share intent format & 9-locale parity in `strings_sporting_journey.xml` | `TranslationParityTest` | Intent populated, zero missing keys in 9 locales |

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-SPT-001..004`, companion to `REQ-SET-086` (*Supporter Tiers & Emblems*) and `REQ-PER-007` (*Period Analytics*).
* **Historical Origin & Commit Trace**: `REQ-SET-086` introduced in `ATT-4773` placed the emblem in `SensorStatus` as a visual tier indicator without click handling.
* **Root Reason for Existing Formulation**: Cockpit sensor bar prioritized hardware telemetry indicators; emblem click handling was deferred until a dedicated retrospective surface was defined.
* **Preservation of Core Invariants**: Free tier emblem remains hidden; paid/pioneer tiers gain tap access. All SQLite queries are strictly read-only on `Dispatchers.IO` with zero DB migrations.
