# Technical Architecture & Coding Standards (Starter Template)

This document serves as the authoritative living source of truth for software engineering (SWE.1 / SWE.2) construction invariants, technical architecture patterns, and testing standards.

---

## 1. Core Engineering Principles & Software Construction

### 1.1 Reuse Before Create & DRY (Shared Code, Utilities & Architecture Over Duplication)
* **Overarching Invariant**:
  * Before designing or implementing a new helper function, repository, formatter, or data structure, developers and AI agents **MUST** inspect the existing codebase and reuse existing shared architectural components.
  * Always favor existing shared utilities, repositories, formatters, and reusable components over writing parallel, ad-hoc, or duplicate code.
* **Canonical Formatters & Data Conversion**:
  * All unit math, string conversions, and scalar representations must route through canonical utilities.
  * Inline manual string template formatting or ad-hoc math directly in presentation layers is strictly discouraged.

### 1.2 Chesterton's Fence for Empty Callback Lambdas & Event Listeners
* **No Casual Callback Suppression**:
  * When refactoring components, views, or controllers, developers and AI agents **MUST NOT** pass an empty lambda (`{}`) or suppress an existing callback parameter without first inspecting all internal call sites within the child component to audit every place that callback is invoked.
  * Passing `{}` is functionally a silent deletion of feature behavior.
* **Intent-Explicit Callback Signatures**:
  * Avoid dual-use generic callbacks. Name callbacks explicitly after user or system intent.

### 1.3 Prohibition of Sorting and Ranking on Formatted Display Strings
* **Strict Numeric Primitive Invariant**:
  * All sorting comparators, tie-breakers, candidate rankings, and thresholds in domain repositories and view logic **MUST** evaluate raw numeric primitives (`Double`, `Float`, `Long`, `Int`), never formatted display strings.
  * Formatted strings often switch units or include locale-dependent thousands/decimal separators. String comparisons are lexicographical by character code, causing candidate entities to flip or sort incorrectly at unit boundaries.
  * Models exposing formatted strings for UI presentation must also expose raw unrounded primitives for mathematical comparison and sorting.

### 1.4 Explicit Confirmation for Destructive or Cache-Invalidating Operations
* **Destructive & Bulk Re-computation Guard**:
  * Any user-triggered action that invalidates existing historical caches, re-indexes persistent databases, clears bulk data, or resets persistent preferences **MUST** be protected by an explicit confirmation dialog.
  * The dialog must clearly explain the consequences of the operation before executing the background task.

### 1.5 Reactive Stream Subscription Over Static Snapshot Reads
* **Active Stream Subscription Invariant**:
  * When a reactive pipeline or computed view state derives state from dynamic data flows, those streams **MUST** be subscribed as active reactive inputs to the pipeline.
  * Never read a static snapshot value of a rapidly changing stream inside a calculation that only updates on unrelated events. Static snapshot reads leave derived state frozen during live system activity.

---

## 2. Project Architecture & Extensibility Guidelines (Template Placeholders)

### 2.1 Database & Persistence Architecture
* *[Project Specific]*: Define entity mapping symmetry, migration strategies, and database transaction boundaries.

### 2.2 Concurrency & Thread Safety
* *[Project Specific]*: Define dispatcher injection standards, thread pools, and cancellation lifecycle contracts.

### 2.3 Error Handling & Logging Hygiene
* *[Project Specific]*: Define structured logging standards, exception propagation policies, and telemetry instrumentation.
