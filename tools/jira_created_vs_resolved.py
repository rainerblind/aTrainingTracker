#!/usr/bin/env python3
"""
Fetches non-Epic, non-Sub-Task Jira issues from project ATT and generates:
1. A compact CLI summary (Created, Closed, Open/Unresolved, Active WIP, Today's delta).
2. A self-contained interactive HTML report (zero external CDN or Python plotting deps)
   visualizing cumulative Created, Closed, Open Backlog (Created - Closed), and daily
   Created vs. Closed bars, filterable by timeframe, issue type, and count vs. Story Points.
"""

from __future__ import annotations

import argparse
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import date, datetime, timedelta
import html
import json
from pathlib import Path
import sys
import webbrowser
from typing import Any

TOOLS_DIR = Path(__file__).resolve().parent
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

import jira_util  # noqa: E402

DEFAULT_JQL = (
    "project = ATT AND issuetype not in subTaskIssueTypes() "
    "AND issuetype != Epic ORDER BY created ASC"
)
ISSUE_FIELDS = [
    "created",
    "resolutiondate",
    "status",
    "issuetype",
    "customfield_10016",
]
KNOWN_TYPES = ("Bug", "Feature", "Verbesserung")


@dataclass(frozen=True)
class NormalizedIssue:
    """Normalized Jira issue fields needed for time-series aggregation."""

    key: str
    issue_type: str
    status_name: str
    status_category: str
    created_date: str
    resolved_date: str | None
    story_points: float


def _parse_iso_local_date(timestamp: str | None) -> str | None:
    """Extracts YYYY-MM-DD in the issue timestamp's own local offset."""
    if not timestamp:
        return None
    if len(timestamp) >= 10 and timestamp[4] == "-" and timestamp[7] == "-":
        return timestamp[:10]
    return None


def normalize_issues(raw_issues: list[dict[str, Any]]) -> list[NormalizedIssue]:
    """Converts raw Jira API issue dictionaries into NormalizedIssue records."""
    normalized: list[NormalizedIssue] = []
    for raw in raw_issues:
        fields = raw.get("fields") or {}
        created_date = _parse_iso_local_date(fields.get("created"))
        if not created_date:
            continue
        issue_type = (fields.get("issuetype") or {}).get("name") or "Other"
        status_obj = fields.get("status") or {}
        status_name = status_obj.get("name") or "Unknown"
        status_category = (status_obj.get("statusCategory") or {}).get("name") or "Unknown"
        resolved_date = _parse_iso_local_date(fields.get("resolutiondate"))
        raw_sp = fields.get("customfield_10016")
        story_points = float(raw_sp) if isinstance(raw_sp, (int, float)) else 0.0
        normalized.append(
            NormalizedIssue(
                key=str(raw.get("key") or ""),
                issue_type=issue_type,
                status_name=status_name,
                status_category=status_category,
                created_date=created_date,
                resolved_date=resolved_date,
                story_points=story_points,
            )
        )
    return normalized


