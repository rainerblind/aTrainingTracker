# Höchstmaß — Android Jetpack Compose Reference Showcase

This directory provides the authentic, real-world reference implementation and governance showcase for the **Höchstmaß (Agile Agentic ASPICE)** methodology, derived directly from the production Android application **aTrainingTracker**.

---

## 1. Showcase Overview

Unlike toy examples or synthetic benchmarks, this reference showcase reflects the hard-won engineering principles developed and refined over **53+ production sprints** and **1,100+ closed Jira tickets**:
* **Application Domain**: High-frequency real-time biometric and telemetry tracking (ANT+, Bluetooth Low Energy, GPS, Power Meters, Cycling Radar, DFA alpha-1 HRV, and Metabolic Modeling).
* **Architecture**: Jetpack Compose, Material 3, Android Architecture Components (ViewModel, StateFlow), Room Database, Foreground Tracking Services, and Multi-Threaded Sensor Pipelines.
* **Governance Model**: Agile Agentic ASPICE (AAA) with 5-stage defense layers, automated contract tests, and dual-agent adversarial auditing.

---

## 2. Directory Contents

* **[`coding_standards.md`](coding_standards.md)**: Production Kotlin and Jetpack Compose engineering standards, covering state hygiene, coroutine dispatchers, canonical formatters, and architectural invariants.
* **[`design_guidelines.md`](design_guidelines.md)**: Jetpack Compose and Material 3 design system rules, component selection heuristics (e.g. `SingleChoiceSegmentedButtonRow` vs. `FilterChip`), theme tokens, and accessibility standards.
* **[`CASE_STUDIES.md`](CASE_STUDIES.md)**: Detailed post-mortem breakdowns of 3 critical adversarial reviews where dual-agent ASPICE gates prevented catastrophic regressions (Metabolic Drift, Real-Time HRV, and Radar Bytecode Hallucinations).
* **[`artifacts/`](artifacts/)**: Authentic Stage 1 through Stage 5 lifecycle deliverables and sprint retrospective artifacts demonstrating the complete ASPICE audit trail.
