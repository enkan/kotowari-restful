package kotowari.restful.data;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serial;
import java.io.Serializable;
import java.net.URI;
import java.util.*;

/**
 * Immutable RFC 9457 "Problem Details for HTTP APIs" representation.
 *
 * <p>Instances are created via factory methods:
 * <ul>
 *   <li>{@link #valueOf(int)} / {@link #valueOf(int, String)} — from an HTTP status code</li>
 *   <li>{@link #fromViolationList(List)} — from a list of {@link Violation}s (400)</li>
 * </ul>
 *
 * <p>Serialized to JSON by Jackson; fields with {@code null} or empty values are omitted.
 *
 * @author kawasima
 */
public class Problem implements Serializable{
    @Serial
    private static final long serialVersionUID = 1L;
    private static final URI DEFAULT_TYPE = URI.create("about:blank");
    private final URI type;
    private final String title;
    private final int status;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String detail;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final URI instance;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<Violation> violations;

    private Problem(URI type, String title, int status, String detail, URI instance, List<Violation> violations) {
        this.type = Optional.ofNullable(type).orElse(DEFAULT_TYPE);
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.instance = instance;
        this.violations = violations != null ? violations : List.of();
    }

    /**
     * Creates a Problem with the given RFC 9457 fields (without violations).
     *
     * @param type     a URI identifying the problem type, or {@code null} for {@code about:blank}
     * @param title    a short human-readable summary
     * @param status   the HTTP status code
     * @param detail   a human-readable explanation specific to this occurrence, or {@code null}
     * @param instance a URI identifying the specific occurrence, or {@code null}
     */
    public Problem(URI type, String title, int status, String detail, URI instance) {
        this(type, title, status, detail, instance, null);
    }

    /**
     * Creates a 400 (Bad Request) Problem from a pre-built list of {@link Violation}s.
     *
     * @param violations the list of violations
     * @return a Problem with status 400 and the violations list
     */
    public static Problem fromViolationList(List<Violation> violations) {
        return new Problem(DEFAULT_TYPE, "Malformed", 400, null, null, violations);
    }

    /**
     * Creates a Problem with the given HTTP status code and its standard title.
     *
     * @param status the HTTP status code
     * @return a Problem with the standard title for the status
     */
    public static Problem valueOf(int status) {
        return new Problem(DEFAULT_TYPE, DEFAULT_TITLES.getOrDefault(status, "Problem occurs"), status, null, null);
    }

    /**
     * Creates a Problem with the given HTTP status code, its standard title, and a detail message.
     *
     * @param status the HTTP status code
     * @param detail a human-readable explanation specific to this occurrence
     * @return a Problem with the standard title and detail
     */
    public static Problem valueOf(int status, String detail) {
        return new Problem(DEFAULT_TYPE, DEFAULT_TITLES.getOrDefault(status, "Problem occurs"), status, detail, null);
    }

    public static Problem valueOf(int status, URI instance) {
        return new Problem(DEFAULT_TYPE, DEFAULT_TITLES.getOrDefault(status, "Problem occurs"), status, null, instance);
    }

    public static Problem valueOf(int status, String detail, URI instance) {
        return new Problem(DEFAULT_TYPE, DEFAULT_TITLES.getOrDefault(status, "Problem occurs"), status, detail, instance);
    }

    /**
     * Returns a fluent {@link Builder} for constructing a {@link Problem} with
     * a custom {@code type} URI and other optional fields, as recommended by
     * RFC 9457 §4.2.
     *
     * <p>Example:
     * <pre>{@code
     * Problem.builder()
     *     .status(429)
     *     .type(ProblemTypes.TOO_MANY_REQUESTS)
     *     .detail("rate limit exceeded; retry in 30s")
     *     .build();
     * }</pre>
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link Problem} instances with RFC 9457 {@code type}
     * URIs and other optional fields. Created via {@link Problem#builder()}.
     *
     * <p>{@link #status(int)} is the only required call; {@link #title(String)}
     * defaults to the standard HTTP reason phrase for the status. Unset fields
     * are omitted from the JSON serialization.
     */
    public static final class Builder {
        private URI type;
        private String title;
        private int status;
        private String detail;
        private URI instance;
        private List<Violation> violations;

        private Builder() {}

        /** Sets the RFC 9457 {@code type} URI. */
        public Builder type(URI type) { this.type = type; return this; }

        /** Sets the RFC 9457 {@code title}. Defaults to the standard reason phrase. */
        public Builder title(String title) { this.title = title; return this; }