def build_daily_series(
    issues: list[NormalizedIssue],
    end_date: date | None = None,
    issue_type_filter: str | None = None,
    use_story_points: bool = False,
) -> list[dict[str, Any]]:
    """
    Computes a continuous daily time series from the earliest date up to end_date.

    Because cumulative counts always accumulate from project inception (t=0), zooming
    into a recent window in the UI preserves the true open-backlog baseline instead of
    Jira Cloud's zero-baseline artifact.
    """
    filtered = [
        issue
        for issue in issues
        if issue_type_filter is None or issue.issue_type == issue_type_filter
    ]
    if not filtered:
        return []

    created_by_day: dict[str, float] = defaultdict(float)
    resolved_by_day: dict[str, float] = defaultdict(float)
    min_date_str = filtered[0].created_date
    max_date_str = filtered[0].created_date

    for issue in filtered:
        weight = issue.story_points if use_story_points else 1.0
        created_by_day[issue.created_date] += weight
        if issue.created_date < min_date_str:
            min_date_str = issue.created_date
        if issue.created_date > max_date_str:
            max_date_str = issue.created_date
        if issue.resolved_date:
            resolved_by_day[issue.resolved_date] += weight
            if issue.resolved_date < min_date_str:
                min_date_str = issue.resolved_date
            if issue.resolved_date > max_date_str:
                max_date_str = issue.resolved_date

    start_d = date.fromisoformat(min_date_str)
    last_d = max(date.fromisoformat(max_date_str), end_date or date.fromisoformat(max_date_str))

    series: list[dict[str, Any]] = []
    cum_created = 0.0
    cum_resolved = 0.0
    cursor = start_d
    while cursor <= last_d:
        day_str = cursor.isoformat()
        dc = created_by_day.get(day_str, 0.0)
        dr = resolved_by_day.get(day_str, 0.0)
        cum_created += dc
        cum_resolved += dr
        series.append(
            {
                "date": day_str,
                "daily_created": round(dc, 2),
                "daily_resolved": round(dr, 2),
                "cum_created": round(cum_created, 2),
                "cum_resolved": round(cum_resolved, 2),
                "open_backlog": round(cum_created - cum_resolved, 2),
            }
        )
        cursor += timedelta(days=1)
    return series


def build_summary(issues: list[NormalizedIssue], today_str: str) -> dict[str, Any]:
    """Computes current totals, status breakdown, type breakdown, and today's deltas."""
    status_counts = Counter(i.status_name for i in issues)
    category_counts = Counter(i.status_category for i in issues)
    type_counts = Counter(i.issue_type for i in issues)

    total_created = len(issues)
    total_resolved = sum(1 for i in issues if i.resolved_date is not None)
    total_open = total_created - total_resolved
    active_wip = sum(
        1 for i in issues if i.resolved_date is None and i.status_category == "In Arbeit"
    )
    # Fallback for English Jira sites where statusCategory is "In Progress"
    if active_wip == 0:
        active_wip = sum(
            1
            for i in issues
            if i.resolved_date is None
            and i.status_category in ("In Arbeit", "In Progress")
        )
    todo_open = total_open - active_wip

    created_today = sum(1 for i in issues if i.created_date == today_str)
    resolved_today = sum(1 for i in issues if i.resolved_date == today_str)
    total_sp_created = round(sum(i.story_points for i in issues), 1)
    total_sp_resolved = round(
        sum(i.story_points for i in issues if i.resolved_date is not None), 1
    )

    return {
        "generated_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "today": today_str,
        "total_created": total_created,
        "total_resolved": total_resolved,
        "total_open": total_open,
        "active_wip": active_wip,
        "todo_open": todo_open,
        "created_today": created_today,
        "resolved_today": resolved_today,
        "total_sp_created": total_sp_created,
        "total_sp_resolved": total_sp_resolved,
        "total_sp_open": round(total_sp_created - total_sp_resolved, 1),
        "by_status": dict(status_counts.most_common()),
        "by_category": dict(category_counts.most_common()),
        "by_type": dict(type_counts.most_common()),
    }


def build_report_payload(
    issues: list[NormalizedIssue], today: date | None = None
) -> dict[str, Any]:
    """Builds the complete JSON payload consumed by the interactive HTML template."""
    ref_today = today or date.today()
    today_str = ref_today.isoformat()
    available_types = [t for t in KNOWN_TYPES if any(i.issue_type == t for i in issues)]
    for t in sorted({i.issue_type for i in issues}):
        if t not in available_types:
            available_types.append(t)

    series_by_mode: dict[str, dict[str, list[dict[str, Any]]]] = {
        "count": {"ALL": build_daily_series(issues, end_date=ref_today)},
        "sp": {"ALL": build_daily_series(issues, end_date=ref_today, use_story_points=True)},
    }
    for t in available_types:
        series_by_mode["count"][t] = build_daily_series(
            issues, end_date=ref_today, issue_type_filter=t
        )
        series_by_mode["sp"][t] = build_daily_series(
            issues, end_date=ref_today, issue_type_filter=t, use_story_points=True
        )

    return {
        "summary": build_summary(issues, today_str),
        "types": ["ALL"] + available_types,
        "series": series_by_mode,
    }


