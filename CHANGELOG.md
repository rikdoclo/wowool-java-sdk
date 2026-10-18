# Changelog

All notable changes to the **Wowool Java SDK** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.0.0] - 2026-10-18

### Overview

Wowool Java SDK v2.0.0 is a flagship major release delivering high-performance, idiomatic Java 22+ symbolic NLP capabilities. This release combines the raw speed of Project Panama Foreign Function & Memory (FFM) with Project Loom Virtual Threads, introduces a type-safe fluent Rule DSL, enriches `WowoolDocument` with spatial context windowing, and provides resilient multi-platform discovery across macOS and Linux architectures.

**Normalized Systems Theory (NST) Alignment:** The SDK architecture is formalized around the four core theorems of Normalized Systems Theory (Mannaert & Verelst) to eliminate combinatorial effects: strict Separation of Concerns (SoC) verified by ArchUnit DAG tests, Data Version Transparency (DVT) via immutable records and non-breaking API evolution, Action Version Transparency (AVT) via pluggable domain pipelines, and Separation of States (SoS) via confined Panama arenas and unpinned pipeline leases.

**100% Non-Breaking Backward Compatibility:** All existing v1.0.0 public APIs (`WowoolEngine`, `WowoolPipeline`, `DomainCompiler`, `LanguageIdentifier`, `WowoolDocument`, and the sealed `Annotation` hierarchy) remain completely functional without breaking changes.

---

### Added

#### 1. Type-Safe Fluent Rule DSL (`com.wowool.sdk.rule`)
- **Declarative Rule Builder (`WowoolRule`):** Construct syntactically validated Wowool extraction rules at compile-time using a fluent Java builder without manual string concatenation.
- **Pattern Combinators (`Patterns`):**
  - `Patterns.literal("...")`: Surface form exact matching.
  - `Patterns.lemma("...")`: Morphological stem and dictionary lemma matching.
  - `Patterns.property("...")`: Grammatical property and POS feature matching (e.g. `+noun`, `+verb`).
  - `Patterns.concept("...")`: Upstream semantic concept or entity matching (e.g. `Person`, `Organization`).
  - `Patterns.gap()`: Flexible wildcard gap matching intervening tokens (`..`).
  - `Patterns.choice(...)`: Logical disjunction between alternative patterns.
  - `Patterns.optional(...)`: Optional pattern matching.
  - `Sequence.of(...)`: Ordered sequential pattern grouping.
- **Direct Domain Compilation:** Integrated `compiler.compileRule(WowoolRule, Path, String)` and `compiler.compileRules(RuleSet, Path, String)` in `DomainCompiler` to compile rules directly into native binary `.dom` files.
- **Rule Bundling (`RuleSet`):** Group multiple `WowoolRule` instances into immutable rule sets for batch compilation.
- **JPMS Module Export:** Exported `com.wowool.sdk.rule` in `module-info.java`.

