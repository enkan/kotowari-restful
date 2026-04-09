package kotowari.restful;

import enkan.util.MixinUtils;
import enkan.web.collection.Headers;
import enkan.web.data.DefaultHttpRequest;
import enkan.web.data.HttpRequest;
import enkan.web.data.SseEmitter;
import enkan.web.data.StreamingBody;
import jakarta.ws.rs.core.MediaType;
import kotowari.data.BodyDeserializable;
import kotowari.restful.data.ApiResponse;
import kotowari.restful.data.DefaultResource;
import kotowari.restful.data.PatchDocument;
import kotowari.restful.data.PreferDirectives;
import kotowari.restful.data.Problem;
import kotowari.restful.data.ProblemTypes;
import kotowari.restful.data.Resource;
import kotowari.restful.data.RestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import static enkan.util.BeanBuilder.builder;
import static kotowari.restful.DecisionPoint.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for features added in the Enkan 0.15.0 follow-up work:
 * <ul>
 *   <li>#1 ETag comparison via {@code ETagUtils}</li>
 *   <li>#3 {@code StreamingBody} / {@code SseEmitter} pass-through</li>
 *   <li>#4 {@code Accept-Patch} header (RFC 5789)</li>
 *   <li>#5 {@code Prefer} header (RFC 7240)</li>
 *   <li>#6 PATCH content-type branching (RFC 7396 / 6902)</li>
 *   <li>#7 {@code Problem.builder()} + {@code ProblemTypes}</li>
 *   <li>#8 429 / 428 first-class handlers (RFC 6585)</li>
 * </ul>
 */
class ResourceEngine015FeaturesTest {
    private ResourceEngine engine;

    @BeforeEach
    void setup() {
        engine = new ResourceEngine();
    }

