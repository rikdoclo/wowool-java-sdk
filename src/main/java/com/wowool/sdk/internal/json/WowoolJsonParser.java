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

    private WowoolJsonParser() {}

    public static WowoolDocument parse(String text, String rawJson) throws IOException {
        JsonNode root = MAPPER.readTree(rawJson);

        String language = root.path("language").asText("unknown");
        JsonNode sentencesNode = root.path("sentences");

        List<SentenceSpan> sentences = new ArrayList<>();
        List<EntitySpan> allEntities = new ArrayList<>();
        List<TokenSpan> allTokens = new ArrayList<>();

        int textCharLen = text != null ? text.length() : 0;
        int[] byteToChar = buildByteToCharMap(text);

        if (sentencesNode.isArray()) {
            for (JsonNode sentNode : sentencesNode) {
                if (!sentNode.isArray() || sentNode.size() < 4) continue;

                int sentByteBegin = sentNode.get(1).asInt();
                int sentByteEnd = sentNode.get(2).asInt();
                int sentBegin = toCharOffset(sentByteBegin, byteToChar, textCharLen);
                int sentEnd = toCharOffset(sentByteEnd, byteToChar, textCharLen);
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
                            int byteBegin = ann.get(1).asInt();
                            int byteEnd = ann.get(2).asInt();
                            int begin = toCharOffset(byteBegin, byteToChar, textCharLen);
                            int end = toCharOffset(byteEnd, byteToChar, textCharLen);
                            String uri = ann.get(3).asText();

                            // "Sentence" concept represents the structural sentence itself; skip as an entity
                            if ("Sentence".equalsIgnoreCase(uri)) {
                                continue;
                            }

                            Map<String, Object> attributes = Collections.emptyMap();
                            if (ann.size() > 4 && ann.get(4).isObject()) {
                                attributes = parseAttributes(ann.get(4));
                            }

                            String literal = slice(text, begin, end);
                            EntitySpan entity = new EntitySpan(uri, literal, begin, end, attributes);
                            sentenceEntities.add(entity);
                            allEntities.add(entity);
                        } else if (annType == 3 && ann.size() >= 4) {
                            // Token Annotation
                            int byteBegin = ann.get(1).asInt();
                            int byteEnd = ann.get(2).asInt();
                            int begin = toCharOffset(byteBegin, byteToChar, textCharLen);
                            int end = toCharOffset(byteEnd, byteToChar, textCharLen);
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

    /**
     * Builds a fast lookup table mapping UTF-8 byte offsets to Java UTF-16 char offsets.
     * Returns null if the text is pure ASCII (fast-path where byteOffset == charOffset).
     */
    static int[] buildByteToCharMap(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        int charLen = text.length();
        boolean hasNonAscii = false;
        for (int i = 0; i < charLen; i++) {
            if (text.charAt(i) >= 0x80) {
                hasNonAscii = true;
                break;
            }
        }
        if (!hasNonAscii) {
            return null;
        }

        int totalBytes = 0;
        for (int i = 0; i < charLen; i++) {
            char c = text.charAt(i);
            if (c <= 0x7F) {
                totalBytes += 1;
            } else if (c <= 0x7FF) {
                totalBytes += 2;
            } else if (Character.isHighSurrogate(c) && i + 1 < charLen && Character.isLowSurrogate(text.charAt(i + 1))) {
                totalBytes += 4;
                i++;
            } else {
                totalBytes += 3;
            }
        }

        int[] byteToChar = new int[totalBytes + 1];
        int byteIdx = 0;
        for (int i = 0; i < charLen; i++) {
            char c = text.charAt(i);
            if (c <= 0x7F) {
                byteToChar[byteIdx++] = i;
            } else if (c <= 0x7FF) {
                byteToChar[byteIdx++] = i;
                byteToChar[byteIdx++] = i;
            } else if (Character.isHighSurrogate(c) && i + 1 < charLen && Character.isLowSurrogate(text.charAt(i + 1))) {
                byteToChar[byteIdx++] = i;
                byteToChar[byteIdx++] = i;
                byteToChar[byteIdx++] = i;
                byteToChar[byteIdx++] = i;
                i++;
            } else {
                byteToChar[byteIdx++] = i;
                byteToChar[byteIdx++] = i;
                byteToChar[byteIdx++] = i;
            }
        }
        byteToChar[byteIdx] = charLen;
        return byteToChar;
    }

    private static int toCharOffset(int byteOffset, int[] byteToChar, int charLen) {
        if (byteToChar == null) {
            return Math.max(0, Math.min(byteOffset, charLen));
        }
        if (byteOffset <= 0) return 0;
        if (byteOffset >= byteToChar.length) return charLen;
        return byteToChar[byteOffset];
    }

    private static String slice(String text, int begin, int end) {
        if (text == null) return "";
        int safeBegin = Math.max(0, Math.min(begin, text.length()));
        int safeEnd = Math.max(safeBegin, Math.min(end, text.length()));
        return text.substring(safeBegin, safeEnd);
    }

    private static Map<String, Object> parseAttributes(JsonNode node) {
        if (node == null || !node.isObject() || node.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            map.put(entry.getKey(), convertNode(entry.getValue()));
        }
        return Collections.unmodifiableMap(map);
    }

    private static Object convertNode(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        if (node.isNumber()) return node.numberValue();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isArray()) {
            List<Object> list = new ArrayList<>(node.size());
            for (JsonNode item : node) {
                list.add(convertNode(item));
            }
            return Collections.unmodifiableList(list);
        }
        if (node.isObject()) {
            return parseAttributes(node);
        }
        return node.asText();
    }
}
