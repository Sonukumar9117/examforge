package com.examforge.util;

/** Shared validation rules (one place, so requests and the admin bootstrap agree). */
public final class ValidationPatterns {
    private ValidationPatterns() {}

    /** Letters + digits, 8–72 chars (BCrypt only uses the first 72 bytes). */
    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String PASSWORD_MESSAGE = "must be 8-72 characters and contain a letter and a number";
    public static final String USERNAME_REGEX = "^[A-Za-z0-9_]{3,30}$";
    public static final String USERNAME_MESSAGE = "must be 3-30 characters: letters, numbers or underscore";
}
