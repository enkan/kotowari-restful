package kotowari.restful.data;

import com.fasterxml.jackson.annotation.JsonInclude;

import javax.validation.ConstraintViolation;
import java.io.Serializable;
import java.net.URI;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A representation for problem JSON.
 *
 * @author kawasima
 */
public class Problem implements Serializable{
    private static final URI DEFAULT_TYPE = URI.create("about:blank");
    private URI type;
    private String title;
    private int status;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String detail;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private URI instance;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<Violation> violations;

    private Problem() {

    }

    public Problem(URI type, String title, int status, String detail, URI instance) {
        this.type = Optional.ofNullable(type).orElse(DEFAULT_TYPE);
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.instance = instance;
    }

    public static <T> Problem fromViolations(Set<ConstraintViolation<T>> violations) {
        Problem problem = new Problem(DEFAULT_TYPE, "Malformed", 400, null, null);
        problem.violations = violations.stream()
                .map((Function<ConstraintViolation<T>, Violation>) Violation::new)
                .collect(Collectors.toList());
        return problem;
    }

    public static Problem fromException(Exception e) {
        return new Problem(DEFAULT_TYPE, "Internal Server Error", 500, e.getMessage(), null);
    }

    public static Problem valueOf(int status) {
        Problem problem = new Problem();
        problem.type = DEFAULT_TYPE;
        problem.title = DEFAULT_TITLES.getOrDefault(status, "Problem occurs");
        problem.status = status;
        return problem;
    }

    public static Problem valueOf(int status, String detail) {
        Problem problem = Problem.valueOf(status);
        problem.detail = detail;
        return problem;
    }

    public static Problem valueOf(int status, URI instance) {
        Problem problem = Problem.valueOf(status);
        problem.instance = instance;
        return problem;
    }

    public static Problem valueOf(int status, String detail, URI instance) {
        Problem problem = Problem.valueOf(status, detail);
        problem.instance = instance;
        return problem;
    }

    public static class Violation<T> implements Serializable {
        private String field;
        private String message;

        public Violation(ConstraintViolation<T> constraintViolation) {
            this.field = constraintViolation.getPropertyPath().toString();
            this.message = constraintViolation.getMessage();
        }

        public Violation(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public String getMessage() {
            return message;
        }
    }

    public URI getType() {
        return type;
    }

    public void setType(URI type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public URI getInstance() {
        return instance;
    }

    public void setInstance(URI instance) {
        this.instance = instance;
    }

    public List<Violation> getViolations() {
        return violations;
    }

    private static final Map<Integer, String> DEFAULT_TITLES = new HashMap<>();

    static {
        DEFAULT_TITLES.put(100, "Continue");
        DEFAULT_TITLES.put(101, "Switching Protocols");
        DEFAULT_TITLES.put(102, "Processing");
        DEFAULT_TITLES.put(103, "Checkpoint");
        DEFAULT_TITLES.put(200, "OK");
        DEFAULT_TITLES.put(201, "Created");
        DEFAULT_TITLES.put(202, "Accepted");
        DEFAULT_TITLES.put(203, "Non-Authoritative Information");
        DEFAULT_TITLES.put(204, "No Content");
        DEFAULT_TITLES.put(205, "Reset Content");
        DEFAULT_TITLES.put(206, "Partial Content");
        DEFAULT_TITLES.put(207, "Multi-Status");
        DEFAULT_TITLES.put(208, "Already Reported");
        DEFAULT_TITLES.put(226, "IM Used");
        DEFAULT_TITLES.put(300, "Multiple Choices");
        DEFAULT_TITLES.put(301, "Moved Permanently");
        DEFAULT_TITLES.put(302, "Found");
        DEFAULT_TITLES.put(303, "See Other");
        DEFAULT_TITLES.put(304, "Not Modified");
        DEFAULT_TITLES.put(305, "Use Proxy");
        DEFAULT_TITLES.put(307, "Temporary Redirect");
        DEFAULT_TITLES.put(308, "Permanent Redirect");
        DEFAULT_TITLES.put(400, "Bad Request");
        DEFAULT_TITLES.put(401, "Unauthorized");
        DEFAULT_TITLES.put(402, "Payment Required");
        DEFAULT_TITLES.put(403, "Forbidden");
        DEFAULT_TITLES.put(404, "Not Found");
        DEFAULT_TITLES.put(405, "Method Not Allowed");
        DEFAULT_TITLES.put(406, "Not Acceptable");
        DEFAULT_TITLES.put(407, "Proxy Authentication Required");
        DEFAULT_TITLES.put(408, "Request Timeout");
        DEFAULT_TITLES.put(409, "Conflict");
        DEFAULT_TITLES.put(410, "Gone");
        DEFAULT_TITLES.put(411, "Length Required");
        DEFAULT_TITLES.put(412, "Precondition Failed");
        DEFAULT_TITLES.put(413, "Request Entity Too Large");
        DEFAULT_TITLES.put(414, "Request-URI Too Long");
        DEFAULT_TITLES.put(415, "Unsupported Media Type");
        DEFAULT_TITLES.put(416, "Requested Range Not Satisfiable");
        DEFAULT_TITLES.put(417, "Expectation Failed");
        DEFAULT_TITLES.put(418, "I'm a teapot");
        DEFAULT_TITLES.put(422, "Unprocessable Entity");
        DEFAULT_TITLES.put(423, "Locked");
        DEFAULT_TITLES.put(424, "Failed Dependency");
        DEFAULT_TITLES.put(426, "Upgrade Required");
        DEFAULT_TITLES.put(428, "Precondition Required");
        DEFAULT_TITLES.put(429, "Too Many Requests");
        DEFAULT_TITLES.put(431, "Request Header Fields Too Large");
        DEFAULT_TITLES.put(451, "Unavailable For Legal Reasons");
        DEFAULT_TITLES.put(500, "Internal Server Error");
        DEFAULT_TITLES.put(501, "Not Implemented");
        DEFAULT_TITLES.put(502, "Bad Gateway");
        DEFAULT_TITLES.put(503, "Service Unavailable");
        DEFAULT_TITLES.put(504, "Gateway Timeout");
        DEFAULT_TITLES.put(505, "HTTP Version Not Supported");
        DEFAULT_TITLES.put(506, "Variant Also Negotiates");
        DEFAULT_TITLES.put(507, "Insufficient Storage");
        DEFAULT_TITLES.put(508, "Loop Detected");
        DEFAULT_TITLES.put(509, "Bandwidth Limit Exceeded");
        DEFAULT_TITLES.put(510, "Not Extended");
        DEFAULT_TITLES.put(511, "Network Authentication Required");
    }
}
