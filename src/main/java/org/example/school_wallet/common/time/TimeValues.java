package org.example.school_wallet.common.time;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Converts instants to the compact ISO-8601 text stored in school records.
 *
 * <p>School date fields use {@code VARCHAR(20)} columns. {@link Instant#toString()}
 * can contain nanoseconds and exceed that width, so persisted values are
 * truncated to whole seconds. The result is still sortable as text because the
 * ISO-8601 UTC representation is lexicographically chronological.
 */
public final class TimeValues {

    private TimeValues() {
    }

    public static String text(Instant instant) {
        if (instant == null) {
            return "";
        }
        return instant.truncatedTo(ChronoUnit.SECONDS).toString();
    }

    public static String now() {
        return text(Instant.now());
    }
}
