#!/usr/bin/env python3
"""
Tool: tools/resample_high_density_drawables.py
Purpose: Populate missing high-density raster buckets (drawable-xxhdpi, drawable-xxxhdpi, drawable-nodpi)
for trademark and core drawables using high-quality Lanczos resampling (REQ-UI-380).
Includes automated validation for RGBA mode, non-zero alpha, and aspect ratio fidelity.
"""

import os
import sys
from PIL import Image

RES_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "../app/src/main/res"))

def ensure_dir(path):
    os.makedirs(path, exist_ok=True)

def resample_and_save(src_path, dst_path, target_size):
    if not os.path.exists(src_path):
        raise FileNotFoundError(f"Source file not found: {src_path}")
    
    with Image.open(src_path) as im:
        src_mode = im.mode
        src_w, src_h = im.size
        src_aspect = src_w / float(src_h)
        target_w, target_h = target_size
        target_aspect = target_w / float(target_h)

        if abs(src_aspect - target_aspect) > 0.01:
            raise ValueError(f"Aspect ratio mismatch for {dst_path}: src={src_aspect}, target={target_aspect}")

        im_rgba = im.convert("RGBA")
        resampled = im_rgba.resize(target_size, Image.Resampling.LANCZOS)

        # Automated Visual & Alpha Sanity Check
        extrema = resampled.getextrema()
        alpha_extrema = extrema[3]  # (min_alpha, max_alpha)
        if alpha_extrema[1] == 0:
            raise ValueError(f"Resampled image {dst_path} is completely transparent!")

        ensure_dir(os.path.dirname(dst_path))
        resampled.save(dst_path, "PNG")
        print(f"Generated: {os.path.relpath(dst_path, RES_DIR)} ({target_w}x{target_h}) from {os.path.relpath(src_path, RES_DIR)}")

def main():
    print(f"Resampling high-density drawables in: {RES_DIR}")

    # 1. ant_logo.png
    src_ant = os.path.join(RES_DIR, "drawable-xhdpi/ant_logo.png")
    resample_and_save(src_ant, os.path.join(RES_DIR, "drawable-xxhdpi/ant_logo.png"), (144, 144))
    resample_and_save(src_ant, os.path.join(RES_DIR, "drawable-xxxhdpi/ant_logo.png"), (192, 192))
    resample_and_save(src_ant, os.path.join(RES_DIR, "drawable-nodpi/ant_logo.png"), (96, 96))

    # 2. logo_protocol_bluetooth.png
    src_bt = os.path.join(RES_DIR, "drawable-xxhdpi/logo_protocol_bluetooth.png")
    resample_and_save(src_bt, os.path.join(RES_DIR, "drawable-xxxhdpi/logo_protocol_bluetooth.png"), (192, 192))
    resample_and_save(src_bt, os.path.join(RES_DIR, "drawable-nodpi/logo_protocol_bluetooth.png"), (96, 96))

    # 3. dropbox_logo_blue.png
    src_db = os.path.join(RES_DIR, "drawable-xxhdpi/dropbox_logo_blue.png")
    resample_and_save(src_db, os.path.join(RES_DIR, "drawable-xxxhdpi/dropbox_logo_blue.png"), (192, 192))
    resample_and_save(src_db, os.path.join(RES_DIR, "drawable-nodpi/dropbox_logo_blue.png"), (96, 96))

    # 4. logo.png (96x84 in xhdpi)
    src_logo = os.path.join(RES_DIR, "drawable-xhdpi/logo.png")
    resample_and_save(src_logo, os.path.join(RES_DIR, "drawable-xxhdpi/logo.png"), (144, 126))
    resample_and_save(src_logo, os.path.join(RES_DIR, "drawable-xxxhdpi/logo.png"), (192, 168))
    resample_and_save(src_logo, os.path.join(RES_DIR, "drawable-nodpi/logo.png"), (96, 84))

    # 5. stat_sys_battery_*.png
    battery_icons = ["10", "20", "40", "60", "80"]
    for b in battery_icons:
        src_bat = os.path.join(RES_DIR, f"drawable/stat_sys_battery_{b}.png")
        resample_and_save(src_bat, os.path.join(RES_DIR, f"drawable-xxhdpi/stat_sys_battery_{b}.png"), (33, 57))
        resample_and_save(src_bat, os.path.join(RES_DIR, f"drawable-xxxhdpi/stat_sys_battery_{b}.png"), (44, 76))
        resample_and_save(src_bat, os.path.join(RES_DIR, f"drawable-nodpi/stat_sys_battery_{b}.png"), (22, 38))

    src_bat_unk = os.path.join(RES_DIR, "drawable/stat_sys_battery_unknown.png")
    resample_and_save(src_bat_unk, os.path.join(RES_DIR, "drawable-xxhdpi/stat_sys_battery_unknown.png"), (30, 64))
    resample_and_save(src_bat_unk, os.path.join(RES_DIR, "drawable-xxxhdpi/stat_sys_battery_unknown.png"), (40, 85))
    resample_and_save(src_bat_unk, os.path.join(RES_DIR, "drawable-nodpi/stat_sys_battery_unknown.png"), (15, 32))

    # 6. export_*.png
    exports = ["error", "failed", "success"]
    for e in exports:
        src_exp = os.path.join(RES_DIR, f"drawable/export_{e}.png")
        resample_and_save(src_exp, os.path.join(RES_DIR, f"drawable-xxhdpi/export_{e}.png"), (42, 42))
        resample_and_save(src_exp, os.path.join(RES_DIR, f"drawable-xxxhdpi/export_{e}.png"), (56, 56))
        resample_and_save(src_exp, os.path.join(RES_DIR, f"drawable-nodpi/export_{e}.png"), (21, 21))

    print("High-density and nodpi resampling completed successfully with zero validation errors.")

if __name__ == "__main__":
    main()
