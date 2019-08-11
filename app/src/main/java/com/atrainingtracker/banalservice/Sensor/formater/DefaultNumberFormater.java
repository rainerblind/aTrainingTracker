package com.atrainingtracker.banalservice.Sensor.formater;

// import java.text.NumberFormat;

import java.text.DecimalFormat;

public class DefaultNumberFormater implements MyFormater<Number> {
    @Override
    public String format(Number value) {
        if (value == null) {
            return "--";
        } else {
            // return String.format("%.2f", value);
            // return (NumberFormat.getInstance().).format(value);
            DecimalFormat format = new DecimalFormat();
            format.setMaximumFractionDigits(1);
            return format.format(value);
        }
    }

}
