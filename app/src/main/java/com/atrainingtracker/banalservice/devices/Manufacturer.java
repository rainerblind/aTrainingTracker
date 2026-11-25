/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (c) 2011 - 2026 Rainer Blind <rainer.blind@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/gpl-3.0
 */

package com.atrainingtracker.banalservice.devices;

import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.atrainingtracker.banalservice.BANALService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps numeric ANT+ / FIT manufacturer IDs to human-readable manufacturer names.
 *
 * <p>Uses {@link com.garmin.fit.Manufacturer#getStringFromValue(Integer)} backed by a sparse
 * override and extension map ({@link #MANUFACTURER_OVERRIDES}) for curated display names and
 * newer ANT+ IDs beyond the bundled FIT SDK (REQ-CON-063).
 */
@SuppressWarnings("SpellCheckingInspection")
public class Manufacturer {
    private static final String TAG = "Manufacturer";
    private static final boolean DEBUG = BANALService.getDebug(false);

    public static final String MANUFACTURER_A_TRAINING_TRACKER = "a Training Tracker";
    public static final String MANUFACTURER_GOOGLE = "Google";

    /**
     * Returns the canonical manufacturer display name for a {@code Protocol.SMARTPHONE} sensor
     * (ATT-8111): {@code "a Training Tracker"} for virtual fused/DEM sensors, {@code "Google"}
     * for Google Fused Location, and {@link Build#BRAND} for physical phone hardware sensors.
     */
    @NonNull
    public static String getSmartphoneManufacturer(@Nullable DeviceType deviceType) {
        if (deviceType != null) {
            switch (deviceType) {
                case SPEED_AND_LOCATION_ATT_FUSED:
                case ALTITUDE_AND_SLOPE_ATT_FUSED:
                case ALTITUDE_FROM_POSITION:
                    return MANUFACTURER_A_TRAINING_TRACKER;
                case SPEED_AND_LOCATION_GOOGLE_FUSED:
                    return MANUFACTURER_GOOGLE;
                default:
                    break;
            }
        }
        return Build.BRAND != null ? Build.BRAND : "";
    }

    /**
     * Sparse overrides preserving curated capitalization/punctuation for FIT SDK entries
     * and adding ANT+ manufacturer IDs not yet present in the bundled FIT SDK (e.g. 151–153,
     * 330–350, and curated 5759 ActiGraph).
     */
    private static final Map<Integer, String> MANUFACTURER_OVERRIDES;

    static {
        Map<Integer, String> overrides = new HashMap<>();
        // Curated overrides for FIT SDK tokens (1..329, 5759)
        overrides.put(2, "Garmin"); // GARMIN_FR405_ANTFS
        overrides.put(5, "IDT");
        overrides.put(6, "SRM");
        overrides.put(8, "iBike");
        overrides.put(9, "Saris / CycleOps");
        overrides.put(10, "Spark");
        overrides.put(13, "Dynastream"); // DYNASTREAM_OEM
        overrides.put(17, "MetriGear");
        overrides.put(21, "A & D Medical");
        overrides.put(22, "HMM Diagnostics");
        overrides.put(24, "Thita");
        overrides.put(25, "G.PULSE");
        overrides.put(28, "Peaksware (TrainingPeaks)");
        overrides.put(30, "LeMond Fitness");
        overrides.put(38, "o-synce");
        overrides.put(40, "Concept 2");
        overrides.put(47, "bf1systems");
        overrides.put(50, "MetaLogics");
        overrides.put(51, "4iiiis");
        overrides.put(53, "Seiko Epson"); // SEIKO_EPSON_OEM
        overrides.put(56, "StarTrac");
        overrides.put(58, "ALATECH");
        overrides.put(59, "Mio Technology (MiTAC Global Corporation)");
        overrides.put(60, "ROTOR");
        overrides.put(62, "IDbike");
        overrides.put(64, "WTEK");
        overrides.put(65, "Physi-Cal Enterprises Inc. (MIO Global)");
        overrides.put(67, "BKOOL");
        overrides.put(68, "CatEye");
        overrides.put(70, "Sigma Sport");
        overrides.put(71, "TomTom");
        overrides.put(72, "PeriPedal");
        overrides.put(76, "Moxy Monitor");
        overrides.put(77, "CicloSport");
        overrides.put(78, "POWERbahn");
        overrides.put(79, "Acorn Projects");
        overrides.put(80, "LifeBEAM");
        overrides.put(82, "WELLGO");
        overrides.put(84, "MAGURA");
        overrides.put(87, "Nielsen-Kellerman");
        overrides.put(88, "DK City");
        overrides.put(92, "1partCarbon");
        overrides.put(93, "Inside Ride Technology");
        overrides.put(94, "Sound of Motion");
        overrides.put(96, "Indoorcycling Group");
        overrides.put(97, "MiPulse");
        overrides.put(100, "Campagnolo");
        overrides.put(103, "Limits Technology Ltd.");
        overrides.put(104, "TopAction Technology Inc.");
        overrides.put(116, "ThinkRider");
        overrides.put(118, "WaterRower");
        overrides.put(119, "OrangeTheory");
        overrides.put(125, "NCI Technology");
        overrides.put(128, "ifit com");
        overrides.put(141, "TQ Systems");
        overrides.put(145, "Porsche EP");
        overrides.put(266, "Amer Sports"); // PRECOR in FIT SDK
        overrides.put(269, "Mio Technology"); // NAVMAN in FIT SDK
        overrides.put(270, "Cobi GmbH");
        overrides.put(277, "Falco eMotors Inc.");
        overrides.put(283, "FSA"); // FULLSPEEDAHEAD
        overrides.put(284, "VirtualTraining");
        overrides.put(285, "FeedbackSports");
        overrides.put(288, "MagneticDays");
        overrides.put(290, "Kinetic by Kurt");
        overrides.put(320, "Lishun Electric & Communication"); // LSEC
        overrides.put(323, "Spinning MDA");
        overrides.put(5759, "ActiGraph"); // ACTIGRAPHCORP

        // Extensions beyond bundled FIT SDK (21.141.0)
        overrides.put(151, "Abawo");
        overrides.put(152, "Bafang");
        overrides.put(153, "Luhong Technology");
        overrides.put(330, "EO Swimbetter");
        overrides.put(331, "Mywhoosh");
        overrides.put(332, "Ravemen");
        overrides.put(333, "Tektro Racing Products");
        overrides.put(334, "Darad Innovation Corporation");
        overrides.put(335, "Cycloptim");
        // 336 omitted in FIT Profile
        overrides.put(337, "Runna");
        // 338 omitted in FIT Profile
        overrides.put(339, "Zepp");
        overrides.put(340, "Peloton");
        overrides.put(341, "Carv");
        overrides.put(342, "Tissot");
        // 343, 344 omitted in FIT Profile
        overrides.put(345, "Real Velo");
        overrides.put(346, "Wetech");
        overrides.put(347, "Jespr");
        overrides.put(348, "Huawei");
        overrides.put(349, "Gotoes");
        overrides.put(350, "Cadence App");

        MANUFACTURER_OVERRIDES = Collections.unmodifiableMap(overrides);
    }

    /**
     * Maps an ANT+ / FIT manufacturer ID to a display string, or {@code null} when unknown.
     */
    @Nullable
    public static String getName(int manID) {
        if (DEBUG) Log.d(TAG, "getName: " + manID);

        if (manID <= 0) {
            return null;
        }

        String override = MANUFACTURER_OVERRIDES.get(manID);
        if (override != null) {
            return override;
        }

        String fitToken = com.garmin.fit.Manufacturer.getStringFromValue(manID);
        return formatFitToken(fitToken);
    }

    /**
     * Formats a raw FIT SDK constant token (e.g. {@code "WAHOO_FITNESS"}) into a clean
     * title-cased display name ({@code "Wahoo Fitness"}), or {@code null} if blank.
     */
    @Nullable
    @VisibleForTesting
    static String formatFitToken(@Nullable String rawToken) {
        if (rawToken == null) {
            return null;
        }
        String trimmed = rawToken.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String[] parts = trimmed.split("_+");
        StringBuilder sb = new StringBuilder(trimmed.length());
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                sb.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
