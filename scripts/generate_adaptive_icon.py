#!/usr/bin/env python3
"""
aTrainingTracker: Reconstructed Android Adaptive Icon Generator (REQ-UI-444 / ATT-1482 / ATT-1406)
Converts master vector geometry from Bilder/logo.fig directly to:
1. app/src/main/res/drawable/ic_launcher_foreground.xml (Standard VectorDrawable, 108dp canvas, r ~ 34.0dp)
2. app/src/main/res/drawable/ic_launcher_monochrome.xml (Single-color #000000 silhouette for Material You)
3. app/src/main/res/drawable/ic_launcher_foreground_pioneer.xml (Pioneer Edition Option A)
4. app/src/main/res/drawable/ic_launcher_foreground_bronze.xml (Bronze Supporter Option A)
5. app/src/main/res/drawable/ic_launcher_foreground_silver.xml (Silver Pro-Supporter Option A)
6. app/src/main/res/drawable/ic_launcher_foreground_gold.xml (Gold Pro-Supporter Option A)
7. app/src/main/res/drawable/ic_launcher_foreground_diamond.xml (Diamond Sponsor Option A)
8. images/play_store_icon_512.png (512x512 Play Store master icon with safe-zone padding)
9. docs/engineering/walkthroughs/img/att1482_reconstructed_preview.png (Visual comparison preview)
10. docs/engineering/walkthroughs/img/att1406_reconstructed_tiers_preview.png (All 6 variants comparison preview board)
And restores full-bleed logo_512.png from drawable-xxxhdpi/logo_512.png (sha256: f84c6251...).
"""

import math
import os
import shutil
import subprocess
from PIL import Image, ImageDraw, ImageFont

def fig_to_dp(x, y, cx=6923.26, cy=920.25, scale=0.0093, offset_x=54.0, offset_y=54.0):
    return offset_x + (x - cx) * scale, offset_y + (y - cy) * scale

def scale_len(l, scale=0.0093):
    return l * scale

def catmull_rom_to_bezier(pts):
    """
    Converts closed Catmull-Rom spline control points to smooth cubic Bézier segments (C).
    """
    n = len(pts)
    commands = [f"M {pts[0][0]:.2f},{pts[0][1]:.2f}"]
    for i in range(n):
        p_prev = pts[(i - 1) % n]
        p_start = pts[i]
        p_end = pts[(i + 1) % n]
        p_next = pts[(i + 2) % n]

        c1_x = p_start[0] + (p_end[0] - p_prev[0]) / 6.0
        c1_y = p_start[1] + (p_end[1] - p_prev[1]) / 6.0
        c2_x = p_end[0] - (p_next[0] - p_start[0]) / 6.0
        c2_y = p_end[1] - (p_next[1] - p_start[1]) / 6.0

        commands.append(f"C {c1_x:.2f},{c1_y:.2f} {c2_x:.2f},{c2_y:.2f} {p_end[0]:.2f},{p_end[1]:.2f}")
    commands.append("Z")
    return " ".join(commands)

