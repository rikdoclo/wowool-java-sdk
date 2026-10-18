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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Thread-safe object pool for {@link WowoolPipeline} instances. Enables
 * concurrent multi-threaded text analysis in backend servers (e.g., Spring
 * Boot, Quarkus, Obscuro).
 */
public final class WowoolPipelinePool implements AutoCloseable {

    private final WowoolEngine engine;
    private final String language;
    private final List<String> domains;
    private final int capacity;
    private final BlockingQueue<WowoolPipeline> available;
    private final List<WowoolPipeline> allPipelines;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * Initializes a pool of pre-warmed {@link WowoolPipeline} instances.
     *
     * @param engine
     *            The parent {@link WowoolEngine}
     * @param language
     *            Language name (e.g. "dutch", "english")
     * @param capacity
     *            Number of concurrent pipelines to maintain
     * @param domains
     *            List of domains to cascade
     */
    public WowoolPipelinePool(WowoolEngine engine, String language, int capacity, List<String> domains) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Pool capacity must be at least 1, got: " + capacity);
        }
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.language = Objects.requireNonNull(language, "language must not be null");
        this.domains = (domains == null) ? Collections.emptyList() : List.copyOf(domains);
        this.capacity = capacity;
        this.available = new ArrayBlockingQueue<>(capacity);
        this.allPipelines = new ArrayList<>(capacity);

        try {
            for (int i = 0; i < capacity; i++) {
                WowoolPipeline pipeline = engine.createPipeline(language, this.domains);
                allPipelines.add(pipeline);
                available.offer(pipeline);
            }
        } catch (Throwable t) {
            close();
            throw (t instanceof RuntimeException re)
                    ? re
                    : new WowoolException("Failed to initialize pipeline pool", t);
        }
    }

    /**
     * Acquires an available pipeline from the pool, blocking indefinitely until one
     * is available.
     *
     * @return An active {@link WowoolPipeline}
     */
    public WowoolPipeline acquire() {
        return acquire(Duration.ofMillis(Long.MAX_VALUE));
    }

    /**
     * Acquires an available pipeline from the pool, waiting up to the specified
     * duration.
     *
     * @param timeout
     *            Maximum wait time
     * @return An active {@link WowoolPipeline}
     * @throws WowoolException
     *             if timeout expires or pool is closed
     */
    public WowoolPipeline acquire(Duration timeout) {
        ensureOpen();
        try {
            WowoolPipeline pipeline = available.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (pipeline == null) {
                throw new WowoolException(
                        "Timeout waiting for available pipeline in pool (capacity: " + capacity + ")");
            }
            return pipeline;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WowoolException("Thread interrupted while waiting for pipeline from pool", e);
        }
    }

    /**
     * Returns a borrowed pipeline back to the pool.
     *
     * @param pipeline
     *            Pipeline to return
     */
    public void release(WowoolPipeline pipeline) {
        if (pipeline == null) {
            return;
        }
        if (closed.get()) {
            pipeline.close();
            return;
        }
        available.offer(pipeline);
    }

    /**
     * Executes an action with a leased pipeline, automatically releasing it back to
     * the pool when finished.
     *
     * @param action
     *            The function to execute
     * @param <T>
     *            The return type
     * @return Result of the action
     */
    public <T> T withPipeline(Function<WowoolPipeline, T> action) {
        WowoolPipeline pipeline = acquire();
        try {
            return action.apply(pipeline);
        } finally {
            release(pipeline);
        }
    }

    /**
     * Convenience method to analyze text concurrently using a borrowed pipeline
     * from the pool.
     *
     * @param text
     *            Text to analyze
     * @return Analyzed {@link WowoolDocument}
     */
    public WowoolDocument analyze(String text) {
        return withPipeline(pipeline -> pipeline.analyze(text));
    }

    /**
     * Concurrently analyzes a batch of documents using Project Loom Virtual
     * Threads. Pipelines from this pool are multiplexed across virtual threads
     * without pinning OS carrier threads. Results preserve the exact encounter
     * order of the input collection.
     *
     * @param texts
     *            Collection of texts to analyze
     * @return Ordered list of analyzed {@link WowoolDocument}s
     */
    public List<WowoolDocument> analyzeBatch(Collection<String> texts) {
        Objects.requireNonNull(texts, "texts must not be null");
        ensureOpen();
        if (texts.isEmpty()) {
            return Collections.emptyList();
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<WowoolDocument>> futures = texts.stream()
                    .map(text -> CompletableFuture.supplyAsync(() -> analyze(text), executor)).toList();
            List<WowoolDocument> results = new ArrayList<>(futures.size());
            for (CompletableFuture<WowoolDocument> future : futures) {
                try {
                    results.add(future.join());
                } catch (CompletionException ce) {
                    Throwable cause = ce.getCause();
                    if (cause instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new WowoolException("Batch analysis failed: " + cause.getMessage(), cause);
                }
            }
            return Collections.unmodifiableList(results);
        }
    }

    /**
     * Analyzes a stream of texts into a stream of {@link WowoolDocument}s, leasing
     * pipelines from the pool for each element.
     *
     * @param texts
     *            Stream of texts to analyze
     * @return Stream of analyzed documents
     */
    public Stream<WowoolDocument> analyzeStream(Stream<String> texts) {
        Objects.requireNonNull(texts, "texts must not be null");
        ensureOpen();
        return texts.map(this::analyze);
    }

    /**
     * Concurrently analyzes a collection of texts across virtual threads and
     * returns an ordered stream of {@link WowoolDocument}s.
     *
     * @param texts
     *            Collection of texts to analyze
     * @return Stream of analyzed documents in encounter order
     */
    public Stream<WowoolDocument> analyzeStream(Collection<String> texts) {
        return analyzeBatch(texts).stream();
    }

    /**
     * Asynchronously analyzes the specified text on an unpinned Virtual Thread.
     *
     * @param text
     *            Text to analyze
     * @return {@link CompletableFuture} completing with the analyzed
     *         {@link WowoolDocument}
     */
    public CompletableFuture<WowoolDocument> analyzeAsync(String text) {
        return analyzeAsync(text, virtualThreadExecutor);
    }

    /**
     * Asynchronously analyzes the specified text using the provided executor.
     *
     * @param text
     *            Text to analyze
     * @param executor
     *            Executor to schedule the analysis task on
     * @return {@link CompletableFuture} completing with the analyzed
     *         {@link WowoolDocument}
     */
    public CompletableFuture<WowoolDocument> analyzeAsync(String text, Executor executor) {
        Objects.requireNonNull(executor, "executor must not be null");
        ensureOpen();
        return CompletableFuture.supplyAsync(() -> analyze(text), executor);
    }

    /**
     * Returns the total capacity of the pool.
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Returns the number of currently idle pipelines available in the pool.
     */
    public int availableCount() {
        return available.size();
    }

    /**
     * Closes the pool and terminates all underlying native pipelines and background
     * executors.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            virtualThreadExecutor.shutdown();
            available.clear();
            for (WowoolPipeline pipeline : allPipelines) {
                try {
                    pipeline.close();
                } catch (Throwable ignored) {
                }
            }
            allPipelines.clear();
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("WowoolPipelinePool is closed.");
        }
    }
}
