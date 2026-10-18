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
 * Represents a logical disjunction / choice between alternative patterns
 * {@code (alt1 | alt2)} in a Wowool rule.
 */
public final class ChoicePattern implements RulePattern {

    private final List<RulePattern> alternatives;

    private ChoicePattern(List<RulePattern> alternatives) {
        if (alternatives == null || alternatives.isEmpty()) {
            throw new IllegalArgumentException("ChoicePattern must have at least one alternative.");
        }
        this.alternatives = Collections.unmodifiableList(new ArrayList<>(alternatives));
    }

    /**
     * Creates a choice pattern matching any of the specified patterns.
     *
     * @param alternatives
     *            Alternative patterns
     * @return New ChoicePattern
     */
    public static ChoicePattern of(RulePattern... alternatives) {
        Objects.requireNonNull(alternatives, "alternatives must not be null");
        return new ChoicePattern(Arrays.asList(alternatives));
    }

    /**
     * Creates a choice pattern matching any of the specified patterns.
     *
     * @param alternatives
     *            List of alternative patterns
     * @return New ChoicePattern
     */
    public static ChoicePattern of(List<? extends RulePattern> alternatives) {
        Objects.requireNonNull(alternatives, "alternatives must not be null");
        return new ChoicePattern(new ArrayList<>(alternatives));
    }

    /**
     * Convenience method to create a choice pattern from a list of literal words.
     *
     * @param words
     *            Literal words to match as alternatives
     * @return New ChoicePattern
     */
    public static ChoicePattern ofLiterals(String... words) {
        Objects.requireNonNull(words, "words must not be null");
        List<RulePattern> patterns = Arrays.stream(words).map(TokenPattern::literal).collect(Collectors.toList());
        return new ChoicePattern(patterns);
    }

    /**
     * Returns an unmodifiable list of alternatives in this choice.
     */
    public List<RulePattern> alternatives() {
        return alternatives;
    }

    /**
     * Wraps this choice pattern into an optional pattern {@code (pattern)?}.
     */
    public OptionalPattern optional() {
        return OptionalPattern.of(this);
    }

    @Override
    public String toDsl() {
        return "(" + alternatives.stream().map(RulePattern::toDsl).collect(Collectors.joining(" | ")) + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChoicePattern that)) {
            return false;
        }
        return Objects.equals(alternatives, that.alternatives);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alternatives);
    }

    @Override
    public String toString() {
        return toDsl();
    }
}
