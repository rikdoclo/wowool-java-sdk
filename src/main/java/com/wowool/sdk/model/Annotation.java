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

/**
 * Universal base interface for all linguistic annotations, spans, tokens, and
 * entities produced by the Wowool NLP engine.
 * <p>
 * Implemented as a sealed interface permitting {@link TokenSpan},
 * {@link EntitySpan}, and {@link SentenceSpan}, enabling exhaustive pattern
 * matching in Java 22+.
 */
public sealed interface Annotation permits TokenSpan, EntitySpan, SentenceSpan {

    /**
     * Start character index (inclusive, 0-based) within the analyzed document.
     *
     * @return Begin character offset
     */
    int beginOffset();

    /**
     * End character index (exclusive, 0-based) within the analyzed document.
     *
     * @return End character offset
     */
    int endOffset();

    /**
     * Length in characters of this annotation span.
     *
     * @return Character length
     */
    default int length() {
        return endOffset() - beginOffset();
    }

    /**
     * Checks if this annotation is a {@link TokenSpan}.
     *
     * @return {@code true} if this is a token annotation
     */
    default boolean isToken() {
        return this instanceof TokenSpan;
    }

    /**
     * Checks if this annotation is an {@link EntitySpan}.
     *
     * @return {@code true} if this is an entity annotation
     */
    default boolean isEntity() {
        return this instanceof EntitySpan;
    }

    /**
     * Checks if this annotation is a {@link SentenceSpan}.
     *
     * @return {@code true} if this is a sentence annotation
     */
    default boolean isSentence() {
        return this instanceof SentenceSpan;
    }

    /**
     * Checks if this annotation completely encloses another annotation.
     *
     * @param other
     *            Another annotation to test
     * @return {@code true} if other is within this span
     */
    default boolean contains(Annotation other) {
        if (other == null) {
            return false;
        }
        return beginOffset() <= other.beginOffset() && endOffset() >= other.endOffset();
    }

    /**
     * Checks if a character offset falls within this annotation's range
     * [beginOffset, endOffset).
     *
     * @param offset
     *            Character offset
     * @return {@code true} if offset is within this annotation
     */
    default boolean containsOffset(int offset) {
        return offset >= beginOffset() && offset < endOffset();
    }

    /**
     * Checks if this annotation overlaps with another annotation.
     *
     * @param other
     *            Another annotation to test
     * @return {@code true} if there is an overlapping span
     */
    default boolean overlaps(Annotation other) {
        if (other == null) {
            return false;
        }
        return beginOffset() < other.endOffset() && other.beginOffset() < endOffset();
    }
}
