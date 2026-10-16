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
import java.util.List;
import java.util.Optional;

/**
 * Path resolver for native Wowool shared libraries, language models, and license keys.
 */
public final class WowoolPaths {

    private WowoolPaths() {}

    /**
     * Resolves the Wowool SDK license key.
     * Order:
     * 1. System property 'wowool.license.key'
     * 2. Environment variable 'WOWOOL_SDK_KEY'
     * 3. ~/.config/wowool/WOWOOL_SDK_KEY
     */
    public static String resolveLicenseKey() {
        String prop = System.getProperty("wowool.license.key");
        if (prop != null && !prop.isBlank()) return prop.trim();

        String env = System.getenv("WOWOOL_SDK_KEY");
        if (env != null && !env.isBlank()) return env.trim();

        Path homeKey = Path.of(System.getProperty("user.home"), ".config", "wowool", "WOWOOL_SDK_KEY");
        if (Files.exists(homeKey)) {
            try {
                String content = Files.readString(homeKey).trim();
                if (!content.isBlank()) return content;
            } catch (IOException ignored) {}
        }

        throw new WowoolException("Wowool license key not found. Please set WOWOOL_SDK_KEY environment variable or create ~/.config/wowool/WOWOOL_SDK_KEY");
    }

    /**
     * Resolves the directory containing native Wowool libraries (libwowool, libicuuc, libicudata).
     */
    public static Path resolveDylibDir() {
        String prop = System.getProperty("wowool.dylib.dir");
        if (prop != null && !prop.isBlank()) {
            Path p = Path.of(prop);
            if (Files.isDirectory(p)) return p.toAbsolutePath().normalize();
        }

        String env = System.getenv("WOWOOL_DYLIB_DIR");
        if (env != null && !env.isBlank()) {
            Path p = Path.of(env);
            if (Files.isDirectory(p)) return p.toAbsolutePath().normalize();
        }

        List<Path> candidates = new ArrayList<>();
        // 1. User-level standard directories (~/.wowool)
        candidates.add(Path.of(System.getProperty("user.home"), ".wowool", "lib"));
        candidates.add(Path.of(System.getProperty("user.home"), ".wowool", "dylibs"));

        // 2. Project-local directory
        candidates.add(Path.of("native/dylibs"));
        candidates.add(Path.of("native/lib"));

        // 3. Dynamic Python Virtualenv if active ($VIRTUAL_ENV or local .venv)
        addVirtualEnvCandidates(candidates, "wowool_sdk_cpp.dylibs");

        // 4. Standard enterprise / OS system paths
        candidates.add(Path.of("/opt/wowool/lib"));
        candidates.add(Path.of("/usr/local/lib/wowool"));
        candidates.add(Path.of("/usr/local/lib"));
        candidates.add(Path.of("/usr/lib"));

        for (Path c : candidates) {
            if (Files.isDirectory(c) && (Files.exists(c.resolve("libwowool.3.3.2.dylib")) || Files.exists(c.resolve("libwowool.dylib")) || Files.exists(c.resolve("libwowool.so")))) {
                return c.toAbsolutePath().normalize();
            }
        }

        throw new WowoolException("Native Wowool dynamic library folder not found. Please set 'wowool.dylib.dir' property or 'WOWOOL_DYLIB_DIR' environment variable.");
    }

    /**
     * Resolves the root directory containing language and domain models (lxware/wowool).
     */
    public static Path resolveWowoolRoot() {
        String prop = System.getProperty("wowool.root");
        if (prop != null && !prop.isBlank()) {
            Path p = Path.of(prop);
            if (Files.isDirectory(p)) return p.toAbsolutePath().normalize();
        }

        String env = System.getenv("WOWOOL_ROOT");
        if (env != null && !env.isBlank()) {
            Path p = Path.of(env);
            if (Files.isDirectory(p)) return p.toAbsolutePath().normalize();
        }

        List<Path> candidates = new ArrayList<>();
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

        for (Path c : candidates) {
            if (Files.isDirectory(c)) {
                if (Files.isDirectory(c.resolve("lxware/wowool"))) {
                    return c.toAbsolutePath().normalize();
                }
                // Check if directory itself contains .language files (flat model mount)
                try (var stream = Files.list(c)) {
                    if (stream.anyMatch(p -> p.getFileName().toString().endsWith(".language"))) {
                        return c.toAbsolutePath().normalize();
                    }
                } catch (IOException ignored) {}
            }
        }

        throw new WowoolException("Wowool root directory containing language models not found. Please set 'wowool.root' property or 'WOWOOL_ROOT' environment variable.");
    }

