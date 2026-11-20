#!/usr/bin/env python3
"""Generate the 12-slide German SVG/PNG Storytelling & Architecture Suite
and Interactive HTML Explorers for:
'Agile Agentic ASPICE (AAA) — Schichtbetrieb nach Höchstmaß'
(DOC-GOV-RULES v1.0.5 / DOC-MGT-SCHICHT v1.0.3, 2026-11-19).

Part A (00–06): Temporal Evolution & Phase-by-Phase Deep Dives with Pros & Cons
Part B (07–11): Current Target Architecture Deep Dives
"""

import html
import json
import re
import subprocess
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent

OUT_DIR.mkdir(parents=True, exist_ok=True)

FONT_SANS = "'Inter', 'Liberation Sans', 'DejaVu Sans', sans-serif"
FONT_MONO = "'DejaVu Sans Mono', 'Liberation Mono', monospace"

C = {
    "bg": "#F8FAFC",
    "white": "#FFFFFF",
    "ink": "#0F172A",
    "body": "#334155",
    "muted": "#64748B",
    "border": "#CBD5E1",
    "border_light": "#E2E8F0",
    "header_bg": "#0F172A",
    # Semantic accents: (dark, main, soft_bg, soft_border)
    "indigo": ("#1E3A8A", "#4338CA", "#EEF2FF", "#A5B4FC"),
    "teal":   ("#115E59", "#0D9488", "#F0FDFA", "#5EEAD4"),
    "blue":   ("#1E40AF", "#2563EB", "#EFF6FF", "#93C5FD"),
    "amber":  ("#92400E", "#D97706", "#FFFBEB", "#FCD34D"),
    "rose":   ("#9F1239", "#E11D48", "#FFF1F2", "#FDA4AF"),
    "emerald":("#065F46", "#059669", "#ECFDF5", "#6EE7B7"),
    "violet": ("#5B21B6", "#7C3AED", "#F5F3FF", "#C4B5FD"),
    "slate":  ("#1E293B", "#475569", "#F1F5F9", "#CBD5E1"),
}


def esc(s: str) -> str:
    return html.escape(s, quote=True)


def render_rich_text(text: str, dark_color: str, base_color: str) -> str:
    """Convert inline **bold**, `code`, and *italic* segments into SVG <tspan> elements."""
    parts = re.split(r"(\*\*.*?\*\*|`[^`]+`|\*[^*]+\*)", text)
    out = []
    for p in parts:
        if not p:
            continue
        if p.startswith("**") and p.endswith("**") and len(p) > 4:
            inner = p[2:-2].replace("`", "")
            out.append(f'<tspan font-weight="700" fill="{dark_color}">{esc(inner)}</tspan>')
        elif p.startswith("`") and p.endswith("`") and len(p) > 2:
            out.append(f'<tspan font-family="{FONT_MONO}" font-weight="600" fill="{dark_color}">{esc(p[1:-1])}</tspan>')
        elif p.startswith("*") and p.endswith("*") and len(p) > 2:
            out.append(f'<tspan font-style="italic">{esc(p[1:-1])}</tspan>')
        else:
            out.append(esc(p))
    return "".join(out)


class SvgBuilder:
    def __init__(self, width: int = 1600, height: int = 900, diagram_id: str = "d1"):
        self.w = width
        self.h = height
        self.id = diagram_id
        self.parts: list[str] = []

    def add(self, s: str):
        self.parts.append(s)

    def header(self, badge: str, title: str, subtitle: str, badge_color: str = "teal"):
        dark, main, soft, bdr = C[badge_color]
        bw = max(172, len(badge) * 7.8 + 26)
        self.add(f'''
  <rect x="0" y="0" width="{self.w}" height="74" fill="{C['header_bg']}"/>
  <rect x="0" y="71" width="{self.w}" height="3" fill="{main}"/>
  <rect x="36" y="17" width="{bw:.0f}" height="24" rx="12" fill="{main}"/>
  <text x="{36 + bw/2:.1f}" y="33" text-anchor="middle" font-family="{FONT_SANS}" font-size="11.5" font-weight="700" fill="#FFFFFF" letter-spacing="0.6">{esc(badge)}</text>
  <text x="{36 + bw + 16:.0f}" y="36" font-family="{FONT_SANS}" font-size="21.5" font-weight="800" fill="#FFFFFF">{esc(title)}</text>
  <text x="36" y="60" font-family="{FONT_SANS}" font-size="12.5" font-weight="500" fill="#94A3B8">{esc(subtitle)}</text>
  <text x="{self.w - 36}" y="34" text-anchor="end" font-family="{FONT_SANS}" font-size="12" font-weight="600" fill="#CBD5E1">aTrainingTracker · Agile Agentic ASPICE (AAA)</text>
  <text x="{self.w - 36}" y="56" text-anchor="end" font-family="{FONT_MONO}" font-size="11" fill="#94A3B8">DOC-GOV-RULES v1.0.5 · DOC-MGT-SCHICHT v1.0.3 · 19.11.2026</text>
''')

    def phase_breadcrumbs(self, active_idx: int, active_color: str):
        """6-step horizontal timeline breadcrumb strip (y=86..130)."""
        steps = [
            ("1. Vibe-Coding", "Bis Jun 2026"),
            ("2. Augenmaß", "Jun 2026"),
            ("3. Sorgfältig", "Jun – Aug 2026"),
            ("4a. Höchstmaß", "Sep 2026"),
            ("4b. Skalierung", "Okt – 15. Nov"),
            ("5. Schichtbetrieb", "Ab 17. Nov · AKTIV"),
        ]
        x = 36
        w = 238
        gap = 20
        for i, (lbl, dt) in enumerate(steps):
            if i < active_idx:
                bg, stroke, tf, sf = C["emerald"][2], C["emerald"][3], C["emerald"][0], C["muted"]
                prefix = "✓ "
            elif i == active_idx:
                bg, stroke, tf, sf = C[active_color][1], C[active_color][0], "#FFFFFF", "#E2E8F0"
                prefix = "● "
            else:
                bg, stroke, tf, sf = "#FFFFFF", C["border"], C["muted"], C["muted"]
                prefix = ""
            sw = "2" if i == active_idx else "1.2"
            self.add(f'  <g class="node" data-node="evolution_timeline">')
            self.add(f'    <rect x="{x}" y="86" width="{w}" height="44" rx="9" fill="{bg}" stroke="{stroke}" stroke-width="{sw}"/>')
            self.add(f'    <text x="{x + w/2:.1f}" y="105" text-anchor="middle" font-family="{FONT_SANS}" font-size="12.2" font-weight="700" fill="{tf}">{esc(prefix + lbl)}</text>')
            self.add(f'    <text x="{x + w/2:.1f}" y="121" text-anchor="middle" font-family="{FONT_MONO}" font-size="10.2" font-weight="600" fill="{sf}">{esc(dt)}</text>')
            self.add('  </g>')
            if i < len(steps) - 1:
                acol = "emerald" if i < active_idx else (active_color if i == active_idx else "slate")
                self.arrow(x + w + 2, 108, x + w + gap - 2, 108, color=acol, width=1.8)
            x += w + gap

    def container(self, x, y, w, h, title, color="slate", subtitle="", node_id="", dashed=False):
        dark, main, soft, bdr = C[color]
        nid = f' class="node" data-node="{esc(node_id)}"' if node_id else ""
        dash = ' stroke-dasharray="6,4"' if dashed else ""
        self.add(f'''  <g{nid}>
    <rect x="{x+1.5}" y="{y+2.5}" width="{w}" height="{h}" rx="14" fill="#0F172A" fill-opacity="0.045"/>
    <rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="{soft}" stroke="{bdr}" stroke-width="1.6"{dash}/>
    <rect x="{x}" y="{y}" width="{w}" height="34" rx="14" fill="{main}"/>
    <rect x="{x}" y="{y+20}" width="{w}" height="14" fill="{main}"/>
    <text x="{x+16}" y="{y+22}" font-family="{FONT_SANS}" font-size="13.2" font-weight="700" fill="#FFFFFF">{esc(title)}</text>''')
        if subtitle:
            self.add(f'    <text x="{x+w-14}" y="{y+22}" text-anchor="end" font-family="{FONT_SANS}" font-size="11.2" font-weight="500" fill="#E2E8F0">{esc(subtitle)}</text>')
        self.add("  </g>")

    def card(
        self, x, y, w, h, title, lines, color="slate", accent_bar=True, badge="",
        node_id="", bg="#FFFFFF", title_size=13, line_size=11.8, line_gap=16.5,
        mono_lines=None, strong_border=False, badge_above_title=False, meta_left=""
    ):
        dark, main, soft, bdr = C[color]
        mono_lines = mono_lines or set()
        nid = f' class="node" data-node="{esc(node_id)}"' if node_id else ""
        sw = "2.1" if strong_border else "1.3"
        stroke = main if strong_border else bdr
        self.add(f'  <g{nid}>')
        self.add(f'    <rect x="{x+1.5}" y="{y+2.5}" width="{w}" height="{h}" rx="10" fill="#0F172A" fill-opacity="0.06"/>')
        self.add(f'    <rect x="{x}" y="{y}" width="{w}" height="{h}" rx="10" fill="{bg}" stroke="{stroke}" stroke-width="{sw}"/>')
        if accent_bar:
            self.add(f'    <rect x="{x}" y="{y}" width="5" height="{h}" rx="2.5" fill="{main}"/>')
        tx = x + (15 if accent_bar else 12)
        if badge_above_title:
            if meta_left:
                self.add(f'    <text x="{tx}" y="{y+22}" font-family="{FONT_MONO}" font-size="10.8" font-weight="700" fill="{main}">{esc(meta_left)}</text>')
            if badge:
                bw = max(52, len(badge) * 6.5 + 16)
                bx = x + w - bw - 10
                self.add(f'    <rect x="{bx:.1f}" y="{y+9}" width="{bw:.1f}" height="19" rx="9.5" fill="{soft}" stroke="{bdr}" stroke-width="1"/>')
                self.add(f'    <text x="{bx + bw/2:.1f}" y="{y+22}" text-anchor="middle" font-family="{FONT_SANS}" font-size="10.2" font-weight="700" fill="{dark}">{esc(badge)}</text>')
            ty = y + 44
            self.add(f'    <text x="{tx}" y="{ty}" font-family="{FONT_SANS}" font-size="{title_size}" font-weight="800" fill="{dark}">{esc(title)}</text>')
            cy = ty + 20
        else:
            ty = y + 23
            self.add(f'    <text x="{tx}" y="{ty}" font-family="{FONT_SANS}" font-size="{title_size}" font-weight="700" fill="{dark}">{esc(title)}</text>')
            if badge:
                bw = max(50, len(badge) * 6.6 + 16)
                bx = x + w - bw - 9
                self.add(f'    <rect x="{bx:.1f}" y="{y+9}" width="{bw:.1f}" height="20" rx="10" fill="{soft}" stroke="{bdr}" stroke-width="1"/>')
                self.add(f'    <text x="{bx + bw/2:.1f}" y="{y+22.5}" text-anchor="middle" font-family="{FONT_SANS}" font-size="10.3" font-weight="700" fill="{dark}">{esc(badge)}</text>')
            cy = ty + 19

        for i, line in enumerate(lines):
            if line == "---":
                self.add(f'    <line x1="{tx}" y1="{cy-6}" x2="{x+w-12}" y2="{cy-6}" stroke="{C["border_light"]}" stroke-width="1"/>')
                cy += 6
                continue
            is_mono = i in mono_lines
            ff = FONT_MONO if is_mono else FONT_SANS
            fs = line_size - 0.8 if is_mono else line_size
            fill = main if is_mono else C["body"]
            fw = "600" if is_mono else "400"
            if is_mono:
                self.add(f'    <text x="{tx}" y="{cy:.1f}" font-family="{ff}" font-size="{fs:.1f}" font-weight="{fw}" fill="{fill}">{esc(line)}</text>')
            else:
                rich = render_rich_text(line, dark, fill)
                self.add(f'    <text x="{tx}" y="{cy:.1f}" font-family="{ff}" font-size="{fs:.1f}" font-weight="{fw}" fill="{fill}">{rich}</text>')
            cy += line_gap
        self.add('  </g>')

    def pill(self, x, y, w, h, text, color="slate", filled=False, font_size=11.5, mono=False, node_id=""):
        dark, main, soft, bdr = C[color]
        nid = f' class="node" data-node="{esc(node_id)}"' if node_id else ""
        fill = main if filled else soft
        tfill = "#FFFFFF" if filled else dark
        stroke = main if filled else bdr
        ff = FONT_MONO if mono else FONT_SANS
        self.add(f'  <g{nid}>')
        self.add(f'    <rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{h/2:.1f}" fill="{fill}" stroke="{stroke}" stroke-width="1.3"/>')
        self.add(f'    <text x="{x+w/2:.1f}" y="{y+h/2+font_size*0.36:.1f}" text-anchor="middle" font-family="{ff}" font-size="{font_size}" font-weight="700" fill="{tfill}">{esc(text)}</text>')
        self.add('  </g>')

    def arrow(self, x1, y1, x2, y2, color="slate", label="", lx=None, ly=None, dashed=False, width=2.0, label_bg=True):
        _, main, _, _ = C[color]
        dash = ' stroke-dasharray="5,4"' if dashed else ""
        self.add(f'  <line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{main}" stroke-width="{width}"{dash} marker-end="url(#arr-{color})"/>')
        if label:
            mx = lx if lx is not None else (x1 + x2) / 2
            my = ly if ly is not None else (y1 + y2) / 2 - 7
            if label_bg:
                lw = len(label) * 6.3 + 14
                self.add(f'  <rect x="{mx - lw/2:.1f}" y="{my - 11.5:.1f}" width="{lw:.1f}" height="16.5" rx="4" fill="#FFFFFF" fill-opacity="0.95" stroke="{C[color][3]}" stroke-width="0.8"/>')
            self.add(f'  <text x="{mx:.1f}" y="{my:.1f}" text-anchor="middle" font-family="{FONT_SANS}" font-size="10.6" font-weight="700" fill="{C[color][0]}">{esc(label)}</text>')

    def path_arrow(self, d, color="slate", label="", lx=0, ly=0, dashed=False, width=2.0, label_bg=True):
        _, main, _, _ = C[color]
        dash = ' stroke-dasharray="6,4"' if dashed else ""
        self.add(f'  <path d="{d}" fill="none" stroke="{main}" stroke-width="{width}" stroke-linejoin="round"{dash} marker-end="url(#arr-{color})"/>')
        if label:
            if label_bg:
                lw = len(label) * 6.2 + 16
                self.add(f'  <rect x="{lx - lw/2:.1f}" y="{ly - 12:.1f}" width="{lw:.1f}" height="17" rx="5" fill="#FFFFFF" fill-opacity="0.96" stroke="{C[color][3]}" stroke-width="0.9"/>')
            self.add(f'  <text x="{lx:.1f}" y="{ly:.1f}" text-anchor="middle" font-family="{FONT_SANS}" font-size="10.6" font-weight="700" fill="{C[color][0]}">{esc(label)}</text>')

    def pros_cons_and_story_footer(
        self,
        pros: list[tuple[str, str]],
        cons: list[tuple[str, str]],
        sweet_spot_title: str,
        sweet_spot_lines: list[str],
        next_title: str,
        next_badge: str,
        next_lines: list[str],
        next_color: str,
        node_id: str,
    ):
        """Standardized bottom 2 bands for all 6 Phase Storytelling slides (y=428..874)."""
        # Middle Band: PROS (left) vs CONS (right), y=428..734 (h=306)
        self.container(36, 428, 752, 306, "PROS — Was in dieser Phase hervorragend funktioniert", color="emerald", subtitle="Stärken & Gewinne", node_id=node_id)
        py = 474
        for head, body in pros:
            self.card(52, py, 720, 56, f"+ {head}", [body], color="emerald", accent_bar=True, node_id=node_id, title_size=12.4, line_size=11.4, line_gap=15.5)
            py += 63

        self.container(812, 428, 752, 306, "CONS & GRENZEN — Wo es knirscht & was fehlt", color="rose", subtitle="Risiken & Engpässe", node_id=node_id)
        cy = 474
        for head, body in cons:
            self.card(828, cy, 720, 56, f"− {head}", [body], color="rose", accent_bar=True, node_id=node_id, title_size=12.4, line_size=11.4, line_gap=15.5)
            cy += 63

        # Bottom Story Band (y=750..874, h=124)
        self.card(
            36, 750, 752, 124, sweet_spot_title, sweet_spot_lines,
            color="indigo", badge="Sweet Spot", node_id=node_id,
            title_size=13, line_size=11.8, line_gap=17.5,
        )
        self.card(
            812, 750, 752, 124, next_title, next_lines,
            color=next_color, badge=next_badge, node_id=node_id,
            bg=C[next_color][2], strong_border=True,
            title_size=13, line_size=11.8, line_gap=17.5,
        )

    def render(self) -> str:
        markers = []
        for cname, val in C.items():
            if isinstance(val, tuple):
                _, main, _, _ = val
                markers.append(f'''    <marker id="arr-{cname}" viewBox="0 0 10 10" refX="8.5" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
      <path d="M 1 1.5 L 9 5 L 1 8.5 z" fill="{main}"/>
    </marker>''')
        defs = f'''  <defs>
{chr(10).join(markers)}
  </defs>'''
        return f'''<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {self.w} {self.h}" width="{self.w}" height="{self.h}" id="svg-{self.id}">
{defs}
  <rect x="0" y="0" width="{self.w}" height="{self.h}" fill="{C['bg']}"/>
{chr(10).join(self.parts)}
</svg>
'''


