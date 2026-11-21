# Process Tooling & Engineering Documentation Inventory (Lean AAA v2 Diet)

**Document ID**: `DOC-MGT-INVENTORY`  
**Document Identifier**: `INV-TOOL-DOC-ATT`  
**Standard**: ASPICE 4.0 `MAN.3` / `SUP.8` (Process Infrastructure & Documentation Hygiene)  
**Target Release**: `V5.0.0` | **Version**: 1.0.0  
**Status**: Reviewed | **Approver**: Pending PO Sign-Off | **Last Review Date**: 2026-11-21  
**Governing Ticket**: [ATT-9547](https://rainerblind.atlassian.net/browse/ATT-9547) (Parent Epic [ATT-9537](https://rainerblind.atlassian.net/browse/ATT-9537))  

---

## 1. Anti-Bloat Governance Rule (`Rule 8.10` / `ATT-9547`)

> **Measured-Problem Gate**: Any ticket proposing new process tooling (`tools/*.py`) or a new engineering document category (`docs/engineering/*`) **MUST** cite a concrete measured problem (a weekly `docs/engineering/metrics/method_scorecard.jsonl` metric, a `process_metrics.jsonl` SPC alert, or an archived ASPICE/QA audit finding). Unbacked process-tooling proposals SHALL be rejected at backlog refinement / Ceremony 1.

---

## 2. Inventory of Non-Test `tools/*.py` Scripts (34 Tools)

| Tool (`tools/`) | LoC | Last Touch | Tests | Callers | Category / Owner | Decision | Rationale |
| :--- | ---: | :---: | :---: | ---: | :--- | :---: | :--- |
| `repair_requirement_shards.py` | 328 | 2026-11-12 | N | 0 | One-off migration | **ARCHIVE** | Moved to `tools/archive/` (zero callers, historical one-off repair). |
| `resample_high_density_drawables.py` | 94 | 2026-10-16 | N | 0 | One-off asset script | **ARCHIVE** | Moved to `tools/archive/` (zero callers, historical drawable resample). |
| `locked_build_merge.py` | 12 | 2026-11-08 | N | 1 | Legacy lock shim | **MERGE (later)** | Superseded by `parallel_sprint_lock.py`; retain until legacy caller/test removed. |
| `review_agent.py` | 116 | 2026-11-06 | N | 4 | Legacy gate shim | **MERGE (later)** | Deprecated pre-`gate-verdict` shim still asserted by `test_jira_accounts.py`. |
| `lint_cursor_safety.py` | 208 | 2026-11-12 | N | 1 | Safety linter | **KEEP / MERGE** | Active safety gate (used by `auto_gate.py` & Tier-2); candidate to fold into unified linter post-experiment. |
| `lint_lifecycle_safety.py` | 67 | 2026-10-15 | N | 1 | Safety linter | **KEEP / MERGE** | Active safety gate (used by `auto_gate.py` & Tier-2). |
| `lint_safe_enum.py` | 68 | 2026-10-15 | N | 1 | Safety linter | **KEEP / MERGE** | Active safety gate (used by `auto_gate.py` & Tier-2). |
| `jira_util.py` | 7,057 | 2026-11-21 | Y* | 25 | Core Jira CLI | **KEEP (diet)** | Core workflow CLI (tested via `test_jira_*.py`). Freeze growth; new features require measured problem. |
| `verify_requirement_governance.py` | 6,567 | 2026-11-21 | Y | 19 | Core governance | **KEEP (diet)** | Living-doc & ASPICE verifier. Freeze growth under `Rule 8.10`. |
| `parallel_sprint_lock.py` | 627 | 2026-11-18 | Y* | 9 | Merge/lock CLI | **KEEP** | POSIX flock mutual exclusion & `--merge-ticket` (`test_flock_serialization.py`). |
| `sprint_metrics.py` | 1,148 | 2026-11-21 | Y | 6 | Sprint KPI/SPC | **KEEP** | Per-Schicht metrics & SPC (`process_version: "aaa-v2"`). |
| `method_scorecard.py` | 544 | 2026-11-21 | Y | 2 | Lean AAA v2 | **KEEP** | Weekly effort/defect/PO-cost scorecard (`ATT-9539`). |
| `auto_gate.py` | 537 | 2026-11-21 | Y | 3 | Lean AAA v2 | **KEEP** | Automated quality gate + SHA-256 per-ticket evidence bundler (`ATT-9543`/`9544`). |
| `po_weekly_session.py` | 352 | 2026-11-21 | Y | 2 | Lean AAA v2 | **KEEP** | Weekly PO digest & signed baseline CLI (`ATT-9545`). |
| `baseline_manager.py` | 453 | 2026-11-17 | Y | 4 | SUP.8 baselines | **KEEP** | Immutable sprint/release tags & manifests. |
| `generate_test_evidence.py` | 765 | 2026-11-17 | Y | 3 | Tier-2 evidence | **KEEP** | Per-Schicht test evidence export; align with `auto_gate.py` post-experiment. |
| `generate_release_verification_summary.py` | 886 | 2026-11-18 | Y | 2 | Release gate | **KEEP** | Release verification summary (`release-manager`). |
| `check_secrets_hygiene.py` | 278 | 2026-11-18 | Y | 0 | Security gate | **KEEP** | Secrets leak guard (`release-manager` / CI). |
| `check_static_analysis_baselines.py` | 299 | 2026-11-19 | Y | 1 | Static analysis | **KEEP** | Lint/Detekt ratchet check. |
| `shard_living_docs.py` | 853 | 2026-11-18 | Y | 2 | Living docs | **KEEP** | Requirement/test shard splitter & index sync. |
| `verify_skills.py` | 124 | 2026-11-21 | Y* | 1 | Skill contract | **KEEP** | Validates `.agents/skills/` frontmatter & templates. |
| `sprint_bisect.py` | 346 | 2026-11-08 | Y | 1 | Diagnostics | **KEEP** | Exponential regression bisection (`regression-bisect`). |
| `review_tool.py` | 375 | 2026-11-07 | Y | 1 | Human review UX | **KEEP** | Isolated `.reviews/` Emacs inline review staging. |
| `agent_emulator.py` | 489 | 2026-11-11 | Y | 9 | Product testing | **KEEP** | `emulator-5554` bootstrap & golden DB seeding. |
| `ble_hr_simulator.py` | 704 | 2026-11-12 | Y | 5 | Product testing | **KEEP** | BLE Heart Rate / HRV / Polar simulator. |
| `ble_power_simulator.py` | 503 | 2026-10-15 | Y | 3 | Product testing | **KEEP** | BLE Cycling Power simulator. |
| `ble_radar_simulator.py` | 1,116 | 2026-11-16 | Y | 7 | Product testing | **KEEP** | BLE Rearview Radar V1/V2 simulator. |
| `fake_gps.py` | 672 | 2026-11-11 | Y | 3 | Product testing | **KEEP** | Mock GPS route injector. |
| `replay_workout.py` | 2,931 | 2026-11-12 | Y | 4 | Product testing | **KEEP** | End-to-end TCX/FIT workout replay harness. |
| `simulate_kill.py` | 1,378 | 2026-11-11 | Y | 0 | Product testing | **KEEP** | Process-death crash-recovery test harness. |
| `run_simulator_self_tests.py` | 458 | 2026-11-17 | Y* | 3 | Product testing | **KEEP** | Simulator self-test runner (`test_simulator_self_tests.py`). |
| `run_multi_os_emulator_sweep.py` | 909 | 2026-11-20 | Y | 3 | Product testing | **KEEP** | Multi-API Android emulator sweep. |
| `extract_pixel_field_evidence.py` | 759 | 2026-11-17 | Y | 2 | SYS.5 field QA | **KEEP** | Read-only Pixel 10 authentic telemetry extractor. |
| `generate_promo_video.py` | 536 | 2026-11-04 | N | 0 | Store assets | **KEEP** | Play Store promo video generator. |

---

## 3. Inventory of `docs/` Categories

| Category (`docs/`) | File Count | Decision | Rationale |
| :--- | ---: | :---: | :--- |
| `docs/engineering/{walkthroughs,plans,analysis,test_specs}` | 5,689 | **FROZEN / CAPPED** | Legacy 4-file docs kept read-only (no retroactive conversion); new `tier-b` uses 1 file (`tickets/`), `tier-c` uses 0 (`ATT-9541`/`9542`). |
| `docs/engineering/tickets/` | 1 | **KEEP (Lean v2)** | Single concise combined deliverable (~1–2 pages) for `tier-b` tickets (`ATT-9541`). |
| `docs/engineering/test_evidence/` | 39 | **KEEP (Lean v2)** | Machine-generated per-Schicht & per-ticket SHA-256 evidence bundles (`ATT-9544`). |
| `docs/engineering/{retros,gates,qa_audits,reviews,aspice_audits,code_reviews,metrics,emulator_sweeps}` | 177 | **KEEP** | Core SUP.1 / PA 4.1–5.2 evidence & weekly PO digest records. |
| `docs/{requirements,tests,architecture,system}` | 49 | **KEEP** | Living SWE.1/SWE.2/SWE.4/SYS.2/SYS.3 specifications. |
| `docs/management/` | 7 | **KEEP** | Governance, risk register, release plan, experiment protocol & this inventory. |
| `docs/{guide,store_presence,presentations,releases,screenshots,attachments}` | 270 | **KEEP** | User manual, Play Store assets, Höchstmaß/Vorgehensmodell decks, release manifests. |

---

## 4. Change History

| Version | Date | Author | Ticket | Summary of Changes |
| :--- | :--- | :--- | :--- | :--- |
| `1.0.0` | 2026-11-21 | Workflow Architect (`Interactive-PO`) | `ATT-9547` | Initial inventory of all 34 `tools/*.py` scripts and `docs/` categories with PO-approved `KEEP`/`MERGE`/`ARCHIVE` decisions; archived `repair_requirement_shards.py` and `resample_high_density_drawables.py` to `tools/archive/`; established `Rule 8.10` Measured-Problem Gate. |
