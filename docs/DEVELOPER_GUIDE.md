# Wowool Java SDK — Developer Guide & Technical Reference

**Author:** Rik Doclo  
**Version:** 2.0.0  
**Repository:** [https://github.com/rikdoclo/wowool-java-sdk](https://github.com/rikdoclo/wowool-java-sdk)  
**Java Compatibility:** Java 22+ (including Java 25 LTS)  
**Associated Academic Paper:** [https://doi.org/10.5281/zenodo.23441610](https://doi.org/10.5281/zenodo.23441610)

> 📖 **Academic Research Reference:**  
> This software and its empirical evaluation accompany the published research paper:  
> **"Wowool: High-Throughput Deterministic Symbolic NLP with Multi-Language C-ABI and Project Panama FFM Interoperability"**  
> *Rik Doclo, Philippe Forest, Manuel Rubio De Hita, Nadir Grine (2026).*  
> **Permanent DOI:** [https://doi.org/10.5281/zenodo.23441610](https://doi.org/10.5281/zenodo.23441610) | **Zenodo Record:** [https://zenodo.org/records/23441610](https://zenodo.org/records/23441610)

---

## Table of Contents
1. [Architectural Foundations](#1-architectural-foundations)
   - [Why Project Panama (FFM API) over JNI & Subprocesses](#why-project-panama-ffm-api-over-jni--subprocesses)
   - [Zero-Copy Native Downcalls via the C ABI](#zero-copy-native-downcalls-via-the-c-abi)
   - [System Architecture & Layering Stack](#system-architecture--layering-stack)
   - [Strict Package DAG & Encapsulation Invariants](#strict-package-dag--encapsulation-invariants)
   - [Alignment with Normalized Systems Theory (NST)](#alignment-with-normalized-systems-theory-nst)
2. [Native Memory Lifecycle & Arena Management](#2-native-memory-lifecycle--arena-management)
   - [Shared vs Confined Arenas](#shared-vs-confined-arenas)
   - [Deterministic Memory Cleanup](#deterministic-memory-cleanup)
3. [Core Components Deep Dive](#3-core-components-deep-dive)
   - [WowoolEngine: The Root Factory](#wowoolengine-the-root-factory)
   - [WowoolPipeline: Sequential Domain Chaining](#wowoolpipeline-sequential-domain-chaining)
   - [WowoolDocument & Sealed Annotation Model](#wowooldocument--sealed-annotation-model)
   - [Rich Spatial & Linguistic Navigation](#rich-spatial--linguistic-navigation)
   - [LanguageIdentifier: High-Speed Language Classification](#languageidentifier-high-speed-language-classification)
   - [DomainCompiler: Compiling Rules on the Fly](#domaincompiler-compiling-rules-on-the-fly)
4. [Type-Safe Fluent Rule DSL (`com.wowool.sdk.rule`)](#4-type-safe-fluent-rule-dsl-comwowoolsdkrule)
5. [Concurrency: Project Loom Virtual Threads & Streams](#5-concurrency-project-loom-virtual-threads--streams)
   - [Unpinned Virtual Thread Architecture](#unpinned-virtual-thread-architecture)
   - [Batch Processing & Stream Pipelines](#batch-processing--stream-pipelines)
   - [Pipeline Pooling for Microservices](#pipeline-pooling-for-microservices)
6. [Resilient Multi-Platform Discovery & Diagnostics](#6-resilient-multi-platform-discovery--diagnostics)
   - [Standard Directory Topology](#standard-directory-topology)
   - [Step-by-Step Setup Guide by Operating System](#step-by-step-setup-guide-by-operating-system)
   - [Actionable Diagnostic Reports](#actionable-diagnostic-reports)
7. [Native Error Handling & Exception Mapping](#7-native-error-handling--exception-mapping)
8. [Enterprise Framework Integration](#8-enterprise-framework-integration)
   - [Spring Boot 3.x / 4.x Configuration](#spring-boot-3x--4x-configuration)
   - [OCI Containerization Considerations](#oci-containerization-considerations)
9. [Empirical Panama FFM Performance Benchmarks](#9-empirical-panama-ffm-performance-benchmarks)
10. [Troubleshooting & FAQ](#10-troubleshooting--faq)

---

## 1. Architectural Foundations

### Why Project Panama (FFM API) over JNI & Subprocesses

Traditional cross-language Java integration relied on two sub-optimal approaches:
1. **Java Native Interface (JNI):** Required writing intermediate C/C++ glue code, custom header generation (`javah`), manual Makefiles, and fragile pointers. A memory bug in JNI crashed the entire JVM.
2. **Subprocess / IPC (P2P):** Running a child Python process over `stdin`/`stdout` introduced IPC latency, serialization/deserialization overhead, and process lifecycle overhead.

The **Wowool Java SDK** is built directly on **Project Panama (Foreign Function & Memory API - JEP 454)**, finalized in Java 22:
- **No C++ glue:** Java links directly to symbols in `libwowool.3.3.2.dylib` / `libwowool.so` using `java.lang.foreign.Linker` and `MethodHandle`.
- **Zero-Copy Memory Segments:** JVM off-heap memory is safely allocated and addressed through `MemorySegment`.
- **Sub-Millisecond Performance:** Function invocation latency matches native C call speeds.

### Zero-Copy Native Downcalls via the C ABI

The native Wowool engine exposes its C ABI through functions prefixed with `tir_api_*`. The SDK links directly against these symbols:

| C ABI Function | Java Downcall Handle | Description |
| :--- | :--- | :--- |
| `tir_api_get_engine` | `getEngineHandle` | Instantiates native engine with license key |
| `tir_api_get_analyzer` | `getAnalyzerHandle` | Creates a language analyzer from `.language` |
| `tir_api_analyze_document`| `analyzeDocumentHandle` | Performs syntax & morphological document parsing |
| `tir_api_load_domain` | `loadDomainHandle` | Loads compiled `.dom` binary model into memory |
| `tir_api_domain_process` | `domainProcessHandle` | Cascades extraction rules over an analyzed document |
| `tir_api_get_result_json_buffer` | `getJsonBufferHandle` | Retrieves binary JSON result buffer directly from off-heap |
| `tir_api_dec_ref` | `decRefHandle` | Decrements native reference count and frees memory |
| `tir_api_compile_domain`| `compileDomainHandle` | Compiles `.wow` rule files or Base64 code into `.dom` |

### System Architecture & Layering Stack

The Wowool Java SDK follows a high-performance, three-tier architecture connecting enterprise Java applications directly to the native C++ engine via Project Panama:

![Wowool Java SDK Panama Architecture](images/wowool-java-sdk-panama-architecture.png)

### Strict Package DAG & Encapsulation Invariants

The repository enforces architectural boundaries via bytecode tests (`ArchitectureArchUnitTest`):

1. **Strict Directed Acyclic Graph (DAG):**
   - Sub-packages form a strict acyclic graph with zero package dependency cycles.
   - `com.wowool.sdk.model` contains only pure, immutable domain records and sealed interfaces with zero dependencies on FFM or compiler internals.
   - `com.wowool.sdk.rule` depends solely on standard Java collections and public SDK types.
2. **Encapsulation of Native Pointers:**
   - Raw native handles, Panama `MemorySegment` references, and `MethodHandle` invocations are strictly quarantined within `com.wowool.sdk.internal.ffi`.
   - Native memory addresses never escape into public API methods or return types.
3. **Zero-Reflection Hot Path:**
   - `com.wowool.sdk.internal.json` utilizes direct structural parsing into Java records without runtime reflection, bytecode generation, or dynamic proxies.

### Alignment with Normalized Systems Theory (NST)

The architecture of the Wowool Java SDK is designed in strict accordance with the foundational theorems of **Normalized Systems Theory (NST)** (Mannaert & Verelst), aiming to eliminate *combinatorial effects*—where small functional changes lead to an unbounded ripple effect across the codebase—and guarantee long-term evolvability:

1. **Separation of Concerns (SoC):**
   - Each layer addresses exactly one architectural concern: public API orchestration (`com.wowool.sdk`), declarative rule grammar (`com.wowool.sdk.rule`), immutable document semantics (`com.wowool.sdk.model`), or low-level off-heap C ABI binding (`com.wowool.sdk.internal.ffi`).
   - Bytecode analysis (`ArchitectureArchUnitTest`) continuously validates a strict Directed Acyclic Graph (DAG) with zero cyclic package dependencies.
2. **Data Version Transparency (DVT):**
   - The document model is formalized through strictly immutable Java records (`record`) and sealed interfaces (`Annotation`).
   - The evolution from v1.0.0 to v2.0.0 achieved 100% backward compatibility: adding richer spatial windowing methods and token navigation operators preserved existing record layouts and collection contracts without ripple effects on downstream consumers.
3. **Action Version Transparency (AVT):**
   - Core functional operations—such as domain extraction passes, language identification, and rule compilation—are modular, pluggable pipeline stages.
   - Domains (`.dom`) and rule sets (`RuleSet`) can be added, updated, or swapped independently without modifying the core analyzer or requiring recompilation of caller applications.
4. **Separation of States (SoS):**
   - Intermediate execution state is strictly isolated from application logic.
   - Document analysis operations execute within confined native scopes (`Arena.ofConfined()`), while pipeline pooling (`WowoolPipelinePool`) leases independent native analyzer pointers.
   - This eliminates shared mutable state across threads and virtual tasks, preventing race conditions and state explosion under high-concurrency Project Loom workloads.

---

## 2. Native Memory Lifecycle & Arena Management

### Shared vs Confined Arenas

Panama introduces `Arena` to control the lifetime and thread-confinement of native memory:

1. **`Arena.ofShared()` for Engine Lifetime:**
   - The root `WowoolEngine` uses a shared arena (`engineArena`).
   - Shared memory segments can be safely accessed across multiple worker threads.
   - When `engine.close()` is called, the shared arena is deterministically closed, reclaiming all engine resources.

2. **`Arena.ofConfined()` for Per-Call Operations:**
   - Methods like `pipeline.analyze(text)` or `compiler.compile(...)` use a lightweight `Arena.ofConfined()`.
   - Temporary buffers (such as the document text string and option JSON strings) are allocated in the confined arena and instantly freed when exiting the `try-with-resources` block, preventing GC pressure and off-heap leaks.

### Deterministic Memory Cleanup

Native Wowool objects are reference-counted. Whenever an intermediate analysis result or domain object is produced:
```java
WowoolNative.decRef(resultPtr, exInfo);
```
All SDK classes implement `AutoCloseable`. Always wrap engine and pipeline instances in `try-with-resources` blocks.

---

## 3. Core Components Deep Dive

### WowoolEngine: The Root Factory

`WowoolEngine` is the central singleton or managed bean in your application.

```java
// Automatic discovery:
WowoolEngine engine = WowoolEngine.create();

// Or explicit configuration:
WowoolConfig config = WowoolConfig.builder()
    .licenseKey("XYZ123...")
    .dylibDir(Path.of("/usr/local/lib/wowool"))
    .wowoolRoot(Path.of("/usr/share/wowool"))
    .build();

WowoolEngine engine = WowoolEngine.create(config);
```

### WowoolPipeline: Sequential Domain Chaining

A pipeline encapsulates a base language analyzer combined with an arbitrary chain of domain models (`.dom`):

```java
// Dutch morphology + named entity recognition + custom domains
try (WowoolPipeline pipeline = engine.pipeline("dutch", "entity", "sentiment", "/path/to/custom.dom")) {
    WowoolDocument doc = pipeline.analyze("De CEO gaf een optimistische presentatie in Amsterdam.");
}
```

**Execution Pipeline Flow:**
```
Text Input ──► [Language Analyzer] ──► [Domain 1: entity] ──► [Domain 2: sentiment] ──► WowoolDocument
```

### WowoolDocument & Sealed Annotation Model

The SDK formalizes all linguistic spans through a **sealed `Annotation` hierarchy** (`com.wowool.sdk.model.Annotation`), mirroring the theoretical annotational architecture of the Wowool C++ Blackboard:

```
                      Annotation (sealed interface)
                       ▲         ▲            ▲
                       │         │            │
            TokenSpan ─┘         │            └── SentenceSpan
                                 │
                            EntitySpan
```

#### Universal `Annotation` Interface Contracts
Every annotation implements:
- `int beginOffset()`: 0-based character start index (inclusive).
- `int endOffset()`: 0-based character end index (exclusive).
- `int length()`: Character span length (`endOffset - beginOffset`).
- Type checks: `isToken()`, `isEntity()`, `isSentence()`.
- Spatial queries:
  - `contains(Annotation other)`: Tests if another span is fully enclosed within this span.
  - `containsOffset(int offset)`: Tests if a character index falls inside `[beginOffset, endOffset)`.
  - `overlaps(Annotation other)`: Tests if two spans share overlapping character ranges.

#### Exhaustive Java 22+ Pattern Matching
Because `Annotation` is a `sealed interface`, you can traverse annotations with compiler-verified pattern matching:

```java
WowoolDocument doc = pipeline.analyze(text);

// Traverse all annotations across the document in offset order:
for (Annotation ann : doc.annotations()) {
    switch (ann) {
        case TokenSpan token ->
            System.out.printf("Token: %s | POS: %s | Lemma: %s%n", token.text(), token.pos(), token.lemma());

        case EntitySpan entity ->
            System.out.printf("Entity [%s]: '%s' (Canonical: %s)%n", entity.uri(), entity.text(), entity.canonical());

        case SentenceSpan sentence ->
            System.out.printf("Sentence boundary: %d-%d%n", sentence.beginOffset(), sentence.endOffset());
    }
}
```

#### Sentence-Level Annotation Traversal & Entity Queries
`SentenceSpan` groups all tokens and entities that fall within that specific sentence boundary:

```java
for (SentenceSpan sentence : doc.sentences()) {
    System.out.printf("Sentence: %s (%d tokens, %d entities)%n",
        sentence.text(), sentence.tokenCount(), sentence.entityCount());

    // Sentence-level entity queries:
    if (sentence.hasEntity("Person")) {
        for (EntitySpan person : sentence.entities("Person")) {
            System.out.println("  Found person in sentence: " + person.canonical());
        }
    }

    // Unified stream of annotations within this sentence in character order:
    for (Annotation ann : sentence.annotations()) {
        if (ann instanceof TokenSpan t && t.hasFlag("init-cap")) {
            System.out.println("  Capitalized token: " + t.text());
        }
    }
}
```

#### Document-Level Convenience Queries
`WowoolDocument` provides high-level filtering:

```java
// Filter by entity URI:
List<EntitySpan> persons = doc.entities("Person");

// Find first occurrence:
Optional<EntitySpan> org = doc.findFirst("Organization");

// Spatial queries: find all annotations within a specific character window
List<Annotation> window = doc.annotationsBetween(10, 50);

// Find all tokens that comprise a multi-token entity:
EntitySpan president = doc.findFirst("Person").orElseThrow();
List<TokenSpan> tokens = doc.tokensFor(president);

// Access raw native engine JSON if custom parsing is desired:
String rawJson = doc.rawJson();
```

### Rich Spatial & Linguistic Navigation

`WowoolDocument` provides built-in spatial and linguistic query operators:

```java
EntitySpan entity = doc.findFirst("Person").orElseThrow();

// 1. Precise token-level spatial windowing
List<TokenSpan> before2 = doc.tokensBefore(entity, 2); // 2 tokens immediately preceding entity
List<TokenSpan> after2  = doc.tokensAfter(entity, 2);  // 2 tokens immediately following entity
List<TokenSpan> win     = doc.window(entity, 2);       // 2 before + entity tokens + 2 after

// 2. Spatial overlap detection
List<Annotation> overlapping = doc.overlapping(entity);
List<TokenSpan> overlapTokens = doc.overlappingTokens(entity);

// 3. Fast linguistic queries
List<TokenSpan> verbs = doc.tokensByPos("Verb");
List<TokenSpan> works = doc.tokensByLemma("werken");
boolean hasMeeting = doc.hasLemma("vergadering");
List<TokenSpan> caps = doc.tokensByFlag("init-cap");

// 4. Predicate search
List<TokenSpan> longWords = doc.findTokens(t -> t.text().length() > 12);
```

---

## 4. Type-Safe Fluent Rule DSL (`com.wowool.sdk.rule`)

Wowool Java SDK v2.0 introduces a compile-time checked, fluent rule builder that generates native Wowool DSL rules without manual string formatting.

### Core DSL Architecture & Operators

| DSL Class / Method | Wowool Syntax | Description |
| :--- | :--- | :--- |
| `Patterns.literal("word")` | `"word"` | Matches exact literal surface text |
| `Patterns.lemma("work")` | `'work'` | Matches dictionary lemma / stem |
| `Patterns.property("noun")`| `+noun` | Matches morphological property / flag |
| `Patterns.concept("Person")`| `Person` | Matches upstream semantic concept or POS |
| `Patterns.gap()` | `..` | Wildcard gap matching intervening tokens |
| `Patterns.choice("a", "b")` | `("a" \| "b")` | Logical disjunction between alternatives |
| `Patterns.optional(pat)` | `(pat)?` | Matches pattern optionally |
| `Sequence.of(...)` | `pat1 pat2` | Sequences pattern elements in order |

### Fluent Builder & Compilation Workflow

```java
import com.wowool.sdk.WowoolEngine;
import com.wowool.sdk.WowoolPipeline;
import com.wowool.sdk.DomainCompiler;
import com.wowool.sdk.CompilationResult;
import com.wowool.sdk.model.WowoolDocument;
import com.wowool.sdk.rule.*;

import java.nio.file.Path;

try (WowoolEngine engine = WowoolEngine.create()) {
    DomainCompiler compiler = engine.compiler();

    // 1. Define type-safe extraction rule
    WowoolRule rule = WowoolRule.named("ExtractEmployment")
        .matching(Sequence.of(
            Patterns.concept("Person"),
            Patterns.gap(),
            Patterns.choice("employed", "works"),
            Patterns.literal("at"),
            Patterns.gap(),
            Patterns.concept("Organization")
        ))
        .yields("EmploymentRelation");

    // 2. Compile directly into a binary .dom file
    Path targetDom = Path.of("target/employment.dom");
    CompilationResult result = compiler.compileRule(rule, targetDom, "english");

    if (result.success()) {
        // 3. Immediately cascade in an analysis pipeline
        try (WowoolPipeline pipeline = engine.pipeline("english", "entity", targetDom.toString())) {
            WowoolDocument doc = pipeline.analyze("Satya Nadella works at Microsoft.");
            doc.findFirst("EmploymentRelation").ifPresent(match -> {
                System.out.println("Matched: " + match.literal());
            });
        }
    }
}
```

Multiple rules can be bundled into an immutable `RuleSet`:
```java
RuleSet set = RuleSet.of(rule1, rule2, rule3);
compiler.compileRules(set, targetDom, "english");
```

---

## 5. Concurrency: Project Loom Virtual Threads & Streams

### Unpinned Virtual Thread Architecture & Lock Strategy

In Java 21+, **Project Loom Virtual Threads** multiplex high-concurrency tasks over a small pool of platform carrier threads (`ForkJoinPool`). However, a critical architectural challenge arises with traditional monitor locks:
- **The Pinning Problem:** When a virtual thread enters a `synchronized` block (`monitorenter`), the JVM **pins** the virtual thread to its underlying OS carrier thread. If a native Panama C-ABI downcall or off-heap operation occurs inside a synchronized region, the carrier thread cannot yield. Under high concurrency (e.g. thousands of concurrent requests), this causes carrier thread starvation.
- **The v2.0 Architectural Solution:**
  1. `WowoolPipeline` replaces monitor synchronization with `java.util.concurrent.locks.ReentrantLock`. Unlike monitor locks, `ReentrantLock` cooperates with the Loom scheduler: threads waiting for a pipeline release their carrier thread back to the pool.
  2. `WowoolPipelinePool` manages pipeline instances via an unpinned `ArrayBlockingQueue` lease model. Worker tasks spawned via `Executors.newVirtualThreadPerTaskExecutor()` acquire and return pipeline instances cleanly.
  3. This guarantees that applications can submit tens of thousands of concurrent document analysis tasks with zero thread pinning and optimal multi-core throughput.

![Wowool Java SDK Loom Concurrency Architecture](images/wowool-java-sdk-loom-concurrency.png)

### Batch Processing & Stream Pipelines

```java
import com.wowool.sdk.WowoolEngine;
import com.wowool.sdk.WowoolPipelinePool;
import com.wowool.sdk.model.WowoolDocument;

import java.util.List;
import java.util.stream.Stream;

try (WowoolEngine engine = WowoolEngine.create();
     WowoolPipelinePool pool = engine.pool("dutch", 8, "entity")) {

    // 1. Concurrent Batch Processing with Virtual Threads:
    List<String> texts = List.of(
        "Jan Janssen woont in Amsterdam.",
        "Willem-Alexander is koning der Nederlanden."
    );
    List<WowoolDocument> docs = pool.analyzeBatch(texts);

    // 2. First-class Stream Processing:
    Stream<String> inputLines = Files.lines(Path.of("incoming_documents.txt"));
    pool.analyzeStream(inputLines).forEach(doc -> {
        System.out.println("Analyzed document entities: " + doc.entityCount());
    });

    // 3. Asynchronous Non-Blocking Execution:
    pool.analyzeAsync("Document text...").thenAccept(doc -> {
        System.out.println("Async processing complete: " + doc.sentenceCount());
    });
}
```

### Pipeline Pooling for Microservices

In Spring Boot or Quarkus microservices processing concurrent HTTP requests, inject a managed `WowoolPipelinePool` bean:

```java
@Bean(destroyMethod = "close")
public WowoolPipelinePool wowoolPipelinePool(WowoolEngine engine) {
    int workers = Runtime.getRuntime().availableProcessors();
    return engine.pool("dutch", workers, "entity");
}
```

---

## 6. Resilient Multi-Platform Discovery & Diagnostics

When running outside containerized environments (e.g. local developer workstations, standalone on-premise servers, CI/CD runners), the Wowool Java SDK automatically discovers the required native dynamic libraries (`.dylib` / `.so`), linguistic models, and commercial license key without requiring hardcoded paths.

### Standard Directory Topology

The SDK expects the following canonical directory structure:

```text
~/.wowool/
├── lib/                             # Shared libraries directory (WOWOOL_DYLIB_DIR)
│   ├── libwowool.3.3.2.dylib        # macOS (or libwowool.so.3.3.2 on Linux)
│   ├── libwowool.dylib -> ...       # Symlink (optional)
│   ├── libicudata.74.dylib          # ICU Data dependency
│   └── libicuuc.74.dylib            # ICU UC dependency
└── models/                          # Linguistic models directory (WOWOOL_ROOT)
    └── lxware/
        └── wowool/
            ├── dutch.language       # Base language analyzers
            ├── dutch.morph
            ├── dutch.syn
            ├── dutch.entity.dom     # Domain knowledge bases
            ├── english.language
            └── ...

~/.config/wowool/
└── WOWOOL_SDK_KEY                   # Commercial license key (plain text)
```

---

### Step-by-Step Setup Guide by Operating System

#### 1. macOS (Apple Silicon M1/M2/M3/M4 & Intel x86_64)

##### A. User-Level Setup (`~/.wowool`) — Recommended for Developers
```bash
# 1. Create standard directory structure
mkdir -p ~/.wowool/lib ~/.wowool/models/lxware/wowool ~/.config/wowool

# 2. Copy native libraries (libwowool and ICU dependencies)
cp /path/to/extracted/libwowool*.dylib ~/.wowool/lib/
cp /path/to/extracted/libicu*.dylib ~/.wowool/lib/

# 3. Copy linguistic models
cp -r /path/to/extracted/models/* ~/.wowool/models/lxware/wowool/

# 4. Save your commercial license key
echo "YOUR_COMMERCIAL_SDK_KEY" > ~/.config/wowool/WOWOOL_SDK_KEY
chmod 600 ~/.config/wowool/WOWOOL_SDK_KEY
```

##### B. System-Wide Setup (`/opt/wowool`) — Standalone Servers
```bash
sudo mkdir -p /opt/wowool/lib /opt/wowool/models/lxware/wowool /etc/wowool
sudo cp /path/to/libwowool*.dylib /opt/wowool/lib/
sudo cp -r /path/to/models/* /opt/wowool/models/lxware/wowool/
sudo echo "YOUR_COMMERCIAL_SDK_KEY" | sudo tee /etc/wowool/WOWOOL_SDK_KEY
```

---

#### 2. Linux (Ubuntu, Debian, RHEL, CentOS, Rocky Linux, Fedora)

##### Step 1: Install Native ICU Dependencies
The Wowool C++ runtime links against ICU for Unicode tokenization and normalization:
- **Debian / Ubuntu:**
  ```bash
  sudo apt-get update && sudo apt-get install -y libicu-dev
  ```
- **RHEL / Rocky Linux / Fedora / AlmaLinux:**
  ```bash
  sudo dnf install -y libicu-devel
  ```

##### Step 2: Create Directory Layout
- **User-Level Setup (`~/.wowool`):**
  ```bash
  mkdir -p ~/.wowool/lib ~/.wowool/models/lxware/wowool ~/.config/wowool

  # Copy Linux shared libraries (libwowool.so.3.3.2 and libwowool.so)
  cp /path/to/extracted/libwowool*.so* ~/.wowool/lib/

  # Copy linguistic models
  cp -r /path/to/extracted/models/* ~/.wowool/models/lxware/wowool/

  # Store license key
  echo "YOUR_COMMERCIAL_SDK_KEY" > ~/.config/wowool/WOWOOL_SDK_KEY
  chmod 600 ~/.config/wowool/WOWOOL_SDK_KEY
  ```

- **Enterprise System-Wide Setup (`/opt/wowool`):**
  ```bash
  sudo mkdir -p /opt/wowool/lib /opt/wowool/models/lxware/wowool
  sudo cp /path/to/libwowool*.so* /opt/wowool/lib/
  sudo cp -r /path/to/models/* /opt/wowool/models/lxware/wowool/

  # Optional: Register in dynamic linker
  echo "/opt/wowool/lib" | sudo tee /etc/ld.so.conf.d/wowool.conf
  sudo ldconfig
  ```

---

#### 3. Windows (via WSL2 — Ubuntu)

On Windows 10/11, native high-performance execution is supported via **Windows Subsystem for Linux (WSL2)**:
```bash
# In Windows PowerShell:
wsl --install -d Ubuntu

# Inside your WSL2 Ubuntu terminal:
sudo apt update && sudo apt install -y openjdk-22-jdk maven libicu-dev
mkdir -p ~/.wowool/lib ~/.wowool/models/lxware/wowool ~/.config/wowool

# Copy your Linux binaries and models into the WSL2 paths, then run your Java application:
java --enable-native-access=ALL-UNNAMED -jar app.jar
```

---

#### 4. Project-Local Zero-Config Setup (`./native` — Ideal for Teams & IDEs)

If you want a self-contained repository checkout where new team members can clone and run without touching user directories:

```text
your-project-repository/
├── pom.xml
├── native/
│   ├── lib/
│   │   ├── darwin-aarch64/          # macOS Apple Silicon
│   │   │   └── libwowool.3.3.2.dylib
│   │   ├── darwin-x86_64/           # macOS Intel
│   │   │   └── libwowool.3.3.2.dylib
│   │   └── linux-x86_64/            # Linux x86_64
│   │       └── libwowool.so.3.3.2
│   └── wowool/
│       └── lxware/
│           └── wowool/
│               ├── dutch.language
│               └── ...
```

`WowoolPaths` automatically probes `./native/lib/<arch>` and `./native/wowool`. Opening the project in **IntelliJ IDEA**, **Eclipse**, or **VS Code** works instantly without setting environment variables!

---

### Actionable Diagnostic Reports

If a required binary or model is missing, `WowoolPaths` does not fail with an obscure `UnsatisfiedLinkError`. Instead, it throws a typed `WowoolException` with a comprehensive diagnostic report:

```text
[ERROR] Wowool native library (libwowool.*) not found!
Host Architecture: aarch64 (OS: Mac OS X)
Expected library names: [libwowool.3.3.2.dylib, libwowool.dylib, libwowool.so]

Probed paths:
  - [MISSING DIRECTORY] /Users/developer/.wowool/lib
  - [MISSING DIRECTORY] ./native/lib/darwin-aarch64
  - [EXISTS - NO MATCH] /usr/local/lib (missing: [libwowool.3.3.2.dylib...])
  - [MISSING DIRECTORY] /opt/wowool/lib

Remediation steps:
  1. Set environment variable: export WOWOOL_DYLIB_DIR=/path/to/folder_with_libwowool
  2. Or set Java system property: -Dwowool.dylib.dir=/path/to/folder_with_libwowool
  3. Or copy libwowool.* to ~/.wowool/lib/ or ./native/lib/
```

---

## 7. Native Error Handling & Exception Mapping

Native errors occurring within the C++ core are intercepted via the `tir_exception` struct:

```java
struct tir_exception {
    int error_type;
    char error_message[1024];
};
```

When `error_type != 0`, the SDK extracts the error message and throws a typed `WowoolException`:

```java
try {
    pipeline.analyze(text);
} catch (WowoolException ex) {
    System.err.printf("Native error (%d): %s%n", ex.getErrorCode(), ex.getMessage());
}
```

---

## 8. Enterprise Framework Integration

### Spring Boot 3.x / 4.x Configuration

Create a Spring `@Configuration` bean:

```java
@Configuration
public class WowoolAutoConfiguration {

    @Bean(destroyMethod = "close")
    public WowoolEngine wowoolEngine(
        @Value("${wowool.license-key:#{null}}") String licenseKey,
        @Value("${wowool.root:#{null}}") String wowoolRoot,
        @Value("${wowool.dylib-dir:#{null}}") String dylibDir
    ) {
        WowoolConfig.Builder builder = WowoolConfig.builder();
        if (licenseKey != null) builder.licenseKey(licenseKey);
        if (wowoolRoot != null) builder.wowoolRoot(Path.of(wowoolRoot));
        if (dylibDir != null) builder.dylibDir(Path.of(dylibDir));

        return WowoolEngine.create(builder.build());
    }
}
```

### OCI Containerization Considerations

When packaging and deploying services with the Wowool Java SDK inside OCI-compliant container environments (e.g., Podman, CRI-O, containerd, Kubernetes):

1. **Native C++ Dependencies (`libicu`):** Ensure the OCI base image provides the native ICU libraries required by the underlying Wowool C++ runtime:
   ```Containerfile
   # Example Containerfile (OCI Image Specification)
   FROM eclipse-temurin:25-jre-noble
   RUN apt-get update && apt-get install -y --no-install-recommends libicu-dev && rm -rf /var/lib/apt/lists/*
   ```

2. **Panama FFM JVM Flag:** Explicitly configure native access in the container entrypoint:
   ```Containerfile
   ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]
   ```

3. **Packaging Strategies & Kubernetes (K8s) Operational Impact:**

Depending on your enterprise infrastructure, the SDK supports two distinct deployment patterns:

#### Pattern A: Self-Contained OCI Image (Recommended for Cloud-Native K8s)

In this pattern, the native shared libraries (`libwowool.so`) and required `.language`/`.dom` model assets are copied directly into the container image at build time (e.g. into `/opt/wowool/lib` and `/opt/wowool/models`):

```Containerfile
# Self-contained enterprise production image
FROM eclipse-temurin:25-jre-noble
RUN apt-get update && apt-get install -y --no-install-recommends libicu-dev && rm -rf /var/lib/apt/lists/*

# Copy native Wowool runtime & models directly into image
COPY ./native/lib/linux-x86_64 /opt/wowool/lib
COPY ./native/wowool          /opt/wowool/models
COPY ./target/app.jar         /app/app.jar

ENV WOWOOL_DYLIB_DIR=/opt/wowool/lib
ENV WOWOOL_ROOT=/opt/wowool/models

ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "/app/app.jar"]
```

**Architectural & Operational Impact on Kubernetes:**
- **Zero Volume Dependencies:** Pods are **100% stateless**. No `PersistentVolumeClaims` (PVCs), `PersistentVolumes` (PVs), or storage provisioners (CSI drivers) are needed.
- **Instant Horizontal Autoscaling (HPA / KEDA):** Pods spin up in seconds without waiting for cloud volume attachments, completely eliminating volume attachment latency or `Multi-Attach error for volume` failures across nodes.
- **Multi-Zone & Multi-Cluster Portability:** Pods can be scheduled across any Availability Zone or cluster without being tied to specific regional block storage.
- **Immutable Infrastructure:** Version `2.0.0` of your container image contains the exact, verified combination of application bytecode, native engine, and domain models, preventing model drift.
- **Minimal Kubernetes Manifest:** The only external configuration required is the commercial license key injected via a Kubernetes `Secret`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: wowool-nlp-service
spec:
  replicas: 5
  template:
    spec:
      containers:
        - name: wowool-nlp
          image: registry.example.com/wowool-service:2.0.0
          env:
            - name: WOWOOL_SDK_KEY
              valueFrom:
                secretKeyRef:
                  name: wowool-secret
                  key: sdk-key
          resources:
            requests:
              cpu: "1000m"
              memory: "1Gi"
            limits:
              cpu: "4000m"
              memory: "2Gi"
```

#### Pattern B: External Volume Mounts / PVCs (Dynamic Model Updates)

If your architecture requires updating domain `.dom` files dynamically without rebuilding container images, or sharing multi-gigabyte linguistic corpora across dozens of services, mount the libraries and models via shared read-only volumes (e.g. NFS, CephFS, AWS EFS, or Azure Files):

```yaml
spec:
  containers:
    - name: wowool-service
      image: registry.example.com/wowool-service:2.0.0
      volumeMounts:
        - name: wowool-models
          mountPath: /opt/wowool/models
          readOnly: true
        - name: wowool-lib
          mountPath: /opt/wowool/lib
          readOnly: true
      env:
        - name: WOWOOL_ROOT
          value: /opt/wowool/models
        - name: WOWOOL_DYLIB_DIR
          value: /opt/wowool/lib
        - name: WOWOOL_SDK_KEY
          valueFrom:
            secretKeyRef:
              name: wowool-secret
              key: sdk-key
  volumes:
    - name: wowool-models
      persistentVolumeClaim:
        claimName: wowool-models-pvc
    - name: wowool-lib
      persistentVolumeClaim:
        claimName: wowool-lib-pvc
```

*CLI Execution (e.g. Podman / Docker):*
```bash
podman run --rm -it \
  -v /opt/wowool/models:/opt/wowool/models:ro \
  -v /opt/wowool/lib:/opt/wowool/lib:ro \
  -e WOWOOL_ROOT=/opt/wowool/models \
  -e WOWOOL_DYLIB_DIR=/opt/wowool/lib \
  -e WOWOOL_SDK_KEY="your_license_key" \
  registry.example.com/wowool-service:2.0.0
```

---

## 9. Empirical Panama FFM Performance Benchmarks

The Wowool Java SDK achieves near-C invocation speeds by utilizing direct foreign downcalls without intermediate JNI JNIEnv conversions or sub-process serialization.

Empirical measurements gathered on Apple Silicon (12 cores) with Java 22 / Java 25 LTS:

### Single-Thread Hot Path Latency & Throughput

| Pipeline Configuration | Document Chars | Tokens | Mean Latency (ms) | P90 Latency (ms) | Throughput (Tokens/s) |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Tokenize + POS (Short)** | 109 | 17 | **0.26 ms** | 0.31 ms | **65,000+** |
| **Tokenize + POS (Medium)** | 4,150 | 650 | **2.67 ms** | 2.90 ms | **243,000+** |
| **Tokenize + POS (Long)** | 20,758 | 3,250 | **11.92 ms** | 12.35 ms | **272,000+** |
| **Full NER + POS (Short)** | 109 | 17 | **0.87 ms** | 0.95 ms | **19,500+** |
| **Full NER + POS (Medium)** | 4,150 | 650 | **20.38 ms** | 21.10 ms | **31,800+** |

### Multi-Core Linear Scaling Profile (`WowoolPipelinePool`)

| Active Worker Threads | Total Processed Documents | Aggregate Throughput | Speedup Factor |
| :---: | :---: | :---: | :---: |
| **1 Thread** | 25 | 31,881 tokens/s | **1.00x** (baseline) |
| **2 Threads** | 50 | 56,202 tokens/s | **1.76x** |
| **4 Threads** | 100 | 105,711 tokens/s | **3.32x** |
| **8 Threads** | 200 | 186,846 tokens/s | **5.86x** |
| **12 Threads** | 300 | 216,111 tokens/s | **6.78x** |

### Analysis of Off-Heap Memory Efficiency
- **Off-heap allocation:** Analysis input buffers and native output buffers are scoped within confined arenas or explicit ref-counts.
- **Garbage Collection impact:** Because linguistic structures remain in off-heap segments until converted into lightweight record/class spans, GC pauses are negligible even under sustained multi-gigabyte throughput.

### Academic Research & Citation

The methodology, C-ABI downcall architecture, and Panama FFM benchmark evaluation are formally documented in the published research paper:

> **"Wowool: High-Throughput Deterministic Symbolic NLP with Multi-Language C-ABI and Project Panama FFM Interoperability"**  
> *Rik Doclo, Philippe Forest, Manuel Rubio De Hita, Nadir Grine (2026).*  
> **Permanent DOI:** [https://doi.org/10.5281/zenodo.23441610](https://doi.org/10.5281/zenodo.23441610) | **Zenodo Record:** [https://zenodo.org/records/23441610](https://zenodo.org/records/23441610)

```bibtex
@article{doclo2026wowool,
  title={{Wowool: High-Throughput Deterministic Symbolic NLP with Multi-Language C-ABI and Project Panama FFM Interoperability}},
  author={Doclo, Rik and Forest, Philippe and Rubio De Hita, Manuel and Grine, Nadir},
  year={2026},
  month={10},
  publisher={Zenodo},
  doi={10.5281/zenodo.23441610},
  url={https://doi.org/10.5281/zenodo.23441610}
}
```

---

## 10. Troubleshooting & FAQ

#### Q: JVM throws `IllegalCallerException: native access is disabled`
**A:** Add `--enable-native-access=ALL-UNNAMED` to your JVM execution arguments.

#### Q: `libwowool.3.3.2.dylib` cannot be loaded
**A:** Ensure `WOWOOL_DYLIB_DIR` is set to the folder containing `libwowool.*` and that dependent libraries (`libicudata`, `libicuuc`) are present in the same directory.

#### Q: How fast is the Panama SDK compared to Python?
**A:** Because there is no Python GIL, no inter-process pipe I/O, and no process context switching, average throughput in Java is **5x to 15x higher**, analyzing typical paragraphs in **< 1 millisecond**.
