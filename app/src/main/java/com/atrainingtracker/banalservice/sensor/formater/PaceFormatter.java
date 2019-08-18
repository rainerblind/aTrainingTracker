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

package com.atrainingtracker.banalservice.sensor.formater;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.trainingtracker.TrainingApplication;


public class PaceFormatter implements MyFormatter<Number> {

    @Override
    public String format(Number paceN) {
        if (paceN == null) {
            return "--";
        }

        double pace = paceN.doubleValue();

        if (pace > BANALService.MAX_PACE) {
            return "~~";
        }

        switch (TrainingApplication.getUnit()) {
            case METRIC:
                pace = pace * 1000 / 60;
                break;

            case IMPERIAL:
                pace = pace * BANALService.METER_PER_MILE / 60;
                break;
        }

        int min = (int) Math.floor(pace);
        int sec = (int) Math.floor((pace - min) * 60);
        return min + ":" + (sec <= 9 ? "0" + sec : sec);
    }

}
