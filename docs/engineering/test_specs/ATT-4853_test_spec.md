# Stage 2: Requirement & Test Specification - ATT-4853: Optimize Play Store Description & Refresh Visual Presence

**Ticket**: [ATT-4853](https://rainerblind.atlassian.net/browse/ATT-4853) (consolidating [ATT-490](https://rainerblind.atlassian.net/browse/ATT-490))  
**Sub-task**: `[Req & Test Spec]` ([ATT-5356](https://rainerblind.atlassian.net/browse/ATT-5356))  
**Parent Epic**: Standalone / None  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.5`  
**Requirement Mapping**: `REQ-STP-001`..`REQ-STP-004`  
**Test Spec ID**: `TST-STP-001`..`TST-STP-004`  
**Branch**: `feature/ATT-4853`  
**Author**: AI Agent 1 (`subagent_flash`)  
**Date**: 2026-11-04  

---

## 1. Requirement Specification (REQ-STP-001..004)

### 1.1 Scope & Functional Clauses
1. **9-Language Parity & Character Bounds (`REQ-STP-001`)**: Localized listings in `docs/store_presence/*.md` for DE, EN, ES, FR, IT, JA, NL, PL, PT must obey Google Play bounds: Title $\le 30$, Short Description $\le 80$, Full Description $\le 4000$ UTF-16 code units. Header count annotations SHALL match exact character lengths.
2. **V5.0 Feature Messaging & Backlog Feature Prohibition (`REQ-STP-002`)**: Highlight .FIT, Drive triad, Coggan power suite, Skiba $W'_{\text{bal}}$, PMC, Periods 2.0, Barometer/Bike Bell, and local SQLite data sovereignty. Descriptions SHALL strictly refrain from claiming unimplemented backlog features (*Radar*, *Mader-Heck*, *glycogen depletion*) across all 9 locales (`Rule 71`).
3. **Automated Regression Contract Test (`REQ-STP-003`)**: Provide JUnit 4 contract test `PlayStoreListingContractTest.kt` validating existence, Title $\le 30$, Short $\le 80$, Full $\le 4000$, HTML formatting tags, and negative assertions verifying zero occurrences of 'Radar', 'Mader', 'Glykogen', or 'Glycogen' across all 9 locales.
4. **Visual Screenshots & Uniqueness Invariant (`REQ-STP-004`)**: Capture high-resolution Compose Light/AMOLED screenshots. All 6 primary screenshots in `docs/store_presence/screenshots/` (`01_cockpit_tracking.png`..`06_periods_analytics.png`) MUST be unique (distinct SHA-256 hashes, zero duplicates) and visually verified via `read_file` without mislabeling (`Rule 71`). Google Play Console publishing is designated as Human Prerequisite (`Rule 22`).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-STP-002`, `REQ-STP-003`, `REQ-STP-004` (targeting `docs/store_presence/*.md`, `PlayStoreListingContractTest.kt`, `docs/store_presence/screenshots/`).
* **Historical Origin & Commit Trace**: Sprint 2026-45.4 (`ATT-4853`, commits `f0be0abd`, `cf412745`).
* **Root Reason for Existing Formulation**: Sprint 2026-45.4 drafted premature references to Radar and Mader-Heck, and copied screenshot assets without visual inspection, causing `n.i.O.` rating in Sprint Review.
* **Preservation of Core Invariants**: Purging unimplemented backlog claims restores truth-in-advertising. Enforcing negative contract assertions and SHA-256 screenshot uniqueness with `read_file` verification prevents regressions without altering runtime app code.

### 1.2 Acceptance Criteria (Given-When-Then)
* **AC-1 (Character Bounds & Formatting)**: *Given* all 9 `docs/store_presence/*.md` files, *When* evaluated, *Then* Title $\le 30$, Short $\le 80$, Full $\le 4000$ characters, and HTML tags are balanced.
* **AC-2 (Zero Unimplemented Features)**: *Given* store listings in all 9 locales, *When* scanned, *Then* zero occurrences of 'Radar', 'Mader', or 'Glykogen'/'Glycogen' are present.
* **AC-3 (CI Contract Verification)**: *Given* `PlayStoreListingContractTest.kt`, *When* executed via Gradle, *Then* all character bounds and negative absence checks pass at 100%.
* **AC-4 (Screenshot Uniqueness & Authenticity)**: *Given* `docs/store_presence/screenshots/`, *When* hashed and inspected via `read_file`, *Then* all 6 images have distinct SHA-256 hashes and display their genuine corresponding views.

---

## 2. Test Specification (TST-STP-001..004)
* **TST-STP-001 (Character Bounds & Parity Audit)**: Multi-locale inspection verifying Title $\le 30$, Short $\le 80$, Full $\le 4000$ across all 9 languages.
* **TST-STP-002 (V5.0 Feature Messaging & Negative Backlog Audit)**: Verification of .FIT, Drive, Sports Science/PMC, Periods 2.0, Bell, and zero occurrences of Radar/Mader-Heck/glycogen across all 9 locales.
* **TST-STP-003 (Automated Listing Regression Contract Test)**: `PlayStoreListingContractTest.kt` asserting bounds, HTML formatting, and negative absence assertions.
* **TST-STP-004 (Visual Inspection & SHA-256 Screenshot Audit)**: Distinct SHA-256 hash assertions across `01_cockpit_tracking.png`..`06_periods_analytics.png` and visual verification via `read_file`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-STP-001` | Content / Audit | `docs/store_presence/*.md` | `REQ-STP-001` | Specified |
| `TST-STP-002` | Content / Audit | `docs/store_presence/*.md` | `REQ-STP-002` | Specified |
| `TST-STP-003` | Contract Unit | `PlayStoreListingContractTest.kt` | `REQ-STP-001`, `REQ-STP-002`, `REQ-STP-003` | Specified |
| `TST-STP-004` | Asset / Visual | `docs/store_presence/screenshots/` | `REQ-STP-004` | Specified |
