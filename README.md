# Wowool Java SDK

[![CI Build](https://github.com/rikdoclo/wowool-java-sdk/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/rikdoclo/wowool-java-sdk/actions/workflows/ci.yml?query=branch%3Amain)
[![Java](https://img.shields.io/badge/Java-22%2B%20(LTS%2025)-orange.svg)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Engine](https://img.shields.io/badge/Engine-Wowool%20Native%20C%2B%2B%203.3.2-green.svg)](https://wowool.com)
[![GitHub Repository](https://img.shields.io/badge/GitHub-rikdoclo%2Fwowool--java--sdk-181717?logo=github)](https://github.com/rikdoclo/wowool-java-sdk)

High-performance, idiomatic native **Java SDK for the Wowool NLP & Linguistic Engine**, built on **Project Panama** (the Foreign Function & Memory API - JEP 454 / Java 22+).

This SDK connects directly to the underlying C++ runtime (`libwowool.3.3.2.dylib` / `libwowool.so`) via the official C ABI (`tir_api_*`), delivering **pure in-memory NLP analysis in ~1 millisecond with zero Python runtime and zero IPC overhead**.

---

## Key Features

- ⚡ **Sub-Millisecond Execution:** Direct C ABI downcalls with near zero-copy memory semantics via Panama `Arena`.
- ☕ **100% Pure Modern Java:** No JNI boilerplate, no C++ compilers required, no manual Makefiles. Built with Java `record`s and pattern matching.
- 🔒 **Type-Safe API:** Rich immutable data models for `WowoolDocument`, `SentenceSpan`, `EntitySpan`, and `TokenSpan`.
- 🧠 **Deterministic NLP:** Full access to Wowool's linguistic layers: deep morphology, Part-of-Speech tagging, tokenization, lemmatization, and cascading entity extraction.
- ⚙️ **Dynamic Rule Compilation:** Compile custom Wowool domain rule files (`.wow` / `.woc`) directly to binary `.dom` domains from Java.
- 🌐 **Instant Language Identification:** Integrated native `LanguageIdentifier` capable of detecting languages in sub-millisecond batch calls.
- 🔄 **Safe Lifecycle Management:** Full `AutoCloseable` implementation with deterministic off-heap native memory cleanup.
- 🔍 **Smart Auto-Discovery:** Seamlessly discovers license keys (`WOWOOL_SDK_KEY` or `~/.config/wowool/WOWOOL_SDK_KEY`), dynamic libraries, and language models (`lxware/wowool`).

---

## Architecture Overview

![Wowool Java SDK Panama Architecture](docs/images/wowool-java-sdk-panama-architecture.png)

---

## Requirements

1. **Java 22 or higher** (Tested on OpenJDK 22, 23, 24, 25 LTS, and 26).
2. **Wowool Native Libraries & Models:** `libwowool`, `libicudata`, `libicuuc`, and `.language`/`.dom` files.
3. **JVM Flag:** `--enable-native-access=ALL-UNNAMED` (required by Java runtime for Project Panama Foreign Function access).

---

## Installation & Getting Started

### 1. Build & Install Locally from GitHub

Clone the repository and install the SDK into your local Maven repository (`~/.m2/repository`):

```bash
git clone https://github.com/rikdoclo/wowool-java-sdk.git
cd wowool-java-sdk
mvn clean install
```

### 2. Add Dependency to Your Project

Add the dependency to your project's `pom.xml`:

```xml
<dependency>
    <groupId>com.wowool</groupId>
    <artifactId>wowool-sdk-java</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 3. JVM Native Access Flag

When running your Java application, Project Panama requires the native access flag:

```bash
java --enable-native-access=ALL-UNNAMED -jar your-application.jar
```

---

## Quickstart

### 1. Linguistic Analysis & Entity Recognition

```java
import com.wowool.sdk.WowoolEngine;
import com.wowool.sdk.WowoolPipeline;
import com.wowool.sdk.model.EntitySpan;
import com.wowool.sdk.model.WowoolDocument;

public class Quickstart {
    public static void main(String[] args) {
        // Initialize engine (automatically discovers license key & models)
        try (WowoolEngine engine = WowoolEngine.create()) {

            // Create pipeline with language 'english' and domain 'entity'
            try (WowoolPipeline pipeline = engine.pipeline("english", "entity")) {

                String text = "Satya Nadella spoke at Microsoft headquarters in Redmond.";
                WowoolDocument doc = pipeline.analyze(text);

                System.out.printf("Sentences: %d | Tokens: %d | Entities: %d%n",
                    doc.sentenceCount(), doc.tokenCount(), doc.entityCount());

                // Iterate over all extracted entities
                for (EntitySpan entity : doc.entities()) {
                    System.out.printf("  [%s] '%s' (%d-%d) -> %s%n",
                        entity.uri(),
                        entity.literal(),
                        entity.beginOffset(),
                        entity.endOffset(),
                        entity.attributes());
                }

                // Filter specifically for Person entities
                for (EntitySpan person : doc.entities("Person")) {
                    System.out.println("Found person: " + person.literal());
                }
            }
        }
    }
}
```

---

### 2. Sub-Millisecond Language Identification

```java
import com.wowool.sdk.WowoolEngine;
import com.wowool.sdk.LanguageIdentifier;

try (WowoolEngine engine = WowoolEngine.create();
     LanguageIdentifier lid = engine.languageIdentifier()) {

    String lang1 = lid.identify("This is an English technical document.");
    String lang2 = lid.identify("Ceci est un document rédigé en français.");
    String lang3 = lid.identify("Dit is een Nederlandstalige juridische tekst.");

    System.out.println(lang1); // english
    System.out.println(lang2); // french
    System.out.println(lang3); // dutch
}
```

---

### 3. Compiling and Executing Custom Rules (.wow -> .dom)

```java
import com.wowool.sdk.WowoolEngine;
import com.wowool.sdk.WowoolPipeline;
import com.wowool.sdk.DomainCompiler;
import com.wowool.sdk.CompilationResult;
import com.wowool.sdk.model.WowoolDocument;

import java.nio.file.Path;

try (WowoolEngine engine = WowoolEngine.create()) {
    DomainCompiler compiler = engine.compiler();

    // Define custom Wowool DSL rule
    String ruleSource = "rule: { (\"works\" | \"employed\") \"at\" } = EmploymentRelation;\n";
    Path targetDom = Path.of("target/employment_rules.dom");

    CompilationResult result = compiler.compileSource(ruleSource, targetDom, "english");
    if (result.success()) {
        System.out.println("Domain compiled successfully!");

        // Immediately use the newly compiled domain in a pipeline
        try (WowoolPipeline pipeline = engine.pipeline("english", "entity", targetDom.toString())) {
            WowoolDocument doc = pipeline.analyze("Jane Doe works at Acme Corp.");
            doc.findFirst("EmploymentRelation").ifPresent(rel ->
                System.out.println("Detected custom relation: " + rel.literal())
            );
        }
    }
}
```

---

### 4. Concurrent Analysis with Pipeline Pooling

For multi-threaded services (Spring Boot, Quarkus, Obscuro), `WowoolPipelinePool` maintains pre-warmed pipelines for parallel processing with zero lock contention:

```java
import com.wowool.sdk.WowoolEngine;
import com.wowool.sdk.WowoolPipelinePool;
import com.wowool.sdk.model.WowoolDocument;

try (WowoolEngine engine = WowoolEngine.create();
     WowoolPipelinePool pool = engine.pool("dutch", 8, "entity")) {

    // Safely called concurrently from multiple worker threads
    WowoolDocument doc = pool.analyze("Jan Janssen woont in Amsterdam.");
    System.out.println("Canonical: " + doc.findFirst("Person").get().canonical());
}
```

---

## Configuration & Environment Variables

The SDK automatically resolves paths using standard conventions, but can be overridden:

| Environment Variable | Java System Property | Description | Default / Discovery Order |
| :--- | :--- | :--- | :--- |
| `WOWOOL_SDK_KEY` | `wowool.license.key` | Commercial license key | `~/.config/wowool/WOWOOL_SDK_KEY` |
| `WOWOOL_DYLIB_DIR` | `wowool.dylib.dir` | Folder containing native dynamic libraries | `~/.wowool/lib`, `./native/dylibs`, active `$VIRTUAL_ENV`, `/opt/wowool/lib`, `/usr/local/lib/wowool` |
| `WOWOOL_ROOT` | `wowool.root` | Folder containing `lxware/wowool` models | `~/.wowool/models`, `./native/wowool`, active `$VIRTUAL_ENV`, `/opt/wowool`, `/usr/share/wowool` |

### Explicit Configuration via Builder

```java
WowoolConfig config = WowoolConfig.builder()
    .licenseKey("YOUR_COMMERCIAL_KEY")
    .dylibDir(Path.of("/opt/wowool/lib"))
    .wowoolRoot(Path.of("/opt/wowool/models"))
    .build();

try (WowoolEngine engine = WowoolEngine.create(config)) {
    // ...
}
```

---

## Testing

Run the automated test suite:

```bash
mvn test
```

---

## Documentation

For in-depth architecture, memory management, and enterprise integration patterns, see:
- [Developer Guide & Architecture](docs/DEVELOPER_GUIDE.md)

---

## Contributing & Support

- **Repository:** [https://github.com/rikdoclo/wowool-java-sdk](https://github.com/rikdoclo/wowool-java-sdk)
- **Issues & Bug Reports:** [GitHub Issues](https://github.com/rikdoclo/wowool-java-sdk/issues)

Pull requests and issues are welcome!

---

## License

Copyright (c) 2026 Rik Doclo. All rights reserved.  
Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
