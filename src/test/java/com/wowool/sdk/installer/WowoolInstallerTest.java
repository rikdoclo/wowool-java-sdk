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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WowoolInstallerTest {

    @Test
    @DisplayName("Verify defaultInstallDir resolves non-null standard path")
    void testDefaultInstallDir() {
        Path defaultDir = WowoolInstaller.defaultInstallDir();
        assertNotNull(defaultDir, "defaultInstallDir must never be null");
        assertTrue(defaultDir.toString().contains(".wowool"), "defaultInstallDir should point to .wowool");
    }

    @Test
    @DisplayName("Verify isInstalled returns false for null or non-existent paths")
    void testIsInstalledWithInvalidPaths(@TempDir Path tempDir) {
        assertFalse(WowoolInstaller.isInstalled(null), "Null path must not report as installed");
        assertFalse(WowoolInstaller.isInstalled(tempDir.resolve("non_existent")),
                "Non-existent path must not report as installed");
        assertFalse(WowoolInstaller.isInstalled(tempDir), "Empty temp directory must not report as installed");

        // Partially populated directory without models
        Path libDir = tempDir.resolve("lib");
        try {
            Files.createDirectories(libDir);
            Files.createFile(libDir.resolve("libwowool.dylib"));
            assertFalse(WowoolInstaller.isInstalled(tempDir),
                    "Directory with only lib and no models must not report as installed");
        } catch (Exception ignored) {
        }
    }

    @Test
    @DisplayName("Verify isInstalled returns true when binaries and models are present")
    void testIsInstalledWithCompleteStructure(@TempDir Path tempDir) throws Exception {
        Path libDir = tempDir.resolve("lib");
        Path modelsDir = tempDir.resolve("models/lxware/wowool");

        Files.createDirectories(libDir);
        Files.createDirectories(modelsDir);
        Files.createFile(libDir.resolve("libwowool.dylib"));

        assertTrue(WowoolInstaller.isInstalled(tempDir), "Properly structured directory must report as installed");
    }
}
