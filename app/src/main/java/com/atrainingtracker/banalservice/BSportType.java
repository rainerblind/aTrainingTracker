package com.atrainingtracker.banalservice;

// TODO: does it really make sense to have BSportType and TTSportType???
public enum BSportType {
    UNKNOWN,
    RUN,
    BIKE,
    CONFLICT;

    public BSportType or(BSportType other) {
        return BSportType.values()[(ordinal() | other.ordinal())];
    }

}
