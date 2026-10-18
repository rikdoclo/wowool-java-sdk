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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Automatic language identification using the native Wowool Language Identifier
 * (LID) engine. Detects languages (such as "dutch", "english", "french",
 * "german", "spanish") with sub-millisecond latency.
 */
public final class LanguageIdentifier implements AutoCloseable {

    private final WowoolEngine engine;
    private final MemorySegment lidPtr;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private LanguageIdentifier(WowoolEngine engine, MemorySegment lidPtr) {
        this.engine = engine;
        this.lidPtr = lidPtr;
    }

    static LanguageIdentifier create(WowoolEngine engine) {
        Arena arena = engine.arena();
        MemorySegment exInfo = WowoolNative.allocateExceptionInfo(arena);
        MemorySegment lidPtr = WowoolNative.createLid(engine.nativePointer(), "{}", arena, exInfo);
        return new LanguageIdentifier(engine, lidPtr);
    }

    /**
     * Identifies the primary language of the specified text.
     *
     * @param text
     *            The input text to analyze
     * @return Language code (e.g. "dutch", "english", "french", "german") or
     *         "unknown"
     */
    public synchronized String identify(String text) {
        ensureOpen();
        if (text == null || text.isBlank()) {
            return "unknown";
        }

        try (Arena callArena = Arena.ofConfined()) {
            MemorySegment exInfo = WowoolNative.allocateExceptionInfo(callArena);
            return WowoolNative.identifyLanguage(lidPtr, text, callArena, exInfo);
        }
    }

    /**
     * Performs a granular section-based language analysis and returns the native
     * JSON output.
     *
     * @param text
     *            The input text to analyze
     * @return JSON string representing detected language sections and confidence
     *         scores
     */
    public synchronized String identifySectionJson(String text) {
        ensureOpen();
        if (text == null || text.isBlank()) {
            return "{}";
        }

        try (Arena callArena = Arena.ofConfined()) {
            MemorySegment exInfo = WowoolNative.allocateExceptionInfo(callArena);
            return WowoolNative.identifyLanguageSectionJson(lidPtr, text, callArena, exInfo);
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("LanguageIdentifier has been closed.");
        }
    }

    @Override
    public synchronized void close() {
        if (closed.compareAndSet(false, true)) {
            try (Arena temp = Arena.ofConfined()) {
                MemorySegment exInfo = WowoolNative.allocateExceptionInfo(temp);
                WowoolNative.decRef(lidPtr, exInfo);
            }
        }
    }
}