TIER_CONFIGS = {
    'pioneer': {
        'title': 'Pioneer Edition',
        'ring_color': '#00A86B',
        'overlay_xml': '''    <!-- === OPTION A BADGE OVERLAYS (REQ-UI-445) === -->
    <!-- Outer Border Ring (r = 34dp, safe-zone compliant r <= 34.5dp) -->
    <path
        android:strokeColor="#00A86B"
        android:strokeWidth="1.0"
        android:pathData="M 54,20 A 34,34 0 1,0 54,88 A 34,34 0 1,0 54,20 Z" />

    <!-- Top-Right Emblem: Emerald & Gold Heritage Star (cx=67.6, cy=31.8) -->
    <path
        android:fillColor="#FFD700"
        android:strokeColor="#CCAC00"
        android:strokeWidth="0.4"
        android:pathData="M 67.6,27.3 L 68.8,30.3 L 71.9,30.4 L 69.3,32.2 L 70.3,35.2 L 67.6,33.4 L 64.9,35.2 L 65.9,32.2 L 63.3,30.4 L 66.4,30.3 Z" />
    <path
        android:fillColor="#00A86B"
        android:pathData="M 67.6,28.8 L 68.4,30.8 L 70.5,30.9 L 68.7,32.1 L 69.4,34.1 L 67.6,32.9 L 65.8,34.1 L 66.5,32.1 L 64.7,30.9 L 66.8,30.8 Z" />

    <!-- Bottom Tier Ribbon: ★ PIONEER ★ (y=75.5..82.5) -->
    <path
        android:fillColor="#00A86B"
        android:pathData="M 42,76.5 L 66,76.5 L 66,82.5 L 42,82.5 Z" />
    <path
        android:strokeColor="#FFD700"
        android:strokeWidth="0.8"
        android:pathData="M 42,76.5 L 66,76.5 L 66,82.5 L 42,82.5 Z" />
    <path
        android:fillColor="#007849"
        android:pathData="M 40,78.0 L 42,76.5 L 42,82.5 L 40,81.0 Z" />
    <path
        android:fillColor="#007849"
        android:pathData="M 68,78.0 L 66,76.5 L 66,82.5 L 68,81.0 Z" />
    <path
        android:strokeColor="#FFD700"
        android:strokeWidth="0.8"
        android:pathData="M 44.5,79.5 L 45.5,79.5 M 48.0,79.5 L 60.0,79.5 M 62.5,79.5 L 63.5,79.5" />'''
    },
    'bronze': {
        'title': 'Bronze Supporter',
        'ring_color': '#CD7F32',
        'overlay_xml': '''    <!-- === OPTION A BADGE OVERLAYS (REQ-UI-445) === -->
    <!-- Outer Border Ring (r = 34dp, safe-zone compliant r <= 34.5dp) -->
    <path
        android:strokeColor="#CD7F32"
        android:strokeWidth="1.0"
        android:pathData="M 54,20 A 34,34 0 1,0 54,88 A 34,34 0 1,0 54,20 Z" />

    <!-- Top-Right Emblem: Bronze Medal on Neck Ribbon (cx=67.6, cy=31.8) -->
    <path
        android:strokeColor="#B22222"
        android:strokeWidth="1.2"
        android:strokeLineCap="round"
        android:pathData="M 65.2,27.0 L 67.6,30.0 L 70.0,27.0" />
    <path
        android:fillColor="#CD7F32"
        android:strokeColor="#8C4C14"
        android:strokeWidth="0.6"
        android:pathData="M 67.6,29.3 A 3.2,3.2 0 1,0 67.6,35.7 A 3.2,3.2 0 1,0 67.6,29.3 Z" />
    <path
        android:fillColor="#E69A55"
        android:pathData="M 67.6,30.7 A 1.8,1.8 0 1,0 67.6,34.3 A 1.8,1.8 0 1,0 67.6,30.7 Z" />

    <!-- Bottom Tier Ribbon: SUPPORTER (y=75.5..82.5) -->
    <path
        android:fillColor="#CD7F32"
        android:pathData="M 42,76.5 L 66,76.5 L 66,82.5 L 42,82.5 Z" />
    <path
        android:strokeColor="#FFC700"
        android:strokeWidth="0.8"
        android:pathData="M 42,76.5 L 66,76.5 L 66,82.5 L 42,82.5 Z" />
    <path
        android:fillColor="#8C4C14"
        android:pathData="M 40,78.0 L 42,76.5 L 42,82.5 L 40,81.0 Z" />
    <path
        android:fillColor="#8C4C14"
        android:pathData="M 68,78.0 L 66,76.5 L 66,82.5 L 68,81.0 Z" />
    <path
        android:strokeColor="#FFC700"
        android:strokeWidth="0.8"
        android:pathData="M 45.0,79.5 L 63.0,79.5" />'''
    },
    'silver': {
        'title': 'Silver Pro-Supporter',
        'ring_color': '#C0C0C0',
        'overlay_xml': '''    <!-- === OPTION A BADGE OVERLAYS (REQ-UI-445) === -->
    <!-- Outer Border Ring (r = 34dp, safe-zone compliant r <= 34.5dp) -->
    <path
        android:strokeColor="#C0C0C0"
        android:strokeWidth="1.0"
        android:pathData="M 54,20 A 34,34 0 1,0 54,88 A 34,34 0 1,0 54,20 Z" />

    <!-- Top-Right Emblem: Silver Medal on Neck Ribbon (cx=67.6, cy=31.8) -->
    <path
        android:strokeColor="#1E3A8A"
        android:strokeWidth="1.2"
        android:strokeLineCap="round"
        android:pathData="M 65.2,27.0 L 67.6,30.0 L 70.0,27.0" />
    <path
        android:fillColor="#C0C0C0"
        android:strokeColor="#707070"
        android:strokeWidth="0.6"
        android:pathData="M 67.6,29.3 A 3.2,3.2 0 1,0 67.6,35.7 A 3.2,3.2 0 1,0 67.6,29.3 Z" />
    <path
        android:fillColor="#E8E8E8"
        android:pathData="M 67.6,30.7 A 1.8,1.8 0 1,0 67.6,34.3 A 1.8,1.8 0 1,0 67.6,30.7 Z" />

    <!-- Bottom Tier Ribbon: PRO-SUPPORTER (y=75.5..82.5) -->
    <path
        android:fillColor="#C0C0C0"
        android:pathData="M 42,76.5 L 66,76.5 L 66,82.5 L 42,82.5 Z" />
    <path
        android:strokeColor="#707070"
        android:strokeWidth="0.8"
        android:pathData="M 42,76.5 L 66,76.5 L 66,82.5 L 42,82.5 Z" />
    <path
        android:fillColor="#707070"
        android:pathData="M 40,78.0 L 42,76.5 L 42,82.5 L 40,81.0 Z" />
    <path
        android:fillColor="#707070"
        android:pathData="M 68,78.0 L 66,76.5 L 66,82.5 L 68,81.0 Z" />
    <path
        android:strokeColor="#FFFFFF"
        android:strokeWidth="0.8"
        android:pathData="M 45.0,79.5 L 63.0,79.5" />'''
    },
    'gold': {
        'title': 'Gold Pro-Supporter',
        'ring_color': '#FFD700',
        'overlay_xml': '''    <!-- === OPTION A BADGE OVERLAYS (REQ-UI-445) === -->
    <!-- Outer Border Ring (r = 34dp, safe-zone compliant r <= 34.5dp) -->
    <path
        android:strokeColor="#FFD700"
        android:strokeWidth="1.0"
        android:pathData="M 54,20 A 34,34 0 1,0 54,88 A 34,34 0 1,0 54,20 Z" />

    <!-- Top-Right Emblem: 24K Gold Medal on Neck Ribbon (cx=67.6, cy=31.8) -->
    <path
        android:strokeColor="#8B0000"
        android:strokeWidth="1.2"
        android:strokeLineCap="round"
        android:pathData="M 65.2,27.0 L 67.6,30.0 L 70.0,27.0" />
    <path
        android:fillColor="#FFD700"
        android:strokeColor="#CCAC00"
        android:strokeWidth="0.6"
        android:pathData="M 67.6,29.3 A 3.2,3.2 0 1,0 67.6,35.7 A 3.2,3.2 0 1,0 67.6,29.3 Z" />
    <path
        android:fillColor="#FFF099"
        android:pathData="M 67.6,30.7 A 1.8,1.8 0 1,0 67.6,34.3 A 1.8,1.8 0 1,0 67.6,30.7 Z" />

    <!-- Bottom Tier Ribbon: PRO-SUPPORTER (y=75.5..82.5) -->
    <path
        android:fillColor="#FFD700"
        android:pathData="M 42,76.5 L 66,76.5 L 66,82.5 L 42,82.5 Z" />
    <path
        android:strokeColor="#B89600"
        android:strokeWidth="0.8"
        android:pathData="M 42,76.5 L 66,76.5 L 66,82.5 L 42,82.5 Z" />
    <path
        android:fillColor="#B89600"
        android:pathData="M 40,78.0 L 42,76.5 L 42,82.5 L 40,81.0 Z" />
    <path
        android:fillColor="#B89600"
        android:pathData="M 68,78.0 L 66,76.5 L 66,82.5 L 68,81.0 Z" />
    <path
        android:strokeColor="#FFFFFF"
        android:strokeWidth="0.8"
        android:pathData="M 45.0,79.5 L 63.0,79.5" />'''
    },
    'diamond': {
        'title': 'Diamond Sponsor',
        'ring_color': '#00E5FF',
        'overlay_xml': '''    <!-- === OPTION A BADGE OVERLAYS (REQ-UI-445) === -->
    <!-- Outer Border Ring (r = 34dp, safe-zone compliant r <= 34.5dp) -->
    <path
        android:strokeColor="#00E5FF"
        android:strokeWidth="1.0"
        android:pathData="M 54,20 A 34,34 0 1,0 54,88 A 34,34 0 1,0 54,20 Z" />

    <!-- Top-Right Emblem: Faceted Brilliant Cyan Diamond (cx=67.6, cy=31.8) -->
    <path
        android:fillColor="#E0F7FA"
        android:strokeColor="#00B0FF"
        android:strokeWidth="0.4"
        android:pathData="M 65.2,29.5 L 70.0,29.5 L 71.8,31.5 L 63.4,31.5 Z" />
    <path
        android:fillColor="#00E5FF"
        android:strokeColor="#0091EA"
        android:strokeWidth="0.4"
        android:pathData="M 63.4,31.5 L 71.8,31.5 L 67.6,35.5 Z" />
    <path
        android:fillColor="#80DEEA"
        android:pathData="M 66.0,31.5 L 69.2,31.5 L 67.6,35.5 Z" />
    <path
        android:strokeColor="#FFFFFF"
        android:strokeWidth="0.6"
        android:strokeLineCap="round"
        android:pathData="M 71.5,27.0 L 71.5,30.0 M 70.0,28.5 L 73.0,28.5" />

    <!-- Bottom Tier Ribbon: ◆ SPONSOR ◆ (y=75.5..82.5) -->
    <path
        android:fillColor="#00E5FF"
        android:pathData="M 42,76.5 L 66,76.5 L 66,82.5 L 42,82.5 Z" />
    <path
        android:strokeColor="#0091EA"
        android:strokeWidth="0.8"
        android:pathData="M 42,76.5 L 66,76.5 L 66,82.5 L 42,82.5 Z" />
    <path
        android:fillColor="#0091EA"
        android:pathData="M 40,78.0 L 42,76.5 L 42,82.5 L 40,81.0 Z" />
    <path
        android:fillColor="#0091EA"
        android:pathData="M 68,78.0 L 66,76.5 L 66,82.5 L 68,81.0 Z" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M 45.0,79.5 L 46.0,78.5 L 47.0,79.5 L 46.0,80.5 Z" />
    <path
        android:strokeColor="#FFFFFF"
        android:strokeWidth="0.8"
        android:pathData="M 49.0,79.5 L 59.0,79.5" />
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M 61.0,79.5 L 62.0,78.5 L 63.0,79.5 L 62.0,80.5 Z" />'''
    }
}