HTML_TEMPLATE = """<!DOCTYPE html>
<html lang="de">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Jira ATT — Erstellt vs. Geschlossen vs. Offen (ohne Epics &amp; Sub-Tasks)</title>
<style>
  :root {
    --bg: #0f172a;
    --card: #1e293b;
    --border: #334155;
    --text: #f1f5f9;
    --muted: #94a3b8;
    --created: #60a5fa;
    --resolved: #34d399;
    --open: #fbbf24;
    --accent: #38bdf8;
  }
  * { box-sizing: border-box; }
  body {
    margin: 0;
    padding: 24px;
    background: var(--bg);
    color: var(--text);
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
  }
  .container { max-width: 1280px; margin: 0 auto; }
  header {
    display: flex;
    flex-wrap: wrap;
    justify-content: space-between;
    align-items: baseline;
    gap: 12px;
    margin-bottom: 20px;
  }
  h1 { margin: 0; font-size: 1.4rem; }
  .subtitle { color: var(--muted); font-size: 0.9rem; }
  .kpis {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
    gap: 14px;
    margin-bottom: 20px;
  }
  .kpi {
    background: var(--card);
    border: 1px solid var(--border);
    border-radius: 12px;
    padding: 14px 16px;
  }
  .kpi-label { color: var(--muted); font-size: 0.82rem; margin-bottom: 6px; }
  .kpi-value { font-size: 1.8rem; font-weight: 700; line-height: 1.1; }
  .kpi-sub { color: var(--muted); font-size: 0.8rem; margin-top: 6px; }
  .c-created { color: var(--created); }
  .c-resolved { color: var(--resolved); }
  .c-open { color: var(--open); }
  .controls {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    align-items: center;
    background: var(--card);
    border: 1px solid var(--border);
    border-radius: 12px;
    padding: 12px 16px;
    margin-bottom: 16px;
  }
  .ctrl-group { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
  .ctrl-label { color: var(--muted); font-size: 0.82rem; margin-right: 4px; }
  button.pill {
    background: transparent;
    color: var(--muted);
    border: 1px solid var(--border);
    border-radius: 999px;
    padding: 5px 12px;
    font-size: 0.82rem;
    cursor: pointer;
  }
  button.pill.active {
    background: var(--accent);
    color: #082f49;
    border-color: var(--accent);
    font-weight: 600;
  }
  .chart-card {
    background: var(--card);
    border: 1px solid var(--border);
    border-radius: 12px;
    padding: 16px;
    position: relative;
  }
  .legend {
    display: flex;
    flex-wrap: wrap;
    gap: 18px;
    margin-bottom: 10px;
    font-size: 0.86rem;
  }
  .legend-item { display: flex; align-items: center; gap: 8px; }
  .swatch { width: 14px; height: 4px; border-radius: 2px; display: inline-block; }
  svg { width: 100%; height: 440px; display: block; overflow: visible; }
  .tooltip {
    position: fixed;
    pointer-events: none;
    background: rgba(15, 23, 42, 0.96);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 10px 12px;
    font-size: 0.82rem;
    line-height: 1.5;
    box-shadow: 0 10px 25px rgba(0, 0, 0, 0.4);
    display: none;
    z-index: 20;
  }
  .breakdown {
    margin-top: 16px;
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
    gap: 14px;
  }
  .panel {
    background: var(--card);
    border: 1px solid var(--border);
    border-radius: 12px;
    padding: 14px 16px;
  }
  .panel h3 { margin: 0 0 10px 0; font-size: 0.95rem; color: var(--muted); }
  .row {
    display: flex;
    justify-content: space-between;
    padding: 4px 0;
    border-bottom: 1px solid rgba(51, 65, 85, 0.5);
    font-size: 0.88rem;
  }
  .row:last-child { border-bottom: none; }
</style>
</head>
<body>
<div class="container">
  <header>
    <div>
      <h1>Jira Projekt ATT — Ticket-Verlauf (ohne Sub-Tasks &amp; Epics)</h1>
      <div class="subtitle" id="metaSubtitle"></div>
    </div>
  </header>

  <section class="kpis" id="kpiGrid"></section>

  <section class="controls">
    <div class="ctrl-group" id="rangeGroup">
      <span class="ctrl-label">Zeitraum:</span>
      <button class="pill active" data-range="all">Gesamt</button>
      <button class="pill" data-range="90">90 Tage</button>
      <button class="pill" data-range="30">30 Tage</button>
      <button class="pill" data-range="14">14 Tage</button>
    </div>
    <div class="ctrl-group" id="viewGroup">
      <span class="ctrl-label">Ansicht:</span>
      <button class="pill active" data-view="cumulative">Kumulativ</button>
      <button class="pill" data-view="daily">Täglich (Neu vs. Erledigt)</button>
    </div>
    <div class="ctrl-group" id="metricGroup">
      <span class="ctrl-label">Einheit:</span>
      <button class="pill active" data-metric="count">Tickets</button>
      <button class="pill" data-metric="sp">Story Points</button>
    </div>
    <div class="ctrl-group" id="typeGroup">
      <span class="ctrl-label">Typ:</span>
    </div>
  </section>

  <section class="chart-card">
    <div class="legend" id="legend"></div>
    <svg id="chartSvg" viewBox="0 0 1100 440" preserveAspectRatio="none"></svg>
  </section>

  <section class="breakdown" id="breakdown"></section>
</div>
<div class="tooltip" id="tooltip"></div>

<script>
const REPORT = __REPORT_JSON__;

const state = { range: 'all', view: 'cumulative', metric: 'count', type: 'ALL' };

function renderKPIs() {
  const s = REPORT.summary;
  const pct = s.total_created > 0 ? ((s.total_resolved / s.total_created) * 100).toFixed(1) : '0.0';
  const cards = [
    {
      label: 'Insgesamt erstellt',
      value: s.total_created.toLocaleString('de-DE'),
      cls: 'c-created',
      sub: `Heute +${s.created_today} | ${s.total_sp_created.toLocaleString('de-DE')} SP`
    },
    {
      label: 'Insgesamt geschlossen (Erledigt)',
      value: s.total_resolved.toLocaleString('de-DE'),
      cls: 'c-resolved',
      sub: `${pct}% abgeschlossen | Heute +${s.resolved_today} | ${s.total_sp_resolved.toLocaleString('de-DE')} SP`
    },
    {
      label: 'Offen / Unresolved (Erstellt − Geschlossen)',
      value: s.total_open.toLocaleString('de-DE'),
      cls: 'c-open',
      sub: `Davon ${s.active_wip} aktiv in Arbeit/Review & ${s.todo_open} Zu erledigen`
    },
    {
      label: 'Aktuell aktiv in Arbeit / Review',
      value: s.active_wip.toLocaleString('de-DE'),
      cls: '',
      sub: `Offene Story Points gesamt: ${s.total_sp_open.toLocaleString('de-DE')} SP`
    }
  ];
  document.getElementById('metaSubtitle').textContent = `Stand: ${s.generated_at} | Filter: project = ATT, ohne Epics & Sub-Tasks`;
  document.getElementById('kpiGrid').innerHTML = cards.map(c => `
    <div class="kpi">
      <div class="kpi-label">${c.label}</div>
      <div class="kpi-value ${c.cls}">${c.value}</div>
      <div class="kpi-sub">${c.sub}</div>
    </div>
  `).join('');
}

function renderTypeButtons() {
  const group = document.getElementById('typeGroup');
  REPORT.types.forEach(t => {
    const btn = document.createElement('button');
    btn.className = 'pill' + (t === 'ALL' ? ' active' : '');
    btn.dataset.type = t;
    btn.textContent = t === 'ALL' ? 'Alle' : t;
    group.appendChild(btn);
  });
}

function renderBreakdowns() {
  const s = REPORT.summary;
  const rows = obj => Object.entries(obj).map(([k, v]) => `<div class="row"><span>${k}</span><strong>${v.toLocaleString('de-DE')}</strong></div>`).join('');
  document.getElementById('breakdown').innerHTML = `
    <div class="panel"><h3>Aktueller Status</h3>${rows(s.by_status)}</div>
    <div class="panel"><h3>Nach Typ</h3>${rows(s.by_type)}</div>
  `;
}

function niceStep(maxVal) {
  if (maxVal <= 10) return 2;
  const raw = maxVal / 5;
  const pow = Math.pow(10, Math.floor(Math.log10(raw)));
  for (const m of [1, 2, 2.5, 5, 10]) {
    if (m * pow >= raw) return m * pow;
  }
  return raw;
}

function renderChart() {
  const fullSeries = (REPORT.series[state.metric] && REPORT.series[state.metric][state.type]) || [];
  const data = state.range === 'all' ? fullSeries : fullSeries.slice(-Number(state.range));
  const svg = document.getElementById('chartSvg');
  const legend = document.getElementById('legend');
  if (!data.length) {
    svg.innerHTML = '';
    return;
  }

  const W = 1100, H = 440;
  const pad = { top: 20, right: 24, bottom: 38, left: 62 };
  const plotW = W - pad.left - pad.right;
  const plotH = H - pad.top - pad.bottom;

  let maxY = 1;
  if (state.view === 'cumulative') {
    maxY = Math.max(1, ...data.map(d => Math.max(d.cum_created, d.cum_resolved, d.open_backlog)));
  } else {
    maxY = Math.max(1, ...data.map(d => Math.max(d.daily_created, d.daily_resolved)));
  }
  const step = niceStep(maxY);
  const yTop = Math.ceil(maxY / step) * step;

  const xPos = idx => pad.left + (data.length === 1 ? plotW / 2 : (idx / (data.length - 1)) * plotW);
  const yPos = val => pad.top + plotH - (val / yTop) * plotH;

  let parts = [];
  for (let yv = 0; yv <= yTop + 1e-6; yv += step) {
    const yy = yPos(yv);
    parts.push(`<line x1="${pad.left}" y1="${yy}" x2="${W - pad.right}" y2="${yy}" stroke="#334155" stroke-width="1" stroke-dasharray="3,4"/>`);
    parts.push(`<text x="${pad.left - 8}" y="${yy + 4}" fill="#94a3b8" font-size="11" text-anchor="end">${Math.round(yv)}</text>`);
  }

  const xTickCount = Math.min(8, data.length);
  for (let t = 0; t < xTickCount; t++) {
    const idx = xTickCount === 1 ? 0 : Math.round((t / (xTickCount - 1)) * (data.length - 1));
    const xx = xPos(idx);
    parts.push(`<text x="${xx}" y="${H - 10}" fill="#94a3b8" font-size="11" text-anchor="middle">${data[idx].date}</text>`);
  }

  if (state.view === 'cumulative') {
    legend.innerHTML = `
      <span class="legend-item"><span class="swatch" style="background:var(--created)"></span>Insgesamt erstellt</span>
      <span class="legend-item"><span class="swatch" style="background:var(--resolved)"></span>Insgesamt geschlossen</span>
      <span class="legend-item"><span class="swatch" style="background:var(--open)"></span>Offen / Backlog (Erstellt − Geschlossen)</span>
    `;
    const linePath = key => data.map((d, i) => `${i === 0 ? 'M' : 'L'}${xPos(i).toFixed(1)},${yPos(d[key]).toFixed(1)}`).join(' ');
    const openArea = `${linePath('open_backlog')} L${xPos(data.length - 1).toFixed(1)},${yPos(0).toFixed(1)} L${xPos(0).toFixed(1)},${yPos(0).toFixed(1)} Z`;
    parts.push(`<path d="${openArea}" fill="rgba(251, 191, 36, 0.12)" stroke="none"/>`);
    parts.push(`<path d="${linePath('cum_created')}" fill="none" stroke="#60a5fa" stroke-width="2.6"/>`);
    parts.push(`<path d="${linePath('cum_resolved')}" fill="none" stroke="#34d399" stroke-width="2.6"/>`);
    parts.push(`<path d="${linePath('open_backlog')}" fill="none" stroke="#fbbf24" stroke-width="2.4"/>`);
  } else {
    legend.innerHTML = `
      <span class="legend-item"><span class="swatch" style="background:var(--created)"></span>Pro Tag erstellt</span>
      <span class="legend-item"><span class="swatch" style="background:var(--resolved)"></span>Pro Tag geschlossen</span>
    `;
    const slotW = plotW / data.length;
    const barW = Math.max(1.5, Math.min(14, slotW * 0.4));
    data.forEach((d, i) => {
      const cx = xPos(i);
      const yc = yPos(d.daily_created);
      const yr = yPos(d.daily_resolved);
      const y0 = yPos(0);
      parts.push(`<rect x="${(cx - barW).toFixed(1)}" y="${yc.toFixed(1)}" width="${barW.toFixed(1)}" height="${(y0 - yc).toFixed(1)}" fill="#60a5fa" opacity="0.85"/>`);
      parts.push(`<rect x="${cx.toFixed(1)}" y="${yr.toFixed(1)}" width="${barW.toFixed(1)}" height="${(y0 - yr).toFixed(1)}" fill="#34d399" opacity="0.85"/>`);
    });
  }

  parts.push(`<line id="hoverGuide" x1="0" y1="${pad.top}" x2="0" y2="${pad.top + plotH}" stroke="#94a3b8" stroke-width="1" stroke-dasharray="3,3" visibility="hidden"/>`);
  parts.push(`<rect id="hoverZone" x="${pad.left}" y="${pad.top}" width="${plotW}" height="${plotH}" fill="transparent"/>`);
  svg.innerHTML = parts.join('');

  const zone = document.getElementById('hoverZone');
  const guide = document.getElementById('hoverGuide');
  const tooltip = document.getElementById('tooltip');

  zone.onmousemove = evt => {
    const rect = svg.getBoundingClientRect();
    const scaleX = W / rect.width;
    const svgX = (evt.clientX - rect.left) * scaleX;
    const ratio = Math.max(0, Math.min(1, (svgX - pad.left) / plotW));
    const idx = Math.round(ratio * (data.length - 1));
    const d = data[idx];
    const gx = xPos(idx);
    guide.setAttribute('x1', gx);
    guide.setAttribute('x2', gx);
    guide.setAttribute('visibility', 'visible');

    const unit = state.metric === 'sp' ? ' SP' : '';
    tooltip.innerHTML = `
      <strong>${d.date}</strong><br>
      <span class="c-created">Erstellt gesamt: ${d.cum_created}${unit}</span> (+${d.daily_created}${unit})<br>
      <span class="c-resolved">Geschlossen gesamt: ${d.cum_resolved}${unit}</span> (+${d.daily_resolved}${unit})<br>
      <span class="c-open">Offen / Backlog: ${d.open_backlog}${unit}</span>
    `;
    tooltip.style.display = 'block';
    const left = Math.min(window.innerWidth - 260, evt.clientX + 14);
    const top = Math.max(12, evt.clientY - 70);
    tooltip.style.left = left + 'px';
    tooltip.style.top = top + 'px';
  };
  zone.onmouseleave = () => {
    guide.setAttribute('visibility', 'hidden');
    tooltip.style.display = 'none';
  };
}

function bindControls() {
  document.querySelectorAll('.controls').forEach(container => {
    container.addEventListener('click', evt => {
      const btn = evt.target.closest('button.pill');
      if (!btn) return;
      const group = btn.parentElement;
      group.querySelectorAll('button.pill').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      if (btn.dataset.range) state.range = btn.dataset.range;
      if (btn.dataset.view) state.view = btn.dataset.view;
      if (btn.dataset.metric) state.metric = btn.dataset.metric;
      if (btn.dataset.type) state.type = btn.dataset.type;
      renderChart();
    });
  });
}

renderKPIs();
renderTypeButtons();
renderBreakdowns();
bindControls();
renderChart();
</script>
</body>
</html>
"""


