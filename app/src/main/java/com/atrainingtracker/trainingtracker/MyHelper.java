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

package com.atrainingtracker.trainingtracker;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import androidx.annotation.NonNull;

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.sensor.SensorType;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.jetbrains.annotations.Contract;

import java.io.IOException;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.Locale;

public class MyHelper {
    public static final double METER_PER_MILE = 1609.344;
    private static final String TAG = "MyHelper";
    private static final boolean DEBUG = false;

    // taken from http://stackoverflow.com/questions/1995998/android-get-altitude-by-longitude-and-latitude
    // unfortunately, this works only for US coordinates :-(
    public static double getAltitudeFromUSGS(double longitude, double latitude) {
        double result = Double.NaN;
        HttpClient httpClient = new DefaultHttpClient();
        HttpContext localContext = new BasicHttpContext();
        String url = "http://gisdata.usgs.gov/"
                + "xmlwebservices2/elevation_service.asmx/"
                + "getElevation?X_Value=" + longitude
                + "&Y_Value=" + latitude
                + "&Elevation_Units=METERS&Source_Layer=-1&Elevation_Only=true";
        HttpGet httpGet = new HttpGet(url);
        try {
            HttpResponse response = httpClient.execute(httpGet, localContext);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                InputStream inputStream = entity.getContent();
                int r = -1;
                StringBuilder respStr = new StringBuilder();
                while ((r = inputStream.read()) != -1)
                    respStr.append((char) r);
                String tagOpen = "<double>";
                String tagClose = "</double>";
                if (respStr.indexOf(tagOpen) != -1) {
                    int start = respStr.indexOf(tagOpen) + tagOpen.length();
                    int end = respStr.indexOf(tagClose);
                    String value = respStr.substring(start, end);
                    result = Double.parseDouble(value);
                }
                inputStream.close();
            }
        } catch (ClientProtocolException e) {
        } catch (IOException e) {
        }
        return result;
    }

    // taken from http://stackoverflow.com/questions/1995998/android-get-altitude-by-longitude-and-latitude
    // unfortunately, google API does not allow this usage without displaying it on a google map
    public static double getElevationFromGoogleMaps(double longitude, double latitude) {
        double result = Double.NaN;
        HttpClient httpClient = new DefaultHttpClient();
        HttpContext localContext = new BasicHttpContext();
        String url = "http://maps.googleapis.com/maps/api/elevation/"
                + "xml?locations=" + latitude
                + "," + longitude
                + "&sensor=true";
        HttpGet httpGet = new HttpGet(url);
        try {
            HttpResponse response = httpClient.execute(httpGet, localContext);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                InputStream inputStream = entity.getContent();
                int r = -1;
                StringBuilder respStr = new StringBuilder();
                while ((r = inputStream.read()) != -1)
                    respStr.append((char) r);
                String tagOpen = "<elevation>";
                String tagClose = "</elevation>";
                if (respStr.indexOf(tagOpen) != -1) {
                    int start = respStr.indexOf(tagOpen) + tagOpen.length();
                    int end = respStr.indexOf(tagClose);
                    String value = respStr.substring(start, end);
                    result = Double.parseDouble(value);
                }
                inputStream.close();
            }
        } catch (ClientProtocolException e) {
        } catch (IOException e) {
        }

        return result;
    }


    public static boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) TrainingApplication.getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    public static double string2Double(@NonNull String doubleString) {
        try {
            return Double.parseDouble(doubleString);
        } catch (NumberFormatException e) {
            if (DEBUG) Log.i(TAG, "Failed to parse double, retry with NumberFormat");
            // "sample" was not an integer value
            // You should probably start settings again
        }


        NumberFormat format = NumberFormat.getInstance();
        Number number = 0.0;
        try {
            number = format.parse(doubleString);
        } catch (Exception e) {
            Log.i(TAG, "failed to parse '" + doubleString + "' with the locale format, try to parse with the US format");
            format = NumberFormat.getInstance(Locale.US);
            try {
                number = format.parse(doubleString);
            } catch (Exception ee) {
                Log.i(TAG, "WTF: could not convert '" + doubleString + "' to a number, returning 0");
            }

        }
        return number.doubleValue();
    }

    public static double mps2userUnit(double speed) {
        return mps2userUnit(speed, TrainingApplication.getUnit());
    }

    public static double mps2userUnit(double speed, @NonNull MyUnits myUnits) {
        return switch (myUnits) {
            case METRIC -> speed * 3.6;
            case IMPERIAL -> speed * 2.23693629;
        };
    }

    public static double UserUnit2mps(double speed) {
        return switch (TrainingApplication.getUnit()) {
            case METRIC -> speed / 3.6;
            case IMPERIAL -> speed / 2.23693629;
        };
    }

    public static int getSpeedUnitNameId() {
        return switch (TrainingApplication.getUnit()) {
            case METRIC -> R.string.units_speed_metric;
            case IMPERIAL -> R.string.units_speed_imperial;
        };
    }

    public static int getShortSpeedUnitNameId() {
        return switch (TrainingApplication.getUnit()) {
            case METRIC -> R.string.units_speed_short_metric;
            case IMPERIAL -> R.string.units_speed_short_imperial;
        };
    }

    public static int getPaceUnitNameId() {
        return switch (TrainingApplication.getUnit()) {
            case METRIC -> R.string.units_pace_metric;
            case IMPERIAL -> R.string.units_pace_imperial;
        };
    }

    public static int getShortPaceUnitNameId() {
        return switch (TrainingApplication.getUnit()) {
            case METRIC -> R.string.units_pace_short_metric;
            case IMPERIAL -> R.string.units_pace_short_imperial;
        };
    }

    public static int getDistanceUnitNameId() {
        return switch (TrainingApplication.getUnit()) {
            case METRIC -> R.string.units_distance_metric;
            case IMPERIAL -> R.string.units_distance_imperial;
        };
    }


    public static int getUnitsId(@NonNull SensorType sensorType) {
        return switch (sensorType) {
            case SPEED_mps -> getSpeedUnitNameId();
            case PACE_spm -> getPaceUnitNameId();
            case DISTANCE_m, DISTANCE_m_LAP, LINE_DISTANCE_m -> getDistanceUnitNameId();
            default -> sensorType.getUnitId();
        };
    }

    public static int getShortUnitsId(@NonNull SensorType sensorType) {
        return switch (sensorType) {
            case SPEED_mps -> getShortSpeedUnitNameId();
            case PACE_spm -> getShortPaceUnitNameId();
            case DISTANCE_m, DISTANCE_m_LAP, LINE_DISTANCE_m ->
                    getDistanceUnitNameId();  // TODO: also short version?

            default -> sensorType.getUnitId();  // TODO: also short version?
        };
    }

    @NonNull
    @Contract(pure = true)
    public static String formatRank(int i) {
        int j = i % 10,
                k = i % 100;
        if (j == 1 && k != 11) {
            return i + "st";
        }
        if (j == 2 && k != 12) {
            return i + "nd";
        }
        if (j == 3 && k != 13) {
            return i + "rd";
        }
        return i + "th";
    }
}
