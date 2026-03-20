package kotowari.restful.data;

import java.time.Instant;

/**
 * A thin wrapper around {@link Instant} used for parsed HTTP-date header values.
 *
 * <p>This wrapper exists to prevent HTTP-date values from participating in
 * {@link RestContext}'s type-based parameter injection index as plain {@code Instant}
 * instances. Without this wrapper, any resource method with an {@code Instant}
 * parameter could be unexpectedly injected with a conditional request header date.
 *
 * <p>Use {@link #value()} to obtain the underlying {@link Instant} for comparison:
 * <pre>{@code
 * @Decision(MODIFIED_SINCE)
 * public boolean modifiedSince(RestContext ctx) {
 *     Instant clientDate = ctx.get(RestContext.IF_MODIFIED_SINCE_DATE)
 *             .map(HttpDate::value).orElseThrow();
 *     return myLastModified.isAfter(clientDate);
 * }
 * }</pre>
 *
 * @param value the parsed HTTP-date as an {@link Instant}
 */
public record HttpDate(Instant value) {
}
