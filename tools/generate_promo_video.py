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
    """Generates an authentic outdoor cycling perspective (asphalt road, horizon, greenery, carbon handlebar & stem mount)."""
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
    draw.polygon([(0, 470), (WIDTH, 470), (WIDTH, 580), (0, 580)], fill=(45, 80, 50, 255))

    # 2. Asphalt road perspective
    road_poly = [
        (int(WIDTH * 0.42), 470),
        (int(WIDTH * 0.58), 470),
        (WIDTH + 200, HEIGHT),
        (-200, HEIGHT)
    ]
    draw.polygon(road_poly, fill=(65, 70, 75, 255))

    # Asphalt center dash lines
    for i in range(8):
        y1 = 480 + int(math.pow(i / 7.0, 1.8) * 350)
        y2 = y1 + int(12 + i * 8)
        w1 = max(2, int(i * 1.5))
        w2 = max(3, int((i + 1) * 2.2))
        cx = int(WIDTH * 0.50)
        draw.polygon([(cx - w1, y1), (cx + w1, y1), (cx + w2, y2), (cx - w2, y2)], fill=(220, 220, 200, 200))

    # 3. Soft outdoor blur for natural camera depth of field
    img = img.filter(ImageFilter.GaussianBlur(radius=3))
    draw = ImageDraw.Draw(img)

    # 4. Out-front bike mount arm (reaching forward from bottom center)
    stem_top_w = 60
    stem_bot_w = 110
    mount_arm = [
        (WIDTH // 2 - stem_top_w // 2, 600),
        (WIDTH // 2 + stem_top_w // 2, 600),
        (WIDTH // 2 + stem_bot_w // 2, HEIGHT),
        (WIDTH // 2 - stem_bot_w // 2, HEIGHT)
    ]
    draw.polygon(mount_arm, fill=(25, 28, 32, 255))
    draw.line([(WIDTH // 2 - stem_top_w // 2, 600), (WIDTH // 2 - stem_bot_w // 2, HEIGHT)], fill=(55, 60, 68, 255), width=2)
    draw.line([(WIDTH // 2 + stem_top_w // 2, 600), (WIDTH // 2 + stem_bot_w // 2, HEIGHT)], fill=(15, 18, 20, 255), width=2)

    # Clamp head
    draw.ellipse([WIDTH // 2 - 45, 570, WIDTH // 2 + 45, 660], fill=(30, 34, 38, 255), outline=(60, 65, 72, 255), width=3)
    draw.ellipse([WIDTH // 2 - 12, 603, WIDTH // 2 + 12, 627], fill=(12, 14, 16, 255))

    # 5. Carbon Drop Handlebars across bottom
    # Left bar
    left_bar = [
        (0, 820),
        (WIDTH // 2 - 50, 770),
        (WIDTH // 2 - 50, 830),
        (0, 900)
    ]
    draw.polygon(left_bar, fill=(35, 38, 42, 255))
    draw.line([(0, 820), (WIDTH // 2 - 50, 770)], fill=(75, 80, 88, 255), width=3)

    # Right bar
    right_bar = [
        (WIDTH // 2 + 50, 770),
        (WIDTH, 820),
        (WIDTH, 900),
        (WIDTH // 2 + 50, 830)
    ]
    draw.polygon(right_bar, fill=(35, 38, 42, 255))
    draw.line([(WIDTH // 2 + 50, 770), (WIDTH, 820)], fill=(75, 80, 88, 255), width=3)

    # Subtle handlebar tape wrap texture lines
    for bx in range(60, WIDTH // 2 - 60, 45):
        draw.line([(bx, 840), (bx + 20, 800)], fill=(20, 22, 25, 200), width=4)
    for bx in range(WIDTH // 2 + 80, WIDTH - 40, 45):
        draw.line([(bx, 800), (bx + 20, 840)], fill=(20, 22, 25, 200), width=4)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.convert("RGB").save(path, format="PNG", quality=95)
    print(f"[Generator] Created procedural handlebar background: {path}")

def draw_smartphone_frame(bg_image: Image.Image, screenshot: Image.Image, cx: int, cy: int, target_h: int = 740) -> tuple:
    """Renders a realistic Pixel 10 bezel with rounded corners and drops the screenshot into it."""
    # Compute proportional size
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
    """Finds the first existing screenshot from a list of candidates or creates a rich fallback."""
    for c in filename_candidates:
        p = screens_dir / c
        if p.exists():
            return Image.open(p).convert("RGB")
        # Try parent guide/img
        guide_p = screens_dir.parent / "guide" / "img" / c
        if guide_p.exists():
            return Image.open(guide_p).convert("RGB")
    
    # Fallback placeholder image
    fb = Image.new("RGB", (1080, 2400), (20, 22, 28))
    fdraw = ImageDraw.Draw(fb)
    fdraw.text((100, 300), "aTrainingTracker\nPreview Screen", font=get_font(48, bold=True), fill=(255, 255, 255))
    return fb

def render_scene(bg_path: str, screens_dir: Path, scene_idx: int) -> Image.Image:
    """Renders one of the 5 canonical storyboard showcase scenes at 1920x1080."""
    bg = Image.open(bg_path).convert("RGBA").resize((WIDTH, HEIGHT))

    if scene_idx == 1:
        # Scene 1: Cockpit & Electronic Bell
        scr = find_screenshot(screens_dir, ["01_cockpit_tracking.png", "01_cockpit_standard.png"])
        px, py, pw, ph = draw_smartphone_frame(bg, scr, cx=680, cy=610, target_h=760)
        draw_callout_banner(bg, "YOUR ULTIMATE BIKE COMPUTER", "Live Telemetry Cockpit • Zero Subscriptions • 100% Ad-Free")
        draw_touch_ripple(bg, px + int(pw * 0.78), py + int(ph * 0.88), radius=38) # Touch on Bell button

        # B-roll zoom on power/speed gauge
        crop = (int(scr.width * 0.05), int(scr.height * 0.20), int(scr.width * 0.95), int(scr.height * 0.50))
        draw_broll_cutaway(bg, scr, crop, target_x=1220, target_y=320, w=580, h=360, title="Color-Coded Zone Telemetry")

    elif scene_idx == 2:
        # Scene 2: Sensors & Gear Matrix
        scr = find_screenshot(screens_dir, ["02_sensor_management.png", "02_sensor_pairing.png", "03_equipment_bikes.png"])
        px, py, pw, ph = draw_smartphone_frame(bg, scr, cx=680, cy=610, target_h=760)
        draw_callout_banner(bg, "DUAL BLE + NATIVE ANT+", "Intelligent Hardware Arbitration • Automatic Gear Matrix • Battery Gauges")
        draw_touch_ripple(bg, px + int(pw * 0.50), py + int(ph * 0.35), radius=32)

        # B-roll zoom on sensor list
        crop = (int(scr.width * 0.05), int(scr.height * 0.15), int(scr.width * 0.95), int(scr.height * 0.55))
        draw_broll_cutaway(bg, scr, crop, target_x=1220, target_y=320, w=580, h=360, title="Multi-Protocol Sensor Linking")

    elif scene_idx == 3:
        # Scene 3: Navigation, Dynamic Climbs & Strava Live Segments
        scr = find_screenshot(screens_dir, ["03_live_map.png", "05_gpx_routes.png", "05_live_segments.png"])
        px, py, pw, ph = draw_smartphone_frame(bg, scr, cx=680, cy=610, target_h=760)
        draw_callout_banner(bg, "OFFLINE MAPS, LIVE CLIMBS & STRAVA SEGMENTS", "Adaptive Elevation Profiles • Grade Colors • Real-Time PR / KOM Delta")
        draw_touch_ripple(bg, px + int(pw * 0.50), py + int(ph * 0.65), radius=35)

        # B-roll zoom on climb profile
        crop = (int(scr.width * 0.05), int(scr.height * 0.50), int(scr.width * 0.95), int(scr.height * 0.85))
        draw_broll_cutaway(bg, scr, crop, target_x=1220, target_y=320, w=580, h=360, title="Adaptive Elevation Scrubbing")

    elif scene_idx == 4:
        # Scene 4: Route Clustering & Coggan/Skiba Analytics
        scr = find_screenshot(screens_dir, ["04_lieblingsstrecken.png", "05_physiology_zones.png", "06_cluster_expanded.png"])
        px, py, pw, ph = draw_smartphone_frame(bg, scr, cx=680, cy=610, target_h=760)
        draw_callout_banner(bg, "AUTONOMOUS ROUTE CLUSTERING & COGGAN / SKIBA ANALYTICS", "Lieblingsstrecken Heatmap • Skiba W'bal Battery • NP, IF, TSS & Banister PMC")
        draw_touch_ripple(bg, px + int(pw * 0.50), py + int(ph * 0.40), radius=34)

        # B-roll zoom on physiological analytics
        crop = (int(scr.width * 0.05), int(scr.height * 0.20), int(scr.width * 0.95), int(scr.height * 0.60))
        draw_broll_cutaway(bg, scr, crop, target_x=1220, target_y=320, w=580, h=360, title="Longitudinal Fitness & Fatigue")

    elif scene_idx == 5:
        # Scene 5: 100% Privacy Sovereignty, Cloud Sync & Supporter CTA
        scr = find_screenshot(screens_dir, ["06_periods_analytics.png", "08_yearly_activity_grid.png", "09_dropbox_backup.png"])
        px, py, pw, ph = draw_smartphone_frame(bg, scr, cx=680, cy=610, target_h=760)
        draw_callout_banner(bg, "100% LOCAL DATA OWNERSHIP • GET IT ON GOOGLE PLAY", "1-Click .FIT / Strava Export • Zero Accounts • Community-Driven Supporter Tiers")
        draw_touch_ripple(bg, px + int(pw * 0.80), py + int(ph * 0.25), radius=35)

        # B-roll zoom on yearly activity grid
        crop = (int(scr.width * 0.05), int(scr.height * 0.15), int(scr.width * 0.95), int(scr.height * 0.55))
        draw_broll_cutaway(bg, scr, crop, target_x=1220, target_y=320, w=580, h=360, title="Yearly Activity Contribution")

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

    # 1. Background plate check / generation
    if not bg_photo_path.exists():
        print(f"[Generator] Custom photo not found at {bg_photo_path}. Generating procedural handlebar plate...")
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
    # For a 60s video at 1 fps: 10 frames S1, 15 frames S2, 15 frames S3, 10 frames S4, 10 frames S5 = 60 frames
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
