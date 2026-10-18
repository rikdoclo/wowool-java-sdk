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

import com.wowool.sdk.CompilationResult;
import com.wowool.sdk.DomainCompiler;
import com.wowool.sdk.WowoolEngine;
import com.wowool.sdk.WowoolPipeline;
import com.wowool.sdk.model.EntitySpan;
import com.wowool.sdk.model.WowoolDocument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleDslTest {

    private static WowoolEngine engine;

    @BeforeAll
    static void setUp() {
        try {
            engine = WowoolEngine.create();
        } catch (Exception ignored) {
            // Engine might not be licensed in certain CI environments
        }
    }

    @AfterAll
    static void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    @DisplayName("Verify fluent Rule DSL produces compliant Wowool syntax")
    void testFluentRuleDslSyntax() {
        WowoolRule rule = WowoolRule.named("ExtractEmployment")
                .matching(Sequence.of(Patterns.concept("Person"), Patterns.gap(), Patterns.choice("employed", "works"),
                        Patterns.literal("at"), Patterns.gap(), Patterns.concept("Organization")))
                .yields("EmploymentRelation");

        assertEquals("ExtractEmployment", rule.name().orElseThrow());
        assertEquals("EmploymentRelation", rule.targetConcept());

        String dsl = rule.toDsl();
        assertTrue(dsl.contains("// Rule: ExtractEmployment"));
        assertTrue(dsl.contains(
                "rule: { Person .. (\"employed\" | \"works\") \"at\" .. Organization } = EmploymentRelation;"));
    }

    @Test
    @DisplayName("Verify token matchers: literals, lemmas, properties, concepts, and optionals")
    void testTokenMatchers() {
        TokenPattern literal = TokenPattern.literal("Acme");
        assertEquals("\"Acme\"", literal.toDsl());

        TokenPattern lemma = TokenPattern.lemma("werken");
        assertEquals("'werken'", lemma.toDsl());

        TokenPattern property = TokenPattern.property("noun");
        assertEquals("+noun", property.toDsl());

        TokenPattern concept = TokenPattern.concept("Company");
        assertEquals("Company", concept.toDsl());

        OptionalPattern optional = literal.optional();
        assertEquals("(\"Acme\")?", optional.toDsl());

        ChoicePattern choice = ChoicePattern.ofLiterals("CEO", "CTO", "CFO");
        assertEquals("(\"CEO\" | \"CTO\" | \"CFO\")", choice.toDsl());
    }

    @Test
    @DisplayName("Verify RuleSet serialization")
    void testRuleSet() {
        WowoolRule r1 = WowoolRule.named("RuleA").matching(Sequence.of("werkt", "bij")).yields("Employment");

        WowoolRule r2 = WowoolRule.unnamed().matching(Sequence.of("woont", "in")).yields("Residence");

        RuleSet set = RuleSet.of(r1, r2);
        assertEquals(2, set.size());
        assertFalse(set.isEmpty());

        String dsl = set.toDsl();
        assertTrue(dsl.contains("// Rule: RuleA"));
        assertTrue(dsl.contains("rule: { \"werkt\" \"bij\" } = Employment;"));
        assertTrue(dsl.contains("rule: { \"woont\" \"in\" } = Residence;"));
    }

    @Test
    @DisplayName("Compile structured WowoolRule directly to .dom and execute in live pipeline")
    void testCompileAndExecuteStructuredRule() throws Exception {
        if (engine == null) {
            return;
        }

        DomainCompiler compiler = engine.compiler();
        Path tempDom = Files.createTempFile("dsl_rule_", ".dom");
        tempDom.toFile().deleteOnExit();

        WowoolRule rule = WowoolRule.named("ExtractEmployment")
                .matching(Sequence.of(TokenPattern.literal("werkt"), TokenPattern.literal("bij")))
                .yields("EmploymentRelation");

        CompilationResult result = compiler.compileRule(rule, tempDom, "dutch");
        assertTrue(result.success(), "Rule compilation should succeed: " + result.diagnostics());
        assertTrue(Files.size(tempDom) > 0, "Compiled .dom file must be non-empty");

        try (WowoolPipeline pipeline = engine.pipeline("dutch", "entity", tempDom.toString())) {
            String text = "Jan Janssen woont in Amsterdam en werkt bij Acme International.";
            WowoolDocument doc = pipeline.analyze(text);

            assertTrue(doc.hasEntity("EmploymentRelation"), "Custom rule should match");
            EntitySpan match = doc.findFirst("EmploymentRelation").orElseThrow();
            assertEquals("werkt bij", match.literal());
        }
    }
}
