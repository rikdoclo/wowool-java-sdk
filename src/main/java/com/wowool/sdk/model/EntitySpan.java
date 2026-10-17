/*
 * Copyright (c) 2026 Rik Doclo. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wowool.sdk.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents an extracted linguistic entity or semantic concept within a document.
 *
 * @param uri The concept URI/type identifier (e.g., "Person", "City", "Organization", "Date")
 * @param literal The raw textual representation in the document matching this concept
 * @param beginOffset Character start offset within the document
 * @param endOffset Character end offset within the document
 * @param attributes Extracted attributes (e.g. canonical value, gender, components)
 */
public record EntitySpan(
    String uri,
    String literal,
    int beginOffset,
    int endOffset,
    Map<String, Object> attributes
) implements Annotation {
    public EntitySpan {
        if (attributes == null) {
            attributes = Collections.emptyMap();
        } else {
            attributes = Collections.unmodifiableMap(attributes);
        }
    }

    /**
     * Surface text of this entity (alias for {@link #literal()}).
     *
     * @return Raw text of this entity
     */
    public String text() {
        return literal;
    }

    /**
     * Checks if this entity matches the specified URI (case-insensitive).
     *
     * @param expectedUri The URI to compare against
     * @return true if matching, false otherwise
     */
    public boolean is(String expectedUri) {
        return uri != null && uri.equalsIgnoreCase(expectedUri);
    }

    /**
     * Retrieves the canonical/normalized representation of this entity if present (from attribute "canonical"),
     * or falls back to the surface literal text.
     *
     * @return Normalized canonical representation or raw literal
     */
    public String canonical() {
        Object val = attributes.get("canonical");
        if (val instanceof List<?> list && !list.isEmpty()) {
            return String.valueOf(list.getFirst());
        }
        if (val != null) {
            return String.valueOf(val);
        }
        return literal;
    }

    /**
     * Checks if a specific attribute is present in this entity's attributes.
     *
     * @param key Attribute key name
     * @return true if present, false otherwise
     */
    public boolean hasAttribute(String key) {
        return attributes != null && attributes.containsKey(key);
    }

    /**
     * Retrieves the first string representation of an attribute, handling both scalar and array attributes.
     *
     * @param key Attribute key name
     * @return Optional containing the string value if present
     */
    public Optional<String> firstAttribute(String key) {
        Object val = attributes.get(key);
        if (val instanceof List<?> list && !list.isEmpty()) {
            return Optional.of(String.valueOf(list.getFirst()));
        }
        if (val != null) {
            return Optional.of(String.valueOf(val));
        }
        return Optional.empty();
    }

    /**
     * Retrieves an attribute as a list of strings, safely converting scalar values to a singleton list.
     *
     * @param key Attribute key name
     * @return Immutable list of string values, or empty list if not present
     */
    public List<String> getListAttribute(String key) {
        Object val = attributes.get(key);
        if (val instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        if (val != null) {
            return List.of(String.valueOf(val));
        }
        return Collections.emptyList();
    }

    /**
     * Retrieves an attribute by key, casting to the expected type.
     *
     * @param key Attribute key name
     * @param <T> Expected return type
     * @return Attribute value or null if not present
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * Returns the length in characters of this entity span.
     */
    public int length() {
        return endOffset - beginOffset;
    }
}
