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
 * Represents a segmented sentence with associated tokens and entities.
 *
 * @param beginOffset Start offset within the document
 * @param endOffset End offset within the document
 * @param text The exact sentence text
 * @param tokens Ordered list of tokens within this sentence
 * @param entities Extracted entities within this sentence
 */
public record SentenceSpan(
    int beginOffset,
    int endOffset,
    String text,
    List<TokenSpan> tokens,
    List<EntitySpan> entities
) {
    public SentenceSpan {
        tokens = (tokens == null) ? Collections.emptyList() : Collections.unmodifiableList(tokens);
        entities = (entities == null) ? Collections.emptyList() : Collections.unmodifiableList(entities);
    }
}