def render_html(payload: dict[str, Any]) -> str:
    """Embeds the JSON payload safely into the standalone HTML template."""
    # Escape '<' as unicode escape to prevent premature </script> termination.
    safe_json = json.dumps(payload, ensure_ascii=False).replace("<", "\\u003c")
    return HTML_TEMPLATE.replace("__REPORT_JSON__", safe_json)


def format_terminal_summary(summary: dict[str, Any], days_tail: int = 7, series: list[dict[str, Any]] | None = None) -> str:
    """Formats a readable CLI summary table for terminal inspection."""
    lines = [
        f"Jira ATT Übersicht ({summary['today']}, ohne Epics & Sub-Tasks)",
        f"  Insgesamt erstellt    : {summary['total_created']:5d}  ({summary['total_sp_created']} SP)  [heute +{summary['created_today']}]",
        f"  Insgesamt geschlossen : {summary['total_resolved']:5d}  ({summary['total_sp_resolved']} SP)  [heute +{summary['resolved_today']}]",
        f"  Offen / Backlog       : {summary['total_open']:5d}  ({summary['total_sp_open']} SP)  [davon {summary['active_wip']} aktiv in Arbeit/Review, {summary['todo_open']} Zu erledigen]",
        "  Nach Status:",
    ]
    for status_name, count in summary["by_status"].items():
        lines.append(f"    - {status_name:24s}: {count}")
    if series:
        lines.append(f"  Letzte {min(days_tail, len(series))} Tage (Datum | +Erstellt | +Geschlossen | Gesamt | Offen):")
        for row in series[-days_tail:]:
            lines.append(
                f"    {row['date']} | +{int(row['daily_created']):3d} | +{int(row['daily_resolved']):3d} | {int(row['cum_created']):5d} / {int(row['cum_resolved']):5d} | {int(row['open_backlog']):4d}"
            )
    return "\n".join(lines)


