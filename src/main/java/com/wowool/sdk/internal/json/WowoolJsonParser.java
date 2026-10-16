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

package com.wowool.sdk.internal.json;

import com.wowool.sdk.model.EntitySpan;
import com.wowool.sdk.model.SentenceSpan;
import com.wowool.sdk.model.TokenSpan;
import com.wowool.sdk.model.WowoolDocument;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;

/**
 * High-performance JSON parser mapping the native C++ TIR output into typed WowoolDocument models.
 */
public final class WowoolJsonParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private WowoolJsonParser() {}

    public static WowoolDocument parse(String text, String rawJson) throws IOException {
        JsonNode root = MAPPER.readTree(rawJson);

        String language = root.path("language").asText("unknown");
        JsonNode sentencesNode = root.path("sentences");

        List<SentenceSpan> sentences = new ArrayList<>();
        List<EntitySpan> allEntities = new ArrayList<>();
        List<TokenSpan> allTokens = new ArrayList<>();

        if (sentencesNode.isArray()) {
            for (JsonNode sentNode : sentencesNode) {
                if (!sentNode.isArray() || sentNode.size() < 4) continue;

                int sentBegin = sentNode.get(1).asInt();
                int sentEnd = sentNode.get(2).asInt();
                String sentText = slice(text, sentBegin, sentEnd);

                List<TokenSpan> sentenceTokens = new ArrayList<>();
                List<EntitySpan> sentenceEntities = new ArrayList<>();

                JsonNode annotationsNode = sentNode.get(3);
                if (annotationsNode.isArray()) {
                    for (JsonNode ann : annotationsNode) {
                        if (!ann.isArray() || ann.isEmpty()) continue;
                        int annType = ann.get(0).asInt();

                        if (annType == 2 && ann.size() >= 4) {
                            // Concept / Entity Annotation
                            int begin = ann.get(1).asInt();
                            int end = ann.get(2).asInt();
                            String uri = ann.get(3).asText();

                            // "Sentence" concept represents the structural sentence itself; skip as an entity
                            if ("Sentence".equalsIgnoreCase(uri)) {
                                continue;
                            }

                            Map<String, Object> attributes = Collections.emptyMap();
                            if (ann.size() > 4 && ann.get(4).isObject()) {
                                attributes = MAPPER.convertValue(ann.get(4), MAP_TYPE);
                            }

                            String literal = slice(text, begin, end);
                            EntitySpan entity = new EntitySpan(uri, literal, begin, end, attributes);
                            sentenceEntities.add(entity);
                            allEntities.add(entity);
                        } else if (annType == 3 && ann.size() >= 4) {
                            // Token Annotation
                            int begin = ann.get(1).asInt();
                            int end = ann.get(2).asInt();
                            String literal = ann.get(3).asText();

                            List<String> flags = new ArrayList<>();
                            if (ann.size() > 4 && ann.get(4).isArray()) {
                                for (JsonNode f : ann.get(4)) {
                                    flags.add(f.asText());
                                }
                            }

                            String lemma = null;
                            String pos = null;
                            String stem = null;
                            if (ann.size() > 5 && ann.get(5).isArray() && !ann.get(5).isEmpty()) {
                                JsonNode morph = ann.get(5).get(0);
                                if (morph.isArray()) {
                                    if (morph.size() > 0) lemma = morph.get(0).asText();
                                    if (morph.size() > 1) pos = morph.get(1).asText();
                                    if (morph.size() > 2 && morph.get(2).isTextual()) stem = morph.get(2).asText();
                                }
                            }

                            TokenSpan token = new TokenSpan(literal, lemma, pos, stem, begin, end, flags);
                            sentenceTokens.add(token);
                            allTokens.add(token);
                        }
                    }
                }

                sentences.add(new SentenceSpan(sentBegin, sentEnd, sentText, sentenceTokens, sentenceEntities));
            }
        }

        return new WowoolDocument(text, language, sentences, allEntities, allTokens, rawJson);
    }

    private static String slice(String text, int begin, int end) {
        if (text == null) return "";
        int safeBegin = Math.max(0, Math.min(begin, text.length()));
        int safeEnd = Math.max(safeBegin, Math.min(end, text.length()));
        return text.substring(safeBegin, safeEnd);
    }
}