    private static HttpRequest request(String method, Headers headers) {
        return builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, method)
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, headers)
                .build();
    }

    // --- #1: ETag delegation to ETagUtils -------------------------------

    @Test
    void etagForIfNoneMatchWeakMatchReturnsNotModified() {
        Resource resource = new DefaultResource() {
            @Override
            public Function<RestContext, ?> getFunction(DecisionPoint point) {
                if (point == ETAG_MATCHES_FOR_IF_NONE) {
                    return ctx -> "W/\"v1\"";
                }
                return super.getFunction(point);
            }
        };
        Headers h = Headers.empty();
        h.put("if-none-match", "W/\"v1\"");
        ApiResponse res = engine.run(resource, request("GET", h));
        assertThat(res.getStatus()).isEqualTo(304);
        assertThat(res.getHeaders().get("ETag")).isEqualTo("W/\"v1\"");
    }

    @Test
    void etagForIfNoneMatchNonMatchingReturnsOk() {
        Resource resource = new DefaultResource() {
            @Override
            public Function<RestContext, ?> getFunction(DecisionPoint point) {
                if (point == ETAG_MATCHES_FOR_IF_NONE) {
                    return ctx -> "W/\"v2\"";
                }
                return super.getFunction(point);
            }
        };
        Headers h = Headers.empty();
        h.put("if-none-match", "W/\"v1\"");
        ApiResponse res = engine.run(resource, request("GET", h));
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getHeaders().get("ETag")).isEqualTo("W/\"v2\"");
    }

    @Test
    void etagForIfMatchStrongMismatchReturnsPreconditionFailed() {
        Resource resource = allowingMethods(
                Set.of("GET", "HEAD", "PUT"),
                point -> point == ETAG_MATCHES_FOR_IF_MATCH
                        ? ctx -> "\"v2\""
                        : null);
        Headers h = Headers.empty();
        h.put("if-match", "\"v1\"");
        ApiResponse res = engine.run(resource, request("PUT", h));
        assertThat(res.getStatus()).isEqualTo(412);
    }

    /**
     * Builds a DefaultResource subclass that advertises the given allowed
     * methods via both {@link Resource#getAllowedMethods()} and
     * {@code METHOD_ALLOWED}, and returns the caller-supplied function for
     * any decision point where {@code overrides} yields non-null.
     */
    private static Resource allowingMethods(Set<String> allowed,
                                            Function<DecisionPoint, Function<RestContext, ?>> overrides) {
        return new DefaultResource() {
            @Override
            public Set<String> getAllowedMethods() {
                return allowed;
            }
            @Override
            public Function<RestContext, ?> getFunction(DecisionPoint point) {
                Function<RestContext, ?> override = overrides.apply(point);
                if (override != null) return override;
                if (point == METHOD_ALLOWED) {
                    return ctx -> allowed.contains(
                            ctx.getRequest().getRequestMethod().toUpperCase(Locale.US));
                }
                return super.getFunction(point);
            }
        };
    }

    // --- #3: StreamingBody / SseEmitter pass-through --------------------

    @Test
    void streamingBodyPreservesContentLengthStripAndBodyPassThrough() {
        StreamingBody stream = out -> out.write("chunk".getBytes());
        Resource resource = new DefaultResource() {
            @Override
            public Function<RestContext, ?> getFunction(DecisionPoint point) {
                if (point == HANDLE_OK) {
                    return ctx -> stream;
                }
                return super.getFunction(point);
            }
        };
        Headers h = Headers.empty();
        ApiResponse res = engine.run(resource, request("GET", h));
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getBody()).isSameAs(stream);
        assertThat(res.getHeaders().get("Content-Length")).isNull();
    }

    @Test
    void sseEmitterAddsEventStreamHeaders() {
        SseEmitter emitter = new SseEmitter();
        Resource resource = new DefaultResource() {
            @Override
            public Function<RestContext, ?> getFunction(DecisionPoint point) {
                if (point == HANDLE_OK) {
                    return ctx -> emitter;
                }
                return super.getFunction(point);
            }
        };
        ApiResponse res = engine.run(resource, request("GET", Headers.empty()));
        assertThat(res.getHeaders().get("Content-Type").toString())
                .contains("text/event-stream");
        assertThat(res.getHeaders().get("Cache-Control")).isEqualTo("no-cache");
        assertThat(res.getBody()).isSameAs(emitter);
    }

    // --- #4: Accept-Patch header on OPTIONS / 405 -----------------------

    @Test
    void acceptPatchHeaderEmittedOnOptionsForPatchResource() {
        Resource resource = new DefaultResource() {
            @Override
            public Set<String> getAllowedMethods() {
                return Set.of("GET", "HEAD", "OPTIONS", "PATCH");
            }
            @Override
            public Set<MediaType> getAcceptPatchMediaTypes() {
                return Set.of(PatchDocument.MERGE_PATCH_JSON, PatchDocument.JSON_PATCH_JSON);
            }
        };
        ApiResponse res = engine.run(resource, request("OPTIONS", Headers.empty()));
        String acceptPatch = (String) res.getHeaders().get("Accept-Patch");
        assertThat(acceptPatch).contains("application/merge-patch+json");
        assertThat(acceptPatch).contains("application/json-patch+json");
    }

    @Test
    void acceptPatchHeaderEmittedOnMethodNotAllowedForPatchResource() {
        Resource resource = new DefaultResource() {
            @Override
            public Set<String> getAllowedMethods() {
                return Set.of("GET", "HEAD", "PATCH");
            }
            @Override
            public Set<MediaType> getAcceptPatchMediaTypes() {
                return Set.of(PatchDocument.MERGE_PATCH_JSON);
            }
        };
        ApiResponse res = engine.run(resource, request("DELETE", Headers.empty()));
        assertThat(res.getStatus()).isEqualTo(405);
        assertThat((String) res.getHeaders().get("Accept-Patch"))
                .isEqualTo("application/merge-patch+json");
    }

    @Test
    void acceptPatchHeaderNotEmittedWhenPatchTypesEmpty() {
        Resource resource = new DefaultResource();
        ApiResponse res = engine.run(resource, request("OPTIONS", Headers.empty()));
        assertThat(res.getHeaders().get("Accept-Patch")).isNull();
    }

    // --- #5: Prefer header (RFC 7240) -----------------------------------

    @Test
    void preferReturnMinimalClearsResponseBody() {
        Resource resource = new DefaultResource();
        Headers h = Headers.empty();
        h.put("prefer", "return=minimal");
        ApiResponse res = engine.run(resource, request("GET", h));
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getBody()).isNull();
        assertThat((String) res.getHeaders().get("Preference-Applied"))
                .isEqualTo("return=minimal");
    }

    @Test
    void preferReturnRepresentationEchoesPreferenceApplied() {
        Resource resource = new DefaultResource();
        Headers h = Headers.empty();
        h.put("prefer", "return=representation");
        ApiResponse res = engine.run(resource, request("GET", h));
        assertThat((String) res.getHeaders().get("Preference-Applied"))
                .isEqualTo("return=representation");
        assertThat(res.getBody()).isNotNull();
    }

    @Test
    void preferAddsPreferToVaryHeader() {
        Resource resource = new DefaultResource();
        Headers h = Headers.empty();
        h.put("prefer", "return=minimal");
        h.put("accept", "application/json");
        ApiResponse res = engine.run(resource, request("GET", h));
        assertThat((String) res.getHeaders().get("Vary")).contains("Prefer");
    }

    @Test
    void preferMissingLeavesNoPreferenceApplied() {
        Resource resource = new DefaultResource();
        ApiResponse res = engine.run(resource, request("GET", Headers.empty()));
        assertThat(res.getHeaders().get("Preference-Applied")).isNull();
    }

    // --- #6: PATCH content-type branching (RFC 5789 §3.1 415) -----------

    private static Resource patchResource(Set<MediaType> acceptedPatch) {
        return new DefaultResource() {
            @Override
            public Set<String> getAllowedMethods() {
                return Set.of("GET", "HEAD", "PATCH");
            }
            @Override
            public Set<MediaType> getAcceptPatchMediaTypes() {
                return acceptedPatch;
            }
            @Override
            public Function<RestContext, ?> getFunction(DecisionPoint point) {
                if (point == METHOD_ALLOWED) {
                    return ctx -> getAllowedMethods().contains(
                            ctx.getRequest().getRequestMethod().toUpperCase(Locale.US));
                }
                return super.getFunction(point);
            }
        };
    }

    @Test
    void patchWithUnsupportedContentTypeReturns415() {
        Resource resource = patchResource(Set.of(PatchDocument.MERGE_PATCH_JSON));
        HttpRequest req = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "PATCH")
                .set(HttpRequest::setContentType, "application/xml")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();
        ApiResponse res = engine.run(resource, req);
        assertThat(res.getStatus()).isEqualTo(415);
    }

    @Test
    void patchWithAcceptedContentTypeFlowsThrough() {
        Resource resource = patchResource(Set.of(PatchDocument.MERGE_PATCH_JSON));
        HttpRequest req = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "PATCH")
                .set(HttpRequest::setContentType, "application/merge-patch+json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();
        ApiResponse res = engine.run(resource, req);
        assertThat(res.getStatus()).isNotEqualTo(415);
    }

    // --- #7: Problem.builder() + ProblemTypes ---------------------------

    @Test
    void problemBuilderProducesCustomTypeUri() {
        Problem p = Problem.builder()
                .status(429)
                .type(ProblemTypes.TOO_MANY_REQUESTS)
                .detail("rate limit exceeded")
                .build();
        assertThat(p.getStatus()).isEqualTo(429);
        assertThat(p.getType()).isEqualTo(ProblemTypes.TOO_MANY_REQUESTS);
        assertThat(p.getTitle()).isEqualTo("Too Many Requests");
        assertThat(p.getDetail()).isEqualTo("rate limit exceeded");
    }

    @Test
    void problemValueOfStillDefaultsToAboutBlank() {
        Problem p = Problem.valueOf(404);
        assertThat(p.getType().toString()).isEqualTo("about:blank");
        assertThat(p.getTitle()).isEqualTo("Not Found");
    }

    // --- #8: 429 / 428 first-class handlers (RFC 6585) ------------------

    @Test
    void tooManyRequestsDecisionReturns429() {
        Resource resource = new DefaultResource() {
            @Override
            public Function<RestContext, ?> getFunction(DecisionPoint point) {
                if (point == TOO_MANY_REQUESTS) {
                    return ctx -> {
                        ctx.addHeader("Retry-After", "30");
                        return true;
                    };
                }
                return super.getFunction(point);
            }
        };
        ApiResponse res = engine.run(resource, request("GET", Headers.empty()));
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat((String) res.getHeaders().get("Retry-After")).isEqualTo("30");
    }

    @Test
    void preconditionRequiredDecisionReturns428() {
        Resource resource = allowingMethods(
                Set.of("GET", "HEAD", "PUT"),
                point -> point == PRECONDITION_REQUIRED ? ctx -> true : null);
        ApiResponse res = engine.run(resource, request("PUT", Headers.empty()));
        assertThat(res.getStatus()).isEqualTo(428);
    }

    // --- PreferDirectives parsing ---------------------------------------

    @Test
    void preferDirectivesParseAllDirectives() {
        PreferDirectives d = PreferDirectives.parse("return=representation, handling=lenient, wait=10");
        assertThat(d.returnRepresentation()).isTrue();
        assertThat(d.handlingLenient()).isTrue();
        assertThat(d.waitSeconds()).isEqualTo(10L);
    }

    @Test
    void preferDirectivesEmptyHeaderYieldsNone() {
        assertThat(PreferDirectives.parse(null)).isSameAs(PreferDirectives.NONE);
        assertThat(PreferDirectives.parse("")).isSameAs(PreferDirectives.NONE);
        assertThat(PreferDirectives.parse("unknown")).isSameAs(PreferDirectives.NONE);
    }

    @Test
    void preferDirectivesIgnoresMalformedWait() {
        PreferDirectives d = PreferDirectives.parse("wait=xyz");
        assertThat(d).isSameAs(PreferDirectives.NONE);
    }

    @Test
    void preferDirectivesCaseInsensitive() {
        PreferDirectives d = PreferDirectives.parse("Return=Minimal");
        assertThat(d.returnMinimal()).isTrue();
    }

    @Test
    void preferDirectivesHonorsQuotedStringValue() {
        // RFC 7240 §2 allows quoted-string form; unwrap and compare
        // case-insensitively for token values.
        PreferDirectives d = PreferDirectives.parse("handling=\"Lenient\"");
        assertThat(d.handlingLenient()).isTrue();
        // Quoted wait value must still parse.
        PreferDirectives w = PreferDirectives.parse("wait=\"42\"");
        assertThat(w.waitSeconds()).isEqualTo(42L);
    }

    @Test
    void preferDirectivesHandlingStrictNotEchoedInPreferenceApplied() {
        // handling=strict is parsed but not honored by the engine, so it must
        // NOT appear in Preference-Applied per RFC 7240 §4.5 (don't claim
        // behavior that isn't conditional on the directive).
        PreferDirectives d = PreferDirectives.parse("handling=strict");
        assertThat(d.handlingStrict()).isTrue();
        assertThat(d.toPreferenceApplied()).isEmpty();
    }

    // --- #3 / #4 / #6: additional test-gap coverage ---------------------

    @Test
    void acceptPatchHeaderOrderIsDeterministic() {
        // Accept-Patch should be emitted in alphabetical order regardless of
        // the Set's iteration order so that caches and tests can rely on it.
        Resource resource = new DefaultResource() {
            @Override
            public Set<String> getAllowedMethods() {
                return Set.of("GET", "HEAD", "OPTIONS", "PATCH");
            }
            @Override
            public Set<MediaType> getAcceptPatchMediaTypes() {
                return Set.of(PatchDocument.MERGE_PATCH_JSON, PatchDocument.JSON_PATCH_JSON);
            }
            @Override
            public Function<RestContext, ?> getFunction(DecisionPoint point) {
                if (point == METHOD_ALLOWED) {
                    return ctx -> getAllowedMethods().contains(
                            ctx.getRequest().getRequestMethod().toUpperCase(Locale.US));
                }
                return super.getFunction(point);
            }
        };
        ApiResponse res = engine.run(resource, request("OPTIONS", Headers.empty()));
        assertThat((String) res.getHeaders().get("Accept-Patch"))
                .isEqualTo("application/json-patch+json, application/merge-patch+json");
    }

    @Test
    void patchWithAcceptedContentTypeStashesPatchDocument() {
        // When KNOWN_CONTENT_TYPE accepts the PATCH request, it should
        // stash a tagged PatchDocument on the RestContext pulled from the
        // BodyDeserializable mixin.
        Object[] captured = new Object[1];
        Resource resource = new DefaultResource() {
            @Override
            public Set<String> getAllowedMethods() {
                return Set.of("GET", "HEAD", "PATCH");
            }
            @Override
            public Set<MediaType> getAcceptPatchMediaTypes() {
                return Set.of(PatchDocument.MERGE_PATCH_JSON);
            }
            @Override
            public Function<RestContext, ?> getFunction(DecisionPoint point) {
                if (point == METHOD_ALLOWED) {
                    return ctx -> getAllowedMethods().contains(
                            ctx.getRequest().getRequestMethod().toUpperCase(Locale.US));
                }
                if (point == PATCH) {
                    return ctx -> {
                        captured[0] = ctx.get(RestContext.PATCH_DOCUMENT).orElse(null);
                        return true;
                    };
                }
                return super.getFunction(point);
            }
        };
        DefaultHttpRequest raw = new DefaultHttpRequest();
        raw.setRequestMethod("PATCH");
        raw.setContentType("application/merge-patch+json");
        raw.setHeaders(Headers.empty());
        HttpRequest req = MixinUtils.mixin(raw, BodyDeserializable.class);
        Object patchBody = java.util.Map.of("name", "Alice");
        ((BodyDeserializable) req).setDeserializedBody(patchBody);
        engine.run(resource, req);
        PatchDocument pd = (PatchDocument) captured[0];
        assertThat(pd).isNotNull();
        assertThat(pd.isMergePatch()).isTrue();
        assertThat(pd.body()).isSameAs(patchBody);
    }

    // --- #3: lenient PATCH behavioral tests -----------------------------

    @Test
    void lenientPatchConvertsRuntimeExceptionTo400() {
        // A PATCH handler that throws is normally caught by the engine's
        // exception handler and returned as 500. With Prefer: handling=lenient
        // the engine must surface the failure as a 400 Problem instead.
        Resource resource = new DefaultResource() {
            @Override
            public Set<String> getAllowedMethods() {
                return Set.of("GET", "HEAD", "PATCH");
            }
            @Override
            public Function<RestContext, ?> getFunction(DecisionPoint point) {
                if (point == METHOD_ALLOWED) {
                    return ctx -> getAllowedMethods().contains(
                            ctx.getRequest().getRequestMethod().toUpperCase(Locale.US));
                }
                if (point == PATCH) {
                    return ctx -> { throw new RuntimeException("totally broken"); };
                }
                return super.getFunction(point);
            }
        };
        Headers h = Headers.empty();
        h.put("prefer", "handling=lenient");
        ApiResponse res = engine.run(resource, request("PATCH", h));
        assertThat(res.getStatus()).isEqualTo(400);
        // Default engine is production-mode (printStackTrace=false) so the
        // exception message must NOT leak into the response body.
        Problem problem = (Problem) res.getBody();
        assertThat(problem).isNotNull();
        assertThat(problem.getDetail()).doesNotContain("totally broken");
    }

    @Test
    void lenientPatchExposesExceptionMessageWhenPrintStackTraceEnabled() {
        engine.setPrintStackTrace(true);
        Resource resource = new DefaultResource() {
            @Override
            public Set<String> getAllowedMethods() {
                return Set.of("GET", "HEAD", "PATCH");
            }
            @Override
            public Function<RestContext, ?> getFunction(DecisionPoint point) {
                if (point == METHOD_ALLOWED) {
                    return ctx -> getAllowedMethods().contains(
                            ctx.getRequest().getRequestMethod().toUpperCase(Locale.US));
                }
                if (point == PATCH) {
                    return ctx -> { throw new RuntimeException("explicit detail"); };
                }
                return super.getFunction(point);
            }
        };
        Headers h = Headers.empty();
        h.put("prefer", "handling=lenient");
        ApiResponse res = engine.run(resource, request("PATCH", h));
        assertThat(res.getStatus()).isEqualTo(400);
        Problem problem = (Problem) res.getBody();
        assertThat(problem.getDetail()).contains("explicit detail");
    }

    @Test
    void strictPatchRuntimeExceptionStillReaches500() {
        // Without Prefer: handling=lenient, the engine must route the
        // exception through HANDLE_EXCEPTION → 500 (existing behavior).
        Resource resource = new DefaultResource() {
            @Override
            public Set<String> getAllowedMethods() {
                return Set.of("GET", "HEAD", "PATCH");
            }
            @Override
            public Function<RestContext, ?> getFunction(DecisionPoint point) {
                if (point == METHOD_ALLOWED) {
                    return ctx -> getAllowedMethods().contains(
                            ctx.getRequest().getRequestMethod().toUpperCase(Locale.US));
                }
                if (point == PATCH) {
                    return ctx -> { throw new RuntimeException("still broken"); };
                }
                return super.getFunction(point);
            }
        };
        ApiResponse res = engine.run(resource, request("PATCH", Headers.empty()));
        assertThat(res.getStatus()).isEqualTo(500);
    }

    // --- #8: OPTIONS bypass of TOO_MANY_REQUESTS ------------------------

    @Test
    void optionsRequestBypassesTooManyRequestsDecision() {
        // Resources that always return true from TOO_MANY_REQUESTS must NOT
        // receive 429 on OPTIONS — the default graph skips rate-limiting
        // for OPTIONS so CORS preflight is never blocked.
        Resource resource = new DefaultResource() {
            @Override
            public Set<String> getAllowedMethods() {
                return Set.of("GET", "HEAD", "OPTIONS");
            }
            @Override
            public Function<RestContext, ?> getFunction(DecisionPoint point) {
                if (point == METHOD_ALLOWED) {
                    return ctx -> getAllowedMethods().contains(
                            ctx.getRequest().getRequestMethod().toUpperCase(Locale.US));
                }
                if (point == TOO_MANY_REQUESTS) {
                    return ctx -> true;
                }
                return super.getFunction(point);
            }
        };
        ApiResponse res = engine.run(resource, request("OPTIONS", Headers.empty()));
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void nonOptionsRequestStillHitsTooManyRequestsDecision() {
        Resource resource = new DefaultResource() {
            @Override
            public Function<RestContext, ?> getFunction(DecisionPoint point) {
                if (point == TOO_MANY_REQUESTS) {
                    return ctx -> true;
                }
                return super.getFunction(point);
            }
        };
        ApiResponse res = engine.run(resource, request("GET", Headers.empty()));
        assertThat(res.getStatus()).isEqualTo(429);
    }

    // --- #7: Problem.Builder violations immutability --------------------

    @Test
    void problemBuilderRejectsMissingStatus() {
        // build() must not silently accept the default int (0) which would
        // produce a nonsensical "status=0" Problem.
        try {
            Problem.builder().detail("no status set").build();
            org.junit.jupiter.api.Assertions.fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage()).contains("status");
        }
    }

    @Test
    void problemBuilderRejectsOutOfRangeStatus() {
        try {
            Problem.builder().status(999).build();
            org.junit.jupiter.api.Assertions.fail("expected IllegalStateException for 999");
        } catch (IllegalStateException expected) {
            // pass
        }
        try {
            Problem.builder().status(42).build();
            org.junit.jupiter.api.Assertions.fail("expected IllegalStateException for 42");
        } catch (IllegalStateException expected) {
            // pass
        }
    }

    @Test
    void patchWithWildcardContentTypeIsRejected() {
        // RFC 5789 §3.1: a wildcard Content-Type on a PATCH request is not
        // a concrete patch format and must be rejected with 415 even if the
        // resource advertises the matching concrete type.
        Resource resource = patchResource(Set.of(PatchDocument.MERGE_PATCH_JSON));
        HttpRequest starSlashStar = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "PATCH")
                .set(HttpRequest::setContentType, "*/*")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();
        assertThat(engine.run(resource, starSlashStar).getStatus()).isEqualTo(415);

        HttpRequest applicationStar = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "PATCH")
                .set(HttpRequest::setContentType, "application/*")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();
        assertThat(engine.run(resource, applicationStar).getStatus()).isEqualTo(415);
    }

    @Test
    void problemBuilderViolationsAreDefensivelyCopied() {
        java.util.ArrayList<Problem.Violation> mutable = new java.util.ArrayList<>();
        mutable.add(new Problem.Violation("name", "required"));
        Problem p = Problem.builder()
                .status(422)
                .type(ProblemTypes.UNPROCESSABLE_ENTITY)
                .violations(mutable)
                .build();
        // Later mutation of the caller's list must not leak into the Problem.
        mutable.add(new Problem.Violation("email", "bad format"));
        assertThat(p.getViolations()).hasSize(1);
        // The stored list must be unmodifiable.
        List<Problem.Violation> stored = p.getViolations();
        assertThat(stored).isInstanceOf(List.class);
        assertThat(stored).hasSize(1);
        try {
            stored.add(new Problem.Violation("x", "y"));
            org.junit.jupiter.api.Assertions.fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // pass
        }
    }
}
