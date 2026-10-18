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

/**
 * Convenient static factory methods for constructing Wowool {@link RulePattern}
 * expressions.
 */
public final class Patterns {

    private Patterns() {
    }

    /**
     * Matches a literal token (e.g. {@code "apple"}).
     *
     * @param literal
     *            Literal text
     * @return New TokenPattern
     */
    public static TokenPattern literal(String literal) {
        return TokenPattern.literal(literal);
    }

    /**
     * Matches a dictionary base lemma or stem (e.g. {@code 'work'}).
     *
     * @param lemma
     *            Base lemma
     * @return New TokenPattern
     */
    public static TokenPattern lemma(String lemma) {
        return TokenPattern.lemma(lemma);
    }

    /**
     * Matches a Part-of-Speech tag or concept URI (e.g. {@code Person}).
     *
     * @param concept
     *            Concept name
     * @return New TokenPattern
     */
    public static TokenPattern concept(String concept) {
        return TokenPattern.concept(concept);
    }

    /**
     * Matches a Part-of-Speech tag (alias for {@link #concept(String)}).
     *
     * @param pos
     *            POS tag
     * @return New TokenPattern
     */
    public static TokenPattern pos(String pos) {
        return TokenPattern.pos(pos);
    }

    /**
     * Matches a linguistic property feature (e.g. {@code +noun}).
     *
     * @param property
     *            Property name
     * @return New TokenPattern
     */
    public static TokenPattern property(String property) {
        return TokenPattern.property(property);
    }

    /**
     * Wildcard gap matching any sequence of intervening tokens ({@code ..}).
     *
     * @return GapPattern instance
     */
    public static GapPattern gap() {
        return GapPattern.any();
    }

    /**
     * Matches an optional pattern ({@code (pattern)?}).
     *
     * @param pattern
     *            Inner pattern
     * @return New OptionalPattern
     */
    public static OptionalPattern optional(RulePattern pattern) {
        return OptionalPattern.of(pattern);
    }

    /**
     * Matches any of the specified alternative patterns ({@code (alt1 | alt2)}).
     *
     * @param alternatives
     *            Alternative patterns
     * @return New ChoicePattern
     */
    public static ChoicePattern choice(RulePattern... alternatives) {
        return ChoicePattern.of(alternatives);
    }

    /**
     * Matches any of the specified literal alternatives.
     *
     * @param words
     *            Literal words
     * @return New ChoicePattern
     */
    public static ChoicePattern choice(String... words) {
        return ChoicePattern.ofLiterals(words);
    }

    /**
     * Sequences multiple patterns in order.
     *
     * @param elements
     *            Pattern elements
     * @return New Sequence
     */
    public static Sequence sequence(RulePattern... elements) {
        return Sequence.of(elements);
    }

    /**
     * Sequences multiple literal words in order.
     *
     * @param words
     *            Literal words
     * @return New Sequence
     */
    public static Sequence sequence(String... words) {
        return Sequence.ofLiterals(words);
    }
}
