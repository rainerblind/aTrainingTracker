#!/usr/bin/env python3
"""
aTrainingTracker: Reconstructed Android Adaptive Icon & Pro Supporter Badge Generator (REQ-UI-444 / ATT-1482 / ATT-1406)

Converts master vector geometry from Bilder/logo.fig and the approved Option A design (gen_cervelo_tiers.py /
docs/attachments/att_1406_cervelo_tiers_board.png) directly to:
1. app/src/main/res/drawable/ic_launcher_foreground.xml (Standard VectorDrawable, 108dp canvas, r ~ 34.0dp)
2. app/src/main/res/drawable/ic_launcher_monochrome.xml (Single-color #000000 silhouette for Material You)
3. app/src/main/res/drawable/ic_launcher_foreground_pioneer.xml (Pioneer Edition Option A)
4. app/src/main/res/drawable/ic_launcher_foreground_bronze.xml (Bronze Supporter Option A)
5. app/src/main/res/drawable/ic_launcher_foreground_silver.xml (Silver Pro-Supporter Option A)
6. app/src/main/res/drawable/ic_launcher_foreground_gold.xml (Gold Pro-Supporter Option A)
7. app/src/main/res/drawable/ic_launcher_foreground_diamond.xml (Diamond Sponsor Option A)
8. app/src/main/res/drawable/ic_supporter_emblem_{pioneer,bronze,silver,gold,diamond}.xml (24dp Cockpit emblems)
9. images/play_store_icon_512.png (512x512 Play Store master icon with safe-zone padding)
10. docs/engineering/walkthroughs/img/att1482_reconstructed_preview.png (Visual preview of ic_launcher_foreground.xml)
11. docs/engineering/walkthroughs/img/att1406_reconstructed_tiers_preview.png (6-variant comparison board)
And restores full-bleed logo_512.png from drawable-xxxhdpi/logo_512.png (sha256: f84c6251...).
"""

import html
import io
import math
import os
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
from PIL import Image

INKSCAPE_BIN = "/snap/bin/inkscape" if os.path.exists("/snap/bin/inkscape") else "inkscape"


def bspline_quad_to_cubic_bezier(pts):
    """
    Converts xfig closed approximated spline control points (logo.fig:65, s = -1.000)
    into an exact degree-elevated cubic Bézier (C) path matching xfig / gen_cervelo_tiers.py.
    """
    n = len(pts)
    mids = [
        ((pts[i][0] + pts[(i + 1) % n][0]) / 2.0, (pts[i][1] + pts[(i + 1) % n][1]) / 2.0)
        for i in range(n)
    ]
    commands = [f"M {mids[0][0]:.2f},{mids[0][1]:.2f}"]
    for i in range(n):
        m0 = mids[i]
        p = pts[(i + 1) % n]
        m1 = mids[(i + 1) % n]
        c1_x = m0[0] + (2.0 / 3.0) * (p[0] - m0[0])
        c1_y = m0[1] + (2.0 / 3.0) * (p[1] - m0[1])
        c2_x = m1[0] + (2.0 / 3.0) * (p[0] - m1[0])
        c2_y = m1[1] + (2.0 / 3.0) * (p[1] - m1[1])
        commands.append(f"C {c1_x:.2f},{c1_y:.2f} {c2_x:.2f},{c2_y:.2f} {m1[0]:.2f},{m1[1]:.2f}")
    commands.append("Z")
    return " ".join(commands)


def svg_path_to_absolute_mlcz(path_d):
    """
    Converts any SVG path (including relative m, l, h, v, c, q, z commands from Inkscape)
    into strictly uppercase absolute M, L, C, Z commands with 2-decimal coordinates.
    """
    token_re = re.compile(r"([A-Za-z])|([-+]?\d*\.?\d+(?:[eE][-+]?\d+)?)")
    raw_tokens = token_re.findall(path_d)
    tokens = [cmd if cmd else float(num) for cmd, num in raw_tokens]

    out = []
    idx = 0
    cmd = None
    cx, cy = 0.0, 0.0
    sx, sy = 0.0, 0.0

    while idx < len(tokens):
        if isinstance(tokens[idx], str):
            cmd = tokens[idx]
            idx += 1
            if cmd in ("Z", "z"):
                out.append("Z")
                cx, cy = sx, sy
                continue

        if cmd == "M":
            cx, cy = tokens[idx], tokens[idx + 1]
            sx, sy = cx, cy
            out.append(f"M {cx:.2f},{cy:.2f}")
            idx += 2
            cmd = "L"
        elif cmd == "m":
            cx += tokens[idx]
            cy += tokens[idx + 1]
            sx, sy = cx, cy
            out.append(f"M {cx:.2f},{cy:.2f}")
            idx += 2
            cmd = "l"
        elif cmd == "L":
            cx, cy = tokens[idx], tokens[idx + 1]
            out.append(f"L {cx:.2f},{cy:.2f}")
            idx += 2
        elif cmd == "l":
            cx += tokens[idx]
            cy += tokens[idx + 1]
            out.append(f"L {cx:.2f},{cy:.2f}")
            idx += 2
        elif cmd == "H":
            cx = tokens[idx]
            out.append(f"L {cx:.2f},{cy:.2f}")
            idx += 1
        elif cmd == "h":
            cx += tokens[idx]
            out.append(f"L {cx:.2f},{cy:.2f}")
            idx += 1
        elif cmd == "V":
            cy = tokens[idx]
            out.append(f"L {cx:.2f},{cy:.2f}")
            idx += 1
        elif cmd == "v":
            cy += tokens[idx]
            out.append(f"L {cx:.2f},{cy:.2f}")
            idx += 1
        elif cmd == "C":
            x1, y1 = tokens[idx], tokens[idx + 1]
            x2, y2 = tokens[idx + 2], tokens[idx + 3]
            cx, cy = tokens[idx + 4], tokens[idx + 5]
            out.append(f"C {x1:.2f},{y1:.2f} {x2:.2f},{y2:.2f} {cx:.2f},{cy:.2f}")
            idx += 6
        elif cmd == "c":
            x1, y1 = cx + tokens[idx], cy + tokens[idx + 1]
            x2, y2 = cx + tokens[idx + 2], cy + tokens[idx + 3]
            cx, cy = cx + tokens[idx + 4], cy + tokens[idx + 5]
            out.append(f"C {x1:.2f},{y1:.2f} {x2:.2f},{y2:.2f} {cx:.2f},{cy:.2f}")
            idx += 6
        elif cmd == "Q":
            qx, qy = tokens[idx], tokens[idx + 1]
            ex, ey = tokens[idx + 2], tokens[idx + 3]
            c1x, c1y = cx + (2.0 / 3.0) * (qx - cx), cy + (2.0 / 3.0) * (qy - cy)
            c2x, c2y = ex + (2.0 / 3.0) * (qx - ex), ey + (2.0 / 3.0) * (qy - ey)
            cx, cy = ex, ey
            out.append(f"C {c1x:.2f},{c1y:.2f} {c2x:.2f},{c2y:.2f} {cx:.2f},{cy:.2f}")
            idx += 4
        elif cmd == "q":
            qx, qy = cx + tokens[idx], cy + tokens[idx + 1]
            ex, ey = cx + tokens[idx + 2], cy + tokens[idx + 3]
            c1x, c1y = cx + (2.0 / 3.0) * (qx - cx), cy + (2.0 / 3.0) * (qy - cy)
            c2x, c2y = ex + (2.0 / 3.0) * (qx - ex), ey + (2.0 / 3.0) * (qy - ey)
            cx, cy = ex, ey
            out.append(f"C {c1x:.2f},{c1y:.2f} {c2x:.2f},{c2y:.2f} {cx:.2f},{cy:.2f}")
            idx += 4
        else:
            idx += 1

    return " ".join(out)


