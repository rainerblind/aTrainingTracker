# Chapter 7: Physiology, Training Zones & Performance Analytics

<a id="training-zones-and-power-models"></a>

<table>
<tr>
<td width="65%" valign="top" markdown="1">

## 7.1 Coggan 7-Zone Power Model, FTP, CP & W'

Rigorous training prescription demands mathematically grounded physiological thresholds. Under **Profile & Zones** (*Profil & Zonen*), configure your personal power and heart rate thresholds:

- **Functional Threshold Power (FTP)**: Foundation for Coggan's classic 7-zone power distribution (from *Z1 Active Recovery* through *Z4 Lactate Threshold* to *Z6/Z7 Anaerobic Capacity*).
- **Critical Power (CP) & Anaerobic Work Capacity (W')**:
  - `CP`: True asymptote of sustainable aerobic metabolism in watts.
  - `W'`: Finite anaerobic battery available above CP, measured in kilojoules (kJ).
  - **Built-In 2-Point CP/W' Estimator**: Input any two maximal all-out efforts to compute CP and W' algebraically.
- **Heart Rate Zones**: Automated zone boundaries computed using Karvonen Heart Rate Reserve formulas accounting for resting and maximum pulse rates.

</td>
<td width="35%" valign="top" align="center">
  <a href="../img/07_training_zones.png"><img src="../img/07_training_zones.png" width="240" alt="Coggan 7-Zone Power Model" /></a>
  <br/><sub><em>Configuring Coggan cycling power zones in Profile & Zones</em></sub>
</td>
</tr>
</table>

---

<a id="scientific-telemetry-metrics"></a>

<table>
<tr>
<td width="65%" valign="top" markdown="1">

## 7.2 Scientific Telemetry Metrics: NP, IF, TSS, W'bal & EF

During and after your sessions, **aTrainingTracker** computes state-of-the-art exercise physiology metrics:

- **Normalized Power ($\text{NP}$)**: Weighted power reflecting the true physiological cost of non-steady efforts (surges, hills, descents).
- **Intensity Factor ($\text{IF}$)**: Ratio of NP to your current FTP ($\text{IF} = \text{NP} / \text{FTP}$).
- **Training Stress Score ($\text{TSS}$)**: Holistic physiological dose of the session:
  $$\text{TSS} = \frac{t \times \text{NP} \times \text{IF}}{\text{FTP} \times 3600} \times 100$$
- **Skiba Anaerobic Battery ($W'_{\text{bal}}$)**: Real-time estimate of remaining anaerobic work capacity and replenishment rates during sub-CP recoveries.
- **Efficiency Factor ($\text{EF}$) & Aerobic Decoupling ($\text{Pw:Hr}$)**: Work rate to cardiac output ratio. Cardiac drift ($\text{Pw:Hr} > 5\%$) indicates cardiovascular fatigue.

</td>
<td width="35%" valign="top" align="center">
  <a href="../img/07_power_distribution.png"><img src="../img/07_power_distribution.png" width="240" alt="Power Distribution & W'bal" /></a>
  <br/><sub><em>Synchronized telemetry charts with Skiba W'bal battery</em></sub>
</td>
</tr>
</table>

---

<a id="performance-management-chart"></a>

<table>
<tr>
<td width="65%" valign="top" markdown="1">

## 7.3 Performance Management Chart (PMC)

The integrated PMC models your longitudinal athletic development based on the Banister impulse-response model:

- **Fitness ($\text{CTL}$ - Chronic Training Load)**: Exponentially weighted training load across the past 42 days (blue curve).
- **Fatigue ($\text{ATL}$ - Acute Training Load)**: Acute fatigue accrued over the past 7 days (pink curve).
- **Form ($\text{TSB}$ - Training Stress Balance)**: Balance between fitness and fatigue ($\text{TSB} = \text{CTL} - \text{ATL}$, orange curve). Positive TSB values indicate race readiness.
- **Acute:Chronic Workload Ratio (ACWR)** & **7-Day Ramp Rate**: Safeguard against overtraining injuries and guide peak performance tapering.

</td>
<td width="35%" valign="top" align="center">
  <a href="../img/07_telemetry_charts.png"><img src="../img/07_telemetry_charts.png" width="240" alt="Performance Management Chart" /></a>
  <br/><sub><em>PMC fitness (CTL), fatigue (ATL), and form (TSB) curves</em></sub>
</td>
</tr>
</table>

---

[Previous Chapter: Favorite Routes](06-automatic-route-clustering.md) | [Next Chapter: Workout Aftermath & Periods](08-workout-aftermath-periods.md) | [Back to Table of Contents](index.md)
