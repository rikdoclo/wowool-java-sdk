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
import com.wowool.sdk.internal.ffi.WowoolPaths;
import com.wowool.sdk.internal.json.WowoolJsonParser;
import com.wowool.sdk.model.WowoolDocument;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An instantiated Wowool analysis pipeline.
 * Holds references to the native analyzer and sequentially chained binary domains (.dom).
 * Executes sub-millisecond in-memory linguistic analysis and returns typed {@link WowoolDocument} instances.
 */
public final class WowoolPipeline implements AutoCloseable {

    private final WowoolEngine engine;
    private final Path languagePath;
    private final MemorySegment analyzerPtr;
    private final List<MemorySegment> domainPtrs;
    private final List<String> domainNames;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private WowoolPipeline(
        WowoolEngine engine,
        Path languagePath,
        MemorySegment analyzerPtr,
        List<MemorySegment> domainPtrs,
        List<String> domainNames
    ) {
        this.engine = engine;
        this.languagePath = languagePath;
        this.analyzerPtr = analyzerPtr;
        this.domainPtrs = domainPtrs;
        this.domainNames = domainNames;
    }

    static WowoolPipeline create(WowoolEngine engine, Path languagePath, List<String> domainNames) {
        Arena arena = engine.arena();
        MemorySegment exInfo = WowoolNative.allocateExceptionInfo(arena);

        // 1. Create Analyzer
        String analyzerOpts = "{\"language\":\"" + languagePath.toAbsolutePath() + "\"}";
        MemorySegment analyzerPtr = WowoolNative.createAnalyzer(engine.nativePointer(), analyzerOpts, arena, exInfo);

        // 2. Load Domains with exception safety
        List<MemorySegment> domainPtrs = new ArrayList<>();
        List<String> resolvedDomainNames = new ArrayList<>();

        try {
            if (domainNames != null) {
                String langName = extractLanguageName(languagePath);
                for (String domain : domainNames) {
                    if (domain == null || domain.isBlank()) continue;

                    Path domFile = WowoolPaths.resolveDomainFile(engine.config().wowoolRoot(), langName, domain)
                        .orElseThrow(() -> new WowoolException("Domain file for '" + domain + "' (language: " + langName + ") not found."));

                    MemorySegment domainPtr = WowoolNative.loadDomain(
                        engine.nativePointer(),
                        domFile.toAbsolutePath().toString(),
                        "{}",
                        arena,
                        exInfo
                    );
                    domainPtrs.add(domainPtr);
                    resolvedDomainNames.add(domain);
                }
            }

            return new WowoolPipeline(
                engine,
                languagePath,
                analyzerPtr,
                Collections.unmodifiableList(domainPtrs),
                Collections.unmodifiableList(resolvedDomainNames)
            );
        } catch (Throwable t) {
            // Clean up native resources on failure to avoid off-heap memory leaks
            for (MemorySegment dPtr : domainPtrs) {
                try {
                    WowoolNative.decRef(dPtr, exInfo);
                } catch (Throwable ignored) {}
            }
            try {
                WowoolNative.decRef(analyzerPtr, exInfo);
            } catch (Throwable ignored) {}
            throw (t instanceof RuntimeException re) ? re : new WowoolException("Failed to initialize pipeline", t);
        }
    }

    /**
     * Analyzes the given text using the native C++ engine and configured domains.
     *
     * @param text The input text to analyze
     * @return A strongly typed {@link WowoolDocument}
     */
    public synchronized WowoolDocument analyze(String text) {
        ensureOpen();
        if (text == null) text = "";

        try (Arena callArena = Arena.ofConfined()) {
            MemorySegment exInfo = WowoolNative.allocateExceptionInfo(callArena);

            // 1. Native linguistic analysis
            MemorySegment currentResultPtr = WowoolNative.analyzeDocument(
                analyzerPtr,
                text,
                "{}",
                callArena,
                exInfo
            );

            // 2. Pass result sequentially through loaded domains
            for (MemorySegment domPtr : domainPtrs) {
                currentResultPtr = WowoolNative.processDomain(domPtr, currentResultPtr, exInfo);
            }

            // 3. Extract JSON buffer
            byte[] jsonBytes = WowoolNative.getResultJsonBytes(currentResultPtr, callArena, exInfo);
            String rawJson = new String(jsonBytes, StandardCharsets.UTF_8);

            // 4. Release result object in native memory
            WowoolNative.decRef(currentResultPtr, exInfo);

            // 5. Parse into typed document
            try {
                return WowoolJsonParser.parse(text, rawJson);
            } catch (IOException e) {
                throw new WowoolException("Failed to deserialize native Wowool JSON output: " + e.getMessage(), e);
            }
        }
    }

    public List<String> domainNames() {
        return domainNames;
    }

    public Path languagePath() {
        return languagePath;
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("WowoolPipeline has been closed.");
        }
    }

    private static String extractLanguageName(Path langPath) {
        String fn = langPath.getFileName().toString();
        int at = fn.indexOf('@');
        if (at > 0) return fn.substring(0, at);
        int dot = fn.indexOf('.');
        if (dot > 0) return fn.substring(0, dot);
        return fn;
    }

    @Override
    public synchronized void close() {
        if (closed.compareAndSet(false, true)) {
            try (Arena temp = Arena.ofConfined()) {
                MemorySegment exInfo = WowoolNative.allocateExceptionInfo(temp);
                for (MemorySegment domPtr : domainPtrs) {
                    WowoolNative.decRef(domPtr, exInfo);
                }
                WowoolNative.decRef(analyzerPtr, exInfo);
            }
        }
    }
}