# =============================================================================
# SLIDE 00: EVOLUTION & STORYTELLING (GESAMTÜBERBLICK)
# =============================================================================
def build_d00_evolution() -> str:
    b = SvgBuilder(1600, 900, "d00")
    b.header(
        "00 · EVOLUTION & STORY",
        "Unsere Entwicklung über die Zeit: Vom Vibe-Coding zum Schichtbetrieb",
        "Authentische 5-Monats-Reise aus 80 Retrospektiven & ~4.700 Commits — wie jeder Engpass einen neuen Prozess-Durchbruch ausgelöst hat (Juni – Nov 2026)",
        "indigo",
    )

    b.add(f'  <text x="36" y="102" font-family="{FONT_SANS}" font-size="12" font-weight="700" fill="{C["muted"]}" letter-spacing="0.5">EVOLUTIONS-BOGEN · 6 MEILENSTEINE VON STUFE 1 BIS STUFE 5 (FOLIEN 01–06 ERKLÄREN JEDE PHASE IM DETAIL MIT PROS &amp; CONS)</text>')
    b.add(f'  <path d="M 54 468 L 1544 128" fill="none" stroke="{C["border"]}" stroke-width="2" stroke-dasharray="6,6"/>')

    milestones = [
        ("Folie 01 · Bis Jun", "Stufe 1", "Vibe-Coding", "slate", 218, False, [
            "• 1 Agent baut ad-hoc drauflos",
            "• Direkter Chat → Code → Test",
            "• Keine formalen Specs/Gates",
            "---",
            "**Schmerz:** Halluzinationen,",
            "Regressionen & 0 Gedächtnis.",
        ]),
        ("Folie 02 · Jun 2026", "Stufe 2", "Augenmaß", "blue", 196, False, [
            "• Mensch + Zweitmeinung",
            "• Zweiter Agent prüft Entwurf",
            "• Ohne schweres Prozess-Gerüst",
            "---",
            "**Schmerz:** Bauchgefühl skaliert",
            "nicht über viele Features.",
        ]),
        ("Folie 03 · Jun–Aug", "Stufe 3", "Sorgfältig", "indigo", 174, False, [
            "• 19.–21.06.: REQs & Jira-Start",
            "• Ticket-für-Ticket sauber getestet",
            "• 80 → 204 REQs · 9 Sprachen",
            "---",
            "**Schmerz:** Menschliche Einzel-",
            "steuerung ist der Flaschenhals.",
        ]),
        ("Folie 04 · Sep 2026", "Stufe 4a", "Höchstmaß (Geburt)", "amber", 152, False, [
            "• 03.09.: Dual-Agent Sub-Tasks",
            "• 13.09.: Gate-3 Code Lock",
            "• 25.09.: Chesterton's Fence",
            "• 28.09.: Erster sprint/* Branch",
            "---",
            "**Durchbruch:** Bewusste Gates!",
        ]),
        ("Folie 05 · Okt–15.11.", "Stufe 4b", "Triad & Cold Review", "rose", 130, False, [
            "• 84+ Regeln → Document Triad",
            "• 08.11.: Worktrees + flock",
            "• 11.11.: Emulator + Stage 6",
            "• 12.–15.11.: Tokens & Queue 447",
            "---",
            "**Durchbruch:** Barriere p₆!",
        ]),
        ("Folie 06 · Nov 26", "Stufe 5 AKTIV", "Schichtbetrieb 24/7", "teal", 108, True, [
            "• 3 Schichten (05/15/22) · 3 Lanes",
            "• Live-Emulator S6 + Post-PASS",
            "• Flash/Neon + FROM-CACHE",
            "• Auto-Rollup + Dual-Vendor",
            "---",
            "**Status:** 1.265 REQs · 24/7 Takt!",
        ]),
    ]

    mx = 36
    mw = 242
    mgap = 15.2
    mh = 238
    for idx, (date_lbl, stufe_lbl, title, col, top_y, active, lines) in enumerate(milestones):
        bg = C["teal"][2] if active else "#FFFFFF"
        b.card(
            mx, top_y, mw, mh, title, lines,
            color=col, accent_bar=True, badge=stufe_lbl,
            node_id="evolution_timeline", bg=bg,
            title_size=13.2, line_size=11.1, line_gap=16.5, strong_border=active,
            badge_above_title=True, meta_left=date_lbl,
        )
        if idx < 5:
            next_y = milestones[idx + 1][4]
            b.arrow(mx + mw + 1, top_y + mh * 0.48, mx + mw + mgap - 1, next_y + mh * 0.48, color=col, width=2.0)
        mx += mw + mgap

    # Bottom Left: Empirischer REQ-Wachstums-Chart
    b.container(36, 484, 908, 390, "Empirische Entwicklung: Formale Requirements (REQ-*) & Projekt-Kennzahlen", color="blue", subtitle="Echte Git-Historie · Jun – Nov 2026", node_id="evolution_metrics")

    kpis = [
        ("1.265", "Living REQs", "1.023 Verified (81%)", "blue"),
        ("1.272", "Living Tests", "1.058 Verified (83%)", "emerald"),
        ("80 / 77", "Retros / Tags", "Kontin. Lernen", "violet"),
        ("1.226", "Stage-1 Analysen", "~4.700 Commits", "teal"),
    ]
    kx = 52
    kw = 210
    for val, lbl, sub, col in kpis:
        dark, main, soft, bdr = C[col]
        b.add(f'  <g class="node" data-node="evolution_metrics">')
        b.add(f'    <rect x="{kx+1.5}" y="532.5" width="{kw}" height="62" rx="9" fill="#0F172A" fill-opacity="0.05"/>')
        b.add(f'    <rect x="{kx}" y="530" width="{kw}" height="62" rx="9" fill="#FFFFFF" stroke="{bdr}" stroke-width="1.3"/>')
        b.add(f'    <rect x="{kx}" y="530" width="5" height="62" rx="2.5" fill="{main}"/>')
        b.add(f'    <text x="{kx+16}" y="555" font-family="{FONT_SANS}" font-size="20" font-weight="800" fill="{dark}">{esc(val)}</text>')
        b.add(f'    <text x="{kx+16}" y="571" font-family="{FONT_SANS}" font-size="11.2" font-weight="700" fill="{C["ink"]}">{esc(lbl)}</text>')
        b.add(f'    <text x="{kx+16}" y="585" font-family="{FONT_SANS}" font-size="10.4" fill="{C["muted"]}">{esc(sub)}</text>')
        b.add('  </g>')
        kx += kw + 12

    cx0, cy0, cw, ch = 52, 604, 876, 254
    b.add(f'  <rect x="{cx0}" y="{cy0}" width="{cw}" height="{ch}" rx="10" fill="#FFFFFF" stroke="{C["border"]}" stroke-width="1.2"/>')

    px0, px1 = 108, 894
    py_top, py_bot = 630, 816
    max_val = 1400.0
    for gv in (0, 400, 800, 1200):
        gy = py_bot - (gv / max_val) * (py_bot - py_top)
        b.add(f'  <line x1="{px0}" y1="{gy:.1f}" x2="{px1}" y2="{gy:.1f}" stroke="{C["border_light"]}" stroke-width="1" stroke-dasharray="4,4"/>')
        b.add(f'  <text x="{px0-8}" y="{gy+4:.1f}" text-anchor="end" font-family="{FONT_MONO}" font-size="10.5" fill="{C["muted"]}">{gv}</text>')

    pts_data = [
        ("25.06.", 80,   "Vibe → Req"),
        ("25.07.", 204,  "Sorgfältig"),
        ("15.09.", 313,  "Gate-3 Lock"),
        ("05.10.", 498,  "5-Stage AAA"),
        ("25.10.", 809,  "Document Triad"),
        ("05.11.", 877,  "Parallel-Prep"),
        ("12.11.", 1116, "Stage 6 Cold"),
        ("19.11.", 1286, "Schichtbetrieb"),
    ]
    coords = []
    for i, (dlbl, val, ann) in enumerate(pts_data):
        px = px0 + (i / (len(pts_data) - 1)) * (px1 - px0)
        py = py_bot - (val / max_val) * (py_bot - py_top)
        coords.append((px, py, dlbl, val, ann))

    poly_pts = f"{coords[0][0]:.1f},{py_bot} " + " ".join(f"{x:.1f},{y:.1f}" for x, y, _, _, _ in coords) + f" {coords[-1][0]:.1f},{py_bot}"
    b.add(f'  <polygon points="{poly_pts}" fill="{C["blue"][2]}" fill-opacity="0.72"/>')
    b.add(f'  <polyline points="{" ".join(f"{x:.1f},{y:.1f}" for x, y, _, _, _ in coords)}" fill="none" stroke="{C["blue"][1]}" stroke-width="3" stroke-linejoin="round" stroke-linecap="round"/>')

    for i, (x, y, dlbl, val, ann) in enumerate(coords):
        is_last = (i == len(coords) - 1)
        col = "teal" if is_last else ("rose" if i == len(coords) - 2 else "blue")
        b.add(f'  <circle cx="{x:.1f}" cy="{y:.1f}" r="{6.5 if is_last else 5}" fill="{C[col][1]}" stroke="#FFFFFF" stroke-width="2"/>')
        b.add(f'  <text x="{x:.1f}" y="{py_bot+18}" text-anchor="middle" font-family="{FONT_MONO}" font-size="10.5" font-weight="600" fill="{C["body"]}">{dlbl}</text>')
        b.add(f'  <text x="{x:.1f}" y="{py_bot+32}" text-anchor="middle" font-family="{FONT_SANS}" font-size="9.8" font-weight="700" fill="{C[col][0]}">{esc(ann)}</text>')
        vy = y - 12 if i != 5 else y + 18
        b.add(f'  <text x="{x:.1f}" y="{vy:.1f}" text-anchor="middle" font-family="{FONT_MONO}" font-size="10.8" font-weight="700" fill="{C[col][0]}">{val}</text>')

    # Bottom Right: 4 Lernkurven
    b.container(964, 484, 600, 390, "Der rote Faden: 4 reale Engpässe & unsere Antworten", color="teal", subtitle="Warum jede Regel existiert", node_id="evolution_lessons")
    lessons = [
        ("1. Voreiliges Coden & gebrochene Invarianten", "Sep 2026", [
            "**Schmerz:** Agenten implementieren ohne Analyse & brechen Altlogik.",
            "**→ Lösung:** Gate-3 Code Lock, Chesterton's Fence & agent1 ⇄ agent2.",
        ], "indigo"),
        ("2. Regel-Wildwuchs (84+ Regeln) & Context Rot", "Okt 2026", [
            "**Schmerz:** Zu viele lose Regeln verstopfen lange Agenten-Sessions.",
            "**→ Lösung:** Document Triad (9 Core Rules), Artefakte & 3-Ticket-Rotation.",
        ], "amber"),
        ("3. Betriebsblindheit im selben Chat-Kontext", "Nov 2026", [
            "**Schmerz:** Self-Review & Walkthrough-Anchoring übersehen echte Bugs.",
            "**→ Lösung:** Stage 6 Cold Review (Emulator + PNG) & Dual-Vendor Audits.",
        ], "rose"),
        ("4. Wartezeiten, Build-Staus & Nacht-Leerlauf", "17.–19. Nov", [
            "**Schmerz:** --rerun-tasks (6–8 min) & manuelle Sprint-Starts bremsen.",
            "**→ Lösung:** 3 Schichten, 3 Lanes, flock, FROM-CACHE, Lean Tier-2 & 447/453.",
        ], "teal"),
    ]
    ly = 530
    for title, when, lines, col in lessons:
        b.card(980, ly, 568, 78, title, lines, color=col, badge=when, node_id="evolution_lessons", title_size=12.2, line_size=11.2, line_gap=16)
        ly += 84

    return b.render()


# =============================================================================
# SLIDE 01: PHASE 1 — VIBE-CODING (PROS & CONS)
# =============================================================================
def build_d01_phase1_vibe() -> str:
    b = SvgBuilder(1600, 900, "d01")
    b.header(
        "01 · STUFE 1 VON 5",
        "Phase 1: Vibe-Coding — Ad-hoc mit einem einzelnen Agenten",
        "„Frei Schnauze“: Ein Entwickler promptet direkt in den Code, bis das gewünschte Feature auf den ersten Blick irgendwie läuft",
        "slate",
    )
    b.phase_breadcrumbs(0, "slate")

    # Top Left: Schematic Workflow (x=36..986, y=146..412)
    b.container(36, 146, 950, 266, "Funktionsprinzip: Direkte Prompt-Code-Schleife ohne Leitplanken", color="slate", subtitle="Mensch ⇄ 1 Agent (kein Prozess)", node_id="phase1_vibe")
    b.card(60, 204, 210, 126, "Mensch (Entwickler)", [
        "• Formuliert spontanen",
        "  Wunsch im Chat",
        "• Klickt kurz in der App",
        "• „Sieht gut aus!“",
    ], color="indigo", node_id="phase1_vibe", title_size=12.5, line_size=11.3, line_gap=16.5)

    b.arrow(270, 250, 352, 250, color="slate", label="Ad-hoc Prompt", lx=311, ly=236)
    b.arrow(352, 290, 270, 290, color="amber", label="„Fertig!“", lx=311, ly=309, dashed=True)

    b.card(354, 198, 268, 138, "1 KI-Agent (Single Session)", [
        "• Schreibt Code sofort direkt los",
        "• Ändert oft Neben-Dateien mit",
        "• Keine Analyse, keine Spec,",
        "  kein zweites Augenpaar",
        "• Vergisst Alt-Kontext bei Reset",
    ], color="slate", strong_border=True, node_id="phase1_vibe", title_size=12.5, line_size=11.3, line_gap=16.5)

    b.arrow(622, 267, 704, 267, color="slate", label="Direkt-Edit", lx=663, ly=253)

    b.card(706, 204, 256, 126, "Codebase (Ungesichert)", [
        "• Keine formalen REQ-* / TST-*",
        "• Kaum/keine Regressionstests",
        "• Ungeprüfte Seiten-Effekte",
        "• Wissen lebt nur im Chat-Log",
    ], color="rose", node_id="phase1_vibe", title_size=12.5, line_size=11.3, line_gap=16.5)

    b.add(f'  <rect x="60" y="354" width="902" height="40" rx="8" fill="#FFFFFF" stroke="{C["border"]}" stroke-width="1.2"/>')
    b.add(f'  <text x="511" y="378.5" text-anchor="middle" font-family="{FONT_SANS}" font-size="11.8" font-weight="700" fill="{C["slate"][0]}">Charakteristik: Maximale Startgeschwindigkeit in Minute 1 — aber fehlende Nachvollziehbarkeit ab Tag 3.</text>')

    # Top Right: Steckbrief (x=1006..1564, y=146..412)
    b.container(1006, 146, 558, 266, "Steckbrief der Phase", color="slate", subtitle="Ausgangspunkt", node_id="phase1_vibe")
    b.card(1024, 194, 522, 202, "Einordnung im aTrainingTracker-Kontext", [
        "**Zeitraum:** Einstieg in KI-gestützte Entwicklung (bis Mitte Juni 2026)",
        "**Steuerung:** Mensch interagiert direkt & synchron mit einem Agenten",
        "**Qualitätsprüfung:** Manuelles Ausprobieren („kompiliert & sieht ok aus“)",
        "**Dokumentation:** Keine — Entscheidungen verpuffen nach Chat-Ende",
        "**Test-Strategie:** Sporadisch oder gar nicht automatisiert",
        "**Git-Hygiene:** Große, gemischte Ad-hoc-Commits ohne Ticket-Bezug",
    ], color="slate", node_id="phase1_vibe", title_size=12.6, line_size=11.5, line_gap=22)

    b.pros_cons_and_story_footer(
        pros=[
            ("Sofortiger „Aha!“-Effekt & Tempo", "In wenigen Minuten entstehen lauffähige UI-Entwürfe, Skripte und erste Features ohne Setup-Hürde."),
            ("Null Prozess-Overhead", "Kein Ticket, kein Plan, kein Gate — ideal zum freien Experimentieren und Lernen der KI-Fähigkeiten."),
            ("Hohe Kreativität & Explorationsfreiheit", "Schnelles Verwerfen und Ausprobieren von 3–4 Ideen in einer Stunde kostet fast nichts."),
            ("Niedrigste Einstiegshürde", "Jeder Entwickler kann sofort starten, ohne Governance, Tooling oder Multi-Agenten-Infrastruktur."),
        ],
        cons=[
            ("Stille Regressionen & zerstörte Invarianten", "Der Agent „repariert“ Problem B und reißt unbemerkt bestehende Sensor- oder DB-Logik A ein."),
            ("Selbstbestätigungs-Blindspot", "Derselbe Agent, der einen Denkfehler programmiert, hält seine Lösung für brillant und beteuert „alles passt“."),
            ("Kein Langzeitgedächtnis (Context Amnesia)", "Sobald das Chatfenster voll oder geschlossen ist, gehen Architektur-Gründe unwiederbringlich verloren."),
            ("Exponentiell steigende Reparatur-Schleifen", "Ab einer gewissen App-Größe verbringt man mehr Zeit mit dem Jagen neuer KI-Bugs als mit echtem Fortschritt."),
        ],
        sweet_spot_title="Wann ist Stufe 1 (Vibe-Coding) genau richtig?",
        sweet_spot_lines=[
            "• **Wegwerf-Prototypen & Spikes** (30–90 Min.), um eine UI-Idee oder API-Machbarkeit zu testen.",
            "• **Kleine Einmal-Skripte** ohne Produktions- oder Wartungsverantwortung.",
            "• **Nicht geeignet** für eine gewachsene Android-App mit BLE/ANT+-Sensoren, SQLite-Daten & echten Nutzern!",
        ],
        next_title="Auslöser für Stufe 2 → Augenmaß",
        next_badge="Wendepunkt 1 → 2",
        next_lines=[
            "• Ein einziger Agent überzeugt sich selbst von fehlerhaftem Code — **blindes Vertrauen reicht nicht**.",
            "• Erste Erkenntnis: **„Lass einen zweiten Agenten kritisch draufschauen, bevor ich es übernehme!“**",
        ],
        next_color="blue",
        node_id="phase1_vibe",
    )
    return b.render()


# =============================================================================
# SLIDE 02: PHASE 2 — AUGENMASS (PROS & CONS)
# =============================================================================
def build_d02_phase2_augenmass() -> str:
    b = SvgBuilder(1600, 900, "d02")
    b.header(
        "02 · STUFE 2 VON 5",
        "Phase 2: Augenmaß — Mensch + gezielte Zweitmeinung mehrerer Agenten",
        "Noch ohne Prozess-Ballast, aber mit dem entscheidenden Durchbruch: Ein zweiter, unabhängiger Agent kritisiert den ersten",
        "blue",
    )
    b.phase_breadcrumbs(1, "blue")

    # Top Left: Schematic Workflow
    b.container(36, 146, 950, 266, "Funktionsprinzip: Vier-Augen-Prinzip auf Zuruf (ohne formales Gerüst)", color="blue", subtitle="Mensch + Zweitmeinung", node_id="phase2_augenmass")
    b.card(56, 200, 185, 134, "Mensch (Dirigent)", [
        "• Stellt Aufgabe",
        "• Moderiert beide",
        "  Agenten manuell",
        "• Entscheidet nach",
        "  Bauchgefühl",
    ], color="indigo", node_id="phase2_augenmass", title_size=12.4, line_size=11.2, line_gap=16)

    b.arrow(241, 250, 294, 250, color="blue", label="Auftrag", lx=267, ly=236)

    b.card(296, 198, 224, 138, "Agent A (Umsetzer)", [
        "• Entwirft Lösung & Code",
        "• Erklärt seine Änderungen",
        "• Arbeitet Feedback aus",
        "  Zweitmeinung ein",
    ], color="blue", node_id="phase2_augenmass", title_size=12.4, line_size=11.2, line_gap=16.5)

    b.arrow(520, 246, 588, 246, color="amber", label="Diff / Code", lx=554, ly=232)
    b.arrow(588, 290, 520, 290, color="rose", label="Kritik", lx=554, ly=309, dashed=True)

    b.card(590, 198, 224, 138, "Agent B (Zweitmeinung)", [
        "• Frischer Blick / anderes",
        "  Modell oder neuer Chat",
        "• Deckt logische Lücken &",
        "  Edge Cases auf",
    ], color="amber", strong_border=True, node_id="phase2_augenmass", title_size=12.4, line_size=11.2, line_gap=16.5)

    b.arrow(814, 267, 856, 267, color="emerald", width=2.0)
    b.card(858, 214, 108, 106, "Merge", [
        "wenn das",
        "**Gefühl**",
        "stimmt",
    ], color="emerald", accent_bar=False, node_id="phase2_augenmass", title_size=12.2, line_size=11.2, line_gap=16.5)

    b.add(f'  <rect x="56" y="354" width="910" height="40" rx="8" fill="#FFFFFF" stroke="{C["blue"][3]}" stroke-width="1.2"/>')
    b.add(f'  <text x="511" y="378.5" text-anchor="middle" font-family="{FONT_SANS}" font-size="11.8" font-weight="700" fill="{C["blue"][0]}">Kern-Durchbruch: Schon eine einzige unabhängige Zweitmeinung eliminiert einen Großteil grober KI-Halluzinationen.</text>')

    # Top Right: Steckbrief
    b.container(1006, 146, 558, 266, "Steckbrief der Phase", color="blue", subtitle="Erster Qualitätssprung", node_id="phase2_augenmass")
    b.card(1024, 194, 522, 202, "Einordnung im aTrainingTracker-Kontext", [
        "**Zeitraum:** Übergangsphase im Frühsommer 2026",
        "**Steuerung:** Mensch steuert aktiv, holt gezielt eine Zweitmeinung ein",
        "**Qualitätsprüfung:** Informelles Peer-Review durch zweiten Agenten",
        "**Dokumentation:** Weiterhin minimal — keine verbindliche Spec",
        "**Test-Strategie:** Manuelle Stichproben + vereinzelte Tests",
        "**Lerngewinn:** Geburt der Idee „getrennte Perspektiven fangen Blindspots“",
    ], color="blue", node_id="phase2_augenmass", title_size=12.6, line_size=11.5, line_gap=22)

    b.pros_cons_and_story_footer(
        pros=[
            ("Deutlich höhere Code-Qualität bei minimalem Aufwand", "Ein zweiter Blick deckt offensichtliche Logikfehler, Null-Pointer und Architektur-Brüche sofort auf."),
            ("Bricht die „Ja-Sager“-Spirale eines einzelnen Chats", "Ein frischer Reviewer ist nicht in die Rechtfertigung des Erst-Agenten verliebt."),
            ("Weiterhin extrem flexibel & schnell", "Kein starres Formularwesen — der Mensch entscheidet pragmatisch, wo ein zweiter Blick lohnt."),
            ("Gutes Kosten-Nutzen-Verhältnis für mittlere Änderungen", "Ideal für isolierte Refactorings oder knifflige Einzel-Algorithmen."),
        ],
        cons=[
            ("Qualität hängt komplett von Tagesform & Bauchgefühl ab", "Ohne verbindliche Checkliste übersieht auch Agent B systematische Anforderungen (z. B. 9 Sprachen, DB-Upgrade)."),
            ("Kein verbindlicher Soll-Ist-Abgleich", "Ohne niedergeschriebene Requirements weiß Agent B gar nicht, was das Feature genau erfüllen *muss*."),
            ("Hoher manueller Moderations-Aufwand beim Menschen", "Copy-Paste zwischen Chats, manuelles Nachfragen und manuelles Prüfen fressen Entwicklerzeit."),
            ("Keine wiederholbare Regressionssicherung", "Was heute per Augenmaß gut aussah, kann nächste Woche stillschweigend wieder kaputtgehen."),
        ],
        sweet_spot_title="Wann ist Stufe 2 (Augenmaß) genau richtig?",
        sweet_spot_lines=[
            "• **Explorative Architektur-Entscheidungen** („Challenge meinen Entwurf mit einer Gegenmeinung!“).",
            "• **Kleinere Tools, Skripte & isolierte Komponenten** ohne langfristige Compliance-/Release-Pflicht.",
            "• **Übergangsstufe**, wenn man merkt, dass Vibe-Coding nicht mehr reicht, aber noch kein Ticket-System nutzt.",
        ],
        next_title="Auslöser für Stufe 3 → Sorgfältig",
        next_badge="Wendepunkt 2 → 3",
        next_lines=[
            "• Mitte Juni 2026: Die App wächst rasant — **ohne feste Tickets, Requirements & Tests verliert man den Überblick**.",
            "• Konsequenz (`19.–21.06.2026`): Einführung von **Jira-Tasks**, erster **`requirements.md`** und sauberem Ticket-für-Ticket-Testen!",
        ],
        next_color="indigo",
        node_id="phase2_augenmass",
    )
    return b.render()


