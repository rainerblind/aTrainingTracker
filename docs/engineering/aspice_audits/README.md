# Canonical ASPICE 4.0 Audit Evidence Store: aTrainingTracker

**Document Identifier**: `ASPICE-AUDITS-ATT`  
**Standard**: ASPICE 4.0 `SUP.1.BP6` (Quality Records), `GP 2.1.6` (Ensure independence of quality assurance)  
**Governing Ticket**: [ATT-8212](https://rainerblind.atlassian.net/browse/ATT-8212) | **Requirement**: `REQ-PRO-096`  
**Cadence**: Weekly ("in der Regel wöchentlich", $\le 7$ days)  
**Dual Vendors**: Google Gemini (`Gemini 2.5 Pro` / `Gemini 4 Carbon`) + Anthropic Claude (`Claude 3.7 Sonnet`)  

---

## Purpose & Scope
This directory serves as the canonical version-controlled repository for all independent cross-vendor ASPICE 4.0 process and evidence audits across aTrainingTracker.

To eliminate single-model bias and guarantee technical role independence (`GP 2.1.6`), every weekly audit is performed independently by **both Google Gemini and Anthropic Claude**, evaluating:
1. **ASPICE VDA Scope Compliance**: SWE.1, SWE.2, SWE.3, SWE.4, SWE.5, SWE.6, SUP.1, SUP.8, SUP.9, SUP.10, MAN.3.
2. **Quality Gate Integrity**: Verification of Gate 1–5 verdicts against authentic repository evidence (detecting false passes or unverified gates).
3. **Living Documentation & Traceability Parity**: Bidirectional mathematical parity across master tables and domain shards (`docs/requirements/` $\leftrightarrow$ `docs/tests/`).
4. **Evidence Authenticity**: Ensuring no document claims activities that did not take place.

---

## File Naming Convention
* Standard format: `YYYY-MM-DD_dual_vendor_aspice_audit.md` (e.g. `2026-11-15_dual_vendor_aspice_audit.md`).
* Audits are automatically scheduled at `rank-top` of the Product Backlog via `tools/jira_util.py ensure-weekly-reviews` whenever $\ge 7$ days have elapsed since the latest completed audit record in this directory.
