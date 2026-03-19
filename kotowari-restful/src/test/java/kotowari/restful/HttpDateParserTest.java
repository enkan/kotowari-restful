package kotowari.restful;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link HttpDateParser} covering all three HTTP-date formats
 * defined in RFC 7231 section 7.1.1.1.
 */
class HttpDateParserTest {

    /** IMF-fixdate: {@code Sun, 06 Nov 1994 08:49:37 GMT} */
    @Test
    void parseImfFixdate() {
        Optional<Instant> result = HttpDateParser.parse("Sun, 06 Nov 1994 08:49:37 GMT");
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(Instant.parse("1994-11-06T08:49:37Z"));
    }

    /** RFC 850 (obsolete): {@code Sunday, 06-Nov-94 08:49:37 GMT} */
    @Test
    void parseRfc850() {
        Optional<Instant> result = HttpDateParser.parse("Sunday, 06-Nov-94 08:49:37 GMT");
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(Instant.parse("1994-11-06T08:49:37Z"));
    }

    /** asctime: {@code Sun Nov  6 08:49:37 1994} */
    @Test
    void parseAsctime() {
        Optional<Instant> result = HttpDateParser.parse("Sun Nov  6 08:49:37 1994");
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(Instant.parse("1994-11-06T08:49:37Z"));
    }

    /** asctime with two-digit day: {@code Fri Nov 11 08:49:37 1994} */
    @Test
    void parseAsctimeTwoDigitDay() {
        Optional<Instant> result = HttpDateParser.parse("Fri Nov 11 08:49:37 1994");
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(Instant.parse("1994-11-11T08:49:37Z"));
    }

    @Test
    void parseNull() {
        assertThat(HttpDateParser.parse(null)).isEmpty();
    }

    @Test
    void parseEmpty() {
        assertThat(HttpDateParser.parse("")).isEmpty();
    }

    @Test
    void parseBlank() {
        assertThat(HttpDateParser.parse("   ")).isEmpty();
    }

    @Test
    void parseInvalid() {
        assertThat(HttpDateParser.parse("not-a-date")).isEmpty();
    }

    /** Non-GMT timezone must be rejected per RFC 7231 §7.1.1.1. */
    @Test
    void parseNonGmtTimezone() {
        assertThat(HttpDateParser.parse("Sun, 06 Nov 1994 08:49:37 PST")).isEmpty();
    }
}
