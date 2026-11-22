#!/usr/bin/env python3
"""Generate the minimalist, keynote-ready 10-slide German SVG/PNG deck
and interactive HTML explorer for:
'Agile Agentic ASPICE (AAA) — Vom Vibe-Coding zum Schichtbetrieb nach Höchstmaß'

Design principles:
- Big, effortless typography (30-34px headlines, 16-18px body text)
- Generous whitespace, max 3-4 visual blocks per slide
- Zero CLI commands or internal jargon on the slides
- 3 crisp Pros & 3 crisp Cons per evolution phase
"""

import html
import json
import re
import subprocess
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent

FONT_SANS = "'Inter', 'Liberation Sans', 'DejaVu Sans', sans-serif"

C = {
    "bg": "#F8FAFC",
    "white": "#FFFFFF",
    "ink": "#0F172A",
    "body": "#334155",
    "muted": "#64748B",
    "border": "#CBD5E1",
    "border_light": "#E2E8F0",
    "header_bg": "#0F172A",
    # (dark, main, soft_bg, soft_border)
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


def rich_text(text: str, dark_color: str) -> str:
    parts = re.split(r"(\*\*.*?\*\*)", text)
    out = []
    for p in parts:
        if not p:
            continue
        if p.startswith("**") and p.endswith("**") and len(p) > 4:
            out.append(f'<tspan font-weight="700" fill="{dark_color}">{esc(p[2:-2])}</tspan>')
        else:
            out.append(esc(p))
    return "".join(out)


class Slide:
    def __init__(self, slide_id: str, w: int = 1600, h: int = 900):
        self.id = slide_id
        self.w = w
        self.h = h
        self.parts: list[str] = []

    def add(self, s: str):
        self.parts.append(s)

    def header(self, pill_text: str, title: str, subtitle: str, color: str = "teal", slide_num: str = ""):
        _, main, _, _ = C[color]
        pw = max(130, len(pill_text) * 8.6 + 28)
        self.add(f'''
  <rect x="0" y="0" width="{self.w}" height="106" fill="{C['header_bg']}"/>
  <rect x="0" y="102" width="{self.w}" height="4" fill="{main}"/>
  <rect x="48" y="22" width="{pw:.0f}" height="28" rx="14" fill="{main}"/>
  <text x="{48 + pw/2:.1f}" y="40.5" text-anchor="middle" font-family="{FONT_SANS}" font-size="13" font-weight="700" fill="#FFFFFF" letter-spacing="0.5">{esc(pill_text)}</text>
  <text x="{48 + pw + 18:.0f}" y="45" font-family="{FONT_SANS}" font-size="30" font-weight="800" fill="#FFFFFF">{esc(title)}</text>
  <text x="48" y="81" font-family="{FONT_SANS}" font-size="16" font-weight="500" fill="#CBD5E1">{esc(subtitle)}</text>
  <text x="{self.w - 48}" y="44" text-anchor="end" font-family="{FONT_SANS}" font-size="14" font-weight="700" fill="#94A3B8">{esc(slide_num)}</text>
  <text x="{self.w - 48}" y="78" text-anchor="end" font-family="{FONT_SANS}" font-size="13" font-weight="500" fill="#64748B">aTrainingTracker · Vorgehensmodell</text>
''')

    def breadcrumbs(self, active_idx: int, active_color: str):
        """Clean 6-step progress strip (y=124..166)."""
        steps = [
            "1. Vibe-Coding",
            "2. Augenmaß",
            "3. Sorgfältig",
            "4a. Höchstmaß",
            "4b. Härtung",
            "5. Schichtbetrieb",
        ]
        x = 48
        w = 228
        gap = 27
        for i, lbl in enumerate(steps):
            if i < active_idx:
                bg, bdr, tf = C["emerald"][2], C["emerald"][3], C["emerald"][0]
                prefix = "✓ "
            elif i == active_idx:
                bg, bdr, tf = C[active_color][1], C[active_color][0], "#FFFFFF"
                prefix = ""
            else:
                bg, bdr, tf = "#FFFFFF", C["border"], C["muted"]
                prefix = ""
            sw = "2" if i == active_idx else "1.3"
            self.add(f'  <rect x="{x}" y="124" width="{w}" height="38" rx="19" fill="{bg}" stroke="{bdr}" stroke-width="{sw}"/>')
            self.add(f'  <text x="{x + w/2:.1f}" y="148" text-anchor="middle" font-family="{FONT_SANS}" font-size="14" font-weight="700" fill="{tf}">{esc(prefix + lbl)}</text>')
            if i < len(steps) - 1:
                acol = "emerald" if i < active_idx else (active_color if i == active_idx else "slate")
                self.arrow(x + w + 4, 143, x + w + gap - 4, 143, color=acol, width=2.2)
            x += w + gap

    def box(self, x, y, w, h, fill="#FFFFFF", stroke="#CBD5E1", sw=1.6, rx=16, shadow=True):
        if shadow:
            self.add(f'  <rect x="{x+2}" y="{y+4}" width="{w}" height="{h}" rx="{rx}" fill="#0F172A" fill-opacity="0.06"/>')
        self.add(f'  <rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}"/>')

    def icon_person(self, cx, cy, color="#4338CA", r=16):
        self.add(f'  <circle cx="{cx}" cy="{cy-10}" r="{r*0.56:.1f}" fill="{color}"/>')
        self.add(f'  <path d="M {cx-r*0.95:.1f} {cy+r*0.95:.1f} C {cx-r*0.95:.1f} {cy+1:.1f}, {cx+r*0.95:.1f} {cy+1:.1f}, {cx+r*0.95:.1f} {cy+r*0.95:.1f} Z" fill="{color}"/>')

    def icon_bot(self, cx, cy, color="#2563EB"):
        self.add(f'  <line x1="{cx}" y1="{cy-20}" x2="{cx}" y2="{cy-13}" stroke="{color}" stroke-width="2.5" stroke-linecap="round"/>')
        self.add(f'  <circle cx="{cx}" cy="{cy-22}" r="3" fill="{color}"/>')
        self.add(f'  <rect x="{cx-16}" y="{cy-13}" width="32" height="24" rx="6" fill="{color}"/>')
        self.add(f'  <circle cx="{cx-6}" cy="{cy-2}" r="3" fill="#FFFFFF"/>')
        self.add(f'  <circle cx="{cx+6}" cy="{cy-2}" r="3" fill="#FFFFFF"/>')
        self.add(f'  <rect x="{cx-6}" y="{cy+4}" width="12" height="2.6" rx="1.3" fill="#FFFFFF"/>')

    def icon_code(self, cx, cy, color="#059669"):
        self.add(f'  <rect x="{cx-17}" y="{cy-14}" width="34" height="28" rx="6" fill="none" stroke="{color}" stroke-width="2.6"/>')
        self.add(f'  <polyline points="{cx-6},{cy-5} {cx-11},{cy} {cx-6},{cy+5}" fill="none" stroke="{color}" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"/>')
        self.add(f'  <polyline points="{cx+6},{cy-5} {cx+11},{cy} {cx+6},{cy+5}" fill="none" stroke="{color}" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"/>')
        self.add(f'  <line x1="{cx+2}" y1="{cy-6}" x2="{cx-2}" y2="{cy+6}" stroke="{color}" stroke-width="2.2" stroke-linecap="round"/>')

    def icon_lock(self, cx, cy, color="#D97706"):
        self.add(f'  <path d="M {cx-8} {cy-3} V {cy-9} A 8 8 0 0 1 {cx+8} {cy-9} V {cy-3}" fill="none" stroke="{color}" stroke-width="3" stroke-linecap="round"/>')
        self.add(f'  <rect x="{cx-12}" y="{cy-3}" width="24" height="18" rx="4" fill="{color}"/>')
        self.add(f'  <circle cx="{cx}" cy="{cy+6}" r="2.8" fill="#FFFFFF"/>')

    def icon_check(self, cx, cy, color="#059669"):
        self.add(f'  <circle cx="{cx}" cy="{cy}" r="15" fill="{color}"/>')
        self.add(f'  <polyline points="{cx-7},{cy+1} {cx-2},{cy+6} {cx+8},{cy-6}" fill="none" stroke="#FFFFFF" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>')

    def icon_clock(self, cx, cy, color="#0D9488"):
        self.add(f'  <circle cx="{cx}" cy="{cy}" r="15" fill="none" stroke="{color}" stroke-width="2.8"/>')
        self.add(f'  <polyline points="{cx},{cy-8} {cx},{cy} {cx+6},{cy+4}" fill="none" stroke="{color}" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"/>')

    def icon_shield(self, cx, cy, color="#E11D48"):
        self.add(f'  <path d="M {cx} {cy-16} L {cx+14} {cy-10} V {cy+1} C {cx+14} {cy+10} {cx+7} {cy+15} {cx} {cy+18} C {cx-7} {cy+15} {cx-14} {cy+10} {cx-14} {cy+1} V {cy-10} Z" fill="{color}"/>')
        self.add(f'  <polyline points="{cx-5},{cy+1} {cx-1},{cy+5} {cx+6},{cy-4}" fill="none" stroke="#FFFFFF" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"/>')

    def arrow(self, x1, y1, x2, y2, color="slate", label="", lx=None, ly=None, dashed=False, width=3.0):
        dark, main, _, bdr = C[color]
        dash = ' stroke-dasharray="7,5"' if dashed else ""
        self.add(f'  <line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{main}" stroke-width="{width}" stroke-linecap="round"{dash} marker-end="url(#arr-{color})"/>')
        if label:
            mx = lx if lx is not None else (x1 + x2) / 2
            my = ly if ly is not None else (y1 + y2) / 2 - 12
            lw = len(label) * 7.8 + 20
            self.add(f'  <rect x="{mx - lw/2:.1f}" y="{my - 15:.1f}" width="{lw:.1f}" height="22" rx="6" fill="#FFFFFF" stroke="{bdr}" stroke-width="1"/>')
            self.add(f'  <text x="{mx:.1f}" y="{my:.1f}" text-anchor="middle" font-family="{FONT_SANS}" font-size="13" font-weight="700" fill="{dark}">{esc(label)}</text>')

    def path_arrow(self, d, color="slate", label="", lx=0, ly=0, dashed=False, width=3.0):
        dark, main, _, bdr = C[color]
        dash = ' stroke-dasharray="7,5"' if dashed else ""
        self.add(f'  <path d="{d}" fill="none" stroke="{main}" stroke-width="{width}" stroke-linejoin="round" stroke-linecap="round"{dash} marker-end="url(#arr-{color})"/>')
        if label:
            lw = len(label) * 7.8 + 24
            self.add(f'  <rect x="{lx - lw/2:.1f}" y="{ly - 16:.1f}" width="{lw:.1f}" height="25" rx="7" fill="#FFFFFF" stroke="{bdr}" stroke-width="1.2"/>')
            self.add(f'  <text x="{lx:.1f}" y="{ly+1:.1f}" text-anchor="middle" font-family="{FONT_SANS}" font-size="13.5" font-weight="700" fill="{dark}">{esc(label)}</text>')

    def node_box(self, x, y, w, h, title, sub, color="blue", icon=None, strong=False):
        dark, main, soft, bdr = C[color]
        fill = soft if strong else "#FFFFFF"
        stroke = main if strong else bdr
        sw = 2.6 if strong else 1.8
        self.box(x, y, w, h, fill=fill, stroke=stroke, sw=sw, rx=14)
        cx = x + w / 2
        if icon:
            icon(cx, y + 38, main)
            ty = y + 82
        else:
            ty = y + h / 2 - (4 if sub else -6)
        self.add(f'  <text x="{cx:.1f}" y="{ty:.1f}" text-anchor="middle" font-family="{FONT_SANS}" font-size="17" font-weight="800" fill="{dark}">{esc(title)}</text>')
        if sub:
            self.add(f'  <text x="{cx:.1f}" y="{ty + 24:.1f}" text-anchor="middle" font-family="{FONT_SANS}" font-size="14" font-weight="500" fill="{C["body"]}">{esc(sub)}</text>')

    def pros_cons_and_takeaway(
        self,
        pros: list[tuple[str, str]],
        cons: list[tuple[str, str]],
        takeaway_label: str,
        takeaway_text: str,
        takeaway_color: str,
    ):
        """Airy, big-font Pros vs Cons (y=414..758) + bold 1-sentence Bottom Banner (y=780..864)."""
        # Left: PROS (x=48..782, w=734, h=344)
        e_dark, e_main, e_soft, e_bdr = C["emerald"]
        self.box(48, 414, 734, 344, fill=e_soft, stroke=e_bdr, sw=1.8, rx=16)
        self.add(f'  <rect x="48" y="414" width="734" height="46" rx="16" fill="{e_main}"/>')
        self.add(f'  <rect x="48" y="442" width="734" height="18" fill="{e_main}"/>')
        self.add(f'  <text x="72" y="444" font-family="{FONT_SANS}" font-size="18" font-weight="800" fill="#FFFFFF">PROS — Was gut funktioniert</text>')

        py = 482
        for head, sub in pros:
            self.box(68, py, 694, 76, fill="#FFFFFF", stroke=e_bdr, sw=1.3, rx=12, shadow=False)
            self.add(f'  <circle cx="96" cy="{py+38}" r="13" fill="{e_soft}" stroke="{e_main}" stroke-width="2"/>')
            self.add(f'  <text x="96" y="{py+43.5}" text-anchor="middle" font-family="{FONT_SANS}" font-size="18" font-weight="800" fill="{e_dark}">+</text>')
            self.add(f'  <text x="124" y="{py+31}" font-family="{FONT_SANS}" font-size="17" font-weight="800" fill="{e_dark}">{esc(head)}</text>')
            self.add(f'  <text x="124" y="{py+56}" font-family="{FONT_SANS}" font-size="14.5" font-weight="500" fill="{C["body"]}">{esc(sub)}</text>')
            py += 88

        # Right: CONS (x=818..1552, w=734, h=344)
        r_dark, r_main, r_soft, r_bdr = C["rose"]
        self.box(818, 414, 734, 344, fill=r_soft, stroke=r_bdr, sw=1.8, rx=16)
        self.add(f'  <rect x="818" y="414" width="734" height="46" rx="16" fill="{r_main}"/>')
        self.add(f'  <rect x="818" y="442" width="734" height="18" fill="{r_main}"/>')
        self.add(f'  <text x="842" y="444" font-family="{FONT_SANS}" font-size="18" font-weight="800" fill="#FFFFFF">CONS — Wo die Grenzen liegen</text>')

        cy = 482
        for head, sub in cons:
            self.box(838, cy, 694, 76, fill="#FFFFFF", stroke=r_bdr, sw=1.3, rx=12, shadow=False)
            self.add(f'  <circle cx="866" cy="{cy+38}" r="13" fill="{r_soft}" stroke="{r_main}" stroke-width="2"/>')
            self.add(f'  <text x="866" y="{cy+43}" text-anchor="middle" font-family="{FONT_SANS}" font-size="19" font-weight="800" fill="{r_dark}">−</text>')
            self.add(f'  <text x="894" y="{cy+31}" font-family="{FONT_SANS}" font-size="17" font-weight="800" fill="{r_dark}">{esc(head)}</text>')
            self.add(f'  <text x="894" y="{cy+56}" font-family="{FONT_SANS}" font-size="14.5" font-weight="500" fill="{C["body"]}">{esc(sub)}</text>')
            cy += 88

        # Bottom Single-Sentence Takeaway Banner (y=782..864)
        t_dark, t_main, t_soft, t_bdr = C[takeaway_color]
        self.box(48, 782, 1504, 82, fill=t_soft, stroke=t_main, sw=2.2, rx=14)
        bw = max(175, len(takeaway_label) * 9.0 + 30)
        self.add(f'  <rect x="68" y="805" width="{bw:.0f}" height="36" rx="18" fill="{t_main}"/>')
        self.add(f'  <text x="{68 + bw/2:.1f}" y="828" text-anchor="middle" font-family="{FONT_SANS}" font-size="14" font-weight="800" fill="#FFFFFF">{esc(takeaway_label)}</text>')
        self.add(f'  <text x="{68 + bw + 22:.0f}" y="829" font-family="{FONT_SANS}" font-size="17.5" font-weight="600" fill="{t_dark}">{rich_text(takeaway_text, t_dark)}</text>')

    def render(self) -> str:
        markers = []
        for cname, val in C.items():
            if isinstance(val, tuple):
                _, main, _, _ = val
                markers.append(f'''    <marker id="arr-{cname}" viewBox="0 0 10 10" refX="8.5" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse">
      <path d="M 1 1.5 L 9 5 L 1 8.5 z" fill="{main}"/>
    </marker>''')
        return f'''<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {self.w} {self.h}" width="{self.w}" height="{self.h}" id="svg-{self.id}">
  <defs>
{chr(10).join(markers)}
  </defs>
  <rect x="0" y="0" width="{self.w}" height="{self.h}" fill="{C['bg']}"/>
{chr(10).join(self.parts)}
</svg>
'''


# =============================================================================
# SLIDE 01: UNSERE REISE IN 5 STUFEN (ÜBERBLICK)
# =============================================================================
def build_s01_overview() -> str:
    s = Slide("s01")
    s.header(
        "ÜBERBLICK",
        "Unsere Reise in 5 Stufen (Juni – Nov. 2026)",
        "Wie wir in 5 Monaten vom spontanen Vibe-Coding zum 24/7-Schichtbetrieb nach Höchstmaß gewachsen sind",
        "indigo",
        "Folie 1 / 10",
    )

    # Big 5-Step Rising Staircase (y=134..526)
    steps = [
        ("Stufe 1", "Vibe-Coding", "Bis Jun 2026", "1 Agent baut\nad-hoc drauflos", "slate", 360, False),
        ("Stufe 2", "Augenmaß", "Juni 2026", "Zweitmeinung\nnach Bauchgefühl", "blue", 308, False),
        ("Stufe 3", "Sorgfältig", "Jun – Aug", "Ticket für Ticket\nsauber getestet", "indigo", 256, False),
        ("Stufe 4", "Höchstmaß", "Sep – Mitte Nov", "ASPICE-Gates,\nWorktrees & S6", "amber", 204, False),
        ("Stufe 5", "Schichtbetrieb", "Ab Nov · AKTIV", "24/7 im Takt auf\n3 parallelen Spuren", "teal", 152, True),
    ]

    # Subtle rising background guide
    s.add(f'  <line x1="80" y1="510" x2="1520" y2="170" stroke="{C["border"]}" stroke-width="2.5" stroke-dasharray="8,8"/>')

    x = 48
    w = 274
    gap = 33
    for i, (num, name, dt, desc, col, top_y, active) in enumerate(steps):
        dark, main, soft, bdr = C[col]
        h = 164
        s.box(x, top_y, w, h, fill=soft if active else "#FFFFFF", stroke=main if active else bdr, sw=3 if active else 1.8, rx=16)
        # Top pill
        s.add(f'  <rect x="{x+18}" y="{top_y+16}" width="84" height="26" rx="13" fill="{main}"/>')
        s.add(f'  <text x="{x+60}" y="{top_y+33.5}" text-anchor="middle" font-family="{FONT_SANS}" font-size="13" font-weight="800" fill="#FFFFFF">{esc(num)}</text>')
        s.add(f'  <text x="{x+w-18}" y="{top_y+34}" text-anchor="end" font-family="{FONT_SANS}" font-size="13" font-weight="700" fill="{dark}">{esc(dt)}</text>')
        # Big Title
        s.add(f'  <text x="{x+18}" y="{top_y+78}" font-family="{FONT_SANS}" font-size="23" font-weight="800" fill="{dark}">{esc(name)}</text>')
        # 2-line description
        for li, line in enumerate(desc.split("\n")):
            s.add(f'  <text x="{x+18}" y="{top_y+112 + li*24}" font-family="{FONT_SANS}" font-size="15.5" font-weight="500" fill="{C["body"]}">{esc(line)}</text>')
        if i < 4:
            next_y = steps[i + 1][5]
            s.arrow(x + w + 4, top_y + h * 0.5, x + w + gap - 4, next_y + h * 0.5, color=col, width=2.8)
        x += w + gap

    # Bottom Band: Growth Curve (Left) + 3 Big Numbers (Right) (y=558..768)
    # Left: Super simple REQ growth sparkline card
    s.box(48, 558, 930, 206, fill="#FFFFFF", stroke=C["blue"][3], sw=1.8, rx=16)
    s.add(f'  <text x="76" y="594" font-family="{FONT_SANS}" font-size="18" font-weight="800" fill="{C["ink"]}">Wachstum der dokumentierten Anforderungen (Juni – Nov. 2026)</text>')

    pts = [
        ("Jun", 80), ("Jul", 204), ("Sep", 313), ("Okt", 809), ("Mitte Nov", 1116), ("Heute", 1265),
    ]
    px0, px1, py_top, py_bot = 110, 920, 626, 722
    coords = []
    for i, (lbl, val) in enumerate(pts):
        px = px0 + (i / (len(pts) - 1)) * (px1 - px0)
        py = py_bot - (val / 1350.0) * (py_bot - py_top)
        coords.append((px, py, lbl, val))
    poly = f"{coords[0][0]:.1f},{py_bot} " + " ".join(f"{x:.1f},{y:.1f}" for x, y, _, _ in coords) + f" {coords[-1][0]:.1f},{py_bot}"
    s.add(f'  <polygon points="{poly}" fill="{C["blue"][2]}" fill-opacity="0.8"/>')
    s.add(f'  <polyline points="{" ".join(f"{x:.1f},{y:.1f}" for x, y, _, _ in coords)}" fill="none" stroke="{C["blue"][1]}" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>')
    for i, (px, py, lbl, val) in enumerate(coords):
        col = "teal" if i == len(coords) - 1 else "blue"
        s.add(f'  <circle cx="{px:.1f}" cy="{py:.1f}" r="{8 if i==len(coords)-1 else 6}" fill="{C[col][1]}" stroke="#FFFFFF" stroke-width="2.5"/>')
        s.add(f'  <text x="{px:.1f}" y="{py-14:.1f}" text-anchor="middle" font-family="{FONT_SANS}" font-size="14" font-weight="800" fill="{C[col][0]}">{val}</text>')
        s.add(f'  <text x="{px:.1f}" y="{py_bot+24}" text-anchor="middle" font-family="{FONT_SANS}" font-size="13.5" font-weight="600" fill="{C["muted"]}">{lbl}</text>')

    # Right: 3 Big Hero KPI cards stacked/side-by-side (x=1004..1552, y=558..768)
    kpis = [
        ("1.265", "Anforderungen", "81 % automatisch verifiziert", "blue"),
        ("80", "Retrospektiven", "Jeder Engpass floss ins Modell", "amber"),
        ("3× 24h", "Schichtbetrieb", "3 Schichten · 3 parallele Spuren", "teal"),
    ]
    ky = 558
    for big, title, sub, col in kpis:
        dark, main, soft, bdr = C[col]
        s.box(1004, ky, 548, 62, fill="#FFFFFF", stroke=bdr, sw=1.6, rx=12)
        s.add(f'  <rect x="1004" y="{ky}" width="8" height="62" rx="4" fill="{main}"/>')
        s.add(f'  <text x="1032" y="{ky+41}" font-family="{FONT_SANS}" font-size="28" font-weight="800" fill="{dark}">{esc(big)}</text>')
        s.add(f'  <text x="1175" y="{ky+28}" font-family="{FONT_SANS}" font-size="16" font-weight="800" fill="{C["ink"]}">{esc(title)}</text>')
        s.add(f'  <text x="1175" y="{ky+49}" font-family="{FONT_SANS}" font-size="13.5" font-weight="500" fill="{C["muted"]}">{esc(sub)}</text>')
        ky += 72

    # Bottom Takeaway
    s.box(48, 786, 1504, 78, fill=C["indigo"][2], stroke=C["indigo"][1], sw=2.2, rx=14)
    s.add(f'  <text x="800" y="832" text-anchor="middle" font-family="{FONT_SANS}" font-size="19" font-weight="700" fill="{C["indigo"][0]}">Leitgedanke: Jede neue Stufe entstand nicht am Reißbrett — sondern aus einem echten Schmerz der Stufe davor.</text>')
    return s.render()


# =============================================================================
# SLIDE 02: STUFE 1 — VIBE-CODING
# =============================================================================
def build_s02_vibe() -> str:
    s = Slide("s02")
    s.header(
        "STUFE 1 VON 5",
        "Vibe-Coding — Einfach mal drauflos",
        "Ein Mensch promptet direkt im Chat, ein KI-Agent schreibt sofort Code — bis es auf den ersten Blick läuft",
        "slate",
        "Folie 2 / 10",
    )
    s.breadcrumbs(0, "slate")

    # Clean 3-Box Visual Flow (y=186..392)
    s.box(48, 186, 1504, 206, fill="#FFFFFF", stroke=C["border"], sw=1.6, rx=16)
    s.add(f'  <text x="76" y="218" font-family="{FONT_SANS}" font-size="15" font-weight="700" fill="{C["muted"]}">SO LÄUFT ES AB · KEIN PROZESS, KEINE PRÜFUNG</text>')

    s.node_box(130, 238, 300, 128, "Mensch", "Stellt spontanen Wunsch im Chat", "indigo", icon=s.icon_person)
    s.arrow(430, 286, 610, 286, color="slate", label="Prompt", lx=520, ly=270)
    s.arrow(610, 326, 430, 326, color="amber", label="„Fertig!“", lx=520, ly=352, dashed=True)
    s.node_box(614, 238, 350, 128, "1 KI-Agent", "Codet sofort los — ohne Plan", "slate", icon=s.icon_bot, strong=True)
    s.arrow(964, 302, 1136, 302, color="slate", label="Direkt in den Code", lx=1050, ly=286)
    s.node_box(1140, 238, 330, 128, "App / Codebase", "Keine Tests, keine Dokumentation", "rose", icon=s.icon_code)

    s.pros_cons_and_takeaway(
        pros=[
            ("Sofortige Ergebnisse in Minuten", "Maximales Tempo beim ersten Entwurf ohne jegliche Vorarbeit."),
            ("Null Prozess-Ballast", "Keine Tickets, keine Regeln — volle kreative Freiheit."),
            ("Perfekt für Wegwerf-Prototypen", "Ideal, um in 30 Minuten eine Idee oder ein kleines Skript zu testen."),
        ],
        cons=[
            ("Stille Regressionen", "Neuer Code zerstört unbemerkt bestehende Funktionen."),
            ("Blind für eigene Fehler", "Derselbe Chat hält seine Lösung fast immer für fehlerfrei."),
            ("Kein Langzeitgedächtnis", "Nach Chat-Ende sind alle Entscheidungen unwiederbringlich weg."),
        ],
        takeaway_label="WENDEPUNKT → STUFE 2",
        takeaway_text="Blindes Vertrauen in **einen** Agenten reicht nicht — wir brauchen eine **unabhängige Zweitmeinung**.",
        takeaway_color="blue",
    )
    return s.render()


# =============================================================================
# SLIDE 03: STUFE 2 — AUGENMASS
# =============================================================================
def build_s03_augenmass() -> str:
    s = Slide("s03")
    s.header(
        "STUFE 2 VON 5",
        "Augenmaß — Mensch + gezielte Zweitmeinung",
        "Weiterhin ohne schweres Prozess-Gerüst — aber ein zweiter Agent kritisiert den ersten, bis das Gefühl stimmt",
        "blue",
        "Folie 3 / 10",
    )
    s.breadcrumbs(1, "blue")

    s.box(48, 186, 1504, 206, fill="#FFFFFF", stroke=C["blue"][3], sw=1.6, rx=16)
    s.add(f'  <text x="76" y="218" font-family="{FONT_SANS}" font-size="15" font-weight="700" fill="{C["blue"][0]}">SO LÄUFT ES AB · VIER-AUGEN-PRINZIP AUF ZURUF</text>')

    s.node_box(84, 238, 250, 128, "Mensch", "Steuert & entscheidet", "indigo", icon=s.icon_person)
    s.arrow(334, 302, 424, 302, color="blue", label="Auftrag", lx=379, ly=286)
    s.node_box(428, 238, 290, 128, "KI 1 · Umsetzer", "Schreibt Code-Entwurf", "blue", icon=s.icon_bot)
    s.arrow(718, 284, 868, 284, color="amber", label="Prüf bitte!", lx=793, ly=268)
    s.arrow(868, 326, 718, 326, color="rose", label="Kritik", lx=793, ly=352, dashed=True)
    s.node_box(872, 238, 300, 128, "KI 2 · Zweitmeinung", "Frischer, kritischer Blick", "amber", icon=s.icon_shield, strong=True)
    s.arrow(1172, 302, 1264, 302, color="emerald", label="Gefühl ok", lx=1218, ly=286)
    s.node_box(1268, 238, 248, 128, "Übernahme", "Manuell in die App", "emerald", icon=s.icon_check)

    s.pros_cons_and_takeaway(
        pros=[
            ("Großer Qualitätssprung", "Ein zweiter Blick deckt grobe Logikfehler und Lücken sofort auf."),
            ("Bricht die Ja-Sager-Spirale", "Der Prüfer ist nicht in die Lösung des ersten Agenten verliebt."),
            ("Weiterhin schnell & schlank", "Pragmatisch einsetzbar — ohne starres Formularwesen."),
        ],
        cons=[
            ("Abhängig vom Bauchgefühl", "Ohne feste Kriterien werden Randfälle leicht übersehen."),
            ("Keine niedergeschriebenen Ziele", "Ohne Anforderungen weiß auch KI 2 nicht, was zwingend gelten muss."),
            ("Viel manuelle Handarbeit", "Der Mensch kopiert und moderiert ständig zwischen beiden Chats."),
        ],
        takeaway_label="WENDEPUNKT → STUFE 3",
        takeaway_text="Die App wächst — ohne **klare Tickets, Anforderungen und Tests** verlieren wir den Überblick.",
        takeaway_color="indigo",
    )
    return s.render()


# =============================================================================
# SLIDE 04: STUFE 3 — SORGFÄLTIG
# =============================================================================
def build_s04_sorgfaeltig() -> str:
    s = Slide("s04")
    s.header(
        "STUFE 3 VON 5",
        "Sorgfältig — Ticket für Ticket sauber getestet",
        "Juni – August 2026: Erste Anforderungsliste, Jira-Tickets und automatisierte Tests unter direkter menschlicher Führung",
        "indigo",
        "Folie 4 / 10",
    )
    s.breadcrumbs(2, "indigo")

    s.box(48, 186, 1504, 206, fill="#FFFFFF", stroke=C["indigo"][3], sw=1.6, rx=16)
    s.add(f'  <text x="76" y="218" font-family="{FONT_SANS}" font-size="15" font-weight="700" fill="{C["indigo"][0]}">SO LÄUFT ES AB · STRUKTURIERTE EINZELARBEIT (80 → 204 ANFORDERUNGEN)</text>')

    s.node_box(84, 238, 270, 128, "1. Jira-Ticket", "Klarer Einzelauftrag", "indigo", icon=s.icon_person)
    s.arrow(354, 302, 434, 302, color="indigo")
    s.node_box(438, 238, 320, 128, "2. Bauen + Testen", "Code, Unit-Tests & erste Doku", "blue", icon=s.icon_bot, strong=True)
    s.arrow(758, 302, 838, 302, color="blue")
    s.node_box(842, 238, 300, 128, "3. Mensch prüft", "Review & Test am Gerät", "amber", icon=s.icon_shield)
    s.arrow(1142, 302, 1222, 302, color="emerald")
    s.node_box(1226, 238, 290, 128, "4. Sauberer Stand", "Nächstes Ticket startet manuell", "emerald", icon=s.icon_check)

    s.pros_cons_and_takeaway(
        pros=[
            ("Verlässliche Einzelergebnisse", "Jedes Ticket ist sauber getestet und nachvollziehbar dokumentiert."),
            ("Erstes Gedächtnis im Projekt", "Anforderungen und Tickets bleiben dauerhaft erhalten."),
            ("Angenehmes, direktes Arbeiten", "Für kleine Projekte oder 1–3 Tickets am Tag ideal."),
        ],
        cons=[
            ("KI codet oft noch zu früh los", "Ohne Planungs-Stopp werden bestehende Zusammenhänge übersehen."),
            ("Mensch ist der Flaschenhals", "Ohne persönliche Präsenz steht die gesamte Entwicklung still."),
            ("Große Backlogs dauern ewig", "Hunderte Tickets einzeln von Hand zu steuern skaliert nicht."),
        ],
        takeaway_label="WENDEPUNKT → STUFE 4a",
        takeaway_text="Für Tempo **und** Qualität braucht die KI **bewusste Stolpersteine (Gates)** und autonome Sprints.",
        takeaway_color="amber",
    )
    return s.render()


# =============================================================================
# SLIDE 05: STUFE 4a — HÖCHSTMASS: GEBURT
# =============================================================================
def build_s05_hoechstmass_geburt() -> str:
    s = Slide("s05")
    s.header(
        "STUFE 4a VON 5",
        "Höchstmaß — Bewusste Stolpersteine & Gates",
        "September 2026: Erst verstehen, dann testen planen, dann architektonisch planen — Code erst nach Freigabe",
        "amber",
        "Folie 5 / 10",
    )
    s.breadcrumbs(3, "amber")

    s.box(48, 186, 1504, 206, fill="#FFFFFF", stroke=C["amber"][3], sw=1.8, rx=16)
    s.add(f'  <text x="76" y="218" font-family="{FONT_SANS}" font-size="15" font-weight="700" fill="{C["amber"][0]}">SO LÄUFT ES AB · 5 FESTE STUFEN + VIER-AUGEN-PING-PONG (KI 1 BAUT ⇄ KI 2 PRÜFT JEDES GATE)</text>')

    st = [
        ("1. Analyse", "Ursache verstehen", "blue", False),
        ("2. Test-Spec", "Soll-Zustand", "blue", False),
        ("3. Plan", "CODE-STOPP!", "amber", True),
        ("4. Umsetzung", "Code + Tests", "blue", False),
        ("5. Nachweis", "Dokumentiert", "emerald", False),
    ]
    sx = 76
    sw = 256
    gap = 42
    for i, (t, sub, col, lock) in enumerate(st):
        s.node_box(sx, 238, sw, 128, t, sub, col, icon=s.icon_lock if lock else None, strong=lock)
        if i < 4:
            s.arrow(sx + sw + 4, 302, sx + sw + gap - 4, 302, color=col, width=2.6)
        sx += sw + gap

    s.pros_cons_and_takeaway(
        pros=[
            ("Kein voreiliges Draufloscoden", "Die Code-Sperre vor Schritt 4 schützt bestehende Architektur."),
            ("3× höherer Durchsatz (1.055 Commits)", "Ganze Sprints laufen über Nacht selbstständig durch."),
            ("Unvergessliches Projektgedächtnis", "Kurze Markdown-Artefakte überleben jedes volle Chatfenster."),
        ],
        cons=[
            ("Regel-Wildwuchs (84+ Regeln)", "Immer neue Retro-Regeln überlasteten lange Agenten-Kontexte."),
            ("Nur eine Spur gleichzeitig", "Ein großes Ticket blockierte alle anderen Themen im Sprint."),
            ("Betriebsblindheit im selben Paar", "Subtile Fehler rutschten gelegentlich bis zur Menschen-Abnahme."),
        ],
        takeaway_label="WENDEPUNKT → STUFE 4b",
        takeaway_text="Regeln radikal entschlacken, **parallel arbeiten** und einen **unabhängigen Kalt-Prüfer** einziehen.",
        takeaway_color="rose",
    )
    return s.render()


# =============================================================================
# SLIDE 06: STUFE 4b — HÖCHSTMASS: HÄRTUNG & SKALIERUNG
# =============================================================================
def build_s06_hoechstmass_haertung() -> str:
    s = Slide("s06")
    s.header(
        "STUFE 4b VON 5",
        "Höchstmaß gehärtet — Parallelität & Kalt-Review",
        "Oktober – Mitte Nov. 2026: Regeln verdichtet, parallele Arbeitsbereiche und ein frischer End-Prüfer ohne Vorwissen",
        "rose",
        "Folie 6 / 10",
    )
    s.breadcrumbs(4, "rose")

    s.box(48, 186, 1504, 206, fill="#FFFFFF", stroke=C["rose"][3], sw=1.8, rx=16)
    s.add(f'  <text x="76" y="218" font-family="{FONT_SANS}" font-size="15" font-weight="700" fill="{C["rose"][0]}">DIE 3 GROSSEN UPGRADES DIESER PHASE (2.077 COMMITS IM OKTOBER · 1.116 ANFORDERUNGEN)</text>')

    s.node_box(84, 238, 430, 128, "9 Kernregeln statt 84+", "Aufgeräumtes Regelwerk gegen Kontext-Ballast", "amber", icon=s.icon_check)
    s.arrow(514, 302, 578, 302, color="slate")
    s.node_box(582, 238, 436, 128, "Parallele Arbeitsspuren", "Getrennte Arbeitskopien ohne Merge-Chaos", "teal", icon=s.icon_code)
    s.arrow(1018, 302, 1082, 302, color="slate")
    s.node_box(1086, 238, 430, 128, "Stufe 6: Kalt-Review", "Frische KI ohne Vorwissen prüft vor dem Menschen", "rose", icon=s.icon_shield, strong=True)

    s.pros_cons_and_takeaway(
        pros=[
            ("Deutlich weniger Fehler beim Menschen", "Der kalte End-Prüfer schickt Mängel automatisch zurück zum Heilen."),
            ("Echte Parallelität ohne Konflikte", "Mehrere Themen laufen gleichzeitig in isolierten Arbeitskopien."),
            ("Privates Handy bleibt geschützt", "Agenten testen strikt im eigenen Android-Emulator."),
        ],
        cons=[
            ("Langsam & teuer bei Routine", "Zu viele Voll-Builds und große KI-Modelle bremsten einfache Tickets."),
            ("Leerlauf zwischen den Sprints", "Manuelle Übergaben und Pausen unterbrachen den Arbeitsfluss."),
            ("Kalt-Review prüfte nur den Code-Text", "Optische & Laufzeit-Fehler in der App fielen noch nicht auf."),
        ],
        takeaway_label="WENDEPUNKT → STUFE 5",
        takeaway_text="Jetzt fehlt nur noch der **nahtlose 24/7-Takt**, **Live-Tests im Emulator** und smarte Modellwahl.",
        takeaway_color="teal",
    )
    return s.render()


# =============================================================================
# SLIDE 07: STUFE 5 — SCHICHTBETRIEB NACH HÖCHSTMASS (AKTIV)
# =============================================================================
def build_s07_schichtbetrieb() -> str:
    s = Slide("s07")
    s.header(
        "STUFE 5 VON 5 · AKTIV",
        "Schichtbetrieb nach Höchstmaß — 24/7 im Takt",
        "Seit Mitte Nov. 2026: Volle Höchstmaß-Qualität rund um die Uhr auf 3 Spuren — schnell, wirtschaftlich & kontrolliert",
        "teal",
        "Folie 7 / 10",
    )
    s.breadcrumbs(5, "teal")

    s.box(48, 186, 1504, 206, fill="#FFFFFF", stroke=C["teal"][3], sw=1.8, rx=16)
    s.add(f'  <text x="76" y="218" font-family="{FONT_SANS}" font-size="15" font-weight="700" fill="{C["teal"][0]}">SO LÄUFT ES HEUTE · SELBSTTRAGENDER 24/7-KREISLAUF MIT KLAREN MENSCHEN-GRENZEN</text>')

    s.node_box(84, 238, 290, 128, "Mensch gibt frei", "Füllt geschützten Vorrat", "indigo", icon=s.icon_person)
    s.arrow(374, 302, 444, 302, color="indigo")
    s.node_box(448, 238, 320, 128, "3 Schichten · 3 Spuren", "Früh · Spät · Nacht ohne Pause", "teal", icon=s.icon_clock, strong=True)
    s.arrow(768, 302, 838, 302, color="teal")
    s.node_box(842, 238, 310, 128, "Live Kalt-Review", "Startet App im Emulator + Fotos", "rose", icon=s.icon_shield)
    s.arrow(1152, 302, 1222, 302, color="emerald")
    s.node_box(1226, 238, 290, 128, "Mensch nimmt ab", "Exklusive End-Entscheidung", "emerald", icon=s.icon_check)

    s.pros_cons_and_takeaway(
        pros=[
            ("Kontinuierliche 24/7-Lieferung", "Feste Schichtwechsel ohne Pause und ohne Kontext-Verlust."),
            ("Schnell, günstig & treffsicher", "Kleine Modelle für Routine, starke Modelle nur für Komplexes & UI."),
            ("Mensch frei — aber immer der Chef", "Klare Eingangs- und Abnahme-Tore plus Wochen-Check mit 2 KI-Anbietern."),
        ],
        cons=[
            ("Hohe Anfangsinvestition", "Regelwerk, Skripte und Qualitäts-Automatik müssen aufgebaut werden."),
            ("Überdimensioniert für Kleinkram", "Für schnelle Prototypen oder Einmal-Skripte reichen Stufe 1–3."),
            ("Laufende KI- & Pflege-Kosten", "Dauerbetrieb auf 3 Spuren erfordert Disziplin bei Kosten und Vorrat."),
        ],
        takeaway_label="UNSER FAZIT",
        takeaway_text="Die Kunst ist die richtige Stufe: **Stufe 1–3** für Kleines — **Stufe 5** für große, langlebige Produkte.",
        takeaway_color="teal",
    )
    return s.render()


# =============================================================================
# SLIDE 08: HEUTE IM BETRIEB — 3 SCHICHTEN & KLARE ROLLEN
# =============================================================================
def build_s08_takt_rollen() -> str:
    s = Slide("s08")
    s.header(
        "HEUTE · ORGANISATION",
        "Wie der Schichtbetrieb heute tickt: Takt & Rollen",
        "Drei feste Schichten rund um die Uhr und eine glasklare Aufgabenteilung zwischen Mensch, Schichtführer und KI-Teams",
        "teal",
        "Folie 8 / 10",
    )

    # Top Hero: Big Simple 24h Shift Bar (y=134..332)
    s.box(48, 134, 1504, 198, fill="#FFFFFF", stroke=C["teal"][3], sw=1.8, rx=16)
    s.add(f'  <text x="80" y="172" font-family="{FONT_SANS}" font-size="20" font-weight="800" fill="{C["ink"]}">Der 24-Stunden-Takt: 3 Schichten, 3 feste Übergaben, 0 Leerlauf</text>')

    bar_x, bar_y, bar_h = 80, 196, 82
    segs = [
        (bar_x, 596, "Frühschicht", "05:00 – 15:00 Uhr (10 h)", "teal"),
        (bar_x + 604, 416, "Spätschicht", "15:00 – 22:00 Uhr (7 h)", "blue"),
        (bar_x + 1028, 412, "Nachtschicht", "22:00 – 05:00 Uhr (7 h)", "indigo"),
    ]
    for sx, sw, lbl, sub, col in segs:
        dark, main, soft, _ = C[col]
        s.add(f'  <rect x="{sx}" y="{bar_y}" width="{sw}" height="{bar_h}" rx="12" fill="{soft}" stroke="{main}" stroke-width="2.4"/>')
        s.add(f'  <text x="{sx + sw/2:.1f}" y="{bar_y+36}" text-anchor="middle" font-family="{FONT_SANS}" font-size="21" font-weight="800" fill="{dark}">{esc(lbl)}</text>')
        s.add(f'  <text x="{sx + sw/2:.1f}" y="{bar_y+63}" text-anchor="middle" font-family="{FONT_SANS}" font-size="15" font-weight="600" fill="{C["body"]}">{esc(sub)}</text>')

    for px, t in [(bar_x, "05:00"), (bar_x + 600, "15:00"), (bar_x + 1024, "22:00"), (bar_x + 1440, "05:00")]:
        s.add(f'  <circle cx="{px}" cy="{bar_y+bar_h}" r="8" fill="{C["amber"][1]}" stroke="#FFFFFF" stroke-width="2.5"/>')
        anc = "start" if px == bar_x else ("end" if px == bar_x + 1440 else "middle")
        s.add(f'  <text x="{px}" y="{bar_y+bar_h+27}" text-anchor="{anc}" font-family="{FONT_SANS}" font-size="15" font-weight="800" fill="{C["amber"][0]}">{t} Übergabe</text>')

    # Bottom: 3 Big Role Cards (y=360..762)
    roles = [
        ("1. Mensch (Product Owner)", "Strategie & letzte Instanz", "indigo", s.icon_person, [
            ("Gibt Aufgaben frei", "Wählt aus, was die KI bauen darf."),
            ("Nimmt Ergebnisse ab", "Nur der Mensch schließt Tickets final."),
            ("Testet im echten Leben", "Outdoor- & Hardware-Tests am Smartphone."),
        ]),
        ("2. Schichtführer (Koordinator)", "Taktgeber ohne Eigenbau", "teal", s.icon_clock, [
            ("Steuert 3 parallele Spuren", "Verteilt getrennte Themen konfliktfrei."),
            ("Sichert die Übergabe", "Testet, protokolliert & startet Folgeschicht."),
            ("Darf nichts überstimmen", "Qualitäts-Urteile sind unantastbar."),
        ]),
        ("3. KI-Teams (Bauen ⇄ Prüfen)", "Getrennte Rollen & smarte Modelle", "blue", s.icon_bot, [
            ("Strikte Rollentrennung", "Niemand prüft seine eigene Arbeit."),
            ("Gezielte Modellwahl", "Schnell für Routine, stark für Komplexes & UI."),
            ("Kein Festfahren", "Bei Unklarheit: Frage an Mensch & weiter!"),
        ]),
    ]
    rx = 48
    rw = 480
    rgap = 32
    for title, sub, col, icon, items in roles:
        dark, main, soft, bdr = C[col]
        s.box(rx, 360, rw, 400, fill="#FFFFFF", stroke=bdr, sw=1.8, rx=16)
        s.add(f'  <rect x="{rx}" y="360" width="{rw}" height="74" rx="16" fill="{soft}"/>')
        s.add(f'  <rect x="{rx}" y="418" width="{rw}" height="16" fill="{soft}"/>')
        icon(rx + 42, 397, main)
        s.add(f'  <text x="{rx+78}" y="395" font-family="{FONT_SANS}" font-size="19" font-weight="800" fill="{dark}">{esc(title)}</text>')
        s.add(f'  <text x="{rx+78}" y="419" font-family="{FONT_SANS}" font-size="14" font-weight="600" fill="{C["muted"]}">{esc(sub)}</text>')
        iy = 466
        for h, b_txt in items:
            s.add(f'  <circle cx="{rx+34}" cy="{iy+6}" r="5.5" fill="{main}"/>')
            s.add(f'  <text x="{rx+52}" y="{iy+11}" font-family="{FONT_SANS}" font-size="16.5" font-weight="800" fill="{C["ink"]}">{esc(h)}</text>')
            s.add(f'  <text x="{rx+52}" y="{iy+36}" font-family="{FONT_SANS}" font-size="14.5" font-weight="500" fill="{C["body"]}">{esc(b_txt)}</text>')
            iy += 96
        rx += rw + rgap

    s.box(48, 786, 1504, 78, fill=C["teal"][2], stroke=C["teal"][1], sw=2.2, rx=14)
    s.add(f'  <text x="800" y="832" text-anchor="middle" font-family="{FONT_SANS}" font-size="18.5" font-weight="700" fill="{C["teal"][0]}">Kernprinzip beim Schichtwechsel: Jeder neue Schichtführer liest den echten Projektstand — niemals alte Chatverläufe.</text>')
    return s.render()


# =============================================================================
# SLIDE 09: DER WEG EINES TICKETS (6 STUFEN BIS ZUM MENSCHEN)
# =============================================================================
def build_s09_ticket_weg() -> str:
    s = Slide("s09")
    s.header(
        "HEUTE · QUALITÄTSFLUSS",
        "Der Weg eines Tickets: 6 Stufen bis zum Menschen",
        "Jede Aufgabe durchläuft fünf geprüfte Arbeitsschritte plus einen kalten Live-Test — erst danach wird sie integriert",
        "blue",
        "Folie 9 / 10",
    )

    # Main Pipeline Canvas (y=138..560)
    s.box(48, 138, 1504, 424, fill="#FFFFFF", stroke=C["border"], sw=1.6, rx=16)
    s.add(f'  <text x="80" y="176" font-family="{FONT_SANS}" font-size="16" font-weight="800" fill="{C["blue"][0]}">AUF EIGENEM ARBEITS-BRANCH — ERST NACH STUFE 6 „OK“ KOMMT DAS TICKET IN DIE SCHICHT</text>')

    stages = [
        ("1", "Verstehen", "Ursache &\nUmfang", "blue", False),
        ("2", "Test-Ziel", "Was muss\ngelten?", "blue", False),
        ("3", "Planen", "CODE-STOPP!\nErst Freigabe", "amber", True),
        ("4", "Bauen", "Code &\nschnelle Tests", "blue", False),
        ("5", "Nachweis", "Doku &\nErgebnisse", "blue", False),
        ("6", "Kalt-Check", "Live im\nEmulator!", "rose", True),
        ("✓", "Mensch", "Finale\nAbnahme", "emerald", True),
    ]
    x = 74
    w = 186
    gap = 25
    for i, (num, title, sub, col, strong) in enumerate(stages):
        dark, main, soft, bdr = C[col]
        s.box(x, 206, w, 204, fill=soft if strong else "#FFFFFF", stroke=main if strong else bdr, sw=2.6 if strong else 1.6, rx=16)
        s.add(f'  <circle cx="{x + w/2:.1f}" cy="248" r="24" fill="{main}"/>')
        s.add(f'  <text x="{x + w/2:.1f}" y="256" text-anchor="middle" font-family="{FONT_SANS}" font-size="20" font-weight="800" fill="#FFFFFF">{esc(num)}</text>')
        s.add(f'  <text x="{x + w/2:.1f}" y="308" text-anchor="middle" font-family="{FONT_SANS}" font-size="19" font-weight="800" fill="{dark}">{esc(title)}</text>')
        for li, line in enumerate(sub.split("\n")):
            s.add(f'  <text x="{x + w/2:.1f}" y="{340 + li*24}" text-anchor="middle" font-family="{FONT_SANS}" font-size="14.5" font-weight="600" fill="{C["body"]}">{esc(line)}</text>')
        if i < len(stages) - 1:
            s.arrow(x + w + 3, 308, x + w + gap - 3, 308, color=col, width=2.8)
        x += w + gap

    # Clean Bounce Arc from Stage 6 (x=1222) back to Stage 4 (x=800)
    s.path_arrow(
        "M 1222 410 L 1222 486 L 800 486 L 800 416",
        color="rose",
        label="Fehler gefunden? → Bis zu 3× automatisch zurück zum Nachbessern!",
        lx=1011, ly=514, width=3.2,
    )

    # Bottom 3 Big Simple Rules Cards (y=588..762)
    rules = [
        ("Kein Eigen-Review", "Umsetzer-KI und Prüfer-KI sind strikt getrennt — jede der 5 Stufen hat ihr eigenes Gate.", "indigo", s.icon_bot),
        ("Code erst nach Plan-OK", "Vor der ersten Zeile Produktivcode erzwingt Stufe 3 eine harte Freigabe.", "amber", s.icon_lock),
        ("Echter Live-Test vor Integration", "Stufe 6 startet die App im Emulator, prüft Screenshots und lässt nur Fehlerfreies durch.", "rose", s.icon_shield),
    ]
    rx = 48
    rw = 480
    for title, desc, col, icon in rules:
        dark, main, soft, bdr = C[col]
        s.box(rx, 588, rw, 170, fill="#FFFFFF", stroke=bdr, sw=1.8, rx=16)
        icon(rx + 42, 636, main)
        s.add(f'  <text x="{rx+78}" y="643" font-family="{FONT_SANS}" font-size="19" font-weight="800" fill="{dark}">{esc(title)}</text>')
        # Simple manual 2-line wrap
        words = desc.split()
        mid = len(words) // 2 + 1
        l1 = " ".join(words[:mid])
        l2 = " ".join(words[mid:])
        s.add(f'  <text x="{rx+28}" y="694" font-family="{FONT_SANS}" font-size="15" font-weight="500" fill="{C["body"]}">{esc(l1)}</text>')
        s.add(f'  <text x="{rx+28}" y="720" font-family="{FONT_SANS}" font-size="15" font-weight="500" fill="{C["body"]}">{esc(l2)}</text>')
        rx += rw + 32

    s.box(48, 786, 1504, 78, fill=C["blue"][2], stroke=C["blue"][1], sw=2.2, rx=14)
    s.add(f'  <text x="800" y="832" text-anchor="middle" font-family="{FONT_SANS}" font-size="18.5" font-weight="700" fill="{C["blue"][0]}">Ergebnis: Der Mensch bekommt in der Abnahme nur noch geprüfte, lauffähige und sauber dokumentierte Pakete.</text>')
    return s.render()


# =============================================================================
# SLIDE 10: UNSER 5-FACHES SICHERHEITSNETZ (SWISS CHEESE)
# =============================================================================
def build_s10_sicherheitsnetz() -> str:
    s = Slide("s10")
    s.header(
        "HEUTE · SICHERHEITSNETZ",
        "Unser 5-faches Sicherheitsnetz (Swiss-Cheese-Prinzip)",
        "Keine einzelne Prüfung ist perfekt — erst 5 unabhängige Barrieren mit verschiedenen Perspektiven stoppen Fehler sicher",
        "violet",
        "Folie 10 / 10",
    )

    # Top Hero Visual: Large, Clear Swiss Cheese Slices (y=138..498)
    s.box(48, 138, 1504, 360, fill="#FFFFFF", stroke=C["violet"][3], sw=1.8, rx=16)

    # Incoming defect label & ray blocked at slice 3
    s.add(f'  <rect x="74" y="294" width="126" height="54" rx="10" fill="{C["rose"][2]}" stroke="{C["rose"][1]}" stroke-width="2"/>')
    s.add(f'  <text x="137" y="317" text-anchor="middle" font-family="{FONT_SANS}" font-size="15" font-weight="800" fill="{C["rose"][0]}">Fehler /</text>')
    s.add(f'  <text x="137" y="337" text-anchor="middle" font-family="{FONT_SANS}" font-size="15" font-weight="800" fill="{C["rose"][0]}">Blindspot</text>')

    # Defect beam passes through holes of slice 1 & 2, stops hard at slice 3!
    s.add(f'  <line x1="202" y1="321" x2="778" y2="321" stroke="{C["rose"][1]}" stroke-width="5" stroke-dasharray="10,7" stroke-linecap="round"/>')
    s.add(f'  <circle cx="782" cy="321" r="10" fill="{C["rose"][1]}"/>')

    slices = [
        (316, "Schicht 1", "Umsetzer-KI", "Schnelle Tests\n& Compiler", "blue", 321),
        (566, "Schicht 2", "Gate-Prüfer", "Prüft alle\n5 Stufen", "amber", 321),
        (816, "Schicht 3", "Kalt-Review", "Ohne Vorwissen\n+ App-Emulator", "rose", 252),
        (1066, "Schicht 4", "Schicht & PO", "3×/Tag Check\n+ Mensch", "emerald", 360),
        (1316, "Schicht 5", "2 KI-Anbieter", "Gemini + Claude\n1× pro Woche", "violet", 268),
    ]
    for cx, num, title, sub, col, hole_y in slices:
        dark, main, soft, _ = C[col]
        # Cheese slice rectangle
        s.add(f'  <rect x="{cx-34}" y="206" width="68" height="196" rx="12" fill="{soft}" stroke="{main}" stroke-width="3"/>')
        # Single staggered hole
        s.add(f'  <circle cx="{cx}" cy="{hole_y}" r="14" fill="#FFFFFF" stroke="{main}" stroke-width="2.4"/>')
        # Top label
        s.add(f'  <text x="{cx}" y="186" text-anchor="middle" font-family="{FONT_SANS}" font-size="14" font-weight="800" fill="{dark}">{esc(num)}</text>')
        # Bottom title & sub
        s.add(f'  <text x="{cx}" y="432" text-anchor="middle" font-family="{FONT_SANS}" font-size="18" font-weight="800" fill="{dark}">{esc(title)}</text>')
        for li, line in enumerate(sub.split("\n")):
            s.add(f'  <text x="{cx}" y="{456 + li*20}" text-anchor="middle" font-family="{FONT_SANS}" font-size="14" font-weight="500" fill="{C["body"]}">{esc(line)}</text>')

    # Crisp STOPPED badge right above impact
    s.add(f'  <rect x="634" y="258" width="128" height="32" rx="16" fill="{C["rose"][1]}"/>')
    s.add(f'  <text x="698" y="279" text-anchor="middle" font-family="{FONT_SANS}" font-size="13.5" font-weight="800" fill="#FFFFFF">GESTOPPT!</text>')

    # Bottom: 3 Simple Test Rhythms (y=526..760)
    tiers = [
        ("Im Ticket (~30 Sek.)", "Gezielte schnelle Tests", "Prüfen sofort nur das geänderte Thema — kein Warten auf Riesen-Builds.", "blue"),
        ("Beim Schichtwechsel (3×/Tag)", "Integrations-Prüfung", "Prüft das Zusammenspiel der ganzen Schicht vor dem Zusammenführen.", "teal"),
        ("Einmal pro Woche", "Voll-Check mit 2 KI-Anbietern", "Alle Tests, Code-Qualität & gemeinsamer Blick von Google Gemini & Anthropic Claude.", "violet"),
    ]
    tx = 48
    tw = 480
    for when, title, desc, col in tiers:
        dark, main, soft, bdr = C[col]
        s.box(tx, 526, tw, 232, fill="#FFFFFF", stroke=bdr, sw=1.8, rx=16)
        s.add(f'  <rect x="{tx+24}" y="550" width="{max(160, len(when)*8.8+24):.0f}" height="30" rx="15" fill="{soft}" stroke="{bdr}"/>')
        s.add(f'  <text x="{tx+24 + max(160, len(when)*8.8+24)/2:.1f}" y="570" text-anchor="middle" font-family="{FONT_SANS}" font-size="13.5" font-weight="800" fill="{dark}">{esc(when)}</text>')
        s.add(f'  <text x="{tx+24}" y="622" font-family="{FONT_SANS}" font-size="21" font-weight="800" fill="{dark}">{esc(title)}</text>')
        # Clean 2-3 line wrap
        words = desc.split()
        lines, cur = [], []
        for w_tok in words:
            if len(" ".join(cur + [w_tok])) > 44:
                lines.append(" ".join(cur))
                cur = [w_tok]
            else:
                cur.append(w_tok)
        if cur:
            lines.append(" ".join(cur))
        for li, line in enumerate(lines):
            s.add(f'  <text x="{tx+24}" y="{660 + li*26}" font-family="{FONT_SANS}" font-size="15.5" font-weight="500" fill="{C["body"]}">{esc(line)}</text>')
        tx += tw + 32

    s.box(48, 786, 1504, 78, fill=C["violet"][2], stroke=C["violet"][1], sw=2.2, rx=14)
    s.add(f'  <text x="800" y="832" text-anchor="middle" font-family="{FONT_SANS}" font-size="18.5" font-weight="700" fill="{C["violet"][0]}">Sicherheit durch Vielfalt: Verschiedene Rollen, frische Kontexte, echte App-Tests &amp; zwei KI-Anbieter.</text>')
    return s.render()


SLIDES = [
    ("01_evolution_ueberblick",   "A", "01 · Überblick",       "Unsere Reise in 5 Stufen (Juni – Nov. 2026)", build_s01_overview,
     "Einstiegsfolie: Zeigt den gesamten Entwicklungsbogen in 5 Monaten und wie aus 80 Anforderungen 1.265 verifizierte Anforderungen wurden."),
    ("02_stufe1_vibe_coding",     "A", "02 · Vibe-Coding",     "Stufe 1: Vibe-Coding — Einfach mal drauflos", build_s02_vibe,
     "Startpunkt ohne Prozess: Superschnell für Wegwerf-Prototypen, aber riskant für gewachsene Apps wegen stiller Regressionen und fehlendem Gedächtnis."),
    ("03_stufe2_augenmass",       "A", "03 · Augenmaß",        "Stufe 2: Augenmaß — Mensch + Zweitmeinung", build_s03_augenmass,
     "Der erste große Qualitätssprung: Ein zweiter, unabhängiger Agent kritisiert den ersten — aber ohne schriftliche Anforderungen bleibt es Bauchgefühl."),
    ("04_stufe3_sorgfaeltig",     "A", "04 · Sorgfältig",      "Stufe 3: Sorgfältig — Ticket für Ticket getestet", build_s04_sorgfaeltig,
     "Juni–August 2026: Mit Jira-Tickets, erster requirements.md und Tests entsteht sauberes Handwerk — aber der Mensch ist der zeitliche Flaschenhals."),
    ("05_stufe4a_hoechstmass",    "A", "05 · Höchstmaß (4a)",  "Stufe 4a: Höchstmaß — Bewusste Stolpersteine & Gates", build_s05_hoechstmass_geburt,
     "September 2026: Geburt von Agile Agentic ASPICE (AAA) mit 5 festen Stufen, Gate-3 Code-Stopp und Vier-Augen-Prinzip — 3× mehr Durchsatz."),
    ("06_stufe4b_skalierung",     "A", "06 · Härtung (4b)",    "Stufe 4b: Höchstmaß gehärtet — Parallelität & Kalt-Review", build_s06_hoechstmass_haertung,
     "Oktober–Mitte Nov. 2026: 9 Kernregeln statt 84+, parallele Arbeitskopien, eigener Android-Emulator und kalte End-Prüfung ohne Vorwissen."),
    ("07_stufe5_schichtbetrieb",  "A", "07 · Schichtbetrieb",  "Stufe 5: Schichtbetrieb nach Höchstmaß (AKTIV)", build_s07_schichtbetrieb,
     "Seit Mitte Nov. 2026: Nahtloser 24/7-Takt auf 3 Spuren, Live-Emulator-Prüfung, smarte Modellwahl (Flash vs. Pro/Neon) und ehrliche Abwägung der Trade-offs."),
    ("08_heute_takt_und_rollen",  "B", "08 · Takt & Rollen",   "Heute im Betrieb: 3 Schichten & klare Rollen", build_s08_takt_rollen,
     "Zeigt einfach und klar die 3 Schichten (05/15/22 Uhr) und die Aufgabenteilung zwischen Mensch (PO), Schichtführer und KI-Teams."),
    ("09_heute_weg_eines_tickets","B", "09 · Weg des Tickets", "Der Weg eines Tickets: 6 Stufen bis zum Menschen", build_s09_ticket_weg,
     "Visualisiert die 6 klaren Schritte von der Analyse über den Code-Stopp bis zum kalten Live-Check inkl. automatischer Nachbesserungs-Schleife."),
    ("10_heute_sicherheitsnetz",  "B", "10 · Sicherheitsnetz", "Unser 5-faches Sicherheitsnetz (Swiss Cheese)", build_s10_sicherheitsnetz,
     "Erklärt das Swiss-Cheese-Prinzip mit 5 unabhängigen Prüfschichten sowie die 3 Test-Rhythmen (Ticket, Schichtwechsel, Wochen-Check mit Gemini & Claude)."),
]


def build_html(svgs: dict[str, str], for_artifact: bool = True) -> str:
    meta_json = json.dumps([
        {"id": slug, "group": grp, "short": short, "title": title, "note": note}
        for slug, grp, short, title, _, note in SLIDES
    ], ensure_ascii=False)

    panels = []
    for idx, (slug, _, _, _, _, _) in enumerate(SLIDES):
        hidden = "" if idx == 0 else ' style="display:none"'
        panels.append(f'<div class="slide-panel" data-panel="{slug}"{hidden}>\n{svgs[slug]}\n</div>')
    panels_html = "\n".join(panels)

    if for_artifact:
        head_extra = '<script src="https://www.gstatic.com/antigravity/web/dev/tailwindcss.min.js"></script>'
        root_vars = ""
        body_cls = 'class="bg-[var(--background)] text-[var(--foreground)] antialiased min-h-screen"'
    else:
        head_extra = ""
        root_vars = """
    :root {
      --background: #0f172a; --card: #1e293b; --foreground: #f8fafc;
      --muted-foreground: #94a3b8; --border: #334155;
      --primary: #0d9488; --primary-foreground: #ffffff;
      --secondary: #334155; --secondary-foreground: #f1f5f9;
    }
    @media (prefers-color-scheme: light) {
      :root {
        --background: #f1f5f9; --card: #ffffff; --foreground: #0f172a;
        --muted-foreground: #475569; --border: #cbd5e1;
        --primary: #0d9488; --primary-foreground: #ffffff;
        --secondary: #e2e8f0; --secondary-foreground: #0f172a;
      }
    }
"""
        body_cls = 'style="background:var(--background);color:var(--foreground);margin:0;font-family:\'Inter\',system-ui,sans-serif;"'

    return f'''<!DOCTYPE html>
<html lang="de">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Vorgehensmodell Präsentation (10 Folien) — Vom Vibe-Coding zum Schichtbetrieb</title>
  {head_extra}
  <style>
    {root_vars}
    * {{ box-sizing: border-box; }}
    .shell {{ max-width: 1520px; margin: 0 auto; padding: 14px 18px 24px; }}
    .topbar {{
      display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px;
      padding: 12px 16px; border-radius: 14px; background: var(--card); border: 1px solid var(--border);
      margin-bottom: 12px;
    }}
    .topbar h1 {{ margin: 0; font-size: 17px; font-weight: 800; color: var(--foreground); }}
    .topbar p {{ margin: 2px 0 0; font-size: 13px; color: var(--muted-foreground); }}
    .actions {{ display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }}
    .btn {{
      padding: 7px 12px; border-radius: 9px; font-size: 12.5px; font-weight: 600; cursor: pointer;
      border: 1px solid var(--border); background: var(--secondary); color: var(--secondary-foreground);
    }}
    .btn:hover {{ opacity: 0.9; }}
    .btn-primary {{ background: var(--primary); color: var(--primary-foreground); border-color: transparent; }}
    .nav-strip {{
      display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px;
      padding: 10px 12px; border-radius: 12px; background: var(--card); border: 1px solid var(--border);
      align-items: center;
    }}
    .sep {{ font-size: 12px; font-weight: 700; color: var(--muted-foreground); padding: 0 6px; }}
    .tab {{
      padding: 6px 11px; border-radius: 8px; font-size: 12.5px; font-weight: 600; cursor: pointer;
      border: 1px solid var(--border); background: var(--background); color: var(--foreground);
    }}
    .tab.active {{ background: var(--primary); color: var(--primary-foreground); border-color: transparent; }}
    .stage {{
      background: var(--card); border: 1px solid var(--border); border-radius: 14px; padding: 14px;
    }}
    .slide-frame {{
      border-radius: 10px; overflow: hidden; border: 1px solid #CBD5E1; background: #F8FAFC;
    }}
    .slide-panel svg {{ display: block; width: 100%; height: auto; }}
    .speaker-bar {{
      display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px;
      margin-top: 12px; padding-top: 10px; border-top: 1px solid var(--border);
    }}
    .note {{ font-size: 13.5px; color: var(--muted-foreground); flex: 1; min-width: 260px; }}
    .note strong {{ color: var(--foreground); }}
    .pager {{ display: flex; align-items: center; gap: 8px; }}
    .counter {{ font-size: 13px; font-weight: 700; color: var(--foreground); min-width: 58px; text-align: center; }}
  </style>
</head>
<body {body_cls}>
  <div class="shell">
    <div class="topbar">
      <div>
        <h1>Vom Vibe-Coding zum Schichtbetrieb nach Höchstmaß</h1>
        <p>Kompakte 10-Folien-Präsentation · Pfeiltasten (← / →) zum Blättern · Direkt-Export als SVG oder HD-PNG</p>
      </div>
      <div class="actions">
        <button class="btn" id="btn-svg">⬇ SVG</button>
        <button class="btn btn-primary" id="btn-png">⬇ PNG (2400×1350)</button>
      </div>
    </div>

    <div class="nav-strip" id="nav-strip"></div>

    <div class="stage">
      <div class="slide-frame">
        {panels_html}
      </div>
      <div class="speaker-bar">
        <div class="note" id="speaker-note"></div>
        <div class="pager">
          <button class="btn" id="btn-prev">← Zurück</button>
          <span class="counter" id="counter">1 / 10</span>
          <button class="btn btn-primary" id="btn-next">Weiter →</button>
        </div>
      </div>
    </div>
  </div>

  <script>
    const SLIDES = {meta_json};
    let idx = 0;

    function renderNav() {{
      const nav = document.getElementById('nav-strip');
      nav.innerHTML = '';
      let lastGrp = '';
      SLIDES.forEach((s, i) => {{
        if (s.group !== lastGrp) {{
          const lbl = document.createElement('span');
          lbl.className = 'sep';
          lbl.textContent = s.group === 'A' ? 'Die Reise:' : '|  Heute:';
          nav.appendChild(lbl);
          lastGrp = s.group;
        }}
        const b = document.createElement('button');
        b.className = 'tab' + (i === idx ? ' active' : '');
        b.textContent = s.short;
        b.onclick = () => show(i);
        nav.appendChild(b);
      }});
    }}

    function show(newIdx) {{
      idx = (newIdx + SLIDES.length) % SLIDES.length;
      const cur = SLIDES[idx];
      document.querySelectorAll('.slide-panel').forEach((p) => {{
        p.style.display = p.dataset.panel === cur.id ? 'block' : 'none';
      }});
      document.getElementById('counter').textContent = `${{idx + 1}} / ${{SLIDES.length}}`;
      document.getElementById('speaker-note').innerHTML = `<strong>Sprecher-Notiz:</strong> ${{cur.note}}`;
      renderNav();
    }}

    document.getElementById('btn-prev').onclick = () => show(idx - 1);
    document.getElementById('btn-next').onclick = () => show(idx + 1);
    document.addEventListener('keydown', (e) => {{
      if (e.key === 'ArrowRight' || e.key === ' ') {{ e.preventDefault(); show(idx + 1); }}
      else if (e.key === 'ArrowLeft') {{ e.preventDefault(); show(idx - 1); }}
    }});

    function activeSvgStr() {{
      const svg = document.querySelector(`.slide-panel[data-panel="${{SLIDES[idx].id}}"] svg`);
      const clone = svg.cloneNode(true);
      clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
      return new XMLSerializer().serializeToString(clone);
    }}

    document.getElementById('btn-svg').onclick = () => {{
      const blob = new Blob([activeSvgStr()], {{ type: 'image/svg+xml;charset=utf-8' }});
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a'); a.href = url; a.download = `${{SLIDES[idx].id}}.svg`; a.click();
      URL.revokeObjectURL(url);
    }};

    document.getElementById('btn-png').onclick = () => {{
      const img = new Image();
      const blob = new Blob([activeSvgStr()], {{ type: 'image/svg+xml;charset=utf-8' }});
      const url = URL.createObjectURL(blob);
      img.onload = () => {{
        const c = document.createElement('canvas'); c.width = 2400; c.height = 1350;
        const ctx = c.getContext('2d');
        ctx.fillStyle = '#F8FAFC'; ctx.fillRect(0, 0, c.width, c.height);
        ctx.drawImage(img, 0, 0, c.width, c.height);
        URL.revokeObjectURL(url);
        c.toBlob((pb) => {{
          const pu = URL.createObjectURL(pb);
          const a = document.createElement('a'); a.href = pu; a.download = `${{SLIDES[idx].id}}.png`; a.click();
          URL.revokeObjectURL(pu);
        }}, 'image/png');
      }};
      img.src = url;
    }};

    show(0);
  </script>
</body>
</html>
'''


def main():
    # Clean old slides in OUT_DIR so no stale files remain
    for old in list(OUT_DIR.glob("*.svg")) + list(OUT_DIR.glob("*.png")):
        old.unlink()

    svgs: dict[str, str] = {}
    for slug, grp, short, title, builder, note in SLIDES:
        svg_str = builder()
        svgs[slug] = svg_str
        sp = OUT_DIR / f"{slug}.svg"
        pp = OUT_DIR / f"{slug}.png"
        sp.write_text(svg_str, encoding="utf-8")
        subprocess.run(
            [
                "inkscape", str(sp),
                "--export-type=png", "--export-width=2400", "--export-height=1350",
                f"--export-filename={pp}",
            ],
            check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        )
        print(f"Rendered {pp.name} ({pp.stat().st_size // 1024} KB)", flush=True)

    (OUT_DIR / "index.html").write_text(build_html(svgs, for_artifact=False), encoding="utf-8")
    print(f"Wrote {OUT_DIR / 'index.html'}")


if __name__ == "__main__":
    main()
