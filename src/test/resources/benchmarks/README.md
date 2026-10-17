# Wowool Reproducible Benchmark Corpora

This directory contains the standardized evaluation corpora used for empirical benchmarking of the Wowool NLP platform and the Wowool Java SDK (Java 22 Project Panama Foreign Function & Memory API), as reported in the academic whitepaper (*Wowool: A High-Performance, Deterministic Symbolic NLP Platform with Zero-Copy Interoperability and Neuro-Symbolic Governance*).

## Benchmark Corpus Files

| File | Document Tier | Tokens | Characters | Description |
|---|---|---|---|---|
| `short_administrative_notice.txt` | Short | 17 | 109 | Single-sentence ministerial press release and administrative abstract. |
| `medium_gazette_notice.txt` | Medium | 650 | 4,150 | Multi-paragraph municipal statutory gazette notice and permit application (*Departement Omgeving, Flanders*). |
| `long_regulatory_decree.txt` | Long | 3,250 | 20,758 | Comprehensive administrative decree and environmental dossier (*VLAREM II* regulation). |

## Provenance and Privacy (GDPR Compliance)
These documents represent synthetic yet structurally realistic Dutch administrative, legal, and environmental dossiers modeled after authentic Flemish government gazettes (*Belgisch Staatsblad / Departement Omgeving*). They incorporate dense administrative terminology, nested legal citations, municipal entity references, and complex punctuation, while containing **zero real-world personally identifiable information (PII)**.

## Usage in Automated Tests
These files are automatically loaded by `com.wowool.sdk.benchmark.BenchmarkSuiteTest` during benchmark execution:
```bash
mvn test -Dtest=BenchmarkSuiteTest
```
Raw latency percentiles (P50, P90, P99), mean execution times, and token throughput metrics are exported to `target/benchmarks/benchmark_results.csv`.
