/*
 * aTrainingTracker (ANT+ BTLE)
 * Copyright (C) 2011 - 2019 Rainer Blind <rainer.blind@gmail.com>
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

import android.util.Log;

import com.atrainingtracker.banalservice.BANALService;

@SuppressWarnings("SpellCheckingInspection")
public class Manufacturer {
    /**
     * The lookup table
     **/
    static final String[] manufacturerString = {
            "Unknown",           // 0
            "Garmin",            // 1
            "Garmin", //fr405_antfs,2
            "Zephyr",            // 3
            "Dayton",            // 4
            "IDT",               // 5
            "SRM",               // 6
            "Quarq",             // 7
            "iBike",             // 8
            "Saris / CycleOps",  // 9
            "Spark",             // 10
            "Tanita",            // 11
            "Echowell",          // 12
            "Dynastream",        // 13
            "Nautilus",          // 14
            "Dynastream",        // 15
            "Timex",             // 16
            "MetriGear",         // 17
            "Xelic",             // 18
            "Beurer",            // 19
            "Cardiosport",       // 20
            "A & D Medical",     // 21
            "HMM Diagnostics",   // 22
            "Suunto",            // 23
            "Thita",             // 24
            "G.PULSE",           // 25
            "Clean Mobile",      // 26
            "Pedal Brain",       // 27
            "Peaksware (TrainingPeaks)", // 28
            "Saxonar",                   // 29
            "LeMond Fitness",            // 30
            "Dexcom",                    // 31
            "Wahoo Fitness",             // 32
            "Octane Fitness",            // 33
            "Archinoetics",              // 34
            "The Hurt Box",              // 35
            "Citizen Systems",           // 36
            "Magellan",                  // 37
            "o-synce",                   // 38
            "Holux",                     // 39
            "Concept 2",                 // 40
            "Unknown",                   // 41 there seems to be no entry
            "One Giant Leap",            // 42
            "Ace Sensor",                // 43
            "Brim Brothers",             // 44
            "Xplova",                    // 45
            "Perception Digital",        // 46
            "bf1systems",                // 47
            "Pioneer",                   // 48
            "Spantec",                   // 49
            "MetaLogics",                // 50
            "4iiiis",                    // 51
            "Seiko Epson",               // 52
            "Seiko Epson",               // 53
            "Ifor Powell",               // 54
            "Maxwell Guider",            // 55
            "StarTrac",                  // 56
            "Breakaway",                 // 57
            "ALATECH",                   // 58
            "Mio Technology (MiTAC Global Corporation)",   // 59
            "ROTOR",                     // 60
            "Geonaute",                  // 61
            "IDbike",                    // 62
            "Specialized",               // 63
            "WTEK",                      // 64
            "Physi-Cal Enterprises Inc. (MIO Global)",     // 65
            "North Pole Engineering",    // 66
            "BKOOL",                     // 67
            "CatEye",                    // 68
            "Stages Cycling",            // 69
            "Sigma Sport",               // 70
            "TomTom",                    // 71
            "PeriPedal",                 // 72
            "Wattbike",                  // 73
            "Unknown",                   // 74 there seems to be no entry
            "Unknown",                   // 75 there seems to be no entry
            "Moxy Monitor",              // 76
            "CicloSport",                // 77
            "POWERbahn",                 // 78
            "Acorn Projects",            // 79
            "LifeBEAM",                  // 80
            "Bontrager",                 // 81
            "WELLGO",                    // 82
            "Scosche",                   // 83
            "MAGURA",                    // 84
            "Woodway",                   // 85
            "Elite",                     // 86
            "Nielsen-Kellerman",         // 87
            "DK City",                   // 88
            "Tacx",                      // 89
            "Direction Technology",      // 90
            "Magtonic",                  // 91
            "1partCarbon",               // 92
            "Inside Ride Technology",    // 93
            "Sound of Motion",           // 94
            "Stryd",                     //	95
            "Indoorcycling Group",         // 96
            "MiPulse",                   //	97
            "Bsx Athletics",             //	98
            "Look",                      //	99
            "Campagnolo",                // 100
            "Body Bike Smart",           // 101
            "Praxisworks",               // 102
            "Limits Technology Ltd.",    // 103
            "TopAction Technology Inc.", // 104
            "Cosinuss",                  // 105
            "Fitcare",                   // 106
            "Magene",                    // 107
            "Giant Manufacturing Co",    // 108
            "Tigrasport",                // 109
            "Salutron",                  // 110
            "Technogym",                 // 111
            "Bryton Sensors",            // 112
            "Latitude Limited",          // 113
            "Soaring Technology",        // 114
            "Igpsport",                  // 115
            "ThinkRider",                // 116
            "Gopher Sport",              // 117
            "WaterRower",                // 118
            "OrangeTheory",              // 119
            "Unknown",                   // 120 there seems to be no entry
            "Unknown",                   // 121 there seems to be no entry
            "Unknown",                   // 122 there seems to be no entry
            "Unknown",                   // 123 there seems to be no entry
            "Unknown",                   // 124 there seems to be no entry
            "Unknown",                   // 125 there seems to be no entry
            "Unknown",                   // 126 there seems to be no entry
            "Unknown",                   // 127 there seems to be no entry
            "Unknown",                   // 128 there seems to be no entry
            "Unknown",                   // 129 there seems to be no entry
            "Unknown",                   // 130 there seems to be no entry
            "Unknown",                   // 131 there seems to be no entry
            "Unknown",                   // 132 there seems to be no entry
            "Unknown",                   // 133 there seems to be no entry
            "Unknown",                   // 134 there seems to be no entry
            "Unknown",                   // 135 there seems to be no entry
            "Unknown",                   // 136 there seems to be no entry
            "Unknown",                   // 137 there seems to be no entry
            "Unknown",                   // 138 there seems to be no entry
            "Unknown",                   // 139 there seems to be no entry
            "Unknown",                   // 140 there seems to be no entry
            "Unknown",                   // 141 there seems to be no entry
            "Unknown",                   // 142 there seems to be no entry
            "Unknown",                   // 143 there seems to be no entry
            "Unknown",                   // 144 there seems to be no entry
            "Unknown",                   // 145 there seems to be no entry
            "Unknown",                   // 146 there seems to be no entry
            "Unknown",                   // 147 there seems to be no entry
            "Unknown",                   // 148 there seems to be no entry
            "Unknown",                   // 149 there seems to be no entry
            "Unknown",                   // 150 there seems to be no entry
            "Unknown",                   // 151 there seems to be no entry
            "Unknown",                   // 152 there seems to be no entry
            "Unknown",                   // 153 there seems to be no entry
            "Unknown",                   // 154 there seems to be no entry
            "Unknown",                   // 155 there seems to be no entry
            "Unknown",                   // 156 there seems to be no entry
            "Unknown",                   // 157 there seems to be no entry
            "Unknown",                   // 158 there seems to be no entry
            "Unknown",                   // 159 there seems to be no entry
            "Unknown",                   // 160 there seems to be no entry
            "Unknown",                   // 161 there seems to be no entry
            "Unknown",                   // 162 there seems to be no entry
            "Unknown",                   // 163 there seems to be no entry
            "Unknown",                   // 164 there seems to be no entry
            "Unknown",                   // 165 there seems to be no entry
            "Unknown",                   // 166 there seems to be no entry
            "Unknown",                   // 167 there seems to be no entry
            "Unknown",                   // 168 there seems to be no entry
            "Unknown",                   // 169 there seems to be no entry
            "Unknown",                   // 170 there seems to be no entry
            "Unknown",                   // 171 there seems to be no entry
            "Unknown",                   // 172 there seems to be no entry
            "Unknown",                   // 173 there seems to be no entry
            "Unknown",                   // 174 there seems to be no entry
            "Unknown",                   // 175 there seems to be no entry
            "Unknown",                   // 176 there seems to be no entry
            "Unknown",                   // 177 there seems to be no entry
            "Unknown",                   // 178 there seems to be no entry
            "Unknown",                   // 179 there seems to be no entry
            "Unknown",                   // 180 there seems to be no entry
            "Unknown",                   // 181 there seems to be no entry
            "Unknown",                   // 182 there seems to be no entry
            "Unknown",                   // 183 there seems to be no entry
            "Unknown",                   // 184 there seems to be no entry
            "Unknown",                   // 185 there seems to be no entry
            "Unknown",                   // 186 there seems to be no entry
            "Unknown",                   // 187 there seems to be no entry
            "Unknown",                   // 188 there seems to be no entry
            "Unknown",                   // 189 there seems to be no entry
            "Unknown",                   // 190 there seems to be no entry
            "Unknown",                   // 191 there seems to be no entry
            "Unknown",                   // 192 there seems to be no entry
            "Unknown",                   // 193 there seems to be no entry
            "Unknown",                   // 194 there seems to be no entry
            "Unknown",                   // 195 there seems to be no entry
            "Unknown",                   // 196 there seems to be no entry
            "Unknown",                   // 197 there seems to be no entry
            "Unknown",                   // 198 there seems to be no entry
            "Unknown",                   // 199 there seems to be no entry
            "Unknown",                   // 200 there seems to be no entry
            "Unknown",                   // 201 there seems to be no entry
            "Unknown",                   // 202 there seems to be no entry
            "Unknown",                   // 203 there seems to be no entry
            "Unknown",                   // 204 there seems to be no entry
            "Unknown",                   // 205 there seems to be no entry
            "Unknown",                   // 206 there seems to be no entry
            "Unknown",                   // 207 there seems to be no entry
            "Unknown",                   // 208 there seems to be no entry
            "Unknown",                   // 209 there seems to be no entry
            "Unknown",                   // 210 there seems to be no entry
            "Unknown",                   // 211 there seems to be no entry
            "Unknown",                   // 212 there seems to be no entry
            "Unknown",                   // 213 there seems to be no entry
            "Unknown",                   // 214 there seems to be no entry
            "Unknown",                   // 215 there seems to be no entry
            "Unknown",                   // 216 there seems to be no entry
            "Unknown",                   // 217 there seems to be no entry
            "Unknown",                   // 218 there seems to be no entry
            "Unknown",                   // 219 there seems to be no entry
            "Unknown",                   // 220 there seems to be no entry
            "Unknown",                   // 221 there seems to be no entry
            "Unknown",                   // 222 there seems to be no entry
            "Unknown",                   // 223 there seems to be no entry
            "Unknown",                   // 224 there seems to be no entry
            "Unknown",                   // 225 there seems to be no entry
            "Unknown",                   // 226 there seems to be no entry
            "Unknown",                   // 227 there seems to be no entry
            "Unknown",                   // 228 there seems to be no entry
            "Unknown",                   // 229 there seems to be no entry
            "Unknown",                   // 230 there seems to be no entry
            "Unknown",                   // 231 there seems to be no entry
            "Unknown",                   // 232 there seems to be no entry
            "Unknown",                   // 233 there seems to be no entry
            "Unknown",                   // 234 there seems to be no entry
            "Unknown",                   // 235 there seems to be no entry
            "Unknown",                   // 236 there seems to be no entry
            "Unknown",                   // 237 there seems to be no entry
            "Unknown",                   // 238 there seems to be no entry
            "Unknown",                   // 239 there seems to be no entry
            "Unknown",                   // 240 there seems to be no entry
            "Unknown",                   // 241 there seems to be no entry
            "Unknown",                   // 242 there seems to be no entry
            "Unknown",                   // 243 there seems to be no entry
            "Unknown",                   // 244 there seems to be no entry
            "Unknown",                   // 245 there seems to be no entry
            "Unknown",                   // 246 there seems to be no entry
            "Unknown",                   // 247 there seems to be no entry
            "Unknown",                   // 248 there seems to be no entry
            "Unknown",                   // 249 there seems to be no entry
            "Unknown",                   // 250 there seems to be no entry
            "Unknown",                   // 251 there seems to be no entry
            "Unknown",                   // 252 there seems to be no entry
            "Unknown",                   // 253 there seems to be no entry
            "Unknown",                   // 254 there seems to be no entry
            "Development",               // 255
            "Unknown",                   // 256 there seems to be no entry
            "Healthandlife",             // 257
            "Lezyne",                    // 258
            "Scribe Labs",               // 259
            "Zwift",                     // 260
            "Watteam",                   // 261
            "Recon",                     // 262
            "Favero Electronics",        // 263
            "Dynovelo",                  // 264
            "Strava",                    // 265
            "Amer Sports",               // 266
            "Bryton",                    // 267
            "Sram",                      // 268
            "Mio Technology",            // 269
            "Cobi GmbH",                 // 270
            "Spivi",                     // 271
            "Mio Magellan",              // 272
            "Evesports",                 // 273
            "Sensitivus Gauge",          // 274
            "Podoon",                    // 275
            "Life Time Fitness",         // 276
            "Falco eMotors Inc.",        // 277
            "Minoura",                   // 278
            "Cycliq",                    // 279
            "Luxottica",                 // 280
            "Trainer Road",              // 281
            "The Sufferfest",            // 282
            "FSA",                       // 283
            "VirtualTraining",           // 284
            "FeedbackSports",            // 285
            "Omata",                     // 286
            "Vdo",                       // 287
            "MagneticDays"};             // 288
    private static final String TAG = "Manufacturer";
    private static final boolean DEBUG = BANALService.DEBUG & false;

    /**
     * maps the Manufacturer ID to a string
     */
    public static String getName(int manID) {
        if (DEBUG) Log.d(TAG, "getName: " + manID);

        if (manID >= 0 && manID < manufacturerString.length) {
            return manufacturerString[manID];
        } else {
            return null;
        }
    }
}
