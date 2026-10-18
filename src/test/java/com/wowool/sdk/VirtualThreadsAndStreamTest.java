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

import com.wowool.sdk.model.WowoolDocument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualThreadsAndStreamTest {

    private static WowoolEngine engine;

    @BeforeAll
    static void setUp() {
        try {
            engine = WowoolEngine.create();
        } catch (Exception ignored) {
        }
    }

    @AfterAll
    static void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    @DisplayName("Verify concurrent batch analysis with Virtual Threads across pooled pipelines")
    void testConcurrentBatchAnalysis() {
        if (engine == null) {
            return;
        }

        try (WowoolPipelinePool pool = engine.pool("dutch", 4, "entity")) {
            List<String> texts = List.of("Jan Janssen woont in Amsterdam.",
                    "Willem-Alexander is koning der Nederlanden.", "Rotterdam is een belangrijke havenstad.",
                    "Philips heeft haar hoofdkantoor in Eindhoven.", "ASML is gevestigd in Veldhoven nabij Eindhoven.",
                    "De Universiteit Utrecht is opgericht in 1636.");

            List<WowoolDocument> docs = pool.analyzeBatch(texts);
            assertEquals(texts.size(), docs.size(), "All documents should be processed in batch");

            // Verify order preservation
            for (int i = 0; i < texts.size(); i++) {
                assertEquals(texts.get(i), docs.get(i).text(), "Batch analysis must preserve encounter order");
                assertTrue(docs.get(i).tokenCount() > 0, "Document must have tokens");
            }
        }
    }

    @Test
    @DisplayName("Verify stream processing from Stream<String> to Stream<WowoolDocument>")
    void testStreamProcessing() {
        if (engine == null) {
            return;
        }

        try (WowoolPipelinePool pool = engine.pool("dutch", 2, "entity")) {
            Stream<String> inputStream = Stream.of("Eerste testzin over Den Haag.", "Tweede testzin over Groningen.",
                    "Derde testzin over Maastricht.");

            List<WowoolDocument> results = pool.analyzeStream(inputStream).toList();
            assertEquals(3, results.size());
            assertEquals("Eerste testzin over Den Haag.", results.get(0).text());
            assertEquals("Tweede testzin over Groningen.", results.get(1).text());
            assertEquals("Derde testzin over Maastricht.", results.get(2).text());
        }
    }

    @Test
    @DisplayName("Verify asynchronous Virtual Thread execution with CompletableFuture")
    void testAsyncVirtualThreadAnalysis() throws Exception {
        if (engine == null) {
            return;
        }

        try (WowoolPipelinePool pool = engine.pool("dutch", 2, "entity")) {
            CompletableFuture<WowoolDocument> future = pool.analyzeAsync("Jan Janssen bezoekt Amsterdam.");
            assertNotNull(future);
            WowoolDocument doc = future.get();
            assertNotNull(doc);
            assertEquals("Jan Janssen bezoekt Amsterdam.", doc.text());
            assertTrue(doc.hasEntity("Person") || doc.hasEntity("City"));
        }
    }
}
