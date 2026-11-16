# Project Risk Register: aTrainingTracker

**Document Identifier**: `RISK-REG-ATT`  
**Standard**: ASPICE 4.0 `MAN.3` (Project Management — `MAN.3.BP5` Define, monitor and adjust project risks)  
**Target Release**: `V5.0.0` | **Version**: 1.0.0  
**Status**: Approved | **Last Review Date**: 2026-11-15  
**Governing Ticket**: [ATT-6151](https://rainerblind.atlassian.net/browse/ATT-6151) | **Requirement**: `REQ-STP-010` | **Test**: `TST-STP-010`  

---

## 1. Purpose & Risk Management Strategy (MAN.3.BP5)

This Project Risk Register defines the formal risk management strategy for **aTrainingTracker**. It identifies, evaluates, prioritizes, mitigates, and monitors technical, operational, process, and architectural risks across the software lifecycle.

### Risk Governance Invariants
1. **Recurring Review Cadence**: This risk register is formally reviewed and re-stamped during **Ceremony 1 (Sprint-Start)** by the Sprint Coordinator.
2. **Mandatory High-Exposure Mitigation**: Any risk evaluated with **High Exposure ($E \ge 15$)** MUST have a linked active or planned Jira remediation ticket.
3. **Repository Location**: Maintained in `docs/management/` outside `docs/engineering/` to preserve Rule 67 canonical directory integrity.

---

## 2. Risk Assessment & Scoring Rubric

Risks are quantified along two orthogonal dimensions using a 5-point discrete scale:

### Probability Scale ($P \in [1..5]$)
* **1 - Very Low (Rare)**: Unlikely to occur during normal development (< 5% probability per release cycle).
* **2 - Low (Unlikely)**: May occur occasionally (5%–20% probability).
* **3 - Moderate (Possible)**: Expected to occur intermittently (20%–50% probability).
* **4 - High (Likely)**: Frequent occurrence expected without controls (50%–80% probability).
* **5 - Very High (Almost Certain)**: Chronic or continuous risk without proactive mitigation (> 80% probability).

### Impact Scale ($I \in [1..5]$)
* **1 - Negligible**: Minor aesthetic or documentation anomaly; zero impact on athlete tracking or build.
* **2 - Minor**: Minor non-core workflow defect; easily worked around; minimal schedule impact.
* **3 - Moderate**: Telemetry calculation drift, non-critical service disruption, or minor sprint delay (< 1 day).
* **4 - Major**: Core tracking failure, background service termination, Play Store release rejection, or major schedule slippage.
* **5 - Critical**: Fatal crash, unrecoverable athlete database corruption, security/credential leak, or complete pipeline halt.

### Risk Exposure Matrix ($E = P \times I \in [1..25]$)

| Probability \ Impact | 1 (Negligible) | 2 (Minor) | 3 (Moderate) | 4 (Major) | 5 (Critical) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **5 (Very High)** | 5 (Low) | 10 (Medium) | 15 (High) | 20 (High) | 25 (High) |
| **4 (High)** | 4 (Low) | 8 (Medium) | 12 (Medium) | 16 (High) | 20 (High) |
| **3 (Moderate)** | 3 (Low) | 6 (Low) | 9 (Medium) | 12 (Medium) | 15 (High) |
| **2 (Low)** | 2 (Low) | 4 (Low) | 6 (Low) | 8 (Medium) | 10 (Medium) |
| **1 (Very Low)** | 1 (Low) | 2 (Low) | 3 (Low) | 4 (Low) | 5 (Low) |

### Exposure Classification & Action Thresholds
* **Low Exposure ($1 \le E \le 6$)**: Accept and monitor. Standard development hygiene and code reviews suffice.
* **Medium Exposure ($8 \le E \le 12$)**: Active mitigation required. Guarded by automated unit/contract tests and architectural rules.
* **High Exposure ($15 \le E \le 25$)**: Critical risk. Mandatory linked Jira remediation ticket, explicit CI gating, and coordinator escalation.

---

## 3. Sprint Review Log

The risk register is reviewed at every Sprint-Start ceremony to capture newly surfaced risks, re-score existing exposure, and track mitigation progress.

| Review Date | Sprint | Reviewer / Role | Notes / Actions Taken |
| :--- | :--- | :--- | :--- |
| **2026-11-10** | **2026-46.1** *(Start)* | Sprint Coordinator / Process Owner | Initial risk register baseline established per ASPICE MAN.3.BP5 (ATT-6151). Initial risks RISK-001 through RISK-006 identified; High-exposure risks linked to remediation tickets. |
| **2026-11-10** | **2026-46.1** *(Close)* | Sprint Coordinator / Human Reviewer | Ceremony 2 review across 66 realized tickets (`146.0 SP`): `ATT-6148` & `ATT-6152` completed toward `RISK-001`; `RISK-002` manifested as unmounted UI/importer islands (`ATT-6702`, `ATT-6711`, `ATT-6712`) and AI symbol/signature hallucinations in `SWE.2`/`SYS.2`/`SYS.3` docs (`ATT-6727`, `ATT-6732`, `ATT-6738`–`ATT-6741`). Enacted **Rule 81** (mandatory caller-site `grep`, dynamic symbol-vs-source & bidirectional `SYSREQ` equality checks, and Tier 2 `lintDebug`/`detekt` CI parity). |
| **2026-11-10** | **2026-46.2** *(Start)* | Sprint Coordinator (`ATT-6746`) | Ceremony 1 review across 122 queued sprint tickets (`RISK-001`..`RISK-006` verified; High-exposure `RISK-001` [`E=16`] & `RISK-002` [`E=15`] linked to `ATT-6148`/`ATT-6152` & `ATT-6150`/`ATT-6141` plus active Sprint `2026-46.2` remediations `ATT-6743`, `ATT-6727`–`ATT-6741`, and `ATT-6120`–`ATT-6126`; `RISK-003`/`RISK-004`/`RISK-006` actively mitigated via `ATT-6711`, `ATT-6713`/`ATT-6744`, and `ATT-6716`). |
| **2026-11-11** | **2026-46.2** *(Close)* | Sprint Coordinator / Human Reviewer (`ATT-6746`) | Ceremony 2 review (`ATT-7101` Stage 6 Cold Carbon Review introduced): `RISK-002` mitigated via mandatory **Rule 5** (`Pros` vs. `Cons / Defects Identified` + *"Would you recommend its approval?"*) in `stage6-final-review/SKILL.md`, which caught 34 tickets with subtle edge-case, concurrency, mathematical, or lifecycle defects before `Final Review (Human)` while passing 49 clean tickets (`92.0 SP`). |
| **2026-11-11** | **2026-46.3** *(Start)* | Sprint Coordinator (`ATT-7161`) | Ceremony 1 review across Sprint `2026-46.3` queue (`RISK-001`..`RISK-006` verified; High-exposure `RISK-001` [`E=16`] & `RISK-002` [`E=15`] linked to `ATT-6148`/`ATT-6152` & `ATT-6150`/`ATT-6141` plus active Sprint `2026-46.3` worktree/merge hardening `ATT-7025`, 34 Stage 6 Cold Carbon bounce remediations, and `ATT-6120`–`ATT-6169`; `RISK-004`/`RISK-006` actively mitigated via `ATT-6675`–`ATT-6677`, `ATT-7159`, `ATT-6879`, and `ATT-7034`). |
| **2026-11-12** | **2026-46.3** *(Close)* | Sprint Coordinator / Human Reviewer (`ATT-7161`) | Ceremony 2 review across 90 realized tickets (`187.0 SP`, 71 accepted with `V5.0.0`, 19 retained in `Final Review (Human)`): `ATT-7025` (deterministic `--merge-ticket` lock) & `ATT-6506` (`emulator-5554` physical-phone protection) accepted; ASPICE CL2/CL4 infrastructure tickets (`ATT-6140`, `ATT-6146`, `ATT-6147`, `ATT-6149`, `ATT-6150`, `ATT-6154`–`ATT-6157`) reached `Final Review (Human)`. Enacted **Rule 82** (prioritizing rollover in-progress/bounced tickets before `Zu erledigen`, forbidding non-null default `collectAsState` form hydration races [`ATT-7319`], and enforcing two-sided `Row` weighting + authentic M3 anti-prototype UI [`ATT-7321`]). |
| **2026-11-12** | **2026-46.4** *(Start)* | Sprint Coordinator (`ATT-7322`) | Ceremony 1 review across Sprint `2026-46.4` queue (`RISK-001`..`RISK-006` verified; High-exposure `RISK-001` [`E=16`] & `RISK-002` [`E=15`] linked to `ATT-6148`/`ATT-6152` & `ATT-6150`/`ATT-6141`; 14 in-progress rollover tickets prioritized per Rule 82, 39 `Zu erledigen` tickets audited, and 19 `Final Review (Human)` tickets tracked). |
| **2026-11-12** | **2026-46.4** *(Close)* | Sprint Coordinator / Human Reviewer (`ATT-7322`) | Ceremony 2 review across 28 realized tickets (`60.0 SP` completed): Enacted **Rule 83** (programmatically enforcing mandatory review questions in Stage 6 `final-review-verdict`), strengthened `stage3-impl-plan` and `stage6-final-review` skills with `docs/design_guidelines.md` enforcement, and captured physical hardware findings on Garmin Varia RearVue 820 and Polar H10. |
| **2026-11-12** | **2026-46.5** *(Start)* | Sprint Coordinator (`ATT-7525`) | Ceremony 1 review across 29 queued sprint tickets (`RISK-001`..`RISK-006` verified; High-exposure `RISK-001` [`E=16`] & `RISK-002` [`E=15`] linked to `ATT-6148`/`ATT-6152` & `ATT-6150`/`ATT-6141` plus active Sprint `2026-46.5` design-guideline contract test `ATT-7536`/`ATT-7539`; `RISK-004` actively mitigated via real-hardware RearVue 820 BLE V1/V2/Light fixes `ATT-7543`, `ATT-7542`, `ATT-7545`, `ATT-7541`, and Polar H10 reconnect/signal fixes `ATT-7530`, `ATT-7533`). |
| **2026-11-13** | **2026-46.5** *(Close)* | Sprint Coordinator / Human Reviewer (`ATT-7525`) | Ceremony 2 review across 32 tickets in `Final Review (Human)` (`66.0 SP` completed; 30 accepted with `V5.0.0`, 1 deferred for live morning test [`ATT-7532`], 1 rejected on-device [`ATT-7540` -> `ATT-7737`]). Enacted **Rule 84** (mandatory `Model: "pro"` + 4-point `read_file` screenshot inspection for all graphical/UI/slider/icon tickets; hard-enforced `flock` directly in `./gradlew` + `-Xmx2048m` / `60s` daemon limits; DataStore MockK `resolveDataStore` singleton isolation preventing cross-test NPE contamination). |
| **2026-11-13** | **2026-46.6** *(Start)* | Sprint Coordinator (`ATT-7749`) | Ceremony 1 review across 47 queued sprint tickets (`RISK-001`..`RISK-006` verified; High-exposure `RISK-001` [`E=16`] & `RISK-002` [`E=15`] linked to `ATT-6148`/`ATT-6152` & `ATT-6150`/`ATT-6141` plus active Sprint `2026-46.6` ASPICE/CI/governance hardening `ATT-7558`, `ATT-7559`, `ATT-7562`–`ATT-7565`; `RISK-004` actively mitigated via BLE/ANT+ radar & Polar H10 reconnect fixes `ATT-7755`, `ATT-7546`, `ATT-7547`, `ATT-7573`–`ATT-7580`). |
| **2026-11-15** | **2026-46.7** *(Start)* | Sprint Coordinator (`ATT-8030`) | Ceremony 1 review across 31 regular sprint tickets (`82.0 SP`, `RISK-001`..`RISK-006` verified; High-exposure `RISK-001` [`E=16`] & `RISK-002` [`E=15`] linked to `ATT-6148`/`ATT-6152` [`Erledigt`] & `ATT-6150` [`Erledigt`]/`ATT-6141` [`Zu erledigen`] plus active `TrackerService`/`BANALService` characterisation harness `ATT-7902`; `RISK-003`/`RISK-004`/`RISK-006` actively mitigated via API 33+ gated `polar-ble-sdk` GATT arbitration [`ATT-7762`..`ATT-8020`], OOM/crash-recovery hardening [`ATT-7874`–`ATT-7877`], and serialized `TrackingViewsDbHelper`/SQLite repository migrations [`ATT-7972`, `ATT-7760`, `ATT-7909`]). |
| **2026-11-15** | **2026-46.7** *(Close)* | Sprint Coordinator (`ATT-8030`) | Sprint closed direct-to-Retro per PO instruction (`15/30` regular tickets = `36.0 SP` completed in `Final Review (Human)` on clean Tier-2 `9115648e` deployed to `Pixel 10`; `15` untouched `Zu erledigen` = `41.0 SP` preserved cleanly). Enacted **Rule 85** (`ATT-8030`): fixed 12h sprint cadence (`04:00`/`16:00`), Three-Tier Jira board (`Vom PO Freigegeben` ID `447` tamper lock), Multi-Lane Greedy Bin-Packing, Dual-Ticket Retro (`Review & Retro` + rolling `Wochen Review & Retro`), unthrottled Gradle daemons (`-Xmx4096m`/`-Xmx3072m`, `workers.max=6`, `1800s` idle), lean `--tests`-only Stage 4–5 checks, and read-only Stage 6 Cold Reviews (`ZERO ./gradlew` lock calls). Truthful `NOT PERFORMED` Ceremony 2 review recorded (`sprint_2026-46.7.md`) pending async human PO sign-off. |
| **2026-11-15** | **2026-46.8** *(Start)* | Sprint Coordinator (`ATT-8225`) | Ceremony 1 review across 18 packed regular sprint tickets (`44.0 SP`, 12h window ending `2026-11-16 04:00` with `03:25` start-cutoff; `RISK-001`..`RISK-006` verified; High-exposure `RISK-001` [`E=16`] & `RISK-002` [`E=15`] linked to `ATT-6148`/`ATT-6152` [`Erledigt`] & `ATT-6150` [`Erledigt`]/`ATT-6141` [`Zu erledigen` in Sprint `2026-46.8`] plus ASPICE 4.0 remediation suite `ATT-8213`, `ATT-8212`, `ATT-8162`, `ATT-8142`–`ATT-8144`; `RISK-004`/`RISK-006` actively mitigated via `ATT-7875`/`ATT-7874`, `ATT-7909`, `ATT-7737`, `ATT-8000`/`ATT-8001`). |
| **2026-11-16** | **2026-46.8** *(Close)* | Sprint Coordinator (`ATT-8225`) | Ceremony 2 closure (`17/17` active code tickets = `42.0 SP` completed in `Final Review (Human)` labeled `sprint-2026-46.8`; untouched `ATT-7761` [`2 SP`] returned to top of `Vom PO Freigegeben` ID `447` at `03:25` cutoff). Landed ASPICE 4.0 hardening (`ATT-8213`, `ATT-8212`, `ATT-8162`, `ATT-8141`–`ATT-8144`), Polar swim & backfill (`ATT-8000`, `ATT-8001`), FIT/TCX Kotlin migration & repository encapsulation (`ATT-7909`, `ATT-7906`), RearVue 820 BLE light fix (`ATT-7737`), crash/OOM/sparkline/color fixes (`ATT-7874`, `ATT-7875`, `ATT-7877`, `ATT-7760`, `ATT-8196`). `RISK-001`..`RISK-006` verified within bounds; `qa-audit` false-positive heuristic on `ATT-8213`/`ATT-8212` tracked to `ATT-8152` in `447`; truthful `NOT PERFORMED` review logged (`sprint_2026-46.8.md`). |
| **2026-11-16** | **2026-47.1** *(Start)* | Sprint Coordinator (`ATT-8314`) | Ceremony 1 review across 13 regular tickets (`29.0 SP`, 12h window ending `2026-11-16 16:00` CET, `15:25` start-cutoff; packed strictly `ORDER BY rank ASC` across disjoint Epics `ATT-5908`, `ATT-8138`, `ATT-5910`, `ATT-355`, `ATT-3539`, `ATT-68`). Verified `RISK-001`..`RISK-006` with `ATT-8145`–`ATT-8148` & `ATT-8166` addressing governance/traceability accuracy (`RISK-002`) and `ATT-8016`–`ATT-8018` extending Polar sensor pipelines (`RISK-004`). Noted prior `sprint_2026-46.8.md` QA audit remediation (`ATT-8152`) & Detekt debt (`ATT-8169`) remain queued in `447`. |
| **2026-11-16** | **2026-47.1** *(Close)* | Sprint Coordinator (`ATT-8314`) | Ceremony 2 closure (`13/13` committed code tickets = `29.0 SP` completed in `Final Review (Human)` labeled `sprint-2026-47.1`; `0` returns). Landed full ASPICE Epics `ATT-8138` governance (`ATT-8145`–`ATT-8148`, `ATT-8166`: `0` legacy unbacked TSTs, `100%` SYS-to-SWE reverse coverage, `0` code orphans across `2,346` files, PO queue `447` changelog provenance) and Polar H10/Verity/Cockpit/Monetization features (`ATT-8016`–`ATT-8018`, `ATT-7761`, `ATT-8117`, `ATT-8108`, `ATT-7740`, `ATT-8096`). Tier-2 Gate green (`6,482/6,482` tests, Kover `81.23%` line / `59.97%` branch, `:app:lintDebug` `0 errors` after `12de9c54` & `7e97be1e`). `RISK-001`..`RISK-006` within bounds; `ATT-8152` QA heuristic & `ATT-8150` Gate FTPR queued in `447`. |
| **2026-11-16** | **2026-47.2** *(Start)* | Sprint Coordinator (`ATT-8388`) | Ceremony 1 review across 6 packed tickets (`15.0 SP`, window `13:41–16:00` CET aligning back onto fixed `04:00`/`16:00` 12h cadence, `15:25` start-cutoff; packed strictly `ORDER BY rank ASC` across disjoint Epics `ATT-166` [`ATT-8337`, `ATT-8081`], `ATT-8138` [`ATT-8149`, `ATT-8150`], `ATT-5910` [`ATT-8019`, `ATT-8020`], skipping `human`-blocked `ATT-7573` and unestimated `ATT-8109`). `RISK-001`..`RISK-006` verified: `ATT-8337`/`ATT-8081` mitigate GNSS/altitude data integrity (`RISK-006`), `ATT-8149`/`ATT-8150` mitigate multi-lane ID drift & gate discrimination (`RISK-002`), and `ATT-8019`/`ATT-8020` mitigate BLE field diagnostics (`RISK-004`). |

---

## 4. Registered Project & Technical Risks

| Risk ID | Category | Description | $P$ | $I$ | $E$ | Exposure Tier | Mitigation Strategy | Mitigation Ticket | Owner |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :--- | :--- | :--- |
| **RISK-001** | Operational | **Single Human Release & Key Bottleneck**: Sole human maintainer holds production release signing credentials and Google Play publication responsibility; maintainer absence blocks releases. | 4 | 4 | **16** | **High** | Externalized secure keystore provider chain decoupled from repo; formal CM plan; automated release workflow; multi-agent pipeline. | [ATT-6148](https://rainerblind.atlassian.net/browse/ATT-6148), [ATT-6152](https://rainerblind.atlassian.net/browse/ATT-6152) | Release Manager |
| **RISK-002** | Technical / AI | **AI Model Drift & Context Compaction Errors**: LLM non-determinism, context window limits, or hallucinations causing subtle behavioral or architectural regressions during autonomous sprint runs. | 3 | 5 | **15** | **High** | Strict AAA governance rules; Rule 66 lost-wakeup watchdogs; automated build and architectural contract test suites (`*ContractTest.kt`); human gate sign-off. | [ATT-6150](https://rainerblind.atlassian.net/browse/ATT-6150), [ATT-6141](https://rainerblind.atlassian.net/browse/ATT-6141) | AI Coordinator |
| **RISK-003** | Platform | **Google Play Policy & Foreground Service Restrictions**: Annual Android Target SDK bumps and evolving background/FGS permissions (API 34/35) threatening background tracking continuity. | 3 | 4 | **12** | **Medium** | Pre-emptive Target SDK verification; Rule 29 dynamic runtime permission gating before `startForeground()`; `ManifestSecurityContractTest`; Play Store copy contract tests. | Planned Target SDK Ticket | Architecture Lead |
| **RISK-004** | Hardware | **Bluetooth LE & ANT+ Peripheral Fragmentation**: Vendor variations in BLE GATT characteristics, erratic connection drops, and non-standard OUI MAC addresses across sensors. | 3 | 3 | **9** | **Medium** | Modular `BANALService` abstraction; synthetic hardware test harnesses (`ble_peripheral_simulator.py`); IEEE MAC address classification (`ATT-6245`); defensive proxy unwrapping (`ATT-5191`). | Continuous Framework Maintenance | Sensor Framework Lead |
| **RISK-005** | External API | **Third-Party Cloud Rate Limiting & OAuth Expiry**: Strava, Dropbox, or Google Drive API throttling, breaking token refreshes or bulk activity sync. | 2 | 3 | **6** | **Low** | Local SQLite data sovereignty (all tracking works 100% offline); variant-scoped OAuth custom URI schemes (`ATT-4668`/`ATT-4706`); exponential backoff in sync workers. | Continuous Maintenance | Cloud Integrations Lead |
| **RISK-006** | Data Integrity | **SQLite Schema Migration Incompatibilities**: Incremental `onUpgrade` migrations failing on historical user databases, risking user workout data loss. | 2 | 5 | **10** | **Medium** | Rule 64 automated migration contract tests (`onCreate` vs `onUpgrade` schema parity); safe `DEFAULT` constraints on non-null columns; full-app `.attbackup` archive support. | Rule 64 Architectural Invariant | Data Architecture Lead |

---

## 5. Traceability & Review History

* **ASPICE Mapping**: Process `MAN.3` (Project Management), Base Practice `MAN.3.BP5` (Define, monitor and adjust project risks).
* **Requirement**: `REQ-STP-010` (*Project Risk Register & Sprint-Start Review Governance*).
* **Verification**: Automated build contract test `testProjectRiskRegister_existsAndHighExposureMitigated` in `ReleaseCiAndLintConfigContractTest.kt` (`TST-STP-010`).
