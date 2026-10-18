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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Represents a segmented sentence with associated tokens and entities.
 *
 * @param beginOffset
 *            Start offset within the document
 * @param endOffset
 *            End offset within the document
 * @param text
 *            The exact sentence text
 * @param tokens
 *            Ordered list of tokens within this sentence
 * @param entities
 *            Extracted entities within this sentence
 */
public record SentenceSpan(int beginOffset, int endOffset, String text, List<TokenSpan> tokens,
        List<EntitySpan> entities) implements Annotation {
    public SentenceSpan {
        tokens = (tokens == null) ? Collections.emptyList() : Collections.unmodifiableList(tokens);
        entities = (entities == null) ? Collections.emptyList() : Collections.unmodifiableList(entities);
    }

    /**
     * Returns an unmodifiable list of all annotations (both tokens and entities)
     * within this sentence, sorted by character offset.
     *
     * @return All annotations within this sentence in document order
     */
    public List<Annotation> annotations() {
        List<Annotation> all = new ArrayList<>(tokens.size() + entities.size());
        all.addAll(tokens);
        all.addAll(entities);
        all.sort(Comparator.comparingInt(Annotation::beginOffset).thenComparingInt(Annotation::endOffset));
        return Collections.unmodifiableList(all);
    }

    /**
     * Filters all entities in this sentence matching a specific concept URI
     * (case-insensitive).
     *
     * @param uri
     *            Concept URI (e.g., "Person", "City")
     * @return Matching entities within this sentence
     */
    public List<EntitySpan> entities(String uri) {
        if (uri == null || uri.isBlank()) {
            return Collections.emptyList();
        }
        return entities.stream().filter(e -> e.is(uri)).toList();
    }

    /**
     * Checks if this sentence contains at least one entity matching the specified
     * concept URI.
     *
     * @param uri
     *            Concept URI
     * @return true if present, false otherwise
     */
    public boolean hasEntity(String uri) {
        return entities.stream().anyMatch(e -> e.is(uri));
    }

    /**
     * Number of tokens within this sentence.
     */
    public int tokenCount() {
        return tokens.size();
    }

    /**
     * Number of entities within this sentence.
     */
    public int entityCount() {
        return entities.size();
    }
}
