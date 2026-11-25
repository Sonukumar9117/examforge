package com.examforge.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/** URL-friendly identifiers: "SSC CGL & CHSL" -> "ssc-cgl-chsl". */
public final class Slugs {
    private Slugs() {}

    public static String slugify(String text) {
        String s = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (s.isEmpty()) s = "item";
        return s.length() > 100 ? s.substring(0, 100).replaceAll("-+$", "") : s;
    }

    /** slugify + append -2, -3 … until {@code taken} says the value is free. */
    public static String unique(String text, Predicate<String> taken) {
        String base = slugify(text);
        String candidate = base;
        for (int i = 2; taken.test(candidate); i++) {
            candidate = base + "-" + i;
        }
        return candidate;
    }
}
