h2. Bug Description
In ConfigureFilterDialog, switching to or configuring Exponential Smoothing (EXPONENTIAL_SMOOTHING) allows the filter constant to retain invalid values from preceding selections (e.g., constant = 3.0 inherited from the 3s power moving average preset).
Mathematically, the exponential smoothing factor alpha must strictly satisfy 0 < alpha <= 1. A value of 3.0 (alpha=3.00) is physically and mathematically invalid, leading to divergent signal estimation.

h2. Steps to Reproduce
1. In Tracking view, configure a sensor field (e.g. Cycling Power with 3s moving average preset active, constant = 3.0).
2. Open ConfigureFilterDialog and select "Manuell / Experte" (Custom / Expert).
3. In "Glättungsart" (Smoothing Type) dropdown, select "Exponentielle Glättung" (Exponential Smoothing).
4. Observe that the guidance card renders "Exponentielle Glättung (alpha=3,00)" and the numeric input field displays "3.0".

h2. Expected Behavior
- When switching to Exponential Smoothing, the constant must automatically be clamped/defaulted into the valid range (0, 1] (e.g., 0.8 or 0.5) if the current constant is > 1.0 or <= 0.0.
- State machines and text input validation must enforce 0 < alpha <= 1 for EXPONENTIAL_SMOOTHING.

h2. Environment & Context
- Release: V4.9.38
- Active Sprint: 2026-39.3
- Epic: ATT-754 (Filtering)
- Evidence Screenshot: docs/attachments/exponential_smoothing_bug.png
