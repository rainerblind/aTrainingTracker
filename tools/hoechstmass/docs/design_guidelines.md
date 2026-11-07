# UI & Interaction Design Guidelines (Starter Template)

This document serves as the living source of truth for UI/UX patterns, component selection heuristics, and interaction design principles.

---

## 1. Mode Switcher & Component Selection Heuristics

Selecting the right control depends strictly on the **cardinality** and **mutual exclusivity** of the choices.

### 1.1 Binary Mutually Exclusive Mode Selections (Exactly 2 Options)
* **Standard Control**: Segmented Button Row / Two-Option Pill Switch.
* **When to Use**: Choosing between exactly two mutually exclusive display, calculation, or visualization modes (e.g. Table vs. Chart, Light vs. Dark).
* **Anti-Pattern (Avoid)**:
  * ❌ Do NOT use separate independent chips or isolated toggles for binary exclusive choices.
  * ❌ Do NOT use radio buttons unless part of an extensive questionnaire form.

### 1.2 Categorical Filters & Multi-Option Tag Sets (3–4 Options)
* **Standard Control**: Filter Chips / Suggestion Chips.
* **When to Use**:
  * Filtering lists by categories.
  * Selecting among 3 to 4 distinct discrete states where each option has clear descriptive context.
* **Ordering Rule ('Other' on the Far Right)**:
  * Across all categorical chip selectors, specific primary options must appear first, and the catch-all `Other` / `Misc` option **MUST** always be positioned on the **far right**, even when preselected as default.

### 1.3 Large Option Sets (>4 Options)
* **Standard Control**: Dropdown Menu, Selection Dialog, or Modal Bottom Sheet.
* **When to Use**: Options list exceeds available horizontal screen space or screen real estate.

### 1.4 Discrete Scalar Ranges & Durations ($\ge 5$ Numeric Values)
* **Standard Control**: Discrete Slider or Stepped Range Picker with snap points and live value readout.
* **When to Use**: Configuring durations, distances, or numeric settings offering $\ge 5$ discrete scalar options.
* **Anti-Pattern (Avoid)**:
  * ❌ Do NOT use multi-chip wrapping rows for large scalar sets. Wrapping creates ragged, uneven lines and poor touch rhythm.

---

## 2. Dialogs, Modals & Layout Contracts

### 2.1 Dialog Action Hierarchy & Edge Padding
* **Action Button Order**:
  * Positive/Affirmative action on the primary emphasis position.
  * Dismissive/Cancel action on the neutral secondary position.
  * Destructive action distinctly colored (e.g., error red) and separated from accidental touches.
* **Touch Targets & Margins**:
  * Ensure touch targets meet minimum accessible dimensions (e.g. 48×48 dp/px).
  * Consistent content edge padding across dialog bodies and sheets.
