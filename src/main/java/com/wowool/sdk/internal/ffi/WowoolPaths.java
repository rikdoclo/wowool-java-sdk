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

package com.wowool.sdk.internal.ffi;

import com.wowool.sdk.WowoolException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Path resolver and diagnostic locator for native Wowool shared libraries,
 * language models, and license keys across Darwin (macOS) and Linux
 * architectures.
 */
public final class WowoolPaths {

    private static final String OS_NAME = System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT);
    private static final String OS_ARCH = System.getProperty("os.arch", "unknown").toLowerCase(Locale.ROOT);

    private WowoolPaths() {
    }

    /**
     * Checks if the host operating system is macOS / Darwin.
     */
    public static boolean isMac() {
        return OS_NAME.contains("mac") || OS_NAME.contains("darwin");
    }

    /**
     * Checks if the host operating system is Linux.
     */
    public static boolean isLinux() {
        return OS_NAME.contains("linux");
    }

    /**
     * Returns a normalized architecture identifier (e.g., "aarch64", "x86_64").
     */
    public static String normalizedArch() {
        if (OS_ARCH.equals("arm64") || OS_ARCH.equals("aarch64")) {
            return "aarch64";
        }
        if (OS_ARCH.equals("x86_64") || OS_ARCH.equals("amd64")) {
            return "x86_64";
        }
        return OS_ARCH;
    }

    /**
     * Expected dynamic library filenames based on operating system convention.
     */
    public static List<String> expectedLibraryNames() {
        List<String> names = new ArrayList<>();
        if (isMac()) {
            names.add("libwowool.3.4.1.dylib");
            names.add("libwowool.3.3.2.dylib");
            names.add("libwowool.dylib");
            names.add("libwowool.so");
        } else if (isLinux()) {
            names.add("libwowool.so.3.4.1");
            names.add("libwowool.so.3.3.2");
            names.add("libwowool.3.3.2.so");
            names.add("libwowool.so");
            names.add("libwowool.dylib");
        } else {
            names.add("wowool.dll");
            names.add("libwowool.3.4.1.dll");
            names.add("libwowool.3.3.2.dll");
            names.add("libwowool.dll");
            names.add("libwowool.so");
        }
        return Collections.unmodifiableList(names);
    }

    /**
     * Resolves the Wowool SDK license key. Order: 1. System property
     * 'wowool.license.key' 2. Environment variable 'WOWOOL_SDK_KEY' 3.
     * ~/.config/wowool/WOWOOL_SDK_KEY
     */
    public static String resolveLicenseKey() {
        String prop = System.getProperty("wowool.license.key");
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }

        String env = System.getenv("WOWOOL_SDK_KEY");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }

        Path homeKey = Path.of(System.getProperty("user.home"), ".config", "wowool", "WOWOOL_SDK_KEY");
        if (Files.exists(homeKey)) {
            try {
                String content = Files.readString(homeKey).trim();
                if (!content.isBlank()) {
                    return content;
                }
            } catch (IOException ignored) {
            }
        }

        return "";
    }

    /**
     * Resolves the directory containing native Wowool libraries (libwowool,
     * libicuuc, libicudata). Searches multi-platform Darwin/Linux architecture
     * paths with comprehensive diagnostics on failure.
     */
    public static Path resolveDylibDir() {
        String prop = System.getProperty("wowool.dylib.dir");
        if (prop != null && !prop.isBlank()) {
            Path p = Path.of(prop);
            if (Files.isDirectory(p)) {
                return p.toAbsolutePath().normalize();
            }
        }

        String env = System.getenv("WOWOOL_DYLIB_DIR");
        if (env != null && !env.isBlank()) {
            Path p = Path.of(env);
            if (Files.isDirectory(p)) {
                return p.toAbsolutePath().normalize();
            }
        }

        Set<Path> candidates = new LinkedHashSet<>();
        String arch = normalizedArch();

        // 1. User-level standard directories (~/.wowool)
        candidates.add(Path.of(System.getProperty("user.home"), ".wowool", "lib"));
        candidates.add(Path.of(System.getProperty("user.home"), ".wowool", "dylibs"));
        candidates.add(Path.of(System.getProperty("user.home"), ".wowool", "lib", arch));

        // 2. Project-local directory and arch subdirectories
        candidates.add(Path.of("native/dylibs"));
        candidates.add(Path.of("native/lib"));
        candidates.add(Path.of("native/lib", isMac() ? "darwin-" + arch : "linux-" + arch));
        candidates.add(Path.of("native/lib", arch));

        // 3. Dynamic Python Virtualenv if active ($VIRTUAL_ENV or local .venv)
        addVirtualEnvCandidates(candidates, "wowool_sdk_cpp.dylibs");

        // 4. Standard enterprise and OS system paths
        candidates.add(Path.of("/opt/wowool/lib"));
        candidates.add(Path.of("/opt/wowool/lib", arch));
        candidates.add(Path.of("/usr/local/lib/wowool"));
        candidates.add(Path.of("/usr/local/lib"));
        candidates.add(Path.of("/usr/lib"));

        // Linux Debian/Ubuntu multi-arch paths
        if (isLinux()) {
            candidates.add(Path.of("/usr/lib/x86_64-linux-gnu"));
            candidates.add(Path.of("/usr/lib/aarch64-linux-gnu"));
            candidates.add(Path.of("/usr/lib64"));
        }

        List<String> targetNames = expectedLibraryNames();
        List<String> probeDiagnostics = new ArrayList<>();

        for (Path c : candidates) {
            Path absCandidate = c.toAbsolutePath().normalize();
            if (!Files.exists(absCandidate)) {
                probeDiagnostics.add(String.format("  - [MISSING DIRECTORY] %s", absCandidate));
                continue;
            }
            if (!Files.isDirectory(absCandidate)) {
                probeDiagnostics.add(String.format("  - [NOT A DIRECTORY]  %s", absCandidate));
                continue;
            }

            for (String targetName : targetNames) {
                Path binary = absCandidate.resolve(targetName);
                if (Files.exists(binary)) {
                    return absCandidate;
                }
            }

            probeDiagnostics.add(String.format("  - [EXISTS - NO MATCH] %s (missing: %s)", absCandidate, targetNames));
        }

        throw new WowoolException(buildDylibNotFoundMessage(probeDiagnostics, targetNames));
    }

    /**
     * Resolves the root directory containing language and domain models
     * (lxware/wowool).
     */
    public static Path resolveWowoolRoot() {
        String prop = System.getProperty("wowool.root");
        if (prop != null && !prop.isBlank()) {
            Path p = Path.of(prop);
            if (Files.isDirectory(p)) {
                return p.toAbsolutePath().normalize();
            }
        }

        String env = System.getenv("WOWOOL_ROOT");
        if (env != null && !env.isBlank()) {
            Path p = Path.of(env);
            if (Files.isDirectory(p)) {
                return p.toAbsolutePath().normalize();
            }
        }

        Set<Path> candidates = new LinkedHashSet<>();
        // 1. User-level standard directories (~/.wowool)
        candidates.add(Path.of(System.getProperty("user.home"), ".wowool", "models"));
        candidates.add(Path.of(System.getProperty("user.home"), ".wowool"));

        // 2. Project-local directory
        candidates.add(Path.of("native/wowool"));
        candidates.add(Path.of("native/models"));

        // 3. Dynamic Python Virtualenv if active ($VIRTUAL_ENV or local .venv)
        addVirtualEnvCandidates(candidates, "wowool");

        // 4. Standard enterprise / OS system paths
        candidates.add(Path.of("/opt/wowool/models"));
        candidates.add(Path.of("/opt/wowool"));
        candidates.add(Path.of("/usr/share/wowool"));
        candidates.add(Path.of("/usr/local/share/wowool"));

        List<String> probeDiagnostics = new ArrayList<>();

        for (Path c : candidates) {
            Path absCandidate = c.toAbsolutePath().normalize();
            if (!Files.exists(absCandidate)) {
                probeDiagnostics.add(String.format("  - [MISSING DIRECTORY] %s", absCandidate));
                continue;
            }
            if (!Files.isDirectory(absCandidate)) {
                probeDiagnostics.add(String.format("  - [NOT A DIRECTORY]  %s", absCandidate));
                continue;
            }

            if (Files.isDirectory(absCandidate.resolve("lxware/wowool"))) {
                return absCandidate;
            }

            // Check if directory itself contains .language files (flat model mount)
            try (Stream<Path> stream = Files.list(absCandidate)) {
                if (stream.anyMatch(p -> p.getFileName().toString().endsWith(".language"))) {
                    return absCandidate;
                }
            } catch (IOException ignored) {
            }

            probeDiagnostics.add(String.format("  - [EXISTS - NO MODELS] %s (missing 'lxware/wowool' or '*.language')",
                    absCandidate));
        }

        throw new WowoolException(buildModelRootNotFoundMessage(probeDiagnostics));
    }

    /**
     * Resolves the .language file for the given language. Supports both python
     * 'lxware/wowool' layouts and flat directory mounts.
     */
    public static Optional<Path> resolveLanguageFile(Path wowoolRoot, String language) {
        if (wowoolRoot == null || language == null || language.isBlank()) {
            return Optional.empty();
        }

        // 1. Direct path if specified
        Path direct = Path.of(language);
        if (Files.exists(direct)) {
            return Optional.of(direct.toAbsolutePath().normalize());
        }

        List<Path> searchDirs = getModelSearchDirectories(wowoolRoot);

        for (Path dir : searchDirs) {
            // Direct name e.g. dutch.language
            Path candidate1 = dir.resolve(language + ".language");
            if (Files.exists(candidate1)) {
                return Optional.of(candidate1.toAbsolutePath().normalize());
            }

            // Versioned pattern, e.g. dutch@3.2.7.language
            try (Stream<Path> stream = Files.list(dir)) {
                Optional<Path> found = stream.filter(p -> {
                    String name = p.getFileName().toString();
                    return name.startsWith(language + "@") && name.endsWith(".language");
                }).findFirst();
                if (found.isPresent()) {
                    return found.map(p -> p.toAbsolutePath().normalize());
                }
            } catch (IOException ignored) {
            }
        }

        return Optional.empty();
    }

    /**
     * Resolves a binary domain file (.dom) for the given language and domain name.
     * Supports standard names, versioned names (@version), and flat layouts.
     */
    public static Optional<Path> resolveDomainFile(Path wowoolRoot, String language, String domain) {
        if (wowoolRoot == null || domain == null || domain.isBlank()) {
            return Optional.empty();
        }

        // 1. Direct file
        Path direct = Path.of(domain);
        if (Files.exists(direct)) {
            return Optional.of(direct.toAbsolutePath().normalize());
        }

        List<Path> searchDirs = getModelSearchDirectories(wowoolRoot);
        String prefix = (language != null && !language.isBlank()) ? language + "-" + domain : domain;

        for (Path dir : searchDirs) {
            // Exact filenames: entity.dom or dutch-entity.dom
            Path exactDomain = dir.resolve(domain + ".dom");
            if (Files.exists(exactDomain)) {
                return Optional.of(exactDomain.toAbsolutePath().normalize());
            }

            Path exactPrefix = dir.resolve(prefix + ".dom");
            if (Files.exists(exactPrefix)) {
                return Optional.of(exactPrefix.toAbsolutePath().normalize());
            }

            // Pattern with @version or prefix: e.g. dutch-entity@3.2.7.dom or
            // entity@3.2.7.dom
            try (Stream<Path> stream = Files.list(dir)) {
                Optional<Path> found = stream.filter(p -> {
                    String name = p.getFileName().toString();
                    return (name.startsWith(prefix + "@") || name.startsWith(domain + "@")
                            || name.equals(prefix + ".dom") || name.equals(domain + ".dom")) && name.endsWith(".dom");
                }).findFirst();
                if (found.isPresent()) {
                    return found.map(p -> p.toAbsolutePath().normalize());
                }
            } catch (IOException ignored) {
            }
        }

        return Optional.empty();
    }

    /**
     * Returns search directories under the specified wowoolRoot for linguistic
     * models.
     */
    public static List<Path> getModelSearchDirectories(Path wowoolRoot) {
        if (wowoolRoot == null) {
            return Collections.emptyList();
        }
        List<Path> searchDirs = new ArrayList<>();
        Path lxware = wowoolRoot.resolve("lxware/wowool");
        if (Files.isDirectory(lxware)) {
            searchDirs.add(lxware);
        }
        Path modelsLxware = wowoolRoot.resolve("models/lxware/wowool");
        if (Files.isDirectory(modelsLxware)) {
            searchDirs.add(modelsLxware);
        }
        if (Files.isDirectory(wowoolRoot)) {
            searchDirs.add(wowoolRoot);
        }
        Path modelsSub = wowoolRoot.resolve("models");
        if (Files.isDirectory(modelsSub)) {
            searchDirs.add(modelsSub);
        }
        return Collections.unmodifiableList(searchDirs);
    }

    private static void addVirtualEnvCandidates(Set<Path> candidates, String targetDirName) {
        List<Path> venvRoots = new ArrayList<>();
        String venvEnv = System.getenv("VIRTUAL_ENV");
        if (envIsPresent(venvEnv)) {
            venvRoots.add(Path.of(venvEnv));
        }
        venvRoots.add(Path.of(".venv"));
        venvRoots.add(Path.of("../.venv"));
        venvRoots.add(Path.of("../../.venv"));

        for (Path venv : venvRoots) {
            Path libDir = venv.resolve("lib");
            if (Files.isDirectory(libDir)) {
                try (Stream<Path> stream = Files.list(libDir)) {
                    stream.filter(Files::isDirectory).filter(p -> p.getFileName().toString().startsWith("python"))
                            .forEach(pyDir -> {
                                Path sitePackages = pyDir.resolve("site-packages").resolve(targetDirName);
                                candidates.add(sitePackages);
                            });
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static boolean envIsPresent(String s) {
        return s != null && !s.isBlank();
    }

    private static String buildDylibNotFoundMessage(List<String> probed, List<String> targetNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("Native Wowool dynamic library folder not found across multi-platform search paths.\n");
        sb.append("--------------------------------------------------------------------------------\n");
        sb.append(String.format("Platform Architecture: %s (%s, %s)\n", normalizedArch(), OS_NAME, OS_ARCH));
        sb.append(String.format("Expected Libraries:    %s\n", targetNames));
        sb.append(String.format("Probed Locations (%d checked):\n", probed.size()));
        for (String line : probed) {
            sb.append(line).append("\n");
        }
        sb.append("--------------------------------------------------------------------------------\n");
        sb.append("Actionable Remediation:\n");
        sb.append("  1. Specify via JVM property:     -Dwowool.dylib.dir=/path/to/wowool/lib\n");
        sb.append("  2. Set environment variable:     export WOWOOL_DYLIB_DIR=/path/to/wowool/lib\n");
        sb.append("  3. Install Wowool C-ABI dylibs:  mkdir -p ~/.wowool/lib && cp libwowool* ~/.wowool/lib/\n");
        return sb.toString();
    }

    /**
     * Lists all available language models discovered in the configured Wowool root.
     *
     * @param wowoolRoot
     *            Directory containing linguistic models
     * @return List of language names or formatted version strings (e.g., "dutch
     *         (v3.2.7)")
     */
    public static List<String> listAvailableLanguages(Path wowoolRoot) {
        if (wowoolRoot == null) {
            return Collections.emptyList();
        }
        Set<String> languages = new TreeSet<>();
        List<Path> searchDirs = getModelSearchDirectories(wowoolRoot);

        for (Path dir : searchDirs) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".language")).forEach(p -> {
                    String fn = p.getFileName().toString();
                    String base = fn.substring(0, fn.length() - ".language".length());
                    if (base.contains("@")) {
                        String[] parts = base.split("@", 2);
                        languages.add(parts[0] + " (v" + parts[1] + ")");
                    } else {
                        languages.add(base);
                    }
                });
            } catch (IOException ignored) {
            }
        }
        return List.copyOf(languages);
    }

    /**
     * Lists all available domain models (.dom) for a given language discovered in
     * the configured Wowool root.
     *
     * @param wowoolRoot
     *            Directory containing linguistic models
     * @param language
     *            Language prefix to filter by (e.g. "dutch")
     * @return List of domain names or formatted version strings (e.g., "entity
     *         (v3.2.7)")
     */
    public static List<String> listAvailableDomains(Path wowoolRoot, String language) {
        if (wowoolRoot == null || language == null || language.isBlank()) {
            return Collections.emptyList();
        }
        Set<String> domains = new TreeSet<>();
        List<Path> searchDirs = getModelSearchDirectories(wowoolRoot);
        String prefix = language.trim().toLowerCase(Locale.ROOT) + "-";

        for (Path dir : searchDirs) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".dom")).forEach(p -> {
                    String fn = p.getFileName().toString();
                    String base = fn.substring(0, fn.length() - ".dom".length());
                    if (base.startsWith(prefix)) {
                        String sub = base.substring(prefix.length());
                        if (sub.contains("@")) {
                            String[] parts = sub.split("@", 2);
                            domains.add(parts[0] + " (v" + parts[1] + ")");
                        } else {
                            domains.add(sub);
                        }
                    } else if (!base.contains("-")) {
                        // Unprefixed global domain, e.g. entity.dom or entity@3.2.8.dom
                        if (base.contains("@")) {
                            String[] parts = base.split("@", 2);
                            domains.add(parts[0] + " (v" + parts[1] + ")");
                        } else {
                            domains.add(base);
                        }
                    }
                });
            } catch (IOException ignored) {
            }
        }
        return List.copyOf(domains);
    }

    private static String buildModelRootNotFoundMessage(List<String> probed) {
        StringBuilder sb = new StringBuilder();
        sb.append("Wowool root directory containing language and domain models not found.\n");
        sb.append("--------------------------------------------------------------------------------\n");
        sb.append(String.format("Platform Architecture: %s (%s)\n", normalizedArch(), OS_NAME));
        sb.append("Expected Model Layout: Directory containing 'lxware/wowool/' or flat '*.language' files\n");
        sb.append(String.format("Probed Locations (%d checked):\n", probed.size()));
        for (String line : probed) {
            sb.append(line).append("\n");
        }
        sb.append("--------------------------------------------------------------------------------\n");
        sb.append("Actionable Remediation:\n");
        sb.append("  1. Specify via JVM property:     -Dwowool.root=/path/to/wowool/models\n");
        sb.append("  2. Set environment variable:     export WOWOOL_ROOT=/path/to/wowool/models\n");
        sb.append("  3. Mount or unpack models into:  ~/.wowool/models/ or /opt/wowool/models/\n");
        return sb.toString();
    }

    private static String buildMissingLicenseMessage(Path homeKey) {
        return "Wowool license key not found.\n" + "Actionable Remediation:\n"
                + "  1. Set environment variable: export WOWOOL_SDK_KEY=\"<your-license-key>\"\n"
                + "  2. Pass JVM property:        -Dwowool.license.key=\"<your-license-key>\"\n"
                + "  3. Save key to file:         mkdir -p ~/.config/wowool && echo \"<key>\" > " + homeKey;
    }
}