# =============================================================================
# SLIDE 03: PHASE 3 — SORGFÄLTIG (JUN – AUG 2026 · PROS & CONS)
# =============================================================================
def build_d03_phase3_sorgfaeltig() -> str:
    b = SvgBuilder(1600, 900, "d03")
    b.header(
        "03 · STUFE 3 VON 5",
        "Phase 3: Sorgfältig — Mensch steuert Ticket-für-Ticket sauber getestet",
        "Juni – August 2026: Erste requirements.md (19.06.), Jira-Anbindung (21.06.), 9-Sprachen-Support & automatisierte Tests — noch ohne schweres ASPICE-Gerüst",
        "indigo",
    )
    b.phase_breadcrumbs(2, "indigo")

    # Top Left: Schematic Workflow
    b.container(36, 146, 950, 266, "Funktionsprinzip: Sauberer Ticket-Einzelzyklus unter direkter menschlicher Führung", color="indigo", subtitle="Jun – Aug 2026 · 80 → 204 REQs", node_id="phase3_sorgfaeltig")

    b.card(56, 200, 172, 134, "1. Jira-Ticket", [
        "• Klarer Einzel-",
        "  auftrag",
        "• Mensch wählt",
        "  nächstes Ticket",
    ], color="indigo", node_id="phase3_sorgfaeltig", title_size=12.2, line_size=11.2, line_gap=16.5)
    b.arrow(228, 267, 260, 267, color="indigo", width=2.0)

    b.card(262, 198, 224, 138, "2. Umsetzung + Tests", [
        "• Agent implementiert",
        "• Schreibt Unit-Tests",
        "• Aktualisiert erste",
        "  requirements.md",
        "• 9 Sprachen berücksichtigt",
    ], color="blue", node_id="phase3_sorgfaeltig", title_size=12.2, line_size=11.2, line_gap=16)
    b.arrow(486, 267, 518, 267, color="blue", width=2.0)

    b.card(520, 198, 220, 138, "3. Mensch prüft", [
        "• Code-Review &",
        "  Ausprobieren am Gerät",
        "• Nachbesserung bei",
        "  Abweichungen",
    ], color="amber", strong_border=True, node_id="phase3_sorgfaeltig", title_size=12.2, line_size=11.2, line_gap=16.5)
    b.arrow(740, 267, 772, 267, color="emerald", width=2.0)

    b.card(774, 200, 192, 134, "4. Sauberer Commit", [
        "• Feature fertig",
        "• Ticket geschlossen",
        "• Nächstes Ticket",
        "  startet manuell",
    ], color="emerald", node_id="phase3_sorgfaeltig", title_size=12.2, line_size=11.2, line_gap=16.5)

    b.add(f'  <rect x="56" y="354" width="910" height="40" rx="8" fill="#FFFFFF" stroke="{C["indigo"][3]}" stroke-width="1.2"/>')
    b.add(f'  <text x="511" y="378.5" text-anchor="middle" font-family="{FONT_SANS}" font-size="11.6" font-weight="700" fill="{C["indigo"][0]}">Echter Git-Meilenstein (21.06.2026): „Going towards a much more strict and professional documentation and testing process; motivated by ASPICE.“</text>')

    # Top Right: Steckbrief
    b.container(1006, 146, 558, 266, "Steckbrief & reale Zahlen", color="indigo", subtitle="Jun – Aug 2026", node_id="phase3_sorgfaeltig")
    b.card(1024, 194, 522, 202, "Einordnung im aTrainingTracker-Kontext", [
        "**Zeitraum:** 19. Juni – Ende August 2026 (~680 Commits)",
        "**Requirements:** Wachstum von **80** (25.06.) auf **204 REQ-*** (25.07.)",
        "**Steuerung:** Mensch steuert jedes einzelne Ticket interaktiv",
        "**Qualitätsprüfung:** Automatisierte Tests + menschliche Endabnahme",
        "**Was noch fehlt:** Harte Pre-Code-Gates, unabhängiger KI-Auditor,",
        "  formale REQ↔TST-Parität & autonome Sprint-Ausführung",
    ], color="indigo", node_id="phase3_sorgfaeltig", title_size=12.6, line_size=11.5, line_gap=22)

    b.pros_cons_and_story_footer(
        pros=[
            ("Saubere, nachvollziehbare Einzel-Ergebnisse", "Jedes Ticket hat klaren Scope, Tests und erste Anforderungs-Dokumentation — deutlich weniger Chaos."),
            ("Geringe Prozess-Reibung für Einzel-Entwickler", "Leichtgewichtig, sofort verständlich und angenehm im direkten Pair-Programming mit der KI."),
            ("Erste echte Wissensbasis im Repository", "Mit requirements.md und Jira existiert erstmals ein Gedächtnis außerhalb flüchtiger Chatverläufe."),
            ("Hohe menschliche Kontrolle bei jedem Schritt", "Der Entwickler bleibt permanent „am Steuer“ und sieht jede Änderung unmittelbar."),
        ],
        cons=[
            ("Agenten coden oft noch voreilig drauflos", "Ohne erzwungene Analyse- & Planungs-Gates springt die KI direkt in den Code und übersieht Alt-Invarianten."),
            ("Mensch ist der zeitliche Flaschenhals (1:1 Bindung)", "Wenn der Entwickler schläft, arbeitet oder Pause macht, steht die gesamte Entwicklung still."),
            ("Requirements & Tests driften schleichend auseinander", "Ohne automatische Governance-Prüfung veralten Statusangaben und Test-Zuordnungen nach wenigen Wochen."),
            ("Skaliert nicht auf große V5-Backlogs (~100+ Tickets)", "Ticket-für-Ticket manuell durchzuführen kostet Monate — der Durchsatz stößt an eine harte Decke."),
        ],
        sweet_spot_title="Wann ist Stufe 3 (Sorgfältig) genau richtig?",
        sweet_spot_lines=[
            "• **Kleine bis mittlere Projekte** oder Teams, die entspannt 1–3 Tickets am Tag interaktiv bearbeiten.",
            "• **Gezielte Bugfixes & Wartung** in Codebases, in denen man keinen autonomen Sprint-Betrieb braucht.",
            "• **Solides professionelles Niveau** — für viele Alltagsprojekte bereits völlig ausreichend!",
        ],
        next_title="Auslöser für Stufe 4a → Höchstmaß (Geburt von AAA)",
        next_badge="Wendepunkt 3 → 4a",
        next_lines=[
            "• Anfang September 2026: Voreilige KI-Edits brechen Altlogik; manuelles Babysitting bremst massiv.",
            "• Durchbruch (`03.–28.09.`): **„Bewusste Stolpersteine (ASPICE-Gates), Dual-Agent-Kaskade & Sprint-Branches!“**",
        ],
        next_color="amber",
        node_id="phase3_sorgfaeltig",
    )
    return b.render()


# =============================================================================
# SLIDE 04: PHASE 4a — HÖCHSTMASS: GEBURT & STOLPERSTEINE (SEP 2026)
# =============================================================================
def build_d04_phase4a_hoechstmass() -> str:
    b = SvgBuilder(1600, 900, "d04")
    b.header(
        "04 · STUFE 4a VON 5",
        "Phase 4a: Höchstmaß — Geburt von Agile Agentic ASPICE (AAA)",
        "September 2026: Der Paradigmenwechsel vom „schnellen Coden“ zu bewussten Stolpersteinen, Gate-3 Code Lock, Chesterton's Fence & Zipper-Prinzip",
        "amber",
    )
    b.phase_breadcrumbs(3, "amber")

    # Top Left: Schematic Workflow (5-Stage Cascade + Code Lock + Zipper)
    b.container(36, 146, 950, 266, "Funktionsprinzip: Die 5-Stage ASPICE-Kaskade + Gate-3 Code Lock (Strategie A)", color="amber", subtitle="Sep 2026 · 1.055 Commits", node_id="phase4a_hoechstmass")

    st_mini = [
        ("S1 Analysis", "Root Cause &", "Chesterton", "blue"),
        ("S2 Test Spec", "REQ ↔ TST", "Given-When", "blue"),
        ("S3 Impl Plan", "SWE.2 Plan", "**CODE LOCK**", "amber"),
        ("S4 Impl", "Code + Tests", "erst nach G3!", "blue"),
        ("S5 Test", "Walkthrough", "[Verified]", "emerald"),
    ]
    sx = 54
    sw = 162
    for idx, (t, l1, l2, col) in enumerate(st_mini):
        strong = (idx == 2)
        b.card(sx, 198, sw, 82, t, [l1, l2], color=col, accent_bar=False, badge=f"G{idx+1}", node_id="phase4a_hoechstmass", bg=C["amber"][2] if strong else "#FFFFFF", title_size=11.6, line_size=10.8, line_gap=15.5, strong_border=strong)
        if idx < 4:
            b.arrow(sx + sw + 1, 239, sx + sw + 18, 239, color=col, width=1.8)
        sx += sw + 20

    # Zipper + Human Sprint Control strip underneath
    b.card(54, 296, 444, 98, "Asymmetrisches Zipper-Prinzip (agent1 ⇄ agent2)", [
        "• **agent1** erstellt Artefakte (`submit-stage`)",
        "• **agent2** prüft streng am Gate (`gate-verdict PASS|REVISION`)",
        "• Kein Agent prüft jemals seine eigene Arbeit!",
    ], color="indigo", node_id="phase4a_hoechstmass", title_size=11.8, line_size=11, line_gap=15.5)

    b.card(514, 296, 452, 98, "Mensch steuert Sprint (Ceremony 1 & 2)", [
        "• **03.09.:** Dual-Agent Sub-Task Workflow eingeführt",
        "• **13.09.:** 4-Pillar Defense gegen vorzeitiges Coden (`Gate 3`)",
        "• **28.09.:** Erster `sprint/2026-40.2` Branch (Strategie A)",
    ], color="teal", node_id="phase4a_hoechstmass", title_size=11.8, line_size=11, line_gap=15.5)

    # Top Right: Steckbrief
    b.container(1006, 146, 558, 266, "Steckbrief & reale Zahlen", color="amber", subtitle="September 2026", node_id="phase4a_hoechstmass")
    b.card(1024, 194, 522, 202, "Der Durchbruch im September 2026", [
        "**Zeitraum:** 03. – 30. September 2026 (**1.055 Commits** — 3× Sprung!)",
        "**Requirements:** Wachstum auf **313 → ~450 REQ-***",
        "**Steuerung:** Mensch plant Sprint (C1) & nimmt ab (C2); Agenten",
        "  arbeiten ganze Sprints autonom über Stages 1–5 ab",
        "**Schlüssel-Konzepte:** Stolpersteine, Gate-3 Code Lock,",
        "  Chesterton's Fence Archäologie & Artefakt-Langzeitgedächtnis",
    ], color="amber", node_id="phase4a_hoechstmass", title_size=12.6, line_size=11.5, line_gap=22)

    b.pros_cons_and_story_footer(
        pros=[
            ("Voreiliges Coden & blinde Altlogik-Brüche gestoppt", "Gate-3 Code Lock und Chesterton's Fence zwingen die KI erst zum Verstehen, dann zum Planen, dann zum Coden."),
            ("Echter autonomer Sprint-Durchsatz (3× mehr Commits)", "Statt einzelner Prompts arbeitet das Agenten-Paar ganze Sprint-Backlogs über Nacht sauber ab."),
            ("Reproduzierbares Langzeitgedächtnis in Markdown", "Analysis, Test Spec, Plan und Walkthrough überleben jedes volle Kontextfenster (Infinite Context)."),
            ("Menschliche Energie fließt in Architektur & Abnahme", "Der PO prüft in Ceremony 2 getestete Ergebnisse statt kleinteilige Zwischenschritte zu babysitten."),
        ],
        cons=[
            ("Regel-Wildwuchs über viele Retros (bald 84+ Regeln)", "Jede Retro fügte neue Regeln hinzu — lange Regeldateien erzeugten Context Rot und wurden teils überlesen."),
            ("Sequenzielle 1-Lane-Ausführung bremst Durchsatz", "Ein blockiertes oder großes Ticket hielt den gesamten Sprint auf; paralleles Arbeiten war noch konfliktträchtig."),
            ("Self-Anchoring im selben Pair über Stages 1–5", "Da das Paar den ganzen Ticket-Verlauf teilte, rutschten subtile Bugs manchmal bis in die PO-Abnahme (C2) durch."),
            ("Tests & Emulator kollidierten mit dem Produktiv-Handy", "Ohne isolierten Emulator und Lock-Dateien blockierten Builds oder störten das persönliche Smartphone."),
        ],
        sweet_spot_title="Wann ist Stufe 4a (Klassisches Höchstmaß) stark?",
        sweet_spot_lines=[
            "• **Qualitätskritische Features & Bugfixes**, bei denen man volle Traceability (REQ↔TST) und saubere Architektur braucht.",
            "• **Interaktive Tagessprints**, in denen der PO nach jedem Sprint persönlich durch alle Tickets geht.",
            "• **Fundament** für alles, was danach kam: Alle Kernprinzipien von AAA wurden in diesem Monat geboren.",
        ],
        next_title="Auslöser für Stufe 4b → Triad, Worktrees & Stage 6 Cold Review",
        next_badge="Wendepunkt 4a → 4b",
        next_lines=[
            "• Oktober/November 2026: **2.077 Commits/Monat**, **84+ Regeln** und immer größere Sprints fordern industrielle Härtung!",
            "• Antwort (`Okt – 15.11.`): **Document Triad (9 Core Rules)**, **parallele Worktrees**, **`emulator-5554`** & **Stage 6 Cold Review**!",
        ],
        next_color="rose",
        node_id="phase4a_hoechstmass",
    )
    return b.render()


# =============================================================================
# SLIDE 05: PHASE 4b — HÖCHSTMASS: TRIAD, WORKTREES & STAGE 6 (OKT – 15. NOV)
# =============================================================================
def build_d05_phase4b_skalierung() -> str:
    b = SvgBuilder(1600, 900, "d05")
    b.header(
        "05 · STUFE 4b VON 5",
        "Phase 4b: Industrialisierung — Document Triad, Worktrees & Stage 6 Cold Review",
        "Oktober – 15. Nov. 2026: Konsolidierung von 84+ Retro-Regeln, parallele Worktree-Lanes + flock, dedizierter Emulator & orthogonale Cold-Review-Barriere p₆",
        "rose",
    )
    b.phase_breadcrumbs(4, "rose")

    # Top Left: 4 Industrialization Pillars Schematic
    b.container(36, 146, 950, 266, "Die 4 großen Architektur-Upgrades in Phase 4b (Okt – 15. Nov 2026)", color="rose", subtitle="2.077 Commits im Okt · 498 → 1.116 REQs", node_id="phase4b_skalierung")

    upgrades = [
        ("1. Document Triad", "Okt 2026", [
            "• 84+ lose Retro-Regeln",
            "  → **9 Core Rules**",
            "• coding_standards.md",
            "• design_guidelines.md",
            "• Domain-Shards & Parität",
        ], "amber"),
        ("2. Worktrees & flock", "08.11.2026", [
            "• Parallele Epic-Lanes",
            "• Workspace: share",
            "• POSIX **flock** für",
            "  Gradle & --merge-ticket",
            "• Konfliktfreie Strings",
        ], "teal"),
        ("3. Stage 6 Cold Review", "11.–12.11.", [
            "• **Zero-Context** Auditor",
            "• Anti-Anchoring (Diff!)",
            "• Pros/Cons/Freigabe?",
            "• **3-Bounce-Healing**",
            "• emulator-5554 (11.11.)",
        ], "rose"),
        ("4. SUP.1 & Queue 447", "12.–15.11.", [
            "• Getrennte Jira-Tokens",
            "  (.env.jira.agent1/2)",
            "• **ID 447** Human-Queue",
            "• Erster 12h-Dauertakt",
            "  (ATT-8030 / Rule 85)",
        ], "indigo"),
    ]
    ux = 54
    uw = 216
    for t, dt, lines, col in upgrades:
        b.card(ux, 198, uw, 194, t, lines, color=col, badge=dt, node_id="phase4b_skalierung", title_size=12, line_size=11, line_gap=17, strong_border=(col == "rose"), badge_above_title=True, meta_left="Upgrade")
        if ux < 700:
            b.arrow(ux + uw + 2, 295, ux + uw + 14, 295, color=col, width=1.8)
        ux += uw + 16

    # Top Right: Steckbrief
    b.container(1006, 146, 558, 266, "Steckbrief & reale Zahlen", color="rose", subtitle="Okt – 15. Nov 2026", node_id="phase4b_skalierung")
    b.card(1024, 194, 522, 202, "Vom Handwerk zur gehärteten Engineering-Pipeline", [
        "**Zeitraum:** Oktober – 15. November 2026 (**>2.600 Commits**)",
        "**Requirements:** Sprung von **498** (05.10.) auf **1.116 REQ-*** (12.11.)",
        "**Qualitäts-Meilenstein:** 100 % grüne Unit-Test-Suite (**5.947 Tests**)",
        "**Neue Verteidigung:** Stage 6 (`p₆`) fängt Defekte ab, **bevor** der PO sie sieht",
        "**Hardware-Schutz:** Agenten testen strikt auf `emulator-5554`, nie am Handy",
        "**Offener Engpass:** Sprints endeten noch mit manuellen Pausen & teuren Rebuilds",
    ], color="rose", node_id="phase4b_skalierung", title_size=12.6, line_size=11.4, line_gap=22)

    b.pros_cons_and_story_footer(
        pros=[
            ("Dramatisch weniger Defect Escapes bis zum PO", "Stage 6 Cold Review (Zero-Context + 3-Bounce-Loop) heilt subtile Bugs autonom vor der PO-Abnahme."),
            ("Echte Parallelität ohne Merge-Hölle", "Disjunkte Epic-Worktrees, Domain-Shards und POSIX flock ermöglichen parallele Feature-Entwicklung."),
            ("Context Rot unter Kontrolle durch Document Triad", "Statt 84+ verstreuter Regeln reichen 9 kompakte Core Rules + automatische SKILL.md-Injektion via CLI."),
            ("Echte ASPICE SUP.1/SUP.8 Rollen- & Geräte-Trennung", "Separate Jira-Credentials je Rolle und isolierter emulator-5554 schützen Prozess & Daily-Driver-Handy."),
        ],
        cons=[
            ("Pro-Modelle & --rerun-tasks verbrannten Zeit & Budget", "Full-Rebuilds (6–8 min/Lauf) und pauschales Pro-Routing machten Routine-Tickets unnötig langsam und teuer."),
            ("Vorzeitige Squashes & fehlender Live-Test in Stage 6", "Statisches Diff-Review allein übersah UI-/Runtime-Bugs; frühes Mergen erschwerte saubere Stage-6-Bounces."),
            ("Leerlauf & manuelle Übergaben zwischen Sprints", "Zwischen Sprint-Ende und nächstem Start lagen Wartezeiten; Retro-Erkenntnisse versickerten über Einzel-Sprints."),
            ("Steigende Tooling- & Governance-Komplexität", "Mehr Skripte (jira_util.py, parallel_sprint_lock.py, Verifier) erfordern disziplinierte Pflege & Tests."),
        ],
        sweet_spot_title="Was diese Phase dauerhaft etabliert hat",
        sweet_spot_lines=[
            "• **Qualitäts-Architektur:** Document Triad, Stage 6 Cold Review, Rollen-Trennung und Emulator-Policy sind unersetzbar geworden.",
            "• **Beweis der Skalierbarkeit:** Über 1.100 formale Requirements und fast 6.000 grüne Unit-Tests in nur 6 Wochen.",
            "• **Lernkurve:** Jetzt waren nicht mehr „zu wenig Regeln“ das Problem — sondern **Taktung, Effizienz & echte Live-Verifikation**.",
        ],
        next_title="Auslöser für Stufe 5 → Schichtbetrieb nach Höchstmaß",
        next_badge="Wendepunkt 4b → 5",
        next_lines=[
            "• 17.–19. Nov. 2026: **Direkter 24/7-Schichtwechsel (`05/15/22`)**, **3 Lanes**, **Live-Emulator in Stage 6 + Post-PASS Squash**,",
            "  **Flash/Neon-Routing**, **Lean Tier-2 + `FROM-CACHE`**, **Auto-Wochen-Rollup** & **Dual-Vendor Audits (Gemini + Claude)**!",
        ],
        next_color="teal",
        node_id="phase4b_skalierung",
    )
    return b.render()


# =============================================================================
# SLIDE 06: PHASE 5 — SCHICHTBETRIEB NACH HÖCHSTMASS (AKTIV · PROS & CONS)
# =============================================================================
def build_d06_phase5_schichtbetrieb() -> str:
    b = SvgBuilder(1600, 900, "d06")
    b.header(
        "06 · STUFE 5 VON 5 (AKTIV)",
        "Phase 5: Schichtbetrieb nach Höchstmaß — Kontinuierlicher 24/7-Betrieb",
        "Seit 17.–19. Nov. 2026: Volle Höchstmaß-Qualität im nahtlosen 3-Schicht-Takt (05/15/22) auf 3 Lanes mit Live-Stage-6, FROM-CACHE & Dual-Vendor-Audits",
        "teal",
    )
    b.phase_breadcrumbs(5, "teal")

    # Top Left: Schematic of Stufe 5 Operating Loop
    b.container(36, 146, 950, 266, "Funktionsprinzip: Der selbsttragende 24/7-Regelkreis (Details auf Folien 07–11)", color="teal", subtitle="DOC-GOV-RULES v1.0.5 · SCHICHT v1.0.3", node_id="phase5_schichtbetrieb")

    loop_cards = [
        ("PO-Input", "ID 447", [
            "• Kuratiert Backlog",
            "• Schiebt in **447**",
            "• ORDER BY rank",
        ], "indigo"),
        ("3-Schicht-Takt", "05 / 15 / 22", [
            "• 3 disjunkte Lanes",
            "• Flash / Neon*",
            "• FROM-CACHE",
        ], "teal"),
        ("Live Stage 6", "Post-PASS", [
            "• Emulator + PNG",
            "• max. 3 Bounces",
            "• 1 Squash → sprint",
        ], "rose"),
        ("Schichtwechsel", "0 Leerlauf", [
            "• Lean Tier-2 Gate",
            "• Auto-Wochen-Rollup",
            "• develop + Start!",
        ], "emerald"),
        ("PO & Weekly", "453 / 454", [
            "• PO-Abnahme (453)",
            "• Feldtest (454)",
            "• Gemini + Claude",
        ], "violet"),
    ]
    lx = 54
    lw = 166
    for idx, (t, bdg, lines, col) in enumerate(loop_cards):
        b.card(lx, 196, lw, 134, t, lines, color=col, accent_bar=False, badge=bdg, node_id="phase5_schichtbetrieb", title_size=11.8, line_size=10.8, line_gap=16, strong_border=(col in ("teal", "rose")), badge_above_title=True, meta_left=f"0{idx+1}")
        if idx < 4:
            b.arrow(lx + lw + 1, 263, lx + lw + 19, 263, color=col, width=1.9)
        lx += lw + 20

    b.add(f'  <rect x="54" y="344" width="912" height="50" rx="8" fill="#FFFFFF" stroke="{C["teal"][3]}" stroke-width="1.3"/>')
    b.add(f'  <text x="510" y="365" text-anchor="middle" font-family="{FONT_SANS}" font-size="11.6" font-weight="700" fill="{C["teal"][0]}">Leitmotiv: „Höchstmaß“ liefert die ASPICE-Qualität — der „Schichtbetrieb“ liefert Takt, Effizienz &amp; 24/7-Kontinuität.</text>')
    b.add(f'  <text x="510" y="384" text-anchor="middle" font-family="{FONT_SANS}" font-size="11.1" fill="{C["body"]}">Neuerungen 17.–19.11.: Live-Emulator-Stage-6 · Post-PASS Squash · Cross-Worktree FROM-CACHE · Lean Tier-2 · Write-Allowlist (R2.7) · Auto-Wochen-Rollup.</text>')

    # Top Right: Steckbrief
    b.container(1006, 146, 558, 266, "Steckbrief & aktueller Stand", color="teal", subtitle="Stand 19.11.2026", node_id="phase5_schichtbetrieb")
    b.card(1024, 194, 522, 202, "Reifegrad-Bilanz heute (19.11.2026)", [
        "**Takt:** 01_Früh (10h) · 02_Spät (7h) · 03_Nacht (7h) — 0 min Leerlauf",
        "**Parallelität:** 3 disjunkte Lanes (ASPICE/Tooling + 2× Produkt)",
        "**Speed & Kosten:** Flash-Standard, Pro/Neon nur komplex/grafisch,",
        "  inkrementelles `--rerun` + `FROM-CACHE` (~15–60 s statt 6–8 min!)",
        "**Sicherheit:** Write-Allowlist (`Rule 2.7`) + Dual-Vendor (Gemini+Claude)",
        "**Bilanz:** 1.265 Living REQs · 1.272 Tests · 80 Retros · 77 Tags",
    ], color="teal", node_id="phase5_schichtbetrieb", title_size=12.6, line_size=11.4, line_gap=22)

    b.pros_cons_and_story_footer(
        pros=[
            ("Kontinuierliche 24/7-Lieferung ohne Context Rot", "Feste Schichtgrenzen, Single Source of Truth und direkter Schichtwechsel halten die Qualität über Tage stabil."),
            ("Maximale Defekt-Abwehr bei hoher Geschwindigkeit", "Live-Emulator Stage 6 + Post-PASS Squash + Lean Tier-2 + FROM-CACHE verbinden Tempo mit Tiefe."),
            ("PO vollständig entkoppelt, aber jederzeit in Kontrolle", "Geschützte Human-Queues (447 / 453 / 454) + Auto-Wochen-Rollup bündeln Entscheidungen ohne 24/7-Präsenz."),
            ("Herstellerunabhängige Qualitätssicherung (Dual-Vendor)", "Wöchentliche gemeinsame Audits durch Google Gemini & Anthropic Claude brechen Modell-Blindspots."),
        ],
        cons=[
            ("Hohe initiale Investition in Governance & Tooling", "Skills, Rules, jira_util.py, Lock-Skripte, Verifier und Emulator-Skripte wollen gebaut und gepflegt werden."),
            ("Überdimensioniert für kleine Wegwerf-Projekte", "Für ein 2-Stunden-Skript oder einen schnellen Prototyp sind Stufe 1–3 deutlich wirtschaftlicher."),
            ("Laufende Token- & Compute-Kosten im Dauerbetrieb", "24/7 auf 3 Lanes erfordert diszipliniertes Modell-Routing (Flash vs. Pro/Neon) und sauberes Caching."),
            ("Disziplin bei PO-Vorrat & Abnahme nötig", "Ohne gepflegte Queue ID 447 pausiert der Betrieb; ohne regelmäßige Abnahme wächst Queue ID 453 an."),
        ],
        sweet_spot_title="Wann lohnt sich Stufe 5 (Schichtbetrieb nach Höchstmaß)?",
        sweet_spot_lines=[
            "• **Langlebige, komplexe Softwareprodukte** mit hohen Qualitäts-, Stabilitäts- und Nachweisansprüchen (wie aTrainingTracker V5).",
            "• **Große Backlogs mit vielen parallelisierbaren Epics**, bei denen 24/7-Durchsatz echten Produktwert schafft.",
            "• **Die Kunst liegt in der Wahl der richtigen Stufe:** Nicht jedes Problem braucht Stufe 5 — aber Stufe 5 beherrscht die großen!",
        ],
        next_title="Ausblick: Nächste Evolutionsschritte ab V5.0.0",
        next_badge="Roadmap / Zukunft",
        next_lines=[
            "• **Verifizierte ASPICE CL2-Reife** als Basis — danach schrittweise datengetriebene Steuerung (SPC / PA 4.1 & PA 4.2).",
            "• **Tiefe Details zum heutigen Zielbild:** Die folgenden **5 Architektur-Folien (07–11)** zeigen alle Mechanismen im Detail!",
        ],
        next_color="violet",
        node_id="phase5_schichtbetrieb",
    )
    return b.render()