def text_to_vector_path(text_str, x=54.0, y=79.7, font_size=4.2, letter_spacing=0.35):
    """
    Uses Inkscape (--export-text-to-path) to convert a ribbon label ('★ PIONEER ★',
    'SUPPORTER', 'PRO-SUPPORTER', '◆ SPONSOR ◆') into an exact VectorDrawable pathData string.
    """
    escaped = html.escape(text_str)
    svg = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="108" height="108">
    <text x="{x}" y="{y}" font-family="DejaVu Sans, sans-serif" font-weight="bold" font-size="{font_size}" letter-spacing="{letter_spacing}" fill="#FFFFFF" text-anchor="middle">{escaped}</text>
    </svg>"""
    proc = subprocess.run(
        [INKSCAPE_BIN, "--pipe", "--export-type=svg", "--export-text-to-path", "--export-plain-svg"],
        input=svg.encode("utf-8"),
        capture_output=True,
        check=True,
    )
    root = ET.fromstring(proc.stdout)
    paths = []
    for el in root.iter():
        if el.tag.endswith("path") and el.attrib.get("d"):
            paths.append(svg_path_to_absolute_mlcz(el.attrib["d"]))
    return " ".join(paths)


def polygon_to_path(points_list):
    """Converts [(x0, y0), (x1, y1), ...] to 'M x0,y0 L x1,y1 ... Z'."""
    coords = [f"{x:.2f},{y:.2f}" for x, y in points_list]
    return "M " + " L ".join(coords) + " Z"


def circle_to_path(cx, cy, r):
    """Converts a circle to two 180-degree SVG/VectorDrawable arcs."""
    return f"M {cx:.2f},{cy - r:.2f} A {r:.2f},{r:.2f} 0 1,0 {cx:.2f},{cy + r:.2f} A {r:.2f},{r:.2f} 0 1,0 {cx:.2f},{cy - r:.2f} Z"


def pill_to_path(cx, y_top, width, height):
    """
    Generates a stadium pill path centered horizontally at cx, with semicircular end caps
    so all control points and arc endpoints stay strictly within the safe-zone radius.
    """
    r = height / 2.0
    half_straight = (width / 2.0) - r
    x_left = cx - half_straight
    x_right = cx + half_straight
    y_bot = y_top + height
    return (
        f"M {x_left:.2f},{y_top:.2f} "
        f"L {x_right:.2f},{y_top:.2f} "
        f"A {r:.2f},{r:.2f} 0 0,1 {x_right:.2f},{y_bot:.2f} "
        f"L {x_left:.2f},{y_bot:.2f} "
        f"A {r:.2f},{r:.2f} 0 0,1 {x_left:.2f},{y_top:.2f} Z"
    )


def build_top_emblem_xml(kind, cx=67.6, cy=30.2, scale_factor=1.0):
    """
    Generates Android VectorDrawable <path> elements matching build_top_emblem() in gen_cervelo_tiers.py:
      - 'pioneer_star': Emerald & Gold Heritage Medallion + dotted inner ring + 5-pointed gold star
      - 'medal_bronze', 'medal_silver', 'medal_gold': V-shaped two-tone neck ribbon + double medal disc + 5-pointed star
      - 'diamond': Multi-faceted brilliant-cut cyan/ice diamond + girdle line + 4-ray sparkle
    """
    s = scale_factor
    lines = []

    if kind.startswith("medal_"):
        if kind == "medal_bronze":
            c_outer, c_inner, c_star, c_stroke = "#CD7F32", "#A05A2C", "#FFE0B2", "#6E3B16"
            rib1, rib2 = "#1E3A8A", "#B91C1C"
        elif kind == "medal_silver":
            c_outer, c_inner, c_star, c_stroke = "#E2E8F0", "#94A3B8", "#FFFFFF", "#475569"
            rib1, rib2 = "#1E3A8A", "#2563EB"
        else:  # medal_gold
            c_outer, c_inner, c_star, c_stroke = "#FFD700", "#F59E0B", "#FFFBEB", "#92400E"
            rib1, rib2 = "#B91C1C", "#1E3A8A"

        # Left & right V-shaped neck ribbon polygons
        rib_left = polygon_to_path([
            (cx - 3.8 * s, cy - 6.0 * s),
            (cx - 1.0 * s, cy - 6.0 * s),
            (cx + 0.2 * s, cy - 1.2 * s),
            (cx - 2.0 * s, cy - 1.2 * s),
        ])
        rib_right = polygon_to_path([
            (cx + 3.8 * s, cy - 6.0 * s),
            (cx + 1.0 * s, cy - 6.0 * s),
            (cx - 0.2 * s, cy - 1.2 * s),
            (cx + 2.0 * s, cy - 1.2 * s),
        ])
        lines.append(f'    <path\n        android:fillColor="{rib1}"\n        android:pathData="{rib_left}" />')
        lines.append(f'    <path\n        android:fillColor="{rib2}"\n        android:pathData="{rib_right}" />')

        # Outer & inner medal discs
        mc_y = cy + 1.2 * s
        lines.append(
            f'    <path\n        android:fillColor="{c_outer}"\n        android:strokeColor="{c_stroke}"\n'
            f'        android:strokeWidth="{0.65 * s:.2f}"\n        android:pathData="{circle_to_path(cx, mc_y, 5.0 * s)}" />'
        )
        lines.append(
            f'    <path\n        android:fillColor="{c_inner}"\n        android:strokeColor="{c_star}"\n'
            f'        android:strokeWidth="{0.35 * s:.2f}"\n        android:pathData="{circle_to_path(cx, mc_y, 3.6 * s)}" />'
        )

        # 5-pointed star in center of medal
        star_pts = []
        for k in range(10):
            ang = -math.pi / 2.0 + k * math.pi / 5.0
            r = (2.4 if k % 2 == 0 else 1.0) * s
            star_pts.append((cx + r * math.cos(ang), mc_y + r * math.sin(ang)))
        lines.append(f'    <path\n        android:fillColor="{c_star}"\n        android:pathData="{polygon_to_path(star_pts)}" />')

    elif kind == "diamond":
        top_y = cy - 4.8 * s
        girdle_y = cy - 1.4 * s
        tip_y = cy + 6.0 * s
        w_top = 4.0 * s
        w_girdle = 6.4 * s

        # Outer diamond base silhouette
        outer_poly = polygon_to_path([
            (cx - w_top, top_y),
            (cx + w_top, top_y),
            (cx + w_girdle, girdle_y),
            (cx, tip_y),
            (cx - w_girdle, girdle_y),
        ])
        lines.append(
            f'    <path\n        android:fillColor="#38BDF8"\n        android:strokeColor="#0284C7"\n'
            f'        android:strokeWidth="{0.65 * s:.2f}"\n        android:strokeLineJoin="round"\n        android:pathData="{outer_poly}" />'
        )
        # Crown table (brightest ice-white facet)
        crown_table = polygon_to_path([
            (cx - 2.1 * s, top_y),
            (cx + 2.1 * s, top_y),
            (cx + 2.8 * s, girdle_y),
            (cx - 2.8 * s, girdle_y),
        ])
        lines.append(f'    <path\n        android:fillColor="#E0F2FE"\n        android:pathData="{crown_table}" />')
        # Left crown facet
        crown_left = polygon_to_path([
            (cx - w_top, top_y),
            (cx - 2.1 * s, top_y),
            (cx - 2.8 * s, girdle_y),
            (cx - w_girdle, girdle_y),
        ])
        lines.append(f'    <path\n        android:fillColor="#BAE6FD"\n        android:pathData="{crown_left}" />')
        # Right crown facet
        crown_right = polygon_to_path([
            (cx + 2.1 * s, top_y),
            (cx + w_top, top_y),
            (cx + w_girdle, girdle_y),
            (cx + 2.8 * s, girdle_y),
        ])
        lines.append(f'    <path\n        android:fillColor="#7DD3FC"\n        android:pathData="{crown_right}" />')
        # Center pavilion facet
        pav_center = polygon_to_path([
            (cx - 2.8 * s, girdle_y),
            (cx + 2.8 * s, girdle_y),
            (cx, tip_y),
        ])
        lines.append(f'    <path\n        android:fillColor="#7DD3FC"\n        android:pathData="{pav_center}" />')
        # Left pavilion facet
        pav_left = polygon_to_path([
            (cx - w_girdle, girdle_y),
            (cx - 2.8 * s, girdle_y),
            (cx, tip_y),
        ])
        lines.append(f'    <path\n        android:fillColor="#BAE6FD"\n        android:pathData="{pav_left}" />')
        # Right pavilion facet (deepest cyan-blue)
        pav_right = polygon_to_path([
            (cx + 2.8 * s, girdle_y),
            (cx + w_girdle, girdle_y),
            (cx, tip_y),
        ])
        lines.append(f'    <path\n        android:fillColor="#0EA5E9"\n        android:pathData="{pav_right}" />')
        # Crisp white girdle line
        lines.append(
            f'    <path\n        android:strokeColor="#FFFFFF"\n        android:strokeWidth="{0.45 * s:.2f}"\n'
            f'        android:pathData="M {cx - w_girdle:.2f},{girdle_y:.2f} L {cx + w_girdle:.2f},{girdle_y:.2f}" />'
        )
        # Top-right 4-ray sparkle (nested cleanly inside the r=34dp border ring)
        sp_x = cx + 5.2 * s
        sp_y = top_y + 1.0 * s
        sp_r = 1.1 * s
        lines.append(
            f'    <path\n        android:strokeColor="#0284C7"\n        android:strokeWidth="{0.60 * s:.2f}"\n'
            f'        android:strokeLineCap="round"\n'
            f'        android:pathData="M {sp_x:.2f},{sp_y - sp_r:.2f} L {sp_x:.2f},{sp_y + sp_r:.2f} M {sp_x - sp_r:.2f},{sp_y:.2f} L {sp_x + sp_r:.2f},{sp_y:.2f}" />'
        )

    elif kind == "pioneer_star":
        pc_y = cy + 0.5 * s
        # Emerald medallion with gold border
        lines.append(
            f'    <path\n        android:fillColor="#065F46"\n        android:strokeColor="#F59E0B"\n'
            f'        android:strokeWidth="{0.80 * s:.2f}"\n        android:pathData="{circle_to_path(cx, pc_y, 5.6 * s)}" />'
        )
        # Inner gold heritage ring (12 dashed segments around r = 4.3 * s to emulate stroke-dasharray in VectorDrawable)
        dash_cmds = []
        r_ring = 4.3 * s
        for d_idx in range(12):
            a1 = d_idx * (2.0 * math.pi / 12.0)
            a2 = a1 + (2.0 * math.pi / 20.0)
            x1, y1 = cx + r_ring * math.cos(a1), pc_y + r_ring * math.sin(a1)
            x2, y2 = cx + r_ring * math.cos(a2), pc_y + r_ring * math.sin(a2)
            dash_cmds.append(f"M {x1:.2f},{y1:.2f} L {x2:.2f},{y2:.2f}")
        lines.append(
            f'    <path\n        android:strokeColor="#FDE68A"\n        android:strokeWidth="{0.40 * s:.2f}"\n'
            f'        android:strokeLineCap="round"\n        android:pathData="{" ".join(dash_cmds)}" />'
        )
        # 5-pointed gold heritage star
        star_pts = []
        for k in range(10):
            ang = -math.pi / 2.0 + k * math.pi / 5.0
            r = (3.2 if k % 2 == 0 else 1.35) * s
            star_pts.append((cx + r * math.cos(ang), pc_y + r * math.sin(ang)))
        lines.append(f'    <path\n        android:fillColor="#FBBF24"\n        android:pathData="{polygon_to_path(star_pts)}" />')

    return "\n".join(lines)


TIER_CONFIGS = {
    "pioneer": {
        "title": "2. Pioneer (Early Adopter)",
        "subtitle": "ATT-51 Automatic Lifetime Unlock • Heritage Emblem",
        "chip": "PIONEER (SINCE 2011)",
        "chip_color": "#10B981",
        "ring_color": "#10B981",
        "emblem_kind": "pioneer_star",
        "ribbon_text": "★ PIONEER ★",
        "ribbon_bg": "#047857",
        "ribbon_border": "#F59E0B",
        "ribbon_fg": "#FDE68A",
        "ribbon_width": 42.0,
        "font_size": 4.1,
        "letter_spacing": 0.30,
    },
    "bronze": {
        "title": "3. Bronze Supporter",
        "subtitle": "Bronze Medal at Top-Right + Bronze Supporter Ribbon",
        "chip": "BRONZE SUPPORTER",
        "chip_color": "#D97706",
        "ring_color": "#CD7F32",
        "emblem_kind": "medal_bronze",
        "ribbon_text": "SUPPORTER",
        "ribbon_bg": "#9A3412",
        "ribbon_border": "#CD7F32",
        "ribbon_fg": "#FEF3C7",
        "ribbon_width": 42.0,
        "font_size": 4.2,
        "letter_spacing": 0.40,
    },
    "silver": {
        "title": "4. Silver Pro-Supporter",
        "subtitle": "Silver Medal at Top-Right + Silver PRO-SUPPORTER Ribbon",
        "chip": "SILVER PRO-SUPPORTER",
        "chip_color": "#94A3B8",
        "ring_color": "#94A3B8",
        "emblem_kind": "medal_silver",
        "ribbon_text": "PRO-SUPPORTER",
        "ribbon_bg": "#475569",
        "ribbon_border": "#CBD5E1",
        "ribbon_fg": "#F8FAFC",
        "ribbon_width": 45.0,
        "font_size": 3.85,
        "letter_spacing": 0.22,
    },
    "gold": {
        "title": "5. Gold Pro-Supporter",
        "subtitle": "Gold Medal at Top-Right + 24K Gold PRO-SUPPORTER Ribbon",
        "chip": "GOLD PRO-SUPPORTER",
        "chip_color": "#F59E0B",
        "ring_color": "#F59E0B",
        "emblem_kind": "medal_gold",
        "ribbon_text": "PRO-SUPPORTER",
        "ribbon_bg": "#B45309",
        "ribbon_border": "#FDE047",
        "ribbon_fg": "#FFFBEB",
        "ribbon_width": 45.0,
        "font_size": 3.85,
        "letter_spacing": 0.22,
    },
    "diamond": {
        "title": "6. Diamond Sponsor",
        "subtitle": "Faceted Brilliant Diamond at Top-Right + SPONSOR Ribbon",
        "chip": "DIAMOND SPONSOR",
        "chip_color": "#38BDF8",
        "ring_color": "#0284C7",
        "emblem_kind": "diamond",
        "ribbon_text": "◆ SPONSOR ◆",
        "ribbon_bg": "#0369A1",
        "ribbon_border": "#7DD3FC",
        "ribbon_fg": "#E0F2FE",
        "ribbon_width": 43.0,
        "font_size": 4.1,
        "letter_spacing": 0.30,
    },
}


def generate_vector_drawable(tier=None, is_monochrome=False, cached_ribbon_paths=None):
    """
    Generates VectorDrawable XML with proper depth ordering (70 down to 50):
    - Standard / Monochrome (tier=None): scale=0.0093, cx=6923.26, cy=920.25 (fills safe zone r=33.97dp)
    - Supporter Tiers (tier in TIER_CONFIGS): scale=0.00745, cx=7080.0, cy=1360.0, y_offset=-1.6
      matching gen_cervelo_tiers.py / att_1406_cervelo_tiers_board.png 1:1.
    """
    if tier:
        scale = 0.0078
        cx, cy = 7080.0, 1360.0
        ox, oy = 54.0, 54.0 - 2.0
    else:
        scale = 0.0086
        cx, cy = 7080.0, 1280.0
        ox, oy = 54.0, 54.0

    def pt(x, y):
        return ox + (x - cx) * scale, oy + (y - cy) * scale

    def ts(w):
        return w * 15.0 * scale

    def c(color):
        return "#000000" if is_monochrome else color

    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!--',
    ]
    if is_monochrome:
        lines.append('  ~ aTrainingTracker: Android Adaptive Icon Themed Monochrome Layer (Android 13+ / API 33+)')
        lines.append('  ~ Single-color silhouette dynamically tinted by system Material You wallpaper palette.')
    elif tier:
        lines.append(f'  ~ aTrainingTracker: Android Adaptive Icon Foreground - {TIER_CONFIGS[tier]["title"]}')
        lines.append('  ~ REQ-UI-445 / ATT-1406: Option A (Top-Right Emblem + Bottom Tier Ribbon + Border Ring)')
        lines.append('  ~ Matches att_1406_cervelo_tiers_board.png 1:1 on smooth B-spline ATT-1482 base geometry.')
    else:
        lines.append('  ~ aTrainingTracker: Android Adaptive Icon Foreground')
        lines.append('  ~ Reconstructed from authentic master artwork Bilder/logo.fig (REQ-UI-444 / ATT-1482)')
        lines.append('  ~ 108dp canvas with vector geometry centered within safe zone (33.0 <= r <= 34.5dp).')
    lines.extend([
        '  -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="108dp"',
        '    android:height="108dp"',
        '    android:viewportWidth="108"',
        '    android:viewportHeight="108">',
    ])

    # --- Depth 70: Stopwatch Dial & Bezel (Obj 4) ---
    dial_cx, dial_cy = pt(6120, 1523)
    dial_r = 2392.0 * scale
    if is_monochrome:
        lines.append('    <!-- Stopwatch Ring (Monochrome) -->')
        lines.append(
            f'    <path\n        android:strokeColor="{c("#FFC700")}"\n'
            f'        android:strokeWidth="{ts(22):.2f}"\n        android:pathData="{circle_to_path(dial_cx, dial_cy, dial_r)}" />'
        )
    else:
        lines.append('    <!-- Stopwatch Dial & Golden Bezel (Depth 70) -->')
        lines.append(
            f'    <path\n        android:fillColor="#F0F0F0"\n        android:strokeColor="#FFC700"\n'
            f'        android:strokeWidth="{ts(22):.2f}"\n        android:pathData="{circle_to_path(dial_cx, dial_cy, dial_r)}" />'
        )

    # --- Depth 62: Cyclist Right Leg / Pedal (Obj 23) ---
    p23_0, p23_1, p23_2, p23_3 = pt(5670, 1080), pt(6090, 1887), pt(5838, 2804), pt(6090, 3002)
    lines.append('    <!-- Cyclist Right Leg & Pedal (Depth 62) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#0000FF")}"\n        android:strokeWidth="{ts(20):.2f}"\n'
        f'        android:strokeLineCap="round"\n        android:strokeLineJoin="round"\n'
        f'        android:pathData="M {p23_0[0]:.2f},{p23_0[1]:.2f} L {p23_1[0]:.2f},{p23_1[1]:.2f} L {p23_2[0]:.2f},{p23_2[1]:.2f} L {p23_3[0]:.2f},{p23_3[1]:.2f}" />'
    )

    # --- Depth 58: Cyclist Arched Back (Obj 0) ---
    p0_1, p0_3 = pt(6846, 665), pt(5624, 987)
    arc_r = math.hypot(6846 - 6549.201, 665 - 2018.404) * scale
    lines.append('    <!-- Cyclist Arched Back (Depth 58) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#0000FF")}"\n        android:strokeWidth="{ts(30):.2f}"\n'
        f'        android:strokeLineCap="round"\n'
        f'        android:pathData="M {p0_1[0]:.2f},{p0_1[1]:.2f} A {arc_r:.2f},{arc_r:.2f} 0 0,0 {p0_3[0]:.2f},{p0_3[1]:.2f}" />'
    )

    # --- Depth 56: Bike Down Tube & Seat Tube (Obj 18, 19) ---
    p18_0, p18_1 = pt(6829, 1726), pt(5805, 2610)
    lines.append('    <!-- Bike Down Tube (Depth 56) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#FF0000")}"\n        android:strokeWidth="{ts(17):.2f}"\n'
        f'        android:pathData="M {p18_0[0]:.2f},{p18_0[1]:.2f} L {p18_1[0]:.2f},{p18_1[1]:.2f}" />'
    )
    p19_0, p19_1 = pt(5838, 2642), pt(5515, 1042)
    lines.append('    <!-- Bike Seat Tube (Depth 56) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#FF0000")}"\n        android:strokeWidth="{ts(18):.2f}"\n'
        f'        android:pathData="M {p19_0[0]:.2f},{p19_0[1]:.2f} L {p19_1[0]:.2f},{p19_1[1]:.2f}" />'
    )

    # --- Depth 55: Carbon Aero Wheels (Obj 1, 2) ---
    rw_cx, rw_cy = pt(4995, 2520)
    rw_r = 627.0 * scale
    lines.append('    <!-- Carbon Aero Rear Wheel (Depth 55) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#000000")}"\n        android:strokeWidth="{ts(24):.2f}"\n'
        f'        android:pathData="{circle_to_path(rw_cx, rw_cy, rw_r)}" />'
    )
    fw_cx, fw_cy = pt(7155, 2610)
    fw_r = 636.0 * scale
    lines.append('    <!-- Carbon Aero Front Wheel (Depth 55) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#000000")}"\n        android:strokeWidth="{ts(20):.2f}"\n'
        f'        android:pathData="{circle_to_path(fw_cx, fw_cy, fw_r)}" />'
    )

    # --- Depth 55: Frame Fork / Head Tube & Top Tube (Obj 20, 21) ---
    p20_0, p20_1 = pt(7135, 2607), pt(6791, 1564)
    lines.append('    <!-- Bike Front Head Tube (Depth 55) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#FF0000")}"\n        android:strokeWidth="{ts(12):.2f}"\n'
        f'        android:pathData="M {p20_0[0]:.2f},{p20_0[1]:.2f} L {p20_1[0]:.2f},{p20_1[1]:.2f}" />'
    )
    p21_0, p21_1 = pt(6810, 1671), pt(5659, 1582)
    lines.append('    <!-- Bike Top Tube (Depth 55) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#FF0000")}"\n        android:strokeWidth="{ts(5):.2f}"\n'
        f'        android:pathData="M {p21_0[0]:.2f},{p21_0[1]:.2f} L {p21_1[0]:.2f},{p21_1[1]:.2f}" />'
    )

    # --- Depth 55: Pusher Necks / Stems (Obj 25, 26, 27) ---
    p25_0, p25_1 = pt(4896, -535), pt(4720, -830)
    lines.append('    <!-- Left Pusher Stem (Depth 55) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#FFC700")}"\n        android:strokeWidth="{ts(40):.2f}"\n'
        f'        android:pathData="M {p25_0[0]:.2f},{p25_0[1]:.2f} L {p25_1[0]:.2f},{p25_1[1]:.2f}" />'
    )
    p26_0, p26_1 = pt(6120, -900), pt(6120, -1250)
    lines.append('    <!-- Center Pusher Stem (Depth 55) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#FFC700")}"\n        android:strokeWidth="{ts(40):.2f}"\n'
        f'        android:pathData="M {p26_0[0]:.2f},{p26_0[1]:.2f} L {p26_1[0]:.2f},{p26_1[1]:.2f}" />'
    )
    p27_0, p27_1 = pt(7314, -571), pt(7490, -860)
    lines.append('    <!-- Right Pusher Stem (Depth 55) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#FFC700")}"\n        android:strokeWidth="{ts(40):.2f}"\n'
        f'        android:pathData="M {p27_0[0]:.2f},{p27_0[1]:.2f} L {p27_1[0]:.2f},{p27_1[1]:.2f}" />'
    )

    # --- Depth 54: Cyclist Left Leg (Obj 22) ---
    p22_0, p22_1, p22_2, p22_3 = pt(5977, 2491), pt(5814, 2169), pt(6660, 1665), pt(5568, 1239)
    lines.append('    <!-- Cyclist Left Leg (Depth 54) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#0000FF")}"\n        android:strokeWidth="{ts(20):.2f}"\n'
        f'        android:strokeLineCap="round"\n        android:strokeLineJoin="round"\n'
        f'        android:pathData="M {p22_0[0]:.2f},{p22_0[1]:.2f} L {p22_1[0]:.2f},{p22_1[1]:.2f} L {p22_2[0]:.2f},{p22_2[1]:.2f} L {p22_3[0]:.2f},{p22_3[1]:.2f}" />'
    )

    # --- Depth 53: Bicycle Saddle Underneath (Obj 12) ---
    p12_0, p12_1 = pt(5265, 1305), pt(5662, 1305)
    lines.append('    <!-- Bicycle Saddle Underneath (Depth 53) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#000000")}"\n        android:strokeWidth="{ts(7):.2f}"\n'
        f'        android:pathData="M {p12_0[0]:.2f},{p12_0[1]:.2f} L {p12_1[0]:.2f},{p12_1[1]:.2f}" />'
    )

    # --- Depth 50: Frame Stays (Obj 17, 7) ---
    p17_0, p17_1 = pt(5028, 2518), pt(5659, 1582)
    lines.append('    <!-- Bike Rear Seat Stay (Depth 50) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#FF0000")}"\n        android:strokeWidth="{ts(7):.2f}"\n'
        f'        android:pathData="M {p17_0[0]:.2f},{p17_0[1]:.2f} L {p17_1[0]:.2f},{p17_1[1]:.2f}" />'
    )
    p7_0, p7_1 = pt(5895, 2655), pt(5028, 2518)
    lines.append('    <!-- Bike Chain Stay (Depth 50) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#FF0000")}"\n        android:strokeWidth="{ts(7):.2f}"\n'
        f'        android:pathData="M {p7_0[0]:.2f},{p7_0[1]:.2f} L {p7_1[0]:.2f},{p7_1[1]:.2f}" />'
    )

    # --- Depth 50: Cyclist & Runner Heads (Obj 3, 5) ---
    ch_cx, ch_cy = pt(7189, 672)
    ch_r = 254.0 * scale
    lines.append('    <!-- Cyclist Head (Depth 50) -->')
    lines.append(
        f'    <path\n        android:fillColor="{c("#0000FF")}"\n        android:strokeColor="{c("#0000FF")}"\n'
        f'        android:strokeWidth="{ts(10):.2f}"\n        android:pathData="{circle_to_path(ch_cx, ch_cy, ch_r)}" />'
    )
    rh_cx, rh_cy = pt(9630, 450)
    rh_r = 254.0 * scale
    lines.append('    <!-- Runner Head (Depth 50) -->')
    lines.append(
        f'    <path\n        android:fillColor="{c("#0000FF")}"\n        android:strokeColor="{c("#0000FF")}"\n'
        f'        android:strokeWidth="{ts(10):.2f}"\n        android:pathData="{circle_to_path(rh_cx, rh_cy, rh_r)}" />'
    )

    # --- Depth 50: Cyclist Arms, Torso, Handlebars (Obj 6, 8, 15, 24) ---
    p6_0, p6_1 = pt(6829, 665), pt(6990, 682)
    lines.append('    <!-- Cyclist Chin/Neck (Depth 50) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#0000FF")}"\n        android:strokeWidth="{ts(5):.2f}"\n'
        f'        android:pathData="M {p6_0[0]:.2f},{p6_0[1]:.2f} L {p6_1[0]:.2f},{p6_1[1]:.2f}" />'
    )
    p15_0, p15_1 = pt(5715, 1035), pt(6750, 720)
    lines.append('    <!-- Cyclist Lower Torso / Chest (Depth 50) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#0000FF")}"\n        android:strokeWidth="{ts(23):.2f}"\n'
        f'        android:strokeLineCap="round"\n        android:strokeLineJoin="round"\n'
        f'        android:pathData="M {p15_0[0]:.2f},{p15_0[1]:.2f} L {p15_1[0]:.2f},{p15_1[1]:.2f}" />'
    )
    p24_0, p24_1 = pt(6570, 1575), pt(5580, 1170)
    lines.append('    <!-- Cyclist Upper Thigh (Depth 50) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#0000FF")}"\n        android:strokeWidth="{ts(26):.2f}"\n'
        f'        android:strokeLineCap="round"\n        android:strokeLineJoin="round"\n'
        f'        android:pathData="M {p24_0[0]:.2f},{p24_0[1]:.2f} L {p24_1[0]:.2f},{p24_1[1]:.2f}" />'
    )
    p8_0, p8_1, p8_2 = pt(7560, 1575), pt(6864, 1510), pt(6829, 646)
    lines.append('    <!-- Cyclist Arm & Aerobar (Depth 50) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#0000FF")}"\n        android:strokeWidth="{ts(15):.2f}"\n'
        f'        android:strokeLineCap="round"\n        android:strokeLineJoin="round"\n'
        f'        android:pathData="M {p8_0[0]:.2f},{p8_0[1]:.2f} L {p8_1[0]:.2f},{p8_1[1]:.2f} L {p8_2[0]:.2f},{p8_2[1]:.2f}" />'
    )

    # --- Depth 50: Cyclist Hip Spline (Obj 29, logo.fig:65) ---
    # Reconstructed using authentic xfig approximated B-spline elevated to cubic Béziers (C)
    # with authentic logo.fig thickness=4 (ts(4)) so transition into arched back (Obj 0) is 100% flush.
    fig_spline_pts = [
        (5490, 855),
        (5400, 1035),
        (5445, 1260),
        (5535, 1125),
        (5670, 945),
    ]
    dp_spline_pts = [pt(x, y) for x, y in fig_spline_pts]
    bezier_path = bspline_quad_to_cubic_bezier(dp_spline_pts)
    lines.append('    <!-- Cyclist Torso & Hip B-Spline (Depth 50) -->')
    lines.append(
        f'    <path\n        android:fillColor="{c("#0000FF")}"\n        android:strokeColor="{c("#0000FF")}"\n'
        f'        android:strokeWidth="{ts(4):.2f}"\n        android:strokeLineJoin="round"\n'
        f'        android:pathData="{bezier_path}" />'
    )

    # --- Depth 50: Stopwatch Pusher Buttons (Obj 13, 14, 16) ---
    p14_0, p14_1 = pt(4770, -779), pt(4545, -1170)
    lines.append('    <!-- Left Navy Blue Pusher (Depth 50) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#000090")}"\n        android:strokeWidth="{ts(50):.2f}"\n'
        f'        android:pathData="M {p14_0[0]:.2f},{p14_0[1]:.2f} L {p14_1[0]:.2f},{p14_1[1]:.2f}" />'
    )
    p13_0, p13_1 = pt(6120, -1170), pt(6120, -1620)
    lines.append('    <!-- Center Red Pusher (Depth 50) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#FF0000")}"\n        android:strokeWidth="{ts(50):.2f}"\n'
        f'        android:pathData="M {p13_0[0]:.2f},{p13_0[1]:.2f} L {p13_1[0]:.2f},{p13_1[1]:.2f}" />'
    )
    p16_0, p16_1 = pt(7466, -827), pt(7691, -1217)
    lines.append('    <!-- Right Green Pusher (Depth 50) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#009000")}"\n        android:strokeWidth="{ts(50):.2f}"\n'
        f'        android:pathData="M {p16_0[0]:.2f},{p16_0[1]:.2f} L {p16_1[0]:.2f},{p16_1[1]:.2f}" />'
    )

    # --- Depth 50: Runner Limbs & Torso (Obj 9, 10, 11, 28) ---
    p10_0, p10_1, p10_2 = pt(9606, 828), pt(9962, 1576), pt(10119, 946)
    lines.append('    <!-- Runner Front Arm (Depth 50) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#0000FF")}"\n        android:strokeWidth="{ts(17):.2f}"\n'
        f'        android:strokeLineCap="round"\n        android:strokeLineJoin="round"\n'
        f'        android:pathData="M {p10_0[0]:.2f},{p10_0[1]:.2f} L {p10_1[0]:.2f},{p10_1[1]:.2f} L {p10_2[0]:.2f},{p10_2[1]:.2f}" />'
    )
    p11_0, p11_1, p11_2 = pt(9606, 828), pt(8936, 985), pt(9212, 1539)
    lines.append('    <!-- Runner Back Arm (Depth 50) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#0000FF")}"\n        android:strokeWidth="{ts(17):.2f}"\n'
        f'        android:strokeLineCap="round"\n        android:strokeLineJoin="round"\n'
        f'        android:pathData="M {p11_0[0]:.2f},{p11_0[1]:.2f} L {p11_1[0]:.2f},{p11_1[1]:.2f} L {p11_2[0]:.2f},{p11_2[1]:.2f}" />'
    )
    p9_0, p9_1, p9_2 = pt(9527, 1933), pt(10356, 2167), pt(9843, 3115)
    lines.append('    <!-- Runner Front Leg (Depth 50) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#0000FF")}"\n        android:strokeWidth="{ts(28):.2f}"\n'
        f'        android:strokeLineCap="round"\n        android:strokeLineJoin="round"\n'
        f'        android:pathData="M {p9_0[0]:.2f},{p9_0[1]:.2f} L {p9_1[0]:.2f},{p9_1[1]:.2f} L {p9_2[0]:.2f},{p9_2[1]:.2f}" />'
    )
    p28_0, p28_1, p28_2, p28_3 = pt(9585, 1035), pt(9488, 1933), pt(8955, 2700), pt(8190, 3330)
    lines.append('    <!-- Runner Torso & Back Leg (Depth 50) -->')
    lines.append(
        f'    <path\n        android:strokeColor="{c("#0000FF")}"\n        android:strokeWidth="{ts(28):.2f}"\n'
        f'        android:strokeLineCap="round"\n        android:strokeLineJoin="round"\n'
        f'        android:pathData="M {p28_0[0]:.2f},{p28_0[1]:.2f} L {p28_1[0]:.2f},{p28_1[1]:.2f} L {p28_2[0]:.2f},{p28_2[1]:.2f} L {p28_3[0]:.2f},{p28_3[1]:.2f}" />'
    )

    # --- Supporter Overlay: Clean Bottom Tier Stadium Pill + Crisp Vector Text (no outer ring, no top-right emblem) ---
    if tier and not is_monochrome:
        cfg = TIER_CONFIGS[tier]
        lines.append('    <!-- === SUPPORTER BADGE OVERLAY (REQ-UI-445: Bottom Tier Stadium Ribbon) === -->')
        pill_path = pill_to_path(54.0, 74.2, cfg["ribbon_width"], 8.0)
        lines.append(
            f'    <path\n        android:fillColor="{cfg["ribbon_bg"]}"\n'
            f'        android:strokeColor="{cfg["ribbon_border"]}"\n        android:strokeWidth="0.75"\n'
            f'        android:pathData="{pill_path}" />'
        )
        text_path = cached_ribbon_paths[tier]
        lines.append(
            f'    <path\n        android:fillColor="{cfg["ribbon_fg"]}"\n'
            f'        android:pathData="{text_path}" />'
        )

    lines.append('</vector>')
    return "\n".join(lines) + "\n"


def generate_standalone_24dp_emblem(tier):
    """
    Generates the 24dp standalone Cockpit status emblem (ic_supporter_emblem_<tier>.xml)
    using the exact same build_top_emblem_xml() geometry scaled onto a 24x24 viewport.
    """
    cfg = TIER_CONFIGS[tier]
    kind = cfg["emblem_kind"]
    # On 24x24 viewport, center at (12.0, 12.2) with scale_factor = 1.75
    cy = 12.8 if kind.startswith("medal_") else (11.5 if kind == "pioneer_star" else 12.0)
    cx = 11.4 if kind == "diamond" else 12.0
    emblem_paths = build_top_emblem_xml(kind, cx=cx, cy=cy, scale_factor=1.75)
    return "\n".join([
        '<?xml version="1.0" encoding="utf-8"?>',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="24dp"',
        '    android:height="24dp"',
        '    android:viewportWidth="24"',
        '    android:viewportHeight="24">',
        f'    <!-- {cfg["title"]} 24dp Cockpit Emblem (REQ-UI-445) -->',
        emblem_paths,
        '</vector>',
    ]) + "\n"


def vector_xml_to_svg_group(xml_str):
    """
    Converts an Android VectorDrawable XML string into an SVG <g> fragment so Inkscape can
    render the exact VectorDrawable paths for visual inspection and Play Store artwork.
    """
    root = ET.fromstring(xml_str)
    ns = {"android": "http://schemas.android.com/apk/res/android"}
    svg_els = []
    for path_el in root.findall("path"):
        d = path_el.attrib.get(f"{{{ns['android']}}}pathData", "")
        fill = path_el.attrib.get(f"{{{ns['android']}}}fillColor", "none")
        stroke = path_el.attrib.get(f"{{{ns['android']}}}strokeColor", "none")
        sw = path_el.attrib.get(f"{{{ns['android']}}}strokeWidth", "0")
        linecap = path_el.attrib.get(f"{{{ns['android']}}}strokeLineCap", "butt")
        linejoin = path_el.attrib.get(f"{{{ns['android']}}}strokeLineJoin", "miter")
        svg_els.append(
            f'<path d="{d}" fill="{fill}" stroke="{stroke}" stroke-width="{sw}" '
            f'stroke-linecap="{linecap}" stroke-linejoin="{linejoin}" />'
        )
    return "\n".join(svg_els)


def render_svg_to_png(svg_str, out_path, width=512):
    proc = subprocess.run(
        [INKSCAPE_BIN, "--pipe", "--export-type=png", f"--export-width={width}"],
        input=svg_str.encode("utf-8"),
        capture_output=True,
        check=True,
    )
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "wb") as f:
        f.write(proc.stdout)


def render_single_icon_png(xml_str, out_path, size=512):
    svg_paths = vector_xml_to_svg_group(xml_str)
    svg = f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="{size}" height="{size}">
    <rect width="108" height="108" fill="#FFFFFF" />
    {svg_paths}
    </svg>"""
    render_svg_to_png(svg, out_path, width=size)


