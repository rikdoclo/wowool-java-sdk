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

import com.wowool.sdk.model.EntitySpan;
import com.wowool.sdk.model.TokenSpan;
import com.wowool.sdk.model.WowoolDocument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class WowoolSdkTest {

    private static WowoolEngine engine;

    @BeforeAll
    static void setUp() {
        engine = WowoolEngine.create();
        assertNotNull(engine, "WowoolEngine should be successfully initialized");
    }

    @AfterAll
    static void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    @DisplayName("Test pure linguistic analysis without domain models (syntax and morphology only)")
    void testPureLinguisticAnalysis() {
        try (WowoolPipeline pipeline = engine.pipeline("dutch")) {
            String text = "De directeur presenteerde vandaag nieuwe innovatieve plannen.";
            WowoolDocument doc = pipeline.analyze(text);

            assertNotNull(doc);
            assertEquals(text, doc.text());
            assertTrue(doc.sentenceCount() >= 1, "At least 1 sentence expected");
            assertTrue(doc.tokenCount() >= 5, "At least 5 tokens expected");

            boolean foundDirecteur = doc.tokens().stream()
                .anyMatch(t -> "directeur".equalsIgnoreCase(t.literal()));
            assertTrue(foundDirecteur, "Token 'directeur' should be present");
        }
    }

    @Test
    @DisplayName("Test named entity recognition with 'entity' domain")
    void testEntityRecognitionPipeline() {
        try (WowoolPipeline pipeline = engine.pipeline("dutch", "entity")) {
            String text = "Jan Janssen woont in Amsterdam en werkt bij Acme International.";
            WowoolDocument doc = pipeline.analyze(text);

            assertNotNull(doc);
            assertTrue(doc.entityCount() >= 2, "Expected at least 2 entities (Person and City)");

            // 1. Check Person
            List<EntitySpan> persons = doc.entities("Person");
            assertFalse(persons.isEmpty(), "Person 'Jan Janssen' should be recognized");
            EntitySpan jan = persons.getFirst();
            assertEquals("Jan Janssen", jan.literal());
            assertEquals(0, jan.beginOffset());
            assertEquals(11, jan.endOffset());

            // 2. Check City
            List<EntitySpan> cities = doc.entities("City");
            assertFalse(cities.isEmpty(), "City 'Amsterdam' should be recognized");
            EntitySpan city = cities.getFirst();
            assertEquals("Amsterdam", city.literal());

            System.out.println("Extracted entities:");
            for (EntitySpan e : doc.entities()) {
                System.out.printf("  - [%s] '%s' (%d-%d) -> %s%n",
                    e.uri(), e.literal(), e.beginOffset(), e.endOffset(), e.attributes());
            }
        }
    }

    @Test
    @DisplayName("Test multi-sentence segmentation and offset precision")
    void testMultipleSentences() {
        try (WowoolPipeline pipeline = engine.pipeline("dutch", "entity")) {
            String text = "Willem de Boer bezoekt Amsterdam. Daarna reist hij door naar Rotterdam.";
            WowoolDocument doc = pipeline.analyze(text);

            assertEquals(2, doc.sentenceCount(), "Expected 2 sentences");
            assertEquals("Willem de Boer bezoekt Amsterdam.", doc.sentences().get(0).text().trim());

            assertTrue(doc.findFirst("Person").isPresent());
            assertEquals("Willem de Boer", doc.findFirst("Person").get().literal());
        }
    }

    @Test
    @DisplayName("Test automated language identification (LanguageIdentifier)")
    void testLanguageIdentification() {
        try (LanguageIdentifier lid = engine.languageIdentifier()) {
            assertEquals("dutch", lid.identify("Dit is een duidelijke Nederlandstalige tekst over moderne softwareontwikkeling."));
            assertEquals("french", lid.identify("Ceci est un document en langue française pour tester la détection."));
            assertEquals("english", lid.identify("This is a comprehensive document written in the English language."));
        }
    }

    @Test
    @DisplayName("Test dynamic domain compilation (.wow -> .dom) and immediate execution in pipeline")
    void testDomainCompilationAndExecution() throws Exception {
        DomainCompiler compiler = engine.compiler();

        Path tempDom = Files.createTempFile("custom_rule_", ".dom");
        tempDom.toFile().deleteOnExit();

        // Custom DSL Rule: detect 'werkt bij' and tag as 'EmploymentRelation'
        String ruleSource = "rule: { \"werkt\" \"bij\" } = EmploymentRelation;\n";

        CompilationResult result = compiler.compileSource(ruleSource, tempDom, "dutch");
        assertTrue(result.success(), "Compilation should succeed. Diagnostics: " + result.diagnostics());
        assertTrue(Files.size(tempDom) > 0, "Compiled .dom binary should have non-zero size");

        // Execute pipeline using dynamically compiled domain
        try (WowoolPipeline customPipeline = engine.pipeline("dutch", "entity", tempDom.toString())) {
            String text = "Jan Janssen woont in Amsterdam en werkt bij Acme International.";
            WowoolDocument doc = customPipeline.analyze(text);

            assertTrue(doc.findFirst("EmploymentRelation").isPresent(),
                "Dynamically compiled rule 'EmploymentRelation' should be detected!");
            EntitySpan match = doc.findFirst("EmploymentRelation").get();
            assertEquals("EmploymentRelation", match.uri());
            assertEquals("werkt bij", match.literal());
            System.out.println("Custom compiled rule successfully detected: " + match.literal());
        }
    }
}