# =============================================================================
# SLIDE 07 (ehem. 01): BIG PICTURE — SCHICHTBETRIEB NACH HÖCHSTMASS
# =============================================================================
def build_d07_big_picture() -> str:
    b = SvgBuilder(1600, 900, "d07")
    b.header(
        "07 · BIG PICTURE",
        "Aktuelles Zielbild: Gesamtüberblick Schichtbetrieb nach Höchstmaß",
        "Vom Produkt-Backlog über kontinuierlichen 24/7-Multi-Agenten-Betrieb (3 Schichten · 3 Lanes · 6 Stages) bis zur PO-Abnahme & Dual-Vendor-Qualität",
        "teal",
    )

    b.add(f'  <text x="36" y="102" font-family="{FONT_SANS}" font-size="12" font-weight="700" fill="{C["muted"]}" letter-spacing="0.5">EINORDNUNG · DIE 5 ARBEITSSTUFEN DER KI-ZUSAMMENARBEIT (DOC-MGT-SCHICHT)</text>')
    stufen = [
        ("1. Vibe-Coding", "Ad-hoc · 1 Agent", "Baut frei drauf los, bis es läuft", "slate", False),
        ("2. Augenmaß", "Mensch + Zweitmeinung", "Mehrere Agenten ohne Prozess-Ballast", "slate", False),
        ("3. Sorgfältig", "Ticket-für-Ticket", "Sauber getestet ohne ASPICE-Gerüst", "blue", False),
        ("4. Höchstmaß", "Mensch steuert Sprint", "Volle ASPICE-Strenge, Gates & Audits", "indigo", False),
        ("5. Schichtbetrieb", "Schichtführer steuert 24/7", "Höchstmaß rund um die Uhr · 3 Lanes", "teal", True),
    ]
    sx = 36
    sw = 286
    sgap = 24
    for idx, (t, sub, desc, col, active) in enumerate(stufen):
        bg = C["teal"][2] if active else "#FFFFFF"
        b.card(
            sx, 110, sw, 68, t,
            [f"**{sub}**", desc],
            color=col, accent_bar=True,
            badge="AKTIV" if active else "",
            node_id="stufen", bg=bg, title_size=12.8, line_size=11.2, line_gap=15,
            strong_border=active,
        )
        if idx < 4:
            b.arrow(sx + sw + 2, 144, sx + sw + sgap - 2, 144, color="teal" if idx == 3 else "slate", width=1.8)
        sx += sw + sgap

    # Column 1: PO-Steuerung & Input
    b.container(36, 194, 292, 540, "1. PO-Steuerung & Input", color="indigo", subtitle="Mensch (PO)", node_id="po_input")
    b.card(52, 242, 260, 96, "Product Backlog", [
        "• Produkt-Vision, Epics & Bugs",
        "• Priorisierung & Refinement",
        "• Nur der PO entscheidet, was",
        "  für Agenten freigegeben wird",
    ], color="indigo", node_id="po_input", line_size=11.4, line_gap=15.5)
    b.arrow(182, 338, 182, 376, color="indigo", label="Nur PO schiebt frei!", lx=182, ly=361)
    b.card(52, 378, 260, 154, "Vom PO Freigegeben", [
        "• Geschützter Pseudo-Sprint",
        "• Strikte ORDER BY rank ASC",
        "• Agenten-Schreibverbot (R9.1)",
        "• human-execution automatisch",
        "  übersprungen (→ ID 454)",
        "• Kapazität: max. 8 SP / Lane",
    ], color="indigo", badge="ID 447", node_id="queue_447", strong_border=True, line_size=11.4, line_gap=15.5)
    b.card(52, 548, 260, 170, "Schichtführer (Coord.)", [
        "• Hub-and-Spoke-Steuerung",
        "• Greedy Bin-Packing (3 Lanes)",
        "• Single Source of Truth:",
        "  Git, Jira, Retro, Risk Register",
        "• Kein Überschreiben von",
        "  Gate-Verdicts (Rule 3 / SUP.1)",
        "pack-sprint --lanes 3",
    ], color="teal", badge="Hub", node_id="coordinator", line_size=11.3, line_gap=15.2, mono_lines={6})

    # Column 2: Autonomer 24/7-Schichtbetrieb
    b.container(362, 194, 850, 540, "2. Autonomer 24/7-Schichtbetrieb (Strategie A)", color="teal", subtitle="sprint/YYYY-MM-DD_0x_Schicht", node_id="schicht_core")
    b.pill(380, 240, 200, 34, "01_Früh · 05:00–15:00 (10h)", color="teal", filled=True, font_size=11.4, node_id="schicht_takt")
    b.pill(590, 240, 190, 34, "02_Spät · 15:00–22:00 (7h)", color="teal", filled=False, font_size=11.4, node_id="schicht_takt")
    b.pill(790, 240, 195, 34, "03_Nacht · 22:00–05:00 (7h)", color="teal", filled=False, font_size=11.4, node_id="schicht_takt")
    b.pill(996, 240, 198, 34, "Direkt-Übergabe · 0 Leerlauf", color="amber", filled=False, font_size=11, node_id="schicht_takt")

    b.add(f'  <rect x="381.5" y="290.5" width="814" height="136" rx="10" fill="#0F172A" fill-opacity="0.05"/>')
    b.add(f'  <rect x="380" y="288" width="814" height="136" rx="10" fill="#FFFFFF" stroke="{C["teal"][3]}" stroke-width="1.3"/>')
    b.add(f'  <text x="396" y="309" font-family="{FONT_SANS}" font-size="13" font-weight="700" fill="{C["teal"][0]}">3 Parallele Disjunkte Epic-Lanes (Isolierte Workspace: &quot;share&quot; Worktrees · POSIX flock)</text>')
    lanes = [
        ("Lane A · ASPICE & Tooling", "Epic ATT-8138 · feature/<KEY>", "agent1 ⇄ agent2 · Flash / Pro*", "blue"),
        ("Lane B · Produkt-Epic 1", "Disjunkte Packages & Shards", "agent1 ⇄ agent2 · Flash / Pro*", "blue"),
        ("Lane C · Produkt-Epic 2", "Disjunkte Packages & Shards", "agent1 ⇄ agent2 · Flash / Pro*", "blue"),
    ]
    lx = 396
    for lt, ls1, ls2, lcol in lanes:
        b.card(lx, 320, 250, 72, lt, [ls1, ls2], color=lcol, node_id="lanes", title_size=12, line_size=11, line_gap=15.5)
        lx += 266
    b.add(f'  <text x="396" y="413" font-family="{FONT_SANS}" font-size="10.8" font-weight="600" fill="{C["muted"]}">*Modell-Routing (Rule 7.2): Flash = Routine · Pro / Gemini 4 Neon strikt nur für komplexe (Architektur/DB/Sensor/Bounce 2–3) &amp; grafische UI-Tasks.</text>')

    b.add(f'  <rect x="381.5" y="440.5" width="814" height="146" rx="10" fill="#0F172A" fill-opacity="0.05"/>')
    b.add(f'  <rect x="380" y="438" width="814" height="146" rx="10" fill="#FFFFFF" stroke="{C["blue"][3]}" stroke-width="1.3"/>')
    b.add(f'  <text x="396" y="459" font-family="{FONT_SANS}" font-size="13" font-weight="700" fill="{C["blue"][0]}">6-Stage ASPICE-Pipeline pro Ticket (Zipper-Prinzip: agent1 Doing ⇄ agent2 Gate Review)</text>')
    stages = [
        ("S1 Analysis", "Root Cause &", "Chesterton", "blue"),
        ("S2 Test Spec", "REQ ↔ TST", "9 Sprachen", "blue"),
        ("S3 Impl Plan", "SWE.2 Design", "**CODE LOCK**", "amber"),
        ("S4 Impl", "Code + Tier-1", "--rerun", "blue"),
        ("S5 Test", "Walkthrough", "[Verified]", "blue"),
        ("S6 Cold Review", "Zero-Context", "Emulator+PNG", "rose"),
    ]
    stx = 396
    stw = 118
    for idx, (st, s1, s2, scol) in enumerate(stages):
        strong = scol in ("amber", "rose")
        b.card(stx, 470, stw, 72, st, [s1, s2], color=scol, accent_bar=False, node_id="stage6" if idx == 5 else "stages_1_5", title_size=11.5, line_size=10.8, line_gap=15, strong_border=strong, bg=C[scol][2] if strong else "#FFFFFF")
        if idx < 5:
            b.arrow(stx + stw + 1, 506, stx + stw + 15, 506, color=scol, width=1.6)
        stx += stw + 16
    b.add(f'  <rect x="396" y="551" width="782" height="23" rx="6" fill="{C["emerald"][2]}" stroke="{C["emerald"][3]}" stroke-width="1"/>')
    b.add(f'  <text x="787" y="566.5" text-anchor="middle" font-family="{FONT_SANS}" font-size="10.8" font-weight="700" fill="{C["emerald"][0]}">feature/&lt;KEY&gt; bleibt bis Stage-6 PASS isoliert · max. 3 autonome Bounces · Erst nach PASS: 1 Squash-Commit (--merge-ticket)</text>')

    b.add(f'  <rect x="381.5" y="600.5" width="814" height="120" rx="10" fill="#0F172A" fill-opacity="0.05"/>')
    b.add(f'  <rect x="380" y="598" width="814" height="120" rx="10" fill="#FFFFFF" stroke="{C["emerald"][3]}" stroke-width="1.3"/>')
    b.add(f'  <text x="396" y="619" font-family="{FONT_SANS}" font-size="13" font-weight="700" fill="{C["emerald"][0]}">Schichtwechsel-Protokoll um 05:00 / 15:00 / 22:00 (Wrap-up → develop → Direkter Next-Schicht Start)</text>')
    sw_steps = [
        ("1. Merge-Freeze", "Offene feature/*", "bleiben intakt", "slate"),
        ("2. Lean Tier-2", "assemble + --rerun", "Contract + Gov.", "teal"),
        ("3. Pixel 10", "Best-Effort USB", "5.0.0-dev-<sha>", "amber"),
        ("4. Retro & Rollup", "Metrics, QA, Risk", "→ Wochen-Retro", "violet"),
        ("5. develop & Start", "--no-ff + Tag", "carryover-wip", "emerald"),
    ]
    swx = 396
    sww = 144
    for idx, (t, l1, l2, col) in enumerate(sw_steps):
        b.card(swx, 630, sww, 74, t, [l1, l2], color=col, accent_bar=False, node_id="schichtwechsel", title_size=11.5, line_size=10.8, line_gap=15)
        if idx < 4:
            b.arrow(swx + sww + 1, 667, swx + sww + 14, 667, color="emerald", width=1.6)
        swx += sww + 15

    # Column 3: PO-Abnahme & Audits
    b.container(1246, 194, 318, 540, "3. PO-Abnahme & Audits", color="emerald", subtitle="PO & Dual-Vendor", node_id="po_output")
    b.card(1262, 242, 286, 134, "Bereit für PO Abnahme", [
        "• Stage-6-geprüfte Tickets",
        "  (Status: Final Review (Human))",
        "• Exklusives PO-Recht für",
        "  Erledigt + FixVersion (Rule 3.1)",
        "• Schutz vor Agenten-Zugriff",
        "archive-po-acceptance",
    ], color="emerald", badge="ID 453", node_id="queue_453", strong_border=True, line_size=11.3, line_gap=15, mono_lines={5})
    b.card(1262, 392, 286, 126, "Bereit für PO Systemtest", [
        "• human-execution Tickets",
        "• Outdoor- & Sensor-Feldtests",
        "  auf physischem Pixel 10",
        "• Agenten lesen Telemetrie nur",
        "  read-only (Rule 7.6)",
        "defer-systemtest",
    ], color="amber", badge="ID 454", node_id="queue_454", line_size=11.3, line_gap=15, mono_lines={5})
    b.card(1262, 534, 286, 184, "Wochen-Qualitätszyklus", [
        "• Rollierendes Wochen Review &",
        "  Retro (Auto-Rollup pro Schicht)",
        "• Dual-Vendor Audits (1×/Woche):",
        "  **Gemini + Anthropic Claude**",
        "  – ASPICE 4.0 Cross-Audit",
        "  – Holistic Code/Architektur-Review",
        "• Volle Test-Suite, lint, detekt,",
        "  kover (Szenario-Lücken-Check)",
    ], color="violet", badge="1× / Woche", node_id="weekly_dual_vendor", strong_border=True, line_size=11.2, line_gap=15.2)

    b.arrow(328, 455, 360, 455, color="indigo", width=2.4)
    b.arrow(1212, 310, 1244, 310, color="emerald", width=2.4)
    b.arrow(1212, 455, 1244, 455, color="amber", width=2.2, dashed=True)
    b.arrow(1212, 650, 1244, 650, color="violet", width=2.2)

    # Fundament
    b.add(f'  <text x="36" y="762" font-family="{FONT_SANS}" font-size="12" font-weight="700" fill="{C["muted"]}" letter-spacing="0.5">FUNDAMENT · DIE 3 AAA-SÄULEN &amp; DOCUMENT TRIAD</text>')
    pillars = [
        ("1. Stolpersteine (Tripwires)", [
            "Bewusste Quality Gates (1–5 + Cold Review),",
            "Chesterton's Fence & SYSREQ↔REQ↔TST-",
            "Parität verhindern blinde Abkürzungen.",
        ], "rose"),
        ("2. Infinite Context via Artefakte", [
            "Kanonische Markdown-Deliverables in",
            "docs/engineering/ & Living Docs dienen als",
            "Langzeitgedächtnis über Compactions hinweg.",
        ], "blue"),
        ("3. Pflicht zum Widerspruch", [
            "Konstruktive Resistenz bei Regelausnahmen;",
            "Abweichungen nur auf PO-Anweisung und",
            "vollständig dokumentiert (Rule 1.3).",
        ], "amber"),
        ("4. Document Triad & Write-Allowlist", [
            "aspice_governance.md · coding_standards.md",
            "design_guidelines.md · Autonome Schichten",
            "ändern Regeln nie selbst (Rule 2.7).",
        ], "indigo"),
    ]
    px = 36
    pw = 368
    for title, lines, col in pillars:
        b.card(px, 770, pw, 102, title, lines, color=col, node_id="aaa_pillars", title_size=12.5, line_size=11.3, line_gap=15.5)
        px += pw + 18.6

    return b.render()


# =============================================================================
# SLIDE 08: 24/7-SCHICHT-TAKT & DIREKTER SCHICHTWECHSEL
# =============================================================================
def build_d08_schicht_takt() -> str:
    b = SvgBuilder(1600, 900, "d08")
    b.header(
        "08 · SCHICHT-TAKT",
        "Deep-Dive: 24/7-Schicht-Takt & Direkter Schichtwechsel",
        "PO-ausgerichtete 3-Schicht-Kadenz (05:00 / 15:00 / 22:00) mit nahtloser 2-Phasen-Übergabe ohne Leerlauf (Rule 2.2, 2.7 & DOC-MGT-SCHICHT)",
        "teal",
    )

    b.add(f'  <rect x="37.5" y="94.5" width="1528" height="132" rx="14" fill="#0F172A" fill-opacity="0.05"/>')
    b.add(f'  <rect x="36" y="92" width="1528" height="132" rx="14" fill="#FFFFFF" stroke="{C["teal"][3]}" stroke-width="1.5"/>')
    b.add(f'  <text x="56" y="116" font-family="{FONT_SANS}" font-size="14" font-weight="700" fill="{C["ink"]}">24-Stunden-Zyklus · Namenskonvention: YYYY-MM-DD_0x_Schichtname (Jira-Sprint &amp; Git-Branch sprint/&lt;SCHICHT&gt;)</text>')
    b.add(f'  <text x="1544" y="116" text-anchor="end" font-family="{FONT_MONO}" font-size="11.5" font-weight="600" fill="{C["teal"][0]}">z. B. 2026-11-19_01_Früh</text>')

    bar_x, bar_y, bar_h = 56, 136, 48
    segs = [
        (bar_x, 620, "01_Früh · 05:00 – 15:00 (10 h)", "Tages-Hauptschicht · PO erreichbar", "teal"),
        (bar_x + 620, 434, "02_Spät · 15:00 – 22:00 (7 h)", "Nachmittags-/Abendschicht", "blue"),
        (bar_x + 1054, 434, "03_Nacht · 22:00 – 05:00 (7 h)", "Unbeaufsichtigte Nachtschicht", "indigo"),
    ]
    for sx, sw, lbl, sub, col in segs:
        dark, main, soft, bdr = C[col]
        b.add(f'  <g class="node" data-node="schicht_takt">')
        b.add(f'    <rect x="{sx}" y="{bar_y}" width="{sw-4}" height="{bar_h}" rx="8" fill="{soft}" stroke="{main}" stroke-width="1.8"/>')
        b.add(f'    <text x="{sx + sw/2:.1f}" y="{bar_y + 21}" text-anchor="middle" font-family="{FONT_SANS}" font-size="13.5" font-weight="800" fill="{dark}">{esc(lbl)}</text>')
        b.add(f'    <text x="{sx + sw/2:.1f}" y="{bar_y + 39}" text-anchor="middle" font-family="{FONT_SANS}" font-size="11.3" font-weight="500" fill="{C["body"]}">{esc(sub)}</text>')
        b.add('  </g>')

    pins = [
        (bar_x, "05:00", "Schichtwechsel"),
        (bar_x + 620, "15:00", "Schichtwechsel"),
        (bar_x + 1054, "22:00", "Schichtwechsel"),
        (bar_x + 1484, "05:00", "Nächster Tag"),
    ]
    for px, t_lbl, sub_lbl in pins:
        b.add(f'  <circle cx="{px}" cy="{bar_y + bar_h}" r="6" fill="{C["amber"][1]}" stroke="#FFFFFF" stroke-width="2"/>')
        anchor = "start" if px == bar_x else ("end" if px == bar_x + 1484 else "middle")
        b.add(f'  <text x="{px}" y="{bar_y + bar_h + 20}" text-anchor="{anchor}" font-family="{FONT_MONO}" font-size="12" font-weight="700" fill="{C["amber"][0]}">{t_lbl} · {sub_lbl}</text>')

    b.container(36, 244, 762, 458, "Phase 1: Abschluss der laufenden Schicht (Wrap-up & Integration)", color="emerald", subtitle="sprint/<CLOSING_SCHICHT>", node_id="schicht_phase1")
    p1_steps = [
        ("1. Merge-Freeze & WIP-Erhalt", "Stopp", [
            "Keine neuen Merges auf sprint/<ALT>.",
            "Offene feature/<KEY>-Branches bleiben für carryover-wip unberührt.",
        ], "slate", set()),
        ("2. Lean Tier-2 Integration Gate (Rule 7.5)", "1× / Schicht", [
            "./gradlew :app:assembleDebug :app:compileDebugAndroidTestKotlin",
            "  :app:testDebugUnitTest --rerun --tests <Sprint/build/int/loc>",
            "verify_requirement_governance.py · tools/test_*.py · Safety-Linter",
        ], "teal", {0, 1, 2}),
        ("3. Best-Effort Pixel 10 Deploy (Rule 7.6 & 8.6)", "Optional USB", [
            "ANDROID_SERIAL=<serial> ./gradlew installDebug -PstampGitHash=true",
            "Stempelt 5.0.0-dev-<sha> aufs Gerät; ohne USB sauber geloggt (blockiert nie).",
        ], "amber", {0}),
        ("4. Evidenz, Schichtbuch-Retro & Auto-Wochen-Rollup", "Rule 2.2 / 2.7", [
            "sprint-metrics --record --post-to-retro · export-gates · qa-audit",
            "post-weekly-rollup --retro-file Sprint_Review_and_Retro_<KEY>.md",
            "Spiegelt Scorecard, Erkenntnisse & Vorschläge ins Wochen Review & Retro.",
        ], "violet", {0, 1}),
        ("5. develop-Merge, Immutable Tag & PO-Archivierung", "Baseline", [
            "git merge --no-ff sprint/<ALT> → develop + baseline_manager.py tag",
            "archive-po-acceptance verschiebt Final Review (Human) → ID 453.",
        ], "emerald", {0, 1}),
    ]
    py = 290
    for title, badge, lines, col, monos in p1_steps:
        h = 74 if len(lines) == 2 else 82
        b.card(52, py, 730, h, title, lines, color=col, badge=badge, node_id="schicht_phase1", title_size=12.6, line_size=11.3, line_gap=15.5, mono_lines=monos)
        py += h + 8

    b.arrow(798, 474, 858, 474, color="amber", width=3.2)
    b.add(f'  <rect x="802" y="428" width="52" height="36" rx="6" fill="{C["amber"][2]}" stroke="{C["amber"][1]}" stroke-width="1.2"/>')
    b.add(f'  <text x="828" y="443" text-anchor="middle" font-family="{FONT_SANS}" font-size="10.2" font-weight="800" fill="{C["amber"][0]}">DIREKT</text>')
    b.add(f'  <text x="828" y="457" text-anchor="middle" font-family="{FONT_SANS}" font-size="9.8" font-weight="700" fill="{C["amber"][0]}">0 Pause</text>')

    b.container(862, 244, 702, 458, "Phase 2: Direkter Start der nächsten Schicht (Zero-Idle)", color="blue", subtitle="sprint/<NEW_SCHICHT>", node_id="schicht_phase2")
    p2_steps = [
        ("1. Neuen Schicht-Branch ab develop abzweigen", "Git", [
            "git checkout -b sprint/<NEW_SCHICHT> develop",
            "Startet immer auf dem frisch verifizierten, getaggten develop-Stand.",
        ], "emerald", {0}),
        ("2. Aktive WIP-Tickets direkt übernehmen", "Rule 2.3", [
            "jira_util.py --as coordinator carryover-wip <ALT> <NEU>",
            "In-Progress-Tickets & Stage-6-Bounces haben strikt Vorrang vor Zu erledigen.",
        ], "blue", {0}),
        ("3. Retro-, Wochen- & Dual-Vendor-Hygiene", "Automatik", [
            "ensure-retro · ensure-weekly-retro · ensure-weekly-reviews",
            "Legt fällige Dual-Vendor-Reviews (> 7 Tage) automatisch oben im Backlog an.",
        ], "violet", {0}),
        ("4. 3-Lane Greedy Bin-Packing aus ID 447", "Rule 9.1", [
            "pack-sprint <NEW_ID> --lanes 3 --max-sp-per-lane 8",
            "Füllt freie Lane-Kapazität strikt in ORDER BY rank ASC (disjunkte Epics).",
        ], "indigo", {0}),
        ("5. Ceremony-1 Risk Review & Weiterbetrieb", "Go Live", [
            "Ceremony-1-Eintrag in docs/management/risk_register.md protokollieren.",
            "3 Lanes laufen sofort an (oder saubere Pause, falls 447 & WIP leer sind).",
        ], "teal", set()),
    ]
    py = 290
    for title, badge, lines, col, monos in p2_steps:
        b.card(878, py, 670, 74, title, lines, color=col, badge=badge, node_id="schicht_phase2", title_size=12.6, line_size=11.3, line_gap=15.5, mono_lines=monos)
        py += 84

    b.card(36, 720, 494, 154, "Phase 3: Antrittsroutine bei frischem Kontext", [
        "**Single Source of Truth statt Chat-Verlauf:**",
        "1. .agents/rules/aspice_governance.md lesen (Rule 1.1)",
        "2. git status / branch + jira_util.py list prüfen",
        "3. Letzte Retro + risk_register.md + qa_audits/ prüfen",
        "• Übergaben nutzen IDs, Commits & Pfade — keine Kopien.",
    ], color="teal", badge="Anti-Context-Rot", node_id="antrittsroutine", line_size=11.4, line_gap=16)
    b.card(552, 720, 502, 154, "Autonome Retro Write-Allowlist (Rule 2.7)", [
        "**Erlaubt:** retros/, gates/, qa_audits/, reviews/,",
        "  test_evidence/, metrics/, risk_register.md (Append)",
        "**Verboten ohne Interactive-PO / Ticket:**",
        "  .agents/rules/**, .agents/skills/**, standards, tools/**",
        "**→ Regel-Wünsche** als Vorschlag via post-weekly-rollup!",
    ], color="rose", badge="REQ-PRO-117", node_id="write_allowlist", strong_border=True, line_size=11.4, line_gap=16)
    b.card(1076, 720, 488, 154, "Schichtführer-Grenzen & Eskalation", [
        "• Genau **ein Schichtführer** je sprint/*-Branch aktiv.",
        "• Keine Überschreibung von agent2-Verdicts möglich.",
        "• **Unattended Escalation (Rule 6.1):** Bei Unklarheit",
        "  'Question for Human:' + assign human → Lane läuft",
        "  sofort ohne Blockade mit dem nächsten Ticket weiter.",
    ], color="amber", badge="Rule 6.1", node_id="escalation", line_size=11.4, line_gap=16)

    return b.render()


