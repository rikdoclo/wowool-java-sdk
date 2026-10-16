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
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Result of a Wowool document analysis.
 * Contains strongly typed models for sentences, entities, and tokens, with high-performance convenience queries.
 *
 * @param text The original analyzed text
 * @param language The resolved language (e.g., "dutch", "english", "french")
 * @param sentences List of segmented sentences
 * @param entities List of all extracted entity concepts
 * @param tokens List of all tokens
 * @param rawJson The raw JSON output emitted by the native Wowool C++ engine
 */
public record WowoolDocument(
    String text,
    String language,
    List<SentenceSpan> sentences,
    List<EntitySpan> entities,
    List<TokenSpan> tokens,
    String rawJson
) {
    public WowoolDocument {
        sentences = (sentences == null) ? Collections.emptyList() : Collections.unmodifiableList(sentences);
        entities = (entities == null) ? Collections.emptyList() : Collections.unmodifiableList(entities);
        tokens = (tokens == null) ? Collections.emptyList() : Collections.unmodifiableList(tokens);
    }

    /**
     * Filters all entities matching a specific concept URI (case-insensitive).
     * Examples: "Person", "City", "Organization", "Date", "EmploymentRelation".
     *
     * @param uri Concept URI
     * @return Immutable list of matching entities
     */
    public List<EntitySpan> entities(String uri) {
        if (uri == null || uri.isBlank()) return Collections.emptyList();
        return entities.stream()
            .filter(e -> e.is(uri))
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Finds the first entity matching the specified concept URI.
     *
     * @param uri Concept URI
     * @return Optional containing the first matching entity, or empty if none found
     */
    public Optional<EntitySpan> findFirst(String uri) {
        return entities.stream()
            .filter(e -> e.is(uri))
            .findFirst();
    }

    /**
     * Checks if the document contains at least one occurrence of the specified concept URI.
     *
     * @param uri Concept URI
     * @return true if present, false otherwise
     */
    public boolean hasEntity(String uri) {
        return entities.stream().anyMatch(e -> e.is(uri));
    }

    /**
     * Total number of extracted entities.
     */
    public int entityCount() {
        return entities.size();
    }

    /**
     * Total number of segmented sentences.
     */
    public int sentenceCount() {
        return sentences.size();
    }

    /**
     * Total number of tokens.
     */
    public int tokenCount() {
        return tokens.size();
    }
}
