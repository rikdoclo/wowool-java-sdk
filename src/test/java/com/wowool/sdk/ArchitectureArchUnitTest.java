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

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Bytecode-level architectural assertions using ArchUnit. Guarantees that the
 * Wowool Java SDK package dependency graph remains a pure Directed Acyclic
 * Graph (DAG) and that internal native FFM handles never leak into public
 * domain packages.
 */
@AnalyzeClasses(packages = "com.wowool.sdk", importOptions = {ImportOption.DoNotIncludeTests.class})
public class ArchitectureArchUnitTest {

    /**
     * Packages must be free of cyclic dependencies to guarantee modularity and zero
     * combinatorial effects.
     */
    @ArchTest
    static final ArchRule PACKAGES_MUST_BE_FREE_OF_CYCLES = slices().matching("com.wowool.sdk.(*)..").should()
            .beFreeOfCycles().because("Subpackages must form a strict Directed Acyclic Graph (DAG)");

    /**
     * Public model classes must never directly depend on low-level FFI / Panama
     * native internal wrappers.
     */
    @ArchTest
    static final ArchRule MODEL_MUST_NOT_DEPEND_ON_INTERNAL_FFI = noClasses().that().resideInAPackage("..model..")
            .should().dependOnClassesThat().resideInAPackage("..internal.ffi..").because(
                    "Public domain models must remain strictly decoupled from native FFM handles and raw memory segments");
}