# =============================================================================
# SLIDE 09: 6-STAGE-TICKET-LIFECYCLE, ZIPPER & STAGE 6 COLD REVIEW
# =============================================================================
def build_d09_ticket_lifecycle() -> str:
    b = SvgBuilder(1600, 900, "d09")
    b.header(
        "09 · TICKET-LIFECYCLE",
        "Deep-Dive: 6-Stage-Lifecycle, Zipper-Prinzip & Stage 6 Cold Review",
        "Getrennte Doing-/Review-Rollen (agent1 ⇄ agent2), Gate-3 Code Lock, Live-Emulator-Cold-Review auf feature/<KEY> & atomarer Post-PASS Squash",
        "blue",
    )

    b.pill(36, 210, 96, 44, "Zu erledigen", color="slate", filled=False, font_size=11.5, node_id="stages_1_5")
    b.add(f'  <text x="84" y="272" text-anchor="middle" font-family="{FONT_SANS}" font-size="10.5" font-weight="600" fill="{C["muted"]}">Just-in-Time</text>')
    b.add(f'  <text x="84" y="286" text-anchor="middle" font-family="{FONT_SANS}" font-size="10.5" font-weight="600" fill="{C["muted"]}">(Rule 2.4)</text>')
    b.arrow(132, 232, 156, 232, color="blue", width=2.2)

    b.container(158, 94, 910, 282, "Stages 1–5 auf feature/<KEY> · Ping-Pong-Paar (agent1 ⇄ agent2)", color="blue", subtitle="Flash (Routine) · Pro/Neon (komplex/grafisch)", node_id="stages_1_5")

    st_data = [
        ("S1 · Analysis", "Gate 1", ["**Doing (agent1):**", "• Root Cause & Scope", "• Chesterton's Fence", "analysis/<KEY>.md", "---", "**Gate 1 (agent2):**", "• Vollständigkeit"], "blue", {3}),
        ("S2 · Test Spec", "Gate 2", ["**Doing (agent1):**", "• REQ-* ↔ TST-*", "• 9-Sprachen-Parität", "test_specs/<KEY>.md", "---", "**Gate 2 (agent2):**", "• Traceability [Planned]"], "blue", {3}),
        ("S3 · Impl Plan", "LOCK G3", ["**Doing (agent1):**", "• Atomare Schritte", "• SWE.2 Architektur", "plans/<KEY>_plan.md", "---", "**CODE LOCK (Rule 4):**", "check-gate = 0 Pflicht!"], "amber", {3}),
        ("S4 · Impl", "Gate 4", ["**Doing (agent1):**", "• Code + Unit-Tests", "• Tier-1 --rerun", "Status [Implemented]", "---", "**Gate 4 (agent2):**", "• Code & Test-Evidenz"], "blue", {3}),
        ("S5 · Test", "Gate 5", ["**Doing (agent1):**", "• Walkthrough & Docs", "• 0 red. Gradle-Lauf", "Status [Verified]", "---", "**Gate 5 (agent2):**", "• feature/* bleibt intakt!"], "blue", {3}),
    ]
    sx = 172
    sw = 164
    for idx, (t, bg, lines, col, monos) in enumerate(st_data):
        strong = (idx == 2)
        b.card(sx, 140, sw, 200, t, lines, color=col, accent_bar=False, badge=bg, node_id="gate3_lock" if idx == 2 else "stages_1_5", bg=C["amber"][2] if strong else "#FFFFFF", title_size=11.6, line_size=10.8, line_gap=15.5, mono_lines=monos, strong_border=strong)
        if idx < 4:
            b.arrow(sx + sw + 1, 238, sx + sw + 14, 238, color=col, width=1.8)
        sx += sw + 15

    b.add(f'  <text x="174" y="362" font-family="{FONT_SANS}" font-size="11" font-weight="600" fill="{C["blue"][0]}">Gate REVISION → Nachbesserung im Sub-Task · Gate PASS → synchroner Parent-Wechsel (Rule 3.2)</text>')
    b.arrow(1068, 232, 1094, 232, color="rose", width=2.4)

    b.container(1096, 94, 298, 282, "Stage 6 · Final Review", color="rose", subtitle="Cold Reviewer (p₆)", node_id="stage6")
    b.card(1110, 140, 270, 222, "Zero-Context Audit", [
        "**Anti-Anchoring:** Zuerst Jira +",
        "git diff sprint/*...feature/<KEY>",
        "+ Quellcode (NICHT Walkthrough!).",
        "**Live-Verifikation (SWE.6/SYS.5):**",
        "• emulator-5554 + Simulator",
        "• logcat (0 Crashes/ANRs)",
        "• read_file Screenshot-Kritik",
        "**6-Pkt-Audit & Pflichtfragen:**",
        "Pros · Cons · Freigabe-Votum?",
        "**Cons ≠ leer → zwingend BOUNCE!**",
    ], color="rose", accent_bar=True, badge="Rule 3.3", node_id="stage6", title_size=12, line_size=11, line_gap=16.5, mono_lines={1}, strong_border=True)

    b.arrow(1394, 165, 1418, 165, color="emerald", width=2.2)
    b.card(1420, 104, 144, 106, "PASS → Merge", ["--merge-ticket", "1 Squash-Commit", "auf sprint/*", "feature/* weg"], color="emerald", accent_bar=False, node_id="stage6_pass", title_size=11.6, line_size=10.8, line_gap=15.5, mono_lines={0}, strong_border=True, bg=C["emerald"][2])
    b.arrow(1492, 210, 1492, 230, color="indigo", width=2.0)
    b.card(1420, 232, 144, 72, "Final Review", ["(Human) · PO", "→ Queue ID 453"], color="indigo", accent_bar=False, node_id="queue_453", title_size=11.6, line_size=10.8, line_gap=15, strong_border=True, bg=C["indigo"][2])
    b.arrow(1492, 304, 1492, 326, color="emerald", width=2.0)
    b.pill(1420, 328, 144, 42, "Erledigt (nur PO!)", color="emerald", filled=True, font_size=11.5, node_id="queue_453")

    b.path_arrow("M 1245 376 L 1245 418 L 792 418 L 792 343", color="rose", label="BOUNCE 1/3 – 3/3 zurück an S4 (Implementation) auf feature/<KEY> · Bounce 2–3 → Pro/Neon · >3 Bounces → Human", lx=1020, ly=438, width=2.3)

    b.container(36, 466, 504, 408, "Asymmetrisches Zipper-Prinzip & Rollen", color="indigo", subtitle="Rule 7.1 · 7.3 · 7.4", node_id="zipper")
    b.card(52, 512, 472, 114, "Strikte Trennung von Erstellung & Prüfung (REQ-PRO-107.1)", [
        "• **agent1 (Doing):** Führt Stages 1–5 aus via submit-stage",
        "• **agent2 (Gate Review):** Prüft Gates 1–5 (gate-verdict) & Stage 6",
        "• Eigene Credentials je Rolle (.env.jira.agent1 / agent2 / coordinator)",
        "• Fused Handoffs (Reviewer wird sofort nächster Autor) sind abgeschafft.",
    ], color="indigo", node_id="zipper", line_size=11.4, line_gap=16.5)
    b.card(52, 640, 472, 104, "Persistenz & 3-Ticket-Rotation gegen Context Rot", [
        "• Pro aktiver Epic-Lane arbeitet ein persistentes Subagent-Paar.",
        "• Nach je **3 abgeschlossenen Tickets** im selben Epic erfolgt",
        "  proaktive Rotation (Terminate + Respawn) gegen Kontext-Sättigung.",
        "• Defekte/Timeout-Subagenten werden sofort ersetzt (Rule 7.3).",
    ], color="teal", node_id="zipper", line_size=11.4, line_gap=16.5)
    b.card(52, 758, 472, 100, "Kompakte Artefakte & Synchrones CLI (Rule 8.1)", [
        "• Markdown-Deliverables strikt ≤ 60–80 Zeilen (kein Bloat).",
        "• Stage 4 postet direkt nach Jira (keine Extra-MD-Dateien).",
        "• jira_util.py druckt Ziel-SKILL.md + Regeln automatisch beim Wechsel.",
    ], color="slate", node_id="zipper", line_size=11.4, line_gap=16.5)

    b.container(560, 466, 512, 408, "Modell-Routing (Flash vs. Pro / Neon)", color="amber", subtitle="Rule 7.2 · Gezielte Eskalation", node_id="model_routing")
    b.card(576, 512, 480, 152, "Flash (Model: \"flash\") — Standard für Routine", [
        "• Alle regulären **Stages 1–5** Tickets (Standard-Features & Bugs)",
        "• **Routine Stage-6 Cold Reviews:** Low-Risk, 1–2 SP, Doku,",
        "  Contract-Tests, Tooling, Single-Component-Fixes",
        "• Hohe Geschwindigkeit auf allen 3 parallelen Lanes",
        "• Phase-2-Koordinator nutzt typischerweise Flash / Neon.",
    ], color="blue", badge="Routine", node_id="model_routing", line_size=11.5, line_gap=17)
    b.card(576, 680, 480, 178, "Pro / Gemini 4 Neon — Strikte Eskalation", [
        "Einsatz **ausschließlich** bei komplexen oder grafischen Aufgaben:",
        "1. **Komplex:** High-Risk, Multi-Component-Architektur,",
        "   Concurrency/Threading, SQLite/DB-Migrationen,",
        "   ANT+/BLE-Sensorprotokolle, **Stage 6 Bounce 2–3**",
        "2. **Grafisch:** UI/Compose, Canvas, Charts, Icons (Rule 8.5)",
        "Parameter: Model: \"inherit\" (Neon-Coord.) sonst \"pro\"",
    ], color="amber", badge="Komplex / Grafik", node_id="model_routing", strong_border=True, line_size=11.5, line_gap=16.8, mono_lines={5})

    b.container(1092, 466, 472, 408, "Visual Audit, Failure-Modes & Hygiene", color="rose", subtitle="Rule 3.3 · 6.1 · 8.3 · 8.5", node_id="visual_audit")
    b.card(1108, 512, 440, 134, "4-Punkte Visual Self-Inspection (Rule 8.5)", [
        "Pflicht-Prüfung per read_file durch agent1, agent2 & Stage 6:",
        "1. Orientierung & Geometrie (kein Status-Bar-Overlap)",
        "2. Farb-Mapping & Material-3 Track/Stop-Dot-Artefakte",
        "3. Typografie & deutsche String-Längen (kein 2-Zeilen-Bruch)",
        "4. Touch-Ergonomie & Finger-Verdeckung",
    ], color="rose", node_id="visual_audit", line_size=11.3, line_gap=16)
    b.card(1108, 660, 440, 110, "4 Real-Device Failure-Modes im Cold Review", [
        "• Keine self-cancelling rememberCoroutineScope() in Bannern",
        "• Upgrade-Pfad für bestehende SQLite/SharedPrefs (null-safe)",
        "• Sensor-Hysterese/Deadbands gegen 1–5-Hz-Farbflackern",
        "• Disconnected-Sensor Fast-Fail (kein 15–45 s UI-Freeze)",
    ], color="violet", node_id="visual_audit", line_size=11.3, line_gap=16)
    b.card(1108, 784, 440, 74, "Jira FixVersion-Hygiene (Rule 8.3)", [
        "Sub-Tasks erhalten nie eine FixVersion. Parent-Tickets erst",
        "bei finaler PO-Abnahme oder Release (Target: V5.0.0).",
    ], color="slate", node_id="visual_audit", line_size=11.3, line_gap=16)

    return b.render()


