package kotowari.restful;

import enkan.web.data.HttpRequest;
import enkan.web.data.SseEmitter;
import enkan.web.data.StreamingBody;
import enkan.web.util.ETagUtils;
import enkan.web.util.HttpDateFormat;
import enkan.exception.UnrecoverableException;
import jakarta.ws.rs.core.MediaType;
import kotowari.data.BodyDeserializable;
import kotowari.restful.data.ApiResponse;
import kotowari.restful.data.DefaultResource;
import kotowari.restful.data.PatchDocument;
import kotowari.restful.data.PreferDirectives;
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

import kotowari.restful.trace.TraceStore;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
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
 * <p>Post-graph response fixups applied by {@link #run(Resource, HttpRequest)}:
 * <ul>
 *   <li>405 and successful OPTIONS responses receive an {@code Allow} header
 *       (RFC 7231 §6.5.5, RFC 9110 §9.3.7).</li>
 *   <li>HEAD responses and 204/304 responses have their body cleared
 *       (RFC 7231 §4.3.2, RFC 7232 §4.1, RFC 9110 §§15.3.5, 15.4.5).</li>
 *   <li>304 responses have {@code Content-Length}, {@code Content-Range}, and
 *       {@code Trailer} headers removed (RFC 9110 §15.4.5).</li>
 *   <li>A {@code Vary} header is set when content negotiation headers
 *       ({@code Accept}, {@code Accept-Language}, {@code Accept-Charset},
 *       {@code Accept-Encoding}) are present in the request (RFC 7231 §7.1.4).</li>
 * </ul>
 *
 * @author kawasima
 */
public class ResourceEngine {
    private static final Logger LOG = LoggerFactory.getLogger(ResourceEngine.class);
    private boolean printStackTrace = false;
    private boolean tracingEnabled = false;
    private final TraceStore traceStore = new TraceStore();
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
        } catch(UnrecoverableException e) {
            throw e;
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
        RestContext context = new RestContext(wrapResource(resource), request);
        if (tracingEnabled) {
            context.enableTracing();
        }
        ApiResponse response = runDecisionGraph(context);
        int status = response.getStatus();
        boolean methodNotAllowed = status == 405;
        boolean successfulOptions = "OPTIONS".equalsIgnoreCase(request.getRequestMethod())
                && status >= 200 && status < 300;
        if (methodNotAllowed || successfulOptions) {
            response.getHeaders().put("Allow", allowHeaderValue(resource.getAllowedMethods()));
            // RFC 5789 §3.1: Accept-Patch SHOULD be emitted alongside Allow
            // whenever the resource supports PATCH so clients can discover
            // the accepted patch document formats.
            Set<MediaType> patchTypes = resource.getAcceptPatchMediaTypes();
            if (!patchTypes.isEmpty() && resource.getAllowedMethods().contains("PATCH")) {
                response.getHeaders().put("Accept-Patch", acceptPatchHeaderValue(patchTypes));
            }
        }
        Object body = response.getBody();
        boolean streaming = body instanceof StreamingBody || body instanceof SseEmitter;
        if ("HEAD".equalsIgnoreCase(request.getRequestMethod()) || status == 204 || status == 304) {
            response.setBody(null);
        }
        // RFC 9110 §15.4.5: 304 MUST NOT contain Content-Length, Content-Range, or Trailer.
        if (status == 304) {
            response.getHeaders().remove("Content-Length");
            response.getHeaders().remove("Content-Range");
            response.getHeaders().remove("Trailer");
        }
        // For streaming bodies, never pre-set Content-Length — the adapter
        // writes chunked transfer-encoding. SSE also needs a text/event-stream
        // Content-Type and cache-control: no-cache per the WHATWG HTML spec.
        if (streaming && response.getBody() != null) {
            response.getHeaders().remove("Content-Length");
            if (body instanceof SseEmitter) {
                response.getHeaders().putIfAbsent("Content-Type", "text/event-stream; charset=UTF-8");
                response.getHeaders().putIfAbsent("Cache-Control", "no-cache");
                response.getHeaders().putIfAbsent("Connection", "keep-alive");
            }
        }
        // RFC 7231 §7.1.4: set Vary when content negotiation headers are present.
        // Merge with any existing Vary value set by the resource; preserve "Vary: *".
        String existingVary = (String) response.getHeaders().get("Vary");
        if (!"*".equals(existingVary)) {
            Set<String> varyTokens = new LinkedHashSet<>();
            if (existingVary != null) {
                for (String token : existingVary.split(",")) {
                    varyTokens.add(token.strip());
                }
            }
            if (request.getHeaders().containsKey("accept"))          varyTokens.add("Accept");
            if (request.getHeaders().containsKey("accept-language")) varyTokens.add("Accept-Language");
            if (request.getHeaders().containsKey("accept-charset"))  varyTokens.add("Accept-Charset");
            if (request.getHeaders().containsKey("accept-encoding")) varyTokens.add("Accept-Encoding");
            // Prefer also shapes the representation, so add it to Vary.
            if (request.getHeaders().containsKey("prefer"))          varyTokens.add("Prefer");
            if (!varyTokens.isEmpty()) {
                response.getHeaders().remove("Vary");
                response.getHeaders().put("Vary", String.join(", ", varyTokens));
            }
        }
        // RFC 7240 §4.2 / §4.5: apply return=minimal / return=representation
        // to successful 2xx responses and echo Preference-Applied.
        PreferDirectives prefer = context.get(RestContext.PREFER_DIRECTIVES).orElse(null);
        if (prefer != null && status >= 200 && status < 300) {
            if (prefer.returnMinimal() && !streaming) {
                response.setBody(null);
            }
            prefer.toPreferenceApplied().ifPresent(applied ->
                    response.getHeaders().put("Preference-Applied", applied));
        }
        if (tracingEnabled) {
            String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            context.getTrace().ifPresent(t -> {
                t.setMethod(request.getRequestMethod());
                t.setUri(request.getUri());
                traceStore.put(traceId, t);
                LOG.info("Trace recorded: id={} method={} uri={}", traceId,
                        request.getRequestMethod(), request.getUri());
            });
        }
        return response;
    }

    /**
     * Enables or disables per-request decision graph tracing.
     *
     * <p>When enabled, every node visited during graph traversal is recorded in a
     * {@link RequestTrace} and stored in the {@link TraceStore} for later retrieval.
     * This is intended for development use only and should be disabled in production.
     *
     * @param tracingEnabled {@code true} to enable tracing
     */
    public void setTracingEnabled(boolean tracingEnabled) {
        this.tracingEnabled = tracingEnabled;
    }

    /**
     * Returns the {@link TraceStore} that accumulates per-request traces.
     *
     * @return the trace store
     */
    public TraceStore getTraceStore() {
        return traceStore;
    }

    /**
     * Wraps a resource so that decision functions for the points documented
     * below are decorated with engine-level behavior before being returned
     * to the graph. Each wrapper is implemented as a private helper method
     * (see {@code initializeContextWrapper}, {@code authorizedWrapper}, etc.)
     * so that this switch stays easy to scan.
     *
     * <ul>
     *   <li>{@code INITIALIZE_CONTEXT} — parses the {@code Prefer} header
     *       (RFC 7240) into {@link PreferDirectives} and stashes it on the
     *       context before delegating to the resource function.</li>
     *   <li>{@code AUTHORIZED} — when the resource function returns a
     *       {@link String}, it is used as the {@code WWW-Authenticate} header
     *       value and the result is changed to {@code false} (routes to 401),
     *       satisfying RFC 7235 §4.1.</li>
     *   <li>{@code MOVED_PERMANENTLY}, {@code MOVED_TEMPORARILY},
     *       {@code POST_REDIRECT} — when the resource function returns a
     *       {@link String} or {@link URI}, it is set as the {@code Location}
     *       header and the result is changed to {@code true} (routes to the
     *       redirect handler), satisfying RFC 7231 §6.4.</li>
     *   <li>{@code ETAG_MATCHES_FOR_IF_MATCH},
     *       {@code ETAG_MATCHES_FOR_IF_NONE} — when the resource function
     *       returns a {@link String} (an entity-tag), it is compared against
     *       the request's {@code If-Match} / {@code If-None-Match} header via
     *       {@link enkan.web.util.ETagUtils#matchesHeader(String, String, boolean)}
     *       (strong comparison for If-Match per RFC 9110 §13.1.1, weak
     *       comparison for If-None-Match per §13.1.2). The resource ETag is
     *       also stashed on the response as the {@code ETag} header so
     *       downstream {@code ConditionalMiddleware} can reuse it. Boolean
     *       returns bypass this transform and preserve backward
     *       compatibility.</li>
     *   <li>{@code KNOWN_CONTENT_TYPE} — for {@code PATCH} requests, enforces
     *       the resource's declared {@code Accept-Patch} registry
     *       (RFC 5789 §3.1) and stashes a typed {@link PatchDocument} on the
     *       context.</li>
     *   <li>{@code PATCH} — when the client sent
     *       {@code Prefer: handling=lenient} (RFC 7240 §4.4), catches
     *       {@link RuntimeException} and surfaces it as a 400 Problem. The
     *       exception message is only inlined into the response when
     *       {@link #setPrintStackTrace(boolean)} is enabled, to avoid
     *       leaking internal details in production.</li>
     * </ul>
     *
     * <p>{@link Resource#getAllowedMethods()} and
     * {@link Resource#getAcceptPatchMediaTypes()} are delegated to the
     * original resource so that {@code ClassResource} overrides are
     * preserved.
     *
     * @param resource the original resource
     * @return a wrapped resource with header-aware function overrides
     */
    private Resource wrapResource(Resource resource) {
        Set<MediaType> acceptPatchMediaTypes = resource.getAcceptPatchMediaTypes();
        boolean exposeExceptionMessages = this.printStackTrace;
        return new Resource() {
            @Override
            public Function<RestContext, ?> getFunction(DecisionPoint point) {
                Function<RestContext, ?> original = resource.getFunction(point);
                return switch (point) {
                    case INITIALIZE_CONTEXT -> initializeContextWrapper(original);
                    case AUTHORIZED -> authorizedWrapper(original);
                    case MOVED_PERMANENTLY, MOVED_TEMPORARILY, POST_REDIRECT ->
                            original == null ? null : redirectHandler(original);
                    case ETAG_MATCHES_FOR_IF_MATCH ->
                            original == null ? null : etagComparator(original, "if-match", false);
                    case ETAG_MATCHES_FOR_IF_NONE ->
                            original == null ? null : etagComparator(original, "if-none-match", true);
                    case KNOWN_CONTENT_TYPE ->
                            knownContentTypeWrapper(original, acceptPatchMediaTypes);
                    case PATCH -> patchWrapper(original, exposeExceptionMessages);
                    case TOO_MANY_REQUESTS -> tooManyRequestsWrapper(original);
                    default -> original;
                };
            }

            @Override
            public Set<String> getAllowedMethods() {
                return resource.getAllowedMethods();
            }

            @Override
            public Set<MediaType> getAcceptPatchMediaTypes() {
                return acceptPatchMediaTypes;
            }
        };
    }

    /**
     * Returns a wrapper for {@code INITIALIZE_CONTEXT} that always parses the
     * {@code Prefer} header into {@link PreferDirectives} and stashes the
     * result on the {@link RestContext}, then delegates to the original
     * resource function (or yields {@code true} when none is registered).
     */
    private static Function<RestContext, ?> initializeContextWrapper(Function<RestContext, ?> original) {
        return ctx -> {
            String preferHeader = ctx.getRequest().getHeaders().get("prefer");
            PreferDirectives directives = PreferDirectives.parse(preferHeader);
            if (directives != PreferDirectives.NONE) {
                ctx.put(RestContext.PREFER_DIRECTIVES, directives);
            }
            return original != null ? original.apply(ctx) : true;
        };
    }

    /**
     * Returns a wrapper for {@code TOO_MANY_REQUESTS} that unconditionally
     * returns {@code false} (i.e. "not rate-limited") for {@code OPTIONS}
     * requests, skipping the resource's rate-limit function entirely so that
     * CORS preflight and capability-discovery traffic is never throttled by
     * the default graph. For non-OPTIONS methods, the resource function runs
     * as written (or yields {@code false} when none is registered — the
     * same as the {@link DefaultResource} fallback).
     */
    private static Function<RestContext, ?> tooManyRequestsWrapper(Function<RestContext, ?> original) {
        return ctx -> {
            if ("OPTIONS".equalsIgnoreCase(ctx.getRequest().getRequestMethod())) {
                return false;
            }
            return original != null ? original.apply(ctx) : false;
        };
    }

    /**
     * Returns a wrapper for {@code AUTHORIZED} that converts a {@link String}
     * return value from the resource into a {@code WWW-Authenticate} header
     * and a {@code false} decision (RFC 7235 §4.1).
     */
    private static Function<RestContext, ?> authorizedWrapper(Function<RestContext, ?> original) {
        if (original == null) return null;
        return ctx -> {
            Object result = original.apply(ctx);
            if (result instanceof String challenge) {
                ctx.addHeader("WWW-Authenticate", challenge);
                return false;
            }
            return result;
        };
    }

    /**
     * Returns a wrapper for {@code KNOWN_CONTENT_TYPE} that enforces the
     * resource's {@code Accept-Patch} registry on {@code PATCH} requests
     * (RFC 5789 §3.1). Non-PATCH requests delegate directly to the original
     * function unchanged.
     */
    private static Function<RestContext, ?> knownContentTypeWrapper(
            Function<RestContext, ?> original,
            Set<MediaType> acceptPatchMediaTypes) {
        return ctx -> {
            String method = ctx.getRequest().getRequestMethod();
            boolean isPatch = "PATCH".equalsIgnoreCase(method);
            if (!isPatch || acceptPatchMediaTypes.isEmpty()) {
                return original != null ? original.apply(ctx) : true;
            }
            String contentType = ctx.getRequest().getContentType();
            if (contentType == null || contentType.isBlank()) {
                return false;
            }
            MediaType requestType = parseMediaType(contentType);
            if (requestType == null) {
                return false;
            }
            boolean accepted = acceptPatchMediaTypes.stream()
                    .anyMatch(mt -> isCompatibleType(mt, requestType));
            if (!accepted) {
                return false;
            }
            // Stash a tagged PatchDocument once the body has been
            // deserialized upstream so the PATCH action can dispatch without
            // re-parsing Content-Type.
            if (ctx.getRequest() instanceof BodyDeserializable bd) {
                Object body = bd.getDeserializedBody();
                if (body != null) {
                    ctx.put(RestContext.PATCH_DOCUMENT,
                            new PatchDocument(requestType, body));
                }
            }
            return original != null ? original.apply(ctx) : true;
        };
    }

    /**
     * Returns a wrapper for the {@code PATCH} action that honors
     * {@code Prefer: handling=lenient} (RFC 7240 §4.4).
     *
     * <p>When the client opts in with {@code handling=lenient}, a
     * {@link RuntimeException} thrown by the PATCH handler is caught and
     * converted into a 400 Problem. {@link MalformedBodyException} is
     * explicitly re-thrown so that the engine's existing 400 short-circuit
     * path in {@link #runDecisionGraph(RestContext)} handles it uniformly.
     *
     * <p>The exception message is only inlined into the {@code detail} field
     * when {@code exposeExceptionMessages} is true (i.e. the engine is
     * configured for development-mode error reporting via
     * {@link #setPrintStackTrace(boolean)}). In production mode the detail is
     * a fixed, non-revealing string to avoid leaking internal implementation
     * details — consistent with the existing {@code HANDLE_EXCEPTION} policy
     * and the Kotowari-Restful convention that {@link Problem} must not
     * expose exception internals.
     */
    private Function<RestContext, ?> patchWrapper(Function<RestContext, ?> original,
                                                  boolean exposeExceptionMessages) {
        Function<RestContext, ?> patch = original != null ? original : c -> true;
        return ctx -> {
            PreferDirectives prefer = ctx.get(RestContext.PREFER_DIRECTIVES).orElse(null);
            if (prefer == null || !prefer.handlingLenient()) {
                return patch.apply(ctx);
            }
            try {
                return patch.apply(ctx);
            } catch (MalformedBodyException e) {
                // Surface to the engine's MalformedBodyException catch so the
                // 400 short-circuit path produces the canonical response.
                throw e;
            } catch (RuntimeException e) {
                LOG.debug("Lenient PATCH handling caught exception", e);
                ctx.setException(e);
                String detail = exposeExceptionMessages
                        ? e.getMessage()
                        : "Request could not be processed leniently.";
                return Problem.builder()
                        .status(400)
                        .type(kotowari.restful.data.ProblemTypes.BAD_REQUEST)
                        .detail(detail)
                        .build();
            }
        };
    }

    /**
     * Wraps an ETag-returning resource function so that string returns are
     * compared against the request header via {@link ETagUtils#matchesHeader}.
     * Boolean and other non-string returns are passed through unchanged to
     * preserve backward compatibility.
     */
    private static Function<RestContext, ?> etagComparator(
            Function<RestContext, ?> original,
            String headerName,
            boolean weakComparison) {
        return ctx -> {
            Object result = original.apply(ctx);
            if (result instanceof String etag) {
                // Stash the resource-supplied ETag on the response so that a
                // downstream ConditionalMiddleware (or clients inspecting 412
                // responses) can see it.
                ctx.addHeader("ETag", etag);
                String headerValue = ctx.getRequest().getHeaders().get(headerName);
                return ETagUtils.matchesHeader(headerValue, etag, weakComparison);
            }
            return result;
        };
    }

    private static Function<RestContext, ?> redirectHandler(Function<RestContext, ?> original) {
        return ctx -> {
            Object result = original.apply(ctx);
            String location = switch (result) {
                case String s -> s;
                case URI uri -> uri.toString();
                default -> null;
            };
            if (location != null) {
                ctx.addHeader("Location", location);
                return true;
            }
            return result;
        };
    }

    private static String allowHeaderValue(Set<String> methods) {
        StringJoiner joiner = new StringJoiner(", ");
        methods.stream().sorted().forEach(joiner::add);
        return joiner.toString();
    }

    private static String acceptPatchHeaderValue(Set<MediaType> mediaTypes) {
        StringJoiner joiner = new StringJoiner(", ");
        mediaTypes.stream()
                .map(ResourceEngine::formatMediaType)
                .sorted()
                .forEach(joiner::add);
        return joiner.toString();
    }

    /**
     * Formats a {@link MediaType} without relying on
     * {@link MediaType#toString()}, which requires a {@code RuntimeDelegate}
     * implementation on the classpath. Kotowari-Restful aims to stay
     * pluggable at the JAX-RS runtime layer, so this helper renders only the
     * {@code type/subtype} portion — which is sufficient for
     * {@code Accept-Patch} and similar discovery headers.
     */
    private static String formatMediaType(MediaType mediaType) {
        return mediaType.getType() + "/" + mediaType.getSubtype();
    }

    /**
     * Parses a {@code Content-Type} header value into a {@link MediaType}
     * without delegating to {@link MediaType#valueOf(String)}, which requires
     * a JAX-RS {@code RuntimeDelegate} on the classpath. Only the type and
     * subtype are extracted; parameters (e.g. {@code ;charset=UTF-8}) are
     * dropped because Kotowari-Restful compares patch media types on
     * {@code type/subtype} alone.
     *
     * @param raw the raw Content-Type header value
     * @return the parsed media type, or {@code null} if the header is malformed
     */
    private static MediaType parseMediaType(String raw) {
        String value = raw.strip();
        int semi = value.indexOf(';');
        if (semi >= 0) {
            value = value.substring(0, semi).strip();
        }
        int slash = value.indexOf('/');
        if (slash <= 0 || slash == value.length() - 1) {
            return null;
        }
        String type = value.substring(0, slash).trim();
        String subtype = value.substring(slash + 1).trim();
        if (type.isEmpty() || subtype.isEmpty()) {
            return null;
        }
        return new MediaType(type, subtype);
    }

    /**
     * Returns {@code true} when the {@code requested} media type satisfies
     * the resource-advertised {@code accepted} media type on a
     * {@code type/subtype} basis.
     *
     * <p>Wildcards are asymmetric: {@code *} is honored in the
     * {@code accepted} (resource-advertised) position as a wildcard, but
     * <b>rejected</b> in the {@code requested} (request Content-Type)
     * position. A PATCH request with a wildcard {@code Content-Type}
     * (e.g. {@code &#42;/&#42;} or {@code application/&#42;}) is not a
     * valid patch format advertisement and must be treated as unsupported
     * to prevent accidental acceptance of bodies the server cannot actually
     * parse.
     *
     * <p>Avoids relying on {@link MediaType#isCompatible(MediaType)} which
     * internally touches {@code RuntimeDelegate} in some JAX-RS builds.
     */
    private static boolean isCompatibleType(MediaType accepted, MediaType requested) {
        // A wildcard on the request side is not a concrete Content-Type and
        // cannot satisfy a concrete Accept-Patch entry.
        if ("*".equals(requested.getType()) || "*".equals(requested.getSubtype())) {
            return false;
        }
        return typeMatches(accepted.getType(), requested.getType())
                && typeMatches(accepted.getSubtype(), requested.getSubtype());
    }

    /**
     * Compares one component of an {@code accepted} media type against the
     * same component of a {@code requested} media type. The {@code accepted}
     * side may carry a {@code *} wildcard; the {@code requested} side is
     * expected to be concrete (wildcards there are filtered upstream in
     * {@link #isCompatibleType(MediaType, MediaType)}).
     */
    private static boolean typeMatches(String accepted, String requested) {
        return "*".equals(accepted) || accepted.equalsIgnoreCase(requested);
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
            context -> {
                var parsed = HttpDateFormat.parse(context.getRequest().getHeaders().get("if-modified-since"));
                if (parsed.isPresent()) {
                    context.put(RestContext.IF_MODIFIED_SINCE_DATE, new kotowari.restful.data.HttpDate(parsed.get()));
                    return true;
                }
                return null;
            },
            modifiedSince,
            methodDelete);
        // RFC 9110 §13.1.3: If-Modified-Since is only applicable to GET and HEAD.
        Node<?> ifModifiedSinceExists = decision(IF_MODIFIED_SINCE_EXISTS,
            context -> {
                String method = context.getRequest().getRequestMethod();
                return ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method))
                        && context.getRequest().getHeaders().containsKey("if-modified-since");
            },
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
            context -> {
                var parsed = HttpDateFormat.parse(context.getRequest().getHeaders().get("if-unmodified-since"));
                if (parsed.isPresent()) {
                    context.put(RestContext.IF_UNMODIFIED_SINCE_DATE, new kotowari.restful.data.HttpDate(parsed.get()));
                    return true;
                }
                return null;
            },
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

        Node<?> handlePreconditionRequired = handler(HANDLE_PRECONDITION_REQUIRED, 428, "Precondition required.");
        // RFC 6585 §3: when the resource requires a precondition and the
        // request provides neither If-Match nor If-Unmodified-Since, reject
        // with 428. Only meaningful for state-changing methods.
        Node<?> preconditionRequired = decision(PRECONDITION_REQUIRED,
            handlePreconditionRequired,
            ifMatchExists);

        Node<?> exists = decision(EXISTS, preconditionRequired, doesIfMatchStarExistForMissing);
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

        Node<?> handleTooManyRequests = handler(HANDLE_TOO_MANY_REQUESTS, 429, "Too many requests.");
        // RFC 6585 §4: rate-limiting check. Resource functions that return
        // true trigger 429; false allows the request through. OPTIONS is
        // bypassed transparently by {@link #tooManyRequestsWrapper} before
        // the resource function is ever called, so CORS preflight and
        // capability-discovery traffic is never rate-limited by the default
        // graph.
        Node<?> tooManyRequests = decision(TOO_MANY_REQUESTS,
            handleTooManyRequests,
            knownMethod);

        Node<?> handleServiceNotAvailable = handler(HANDLE_SERVICE_NOT_AVAILABLE, 503, "Service not available.");
        Node<?> serviceAvailable = decision(SERVICE_AVAILABLE, tooManyRequests, handleServiceNotAvailable);

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
