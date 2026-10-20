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

package com.wowool.sdk.installer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wowool.sdk.WowoolException;
import com.wowool.sdk.internal.ffi.WowoolPaths;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Zero-configuration on-demand installer and container image pre-bake utility
 * for native Wowool C++ binaries and language domain models.
 * <p>
 * Fetches standardized, official distribution packages via HTTPS directly from
 * PyPI releases (which are standard ZIP archives per PEP 427) without requiring
 * Python, pip, or virtual environments.
 */
public final class WowoolInstaller {

    private static final String DEFAULT_PYPI_API = "https://pypi.org/pypi";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final HttpClient httpClient;
    private final boolean quiet;

    /**
     * Constructs a default installer instance.
     */
    public WowoolInstaller() {
        this(false);
    }

    /**
     * Constructs an installer instance with quiet configuration.
     *
     * @param quiet
     *            whether to suppress progress console logging
     */
    public WowoolInstaller(boolean quiet) {
        this.quiet = quiet;
        this.httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    /**
     * Returns the default standard installation directory ({@code ~/.wowool} or
     * system property {@code wowool.install.dir} / environment
     * {@code WOWOOL_INSTALL_DIR}).
     *
     * @return Path to default installation directory
     */
    public static Path defaultInstallDir() {
        String prop = System.getProperty("wowool.install.dir");
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop).toAbsolutePath().normalize();
        }
        String env = System.getenv("WOWOOL_INSTALL_DIR");
        if (env != null && !env.isBlank()) {
            return Path.of(env).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), ".wowool").toAbsolutePath().normalize();
    }

    /**
     * Verifies if the target directory already contains a complete installation of
     * native binaries and basic models.
     *
     * @param targetDir
     *            Directory to inspect
     * @return {@code true} if binaries and model directories exist
     */
    public static boolean isInstalled(Path targetDir) {
        if (targetDir == null || !Files.isDirectory(targetDir)) {
            return false;
        }

        Path libDir = targetDir.resolve("lib");
        if (!Files.isDirectory(libDir)) {
            return false;
        }

        boolean hasBinary = false;
        try (Stream<Path> stream = Files.list(libDir)) {
            hasBinary = stream.anyMatch(p -> {
                String name = p.getFileName().toString();
                return name.startsWith("libwowool.") && (name.endsWith(".dylib") || name.contains(".so"));
            });
        } catch (IOException ignored) {
            return false;
        }

        if (!hasBinary) {
            return false;
        }

        Path modelsDir = targetDir.resolve("models/lxware/wowool");
        Path lxwareDir = targetDir.resolve("lxware/wowool");
        return Files.isDirectory(modelsDir) || Files.isDirectory(lxwareDir);
    }

    /**
     * Performs automatic on-demand installation to {@link #defaultInstallDir()} if
     * not already present.
     *
     * @return Path to the verified installation directory
     */
    public static Path installIfNeeded() {
        Path target = defaultInstallDir();
        if (isInstalled(target)) {
            return target;
        }
        return new WowoolInstaller().install(target, "auto", List.of("dutch"));
    }

    /**
     * Installs native binaries and specified language models into the designated
     * target directory.
     *
     * @param targetDir
     *            Target installation root (e.g., {@code ~/.wowool} or
     *            {@code /opt/wowool})
     * @param platform
     *            Target platform architecture (e.g., "auto", "darwin-aarch64",
     *            "linux-x86_64", "linux-aarch64")
     * @param languages
     *            List of language packages to provision (e.g., ["dutch"])
     * @return Target installation directory
     */
    public Path install(Path targetDir, String platform, List<String> languages) {
        Objects.requireNonNull(targetDir, "targetDir must not be null");
        String resolvedPlatform = resolvePlatform(platform);

        log(String.format("Preparing Wowool installation at: %s [Platform: %s]", targetDir, resolvedPlatform));

        try {
            Path libDir = targetDir.resolve("lib");
            Path binDir = targetDir.resolve("bin");
            Path modelsDir = targetDir.resolve("models/lxware/wowool");
            Path lxwareRoot = targetDir.resolve("models/lxware");

            Files.createDirectories(libDir);
            Files.createDirectories(binDir);
            Files.createDirectories(modelsDir);

            // 1. Download and extract C++ native library bundle (wowool-sdk-cpp)
            installCppBundle(resolvedPlatform, libDir, binDir);

            // 2. Download and extract Generic language support (wowool-generic)
            installGenericModels(lxwareRoot);

            // 3. Download and extract Language Identification model (wowool-lid)
            installLidModel(lxwareRoot);

            // 4. Download and extract requested language packages (e.g. wowool-dutch)
            if (languages != null) {
                for (String lang : languages) {
                    if (lang != null && !lang.isBlank()) {
                        installLanguagePackage(lang.trim().toLowerCase(Locale.ROOT), lxwareRoot);
                    }
                }
            }

            // 5. Establish convenience symbolic link: <target>/lxware -> models/lxware
            createSymlinkSafe(targetDir.resolve("lxware"), targetDir.relativize(lxwareRoot));

            log(String.format("Successfully installed Wowool native engine and models to %s", targetDir));
            return targetDir;

        } catch (Exception ex) {
            throw new WowoolException("Failed to install Wowool engine and language assets: " + ex.getMessage(), ex);
        }
    }

    private void installCppBundle(String platform, Path libDir, Path binDir) throws IOException, InterruptedException {
        log("Resolving package: wowool-sdk-cpp for " + platform + "...");

        Predicate<String> platformMatcher;
        if (platform.contains("darwin") || platform.contains("macosx") || platform.contains("mac")) {
            platformMatcher = fn -> fn.contains("macosx") && (fn.contains("arm64") || fn.contains("universal2"));
        } else if (platform.contains("aarch64") || platform.contains("arm64")) {
            platformMatcher = fn -> fn.contains("manylinux") && (fn.contains("aarch64") || fn.contains("arm64"));
        } else {
            platformMatcher = fn -> fn.contains("manylinux") && fn.contains("x86_64");
        }

        String wheelUrl = resolveWheelUrl("wowool-sdk-cpp", platformMatcher);
        log("Downloading native binary package from: " + wheelUrl);

        try (InputStream in = openHttpStream(wheelUrl);
                BufferedInputStream bin = new BufferedInputStream(in);
                ZipInputStream zis = new ZipInputStream(bin)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                Path fileName = Path.of(name).getFileName();
                if (fileName == null) {
                    continue;
                }
                String fn = fileName.toString();

                // Shared libraries (*.dylib, *.so, *.so.*)
                if (fn.contains(".dylib") || fn.contains(".so")) {
                    Path dest = libDir.resolve(fn);
                    Files.copy(zis, dest, StandardCopyOption.REPLACE_EXISTING);
                } else if (fn.equals("afst++") || fn.equals("woc++") || fn.equals("wow++")) {
                    Path dest = binDir.resolve(fn);
                    Files.copy(zis, dest, StandardCopyOption.REPLACE_EXISTING);
                    makeExecutableSafe(dest);
                }
            }
        }

        // Create canonical symlinks (libwowool.dylib -> libwowool.3.*.dylib,
        // libwowool.so -> libwowool.so.3.*)
        linkCanonicalLibrary(libDir);
    }

    private void installGenericModels(Path lxwareRoot) throws IOException, InterruptedException {
        log("Resolving package: wowool-generic...");
        String wheelUrl = resolveWheelUrl("wowool-generic", fn -> fn.contains("any") || fn.endsWith(".whl"));
        extractModelWheel(wheelUrl, lxwareRoot);
    }

    private void installLidModel(Path lxwareRoot) throws IOException, InterruptedException {
        log("Resolving package: wowool-lid...");
        String wheelUrl = resolveWheelUrl("wowool-lid", fn -> fn.contains("any") || fn.endsWith(".whl"));
        extractModelWheel(wheelUrl, lxwareRoot);
    }

    private void installLanguagePackage(String language, Path lxwareRoot) throws IOException, InterruptedException {
        String packageName = "wowool-" + language;
        log("Resolving language package: " + packageName + "...");
        String wheelUrl = resolveWheelUrl(packageName, fn -> fn.contains("any") || fn.endsWith(".whl"));
        extractModelWheel(wheelUrl, lxwareRoot);
    }

    private void extractModelWheel(String wheelUrl, Path lxwareRoot) throws IOException, InterruptedException {
        log("Extracting models from: " + wheelUrl);
        try (InputStream in = openHttpStream(wheelUrl);
                BufferedInputStream bin = new BufferedInputStream(in);
                ZipInputStream zis = new ZipInputStream(bin)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (!name.startsWith("wowool/lxware/")) {
                    continue;
                }

                String relativePath = name.substring("wowool/lxware/".length());
                Path dest = lxwareRoot.resolve(relativePath);
                Files.createDirectories(dest.getParent());
                Files.copy(zis, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private String resolveWheelUrl(String packageName, Predicate<String> matcher)
            throws IOException, InterruptedException {
        String apiUrl = System.getProperty("wowool.pypi.url", DEFAULT_PYPI_API) + "/" + packageName + "/json";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(apiUrl)).timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "wowool-java-sdk-installer").GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new WowoolException(String.format("PyPI package resolution failed for '%s' with HTTP status %d",
                    packageName, response.statusCode()));
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode urls = root.path("urls");
        if (urls.isArray()) {
            for (JsonNode urlNode : urls) {
                String filename = urlNode.path("filename").asText("");
                if (filename.endsWith(".whl") && matcher.test(filename)) {
                    return urlNode.path("url").asText();
                }
            }
        }

        throw new WowoolException(String.format(
                "No matching .whl release found for package '%s' satisfying target platform filters", packageName));
    }

    private InputStream openHttpStream(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "wowool-java-sdk-installer").GET().build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new WowoolException(
                    String.format("Download failed for '%s' with HTTP status %d", url, response.statusCode()));
        }
        return response.body();
    }

    private void linkCanonicalLibrary(Path libDir) {
        try (Stream<Path> stream = Files.list(libDir)) {
            List<Path> files = stream.toList();

            // Link macOS: libwowool.dylib -> libwowool.3.*.dylib
            Path canonicalMac = libDir.resolve("libwowool.dylib");
            if (!Files.exists(canonicalMac)) {
                files.stream().filter(p -> {
                    String fn = p.getFileName().toString();
                    return fn.startsWith("libwowool.") && fn.endsWith(".dylib") && !fn.equals("libwowool.dylib");
                }).findFirst().ifPresent(target -> createSymlinkSafe(canonicalMac, target.getFileName()));
            }

            // Link Linux: libwowool.so -> libwowool.so.3.*
            Path canonicalLinux = libDir.resolve("libwowool.so");
            if (!Files.exists(canonicalLinux)) {
                files.stream().filter(p -> {
                    String fn = p.getFileName().toString();
                    return fn.startsWith("libwowool.so.") && !fn.equals("libwowool.so");
                }).findFirst().ifPresent(target -> createSymlinkSafe(canonicalLinux, target.getFileName()));
            }
        } catch (IOException ignored) {
        }
    }

    private static void createSymlinkSafe(Path link, Path target) {
        try {
            if (Files.isSymbolicLink(link) || Files.exists(link)) {
                Files.delete(link);
            }
            Files.createSymbolicLink(link, target);
        } catch (Exception ignored) {
            // On operating systems without symlink privileges (e.g. non-developer mode
            // Windows),
            // copy if symlink creation is denied
            try {
                Path resolvedTarget = link.getParent() != null ? link.getParent().resolve(target) : target;
                if (Files.isRegularFile(resolvedTarget)) {
                    Files.copy(resolvedTarget, link, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ignoredCopy) {
            }
        }
    }

    private static void makeExecutableSafe(Path path) {
        try {
            path.toFile().setExecutable(true, false);
        } catch (SecurityException ignored) {
        }
    }

    private static String resolvePlatform(String platform) {
        if (platform != null && !platform.equalsIgnoreCase("auto") && !platform.isBlank()) {
            return platform.toLowerCase(Locale.ROOT);
        }
        if (WowoolPaths.isMac()) {
            return "darwin-" + WowoolPaths.normalizedArch();
        }
        if (WowoolPaths.isLinux()) {
            return "linux-" + WowoolPaths.normalizedArch();
        }
        return "unknown-" + WowoolPaths.normalizedArch();
    }

    private void log(String message) {
        if (!quiet) {
            System.out.println("[WowoolInstaller] " + message);
        }
    }

    /**
     * Standalone CLI entry point for pre-baking OCI / Docker container images or
     * manual setup.
     * <p>
     * Supported arguments:
     * <ul>
     * <li>{@code --target=
     *
    <dir>
     * } : Target directory (default: {@code ~/.wowool} or {@code /opt/wowool})</li>
     * <li>{@code --platform=<plat>} : Target platform ("auto", "linux-x86_64",
     * "linux-aarch64", "darwin-aarch64")</li>
     * <li>{@code --languages=<l1,l2>} : Comma-separated list of languages (default:
     * "dutch")</li>
     * <li>{@code --quiet} : Suppress logging output</li>
     * </ul>
     *
     * @param args
     *            Command-line arguments
     */
    public static void main(String[] args) {
        Path targetDir = defaultInstallDir();
        String platform = "auto";
        List<String> languages = new ArrayList<>(List.of("dutch"));
        boolean quiet = false;

        for (String arg : args) {
            if (arg.startsWith("--target=")) {
                targetDir = Path.of(arg.substring("--target=".length())).toAbsolutePath().normalize();
            } else if (arg.startsWith("--platform=")) {
                platform = arg.substring("--platform=".length()).trim();
            } else if (arg.startsWith("--languages=")) {
                languages.clear();
                for (String lang : arg.substring("--languages=".length()).split(",")) {
                    if (!lang.isBlank()) {
                        languages.add(lang.trim());
                    }
                }
            } else if (arg.equals("--quiet") || arg.equals("-q")) {
                quiet = true;
            }
        }

        WowoolInstaller installer = new WowoolInstaller(quiet);
        installer.install(targetDir, platform, languages);
    }
}