# =============================================================================
# SLIDE 10: 3-TIER-JIRA-QUEUES, 3-LANE-PARALLELISIERUNG & GIT-WORKTREES
# =============================================================================
def build_d10_jira_lanes_git() -> str:
    b = SvgBuilder(1600, 900, "d10")
    b.header(
        "10 · QUEUES, LANES & GIT",
        "Deep-Dive: 3-Tier-Jira-Queues, 3-Lane-Parallelisierung & Git-Worktrees",
        "Geschützte Human-Pseudo-Sprints (IDs 447 / 453 / 454), disjunkte Epic-Lanes mit POSIX flock & Cross-Worktree Gradle Cache, Git-Strategie A",
        "indigo",
    )

    b.container(36, 94, 456, 780, "1. 3-Tier-Jira-Queues & Human-Schutzzonen", color="indigo", subtitle="Rule 9.1 · 7.6", node_id="jira_queues")
    b.card(54, 144, 420, 86, "Tier 1 · Product Backlog", [
        "• Ungeplante Features, Ideen, Bugs & Follow-ups",
        "• Rule 5.1: Nach create-issue sofort stoppen (kein Eigenstart)",
        "• Kuratierung & Priorisierung ausschließlich durch den PO",
    ], color="slate", badge="PO-Vorrat", node_id="jira_queues", line_size=11.4, line_gap=16)
    b.arrow(264, 230, 264, 272, color="indigo", label="Nur PO darf freigeben (Agenten verboten)", lx=264, ly=255)
    b.card(54, 274, 420, 126, "Tier 2 · Vom PO Freigegeben", [
        "• Permanenter Future-Pseudo-Sprint (Position unter Schichten)",
        "• Strikte Reihenfolge: ORDER BY rank ASC (nie fixes top[:X]!)",
        "• human-execution Tickets werden automatisch übersprungen",
        "• Quelle für pack-sprint --lanes 3 --max-sp-per-lane 8",
        "• Agenten dürfen keine Backlog-Tickets hierher verschieben!",
    ], color="indigo", badge="ID 447", node_id="queue_447", strong_border=True, line_size=11.4, line_gap=16)
    b.arrow(264, 400, 264, 444, color="teal", label="pack-sprint (Greedy Bin-Packing · 3 disjunkte Epics)", lx=264, ly=426)
    b.card(54, 446, 420, 124, "Tier 3 · Aktive Schicht (YYYY-MM-DD_0x_Schicht)", [
        "• Übernimmt laufendes WIP via carryover-wip (Rule 2.3 Vorrang)",
        "• Füllt bis zu 3 disjunkte Epic-Lanes (≤ 8 SP / Lane)",
        "• Enthält pro Schicht genau 1 Schicht-Retro-Ticket",
        "• Übervolle Epics warten fair in ID 447 ohne andere zu blockieren",
        "• Stage-6-geprüfte Tickets warten hier bis Schichtwechsel",
    ], color="teal", badge="Aktiv", node_id="schicht_core", strong_border=True, line_size=11.4, line_gap=16)

    b.path_arrow("M 155 570 L 155 622", color="emerald", label="archive-po-acceptance", lx=155, ly=602)
    b.path_arrow("M 373 570 L 373 622", color="amber", label="defer-systemtest", lx=373, ly=602, dashed=True)
    b.card(54, 626, 202, 166, "PO Abnahme", ["• Stage-6 PASS Tickets", "• Final Review (Human)", "• Label sprint-<ID>", "• 0 künstl. WIP-Limit", "• Exklusive PO-Hoheit", "• Agenten-Zugriff tabu"], color="emerald", badge="ID 453", node_id="queue_453", strong_border=True, title_size=12, line_size=10.8, line_gap=15.5)
    b.card(272, 626, 202, 166, "PO Systemtest", ["• human-execution", "• SYS.5 Feld-/Outdoor-", "  & Hardware-Tests", "• Nie in autonome", "  Schichten ziehen!", "• Agenten: read-only"], color="amber", badge="ID 454", node_id="queue_454", strong_border=True, title_size=12, line_size=10.8, line_gap=15.5)
    b.add(f'  <rect x="54" y="806" width="420" height="52" rx="8" fill="{C["violet"][2]}" stroke="{C["violet"][3]}" stroke-width="1.2"/>')
    b.add(f'  <text x="264" y="827" text-anchor="middle" font-family="{FONT_SANS}" font-size="11.5" font-weight="700" fill="{C["violet"][0]}">Rollierendes Wochen Review &amp; Retro + Dual-Vendor Audits</text>')
    b.add(f'  <text x="264" y="845" text-anchor="middle" font-family="{FONT_SANS}" font-size="10.8" fill="{C["body"]}">Wird bei jedem Schichtwechsel automatisch via post-weekly-rollup gefüttert</text>')

    b.container(514, 94, 542, 780, "2. Hub-and-Spoke 3-Lane-Ausführung & Worktrees", color="teal", subtitle="Rule 7.2 · 7.8 · 9.2 · 9.3", node_id="lanes")
    b.card(534, 144, 502, 92, "Schichtführer / Coordinator (Hub)", [
        "• Reaktive Turn-Grenzen: Subagent-Dispatch als letzter Tool-Call (Rule 7.8)",
        "• Watchdog-Timer: exakt 1× schedule(1200s) je Dispatch (Rule 7.9)",
        "• Strenges Verbot von Peer-to-Peer-Kommunikation zwischen Subagenten",
    ], color="teal", badge="Hub-and-Spoke", node_id="coordinator", strong_border=True, line_size=11.4, line_gap=16)

    for lx in (614, 785, 956):
        b.arrow(lx, 236, lx, 268, color="teal", width=1.9)

    lane_specs = [
        ("Lane A", "ASPICE & Tooling", "ATT-8138", ["Workspace: share", "Detached HEAD", "feature/ATT-A", "---", "agent1 ⇄ agent2", "Flash / Pro*", "≤ 8 SP / Schicht"], "blue"),
        ("Lane B", "Produkt-Epic 1", "Disjunkt", ["Workspace: share", "Detached HEAD", "feature/ATT-B", "---", "agent1 ⇄ agent2", "Flash / Pro*", "≤ 8 SP / Schicht"], "blue"),
        ("Lane C", "Produkt-Epic 2", "Disjunkt", ["Workspace: share", "Detached HEAD", "feature/ATT-C", "---", "agent1 ⇄ agent2", "Flash / Pro*", "≤ 8 SP / Schicht"], "blue"),
    ]
    lx = 534
    for lt, lsub, lbdg, llines, lcol in lane_specs:
        b.card(lx, 270, 158, 214, f"{lt} · {lbdg}", [f"**{lsub}**"] + llines, color=lcol, accent_bar=False, node_id="lanes", title_size=11.8, line_size=11, line_gap=16.5, mono_lines={1, 3})
        lx += 172

    b.add(f'  <rect x="534" y="498" width="502" height="44" rx="8" fill="{C["blue"][2]}" stroke="{C["blue"][3]}" stroke-width="1.2"/>')
    b.add(f'  <text x="785" y="517" text-anchor="middle" font-family="{FONT_SANS}" font-size="11.5" font-weight="700" fill="{C["blue"][0]}">Konfliktfreiheits-Invariante: 0 Package-Overlap · 0 Living-Doc-Shard-Overlap</text>')
    b.add(f'  <text x="785" y="534" text-anchor="middle" font-family="{FONT_SANS}" font-size="10.8" fill="{C["body"]}">Domain-partitionierte Localization-Dateien: values*/strings_&lt;domain&gt;.xml (Rule 9.4)</text>')

    for lx in (614, 785, 956):
        b.arrow(lx, 542, lx, 574, color="amber", width=1.8)

    b.card(534, 576, 502, 138, "POSIX flock Serialisierung & Deterministischer Merge", [
        "Lock-Datei: /tmp/att_build_merge.lock (flock -w 600)",
        "• Serialisiert alle ./gradlew-Aufrufe, Emulator-Installs & Merges",
        "• parallel_sprint_lock.py --merge-ticket führt im Lock automatisch",
        "  verify_requirement_governance.py --sync-indexes aus (Rule 9.5)",
        "• Garantiert konfliktfreie Master-Index-Tabellen & 1 Commit/Ticket",
    ], color="amber", badge="Rule 8.6 / 9.3", node_id="flock_cache", strong_border=True, line_size=11.4, line_gap=16.5, mono_lines={0})

    b.card(534, 728, 502, 130, "Cross-Worktree Gradle Build Cache (FROM-CACHE)", [
        "org.gradle.caching=true · Xmx4096m · Kotlin Daemon Xmx3072m",
        "• Konstanter Debug versionNameSuffix (5.0.0-dev) ab Werk →",
        "  Stage 4, Gate 4 & Stage 6 Cold Worktrees treffen FROM-CACHE!",
        "• -PstampGitHash=true (5.0.0-dev-<sha>) strikt nur bei Pixel-10-Deploy",
        "• 30 min warm Daemon · maxParallelForks = 2 · --rerun (nie --rerun-tasks)",
    ], color="emerald", badge="Rule 8.6", node_id="flock_cache", line_size=11.3, line_gap=16, mono_lines={0})

    b.container(1078, 94, 486, 780, "3. Git-Branching-Topologie (Strategie A)", color="emerald", subtitle="feature → sprint → develop", node_id="git_flow")
    b.add(f'  <rect x="1097.5" y="146.5" width="450" height="546" rx="10" fill="#0F172A" fill-opacity="0.05"/>')
    b.add(f'  <rect x="1096" y="144" width="450" height="546" rx="10" fill="#FFFFFF" stroke="{C["border"]}" stroke-width="1.3"/>')

    b.pill(1110, 158, 92, 24, "develop", color="emerald", filled=True, font_size=11, mono=True)
    b.pill(1224, 158, 128, 24, "sprint/<SCHICHT>", color="teal", filled=True, font_size=10.5, mono=True)
    b.pill(1374, 158, 158, 24, "feature/<KEY> (Worktree)", color="blue", filled=True, font_size=10.5, mono=True)

    dev_x, spr_x, fea_x = 1156, 1288, 1444
    b.add(f'  <line x1="{dev_x}" y1="192" x2="{dev_x}" y2="668" stroke="{C["emerald"][1]}" stroke-width="4" stroke-linecap="round"/>')
    b.add(f'  <circle cx="{dev_x}" cy="210" r="7" fill="{C["emerald"][1]}" stroke="#FFFFFF" stroke-width="2.5"/>')
    b.add(f'  <text x="{dev_x-12}" y="214" text-anchor="end" font-family="{FONT_MONO}" font-size="10.5" font-weight="700" fill="{C["emerald"][0]}">D0</text>')

    b.add(f'  <path d="M {dev_x} 210 C {dev_x} 240, {spr_x} 225, {spr_x} 252 L {spr_x} 510 C {spr_x} 538, {dev_x} 525, {dev_x} 554" fill="none" stroke="{C["teal"][1]}" stroke-width="3.5"/>')
    b.add(f'  <rect x="{spr_x+10}" y="230" width="66" height="20" rx="5" fill="{C["teal"][2]}" stroke="{C["teal"][3]}"/>')
    b.add(f'  <text x="{spr_x+43}" y="244" text-anchor="middle" font-family="{FONT_MONO}" font-size="10" font-weight="700" fill="{C["teal"][0]}">01_Früh</text>')

    b.add(f'  <path d="M {spr_x} 264 C {spr_x} 284, {fea_x} 274, {fea_x} 296 L {fea_x} 352 C {fea_x} 374, {spr_x} 364, {spr_x} 384" fill="none" stroke="{C["blue"][1]}" stroke-width="2.8"/>')
    for cy, lbl in [(302, "S1–S4 Commits"), (332, "S5 Test (intakt)")]:
        b.add(f'  <circle cx="{fea_x}" cy="{cy}" r="5.5" fill="{C["blue"][1]}" stroke="#FFFFFF" stroke-width="2"/>')
        b.add(f'  <text x="{fea_x+12}" y="{cy+4}" font-family="{FONT_SANS}" font-size="10.3" fill="{C["body"]}">{lbl}</text>')
    b.add(f'  <circle cx="{fea_x}" cy="356" r="6" fill="{C["rose"][1]}" stroke="#FFFFFF" stroke-width="2"/>')
    b.add(f'  <text x="{fea_x+12}" y="360" font-family="{FONT_SANS}" font-size="10.3" font-weight="700" fill="{C["rose"][0]}">Stage 6 PASS</text>')
    b.add(f'  <circle cx="{spr_x}" cy="384" r="7" fill="{C["teal"][1]}" stroke="#FFFFFF" stroke-width="2.5"/>')
    b.add(f'  <rect x="{spr_x-106}" y="372" width="96" height="22" rx="5" fill="{C["teal"][2]}" stroke="{C["teal"][3]}"/>')
    b.add(f'  <text x="{spr_x-58}" y="386.5" text-anchor="middle" font-family="{FONT_MONO}" font-size="9.8" font-weight="700" fill="{C["teal"][0]}">1 Squash</text>')

    b.add(f'  <path d="M {spr_x} 404 C {spr_x} 420, {fea_x} 412, {fea_x} 430 L {fea_x} 480 C {fea_x} 498, {spr_x} 490, {spr_x} 504" fill="none" stroke="{C["rose"][1]}" stroke-width="2.8"/>')
    b.add(f'  <circle cx="{fea_x}" cy="442" r="5.5" fill="{C["rose"][1]}" stroke="#FFFFFF" stroke-width="2"/>')
    b.add(f'  <text x="{fea_x+12}" y="446" font-family="{FONT_SANS}" font-size="10.3" font-weight="700" fill="{C["rose"][0]}">S6 BOUNCE → Fix</text>')
    b.add(f'  <circle cx="{fea_x}" cy="472" r="5.5" fill="{C["emerald"][1]}" stroke="#FFFFFF" stroke-width="2"/>')
    b.add(f'  <text x="{fea_x+12}" y="476" font-family="{FONT_SANS}" font-size="10.3" font-weight="700" fill="{C["emerald"][0]}">Re-Audit PASS</text>')
    b.add(f'  <circle cx="{spr_x}" cy="504" r="7" fill="{C["teal"][1]}" stroke="#FFFFFF" stroke-width="2.5"/>')

    b.add(f'  <circle cx="{dev_x}" cy="554" r="8" fill="{C["emerald"][1]}" stroke="#FFFFFF" stroke-width="2.5"/>')
    b.add(f'  <rect x="{dev_x+16}" y="538" width="234" height="32" rx="6" fill="{C["emerald"][2]}" stroke="{C["emerald"][1]}" stroke-width="1.2"/>')
    b.add(f'  <text x="{dev_x+133}" y="552" text-anchor="middle" font-family="{FONT_MONO}" font-size="10.3" font-weight="700" fill="{C["emerald"][0]}">--no-ff Merge + Tag</text>')
    b.add(f'  <text x="{dev_x+133}" y="565" text-anchor="middle" font-family="{FONT_SANS}" font-size="9.8" fill="{C["body"]}">Lean Tier-2 ✓ · Retro ✓ · Archive → 453</text>')

    b.add(f'  <path d="M {dev_x} 574 C {dev_x} 598, {spr_x} 588, {spr_x} 612 L {spr_x} 668" fill="none" stroke="{C["teal"][1]}" stroke-width="3.5" stroke-dasharray="7,4"/>')
    b.add(f'  <circle cx="{spr_x}" cy="620" r="6.5" fill="{C["teal"][1]}" stroke="#FFFFFF" stroke-width="2"/>')
    b.add(f'  <text x="{spr_x+14}" y="618" font-family="{FONT_SANS}" font-size="11" font-weight="700" fill="{C["teal"][0]}">Direkter Start: 02_Spät</text>')
    b.add(f'  <text x="{spr_x+14}" y="633" font-family="{FONT_SANS}" font-size="10.2" fill="{C["body"]}">carryover-wip + pack-sprint</text>')

    b.add(f'  <circle cx="{dev_x}" cy="656" r="6.5" fill="{C["violet"][1]}" stroke="#FFFFFF" stroke-width="2"/>')
    b.add(f'  <text x="{dev_x+14}" y="660" font-family="{FONT_MONO}" font-size="10.2" font-weight="700" fill="{C["violet"][0]}">release/V5.0.0</text>')

    b.card(1096, 706, 450, 152, "Git-, Hook- & Submodul-Hygiene (SUP.9 / Rule 9.2)", [
        "• .githooks/commit-msg erzwingt ATT-\\d+ Referenz je Commit",
        "• .githooks/pre-commit & CI blockieren Git-Konfliktmarker",
        "• feature/<KEY> wird nach Squash gelöscht (git branch -D)",
        "• Keine Symlinks auf ANT-Android-SDKs; kein git add -A darauf",
        "• Ungeprüfte/gebouncte Arbeit landet niemals auf sprint/*",
    ], color="slate", badge="REQ-PRO-121", node_id="git_flow", line_size=11.3, line_gap=16.5)

    return b.render()


# =============================================================================
# SLIDE 11: SWISS CHEESE, 3-STUFEN-TESTPYRAMIDE & GERÄTEPOLITIK
# =============================================================================
def build_d11_swiss_cheese_tests() -> str:
    b = SvgBuilder(1600, 900, "d11")
    b.header(
        "11 · QUALITÄT & TESTS",
        "Deep-Dive: Swiss-Cheese-Abwehr, 3-Stufen-Testpyramide & Gerätepolitik",
        "Statistisch dekorrelierte Qualitätsschichten (p₁·…·p₅), zweistufige Emulations-/Hardware-Policy & 100 % Living-Docs-Parität (Rule 3.5, 7.5, 7.6, 8.9)",
        "violet",
    )

    b.container(36, 94, 746, 780, "5-Schichten Swiss-Cheese-Verteidigungsmodell", color="violet", subtitle="P(Escape) = p₁ · p₂ · p₃ · p₄ · p₅", node_id="swiss_cheese")
    b.add(f'  <rect x="54" y="140" width="710" height="88" rx="10" fill="#FFFFFF" stroke="{C["violet"][3]}" stroke-width="1.2"/>')
    b.add(f'  <text x="68" y="176" font-family="{FONT_SANS}" font-size="11.5" font-weight="700" fill="{C["rose"][0]}">Defekt /</text>')
    b.add(f'  <text x="68" y="191" font-family="{FONT_SANS}" font-size="11.5" font-weight="700" fill="{C["rose"][0]}">Blindspot</text>')
    b.add(f'  <line x1="130" y1="184" x2="424" y2="184" stroke="{C["rose"][1]}" stroke-width="2.6" stroke-dasharray="5,3"/>')
    b.add(f'  <circle cx="428" cy="184" r="5.5" fill="{C["rose"][1]}"/>')

    for sx, lbl, col, hole_y in [(195, "p₁", "blue", 184), (315, "p₂", "amber", 184), (445, "p₃", "rose", 162), (565, "p₄", "emerald", 196), (685, "p₅", "violet", 164)]:
        dark, main, soft, bdr = C[col]
        b.add(f'  <rect x="{sx-16}" y="150" width="32" height="54" rx="5" fill="{soft}" stroke="{main}" stroke-width="2"/>')
        b.add(f'  <circle cx="{sx}" cy="{hole_y}" r="6.5" fill="#FFFFFF" stroke="{main}" stroke-width="1.4"/>')
        b.add(f'  <text x="{sx}" y="220" text-anchor="middle" font-family="{FONT_SANS}" font-size="11.5" font-weight="800" fill="{dark}">{lbl}</text>')

    b.add(f'  <rect x="336" y="146" width="84" height="19" rx="5" fill="{C["rose"][1]}"/>')
    b.add(f'  <text x="378" y="159" text-anchor="middle" font-family="{FONT_SANS}" font-size="9.8" font-weight="800" fill="#FFFFFF">ABGEFANGEN!</text>')

    layers = [
        ("Schicht 1 (p₁) · Executing Agent (agent1)", "Sub-Task", ["Bounded Stage-Skill, Kotlin-Compiler, inkrementelle Tier-1 Unit-Tests (--rerun),", "4-Punkte Visual Self-Check (Rule 8.5) & Living-Doc-Statuspflege."], "blue"),
        ("Schicht 2 (p₂) · Unabhängiger Gate Reviewer (agent2)", "Gates 1–5", ["Eigene Jira-Rolle (.env.jira.agent2), kein Self-Review (REQ-PRO-107.1),", "Gate-3 Code Lock, Chesterton's Fence & Requirement-Governance-Prüfung."], "amber"),
        ("Schicht 3 (p₃) · Stage 6 Cold Final Review", "Zero-Context", ["Frischer Subagent ohne Stage-1–5-Kontext · Anti-Anchoring (Diff zuerst!),", "Live-Verifikation auf emulator-5554 + Simulator + Screenshot-Kritik · 3 Bounces."], "rose"),
        ("Schicht 4 (p₄) · Schichtwechsel & Human Decision Gate", "PO-Hoheit", ["Lean Tier-2 Integration Gate, Best-Effort Pixel-10-Deploy (5.0.0-dev-<sha>),", "exklusive PO-Abnahme (ID 453) & physische Feld-/Systemtests (ID 454)."], "emerald"),
        ("Schicht 5 (p₅) · Weekly Dual-Vendor Cross-Audits (Rule 3.5)", "Gemini + Claude", ["Wöchentliche Doppelprüfung durch Google Gemini & Anthropic Claude:", "ASPICE 4.0 Process Cross-Audit + Holistic Subsystem Code & Architecture Review."], "violet"),
    ]
    ly = 240
    for title, badge, lines, col in layers:
        b.card(54, ly, 710, 92, title, lines, color=col, badge=badge, node_id="swiss_cheese", title_size=12.5, line_size=11.4, line_gap=16.5)
        ly += 100

    b.card(54, 748, 710, 110, "SUP.9 Problem-Resolution & Escape-Tracking (REQ-PRO-121)", [
        "• **4-Stufen Severity:** Critical (Release-Blocker) · Major (Sprint-Blocker) · Minor · Cosmetic",
        "• **Crash/ANR-Invariante:** niemals auf Minor/Cosmetic herabstufen",
        "• Gate-REVISION & CI-Fehler erzeugen deduplizierte process-escape Bugs (jira_util.py)",
    ], color="slate", badge="ASPICE SUP.9", node_id="sup9", title_size=12.2, line_size=11.3, line_gap=16.5)

    b.container(804, 94, 760, 418, "3-Stufen-Test- & Verifikations-Pyramide (Rule 7.5)", color="blue", subtitle="Schnell im Ticket · Tief im Takt", node_id="test_pyramid")
    b.add(f'  <g class="node" data-node="test_tier3"><polygon points="1034,144 1334,144 1396,254 972,254" fill="{C["violet"][2]}" stroke="{C["violet"][1]}" stroke-width="2"/><text x="1184" y="170" text-anchor="middle" font-family="{FONT_SANS}" font-size="13" font-weight="800" fill="{C["violet"][0]}">Tier 3 · Wöchentlich &amp; Release</text><text x="1184" y="191" text-anchor="middle" font-family="{FONT_MONO}" font-size="11" font-weight="600" fill="{C["violet"][0]}">Voll-Suite · lint · detekt · kover</text><text x="1184" y="211" text-anchor="middle" font-family="{FONT_SANS}" font-size="11" fill="{C["body"]}">Dual-Vendor Reviews (Gemini + Claude)</text><text x="1184" y="229" text-anchor="middle" font-family="{FONT_SANS}" font-size="10.8" fill="{C["body"]}">kover nur als Szenario-Lücken-Diagnose!</text></g>')
    b.add(f'  <g class="node" data-node="test_tier2"><polygon points="962,264 1406,264 1474,376 894,376" fill="{C["teal"][2]}" stroke="{C["teal"][1]}" stroke-width="2"/><text x="1184" y="290" text-anchor="middle" font-family="{FONT_SANS}" font-size="13" font-weight="800" fill="{C["teal"][0]}">Lean Tier 2 · Pro Schichtwechsel (05:00 / 15:00 / 22:00)</text><text x="1184" y="311" text-anchor="middle" font-family="{FONT_MONO}" font-size="10.8" font-weight="600" fill="{C["teal"][0]}">assembleDebug · compileDebugAndroidTestKotlin · --rerun</text><text x="1184" y="331" text-anchor="middle" font-family="{FONT_SANS}" font-size="11.2" fill="{C["body"]}">Sprint-Touched + build.* (Contracts) + integration.* + localization.*</text><text x="1184" y="350" text-anchor="middle" font-family="{FONT_SANS}" font-size="11.2" fill="{C["body"]}">verify_requirement_governance.py · Python tools/test_*.py · Safety-Linter</text></g>')
    b.add(f'  <g class="node" data-node="test_tier1"><polygon points="884,386 1484,386 1546,496 822,496" fill="{C["blue"][2]}" stroke="{C["blue"][1]}" stroke-width="2"/><text x="1184" y="413" text-anchor="middle" font-family="{FONT_SANS}" font-size="13" font-weight="800" fill="{C["blue"][0]}">Tier 1 · Pro Ticket in Stage 4 / Gate 4 / Stage 6 (~15–60 s)</text><text x="1184" y="434" text-anchor="middle" font-family="{FONT_MONO}" font-size="11" font-weight="600" fill="{C["blue"][0]}">./gradlew :app:testDebugUnitTest --rerun --tests &lt;TicketSuites&gt;</text><text x="1184" y="455" text-anchor="middle" font-family="{FONT_SANS}" font-size="11.2" fill="{C["body"]}">Cross-Worktree FROM-CACHE · NIEMALS --rerun-tasks (spart 6–8 min/Lauf!)</text><text x="1184" y="474" text-anchor="middle" font-family="{FONT_SANS}" font-size="11.2" fill="{C["body"]}">Stage 5 / Gate 5: Null redundante Gradle-Läufe bei unverändertem Code-SHA</text></g>')

    b.container(804, 532, 760, 342, "2-Stufen-Gerätepolitik (Rule 7.6) & Living-Docs-Parität", color="emerald", subtitle="Emuliert vs. Physisch · Traceability", node_id="device_policy")
    b.card(822, 580, 354, 152, "Tier A · Emulator (Agenten)", [
        "• emulator-5554 (Pixel_8_Android_15)",
        "• Exklusiv für Stages 1–6 (agent_emulator.py)",
        "• Sensor-Simulatoren (BLE Radar, Mock-GPS)",
        "• Deterministische UI-Taps via uiautomator",
        "• Live screencap → read_file Prüfung",
    ], color="blue", badge="Phase 2 & S6", node_id="device_policy", title_size=12.2, line_size=11.2, line_gap=16.5)
    b.card(1192, 580, 354, 152, "Tier B · Pixel 10 (PO-Gerät)", [
        "• **Agenten-Schreibverbot in Phase 2!**",
        "• Rule 7.7: Nie uninstall / pm clear ohne",
        "  PO-Bestätigung & .attbackup-Sicherung",
        "• Schichtwechsel: Best-Effort 5.0.0-dev-<sha>",
        "• Exklusive PO-Feldtests → Queue ID 454",
    ], color="amber", badge="Schicht / PO", node_id="device_policy", title_size=12.2, line_size=11.2, line_gap=16.5)
    b.card(822, 746, 724, 112, "Evidenzbasierte Living-Docs-Parität & Code-Grounding (Rule 8.7 – 8.9)", [
        "• **Bidirektionale Traceability:** SYSREQ-* (SYS.2) ↔ REQ-* (16 Shards) ↔ TST-* (SWE.4–6)",
        "• **Exakte 4-Spalten-Parität:** Total = [Verified] + [Implemented] + [Planned] (--sync-indexes)",
        "• Kontinuierliche SWE.2 Architektur-Pflege in docs/architecture.md · Verifiziert via AspiceSweAuditContractTest",
    ], color="emerald", badge="100 % Parität", node_id="living_docs", title_size=12.2, line_size=11.2, line_gap=16.5)

    return b.render()


