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
import com.wowool.sdk.model.SentenceSpan;
import com.wowool.sdk.model.TokenSpan;
import com.wowool.sdk.model.WowoolDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ModelAndConfigTest {

    @Test
    @DisplayName("Test WowoolConfig builder and manual property injection")
    void testConfigBuilder() {
        Path mockDylib = Path.of("/opt/custom/lib");
        Path mockRoot = Path.of("/opt/custom/models");

        WowoolConfig config = WowoolConfig.builder()
            .licenseKey("secret-test-key")
            .dylibDir(mockDylib)
            .wowoolRoot(mockRoot)
            .build();

        assertEquals("secret-test-key", config.licenseKey());
        assertEquals(mockDylib, config.dylibDir());
        assertEquals(mockRoot, config.wowoolRoot());
    }

    @Test
    @DisplayName("Test WowoolDocument and Span records data integrity")
    void testDocumentAndSpanModels() {
        TokenSpan t1 = new TokenSpan("Jan", "jan", "N(prop)", "jan", 0, 3, List.of("init-cap"));
        TokenSpan t2 = new TokenSpan("Janssen", "janssen", "N(prop)", "janssen", 4, 11, List.of("init-cap"));

        EntitySpan e1 = new EntitySpan("Person", "Jan Janssen", 0, 11,
            Map.of("gender", "male", "canonical", "Jan Janssen"));
        EntitySpan e2 = new EntitySpan("City", "Amsterdam", 21, 30,
            Map.of("country", "Netherlands"));

        SentenceSpan s1 = new SentenceSpan(0, 31, "Jan Janssen woont in Amsterdam.", List.of(t1, t2), List.of(e1, e2));

        WowoolDocument doc = new WowoolDocument(
            "Jan Janssen woont in Amsterdam.",
            "dutch",
            List.of(s1),
            List.of(e1, e2),
            List.of(t1, t2),
            "{}"
        );

        assertEquals("Jan Janssen woont in Amsterdam.", doc.text());
        assertEquals("dutch", doc.language());
        assertEquals(1, doc.sentenceCount());
        assertEquals(2, doc.tokenCount());
        assertEquals(2, doc.entityCount());

        List<EntitySpan> persons = doc.entities("Person");
        assertEquals(1, persons.size());
        assertEquals("Jan Janssen", persons.getFirst().literal());
        assertEquals("male", persons.getFirst().getAttribute("gender"));
        assertEquals(11, persons.getFirst().length());

        Optional<EntitySpan> city = doc.findFirst("City");
        assertTrue(city.isPresent());
        assertEquals("Amsterdam", city.get().literal());
        assertEquals("Netherlands", city.get().getAttribute("country"));

        assertTrue(t1.hasFlag("init-cap"));
        assertFalse(t1.hasFlag("non-existent-flag"));
    }

    @Test
    @DisplayName("Test CompilationResult record")
    void testCompilationResult() {
        CompilationResult ok = new CompilationResult(true, Path.of("/tmp/rule.dom"), List.of("Compiled successfully"), "{}");
        assertTrue(ok.success());
        assertEquals(Path.of("/tmp/rule.dom"), ok.outputFile());
        assertFalse(ok.diagnostics().isEmpty());

        CompilationResult failed = new CompilationResult(false, null, List.of("Syntax error on line 1"), "{}");
        assertFalse(failed.success());
        assertNull(failed.outputFile());
        assertEquals("Syntax error on line 1", failed.diagnostics().getFirst());
    }

    @Test
    @DisplayName("Test WowoolException message and cause retention")
    void testWowoolException() {
        IllegalArgumentException cause = new IllegalArgumentException("Root cause");
        WowoolException ex = new WowoolException("Engine failure", cause);

        assertEquals("Engine failure", ex.getMessage());
        assertSame(cause, ex.getCause());
    }
}
