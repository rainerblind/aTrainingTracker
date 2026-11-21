# Independent ASPICE 4.0 Audit — Charter (shared by all assessors)

## Object of assessment
- Project: aTrainingTracker (Android sports tracking app, AI-agent-driven "Agile Agentic ASPICE" process).
- Baseline: `develop` @ `d59608099cc288c95e60e2bd03c1c6aef55a5e17` (2026-11-21 05:32).
- Frozen snapshot (READ HERE): `develop`
  (git archive, images/PDFs excluded).
- Git history (read-only queries only): `/home/rainer/AndroidStudioProjects/aTrainingTracker` — use
  `git -C <repo> log/show/diff/branch/tag ... d5960809` etc. Always reference `d5960809` or explicit SHAs, NEVER the
  working tree (it's on a detached older commit with uncommitted changes and an active sprint is running).
- Jira (read-only): from the repo dir, `python3 tools/jira_util.py --as coordinator show <KEY>` and
  `python3 tools/jira_util.py --as coordinator search '<JQL>'`. Project key `SCRUM`, issue keys `ATT-nnnn`.

## HARD RULES
1. STRICTLY READ-ONLY. Do not modify, create or delete anything in the repo, the working tree, git refs, or Jira.
   Allowed jira_util subcommands: `show`, `search`, `status` ONLY. Never `move`, `comment`, `submit-stage`,
   `gate-verdict`, `create-*`, `update-*`, `add-*`, `ensure-*`, `pack-*`, `archive-*`, `check-gate`, `sprint-metrics`,
   or anything else. No git checkout/switch/stash/commit/reset/fetch/push.
2. Do NOT run Gradle, emulators, adb, or the project's python test/tool scripts (an active sprint uses the machine).
   Evidence inspection only.
3. Do not print or copy secrets (`.env*`, `secrets.properties`, tokens).
4. Be independent and skeptical: the project produces large volumes of self-generated documentation. Volume is not
   evidence. Verify claims against primary evidence (code, test sources, git history, Jira history). Distinguish
   "documented/claimed" vs "verified". Do not rely on the project's own prior audit
   (`docs/engineering/aspice_audits/`) or QA audits for conclusions — you may cite them as evidence of SUP.1 activity.
5. Sample, don't boil the ocean. Cite concrete evidence: file path + line, commit SHA, or Jira key, for EVERY finding.

## Reference model: Automotive SPICE PAM 4.0
Rating scale: N (0–15%), P (>15–50%), L (>50–85%), F (>85–100%). (P-/P+, L-/L+ allowed.)
CL1 = PA 1.1 ≥ L. CL2 = PA 1.1 = F and PA 2.1, PA 2.2 ≥ L.

PA 2.1 GPs (4.0): 2.1.1 Determine strategy for performance of the process; 2.1.2 Plan the performance; 2.1.3 Determine
resource needs; 2.1.4 Identify and make available resources; 2.1.5 Monitor and adjust; 2.1.6 Manage interfaces between
involved parties.
PA 2.2 GPs (4.0): 2.2.1 Define requirements for work products; 2.2.2 Define requirements for storage and control of
work products; 2.2.3 Identify, store and control work products; 2.2.4 Review and adjust work products.

Use the ASPICE 4.0 base practices for your processes (rate each BP N/P/L/F with rationale).
Key 4.0 notes: traceability + consistency merged into one BP per engineering process; SWE.4 = Software Unit
Verification; SWE.5 = Software Component Verification and Integration Verification; SWE.6 = Software Verification;
SYS.4 = System Integration and Integration Verification; SYS.5 = System Verification; verification measures need
selection criteria, pass/fail criteria, and results recorded and linked.

## Audit-specific risk themes (check where relevant)
- Real human approval vs. agent self-approval (who approves requirements, plans, releases? is "PO sign-off" real?).
- Independence of reviews / QA when author and reviewer are AI agents (same vendor/model?).
- Evidence authenticity: do referenced REQ/TST/test classes/commits actually exist and match? do "PASS" claims have
  primary records? any post-merge fixes revealing gate leakage?
- Traceability resolvability: sample IDs both directions; dangling, orphan, or bulk-generated links.
- Consistency between plans/process docs and actual practice (git history, Jira).
- Over-documentation / doc rot (stale versions, "Pending" approvals, contradictory statements).

## Output
Write your report as Markdown to the file path given in your task. Structure:
1. Scope covered & sample used (list files/commits/tickets examined).
2. Per process: BP table (BP | rating | rationale | evidence refs), then PA 1.1 rating.
3. PA 2.1 / PA 2.2 observations for your processes (GP table with ratings) — if in your remit.
4. Findings list: ID (your prefix + number), severity (Major = blocks attainment of a PA at the target level /
   Minor = weakens rating / Observation / Strength), statement, evidence, recommendation.
5. Open questions you could not resolve.
Keep the report factual and under ~400 lines. When done, reply with a ≤15-line summary + the file path.
