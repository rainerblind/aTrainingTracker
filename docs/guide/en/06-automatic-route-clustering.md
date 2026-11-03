# Chapter 6: Route Clustering & Automated Intelligence ("Lieblingsstrecken")

<a id="automatic-clustering-engine"></a>
## 6.1 The Autonomous Clustering Engine

Athletes naturally establish favorite loops, commute routes, and threshold testing hills. aTrainingTracker uncovers these geographic patterns autonomously in the background:

- **Geometric Path Analysis**: Following workout completion, an offline spatial clustering algorithm (leveraging Fréchet distance and Hausdorff metric bounding) inspects the GPS track against your history.
- **Cluster Synthesis (*Lieblingsstrecken*)**: When 2 or more activities share identical route corridors, they are automatically consolidated into an authoritative Route Cluster.
- **Automated Activity Naming**: Assign a name to a cluster once (e.g. *"Lakeside 40k"* or *"Morning Commute"*), and all subsequent workouts matching that path inherit the descriptive title automatically.

![Cluster Details & Trajectory Popup](../img/06_cluster_expanded.png)

---

<a id="cluster-map-and-history"></a>
## 6.2 Cluster Map Layers & Historical Performance Tracking

- **Trajectory Heatmap Overlays**: The cluster map visualizes recurring routes with density-weighted polylines, revealing your personal athletic training footprint.
- **Longitudinal Trend Curves**: Inspect progression charts tracking average speed, normalized power, and heart rate efficiency across identical circuits over seasons and years.
- **Sport-Specific Filtering**: Isolate running loops from cycling routes to gauge discipline-specific aerobic adaptations cleanly.

![Cluster Trajectory Heatmap on Map](../img/06_cluster_heatmap.png)

---

[Previous: Maps, GPX Routes & Segments](05-maps-routes-climbs-segments.md) | [Next: Physiology & Training Zones](07-physiology-training-zones.md)
