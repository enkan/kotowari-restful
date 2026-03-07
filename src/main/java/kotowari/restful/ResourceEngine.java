package kotowari.restful;

import enkan.data.HttpRequest;
import kotowari.restful.data.ApiResponse;
import kotowari.restful.data.DefaultResource;
import kotowari.restful.data.Problem;
import kotowari.restful.data.Resource;
import kotowari.restful.data.RestContext;
import kotowari.restful.exception.MalformedBodyException;
import kotowari.restful.decision.Action;
import kotowari.restful.decision.Decision;
import kotowari.restful.decision.Handler;
import kotowari.restful.decision.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Function;

import static kotowari.restful.DecisionPoint.*;
import static kotowari.restful.decision.DecisionFactory.*;

/**
 * Owns and executes the Liberator-style decision graph.
 *
 * <p>The graph is built eagerly by {@link #createDefaultGraph()} at construction time
 * and stored in a {@code final} field for thread safety.
 * {@link #run(Resource, HttpRequest)} creates a per-request {@link RestContext}
 * and traverses the graph until a terminal {@link Handler} produces an
 * {@link ApiResponse}.
 *
 * <p>Exception handling:
 * <ul>
 *   <li>{@link MalformedBodyException} → 400 (Bad Request)</li>
 *   <li>All other exceptions → stored on {@link RestContext#setException(Throwable)}
 *       and routed through the {@code HANDLE_EXCEPTION} handler (default 500).
 *       Resource classes may override {@code @Decision(HANDLE_EXCEPTION)} to customize.</li>
 * </ul>
 *
 * @author kawasima
 */
public class ResourceEngine {
    private static final Logger LOG = LoggerFactory.getLogger(ResourceEngine.class);
    private boolean printStackTrace = false;
    private final Node<?> defaultGraph = createDefaultGraph();

    /**
     * Execute a decision graph with the given context.
     *
     * @param context REST Context
     * @return API response
     */
    protected ApiResponse runDecisionGraph(RestContext context) {
        Node<?> decisionNode = defaultGraph;

        try {
            while (true) {
                switch (decisionNode) {
                    case Decision d -> decisionNode = d.execute(context);
                    case Action a -> decisionNode = a.execute(context);
                    case Handler h -> { return h.execute(context); }
                }
            }
        } catch(MalformedBodyException e) {
            LOG.debug("Malformed request body", e);
            ApiResponse response = new ApiResponse();
            response.setStatus(400);
            response.setBody(Problem.valueOf(400));
            return response;
        } catch(Exception e) {
            LOG.error("Error occurs at handling resource", e);
            context.setException(e);
            if (printStackTrace) {
                context.setMessage(Problem.valueOf(500));
            }
            return handler(HANDLE_EXCEPTION, 500, null).execute(context);
        }
    }

    /**
     * Execute a decision class with the given resource and request.
     *
     * @param resource Resource class for executing
     * @param request  A HTTP request
     * @return API response
     */
    public ApiResponse run(Resource resource, HttpRequest request) {
        RestContext context = new RestContext(resource, request);
        return runDecisionGraph(context);
    }

    private static final Function<RestContext, ?> IF_MATCH_STAR_FUNC = context -> Objects.equals("*", context.getRequest().getHeaders().get("if-match"));

