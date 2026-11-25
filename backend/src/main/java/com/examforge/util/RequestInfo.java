package com.examforge.util;

import jakarta.servlet.http.HttpServletRequest;

/** Client metadata stored with sessions and audit entries. */
public record RequestInfo(String ip, String userAgent, boolean mobileClient) {

    public static RequestInfo from(HttpServletRequest req) {
        String ua = req.getHeader("User-Agent");
        if (ua != null && ua.length() > 255) ua = ua.substring(0, 255);
        boolean mobile = "mobile".equalsIgnoreCase(req.getHeader("X-Client-Type"));
        // getRemoteAddr honours X-Forwarded-For only when server.forward-headers-strategy is configured.
        return new RequestInfo(req.getRemoteAddr(), ua, mobile);
    }
}