    /**
     * Helper to discover Wowool packages dynamically in any active or local Python virtual environment.
     */
    private static void addVirtualEnvCandidates(List<Path> candidates, String targetDirName) {
        List<Path> venvRoots = new ArrayList<>();
        String venvEnv = System.getenv("VIRTUAL_ENV");
        if (venvEnv != null && !venvEnv.isBlank()) {
            venvRoots.add(Path.of(venvEnv));
        }
        venvRoots.add(Path.of(".venv"));
        venvRoots.add(Path.of("../.venv"));
        venvRoots.add(Path.of("../../.venv"));

        for (Path venv : venvRoots) {
            Path libDir = venv.resolve("lib");
            if (Files.isDirectory(libDir)) {
                try (var stream = Files.list(libDir)) {
                    stream.filter(Files::isDirectory)
                          .filter(p -> p.getFileName().toString().startsWith("python"))
                          .forEach(pyDir -> {
                              Path sitePackages = pyDir.resolve("site-packages").resolve(targetDirName);
                              candidates.add(sitePackages);
                          });
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Resolves the .language file for the given language.
     * Supports both python 'lxware/wowool' layouts and flat directory mounts.
     */
    public static Optional<Path> resolveLanguageFile(Path wowoolRoot, String language) {
        // 1. Direct path if specified
        Path direct = Path.of(language);
        if (Files.exists(direct)) return Optional.of(direct.toAbsolutePath().normalize());

        List<Path> searchDirs = new ArrayList<>();
        Path lxware = wowoolRoot.resolve("lxware/wowool");
        if (Files.isDirectory(lxware)) searchDirs.add(lxware);
        if (Files.isDirectory(wowoolRoot)) searchDirs.add(wowoolRoot);
        Path modelsSub = wowoolRoot.resolve("models");
        if (Files.isDirectory(modelsSub)) searchDirs.add(modelsSub);

        for (Path dir : searchDirs) {
            // Direct name e.g. dutch.language
            Path candidate1 = dir.resolve(language + ".language");
            if (Files.exists(candidate1)) return Optional.of(candidate1.toAbsolutePath().normalize());

            // Versioned pattern, e.g. dutch@3.2.7.language
            try (var stream = Files.list(dir)) {
                Optional<Path> found = stream
                    .filter(p -> p.getFileName().toString().startsWith(language + "@") && p.getFileName().toString().endsWith(".language"))
                    .findFirst();
                if (found.isPresent()) return found.map(p -> p.toAbsolutePath().normalize());
            } catch (IOException ignored) {}
        }

        return Optional.empty();
    }

    /**
     * Resolves a binary domain file (.dom) for the given language and domain name.
     * Supports standard names, versioned names (@version), and flat layouts.
     */
    public static Optional<Path> resolveDomainFile(Path wowoolRoot, String language, String domain) {
        // 1. Direct file
        Path direct = Path.of(domain);
        if (Files.exists(direct)) return Optional.of(direct.toAbsolutePath().normalize());

        List<Path> searchDirs = new ArrayList<>();
        Path lxware = wowoolRoot.resolve("lxware/wowool");
        if (Files.isDirectory(lxware)) searchDirs.add(lxware);
        if (Files.isDirectory(wowoolRoot)) searchDirs.add(wowoolRoot);
        Path modelsSub = wowoolRoot.resolve("models");
        if (Files.isDirectory(modelsSub)) searchDirs.add(modelsSub);

        String prefix = language + "-" + domain;

        for (Path dir : searchDirs) {
            // Exact filenames: entity.dom or dutch-entity.dom
            Path exactDomain = dir.resolve(domain + ".dom");
            if (Files.exists(exactDomain)) return Optional.of(exactDomain.toAbsolutePath().normalize());

            Path exactPrefix = dir.resolve(prefix + ".dom");
            if (Files.exists(exactPrefix)) return Optional.of(exactPrefix.toAbsolutePath().normalize());

            // Pattern with @version or prefix: e.g. dutch-entity@3.2.7.dom or entity@3.2.7.dom
            try (var stream = Files.list(dir)) {
                Optional<Path> found = stream
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return (name.startsWith(prefix + "@") || name.startsWith(domain + "@")
                                || name.equals(prefix + ".dom") || name.equals(domain + ".dom"))
                            && name.endsWith(".dom");
                    })
                    .findFirst();
                if (found.isPresent()) return found.map(p -> p.toAbsolutePath().normalize());
            } catch (IOException ignored) {}
        }

        return Optional.empty();
    }
}