    /**
     * Create a default decision graph
     *
     * @return A root node of the constructed decision graph.
     */
    protected Node<?> createDefaultGraph() {
        Node<?> handleSeeOther  = handler(HANDLE_SEE_OTHER, 303, null);
        Node<?> handleOK        = handler(HANDLE_OK, 200, "ok");
        Node<?> handleNoContent = handler(HANDLE_NO_CONTENT, 204, null);
        Node<?> handleMultipleRepresentations = handler(HANDLE_MULTIPLE_REPRESENTATIONS, 300, null);
        Node<?> handleAccepted  = handler(HANDLE_ACCEPTED , 202, null);
        Node<?> isMultipleRepresentations = decision(MULTIPLE_REPRESENTATIONS,
            handleMultipleRepresentations, handleOK);
        Node<?> isRespondWithEntity = decision(RESPOND_WITH_ENTITY,
            isMultipleRepresentations, handleNoContent);
        Node<?> handleCreated   = handler(HANDLE_CREATED, 201, null);
        Node<?> isNew           = decision(NEW, handleCreated, isRespondWithEntity);
        Node<?> doesPostRedirect  = decision(POST_REDIRECT, handleSeeOther, isNew);
        Node<?> isPostEnacted   = decision(POST_ENACTED, doesPostRedirect, handleAccepted);
        Node<?> isPutEnacted    = decision(PUT_ENACTED, isNew, handleAccepted);
        Node<?> handleNotFound  = handler(HANDLE_NOT_FOUND, 404, "Resource not found");
        Node<?> handleGone      = handler(HANDLE_GONE, 410, "Resource is gone");
        // Shared error handler for actions that return a Problem.
        // The Action node sets context.status and context.message from the Problem,
        // so this handler simply emits whatever the context holds.
        Node<?> handleActionError = handler(HANDLE_MALFORMED, 400, null);

        Node<?> post            = action(POST, isPostEnacted, handleActionError);
        Node<?> canPostToMissing= decision(CAN_POST_TO_MISSING, post, handleNotFound);
        Node<?> postToMissing   = decision(POST_TO_MISSING, DefaultResource.testRequestMethod("POST"), canPostToMissing, handleNotFound);
        Node<?> handleMovedPermanently = handler(HANDLE_MOVED_PERMANENTLY, 301, null);
        Node<?> handleMovedTemporarily = handler(HANDLE_MOVED_TEMPORARILY, 307, null);
        Node<?> canPostToGone   = decision(CAN_POST_TO_GONE, post, handleGone);
        Node<?> isPostToGone       = decision(POST_TO_GONE, DefaultResource.testRequestMethod("POST"), canPostToGone, handleGone);
        Node<?> isMovedTemporarily = decision(MOVED_TEMPORARILY, handleMovedTemporarily, isPostToGone);
        Node<?> isMovedPermanently = decision(MOVED_PERMANENTLY, handleMovedPermanently, isMovedTemporarily);
        Node<?> didExist         = decision(EXISTED, isMovedPermanently, postToMissing);
        Node<?> handleConflict  = handler(HANDLE_CONFLICT, 409, "Conflict.");
        Node<?> isPatchEnacted  = decision(PATCH_ENACTED, isRespondWithEntity, handleAccepted);
        Node<?> patch           = action(PATCH, isPatchEnacted, handleActionError);
        Node<?> put             = action(PUT, isPutEnacted, handleActionError);
        Node<?> isMethodPost      = decision(METHOD_POST, DefaultResource.testRequestMethod("POST"), post, put);
        Node<?> doesConflict        = decision(CONFLICT, handleConflict, isMethodPost);
        Node<?> handleNotImplemented = handler(HANDLE_NOT_IMPLEMENTED, 501, "Not implemented.");
        Node<?> canPutToMissing = decision(CAN_PUT_TO_MISSING, doesConflict, handleNotFound);
        Node<?> doesPutToDifferentUrl = decision(PUT_TO_DIFFERENT_URL, handleMovedPermanently, canPutToMissing);
        Node<?> isMethodPut       = decision(METHOD_PUT, DefaultResource.testRequestMethod("PUT"), doesPutToDifferentUrl, didExist);
        Node<?> handlePreconditionFailed = handler(HANDLE_PRECONDITION_FAILED, 412, "Precondition failed.");
        Node<?> doesIfMatchStarExistForMissing = decision(DOES_IF_MATCH_STAR_EXIST_FOR_MISSING,
            IF_MATCH_STAR_FUNC,
            handlePreconditionFailed,
            isMethodPut);
        Node<?> handleNotModified = handler(HANDLE_NOT_MODIFIED, 304, null);
        Node<?> ifNoneMatch     = decision(IF_NONE_MATCH,
            DefaultResource.testRequestMethod("HEAD", "GET"),
            handleNotModified,
            handlePreconditionFailed);
        Node<?> putToExisting   = decision(PUT_TO_EXISTING,
            DefaultResource.testRequestMethod("PUT"),
            doesConflict,
            isMultipleRepresentations);
        Node<?> postToExisting   = decision(POST_TO_EXISTING,
            DefaultResource.testRequestMethod("POST"),
            doesConflict,
            putToExisting);
        Node<?> isDeleteEnacted  = decision(DELETE_ENACTED, isRespondWithEntity, handleAccepted);
        Node<?> delete           = action(DELETE, isDeleteEnacted, handleActionError);
        Node<?> methodPatch      = decision(METHOD_PATCH,
            DefaultResource.testRequestMethod("PATCH"), patch, postToExisting);
        Node<?> methodDelete     = decision(METHOD_DELETE,
            DefaultResource.testRequestMethod("DELETE"), delete, methodPatch);
        Node<?> modifiedSince    = decision(MODIFIED_SINCE,
            methodDelete,
            handleNotModified);
        Node<?> ifModifiedSinceValidDate = decision(IF_MODIFIED_SINCE_VALID_DATE,
            context -> null,
            modifiedSince,
            methodDelete);
        Node<?> ifModifiedSinceExists = decision(IF_MODIFIED_SINCE_EXISTS,
            context -> context.getRequest().getHeaders().containsKey("if-modified-since"),
            ifModifiedSinceValidDate,
            methodDelete);

        Node<?> etagMatchesForIfNone = decision(ETAG_MATCHES_FOR_IF_NONE,
            ifNoneMatch,
            ifModifiedSinceExists);

        Node<?> ifNoneMatchStar = decision(IF_NONE_MATCH_STAR,
            context -> Objects.equals("*", context.getRequest().getHeaders().get("if-none-match")),
            ifNoneMatch,
            etagMatchesForIfNone);

        Node<?> ifNoneMatchExists = decision(IF_NONE_MATCH_EXISTS,
            context -> context.getRequest().getHeaders().containsKey("if-none-match"),
            ifNoneMatchStar,
            ifModifiedSinceExists);

        Node<?> unmodifiedSince = decision(UNMODIFIED_SINCE,
            handlePreconditionFailed,
            ifNoneMatchExists);

        Node<?> ifUnmodifiedSinceValidDate = decision(IF_UNMODIFIED_SINCE_VALID_DATE,
            context -> null,
            unmodifiedSince,
            ifNoneMatchExists);

        Node<?> ifUnmodifiedSinceExists = decision(IF_UNMODIFIED_SINCE_EXISTS,
            context -> context.getRequest().getHeaders().containsKey("if-unmodified-since"),
            ifUnmodifiedSinceValidDate,
            ifNoneMatchExists);

        Node<?> etagMatchesForIfMatch = decision(ETAG_MATCHES_FOR_IF_MATCH,
            ifUnmodifiedSinceValidDate,
            handlePreconditionFailed);

        Node<?> ifMatchStar = decision(IF_MATCH_STAR,
            ifUnmodifiedSinceExists,
            etagMatchesForIfMatch);

        Node<?> ifMatchExists = decision(IF_MATCH_EXISTS,
            context -> context.getRequest().getHeaders().containsKey("if-match"),
            ifMatchStar,
            ifUnmodifiedSinceExists);

        Node<?> exists = decision(EXISTS, ifMatchExists, doesIfMatchStarExistForMissing);
        Node<?> handleUnprocessableEntity = handler(HANDLE_UNPROCESSABLE_ENTITY, 422, "Unprocessable entity.");
        Node<?> processable = decision(PROCESSABLE, exists, handleUnprocessableEntity);
        Node<?> handleNotAcceptable = handler(HANDLE_NOT_ACCEPTABLE, 406, "No acceptable resource available.");
        Node<?> isEncodingAvailable = decision(ENCODING_AVAILABLE,
            processable, handleNotAcceptable);

        Node<?> acceptEncodingExists = decision(ACCEPT_ENCODING_EXISTS,
            context -> context.getRequest().getHeaders().containsKey("accept-encoding"),
            isEncodingAvailable, processable);

        Node<?> isCharsetAvailable = decision(CHARSET_AVAILABLE,
            acceptEncodingExists,
            handleNotAcceptable);

        Node<?> acceptCharsetExists = decision(ACCEPT_CHARSET_EXISTS,
            context -> context.getRequest().getHeaders().containsKey("accept-charset"),
            isCharsetAvailable, acceptEncodingExists);
        Node<?> languageAvailable = decision(LANGUAGE_AVAILABLE,
            acceptCharsetExists,
            handleNotAcceptable);
        Node<?> acceptLanguageExists = decision(ACCEPT_LANGUAGE_EXISTS,
            context -> context.getRequest().getHeaders().containsKey("accept-language"),
            languageAvailable, acceptCharsetExists);
        Node<?> mediaTypeAvailable = decision(MEDIA_TYPE_AVAILABLE,
            acceptLanguageExists, handleNotAcceptable);
        Node<?> acceptExists = decision(ACCEPT_EXISTS,
            context -> context.getRequest().getHeaders().containsKey("accept"),
            mediaTypeAvailable, acceptLanguageExists);

        Node<?> handleOptions = handler(HANDLE_OPTIONS, 200, null);

        Node<?> isOptions = decision(IS_OPTIONS,
            DefaultResource.testRequestMethod("OPTIONS"),
            handleOptions,
            acceptExists);

        Node<?> handleRequestEntityTooLarge = handler(HANDLE_REQUEST_ENTITY_TOO_LARGE, 413, "Request entity too large.");
        Node<?> validEntityLength = decision(VALID_ENTITY_LENGTH,
            isOptions, handleRequestEntityTooLarge);
        Node<?> handleUnsupportedMediaType = handler(HANDLE_UNSUPPORTED_MEDIA_TYPE, 415, "Unsupported media type.");
        Node<?> knownContentType = decision(KNOWN_CONTENT_TYPE, validEntityLength, handleUnsupportedMediaType);
        Node<?> validContentHeader = decision(VALID_CONTENT_HEADER, knownContentType, handleNotImplemented);
        Node<?> handleForbidden = handler(HANDLE_FORBIDDEN, 403, "Forbidden.");
        Node<?> isAllowed = decision(ALLOWED, validContentHeader, handleForbidden);
        Node<?> handleUnauthorized = handler(HANDLE_UNAUTHORIZED, 401, "Not Authorized.");
        Node<?> isAuthorized = decision(AUTHORIZED, isAllowed, handleUnauthorized);
        Node<?> handleMalformed = handler(HANDLE_MALFORMED, 400, "Bad request.");
        Node<?> malformed = decision(MALFORMED, handleMalformed, isAuthorized);

        Node<?> handleMethodNotAllowed = handler(HANDLE_METHOD_NOT_ALLOWED, 405, "Method not Allowed");
        Node<?> methodAllowed = decision(METHOD_ALLOWED, null, malformed, handleMethodNotAllowed);

        Node<?> handleUriTooLong = handler(HANDLE_URI_TOO_LONG, 414, "Request URI too long.");
        Node<?> uriTooLong = decision(URI_TOO_LONG, handleUriTooLong, methodAllowed);

        Node<?> handleUnknownMethod = handler(HANDLE_UNKNOWN_METHOD, 501, "Unknown method.");
        Node<?> knownMethod = decision(KNOWN_METHOD, uriTooLong, handleUnknownMethod);

        Node<?> handleServiceNotAvailable = handler(HANDLE_SERVICE_NOT_AVAILABLE, 503, "Service not available.");
        Node<?> serviceAvailable = decision(SERVICE_AVAILABLE, knownMethod, handleServiceNotAvailable);

        return action(INITIALIZE_CONTEXT, serviceAvailable);
    }

    /**
     * When {@code true}, the {@code HANDLE_EXCEPTION} handler includes
     * exception details in the response body as a {@link Problem}.
     * Should only be enabled in development environments.
     *
     * @param printStackTrace {@code true} to include exception details in 500 responses
     */
    public void setPrintStackTrace(boolean printStackTrace) {
        this.printStackTrace = printStackTrace;
    }
}
