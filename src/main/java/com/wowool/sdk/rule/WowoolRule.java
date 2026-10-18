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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Type-safe, compile-time checked representation of a Wowool extraction rule.
 * <p>
 * Example:
 *
 * <pre>{@code
 * WowoolRule rule = WowoolRule.named("ExtractEmployment")
 * 		.matching(Sequence.of(Patterns.concept("Person"), Patterns.gap(), Patterns.choice("employed", "works"),
 * 				Patterns.literal("at"), Patterns.gap(), Patterns.concept("Organization")))
 * 		.yields("EmploymentRelation");
 * }</pre>
 */
public final class WowoolRule {

    private final String name;
    private final RulePattern pattern;
    private final String targetConcept;
    private final Map<String, String> attributes;

    private WowoolRule(String name, RulePattern pattern, String targetConcept, Map<String, String> attributes) {
        this.name = name;
        this.pattern = Objects.requireNonNull(pattern, "pattern must not be null");
        this.targetConcept = Objects.requireNonNull(targetConcept, "targetConcept must not be null");
        if (targetConcept.isBlank()) {
            throw new IllegalArgumentException("targetConcept must not be blank.");
        }
        this.attributes = (attributes == null) ? Collections.emptyMap() : Collections.unmodifiableMap(attributes);
    }

    /**
     * Starts building a named Wowool rule.
     *
     * @param name
     *            Descriptive name or identifier for the rule
     * @return A builder for the rule
     */
    public static RuleBuilder named(String name) {
        return new RuleBuilder(name);
    }

    /**
     * Starts building an unnamed Wowool rule.
     *
     * @return A builder for the rule
     */
    public static RuleBuilder unnamed() {
        return new RuleBuilder(null);
    }

    /**
     * Starts building an unnamed Wowool rule (alias for {@link #unnamed()}).
     *
     * @return A builder for the rule
     */
    public static RuleBuilder rule() {
        return unnamed();
    }

    /**
     * Starts building a Wowool rule with an optional name.
     *
     * @param name
     *            Name of the rule or null for unnamed
     * @return A builder for the rule
     */
    public static RuleBuilder rule(String name) {
        return new RuleBuilder(name);
    }

    /**
     * Returns the optional human-readable name of this rule.
     */
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    /**
     * Returns the matching pattern.
     */
    public RulePattern pattern() {
        return pattern;
    }

    /**
     * Returns the target concept URI produced when this rule matches.
     */
    public String targetConcept() {
        return targetConcept;
    }

    /**
     * Returns optional yield attributes.
     */
    public Map<String, String> attributes() {
        return attributes;
    }

    /**
     * Serializes this rule into the Wowool rule DSL syntax.
     *
     * @return DSL source code for this rule
     */
    public String toDsl() {
        StringBuilder sb = new StringBuilder();
        if (name != null && !name.isBlank()) {
            sb.append("// Rule: ").append(name.trim()).append("\n");
        }
        sb.append("rule: { ").append(pattern.toDsl()).append(" } = ").append(targetConcept).append(";");
        return sb.toString();
    }

    @Override
    public String toString() {
        return toDsl();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WowoolRule that)) {
            return false;
        }
        return Objects.equals(name, that.name) && Objects.equals(pattern, that.pattern)
                && Objects.equals(targetConcept, that.targetConcept) && Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, pattern, targetConcept, attributes);
    }

    /**
     * Builder for specifying the matching pattern of a Wowool rule.
     */
    public static final class RuleBuilder {
        private final String name;

        private RuleBuilder(String name) {
            this.name = name;
        }

        /**
         * Specifies the matching pattern for this rule.
         *
         * @param pattern
         *            The pattern to match
         * @return Builder to specify the yielded concept
         */
        public YieldBuilder matching(RulePattern pattern) {
            Objects.requireNonNull(pattern, "pattern must not be null");
            return new YieldBuilder(name, pattern);
        }

        /**
         * Specifies a sequence of matching patterns for this rule.
         *
         * @param patterns
         *            Array of patterns to match sequentially
         * @return Builder to specify the yielded concept
         */
        public YieldBuilder matching(RulePattern... patterns) {
            Objects.requireNonNull(patterns, "patterns must not be null");
            return new YieldBuilder(name, Sequence.of(patterns));
        }

        /**
         * Specifies a sequence of literal words to match for this rule.
         *
         * @param first
         *            The first literal word
         * @param rest
         *            Additional literal words
         * @return Builder to specify the yielded concept
         */
        public YieldBuilder matching(String first, String... rest) {
            return new YieldBuilder(name, Sequence.of(first, rest));
        }
    }

    /**
     * Builder for specifying the yielded concept of a Wowool rule.
     */
    public static final class YieldBuilder {
        private final String name;
        private final RulePattern pattern;

        private YieldBuilder(String name, RulePattern pattern) {
            this.name = name;
            this.pattern = pattern;
        }

        /**
         * Specifies the target concept URI to yield upon match.
         *
         * @param concept
         *            Target concept URI (e.g., "EmploymentRelation")
         * @return Fully constructed {@link WowoolRule}
         */
        public WowoolRule yields(String concept) {
            return new WowoolRule(name, pattern, concept, Collections.emptyMap());
        }

        /**
         * Specifies the target concept URI and associated attributes to yield.
         *
         * @param concept
         *            Target concept URI
         * @param attributes
         *            Map of yield attributes
         * @return Fully constructed {@link WowoolRule}
         */
        public WowoolRule yields(String concept, Map<String, String> attributes) {
            return new WowoolRule(name, pattern, concept, attributes);
        }
    }
}
