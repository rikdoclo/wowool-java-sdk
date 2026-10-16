# Wowool Java SDK — Developer Guide & Technical Reference

**Author:** Rik Doclo  
**Version:** 1.0.0  
**Repository:** [https://github.com/rikdoclo/wowool-java-sdk](https://github.com/rikdoclo/wowool-java-sdk)  
**Java Compatibility:** Java 22+ (including Java 25 LTS)

---

## Table of Contents
1. [Architectural Foundations](#1-architectural-foundations)
   - [Why Project Panama (FFM API) over JNI & Subprocesses](#why-project-panama-ffm-api-over-jni--subprocesses)
   - [Zero-Copy Native Downcalls via the C ABI](#zero-copy-native-downcalls-via-the-c-abi)
2. [Native Memory Lifecycle & Arena Management](#2-native-memory-lifecycle--arena-management)
   - [Shared vs Confined Arenas](#shared-vs-confined-arenas)
   - [Deterministic Memory Cleanup](#deterministic-memory-cleanup)
3. [Core Components Deep Dive](#3-core-components-deep-dive)
   - [WowoolEngine: The Root Factory](#wowoolengine-the-root-factory)
   - [WowoolPipeline: Sequential Domain Chaining](#wowoolpipeline-sequential-domain-chaining)
   - [WowoolDocument & Typed Records](#wowooldocument--typed-records)
   - [LanguageIdentifier: High-Speed Language Classification](#languageidentifier-high-speed-language-classification)
   - [DomainCompiler: Compiling Rules on the Fly](#domaincompiler-compiling-rules-on-the-fly)
4. [Writing and Compiling Custom Wowool Domains (.wow)](#4-writing-and-compiling-custom-wowool-domains-wow)
5. [Concurrency, Thread Safety & High Throughput](#5-concurrency-thread-safety--high-throughput)
   - [Threading Model](#threading-model)
   - [Pipeline Pooling for Multi-Threaded Services](#pipeline-pooling-for-multi-threaded-services)
6. [Native Error Handling & Exception Mapping](#6-native-error-handling--exception-mapping)
7. [Enterprise Framework Integration](#7-enterprise-framework-integration)
   - [Spring Boot 3.x / 4.x Configuration](#spring-boot-3x--4x-configuration)
   - [OCI Containerization Considerations](#oci-containerization-considerations)
8. [Troubleshooting & FAQ](#8-troubleshooting--faq)

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

### WowoolDocument & Typed Records

`WowoolDocument` provides typed query methods:

```java
WowoolDocument doc = pipeline.analyze(text);

// Filter by entity URI:
List<EntitySpan> persons = doc.entities("Person");

// Find first occurrence:
Optional<EntitySpan> org = doc.findFirst("Organization");

// Inspect tokens and morphology:
for (TokenSpan token : doc.tokens()) {
    String word = token.literal();
    String lemma = token.lemma();
    String posTag = token.pos();
}

// Access raw engine JSON if custom parsing is desired:
String rawJson = doc.rawJson();
```

---

## 4. Writing and Compiling Custom Wowool Domains (.wow)

The SDK enables runtime compilation of Wowool rule files into memory-mapped `.dom` binaries:

```java
DomainCompiler compiler = engine.compiler();

// Define rule DSL
String dsl = """
    // Custom relationship rule
    rule: { Person .. <"employed" | "works"> "at" .. Organization } = EmploymentRelation;
    """;

Path targetDom = Path.of("target/relations.dom");
CompilationResult result = compiler.compileSource(dsl, targetDom, "english");

if (!result.success()) {
    System.err.println("Compilation failed:");
    result.diagnostics().forEach(System.err::println);
}
```

---

## 5. Concurrency, Thread Safety & High Throughput

### Threading Model

- `WowoolEngine` is **thread-safe** and designed to be shared across the entire JVM.
- `WowoolPipeline.analyze()` is synchronized per pipeline instance because the underlying native analyzer maintains temporary state per execution.

### Pipeline Pooling for Multi-Threaded Services

For high-throughput Spring Boot or Quarkus microservices processing concurrent HTTP requests, do not share a single pipeline instance across concurrent threads. Instead, maintain an **object pool** (e.g. using Apache Commons Pool) or `ThreadLocal` pipelines:

```java
public class ThreadLocalPipelinePool implements AutoCloseable {
    private final WowoolEngine engine;
    private final ThreadLocal<WowoolPipeline> pipelineHolder;

    public ThreadLocalPipelinePool(WowoolEngine engine, String language, String... domains) {
        this.engine = engine;
        this.pipelineHolder = ThreadLocal.withInitial(() -> engine.pipeline(language, domains));
    }

    public WowoolDocument analyze(String text) {
        return pipelineHolder.get().analyze(text);
    }

    @Override
    public void close() {
        // cleanup thread locals
    }
}
```

---

## 6. Native Error Handling & Exception Mapping

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

## 7. Enterprise Framework Integration

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

3. **Mounting Wowool Models & Shared Libraries:** Mount the native libraries and `.language`/`.dom` model assets into the OCI container as read-only volumes (e.g. via Kubernetes Pod volume mounts or Podman CLI):

   * **Kubernetes / OCI Pod Specification snippet:**
   ```yaml
   spec:
     containers:
       - name: wowool-service
         image: registry.example.com/wowool-service:1.0.0
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

   * **OCI Runtime CLI (e.g. Podman):**
   ```bash
   podman run --rm -it \
     -v /opt/wowool/models:/opt/wowool/models:ro \
     -v /opt/wowool/lib:/opt/wowool/lib:ro \
     -e WOWOOL_ROOT=/opt/wowool/models \
     -e WOWOOL_DYLIB_DIR=/opt/wowool/lib \
     -e WOWOOL_SDK_KEY="your_license_key" \
     registry.example.com/wowool-service:1.0.0
   ```

---

## 8. Troubleshooting & FAQ

#### Q: JVM throws `IllegalCallerException: native access is disabled`
**A:** Add `--enable-native-access=ALL-UNNAMED` to your JVM execution arguments.

#### Q: `libwowool.3.3.2.dylib` cannot be loaded
**A:** Ensure `WOWOOL_DYLIB_DIR` is set to the folder containing `libwowool.*` and that dependent libraries (`libicudata`, `libicuuc`) are present in the same directory.

#### Q: How fast is the Panama SDK compared to Python?
**A:** Because there is no Python GIL, no inter-process pipe I/O, and no process context switching, average throughput in Java is **5x to 15x higher**, analyzing typical paragraphs in **< 1 millisecond**.
