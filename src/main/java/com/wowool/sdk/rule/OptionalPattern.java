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
 * Represents an optional matching pattern {@code (pattern)?} in a Wowool rule.
 */
public final class OptionalPattern implements RulePattern {

    private final RulePattern inner;

    private OptionalPattern(RulePattern inner) {
        this.inner = Objects.requireNonNull(inner, "inner pattern must not be null");
    }

    /**
     * Creates an optional pattern around the specified inner pattern.
     *
     * @param inner
     *            Pattern that is optional
     * @return New OptionalPattern
     */
    public static OptionalPattern of(RulePattern inner) {
        return new OptionalPattern(inner);
    }

    /**
     * Returns the underlying required pattern.
     */
    public RulePattern inner() {
        return inner;
    }

    @Override
    public String toDsl() {
        String innerDsl = inner.toDsl();
        if (innerDsl.startsWith("(") && innerDsl.endsWith(")")) {
            return innerDsl + "?";
        }
        return "(" + innerDsl + ")?";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OptionalPattern that)) {
            return false;
        }
        return Objects.equals(inner, that.inner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inner);
    }

    @Override
    public String toString() {
        return toDsl();
    }
}
