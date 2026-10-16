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

import com.wowool.sdk.internal.ffi.WowoolNative;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/**
 * Compiles Wowool rule source code (.wow / .woc) directly from Java
 * into binary domain files (.dom) using the native compiler.
 */
public final class DomainCompiler {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final WowoolEngine engine;

    DomainCompiler(WowoolEngine engine) {
        this.engine = engine;
    }

    /**
     * Compiles inline Wowool rule source code into a binary .dom domain file.
     *
     * @param source Wowool rule source code (e.g., {@code "rule: { Person .. <'works'> } = EmploymentRelation;"})
     * @param outputFile Target binary domain file (.dom)
     * @param language Base language for morphology and tokenization (e.g., "dutch", "english")
     * @return {@link CompilationResult} containing status and compiler diagnostics
     */
    public CompilationResult compileSource(String source, Path outputFile, String language) {
        return compile(source, Collections.emptyList(), outputFile, language, false, true);
    }

    /**
     * Compiles one or more Wowool rule source files into a binary .dom domain file.
     *
     * @param inputFiles List of source files (.wow or .woc)
     * @param outputFile Target binary domain file (.dom)
     * @param language Base language for morphology and tokenization (e.g., "dutch", "english")
     * @return {@link CompilationResult} containing status and compiler diagnostics
     */
    public CompilationResult compileFiles(List<Path> inputFiles, Path outputFile, String language) {
        return compile(null, inputFiles, outputFile, language, false, true);
    }

    /**
     * Full compilation method supporting custom strictness and force flags.
     */
    public CompilationResult compile(
        String source,
        List<Path> inputFiles,
        Path outputFile,
        String language,
        boolean strict,
        boolean force
    ) {
        if (outputFile == null) {
            throw new IllegalArgumentException("outputFile must not be null.");
        }

        Map<String, String> opts = new LinkedHashMap<>();
        opts.put("output_file", outputFile.toAbsolutePath().normalize().toString());
        opts.put("strict", String.valueOf(strict));
        opts.put("force", String.valueOf(force));

        if (language != null && !language.isBlank()) {
            opts.put("language", language);
        }

        if (source != null && !source.isBlank()) {
            String b64 = Base64.getEncoder().encodeToString(source.getBytes(StandardCharsets.UTF_8));
            opts.put("wow_base64", b64);
        }

        if (inputFiles != null && !inputFiles.isEmpty()) {
            String joined = String.join(",", inputFiles.stream().map(p -> p.toAbsolutePath().normalize().toString()).toList());
            opts.put("input_files", joined);
        }

        String jsonOpts;
        try {
            jsonOpts = MAPPER.writeValueAsString(opts);
        } catch (IOException e) {
            throw new WowoolException("Failed to serialize compiler options: " + e.getMessage(), e);
        }

        try (Arena callArena = Arena.ofConfined()) {
            MemorySegment exInfo = WowoolNative.allocateExceptionInfo(callArena);
            String rawJson = WowoolNative.compileDomain(jsonOpts, callArena, exInfo);

            boolean success = false;
            List<String> diagnostics = new ArrayList<>();

            try {
                JsonNode root = MAPPER.readTree(rawJson);
                success = root.path("return").asBoolean(false);

                JsonNode sink = root.path("sink");
                if (sink.isObject()) {
                    sink.fields().forEachRemaining(entry -> {
                        String fileKey = entry.getKey();
                        for (JsonNode err : entry.getValue()) {
                            String type = err.path("type").asText("info");
                            String msg = err.path("msg").asText();
                            int line = err.path("line").asInt(-1);
                            int col = err.path("column").asInt(-1);
                            if (line >= 0) {
                                diagnostics.add(String.format("[%s] %s:%d:%d %s", type, fileKey, line, col, msg));
                            } else {
                                diagnostics.add(String.format("[%s] %s: %s", type, fileKey, msg));
                            }
                        }
                    });
                }

                if (root.has("message")) {
                    diagnostics.add(root.path("message").asText());
                }
            } catch (Exception e) {
                diagnostics.add("Failed to parse compiler response: " + e.getMessage());
            }

            return new CompilationResult(success, outputFile, diagnostics, rawJson);
        }
    }
}