def generate_vector_drawable(tier=None, is_monochrome=False):
    """
    Generates VectorDrawable XML with proper depth ordering (70 down to 50),
    integrating Option A badge overlays for supporter tiers.
    """
    scale = 0.0093
    cx, cy = 6923.26, 920.25
    ox, oy = 54.0, 54.0

    def pt(x, y):
        return fig_to_dp(x, y, cx, cy, scale, ox, oy)

    lines = []
    lines.append('<?xml version="1.0" encoding="utf-8"?>')
    lines.append('<!--')
    if is_monochrome:
        lines.append('  ~ aTrainingTracker: Android Adaptive Icon Themed Monochrome Layer (Android 13+ / API 33+)')
        lines.append('  ~ Single-color silhouette dynamically tinted by system Material You wallpaper palette.')
    elif tier:
        tier_title = TIER_CONFIGS[tier]['title']
        lines.append(f'  ~ aTrainingTracker: Android Adaptive Icon Foreground - {tier_title}')
        lines.append('  ~ REQ-UI-445 / ATT-1406: Option A (Top-Right Emblem + Bottom Tier Ribbon + Border Ring)')
        lines.append('  ~ Reconstructed on corrected ATT-1482 base geometry (33.0 <= r <= 34.5dp safe zone).')
        lines.append('  ~ Cervélo P3C bike: #FF0000 frame, #000000 wheels/saddle.')
        lines.append('  ~ Stopwatch: #FFC700 ring/stems, #F0F0F0 dial, #000090/#FF0000/#009000 pushers.')
        lines.append('  ~ Athletes: #0000FF royal blue.')
    else:
        lines.append('  ~ aTrainingTracker: Android Adaptive Icon Foreground')
        lines.append('  ~ Reconstructed from authentic master artwork Bilder/logo.fig (REQ-UI-444 / ATT-1482)')
        lines.append('  ~ 108dp canvas with vector geometry centered within safe zone (33.0 <= r <= 34.5dp).')
        lines.append('  ~ Cervélo P3C: #FF0000 frame, #000000 wheels/saddle.')
        lines.append('  ~ Stopwatch: #FFC700 ring/stems, #F0F0F0 dial, #000090/#FF0000/#009000 pushers.')
        lines.append('  ~ Athletes: #0000FF royal blue.')
    lines.append('  -->')
    lines.append('<vector xmlns:android="http://schemas.android.com/apk/res/android"')
    lines.append('    android:width="108dp"')
    lines.append('    android:height="108dp"')
    lines.append('    android:viewportWidth="108"')
    lines.append('    android:viewportHeight="108">')

    # Color helpers
    def c(color):
        return "#000000" if is_monochrome else color

    # --- Depth 70: Stopwatch Dial & Bezel (Obj 4) ---
    dial_cx, dial_cy = pt(6120, 1523)
    dial_r = scale_len(2392, scale)
    if is_monochrome:
        lines.append('    <!-- Stopwatch Ring (Monochrome) -->')
        lines.append('    <path')
        lines.append(f'        android:strokeColor="{c("#FFC700")}"')
        lines.append('        android:strokeWidth="3.07"')
        lines.append(f'        android:pathData="M {dial_cx:.2f},{dial_cy - dial_r:.2f} A {dial_r:.2f},{dial_r:.2f} 0 1,0 {dial_cx:.2f},{dial_cy + dial_r:.2f} A {dial_r:.2f},{dial_r:.2f} 0 1,0 {dial_cx:.2f},{dial_cy - dial_r:.2f} Z" />')
    else:
        lines.append('    <!-- Stopwatch Dial & Golden Bezel (Depth 70) -->')
        lines.append('    <path')
        lines.append('        android:fillColor="#F0F0F0"')
        lines.append('        android:strokeColor="#FFC700"')
        lines.append('        android:strokeWidth="3.07"')
        lines.append(f'        android:pathData="M {dial_cx:.2f},{dial_cy - dial_r:.2f} A {dial_r:.2f},{dial_r:.2f} 0 1,0 {dial_cx:.2f},{dial_cy + dial_r:.2f} A {dial_r:.2f},{dial_r:.2f} 0 1,0 {dial_cx:.2f},{dial_cy - dial_r:.2f} Z" />')

    # --- Depth 62: Cyclist Right Leg / Pedal (Obj 23) ---
    p23_0 = pt(5670, 1080)
    p23_1 = pt(6090, 1887)
    p23_2 = pt(5838, 2804)
    p23_3 = pt(6090, 3002)
    lines.append('    <!-- Cyclist Right Leg & Pedal (Depth 62) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#0000FF")}"')
    lines.append('        android:strokeWidth="2.79"')
    lines.append('        android:strokeLineCap="round"')
    lines.append('        android:strokeLineJoin="round"')
    lines.append(f'        android:pathData="M {p23_0[0]:.2f},{p23_0[1]:.2f} L {p23_1[0]:.2f},{p23_1[1]:.2f} L {p23_2[0]:.2f},{p23_2[1]:.2f} L {p23_3[0]:.2f},{p23_3[1]:.2f}" />')

    # --- Depth 58: Cyclist Arched Back (Obj 0) ---
    p0_1 = pt(6846, 665)
    p0_3 = pt(5624, 987)
    arc_r = scale_len(1386.0, scale)
    lines.append('    <!-- Cyclist Arched Back (Depth 58) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#0000FF")}"')
    lines.append('        android:strokeWidth="4.18"')
    lines.append('        android:strokeLineCap="round"')
    lines.append(f'        android:pathData="M {p0_1[0]:.2f},{p0_1[1]:.2f} A {arc_r:.2f},{arc_r:.2f} 0 0,0 {p0_3[0]:.2f},{p0_3[1]:.2f}" />')

    # --- Depth 56: Bike Down Tube & Seat Tube (Obj 18, 19) ---
    p18_0 = pt(6829, 1726)
    p18_1 = pt(5805, 2610)
    lines.append('    <!-- Bike Down Tube (Depth 56) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#FF0000")}"')
    lines.append('        android:strokeWidth="2.37"')
    lines.append('        android:strokeLineCap="round"')
    lines.append('        android:strokeLineJoin="round"')
    lines.append(f'        android:pathData="M {p18_0[0]:.2f},{p18_0[1]:.2f} L {p18_1[0]:.2f},{p18_1[1]:.2f}" />')

    p19_0 = pt(5838, 2642)
    p19_1 = pt(5515, 1042)
    lines.append('    <!-- Bike Seat Tube (Depth 56) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#FF0000")}"')
    lines.append('        android:strokeWidth="2.51"')
    lines.append('        android:strokeLineCap="round"')
    lines.append('        android:strokeLineJoin="round"')
    lines.append(f'        android:pathData="M {p19_0[0]:.2f},{p19_0[1]:.2f} L {p19_1[0]:.2f},{p19_1[1]:.2f}" />')

    # --- Depth 55: Carbon Aero Wheels (Obj 1, 2) ---
    rw_cx, rw_cy = pt(4995, 2520)
    rw_r = scale_len(627, scale)
    lines.append('    <!-- Carbon Aero Rear Wheel (Depth 55) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#000000")}"')
    lines.append('        android:strokeWidth="3.35"')
    lines.append(f'        android:pathData="M {rw_cx:.2f},{rw_cy - rw_r:.2f} A {rw_r:.2f},{rw_r:.2f} 0 1,0 {rw_cx:.2f},{rw_cy + rw_r:.2f} A {rw_r:.2f},{rw_r:.2f} 0 1,0 {rw_cx:.2f},{rw_cy - rw_r:.2f} Z" />')

    fw_cx, fw_cy = pt(7155, 2610)
    fw_r = scale_len(636, scale)
    lines.append('    <!-- Carbon Aero Front Wheel (Depth 55) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#000000")}"')
    lines.append('        android:strokeWidth="2.79"')
    lines.append(f'        android:pathData="M {fw_cx:.2f},{fw_cy - fw_r:.2f} A {fw_r:.2f},{fw_r:.2f} 0 1,0 {fw_cx:.2f},{fw_cy + fw_r:.2f} A {fw_r:.2f},{fw_r:.2f} 0 1,0 {fw_cx:.2f},{fw_cy - fw_r:.2f} Z" />')

    # --- Depth 55: Frame Fork / Head Tube & Top Tube (Obj 20, 21) ---
    p20_0 = pt(7135, 2607)
    p20_1 = pt(6791, 1564)
    lines.append('    <!-- Bike Front Head Tube (Depth 55) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#FF0000")}"')
    lines.append('        android:strokeWidth="1.67"')
    lines.append('        android:strokeLineCap="round"')
    lines.append('        android:strokeLineJoin="round"')
    lines.append(f'        android:pathData="M {p20_0[0]:.2f},{p20_0[1]:.2f} L {p20_1[0]:.2f},{p20_1[1]:.2f}" />')

    p21_0 = pt(6810, 1671)
    p21_1 = pt(5659, 1582)
    lines.append('    <!-- Bike Top Tube (Depth 55) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#FF0000")}"')
    lines.append('        android:strokeWidth="0.70"')
    lines.append('        android:strokeLineCap="round"')
    lines.append('        android:strokeLineJoin="round"')
    lines.append(f'        android:pathData="M {p21_0[0]:.2f},{p21_0[1]:.2f} L {p21_1[0]:.2f},{p21_1[1]:.2f}" />')

    # --- Depth 55: Pusher Necks / Stems (Obj 25, 26, 27) ---
    # Stems are thick golden necks (#FFC700) overlapping button caps with zero white gap
    p25_0 = pt(4896, -535)
    p25_1 = pt(4720, -830) # extended into navy button for seamless flush overlap
    lines.append('    <!-- Left Pusher Stem (Depth 55) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#FFC700")}"')
    lines.append('        android:strokeWidth="5.58"')
    lines.append('        android:strokeLineCap="butt"')
    lines.append('        android:strokeLineJoin="miter"')
    lines.append(f'        android:pathData="M {p25_0[0]:.2f},{p25_0[1]:.2f} L {p25_1[0]:.2f},{p25_1[1]:.2f}" />')

    p26_0 = pt(6120, -900)
    p26_1 = pt(6120, -1250) # extended into red button for seamless flush overlap
    lines.append('    <!-- Center Pusher Stem (Depth 55) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#FFC700")}"')
    lines.append('        android:strokeWidth="5.58"')
    lines.append('        android:strokeLineCap="butt"')
    lines.append('        android:strokeLineJoin="miter"')
    lines.append(f'        android:pathData="M {p26_0[0]:.2f},{p26_0[1]:.2f} L {p26_1[0]:.2f},{p26_1[1]:.2f}" />')

    p27_0 = pt(7314, -571)
    p27_1 = pt(7490, -860) # extended into green button for seamless flush overlap
    lines.append('    <!-- Right Pusher Stem (Depth 55) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#FFC700")}"')
    lines.append('        android:strokeWidth="5.58"')
    lines.append('        android:strokeLineCap="butt"')
    lines.append('        android:strokeLineJoin="miter"')
    lines.append(f'        android:pathData="M {p27_0[0]:.2f},{p27_0[1]:.2f} L {p27_1[0]:.2f},{p27_1[1]:.2f}" />')

    # --- Depth 54: Cyclist Left Leg (Obj 22) ---
    p22_0 = pt(5977, 2491)
    p22_1 = pt(5814, 2169)
    p22_2 = pt(6660, 1665)
    p22_3 = pt(5568, 1239)
    lines.append('    <!-- Cyclist Left Leg (Depth 54) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#0000FF")}"')
    lines.append('        android:strokeWidth="2.79"')
    lines.append('        android:strokeLineCap="round"')
    lines.append('        android:strokeLineJoin="round"')
    lines.append(f'        android:pathData="M {p22_0[0]:.2f},{p22_0[1]:.2f} L {p22_1[0]:.2f},{p22_1[1]:.2f} L {p22_2[0]:.2f},{p22_2[1]:.2f} L {p22_3[0]:.2f},{p22_3[1]:.2f}" />')

    # --- Depth 53: Bicycle Saddle Underneath (Obj 12) ---
    p12_0 = pt(5265, 1305)
    p12_1 = pt(5662, 1305)
    lines.append('    <!-- Bicycle Saddle Underneath (Depth 53) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#000000")}"')
    lines.append('        android:strokeWidth="0.98"')
    lines.append('        android:strokeLineCap="round"')
    lines.append('        android:strokeLineJoin="round"')
    lines.append(f'        android:pathData="M {p12_0[0]:.2f},{p12_0[1]:.2f} L {p12_1[0]:.2f},{p12_1[1]:.2f}" />')

    # --- Depth 50: Frame Stays (Obj 17, 7) ---
    p17_0 = pt(5028, 2518)
    p17_1 = pt(5659, 1582)
    lines.append('    <!-- Bike Rear Seat Stay (Depth 50) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#FF0000")}"')
    lines.append('        android:strokeWidth="0.98"')
    lines.append('        android:strokeLineCap="round"')
    lines.append('        android:strokeLineJoin="round"')
    lines.append(f'        android:pathData="M {p17_0[0]:.2f},{p17_0[1]:.2f} L {p17_1[0]:.2f},{p17_1[1]:.2f}" />')

    p7_0 = pt(5895, 2655)
    p7_1 = pt(5028, 2518)
    lines.append('    <!-- Bike Chain Stay (Depth 50) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#FF0000")}"')
    lines.append('        android:strokeWidth="0.98"')
    lines.append('        android:strokeLineCap="round"')
    lines.append('        android:strokeLineJoin="round"')
    lines.append(f'        android:pathData="M {p7_0[0]:.2f},{p7_0[1]:.2f} L {p7_1[0]:.2f},{p7_1[1]:.2f}" />')

    # --- Depth 50: Cyclist & Runner Heads (Obj 3, 5) ---
    ch_cx, ch_cy = pt(7189, 672)
    ch_r = scale_len(254, scale)
    lines.append('    <!-- Cyclist Head (Depth 50) -->')
    lines.append('    <path')
    lines.append(f'        android:fillColor="{c("#0000FF")}"')
    lines.append(f'        android:strokeColor="{c("#0000FF")}"')
    lines.append('        android:strokeWidth="1.39"')
    lines.append(f'        android:pathData="M {ch_cx:.2f},{ch_cy - ch_r:.2f} A {ch_r:.2f},{ch_r:.2f} 0 1,0 {ch_cx:.2f},{ch_cy + ch_r:.2f} A {ch_r:.2f},{ch_r:.2f} 0 1,0 {ch_cx:.2f},{ch_cy - ch_r:.2f} Z" />')

    rh_cx, rh_cy = pt(9630, 450)
    rh_r = scale_len(254, scale)
    lines.append('    <!-- Runner Head (Depth 50) -->')
    lines.append('    <path')
    lines.append(f'        android:fillColor="{c("#0000FF")}"')
    lines.append(f'        android:strokeColor="{c("#0000FF")}"')
    lines.append('        android:strokeWidth="1.39"')
    lines.append(f'        android:pathData="M {rh_cx:.2f},{rh_cy - rh_r:.2f} A {rh_r:.2f},{rh_r:.2f} 0 1,0 {rh_cx:.2f},{rh_cy + rh_r:.2f} A {rh_r:.2f},{rh_r:.2f} 0 1,0 {rh_cx:.2f},{rh_cy - rh_r:.2f} Z" />')

    # --- Depth 50: Cyclist Arms, Torso, Handlebars (Obj 6, 8, 15, 24) ---
    p6_0 = pt(6829, 665)
    p6_1 = pt(6990, 682)
    lines.append('    <!-- Cyclist Chin/Neck (Depth 50) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#0000FF")}"')
    lines.append('        android:strokeWidth="0.70"')
    lines.append('        android:strokeLineCap="round"')
    lines.append('        android:strokeLineJoin="round"')
    lines.append(f'        android:pathData="M {p6_0[0]:.2f},{p6_0[1]:.2f} L {p6_1[0]:.2f},{p6_1[1]:.2f}" />')

    p15_0 = pt(5715, 1035)
    p15_1 = pt(6750, 720)
    lines.append('    <!-- Cyclist Handlebar (Depth 50) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#0000FF")}"')
    lines.append('        android:strokeWidth="3.21"')
    lines.append('        android:strokeLineCap="round"')
    lines.append('        android:strokeLineJoin="round"')
    lines.append(f'        android:pathData="M {p15_0[0]:.2f},{p15_0[1]:.2f} L {p15_1[0]:.2f},{p15_1[1]:.2f}" />')

    p24_0 = pt(6570, 1575)
    p24_1 = pt(5580, 1170)
    lines.append('    <!-- Cyclist Forearm & Aerobar (Depth 50) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#0000FF")}"')
    lines.append('        android:strokeWidth="3.63"')
    lines.append('        android:strokeLineCap="round"')
    lines.append('        android:strokeLineJoin="round"')
    lines.append(f'        android:pathData="M {p24_0[0]:.2f},{p24_0[1]:.2f} L {p24_1[0]:.2f},{p24_1[1]:.2f}" />')

    p8_0 = pt(7560, 1575)
    p8_1 = pt(6864, 1510)
    p8_2 = pt(6829, 646)
    lines.append('    <!-- Cyclist Upper Arm (Depth 50) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#0000FF")}"')
    lines.append('        android:strokeWidth="2.09"')
    lines.append('        android:strokeLineCap="round"')
    lines.append('        android:strokeLineJoin="round"')
    lines.append(f'        android:pathData="M {p8_0[0]:.2f},{p8_0[1]:.2f} L {p8_1[0]:.2f},{p8_1[1]:.2f} L {p8_2[0]:.2f},{p8_2[1]:.2f}" />')

    # --- Depth 50: Cyclist Hip Spline (Obj 29) ---
    # Reconstructed as smooth cubic Béziers in ROYAL BLUE (#0000FF) covering the frame/saddle junction
    fig_spline_pts = [
        (5490, 855),
        (5400, 1035),
        (5445, 1260),
        (5535, 1125),
        (5670, 945)
    ]
    dp_spline_pts = [pt(x, y) for x, y in fig_spline_pts]
    bezier_path = catmull_rom_to_bezier(dp_spline_pts)
    lines.append('    <!-- Cyclist Torso & Hip Spline (Depth 50) -->')
    lines.append('    <path')
    lines.append(f'        android:fillColor="{c("#0000FF")}"')
    lines.append(f'        android:strokeColor="{c("#0000FF")}"')
    lines.append('        android:strokeWidth="1.80"')
    lines.append('        android:strokeLineCap="round"')
    lines.append('        android:strokeLineJoin="round"')
    lines.append(f'        android:pathData="{bezier_path}" />')

    # --- Depth 50: Stopwatch Pusher Buttons (Obj 13, 14, 16) ---
    p14_0 = pt(4770, -779)
    p14_1 = pt(4545, -1170)
    lines.append('    <!-- Left Navy Blue Pusher (Depth 50) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#000090")}"')
    lines.append('        android:strokeWidth="6.98"')
    lines.append('        android:strokeLineCap="butt"')
    lines.append('        android:strokeLineJoin="miter"')
    lines.append(f'        android:pathData="M {p14_0[0]:.2f},{p14_0[1]:.2f} L {p14_1[0]:.2f},{p14_1[1]:.2f}" />')

    p13_0 = pt(6120, -1170)
    p13_1 = pt(6120, -1620)
    lines.append('    <!-- Center Red Pusher (Depth 50) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#FF0000")}"')
    lines.append('        android:strokeWidth="6.98"')
    lines.append('        android:strokeLineCap="butt"')
    lines.append('        android:strokeLineJoin="miter"')
    lines.append(f'        android:pathData="M {p13_0[0]:.2f},{p13_0[1]:.2f} L {p13_1[0]:.2f},{p13_1[1]:.2f}" />')

    p16_0 = pt(7466, -827)
    p16_1 = pt(7691, -1217)
    lines.append('    <!-- Right Green Pusher (Depth 50) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#009000")}"')
    lines.append('        android:strokeWidth="6.98"')
    lines.append('        android:strokeLineCap="butt"')
    lines.append('        android:strokeLineJoin="miter"')
    lines.append(f'        android:pathData="M {p16_0[0]:.2f},{p16_0[1]:.2f} L {p16_1[0]:.2f},{p16_1[1]:.2f}" />')

    # --- Depth 50: Runner Limbs & Torso (Obj 9, 10, 11, 28) ---
    p10_0 = pt(9606, 828)
    p10_1 = pt(9962, 1576)
    p10_2 = pt(10119, 946)
    lines.append('    <!-- Runner Back Arm (Depth 50) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#0000FF")}"')
    lines.append('        android:strokeWidth="2.37"')
    lines.append('        android:strokeLineCap="round"')
    lines.append('        android:strokeLineJoin="round"')
    lines.append(f'        android:pathData="M {p10_0[0]:.2f},{p10_0[1]:.2f} L {p10_1[0]:.2f},{p10_1[1]:.2f} L {p10_2[0]:.2f},{p10_2[1]:.2f}" />')

    p11_0 = pt(9606, 828)
    p11_1 = pt(8936, 985)
    p11_2 = pt(9212, 1539)
    lines.append('    <!-- Runner Forward Arm (Depth 50) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#0000FF")}"')
    lines.append('        android:strokeWidth="2.37"')
    lines.append('        android:strokeLineCap="round"')
    lines.append('        android:strokeLineJoin="round"')
    lines.append(f'        android:pathData="M {p11_0[0]:.2f},{p11_0[1]:.2f} L {p11_1[0]:.2f},{p11_1[1]:.2f} L {p11_2[0]:.2f},{p11_2[1]:.2f}" />')

    p9_0 = pt(9527, 1933)
    p9_1 = pt(10356, 2167)
    p9_2 = pt(9843, 3115)
    lines.append('    <!-- Runner Forward Leg (Depth 50) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#0000FF")}"')
    lines.append('        android:strokeWidth="3.91"')
    lines.append('        android:strokeLineCap="round"')
    lines.append('        android:strokeLineJoin="round"')
    lines.append(f'        android:pathData="M {p9_0[0]:.2f},{p9_0[1]:.2f} L {p9_1[0]:.2f},{p9_1[1]:.2f} L {p9_2[0]:.2f},{p9_2[1]:.2f}" />')

    p28_0 = pt(9585, 1035)
    p28_1 = pt(9488, 1933)
    p28_2 = pt(8955, 2700)
    p28_3 = pt(8190, 3330)
    lines.append('    <!-- Runner Torso & Back Leg (Depth 50) -->')
    lines.append('    <path')
    lines.append(f'        android:strokeColor="{c("#0000FF")}"')
    lines.append('        android:strokeWidth="3.91"')
    lines.append('        android:strokeLineCap="round"')
    lines.append('        android:strokeLineJoin="round"')
    lines.append(f'        android:pathData="M {p28_0[0]:.2f},{p28_0[1]:.2f} L {p28_1[0]:.2f},{p28_1[1]:.2f} L {p28_2[0]:.2f},{p28_2[1]:.2f} L {p28_3[0]:.2f},{p28_3[1]:.2f}" />')

    # Append tier overlays if requested
    if tier and tier in TIER_CONFIGS:
        lines.append(TIER_CONFIGS[tier]['overlay_xml'])

    lines.append('</vector>')
    return '\n'.join(lines) + '\n'

