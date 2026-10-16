# Contributing to Wowool Java SDK

Thank you for your interest in contributing to the **Wowool Java SDK**! We welcome contributions, bug reports, feature requests, and documentation improvements.

---

## Code of Conduct

All contributors are expected to adhere to our [Code of Conduct](CODE_OF_CONDUCT.md). Please read it before participating.

---

## Development Prerequisites

To build and run tests for this project, you need:
1. **JDK 22 or higher** (OpenJDK, Temurin, or Oracle JDK). Project Panama Foreign Function & Memory API (JEP 454) is used extensively.
2. **Apache Maven 3.9+**.
3. **Wowool Native Runtime** (optional for model unit tests, required for full native integration tests): `libwowool.3.3.2.dylib` / `.so`.

---

## Development Workflow

### 1. Fork and Clone

```bash
git clone https://github.com/<your-username>/wowool-java-sdk.git
cd wowool-java-sdk
git checkout -b feature/my-new-feature
```

### 2. Building the Project

Compile the module and run the test suite:

```bash
mvn clean test
```

Generate full JARs, sources, and Javadoc:

```bash
mvn clean verify javadoc:javadoc
```

### 3. Native Integration Testing

The test suite consists of two layers:
* **`ModelAndConfigTest`:** Pure Java unit tests verifying records, spatial document queries, config builders, and JSON parsing. Runs anywhere without native libraries.
* **`WowoolSdkTest`:** Full native integration tests linking directly to `libwowool`. If native libraries or license keys are not configured in your environment, these tests will gracefully skip.

---

## Architectural & Design Guidelines

When contributing code:
* **Strong Encapsulation:** All raw pointers (`MemorySegment`), MethodHandles, and C ABI calls MUST remain internal to `com.wowool.sdk.internal.ffi`. Never expose raw pointers in the public API.
* **Modern Java Idioms:** Use Java `record`s, pattern matching, `Optional`, and immutable collections.
* **Memory Safety:** Always use `try-with-resources` with `Arena.ofConfined()` for per-call memory buffers. Ensure shared pointers are deterministically released with `WowoolNative.decRef()` upon closing or failure.
* **Thread Safety:** Follow the established concurrency patterns (use `WowoolPipelinePool` for pooling stateful analyzers).

---

## Pull Request Guidelines

1. **Keep it Focused:** Keep each pull request focused on a single bug fix, feature, or refactoring.
2. **Add Tests:** Include unit or integration tests demonstrating that your change works and prevents regression.
3. **Commit Messages:** Follow [Conventional Commits](https://www.conventionalcommits.org/):
   * `feat: ...` for new features
   * `fix: ...` for bug fixes
   * `docs: ...` for documentation
   * `test: ...` for tests
4. **Pass CI:** Ensure `mvn clean verify` passes locally before submitting your PR.
