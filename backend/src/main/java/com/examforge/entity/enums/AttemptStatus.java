package com.examforge.entity.enums;

public enum AttemptStatus {
    IN_PROGRESS,
    /** submitted by the student */
    SUBMITTED,
    /** submitted by the server because the deadline passed */
    AUTO_SUBMITTED;

    public boolean isFinished() {
        return this != IN_PROGRESS;
    }
}
