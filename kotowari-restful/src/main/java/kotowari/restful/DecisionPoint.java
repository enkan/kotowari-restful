package kotowari.restful;

/**
 * Every node in the decision graph.
 *
 * <p>Divided into four categories:
 * <ul>
 *   <li><b>User-customizable decisions</b> — override via {@code @Decision}
 *       on a resource class (e.g. {@code EXISTS}, {@code MALFORMED}, {@code AUTHORIZED}).</li>
 *   <li><b>Internal HTTP header decisions</b> — driven by the engine based on
 *       request headers (e.g. {@code IF_MATCH_EXISTS}, {@code ACCEPT_EXISTS}).</li>
 *   <li><b>Actions</b> — {@code POST}, {@code PUT}, {@code PATCH}, {@code DELETE},
 *       {@code INITIALIZE_CONTEXT}.</li>
 *   <li><b>Handlers</b> — terminal nodes that produce an {@link kotowari.restful.data.ApiResponse}
 *       with a fixed status code (e.g. {@code HANDLE_OK} → 200, {@code HANDLE_CREATED} → 201,
 *       {@code HANDLE_EXCEPTION} → 500).</li>
 * </ul>
 *
 * @author kawasima
 */
public enum DecisionPoint {
    ALLOWED,
    AUTHORIZED,
    CHARSET_AVAILABLE,
    CAN_POST_TO_GONE,
    CAN_POST_TO_MISSING,
    CAN_PUT_TO_MISSING,
    CONFLICT,
    DELETE_ENACTED,
    ENCODING_AVAILABLE,
    ETAG_MATCHES_FOR_IF_MATCH,
    ETAG_MATCHES_FOR_IF_NONE,
    EXISTED,
    EXISTS,
    KNOWN_CONTENT_TYPE,
    KNOWN_METHOD,
    LANGUAGE_AVAILABLE,
    MALFORMED,
    MEDIA_TYPE_AVAILABLE,
    METHOD_ALLOWED,
    MODIFIED_SINCE,
    MOVED_PERMANENTLY,
    MOVED_TEMPORARILY,
    MULTIPLE_REPRESENTATIONS,
    POST_ENACTED,
    PUT_ENACTED,
    PATCH_ENACTED,
    NEW,
    POST_REDIRECT,
    PUT_TO_DIFFERENT_URL,
    PROCESSABLE,
    RESPOND_WITH_ENTITY,
    SERVICE_AVAILABLE,
    /**
     * Rate-limit check per RFC 6585 §4. Returns {@code true} to return
     * {@code 429 Too Many Requests}, {@code false} (the default) to allow
     * the request through. Resources may stash a {@code Retry-After} value
     * via {@link kotowari.restful.data.RestContext#addHeader(String, String)}.
     *
     * <p><b>OPTIONS bypass:</b> the engine always skips this decision for
     * {@code OPTIONS} requests, so CORS preflight and capability-discovery
     * requests are never rate-limited by the default graph. Resources that
     * want to rate-limit {@code OPTIONS} can still do so by inspecting the
     * request method from inside a custom {@code TOO_MANY_REQUESTS}
     * function — but note that the default graph will short-circuit around
     * the function for OPTIONS, so any such enforcement must be implemented
     * outside the default decision path (e.g. via an enkan middleware).
     */
    TOO_MANY_REQUESTS,
    /**
     * Preconditional requirement check per RFC 6585 §3. Returns {@code true}
     * when the request carries a precondition header ({@code If-Match} /
     * {@code If-Unmodified-Since}) that the resource required, or when the
     * resource does not require one. Returns {@code false} to return
     * {@code 428 Precondition Required}.
     */
    PRECONDITION_REQUIRED,
    UNMODIFIED_SINCE,
    URI_TOO_LONG,
    VALID_CONTENT_HEADER,
    VALID_ENTITY_LENGTH,
    //Internal Decision
    ACCEPT_CHARSET_EXISTS,
    ACCEPT_ENCODING_EXISTS,
    ACCEPT_LANGUAGE_EXISTS,
    ACCEPT_EXISTS,
    IF_MATCH_EXISTS,
    IF_MATCH_STAR,
    DOES_IF_MATCH_STAR_EXIST_FOR_MISSING,
    IF_MODIFIED_SINCE_EXISTS,
    IF_MODIFIED_SINCE_VALID_DATE,
    IF_NONE_MATCH,
    IF_NONE_MATCH_EXISTS,
    IF_NONE_MATCH_STAR,
    IF_UNMODIFIED_SINCE_EXISTS,
    IF_UNMODIFIED_SINCE_VALID_DATE,
    IS_OPTIONS,
    METHOD_DELETE,
    METHOD_POST,
    METHOD_PUT,
    METHOD_PATCH,
    POST_TO_GONE,
    POST_TO_EXISTING,
    POST_TO_MISSING,
    PUT_TO_EXISTING,

    // Actions
    INITIALIZE_CONTEXT,
    POST,
    PUT,
    DELETE,
    PATCH,

    // Handlers
    HANDLE_OK,
    HANDLE_CREATED,
    HANDLE_OPTIONS,
    HANDLE_ACCEPTED,
    HANDLE_NO_CONTENT,
    HANDLE_MOVED_PERMANENTLY,
    HANDLE_SEE_OTHER,
    HANDLE_NOT_MODIFIED,
    HANDLE_MOVED_TEMPORARILY,
    HANDLE_MULTIPLE_REPRESENTATIONS,
    HANDLE_MALFORMED,
    HANDLE_UNAUTHORIZED,
    HANDLE_FORBIDDEN,
    HANDLE_NOT_FOUND,
    HANDLE_METHOD_NOT_ALLOWED,
    HANDLE_NOT_ACCEPTABLE,
    HANDLE_CONFLICT,
    HANDLE_GONE,
    HANDLE_PRECONDITION_FAILED,
    HANDLE_REQUEST_ENTITY_TOO_LARGE,
    HANDLE_URI_TOO_LONG,
    HANDLE_UNSUPPORTED_MEDIA_TYPE,
    HANDLE_UNPROCESSABLE_ENTITY,
    HANDLE_EXCEPTION,
    HANDLE_NOT_IMPLEMENTED,
    HANDLE_UNKNOWN_METHOD,
    HANDLE_SERVICE_NOT_AVAILABLE,
    HANDLE_TOO_MANY_REQUESTS,
    HANDLE_PRECONDITION_REQUIRED
}
