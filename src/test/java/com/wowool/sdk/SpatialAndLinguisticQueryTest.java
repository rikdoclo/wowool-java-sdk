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

package com.wowool.sdk;

import com.wowool.sdk.model.Annotation;
import com.wowool.sdk.model.EntitySpan;
import com.wowool.sdk.model.TokenSpan;
import com.wowool.sdk.model.WowoolDocument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpatialAndLinguisticQueryTest {

    private static WowoolEngine engine;
    private static WowoolPipeline pipeline;

    @BeforeAll
    static void setUp() {
        try {
            engine = WowoolEngine.create();
            pipeline = engine.pipeline("dutch", "entity");
        } catch (Exception ignored) {
        }
    }

    @AfterAll
    static void tearDown() {
        if (pipeline != null) {
            pipeline.close();
        }
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    @DisplayName("Verify spatial navigation: tokensBefore, tokensAfter, and context window")
    void testSpatialNavigation() {
        if (pipeline == null) {
            return;
        }

        String text = "De bekende wetenschapper Albert Einstein werd geboren in Ulm.";
        WowoolDocument doc = pipeline.analyze(text);

        List<EntitySpan> persons = doc.entities("Person");
        assertFalse(persons.isEmpty(), "Should extract person entity");
        EntitySpan einstein = persons.getFirst();

        // 1. tokensBefore
        List<TokenSpan> before2 = doc.tokensBefore(einstein, 2);
        assertEquals(2, before2.size(), "Should return exactly 2 tokens before Einstein");
        assertEquals("bekende", before2.get(0).text());
        assertEquals("wetenschapper", before2.get(1).text());

        // 2. tokensAfter
        List<TokenSpan> after2 = doc.tokensAfter(einstein, 2);
        assertEquals(2, after2.size(), "Should return exactly 2 tokens after Einstein");
        assertEquals("werd", after2.get(0).text());
        assertEquals("geboren", after2.get(1).text());

        // 3. window
        List<TokenSpan> win = doc.window(einstein, 1);
        // Expect: "wetenschapper" (1 before) + "Albert", "Einstein" (inside) + "werd"
        // (1 after) = 4 tokens
        assertEquals(4, win.size());
        assertEquals("wetenschapper", win.get(0).text());
        assertEquals("Albert", win.get(1).text());
        assertEquals("Einstein", win.get(2).text());
        assertEquals("werd", win.get(3).text());
    }

    @Test
    @DisplayName("Verify spatial overlap queries: overlapping, overlappingEntities, overlappingTokens")
    void testOverlappingQueries() {
        if (pipeline == null) {
            return;
        }

        String text = "Jan Janssen woont in Amsterdam.";
        WowoolDocument doc = pipeline.analyze(text);

        EntitySpan person = doc.findFirst("Person").orElseThrow();

        // Overlapping tokens for Jan Janssen
        List<TokenSpan> overlappingTokens = doc.overlappingTokens(person);
        assertEquals(2, overlappingTokens.size());
        assertEquals("Jan", overlappingTokens.get(0).text());
        assertEquals("Janssen", overlappingTokens.get(1).text());

        // Generic overlapping annotations
        List<Annotation> overlapping = doc.overlapping(person);
        assertTrue(overlapping.size() >= 2);
    }

    @Test
    @DisplayName("Verify linguistic filter queries: lemma, pos, flags, and predicate search")
    void testLinguisticQueries() {
        if (pipeline == null) {
            return;
        }

        String text = "De astronauten vliegen naar het internationale ruimtestation.";
        WowoolDocument doc = pipeline.analyze(text);

        // Lemmas
        List<String> allLemmas = doc.lemmas();
        assertFalse(allLemmas.isEmpty());
        assertTrue(doc.hasLemma("vliegen"), "Document should contain lemma 'vliegen'");

        List<TokenSpan> vliegenTokens = doc.tokensByLemma("vliegen");
        assertEquals(1, vliegenTokens.size());
        assertEquals("vliegen", vliegenTokens.getFirst().text());

        // POS
        List<String> posTags = doc.posTags();
        assertFalse(posTags.isEmpty());

        // Predicate search: tokens with length > 10
        List<TokenSpan> longTokens = doc.findTokens(t -> t.text().length() > 10);
        assertFalse(longTokens.isEmpty(), "Should find long words such as internationale / ruimtestation");
        assertTrue(longTokens.stream().anyMatch(t -> t.text().equalsIgnoreCase("internationale")));
    }
}
