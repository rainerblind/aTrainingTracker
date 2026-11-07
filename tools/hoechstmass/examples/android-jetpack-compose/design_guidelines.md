# Android Jetpack Compose Design Guidelines & UI Patterns

This document defines the living UI/UX design patterns, Material 3 component selection heuristics, and interaction standards for Android Jetpack Compose applications.

---

## 1. Mode Switcher & Component Selection Heuristics

Selecting the appropriate Material 3 control depends strictly on the **cardinality** and **mutual exclusivity** of the user choices.

### 1.1 Binary Mutually Exclusive Selections (Exactly 2 Options)
* **Standard Control**: Material 3 `SingleChoiceSegmentedButtonRow` with `SegmentedButton`.
* **When to Use**:
  * Choosing between exactly two mutually exclusive display, calculation, or visualization modes (e.g. *5 Zones* vs. *Histogram*, *Table* vs. *Chart*, *Pace* vs. *Speed*).
* **Layout Rules**:
  * Center the segmented row or fill equal horizontal width.
  * Use compact height (`28.dp` in cards, `36.dp` in settings dialogs).
  * Use `SegmentedButtonDefaults.itemShape(index, count)` for seamless rounded boundary pills.
* **Anti-Patterns**:
  * ❌ **Do NOT use `FilterChip`** for binary exclusive choices. Two adjacent chips look unanchored, visually cluttered, and semantically imply independent filter tags rather than a unified switch.
  * ❌ **Do NOT use Radio Buttons** unless part of a long vertical questionnaire list.

### 1.2 Multi-Select & Filter Tags
* **Standard Control**: Material 3 `FilterChip` with leading checkmark icon.
* **When to Use**: Filtering a collection by non-exclusive attributes (e.g. Sensor Types: *Heart Rate*, *Cadence*, *Radar*).

---

## 2. Typography, Color & Theme Tokens

### 2.1 Color Tokens & Contrast
* Never use raw hex colors in composables. Always reference `MaterialTheme.colorScheme` tokens (`primary`, `surfaceVariant`, `onSurface`, etc.).
* Guarantee a minimum contrast ratio of 4.5:1 for body text and 3:1 for large graphical elements.

### 2.2 Elevation & Hierarchy
* Use tonal elevation rather than heavy drop shadows to distinguish cards and dialog surfaces in Material 3.

---

## 3. Touch Targets & Accessibility Standards

* **Minimum Touch Target**: All clickable elements must enforce a minimum touch target area of `48.dp x 48.dp` via `Modifier.minimumInteractiveComponentSize()`.
* **Content Descriptions**: Every `IconButton`, `Icon`, and interactive graphic must declare an informative `contentDescription` or explicitly pass `null` for purely decorative elements.
