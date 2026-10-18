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

import java.util.Collections;
import java.util.List;

/**
 * Represents an individual word/punctuation token with linguistic annotations.
 *
 * @param literal
 *            The raw textual representation of the token
 * @param lemma
 *            The dictionary base form (lemma)
 * @param pos
 *            The Part-of-Speech tag (e.g., "V-Pres", "Prop-Std", "Noun-SG")
 * @param stem
 *            Optional morphological stem
 * @param beginOffset
 *            Character start offset within the document
 * @param endOffset
 *            Character end offset within the document
 * @param flags
 *            Linguistic flags (e.g., "init-cap", "init-token")
 */
public record TokenSpan(String literal, String lemma, String pos, String stem, int beginOffset, int endOffset,
        List<String> flags) implements Annotation {
    public TokenSpan {
        if (flags == null) {
            flags = Collections.emptyList();
        } else {
            flags = Collections.unmodifiableList(flags);
        }
    }

    /**
     * Surface text of this token (alias for {@link #literal()}).
     *
     * @return Raw text of this token
     */
    public String text() {
        return literal;
    }

    /**
     * Checks if a specific linguistic flag is present on this token.
     *
     * @param flag
     *            Flag name (e.g., "init-cap")
     * @return true if present, false otherwise
     */
    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }
}
