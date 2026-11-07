# Show HN: Höchstmaß – Agile Agentic ASPICE Framework for Autonomous Pair Programming

> *„Power without control can become destructive. Power guided by intelligence becomes excellence.“*  
> — **ChatGPT**

---

### Dual Attribution Rationale: Why Quote ChatGPT in a Show HN Submission?

We explicitly attribute this opening epigraph to **ChatGPT** for two deliberate reasons:
1. **Radical Attribution & Human/AI Separation**: Engineering integrity requires unvarnished provenance tracking. In our system, every commit, specification, diff, and review is attributed to its authentic actor: `agent1` (Implementation Doing), `agent2` (Adversarial Gate Reviewing), `coordinator` (Stage Orchestrator), or `human` (Human Gatekeeper & System Owner). Obscuring AI origins or claiming sole human authorship is antithetical to auditable engineering.
2. **Cross-Model / Cross-Vendor Adversarial Review (including UI Screenshots across Gemini & ChatGPT)**: Relying on a single model architecture creates monoculture blind spots. Our pipeline pairs Google Gemini with OpenAI ChatGPT for cross-vendor architectural analysis and visual UI screenshot critique, ensuring one model never merely validates its own hallucinations.

---

> [!NOTE]
> ### Etymology: Why „Höchst·maß“?
> **Höchst·maß** (`/ˈhøːçstˌmaːs/`) is a German compound noun: **Höchst-** (highest, supreme, maximum) + **-maß** (standard of measurement, degree, benchmark, or *Maßarbeit*—bespoke precision tailoring). While English speakers might interpret "maximum" as a hard upper bound, German engineering uses *Höchstmaß* to denote **the supreme standard of quality and precision**. We preserve the authentic German orthography—including **ö** and **ß**—to reflect our commitment to uncompromised precision in agentic software engineering.

---

## 1. What is Höchstmaß?

**Höchstmaß** is an open-source framework that adapts automotive software engineering standards (**Automotive SPICE / ASPICE**) to autonomous, multi-agent AI pair programming swarms.

Single-prompt "vibe coding" falls apart on complex projects due to three fundamental LLM failure modes:
1. Mathematical equation & simulation drift in non-linear algorithms.
2. Silent regressions in non-target callers and broken architectural invariants.
3. SDK method hallucinations that pass local type-checking stubs but crash in production bytecode.

Höchstmaß solves this by structuring agent workflows into an automated **Swiss Cheese defense model** with alternating, adversarial multi-agent review gates.

---

## 2. Architecture & The Zipper Principle

Rather than running monolithic prompts, work is decomposed into 5 sequential stages governed by an alternating pair of persistent subagents (`subagent_pro` for deep reasoning, `subagent_flash` for rapid execution):

```
Stage 1: Problem Domain & Root Cause Analysis  ──> Gate 1 Review (Pass/Revision)
Stage 2: Formal Requirements (REQ-XXX / TST-XXX) ──> Gate 2 Review (Pass/Revision)
Stage 3: Implementation Plan & SWE.2 Design     ──> Gate 3 Review (Code Lock Release)
Stage 4: Software Construction & Targeted Tests  ──> Gate 4 Review (Pass/Revision)
Stage 5: Clean-Room Regression & Walkthrough     ──> Gate 5 Review ──> Human Gate
```

### Core Invariants:
* **Rule 4 (Code Lock)**: Source code modification is programmatically locked via CLI tools (`tools/jira_util.py check-gate`) until Gate 3 passes.
* **100% Living Documentation Parity**: Requirement governance scripts verify that all `[Planned]` and `[Verified]` states across domain markdown shards match living master tables with mathematical precision.
* **Bytecode Ground-Truth Auditing**: External SDK bindings are verified using automated bytecode disassembly (`javap -c -p`) in contract tests to eliminate hallucinated methods.

---

## 3. Real-World Production Track Record

We battle-tested Höchstmaß across **53+ production sprints** and **1,100+ closed Jira tickets** on **aTrainingTracker** (an Android Jetpack Compose app handling multi-threaded ANT+ and BLE sensor streams at 1,000 Hz):
* **0 runtime defects** escaped to production releases.
* **875+ living requirements** maintained with 100% test traceability.
* Caught complex regressions before compilation, including floating-point drift in Mader-Heck metabolic models (`ATT-4786`) and memory allocation spikes in real-time DFA alpha-1 HRV pipelines (`ATT-3539`).

Repository & Starter Kits: [`tools/hoechstmass/`](tools/hoechstmass/)
