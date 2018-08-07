package kotowari.restful;

import enkan.data.HttpRequest;
import kotowari.restful.data.ApiResponse;
import kotowari.restful.data.Problem;
import kotowari.restful.data.Resource;
import kotowari.restful.data.RestContext;
import kotowari.restful.decision.Decision;
import kotowari.restful.decision.Handler;
import kotowari.restful.decision.Node;

import java.util.*;
import java.util.function.Function;

import static enkan.util.BeanBuilder.*;
import static kotowari.restful.DecisionPoint.*;
import static kotowari.restful.decision.DecisionFactory.*;

public class ResourceEngine {
    private ApiResponse runDecisionGraph(RestContext context) {
        Node<?> decisionNode = createDefaultGraph();

        try {
            do {
                if (decisionNode instanceof Decision) {
                    decisionNode = ((Decision) decisionNode).execute(context);
                } else if (decisionNode instanceof Handler) {
                    return ((Handler) decisionNode).execute(context);
                }
            } while (decisionNode != null);
        } catch(Exception e) {
            return builder(new ApiResponse())
                    .set(ApiResponse::setStatus, 500)
                    .set(ApiResponse::setBody, Problem.fromException(e))
                    .build();
        }
        return builder(new ApiResponse())
                .set(ApiResponse::setStatus, 500)
                .build();
    }
    public ApiResponse run(Resource resource, HttpRequest request) {
        RestContext context = new RestContext(resource, request);

        return runDecisionGraph(context);
    }

    private Function<RestContext, ?> createIsMethod(String... methods) {
        Set<String> methodSet = new HashSet<>(Arrays.asList(methods));
        return context -> methodSet.contains(context.getRequest().getRequestMethod().toUpperCase(Locale.US));
    }

    private Function<RestContext, ?> IF_MATCH_STAR_FUNC = context -> Objects.equals("*", context.getRequest().getHeaders().get("if-match"));

    public Date genLastModified(RestContext context) {
        return null;
    }

