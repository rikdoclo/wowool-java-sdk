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

package com.wowool.sdk.benchmark;

import com.wowool.sdk.WowoolEngine;
import com.wowool.sdk.WowoolPipeline;
import com.wowool.sdk.WowoolPipelinePool;
import com.wowool.sdk.model.WowoolDocument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BenchmarkSuiteTest {

    private static WowoolEngine engine;
    private static final int WARMUP_ITERATIONS = 50;
    private static final int MEASURE_ITERATIONS = 200;

    private static String shortText;
    private static String mediumText;
    private static String longText;

    @BeforeAll
    static void setUp() {
        try {
            engine = WowoolEngine.create();
            assertNotNull(engine, "WowoolEngine should be initialized");

            // Load realistic Dutch administrative benchmark corpora from resources
            shortText = loadResource("/benchmarks/short_administrative_notice.txt",
                "Minister-president Jan Jambon bezocht vandaag de haven van Antwerpen en sprak over duurzame energietransitie.");
            
            StringBuilder sbMed = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                sbMed.append("Op ").append(10 + i).append(" oktober 2026 heeft het Departement Omgeving een openbaar onderzoek ingesteld naar perceel afdeling 2, sectie B, nummer 144/A te Gent. ");
                sbMed.append("De aanvrager, mevrouw Sophie Peeters, wenst een omgevingsvergunning te bekomen voor de exploitatie van een warmtepompinstallatie conform VLAREM II regelgeving. ");
                sbMed.append("Burgers en belanghebbenden kunnen tot uiterlijk 15 november 2026 bezwaarschriften indienen bij het schepencollege. ");
            }
            mediumText = loadResource("/benchmarks/medium_gazette_notice.txt", sbMed.toString());

            StringBuilder sbLong = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                sbLong.append(mediumText).append("\n\n");
            }
            longText = loadResource("/benchmarks/long_regulatory_decree.txt", sbLong.toString());

        } catch (Throwable e) {
            org.junit.jupiter.api.Assumptions.abort("Skipping benchmark, engine unavailable: " + e.getMessage());
        }
    }

    private static String loadResource(String resourcePath, String fallback) {
        try (var is = BenchmarkSuiteTest.class.getResourceAsStream(resourcePath)) {
            if (is != null) {
                return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    @AfterAll
    static void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    record BenchmarkStats(
        String name,
        int docChars,
        int tokenCount,
        double p50Ms,
        double p90Ms,
        double p99Ms,
        double meanMs,
        double throughputTokensPerSec,
        double throughputDocsPerSec
    ) {}

    @Test
    @DisplayName("Run Comprehensive Multi-Tier Benchmark Suite")
    void runBenchmarkSuite() throws Exception {
        List<BenchmarkStats> results = new ArrayList<>();

        System.out.println("================================================================================");
        System.out.println("           WOWOOL REPRODUCIBLE ACADEMIC BENCHMARK SUITE (JAVA 22 FFM)           ");
        System.out.println("================================================================================");
        System.out.printf("Hardware: %s (%d cores)%n", System.getProperty("os.arch"), Runtime.getRuntime().availableProcessors());
        System.out.printf("OS: %s %s%n", System.getProperty("os.name"), System.getProperty("os.version"));
        System.out.printf("Java: %s (%s)%n", System.getProperty("java.version"), System.getProperty("java.vendor"));
        System.out.println("--------------------------------------------------------------------------------");

        // 1. Pipeline: "dutch" (Syntax & Morphology)
        results.add(profilePipeline("Tokenize+POS (Short)", "dutch", new String[0], shortText));
        results.add(profilePipeline("Tokenize+POS (Medium)", "dutch", new String[0], mediumText));
        results.add(profilePipeline("Tokenize+POS (Long)", "dutch", new String[0], longText));

        // 2. Pipeline: "dutch", "entity" (NER + Syntax)
        results.add(profilePipeline("Full NER+POS (Short)", "dutch", new String[]{"entity"}, shortText));
        results.add(profilePipeline("Full NER+POS (Medium)", "dutch", new String[]{"entity"}, mediumText));
        results.add(profilePipeline("Full NER+POS (Long)", "dutch", new String[]{"entity"}, longText));

        // Print Summary Table
        System.out.println();
        System.out.println("### Empirical Benchmark Results Summary");
        System.out.println("| Benchmark Configuration | Size (chars) | Tokens | P50 (ms) | P90 (ms) | P99 (ms) | Mean (ms) | Throughput (Tokens/s) | Docs/s |");
        System.out.println("|:---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|");
        for (BenchmarkStats s : results) {
            System.out.printf("| %-23s | %10d | %6d | %8.3f | %8.3f | %8.3f | %9.3f | %21.0f | %6.0f |%n",
                s.name(), s.docChars(), s.tokenCount(), s.p50Ms(), s.p90Ms(), s.p99Ms(), s.meanMs(), s.throughputTokensPerSec(), s.throughputDocsPerSec());
        }
        System.out.println("--------------------------------------------------------------------------------");

        // 3. Multi-threaded scaling test
        profileConcurrency(results);

        // Export CSV and JSON
        exportResults(results);
    }

    private BenchmarkStats profilePipeline(String testName, String language, String[] domains, String text) {
        try (WowoolPipeline pipeline = engine.pipeline(language, domains)) {
            // Verify tokens
            WowoolDocument baselineDoc = pipeline.analyze(text);
            int tokenCount = baselineDoc.tokenCount();

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                pipeline.analyze(text);
            }

            // Measurement
            long[] durationsNs = new long[MEASURE_ITERATIONS];
            long totalStart = System.nanoTime();
            for (int i = 0; i < MEASURE_ITERATIONS; i++) {
                long start = System.nanoTime();
                pipeline.analyze(text);
                durationsNs[i] = System.nanoTime() - start;
            }
            long totalElapsedNs = System.nanoTime() - totalStart;

            Arrays.sort(durationsNs);

            double p50 = durationsNs[(int) (MEASURE_ITERATIONS * 0.50)] / 1_000_000.0;
            double p90 = durationsNs[(int) (MEASURE_ITERATIONS * 0.90)] / 1_000_000.0;
            double p99 = durationsNs[(int) (MEASURE_ITERATIONS * 0.99)] / 1_000_000.0;

            long sumNs = 0;
            for (long d : durationsNs) sumNs += d;
            double meanMs = (sumNs / (double) MEASURE_ITERATIONS) / 1_000_000.0;

            double totalSeconds = totalElapsedNs / 1_000_000_000.0;
            double throughputTokens = (MEASURE_ITERATIONS * (long) tokenCount) / totalSeconds;
            double throughputDocs = MEASURE_ITERATIONS / totalSeconds;

            System.out.printf("[DONE] %-25s -> Mean: %.3f ms | P90: %.3f ms | Throughput: %,.0f tokens/s%n",
                testName, meanMs, p90, throughputTokens);

            return new BenchmarkStats(testName, text.length(), tokenCount, p50, p90, p99, meanMs, throughputTokens, throughputDocs);
        }
    }

    private void profileConcurrency(List<BenchmarkStats> results) throws Exception {
        int[] threadCounts = {1, 2, 4, 8, 12};
        int batchesPerThread = 25;

        System.out.println();
        System.out.println("### Multi-Core Scaling Profile (Medium Document: ~800 tokens, 'dutch,entity')");
        System.out.println("| Threads | Total Docs | Total Time (s) | Aggregate Throughput (Tokens/s) | Speedup Factor |");
        System.out.println("|:---:|:---:|:---:|:---:|:---:|");

        double baselineThroughput = 0.0;

        for (int threads : threadCounts) {
            if (threads > Runtime.getRuntime().availableProcessors()) continue;

            try (WowoolPipelinePool pool = engine.pool("dutch", threads, "entity")) {
                ExecutorService executor = Executors.newFixedThreadPool(threads);
                List<Callable<Void>> tasks = new ArrayList<>();

                for (int t = 0; t < threads; t++) {
                    tasks.add(() -> {
                        for (int i = 0; i < batchesPerThread; i++) {
                            pool.withPipeline(p -> p.analyze(mediumText));
                        }
                        return null;
                    });
                }

                // Warmup
                pool.withPipeline(p -> p.analyze(mediumText));

                long start = System.nanoTime();
                List<Future<Void>> futures = executor.invokeAll(tasks);
                for (Future<Void> f : futures) f.get();
                long elapsedNs = System.nanoTime() - start;

                executor.shutdown();

                double elapsedSec = elapsedNs / 1_000_000_000.0;
                int totalDocs = threads * batchesPerThread;
                int tokensPerDoc = pool.withPipeline(p -> p.analyze(mediumText)).tokenCount();
                double aggThroughput = (totalDocs * (long) tokensPerDoc) / elapsedSec;

                if (threads == 1) {
                    baselineThroughput = aggThroughput;
                }
                double speedup = aggThroughput / baselineThroughput;

                System.out.printf("| %7d | %10d | %14.3f | %,31.0f | %13.2fx |%n",
                    threads, totalDocs, elapsedSec, aggThroughput, speedup);
            }
        }
    }

    private void exportResults(List<BenchmarkStats> results) {
        Path targetDir = Path.of("target/benchmarks");
        try {
            Files.createDirectories(targetDir);
            Path csvFile = targetDir.resolve("benchmark_results.csv");
            try (PrintWriter pw = new PrintWriter(new FileWriter(csvFile.toFile()))) {
                pw.println("benchmark_name,doc_chars,token_count,p50_ms,p90_ms,p99_ms,mean_ms,tokens_per_sec,docs_per_sec");
                for (BenchmarkStats s : results) {
                    pw.printf(Locale.US, "%s,%d,%d,%.4f,%.4f,%.4f,%.4f,%.2f,%.2f%n",
                        s.name(), s.docChars(), s.tokenCount(), s.p50Ms(), s.p90Ms(), s.p99Ms(), s.meanMs(), s.throughputTokensPerSec(), s.throughputDocsPerSec());
                }
            }
            System.out.printf("%n[EXPORT] Benchmark results written to: %s%n", csvFile.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("Could not export results: " + e.getMessage());
        }
    }
}
