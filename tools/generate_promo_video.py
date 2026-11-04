#!/usr/bin/env python3
"""
tools/generate_promo_video.py

Automated Google Play Store Promo Video & Feature Showcase Generator for aTrainingTracker.
Composites real Android UI screenshots into an authentic bicycle handlebar / outdoor cockpit
perspective with smartphone bezel, touch ripple animations, zoomed B-roll callouts, and
high-contrast visual-first title banners. Outputs composite keyframes and 1080p MP4 showcase video.

Complies with REQ-STP-006, TST-STP-006, and Rule 67.
"""

import argparse
import math
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageFont

WIDTH = 1920
HEIGHT = 1080
MOUNT_CX = 680

def get_font(size: int, bold: bool = False) -> ImageFont.ImageFont:
    """Load standard system TrueType font or fallback to default."""
    font_names = [
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
        "/usr/share/fonts/truetype/freefont/FreeSansBold.ttf" if bold else "/usr/share/fonts/truetype/freefont/FreeSans.ttf",
    ]
    for fn in font_names:
        if os.path.exists(fn):
            try:
                return ImageFont.truetype(fn, size)
            except Exception:
                pass
    return ImageFont.load_default()

def generate_procedural_handlebar_background(path: str):
    """
    Generates an authentic outdoor cycling perspective (asphalt road, horizon, greenery,
    aero carbon stem, 4 titanium bolts, drop handlebar, and out-front dual-arm computer mount)
    symmetrically centered directly behind and beneath the smartphone at X = 680 (MOUNT_CX).
    """
    img = Image.new("RGBA", (WIDTH, HEIGHT), (20, 25, 30, 255))
    draw = ImageDraw.Draw(img)

    # 1. Sky & atmospheric horizon (top half)
    for y in range(480):
        t = y / 480.0
        r = int(120 + 80 * (1.0 - t))
        g = int(170 + 60 * (1.0 - t))
        b = int(220 + 35 * (1.0 - t))
        draw.line([(0, y), (WIDTH, y)], fill=(r, g, b, 255))

    # Distant mountains / rolling hills
    hill_points = [(0, 420)]
    for x in range(0, WIDTH + 40, 40):
        hy = 390 + int(35 * math.sin(x * 0.005) + 20 * math.cos(x * 0.012))
        hill_points.append((x, hy))
    hill_points.extend([(WIDTH, 500), (0, 500)])
    draw.polygon(hill_points, fill=(70, 95, 80, 255))

    # Roadside greenery
    draw.polygon([(0, 460), (WIDTH, 460), (WIDTH, 560), (0, 560)], fill=(45, 80, 50, 255))

    # 2. Asphalt road perspective
    road_poly = [
        (int(WIDTH * 0.40), 460),
        (int(WIDTH * 0.60), 460),
        (WIDTH + 200, HEIGHT),
        (-200, HEIGHT)
    ]
    draw.polygon(road_poly, fill=(65, 70, 75, 255))

    # Asphalt center dash lines
    for i in range(8):
        y1 = 470 + int(math.pow(i / 7.0, 1.8) * 360)
        y2 = y1 + int(12 + i * 8)
        w1 = max(2, int(i * 1.5))
        w2 = max(3, int((i + 1) * 2.2))
        cx = int(WIDTH * 0.50)
        draw.polygon([(cx - w1, y1), (cx + w1, y1), (cx + w2, y2), (cx - w2, y2)], fill=(220, 220, 200, 200))

    # 3. Soft outdoor blur for natural camera depth of field bokeh
    img = img.filter(ImageFilter.GaussianBlur(radius=5))
    draw = ImageDraw.Draw(img)

    # 4. Aero Carbon Stem (centered symmetrically at MOUNT_CX = 680)
    bar_y = 970
    stem_y_top = bar_y - 20
    stem_top_w = 76
    stem_bot_w = 114

    stem_poly = [
        (MOUNT_CX - stem_top_w // 2, stem_y_top),
        (MOUNT_CX + stem_top_w // 2, stem_y_top),
        (MOUNT_CX + stem_bot_w // 2, HEIGHT),
        (MOUNT_CX - stem_bot_w // 2, HEIGHT)
    ]
    draw.polygon(stem_poly, fill=(24, 26, 30, 255))
    draw.line([(MOUNT_CX - stem_top_w // 2, stem_y_top), (MOUNT_CX - stem_bot_w // 2, HEIGHT)], fill=(65, 72, 82, 255), width=2)
    draw.line([(MOUNT_CX + stem_top_w // 2, stem_y_top), (MOUNT_CX + stem_bot_w // 2, HEIGHT)], fill=(12, 14, 16, 255), width=2)

    # Carbon weave cross-hatch texture on stem
    for sy in range(stem_y_top, HEIGHT, 14):
        w_at_y = int(stem_top_w + (stem_bot_w - stem_top_w) * (sy - stem_y_top) / max(1, HEIGHT - stem_y_top))
        draw.line([(MOUNT_CX - w_at_y // 2 + 4, sy), (MOUNT_CX + w_at_y // 2 - 4, sy + 7)], fill=(34, 38, 44, 180), width=1)
        draw.line([(MOUNT_CX - w_at_y // 2 + 4, sy + 7), (MOUNT_CX + w_at_y // 2 - 4, sy)], fill=(18, 20, 23, 180), width=1)

    # 5. Carbon Drop Handlebars across bar_y = 970 through MOUNT_CX = 680
    bar_h = 44
    # Left bar
    draw.polygon([(0, bar_y + 10), (MOUNT_CX - 35, bar_y), (MOUNT_CX - 35, bar_y + bar_h), (0, bar_y + 10 + bar_h)], fill=(30, 33, 38, 255))
    draw.line([(0, bar_y + 10), (MOUNT_CX - 35, bar_y)], fill=(80, 88, 98, 255), width=3)
    draw.line([(0, bar_y + 10 + bar_h), (MOUNT_CX - 35, bar_y + bar_h)], fill=(15, 17, 20, 255), width=2)

    # Right bar
    draw.polygon([(MOUNT_CX + 35, bar_y), (WIDTH, bar_y + 10), (WIDTH, bar_y + 10 + bar_h), (MOUNT_CX + 35, bar_y + bar_h)], fill=(30, 33, 38, 255))
    draw.line([(MOUNT_CX + 35, bar_y), (WIDTH, bar_y + 10)], fill=(80, 88, 98, 255), width=3)
    draw.line([(MOUNT_CX + 35, bar_y + bar_h), (WIDTH, bar_y + 10 + bar_h)], fill=(15, 17, 20, 255), width=2)

    # Handlebar tape wrap texture lines
    for bx in range(40, MOUNT_CX - 45, 36):
        draw.line([(bx, bar_y + bar_h + 8), (bx + 18, bar_y + 4)], fill=(20, 22, 26, 210), width=4)
    for bx in range(MOUNT_CX + 60, WIDTH - 40, 36):
        draw.line([(bx, bar_y + 4), (bx + 18, bar_y + bar_h + 8)], fill=(20, 22, 26, 210), width=4)

    # 6. Out-Front Dual-Arm Computer Mount reaching up to smartphone center cy = 570
    arm_left_clamp_x = MOUNT_CX - 44
    arm_right_clamp_x = MOUNT_CX + 44
    cradle_y = 570

    # Dual CNC alloy mount arms
    draw.polygon([(arm_left_clamp_x - 8, bar_y), (MOUNT_CX - 32, cradle_y + 40), (MOUNT_CX - 18, cradle_y + 40), (arm_left_clamp_x + 8, bar_y)], fill=(22, 24, 28, 255))
    draw.line([(arm_left_clamp_x - 8, bar_y), (MOUNT_CX - 32, cradle_y + 40)], fill=(95, 105, 118, 255), width=2)
    draw.line([(arm_left_clamp_x + 8, bar_y), (MOUNT_CX - 18, cradle_y + 40)], fill=(12, 14, 16, 255), width=2)

    draw.polygon([(arm_right_clamp_x - 8, bar_y), (MOUNT_CX + 18, cradle_y + 40), (MOUNT_CX + 32, cradle_y + 40), (arm_right_clamp_x + 8, bar_y)], fill=(22, 24, 28, 255))
    draw.line([(arm_right_clamp_x + 8, bar_y), (MOUNT_CX + 32, cradle_y + 40)], fill=(95, 105, 118, 255), width=2)
    draw.line([(arm_right_clamp_x - 8, bar_y), (MOUNT_CX + 18, cradle_y + 40)], fill=(12, 14, 16, 255), width=2)

    # Circular cradle puck with quarter-turn insert
    draw.ellipse([MOUNT_CX - 50, cradle_y - 25, MOUNT_CX + 50, cradle_y + 75], fill=(28, 31, 36, 255), outline=(80, 90, 102, 255), width=3)
    draw.ellipse([MOUNT_CX - 22, cradle_y, MOUNT_CX + 22, cradle_y + 44], fill=(14, 16, 18, 255), outline=(110, 122, 138, 255), width=2)
    draw.rectangle([MOUNT_CX - 6, cradle_y + 8, MOUNT_CX + 6, cradle_y + 36], fill=(45, 50, 58, 255))
    draw.rectangle([MOUNT_CX - 14, cradle_y + 16, MOUNT_CX + 14, cradle_y + 28], fill=(45, 50, 58, 255))

    # 7. Stem Faceplate Clamp (centered symmetrically at MOUNT_CX = 680)
    fp_w = 72
    fp_h = 52
    fp_x1 = MOUNT_CX - fp_w // 2
    fp_y1 = bar_y - 6
    fp_x2 = MOUNT_CX + fp_w // 2
    fp_y2 = bar_y + fp_h - 6
    draw.rounded_rectangle([fp_x1, fp_y1, fp_x2, fp_y2], radius=6, fill=(36, 40, 46, 255), outline=(100, 110, 124, 255), width=2)
    draw.rounded_rectangle([MOUNT_CX - 12, bar_y + 6, MOUNT_CX + 12, bar_y + 32], radius=3, fill=(18, 20, 24, 255), outline=(60, 66, 75, 255), width=1)

    # 8. Four Titanium Hex Bolts
    bolt_coords = [
        (MOUNT_CX - 24, bar_y + 4),
        (MOUNT_CX + 24, bar_y + 4),
        (MOUNT_CX - 24, bar_y + 36),
        (MOUNT_CX + 24, bar_y + 36)
    ]
    for bx, by in bolt_coords:
        draw.ellipse([bx - 6, by - 6, bx + 6, by + 6], fill=(175, 185, 195, 255), outline=(230, 240, 250, 255), width=1)
        draw.polygon([
            (bx - 3, by - 2), (bx, by - 4), (bx + 3, by - 2),
            (bx + 3, by + 2), (bx, by + 4), (bx - 3, by + 2)
        ], fill=(30, 35, 40, 255))

    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.convert("RGB").save(path, format="PNG", quality=95)
    print(f"[Generator] Created procedural handlebar background: {path} (Mount centered at X={MOUNT_CX})")

def draw_smartphone_frame(bg_image: Image.Image, screenshot: Image.Image, cx: int = MOUNT_CX, cy: int = 570, target_h: int = 720) -> tuple:
    """Renders a realistic Pixel 10 bezel with rounded corners and drops the screenshot into it."""
    aspect = screenshot.width / float(screenshot.height)
    screen_w = int(target_h * aspect)
    screen_h = target_h

    bezel_pad = 14
    outer_w = screen_w + bezel_pad * 2
    outer_h = screen_h + bezel_pad * 2

    phone_layer = Image.new("RGBA", (outer_w, outer_h), (0, 0, 0, 0))
    pdraw = ImageDraw.Draw(phone_layer)

    # Outer bezel with rounded corners
    corner_radius = 36
    pdraw.rounded_rectangle(
        [(0, 0), (outer_w - 1, outer_h - 1)],
        radius=corner_radius,
        fill=(22, 24, 27, 255),
        outline=(85, 92, 102, 255),
        width=3
    )

    # Screen cutout
    resized_screen = screenshot.resize((screen_w, screen_h), Image.Resampling.LANCZOS)
    
    # Mask screen to rounded corners
    mask = Image.new("L", (screen_w, screen_h), 0)
    mdraw = ImageDraw.Draw(mask)
    mdraw.rounded_rectangle([(0, 0), (screen_w - 1, screen_h - 1)], radius=26, fill=255)

    phone_layer.paste(resized_screen, (bezel_pad, bezel_pad), mask)

    # Speaker notch & punch hole camera
    cam_x = outer_w // 2
    cam_y = bezel_pad + 16
    pdraw.ellipse([(cam_x - 7, cam_y - 7), (cam_x + 7, cam_y + 7)], fill=(8, 10, 12, 255), outline=(40, 45, 50, 255), width=1)

    # Subtle screen glass glare reflection line
    glare = Image.new("RGBA", (outer_w, outer_h), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glare)
    gdraw.polygon([(0, 0), (int(outer_w * 0.4), 0), (0, int(outer_h * 0.7))], fill=(255, 255, 255, 18))
    phone_layer = Image.alpha_composite(phone_layer, glare)

    # Paste phone onto background centered at (cx, cy)
    top_left_x = cx - outer_w // 2
    top_left_y = cy - outer_h // 2
    bg_image.paste(phone_layer, (top_left_x, top_left_y), phone_layer)

    return (top_left_x, top_left_y, outer_w, outer_h)

def draw_callout_banner(img: Image.Image, title: str, subtitle: str, y_pos: int = 40):
    """Draws a premium high-contrast visual-first banner across the upper canvas."""
    draw = ImageDraw.Draw(img)
    font_title = get_font(38, bold=True)
    font_sub = get_font(22, bold=False)

    # Title & subtitle measurement
    tb_title = draw.textbbox((0, 0), title, font=font_title)
    tb_sub = draw.textbbox((0, 0), subtitle, font=font_sub)

    w_title = tb_title[2] - tb_title[0]
    w_sub = tb_sub[2] - tb_sub[0]
    card_w = max(w_title, w_sub) + 80
    card_h = 110

    card_x1 = (WIDTH - card_w) // 2
    card_y1 = y_pos
    card_x2 = card_x1 + card_w
    card_y2 = card_y1 + card_h

    # Card backdrop with soft shadow
    overlay = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    odraw = ImageDraw.Draw(overlay)
    
    # Shadow
    odraw.rounded_rectangle([(card_x1 + 4, card_y1 + 6), (card_x2 + 4, card_y2 + 6)], radius=18, fill=(0, 0, 0, 140))
    # Card surface
    odraw.rounded_rectangle([(card_x1, card_y1), (card_x2, card_y2)], radius=18, fill=(18, 22, 28, 235), outline=(0, 180, 216, 255), width=2)

    img.paste(Image.alpha_composite(img.convert("RGBA"), overlay), (0, 0))
    draw = ImageDraw.Draw(img)

    # Draw centered text
    title_x = (WIDTH - w_title) // 2
    title_y = card_y1 + 18
    draw.text((title_x, title_y), title, font=font_title, fill=(255, 255, 255, 255))

    sub_x = (WIDTH - w_sub) // 2
    sub_y = title_y + 46
    draw.text((sub_x, sub_y), subtitle, font=font_sub, fill=(180, 215, 235, 255))

def draw_touch_ripple(img: Image.Image, x: int, y: int, radius: int = 35):
    """Draws an animated touch gesture ripple indicating an athlete interaction."""
    overlay = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    odraw = ImageDraw.Draw(overlay)

    # Concentric rings
    odraw.ellipse([(x - radius, y - radius), (x + radius, y + radius)], outline=(2, 136, 209, 230), width=4)
    odraw.ellipse([(x - radius + 12, y - radius + 12), (x + radius - 12, y + radius - 12)], outline=(79, 195, 247, 180), width=3)
    odraw.ellipse([(x - 8, y - 8), (x + 8, y + 8)], fill=(255, 255, 255, 240))

    img.paste(Image.alpha_composite(img.convert("RGBA"), overlay), (0, 0))

def draw_broll_cutaway(img: Image.Image, screenshot: Image.Image, crop_box: tuple, target_x: int, target_y: int, w: int, h: int, title: str):
    """Renders a zoomed-in B-Roll cutaway card highlighting fine metric details."""
    cropped = screenshot.crop(crop_box).resize((w, h), Image.Resampling.LANCZOS)

    overlay = Image.new("RGBA", (w + 24, h + 50), (0, 0, 0, 0))
    odraw = ImageDraw.Draw(overlay)

    # Rounded card container
    odraw.rounded_rectangle([(0, 0), (w + 23, h + 49)], radius=16, fill=(16, 20, 25, 245), outline=(2, 136, 209, 255), width=2)
    overlay.paste(cropped, (12, 38))

    font_badge = get_font(18, bold=True)
    odraw.text((16, 10), title.upper(), font=font_badge, fill=(79, 195, 247, 255))

    img.paste(overlay, (target_x, target_y), overlay)

def find_screenshot(screens_dir: Path, filename_candidates: list) -> Image.Image:
    """Finds the first existing screenshot from candidates across store presence and docs/guide/img."""
    for c in filename_candidates:
        p = screens_dir / c
        if p.exists():
            return Image.open(p).convert("RGB")
        # Direct check in docs/guide/img/
        guide_p = screens_dir.parent.parent / "guide" / "img" / c
        if guide_p.exists():
            return Image.open(guide_p).convert("RGB")
        guide_p2 = screens_dir.parent / "guide" / "img" / c
        if guide_p2.exists():
            return Image.open(guide_p2).convert("RGB")
    
    # Fallback placeholder image
    fb = Image.new("RGB", (1080, 2424), (20, 22, 28))
    fdraw = ImageDraw.Draw(fb)
    fdraw.text((100, 300), "aTrainingTracker\nPreview Screen", font=get_font(48, bold=True), fill=(255, 255, 255))
    return fb

def render_scene(bg_path: str, screens_dir: Path, scene_idx: int) -> Image.Image:
    """Renders one of the 5 canonical storyboard showcase scenes at 1920x1080."""
    bg = Image.open(bg_path).convert("RGBA").resize((WIDTH, HEIGHT))

    if scene_idx == 1:
        # Scene 1: Cockpit & Electronic Bell
        scr = find_screenshot(screens_dir, ["01_cockpit_tracking.png", "01_cockpit_standard.png"])
        px, py, pw, ph = draw_smartphone_frame(bg, scr, cx=MOUNT_CX, cy=570, target_h=720)
        draw_callout_banner(bg, "YOUR ULTIMATE BIKE COMPUTER", "Live Telemetry Cockpit • Zero Subscriptions • 100% Ad-Free")
        draw_touch_ripple(bg, px + int(pw * 0.78), py + int(ph * 0.88), radius=38)  # Touch on Bell button

        # B-roll zoom on speed/distance/HR telemetry cluster
        crop = (int(scr.width * 0.04), int(scr.height * 0.18), int(scr.width * 0.96), int(scr.height * 0.60))
        draw_broll_cutaway(bg, scr, crop, target_x=1220, target_y=320, w=580, h=360, title="Color-Coded Zone Telemetry")

    elif scene_idx == 2:
        # Scene 2: Sensors & Gear Matrix
        scr = find_screenshot(screens_dir, ["02_sensor_management.png", "02_sensor_pairing.png", "03_equipment_bikes.png"])
        px, py, pw, ph = draw_smartphone_frame(bg, scr, cx=MOUNT_CX, cy=570, target_h=720)
        draw_callout_banner(bg, "DUAL BLE + NATIVE ANT+", "Intelligent Hardware Arbitration • Automatic Gear Matrix • Battery Gauges")
        draw_touch_ripple(bg, px + int(pw * 0.50), py + int(ph * 0.62), radius=34)

        # B-roll zoom on paired hardware sensors (cadence, speed, HR with battery gauges)
        crop = (int(scr.width * 0.04), int(scr.height * 0.47), int(scr.width * 0.96), int(scr.height * 0.88))
        draw_broll_cutaway(bg, scr, crop, target_x=1220, target_y=320, w=580, h=360, title="Multi-Protocol Sensor Matrix")

    elif scene_idx == 3:
        # Scene 3: Navigation, Dynamic Climbs & Strava Live Segments
        scr = find_screenshot(screens_dir, ["03_live_map.png", "05_map_cockpit.png"])
        scr_broll = find_screenshot(screens_dir, ["05_live_segments.png", "03_live_map.png"])
        px, py, pw, ph = draw_smartphone_frame(bg, scr, cx=MOUNT_CX, cy=570, target_h=720)
        draw_callout_banner(bg, "OFFLINE MAPS, LIVE CLIMBS & STRAVA SEGMENTS", "Adaptive Elevation Profiles • Grade Colors • Real-Time PR / KOM Delta")
        draw_touch_ripple(bg, px + int(pw * 0.50), py + int(ph * 0.65), radius=35)

        # B-roll zoom on Strava Live Segment tracking with PR comparison
        crop = (int(scr_broll.width * 0.04), int(scr_broll.height * 0.68), int(scr_broll.width * 0.96), int(scr_broll.height * 0.93))
        draw_broll_cutaway(bg, scr_broll, crop, target_x=1220, target_y=320, w=580, h=360, title="Strava Live Segment Tracking")

    elif scene_idx == 4:
        # Scene 4: Route Clustering & Coggan/Skiba Analytics
        scr = find_screenshot(screens_dir, ["04_lieblingsstrecken.png", "06_cluster_expanded.png"])
        scr_broll = find_screenshot(screens_dir, ["05_physiology_zones.png", "07_training_zones.png"])
        px, py, pw, ph = draw_smartphone_frame(bg, scr, cx=MOUNT_CX, cy=570, target_h=720)
        draw_callout_banner(bg, "AUTONOMOUS ROUTE CLUSTERING & COGGAN / SKIBA ANALYTICS", "Lieblingsstrecken Heatmap • Skiba W'bal Battery • NP, IF, TSS & Banister PMC")
        draw_touch_ripple(bg, px + int(pw * 0.50), py + int(ph * 0.40), radius=34)

        # B-roll zoom on Coggan physiological training zones histogram
        crop = (int(scr_broll.width * 0.04), int(scr_broll.height * 0.24), int(scr_broll.width * 0.96), int(scr_broll.height * 0.69))
        draw_broll_cutaway(bg, scr_broll, crop, target_x=1220, target_y=320, w=580, h=360, title="Coggan Heart Rate & Power Zones")

    elif scene_idx == 5:
        # Scene 5: 100% Privacy Sovereignty, Cloud Sync & Supporter CTA
        scr = find_screenshot(screens_dir, ["06_periods_analytics.png", "08_periods_screen.png"])
        scr_broll = find_screenshot(screens_dir, ["09_fit_export.png", "09_dropbox_backup.png"])
        px, py, pw, ph = draw_smartphone_frame(bg, scr, cx=MOUNT_CX, cy=570, target_h=720)
        draw_callout_banner(bg, "100% LOCAL DATA OWNERSHIP • GET IT ON GOOGLE PLAY", "1-Click .FIT / Strava Export • Zero Accounts • Community-Driven Supporter Tiers")
        draw_touch_ripple(bg, px + int(pw * 0.70), py + int(ph * 0.32), radius=34)

        # B-roll zoom on Android SAF .FIT export & cloud sync targets (Drive, Dropbox, NAS)
        crop = (int(scr_broll.width * 0.04), int(scr_broll.height * 0.08), int(scr_broll.width * 0.96), int(scr_broll.height * 0.45))
        draw_broll_cutaway(bg, scr_broll, crop, target_x=1220, target_y=320, w=580, h=360, title="SAF .FIT & Cloud Export")

    return bg.convert("RGB")

def compile_video_gstreamer(frame_paths: list, output_mp4: str, fps: int = 1) -> bool:
    """Encodes frame list to MP4 using gst-launch-1.0 with x264enc / openh264enc."""
    if not shutil.which("gst-launch-1.0"):
        return False

    with tempfile.TemporaryDirectory() as tmpdir:
        for idx, fp in enumerate(frame_paths):
            target = os.path.join(tmpdir, f"frame_{idx:05d}.png")
            shutil.copy(fp, target)

        stop_idx = len(frame_paths) - 1

        # Attempt 1: x264enc
        cmd_x264 = [
            "gst-launch-1.0", "-e",
            "multifilesrc", f"location={tmpdir}/frame_%05d.png", "index=0", f"stop-index={stop_idx}",
            f"caps=image/png,framerate={fps}/1",
            "!", "pngdec",
            "!", "videoconvert",
            "!", "x264enc", "bitrate=4000", "speed-preset=fast",
            "!", "h264parse",
            "!", "mp4mux",
            "!", "filesink", f"location={output_mp4}"
        ]
        res = subprocess.run(cmd_x264, capture_output=True, text=True)
        if res.returncode == 0 and os.path.exists(output_mp4) and os.path.getsize(output_mp4) > 1000:
            print(f"[VideoEncoder] Successfully encoded with GStreamer x264enc: {output_mp4} ({os.path.getsize(output_mp4)} bytes)")
            return True

        # Attempt 2: openh264enc fallback
        cmd_openh264 = [
            "gst-launch-1.0", "-e",
            "multifilesrc", f"location={tmpdir}/frame_%05d.png", "index=0", f"stop-index={stop_idx}",
            f"caps=image/png,framerate={fps}/1",
            "!", "pngdec",
            "!", "videoconvert",
            "!", "openh264enc",
            "!", "h264parse",
            "!", "mp4mux",
            "!", "filesink", f"location={output_mp4}"
        ]
        res = subprocess.run(cmd_openh264, capture_output=True, text=True)
        if res.returncode == 0 and os.path.exists(output_mp4) and os.path.getsize(output_mp4) > 1000:
            print(f"[VideoEncoder] Successfully encoded with GStreamer openh264enc: {output_mp4} ({os.path.getsize(output_mp4)} bytes)")
            return True

    return False

def compile_video_ffmpeg(frame_paths: list, output_mp4: str, fps: int = 1) -> bool:
    """Encodes frame list to MP4 using ffmpeg if present."""
    if not shutil.which("ffmpeg"):
        return False

    with tempfile.TemporaryDirectory() as tmpdir:
        for idx, fp in enumerate(frame_paths):
            shutil.copy(fp, os.path.join(tmpdir, f"frame_{idx:05d}.png"))

        cmd = [
            "ffmpeg", "-y", "-r", str(fps),
            "-i", os.path.join(tmpdir, "frame_%05d.png"),
            "-c:v", "libx264", "-pix_fmt", "yuv420p",
            output_mp4
        ]
        res = subprocess.run(cmd, capture_output=True, text=True)
        if res.returncode == 0 and os.path.exists(output_mp4) and os.path.getsize(output_mp4) > 1000:
            print(f"[VideoEncoder] Successfully encoded with FFmpeg: {output_mp4}")
            return True
    return False

def main():
    parser = argparse.ArgumentParser(description="Generate Google Play Store Promo Video & Feature Showcase Assets")
    parser.add_argument("--bg-photo", default="docs/store_presence/video/handlebar_bg_template.png", help="Custom outdoor handlebar background photo")
    parser.add_argument("--screens-dir", default="docs/store_presence/screenshots", help="Directory containing UI screenshots")
    parser.add_argument("--output-video", default="docs/store_presence/video/att_promo_showcase.mp4", help="Output MP4 file path")
    parser.add_argument("--output-frames", default="docs/store_presence/video/frames", help="Directory for exported keyframes")
    parser.add_argument("--fps", type=int, default=1, help="Frame rate for video compilation")
    parser.add_argument("--duration", type=int, default=60, help="Target duration in seconds")
    parser.add_argument("--force-bg", action="store_true", help="Force regenerate procedural background plate")
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parent.parent
    bg_photo_path = repo_root / args.bg_photo
    screens_dir = repo_root / args.screens_dir
    out_video_path = repo_root / args.output_video
    out_frames_dir = repo_root / args.output_frames
    walkthrough_img_dir = repo_root / "docs" / "engineering" / "walkthroughs" / "img"

    out_frames_dir.mkdir(parents=True, exist_ok=True)
    out_video_path.parent.mkdir(parents=True, exist_ok=True)
    walkthrough_img_dir.mkdir(parents=True, exist_ok=True)

    # 1. Background plate check / generation (always regenerate procedural template)
    is_default_template = "handlebar_bg_template.png" in str(bg_photo_path)
    if args.force_bg or not bg_photo_path.exists() or is_default_template:
        print(f"[Generator] Generating procedural handlebar plate at {bg_photo_path} (MOUNT_CX={MOUNT_CX})...")
        generate_procedural_handlebar_background(str(bg_photo_path))

    # 2. Render 5 distinct storyboard scenes
    scene_names = [
        "scene_01_cockpit.png",
        "scene_02_sensors.png",
        "scene_03_navigation.png",
        "scene_04_clustering.png",
        "scene_05_privacy_export.png",
    ]
    scene_durations = [10, 15, 15, 10, 10]  # Sum = 60s

    rendered_scenes = []
    print("[Generator] Rendering 5 storyboard composite scenes...")
    for idx in range(1, 6):
        scene_img = render_scene(str(bg_photo_path), screens_dir, idx)
        dest_fp = out_frames_dir / scene_names[idx - 1]
        scene_img.save(dest_fp, quality=95)
        rendered_scenes.append(dest_fp)
        print(f"  ✓ Rendered Scene {idx}: {dest_fp} ({scene_img.width}x{scene_img.height})")

    # Copy key previews into docs/engineering/walkthroughs/img/ for walkthrough evidence
    shutil.copy(rendered_scenes[0], walkthrough_img_dir / "att2274_scene_01_cockpit.png")
    shutil.copy(rendered_scenes[2], walkthrough_img_dir / "att2274_scene_03_navigation.png")
    print("  ✓ Copied evidence previews to docs/engineering/walkthroughs/img/")

    # 3. Build sequence of frames matching scene durations
    timeline_frames = []
    with tempfile.TemporaryDirectory() as seq_dir:
        frame_counter = 0
        for s_idx, duration in enumerate(scene_durations):
            scene_path = rendered_scenes[s_idx]
            for _ in range(duration * args.fps):
                frame_fp = os.path.join(seq_dir, f"frame_{frame_counter:05d}.png")
                shutil.copy(scene_path, frame_fp)
                timeline_frames.append(frame_fp)
                frame_counter += 1

        print(f"[Generator] Assembled {len(timeline_frames)} timeline frames for {args.duration}s video at {args.fps} fps.")

        # 4. Compile video using available backend
        encoded = False
        if not encoded:
            encoded = compile_video_ffmpeg(timeline_frames, str(out_video_path), fps=args.fps)
        if not encoded:
            encoded = compile_video_gstreamer(timeline_frames, str(out_video_path), fps=args.fps)

        if not encoded:
            print("[Warning] Neither ffmpeg nor GStreamer h264 encoder succeeded in creating video.", file=sys.stderr)
            sys.exit(1)

    print(f"\n[SUCCESS] Generated Promo Video and Assets:")
    print(f"  • Video: {out_video_path} ({os.path.getsize(out_video_path)} bytes)")
    print(f"  • Frames: {out_frames_dir} (5 scenes)")
    print(f"  • Walkthrough Images: {walkthrough_img_dir}/att2274_*.png")

if __name__ == "__main__":
    main()