        /** Sets the HTTP status code (required). */
        public Builder status(int status) { this.status = status; return this; }

        /** Sets the per-occurrence {@code detail} message. */
        public Builder detail(String detail) { this.detail = detail; return this; }

        /** Sets the RFC 9457 {@code instance} URI. */
        public Builder instance(URI instance) { this.instance = instance; return this; }

        /**
         * Sets the field-level violations list. The list is defensively
         * copied to an unmodifiable {@link List} so that later mutations of
         * the caller's list cannot leak into the built {@link Problem}.
         *
         * @param violations the list to copy, or {@code null} to clear
         * @return this builder
         */
        public Builder violations(List<Violation> violations) {
            this.violations = violations == null ? null : List.copyOf(violations);
            return this;
        }

        /**
         * Builds the immutable {@link Problem}. If {@link #title(String)} was
         * not set, the standard reason phrase for the status is used.
         *
         * @return the constructed Problem
         */
        public Problem build() {
            String resolvedTitle = title != null
                    ? title
                    : DEFAULT_TITLES.getOrDefault(status, "Problem occurs");
            return new Problem(type, resolvedTitle, status, detail, instance, violations);
        }
    }

    /**
     * A single field-level validation error included in a Problem response.
     */
    public record Violation(String field, String code, String message) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * Creates a Violation without a code (backward-compatible).
         *
         * @param field   the field path
         * @param message the error message
         */
        public Violation(String field, String message) {
            this(field, null, message);
        }
    }

    public URI getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public int getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    public URI getInstance() {
        return instance;
    }

    public List<Violation> getViolations() {
        return violations;
    }

    private static final Map<Integer, String> DEFAULT_TITLES;
    static {
        Map<Integer, String> m = new HashMap<>();
        m.put(100, "Continue");
        m.put(101, "Switching Protocols");
        m.put(102, "Processing");
        m.put(103, "Checkpoint");
        m.put(200, "OK");
        m.put(201, "Created");
        m.put(202, "Accepted");
        m.put(203, "Non-Authoritative Information");
        m.put(204, "No Content");
        m.put(205, "Reset Content");
        m.put(206, "Partial Content");
        m.put(207, "Multi-Status");
        m.put(208, "Already Reported");
        m.put(226, "IM Used");
        m.put(300, "Multiple Choices");
        m.put(301, "Moved Permanently");
        m.put(302, "Found");
        m.put(303, "See Other");
        m.put(304, "Not Modified");
        m.put(305, "Use Proxy");
        m.put(307, "Temporary Redirect");
        m.put(308, "Permanent Redirect");
        m.put(400, "Bad Request");
        m.put(401, "Unauthorized");
        m.put(402, "Payment Required");
        m.put(403, "Forbidden");
        m.put(404, "Not Found");
        m.put(405, "Method Not Allowed");
        m.put(406, "Not Acceptable");
        m.put(407, "Proxy Authentication Required");
        m.put(408, "Request Timeout");
        m.put(409, "Conflict");
        m.put(410, "Gone");
        m.put(411, "Length Required");
        m.put(412, "Precondition Failed");
        m.put(413, "Request Entity Too Large");
        m.put(414, "Request-URI Too Long");
        m.put(415, "Unsupported Media Type");
        m.put(416, "Requested Range Not Satisfiable");
        m.put(417, "Expectation Failed");
        m.put(418, "I'm a teapot");
        m.put(422, "Unprocessable Entity");
        m.put(423, "Locked");
        m.put(424, "Failed Dependency");
        m.put(426, "Upgrade Required");
        m.put(428, "Precondition Required");
        m.put(429, "Too Many Requests");
        m.put(431, "Request Header Fields Too Large");
        m.put(451, "Unavailable For Legal Reasons");
        m.put(500, "Internal Server Error");
        m.put(501, "Not Implemented");
        m.put(502, "Bad Gateway");
        m.put(503, "Service Unavailable");
        m.put(504, "Gateway Timeout");
        m.put(505, "HTTP Version Not Supported");
        m.put(506, "Variant Also Negotiates");
        m.put(507, "Insufficient Storage");
        m.put(508, "Loop Detected");
        m.put(509, "Bandwidth Limit Exceeded");
        m.put(510, "Not Extended");
        m.put(511, "Network Authentication Required");
        DEFAULT_TITLES = Collections.unmodifiableMap(m);
    }
}