def vector_to_svg(vd_text, background_color="#FFFFFF"):
    """
    Converts VectorDrawable XML to standalone SVG for high-res raster rendering via Inkscape.
    """
    import xml.etree.ElementTree as ET
    root = ET.fromstring(vd_text)
    svg_lines = [
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="512" height="512">',
        f'  <rect width="108" height="108" fill="{background_color}" />'
    ]
    for child in root:
        if child.tag.endswith('path'):
            attrs = {k.split('}')[-1]: v for k, v in child.attrib.items()}
            fill = attrs.get('fillColor', 'none')
            stroke = attrs.get('strokeColor', 'none')
            stroke_w = attrs.get('strokeWidth', '1')
            cap = attrs.get('strokeLineCap', 'butt')
            join = attrs.get('strokeLineJoin', 'miter')
            data = attrs.get('pathData', '')
            svg_lines.append(
                f'  <path d="{data}" fill="{fill}" stroke="{stroke}" stroke-width="{stroke_w}" stroke-linecap="{cap}" stroke-linejoin="{join}" />'
            )
    svg_lines.append('</svg>')
    return '\n'.join(svg_lines)

def composite_tiers_preview(variant_images, output_png):
    """
    Composites a 6-variant preview board PNG showing Standard, Pioneer, Bronze, Silver, Gold, Diamond
    with circular launcher guides and badge metadata.
    """
    # 2 rows x 3 columns
    card_w, card_h = 360, 420
    board_w = card_w * 3 + 80
    board_h = card_h * 2 + 160

    board = Image.new("RGBA", (board_w, board_h), (15, 23, 42, 255)) # Dark slate #0F172A
    draw = ImageDraw.Draw(board)

    # Header
    title_text = "aTrainingTracker - Pro Supporter Launcher Icons (Option A Reconstructed - ATT-1406)"
    sub_text = "Corrected ATT-1482 Base Geometry (Smooth Béziers, Flush Pushers, r <= 34.5dp) + Option A Badges"
    draw.text((40, 30), title_text, fill=(255, 255, 255, 255))
    draw.text((40, 65), sub_text, fill=(148, 163, 184, 255))

    keys = ['standard', 'pioneer', 'bronze', 'silver', 'gold', 'diamond']
    labels = [
        "1. Standard (Free / Default)",
        "2. Pioneer Edition (0 € Lifetime)",
        "3. Bronze Supporter (1 €/mo)",
        "4. Silver Pro-Supporter (25 €/yr)",
        "5. Gold Pro-Supporter (50 €/yr)",
        "6. Diamond Sponsor (100 €/yr)"
    ]
    subtitles = [
        "Modernized Cervélo P3C & Stopwatch",
        "Emerald & Gold Star + Ribbon",
        "Bronze Medal + SUPPORTER Ribbon",
        "Silver Medal + PRO-SUPPORTER Ribbon",
        "24K Gold Medal + PRO-SUPPORTER Ribbon",
        "Faceted Diamond + SPONSOR Ribbon"
    ]

    for idx, key in enumerate(keys):
        col = idx % 3
        row = idx // 3
        x_card = 40 + col * card_w
        y_card = 120 + row * card_h

        # Card container
        draw.rounded_rectangle([x_card + 10, y_card + 10, x_card + card_w - 10, y_card + card_h - 10], radius=16, fill=(30, 41, 59, 255), outline=(51, 65, 85, 255), width=1)

        # Label
        draw.text((x_card + 24, y_card + 24), labels[idx], fill=(248, 250, 252, 255))
        draw.text((x_card + 24, y_card + 46), subtitles[idx], fill=(148, 163, 184, 255))

        # Icon image (resize to 260x260)
        img = variant_images[key].convert("RGBA").resize((260, 260), Image.Resampling.LANCZOS)
        
        # Create circular masked icon (Pixel launcher preview)
        mask = Image.new("L", (260, 260), 0)
        mask_draw = ImageDraw.Draw(mask)
        mask_draw.ellipse((0, 0, 259, 259), fill=255)
        
        # Circular surface background (#F1F5F9)
        circle_surf = Image.new("RGBA", (260, 260), (241, 245, 249, 255))
        circle_surf.paste(img, (0, 0), img)

        icon_x = x_card + (card_w - 260) // 2
        icon_y = y_card + 75

        # Paste with circular mask
        board.paste(circle_surf, (icon_x, icon_y), mask)

        # Draw circle outline & safe-zone guide
        draw.ellipse([icon_x, icon_y, icon_x + 259, icon_y + 259], outline=(203, 213, 225, 255), width=2)
        # Safe-zone guideline (dashed/subtle circle at r = 34.5dp on 108dp canvas -> scale = 260 / 108 * 34.5 ~ 83px radius from center)
        cx_i = icon_x + 130
        cy_i = icon_y + 130
        r_sz = int(260.0 / 108.0 * 34.5)
        draw.ellipse([cx_i - r_sz, cy_i - r_sz, cx_i + r_sz, cy_i + r_sz], outline=(100, 116, 139, 120), width=1)

        # Status caption
        draw.text((icon_x + 30, icon_y + 280), "Safe-zone envelope r <= 34.5dp", fill=(100, 116, 139, 255))

    board.save(output_png)
    print(f"Composited 6-variant preview board -> {output_png}")

