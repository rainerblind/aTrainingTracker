---
name: ui-designer
description: Rapid Jetpack Compose UI design and visual prototyping skill. Operates in high-velocity exploratory loops using @Preview composables, visual variant diffing, and immediate human check-and-tweak cycles without heavy stage ceremony.
---

# Skill: ui-designer

## Overview
This skill specializes in **high-velocity UI/UX design and Compose component iteration**. Rather than requiring full multi-stage ASPICE cycles or slow physical device builds, `ui-designer` focuses on rapid code tweaks and immediate human visual verification using Jetpack Compose `@Preview` composables.

## Core Workflow Loop
```text
Agent updates Compose code / @Preview
       ↓
Human views preview / renders in IDE
       ↓
Human gives instant visual feedback
       ↓
Agent tweaks padding, typography, colors, layout
       ↓
(Repeat until visually perfected)
```

## Guiding Principles & Design System Tokens
1. **AMOLED True Dark Mode**:
   - Backgrounds: `#000000` (Pure Black, `Color.Black`).
   - Surfaces: Low-luminance dark gray (`#121212`, `#1E1E1E`).
   - Accent colors: High contrast, vibrant sports accents (e.g. electric cyan, lime green, safety orange).
2. **WCAG Contrast & Readability**:
   - High legibility outdoors under bright sunlight and while moving (running/cycling).
   - Glanceability: Large metrics (bold, 32sp+), secondary labels (14-16sp).
3. **Composable `@Preview` Best Practices**:
   - Provide interactive and parameter-driven `@Preview` composables:
     ```kotlin
     @Preview(name = "Light Mode", showBackground = true)
     @Preview(name = "Dark AMOLED Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
     @Composable
     fun PreviewMyComponent() {
         TrainingTrackerTheme {
             MyComponent(...)
         }
     }
     ```
   - Avoid hardcoded hardware dependencies inside composables (pass state down, hoist events up).
   - Use mock data providers for previews to demonstrate all component states: Normal, Selected, Empty, Overflow/Error.
4. **Visual Variant Diffing**:
   - When proposing design choices to the user, present distinct variants side-by-side (e.g. *Variant A: Compact Pill* vs. *Variant B: Outlined Card*).
   - Enable rapid human decision-making with zero latency.
