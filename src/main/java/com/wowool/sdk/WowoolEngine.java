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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Primary entry point for the native Wowool C++ Engine via Project Panama (JEP
 * 454). Manages the native engine instance and serves as a factory for
 * {@link WowoolPipeline}s, {@link LanguageIdentifier}s, and
 * {@link DomainCompiler}s. Implements {@link AutoCloseable} for deterministic
 * native memory management.
 */
public final class WowoolEngine implements AutoCloseable {

    private final WowoolConfig config;
    private final Arena engineArena;
    private final MemorySegment enginePtr;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private WowoolEngine(WowoolConfig config, Arena engineArena, MemorySegment enginePtr) {
        this.config = config;
        this.engineArena = engineArena;
        this.enginePtr = enginePtr;
    }

    /**
     * Creates a new WowoolEngine using default discovery rules for license keys and
     * model directories.
     */
    public static WowoolEngine create() {
        return create(WowoolConfig.defaults());
    }

    /**
     * Creates a new WowoolEngine with an explicit commercial license key.
     */
    public static WowoolEngine create(String licenseKey) {
        return create(WowoolConfig.builder().licenseKey(licenseKey).build());
    }

    /**
     * Creates a new WowoolEngine using explicit configuration parameters.
     */
    public static WowoolEngine create(WowoolConfig config) {
        WowoolNative.ensureLoaded(config.dylibDir());

        Arena arena = Arena.ofShared();
        MemorySegment exInfo = WowoolNative.allocateExceptionInfo(arena);

        String engineOpts = "{\"license_key\":\"" + config.licenseKey() + "\"}";
        MemorySegment enginePtr = WowoolNative.createEngine(engineOpts, arena, exInfo);

        return new WowoolEngine(config, arena, enginePtr);
    }

    /**
     * Creates an analysis pipeline for the specified language and optional domain
     * models. Example: {@code engine.pipeline("dutch", "entity")}
     *
     * @param language
     *            Language name (e.g., "dutch", "english", "french", "german")
     * @param domains
     *            Optional domain names (e.g., "entity", "numbers", "sentiment") or
     *            paths to .dom files
     * @return A configured {@link WowoolPipeline}
     */
    public WowoolPipeline pipeline(String language, String... domains) {
        return createPipeline(language, Arrays.asList(domains));
    }

    /**
     * Creates an analysis pipeline with a list of domains.
     */
    public WowoolPipeline createPipeline(String language, List<String> domains) {
        ensureOpen();

        Path langFile = WowoolPaths.resolveLanguageFile(config.wowoolRoot(), language)
                .orElseThrow(() -> new WowoolException("Language file for '" + language + "' not found under "
                        + config.wowoolRoot() + ". Probed directories: "
                        + WowoolPaths.getModelSearchDirectories(config.wowoolRoot())));

        return WowoolPipeline.create(this, langFile, domains);
    }

    /**
     * Creates a pooled pipeline instance for high-throughput multi-threaded
     * concurrent analysis.
     *
     * @param language
     *            Language name (e.g., "dutch", "english")
     * @param capacity
     *            Number of pipelines to maintain in pool
     * @param domains
     *            Optional domain names or .dom file paths
     * @return A thread-safe {@link WowoolPipelinePool}
     */
    public WowoolPipelinePool pool(String language, int capacity, String... domains) {
        return createPool(language, capacity, Arrays.asList(domains));
    }

    /**
     * Creates a pooled pipeline instance for high-throughput multi-threaded
     * concurrent analysis.
     *
     * @param language
     *            Language name (e.g., "dutch", "english")
     * @param capacity
     *            Number of pipelines to maintain in pool
     * @param domains
     *            List of domains to cascade
     * @return A thread-safe {@link WowoolPipelinePool}
     */
    public WowoolPipelinePool createPool(String language, int capacity, List<String> domains) {
        ensureOpen();
        return new WowoolPipelinePool(this, language, capacity, domains);
    }

    /**
     * Creates a new {@link LanguageIdentifier} for sub-millisecond language
     * identification.
     */
    public LanguageIdentifier languageIdentifier() {
        ensureOpen();
        return LanguageIdentifier.create(this);
    }

    /**
     * Creates a {@link DomainCompiler} to compile .wow / .woc rule files to binary
     * .dom files.
     */
    public DomainCompiler compiler() {
        ensureOpen();
        return new DomainCompiler(this);
    }

    /**
     * Retrieves the native engine runtime capabilities and version metadata as a
     * JSON string.
     */
    public String info() {
        ensureOpen();
        try (Arena callArena = Arena.ofConfined()) {
            MemorySegment exInfo = WowoolNative.allocateExceptionInfo(callArena);
            return WowoolNative.getEngineInfo(enginePtr, callArena, exInfo);
        }
    }

    /**
     * Retrieves registered languages and dictionaries in the native engine as a
     * JSON string.
     */
    public String languages() {
        ensureOpen();
        try (Arena callArena = Arena.ofConfined()) {
            MemorySegment exInfo = WowoolNative.allocateExceptionInfo(callArena);
            return WowoolNative.getEngineLanguages(enginePtr, callArena, exInfo);
        }
    }

    public WowoolConfig config() {
        return config;
    }

    public MemorySegment nativePointer() {
        ensureOpen();
        return enginePtr;
    }

    public Arena arena() {
        return engineArena;
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("WowoolEngine has been closed.");
        }
    }

    @Override
    public synchronized void close() {
        if (closed.compareAndSet(false, true)) {
            try (Arena temp = Arena.ofConfined()) {
                MemorySegment exInfo = WowoolNative.allocateExceptionInfo(temp);
                WowoolNative.decRef(enginePtr, exInfo);
            } finally {
                engineArena.close();
            }
        }
    }
}