#### 2. Project Loom Virtual Threads & Stream Processing (`com.wowool.sdk`)
- **Concurrent Batch Analysis (`WowoolPipelinePool.analyzeBatch`):** Process document collections in parallel using unpinned Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`), preserving input order.
- **First-Class Stream Pipelines (`analyzeStream`):** Lazy, high-throughput streaming analysis (`Stream<String> -> Stream<WowoolDocument>`) multiplexed across pooled pipeline instances.
- **Asynchronous Execution (`analyzeAsync`):** Non-blocking `CompletableFuture<WowoolDocument>` execution for reactive microservice architectures.
- **Unpinned Carrier Thread Execution:** Refactored pipeline internal locking to `java.util.concurrent.locks.ReentrantLock`, eliminating JVM monitor carrier thread pinning during native C-ABI downcalls.

#### 3. Spatial & Linguistic Navigation (`com.wowool.sdk.model.WowoolDocument`)
- **Spatial Windowing:**
  - `tokensBefore(Annotation span, int count)`: Retrieve the *N* tokens immediately preceding a span.
  - `tokensAfter(Annotation span, int count)`: Retrieve the *N* tokens immediately following a span.
  - `window(Annotation span, int margin)`: Extract a symmetric context window (preceding tokens + span tokens + following tokens).
  - `window(Annotation span, int beforeCount, int afterCount)`: Extract an asymmetric contextual token window.
- **Spatial Overlap Queries:**
  - `overlapping(Annotation span)`: Retrieve all annotations intersecting the span's character boundaries.
  - `overlappingTokens(Annotation span)`: Retrieve all tokens that overlap with the target span.
  - `overlappingEntities(Annotation span)`: Retrieve all entities intersecting the target span.
- **Convenience Linguistic Filtering:**
  - `tokensByLemma(String lemma)`: Filter document tokens by normalized dictionary lemma.
  - `tokensByPos(String pos)`: Filter tokens by part-of-speech tag.
  - `tokensByFlag(String flag)`: Filter tokens by morphological flags (e.g. `init-cap`).
  - `lemmas()` & `posTags()`: Set views of all distinct lemmas and POS tags present in the document.
  - `hasLemma(String)` & `hasPos(String)`: Fast boolean membership checks.
  - `findTokens(Predicate<TokenSpan>)`: Arbitrary functional predicate search over tokens.

#### 4. Resilient Multi-Platform Discovery & Diagnostics (`com.wowool.sdk.internal.ffi`)
- **Multi-Architecture Resolution:** Automated platform detection supporting Darwin (Apple Silicon `aarch64` and Intel `x86_64`) and Linux (`x86_64`, `aarch64`, Debian/Ubuntu multi-arch paths).
- **Actionable Diagnostics:** Rich diagnostic error messages generated when native shared libraries (`libwowool.*`) or linguistic models (`lxware/wowool`) are not found, listing detected OS/architecture, probed file paths, and step-by-step remediation instructions.

#### 5. Local Verification & Quality Gate Harness
- **Comprehensive Verification Script (`scripts/local-verify.sh`):** Unified local test harness supporting `--fast`, `--format`, and `--full` verification modes.
- **Bytecode Architecture Enforcement (`ArchitectureArchUnitTest`):** ArchUnit rules enforcing strict Directed Acyclic Graph (DAG) package dependencies and FFM native downcall encapsulation.
- **Deterministic Regression Testing (`DeterministicFingerprintTest`):** Cryptographic SHA-256 canonical output fingerprint verification guaranteeing bit-for-bit behavioral stability.
- **Reproducible Academic Benchmarks (`BenchmarkSuiteTest`):** Automated benchmark suite measuring P50/P90/P99 latency, tokens/sec throughput, and multi-core scaling metrics as reported in the published academic paper (DOI: `10.5281/zenodo.23441610`).

---

### Changed

- **`pom.xml`:** Version bumped from `1.0.0` to `2.0.0`.
- **`DomainCompiler`:** Added overloaded compilation methods accepting `WowoolRule` and `RuleSet`.
- **`WowoolPipeline`:** Converted `synchronized` method locks to `ReentrantLock` for unpinned Virtual Thread execution.
- **`README.md` & `docs/DEVELOPER_GUIDE.md`:** Updated with v2.0.0 architecture guides, fluent DSL examples, Virtual Thread patterns, OCI container configurations, and empirical Panama FFM benchmark tables.

---

## [1.0.0] - 2026-10-16

### Initial Release

- Initial public release of the official **Wowool Java SDK** powered by Java 22+ Project Panama (FFM API / JEP 454).
- Direct C-ABI native downcalls (`tir_api_*`) to `libwowool.3.3.2.dylib` / `libwowool.so` without intermediate JNI glue.
- **Core Components:**
  - `WowoolEngine`: Root factory managing native engine lifecycle and shared memory arenas.
  - `WowoolPipeline`: Sequential domain cascading over native language analyzers.
  - `WowoolPipelinePool`: Fixed-size pipeline pool with blocking leases for multi-threaded workloads.
  - `LanguageIdentifier`: Sub-millisecond multilingual text classification.
  - `DomainCompiler`: On-the-fly compilation of `.wow` domain syntax to binary `.dom` files.
- **Document Model:**
  - Sealed `Annotation` hierarchy: `TokenSpan`, `EntitySpan`, and `SentenceSpan`.
  - Immutable Java records with exhaustive pattern matching support.
  - Sentence-level entity and token containment queries.
- **Off-Heap Memory Management:**
  - Confined arena scopes (`Arena.ofConfined`) for zero-overhead, GC-free document parsing.
  - Reference counting integration (`tir_api_dec_ref`).