def render_tiers_comparison_board(xml_by_tier, out_path):
    """
    Renders the 6-card comparison board directly from the actual generated Android VectorDrawable
    XML files (with both Squircle and Pixel Circle Launcher masks) so visual verification inspects
    the true VectorDrawable geometry.
    """
    width = 1600
    height = 1080
    svg = [
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {width} {height}" width="{width}" height="{height}">',
        '<defs>',
        '  <linearGradient id="bg-classic" x1="0%" y1="0%" x2="100%" y2="100%">',
        '    <stop offset="0%" stop-color="#FFFFFF"/>',
        '    <stop offset="100%" stop-color="#F1F5F9"/>',
        '  </linearGradient>',
        '  <clipPath id="mask-circle"><circle cx="54" cy="54" r="35"/></clipPath>',
        '  <clipPath id="mask-squircle"><rect x="19" y="19" width="70" height="70" rx="21" ry="21"/></clipPath>',
        '</defs>',
        f'<rect width="{width}" height="{height}" fill="#0B0F17"/>',
        '<text x="50" y="52" font-family="DejaVu Sans, sans-serif" font-weight="bold" font-size="26" fill="#F8FAFC">aTrainingTracker — Generated Android VectorDrawable Supporter Icons (ATT-1482 &amp; ATT-1406)</text>',
        '<text x="50" y="82" font-family="DejaVu Sans, sans-serif" font-size="15" fill="#94A3B8">Rendered directly from res/drawable/ic_launcher_foreground*.xml • Smooth B-Spline Cyclist Back/Hip • Option A Emblems &amp; Vector Text Ribbons</text>',
    ]

    cards = [
        {
            "key": "standard",
            "title": "1. Standard (Classic Cervélo P3C)",
            "subtitle": "ic_launcher_foreground.xml • Smooth B-Spline",
            "chip": "FREE / STANDARD",
            "chip_color": "#64748B",
        }
    ]
    for k in ["pioneer", "bronze", "silver", "gold", "diamond"]:
        cfg = TIER_CONFIGS[k]
        cards.append({
            "key": k,
            "title": cfg["title"],
            "subtitle": f"ic_launcher_foreground_{k}.xml",
            "chip": cfg["chip"],
            "chip_color": cfg["chip_color"],
        })

    card_w = 480
    card_h = 435
    start_x = 50
    start_y = 110
    gap_x = 30
    gap_y = 30

    for idx, cinfo in enumerate(cards):
        col = idx % 3
        row = idx // 3
        cx = start_x + col * (card_w + gap_x)
        cy = start_y + row * (card_h + gap_y)
        svg_group = vector_xml_to_svg_group(xml_by_tier[cinfo["key"]])

        svg.append(f'<rect x="{cx}" y="{cy}" width="{card_w}" height="{card_h}" rx="18" fill="#131B2E" stroke="#23304C" stroke-width="1.5"/>')
        svg.append(f'<rect x="{cx+24}" y="{cy+20}" width="195" height="24" rx="12" fill="{cinfo["chip_color"]}22" stroke="{cinfo["chip_color"]}" stroke-width="1"/>')
        svg.append(f'<text x="{cx+121}" y="{cy+36}" font-family="DejaVu Sans, sans-serif" font-weight="bold" font-size="11" fill="{cinfo["chip_color"]}" text-anchor="middle">{html.escape(cinfo["chip"])}</text>')
        svg.append(f'<text x="{cx+24}" y="{cy+68}" font-family="DejaVu Sans, sans-serif" font-weight="bold" font-size="19" fill="#F8FAFC">{html.escape(cinfo["title"])}</text>')
        svg.append(f'<text x="{cx+24}" y="{cy+90}" font-family="DejaVu Sans, sans-serif" font-size="13" fill="#94A3B8">{html.escape(cinfo["subtitle"])}</text>')

        # 1) Large Squircle Preview
        sc_large = 2.75
        lx = cx + 26
        ly = cy + 115
        svg.append(f'<g transform="translate({lx - 19*sc_large:.1f}, {ly - 19*sc_large:.1f}) scale({sc_large})">')
        svg.append('  <g clip-path="url(#mask-squircle)">')
        svg.append('    <rect x="0" y="0" width="108" height="108" fill="url(#bg-classic)"/>')
        svg.append(svg_group)
        svg.append('  </g>')
        svg.append('  <rect x="19" y="19" width="70" height="70" rx="21" ry="21" fill="none" stroke="#475569" stroke-width="0.6"/>')
        svg.append('</g>')
        svg.append(f'<text x="{lx + 96}" y="{ly + 216}" font-family="DejaVu Sans, sans-serif" font-size="12" fill="#94A3B8" text-anchor="middle">Squircle Mask</text>')

        # 2) Pixel Circle Mask Preview
        sc_med = 2.25
        rx_pos = cx + 265
        ry_pos = cy + 132
        svg.append(f'<g transform="translate({rx_pos - 19*sc_med:.1f}, {ry_pos - 19*sc_med:.1f}) scale({sc_med})">')
        svg.append('  <g clip-path="url(#mask-circle)">')
        svg.append('    <rect x="0" y="0" width="108" height="108" fill="url(#bg-classic)"/>')
        svg.append(svg_group)
        svg.append('  </g>')
        svg.append('  <circle cx="54" cy="54" r="35" fill="none" stroke="#475569" stroke-width="0.7"/>')
        svg.append('</g>')
        svg.append(f'<text x="{rx_pos + 79}" y="{ry_pos + 184}" font-family="DejaVu Sans, sans-serif" font-size="12" fill="#94A3B8" text-anchor="middle">Circle Mask (Pixel)</text>')

    svg.append('</svg>')
    render_svg_to_png("\n".join(svg), out_path, width=width)


