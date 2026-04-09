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
 *       (§4.2) — controls whether successful mutating responses include a body.
 *       Both directives are honored by the engine.</li>
 *   <li>{@code handling=lenient} (§4.4) — honored by the engine: on a PATCH
 *       request, {@link RuntimeException}s thrown by the resource's PATCH
 *       handler are caught and surfaced as a 400 Problem rather than a 500.</li>
 *   <li>{@code handling=strict} (§4.4) — parsed but <b>not honored</b>. Per
 *       RFC 7240 §4.4, {@code handling=strict} asks the server to apply
 *       stricter-than-default validation. Kotowari-Restful is already strict
 *       by default (any non-null resource return shapes the decision), so
 *       there is nothing additional to do. The directive is exposed on this
 *       record for resources that want to react to it, but is deliberately
 *       <b>omitted</b> from {@link #toPreferenceApplied()} to avoid claiming
 *       server-side behavior that is not in fact conditional on it.</li>
 *   <li>{@code wait=N} (§4.3) — maximum number of seconds the client is willing
 *       to wait for the response. Parsed and exposed for resources that want
 *       to react; the engine does not otherwise enforce it.</li>
 * </ul>
 *
 * <p>Unknown or malformed tokens are ignored per RFC 7240 §2. The parser is
 * tolerant: whitespace around {@code ,} and {@code =} is stripped, directive
 * names and unquoted token values are matched case-insensitively, and
 * quoted-string values are unquoted before comparison without forcing their
 * case.
 *
 * <p>Resources can inspect the directives via
 * {@link RestContext#get(ContextKey)} with {@link RestContext#PREFER_DIRECTIVES}.
 * The engine always echoes applied preferences back to the client via the
 * {@code Preference-Applied} response header (§4.5) — only directives that
 * the engine actually honors are echoed.
 *
 * @param returnRepresentation  {@code true} if {@code return=representation}
 * @param returnMinimal         {@code true} if {@code return=minimal}
 * @param handlingLenient       {@code true} if {@code handling=lenient}
 * @param handlingStrict        {@code true} if {@code handling=strict} (parsed but not honored)
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
            String token = raw.strip();
            if (token.isEmpty()) continue;
            // Strip any RFC 7240 §2 parameters (;foo=bar) — we don't
            // currently act on any parameter.
            int semi = token.indexOf(';');
            if (semi >= 0) {
                token = token.substring(0, semi).strip();
            }
            int eq = token.indexOf('=');
            // Directive names are case-insensitive per RFC 7240 §2.
            String name = (eq >= 0 ? token.substring(0, eq) : token)
                    .strip().toLowerCase(Locale.ROOT);
            String rawValue = eq >= 0 ? token.substring(eq + 1).strip() : "";
            // Unwrap quoted-string values per RFC 7240 §2. Keep the unquoted
            // value verbatim — case is preserved so that callers relying on
            // quoted-string semantics (e.g. for {@code wait=N}) see the
            // original characters. Token-form values are compared
            // case-insensitively below.
            boolean quoted = rawValue.length() >= 2
                    && rawValue.charAt(0) == '"'
                    && rawValue.charAt(rawValue.length() - 1) == '"';
            String value = quoted ? rawValue.substring(1, rawValue.length() - 1) : rawValue;
            // For directives whose values are tokens (return, handling) we
            // normalize to lowercase; for wait (numeric) the value is
            // already safe to parse as-is.
            String valueFolded = value.toLowerCase(Locale.ROOT);

            switch (name) {
                case "return" -> {
                    if ("representation".equals(valueFolded)) returnRepresentation = true;
                    else if ("minimal".equals(valueFolded)) returnMinimal = true;
                }
                case "handling" -> {
                    if ("lenient".equals(valueFolded)) handlingLenient = true;
                    else if ("strict".equals(valueFolded)) handlingStrict = true;
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
     * directives the engine actually honors, or {@link Optional#empty()} if
     * none of the honored directives are set. The header value is formatted
     * per RFC 7240 §4.5.
     *
     * <p>Note that {@code handling=strict} is deliberately omitted: the
     * engine is strict by default and does nothing conditional on this
     * directive, so echoing it would imply server-side behavior that is not
     * actually in effect. {@code wait=N} is also omitted because the engine
     * does not enforce it — it is exposed only for resources that choose to
     * react.
     *
     * @return the {@code Preference-Applied} value, or empty
     */
    public Optional<String> toPreferenceApplied() {
        StringBuilder sb = new StringBuilder();
        if (returnRepresentation) append(sb, "return=representation");
        if (returnMinimal)        append(sb, "return=minimal");
        if (handlingLenient)      append(sb, "handling=lenient");
        return sb.length() == 0 ? Optional.empty() : Optional.of(sb.toString());
    }

    private static void append(StringBuilder sb, String token) {
        if (sb.length() > 0) sb.append(", ");
        sb.append(token);
    }
}
