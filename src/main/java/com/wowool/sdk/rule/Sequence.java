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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents an ordered sequence of matching patterns evaluated sequentially in
 * a Wowool rule.
 */
public final class Sequence implements RulePattern {

    private final List<RulePattern> elements;

    private Sequence(List<RulePattern> elements) {
        if (elements == null || elements.isEmpty()) {
            throw new IllegalArgumentException("Sequence must contain at least one pattern element.");
        }
        this.elements = Collections.unmodifiableList(new ArrayList<>(elements));
    }

    /**
     * Creates a sequence from an array of pattern elements.
     *
     * @param elements
     *            Ordered pattern elements
     * @return New Sequence
     */
    public static Sequence of(RulePattern... elements) {
        Objects.requireNonNull(elements, "elements must not be null");
        return new Sequence(Arrays.asList(elements));
    }

    /**
     * Creates a sequence from a list of pattern elements.
     *
     * @param elements
     *            Ordered pattern elements
     * @return New Sequence
     */
    public static Sequence of(List<? extends RulePattern> elements) {
        Objects.requireNonNull(elements, "elements must not be null");
        return new Sequence(new ArrayList<>(elements));
    }

    /**
     * Convenience method to create a sequence from string literals.
     *
     * @param first
     *            The first literal word
     * @param rest
     *            Additional literal words
     * @return New Sequence
     */
    public static Sequence of(String first, String... rest) {
        Objects.requireNonNull(first, "first literal must not be null");
        List<RulePattern> list = new ArrayList<>();
        list.add(TokenPattern.literal(first));
        if (rest != null) {
            for (String s : rest) {
                list.add(TokenPattern.literal(s));
            }
        }
        return new Sequence(list);
    }

    /**
     * Creates a sequence from string literals.
     *
     * @param words
     *            Literal words
     * @return New Sequence
     */
    public static Sequence ofLiterals(String... words) {
        Objects.requireNonNull(words, "words must not be null");
        List<RulePattern> list = Arrays.stream(words).map(TokenPattern::literal).collect(Collectors.toList());
        return new Sequence(list);
    }

    /**
     * Returns an unmodifiable list of elements in this sequence.
     */
    public List<RulePattern> elements() {
        return elements;
    }

    /**
     * Number of elements in this sequence.
     */
    public int size() {
        return elements.size();
    }

    @Override
    public String toDsl() {
        return elements.stream().map(RulePattern::toDsl).collect(Collectors.joining(" "));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Sequence sequence)) {
            return false;
        }
        return Objects.equals(elements, sequence.elements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(elements);
    }

    @Override
    public String toString() {
        return toDsl();
    }
}
