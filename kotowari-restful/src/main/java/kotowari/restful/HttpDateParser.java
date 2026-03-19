package kotowari.restful;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.Optional;

/**
 * Parses HTTP-date values as defined in RFC 7231 section 7.1.1.1.
 *
 * <p>Three formats are accepted (tried in order):
 * <ol>
 *   <li><b>IMF-fixdate</b> — {@code Sun, 06 Nov 1994 08:49:37 GMT}</li>
 *   <li><b>RFC 850 (obsolete)</b> — {@code Sunday, 06-Nov-94 08:49:37 GMT}</li>
 *   <li><b>asctime</b> — {@code Sun Nov  6 08:49:37 1994}</li>
 * </ol>
 *
 * <p>If the value does not match any format, {@link #parse(String)} returns
 * {@link Optional#empty()}, allowing the caller to skip the condition as
 * required by RFC 9110 section 13.1.3 and section 13.1.4.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc7231#section-7.1.1.1">RFC 7231 §7.1.1.1</a>
 */
final class HttpDateParser {

    /** IMF-fixdate: {@code Sun, 06 Nov 1994 08:49:37 GMT} */
    private static final DateTimeFormatter IMF_FIXDATE =
            DateTimeFormatter.RFC_1123_DATE_TIME;

    /**
     * RFC 850 (obsolete): {@code Sunday, 06-Nov-94 08:49:37 GMT}.
     *
     * <p>Two-digit years are interpreted with a 50-year window: years 00–49 map
     * to 2000–2049, years 50–99 map to 1950–1999.
     */
    private static final DateTimeFormatter RFC_850 = new DateTimeFormatterBuilder()
            .appendText(ChronoField.DAY_OF_WEEK, TextStyle.FULL)
            .appendLiteral(", ")
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .appendLiteral('-')
            .appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT)
            .appendLiteral('-')
            .appendValueReduced(ChronoField.YEAR, 2, 2, 1950)
            .appendLiteral(' ')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .appendLiteral(' ')
            .appendZoneId()
            .toFormatter(Locale.US);

    /**
     * asctime: {@code Sun Nov  6 08:49:37 1994}.
     *
     * <p>The day-of-month is space-padded (single-digit days are preceded by a space).
     */
    private static final DateTimeFormatter ASCTIME = new DateTimeFormatterBuilder()
            .appendText(ChronoField.DAY_OF_WEEK, TextStyle.SHORT)
            .appendLiteral(' ')
            .appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT)
            .appendLiteral(' ')
            .padNext(2)
            .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
            .appendLiteral(' ')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .appendLiteral(' ')
            .appendValue(ChronoField.YEAR, 4)
            .toFormatter(Locale.US)
            .withZone(ZoneOffset.UTC);

    private HttpDateParser() {}

    /**
     * Parses an HTTP-date string into an {@link Instant}.
     *
     * <p>Tries IMF-fixdate, RFC 850, and asctime formats in order. Returns
     * {@link Optional#empty()} if the value is {@code null}, blank, or does
     * not match any recognized format.
     *
     * @param httpDate the HTTP-date header value
     * @return the parsed instant, or empty if the value is not a valid HTTP-date
     */
    static Optional<Instant> parse(String httpDate) {
        if (httpDate == null || httpDate.isBlank()) {
            return Optional.empty();
        }
        for (DateTimeFormatter fmt : new DateTimeFormatter[]{IMF_FIXDATE, RFC_850, ASCTIME}) {
            try {
                return Optional.of(Instant.from(fmt.parse(httpDate)));
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return Optional.empty();
    }
}
