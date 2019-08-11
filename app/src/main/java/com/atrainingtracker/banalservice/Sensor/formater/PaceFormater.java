package com.atrainingtracker.banalservice.Sensor.formater;

import com.atrainingtracker.banalservice.BANALService;
import com.atrainingtracker.trainingtracker.TrainingApplication;


public class PaceFormater implements MyFormater<Number> {

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
