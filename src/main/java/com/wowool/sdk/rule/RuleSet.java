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
 * An immutable collection of {@link WowoolRule}s representing a complete
 * linguistic extraction domain.
 */
public final class RuleSet {

    private final List<WowoolRule> rules;

    private RuleSet(List<WowoolRule> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException("RuleSet must contain at least one rule.");
        }
        this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
    }

    /**
     * Creates a RuleSet from an array of rules.
     *
     * @param rules
     *            The rules to include
     * @return New RuleSet
     */
    public static RuleSet of(WowoolRule... rules) {
        Objects.requireNonNull(rules, "rules must not be null");
        return new RuleSet(Arrays.asList(rules));
    }

    /**
     * Creates a RuleSet from a list of rules.
     *
     * @param rules
     *            The list of rules to include
     * @return New RuleSet
     */
    public static RuleSet of(List<WowoolRule> rules) {
        Objects.requireNonNull(rules, "rules must not be null");
        return new RuleSet(new ArrayList<>(rules));
    }

    /**
     * Returns an unmodifiable list of rules in this set.
     */
    public List<WowoolRule> rules() {
        return rules;
    }

    /**
     * Number of rules in this set.
     */
    public int size() {
        return rules.size();
    }

    /**
     * Checks if this set is empty.
     */
    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /**
     * Serializes all rules in this set into valid Wowool domain DSL code.
     *
     * @return Multi-rule DSL source code
     */
    public String toDsl() {
        return rules.stream().map(WowoolRule::toDsl).collect(Collectors.joining("\n\n", "", "\n"));
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
        if (!(o instanceof RuleSet ruleSet)) {
            return false;
        }
        return Objects.equals(rules, ruleSet.rules);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rules);
    }
}
