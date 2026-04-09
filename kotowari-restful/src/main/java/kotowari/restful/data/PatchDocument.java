package kotowari.restful.data;

import jakarta.ws.rs.core.MediaType;

import java.io.Serial;
import java.io.Serializable;

/**
 * A PATCH request body tagged with its media type so resources can dispatch on
 * the patch format (RFC 7396 JSON Merge Patch, RFC 6902 JSON Patch, etc.)
 * without re-parsing the {@code Content-Type} header.
 *
 * <p>The {@link #body()} is the already-deserialized body object exactly as
 * {@link kotowari.data.BodyDeserializable#getDeserializedBody()} produced it:
 * typically a {@link java.util.Map} for {@code application/merge-patch+json}
 * or a {@link java.util.List} for {@code application/json-patch+json}.
 * It is up to the downstream patch applier to interpret the structure.
 *
 * <p>Two convenience media-type constants model the two most common JSON-based
 * patch formats. Other patch formats (e.g. XML Patch) may be represented by
 * {@link PatchDocument} instances carrying the appropriate {@link MediaType}.
 *
 * @param mediaType the parsed {@code Content-Type} of the patch document
 * @param body      the deserialized patch document
 */
public record PatchDocument(MediaType mediaType, Object body) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** {@code application/merge-patch+json} — RFC 7396. */
    public static final MediaType MERGE_PATCH_JSON =
            new MediaType("application", "merge-patch+json");

    /** {@code application/json-patch+json} — RFC 6902. */
    public static final MediaType JSON_PATCH_JSON =
            new MediaType("application", "json-patch+json");

    /**
     * Returns {@code true} if this patch is an RFC 7396 JSON Merge Patch.
     */
    public boolean isMergePatch() {
        return MERGE_PATCH_JSON.isCompatible(mediaType);
    }

    /**
     * Returns {@code true} if this patch is an RFC 6902 JSON Patch.
     */
    public boolean isJsonPatch() {
        return JSON_PATCH_JSON.isCompatible(mediaType);
    }
}
