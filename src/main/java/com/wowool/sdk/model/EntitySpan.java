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
import java.util.Map;

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
) {
    public EntitySpan {
        if (attributes == null) {
            attributes = Collections.emptyMap();
        } else {
            attributes = Collections.unmodifiableMap(attributes);
        }
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
