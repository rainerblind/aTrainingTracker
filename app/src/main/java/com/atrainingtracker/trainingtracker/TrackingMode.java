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

import com.atrainingtracker.R;
import com.atrainingtracker.banalservice.BSportType;

/**
 * Created by rainer on 03.03.16.
 */

public enum TrackingMode {
    WAITING_FOR_BANAL_SERVICE(R.string.waiting_for, R.string.banal_service, R.string.banal_service, R.string.banal_service),
    SEARCHING(R.string.searching_for, R.string.some_remote_device, R.string.some_remote_device, R.string.some_remote_device),
    READY(R.string.ready_to, R.string.ready_run, R.string.ready_bike, R.string.ready_other),
    TRACKING(R.string.tracking, R.string.tracking_run, R.string.tracking_bike, R.string.tracking_other),
    PAUSED(R.string.paused, R.string.paused_run, R.string.paused_bike, R.string.paused_other);


    private final int titleId;
    private final int runId;
    private final int bikeId;
    private final int otherId;

    TrackingMode(int titleId, int runId, int bikeId, int otherId) {
        this.titleId = titleId;
        this.runId = runId;
        this.bikeId = bikeId;
        this.otherId = otherId;
    }

    public int getTitleId() {
        return titleId;
    }

    public int getSportId(BSportType sportType) {
        return switch (sportType) {
            case RUN -> runId;
            case BIKE -> bikeId;
            default -> otherId;
        };
    }

}