def fetch_issues(jql: str = DEFAULT_JQL) -> list[NormalizedIssue]:
    """Queries Jira via paged_jql_search and returns normalized issues."""
    raw = jira_util.paged_jql_search(jql, ISSUE_FIELDS)
    return normalize_issues(raw)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Fetch Jira ATT ticket counts (excluding Epics & Sub-Tasks) and generate an interactive HTML chart."
    )
    parser.add_argument(
        "--out",
        default="/tmp/jira_created_vs_resolved.html",
        help="Output HTML file path (default: /tmp/jira_created_vs_resolved.html)",
    )
    parser.add_argument(
        "--json-out",
        default="",
        help="Optional path to also write raw aggregated JSON series.",
    )
    parser.add_argument(
        "--open",
        action="store_true",
        help="Open generated HTML report in the default browser.",
    )
    parser.add_argument(
        "--days",
        type=int,
        default=10,
        help="Number of recent days to print in the terminal summary (default: 10).",
    )
    args = parser.parse_args(argv)

    issues = fetch_issues()
    payload = build_report_payload(issues)

    out_path = Path(args.out).expanduser().resolve()
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(render_html(payload), encoding="utf-8")

    if args.json_out:
        json_path = Path(args.json_out).expanduser().resolve()
        json_path.parent.mkdir(parents=True, exist_ok=True)
        json_path.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
        )

    print(
        format_terminal_summary(
            payload["summary"],
            days_tail=args.days,
            series=payload["series"]["count"]["ALL"],
        )
    )
    print(f"\nHTML-Diagramm geschrieben nach: {html.unescape(str(out_path))}")

    if args.open:
        webbrowser.open(out_path.as_uri())

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
