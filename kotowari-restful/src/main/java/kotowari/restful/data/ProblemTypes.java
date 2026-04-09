package kotowari.restful.data;

import java.net.URI;

/**
 * Canonical {@code type} URIs for the most common {@link Problem} categories,
 * as recommended by RFC 9457 §4.2. Having stable, dereferenceable URIs for
 * problem types allows clients to identify errors programmatically without
 * depending on HTTP status codes alone.
 *
 * <p>The base URI defaults to {@code https://kotowari.unit8.net/problems/} and
 * can be overridden at JVM start-up via the system property
 * {@code kotowari.restful.problem.base-uri}. This is useful for deployments
 * that want to host a problem-type registry on their own domain — the returned
 * URIs will dereference to documentation under the chosen base.
 *
 * <p>Example:
 * <pre>{@code
 * return Problem.builder()
 *         .status(422)
 *         .type(ProblemTypes.UNPROCESSABLE_ENTITY)
 *         .detail("name must not be blank")
 *         .build();
 * }</pre>
 *
 * <p>Constants are provided only for the status codes with concrete meanings
 * in the Kotowari-Restful decision graph. Applications are free to define
 * their own URIs for finer-grained problem types.
 */
public final class ProblemTypes {
    /** System property for overriding the default base URI. */
    public static final String BASE_URI_PROPERTY = "kotowari.restful.problem.base-uri";

    private static final String DEFAULT_BASE_URI = "https://kotowari.unit8.net/problems/";

    /** The resolved base URI, ending in {@code /}. */
    public static final String BASE_URI = resolveBaseUri();

    public static final URI BAD_REQUEST            = uri("bad-request");
    public static final URI UNAUTHORIZED           = uri("unauthorized");
    public static final URI FORBIDDEN              = uri("forbidden");
    public static final URI NOT_FOUND              = uri("not-found");
    public static final URI METHOD_NOT_ALLOWED     = uri("method-not-allowed");
    public static final URI NOT_ACCEPTABLE         = uri("not-acceptable");
    public static final URI CONFLICT               = uri("conflict");
    public static final URI GONE                   = uri("gone");
    public static final URI PRECONDITION_FAILED    = uri("precondition-failed");
    public static final URI PAYLOAD_TOO_LARGE      = uri("payload-too-large");
    public static final URI UNSUPPORTED_MEDIA_TYPE = uri("unsupported-media-type");
    public static final URI UNPROCESSABLE_ENTITY   = uri("unprocessable-entity");
    public static final URI PRECONDITION_REQUIRED  = uri("precondition-required");
    public static final URI TOO_MANY_REQUESTS      = uri("too-many-requests");
    public static final URI INTERNAL_SERVER_ERROR  = uri("internal-server-error");
    public static final URI SERVICE_UNAVAILABLE    = uri("service-unavailable");

    private ProblemTypes() {}

    private static String resolveBaseUri() {
        String configured = System.getProperty(BASE_URI_PROPERTY, DEFAULT_BASE_URI);
        return configured.endsWith("/") ? configured : configured + "/";
    }

    private static URI uri(String slug) {
        return URI.create(BASE_URI + slug);
    }
}