# =============================================================================
# INTERACTIVE INSPECTOR METADATA
# =============================================================================
NODE_DETAILS = {
    "evolution_timeline": {
        "title": "Unsere 5-Monats-Evolution (Juni – November 2026)",
        "tag": "Storytelling · 6 Meilensteine",
        "summary": "Jeder Entwicklungssprung entstand nicht am Reißbrett, sondern als direkte Antwort auf echte Reibungsverluste in 80 Sprint-Retrospektiven.",
        "bullets": [
            "Stufe 1 (Vibe-Coding): Direkter Chat → Code, schnelles Lernen, aber stille Regressionen.",
            "Stufe 2 (Augenmaß): Erste Zweitmeinung mehrerer Agenten — besser, aber noch nach Bauchgefühl.",
            "Stufe 3 (Sorgfältig, Jun–Aug): Erste requirements.md (19.06.), Jira (21.06.), 80→204 REQs, manuell gesteuert.",
            "Stufe 4a (Höchstmaß, Sep): Dual-Agent Sub-Tasks, Gate-3 Code Lock, Chesterton's Fence, erster sprint/*-Branch.",
            "Stufe 4b (Skalierung, Okt–15.11.): Document Triad (84+→9 Core Rules), Worktrees+flock, Emulator & Stage 6 Cold Review.",
            "Stufe 5 (Schichtbetrieb, ab 17.11.): 24/7 3-Schicht-Takt, 3 Lanes, Live-S6, FROM-CACHE & Dual-Vendor Audits.",
        ],
        "refs": ["docs/engineering/rule_history_archive.md", "docs/management/schichtbetrieb.md"],
    },
    "evolution_metrics": {
        "title": "Empirische Wachstumskurve (Git-verifiziert)",
        "tag": "Daten · Stand 19.11.2026",
        "summary": "Die formalen Anforderungen sind von 80 auf 1.265 Living Requirements (1.286 eindeutige IDs) gewachsen — bei über 81 % verifizierter Testdeckung.",
        "bullets": [
            "Living Requirements: 1.265 über 16 Domain-Shards (1.023 [Verified]).",
            "Living Tests: 1.272 über 12 Domain-Shards (1.058 [Verified]).",
            "Prozess-Evidenz: 80 Sprint-Retrospektiven, 77 unveränderliche sprint/*-Tags, 1.226 Stage-1-Analysen.",
            "Commit-Aktivität seit Juni 2026: ~4.700 Commits (Höhepunkt: 2.077 Commits im Oktober 2026).",
        ],
        "refs": ["docs/requirements.md", "docs/tests.md", "docs/engineering/metrics/process_metrics.jsonl"],
    },
    "evolution_lessons": {
        "title": "Der rote Faden: Schmerz → Prozess-Antwort",
        "tag": "Storytelling-Kern",
        "summary": "Diese 4-Schritte-Dramaturgie erklärt sofort, warum unser Modell so aussieht wie heute.",
        "bullets": [
            "1. Voreiliges Coden → Gate-3 Code Lock + Chesterton's Fence + Zipper (agent1 ⇄ agent2).",
            "2. 84+ lose Regeln & Context Rot → Document Triad (9 Core Rules) + Artefakte + 3-Ticket-Rotation.",
            "3. Betriebsblindheit im selben Chat → Stage 6 Zero-Context Cold Review (Live-Emulator) + Weekly Dual-Vendor (Gemini + Claude).",
            "4. Wartezeiten & Build-Staus → 3 Schichten (05/15/22), 3 Worktree-Lanes, POSIX flock, FROM-CACHE, Lean Tier-2 & Human-Queues (447/453/454).",
        ],
        "refs": [".agents/rules/aspice_governance.md", "docs/project_protocol.md"],
    },
    "phase1_vibe": {
        "title": "Stufe 1: Vibe-Coding — Einordnung",
        "tag": "Folie 01 · Ad-hoc",
        "summary": "Maximale Geschwindigkeit beim ersten Entwurf, aber ohne Gedächtnis, ohne Zweitmeinung und ohne Regressionsschutz.",
        "bullets": [
            "Pro: Sofortiger Start, null Prozess-Overhead, perfekt für Wegwerf-Prototypen.",
            "Con: Stille Regressionen, Selbstbestätigungs-Blindspot, Context Amnesia nach Chat-Ende.",
            "Wendepunkt: Sobald die Codebase wächst, zerstört blinder Fortschritt mehr als er baut → Wunsch nach einer Zweitmeinung (Stufe 2).",
        ],
        "refs": ["docs/management/schichtbetrieb.md (Abschnitt 0)"],
    },
    "phase2_augenmass": {
        "title": "Stufe 2: Augenmaß — Zweitmeinung ohne Ballast",
        "tag": "Folie 02 · Vier-Augen-Prinzip",
        "summary": "Der erste echte Qualitätssprung: Ein zweiter, unabhängiger Agent kritisiert den Code des ersten — gesteuert vom Menschen nach Gefühl.",
        "bullets": [
            "Pro: Bricht die Ja-Sager-Spirale, fängt grobe Logikfehler bei minimalem Aufwand ab.",
            "Con: Abhängig von Bauchgefühl, kein formaler Soll-Ist-Abgleich, hoher manueller Copy-Paste-Aufwand.",
            "Wendepunkt: Ohne niedergeschriebene Requirements & Tickets verliert man bei wachsender App den Überblick → Stufe 3.",
        ],
        "refs": ["docs/management/schichtbetrieb.md (Abschnitt 0)"],
    },
    "phase3_sorgfaeltig": {
        "title": "Stufe 3: Sorgfältig — Ticket-für-Ticket (Jun – Aug 2026)",
        "tag": "Folie 03 · Erste REQs & Jira",
        "summary": "Mit Jira-Tasks, erster requirements.md (19.06.2026) und Unit-Tests entsteht ein solides professionelles Handwerk — aber 1:1 an menschliche Präsenz gebunden.",
        "bullets": [
            "Pro: Saubere Einzelergebnisse, erste Wissensbasis im Repo (80 → 204 REQs), geringe Prozess-Reibung.",
            "Con: Agenten coden noch voreilig los; Mensch ist der Durchsatz-Flaschenhals; Docs & Tests driften ohne Verifier.",
            "Wendepunkt: Für große Backlogs und stabile Altlogik braucht es harte Pre-Code-Gates & autonome Sprints → Stufe 4a.",
        ],
        "refs": ["docs/management/schichtbetrieb.md (Abschnitt 0)", "docs/requirements.md"],
    },
    "phase4a_hoechstmass": {
        "title": "Stufe 4a: Höchstmaß — Geburt von AAA (Sep 2026)",
        "tag": "Folie 04 · Stolpersteine & Gates",
        "summary": "Im September 2026 entsteht Agile Agentic ASPICE: 5-Stage-Kaskade, Gate-3 Code Lock, Zipper (agent1 ⇄ agent2), Chesterton's Fence und Strategy-A Sprint-Branches.",
        "bullets": [
            "Pro: 3× mehr Commits (1.055 im Sept.), kein voreiliges Coden mehr, Artefakt-Langzeitgedächtnis.",
            "Con: Regel-Wildwuchs (84+ Regeln), sequenzielle 1-Lane-Ausführung, Self-Anchoring im selben Pair.",
            "Wendepunkt: Hoher Durchsatz im Oktober (2.077 Commits) erfordert Konsolidierung, Parallelisierung & unabhängigen Cold Review → Stufe 4b.",
        ],
        "refs": ["docs/project_protocol.md", "docs/engineering/rule_history_archive.md"],
    },
    "phase4b_skalierung": {
        "title": "Stufe 4b: Industrialisierung — Triad, Worktrees & Stage 6 (Okt – 15.11.)",
        "tag": "Folie 05 · Skalierung & Barriere p₆",
        "summary": "Verdichtung auf 9 Core Rules (Document Triad), parallele Worktree-Lanes + POSIX flock, dedizierter emulator-5554, Stage 6 Cold Review & getrennte Rollen-Tokens.",
        "bullets": [
            "Pro: Massiv weniger Defect Escapes bis zum PO, echte Parallelität, 5.947 grüne Tests, 1.116 REQs.",
            "Con: --rerun-tasks & pauschales Pro-Routing kosteten Zeit/Budget; Leerlauf zwischen Sprints; statisches Stage-6-Diff übersah Runtime-UI-Bugs.",
            "Wendepunkt: Durchgehendes 24/7-Schichtmodell mit Live-Emulator Stage 6, FROM-CACHE & Dual-Vendor-Audits → Stufe 5.",
        ],
        "refs": [".agents/skills/stage6-final-review/SKILL.md", "tools/parallel_sprint_lock.py"],
    },
    "phase5_schichtbetrieb": {
        "title": "Stufe 5: Schichtbetrieb nach Höchstmaß (ab 17.11.2026 — AKTIV)",
        "tag": "Folie 06 · Aktueller Stand",
        "summary": "Volle Höchstmaß-Qualität im nahtlosen 24/7-3-Schicht-Takt auf 3 Lanes — wirtschaftlich optimiert und durch Dual-Vendor-Audits abgesichert.",
        "bullets": [
            "Pro: 24/7-Kontinuität ohne Leerlauf, Live-Emulator S6 + Post-PASS Squash, FROM-CACHE (~15–60 s), geschützte PO-Queues (447/453/454), Gemini+Claude Audits.",
            "Con (ehrlich): Hoher Tooling-/Governance-Aufbau, überdimensioniert für Einmal-Skripte, laufende Token-Kosten & PO-Disziplin bei 447/453 nötig.",
            "Deep-Dives: Folien 07–11 erklären Big Picture, Schichtwechsel, 6-Stage-Lifecycle, Jira/Git-Architektur & Test-Pyramide im Detail.",
        ],
        "refs": ["docs/management/schichtbetrieb.md (v1.0.3)", ".agents/rules/aspice_governance.md (v1.0.5)"],
    },
    "stufen": {"title": "Die 5 Arbeitsstufen der KI-Zusammenarbeit", "tag": "DOC-MGT-SCHICHT", "summary": "Schichtbetrieb nach Höchstmaß ist die 5. Ausbaustufe — Folien 01–06 beleuchten jede Stufe mit Schema, Pros & Cons.", "bullets": ["Stufe 1: Vibe-Coding", "Stufe 2: Augenmaß", "Stufe 3: Sorgfältig", "Stufe 4: Höchstmaß", "Stufe 5: Schichtbetrieb nach Höchstmaß (AKTIV)"], "refs": ["docs/management/schichtbetrieb.md"]},
    "po_input": {"title": "PO-Steuerung & Product Backlog", "tag": "Human-Governance", "summary": "Der menschliche Product Owner behält volle Hoheit über Priorisierung und Freigabe.", "bullets": ["Nach create-issue sofort stoppen (Rule 5.1).", "Nur der PO verschiebt Tickets in ID 447 (Rule 9.1)."], "refs": [".agents/rules/aspice_governance.md"]},
    "queue_447": {"title": "Vom PO Freigegeben (ID 447)", "tag": "Rule 9.1", "summary": "Geschützter Future-Pseudo-Sprint als alleinige Eingangs-Queue in ORDER BY rank ASC.", "bullets": ["human-execution automatisch übersprungen.", "Agenten dürfen nicht aus dem Backlog nach 447 schieben.", "pack-sprint --lanes 3 --max-sp-per-lane 8."], "refs": [".agents/rules/aspice_governance.md (Rule 9.1)"]},
    "coordinator": {"title": "Schichtführer / Coordinator (Hub)", "tag": "Rule 2.2 · 7.8 · 7.9", "summary": "Genau ein Schichtführer pro sprint/*-Branch im Hub-and-Spoke-Verfahren.", "bullets": ["Watchdog 1200s pro Dispatch, kein Peer-to-Peer.", "Single Source of Truth statt Chat-Verlauf."], "refs": ["docs/management/schichtbetrieb.md"]},
    "schicht_core": {"title": "Autonomer 24/7-Schichtbetrieb", "tag": "Rule 2.2", "summary": "3 PO-ausgerichtete Schichten (01_Früh, 02_Spät, 03_Nacht) auf 3 disjunkten Lanes.", "bullets": ["Direkter Schichtwechsel ohne Leerlauf.", "Post-PASS Squash erst nach Stage 6."], "refs": ["docs/management/schichtbetrieb.md"]},
    "schicht_takt": {"title": "3-Schicht-Kadenz (05:00 / 15:00 / 22:00)", "tag": "Rule 2.2", "summary": "01_Früh (10h), 02_Spät (7h), 03_Nacht (7h) — Namenskonvention YYYY-MM-DD_0x_Schichtname.", "bullets": ["Begrenzt Context Rot durch feste Integrationspunkte.", "Jede Schicht endet mit --no-ff Merge & Tag auf develop."], "refs": ["docs/management/schichtbetrieb.md"]},
    "schicht_phase1": {"title": "Schichtwechsel Phase 1: Wrap-up", "tag": "Rule 2.2 · 7.5", "summary": "Merge-Freeze → Lean Tier-2 Gate → Best-Effort Pixel 10 Deploy → Retro & Auto-Wochen-Rollup → develop Merge & Archive nach 453.", "bullets": ["Offene feature/* bleiben intakt für carryover-wip.", "post-weekly-rollup spiegelt Erkenntnisse ins Wochen Review & Retro."], "refs": ["docs/management/schichtbetrieb.md"]},
    "schicht_phase2": {"title": "Schichtwechsel Phase 2: Direkt-Start", "tag": "Rule 2.2 · Zero-Idle", "summary": "Neuer Branch ab develop → carryover-wip → ensure-retro/weekly → pack-sprint --lanes 3 → Risiko-Log → sofort weiter.", "bullets": ["WIP & Stage-6-Bounces haben strikt Vorrang vor Zu erledigen (Rule 2.3)."], "refs": ["docs/management/schichtbetrieb.md"]},
    "schichtwechsel": {"title": "Schichtwechsel-Protokoll", "tag": "Rule 2.2 & 2.7", "summary": "Verbindet kontrollierte Integration mit nahtlosem Start der Folgeschicht.", "bullets": ["0 Minuten Leerlauf.", "Automatischer Wochen-Rollup."], "refs": ["docs/management/schichtbetrieb.md"]},
    "antrittsroutine": {"title": "Phase 3: Antrittsroutine", "tag": "Rule 1.1 · 2.6", "summary": "Frischer Schichtführer liest Governance, Git, Jira, letzte Retro & Risk Register statt altem Chat.", "bullets": ["Verhindert Context Rot zuverlässig."], "refs": ["docs/management/schichtbetrieb.md"]},
    "write_allowlist": {"title": "Autonome Retro Write-Allowlist", "tag": "Rule 2.7 · REQ-PRO-117", "summary": "Autonome Abschlüsse dürfen nur Evidenz/Logs schreiben — niemals Regeln, Skills oder Tooling.", "bullets": ["Regel-Vorschläge laufen strukturiert über post-weekly-rollup ins Wochen Review & Retro."], "refs": [".agents/rules/aspice_governance.md (Rule 2.7)"]},
    "escalation": {"title": "Unattended Escalation", "tag": "Rule 6.1", "summary": "Bei Unklarheit 'Question for Human:' kommentieren, an human zuweisen & nächstes Ticket ziehen.", "bullets": ["Blockiert niemals die Schicht oder Konsole."], "refs": [".agents/rules/aspice_governance.md (Rule 6.1)"]},
    "lanes": {"title": "3 Parallele Disjunkte Epic-Lanes", "tag": "Rule 7.2 · 9.1 · 9.2", "summary": "1× ASPICE/Tooling (ATT-8138) + 2× Produkt-Epics in isolierten Workspace:'share' Worktrees.", "bullets": ["0 Package-Overlap, 0 Living-Doc-Shard-Overlap, max. 8 SP/Lane."], "refs": [".agents/rules/aspice_governance.md"]},
    "stages_1_5": {"title": "Stages 1–5 (Analysis bis Verification)", "tag": "ASPICE SWE.1–SWE.6", "summary": "5 Sub-Task-Stages mit jeweils eigenem Artefakt & unabhängigem agent2-Gate.", "bullets": ["Gate PASS rückt Parent-Ticket synchron weiter (Rule 3.2).", "Stage 5 spart redundanten Gradle-Lauf bei unverändertem Code-SHA."], "refs": [".agents/skills/stage1-analysis/SKILL.md"]},
    "gate3_lock": {"title": "Gate 3: Code Lock", "tag": "Rule 4.1", "summary": "Vor Produktionscode in app/src/ muss check-gate Exit-Code 0 (GATE_PASSED) liefern.", "bullets": ["Verhindert ungeplantes Draufloscoden."], "refs": [".agents/rules/aspice_governance.md (Rule 4.1)"]},
    "stage6": {"title": "Stage 6: Cold Final Review (Agent)", "tag": "Rule 3.3 · 3.4", "summary": "Zero-Context Adversarial Review inkl. Live-Emulator, Simulator & read_file Screenshot-Audit.", "bullets": ["Anti-Anchoring: Diff & Code zuerst, nie Walkthrough!", "Cons ≠ leer → zwingend BOUNCE (max. 3× auf feature/*)."], "refs": [".agents/skills/stage6-final-review/SKILL.md"]},
    "stage6_pass": {"title": "Post-PASS Atomarer Squash-Merge", "tag": "Rule 8.2 · 9.5", "summary": "Erst nach Stage-6 PASS wird feature/<KEY> als 1 Commit auf sprint/* gesquasht.", "bullets": ["--merge-ticket führt --sync-indexes im POSIX flock aus."], "refs": [".agents/rules/aspice_governance.md (Rule 8.2)"]},
    "zipper": {"title": "Asymmetrisches Zipper-Prinzip", "tag": "Rule 7.1 · 7.3 · 7.4", "summary": "Getrennte Rollen agent1 (Doing) ⇄ agent2 (Gate Review) mit eigenen Jira-Tokens & 3-Ticket-Rotation.", "bullets": ["Kein Self-Review (REQ-PRO-107.1), keine Fused Handoffs."], "refs": [".agents/rules/aspice_governance.md (Rule 7)"]},
    "model_routing": {"title": "Modell-Routing (Flash vs. Pro/Neon)", "tag": "Rule 7.2", "summary": "Flash für Routine; Pro/Gemini 4 Neon strikt nur für komplexe oder grafische Aufgaben.", "bullets": ["Bounce 2–3 eskaliert auf Pro/Neon."], "refs": [".agents/rules/aspice_governance.md (Rule 7.2)"]},
    "visual_audit": {"title": "Visual Audit & Failure-Modes", "tag": "Rule 8.5", "summary": "Pflicht-Prüfung aller Bilder via read_file + 4 Real-Device Failure-Modes im Cold Review.", "bullets": ["Geometrie, M3-Farben, deutsche Textlängen, Touch-Ergonomie."], "refs": [".agents/rules/aspice_governance.md (Rule 8.5)"]},
    "queue_453": {"title": "Bereit für PO Abnahme (ID 453)", "tag": "Rule 3.1 · 9.1", "summary": "Stage-6-geprüfte Tickets warten hier geschützt auf exklusive PO-Abnahme (Erledigt + FixVersion).", "bullets": ["Befüllt per archive-po-acceptance nach Schicht-Abschluss."], "refs": [".agents/rules/aspice_governance.md"]},
    "queue_454": {"title": "Bereit für PO Systemtest (ID 454)", "tag": "Rule 7.6 · 9.1", "summary": "Geschützte Queue für human-execution / Outdoor- & Hardware-Systemtests auf Pixel 10.", "bullets": ["Agenten ziehen hier niemals Tickets heraus."], "refs": [".agents/rules/aspice_governance.md (Rule 7.6)"]},
    "weekly_dual_vendor": {"title": "Wochen-Qualität & Dual-Vendor Audits", "tag": "Rule 3.5", "summary": "Google Gemini + Anthropic Claude prüfen wöchentlich gemeinsam ASPICE-Prozess & Subsystem-Code.", "bullets": ["Gekoppelt mit Voll-Suite, lintDebug, detekt & kover."], "refs": [".agents/rules/aspice_governance.md (Rule 3.5)"]},
    "aaa_pillars": {"title": "3 AAA-Säulen & Document Triad", "tag": "DOC-GOV-PROTOCOL", "summary": "Stolpersteine, Artefakt-Infinite-Context & Pflicht zum Widerspruch + Document Triad.", "bullets": ["aspice_governance.md · coding_standards.md · design_guidelines.md."], "refs": ["docs/project_protocol.md"]},
    "jira_queues": {"title": "3-Tier-Jira-Queues", "tag": "Rule 9.1", "summary": "Product Backlog → ID 447 (PO-Freigabe) → Aktive Schicht → ID 453 (PO-Abnahme) / ID 454 (Systemtest).", "bullets": ["Klare Human-Schutzzonen verhindern unkontrolliertes Eigenleben."], "refs": [".agents/rules/aspice_governance.md (Rule 9.1)"]},
    "flock_cache": {"title": "POSIX flock & Gradle FROM-CACHE", "tag": "Rule 8.6 · 9.3", "summary": "Serialisierte Builds/Merges via /tmp/att_build_merge.lock + Cross-Worktree Build Cache.", "bullets": ["Konstanter Debug-Suffix (5.0.0-dev) ab Werk; -PstampGitHash nur für Pixel 10."], "refs": [".agents/rules/aspice_governance.md (Rule 8.6)"]},
    "git_flow": {"title": "Git-Branching-Topologie (Strategie A)", "tag": "Rule 8.2 · 9.2", "summary": "feature/<KEY> → 1 Squash-Commit auf sprint/<SCHICHT> → --no-ff Merge & Tag auf develop.", "bullets": [".githooks erzwingen ATT-\\d+ und blockieren Konfliktmarker."], "refs": ["docs/project_governance.md"]},
    "swiss_cheese": {"title": "5-Schichten Swiss-Cheese-Abwehr", "tag": "DOC-GOV-PROTOCOL", "summary": "P(Escape) = p₁ · p₂ · p₃ · p₄ · p₅ durch 5 statistisch dekorrelierte Barrieren.", "bullets": ["agent1, agent2, Cold Review (S6), Schichtwechsel/PO, Dual-Vendor (Gemini+Claude)."], "refs": ["docs/project_protocol.md"]},
    "sup9": {"title": "ASPICE SUP.9 Problem-Resolution", "tag": "REQ-PRO-121", "summary": "4-Stufen Severity-Rubrik, Crash/ANR-Invariante & deduplizierte process-escape Bugs.", "bullets": ["Explizite Bug-Auflösung (Fixed, Duplicate, Rejected, Cannot Reproduce)."], "refs": ["docs/project_governance.md"]},
    "test_pyramid": {"title": "3-Stufen-Testpyramide", "tag": "Rule 7.5", "summary": "Tier 1 (~15–60 s) im Ticket · Lean Tier 2 am Schichtwechsel · Tier 3 wöchentlich & bei Releases.", "bullets": ["Niemals --rerun-tasks!"], "refs": [".agents/rules/aspice_governance.md (Rule 7.5)"]},
    "test_tier1": {"title": "Tier 1: Inkrementelle Ticket-Tests", "tag": "Rule 7.5", "summary": "--rerun --tests <TicketSuites> mit Cross-Worktree FROM-CACHE (~15–60 s).", "bullets": ["0 redundante Stage-5-Läufe bei unverändertem SHA."], "refs": [".agents/rules/aspice_governance.md (Rule 7.5)"]},
    "test_tier2": {"title": "Lean Tier 2: Schichtwechsel-Gate", "tag": "Rule 7.5", "summary": "assembleDebug, AndroidTest-Compile, Sprint/Contract/Integration/Localization + Governance-Checks.", "bullets": ["3× täglich vor dem Merge in develop."], "refs": [".agents/rules/aspice_governance.md (Rule 7.5)"]},
    "test_tier3": {"title": "Tier 3: Wöchentliche Voll-Suite", "tag": "Rule 3.5 · 7.5", "summary": "Vollständige Unit-Suite, lintDebug, detekt & koverXmlReportDebug (Szenario-Lücken-Check).", "bullets": ["Gekoppelt an wöchentliche Dual-Vendor Audits & Releases."], "refs": [".agents/rules/aspice_governance.md (Rule 3.5)"]},
    "device_policy": {"title": "2-Stufen-Gerätepolitik", "tag": "Rule 7.6 · 7.7", "summary": "Emulator-5554 für Agenten (Stages 1–6); Pixel 10 geschützt für Best-Effort Schicht-Deploy & PO-Feldtests.", "bullets": ["Rule 7.7 schützt Nutzerdaten vor uninstall/pm clear."], "refs": [".agents/rules/aspice_governance.md (Rule 7.6–7.7)"]},
    "living_docs": {"title": "Living-Docs-Parität", "tag": "Rule 8.7–8.9", "summary": "100 % bidirektionale Traceability (SYSREQ ↔ REQ ↔ TST) & mathematische 4-Spalten-Parität.", "bullets": ["Total = Verified + Implemented + Planned."], "refs": ["docs/project_governance.md"]},
}

