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

package com.wowool.sdk.rule;

import java.util.Objects;

/**
 * Represents an individual token match condition in a Wowool rule. Supported
 * token matchers include literal words ({@code "word"}), lemmas/stems
 * ({@code 'stem'}), properties ({@code +prop}), and syntactic/semantic concepts
 * or POS tags ({@code Person}).
 */
public final class TokenPattern implements RulePattern {

    /**
     * Type of token matcher.
     */
    public enum Type {
        LITERAL, LEMMA, PROPERTY, CONCEPT
    }

    private final Type type;
    private final String value;

    private TokenPattern(Type type, String value) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.value = Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Token pattern value must not be blank.");
        }
    }

    /**
     * Matches an exact surface literal string (e.g. {@code "werkt"}).
     *
     * @param literal
     *            Literal text to match
     * @return New TokenPattern
     */
    public static TokenPattern literal(String literal) {
        return new TokenPattern(Type.LITERAL, literal);
    }

    /**
     * Matches a dictionary base lemma or stem (e.g. {@code 'work'}).
     *
     * @param lemma
     *            Lemma to match
     * @return New TokenPattern
     */
    public static TokenPattern lemma(String lemma) {
        return new TokenPattern(Type.LEMMA, lemma);
    }

    /**
     * Matches a linguistic property or feature flag (e.g. {@code +noun}).
     *
     * @param property
     *            Property name without leading '+'
     * @return New TokenPattern
     */
    public static TokenPattern property(String property) {
        String clean = property.startsWith("+") ? property.substring(1) : property;
        return new TokenPattern(Type.PROPERTY, clean);
    }

    /**
     * Matches an upstream semantic concept or Part-of-Speech tag (e.g.
     * {@code Person}).
     *
     * @param concept
     *            Concept URI or POS tag name
     * @return New TokenPattern
     */
    public static TokenPattern concept(String concept) {
        return new TokenPattern(Type.CONCEPT, concept);
    }

    /**
     * Alias for {@link #concept(String)} to match a Part-of-Speech tag.
     *
     * @param pos
     *            POS tag name
     * @return New TokenPattern
     */
    public static TokenPattern pos(String pos) {
        return concept(pos);
    }

    /**
     * Wraps this token pattern into an optional pattern {@code (pattern)?}.
     *
     * @return OptionalPattern wrapping this token
     */
    public OptionalPattern optional() {
        return OptionalPattern.of(this);
    }

    /**
     * Returns the type of this token matcher.
     */
    public Type type() {
        return type;
    }

    /**
     * Returns the raw value for this matcher.
     */
    public String value() {
        return value;
    }

    @Override
    public String toDsl() {
        return switch (type) {
            case LITERAL -> "\"" + escapeString(value) + "\"";
            case LEMMA -> "'" + escapeString(value) + "'";
            case PROPERTY -> "+" + value;
            case CONCEPT -> value;
        };
    }

    private static String escapeString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TokenPattern that)) {
            return false;
        }
        return type == that.type && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public String toString() {
        return toDsl();
    }
}
