# Project Quality Assurance Strategy & Plan: aTrainingTracker

**Document ID**: `DOC-MGT-QA`  
**Document Identifier**: `QA-PLAN-ATT`  
**Standard**: ASPICE 4.0 `SUP.1` (Quality Assurance) & `GP 2.1.6` (Ensure independence of quality assurance)  
**Target Release**: `V5.0.0` | **Version**: 1.2.1  
**Status**: Reviewed | **Approver**: Pending PO Sign-Off | **Date**: 2026-11-17  
**Governing Tickets**: [ATT-8212](https://rainerblind.atlassian.net/browse/ATT-8212), [ATT-8213](https://rainerblind.atlassian.net/browse/ATT-8213), [ATT-8164](https://rainerblind.atlassian.net/browse/ATT-8164), [ATT-8169](https://rainerblind.atlassian.net/browse/ATT-8169) | **Requirements**: `REQ-PRO-095`, `REQ-PRO-096`, `REQ-PRO-061`, `REQ-PRO-120`, `REQ-PRO-123`  

---

## 1. Purpose & Quality Objectives (SUP.1.BP1)

This Quality Assurance (QA) Plan defines the independent quality assurance strategy, organizational independence boundaries, verification levels, non-conformance escalation paths, and automated scheduling controls for **aTrainingTracker**. Its primary objective is to guarantee that work products, engineering processes, and releases satisfy ASPICE 4.0 Capability Level 2 (CL2) requirements, maintain verifiable evidence integrity, and eliminate single-agent or single-vendor blind spots.

---

## 2. Five-Layer Quality Assurance Architecture & Role Independence (GP 2.1.6)

To enforce technical role independence (`GP 2.1.6`), aTrainingTracker organizes quality verification across five complementary defense layers:

| Layer | Verification Mechanism | Scope | Responsible Actor | Independence Level |
| :--- | :--- | :--- | :--- | :--- |
| **Layer 1: Automated Contract Gates** | CI, Detekt, Lint, Kover coverage floors, `verify_requirement_governance.py` | Per-commit / Per-PR automated regression & syntax gates | Automated Tooling | Tool-Enforced |
| **Layer 2: Stage 6 Cold Carbon Review** | Independent, zero-build, adversarial review on squashed commit | Single-ticket diff on `sprint/<ID>` prior to human handoff | Fresh Cold `Gemini 4 Carbon` (`agent2`) | Cross-Model Independence |
| **Layer 3: Sprint Process QA Audits** | Sampled ASPICE process compliance audits recorded in `docs/engineering/qa_audits/` | $\ge 2$ sampled sprint tickets per 12h sprint | Dedicated `qa` role (`jira_util.py qa-audit`) | Process-Level Independence |
| **Layer 4: Human Acceptance & Device Ride Verification** | End-to-end on-device verification on Google Pixel (`REQ-TST-004`) | Physical BLE/ANT+ sensors, GPS, UI ergonomics | Human Product Owner | External Human Gate |
| **Layer 5: Weekly Dual-Vendor Holistic Reviews** | Whole-subsystem code audits & ASPICE process cross-audits | Existing codebase subsystems & delta commits | **Dual AI Vendors: Google Gemini + Anthropic Claude** | **Cross-Vendor Independence (`GP 2.1.6`)** |

---

## 3. Quality Criteria & Work Product Standards (SUP.1.BP2, SUP.1.BP3)

All engineering work products are evaluated against standardized objective quality criteria:
1. **Source Code & Architecture**:
   - Zero new Detekt / Android Lint warnings; adherence to architectural invariants in `docs/coding_standards.md`.
   - Global coverage floors: $\ge 40\%$ line, $\ge 35\%$ branch coverage via Kover (`./gradlew koverVerifyDebug`).
   - Clean lifecycle management: try-with-resources for SQLite cursors, zero UI-thread blocking, zero Compose snapshot writes during composition.
   - Code-grounding: every symbol, class, and method in `docs/architecture.md` must correspond to authentic code in `app/src/main/`.
2. **User Interface & Localization**:
   - 100% translation parity across all 9 supported locales (`EN, DE, ES, FR, IT, JA, NL, PL, PT`) without raw emoji literals.
   - Ergonomic compliance: visual self-inspection checklist verified across orientation, M3 track artifacts, typography, and touch target size.
3. **Living Documentation & Traceability**:
   - 100% mathematical parity between master index tables and domain shards in `docs/requirements/` and `docs/tests/`.
   - Complete bidirectional traceability between requirements (`REQ-DOM-NNN`) and test cases (`TST-DOM-NNN`).

---

## 4. Assurance of Engineering Processes (SUP.1.BP4)

Adherence to defined engineering processes is enforced through automated lifecycle tooling:
* **AAA Lifecycle Gates (Gates 1–5)**: Sub-tasks advance sequentially via composite CLI commands (`submit-stage` / `gate-verdict`) with strict role locking (`agent1` implementer vs `agent2` auditor).
* **Code Lock (Gate 3)**: Modifying production source code is strictly blocked until Gate 3 (Implementation Plan) passes (`jira_util.py check-gate`).
* **One Commit per Ticket**: Completed tickets are merged as strictly one squashed atomic commit with clean commit messages.
* **Sprint Process Audits**: Independent QA auditor dynamically samples $\ge \max(2, \lceil 0.10 \times N \rceil)$ tickets per sprint to verify process conformance (`jira_util.py qa-audit`).

---

## 5. Non-Conformance Tracking, Escalation & Corrective Action (SUP.1.BP5)

Whenever non-conformances, gate escapes, or evidence integrity defects are detected:
1. **Classification**:
   - **Critical**: Evidence fabrication, unverified claims, or severe security/data corruption risks.
   - **Major**: Process deviations, ungrounded requirements, missing test specifications, or role-separation breaches.
   - **Minor**: Documentation typos, formatting discrepancies, or minor metric calculation issues.
2. **Escalation Path**:
   - In-sprint stage defect $\rightarrow$ `gate-verdict <KEY> REVISION` or `final-review-verdict <KEY> BOUNCE` (healing within lane).
   - Requirement/design ambiguity $\rightarrow$ Assign to `human` with descriptive comment (Rule 6.1 unattended escalation).
   - Systemic audit finding $\rightarrow$ File dedicated CAPA ticket under Epic `ATT-232` (or active milestone remediation epic) ranked at `rank-top`.
3. **Communication**: All non-conformances and corrective action resolutions are recorded in Jira comments and summarized in weekly retrospective and audit deliverables.

---

## 6. Quality Records & Metrics Retention (SUP.1.BP6)

To maintain verifiable audit trails, all quality records are formally version-controlled as Configuration Items (`cm_plan.md`):
* `docs/engineering/aspice_audits/`: Versioned weekly dual-vendor ASPICE cross-audit reports (`YYYY-MM-DD_dual_vendor_aspice_audit.md`).
* `docs/engineering/code_reviews/`: Versioned weekly dual-vendor code review reports and `coverage_matrix.md`.
* `docs/engineering/emulator_sweeps/`: Versioned weekly & pre-release serial Multi-OS & 9-language emulator sweep reports (`YYYY-MM-DD_sweep.md`) and per-locale screenshots (`REQ-PRO-135`, `ATT-8222`).
* `docs/engineering/qa_audits/`: Per-sprint QA process compliance audit reports (`sprint_<sprint_id>.md`).
* `docs/engineering/gates/`: Sprint quality gate export records.
* `docs/engineering/metrics/process_metrics.jsonl`: PA 4.1 quantitative process metrics and PA 4.2 SPC control limits.

---

## 7. Mandatory Weekly Dual-Vendor Review Mandate (Layer 5)

Under `SWE.2`, `SWE.3.BP4`, `SWE.4.BP2`, `SUP.1.BP1/BP3`, and `GP 2.1.6`, the project mandates regular weekly ("in der Regel wöchentlich", cadence $\le 7$ days) independent reviews conducted across **two distinct AI vendors (Google Gemini and Anthropic Claude)**:

### 7.1 Dual Review Pillars
1. **ASPICE 4.0 Process & Evidence Cross-Audits (`ATT-8212`)**:
   - Audits living documentation, gate verdicts, test integrity, and ASPICE BP evidence in `docs/engineering/aspice_audits/`.
2. **Holistic Codebase & Architecture Reviews (`ATT-8213`)**:
   - Broad codebase scope: reviews are **NOT** restricted to delta diffs (`<last_sha>..develop`).
   - Every weekly review inspects **full production & test subsystem slices** of the existing codebase alongside recent changes, systematically tracking progress via `docs/engineering/code_reviews/coverage_matrix.md` across all 9 package domains (`tracker/`, `banalservice/`, `database/`, `exporter/`, `ui/tracking/`, `ui/aftermath/`, `ui/settings/`, `tools/`, `test/`).
   - Reconciles findings into `docs/engineering/code_reviews/YYYY-MM-DD_dual_vendor_code_review.md` and files actionable Bug/Improvement tickets under Epic `ATT-232` / `ATT-8138`.
3. **Serial Multi-OS & 9-Language Emulator UI Verification Sweeps (`ATT-8222` / `REQ-PRO-135`)**:
   - Executed serially across representative Android OS levels and all 9 app locales (`en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`) via `python3 tools/run_multi_os_emulator_sweep.py` strictly on `emulator-*` instances (`REQ-TST-004`).
   - Audits `logcat` (`FATAL EXCEPTION`, `ANR`, `StrictMode`, `IllegalFormat*`) and `uiautomator dump` bounds, archiving results under `docs/engineering/emulator_sweeps/YYYY-MM-DD_sweep.md`.

### 7.2 Automated Backlog Scheduling & Enforcement (`ensure-weekly-reviews`)
* To guarantee that weekly reviews and multi-OS/9-locale sweeps cannot be overlooked or deprioritized during active sprints, `tools/jira_util.py ensure-weekly-reviews` runs during Ceremony 1 (Sprint Planning) and Ceremony 2 (Sprint Review).
* If `> 7 days` have elapsed since the latest completed review/sweep (with new `develop` commits for emulator sweeps, or unreviewed subsystems remaining in `coverage_matrix.md` / `aspice_audits/`) and no open ticket exists in Jira, the tooling **automatically creates the due ticket under Epic `ATT-232` and ranks it at the very top of the Product Backlog (`rank-top`)**.
* `release-manager/SKILL.md` verifies that weekly dual-vendor reviews and multi-OS/9-language emulator sweeps are current ($\le 7$ days) prior to production release branch cutting.

---

## 8. Static Analysis Baseline Governance & Java Tooling Evaluation (SWE.4)

### 8.1 CI Safety Guards & Monotonic Baseline Ratchet (`REQ-PRO-123.1`, `REQ-PRO-123.2`)
* **Custom Safety Linters**: `.github/workflows/android-branch-build.yml` and `.github/workflows/android-release.yml` execute `tools/lint_cursor_safety.py`, `tools/lint_lifecycle_safety.py`, and `tools/lint_safe_enum.py` prior to `:app:assembleDebug` / `:app:assembleRelease`, failing the build on any non-zero exit code.
* **Baseline Ratchet**: `tools/check_static_analysis_baselines.py` verifies `app/detekt-baseline.xml` (`9,516` `<ID>` entries) and `app/lint-baseline.xml` (`238` `<issue>` entries) never grow relative to the Git base ref or current ceiling (falling back cleanly to ceiling limits on shallow clones), while `tools/sprint_metrics.py` tracks counts and per-sprint deltas.

### 8.2 Java Static Analysis Evaluation (`ADR-001`, `REQ-PRO-123.4`)
Per `docs/architecture/adr_001_java_core_services_migration.md` (`ADR-001` / `REQ-ARC-005`), **120** `.java` files remain in `app/src/main/java` under **Gradle `9.6.0`**, **AGP `9.4.0`**, and **JDK `21`** (Java `17` `compileOptions`). Evaluation of dedicated Java analyzers:

| Tool | Build Stack Compatibility (AGP 9.4 / JDK 21) | Adoption Friction & Baseline Support | Determination |
| :--- | :--- | :--- | :--- |
| **PMD** | Source-only AST scan; independent of AGP variant bytecode wiring and JDK internals | Low friction; supports ruleset exclusions and ratchetable violation baselines across 120 legacy `.java` files | **Recommended** (incremental adoption on `Keep` core Java subsystems) |
| **SpotBugs** | Requires AGP `9.4.0` variant bytecode paths across mixed Java/Kotlin compilation | Medium-High friction; large XML exclude filter maintenance across legacy sensor/DB classes | **Deferred** (revisit only if bytecode-only dataflow checks are required) |
| **Error Prone** | Hooks `javac` internals; requires brittle JDK 21 `--add-exports` flags | High friction; compile-time failures across legacy Java patterns block builds without mass annotation | **Rejected** for legacy codebase |

### 8.3 Detekt & Android Lint Baseline Burn-Down Plan
* **Detekt (`app/detekt-baseline.xml`, starting baseline: `9,516` `<ID>` entries)**: Target reduction of **250 entries/sprint** via `ADR-001` Tier 1–2 migrations (`ATT-7903`, `ATT-7905`, `ATT-7906`) and boy-scout cleanup on touched Kotlin files.
* **Android Lint (`app/lint-baseline.xml`, starting baseline: `238` `<issue>` entries)**: Target reduction of **20 entries/sprint** alongside `app/build.gradle` `Range` suppression burn-down (retaining `MissingTranslation` via `TranslationParityTest` and `DuplicatePlatformClasses`).

---

## 9. Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.1.0` | 2026-11-15 | AI Agent (`ATT-8212`, `ATT-8213`) | `ATT-8212`, `ATT-8213` | Established dual-vendor weekly QA review strategy (`SUP.1`, `GP 2.1.6`). |
| `1.2.0` | 2026-11-17 | AI Agent (`ATT-8164`) | `ATT-8164` | Standardized `DOC-MGT-QA` header, reset self-declared `Approved` to `Reviewed` (`Pending PO Sign-Off`), and added Change History. |
| `1.2.1` | 2026-11-17 | AI Agent (`ATT-8169`) | `ATT-8169` | Added Section 8 documenting CI safety guards, Detekt/Lint baseline ratchet & burn-down plan, and PMD vs SpotBugs vs Error Prone Java evaluation referencing `ADR-001`. |
| `1.2.2` | 2026-11-20 | Lane A Flash (`ATT-8222`, `--as agent1`) | `ATT-8222` | Added Pillar 3 (`Serial Multi-OS & 9-Language Emulator UI Verification Sweeps`) to §6 & §7 and `ensure-weekly-reviews` scheduling + pre-release gate integration (`REQ-PRO-135`). |