    public Node<?> createDefaultGraph() {
        Node<?> handleSeeOther  = handler(HANDLE_SEE_OTHER, 303, null);
        Node<?> handleOK        = handler(HANDLE_OK, 200, "ok");
        Node handleNoContent = handler(HANDLE_NO_CONTENT, 204, null);
        Node handleMultipleRepresentations = handler(HANDLE_MULTIPLE_REPRESENTATIONS, 300, null);
        Node handleAccepted  = handler(HANDLE_ACCEPTED , 202, null);
        Node isMultipleRepresentations = decision(MULTIPLE_REPRESENTATIONS,
            handleMultipleRepresentations, handleOK);
        Node isRespondWithEntity = decision(RESPOND_WITH_ENTITY,
            isMultipleRepresentations, handleNoContent);
        Node handleCreated   = handler(HANDLE_CREATED, 201, null);
        Node isNew           = decision(NEW, handleCreated, isRespondWithEntity);
        Node isPostRedirect  = decision(POST_REDIRECT, handleSeeOther, isNew);
        Node isPostEnacted   = decision(POST_ENACTED, isPostRedirect, handleAccepted);
        Node isPutEnacted    = decision(PUT_ENACTED, isNew, handleAccepted);
        Node handleNotFound  = handler(HANDLE_NOT_FOUND, 404, "Resource not found");
        Node handleGone      = handler(HANDLE_GONE, 410, "Resource is gone");
        Node post            = action(POST, isPostEnacted);
        Node canPostToMissing= decision(CAN_POST_TO_MISSING, post, handleNotFound);
        Node postToMissing   = decision(POST_TO_MISSING, createIsMethod("POST"), canPostToMissing, handleNotFound);
        Node handleMovedPermanently = handler(HANDLE_MOVED_PERMANENTLY, 301, null);
        Node handleMovedTemporarily = handler(HANDLE_MOVED_TEMPORARILY, 307, null);
        Node canPostToGone   = decision(CAN_POST_TO_GONE, post, handleGone);
        Node postToGone      = decision(POST_TO_GONE, canPostToGone, handleGone);
        Node movedTemporarily= decision(MOVED_TEMPORARILY, handleMovedTemporarily, postToGone);
        Node movedPermanently= decision(MOVED_PERMANENTLY, handleMovedPermanently, movedTemporarily);
        Node existed         = decision(EXISTED, movedPermanently, postToMissing);
        Node handleConflict  = handler(HANDLE_CONFLICT, 409, "Conflict.");
        Node patchEnacted    = decision(PATCH_ENACTED, isRespondWithEntity, handleAccepted);
        Node patch           = action(PATCH, patchEnacted);
        Node put             = action(PUT, isPutEnacted);
        Node methodPost      = decision(METHOD_POST, createIsMethod("POST"), post, put);
        Node conflict        = decision(CONFLICT, handleConflict, methodPost);
        Node handleNotImplemented = handler(HANDLE_NOT_IMPLEMENTED, 501, "Not implemented.");
        Node canPutToMissing = decision(CAN_PUT_TO_MISSING, conflict, handleNotImplemented);
        Node putToDifferentUrl = decision(PUT_TO_DIFFERENT_URL, handleMovedPermanently, canPutToMissing);
        Node methodPut       = decision(METHOD_PUT, createIsMethod("PUT"), putToDifferentUrl, existed);
        Node handlePreconditionFailed = handler(HANDLE_PRECONDITION_FAILED, 412, "Precondition failed.");
        Node ifMatchStarExistsForMissing = decision(IF_MATCH_STAR_EXISTS_FOR_MISSING,
            IF_MATCH_STAR_FUNC,
            handlePreconditionFailed,
            methodPut);
        Node handleNotModified = handler(HANDLE_NOT_MODIFIED, 304, null);
        Node ifNoneMatch     = decision(IF_NONE_MATCH,
            createIsMethod("HEAD", "GET"),
            handleNotModified,
            handlePreconditionFailed);
        Node putToExisting   = decision(PUT_TO_EXISTING,
            createIsMethod("PUT"),
            conflict,
            isMultipleRepresentations);
        Node postToExisting   = decision(POST_TO_EXISTING,
            createIsMethod("POST"),
            conflict,
            putToExisting);
        Node deleteEnacted    = decision(DELETE_ENACTED, isRespondWithEntity, handleAccepted);
        Node delete           = action(DELETE, deleteEnacted);
        Node methodPatch      = decision(METHOD_PATCH,
            createIsMethod("PATCH"), patch, postToExisting);
        Node methodDelete     = decision(METHOD_DELETE,
            createIsMethod("DELETE"), delete, methodPatch);
        Node modifiedSince    = decision(MODIFIED_SINCE,
            context -> {
                return null;
            },
            methodDelete,
            handleNotModified);
        Node ifModifiedSinceValidDate = decision(IF_MODIFIED_SINCE_VALID_DATE,
            context -> {
                return null;
            },
            modifiedSince,
            methodDelete);
        Node ifModifiedSinceExists = decision(IF_MODIFIED_SINCE_EXISTS,
            context -> null,
            ifModifiedSinceValidDate,
            methodDelete);

        Node etagMatchesForIfNone = decision(ETAG_MATCHES_FOR_IF_NONE,
            context -> {
                return null;
            },
            ifNoneMatch,
            ifModifiedSinceExists);

        Node ifNoneMatchStar = decision(IF_NONE_MATCH_STAR,
            context -> Objects.equals("*", context.getRequest().getHeaders().get("if-none-match")),
            ifNoneMatch,
            ifModifiedSinceExists);

        Node ifNoneMatchExists = decision(IF_NONE_MATCH_EXISTS,
            context -> context.getRequest().getHeaders().containsKey("if-none-match"),
            ifNoneMatchStar,
            ifModifiedSinceExists);

        Node unmodifiedSince = decision(UNMODIFIED_SINCE,
            context -> {
                return null;
            },
            handlePreconditionFailed,
            ifNoneMatchExists);

        Node ifUnmodifiedSinceValidDate = decision(IF_UNMODIFIED_SINCE_EXISTS,
            context -> null,
            unmodifiedSince,
            ifNoneMatchExists);

        Node ifUnmodifiedSinceExists = decision(IF_UNMODIFIED_SINCE_EXISTS,
            context -> context.getRequest().getHeaders().containsKey("if-unmodified-since"),
            ifUnmodifiedSinceValidDate,
            ifNoneMatchExists);

        Node etagMatchesForIfMatch = decision(ETAG_MATCHES_FOR_IF_MATCH,
            context -> null,
            ifUnmodifiedSinceValidDate,
            handlePreconditionFailed);

        Node ifMatchStar = decision(IF_MATCH_STAR,
            ifUnmodifiedSinceExists,
            etagMatchesForIfMatch);

        Node ifMatchExists = decision(IF_MATCH_EXISTS,
            context -> context.getRequest().getHeaders().containsKey("if-match"),
            ifMatchStar,
            ifUnmodifiedSinceExists);

        Node exists = decision(EXISTS, ifMatchExists, ifMatchStarExistsForMissing);
        Node handleUnprocessableEntity = handler(HANDLE_UNPROCESSABLE_ENTITY, 422, "Unprocessable entity.");
        Node processable = decision(PROCESSABLE, exists, handleUnprocessableEntity);
        Node handleNotAcceptable = handler(HANDLE_NOT_ACCEPTABLE, 406, "No acceptable resource available.");
        Node encodingAvailable = decision(ENCODING_AVAILABLE,
            context -> null,
            processable, handleNotAcceptable);

        Node acceptEncodingExists = decision(ACCEPT_ENCODING_EXISTS,
            context -> context.getRequest().getHeaders().containsKey("accept-encoding"),
            encodingAvailable, processable);

        Node charsetAvailable = decision(CHARSET_AVAILABLE,
            context -> null,
            acceptEncodingExists,
            handleNotAcceptable);

        Node acceptCharsetExists = decision(ACCEPT_CHARSET_EXISTS,
            context -> context.getRequest().getHeaders().containsKey("accept-charset"),
            charsetAvailable, acceptEncodingExists);
        Node languageAvailable = decision(LANGUAGE_AVAILABLE,
            context -> null,
            acceptCharsetExists,
            handleNotAcceptable);
        Node acceptLanguageExists = decision(ACCEPT_LANGUAGE_EXISTS,
            context -> context.getRequest().getHeaders().containsKey("accept-charset"),
            languageAvailable, acceptCharsetExists);
        Node mediaTypeAvailable = decision(MEDIA_TYPE_AVAILABLE,
            context -> null,
            acceptLanguageExists, handleNotAcceptable);
        Node acceptExists = decision(ACCEPT_EXISTS,
            context -> null,
            mediaTypeAvailable, acceptLanguageExists);

        Node handleOptions = handler(HANDLE_OPTIONS, 200, null);

        Node isOptions = decision(IS_OPTIONS,
            createIsMethod("OPTIONS"),
            handleOptions,
            acceptExists);

        Node handleRequestEntityTooLarge = handler(HANDLE_REQUEST_ENTITY_TOO_LARGE, 413, "Request entity too large.");
        Node validEntityLength = decision(VALID_ENTITY_LENGTH,
            isOptions, handleRequestEntityTooLarge);
        Node handleUnsupportedMediaType = handler(HANDLE_UNSUPPORTED_MEDIA_TYPE, 415, "Unsupported media type.");
        Node knownContentType = decision(KNOWN_CONTENT_TYPE, validEntityLength, handleUnsupportedMediaType);
        Node validContentHeader = decision(VALID_CONTENT_HEADER, knownContentType, handleNotImplemented);
        Node handleForbidden = handler(HANDLE_FORBIDDEN, 403, "Forbidden.");
        Node allowed = decision(ALLOWED, validContentHeader, handleForbidden);
        Node handleUnauthorized = handler(HANDLE_UNAUTHORIZED, 401, "Not authorized.");
        Node authorized = decision(AUTHORIZED, allowed, handleUnauthorized);
        Node handleMalformed = handler(HANDLE_MALFORMED, 400, "Bad request.");
        Node malformed = decision(MALFORMED, handleMalformed, authorized);

        Node handleMethodNotAllowed = handler(HANDLE_METHOD_NOT_ALLOWED, 405, "Method not allowed");
        Node methodAllowed = decision(METHOD_ALLOWED, null, malformed, handleMethodNotAllowed);

        Node handleUriTooLong = handler(HANDLE_URI_TOO_LONG, 414, "Request URI too long.");
        Node uriTooLong = decision(URI_TOO_LONG, handleUriTooLong, methodAllowed);

        Node handleUnknownMethod = handler(HANDLE_UNKNOWN_METHOD, 501, "Unknown method.");
        Node knownMethod = decision(KNOWN_METHOD, uriTooLong, handleUnknownMethod);

        Node handleServiceNotAvailable = handler(HANDLE_SERVICE_NOT_AVAILABLE, 503, "Service not available.");
        Node serviceAvailable = decision(SERVICE_AVAILABLE, knownMethod, handleServiceNotAvailable);

        Node<?> initializeContext = action(INITIALIZE_CONTEXT, serviceAvailable);

        return initializeContext;
    }
}
