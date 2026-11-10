# Project Risk Register: aTrainingTracker

**Document Identifier**: `RISK-REG-ATT`  
**Standard**: ASPICE 4.0 `MAN.3` (Project Management — `MAN.3.BP5` Define, monitor and adjust project risks)  
**Target Release**: `V5.0.0` | **Version**: 1.0.0  
**Status**: Approved | **Last Review Date**: 2026-11-10  
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
