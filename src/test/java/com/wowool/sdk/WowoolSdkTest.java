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
        try {
            engine = WowoolEngine.create();
            assertNotNull(engine, "WowoolEngine should be successfully initialized");
            // Quick dry-run to verify native models and license key validity
            try (WowoolPipeline probe = engine.pipeline("dutch")) {
                probe.analyze("test");
            }
        } catch (Throwable e) {
            org.junit.jupiter.api.Assumptions.abort(
                "Native Wowool engine/license not fully operational in this environment; skipping native tests: " + e.getMessage()
            );
        }
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

    @Test
    @DisplayName("Test concurrent multi-threaded execution with WowoolPipelinePool")
    void testConcurrentPipelinePool() throws Exception {
        try (WowoolPipelinePool pool = engine.pool("dutch", 3, "entity")) {
            assertEquals(3, pool.capacity());
            assertEquals(3, pool.availableCount());

            int taskCount = 10;
            java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(4);
            java.util.List<java.util.concurrent.Future<WowoolDocument>> futures = new java.util.ArrayList<>();

            for (int i = 0; i < taskCount; i++) {
                final int idx = i;
                futures.add(executor.submit(() -> {
                    String sample = "Jan Janssen bezoekt Amsterdam voor taak " + idx + ".";
                    return pool.analyze(sample);
                }));
            }

            for (var f : futures) {
                WowoolDocument doc = f.get(5, java.util.concurrent.TimeUnit.SECONDS);
                assertNotNull(doc);
                assertTrue(doc.hasEntity("Person"));
                assertTrue(doc.hasEntity("City"));
                assertEquals("Jan Janssen", doc.findFirst("Person").get().canonical());
            }

            executor.shutdown();
            assertTrue(executor.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("Test native C ABI metadata methods (engine.info, engine.languages, pipeline.domainInfo)")
    void testNativeMetadata() {
        String info = engine.info();
        assertNotNull(info);
        assertTrue(info.contains("sdk_version"));

        String langs = engine.languages();
        assertNotNull(langs);

        try (WowoolPipeline pipeline = engine.pipeline("dutch", "entity")) {
            String domainInfo = pipeline.domainInfo(0);
            assertNotNull(domainInfo);
            assertTrue(domainInfo.length() > 5);
        }
    }

    @Test
    @DisplayName("Test UTF-8 multi-byte characters and offset alignment (regression for middle dot, accents, currency)")
    void testUtf8MultiByteCharacterOffsets() {
        try (WowoolPipeline pipeline = engine.pipeline("dutch", "entity")) {
            // Text containing multi-byte UTF-8 characters:
            // '·' (2 bytes: U+00B7), '€' (3 bytes: U+20AC), 'ë' (2 bytes: U+00EB)
            String text = "# 53429 · Kennisgeving opstart voor Patrick Van Geyte met budget € 500 te Brussel.";
            WowoolDocument doc = pipeline.analyze(text);

            assertNotNull(doc);
            assertTrue(doc.hasEntity("Person"), "Person entity should be found");
            assertTrue(doc.hasEntity("City"), "City entity should be found");

            EntitySpan person = doc.findFirst("Person").orElseThrow();
            assertEquals("Patrick Van Geyte", person.literal());
            assertEquals("Patrick Van Geyte", text.substring(person.beginOffset(), person.endOffset()));

            EntitySpan city = doc.findFirst("City").orElseThrow();
            assertEquals("Brussel", city.literal());
            assertEquals("Brussel", text.substring(city.beginOffset(), city.endOffset()));

            // Verify all entities match text.substring(begin, end) exactly
            for (EntitySpan e : doc.entities()) {
                assertEquals(e.literal(), text.substring(e.beginOffset(), e.endOffset()),
                    "Entity literal must match text slice at offsets: " + e.literal());
            }

            // Verify all sentence spans match text.substring(begin, end)
            for (var s : doc.sentences()) {
                assertEquals(s.text(), text.substring(s.beginOffset(), s.endOffset()),
                    "Sentence text must match text slice at offsets");
            }
        }
    }
}
