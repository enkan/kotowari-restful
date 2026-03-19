package kotowari.restful;

import enkan.collection.Headers;
import enkan.data.DefaultHttpRequest;
import enkan.data.HttpRequest;
import kotowari.restful.data.ApiResponse;
import kotowari.restful.data.DefaultResource;
import kotowari.restful.data.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import kotowari.restful.data.HttpDate;

import static enkan.util.BeanBuilder.builder;
import static org.assertj.core.api.Assertions.assertThat;

class ResourceEngineTest {
    private ResourceEngine resourceEngine;

    @BeforeEach
    void setup() {
        resourceEngine = new ResourceEngine();
    }

    @Test
    void http200Ok() {
        DefaultResource resource = new DefaultResource();
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        resourceEngine.run(resource, request);
    }

    @Test
    void httpPost() {
        DefaultResource resource = new DefaultResource();
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "POST")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        resourceEngine.run(resource, request);
    }

    @Test
    void methodNotAllowedReturnsAllowHeader() {
        // DefaultResource allows GET and HEAD only
        DefaultResource resource = new DefaultResource();
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "DELETE")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        ApiResponse response = resourceEngine.run(resource, request);

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeaders().get("Allow")).isEqualTo("GET, HEAD");
    }

    @Test
    void optionsReturnsAllowHeader() {
        // Resource that advertises GET, HEAD, POST and allows OPTIONS through METHOD_ALLOWED
        Resource resource = new DefaultResource() {
            @Override
            public Set<String> getAllowedMethods() {
                return Set.of("GET", "HEAD", "POST");
            }

            @Override
            public java.util.function.Function<kotowari.restful.data.RestContext, ?> getFunction(kotowari.restful.DecisionPoint point) {
                if (point == kotowari.restful.DecisionPoint.METHOD_ALLOWED) {
                    return DefaultResource.testRequestMethod("GET", "HEAD", "POST", "OPTIONS");
                }
                return super.getFunction(point);
            }
        };
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "OPTIONS")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        ApiResponse response = resourceEngine.run(resource, request);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaders().get("Allow")).isEqualTo("GET, HEAD, POST");
    }

    @Test
    void headResponseHasNoBody() {
        DefaultResource resource = new DefaultResource();
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "HEAD")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        ApiResponse response = resourceEngine.run(resource, request);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void notModifiedResponseHasNoBody() {
        // Resource that routes to 304 and also overrides HANDLE_NOT_MODIFIED to return a body,
        // proving that the post-graph fixup strips it even when the handler would emit one.
        DefaultResource resource = new DefaultResource() {
            @Override
            public java.util.function.Function<kotowari.restful.data.RestContext, ?> getFunction(kotowari.restful.DecisionPoint point) {
                if (point == kotowari.restful.DecisionPoint.IF_NONE_MATCH_EXISTS) {
                    return ctx -> true;
                }
                if (point == kotowari.restful.DecisionPoint.IF_NONE_MATCH_STAR) {
                    return ctx -> false;
                }
                if (point == kotowari.restful.DecisionPoint.ETAG_MATCHES_FOR_IF_NONE) {
                    return ctx -> true;
                }
                if (point == kotowari.restful.DecisionPoint.HANDLE_NOT_MODIFIED) {
                    return ctx -> "should be stripped";
                }
                return super.getFunction(point);
            }
        };
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.of("if-none-match", "\"abc\""))
                .build();

        ApiResponse response = resourceEngine.run(resource, request);

        assertThat(response.getStatus()).isEqualTo(304);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void notModifiedResponseStripsProhibitedHeaders() {
        // RFC 9110 §15.4.5: 304 MUST NOT contain Content-Length, Content-Range, or Trailer.
        DefaultResource resource = new DefaultResource() {
            @Override
            public java.util.function.Function<kotowari.restful.data.RestContext, ?> getFunction(kotowari.restful.DecisionPoint point) {
                if (point == kotowari.restful.DecisionPoint.IF_NONE_MATCH_EXISTS) {
                    return ctx -> true;
                }
                if (point == kotowari.restful.DecisionPoint.IF_NONE_MATCH_STAR) {
                    return ctx -> false;
                }
                if (point == kotowari.restful.DecisionPoint.ETAG_MATCHES_FOR_IF_NONE) {
                    return ctx -> true;
                }
                if (point == kotowari.restful.DecisionPoint.HANDLE_NOT_MODIFIED) {
                    return ctx -> {
                        ctx.addHeader("Content-Length", "42");
                        ctx.addHeader("Content-Range", "bytes 0-41/42");
                        ctx.addHeader("Trailer", "Expires");
                        ctx.addHeader("ETag", "\"abc\""); // allowed — should survive
                        return "body";
                    };
                }
                return super.getFunction(point);
            }
        };
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.of("if-none-match", "\"abc\""))
                .build();

        ApiResponse response = resourceEngine.run(resource, request);

        assertThat(response.getStatus()).isEqualTo(304);
        assertThat(response.getBody()).isNull();
        assertThat(response.getHeaders().get("Content-Length")).isNull();
        assertThat(response.getHeaders().get("Content-Range")).isNull();
        assertThat(response.getHeaders().get("Trailer")).isNull();
        assertThat(response.getHeaders().get("ETag")).isEqualTo("\"abc\"");
    }

    @Test
    void noContentResponseHasNoBody() {
        // Resource that returns 204 by responding with entity=false after DELETE.
        // HANDLE_NO_CONTENT is overridden to return a non-null body, proving the fixup strips it.
        DefaultResource resource = new DefaultResource() {
            @Override
            public java.util.function.Function<kotowari.restful.data.RestContext, ?> getFunction(kotowari.restful.DecisionPoint point) {
                if (point == kotowari.restful.DecisionPoint.METHOD_ALLOWED) {
                    return DefaultResource.testRequestMethod("GET", "HEAD", "DELETE");
                }
                if (point == kotowari.restful.DecisionPoint.HANDLE_NO_CONTENT) {
                    return ctx -> "should be stripped";
                }
                return super.getFunction(point);
            }
        };
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "DELETE")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        ApiResponse response = resourceEngine.run(resource, request);

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void unauthorizedSetsWwwAuthenticateHeader() {
        // AUTHORIZED returns a WWW-Authenticate challenge string → 401 with header
        DefaultResource resource = new DefaultResource() {
            @Override
            public java.util.function.Function<kotowari.restful.data.RestContext, ?> getFunction(kotowari.restful.DecisionPoint point) {
                if (point == kotowari.restful.DecisionPoint.AUTHORIZED) {
                    return ctx -> "Bearer realm=\"api\"";
                }
                return super.getFunction(point);
            }
        };
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        ApiResponse response = resourceEngine.run(resource, request);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeaders().get("WWW-Authenticate")).isEqualTo("Bearer realm=\"api\"");
    }

    @Test
    void movedPermanentlySetsLocationHeader() {
        // MOVED_PERMANENTLY returns a location string → 301 with Location header
        DefaultResource resource = new DefaultResource() {
            @Override
            public java.util.function.Function<kotowari.restful.data.RestContext, ?> getFunction(kotowari.restful.DecisionPoint point) {
                if (point == kotowari.restful.DecisionPoint.EXISTS) {
                    return ctx -> false;
                }
                if (point == kotowari.restful.DecisionPoint.EXISTED) {
                    return ctx -> true;
                }
                if (point == kotowari.restful.DecisionPoint.MOVED_PERMANENTLY) {
                    return ctx -> "/new-location";
                }
                return super.getFunction(point);
            }
        };
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        ApiResponse response = resourceEngine.run(resource, request);

        assertThat(response.getStatus()).isEqualTo(301);
        assertThat(response.getHeaders().get("Location")).isEqualTo("/new-location");
    }

    @Test
    void movedPermanentlyWithUriSetsLocationHeader() {
        // MOVED_PERMANENTLY returns a URI → 301 with Location header
        DefaultResource resource = new DefaultResource() {
            @Override
            public java.util.function.Function<kotowari.restful.data.RestContext, ?> getFunction(kotowari.restful.DecisionPoint point) {
                if (point == kotowari.restful.DecisionPoint.EXISTS) {
                    return ctx -> false;
                }
                if (point == kotowari.restful.DecisionPoint.EXISTED) {
                    return ctx -> true;
                }
                if (point == kotowari.restful.DecisionPoint.MOVED_PERMANENTLY) {
                    return ctx -> java.net.URI.create("/new-location");
                }
                return super.getFunction(point);
            }
        };
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        ApiResponse response = resourceEngine.run(resource, request);

        assertThat(response.getStatus()).isEqualTo(301);
        assertThat(response.getHeaders().get("Location")).isEqualTo("/new-location");
    }

    @Test
    void movedTemporarilySetsLocationHeader() {
        // MOVED_TEMPORARILY returns a location string → 307 with Location header
        DefaultResource resource = new DefaultResource() {
            @Override
            public java.util.function.Function<kotowari.restful.data.RestContext, ?> getFunction(kotowari.restful.DecisionPoint point) {
                if (point == kotowari.restful.DecisionPoint.EXISTS) {
                    return ctx -> false;
                }
                if (point == kotowari.restful.DecisionPoint.EXISTED) {
                    return ctx -> true;
                }
                if (point == kotowari.restful.DecisionPoint.MOVED_PERMANENTLY) {
                    return ctx -> false;
                }
                if (point == kotowari.restful.DecisionPoint.MOVED_TEMPORARILY) {
                    return ctx -> "/temp-location";
                }
                return super.getFunction(point);
            }
        };
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        ApiResponse response = resourceEngine.run(resource, request);

        assertThat(response.getStatus()).isEqualTo(307);
        assertThat(response.getHeaders().get("Location")).isEqualTo("/temp-location");
    }

    @Test
    void postRedirectSetsLocationHeader() {
        // POST_REDIRECT returns a location string → 303 See Other with Location header
        DefaultResource resource = new DefaultResource() {
            @Override
            public Set<String> getAllowedMethods() {
                return Set.of("GET", "HEAD", "POST");
            }

            @Override
            public java.util.function.Function<kotowari.restful.data.RestContext, ?> getFunction(kotowari.restful.DecisionPoint point) {
                if (point == kotowari.restful.DecisionPoint.METHOD_ALLOWED) {
                    return DefaultResource.testRequestMethod("GET", "HEAD", "POST");
                }
                if (point == kotowari.restful.DecisionPoint.POST_REDIRECT) {
                    return ctx -> "/created-resource";
                }
                return super.getFunction(point);
            }
        };
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "POST")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        ApiResponse response = resourceEngine.run(resource, request);

        assertThat(response.getStatus()).isEqualTo(303);
        assertThat(response.getHeaders().get("Location")).isEqualTo("/created-resource");
    }

    // ── If-Modified-Since tests ───────────────────────────────────────────

    @Test
    void ifModifiedSinceValidDate_notModified_returns304() {
        HttpDate[] captured = new HttpDate[1];
        DefaultResource resource = new DefaultResource() {
            @Override
            public java.util.function.Function<kotowari.restful.data.RestContext, ?> getFunction(DecisionPoint point) {
                if (point == DecisionPoint.MODIFIED_SINCE) {
                    return ctx -> {
                        captured[0] = ctx.get(kotowari.restful.data.RestContext.IF_MODIFIED_SINCE_DATE).orElse(null);
                        return false; // resource NOT modified since that date → 304
                    };
                }
                return super.getFunction(point);
            }
        };
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.of("if-modified-since", "Sun, 06 Nov 1994 08:49:37 GMT"))
                .build();

        ApiResponse response = resourceEngine.run(resource, request);

        assertThat(response.getStatus()).isEqualTo(304);
        assertThat(captured[0]).isNotNull();
        assertThat(captured[0].value()).isEqualTo(Instant.parse("1994-11-06T08:49:37Z"));
    }

    @Test
    void ifModifiedSinceValidDate_modified_returns200() {
        DefaultResource resource = new DefaultResource() {
            @Override
            public java.util.function.Function<kotowari.restful.data.RestContext, ?> getFunction(DecisionPoint point) {
                if (point == DecisionPoint.MODIFIED_SINCE) {
                    return ctx -> true; // resource IS modified → proceed normally
                }
                return super.getFunction(point);
            }
        };
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.of("if-modified-since", "Sun, 06 Nov 1994 08:49:37 GMT"))
                .build();

        ApiResponse response = resourceEngine.run(resource, request);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void ifModifiedSince_ignoredForPost() {
        // RFC 9110 §13.1.3: If-Modified-Since must be ignored for non-GET/HEAD methods.
        DefaultResource resource = new DefaultResource() {
            @Override
            public java.util.function.Function<kotowari.restful.data.RestContext, ?> getFunction(DecisionPoint point) {
                if (point == DecisionPoint.METHOD_ALLOWED) {
                    return DefaultResource.testRequestMethod("GET", "HEAD", "POST");
                }
                if (point == DecisionPoint.MODIFIED_SINCE) {
                    return ctx -> false; // would return 304 if reached
                }
                return super.getFunction(point);
            }
        };
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "POST")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.of("if-modified-since", "Sun, 06 Nov 1994 08:49:37 GMT"))
                .build();

        ApiResponse response = resourceEngine.run(resource, request);

        // POST should not trigger 304 even with a valid If-Modified-Since header
        assertThat(response.getStatus()).isNotEqualTo(304);
    }

    @Test
    void ifModifiedSinceInvalidDate_skipsValidation_returns200() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.of("if-modified-since", "not-a-date"))
                .build();

        ApiResponse response = resourceEngine.run(new DefaultResource(), request);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ── If-Unmodified-Since tests ─────────────────────────────────────────

    @Test
    void ifUnmodifiedSinceValidDate_modified_returns412() {
        HttpDate[] captured = new HttpDate[1];
        DefaultResource resource = new DefaultResource() {
            @Override
            public java.util.function.Function<kotowari.restful.data.RestContext, ?> getFunction(DecisionPoint point) {
                if (point == DecisionPoint.UNMODIFIED_SINCE) {
                    return ctx -> {
                        captured[0] = ctx.get(kotowari.restful.data.RestContext.IF_UNMODIFIED_SINCE_DATE).orElse(null);
                        return true; // resource WAS modified since that date → precondition failed
                    };
                }
                return super.getFunction(point);
            }
        };
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.of("if-unmodified-since", "Sun, 06 Nov 1994 08:49:37 GMT"))
                .build();

        ApiResponse response = resourceEngine.run(resource, request);

        assertThat(response.getStatus()).isEqualTo(412);
        assertThat(captured[0]).isNotNull();
        assertThat(captured[0].value()).isEqualTo(Instant.parse("1994-11-06T08:49:37Z"));
    }

    @Test
    void ifUnmodifiedSinceInvalidDate_skipsValidation_returns200() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.of("if-unmodified-since", "garbage"))
                .build();

        ApiResponse response = resourceEngine.run(new DefaultResource(), request);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    // ── Vary header tests ─────────────────────────────────────────────────

    @Test
    void varyHeaderSetWhenAcceptPresent() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.of("accept", "application/json"))
                .build();

        ApiResponse response = resourceEngine.run(new DefaultResource(), request);

        assertThat(response.getHeaders().get("Vary")).isEqualTo("Accept");
    }

    @Test
    void varyHeaderIncludesMultipleNegotiationHeaders() {
        Headers headers = Headers.of("accept", "application/json",
                "accept-language", "en");
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, headers)
                .build();

        ApiResponse response = resourceEngine.run(new DefaultResource(), request);

        assertThat(response.getHeaders().get("Vary")).isEqualTo("Accept, Accept-Language");
    }

    @Test
    void varyHeaderAbsentWhenNoNegotiationHeaders() {
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.empty())
                .build();

        ApiResponse response = resourceEngine.run(new DefaultResource(), request);

        assertThat(response.getHeaders().get("Vary")).isNull();
    }

    @Test
    void varyHeaderMergesWithExistingValue() {
        // Resource that pre-sets Vary: Origin
        DefaultResource resource = new DefaultResource() {
            @Override
            public java.util.function.Function<kotowari.restful.data.RestContext, ?> getFunction(DecisionPoint point) {
                if (point == DecisionPoint.HANDLE_OK) {
                    return ctx -> {
                        ctx.addHeader("Vary", "Origin");
                        return "ok";
                    };
                }
                return super.getFunction(point);
            }
        };
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.of("accept", "application/json"))
                .build();

        ApiResponse response = resourceEngine.run(resource, request);

        assertThat(response.getHeaders().get("Vary")).isEqualTo("Origin, Accept");
    }

    @Test
    void varyStarIsPreserved() {
        // Resource that pre-sets Vary: *
        DefaultResource resource = new DefaultResource() {
            @Override
            public java.util.function.Function<kotowari.restful.data.RestContext, ?> getFunction(DecisionPoint point) {
                if (point == DecisionPoint.HANDLE_OK) {
                    return ctx -> {
                        ctx.addHeader("Vary", "*");
                        return "ok";
                    };
                }
                return super.getFunction(point);
            }
        };
        HttpRequest request = builder(new DefaultHttpRequest())
                .set(HttpRequest::setRequestMethod, "GET")
                .set(HttpRequest::setContentType, "application/json")
                .set(HttpRequest::setHeaders, Headers.of("accept", "application/json"))
                .build();

        ApiResponse response = resourceEngine.run(resource, request);

        // Vary: * must not be overwritten
        assertThat(response.getHeaders().get("Vary")).isEqualTo("*");
    }
}