def main():
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    drawable_dir = os.path.join(repo_root, "app/src/main/res/drawable")

    # 1. Pre-compute crisp vector text paths for all 5 Supporter ribbons via Inkscape
    cached_ribbon_paths = {}
    for tier, cfg in TIER_CONFIGS.items():
        cached_ribbon_paths[tier] = text_to_vector_path(
            cfg["ribbon_text"],
            x=54.0,
            y=79.7,
            font_size=cfg["font_size"],
            letter_spacing=cfg["letter_spacing"],
        )

    # 2. Generate Standard & Monochrome VectorDrawables (ATT-1482)
    xml_by_tier = {}
    fg_std = generate_vector_drawable(tier=None, is_monochrome=False)
    xml_by_tier["standard"] = fg_std
    with open(os.path.join(drawable_dir, "ic_launcher_foreground.xml"), "w", encoding="utf-8") as f:
        f.write(fg_std)

    mono_xml = generate_vector_drawable(tier=None, is_monochrome=True)
    with open(os.path.join(drawable_dir, "ic_launcher_monochrome.xml"), "w", encoding="utf-8") as f:
        f.write(mono_xml)

    # 3. Generate all 5 Supporter Launcher Icon Foregrounds & 24dp Cockpit Emblems (ATT-1406)
    for tier in TIER_CONFIGS:
        fg_tier = generate_vector_drawable(tier=tier, is_monochrome=False, cached_ribbon_paths=cached_ribbon_paths)
        xml_by_tier[tier] = fg_tier
        with open(os.path.join(drawable_dir, f"ic_launcher_foreground_{tier}.xml"), "w", encoding="utf-8") as f:
            f.write(fg_tier)

        emblem_xml = generate_standalone_24dp_emblem(tier)
        with open(os.path.join(drawable_dir, f"ic_supporter_emblem_{tier}.xml"), "w", encoding="utf-8") as f:
            f.write(emblem_xml)

    # 4. Restore full-bleed logo_512.png from drawable-xxxhdpi/logo_512.png
    ref_full_bleed = os.path.join(repo_root, "app/src/main/res/drawable-xxxhdpi/logo_512.png")
    if os.path.exists(ref_full_bleed):
        shutil.copyfile(ref_full_bleed, os.path.join(drawable_dir, "logo_512.png"))
        shutil.copyfile(ref_full_bleed, os.path.join(repo_root, "images/logo_512.png"))

    # 5. Render Play Store 512x512 master icon & walkthrough previews via Inkscape
    render_single_icon_png(fg_std, os.path.join(repo_root, "images/play_store_icon_512.png"), size=512)
    render_single_icon_png(
        fg_std,
        os.path.join(repo_root, "docs/engineering/walkthroughs/img/att1482_reconstructed_preview.png"),
        size=512,
    )
    render_tiers_comparison_board(
        xml_by_tier,
        os.path.join(repo_root, "docs/engineering/walkthroughs/img/att1406_reconstructed_tiers_preview.png"),
    )
    print("Successfully generated all adaptive icons, supporter foregrounds, 24dp emblems, and previews.")


if __name__ == "__main__":
    main()
