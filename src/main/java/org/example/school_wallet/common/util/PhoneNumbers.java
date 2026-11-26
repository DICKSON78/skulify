package org.example.school_wallet.common.util;

import java.util.regex.Pattern;

/**
 * Normalises Tanzanian phone numbers to E.164 (+255...). Portal login by phone
 * needs a single canonical form regardless of how the number was typed.
 */
public final class PhoneNumbers {

    private static final Pattern LOCAL = Pattern.compile("^0\\d{9}$");
    private static final Pattern INTERNATIONAL = Pattern.compile("^\\+?255\\d{9}$");
    private static final Pattern GENERIC = Pattern.compile("^\\+?\\d{9,15}$");

    private PhoneNumbers() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String compact = raw.replaceAll("[\\s\\-()]", "");
        if (LOCAL.matcher(compact).matches()) {
            return "+255" + compact.substring(1);
        }
        if (INTERNATIONAL.matcher(compact).matches()) {
            return compact.startsWith("+") ? compact : "+" + compact;
        }
        if (GENERIC.matcher(compact).matches()) {
            return compact;
        }
        return compact;
    }

    public static boolean looksLikePhone(String value) {
        if (value == null) {
            return false;
        }
        String compact = value.replaceAll("[\\s\\-()]", "");
        return LOCAL.matcher(compact).matches()
                || INTERNATIONAL.matcher(compact).matches()
                || GENERIC.matcher(compact).matches();
    }
}