DIAGRAMS = [
    # Part A: Zeitliche Entwicklung & Phasen (Pros & Cons)
    ("00_evolution_storytelling",      "A", "00 · Evolution",        "Überblick: Vom Vibe-Coding zum Schichtbetrieb (Jun – Nov 2026)", build_d00_evolution),
    ("01_phase1_vibe_coding",          "A", "01 · Vibe-Coding",      "Phase 1: Vibe-Coding — Erklärung, Pros & Cons", build_d01_phase1_vibe),
    ("02_phase2_augenmass",            "A", "02 · Augenmaß",         "Phase 2: Augenmaß — Zweitmeinung, Pros & Cons", build_d02_phase2_augenmass),
    ("03_phase3_sorgfaeltig",          "A", "03 · Sorgfältig",       "Phase 3: Sorgfältig (Jun–Aug 2026) — Erklärung, Pros & Cons", build_d03_phase3_sorgfaeltig),
    ("04_phase4a_hoechstmass_geburt",  "A", "04 · Höchstmaß (4a)",   "Phase 4a: Geburt von Höchstmaß (Sep 2026) — Erklärung, Pros & Cons", build_d04_phase4a_hoechstmass),
    ("05_phase4b_hoechstmass_skalierung","A","05 · Skalierung (4b)", "Phase 4b: Triad, Worktrees & Stage 6 (Okt–15.11.) — Pros & Cons", build_d05_phase4b_skalierung),
    ("06_phase5_schichtbetrieb",       "A", "06 · Schichtbetrieb (5)","Phase 5: Schichtbetrieb nach Höchstmaß (AKTIV) — Pros & Cons", build_d06_phase5_schichtbetrieb),
    # Part B: Aktuelles Zielbild (Architektur-Deep-Dives)
    ("07_big_picture_vorgehensmodell", "B", "07 · Big Picture",      "Zielbild: Gesamtüberblick Schichtbetrieb nach Höchstmaß", build_d07_big_picture),
    ("08_schicht_takt_und_uebergabe",  "B", "08 · Schicht-Takt",     "Deep-Dive: 24/7-Schicht-Takt & Direkter Schichtwechsel", build_d08_schicht_takt),
    ("09_ticket_lifecycle_6_stages",   "B", "09 · 6-Stage & S6",     "Deep-Dive: 6-Stage-Lifecycle, Zipper & Stage 6 Cold Review", build_d09_ticket_lifecycle),
    ("10_jira_queues_lanes_git",       "B", "10 · Jira, Lanes & Git","Deep-Dive: 3-Tier-Jira-Queues, 3 Lanes & Git-Worktrees", build_d10_jira_lanes_git),
    ("11_swiss_cheese_test_pyramide",  "B", "11 · Swiss Cheese",     "Deep-Dive: Swiss-Cheese-Abwehr, Test-Pyramide & Gerätepolitik", build_d11_swiss_cheese_tests),
]


def build_interactive_html(svgs: dict[str, str], for_artifact: bool = True) -> str:
    tabs_json = json.dumps([
        {"id": slug, "group": grp, "short": short, "title": title}
        for slug, grp, short, title, _ in DIAGRAMS
    ], ensure_ascii=False)
    nodes_json = json.dumps(NODE_DETAILS, ensure_ascii=False)

    svg_blocks = []
    for idx, (slug, _, _, _, _) in enumerate(DIAGRAMS):
        hidden = "" if idx == 0 else ' style="display:none"'
        svg_blocks.append(f'<div class="diagram-panel" data-panel="{slug}"{hidden}>\n{svgs[slug]}\n</div>')
    svg_html = "\n".join(svg_blocks)

    if for_artifact:
        head_extra = '<script src="https://www.gstatic.com/antigravity/web/dev/tailwindcss.min.js"></script>'
        root_vars = ""
        body_cls = 'class="bg-[var(--background)] text-[var(--foreground)] antialiased min-h-screen"'
    else:
        head_extra = ""
        root_vars = """
    :root {
      --background: #0f172a;
      --card: #1e293b;
      --foreground: #f8fafc;
      --muted-foreground: #94a3b8;
      --border: #334155;
      --primary: #0d9488;
      --primary-foreground: #ffffff;
      --secondary: #334155;
      --secondary-foreground: #f1f5f9;
    }
    @media (prefers-color-scheme: light) {
      :root {
        --background: #f1f5f9;
        --card: #ffffff;
        --foreground: #0f172a;
        --muted-foreground: #475569;
        --border: #cbd5e1;
        --primary: #0d9488;
        --primary-foreground: #ffffff;
        --secondary: #e2e8f0;
        --secondary-foreground: #0f172a;
      }
    }
"""
        body_cls = 'style="background:var(--background);color:var(--foreground);margin:0;font-family:\'Inter\',system-ui,sans-serif;"'

    return f'''<!DOCTYPE html>
<html lang="de">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Vorgehensmodell Storytelling &amp; Architektur-Deck — Schichtbetrieb nach Höchstmaß (AAA)</title>
  {head_extra}
  <style>
    {root_vars}
    * {{ box-sizing: border-box; }}
    .app-shell {{ max-width: 1700px; margin: 0 auto; padding: 14px 18px 26px; }}
    .top-bar {{
      display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between;
      gap: 10px; padding: 12px 16px; border-radius: 14px;
      background: var(--card); border: 1px solid var(--border); margin-bottom: 12px;
    }}
    .title-group h1 {{ margin: 0; font-size: 17px; font-weight: 800; color: var(--foreground); }}
    .title-group p {{ margin: 3px 0 0; font-size: 12.5px; color: var(--muted-foreground); }}
    .btn-row {{ display: flex; flex-wrap: wrap; gap: 7px; align-items: center; }}
    .btn {{
      display: inline-flex; align-items: center; gap: 5px; padding: 7px 11px;
      border-radius: 8px; font-size: 12px; font-weight: 600; cursor: pointer;
      border: 1px solid var(--border); background: var(--secondary); color: var(--secondary-foreground);
      transition: transform 0.12s ease, opacity 0.12s ease;
    }}
    .btn:hover {{ opacity: 0.92; transform: translateY(-1px); }}
    .btn-primary {{ background: var(--primary); color: var(--primary-foreground); border-color: transparent; }}
    .tab-groups {{
      display: flex; flex-direction: column; gap: 7px; margin-bottom: 12px;
      padding: 10px 14px; border-radius: 12px; background: var(--card); border: 1px solid var(--border);
    }}
    .tab-row {{ display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }}
    .row-lbl {{
      font-size: 12px; font-weight: 700; color: var(--muted-foreground); min-width: 196px;
    }}
    .tab-btn {{
      padding: 6px 11px; border-radius: 8px; font-size: 12px; font-weight: 600;
      cursor: pointer; border: 1px solid var(--border); background: var(--background);
      color: var(--foreground); transition: all 0.14s ease;
    }}
    .tab-btn:hover {{ border-color: var(--primary); }}
    .tab-btn.active {{
      background: var(--primary); color: var(--primary-foreground); border-color: transparent;
      box-shadow: 0 2px 7px rgba(0,0,0,0.16);
    }}
    .stage-grid {{
      display: grid; grid-template-columns: minmax(0, 1fr) 330px; gap: 14px; align-items: start;
    }}
    .stage-grid.present-mode {{ grid-template-columns: 1fr; }}
    .stage-grid.present-mode .inspector {{ display: none; }}
    @media (max-width: 1180px) {{
      .stage-grid {{ grid-template-columns: 1fr; }}
    }}
    .canvas-card {{
      background: var(--card); border: 1px solid var(--border); border-radius: 14px;
      padding: 12px; overflow: hidden;
    }}
    .canvas-toolbar {{
      display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center;
      gap: 8px; padding: 2px 4px 10px; border-bottom: 1px solid var(--border); margin-bottom: 10px;
      font-size: 12.5px; color: var(--muted-foreground);
    }}
    .nav-controls {{ display: flex; align-items: center; gap: 6px; }}
    .svg-viewport {{
      overflow: auto; border-radius: 10px; background: #F8FAFC; border: 1px solid #CBD5E1;
      max-height: 75vh;
    }}
    .diagram-panel svg {{
      display: block; width: 100%; height: auto; transform-origin: top left; transition: width 0.15s ease;
    }}
    .diagram-panel .node {{ cursor: pointer; transition: filter 0.14s ease; }}
    .diagram-panel .node:hover {{ filter: drop-shadow(0 0 6px rgba(13, 148, 136, 0.55)); }}
    .diagram-panel .node.selected {{ filter: drop-shadow(0 0 8px rgba(67, 56, 202, 0.75)); }}
    .inspector {{
      background: var(--card); border: 1px solid var(--border); border-radius: 14px;
      padding: 15px; position: sticky; top: 14px;
    }}
    .insp-tag {{
      display: inline-block; padding: 3px 9px; border-radius: 999px; font-size: 12px;
      font-weight: 700; background: var(--secondary); color: var(--secondary-foreground);
      margin-bottom: 8px;
    }}
    .insp-title {{ margin: 0 0 8px; font-size: 15.5px; font-weight: 800; color: var(--foreground); }}
    .insp-summary {{ margin: 0 0 11px; font-size: 12.8px; line-height: 1.5; color: var(--muted-foreground); }}
    .insp-list {{ margin: 0 0 13px; padding-left: 18px; font-size: 12.3px; line-height: 1.55; color: var(--foreground); }}
    .insp-list li {{ margin-bottom: 5px; }}
    .insp-refs {{
      border-top: 1px solid var(--border); padding-top: 9px; font-size: 12px; color: var(--muted-foreground);
    }}
    .insp-refs code {{
      display: block; margin-top: 4px; padding: 4px 8px; border-radius: 6px;
      background: var(--secondary); color: var(--secondary-foreground); font-size: 12px;
      word-break: break-all;
    }}
    .toast {{
      position: fixed; bottom: 18px; right: 18px; padding: 10px 16px; border-radius: 10px;
      background: #0F172A; color: #FFFFFF; font-size: 13px; font-weight: 600;
      box-shadow: 0 6px 20px rgba(0,0,0,0.28); opacity: 0; pointer-events: none;
      transition: opacity 0.2s ease; z-index: 99;
    }}
    .toast.show {{ opacity: 1; }}
  </style>
</head>
<body {body_cls}>
  <div class="app-shell">
    <div class="top-bar">
      <div class="title-group">
        <h1>Agile Agentic ASPICE (AAA) — Evolution &amp; Schichtbetrieb nach Höchstmaß</h1>
        <p>12-teiliges Storytelling- &amp; Architektur-Deck (Folien 00–06: Zeitliche Entwicklung mit Pros &amp; Cons · Folien 07–11: Aktuelles Zielbild im Detail).</p>
      </div>
      <div class="btn-row">
        <button class="btn" id="btn-present" title="Inspektor aus-/einblenden">⛶ Präsentations-Modus</button>
        <button class="btn" id="btn-zoom-out">− Zoom</button>
        <button class="btn" id="btn-zoom-fit">100 %</button>
        <button class="btn" id="btn-zoom-in">+ Zoom</button>
        <button class="btn" id="btn-export-svg">⬇ SVG</button>
        <button class="btn btn-primary" id="btn-export-png">⬇ PNG (2400×1350)</button>
      </div>
    </div>

    <div class="tab-groups">
      <div class="tab-row" id="row-a"><span class="row-lbl">Teil A · Evolution &amp; Phasen (00–06):</span></div>
      <div class="tab-row" id="row-b"><span class="row-lbl">Teil B · Zielbild-Architektur (07–11):</span></div>
    </div>

    <div class="stage-grid" id="stage-grid">
      <div class="canvas-card">
        <div class="canvas-toolbar">
          <span id="active-caption"><strong>00 · Evolution</strong> — Überblick: Vom Vibe-Coding zum Schichtbetrieb (Jun – Nov 2026)</span>
          <div class="nav-controls">
            <button class="btn" id="btn-prev">← Zurück</button>
            <span id="slide-counter" style="font-weight:700;color:var(--foreground);padding:0 4px;">1 / 12</span>
            <button class="btn" id="btn-next">Weiter →</button>
          </div>
        </div>
        <div class="svg-viewport" id="svg-viewport">
          {svg_html}
        </div>
      </div>

      <aside class="inspector" id="inspector">
        <span class="insp-tag" id="insp-tag">Interaktiver Inspektor</span>
        <h2 class="insp-title" id="insp-title">Baustein im Diagramm auswählen</h2>
        <p class="insp-summary" id="insp-summary">Klicke auf beliebige Kacheln oder wechsle mit ← / → durch alle 12 Folien.</p>
        <ul class="insp-list" id="insp-list"></ul>
        <div class="insp-refs" id="insp-refs"></div>
      </aside>
    </div>
  </div>

  <div class="toast" id="toast"></div>

  <script>
    const TABS = {tabs_json};
    const NODES = {nodes_json};
    const DEFAULT_NODE_FOR_SLIDE = {{
      '00_evolution_storytelling': 'evolution_timeline',
      '01_phase1_vibe_coding': 'phase1_vibe',
      '02_phase2_augenmass': 'phase2_augenmass',
      '03_phase3_sorgfaeltig': 'phase3_sorgfaeltig',
      '04_phase4a_hoechstmass_geburt': 'phase4a_hoechstmass',
      '05_phase4b_hoechstmass_skalierung': 'phase4b_skalierung',
      '06_phase5_schichtbetrieb': 'phase5_schichtbetrieb',
      '07_big_picture_vorgehensmodell': 'schicht_core',
      '08_schicht_takt_und_uebergabe': 'schicht_phase1',
      '09_ticket_lifecycle_6_stages': 'stage6',
      '10_jira_queues_lanes_git': 'jira_queues',
      '11_swiss_cheese_test_pyramide': 'swiss_cheese',
    }};
    let activeSlug = TABS[0].id;
    let zoomPct = 100;

    const rowA = document.getElementById('row-a');
    const rowB = document.getElementById('row-b');
    const captionEl = document.getElementById('active-caption');
    const counterEl = document.getElementById('slide-counter');
    const toastEl = document.getElementById('toast');

    function showToast(msg) {{
      toastEl.textContent = msg;
      toastEl.classList.add('show');
      setTimeout(() => toastEl.classList.remove('show'), 2400);
    }}

    function renderTabs() {{
      rowA.querySelectorAll('.tab-btn').forEach((n) => n.remove());
      rowB.querySelectorAll('.tab-btn').forEach((n) => n.remove());
      TABS.forEach((t) => {{
        const btn = document.createElement('button');
        btn.className = 'tab-btn' + (t.id === activeSlug ? ' active' : '');
        btn.textContent = t.short;
        btn.onclick = () => selectTab(t.id);
        (t.group === 'A' ? rowA : rowB).appendChild(btn);
      }});
    }}

    function applyZoom() {{
      const panel = document.querySelector(`.diagram-panel[data-panel="${{activeSlug}}"] svg`);
      if (panel) panel.style.width = zoomPct + '%';
      document.getElementById('btn-zoom-fit').textContent = zoomPct + ' %';
    }}

    function selectTab(slug) {{
      activeSlug = slug;
      const idx = TABS.findIndex((t) => t.id === slug);
      document.querySelectorAll('.diagram-panel').forEach((p) => {{
        p.style.display = p.dataset.panel === slug ? 'block' : 'none';
      }});
      const tab = TABS[idx];
      captionEl.innerHTML = `<strong>${{tab.short}}</strong> — ${{tab.title}}`;
      counterEl.textContent = `${{idx + 1}} / ${{TABS.length}}`;
      renderTabs();
      applyZoom();
      if (DEFAULT_NODE_FOR_SLIDE[slug]) showNode(DEFAULT_NODE_FOR_SLIDE[slug]);
    }}

    function stepSlide(delta) {{
      const idx = TABS.findIndex((t) => t.id === activeSlug);
      const next = (idx + delta + TABS.length) % TABS.length;
      selectTab(TABS[next].id);
    }}

    function showNode(nodeId) {{
      const data = NODES[nodeId];
      if (!data) return;
      document.querySelectorAll('.diagram-panel .node.selected').forEach((el) => el.classList.remove('selected'));
      document.querySelectorAll(`.diagram-panel[data-panel="${{activeSlug}}"] .node[data-node="${{nodeId}}"]`).forEach((el) => el.classList.add('selected'));

      document.getElementById('insp-tag').textContent = data.tag;
      document.getElementById('insp-title').textContent = data.title;
      document.getElementById('insp-summary').textContent = data.summary;
      document.getElementById('insp-list').innerHTML = data.bullets.map((b) => `<li>${{b}}</li>`).join('');
      document.getElementById('insp-refs').innerHTML = '<strong>Governing Docs / Dateien:</strong>' + data.refs.map((r) => `<code>${{r}}</code>`).join('');
    }}

    document.getElementById('svg-viewport').addEventListener('click', (e) => {{
      const nodeEl = e.target.closest('.node');
      if (nodeEl && nodeEl.dataset.node) showNode(nodeEl.dataset.node);
    }});

    document.getElementById('btn-prev').onclick = () => stepSlide(-1);
    document.getElementById('btn-next').onclick = () => stepSlide(1);
    document.getElementById('btn-present').onclick = () => {{
      const grid = document.getElementById('stage-grid');
      grid.classList.toggle('present-mode');
    }};
    document.getElementById('btn-zoom-in').onclick = () => {{ zoomPct = Math.min(220, zoomPct + 20); applyZoom(); }};
    document.getElementById('btn-zoom-out').onclick = () => {{ zoomPct = Math.max(60, zoomPct - 20); applyZoom(); }};
    document.getElementById('btn-zoom-fit').onclick = () => {{ zoomPct = 100; applyZoom(); }};

    document.addEventListener('keydown', (e) => {{
      if (e.key === 'ArrowRight') stepSlide(1);
      else if (e.key === 'ArrowLeft') stepSlide(-1);
    }});

    function getActiveSvgString() {{
      const svg = document.querySelector(`.diagram-panel[data-panel="${{activeSlug}}"] svg`);
      const clone = svg.cloneNode(true);
      clone.removeAttribute('style');
      clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
      return new XMLSerializer().serializeToString(clone);
    }}

    document.getElementById('btn-export-svg').onclick = () => {{
      const svgStr = getActiveSvgString();
      const blob = new Blob([svgStr], {{ type: 'image/svg+xml;charset=utf-8' }});
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; a.download = `${{activeSlug}}.svg`; a.click();
      URL.revokeObjectURL(url);
      showToast(`SVG exportiert: ${{activeSlug}}.svg`);
    }};

    document.getElementById('btn-export-png').onclick = () => {{
      const svgStr = getActiveSvgString();
      const img = new Image();
      const svgBlob = new Blob([svgStr], {{ type: 'image/svg+xml;charset=utf-8' }});
      const url = URL.createObjectURL(svgBlob);
      img.onload = () => {{
        const canvas = document.createElement('canvas');
        canvas.width = 2400; canvas.height = 1350;
        const ctx = canvas.getContext('2d');
        ctx.fillStyle = '#F8FAFC';
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
        URL.revokeObjectURL(url);
        canvas.toBlob((pngBlob) => {{
          const pngUrl = URL.createObjectURL(pngBlob);
          const a = document.createElement('a');
          a.href = pngUrl; a.download = `${{activeSlug}}.png`; a.click();
          URL.revokeObjectURL(pngUrl);
          showToast(`HD-PNG exportiert: ${{activeSlug}}.png (2400×1350)`);
        }}, 'image/png');
      }};
      img.src = url;
    }};

    selectTab(TABS[0].id);
  </script>
</body>
</html>
'''


def render_png(svg_path: Path, png_path: Path) -> str:
    subprocess.run(
        [
            "inkscape",
            str(svg_path),
            "--export-type=png",
            "--export-width=2400",
            "--export-height=1350",
            f"--export-filename={png_path}",
        ],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return f"Rendered {png_path.name} ({png_path.stat().st_size // 1024} KB)"


def main():
    # Remove any old renumbered SVG/PNG files in OUT_DIR to avoid duplicates
    for old in OUT_DIR.glob("*.svg"):
        old.unlink()
    for old in OUT_DIR.glob("*.png"):
        old.unlink()

    svgs: dict[str, str] = {}
    jobs: list[tuple[Path, Path]] = []
    for slug, grp, short, title, builder in DIAGRAMS:
        svg_content = builder()
        svgs[slug] = svg_content
        svg_path = OUT_DIR / f"{slug}.svg"
        png_path = OUT_DIR / f"{slug}.png"
        svg_path.write_text(svg_content, encoding="utf-8")
        jobs.append((svg_path, png_path))

    # Sequential Inkscape export (avoids Snap Inkscape concurrent profile race)
    for sp, pp in jobs:
        print(render_png(sp, pp), flush=True)

    offline_html = build_interactive_html(svgs, for_artifact=False)
    (OUT_DIR / "index.html").write_text(offline_html, encoding="utf-8")
    print(f"Wrote offline viewer: {OUT_DIR / 'index.html'}")




if __name__ == "__main__":
    main()
