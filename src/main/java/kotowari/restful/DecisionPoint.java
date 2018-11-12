package kotowari.restful;

public enum DecisionPoint {
    IS_ALLOWED,
    IS_AUTHORIZED,
    IS_CHARSET_AVAILABLE,
    CAN_POST_TO_GONE,
    CAN_POST_TO_MISSING,
    CAN_PUT_TO_MISSING,
    DOES_CONFLICT,
    IS_DELETE_ENACTED,
    IS_ENCODING_AVAILABLE,
    ETAG_MATCHES_FOR_IF_MATCH,
    ETAG_MATCHES_FOR_IF_NONE,
    DID_EXIST,
    EXISTS,
    KNOWN_CONTENT_TYPE,
    KNOWN_METHOD,
    LANGUAGE_AVAILABLE,
    MALFORMED,
    MEDIA_TYPE_AVAILABLE,
    METHOD_ALLOWED,
    MODIFIED_SINCE,
    IS_MOVED_PERMANENTLY,
    IS_MOVED_TEMPORARILY,
    IS_MULTIPLE_REPRESENTATIONS,
    IS_POST_ENACTED,
    IS_PUT_ENACTED,
    IS_PATCH_ENACTED,
    IS_NEW,
    DOES_POST_REDIRECT,
    DOES_PUT_TO_DIFFERENT_URL,
    PROCESSABLE,
    IS_RESPOND_WITH_ENTITY,
    SERVICE_AVAILABLE,
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
    IF_INMODIFIED_SINCE_VALID_DATE,
    IS_OPTIONS,
    METHOD_DELETE,
    IS_METHOD_POST,
    IS_METHOD_PUT,
    METHOD_PATCH,
    IS_POST_TO_GONE,
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
    HANDLE_SERVICE_NOT_AVAILABLE
}
