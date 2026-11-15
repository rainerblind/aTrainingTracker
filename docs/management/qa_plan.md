# Project Quality Assurance Strategy & Plan: aTrainingTracker

**Document Identifier**: `QA-PLAN-ATT`  
**Standard**: ASPICE 4.0 `SUP.1` (Quality Assurance) & `GP 2.1.6` (Ensure independence of quality assurance)  
**Target Release**: `V5.0.0` | **Version**: 1.0.0  
**Status**: Approved | **Date**: 2026-11-15  
**Governing Tickets**: [ATT-8212](https://rainerblind.atlassian.net/browse/ATT-8212), [ATT-8213](https://rainerblind.atlassian.net/browse/ATT-8213) | **Requirements**: `REQ-PRO-095`, `REQ-PRO-061`  

---

## 1. Purpose & Quality Objectives (SUP.1.BP1)

This Quality Assurance (QA) Plan defines the independent quality assurance strategy, organizational independence boundaries, verification levels, and automated scheduling controls for **aTrainingTracker**. Its primary objective is to guarantee that work products, engineering processes, and releases satisfy ASPICE 4.0 Capability Level 2 (CL2) requirements and prevent single-agent or single-vendor bias.

---

## 2. Five-Layer Quality Assurance Architecture

To enforce technical role independence (`GP 2.1.6`), aTrainingTracker organizes quality verification across five complementary defense layers:

| Layer | Verification Mechanism | Scope | Responsible Actor | Independence Level |
| :--- | :--- | :--- | :--- | :--- |
| **Layer 1: Automated Contract Gates** | CI, Detekt, Lint, Kover coverage floors, `verify_requirement_governance.py` | Per-commit / Per-PR automated regression & syntax gates | Automated Tooling | Tool-Enforced |
| **Layer 2: Stage 6 Cold Carbon Review** | Independent, zero-build, adversarial review on squashed commit | Single-ticket diff on `sprint/<ID>` prior to human handoff | Fresh Cold `Gemini 4 Carbon` (`agent2`) | Cross-Model Independence |
| **Layer 3: Sprint Process QA Audits** | Sampled ASPICE process compliance audits recorded in `docs/engineering/qa_audits/` | $\ge 2$ sampled sprint tickets per 12h sprint | Dedicated `qa` role (`jira_util.py qa-audit`) | Process-Level Independence |
| **Layer 4: Human Acceptance & Device Ride Verification** | End-to-end on-device verification on Google Pixel (`REQ-TST-004`) | Physical BLE/ANT+ sensors, GPS, UI ergonomics | Human Product Owner | External Human Gate |
| **Layer 5: Weekly Dual-Vendor Holistic Reviews** | Whole-subsystem code audits & ASPICE process cross-audits | Existing codebase subsystems & delta commits | **Dual AI Vendors: Google Gemini + Anthropic Claude** | **Cross-Vendor Independence (`GP 2.1.6`)** |

---

## 3. Mandatory Weekly Dual-Vendor Review Mandate (Layer 5)

Under `SWE.2`, `SWE.3.BP4`, `SWE.4.BP2`, `SUP.1.BP1/BP3`, and `GP 2.1.6`, the project mandates regular weekly ("in der Regel wöchentlich", cadence $\le 7$ days) independent reviews conducted across **two distinct AI vendors (Google Gemini and Anthropic Claude)**:

### 3.1 Dual Review Pillars
1. **ASPICE 4.0 Process & Evidence Cross-Audits (`ATT-8212`)**:
   - Audits living documentation, gate verdicts, test integrity, and ASPICE BP evidence in `docs/engineering/aspice_audits/`.
2. **Holistic Codebase & Architecture Reviews (`ATT-8213`)**:
   - Broad codebase scope: reviews are **NOT** restricted to delta diffs (`<last_sha>..develop`).
   - Every weekly review inspects **full production & test subsystem slices** of the existing codebase alongside recent changes, systematically tracking progress via `docs/engineering/code_reviews/coverage_matrix.md` across all 9 package domains (`tracker/`, `banalservice/`, `database/`, `exporter/`, `ui/tracking/`, `ui/aftermath/`, `ui/settings/`, `tools/`, `test/`).
   - Reconciles findings into `docs/engineering/code_reviews/YYYY-MM-DD_dual_vendor_code_review.md` and files actionable Bug/Improvement tickets under Epic `ATT-232` / `ATT-8138`.

### 3.2 Automated Backlog Scheduling & Enforcement (`ensure-weekly-reviews`)
* To guarantee that weekly reviews cannot be overlooked or deprioritized during active sprints, `tools/jira_util.py ensure-weekly-reviews` runs during Ceremony 1 (Sprint Planning) and Ceremony 2 (Sprint Review).
* If `> 7 days` have elapsed since the latest completed review (or unreviewed subsystems remain in `coverage_matrix.md`) and no open review ticket exists in Jira, the tooling **automatically creates the review ticket under Epic `ATT-232` and ranks it at the very top of the Product Backlog (`rank-top`)**.
* `release-manager/SKILL.md` verifies that weekly dual-vendor reviews are current prior to production release branch cutting.
