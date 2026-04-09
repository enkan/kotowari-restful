package kotowari.restful.data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;
import java.util.Optional;

/**
 * Parsed {@code Prefer} request header directives per RFC 7240.
 *
 * <p>Only the directives that the Kotowari-Restful decision graph acts upon are
 * currently modeled:
 * <ul>
 *   <li>{@code return=representation} / {@code return=minimal}
 *       (§4.2) — controls whether successful mutating responses include a body</li>
 *   <li>{@code handling=lenient} / {@code handling=strict}
 *       (§4.4) — asks the server to ignore non-fatal errors in the request</li>
 *   <li>{@code wait=N} (§4.3) — maximum number of seconds the client is willing
 *       to wait for the response</li>
 * </ul>
 *
 * <p>Unknown or malformed tokens are ignored per RFC 7240 §2. The parser is
 * tolerant: whitespace around {@code ,} and {@code =} is stripped, and tokens
 * are matched case-insensitively.
 *
 * <p>Resources can inspect the directives via
 * {@link RestContext#get(ContextKey)} with {@link RestContext#PREFER_DIRECTIVES}.
 * The engine always echoes applied preferences back to the client via the
 * {@code Preference-Applied} response header (§4.5).
 *
 * @param returnRepresentation  {@code true} if {@code return=representation}
 * @param returnMinimal         {@code true} if {@code return=minimal}
 * @param handlingLenient       {@code true} if {@code handling=lenient}
 * @param handlingStrict        {@code true} if {@code handling=strict}
 * @param waitSeconds           the {@code wait} directive in seconds, or {@code null}
 */
public record PreferDirectives(
        boolean returnRepresentation,
        boolean returnMinimal,
        boolean handlingLenient,
        boolean handlingStrict,
        Long waitSeconds
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * An empty {@link PreferDirectives} instance used when no {@code Prefer}
     * header is present on the request.
     */
    public static final PreferDirectives NONE =
            new PreferDirectives(false, false, false, false, null);

    /**
     * Parses the raw {@code Prefer} header value.
     *
     * <p>Returns {@link #NONE} if the value is {@code null}, blank, or contains
     * no recognized directives. Unknown directives are silently ignored.
     *
     * @param headerValue the raw {@code Prefer} header value, or {@code null}
     * @return the parsed directives
     */
    public static PreferDirectives parse(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return NONE;
        }
        boolean returnRepresentation = false;
        boolean returnMinimal = false;
        boolean handlingLenient = false;
        boolean handlingStrict = false;
        Long waitSeconds = null;

        for (String raw : headerValue.split(",")) {
            String token = raw.strip().toLowerCase(Locale.ROOT);
            if (token.isEmpty()) continue;
            // Strip any parameters (;foo=bar) — we don't currently use them.
            int semi = token.indexOf(';');
            if (semi >= 0) {
                token = token.substring(0, semi).strip();
            }
            int eq = token.indexOf('=');
            String name = eq >= 0 ? token.substring(0, eq).strip() : token;
            String value = eq >= 0 ? token.substring(eq + 1).strip() : "";
            // Allow RFC 7240 quoted-string values.
            if (value.length() >= 2 && value.charAt(0) == '"'
                    && value.charAt(value.length() - 1) == '"') {
                value = value.substring(1, value.length() - 1);
            }

            switch (name) {
                case "return" -> {
                    if ("representation".equals(value)) returnRepresentation = true;
                    else if ("minimal".equals(value)) returnMinimal = true;
                }
                case "handling" -> {
                    if ("lenient".equals(value)) handlingLenient = true;
                    else if ("strict".equals(value)) handlingStrict = true;
                }
                case "wait" -> {
                    try {
                        long seconds = Long.parseLong(value);
                        if (seconds >= 0) waitSeconds = seconds;
                    } catch (NumberFormatException ignored) {
                        // malformed; silently ignore per RFC 7240 §2
                    }
                }
                default -> {
                    // Unknown directive; ignore per §2.
                }
            }
        }

        if (!returnRepresentation && !returnMinimal
                && !handlingLenient && !handlingStrict
                && waitSeconds == null) {
            return NONE;
        }
        return new PreferDirectives(
                returnRepresentation, returnMinimal,
                handlingLenient, handlingStrict, waitSeconds);
    }

    /**
     * Returns the {@code Preference-Applied} header value that reflects the
     * directives actually honoured by the server, or {@link Optional#empty()}
     * if none of the modeled directives are set. The header value is formatted
     * per RFC 7240 §4.5.
     *
     * @return the {@code Preference-Applied} value, or empty
     */
    public Optional<String> toPreferenceApplied() {
        StringBuilder sb = new StringBuilder();
        if (returnRepresentation) append(sb, "return=representation");
        if (returnMinimal)        append(sb, "return=minimal");
        if (handlingLenient)      append(sb, "handling=lenient");
        if (handlingStrict)       append(sb, "handling=strict");
        if (waitSeconds != null)  append(sb, "wait=" + waitSeconds);
        return sb.length() == 0 ? Optional.empty() : Optional.of(sb.toString());
    }

    private static void append(StringBuilder sb, String token) {
        if (sb.length() > 0) sb.append(", ");
        sb.append(token);
    }
}
