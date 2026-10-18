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
 * Represents a wildcard gap {@code ..} between patterns in a Wowool rule.
 */
public final class GapPattern implements RulePattern {

    private static final GapPattern INSTANCE = new GapPattern();

    private GapPattern() {
    }

    /**
     * Singleton instance representing the wildcard gap {@code ..}.
     *
     * @return GapPattern instance
     */
    public static GapPattern any() {
        return INSTANCE;
    }

    @Override
    public String toDsl() {
        return "..";
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GapPattern;
    }

    @Override
    public int hashCode() {
        return GapPattern.class.hashCode();
    }

    @Override
    public String toString() {
        return toDsl();
    }
}