def main():
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
    res_dir = os.path.join(repo_root, 'app', 'src', 'main', 'res')
    img_dir = os.path.join(repo_root, 'images')
    preview_dir = os.path.join(repo_root, 'docs', 'engineering', 'walkthroughs', 'img')
    os.makedirs(preview_dir, exist_ok=True)

    variant_xmls = {}

    # 1. Generate ic_launcher_foreground.xml (Standard)
    fg_xml = generate_vector_drawable(tier=None, is_monochrome=False)
    fg_path = os.path.join(res_dir, 'drawable', 'ic_launcher_foreground.xml')
    with open(fg_path, 'w', encoding='utf-8') as f:
        f.write(fg_xml)
    variant_xmls['standard'] = fg_xml
    print(f"Generated {fg_path}")

    # 2. Generate ic_launcher_monochrome.xml
    mono_xml = generate_vector_drawable(tier=None, is_monochrome=True)
    mono_path = os.path.join(res_dir, 'drawable', 'ic_launcher_monochrome.xml')
    with open(mono_path, 'w', encoding='utf-8') as f:
        f.write(mono_xml)
    print(f"Generated {mono_path}")

    # 3. Generate all 5 Supporter Tier Foregrounds
    for tier in ['pioneer', 'bronze', 'silver', 'gold', 'diamond']:
        tier_xml = generate_vector_drawable(tier=tier, is_monochrome=False)
        tier_path = os.path.join(res_dir, 'drawable', f'ic_launcher_foreground_{tier}.xml')
        with open(tier_path, 'w', encoding='utf-8') as f:
            f.write(tier_xml)
        variant_xmls[tier] = tier_xml
        print(f"Generated {tier_path}")

    # 4. Restore full-bleed logo_512.png (sha256: f84c6251...) to drawable/ and images/
    xxxhdpi_logo = os.path.join(res_dir, 'drawable-xxxhdpi', 'logo_512.png')
    target_drawable_logo = os.path.join(res_dir, 'drawable', 'logo_512.png')
    target_images_logo = os.path.join(img_dir, 'logo_512.png')
    shutil.copyfile(xxxhdpi_logo, target_drawable_logo)
    shutil.copyfile(xxxhdpi_logo, target_images_logo)
    print(f"Restored full-bleed {target_drawable_logo} and {target_images_logo} from {xxxhdpi_logo}")

    # 5. Render high-res PNG for Play Store & ATT-1482 preview
    temp_svg = os.path.join(preview_dir, 'temp_icon.svg')
    svg_content = vector_to_svg(fg_xml, background_color="#FFFFFF")
    with open(temp_svg, 'w', encoding='utf-8') as f:
        f.write(svg_content)

    play_store_icon = os.path.join(img_dir, 'play_store_icon_512.png')
    preview_png = os.path.join(preview_dir, 'att1482_reconstructed_preview.png')
    subprocess.run(['/snap/bin/inkscape', temp_svg, '-o', play_store_icon, '-w', '512', '-h', '512'], check=True)
    shutil.copyfile(play_store_icon, preview_png)
    if os.path.exists(temp_svg):
        os.remove(temp_svg)
    print(f"Generated high-res {play_store_icon} and {preview_png}")

    # 6. Render high-res PNGs for all 6 variants and composite ATT-1406 preview board
    variant_images = {}
    for key, xml_data in variant_xmls.items():
        svg_data = vector_to_svg(xml_data, background_color="#FFFFFF")
        temp_tier_svg = os.path.join(preview_dir, f'temp_{key}.svg')
        temp_tier_png = os.path.join(preview_dir, f'temp_{key}.png')
        with open(temp_tier_svg, 'w', encoding='utf-8') as f:
            f.write(svg_data)
        subprocess.run(['/snap/bin/inkscape', temp_tier_svg, '-o', temp_tier_png, '-w', '512', '-h', '512'], check=True)
        variant_images[key] = Image.open(temp_tier_png)
        if os.path.exists(temp_tier_svg):
            os.remove(temp_tier_svg)
        if os.path.exists(temp_tier_png):
            os.remove(temp_tier_png)

    board_output = os.path.join(preview_dir, 'att1406_reconstructed_tiers_preview.png')
    composite_tiers_preview(variant_images, board_output)

if __name__ == '__main__':
    main()
