# Kapitel 7: Physiologie, Trainingszonen & Leistungsanalyse

<a id="training-zones-and-power-models"></a>

<table>
<tr>
<td width="65%" valign="top" markdown="1">

## 7.1 Coggan 7-Zonen-Modell, FTP, CP & W'

Wissenschaftlich fundierte Trainingssteuerung erfordert präzise physiologische Schwellenwerte. Unter **Profil & Zonen** passt du deine Leistungs- und Herzfrequenzzonen individuell an:

- **Funktionelle Schwellenleistung (FTP)**: Basis für die 7 Coggan-Leistungszonen (von *Z1 Active Recovery* über *Z4 Threshold* bis *Z6/Z7 Anaerobe Kapazität*).
- **Critical Power (CP) & Anaerobe Arbeitskapazität (W')**:
  - `CP`: Dauerhafte aerobe Leistungsgrenze in Watt.
  - `W'`: Deine endliche anaerobe Energiereserve oberhalb von CP in Kilojoule (kJ).
  - **Integrierter 2-Punkt CP/W'-Schätzer**: Trage einfach zwei All-Out-Bestleistungen ein, um CP und W' mathematisch exakt berechnen zu lassen.
- **Herzfrequenzzonen**: Automatische Zonenberechnung nach Karvonen (unter Berücksichtigung von Ruhe- und Maximalpuls).

</td>
<td width="35%" valign="top" align="center">
  <a href="../img/07_training_zones.png"><img src="../img/07_training_zones.png" width="240" alt="Coggan 7-Zonen-Leistungsmodell" /></a>
  <br/><sub><em>Konfiguration der Coggan-Leistungszonen in Profil & Zonen</em></sub>
</td>
</tr>
</table>

---

<a id="scientific-telemetry-metrics"></a>

<table>
<tr>
<td width="65%" valign="top" markdown="1">

## 7.2 Wissenschaftliche Leistungsmetriken: WP, RI, Load, W'bal & EF

Während und nach deinen Einheiten berechnet **aTrainingTracker** modernste trainingswissenschaftliche Kennzahlen:

- **Gewichtete Leistung ($\text{WP}$)**: Gewichtete Wattleistung, die den physiologischen Stress ungleichmäßiger Belastungen (Antritte, Anstiege, Abfahrten) realistisch abbildet.
- **Relative Intensität ($\text{RI}$)**: Verhältnis von WP zu deiner aktuellen FTP ($\text{RI} = \text{WP} / \text{FTP}$).
- **Trainingsbelastung ($\text{Load}$)**: Maß für die physiologische Gesamtdosis der Einheit:
  $$\text{Load} = \frac{t \times \text{WP} \times \text{RI}}{\text{FTP} \times 3600} \times 100$$
- **Skiba Anaerober Akku ($W'_{\text{bal}}$)**: Zeigt in Echtzeit, wie viele Kilojoule deiner anaeroben Energiereserve aktuell noch verfügbar sind und wie schnell sich dein Akku bei Fahrten unterhalb von CP regeneriert.
- **Effizienzfaktor ($\text{EF}$) & Aerobe Entkopplung ($\text{Pw:Hr}$)**: Verhältnis von Leistung zu Herzfrequenz. Ein Drift von $\text{Pw:Hr} > 5\%$ signalisiert kardiovaskuläre Ermüdung.

</td>
<td width="35%" valign="top" align="center">
  <a href="../img/07_power_distribution.png"><img src="../img/07_power_distribution.png" width="240" alt="Leistungsverteilung & W'-Bilanz" /></a>
  <br/><sub><em>Telemetrie-Zeitreihen mit W'-Bilanz (Anaerober Akku)</em></sub>
</td>
</tr>
</table>

---

<a id="performance-management-chart"></a>

<table>
<tr>
<td width="65%" valign="top" markdown="1">

## 7.3 Belastungssteuerung & Form (Training Load & Form)

Die integrierte Belastungssteuerung visualisiert deinen langfristigen Formaufbau nach dem Banister-Impuls-Reaktions-Modell:

- **Fitness (42 Tage)**: Exponentiell geglättete Trainingsbelastung der letzten 42 Tage (blaue Kurve).
- **Ermüdung (7 Tage)**: Kurzfristige Erschöpfung der vergangenen 7 Tage (pinke Kurve).
- **Form (Balance)**: Differenz zwischen Fitness und Ermüdung ($\text{Form} = \text{Fitness} - \text{Ermüdung}$, orange Kurve). Ein positiver Form-Wert signalisiert optimale Renntauglichkeit.
- **Belastungsquote (ACWR)** & **7-Tage-Steigerungsrate**: Schützen dich vor Übertraining und steuern dein Tapering gezielt zum Saisonhöhepunkt.

</td>
<td width="35%" valign="top" align="center">
  <a href="../img/07_telemetry_charts.png"><img src="../img/07_telemetry_charts.png" width="240" alt="Belastungssteuerung (Training Load & Form)" /></a>
  <br/><sub><em>Verlaufskurven der Belastungssteuerung (Fitness, Ermüdung, Form)</em></sub>
</td>
</tr>
</table>

---

[Vorheriges Kapitel: Lieblingsstrecken](06-automatic-route-clustering.md) | [Nächstes Kapitel: Workout-Auswertung & Perioden](08-workout-aftermath-periods.md) | [Zurück zur Übersicht](index.md)
