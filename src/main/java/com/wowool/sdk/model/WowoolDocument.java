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
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Result of a Wowool document analysis. Contains strongly typed models for
 * sentences, entities, and tokens, with high-performance convenience queries.
 *
 * @param text
 *            The original analyzed text
 * @param language
 *            The resolved language (e.g., "dutch", "english", "french")
 * @param sentences
 *            List of segmented sentences
 * @param entities
 *            List of all extracted entity concepts
 * @param tokens
 *            List of all tokens
 * @param rawJson
 *            The raw JSON output emitted by the native Wowool C++ engine
 */
public record WowoolDocument(String text, String language, List<SentenceSpan> sentences, List<EntitySpan> entities,
        List<TokenSpan> tokens, String rawJson) {
    public WowoolDocument {
        sentences = (sentences == null) ? Collections.emptyList() : Collections.unmodifiableList(sentences);
        entities = (entities == null) ? Collections.emptyList() : Collections.unmodifiableList(entities);
        tokens = (tokens == null) ? Collections.emptyList() : Collections.unmodifiableList(tokens);
    }

    /**
     * Filters all entities matching a specific concept URI (case-insensitive).
     * Examples: "Person", "City", "Organization", "Date", "EmploymentRelation".
     *
     * @param uri
     *            Concept URI
     * @return Immutable list of matching entities
     */
    public List<EntitySpan> entities(String uri) {
        if (uri == null || uri.isBlank()) {
            return Collections.emptyList();
        }
        return entities.stream().filter(e -> e.is(uri)).collect(Collectors.toUnmodifiableList());
    }

    /**
     * Finds the first entity matching the specified concept URI.
     *
     * @param uri
     *            Concept URI
     * @return Optional containing the first matching entity, or empty if none found
     */
    public Optional<EntitySpan> findFirst(String uri) {
        return entities.stream().filter(e -> e.is(uri)).findFirst();
    }

    /**
     * Checks if the document contains at least one occurrence of the specified
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

    /**
     * Returns all entities whose span is completely contained within the given
     * character offset range.
     *
     * @param beginOffset
     *            Start character index (inclusive)
     * @param endOffset
     *            End character index (inclusive)
     * @return Immutable list of matching entities
     */
    public List<EntitySpan> entitiesBetween(int beginOffset, int endOffset) {
        return entities.stream().filter(e -> e.beginOffset() >= beginOffset && e.endOffset() <= endOffset).toList();
    }

    /**
     * Returns all tokens completely covered by the given entity span.
     *
     * @param entity
     *            Target entity span
     * @return Immutable list of tokens covering this entity
     */
    public List<TokenSpan> tokensFor(EntitySpan entity) {
        return tokensFor((Annotation) entity);
    }

    /**
     * Returns all tokens completely covered by the given annotation span.
     *
     * @param span
     *            Target annotation span
     * @return Immutable list of tokens covering this annotation
     */
    public List<TokenSpan> tokensFor(Annotation span) {
        if (span == null) {
            return Collections.emptyList();
        }
        if (span instanceof TokenSpan ts) {
            return List.of(ts);
        }
        return tokens.stream().filter(t -> t.beginOffset() >= span.beginOffset() && t.endOffset() <= span.endOffset())
                .toList();
    }

    /**
     * Returns up to {@code count} tokens occurring immediately before the specified
     * span in document order.
     *
     * @param span
     *            The anchor span
     * @param count
     *            Maximum number of tokens to return
     * @return Immutable list of tokens immediately preceding the span
     */
    public List<TokenSpan> tokensBefore(Annotation span, int count) {
        if (span == null || count <= 0) {
            return Collections.emptyList();
        }
        int targetOffset = span.beginOffset();
        List<TokenSpan> matching = new ArrayList<>();
        for (TokenSpan t : tokens) {
            if (t.endOffset() <= targetOffset) {
                matching.add(t);
            } else {
                break;
            }
        }
        if (matching.size() <= count) {
            return Collections.unmodifiableList(matching);
        }
        return Collections.unmodifiableList(matching.subList(matching.size() - count, matching.size()));
    }

    /**
     * Returns up to {@code count} tokens occurring immediately after the specified
     * span in document order.
     *
     * @param span
     *            The anchor span
     * @param count
     *            Maximum number of tokens to return
     * @return Immutable list of tokens immediately following the span
     */
    public List<TokenSpan> tokensAfter(Annotation span, int count) {
        if (span == null || count <= 0) {
            return Collections.emptyList();
        }
        int targetOffset = span.endOffset();
        List<TokenSpan> matching = new ArrayList<>();
        for (TokenSpan t : tokens) {
            if (t.beginOffset() >= targetOffset) {
                matching.add(t);
                if (matching.size() == count) {
                    break;
                }
            }
        }
        return Collections.unmodifiableList(matching);
    }

    /**
     * Returns a token context window centered around {@code span}, containing up to
     * {@code margin} tokens before the span, the tokens covered by the span, and up
     * to {@code margin} tokens after.
     *
     * @param span
     *            Anchor annotation span
     * @param margin
     *            Number of contextual tokens on both sides
     * @return Combined list of tokens within the contextual window
     */
    public List<TokenSpan> window(Annotation span, int margin) {
        return window(span, margin, margin);
    }

    /**
     * Returns an asymmetric token context window containing up to
     * {@code beforeCount} tokens before the span, the tokens covered by the span,
     * and up to {@code afterCount} tokens after.
     *
     * @param span
     *            Anchor annotation span
     * @param beforeCount
     *            Number of contextual tokens before
     * @param afterCount
     *            Number of contextual tokens after
     * @return Combined list of tokens within the contextual window
     */
    public List<TokenSpan> window(Annotation span, int beforeCount, int afterCount) {
        if (span == null) {
            return Collections.emptyList();
        }
        List<TokenSpan> before = tokensBefore(span, beforeCount);
        List<TokenSpan> inside = tokensFor(span);
        List<TokenSpan> after = tokensAfter(span, afterCount);

        List<TokenSpan> result = new ArrayList<>(before.size() + inside.size() + after.size());
        result.addAll(before);
        result.addAll(inside);
        result.addAll(after);
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns all annotations (tokens and entities) that spatially overlap with the
     * specified span. Excludes the span itself if present in the document.
     *
     * @param span
     *            Anchor span to test for overlap
     * @return Immutable list of overlapping annotations
     */
    public List<Annotation> overlapping(Annotation span) {
        if (span == null) {
            return Collections.emptyList();
        }
        return annotations().stream().filter(a -> !a.equals(span) && a.overlaps(span)).toList();
    }

    /**
     * Returns all entities that spatially overlap with the specified span. Excludes
     * the span itself if present in the document.
     *
     * @param span
     *            Anchor span to test for overlap
     * @return Immutable list of overlapping entities
     */
    public List<EntitySpan> overlappingEntities(Annotation span) {
        if (span == null) {
            return Collections.emptyList();
        }
        return entities.stream().filter(e -> !e.equals(span) && e.overlaps(span)).toList();
    }

    /**
     * Returns all tokens that spatially overlap with the specified span. Excludes
     * the span itself if present in the document.
     *
     * @param span
     *            Anchor span to test for overlap
     * @return Immutable list of overlapping tokens
     */
    public List<TokenSpan> overlappingTokens(Annotation span) {
        if (span == null) {
            return Collections.emptyList();
        }
        return tokens.stream().filter(t -> !t.equals(span) && t.overlaps(span)).toList();
    }

    /**
     * Finds all tokens matching the given lemma (case-insensitive).
     *
     * @param lemma
     *            Dictionary base form
     * @return Immutable list of matching tokens
     */
    public List<TokenSpan> tokensByLemma(String lemma) {
        if (lemma == null || lemma.isBlank()) {
            return Collections.emptyList();
        }
        return tokens.stream().filter(t -> t.lemma() != null && t.lemma().equalsIgnoreCase(lemma)).toList();
    }

    /**
     * Finds all tokens matching the specified Part-of-Speech tag (case-insensitive
     * or prefix).
     *
     * @param pos
     *            Part-of-Speech tag (e.g., "Noun", "Verb", "V-Pres")
     * @return Immutable list of matching tokens
     */
    public List<TokenSpan> tokensByPos(String pos) {
        if (pos == null || pos.isBlank()) {
            return Collections.emptyList();
        }
        return tokens.stream()
                .filter(t -> t.pos() != null && (t.pos().equalsIgnoreCase(pos) || t.pos().startsWith(pos))).toList();
    }

    /**
     * Finds all tokens bearing the specified linguistic flag.
     *
     * @param flag
     *            Flag name (e.g. "init-cap")
     * @return Immutable list of matching tokens
     */
    public List<TokenSpan> tokensByFlag(String flag) {
        if (flag == null || flag.isBlank()) {
            return Collections.emptyList();
        }
        return tokens.stream().filter(t -> t.hasFlag(flag)).toList();
    }

    /**
     * Returns all token lemmas across the document in encounter order.
     *
     * @return Immutable list of lemmas
     */
    public List<String> lemmas() {
        return tokens.stream().map(TokenSpan::lemma).toList();
    }

    /**
     * Returns all token Part-of-Speech tags across the document in encounter order.
     *
     * @return Immutable list of POS tags
     */
    public List<String> posTags() {
        return tokens.stream().map(TokenSpan::pos).toList();
    }

    /**
     * Checks if the document contains at least one token with the given lemma.
     *
     * @param lemma
     *            Dictionary base form
     * @return true if present, false otherwise
     */
    public boolean hasLemma(String lemma) {
        if (lemma == null || lemma.isBlank()) {
            return false;
        }
        return tokens.stream().anyMatch(t -> t.lemma() != null && t.lemma().equalsIgnoreCase(lemma));
    }

    /**
     * Checks if the document contains at least one token with the given
     * Part-of-Speech tag.
     *
     * @param pos
     *            POS tag
     * @return true if present, false otherwise
     */
    public boolean hasPos(String pos) {
        if (pos == null || pos.isBlank()) {
            return false;
        }
        return tokens.stream()
                .anyMatch(t -> t.pos() != null && (t.pos().equalsIgnoreCase(pos) || t.pos().startsWith(pos)));
    }

    /**
     * Finds all tokens matching the given arbitrary predicate.
     *
     * @param predicate
     *            Filter condition
     * @return Immutable list of matching tokens
     */
    public List<TokenSpan> findTokens(Predicate<TokenSpan> predicate) {
        if (predicate == null) {
            return Collections.emptyList();
        }
        return tokens.stream().filter(predicate).toList();
    }

    /**
     * Returns an unmodifiable list of all annotations (both tokens and entities)
     * across the entire document, sorted in ascending document order by start
     * offset.
     *
     * @return All annotations in document order
     */
    public List<Annotation> annotations() {
        List<Annotation> all = new ArrayList<>(tokens.size() + entities.size());
        all.addAll(tokens);
        all.addAll(entities);
        all.sort(Comparator.comparingInt(Annotation::beginOffset).thenComparingInt(Annotation::endOffset));
        return Collections.unmodifiableList(all);
    }

    /**
     * Returns all annotations whose span is completely contained within the given
     * character offset range.
     *
     * @param beginOffset
     *            Start character index (inclusive)
     * @param endOffset
     *            End character index (inclusive)
     * @return Immutable list of matching annotations
     */
    public List<Annotation> annotationsBetween(int beginOffset, int endOffset) {
        return annotations().stream().filter(a -> a.beginOffset() >= beginOffset && a.endOffset() <= endOffset)
                .toList();
    }
}
